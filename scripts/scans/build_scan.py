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
import re
import functools
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
from meshlib import quantize, varint, write_triangles, write_vertices  # noqa: E402

Image.MAX_IMAGE_PIXELS = None
ROOT = Path(__file__).resolve().parents[2]
TEMP = ROOT / ".temp"
BLOCKS = json.loads((ROOT / "scripts/voxel-preview/blocks.json").read_text())
MC_ASSETS = ("https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/"
             "58d92c8cee4c7ac22d02988fb2bc3e4648af9ef2/assets/minecraft/textures/block")

# IronSource's "concrete" material list (assets/mc_blocks.toml), plus black for soot and stains.
GREYS = ["light_gray_concrete", "gray_concrete", "smooth_stone", "stone", "andesite",
         "polished_andesite", "tuff", "light_gray_terracotta", "cyan_terracotta", "white_concrete",
         "black_concrete"]
COLORS = ["red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "green_concrete",
          "cyan_concrete", "light_blue_concrete", "blue_concrete", "purple_concrete",
          "magenta_concrete", "pink_concrete", "yellow_terracotta", "red_terracotta"]

# Painted and colored stone, for palettes that must stay grey (white, black, grey and the
# near-neutral cyan terracotta stay in).
COLORED = (r"(orange|magenta|light_blue|yellow|lime|pink|purple|blue|brown|green|red)_(concrete|terracotta)$"
           r"|^terracotta$|red_sandstone|sandstone|nether|netherrack|end_stone|purpur|prismarine|"
           r"resin|^bricks$|mud_bricks|packed_mud|granite|dripstone|cyan_concrete")

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
                    "tuff", "andesite", "granite", "calcite"],
        "penalty": {"calcite": 0.01},     # IronSource: calcite only for very white
        "full": {"lists": ["stone"]},
    },
    "skatepark": {
        "input": "southbank_undercroft_skatepark.glb",
        "meters_per_unit": 0.7,        # floor to soffit ~5.1 units, ~3.6 m
        "mode": "shell",
        "palette": GREYS + COLORS,
        "chroma": 1.6,
        "cast": 1.0,
        "deadzone": 0.04,
        "full": {"lists": ["stone"]},
    },
    "skatepark_grey": {
        "input": "southbank_undercroft_skatepark.glb",
        "meters_per_unit": 0.7,
        "mode": "shell",
        "palette": GREYS,
        "cast": 1.0,
        "deadzone": 0.04,
        "full": {"lists": ["stone"], "exclude": COLORED},
    },
    "hall": {
        "input": "hintze_hall.glb",
        "meters_per_unit": 1.0,
        "mode": "shell",
        # IronSource's building list, minus brick and metal (Waterhouse's terracotta hall)
        "palette": ["white_terracotta", "smooth_sandstone", "calcite", "light_gray_terracotta",
                    "terracotta", "mud_bricks", "packed_mud", "brown_terracotta", "gray_terracotta",
                    "cyan_terracotta", "stone", "smooth_stone", "andesite", "polished_andesite",
                    "tuff", "stone_bricks", "polished_deepslate", "spruce_planks",
                    "dark_oak_planks", "bone_block"],
        "penalty": {"calcite": 0.01},
        "full": {"lists": ["stone"]},
        # the side galleries' glazing is a transparent material; the roof lights and windows are
        # scanned as blown-out white
        "glass": {"materials": ["lambert4SG"], "white_min": 200, "max_spread": 30},
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


def srgb_to_linear(c: np.ndarray) -> np.ndarray:
    c = c / 255.0
    return np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)


def oklab(lin: np.ndarray) -> np.ndarray:
    """Linear sRGB (0..1) -> OKLab."""
    m1 = np.array([[0.4122214708, 0.5363325363, 0.0514459929],
                   [0.2119034982, 0.6806995451, 0.1073969566],
                   [0.0883024619, 0.2817188376, 0.6299787005]])
    m2 = np.array([[0.2104542553, 0.7936177850, -0.0040720468],
                   [1.9779984951, -2.4285922050, 0.4505937099],
                   [0.0259040371, 0.7827717662, -0.8086757660]])
    return np.cbrt(lin @ m1.T) @ m2.T


