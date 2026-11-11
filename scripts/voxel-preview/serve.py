"""Static server for the voxel preview with caching disabled, so a rebuilt mesh or edited
voxelizer shows up on a plain reload. Serves the repo root on http://localhost:8056/.
Block textures missing from .temp/textures/ are fetched from the pinned vanilla asset mirror
on first request."""

import functools
import http.server
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / ".temp/textures"
# Same pinned mirror as scripts/stonehenge/build_mesh.py (commit == tag 1.21.4).
MC_ASSETS = ("https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/"
             "58d92c8cee4c7ac22d02988fb2bc3e4648af9ef2/assets/minecraft/textures/block")


class NoCache(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        m = re.fullmatch(r"/\.temp/textures/([a-z0-9_]+)\.png", self.path)
        if m and not (TEXTURES / f"{m[1]}.png").exists():
            try:
                with urllib.request.urlopen(f"{MC_ASSETS}/{m[1]}.png", timeout=30) as r:
                    TEXTURES.mkdir(parents=True, exist_ok=True)
                    (TEXTURES / f"{m[1]}.png").write_bytes(r.read())
            except OSError as e:
                self.log_message("texture %s: %s", m[1], e)
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    handler = functools.partial(NoCache, directory=str(ROOT))
    with http.server.ThreadingHTTPServer(("127.0.0.1", 8056), handler) as httpd:
        print("preview: http://localhost:8056/scripts/voxel-preview/")
        httpd.serve_forever()
