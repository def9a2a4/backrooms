# /// script
# requires-python = ">=3.11"
# dependencies = ["trimesh", "numpy", "scipy", "pillow", "fast-simplification"]
# ///
"""Build compressed meshes from photogrammetry scans for the voxel preview (and future levels).

Generic counterpart of scripts/stonehenge/build_mesh.py: instead of hand-tuned material rules,
every face's texture color is matched to the nearest block of a per-model palette, after
normalizing the scan's lighting to the palette's (lightness mean/spread, color cast). Writes the
same SHM2 format the runtime and scripts/voxel-preview read.

Models are listed in MODELS below; their source GLBs live in .temp/ (gitignored). Face colors are
cached in .temp/scans/<name>.faces.npz, since loading the 100-200 MB GLBs takes a while.

Usage: uv run scripts/scans/build_scan.py gobekli [--max-kb 50] [--output ...]
       uv run scripts/scans/build_scan.py all
"""

import argparse
import gzip
import io
import json
import struct
import sys
import urllib.request
from pathlib import Path

import fast_simplification
import numpy as np
import trimesh
from PIL import Image
from scipy.spatial import cKDTree

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from meshlib import quantize, write_triangles, write_vertices  # noqa: E402

Image.MAX_IMAGE_PIXELS = None
ROOT = Path(__file__).resolve().parents[2]
TEMP = ROOT / ".temp"
BLOCKS = json.loads((ROOT / "scripts/voxel-preview/blocks.json").read_text())
MC_ASSETS = ("https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/"
             "58d92c8cee4c7ac22d02988fb2bc3e4648af9ef2/assets/minecraft/textures/block")

GREYS = ["stone", "smooth_stone", "andesite", "polished_andesite", "tuff", "gray_concrete",
         "light_gray_concrete", "cyan_terracotta", "light_gray_terracotta", "deepslate",
         "gray_concrete_powder", "light_gray_concrete_powder", "black_concrete", "white_concrete"]
COLORS = ["red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "green_concrete",
          "cyan_concrete", "light_blue_concrete", "blue_concrete", "purple_concrete",
          "magenta_concrete", "pink_concrete", "yellow_terracotta", "red_terracotta"]

# mode "column": terrain voxelized by vertical rays (solid below up-facing surfaces), as Level 56.
# mode "shell": interiors; every voxel within `thickness` of a surface is solid, normals ignored
# (scans of rooms are single-sided sheets and their winding isn't reliable).
MODELS = {
    "gobekli": {
        "input": "gobekli_tepe.glb",
        "meters_per_unit": 0.27,       # enclosure D (~20 m across) spans ~75 units
        "mode": "column",
        "palette": ["coarse_dirt", "dirt", "rooted_dirt", "packed_mud", "mud_bricks",
                    "dripstone_block", "sandstone", "smooth_sandstone", "terracotta",
                    "white_terracotta", "light_gray_terracotta", "brown_terracotta", "gravel",
                    "tuff", "andesite", "granite"],
        "cast": 0.6,
    },
    "skatepark": {
        "input": "southbank_undercroft_skatepark.glb",
        "meters_per_unit": 0.7,        # floor to soffit ~5.1 units, ~3.6 m
        "mode": "shell",
        "palette": GREYS + COLORS,
        "chroma": 1.6,
        "cast": 1.0,
    },
    "skatepark_grey": {
        "input": "southbank_undercroft_skatepark.glb",
        "meters_per_unit": 0.7,
        "mode": "shell",
        "palette": GREYS,
        "cast": 1.0,
    },
    "hall": {
        "input": "hintze_hall.glb",
        "meters_per_unit": 1.0,
        "mode": "shell",
        "palette": ["white_terracotta", "light_gray_terracotta", "terracotta", "brown_terracotta",
                    "gray_terracotta", "cyan_terracotta", "smooth_sandstone", "sandstone",
                    "cut_sandstone", "stone_bricks", "polished_andesite", "smooth_stone",
                    "spruce_planks", "dark_oak_planks", "bone_block", "mud_bricks", "packed_mud",
                    "dripstone_block", "black_concrete", "quartz_block"],
        "cast": 0.5,
    },
}


def textures_of(block: str) -> dict:
    t = BLOCKS.get(block, block)
    return {"side": t, "top": t, "bottom": t} if isinstance(t, str) else t


def fetch_texture(name: str) -> Path:
    path = TEMP / "textures" / f"{name}.png"
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(f"{MC_ASSETS}/{name}.png", timeout=30) as r:
            path.write_bytes(r.read())
    return path


