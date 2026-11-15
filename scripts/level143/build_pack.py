"""Level 143's building pack: every building in buildings.CATALOG drawn a few times per style,
from fixed seeds, as Sponge v3 schematics in the shape of Level 142's Mace pack, with the word
lists, emblems and flags, zipped (byte-for-byte reproducibly) for scripts/scans/publish.py.

Usage: python3 scripts/level143/build_pack.py [--out .temp/level143] [--only NAME] [--report BLOCKS_JSON]

  --report  the vanilla block report, to check every block state (a misspelt block loads as air):
            java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
            (default .temp/level143/report/reports/blocks.json, skipped if missing)
"""

import argparse
import hashlib
import random
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from buildings import CATALOG  # noqa: E402
import buildings_extra  # noqa: E402,F401  (registers more kinds)
import sky  # noqa: E402,F401  (registers more sky features)
import buildings_more  # noqa: E402,F401  (registers a third set of kinds)
from parts import STYLES  # noqa: E402
from schem import Checker  # noqa: E402
import words  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".temp" / "level143"
PACK_NAME = "level143-buildings.zip"

README = """Level 143's buildings, for the Backrooms Minecraft plugin
(https://github.com/def9a2a4/backrooms).

Drawn by scripts/level143/build_pack.py from rules and fixed seeds, in the format of Level 142's
Mace pack (Sponge v3 schematics with Metadata.Mace; stand-in blocks for what each city fills in),
with the word lists, gate emblems and tower flags the cities use.
"""


def seed(*parts):
    return int.from_bytes(hashlib.sha256("/".join(map(str, parts)).encode()).digest()[:8], "big")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=OUT)
    ap.add_argument("--only", help="only catalog entries whose name contains this")
    ap.add_argument("--report", type=Path, default=OUT / "report" / "reports" / "blocks.json")
    args = ap.parse_args()

    checker = Checker(args.report)
    entries, lines = [], []
    for st in STYLES:
        for e in CATALOG:
            if e.styles and st.key not in e.styles:
                continue
            if args.only and args.only not in e.name:
                continue
            for i in range(e.n):
                rng = random.Random(seed(st.key, e.name, i))
                b = e.make(rng, st)
                if b.kind != "sky_feature":   # floats over the mine entrance, never in a lot
                    b.square()
                b.meta["frequency"] = e.freq
                b.meta["files"] = st.file
                if e.unique:
                    b.meta["unique"] = "yes"
                b.meta.update(e.extra or {})
                b.name = f"{b.name} ({st.key} {i + 1})"
                data, palette = b.schematic()
                checker.check(b, palette)
                path = f"schem/{b.kind}/{st.key}_{e.name}_{i + 1}.schem"
                entries.append((path, data))
                lo, hi = b.bounds()
                lines.append(f"{b.kind:<20} {path.rsplit('/', 1)[1]:<36} {b.w:>3}x{hi - lo + 1:>3}x{b.l:<3} "
                             f"blocks={sum(1 for v in b.blocks.values() if v[0] != 'air'):<5} "
                             f"entities={len(b.entities):<3} {e.freq}")

    for name, lst in sorted(words.WORDS.items()):
        entries.append((f"words/{name}.txt", ("\n".join(lst) + "\n").encode()))
    for style, names in words.EMBLEMS_BY_STYLE.items():
        for n in names:
            entries.append((f"emblems/{style}_{n}.txt", words.emblem(words.EMBLEM_ART[n], words.EMBLEM_BLOCKS[style]).encode()))
    for n, rows in sorted(words.FLAGS.items()):
        entries.append((f"flags/{n}.txt", ("\n".join(rows) + "\n").encode()))
    entries.append(("README.txt", README.encode()))

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "report.txt").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
    if checker.bad:
        print("\nbad block states:")
        for k, v in sorted(checker.bad.items()):
            print(f"  {k}  ({', '.join(sorted(v)[:3])}{'...' if len(v) > 3 else ''})")
    elif checker.blocks is None:
        print(f"\n(no block report at {args.report}: states not checked)")
    if args.only:
        return 1 if checker.bad else 0

    pack = args.out.resolve() / PACK_NAME
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    n = sum(1 for p, _ in entries if p.startswith("schem/"))
    print(f"\n{n} buildings, {len(entries)} files, {pack.stat().st_size / 1024:.0f} KB: {pack.relative_to(ROOT)}")
    return 1 if checker.bad else 0


if __name__ == "__main__":
    sys.exit(main())
