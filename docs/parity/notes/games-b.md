# Hoop shot, air hockey and the stacker (agent: games-b)

Branch `port/games-b`. Three machines ported from build-13 with their rules, physics, scoring,
input, attract screens, painted art and 3D scenes; every Kotlin test of the area ported; the hall's
`CabinetForm.beveled_box` in use since the hall kit landed.

## Files

| Kotlin | Godot | Status |
|---|---|---|
| `games/hoops/HoopsGame.kt` (`HoopsGame`) | `godot/scripts/games/hoops/hoops_game.gd` | done |
| `games/hoops/HoopsGame.kt` (`HoopsTuning`) | `godot/scripts/games/hoops/hoops_tuning.gd` | done (a file of its own: the cross-game tests read it) |
| `games/hoops/HoopsScene.kt` (`HoopsScene`) | `godot/scripts/games/hoops/hoops_scene.gd` | done |
| `games/hoops/HoopsScene.kt` (`HoopsGeo`, `HoopsLook`) | `godot/scripts/games/hoops/hoops_geo.gd`, `hoops_look.gd` | done |
| `games/hoops/HoopsArt.kt` (`HoopsArt`, `Readout`) | `godot/scripts/games/hoops/hoops_art.gd` (`HoopsArt.Readout`) | done |
| `games/airhockey/AirHockeyGame.kt` (`AirHockeyGame`) | `godot/scripts/games/airhockey/air_hockey_game.gd` | done |
| `games/airhockey/AirHockeyGame.kt` (`HockeyTuning`) | `godot/scripts/games/airhockey/hockey_tuning.gd` | done |
| `games/airhockey/HockeyScene.kt` (`HockeyScene`) | `godot/scripts/games/airhockey/hockey_scene.gd` | done |
| `games/airhockey/HockeyScene.kt` (`HockeyGeo`, `HockeyLook`) | `godot/scripts/games/airhockey/hockey_geo.gd`, `hockey_look.gd` | done |
| `games/airhockey/HockeyArt.kt` (`HockeyArt`, `Scoreboard`) | `godot/scripts/games/airhockey/hockey_art.gd` (`HockeyArt.Scoreboard`) | done |
| `games/stacker/StackerGame.kt` (`StackerGame`) | `godot/scripts/games/stacker/stacker_game.gd` | done |
| `games/stacker/StackerGame.kt` (`StackerTuning`) | `godot/scripts/games/stacker/stacker_tuning.gd` | done |
| `games/stacker/StackerScene.kt` (`StackerScene`, `StackerLook`) | `godot/scripts/games/stacker/stacker_scene.gd`, `stacker_look.gd` | done |
| `games/stacker/StackerArt.kt` | `godot/scripts/games/stacker/stacker_art.gd` | done |

