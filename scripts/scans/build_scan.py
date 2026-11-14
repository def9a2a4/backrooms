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
        # sun-bleached hilltop, shadowed pits; worn floors paler than the cut faces
        "tune": {"height": 0.15, "slope": 0.1},
        # an excavation: no masonry (bricks and tiles of any kind)
        "full": {"lists": ["stone"], "exclude": r"brick|tiles"},
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
        # picked in the voxel preview
        "tune": {"blur": 0, "spread": 1.6, "pattern": 0.5, "majority": 0, "height": 0.65,
                 "slope": -0.05, "brightness": -0.2},
        # the side galleries' glazing is a transparent material; the roof lights and windows are
        # scanned as blown-out white
        # (clerestory and roof lights start ~14 m up; min_y is in meters above the lowest point)
        "glass": {"materials": ["lambert4SG"], "white_min": 165, "max_spread": 35, "min_y": 13.5,
                  "outer": 1.5, "cell_fraction": 0.35,
                  # the blue whale's pale spine hangs above min_y
                  "keep": [[-6, 5, -6, 6, 15, 21]],
                  # the ground-floor display cases (plinth top to frame top), modeled as flat
                  # panels with thin corner posts
                  "cases": [[-14.17, 1.15, -3.37, -10.21, 4.59, -1.63],
                            [-13.78, 0.76, 15.5, -11.37, 3.3, 17.18],
                            [10.42, 1.19, -9.43, 14.82, 2.89, -8.09],
                            [11.76, 1.07, 9.57, 14.1, 3.86, 10.69],
                            [11.71, 1.11, 15.69, 14.14, 3.57, 17.13],
                            [11.85, 1.04, -3.51, 13.87, 3.27, -1.44]]},
        # the blue whale is a skeleton: all bone, whatever the scan's colors say
        # (the jaw dips to 4.5 m; past z 18.5 is the staircase landing)
        "force": [{"block": "bone_block", "boxes": [[-6, 4.4, -6, 6, 15, 18.5]]}],
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


def glass_faces(cfg: dict, colors: np.ndarray, centroids: np.ndarray) -> np.ndarray | None:
    """Faces that are glass, per the model's `glass` rule: listed (transparent) glTF materials,
    pale grey-white texels above `min_y` meters or on the outer skin, which in a photo scan are
    daylight through a window (elsewhere, pale faces are lit stone and displays), except inside
    the `keep` boxes, and the skins of the `cases` boxes. Boxes are [x0, y0, z0, x1, y1, z1] in
    meters, x/z from the model's center, y above its lowest point."""
    rule = cfg.get("glass")
    if not rule:
        return None
    names, index = face_materials(cfg)
    glass = np.isin(index, [i for i, n in enumerate(names) if n in rule.get("materials", [])])
    if "white_min" in rule:
        c = colors.astype(float)
        pale = (c.min(axis=1) >= rule["white_min"]) & (np.ptp(c, axis=1) <= rule.get("max_spread", 30))
        high = centroids[:, 1] >= rule.get("min_y", -np.inf)
        if "outer" in rule:
            # the outside skin (within `outer` m of the footprint's edge) has windows at any height
            lo, hi = centroids.min(axis=0), centroids.max(axis=0)
            m = rule["outer"]
            high |= ((centroids[:, 0] < lo[0] + m) | (centroids[:, 0] > hi[0] - m)
                     | (centroids[:, 2] < lo[2] + m) | (centroids[:, 2] > hi[2] - m))
        for x0, y0, z0, x1, y1, z1 in rule.get("keep", []):
            high &= ~in_box(centroids, (x0, y0, z0), (x1, y1, z1))
        glass |= pale & high
    # display cases: the faces on a case's sides and top (within `case_skin` m of them), so what's
    # inside stays as it is
    sk = rule.get("case_skin", 0.2)
    for x0, y0, z0, x1, y1, z1 in rule.get("cases", []):
        glass |= (in_box(centroids, (x0 - sk, y0, z0 - sk), (x1 + sk, y1 + sk, z1 + sk))
                  & ~in_box(centroids, (x0 + sk, y0, z0 + sk), (x1 - sk, y1 - sk, z1 - sk)))
    return glass


def in_box(p: np.ndarray, lo, hi) -> np.ndarray:
    return np.all((p >= np.array(lo)) & (p < np.array(hi)), axis=1)


