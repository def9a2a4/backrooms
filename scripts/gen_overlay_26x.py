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
Re-run after editing any of the base dimension_type JSONs listed below.

Timeline template: scripts/data/day_timeline_26x.json, taken from vanilla 26.3
data/minecraft/timeline/day.json, keeping only visual/* and gameplay/sky_light_level
tracks, with "clock" set to the placeholder "CLOCK".
"""

import json
import os

DATAPACK = "src/main/resources/backrooms_datapack"
OVERLAY = os.path.join(DATAPACK, "overlay_26x/data/backrooms")
TEMPLATE = "scripts/data/day_timeline_26x.json"

# Dimension types whose levels set fixed_time (see src/main/resources/levels/*.yml)
FIXED_TIME_TYPES = ["alpha", "light", "twilight"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    with open(TEMPLATE) as f:
        template = json.load(f)

    for name in FIXED_TIME_TYPES:
        clock = f"backrooms:{name}"
        timeline = f"backrooms:{name}_day"

        write_json(os.path.join(OVERLAY, "world_clock", f"{name}.json"), {})
        write_json(os.path.join(OVERLAY, "timeline", f"{name}_day.json"), {**template, "clock": clock})

        with open(os.path.join(DATAPACK, "data/backrooms/dimension_type", f"{name}.json")) as f:
            dim = json.load(f)
        # Vanilla 26.x only sets has_fixed_time on clockless dimensions (the nether)
        dim.pop("has_fixed_time", None)
        dim["default_clock"] = clock
        dim["timelines"] = [timeline]
        write_json(os.path.join(OVERLAY, "dimension_type", f"{name}.json"), dim)

        print(f"wrote overlay for {name}")


if __name__ == "__main__":
    main()
