"""Fails if a Markdown file links to a local file that doesn't exist (bug 10: 2.0.0's
docs/KOTLIN_VERSION.md pointed its images at docs/docs/screenshots).

Checks every tracked .md file: Markdown links and images, and HTML <img src>/<a href>, resolved
relative to the file. Web links and in-page anchors are skipped. Run from the repo root:

    python tools/check_doc_links.py
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MD_LINK = re.compile(r"!?\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
HTML_LINK = re.compile(r"<(?:img|a)\b[^>]*?\b(?:src|href)\s*=\s*\"([^\"]+)\"", re.IGNORECASE)


def targets(text: str):
    # Fenced code is not links.
    text = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    for m in MD_LINK.finditer(text):
        yield m.group(1)
    for m in HTML_LINK.finditer(text):
        yield m.group(1)


def main() -> int:
    files = subprocess.run(["git", "ls-files", "*.md", "**/*.md"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    bad = []
    checked = 0
    for name in sorted(set(files)):
        md = ROOT / name
        if not md.is_file() or name.startswith("app/") or "/.worktrees/" in name:
            continue
        for t in targets(md.read_text(encoding="utf-8")):
            if re.match(r"^[a-z][a-z0-9+.-]*:", t, re.IGNORECASE) or t.startswith("#"):
                continue
            path = t.split("#", 1)[0].split("?", 1)[0]
            if not path:
                continue
            checked += 1
            if not (md.parent / path).exists():
                bad.append(f"{name}: {t}")
    for b in bad:
        print(f"broken link: {b}")
    if not bad:
        print(f"{checked} local links in Markdown files, all present")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
