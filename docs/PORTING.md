# Porting guide (for everyone working on the Godot port)

Pocket Arcade build-13 (Kotlin, Compose, OpenGL ES 3) is being ported to Godot 4.6.2 with typed
GDScript, faithfully: same features, rules, numbers and look. The Kotlin source in `app/` is the
spec; `docs/PORT_PARITY.md` is the contract and the record of every decision. Read both sections
of this guide that apply to you before writing code.

## 1. Ground rules

- Work only in your own worktree and branch. Before anything else run
  `git merge-base --is-ancestor <foundation-sha> HEAD` (the lead gives you the sha); if it fails,
  stop and report.
- Touch only the files you own (your brief lists them). If you need a change elsewhere, say so in
  your report instead of editing it. Never edit `app/` (it is the reference and holds the signing
  key), `docs/PORT_PARITY.md`, `.github/` or another agent's files.
- Never push, never open PRs, never touch `main` or other branches. Commit on your branch with
  `git -c user.name="Larry" -c user.email="killthegh00ul@gmail.com" commit` and end each message
  with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No emulator, no device. Verify with headless tests and the desktop capture scenes.
- No third-party addons, and nothing imported: every model, texture, glyph and sound is made in
  code, as in build-13.
- Nothing quietly simplified. When something can't be matched, do the closest faithful thing and
  write it down (section 8).

## 2. Running things

- Godot: `C:\Users\larry\Downloads\Godot_v4.6.2-stable_win64.exe\Godot_v4.6.2-stable_win64_console.exe`
  (not on PATH; the `.exe` "folder" is a directory). In Git Bash:
  `export GODOT="/c/Users/larry/Downloads/Godot_v4.6.2-stable_win64.exe/Godot_v4.6.2-stable_win64_console.exe"`.
- All tests: `bash tools/run_tests.sh` from the repo root. One file or name: `bash tools/run_tests.sh --filter=claw`.
  It imports the project first (new `class_name` scripts resolve only after an import), prints
  `N tests, M failed` and exits non-zero on any failure. `tests/scripts_compile_test.gd` loads every
  script and shader, so a parse error anywhere fails the run.
- Wrap long Godot runs in `timeout` (a parse error in a scene can hang a non-headless run).
- Before committing: `python tools/check_nul.py` (a broken write can leave NUL bytes).

## 3. Where things go

- Kotlin `app/src/main/java/com/pocketarcade/<pkg>/<Name>.kt` becomes
  `godot/scripts/<pkg>/<snake_name>.gd` with `class_name <Name>` (snake: `WhackAMoleGame` →
  `whack_a_mole_game`). Kotlin tests `app/src/test/.../<pkg>/<Name>Test.kt` become
  `godot/tests/<pkg>/<snake_name>_test.gd`, one `func test_<snake_name>()` per `@Test`.
- A Kotlin file with several top-level classes: the main one gets the file; small helpers become
  inner classes (`CircleWorld.Body`), or files of their own when other packages use them.
- Names Godot already has get a `Pa` prefix (`PaCamera3D`, `PaTexture`, `PaPaint`, `PaPath`).
- Constants keep their Kotlin names in UPPER_CASE; properties and functions become snake_case.
  Kotlin `val` properties of an interface are plain fields set in `_init` (see `MiniGame`).

## 4. GDScript rules learned the hard way

- No named arguments: pass every argument positionally in Kotlin's declaration order (look the
  signature up; defaults in the middle must be written out).
- Built-in names can't be reused: no variables called `seed`, `range`, `clamp`, `signal`, `pass`;
  `Xform.set` is `set_xf`. A function and a property can't share a name (`SceneFx.flare` the texture
  is `flare_tex()`, `SceneFx.shaft(...)` the drawing helper is `shaft_beam(...)`).
- Kotlin `Int` overflows; GDScript ints are 64-bit. Hashes and random arithmetic that rely on 32-bit
  wrap use `MathUtil.i32`, `ushr32`, `shl32`. Colours are ARGB ints kept positive (`0xFF120A24`);
  Kotlin's `-1` "no tint" is handled by `Pal.argb()`.
- Kotlin `roundToInt` is `MathUtil.round_to_int` (half up, not Godot's half away from zero for
  negatives). Use `floorf`/`floori`, `ceilf`/`ceili`, `absf`, `minf`, `clampf`: the untyped
  `floor`/`abs` return Variants.
