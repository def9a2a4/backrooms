# /// script
# requires-python = ">=3.11"
# dependencies = ["trimesh", "numpy", "scipy", "pillow", "fast-simplification"]
# ///
"""Build the compressed Stonehenge mesh that Level 56 voxelizes at runtime.

Source: "Stonehenge England - VR" by Graphic solutions, CC BY 4.0
https://sketchfab.com/3d-models/stonehenge-england-vr-7e7febabef2045a68755cc39c8c7c91f

Pipeline: load GLB → sample texture colors per face → classify ground/stone → pick the closest
block in each family → decimate → transfer materials to the decimated vertices → write
src/main/resources/structures/stonehenge.mesh.gz (format documented in write_mesh()).

Usage: uv run scripts/stonehenge/build_mesh.py [--input .temp/stonehenge_england_-_vr.glb]
"""

import argparse
import gzip
import io
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

ROOT = Path(__file__).resolve().parents[2]
TEMP = ROOT / ".temp"
OUT = ROOT / "src/main/resources/structures/stonehenge.mesh.gz"

# Same pinned vanilla asset mirror defCoreLib uses (commit == tag 1.21.4).
MC_ASSETS = ("https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/"
             "58d92c8cee4c7ac22d02988fb2bc3e4648af9ef2/assets/minecraft/textures/block")

# Block palette: (id, family). family 0 = ground, 1 = stone. Faces are assigned by the
# relative rules in assign_blocks() below, not by absolute color: the photogrammetry texture has
# baked golden-hour light and long stone shadows, so no vanilla block matches it directly.
PALETTE = [
    ("grass_block",       0),
    ("dirt",              0),
    ("coarse_dirt",       0),
    ("dirt_path",         0),
    ("stone",             1),
    ("andesite",          1),
    ("cobblestone",       1),
    ("mossy_cobblestone", 1),
    ("moss_block",        1),
    ("tuff",              1),
    # variant-only entries: never assigned to a vertex, only mixed in through VARIANTS
    ("rooted_dirt",       0),
    ("gravel",            0),
]
PAL = {name: i for i, (name, _) in enumerate(PALETTE)}

# Visually-near blocks mixed into each material's patches at runtime, as (block, weight).
# The voxelizer picks one per block with clumpy noise, so a "stone" region reads as mostly
# stone with drifts of andesite and cobble rather than one flat texture. Weights are relative.
VARIANTS = {
    "grass_block":       [("grass_block", 92), ("moss_block", 5), ("coarse_dirt", 3)],
    "dirt":              [("dirt", 60), ("coarse_dirt", 25), ("rooted_dirt", 15)],
    "coarse_dirt":       [("coarse_dirt", 60), ("dirt", 25), ("gravel", 15)],
    "dirt_path":         [("dirt_path", 70), ("coarse_dirt", 20), ("gravel", 10)],
    "stone":             [("stone", 60), ("andesite", 26), ("cobblestone", 9), ("tuff", 5)],
    "andesite":          [("andesite", 55), ("stone", 30), ("tuff", 10), ("cobblestone", 5)],
    "cobblestone":       [("cobblestone", 55), ("andesite", 20), ("stone", 15), ("mossy_cobblestone", 10)],
    "mossy_cobblestone": [("mossy_cobblestone", 55), ("cobblestone", 20), ("moss_block", 15), ("andesite", 10)],
    "moss_block":        [("moss_block", 70), ("mossy_cobblestone", 30)],
    "tuff":              [("tuff", 60), ("cobblestone", 20), ("andesite", 20)],
}
# Extra textures the preview needs that aren't palette entries.
PREVIEW_TEXTURES = ["grass_block_top", "grass_block_side", "dirt", "coarse_dirt", "dirt_path_top",
                    "dirt_path_side", "stone", "andesite", "cobblestone", "mossy_cobblestone",
                    "moss_block", "tuff", "rooted_dirt", "gravel",
                    "polished_diorite", "lodestone_top", "lodestone_side"]


def fetch_texture(name: str) -> Path:
    path = TEMP / "textures" / f"{name}.png"
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(f"{MC_ASSETS}/{name}.png", timeout=30) as r:
            path.write_bytes(r.read())
    return path


def fetch_preview_textures():
    for name in PREVIEW_TEXTURES:
        fetch_texture(name)


