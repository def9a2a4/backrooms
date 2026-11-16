"""Publish the level meshes. They're kept out of the jar to keep it light: each is copied into
meshes/ (gitignored) under a content-addressed name (<mesh>-<sha256[:12]>.mesh.gz, so a URL
never changes meaning), and the manifest the plugin ships is rewritten
(src/main/resources/meshes.json: file, sha256, bytes per mesh). Level 142's Mace pack rides
along as "mace" (mace-<sha256[:12]>.zip): same download, same check.

Servers download https://def9a2a4.github.io/backrooms-data/meshes/<file> and check the hash, so
new meshes only reach servers once meshes/ is copied into the backrooms-data repo's meshes/ folder
and pushed. Keep old files there: released jars still name them.

Usage: python3 scripts/scans/publish.py
       python3 scripts/scans/publish.py --install server/plugins/Backrooms/meshes
         (copy the published meshes into a server's data folder, so it needn't download)
"""

import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "meshes"
MANIFEST = ROOT / "src/main/resources/meshes.json"

# mesh name (a level's `meshes:` / generator_config.mesh, ShapeLibrary.Shape) -> build output
MESHES = {
    # scripts/stonehenge/build_mesh.py
    "stonehenge": ".temp/meshes/stonehenge.mesh.gz",
    # scripts/testscene/build_meshes.py
    "teapot": ".temp/meshes/testscene/teapot.mesh.gz",
    "suzanne": ".temp/meshes/testscene/suzanne.mesh.gz",
    "bunny": ".temp/meshes/testscene/bunny.mesh.gz",
    "dragon": ".temp/meshes/testscene/dragon.mesh.gz",
    "lucy": ".temp/meshes/testscene/lucy.mesh.gz",
    # scripts/scans/build_scan.py --style full --max-kb 8000 --tex-cell 0.0625
    "hintze_hall": ".temp/meshes/hall_16x.mesh.gz",
    "undercroft_grey": ".temp/meshes/skatepark_grey_16x.mesh.gz",
    "undercroft_color": ".temp/meshes/skatepark_16x.mesh.gz",
    "gobekli_tepe": ".temp/meshes/gobekli_16x.mesh.gz",
    # scripts/mace/extract_schem.py --pack (not a mesh: Level 142's buildings and word lists)
    "mace": ".temp/mace/mace-buildings.zip",
    # scripts/level143/build_pack.py (Level 143's generated buildings, same layout as the Mace pack)
    "level143": ".temp/level143/level143-buildings.zip",
    # scripts/books/gutenberg.py (not a mesh: Level 1882's library books, optional)
    "shelf_books": ".temp/books/shelf_books.json.gz",
}