def case_boxes(cfg: dict) -> list:
    """The display cases' boxes grown by the skin: the decimated mesh's vertices in them fall back
    to glass (a case's panels are a few big triangles, mostly too far from the surface grid's
    cells for it to reach)."""
    rule = cfg.get("glass") or {}
    sk = rule.get("case_skin", 0.2)
    # (the panels start inside the plinth, below y0; the plinth's own surface keeps its blocks
    # from the grid, which wins over the vertex fallback)
    return [[x0 - sk, y0 - 0.8, z0 - sk, x1 + sk, y1 + sk, z1 + sk]
            for x0, y0, z0, x1, y1, z1 in rule.get("cases", [])]


def case_glass(cfg: dict, verts: np.ndarray, vmat: np.ndarray, glass_mat: int) -> np.ndarray:
    vmat = vmat.copy()
    for b in case_boxes(cfg):
        vmat[in_box(verts, b[:3], b[3:])] = glass_mat
    return vmat


def forced_blocks(cfg: dict, pal: list) -> list:
    """The model's `force` rules as [(palette index, box)]: everything in a box (cells and the
    decimated mesh's vertices) is that block. Boxes as for the glass rule."""
    return [(pal.index(r["block"]), b) for r in cfg.get("force", []) if r["block"] in pal
            for b in r["boxes"]]


def force_mats(cfg: dict, pal: list, points: np.ndarray, mat: np.ndarray) -> np.ndarray:
    mat = mat.copy()
    for m, b in forced_blocks(cfg, pal):
        mat[in_box(points, b[:3], b[3:])] = m
    return mat


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


