"""Shared mesh encoding for the build scripts (scripts/stonehenge, scripts/testscene).

Vertices are quantized to uint16 against the bbox, sorted in Morton order and written as
zigzag-varint deltas. Triangles are rotated so their smallest index comes first (keeping
winding), sorted, and written as zigzag-varint (a - prevA), (b - a), (c - a).
"""

import io

import numpy as np


def varint(buf: io.BytesIO, v: int):
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            buf.write(bytes([b | 0x80]))
        else:
            buf.write(bytes([b]))
            return


def zigzag(v: int) -> int:
    return (v << 1) ^ (v >> 63)


def morton3(q: np.ndarray) -> np.ndarray:
    def spread(x):
        x = x.astype(np.uint64) & 0xFFFF
        x = (x | (x << 32)) & 0x1F00000000FFFF
        x = (x | (x << 16)) & 0x1F0000FF0000FF
        x = (x | (x << 8)) & 0x100F00F00F00F00F
        x = (x | (x << 4)) & 0x10C30C30C30C30C3
        x = (x | (x << 2)) & 0x1249249249249249
        return x
    return spread(q[:, 0]) | (spread(q[:, 1]) << 1) | (spread(q[:, 2]) << 2)


def quantize(verts: np.ndarray, faces: np.ndarray):
    """Returns (lo, hi, q, order, f): bbox, Morton-sorted uint16 coords, the vertex order applied
    (so callers can reorder per-vertex data with [order]) and the remapped, sorted triangles."""
    lo, hi = verts.min(axis=0), verts.max(axis=0)
    q = np.round((verts - lo) / np.maximum(hi - lo, 1e-9) * 65535).astype(np.int64)

    order = np.argsort(morton3(q), kind="stable")
    remap = np.empty_like(order)
    remap[order] = np.arange(len(order))
    q = q[order]
    f = remap[faces]
    rot = f.argmin(axis=1)
    f = np.stack([f[np.arange(len(f)), (rot + i) % 3] for i in range(3)], axis=1)
    f = f[np.lexsort((f[:, 1], f[:, 0]))]
    return lo, hi, q, order, f


def write_vertices(buf: io.BytesIO, q: np.ndarray):
    varint(buf, len(q))
    prev = np.zeros(3, np.int64)
    for row in q:
        for c in range(3):
            varint(buf, zigzag(int(row[c] - prev[c])))
        prev = row


def write_triangles(buf: io.BytesIO, f: np.ndarray):
    varint(buf, len(f))
    prev_a = 0
    for a, b, c in f:
        varint(buf, zigzag(int(a - prev_a)))
        varint(buf, zigzag(int(b - a)))
        varint(buf, zigzag(int(c - a)))
        prev_a = a
