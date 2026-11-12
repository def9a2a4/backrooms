"""Publish the scan-level meshes: copy each into the GitHub Pages repo under a content-addressed
name (<mesh>-<sha256[:12]>.mesh.gz, so a URL never changes meaning) and write the manifest the
plugin ships (src/main/resources/scans/meshes.json: file, sha256, bytes per mesh).

The plugin downloads https://def9a2a4.github.io/backrooms/meshes/<file> and checks the hash.
This script only copies files; commit and push the Pages repo yourself.

Usage: python3 scripts/scans/publish.py [--pages ../def9a2a4.github.io]
       python3 scripts/scans/publish.py --install server/plugins/Backrooms/meshes
         (copy the meshes the manifest names into a server's data folder, so it needn't download)
"""

import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "src/main/resources/scans/meshes.json"
CREDITS = """Meshes for the scan levels of the Backrooms Minecraft plugin (levels 1881, 1967, 1973, 9500)

These files are modified versions of the following 3D scans, all licensed under CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/):

  hintze_hall       "Hintze Hall" by artfletch
                    https://sketchfab.com/3d-models/hintze-hall-45f5e56887f44075bbf283977c99541f
  undercroft_grey,  "Southbank Undercroft Skatepark" by artfletch
  undercroft_color  https://sketchfab.com/3d-models/southbank-undercroft-skatepark-add37d5dc6a2456eb0d7607c6fbd884d
  gobekli_tepe      "Gobekli Tepe" by rmark
                    https://sketchfab.com/3d-models/gobekli-tepe-5a4d25c355ff48d59ab76c36812b9cf3

Changes: decimated to ~1.8 million triangles, recentered and rescaled, texture colors replaced by
a 3D grid of Minecraft block ids, re-encoded in the plugin's compressed mesh format. Built with
scripts/scans/build_scan.py; voxelized into Minecraft worlds at runtime.

The modified meshes are distributed under the same license, CC BY 4.0.
"""

# mesh name (generator_config.mesh) -> build output (build_scan.py --style full --max-kb 8000
# --tex-cell 0.0625 --output .temp/meshes/<model>_16x.mesh.gz)
MESHES = {
    "hintze_hall": ".temp/meshes/hall_16x.mesh.gz",
    "undercroft_grey": ".temp/meshes/skatepark_grey_16x.mesh.gz",
    "undercroft_color": ".temp/meshes/skatepark_16x.mesh.gz",
    "gobekli_tepe": ".temp/meshes/gobekli_16x.mesh.gz",
}


def install(dest: Path):
    """Copy each manifest mesh into dest under its published name, from the local build. Skips
    (with a note) meshes whose build no longer matches the manifest: run publish first."""
    manifest = json.loads(MANIFEST.read_text())["meshes"]
    dest.mkdir(parents=True, exist_ok=True)
    for name, m in manifest.items():
        target = dest / m["file"]
        if target.exists() and target.stat().st_size == m["bytes"]:
            continue
        src = ROOT / MESHES[name]
        if not src.exists():
            print(f"{name}: {src.relative_to(ROOT)} not built; the server will download it")
            continue
        data = src.read_bytes()
        if hashlib.sha256(data).hexdigest() != m["sha256"]:
            print(f"{name}: local build differs from the manifest (run publish.py); not installed")
            continue
        target.write_bytes(data)
        print(f"installed {target}")
    for old in [*dest.glob("*.mesh.gz"), *dest.glob("*.part")]:
        if old.name not in {m["file"] for m in manifest.values()}:
            old.unlink()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", default=str(ROOT.parent / "def9a2a4.github.io"))
    ap.add_argument("--install", metavar="DIR", help="copy the manifest's meshes into DIR and stop")
    args = ap.parse_args()
    if args.install:
        install(Path(args.install))
        return
    out_dir = Path(args.pages) / "docs/backrooms/meshes"
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = {}
    for name, src in MESHES.items():
        data = (ROOT / src).read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        file = f"{name}-{sha[:12]}.mesh.gz"
        dest = out_dir / file
        if not dest.exists():
            shutil.copyfile(ROOT / src, dest)
        manifest[name] = {"file": file, "sha256": sha, "bytes": len(data)}
        print(f"{name:18s} {len(data) / 1048576:5.1f} MB  {file}")

    # drop superseded versions of these meshes from the Pages repo
    keep = {m["file"] for m in manifest.values()}
    for old in out_dir.glob("*.mesh.gz"):
        if old.name not in keep and old.name.rsplit("-", 1)[0] in MESHES:
            old.unlink()
            print(f"removed {old.name}")

    (out_dir / "CREDITS.txt").write_text(CREDITS)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    (MANIFEST.parent / "CREDITS.txt").write_text(CREDITS)
    MANIFEST.write_text(json.dumps({
        "_comment": "Written by scripts/scans/publish.py. Meshes are downloaded from "
                    "scan_levels.base_url + file and checked against sha256.",
        "meshes": manifest,
    }, indent=2) + "\n")
    total = sum(m["bytes"] for m in manifest.values())
    print(f"total {total / 1048576:.1f} MB -> {out_dir}\nmanifest {MANIFEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
