"""A small driver for the one emulator (emulator-5558) used for reference shots and smoke tests.

    python tools/emu.py shot NAME             screenshot into docs/parity/ref/NAME.png
    python tools/emu.py tap X Y               tap at device pixels
    python tools/emu.py tapt TEXT             tap the first view whose text or description contains TEXT
    python tools/emu.py texts                 list the labelled views on screen (text/description and bounds)
    python tools/emu.py swipe X1 Y1 X2 Y2 MS  swipe
    python tools/emu.py key CODE              key event (4 = Back)
    python tools/emu.py wait SECONDS          wait on the device
    python tools/emu.py play GAME             start build-13's MainActivity straight into a machine

Several commands can be chained with ' ; ' in one argument list, e.g.
    python tools/emu.py tapt "TAP TO START" ; wait 3 ; shot b13_02_onboarding
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADB = str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe")
DEVICE = os.environ.get("EMU", "emulator-5558")
SHOTS = ROOT / "docs/parity/ref"


def adb(*args: str, binary: bool = False):
    r = subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name: str) -> None:
    SHOTS.mkdir(parents=True, exist_ok=True)
    data = adb("exec-out", "screencap", "-p", binary=True)
    (SHOTS / f"{name}.png").write_bytes(data)
    print(f"saved {name}.png ({len(data) // 1024} KB)")


def views() -> list[tuple[str, tuple[int, int, int, int]]]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    out = []
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        label = (m.group(1) or m.group(2)).strip()
        if label:
            out.append((label, tuple(int(v) for v in m.groups()[2:])))
    return out


def tap_text(text: str) -> bool:
    for label, (x0, y0, x1, y1) in views():
        if text.lower() in label.lower():
            adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
            print(f"tapped {label!r} at {(x0 + x1) // 2},{(y0 + y1) // 2}")
            return True
    print(f"no view with {text!r}")
    return False


def run(cmd: list[str]) -> None:
    op, args = cmd[0], cmd[1:]
    if op == "shot":
        shot(args[0])
    elif op == "tap":
        adb("shell", "input", "tap", args[0], args[1])
    elif op == "tapt":
        tap_text(" ".join(args))
    elif op == "texts":
        for label, b in views():
            print(f"{b}  {label}")
    elif op == "swipe":
        adb("shell", "input", "swipe", *args)
    elif op == "key":
        adb("shell", "input", "keyevent", args[0])
    elif op == "wait":
        time.sleep(float(args[0]))
    elif op == "play":
        adb("shell", "am", "start", "-n", "com.pocketarcade/.MainActivity", "--es", "play", args[0])
    else:
        raise SystemExit(f"unknown command {op}")


def main() -> None:
    cmd: list[str] = []
    for a in sys.argv[1:] + [";"]:
        if a == ";":
            if cmd:
                run(cmd)
            cmd = []
        else:
            cmd.append(a)


if __name__ == "__main__":
    main()
