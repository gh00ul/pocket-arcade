"""Fails if any tracked or staged text file contains a NUL byte (a sign of a broken write).

Run from the repo root: python tools/check_nul.py
"""
import subprocess
import sys
from pathlib import Path

BINARY = {".png", ".jpg", ".jpeg", ".webp", ".jks", ".keystore", ".apk", ".aab", ".jar", ".aar",
          ".so", ".zip", ".tpz", ".ogg", ".wav", ".ttf", ".otf", ".res", ".scn", ".ico", ".bin"}


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    names = subprocess.run(["git", "ls-files", "--cached"], cwd=root, capture_output=True, text=True, check=True).stdout.split("\n")
    bad = []
    for name in names:
        if not name or Path(name).suffix.lower() in BINARY:
            continue
        path = root / name
        if path.is_file() and b"\x00" in path.read_bytes():
            bad.append(name)
    for name in bad:
        print(f"NUL byte in {name}")
    if not bad:
        print(f"no NUL bytes in {len([n for n in names if n])} tracked files")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