@functools.cache
def block_color(block: str) -> dict:
    """OKLab per face (side, top, bottom): alpha-weighted mean of the texture in linear light
    (first frame of animated strips). After IronSource's ironsource-convert palette.rs."""
    out = {}
    for face, name in textures_of(block).items():
        img = np.asarray(Image.open(fetch_texture(name)).convert("RGBA"), float)
        img = img[: img.shape[1]]
        a = img[..., 3:] / 255
        out[face] = oklab((srgb_to_linear(img[..., :3]) * a).sum(axis=(0, 1)) / a.sum())
    return out


def face_materials(cfg: dict):
    """Per-face glTF material name, in load_faces() order (cached separately: only models with a
    `glass` rule need it). Returns (names, per-face index into names)."""
    cache = TEMP / "scans" / f"{cfg['input']}.materials.npz"
    if cache.exists():
        d = np.load(cache, allow_pickle=False)
        return list(d["names"]), d["index"]
    print(f"  reading materials of {cfg['input']}")
    names, index = [], []
    for g in trimesh.load(TEMP / cfg["input"]).dump(concatenate=False):
        n = getattr(g.visual.material, "name", "") or ""
        if n not in names:
            names.append(n)
        index.append(np.full(len(g.faces), names.index(n), np.int16))
    index = np.concatenate(index)
    np.savez(cache, names=np.array(names), index=index)
    return names, index


def glass_faces(cfg: dict, colors: np.ndarray) -> np.ndarray | None:
    """Faces that are glass, per the model's `glass` rule: listed (transparent) glTF materials,
    and blown-out near-white texels, which in a photo scan are daylight through a window."""
    rule = cfg.get("glass")
    if not rule:
        return None
    names, index = face_materials(cfg)
    glass = np.isin(index, [i for i, n in enumerate(names) if n in rule.get("materials", [])])
    if "white_min" in rule:
        c = colors.astype(float)
        glass |= (c.min(axis=1) >= rule["white_min"]) & (np.ptp(c, axis=1) <= rule.get("max_spread", 30))
    return glass


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
    arrays = {}                  # one RGB array per distinct texture, not per primitive
    for g in scene.dump(concatenate=False):
        m = g.visual.material
        # some scans are exported unlit: the color lives in the emissive texture
        tex = getattr(m, "emissiveTexture", None) or getattr(m, "baseColorTexture", None)
        uv = getattr(g.visual, "uv", None)
        if tex is None or uv is None:
            col = np.full((len(g.faces), 3), 128.0)
        else:
            if id(tex) not in arrays:
                arrays[id(tex)] = (tex, np.asarray(tex.convert("RGB")))
            t = arrays[id(tex)][1]
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


