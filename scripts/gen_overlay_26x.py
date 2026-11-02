#!/usr/bin/env python3
"""Generate the 26.x datapack overlay (backrooms_datapack/overlay_26x).

26.x moved time of day into world clocks + timelines. A dimension type without a
`default_clock` can't have its time set (World.setTime throws), so levels with a
`fixed_time` render at the wrong time of day. For each dimension type used by a
fixed-time level, this writes:

  world_clock/<type>.json       its own clock
  timeline/<type>_day.json      vanilla day timeline (visual tracks) bound to that clock
  dimension_type/<type>.json    the base type + default_clock + timelines

The overlay only applies on 26.x data formats, so 1.21.11 never sees these keys.

Which types need a clock is derived from src/main/resources/levels/level_*.yml:
any `dimension_type` paired with a `fixed_time`, except `default` (the vanilla
overworld type, which already has a clock) and types without skylight (time has
no visible effect there).

Usage:
  python3 scripts/gen_overlay_26x.py           regenerate the overlay
  python3 scripts/gen_overlay_26x.py --check   fail if the committed overlay is stale

Timeline template: scripts/data/day_timeline_26x.json, taken from vanilla 26.3
data/minecraft/timeline/day.json, keeping only visual/* and gameplay/sky_light_level
tracks, with "clock" set to the placeholder "CLOCK".
"""

import glob
import json
import os
import re
import sys

DATAPACK = "src/main/resources/backrooms_datapack"
OVERLAY_ROOT = os.path.join(DATAPACK, "overlay_26x")
OVERLAY = os.path.join(OVERLAY_ROOT, "data/backrooms")
BASE_TYPES = os.path.join(DATAPACK, "data/backrooms/dimension_type")
TEMPLATE = "scripts/data/day_timeline_26x.json"
LEVELS = "src/main/resources/levels/level_*.yml"

# Must match ConfigDrivenLevel's default for dimension_type
DEFAULT_DIMENSION_TYPE = "dark"


def top_level_scalar(text, key):
    m = re.search(rf"^{key}:\s*([^\s#]+)", text, re.MULTILINE)
    return m.group(1).strip("\"'") if m else None


def fixed_time_types():
    types = set()
    for path in sorted(glob.glob(LEVELS)):
        if path.endswith("_books.yml"):
            continue
        with open(path) as f:
            text = f.read()
        if top_level_scalar(text, "fixed_time") is None:
            continue
        dim = top_level_scalar(text, "dimension_type") or DEFAULT_DIMENSION_TYPE
        if dim == "default":
            continue
        base = os.path.join(BASE_TYPES, f"{dim}.json")
        if not os.path.exists(base):
            sys.exit(f"{path}: dimension_type '{dim}' has no {base}")
        with open(base) as f:
            if not json.load(f).get("has_skylight", False):
                continue
        types.add(dim)
    return sorted(types)


def render():
    """Return {relative path under OVERLAY: file content} for the whole overlay."""
    with open(TEMPLATE) as f:
        template = json.load(f)

    def dump(data):
        return json.dumps(data, indent=2) + "\n"

    files = {}
    for name in fixed_time_types():
        clock = f"backrooms:{name}"
        timeline = f"backrooms:{name}_day"

        with open(os.path.join(BASE_TYPES, f"{name}.json")) as f:
            dim = json.load(f)
        # Vanilla 26.x only sets has_fixed_time on clockless dimensions (the nether)
        dim.pop("has_fixed_time", None)
        dim["default_clock"] = clock
        dim["timelines"] = [timeline]

        files[f"world_clock/{name}.json"] = dump({})
        files[f"timeline/{name}_day.json"] = dump({**template, "clock": clock})
        files[f"dimension_type/{name}.json"] = dump(dim)
    return files


def existing():
    files = {}
    for path in glob.glob(os.path.join(OVERLAY_ROOT, "**/*"), recursive=True):
        if os.path.isfile(path):
            with open(path) as f:
                files[os.path.relpath(path, OVERLAY)] = f.read()
    return files


def main():
    wanted = render()
    if "--check" in sys.argv:
        have = existing()
        stale = sorted(p for p in wanted.keys() | have.keys() if wanted.get(p) != have.get(p))
        if stale:
            print("overlay_26x is out of date; run: python3 scripts/gen_overlay_26x.py")
            for p in stale:
                print(f"  {os.path.join(OVERLAY, p)}")
            sys.exit(1)
        print(f"overlay_26x up to date ({len(wanted) // 3} dimension types)")
        return

    for path in existing():
        os.remove(os.path.join(OVERLAY, path))
    for path, content in wanted.items():
        full = os.path.join(OVERLAY, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(content)
    print(f"wrote overlay for {', '.join(sorted({p.split('/')[1][:-5] for p in wanted if p.startswith('world_clock/')}))}")


if __name__ == "__main__":
    main()
