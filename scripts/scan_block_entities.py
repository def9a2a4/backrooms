#!/usr/bin/env python3
"""Scan Minecraft anvil region files for block entities, without a server.

Useful for verifying worldgen output (sign text, chest/lectern/spawner
placement) straight from region files. Stdlib only — no nbtlib/anvil deps.

Usage:
    scripts/scan_block_entities.py <world-or-region-dir> [--id sign] [--all]

    <world-or-region-dir>   a region/ directory, or any dir containing one
                            (e.g. test-server/world/dimensions/minecraft/bkrms_101)
    --id SUBSTRING          filter by block entity id substring (default: sign)
    --all                   no id filter; list every block entity
    --blank                 for signs: include signs with no text (default: shown
                            as a count only)

Examples:
    scripts/scan_block_entities.py test-server/world/dimensions/minecraft/bkrms_101
    scripts/scan_block_entities.py server/bkrms_0 --id chest
    scripts/scan_block_entities.py test-server/world --all
"""
import argparse
import glob
import json
import os
import struct
import sys
import zlib

# ---------------------------------------------------------------------------
# Minimal NBT parser (big-endian, java edition)
# ---------------------------------------------------------------------------

def _read_payload(buf, pos, tag_type):
    """Parse one NBT payload, return (value, new_pos). Arrays are skipped."""
    if tag_type == 1:   return buf[pos], pos + 1
    if tag_type == 2:   return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if tag_type == 3:   return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if tag_type == 4:   return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if tag_type == 5:   return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if tag_type == 6:   return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if tag_type == 7:   # byte array
        n = struct.unpack_from(">i", buf, pos)[0]
        return None, pos + 4 + n
    if tag_type == 8:   # string
        n = struct.unpack_from(">H", buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if tag_type == 9:   # list
        inner = buf[pos]
        n = struct.unpack_from(">i", buf, pos + 1)[0]
        p = pos + 5
        out = []
        for _ in range(n):
            v, p = _read_payload(buf, p, inner)
            out.append(v)
        return out, p
    if tag_type == 10:  # compound
        d = {}
        p = pos
        while True:
            t = buf[p]
            p += 1
            if t == 0:
                break
            n = struct.unpack_from(">H", buf, p)[0]
            name = buf[p + 2:p + 2 + n].decode("utf-8", "replace")
            p += 2 + n
            v, p = _read_payload(buf, p, t)
            d[name] = v
        return d, p
    if tag_type == 11:  # int array
        n = struct.unpack_from(">i", buf, pos)[0]
        return None, pos + 4 + 4 * n
    if tag_type == 12:  # long array
        n = struct.unpack_from(">i", buf, pos)[0]
        return None, pos + 4 + 8 * n
    raise ValueError(f"unknown NBT tag {tag_type}")


def parse_nbt_root(data):
    if not data or data[0] != 10:
        raise ValueError("root is not a compound")
    name_len = struct.unpack_from(">H", data, 1)[0]
    value, _ = _read_payload(data, 3 + name_len, 10)
    return value


# ---------------------------------------------------------------------------
# Anvil region reader
# ---------------------------------------------------------------------------

def iter_chunks(region_path):
    """Yield parsed chunk NBT compounds from one .mca file."""
    with open(region_path, "rb") as f:
        raw = f.read()
    if len(raw) < 8192:
        return
    for i in range(1024):
        offset = int.from_bytes(raw[i * 4:i * 4 + 3], "big") * 4096
        if offset == 0 or offset + 5 > len(raw):
            continue
        length = struct.unpack_from(">i", raw, offset)[0]
        compression = raw[offset + 4]
        blob = raw[offset + 5:offset + 4 + length]
        try:
            if compression == 2:
                data = zlib.decompress(blob)
            elif compression == 3:
                data = blob  # uncompressed
            elif compression == 1:
                data = zlib.decompress(blob, 31)  # gzip
            else:
                continue
            yield parse_nbt_root(data)
        except Exception:
            continue  # torn/corrupt chunk — skip


# ---------------------------------------------------------------------------
# Sign text extraction
# ---------------------------------------------------------------------------

def _component_text(msg):
    """A text component: JSON string (pre-1.21.5) or NBT compound (1.21.5+)."""
    if isinstance(msg, str):
        try:
            j = json.loads(msg)
        except Exception:
            return msg
        if isinstance(j, str):
            return j
        if isinstance(j, dict):
            return str(j.get("text", ""))
        return str(j)
    if isinstance(msg, dict):
        return str(msg.get("text", msg.get("", "")))
    return "" if msg is None else str(msg)


def sign_text(block_entity):
    """Join a sign's non-empty front lines with ' / '."""
    front = block_entity.get("front_text") or {}
    lines = [_component_text(m) for m in front.get("messages") or []]
    return " / ".join(line for line in lines if line.strip())


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def find_region_dir(path):
    if os.path.basename(os.path.normpath(path)) == "region":
        return path
    direct = os.path.join(path, "region")
    if os.path.isdir(direct):
        return direct
    matches = glob.glob(os.path.join(path, "**", "region"), recursive=True)
    if len(matches) == 1:
        return matches[0]
    if len(matches) > 1:
        sys.exit(f"multiple region/ dirs under {path}; pass one of:\n  "
                 + "\n  ".join(matches))
    sys.exit(f"no region/ directory found under {path}")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("path", help="region/ dir, or a world dir containing one")
    ap.add_argument("--id", default="sign",
                    help="block entity id substring filter (default: sign)")
    ap.add_argument("--all", action="store_true", help="no id filter")
    ap.add_argument("--blank", action="store_true",
                    help="list blank signs individually too")
    args = ap.parse_args()

    region_dir = find_region_dir(args.path)
    wanted = None if args.all else args.id

    found = []
    for mca in sorted(glob.glob(os.path.join(region_dir, "*.mca"))):
        for chunk in iter_chunks(mca):
            for be in chunk.get("block_entities") or []:
                if not isinstance(be, dict):
                    continue
                be_id = str(be.get("id", ""))
                if wanted is not None and wanted not in be_id:
                    continue
                found.append(be)

    signs = [be for be in found if "sign" in str(be.get("id", ""))]
    others = [be for be in found if be not in signs]

    if signs:
        filled = [(be, sign_text(be)) for be in signs]
        with_text = [(be, t) for be, t in filled if t]
        blanks = [be for be, t in filled if not t]
        print(f"signs: {len(signs)}  (with text: {len(with_text)}, blank: {len(blanks)})")
        for be, text in sorted(with_text, key=lambda e: (e[0].get("x"), e[0].get("z"))):
            print(f"  ({be.get('x'):>6},{be.get('y'):>4},{be.get('z'):>6})  {text}")
        if args.blank:
            for be in sorted(blanks, key=lambda e: (e.get("x"), e.get("z"))):
                print(f"  ({be.get('x'):>6},{be.get('y'):>4},{be.get('z'):>6})  <blank>")

    if others:
        print(f"other block entities: {len(others)}")
        for be in sorted(others, key=lambda e: (str(e.get("id")), e.get("x"), e.get("z"))):
            print(f"  ({be.get('x'):>6},{be.get('y'):>4},{be.get('z'):>6})  {be.get('id')}")

    if not found:
        print("no matching block entities found")


if __name__ == "__main__":
    main()