- Kotlin `Float` is 32-bit, GDScript `float` 64-bit. Where an index or count comes from float
  arithmetic, compare with build-13's values in a test; emulate 32-bit with a one-element
  `PackedFloat32Array` only where it matters (see `SfxBank._frames`).
- `kotlin.random.Random(seed)` is `KRandom.new(seed)` (bit-exact: `next_int`, `next_int_until(n)`,
  `next_int_range(a, b)`, `next_float`, `next_double`, `range_f(a, b)` for the `Random.range`
  extension, `chance(p)`, `pick`, `shuffle`). Seeded rounds must draw the same numbers as build-13.
- Lambdas capture locals by value: to change a counter inside one, box it (`var n := [0]`).
- Packed arrays are shared through a local variable but copied when stored in an `Array`, a
  Dictionary or a typed array element: write the local back after changing it.
- `match` can't use another class's enum values as patterns: use if/elif.
- Don't allocate in per-frame or per-step loops of hot code (the coin pusher, the claw pile,
  pinball, fishing): reuse objects and packed arrays, prefer typed locals.

## 5. The engine (already ported, in `godot/scripts/engine`)

- `r3d/`: `Renderer3D` (build-13's immediate-mode renderer: `begin/vertex/end`, `quad`, `sprite`,
  `billboard`, `beam`, `flat`, `decal`, `draw_model`), `Model`/`ModelBuilder` (`box`, `cylinder`,
  `sphere`, `capsule`, `disc`, `quad`..., `add_xf`), `Xform`, `BoxFaces`, `Region`, `PaTexture`,
  `TexKit`, `TexPaint` (procedural textures painted by Godot's 2D renderer: `TexPaint.paint_texture`),
  `Lighting`/`PointLight`, `PaCamera3D`, `Stage3D` (a game's 3D view: `look`, `begin`, `present`,
  field/world mapping), `GameViewport` (where the game field sits on screen).
- `gl/`: `Gfx` (slots the 3D pictures are shown in), `GfxSlot`, `GfxQuality`, `FrameGate`,
  `HdrLook`, `HdrMath`, the scene shader. Post-processing (bloom, grade...) is being ported.
- `ui2d/`: `DrawScope` (Compose's DrawScope: `draw_rect`, `draw_circle`, `draw_round_rect`,
  `draw_path`, `draw_arc`, `draw_line`, `draw_image`, `push/pop/translate/scale_by/rotate_deg/clip_rect`),
  `PaBrush` (gradients), `PaPath`, `PaStroke`.
- `ArcadeFont` (the arcade type and its icons: `draw`, `draw_centered`, `draw_shadowed`, `width`),
  `Fonts`, `Pal` (the palette), `MathUtil`, `KRandom`, `Display` (dp sizing, safe area).
- Juice: `Particles`, `ScreenShake` (`ScreenShake.intensity` is 0 under reduce motion),
  `FloatingTexts`, `Flash`, `Spring`, `PunchSpring`, `TimeScale`, `SimClock`, `GameLoop.FIXED_DT`
  (1/120 s).
- Input: `TouchType` (DOWN, MOVE, UP), `FlickTracker` (`velocity()` returns a Vector2),
  `TouchInput`, `TiltMath`, `TiltSteer`.
- Sound: `AudioSynth` (`play(sfx, volume, pitch)`, `play_at(sfx, x, z, volume, pitch)`,
  `enter_scene`, `music`), `Sfx` (the 63 sounds, build-13's names), `MusicScene`, `Stinger`,
  `RoundMusic`. Haptics: `Haptics` (`tick`, `hit`, `heavy`, `soft`, `bump`, `rumble(level)`, `win`,
  `jackpot`).
- `CircleWorld` (`engine/Physics.kt`: `CircleWorld.Body`, `CircleWorld.Segment`), `Painter`.
- Data (`scripts/data`): `ArcadeRepository`, `SaveState`, `Catalog`, `GameSettings`,
  `SettingsStore`, `ScoreTables`, `TokenGate`.

## 6. The game contract (`godot/scripts/games`)

- `MiniGame`: `id`, `title`, `marquee`, `instructions`, `look` (`MiniGame.CabinetLook.new(body,
  trim, glow, MiniGame.CabinetShape.X)`), `round_seconds`, `score`, `bonus_tickets`, and
  `cabinet()`, `draw_attract(p, w, h, t)`, `start(fx)`, `update(dt, time_left)`, `draw(scope)`,
  `on_touch(type, id, x, y, time_ms)`, `cancel_input()`, `finished()`, `tickets_for(score)`.
- `BaseMiniGame`: implement `reset()`, `step(dt)`, `render(scope)`, `cancel_input()`, and override
  `on_time_up()` / `is_settled()` as the Kotlin does. It owns `particles`, `shake`, `popups`,
  `flash`, `rng`, `time`, `time_left`, `time_up`, `ended_early`, `fixed_seed`, and the helpers
  `add_score`, `play`, `hit_stop`, `slow_mo`, `punch`, `impact`, `big_moment`.
- `GameFx`: `audio`, `haptics`, `collect(item_id)` (Kotlin's `onCollectible`), and the feel requests.
- Tilt-steered games (Kotlin's `TiltControlled`) have a `tilt_steering` property and `on_tilt(lean)`.
- `GameRegistry` loads each machine by path (`scripts/games/<pkg>/<name>_game.gd`), so the project
  runs while games are missing. Keep the file names it lists.
- `SceneFx` (`scripts/games/scenea`) is shared by the claw, skee-ball, whack-a-mole and pusher scenes.
- Tests: `tests/games/sim_harness.gd` (`SimHarness`) is build-13's SimHarness: `RoundDriver`,
  `play_round`, `flick`, `flick_no_move`, `tap`, `gaussian`, `assert_payout_bands`, `Stats`.
- A machine's custom hall cabinet (`*Cabinet.kt`) depends on the hall's cabinet kit
  (`CabinetDesign`, `CabinetBuild`, `HallArt`, `MachineKit`, `MachineArt`), which the hall agent is
  porting. Those four cabinet files are a second step: leave them until the lead says the kit is in.

## 7. Tests

- Port every Kotlin `@Test` in your area, keeping its name (snake_case) and its assertions. Only
  Android/Compose/GL-internal tests may be dropped, and each drop is reported with a reason.
- Deterministic only: seeded `KRandom`, fixed steps (`GameLoop.FIXED_DT`), no wall clock, no
  sleeping; `await frames(n)` only to let layout or the scene tree settle.
- Test base class `PaTest` (`tests/framework/pa_test.gd`): `assert_true/false`, `assert_eq` (strict:
  an int never equals a float), `assert_ne`, `assert_near(expected, actual, tolerance)`,
  `assert_gt/ge/lt/le`, `assert_null/not_null`, `assert_is_int`, `assert_has/not_has`,
  `expect_error(fragment)` for an engine error a test provokes on purpose; `host` is a scratch
  node emptied after each test, `tree` the scene tree.
- Godot-only tests (things that can break in the port) are welcome; say which ones they are.

## 8. Visual checks and references

- build-13 reference screenshots (1080 × 2400, 420 dpi): `C:\Users\larry\Documents\New project\pocket-arcade\docs\parity\ref\b13_*.png`
  (not in git; read them from that absolute path). More are added as the port goes.
- Render your work non-headless at 1080 × 2400 (and 1080 × 1920) with a temporary capture scene in
  `godot/tools/capture/` (see `render_probe.gd`, `paint_probe.gd`, `game_probe.gd`), e.g.
  `"$GODOT" --path godot --resolution 1080x2400 res://tools/capture/game_probe.tscn -- --game=claw --out=C:/.../claw.png`.
  Look at the pictures; compare with the references.
- Draw calls: share materials and meshes (MeshKit, one material per texture/blend/cull), use
  MultiMesh for repeated models; `GfxSlot.measured_draw_calls()` reports a frame's count.

## 9. Reporting

When you finish (or run out of time), write `docs/parity/notes/<your-agent-name>.md` (yours alone)
with: every Kotlin file you ported → Godot file and status (done / partial / dropped + reason);
every Kotlin test → Godot test (count) or the reason it was dropped; every deviation (what differs
and why); what you checked visually; anything left to do. Commit it with your work. Your final
message: the branch, the last commit sha, the test count (`N tests, M failed` from a full run),
and the same summary in a few lines.