def assign(lab: np.ndarray, area: np.ndarray, up: np.ndarray, cfg: dict, contrast=None, shade=0.0):
    """Nearest palette block per face in OKLab, IronSource-style: the block's top and side
    colors mixed by how much the face looks up (or down: bottom), lightness counting a quarter
    (baked light and shadow make it the least reliable channel). The scan's lightness is first
    mapped onto the palette's range, and `cast` of its average tint removed (white balance).
    With `contrast` (std of OKLab L inside each sample), blocks whose texture is as busy as the
    surface win: + tune pattern * (contrast - block contrast)^2 (IronSource's pattern term,
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
    # (shading and brightness come after the mapping: before it, the mapping would rescale them
    # away along with the scan's own lightness spread)
    q[:, 0] = ((lab[:, 0] - fmean[0]) / max(fstd[0], 1e-6) * pstd[0] * tune(cfg)["spread"] + pmean[0]
               + shade + tune(cfg)["brightness"] * BRIGHT)
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
            dist += tune(cfg)["pattern"] * (contrast[sl, None] - bcon[None]) ** 2
        best[sl] = dist.argmin(axis=1)
    return best, side


# Matching knobs, per model in MODELS[...]["tune"] (pick them with the sliders in
# scripts/voxel-preview, which redoes this matching live from the --cells-only export):
#   blur      color blur radius in blocks before matching, 0..16 (photogrammetry texture is noisy
#             at block scale: baked shadows, dirt, sensor noise)
#   spread    contrast: how much of the palette's lightness range the scan's lightness is
#             stretched over (lower = calmer, fewer jumps between light and dark blocks)
#   pattern   weight of matching texture busyness (IronSource uses 0.5)
#   majority  3x3x3 majority-filter passes after matching (removes lone blocks)
#   height    height shading, -1..1: lighter higher up (negative: darker)
#   slope     slope shading, -1..1: flat ground lighter than steep faces (negative: the
#             reverse). Each adds up to +-SHADE/2 to OKLab lightness, after the contrast mapping.
#   brightness  -1..1: lightness shift after the contrast mapping, up to +-BRIGHT (OKLab L)
TUNE_DEFAULTS = {"blur": 2, "spread": 0.8, "pattern": 0.5, "majority": 1, "height": 0.0, "slope": 0.0,
                 "brightness": 0.0}
SHADE = 0.6
BRIGHT = 0.15


def tune(cfg: dict) -> dict:
    return {**TUNE_DEFAULTS, **cfg.get("tune", {})}
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


def match_cells(cen, area, lab, up, cfg, cell, forced=None, forced_mat=-1, forced_fraction=0.5,
                export=None):
    """Blocks per grid cell instead of per face (style "full"): each cell's area-weighted mean
    color, blurred over about `blur` cells around it (photogrammetry texture is noisy at block
    scale: baked shadows, dirt, sensor noise), the spread of lightness inside it as surface
    contrast, then assign() with the pattern term, then `majority` passes of a 3×3×3 majority
    filter that flips lone blocks to what surrounds them.
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
    frac = np.bincount(inv, weights=area * forced) / wsum if forced is not None else np.zeros(len(keys))
    if export is not None:
        export_cells(export, cfg, cell, origin, dims, keys, mean, contrast, upm, wsum, frac,
                     forced_mat, forced_fraction)
        return None
    blur, majority = tune(cfg)["blur"], tune(cfg)["majority"]
    cx = keys // (dims[1] * dims[2])
    cz = (keys // dims[1]) % dims[2]
    cy = keys % dims[1]
    # the 26 neighbors of every cell: index (j) and whether it exists (ok)
    neighbors = []
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
                neighbors.append((j, ok, 1 / np.sqrt(dx * dx + dy * dy + dz * dz)))
    if blur > 0:
        mean = blur_cells(np.stack([cx, cy, cz], axis=1), mean, wsum, blur)
    t = tune(cfg)
    # shading overlay: height across the model's full height, slope from how much the surface
    # faces up (0 wall, 1 floor); added after the contrast mapping (see assign)
    shade = (t["height"] * (cy / max(dims[1] - 1, 1) - 0.5)
             + t["slope"] * (np.clip(upm, 0, 1) - 0.5)) * SHADE
    mat, plab = assign(mean, wsum, upm, cfg, contrast, shade)
    for _ in range(majority):
        # each cell takes the block most of its 3×3×3 neighborhood has (missing neighbors vote
        # for the cell itself, and ties keep it)
        votes = np.stack([mat] + [np.where(ok, mat[j], mat) for j, ok, _ in neighbors])
        counts = np.stack([(votes == votes[k]).sum(axis=0, dtype=np.int16) for k in range(len(votes))])
        mat = votes[counts.argmax(axis=0), np.arange(len(mat))]
    if forced is not None:
        # cells mostly made of forced faces (glass) take the forced block
        mat[frac >= forced_fraction] = forced_mat
    centers = origin + (np.stack([cx, cy, cz], axis=1) + 0.5) * cell
    return (origin, dims, keys, mat.astype(np.uint8)), centers, mat, plab


def blur_cells(c: np.ndarray, mean: np.ndarray, w: np.ndarray, radius: float) -> np.ndarray:
    """Low-pass the cells' colors with a blur of about `radius` cells (the kernel's std): average
    them into bricks of 2*radius cells (weighted by surface area), then give each cell the
    trilinear mix of the 8 brick averages around it, each also weighted by how much surface it
    holds. Cost doesn't grow with the radius. tune-worker.js blurCells() is the same."""
    S = max(int(round(2 * radius)), 1)
    b = c // S
    bdims = b.max(axis=0) + 2
    bkey = lambda q: (q[:, 0] * bdims[2] + q[:, 2]) * bdims[1] + q[:, 1]
    keys, inv = np.unique(bkey(b), return_inverse=True)
    bw = np.bincount(inv, weights=w)
    bm = np.stack([np.bincount(inv, weights=w * mean[:, k]) for k in range(3)], axis=1) / bw[:, None]
    u = (c + 0.5) / S - 0.5
    i0 = np.floor(u).astype(np.int64)
    f = u - i0
    acc = np.zeros_like(mean)
    tot = np.zeros(len(c))
    for o in np.array([(x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)]):
        q = i0 + o
        t = np.prod(np.where(o == 1, f, 1 - f), axis=1)
        k = bkey(q)
        j = np.clip(np.searchsorted(keys, k), 0, len(keys) - 1)
        ok = np.all(q >= 0, axis=1) & (keys[j] == k)
        wt = t * bw[j] * ok
        acc += bm[j] * wt[:, None]
        tot += wt
    return acc / tot[:, None]


def export_cells(path, cfg, cell, origin, dims, keys, mean, contrast, up, wsum, frac,
                 forced_mat, forced_fraction):
    """Per-cell inputs to the matching, for the preview's tuning sliders (tune.js redoes blur,
    assign() and the majority filter from these). Format, little-endian, gzipped:
      "CEL1", u32 header length, JSON header (cell, origin, dims, count, palette with OKLab per
      face, texture contrast and penalty, the fixed knobs and the tune defaults), padding to 4
      bytes, then per cell: u32 key[count] (as in TEX1), f32 L, a, b, contrast, up, area, glass[]
    """
    pal = cfg["palette"]
    header = {
        "cell": cell, "origin": [float(v) for v in origin], "dims": [int(v) for v in dims],
        "count": int(len(keys)),
        "palette": [{"id": b, **{f: [float(x) for x in c] for f, c in block_color(b).items()},
                     "contrast": block_contrast(b), "penalty": cfg.get("penalty", {}).get(b, 0.0)}
                    for b in pal],
        "fixed": {"cast": cfg.get("cast", 0.0), "chroma": cfg.get("chroma", 1.0),
                  "deadzone": cfg.get("deadzone", 0.0), "forced": int(forced_mat),
                  "forced_fraction": forced_fraction,
                  "case_boxes": case_boxes(cfg) if forced_mat >= 0 else [],
                  "force": [{"mat": m, "box": b} for m, b in forced_blocks(cfg, pal)]},
        "tune": tune(cfg),
    }
    h = json.dumps(header).encode()
    buf = io.BytesIO()
    buf.write(b"CEL1")
    buf.write(struct.pack("<I", len(h)))
    buf.write(h)
    buf.write(b" " * (-(8 + len(h)) % 4))
    buf.write(keys.astype("<u4").tobytes())
    for col in (mean[:, 0], mean[:, 1], mean[:, 2], contrast, up, wsum, frac):
        buf.write(np.asarray(col, "<f4").tobytes())
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(buf.getvalue(), compresslevel=6, mtime=0))
    print(f"  exported {len(keys)} cells -> {path.relative_to(ROOT)} ({path.stat().st_size / 1048576:.1f} MB)")


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