def to_lab(rgb: np.ndarray) -> np.ndarray:
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ m.T / np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]),
                     200 * (f[..., 1] - f[..., 2])], axis=-1)


def load_model(path: Path):
    """Flatten the scene to world-space meters. Returns verts, faces, per-face RGB, is_perimeter."""
    scene = trimesh.load(path)
    verts, faces, colors, perim = [], [], [], []
    offset = 0
    for g in scene.dump(concatenate=False):
        tex = np.asarray(g.visual.material.baseColorTexture.convert("RGB"))
        h, w, _ = tex.shape
        v, f, uv = g.vertices, g.faces, g.visual.uv
        acc = np.zeros((len(f), 3))
        weights = [(1 / 3, 1 / 3, 1 / 3), (0.6, 0.2, 0.2), (0.2, 0.6, 0.2), (0.2, 0.2, 0.6)]
        for a, b, c in weights:
            t = uv[f[:, 0]] * a + uv[f[:, 1]] * b + uv[f[:, 2]] * c
            px = np.clip((t[:, 0] % 1) * w, 0, w - 1).astype(int)
            py = np.clip((1 - t[:, 1] % 1) * h, 0, h - 1).astype(int)
            acc += tex[py, px]
        verts.append(v)
        faces.append(f + offset)
        colors.append(acc / len(weights))
        perim.append(np.full(len(f), "Perimeter" in g.metadata.get("name", "")))
        offset += len(v)
    return (np.concatenate(verts), np.concatenate(faces), np.concatenate(colors),
            np.concatenate(perim))


def ground_estimate(verts: np.ndarray, cell: float = 1.0, window: int = 9):
    """Per-XZ-cell ground height: low percentile per cell, then a min filter wide enough
    (window × cell meters) to see past the widest stone."""
    from scipy.ndimage import minimum_filter, generic_filter  # noqa: F401
    x0, z0 = verts[:, 0].min(), verts[:, 2].min()
    ix = ((verts[:, 0] - x0) / cell).astype(int)
    iz = ((verts[:, 2] - z0) / cell).astype(int)
    grid = np.full((ix.max() + 1, iz.max() + 1), np.inf)
    np.minimum.at(grid, (ix, iz), verts[:, 1])
    grid = minimum_filter(grid, size=window, mode="nearest")
    fill = np.nanmedian(grid[np.isfinite(grid)])
    grid[~np.isfinite(grid)] = fill

    def lookup(p):
        gx = np.clip(((p[:, 0] - x0) / cell).astype(int), 0, grid.shape[0] - 1)
        gz = np.clip(((p[:, 2] - z0) / cell).astype(int), 0, grid.shape[1] - 1)
        return grid[gx, gz]
    return lookup


def quantile_rank(x: np.ndarray) -> np.ndarray:
    """0..1 rank of each value within x."""
    r = np.empty(len(x))
    r[np.argsort(x, kind="stable")] = np.linspace(0, 1, len(x)) if len(x) > 1 else 0
    return r


def classify(verts, faces, colors, perim, args):
    """Family per face (0 ground / 1 stone), then a palette entry by relative color rules."""
    tri = verts[faces]
    centroid = tri.mean(axis=1)
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    ny = n[:, 1] / np.maximum(np.linalg.norm(n, axis=1), 1e-12)
    height = centroid[:, 1] - ground_estimate(verts)(centroid)

    lab = to_lab(colors)
    green = -lab[:, 1]            # a* < 0 is green
    chroma = np.hypot(lab[:, 1], lab[:, 2])
    stone = (height > args.stone_height) | ((height > 0.12) & (ny < 0.7)) \
        | ((height > 0.05) & (green < 2) & (lab[:, 0] > 55))
    stone &= ~perim
    fam = stone.astype(np.int8)
    return fam, assign_blocks(fam, lab, green, chroma)