Every constant group was compared name by name and value by value with the Kotlin objects
(script, not by eye): `HoopsTuning` 15, `HoopsGeo` 20, `HoopsLook` 67, `HockeyTuning` 15,
`HockeyGeo` 14, `HockeyLook` 38, `StackerTuning` 17, `StackerLook` 31, all present and equal.
Companion constants (trail sizes, burst pools, colours, `SUBSTEPS`...) keep their names on the
classes that had them. Bot hooks are methods: `bot_shots()`, `bot_puck_x()`..., `bot_goals()` (a
`Vector2i` for Kotlin's pair), `bot_place_puck()`, `bot_screen()` (a `Vector2`), `bot_delta()`,
`bot_moving()`, `bot_height()`.

## Tests (32)

| Kotlin | Godot | Count |
|---|---|---|
| `games/hoops/HoopsSceneTest.kt` (3) | `godot/tests/games/hoops/hoops_scene_test.gd` | 3 |
| `games/airhockey/AirHockeyBuzzerTest.kt` (3) | `godot/tests/games/airhockey/air_hockey_buzzer_test.gd` | 3 |
| `games/airhockey/HockeySceneTest.kt` (3) | `godot/tests/games/airhockey/hockey_scene_test.gd` | 3 |
| `games/stacker/StackerSceneTest.kt` (4) | `godot/tests/games/stacker/stacker_scene_test.gd` | 4 + 1 Godot-only |
| `games/GameSimulationTest.kt` bots `hoops`, `hockey`, `stacker` | `*_simulation_test.gd` in each folder | 2 each (payout bands; same seed, same score, on a reused machine) |

Nothing dropped: no test of the area touches Android, Compose or GL internals. The scene tests
also check that nothing gets painted (`PaintPump.pending()` unchanged).

Godot-only tests (marked in their files):

- `stacker_scene_test.gd::test_slab_colours_match_build_13`: `StackerArt.hsv` keeps build-13's
  Float arithmetic; body and glow colours of levels 0..29 equal what build-13 printed, bit for bit.
- Parity with build-13's own numbers, played through `godot/tests/games/f32_round_driver.gd` (a
  round driver keeping build-13's Float clock: `t += 1f/120f`, `(t * 1000f).toLong()`):
  `stacker_parity_test.gd` (round seed 145, the good bot: all 46 drops on the same step with the
  same score), `hoops_parity_test.gd` (round seed 97: score and shots identical every second for
  34 s), `air_hockey_parity_test.gd` (the CPU alone for 12 s, the touch-to-table mapping, and the
  good bot's mallet chase for its first 72 steps, all to 1e-3).
- `*_round_test.gd`: InputCancelTest's and ReplayResetTest's cases for these machines (a pause lets
  go of the pointer; PLAY AGAIN replays like a fresh instance, the air hockey chaser included), so
  the machines are covered on this branch before the cross-game files are ported.

The reference numbers came from scratch runs of build-13's own classes on the JVM (a copy of
`app/` outside the repo with one extra test that printed them; `app/` itself untouched).

## Build-13 against the port

Payout simulation, build-13's GameSimulationTest seeds (12 rounds per bot):

| Bot | build-13 score / tickets (min, max) | Godot score / tickets (min, max) |
|---|---|---|
| hoops noise 4% | 757.5 / 39.3 (25, 54) | 689.9 / 36.1 (25, 51) |
| hoops noise 14% | 117.7 / 7.4 (4, 13) | 116.6 / 7.3 (4, 12) |
| hockey lag 50 ms | 750.0 / 16.8 (6, 32) | 1060.4 / 22.9 (11, 32) |
| hockey lag 300 ms | 362.5 / 9.2 (2, 16) | 329.2 / 8.4 (2, 27) |
| stacker sigma 8 | 712.9 / 36.2 (23, 59) | 747.9 / 38.0 (20, 59) |
| stacker sigma 22 | 184.2 / 9.8 (5, 17) | 185.8 / 9.9 (5, 18) |

Twelve rounds of air hockey are noisy (one round's score spread is ±400), so 100 rounds per bot
were run on both sides with the same seeds (scratch runs, not in the suite):

| Bot (100 rounds) | build-13 mean ± sd | Godot mean ± sd |
|---|---|---|
| hoops good | 807.8 ± 167.5 | 797.0 ± 173.7 |
| hoops casual | 109.9 ± 44.8 | 110.0 ± 40.4 |
| hockey good (goals for-against) | 810.8 ± 412.8 (508-96) | 777.5 ± 398.5 (493-102) |
| hockey casual | 301.3 ± 209.5 (266-541) | 321.8 ± 197.1 (291-570) |
| stacker good | 737.3 ± 207.3 | 741.1 ± 224.5 |
| stacker casual | 161.4 ± 45.5 | 165.9 ± 49.6 |

All within a standard error. With the Float clock, 6 of the 12 good-bot hoops rounds give exactly
build-13's score and the other 6 differ by one or two baskets; every step count matches.

## Deviations

- **64-bit simulation.** The games run in GDScript floats where build-13 used Kotlin Floats. Under
  build-13's clock the seeded rounds replay its numbers (above) until a borderline event: the hoops
  round clock and ball maths can turn a rim-out into a make once the hoop moves; in air hockey the
  serve delay counted down in Floats goes live one step later than in doubles, so the first contact
  (and everything after) differs. Emulating Floats through the whole physics would cost a function
  call per operation in hot loops for no gameplay difference; the statistics match. (The lead's
  `SimHarness` keeps a double clock, so the suite's own simulation tests are not seed-identical to
  build-13's table.)
- **Float arithmetic kept where an integer depends on it.** `StackerArt.hsv` (colour channels),
  the city texture's window counts, the court's plank joints, the crowd's heads per row and the
  stacker attract's skyline step along in emulated Floats (`_f32`), so counts and cuts match
  build-13 exactly.
- **Live textures repaint from a fresh recording.** `HoopsArt.Readout` and `HockeyArt.Scoreboard`
  clear their TexPaint before each repaint (the port's painter keeps a recording; build-13 painted
  over its bitmap). The first operation is an opaque gradient over the whole texture, so the
  picture is the same.
- **Gradient paints are white.** The ring textures set the shaded paint's colour to white after
  `reset()`: Android ignores a shaded paint's RGB (only its alpha, 255 after a reset, counts), but
  the port's `PaintLayer` multiplies the gradient by the whole colour, so the reset's black would
  paint the ring black. Same picture as build-13.
- Kotlin's `-1` colours (`label(..., -1)` inside glows) are written `Pal.WHITE` (the same value).
- `pt` scratch arrays are `_pt_x` / `_pt_y`, which keep their last value when a projection fails,
  as the Kotlin array did.
- `HoopsArt.pad` is ported though nothing draws it (build-13 painted it lazily and never used
  it either); `StackerScene.draw_world` still fetches the shaft texture build-13 fetched there
  into an unused local, which paints it before the first perfect drop needs it.

## Visual checks

Captures (non-headless, 1080 × 2400 requested; this desktop clamps the window to 1080 × 2119,
which still shows the whole 1080 × 1920 field) with `godot/tools/capture/games_b_probe.tscn`
(plays a machine with build-13's good bot; `--attract` draws an eight-frame contact sheet of the
attract loop through the hall's LiveScreen, 24 × 18 pixels and 16 × 24 for the tower) and
`games_b_art_probe.tscn` (every painted texture of the three machines on a checkerboard):

- Hoops at 7.3 s (on fire, ×5, "ON FIRE! X5", balls returning), at 26 s (the hoop moving, 20/26),
  and at rest: arena wall with the painted crowd, ad boards, HOOP SHOT neon, MAKES and MULT
  readouts, floodlights, rail, board with LED frame and markings, rim and woven net, cage nets,
  court with key and arcs. Every texture checked on its own (floor, crowd, logo core and halo,
  ad boards, steel, pad, board face and marks, glint, rim, cage net, ring, flare, shaft, readouts,
  ball skin).
- Air hockey at 6.6 s and 12 s: LED wall, tiled floor, scoreboard with pips, neon table with
  chamfered rails and corners, mallets with glow rings, puck with its trail, hit shockwave, wall
  flare and sparks. Textures: surface, rail, body, slot, post, floor tile, wall, both mallet skins,
  puck side and top, ring, flare, scoreboard.
- Stacker at 3 s and 20 s: rooftop with neon rings and chamfered parapet, the city and cloud decks
  far below, height markers, the rainbow tower with neon edges, the sliding slab, perfect-drop ring,
  flare and beam, HUD (height, hearts, PERFECT x2). Textures: slab side, city, cloud, roof, steel,
  ring, flare, shaft, markers.
- Attract loops: hoops (shot arcs, swish, +13, rim flare), air hockey (title card, ricochets,
  goal flash with sparks and GOAL!), stacker (slabs sliding in and landing, overhangs falling).

build-13 has no reference screenshots of these three screens (`docs/parity/ref` has the whack
round only; the hall shots show these cabinets too small to compare), and the README's
`hoops.png`, `airhockey.png` and `stacker.png` predate build-13's redesign of the scenes. The
pictures were checked against the scene code and those older shots for framing (camera, field
layout); a build-13 capture of each round on the emulator would allow a side-by-side.

Draw calls (planned / measured by `GfxSlot`): hoops 31-38 (34/31 typical), air hockey 22-25,
stacker 11-24 (it grows with the tower: 26 courses drawn). The lead's `game_probe.tscn` with
`--seconds=6 --taps` reports hoops 31/30, air hockey 23/23, stacker 13/10 (its random taps topple
the tower at once).

The attract loops were also drawn through the hall's own `LiveScreen` (MachineArt, CanvasPainter,
scanline glass, the HIGH SCORE card): `games_b_probe --attract` uses it, at the hall's screen sizes.
On this desktop the system font standing in for Roboto Bold is wider, so "STACK" and the tower's
HIGH SCORE card overrun the 16-pixel-wide screen by a few texels; on Android (Roboto) they fit.

Costs on this desktop (RTX 3090 machine; a phone is several times slower): a simulation step (with
the bot) takes 47 µs for hoops, 33 µs for air hockey, 10 µs for the stacker, headless. Recording
a hoops frame takes about 1.1 ms in the scene's own calls (60 net strands and ~90 sprites, beams
and quads through Renderer3D's immediate mode), and `Renderer3D.finish_frame` another 1.7 ms,
mostly the light grid (hoops' lamp has a 760 radius, so its 48 × 48 cells are walked every frame).

## Open items for others

- Engine (`PaintLayer._draw`): a paint with a shader should modulate the gradient by its alpha
  only (`Color(1, 1, 1, a)`), as Android does; the art here works round it (white paints).
- Engine (`Renderer3D._pack_lights`): the per-frame light grid costs ~1.7 ms on a desktop core
  with build-13's big-radius lamps; worth optimising (clear only touched cells, or cache the grid
  when the lights don't move: they don't, in hoops, except the accent and spot following the hoop).
- `docs/PORT_PARITY.md`: the nine Kotlin files and four test files above can be marked done, with
  the file splits listed under Files.
- Seen in passing on trunk (games-a's area, not touched here): `coin_pusher_sim_test.gd` fails its
  "skill should pay" band (good 236.7 vs casual 256.7), while build-13's table had 268.3 vs 230.0.