CREDITS = """Meshes for the Backrooms Minecraft plugin (https://github.com/def9a2a4/backrooms)

Each file is <mesh>-<sha256 prefix>.mesh.gz. They're derived from these models:

stonehenge (Level 56)
  "Stonehenge England - VR" by Graphic solutions, CC BY 4.0
  https://sketchfab.com/3d-models/stonehenge-england-vr-7e7febabef2045a68755cc39c8c7c91f
  Changes: cropped to the stone circle, decimated to ~10k triangles, textures reduced to a
  per-vertex Minecraft block palette (scripts/stonehenge/build_mesh.py).

hintze_hall (Level 1881)
  "Hintze Hall" by artfletch, CC BY 4.0
  https://sketchfab.com/3d-models/hintze-hall-45f5e56887f44075bbf283977c99541f
undercroft_grey, undercroft_color (Levels 1967, 1973)
  "Southbank Undercroft Skatepark" by artfletch, CC BY 4.0
  https://sketchfab.com/3d-models/southbank-undercroft-skatepark-add37d5dc6a2456eb0d7607c6fbd884d
gobekli_tepe (Level 9500)
  "Gobekli Tepe" by rmark, CC BY 4.0
  https://sketchfab.com/3d-models/gobekli-tepe-5a4d25c355ff48d59ab76c36812b9cf3
  Changes to the scans: decimated to ~1.8 million triangles, recentered and rescaled, texture
  colors replaced by a 3D grid of Minecraft block ids (scripts/scans/build_scan.py).

The modified meshes above are distributed under the same license, CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/).

teapot, suzanne, bunny, dragon, lucy (Level 418), from the copies in
https://github.com/alecjacobson/common-3d-test-models (commit 8a4f864):
  teapot   Utah teapot, Martin Newell, 1975. Public domain.
  suzanne  Suzanne, the Blender monkey. Blender Foundation, https://www.blender.org/
  bunny    Stanford Bunny, Stanford 3D Scanning Repository
  dragon   XYZ RGB Asian Dragon, XYZ RGB Inc., via the Stanford 3D Scanning Repository
  lucy     Lucy, Stanford 3D Scanning Repository
  Stanford 3D Scanning Repository: http://graphics.stanford.edu/data/3Dscanrep/ -- used with
  credit under the repository's terms, which allow research and non-commercial use.
  Changes: rotated upright, scaled to unit height, decimated to a few thousand triangles
  (scripts/testscene/build_meshes.py).

All of them are voxelized into Minecraft worlds at runtime.

mace (Level 142): mace-<sha256 prefix>.zip
  The buildings and word lists of Mace, a Minecraft city generator by Robson,
  https://github.com/corbin-auriti/mace-minecraft, GNU GPL v3.
  Changes: buildings copied out of Mace's template world into Sponge Schematic v3 files with
  their blocks converted to Minecraft 1.21.11 (scripts/mace/extract_schem.py). The archive holds
  Mace's licence and is distributed under the same licence, GPL v3.

level143 (Level 143): level143-<sha256 prefix>.zip
  Buildings generated by the plugin's scripts/level143/build_pack.py, in the same layout as the
  Mace pack. Part of the plugin; no outside sources.

shelf_books (Level 1882): shelf_books-<sha256 prefix>.json.gz
  Chapters of public-domain books from Project Gutenberg (https://www.gutenberg.org/), laid out
  as Minecraft book pages by scripts/books/gutenberg.py. Public domain.
"""


def suffix(src):
    """The published file's extension: .mesh.gz for meshes, else the build output's own."""
    return next((s for s in (".mesh.gz", ".json.gz") if src.endswith(s)), Path(src).suffix)


PUBLISHED = ("*.mesh.gz", "*.zip", "*.json.gz")


def install(dest: Path):
    """Copy the manifest's meshes from meshes/ into dest, dropping stale or partial files."""
    manifest = json.loads(MANIFEST.read_text())["meshes"]
    dest.mkdir(parents=True, exist_ok=True)
    keep = {m["file"] for m in manifest.values()}
    for m in manifest.values():
        target = dest / m["file"]
        if not (target.exists() and target.stat().st_size == m["bytes"]):
            shutil.copyfile(OUT / m["file"], target)
            print(f"installed {target}")
    for old in [*(f for g in PUBLISHED for f in dest.glob(g)), *dest.glob("*.part")]:
        if old.name not in keep:
            old.unlink()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", metavar="DIR", help="copy the published meshes into DIR and stop")
    args = ap.parse_args()
    if args.install:
        install(Path(args.install))
        return

    OUT.mkdir(parents=True, exist_ok=True)
    old = json.loads(MANIFEST.read_text())["meshes"] if MANIFEST.exists() else {}
    manifest = {}
    for name, src in MESHES.items():
        path = ROOT / src
        if not path.exists():
            if name in old:   # not rebuilt here: keep what's published
                manifest[name] = old[name]
                print(f"{name:18s} (kept {old[name]['file']}: {src} not built)")
                continue
            raise SystemExit(f"{name}: {src} not built")
        data = path.read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        file = f"{name}-{sha[:12]}{suffix(src)}"
        if not (OUT / file).exists():
            (OUT / file).write_bytes(data)
        manifest[name] = {"file": file, "sha256": sha, "bytes": len(data)}
        print(f"{name:18s} {len(data) / 1048576:6.2f} MB  {file}")

    keep = {m["file"] for m in manifest.values()}
    for f in (f for g in PUBLISHED for f in OUT.glob(g)):
        if f.name not in keep:
            f.unlink()
            print(f"removed {f.name}")
    (OUT / "CREDITS.txt").write_text(CREDITS)
    MANIFEST.write_text(json.dumps({
        "_comment": "Written by scripts/scans/publish.py. Meshes are downloaded from "
                    "meshes.base_url + file and checked against sha256.",
        "meshes": manifest,
    }, indent=2) + "\n")
    total = sum(m["bytes"] for m in manifest.values())
    print(f"total {total / 1048576:.1f} MB in {OUT.relative_to(ROOT)}/, manifest {MANIFEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