def export_textured(name: str, cfg: dict, size: int = 4096):
    """The scan as it is, photo textures shrunk to `size`, placed like build()'s meshes, for the
    preview's "Textured scan" view: .temp/textured/<model>.glb (one mesh per texture) and
    <model>.json (texels per meter at that size, for the view's blur)."""
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
    print(f"  loading {cfg['input']}")
    geoms = trimesh.load(TEMP / cfg["input"]).dump(concatenate=False)
    V = np.concatenate([g.vertices for g in geoms]) * cfg["meters_per_unit"]
    lo, hi = V.min(axis=0), V.max(axis=0)
    center = (lo + hi) / 2
    ground_ref = float(np.percentile(V[:, 1], 1)) if cfg["mode"] == "column" else float(lo[1])
    shift = np.array([center[0], ground_ref, center[2]])
    groups = {}                  # texture -> [(verts, faces, uv)]
    for g in geoms:
        m = g.visual.material
        tex = getattr(m, "emissiveTexture", None) or getattr(m, "baseColorTexture", None)
        uv = getattr(g.visual, "uv", None)
        if tex is None or uv is None:
            tex, uv = None, np.full((len(g.vertices), 2), 0.5)
        groups.setdefault(id(tex), (tex, []))[1].append((g.vertices * cfg["meters_per_unit"] - shift, g.faces, uv))
    scene = trimesh.Scene()
    tex_area = world_area = 0.0
    for tex, parts in groups.values():
        img = (tex.convert("RGB").resize((size, size), Image.LANCZOS) if tex is not None
               else Image.new("RGB", (4, 4), (128, 128, 128)))
        img.format = "JPEG"      # embedded as JPEG, not PNG
        off, vs, fs, uvs = 0, [], [], []
        for v, f, uv in parts:
            vs.append(v); fs.append(f + off); uvs.append(uv); off += len(v)
        v, f, uv = np.concatenate(vs), np.concatenate(fs), np.concatenate(uvs)
        if tex is not None:
            t = uv[f] * size
            e1, e2 = t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]
            tex_area += np.abs(e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0]).sum() / 2
            world_area += np.linalg.norm(np.cross(v[f][:, 1] - v[f][:, 0], v[f][:, 2] - v[f][:, 0]), axis=1).sum() / 2
        mat = trimesh.visual.material.PBRMaterial(baseColorTexture=img, metallicFactor=0.0)
        scene.add_geometry(trimesh.Trimesh(v, f, visual=trimesh.visual.TextureVisuals(uv=uv, material=mat),
                                           process=False))
    out = TEMP / "textured" / f"{name}.glb"
    out.parent.mkdir(parents=True, exist_ok=True)
    scene.export(out)
    tpm = float(np.sqrt(tex_area / max(world_area, 1e-9)))
    out.with_suffix(".json").write_text(json.dumps({"texels_per_meter": tpm, "texture": size}) + "\n")
    print(f"  wrote {out.relative_to(ROOT)} ({out.stat().st_size / 1048576:.1f} MB), "
          f"{tpm:.0f} texels per meter")