def block_color(block: str) -> np.ndarray:
    """Average opaque-pixel RGB, sides and top weighted equally (first animation frame only)."""
    t = textures_of(block)
    acc = []
    fetch_texture(t["bottom"])
    for face in ("side", "top"):
        img = np.asarray(Image.open(fetch_texture(t[face])).convert("RGBA"), float)
        img = img[: img.shape[1]]
        px = img[img[..., 3] > 0][:, :3]
        acc.append(px.mean(axis=0))
    return np.mean(acc, axis=0)


def to_lab(rgb: np.ndarray) -> np.ndarray:
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ m.T / np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]),
                     200 * (f[..., 1] - f[..., 2])], axis=-1)


def load_faces(name: str, cfg: dict):
    """World-space (glTF Y-up) verts, faces, per-face RGB; cached."""
    cache = TEMP / "scans" / f"{cfg['input']}.faces.npz"
    if cache.exists():
        d = np.load(cache)
        return d["V"], d["F"], d["C"]
    print(f"  loading {cfg['input']} (slow, cached afterwards)")
    scene = trimesh.load(TEMP / cfg["input"])
    V, F, C = [], [], []
    off = 0
    for g in scene.dump(concatenate=False):
        m = g.visual.material
        # some scans are exported unlit: the color lives in the emissive texture
        tex = getattr(m, "emissiveTexture", None) or getattr(m, "baseColorTexture", None)
        uv = getattr(g.visual, "uv", None)
        if tex is None or uv is None:
            col = np.full((len(g.faces), 3), 128.0)
        else:
            t = np.asarray(tex.convert("RGB"))
            h, w, _ = t.shape
            col = np.zeros((len(g.faces), 3))
            for a, b, c in [(1 / 3, 1 / 3, 1 / 3), (0.6, 0.2, 0.2), (0.2, 0.6, 0.2), (0.2, 0.2, 0.6)]:
                p = uv[g.faces[:, 0]] * a + uv[g.faces[:, 1]] * b + uv[g.faces[:, 2]] * c
                px = np.clip((p[:, 0] % 1) * w, 0, w - 1).astype(int)
                py = np.clip((1 - p[:, 1] % 1) * h, 0, h - 1).astype(int)
                col += t[py, px] / 4
        V.append(g.vertices)
        F.append(g.faces + off)
        C.append(col)
        off += len(g.vertices)
    V, F, C = np.concatenate(V), np.concatenate(F), np.concatenate(C).astype(np.float32)
    cache.parent.mkdir(parents=True, exist_ok=True)
    np.savez(cache, V=V, F=F, C=C)
    return V, F, C


def assign(colors: np.ndarray, area: np.ndarray, cfg: dict):
    """Nearest palette block per face, after mapping the scan's color statistics onto the
    palette's. Returns (palette indices, palette Lab)."""
    pal = cfg["palette"]
    plab = to_lab(np.array([block_color(b) for b in pal]))
    lab = to_lab(colors.astype(float))
    w = area / area.sum()
    fmean = (lab * w[:, None]).sum(axis=0)
    fstd = np.sqrt((((lab - fmean) ** 2) * w[:, None]).sum(axis=0))
    pmean, pstd = plab.mean(axis=0), plab.std(axis=0)
    out = np.empty_like(lab)
    out[:, 0] = (lab[:, 0] - fmean[0]) / max(fstd[0], 1e-6) * pstd[0] * cfg.get("spread", 0.8) + pmean[0]
    cast = cfg.get("cast", 0.5)      # 1 = remove the scan's average tint entirely
    out[:, 1:] = (lab[:, 1:] - fmean[1:]) * cfg.get("chroma", 1.0) + fmean[1:] * (1 - cast) + pmean[1:] * cast
    d = ((out[:, None, :] - plab[None, :, :]) ** 2 * np.array([1.0, 1.4, 1.4])).sum(axis=2)
    return d.argmin(axis=1), plab


def decimate(verts, faces, target, agg=7):
    m = trimesh.Trimesh(verts, faces, process=False)
    m.merge_vertices()
    m.update_faces(m.nondegenerate_faces())
    v, f = fast_simplification.simplify(m.vertices, m.faces,
                                        target_reduction=1 - target / len(m.faces), agg=agg)
    d = trimesh.Trimesh(v, f, process=True)
    d.update_faces(d.nondegenerate_faces())
    d.remove_unreferenced_vertices()
    return d