def assign(lab: np.ndarray, area: np.ndarray, up: np.ndarray, cfg: dict, contrast=None):
    """Nearest palette block per face in OKLab, IronSource-style: the block's top and side
    colors mixed by how much the face looks up (or down: bottom), lightness counting a quarter
    (baked light and shadow make it the least reliable channel). The scan's lightness is first
    mapped onto the palette's range, and `cast` of its average tint removed (white balance).
    With `contrast` (std of OKLab L inside each sample), blocks whose texture is as busy as the
    surface win: + PATTERN_WEIGHT * (contrast - block contrast)^2 (IronSource's pattern term,
    contrast only). Returns (palette indices, palette OKLab as seen from the side).""" 
    pal = cfg["palette"]
    faces = [block_color(b) for b in pal]
    side = np.array([f["side"] for f in faces])
    top = np.array([f["top"] for f in faces])
    bottom = np.array([f["bottom"] for f in faces])
    w = area / area.sum()
    fmean = (lab * w[:, None]).sum(axis=0)
    fstd = np.sqrt((((lab - fmean) ** 2) * w[:, None]).sum(axis=0))
    pl = (side + top) / 2
    pmean, pstd = pl.mean(axis=0), pl.std(axis=0)
    q = np.empty_like(lab)
    q[:, 0] = (lab[:, 0] - fmean[0]) / max(fstd[0], 1e-6) * pstd[0] * cfg.get("spread", 0.8) + pmean[0]
    cast = cfg.get("cast", 0.0)      # 1 = remove the scan's average tint entirely (to neutral)
    q[:, 1:] = (lab[:, 1:] - fmean[1:]) * cfg.get("chroma", 1.0) + fmean[1:] * (1 - cast)
    # Grey scans: color noise around neutral (~0.02) outweighs the differences between grey
    # blocks, so chroma below `deadzone` is squashed to neutral; stronger colors (paint) keep
    # what's left above it.
    dz = cfg.get("deadzone", 0.0)
    if dz:
        c = np.hypot(q[:, 1], q[:, 2])
        q[:, 1:] *= (np.maximum(c - dz, 0) / np.maximum(c, 1e-9))[:, None]
    f = np.clip(up, 0, 1)[:, None, None]
    g = np.clip(-up, 0, 1)[:, None, None]
    best = np.empty(len(q), int)
    penalty = np.array([cfg.get("penalty", {}).get(b, 0.0) for b in pal])
    bcon = np.array([block_contrast(b) for b in pal]) if contrast is not None else None
    for s0 in range(0, len(q), 200_000):      # chunked: faces x palette x 3 floats
        sl = slice(s0, s0 + 200_000)
        cand = side[None] * (1 - f[sl] - g[sl]) + top[None] * f[sl] + bottom[None] * g[sl]
        d = q[sl, None, :] - cand
        dist = 0.25 * d[..., 0] ** 2 + d[..., 1] ** 2 + d[..., 2] ** 2 + penalty[None]
        if bcon is not None:
            dist += PATTERN_WEIGHT * (contrast[sl, None] - bcon[None]) ** 2
        best[sl] = dist.argmin(axis=1)
    return best, side


PATTERN_WEIGHT = 0.5    # IronSource's weight for texture contrast/grain differences
FULL_LISTS = json.loads((ROOT / "scripts/scans/full_palette.json").read_text())


def full_palette(cfg: dict) -> list:
    """The model's slice of the full list: MODELS[...]["full"] = {"lists": [...], "exclude":
    regex}: which IronSource lists, minus blocks that don't belong in this place."""
    spec = cfg["full"]
    ids = [b for lst in spec["lists"] for b in FULL_LISTS[lst]]
    ex = re.compile(spec.get("exclude", r"^$"))
    return list(dict.fromkeys(b for b in ids if not ex.search(b)))


@functools.cache
def block_contrast(block: str) -> float:
    """Std of OKLab L over the block's side texture at 16x16: how busy it looks."""
    t = textures_of(block)["side"]
    img = np.asarray(Image.open(fetch_texture(t)).convert("RGBA"), float)
    img = img[: img.shape[1]]
    px = img[img[..., 3] > 0][:, :3]
    return float(oklab(srgb_to_linear(px))[:, 0].std())