def build(name: str, args):
    cfg = MODELS[name]
    print(f"{name}:")
    if args.textured:
        export_textured(name, cfg)
        return
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
    glass = glass_faces(cfg, C, tri.mean(axis=1))
    if args.style == "full":
        pal_ids = full_palette(cfg)
        if glass is not None:
            # glass is never picked by color, only by the glass rule
            pal_ids = pal_ids + ["glass"]
            cfg = dict(cfg, penalty={**cfg.get("penalty", {}), "glass": 1e9})
            print(f"  glass: {area[glass].sum() / area.sum() * 100:.1f}% of the surface")
        for b in {r["block"] for r in cfg.get("force", [])} - set(pal_ids):
            # only by the force rule, like glass
            pal_ids = pal_ids + [b]
            cfg = dict(cfg, penalty={**cfg.get("penalty", {}), b: 1e9})
        cfg = dict(cfg, palette=pal_ids)
        cfg["tune"] = {**cfg.get("tune", {}), **{k: v for k, v in vars(args).items()
                                                 if k in TUNE_DEFAULTS and v is not None}}
        match = dict(forced=glass, forced_mat=pal_ids.index("glass") if glass is not None else -1,
                     forced_fraction=cfg.get("glass", {}).get("cell_fraction", 0.5))
        if args.cells_only:
            match_cells(tri.mean(axis=1), area, lab, up, cfg, args.tex_cell,
                        export=TEMP / "cells" / f"{name}.cells.gz", **match)
            return
        grid, cell_centers, cell_mat, plab = match_cells(
            tri.mean(axis=1), area, lab, up, cfg, args.tex_cell, **match)
        cell_mat = force_mats(cfg, pal_ids, cell_centers, cell_mat)
        grid = (*grid[:3], cell_mat.astype(np.uint8))
        print(f"  tune {tune(cfg)}")
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
    if args.style == "full":
        # the grid already carries the scan's detail block by block; mixing in look-alikes on
        # top of it only adds noise
        variants = [[(i, 1)] for i in range(len(pal))]

    out = Path(args.output).resolve() if args.output else TEMP / "meshes" / f"{name}.mesh.gz"
    budget = args.max_kb * 1024
    target, best = int(args.max_kb * 220), None      # ~220 tris per gzipped KB
    for _ in range(6):
        dec = decimate(V, F, target)
        vmat = transfer(tri.mean(axis=1), area, mat, dec.vertices, len(pal))
        if "glass" in pal:
            vmat = case_glass(cfg, dec.vertices, vmat, pal.index("glass"))
        vmat = force_mats(cfg, pal, dec.vertices, vmat)
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
        if "glass" in pal:
            vmat = case_glass(cfg, dec.vertices, vmat, pal.index("glass"))
        vmat = force_mats(cfg, pal, dec.vertices, vmat)
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
    ap.add_argument("--blur", type=float, help="override the model's tune.blur (see TUNE_DEFAULTS)")
    ap.add_argument("--spread", type=float, help="override tune.spread (contrast)")
    ap.add_argument("--pattern", type=float, help="override tune.pattern (texture matching)")
    ap.add_argument("--majority", type=int, help="override tune.majority")
    ap.add_argument("--height", type=float, help="override tune.height (height shading)")
    ap.add_argument("--slope", type=float, help="override tune.slope (slope shading)")
    ap.add_argument("--brightness", type=float, help="override tune.brightness")
    ap.add_argument("--cells-only", action="store_true",
                    help="--style full: only export .temp/cells/<model>.cells.gz for the preview's "
                         "tuning sliders (no decimation, no mesh)")
    ap.add_argument("--textured", action="store_true",
                    help="only export .temp/textured/<model>.glb, the scan with shrunk photo "
                         "textures, for the preview's \"Textured scan\" view")
    ap.add_argument("--output", help="default .temp/meshes/<model>.mesh.gz")
    args = ap.parse_args()
    for name in (MODELS if args.model == "all" else [args.model]):
        build(name, args)


if __name__ == "__main__":
    main()