def transfer(src_centroids, src_area, src_mat, dst_verts, n_pal, k=24):
    """Area-weighted vote of the nearest original faces for each decimated vertex."""
    _, nn = cKDTree(src_centroids).query(dst_verts, k=k)
    votes = np.zeros((len(dst_verts), n_pal))
    rows = np.repeat(np.arange(len(dst_verts)), k)
    np.add.at(votes, (rows, src_mat[nn].ravel()), src_area[nn].ravel())
    return votes.argmax(axis=1).astype(np.uint8)


def write_mesh(verts, faces, vmat, palette, variants, ground_ref, island, path: Path):
    """SHM2, as documented in scripts/stonehenge/build_mesh.py write_mesh(). Every entry is
    family 1 (materials come from the nearest vertex, not the column's top hit)."""
    lo, hi, q, order, f = quantize(verts, faces)
    vmat = vmat[order]
    buf = io.BytesIO()
    buf.write(b"SHM2")
    buf.write(struct.pack("<10f", *lo, *hi, ground_ref, *island))
    buf.write(struct.pack("<B", len(palette)))
    for i, name in enumerate(palette):
        buf.write(struct.pack("<BB", 1, len(name)))
        buf.write(name.encode("ascii"))
        buf.write(struct.pack("<B", len(variants[i])))
        for idx, weight in variants[i]:
            buf.write(struct.pack("<BB", idx, weight))
    write_vertices(buf, q)
    buf.write(vmat.astype(np.uint8).tobytes())
    write_triangles(buf, f)
    data = gzip.compress(buf.getvalue(), compresslevel=9, mtime=0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return len(data)


def build(name: str, args):
    cfg = MODELS[name]
    print(f"{name}:")
    V, F, C = load_faces(name, cfg)
    V = V * cfg["meters_per_unit"]
    lo, hi = V.min(axis=0), V.max(axis=0)
    center = (lo + hi) / 2
    ground_ref = float(np.percentile(V[:, 1], 1)) if cfg["mode"] == "column" else float(lo[1])
    V = V - np.array([center[0], ground_ref, center[2]])
    radius = float(np.sqrt((V[:, 0] ** 2 + V[:, 2] ** 2).max()))
    island = (0.0, 0.0, radius if cfg["mode"] == "column" else 0.0)
    print(f"  {len(F)} tris, extent {(hi - lo).round(1)} m")

    tri = V[F]
    area = np.linalg.norm(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]), axis=1) / 2
    mat, plab = assign(C, area, cfg)
    pal = cfg["palette"]
    for i in np.argsort(-np.bincount(mat, weights=area, minlength=len(pal))):
        share = area[mat == i].sum() / area.sum() * 100
        if share >= 0.1:
            print(f"    {pal[i]:28s} {share:5.1f}%")
    # variants: each block mixed with its two nearest palette neighbors by color
    dist = ((plab[:, None] - plab[None]) ** 2).sum(axis=2)
    variants = [[(i, 70)] + [(int(j), wt) for j, wt in zip(np.argsort(dist[i])[1:3], (18, 12))]
                for i in range(len(pal))]

    out = Path(args.output) if args.output else TEMP / "meshes" / f"{name}.mesh.gz"
    budget = args.max_kb * 1024
    target, best = int(args.max_kb * 200), None
    for _ in range(6):
        dec = decimate(V, F, target)
        vmat = transfer(tri.mean(axis=1), area, mat, dec.vertices, len(pal))
        size = write_mesh(dec.vertices, dec.faces, vmat, pal, variants, 0.0, island, out)
        print(f"  {len(dec.faces)} tris -> {size / 1024:.1f} KB")
        if size <= budget and (best is None or len(dec.faces) > best[0]):
            best = (len(dec.faces), target)
        if budget * 0.97 <= size <= budget:
            break
        target = int(target * budget * 0.985 / size)
    if best and best[0] != len(dec.faces):
        dec = decimate(V, F, best[1])
        vmat = transfer(tri.mean(axis=1), area, mat, dec.vertices, len(pal))
        size = write_mesh(dec.vertices, dec.faces, vmat, pal, variants, 0.0, island, out)
    print(f"  wrote {out.relative_to(ROOT)}: {len(dec.faces)} tris, {size / 1024:.1f} KB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model", choices=[*MODELS, "all"])
    ap.add_argument("--max-kb", type=float, default=50.0, help="gzipped size budget")
    ap.add_argument("--output", help="default .temp/meshes/<model>.mesh.gz")
    args = ap.parse_args()
    for name in (MODELS if args.model == "all" else [args.model]):
        build(name, args)


if __name__ == "__main__":
    main()
