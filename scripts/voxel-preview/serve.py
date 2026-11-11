"""Static server for the Stonehenge preview with caching disabled, so a rebuilt mesh or edited
voxelizer shows up on a plain reload. Serves the repo root on http://localhost:8056/."""

import functools
import http.server
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    handler = functools.partial(NoCache, directory=str(ROOT))
    with http.server.ThreadingHTTPServer(("127.0.0.1", 8056), handler) as httpd:
        print("preview: http://localhost:8056/scripts/stonehenge/preview/")
        httpd.serve_forever()