def assign_blocks(fam, lab, green, chroma):
    mat = np.zeros(len(fam), np.int16)

    # Ground: mostly grass. Least-green faces become dirt; the least green AND palest become
    # path (worn visitor paths and bare chalk read pale and grey in the texture).
    g = fam == 0
    gr = quantile_rank(green[g] + 0.02 * chroma[g])
    lr = quantile_rank(lab[g, 0])
    m = np.full(g.sum(), PAL["grass_block"])
    m[gr < 0.10] = PAL["coarse_dirt"]
    m[gr < 0.05] = PAL["dirt"]
    m[(gr < 0.10) & (lr > 0.75)] = PAL["dirt_path"]
    mat[g] = m

    # Stone: lightness bands (dark → tuff/cobble, light → stone), greenest → moss.
    s = fam == 1
    lr = quantile_rank(lab[s, 0])
    gr = quantile_rank(green[s])
    m = np.full(s.sum(), PAL["andesite"])
    m[lr > 0.55] = PAL["stone"]
    m[lr < 0.30] = PAL["cobblestone"]
    m[lr < 0.12] = PAL["tuff"]
    m[gr > 0.86] = PAL["mossy_cobblestone"]
    m[(gr > 0.97) & (lr < 0.6)] = PAL["moss_block"]
    mat[s] = m
    return mat


def decimate(verts, faces, target):
    m = trimesh.Trimesh(verts, faces, process=False)
    m.merge_vertices()
    m.update_faces(m.nondegenerate_faces())
    reduction = 1 - target / len(m.faces)
    v, f = fast_simplification.simplify(m.vertices, m.faces, target_reduction=reduction, agg=args.agg)
    d = trimesh.Trimesh(v, f, process=True)
    d.update_faces(d.nondegenerate_faces())
    d.remove_unreferenced_vertices()
    return d


def transfer_materials(src_centroids, src_area, src_mat, dst_verts, n_pal, k=24):
    """Area-weighted vote of the nearest original faces for each decimated vertex."""
    tree = cKDTree(src_centroids)
    _, nn = tree.query(dst_verts, k=k)
    votes = np.zeros((len(dst_verts), n_pal))
    rows = np.repeat(np.arange(len(dst_verts)), k)
    np.add.at(votes, (rows, src_mat[nn].ravel()), src_area[nn].ravel())
    return votes.argmax(axis=1).astype(np.uint8)


