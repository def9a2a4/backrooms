# /// script
# requires-python = ">=3.11"
# dependencies = ["trimesh", "numpy", "pillow", "fast-simplification"]
# ///
"""Build the compressed test-scene meshes that Level 418 voxelizes at runtime.

Sources: alecjacobson/common-3d-test-models (pinned below), which redistributes
  teapot          Utah teapot, Martin Newell (public domain)
  suzanne         Blender Foundation
  stanford-bunny  Stanford 3D Scanning Repository
  xyzrgb_dragon   Stanford 3D Scanning Repository (XYZ RGB Inc.)
  lucy            Stanford 3D Scanning Repository
See meshes/CREDITS.txt.

Pipeline: download OBJ → rotate to y-up, face +z → base at y=0, centered in XZ, unit height →
decimate → write .temp/meshes/testscene/<name>.mesh.gz (format in write_mesh()).

Usage: uv run scripts/testscene/build_meshes.py [--preview]
  --preview also writes .temp/testscene/<name>.png (front and side silhouettes) for checking
  the orientation.
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
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from meshlib import quantize, write_triangles, write_vertices  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEMP = ROOT / ".temp" / "testscene"
OUT = ROOT / ".temp/meshes/testscene"   # published to meshes/ by scripts/scans/publish.py

SOURCE = ("https://raw.githubusercontent.com/alecjacobson/common-3d-test-models/"
          "8a4f8642acaf43f9cd7b67858a1502e1055ef202/data")

# (output name, source file, rotation as a list of (axis, degrees) applied in order, target tris)
MODELS = [
    ("teapot",    "teapot.obj",         [],               4000),
    ("suzanne",   "suzanne.obj",        [],               968),
    ("bunny",     "stanford-bunny.obj", [],               5000),
    ("dragon",    "xyzrgb_dragon.obj",  [],               20000),
    ("lucy",      "lucy.obj",           [("x", -90)],     25000),
]


def rotation(axis: str, deg: float) -> np.ndarray:
    t = np.radians(deg)
    c, s = np.cos(t), np.sin(t)
    return {
        "x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
        "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
        "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]]),
    }[axis]


def fetch(name: str) -> Path:
    path = TEMP / name
    if not path.exists():
        TEMP.mkdir(parents=True, exist_ok=True)
        print(f"  downloading {name}")
        urllib.request.urlretrieve(f"{SOURCE}/{name}", path)
    return path


def normalize(verts: np.ndarray, rot) -> np.ndarray:
    for axis, deg in rot:
        verts = verts @ rotation(axis, deg).T
    lo, hi = verts.min(axis=0), verts.max(axis=0)
    verts = verts - np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
    return verts / (hi[1] - lo[1])


def decimate(verts, faces, target):
    m = trimesh.Trimesh(verts, faces, process=True)
    m.update_faces(m.nondegenerate_faces())
    if len(m.faces) > target:
        v, f = fast_simplification.simplify(m.vertices, m.faces,
                                            target_reduction=1 - target / len(m.faces), agg=5)
        m = trimesh.Trimesh(v, f, process=True)
        m.update_faces(m.nondegenerate_faces())
    m.remove_unreferenced_vertices()
    return m


def write_mesh(verts, faces, path: Path):
    """Format (little-endian, whole stream gzipped):
      "VXM1"
      f32 bboxMin[3], bboxMax[3]        unit height, base at y=0, centered in XZ, front faces +z
      varint vertexCount, then per vertex 3 × zigzag-varint deltas of uint16 quantized coords
      varint triCount, then per tri zigzag-varint (a - prevA), (b - a), (c - a)
    Vertex/triangle encoding is shared with the Stonehenge mesh, see scripts/meshlib.py.
    """
    lo, hi, q, _, f = quantize(verts, faces)
    buf = io.BytesIO()
    buf.write(b"VXM1")
    buf.write(struct.pack("<6f", *lo, *hi))
    write_vertices(buf, q)
    write_triangles(buf, f)
    raw = buf.getvalue()
    data = gzip.compress(raw, compresslevel=9, mtime=0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return len(raw), len(data)


def preview(verts, faces, path: Path, size=256):
    """Front (looking down -z) and side (looking down -x) silhouettes, side by side."""
    img = Image.new("L", (size * 2, size), 255)
    d = ImageDraw.Draw(img)
    span = max(np.ptp(verts[:, 0]), np.ptp(verts[:, 2]), 1.0)
    s = (size - 16) / span
    for panel, (h, sign) in enumerate([(0, 1), (2, -1)]):
        ox = panel * size + size / 2
        for tri in verts[faces]:
            pts = [(ox + sign * p[h] * s, size - 8 - p[1] * s) for p in tri]
            d.polygon(pts, fill=0)
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()

    for name, src, rot, target in MODELS:
        m = trimesh.load(fetch(src), force="mesh", process=False)
        verts = normalize(np.asarray(m.vertices, dtype=np.float64), rot)
        dec = decimate(verts, np.asarray(m.faces), target)
        raw, packed = write_mesh(dec.vertices, dec.faces, OUT / f"{name}.mesh.gz")
        ext = dec.vertices.max(axis=0) - dec.vertices.min(axis=0)
        print(f"{name:10s} {len(m.faces):6d} -> {len(dec.faces):5d} tris, "
              f"extent {ext.round(2)}, {packed / 1024:.1f} KB")
        if args.preview:
            preview(dec.vertices, dec.faces, TEMP / f"{name}.png")


if __name__ == "__main__":
    main()