def match_cells(cen, area, lab, up, cfg, cell, smooth, forced=None, forced_mat=-1):
    """Blocks per grid cell instead of per face (style "full"): each cell's area-weighted mean
    color (blended with its 26 neighbors by `smooth`, light denoising), the spread of lightness
    inside it as surface contrast, then assign() with the pattern term.
    Returns (grid tuple for write_grid, cell centers in meters, cell materials, palette OKLab)."""
    origin = cen.min(axis=0)
    q = np.floor((cen - origin) / cell).astype(np.int64)
    dims = q.max(axis=0) + 1
    lin = (q[:, 0] * dims[2] + q[:, 2]) * dims[1] + q[:, 1]
    keys, inv = np.unique(lin, return_inverse=True)
    wsum = np.bincount(inv, weights=area)
    mean = np.stack([np.bincount(inv, weights=area * lab[:, c]) / wsum for c in range(3)], axis=1)
    l2 = np.bincount(inv, weights=area * lab[:, 0] ** 2) / wsum
    contrast = np.sqrt(np.maximum(l2 - mean[:, 0] ** 2, 0))
    upm = np.bincount(inv, weights=area * up) / wsum
    cx = keys // (dims[1] * dims[2])
    cz = (keys // dims[1]) % dims[2]
    cy = keys % dims[1]
    if smooth > 0:
        acc = mean * wsum[:, None]
        tot = wsum.copy()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx == dy == dz == 0:
                        continue
                    nx, ny, nz = cx + dx, cy + dy, cz + dz
                    ok = (nx >= 0) & (ny >= 0) & (nz >= 0) & (nx < dims[0]) & (ny < dims[1]) & (nz < dims[2])
                    nk = (nx * dims[2] + nz) * dims[1] + ny
                    j = np.clip(np.searchsorted(keys, nk), 0, len(keys) - 1)
                    ok &= keys[j] == nk
                    wt = smooth / np.sqrt(dx * dx + dy * dy + dz * dz) * wsum[j] * ok
                    acc += mean[j] * wt[:, None]
                    tot += wt
        mean = acc / tot[:, None]
    mat, plab = assign(mean, wsum, upm, cfg, contrast)
    if forced is not None:
        # cells mostly made of forced faces (glass) take the forced block
        frac = np.bincount(inv, weights=area * forced) / wsum
        mat[frac > 0.5] = forced_mat
    centers = origin + (np.stack([cx, cy, cz], axis=1) + 0.5) * cell
    return (origin, dims, keys, mat.astype(np.uint8)), centers, mat, plab


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


def surface_grid(centroids, area, mat, n_pal, cell):
    """Material grid of the full-resolution surface: one palette index per `cell`-meter cube
    the original surface passes through (area-weighted vote of the faces in it). A 3D texture
    that survives decimation: the runtime looks blocks up by position instead of per vertex.
    Returns (origin, dims, linear cell indices sorted, materials)."""
    origin = centroids.min(axis=0)
    q = np.floor((centroids - origin) / cell).astype(np.int64)
    dims = q.max(axis=0) + 1
    lin = (q[:, 0] * dims[2] + q[:, 2]) * dims[1] + q[:, 1]      # y fastest, then z, then x
    keys, inv = np.unique(lin, return_inverse=True)
    votes = np.bincount(inv * n_pal + mat, weights=area, minlength=len(keys) * n_pal)
    return origin, dims, keys, votes.reshape(len(keys), n_pal).argmax(axis=1).astype(np.uint8)


def write_grid(buf: io.BytesIO, grid, cell):
    """Optional trailing section (readers that stop after the triangles ignore it):
      "TEX1", f32 cell (meters), f32 origin[3], u16 dims[3] (x, y, z)
      varint count, then per cell zigzag-free varint delta of the linear index
        ((x * dimZ + z) * dimY + y, ascending), then u8[count] palette index per cell
    """
    origin, dims, keys, mats = grid
    buf.write(b"TEX1")
    buf.write(struct.pack("<4f3H", cell, *origin, *dims))
    varint(buf, len(keys))
    prev = 0
    for k in keys.tolist():
        varint(buf, k - prev)
        prev = k
    buf.write(mats.tobytes())


def write_mesh(verts, faces, vmat, palette, variants, ground_ref, island, path: Path,
               grid=None, cell=0.0):
    """SHM2, as documented in scripts/stonehenge/build_mesh.py write_mesh(), plus the optional
    TEX1 surface grid (write_grid). Every entry is family 1 (materials come from the grid or
    the nearest vertex, not the column's top hit)."""
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
    if grid is not None:
        write_grid(buf, grid, cell)
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
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    area = np.linalg.norm(n, axis=1) / 2
    up = n[:, 1] / np.maximum(2 * area, 1e-12)
    if cfg["mode"] == "shell":
        up = np.abs(up)        # floors and ceilings alike read as tops; winding isn't reliable
    lab = oklab(srgb_to_linear(C.astype(float)))
    glass = glass_faces(cfg, C)
    if args.style == "full":
        pal_ids = full_palette(cfg)
        if glass is not None:
            # glass is never picked by color, only by the glass rule
            pal_ids = pal_ids + ["glass"]
            cfg = dict(cfg, penalty={**cfg.get("penalty", {}), "glass": 1e9})
            print(f"  glass: {area[glass].sum() / area.sum() * 100:.1f}% of the surface")
        cfg = dict(cfg, palette=pal_ids)
        grid, cell_centers, cell_mat, plab = match_cells(
            tri.mean(axis=1), area, lab, up, cfg, args.tex_cell, args.smooth,
            forced=glass, forced_mat=len(pal_ids) - 1 if glass is not None else -1)
        # faces take their cell's block, so the per-vertex fallback agrees with the grid
        _, near = cKDTree(cell_centers).query(tri.mean(axis=1))
        mat = cell_mat[near]
    else:
        mat, plab = assign(lab, area, up, cfg)
    pal = cfg["palette"]
    for i in np.argsort(-np.bincount(mat, weights=area, minlength=len(pal))):
        share = area[mat == i].sum() / area.sum() * 100
        if share >= 0.1:
            print(f"    {pal[i]:28s} {share:5.1f}%")
    # variants: each block mixed with its two nearest palette neighbors by color
    dd = plab[:, None] - plab[None]
    dist = 0.25 * dd[..., 0] ** 2 + dd[..., 1] ** 2 + dd[..., 2] ** 2
    clear = [i for i, b in enumerate(pal) if b == "glass"]
    dist[:, clear] = np.inf       # nothing turns into glass by mixing
    variants = [[(i, 70)] + [(int(j), wt) for j, wt in zip(np.argsort(dist[i])[1:3], (18, 12))]
                if i not in clear else [(i, 1)] for i in range(len(pal))]

    out = Path(args.output).resolve() if args.output else TEMP / "meshes" / f"{name}.mesh.gz"
    budget = args.max_kb * 1024
    target, best = int(args.max_kb * 220), None      # ~220 tris per gzipped KB
    for _ in range(6):
        dec = decimate(V, F, target)
        vmat = transfer(tri.mean(axis=1), area, mat, dec.vertices, len(pal))
        size = write_mesh(dec.vertices, dec.faces, vmat, pal, variants, 0.0, island, out)
        print(f"  {len(dec.faces)} tris -> {size / 1024:.1f} KB")
        if size <= budget * (1 + args.tolerance / 2) and (best is None or len(dec.faces) > best[0]):
            best = (len(dec.faces), target)
        if budget * (1 - args.tolerance) <= size <= budget * (1 + args.tolerance / 2):
            break
        target = int(target * budget * 0.985 / size)
    if best and best[0] != len(dec.faces):
        dec = decimate(V, F, best[1])
        vmat = transfer(tri.mean(axis=1), area, mat, dec.vertices, len(pal))
        size = write_mesh(dec.vertices, dec.faces, vmat, pal, variants, 0.0, island, out)
    geo = size
    if args.tex_cell > 0:
        if args.style != "full":
            grid = surface_grid(tri.mean(axis=1), area, mat, len(pal), args.tex_cell)
        size = write_mesh(dec.vertices, dec.faces, vmat, pal, variants, 0.0, island, out,
                          grid, args.tex_cell)
        print(f"  surface grid: {len(grid[2])} cells of {args.tex_cell} m, "
              f"{(size - geo) / 1024:.1f} KB")
    print(f"  wrote {out.relative_to(ROOT)}: {len(dec.faces)} tris, {size / 1024:.1f} KB "
          f"({geo / 1024:.1f} KB geometry)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model", choices=[*MODELS, "all"])
    ap.add_argument("--max-kb", type=float, default=50.0, help="gzipped geometry size budget")
    ap.add_argument("--tex-cell", type=float, default=0.25,
                    help="surface material grid cell in meters (0: none, per-vertex only)")
    ap.add_argument("--tolerance", type=float, default=0.03,
                    help="accept sizes within this fraction of --max-kb (fewer decimation passes)")
    ap.add_argument("--style", choices=["palette", "full"], default="palette",
                    help="palette: the model's own block list, per face; full: IronSource's full "
                         "block list matched per grid cell with light smoothing and a texture "
                         "contrast term")
    ap.add_argument("--smooth", type=float, default=0.05,
                    help="--style full: weight of each neighbor cell in the color smoothing "
                         "(26 neighbors: 0.05 gives them about as much weight as the cell)")
    ap.add_argument("--output", help="default .temp/meshes/<model>.mesh.gz")
    args = ap.parse_args()
    for name in (MODELS if args.model == "all" else [args.model]):
        build(name, args)


if __name__ == "__main__":
    main()