def write_mesh(verts, faces, vmat, ground_ref, island, path: Path) -> int:
    """Format (little-endian, whole stream gzipped):
      "SHM2"
      f32 bboxMin[3], bboxMax[3], groundRef          meters, x east / y up / z south
      f32 islandX, islandZ, islandRadius             circle fitted to the ground patch's rim
      u8 paletteSize, then per entry: u8 family (0 ground, 1 stone), u8 len, ASCII block id,
        u8 variantCount, then per variant: u8 palette index, u8 weight
      varint vertexCount, then per vertex 3 × zigzag-varint deltas of uint16 quantized coords
      u8[vertexCount] palette index per vertex
      varint triCount, then per tri: zigzag-varint (a - prevA), varint (b - a + ...), see below
        a is the smallest index of the triangle (winding preserved by rotation),
        b and c are written as zigzag-varint (b - a) and (c - a).
    """
    lo, hi, q, order, f = quantize(verts, faces)
    vmat = vmat[order]

    buf = io.BytesIO()
    buf.write(b"SHM2")
    buf.write(struct.pack("<10f", *lo, *hi, ground_ref, *island))
    buf.write(struct.pack("<B", len(PALETTE)))
    for name, fam in PALETTE:
        buf.write(struct.pack("<BB", fam, len(name)))
        buf.write(name.encode("ascii"))
        variants = VARIANTS.get(name, [(name, 1)])
        buf.write(struct.pack("<B", len(variants)))
        for vname, weight in variants:
            buf.write(struct.pack("<BB", PAL[vname], weight))
    write_vertices(buf, q)
    buf.write(vmat.astype(np.uint8).tobytes())
    write_triangles(buf, f)
    raw = buf.getvalue()
    data = gzip.compress(raw, compresslevel=9, mtime=0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return len(raw), len(data)


def main():
    global args
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default=str(TEMP / "stonehenge_england_-_vr.glb"))
    ap.add_argument("--output", default=str(OUT))
    ap.add_argument("--target-tris", type=int, default=9900, help="starting guess for the search")
    ap.add_argument("--max-kb", type=float, default=50.0, help="gzipped size budget")
    ap.add_argument("--island-radius", type=float, default=18.75,
                    help="meters; 300 blocks at the level's 16x scale")
    ap.add_argument("--crop-margin", type=float, default=0.5,
                    help="meters kept beyond the island radius for the runtime's column warp")
    ap.add_argument("--agg", type=int, default=7, help="fast_simplification aggressiveness 0-10")
    ap.add_argument("--stone-height", type=float, default=0.3,
                    help="meters above local ground at which a face counts as stone")
    ap.add_argument("--rotate", type=float, default=0.0, help="degrees about Y, clockwise from above")
    args = ap.parse_args()

    print(f"loading {args.input}")
    verts, faces, colors, perim = load_model(Path(args.input))
    print(f"  {len(verts)} verts, {len(faces)} tris")

    # Center on the sarsen circle: bbox center of everything well above the ground.
    lookup = ground_estimate(verts)
    tall = verts[verts[:, 1] - lookup(verts) > 2.0]
    center = (tall.min(axis=0) + tall.max(axis=0)) / 2
    # Crop to the island circle (plus a margin so warped rim columns still hit the mesh).
    # The island is the circle itself: the runtime cuts it exactly at island radius.
    radius = args.island_radius
    r_all = np.hypot(verts[:, 0] - center[0], verts[:, 2] - center[2])
    keep = (r_all[faces] < radius + args.crop_margin).any(axis=1)
    faces, colors, perim = faces[keep], colors[keep], perim[keep]
    used = np.unique(faces)
    remap = np.full(len(verts), -1)
    remap[used] = np.arange(len(used))
    verts, faces = verts[used], remap[faces]
    # ground reference: median ground height in the outer 2 m of the island
    rr = np.hypot(verts[:, 0] - center[0], verts[:, 2] - center[2])
    lookup = ground_estimate(verts)
    ring = (rr > radius - 2) & (rr < radius) & (verts[:, 1] - lookup(verts) < 0.2)
    ground_ref = float(np.median(verts[ring, 1]))
    verts = verts - np.array([center[0], ground_ref, center[2]])
    island = (0.0, 0.0, float(radius))
    if args.rotate:
        t = np.radians(args.rotate)
        r = np.array([[np.cos(t), 0, np.sin(t)], [0, 1, 0], [-np.sin(t), 0, np.cos(t)]])
        verts = verts @ r.T
    print(f"  cropped to r={radius} m (+{args.crop_margin} m): {len(verts)} verts, {len(faces)} tris")
    print(f"  center {center[[0, 2]].round(2)}, groundRef {ground_ref:.2f}, "
          f"extent {verts.min(axis=0).round(1)} .. {verts.max(axis=0).round(1)}")

    fetch_preview_textures()
    fam, mat = classify(verts, faces, colors, perim, args)
    print(f"  stone faces {np.mean(fam == 1) * 100:.1f}%")
    for i, (name, _) in enumerate(PALETTE):
        print(f"  {name:18s} {np.mean(mat == i) * 100:5.1f}% of faces")

    tri = verts[faces]
    area = np.linalg.norm(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]), axis=1) / 2
    # Search the triangle budget that lands just under --max-kb (size is ~linear in tris).
    budget = args.max_kb * 1024
    target, best = args.target_tris, None
    for _ in range(6):
        dec = decimate(verts, faces, target)
        vmat = transfer_materials(tri.mean(axis=1), area, mat, dec.vertices, len(PALETTE))
        raw, packed = write_mesh(dec.vertices, dec.faces, vmat, 0.0, island, Path(args.output))
        print(f"  {len(dec.faces)} tris / {len(dec.vertices)} verts -> {packed / 1024:.1f} KB")
        if packed <= budget and (best is None or len(dec.faces) > best[0]):
            best = (len(dec.faces), target)
        if budget * 0.97 <= packed <= budget:
            break
        target = int(target * budget * 0.985 / packed)
    if best and best[0] != len(dec.faces):
        dec = decimate(verts, faces, best[1])
        vmat = transfer_materials(tri.mean(axis=1), area, mat, dec.vertices, len(PALETTE))
        raw, packed = write_mesh(dec.vertices, dec.faces, vmat, 0.0, island, Path(args.output))
    print(f"wrote {args.output}: {len(dec.faces)} tris, {raw} B raw, "
          f"{packed} B gzipped ({packed / 1024:.1f} KB)")


if __name__ == "__main__":
    main()
