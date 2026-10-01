"""Inventory of the build-13 Kotlin source for docs/PORT_PARITY.md.

Prints Markdown tables: every Kotlin source file with its Godot destination, every Kotlin
test file with its @Test count and Godot destination, the Sfx list and the saved keys.
Run from the repo root:  python tools/parity_inventory.py > /tmp/inventory.md
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/pocketarcade"
TEST = ROOT / "app/src/test/java/com/pocketarcade"


def snake(name: str) -> str:
    s = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name)
    s = re.sub(r"(?<=[A-Z])([A-Z][a-z])", r"_\1", s)
    return s.lower()


# Kotlin files whose job Godot's engine does, or that move somewhere other than the rule says.
SPECIAL = {
    "MainActivity.kt": "scripts/app/main.gd + android plugin (lifecycle, immersive mode, `play` extra)",
    "ArcadeApp.kt": "scripts/app/arcade_app.gd",
    "engine/gl/GlThread.kt": "Godot renderer thread (dropped: engine-owned)",
    "engine/gl/GlSurface.kt": "scripts/engine/gl/gfx.gd (SubViewport slots)",
    "engine/gl/GlGeneration.kt": "dropped: Godot owns the GL context and its loss",
    "engine/gl/RestartPolicy.kt": "dropped: Godot owns the GL context and its loss",
    "engine/gl/GlRenderer.kt": "scripts/engine/gl/gfx.gd + scripts/engine/gl/post_chain.gd",
    "engine/gl/GlShaders.kt": "shaders/scene.gdshader + shaders/post_*.gdshader",
    "engine/gl/HdrPipeline.kt": "scripts/engine/gl/post_chain.gd",
}


def dest(rel: str) -> str:
    if rel in SPECIAL:
        return SPECIAL[rel]
    p = Path(rel)
    return f"scripts/{p.parent.as_posix()}/{snake(p.stem)}.gd".replace("scripts/./", "scripts/")


def main() -> None:
    out = sys.stdout
    files = sorted(MAIN.rglob("*.kt"))
    out.write(f"### Kotlin sources ({len(files)} files, "
              f"{sum(len(f.read_text(encoding='utf-8').splitlines()) for f in files)} lines)\n\n")
    out.write("| Kotlin file | Lines | Godot destination | Status |\n|---|---:|---|---|\n")
    for f in files:
        rel = f.relative_to(MAIN).as_posix()
        n = len(f.read_text(encoding="utf-8").splitlines())
        out.write(f"| `{rel}` | {n} | `{dest(rel)}` | todo |\n")

    tests = sorted(TEST.rglob("*.kt"))
    total = 0
    rows = []
    for f in tests:
        src = f.read_text(encoding="utf-8")
        c = src.count("@Test")
        ign = src.count("@Ignore")
        total += c
        rel = f.relative_to(TEST).as_posix()
        p = Path(rel)
        g = f"tests/{p.parent.as_posix()}/{snake(p.stem)}.gd".replace("tests/./", "tests/")
        rows.append(f"| `{rel}` | {c}{' (' + str(ign) + ' @Ignore)' if ign else ''} | `{g}` | todo |\n")
    out.write(f"\n### Kotlin tests ({len(tests)} files, {total} @Test)\n\n")
    out.write("| Kotlin test file | @Test | Godot test | Status |\n|---|---:|---|---|\n")
    out.writelines(rows)

    synth = (MAIN / "engine/AudioSynth.kt").read_text(encoding="utf-8")
    body = synth[synth.index("enum class Sfx {") + 16: synth.index("}", synth.index("enum class Sfx {"))]
    sfx = [s.strip() for s in re.sub(r"//.*", "", body).replace("\n", " ").split(",") if s.strip()]
    out.write(f"\n### Sound effects ({len(sfx)})\n\n" + ", ".join(f"`{s}`" for s in sfx) + "\n")

    keys = []
    for rel in ["data/ArcadeRepository.kt", "data/SettingsStore.kt"]:
        src = (MAIN / rel).read_text(encoding="utf-8")
        for m in re.finditer(r"(\w+)PreferencesKey\(\"([^\"]+)\"\)", src):
            keys.append((rel, m.group(2), m.group(1)))
        for m in re.finditer(r"const val (\w+_PREFIX) = \"([^\"]+)\"", src):
            keys.append((rel, m.group(2) + "<gameId>", "prefix"))
    out.write("\n### Saved keys\n\n| Store | Key | Type |\n|---|---|---|\n")
    for rel, k, t in keys:
        out.write(f"| `{rel}` | `{k}` | {t} |\n")


if __name__ == "__main__":
    main()
