#!/usr/bin/env bash
# The one test command:  tools/run_tests.sh [--filter=<substring>]
# Imports the Godot project (so every class_name is registered), then runs every
# godot/tests/**/*_test.gd headless. Exits non-zero if any test fails.
set -euo pipefail
cd "$(dirname "$0")/.."
GODOT="${GODOT:-godot}"
if ! command -v "$GODOT" >/dev/null 2>&1; then
  for c in "/c/Users/larry/Downloads/Godot_v4.6.2-stable_win64.exe/Godot_v4.6.2-stable_win64_console.exe"; do
    [ -x "$c" ] && GODOT="$c" && break
  done
fi
"$GODOT" --headless --path godot --import >/dev/null 2>&1 || true
"$GODOT" --headless --path godot res://tests/run_tests.tscn -- "$@"
