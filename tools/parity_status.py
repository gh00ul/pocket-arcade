"""Sets the status (and optionally the destination) of rows in docs/PORT_PARITY.md.

A row is found by the text of its first cell (a Kotlin file such as `engine/audio/Spatial.kt`,
or the start of a feature description). Run from the repo root:

    python tools/parity_status.py "engine/audio/Spatial.kt" done
    python tools/parity_status.py "engine/audio/Music.kt" "kept in Kotlin" --dest "android plugin"
"""
import argparse
import sys
from pathlib import Path

DOC = Path(__file__).resolve().parent.parent / "docs/PORT_PARITY.md"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("key", help="text the row's first cell starts with (backticks optional)")
    ap.add_argument("status")
    ap.add_argument("--dest", help="new Godot destination (third cell)")
    ap.add_argument("--all", action="store_true", help="change every matching row, not just the first")
    a = ap.parse_args()
    lines = DOC.read_text(encoding="utf-8").split("\n")
    key = a.key.strip("`")
    hits = 0
    for i, line in enumerate(lines):
        if not line.startswith("|"):
            continue
        cells = line.split("|")
        if len(cells) < 4:
            continue
        first = cells[1].strip().strip("`")
        if not first.startswith(key):
            continue
        # The status is always the last cell.
        cells[-2] = f" {a.status} "
        if a.dest is not None and len(cells) >= 5:
            cells[-3] = f" `{a.dest}` " if not a.dest.startswith("`") else f" {a.dest} "
        lines[i] = "|".join(cells)
        hits += 1
        if not a.all:
            break
    if hits == 0:
        print(f"no row starts with {key!r}", file=sys.stderr)
        return 1
    DOC.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print(f"{hits} row(s) set to {a.status!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
