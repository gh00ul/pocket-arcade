"""Installs Godot's Android build template (for the Gradle export) into godot/android/build.

The template ships inside the official export templates archive (a 1 GB .tpz) as
templates/android_source.zip. This reads just that member with HTTP range requests, unzips it
into godot/android/build and writes godot/android/.build_version, as the editor's
"Install Android Build Template" does. Run from the repo root:

    python tools/fetch_android_template.py [--version 4.6.2]
"""
import argparse
import io
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
URL = "https://github.com/godotengine/godot-builds/releases/download/{v}-stable/Godot_v{v}-stable_export_templates.tpz"
BLOCK = 1 << 20


class RangeFile(io.RawIOBase):
    """A read-only, seekable view of a remote file, fetched in blocks with HTTP range requests."""

    def __init__(self, url: str):
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req) as r:
            self.url = r.geturl()
            self.size = int(r.headers["Content-Length"])
        self.pos = 0
        self.cache: dict[int, bytes] = {}

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, offset, whence=io.SEEK_SET):
        if whence == io.SEEK_SET:
            self.pos = offset
        elif whence == io.SEEK_CUR:
            self.pos += offset
        else:
            self.pos = self.size + offset
        return self.pos

    def _block(self, i: int) -> bytes:
        if i not in self.cache:
            start = i * BLOCK
            end = min(start + BLOCK, self.size) - 1
            req = urllib.request.Request(self.url, headers={"Range": f"bytes={start}-{end}"})
            with urllib.request.urlopen(req) as r:
                self.cache[i] = r.read()
        return self.cache[i]

    def readinto(self, b):
        if self.pos >= self.size:
            return 0
        n = min(len(b), self.size - self.pos)
        out = bytearray()
        while len(out) < n:
            i, off = divmod(self.pos + len(out), BLOCK)
            chunk = self._block(i)[off:off + n - len(out)]
            out += chunk
        b[:n] = out
        self.pos += n
        return n


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="4.6.2")
    a = ap.parse_args()
    url = URL.format(v=a.version)
    print(f"reading templates/android_source.zip from {url}")
    remote = RangeFile(url)
    with zipfile.ZipFile(io.BufferedReader(remote, buffer_size=BLOCK)) as tpz:
        data = tpz.read("templates/android_source.zip")
        version = tpz.read("templates/version.txt").decode().strip()
    print(f"android_source.zip: {len(data) // 1024} KB, templates {version}")
    dest = ROOT / "godot/android"
    build = dest / "build"
    if build.exists():
        shutil.rmtree(build)
    build.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        z.extractall(build)
    (dest / ".build_version").write_text(version, encoding="utf-8")
    # Keep Godot from importing the template's files as project resources.
    (build / ".gdignore").write_text("", encoding="utf-8")
    print(f"installed into {build}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
