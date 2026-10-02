# Pocket Arcade: Godot port parity

The contract for porting Pocket Arcade **build-13** (tag `build-13` = `58d6ce7`, the Kotlin app in
`app/`) to Godot 4.6.2 (GDScript, Compatibility renderer) in `godot/`. The Kotlin source is the
spec. Every feature, screen, mechanic, constant group, setting, save key, sound, visual effect,
Kotlin source file and Kotlin test file is listed here with where it lives in Godot and its status.

**Status:** `todo` · `wip` · `done` (with evidence: a test or a screenshot) · `dropped` (with the
reason) · `deviation` (done differently, with the reason). Nothing is simplified silently: anything
that can't be matched is recorded under [Deviations](#deviations-and-decisions).

## Layout and conventions

- One Kotlin file maps to `godot/scripts/<package>/<snake_case>.gd`. A Kotlin file declaring several
  top-level classes splits into sibling files named after them (recorded in the file map below).
- Kotlin tests map to `godot/tests/<package>/<snake_case>.gd` with one `func test_*()` per `@Test`
  (camelCase name → snake_case). Godot-only tests are marked as such in their file.
- Porting rules (positional arguments, 32-bit ints, ARGB colours, Kotlin `Random`, names that
  collide with Godot built-ins...) are in [docs/PORTING.md](PORTING.md).
- One command runs every test headless: `tools/run_tests.sh` (non-zero exit on failure).

## 1. Screens and flows

| Item | Kotlin | Godot | Status |
|---|---|---|---|
| App shell: immersive full screen, keep screen on, portrait, `Pal.NIGHT` window, launch extra `play=<id>` (pays a token, skips the title) | `MainActivity.kt`, `AndroidManifest.xml` | `scripts/app/main.gd`, `scripts/app/arcade_services.gd`, `android-plugin/` | done: shell, services, migration on start, Back routing, pause/resume, `play` extra read (walking into the machine: app flow), the 3D layer under the UI; verified on the emulator |
| Top-level flow TITLE → HUB ↔ GAME, overlays (prizes, tokens, profile, photo, map, settings), banner (3.2 s), layer order, `busy` guards | `ArcadeApp.kt` | `scripts/app/arcade_app.gd` | todo |
| Back: overlay → close + BLIP; game → host `onExitPressed` (intro: exit + refund, results: leave, paused: resume, playing/countdown/ending: pause); title/hall with nothing open → app to background (Android default) | `ArcadeApp.kt`, `GameHostScreen.kt` | `scripts/app/main.gd` (root window's go_back_requested), `scripts/ui/game_round.gd`, `scripts/app/arcade_app.gd` | host and shell done (`tests/bugs/back_button_test.gd`, `tests/ui/game_round_test.gd`), app to background verified on the emulator; overlays: app flow |
| Loading screen: synthwave sky, stars, grid, neon POCKET ARCADE sign, spinning coin, progress bar + shimmer, labels with dots, 7 rotating tips, fade-out | `ui/LoadingScreen.kt` | `scripts/ui/loading_screen.gd` | todo |
| Load plans and GPU warm-up: boot plan (paint showroom, wheel in, warm GPU 8 s), hall plan (carpet → walls → neon → hall → machines → furniture → crowd → prize wall → warm GPU 15 s), frame budgets | `startup/*`, `hub/TitleUnits.kt`, `hub/HallStage.kt`, `engine/gl/Warmup.kt` | `scripts/startup/*` | todo |
| Title: 3D showroom of every cabinet on a spline camera path, neon sign ignition timeline (letters, flicker, breath, sweep, buzz), tagline, stars, dust, save line, welcome copy, version, TAP TO START | `ui/TitleScreen.kt`, `ui/TitleTimeline.kt`, `hub/TitleShowcase.kt`, `hub/ShowroomPath.kt` | `scripts/ui/title_screen.gd`, `scripts/ui/title_timeline.gd`, `scripts/hub/title_showcase.gd` | todo |
| Title → hall handoff: exit push, dark wait for the hall, doorway glow, camera entrance, HUD fade-in (calm variants) | `ui/Handoff.kt` | `scripts/ui/handoff.gd` | todo |
| Onboarding / tutorial: WALK → LOOK → PLAY → PRIZES, coach card, check/outro, guide arrow (on/off screen/behind), ghost touch demo, skip step/end, auto-start policy, replay from Settings | `ui/Onboarding.kt`, `ui/TutorialSteps.kt` | `scripts/ui/onboarding.gd`, `scripts/ui/tutorial_steps.gd` | todo |
| Daily bonus: +10 tokens per local calendar day, card with bouncing coins, counter bump, sounds, calm variant | `ui/DailyBonus.kt`, `data/ArcadeRepository.kt` | `scripts/ui/daily_bonus.gd` | todo |
| Hall HUD: currency pill (rolling numbers, low-token pulse), view toggle, profile, mute; second row map + settings; HUD fade | `ui/Hud.kt` | `scripts/ui/hud.gd` | todo |
| Map: floor plan, props by colour, labelled machine banks, pins (tokens, prizes, photos, café, doors), you marker, legend, tap to walk | `ui/MapScreen.kt` | `scripts/ui/map_screen.gd` | todo |
| Profile: arcade name plaque, stat tiles, high scores with emblems and play counts, plush collection | `ui/ProfileScreen.kt` | `scripts/ui/profile_screen.gd` | todo |
| Token machine: kiosk art with chase bulbs, daily refill ring, trade 40 tickets → 1 token, spare token when broke (3 min cooldown) | `ui/TokenMachineScreen.kt` | `scripts/ui/token_machine_screen.gd` | todo |
| Prize counter: tabs HATS/STYLE/DECOR/PLUSH, turntable preview, rarity, item states, buy/equip, plush collection grid | `ui/PrizeCounterScreen.kt` | `scripts/ui/prize_counter_screen.gd` | todo |
| Settings: every row (controls, racer, comfort, sound, graphics, help, reset), steppers, cycles | `ui/SettingsScreen.kt` | `scripts/ui/settings_screen.gd` | todo |
| Photo booth: 4 poses, countdown, flash, studio renders, strip composition, save (keep newest 4), photo wall, share sheet | `ui/PhotoBoothScreen.kt`, `ui/PhotoBoothPlan.kt`, `share/*`, `hub/PhotoWall.kt` | `scripts/ui/photo_booth_screen.gd`, `scripts/share/*`, `scripts/hub/photo_wall.gd`, `android-plugin/` | todo |
| Game host: intro card, countdown + GO, round clock, last-5 ticks, time up, ENDING (≥1.2 s and 144 steps), pause card, results (score count-up, best line / NEW HIGH SCORE slam, grade stamp S/A/B/C, ticket printer, flights into the counter, tap to skip), play again (token gate), bezel bulbs, top bar | `ui/GameHostScreen.kt`, `ui/ResultsPlan.kt` | `scripts/ui/game_round.gd` (the round), `scripts/ui/game_host_screen.gd` (drawing, input), `scripts/ui/results_plan.gd` | round and drawing done (`tests/ui/game_round_test.gd` 11, `results_plan_test.gd` 10; captured at every phase); the cards' buttons and the currency flights move to the UI kit's widgets when it lands |
| Rolling numbers (odometer), currency flights (token/ticket arcs), banner | `ui/RollingNumber.kt`, `ui/CurrencyFx.kt`, `ui/Widgets.kt` | `scripts/ui/rolling_number.gd`, `scripts/ui/currency_fx.gd`, `scripts/ui/widgets.gd` | todo |
| Theme and widgets: palette, UiColors/Space/Radius/Edge/Text/Glow, panel texture, ArcadePanel, GlassBox, ArcadeButton (lip, gloss, press spring), RoundButton, chips, progress bar, countdown ring, toggle, entrances, token/ticket art, 14 vector icons | `ui/UiTheme.kt`, `ui/Widgets.kt`, `ui/UiParts.kt`, `ui/UiIcons.kt` | `scripts/ui/ui_theme.gd`, `scripts/ui/widgets.gd`, `scripts/ui/ui_parts.gd`, `scripts/ui/ui_icons.gd` | todo |
| Thumbnails / studio renders (figures, plushies, decor) | `ui/Thumbs.kt` | `scripts/ui/thumbs.gd` | todo |

## 2. Games

All 11 machines keep their ids, titles, marquees, round lengths, cabinet looks, instructions, rules,
tuning objects, ticket formulas and attract screens. Detailed per-game checklists (every mechanic,
constant group with counts and key values, input, visuals, sounds, randomness) are kept in the
inventory appendix, [docs/parity/GAMES.md](parity/GAMES.md).

| Game | id | Round | Tickets | Tuning groups | Godot | Status |
|---|---|---|---|---|---|---|
| Claw machine | `claw` | 45 s | `2 + score/10` | `ClawTuning` (19), companion (43), `PlushShade` (3) | `scripts/games/claw/` | todo |
| Whack-a-mole | `whack` | 45 s | `1 + score/30` | `WhackTuning` (16), companion (35), `Moles` (2) | `scripts/games/whackamole/` | todo |
| Skee-ball | `skeeball` | 40 s | `1 + score/50` | `SkeeTuning` (16), companion (45), `SkeeArt` (1) | `scripts/games/skeeball/` | todo |
| Hoop shot | `hoops` | 44 s | `2 + score/20` | `HoopsTuning` (15), `HoopsGeo` (20), `HoopsLook` (67), companions | `scripts/games/hoops/` | todo |
| Coin pusher | `pusher` | 50 s | `1 + score/15` + 10 per ticket bundle | `PusherTuning` (22), companion (34) | `scripts/games/coinpusher/` | todo |
| Air hockey | `airhockey` | 60 s | `2 + score/50` | `HockeyTuning` (15), `HockeyGeo` (14), `HockeyLook` (38), companions | `scripts/games/airhockey/` | todo |
| Turbo racer | `racer` | 72 s | `1 + score/40` (payout fix 72cefaa: drift drag, 350 units/point, road-only distance, grid row) | `RacerTuning` (42), `DriftButton` (4), companion (28), `RacerLook` (19) | `scripts/games/racer/` | todo |
| Stacker | `stacker` | 60 s | `1 + score/20` | `StackerTuning` (17), `StackerLook` (31) | `scripts/games/stacker/` | todo |
| Shootout | `shooter` | 55 s | `2 + score/400` + 5 on a boss kill | `ShooterTuning` (45), `ShooterWorld` (29), companion (57) | `scripts/games/shooter/` | todo |
| Star flipper (pinball) | `pinball` | 60 s | `1 + score/60` | `PinballTuning` (51), `PinballTable` (50 + walls), companion (65), `DotMatrix` | `scripts/games/pinball/` | todo |
| Gone fishing | `fishing` | 55 s | `2 + score/10` | `FishingTuning` (68), `Crank` (5), companion (39) | `scripts/games/fishing/` | todo |
| Shared: `MiniGame`, `BaseMiniGame` (juice, rng, clock, addScore, hitStop/slowMo/punch/impact/bigMoment), `GameFx`, `CabinetLook`/`CabinetShape`, `GameRegistry` order, `SceneFx` | | `scripts/games/*.gd`, `scripts/games/scenea/` | todo |

Per game, the checklist rows to close are: identity · every mechanic · ticket formula + bonus
tickets · every tuning constant (checked by `tools/parity_constants.py`) · input (incl. multi-touch,
flicks from historical samples, `cancelInput`) · 3D scene (models, lights, camera, effects) · 2D
HUD · particles/popups/shake/flash · attract screen · cabinet (built-in shape or `CabinetDesign`) ·
sounds and haptics · seeded randomness (Kotlin `Random` sequences).

## 3. The hall

Detailed checklist: [docs/parity/HALL.md](parity/HALL.md).

| Item | Kotlin | Status |
|---|---|---|
| Floor plan: `HubLayout` dimensions, 13 slots with bank assignment and spares, walls, prize corner, pillars, family floor, entrance, walk grid, aisle hangouts, spots and `SpotType` handlers, wall signs, posters | `hub/HubMap.kt` | todo |
| Collision: overhead feet box, first-person body (sub-steps, push-out, slide round), kid vs player | `hub/Collision.kt` | todo |
| Player and controls: overhead stick, first-person stick + look (sensitivity, invert, left-handed), run latch, assist, tap-to-walk with ray casts, walk routes, bumps, footsteps | `hub/Player.kt`, `hub/HubWorld.kt`, `hub/Joystick.kt`, `hub/WalkRoute.kt` | todo |
| Cameras: overhead follow, first person (FOV setting, head bob, run FOV kick, blend), title entrance, dive into a cabinet | `hub/Camera.kt` | todo |
| NPCs and kids: 16 kids, state machine, café queue, give way, gaze, emotes (wave, celebrate), purchase cheer | `hub/Npc.kt`, `hub/Emotes.kt` | todo |
| Figure animation: gait, pose blender, `FigureAnim` springs and follow-through, figures, looks, hats, shadows | `hub/FigureAnim.kt`, `hub/Gait.kt`, `hub/PoseBlender.kt`, `hub/AnimMath.kt`, `hub/Figures.kt`, `hub/FigureShadow.kt` | todo |
| Café: layout, barista state machine, slush, steam, pendants, art | `hub/Cafe*.kt` | todo |
| Props and decor: every `PropKind`, 9 `DecorStyle`s with lights, prize wall with won/silhouette plushies, photo wall, disco spots, decor shades | `hub/Props.kt`, `hub/PrizeWall.kt`, `hub/PhotoWall.kt`, `hub/HallKit.kt` | todo |
| Lighting rig: 45 downlights + neon + street lamps, per-frame light picking (64), flicker, trusses/rails/spots with beams and pools, murals, racer sign, entrance chase bulbs | `hub/HallRig.kt`, `hub/HallKit.kt`, `hub/HallScene.kt`, `hub/HallArt.kt` | todo |
| Cabinets: `CabinetDesign` seam + `CabinetBuild` helpers, `beveledBox`, `CabinetPaint`, `MachineArt` (marquee, topper, side art, LED score display "HI n"), live attract screens, `MachineUnit` per shape with attract motion, marquee chase bulbs, `MachineKit` parts, highlight | `hub/Cabinet*.kt`, `hub/Machine*.kt`, `hub/Highlight.kt` | todo |
| Hall labels refresh after a round (bug 4) | `hub/MachineArt.kt` | todo |
| Prompt bubble per spot (title, action, info, accent, badge, cost), hints | `hub/PromptBubble.kt`, `hub/HubRenderer.kt` | todo |
| Soundscape: crowd level, music intensity, positional steps/cheers, cabinet attract bleeps, café | `hub/HallSoundscape.kt` | todo |

## 4. Settings

Every key of `data/SettingsStore.kt` (separate file, sanitized on read and write):
`look_percent` (50..200/10, 100) · `invert_y` · `left_handed` · `fov_deg` (60..90/5, 70) ·
`run_latch` (true) · `reduce_motion` · `haptics` (true) · `sfx_percent` · `ambience_percent` ·
`music_percent` (0..100/10, 100) · `haptics_percent` (20..100/20, 100) · `tilt_steering` ·
`gfx_quality` (AUTO/BATTERY/BEST) · `gfx_frame_cap` (AUTO/30/60).

| Item | Godot | Status |
|---|---|---|
| `GameSettings`, `StepRange`, `SettingsStore` read/write/sanitize | `scripts/data/game_settings.gd`, `step_range.gd`, `settings_store.gd` | done: `tests/data/settings_store_test.gd` (11 tests) |
| Applying settings: hall controls/view, volumes, haptics, shake/reduce motion, quality tier, frame cap, racer tilt | `scripts/app/arcade_app.gd` | todo |

## 5. Save data

Save keys (the DataStore's names, kept as they were): `tokens` · `tickets` · `last_refill_day` ·
`owned` · `hat` · `outfit` · `collection` · `muted` · `spare_token_at` · `total_plays` ·
`first_person` · `stats` · `unlocked` · `collectibles` · `arcade_name` · `hs_<gameId>` ·
`scores_<gameId>`.

| Item | Godot | Status |
|---|---|---|
| Versioned save `user://pocket_arcade_save_v1.json` and settings `user://pocket_arcade_settings_v1.json`: typed values (ints as decimal strings), atomic write (tmp + rename), last good generation kept as `.bak`, corrupt file falls back to backup then defaults | `scripts/data/prefs_store.gd` | done: `tests/data/repository_robustness_test.gd` |
| Repository rules: tokens, refunds, tickets, shop, equip, daily refill, spare token, high scores, top-5 tables, prizes, stats (saturating Long), unlocks, collectibles, arcade name | `scripts/data/arcade_repository.gd`, `save_state.gd`, `score_tables.gd`, `token_gate.gd` | done: `tests/data/*` (repository 28, codecs 20, extras 22, robustness 11, token gate 3, first person 1) |
| Item catalog: 10 hats, 10 outfits, 9 decor, 11 plushies | `scripts/data/catalog.gd` | done |
| Migration from build-13's two DataStores (every key) and from 2.0.0's JSON (core fields win, owned merged, max high scores, build-13-only data from the DataStore), old files untouched, runs once | `scripts/data/save_migration.gd` | done: `tests/data/save_migration_test.gd` (10 tests); emulator check todo |
| Photo strips keep their folder and names (`files/photos/strip-*.png`) so they survive the update | `scripts/share/photo_store.gd` | todo |

## 6. Sound

| Item | Kotlin | Godot | Status |
|---|---|---|---|
| 63 synthesized sound effects (list below), rendered once (cached on disk), played from a pool of players so sounds don't cut each other off; volume, pitch | `engine/AudioSynth.kt`, `engine/audio/SfxBank.kt` | `scripts/engine/audio_synth.gd`, `scripts/engine/audio/sfx_bank.gd`, `audio_cache.gd` | done: every `tone()` call ported with its arguments; buffer lengths and note starts in Kotlin's 32-bit float arithmetic (checked); synthesis takes 0.9 s on a desktop core (threads in parallel), first launch only (`tests/engine/audio_synth_test.gd`) |
| Mixer: priorities, voice stealing, ducking per sfx, reverb send per sfx, limiter, mute, volumes | `engine/audio/MixEngine.kt`, `engine/audio/Dsp.kt` | `scripts/engine/audio/mix_engine.gd`, `audio_buses.gd`, `sfx_mix.gd` | done: 28 voices, each a dry player on its own panned bus plus a send player on the room bus; build-13's steal order, sends and ducks; checked live on the desktop driver (bus peaks) and in `tests/engine/audio/mix_engine_test.gd` |
| Positional sound: listener position/yaw, pan law, distance attenuation and wetness | `engine/audio/Spatial.kt` | `scripts/engine/audio/spatial.gd`, `placement.gd` | done: exact; Godot's panner is set so it reproduces each voice's left/right gains exactly (`tests/engine/audio/spatial_test.gd`, `mix_engine_test.gd`) |
| Rooms: TITLE / HALL / GAME reverb with crossfade | `engine/audio/Reverb.kt` | `scripts/engine/audio/reverb_room.gd` | done: Godot's AudioEffectReverb (the same Freeverb layout) set per room from its source; decay, width, return level and stability checked on an offline copy of Godot's algorithm (`tests/engine/audio/reverb_test.gd`); early reflections dropped (Deviations) |
| Music: scenes Title, Hall (intensity layers), Results, one theme per machine + fallback; 5 stingers (COUNTDOWN, GO, TIME_UP, RESULTS, HIGH_SCORE) with ducking; quiet (low-pass) under cards; round music intensity | `engine/audio/Music.kt`, `Tracks.kt`, `Score.kt`, `ScorePlayer.kt`, `MusicVoice.kt`, `Patches.kt`, `RoundMusic.kt` | control: `scripts/engine/audio/music_control.gd`, `music_scene.gd`, `stinger.gd`, `round_music.gd`; synthesis: build-13's classes unchanged in the Android plugin | control done (`round_music_test.gd`); synthesis in the plugin: todo (Deviations) |
| Hall ambience: crowd babble, attract bleeps from each cabinet (palette per game), café steam and cups | `engine/audio/HallAmbience.kt` | `scripts/engine/audio/hall_ambience.gd`, `attract.gd` | done: bleeps and café sounds scheduled exactly as build-13 (the same `Random(99)` sequence); hum and murmur rendered once as seamless loops (`tests/engine/audio/hall_ambience_test.gd`) |

Sound effects: `BLIP` `SELECT` `ERROR` `COIN` `TOKEN` `TICKET` `PRINT` `WIN` `JACKPOT` `LOSE`
`WHACK` `BONK` `BOMB` `POP` `SWISH` `RIM` `BOUNCE` `THUD` `ROLL` `CLAW_MOTOR` `CLAW_GRAB` `DROP`
`PRIZE` `CHEER` `STEP` `WHOOSH` `COUNTDOWN` `GO` `HIGHSCORE` `CLINK` `SPILL` `BUZZER` `LUCKY`
`GUTTER` `GUNSHOT` `RELOAD` `DRY_FIRE` `RICOCHET` `EXPLOSION` `ENEMY_FIRE` `ALARM` `SHELL`
`FLIPPER` `BUMPER` `SLINGSHOT` `PLUNGER` `DRAIN` `SPINNER` `TILT` `CAST` `SPLASH` `REEL` `BITE`
`LINE_SNAP` `CATCH` `ENGINE` `SKID` `BOOST` `CRASH` `LAP` `FINISH` `HORN` `STEAM` (63).

## 7. The look

| Effect | Kotlin | Godot | Status |
|---|---|---|---|
| Immediate-mode 3D recorder API (`begin/vertex/end`, quad, billboard, sprite, beam, flat, decal, models with `Xform`, tint, emissive boost, blend layers OPAQUE/ALPHA/ADD, depth bias, culling, draw distance) | `engine/r3d/Renderer3D.kt`, `Model.kt`, `RenderPass.kt` | `scripts/engine/r3d/*` | todo |
| Scene shading: hemisphere ambient, one directional light, up to 64 point lights through a light grid (8 per cell), wrapped Lambert, gloss specular, emissive, fog darkening, rim light, blacklight floor glow, environment reflections (built-in room cubemap), glass layer, lit-paint ceiling (HDR) | `engine/gl/GlShaders.kt` `SCENE_FS`, `engine/r3d/Lighting.kt`, `EnvMap.kt` | `shaders/scene.gdshader` | todo |
| Floor reflections: mirror pass of glowing things, or streaks from last frame's bloom; matte floor glow | `GlRenderer.kt`, `SCENE_FS` | `shaders/scene.gdshader`, `scripts/engine/gl/gfx.gd` | todo |
| HDR picture: reversible encode, ACES tone map with hue keep, exposure | `HdrMath.kt`, `GlShaders.kt` | `shaders/post_*.gdshader` | todo |
| Bloom: bright pass (Karis, soft knee, input cap), dual-filter octaves (4/3/2), energy-conserving add, LDR variant | `GlShaders.kt`, `HdrPipeline.kt` | `scripts/engine/gl/post_chain.gd` | todo |
| Finish: colour grade (saturation, contrast, split toning, lifted blacks), vignette (tinted), anamorphic glare, film grain, chromatic aberration, sharpen, dither | `GlShaders.kt` `COMPOSITE_*` | `shaders/post_composite.gdshader` | todo |
| MSAA, render scale (ScalePacer), quality ladder (5 rungs) and tiers, frame cap (FrameGate), frame stats | `engine/gl/GfxQuality.kt`, `ScalePacer.kt`, `FrameStats.kt` | `scripts/engine/gl/*` | todo |
| Particles: SQUARE / CONFETTI / SPARKLE with glow inside the 3D picture | `engine/Particles.kt` | `scripts/engine/particles.gd` | todo |
| Juice: screen shake, springs, popups, flash, hit-stop, slow-mo, camera punch | `engine/Juice.kt` | `scripts/engine/*` | todo |
| Procedural textures: TexPaint (gradients, rounded shapes, text, glows, grain), TexKit, CanvasPainter for attract screens | `engine/r3d/TexPaint.kt`, `TexKit.kt` | `scripts/engine/r3d/tex_paint.gd` | todo |
| Type: ArcadeFont (sans-serif-black, condensed bold, inline vector icons, shadows) | `engine/ArcadeFont.kt`, `engine/r3d/TexPaint.kt` `Fonts` | `scripts/engine/arcade_font.gd` | todo |
| Highlight of the machine in reach (emissive boost, pulse, pool) | `hub/Highlight.kt` | `scripts/hub/highlight.gd` | todo |
| Planar figure shadows from the lights | `hub/FigureShadow.kt` | `scripts/hub/figure_shadow.gd` | todo |

## 8. Input and haptics

| Item | Kotlin | Godot | Status |
|---|---|---|---|
| Multi-touch DOWN/MOVE/UP in field units, pointer ids, `cancelInput` on pause/end | `GameHostScreen.kt` | `scripts/ui/game_host_screen.gd` | todo |
| Every batched (historical) touch sample delivered with its own time; unbuffered dispatch while playing | `GameHostScreen.kt` | `android-plugin/.../TouchRecorder.kt` + `scripts/engine/touch_input.gd` | done: the plugin records every MotionEvent sample (history first, each at its event time, only moved pointers) and the host reads them in order on Godot's clock; desktop uses Godot's touch events (`tests/engine/touch_input_test.gd`); exercised in a round on the emulator: todo |
| `FlickTracker` (90 ms window) | `engine/Touch.kt` | `scripts/engine/flick_tracker.gd` | done (`tests/engine/flick_tracker_test.gd`, 5) |
| Back-gesture exclusion: 80 × 200 dp at the bottom of each side, game screen only | `engine/Touch.kt` | `TouchInput.thumb_zones` + plugin `setGestureExclusion` (API 29+) | done: zones match build-13's (`touch_input_test.gd`); the host sets them: todo |
| Haptics: tick, hit, heavy, soft, bump, rumble, win, jackpot; primitives (API 30+) or waveforms; strength scaling; rate limits | `engine/Haptics.kt` | `scripts/engine/haptics.gd` + `android-plugin/` | done: every pattern, fallback, strength and gap as build-13 (`tests/engine/haptics_test.gd`, 11); on the emulator a hit played `Primitive=CLICK(scale=0.70)` with usage TOUCH (`dumpsys vibrator_manager`) |
| Tilt steering: game rotation vector or accelerometer, dead zone, curve | `engine/TiltSteer.kt` | `scripts/engine/tilt_steer.gd`, `tilt_math.gd` + plugin `TiltReader.kt` | done (`tests/engine/tilt_math_test.gd`, 6); on a real phone: todo |
| Safe area (cutouts) and edge-to-edge 16:9 … 21:9 | Compose insets | `scripts/app/display.gd` | todo |

## 9. Accessibility

| Item | Status |
|---|---|
| Screen-reader labels: every button/control's description, spoken text for glyphs (`spokenText`), live regions (daily bonus, coach card), roles (button, tab, switch), progress bars | todo |
| Reduce motion: no shake/flash/hit-stop/slow-mo/punch, calm title and handoff, no head bob or run zoom, static bulbs, snapped animations, steady prompts | todo |

## 10. Kotlin source files

Every Kotlin source file (the map is checked by `tools/parity_inventory.py`): (170 files, 55558 lines)

| Kotlin file | Lines | Godot destination | Status |
|---|---:|---|---|
| `ArcadeApp.kt` | 599 | `scripts/app/arcade_app.gd` | todo |
| `data/ArcadeRepository.kt` | 425 | `scripts/data/arcade_repository.gd` | done |
| `data/Catalog.kt` | 106 | `scripts/data/catalog.gd` | done |
| `data/SaveState.kt` | 39 | `scripts/data/save_state.gd` | done |
| `data/ScoreTables.kt` | 63 | `scripts/data/score_tables.gd` | done |
| `data/SettingsStore.kt` | 188 | `scripts/data/settings_store.gd` + `game_settings.gd` + `step_range.gd` | done |
| `data/TokenGate.kt` | 29 | `scripts/data/token_gate.gd` | done |
| `engine/ArcadeFont.kt` | 286 | `scripts/engine/arcade_font.gd` | todo |
| `engine/audio/Dsp.kt` | 51 | `scripts/engine/audio/dsp.gd` | done |
| `engine/audio/HallAmbience.kt` | 304 | `scripts/engine/audio/hall_ambience.gd` | done (loops pre-rendered; see Deviations) |
| `engine/audio/MixEngine.kt` | 355 | `scripts/engine/audio/mix_engine.gd, audio_buses.gd, sfx_mix.gd, audio_priority.gd, attract.gd` | done (Godot players and buses; see Deviations) |
| `engine/audio/Music.kt` | 376 | `android-plugin: build-13's Music.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/audio/MusicVoice.kt` | 352 | `android-plugin: build-13's MusicVoice.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/audio/Patches.kt` | 262 | `android-plugin: build-13's Patches.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/audio/Reverb.kt` | 266 | `scripts/engine/audio/reverb_room.gd` | done (AudioEffectReverb mapped; see Deviations) |
| `engine/audio/RoundMusic.kt` | 70 | `scripts/engine/audio/round_music.gd` | done |
| `engine/audio/Score.kt` | 373 | `android-plugin: build-13's Score.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/audio/ScorePlayer.kt` | 212 | `android-plugin: build-13's ScorePlayer.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/audio/SfxBank.kt` | 397 | `scripts/engine/audio/sfx_bank.gd, sfx.gd, audio_cache.gd` | done |
| `engine/audio/Spatial.kt` | 129 | `scripts/engine/audio/spatial.gd, placement.gd` | done |
| `engine/audio/Tracks.kt` | 481 | `android-plugin: build-13's Tracks.kt unchanged; control in scripts/engine/audio/music_control.gd` | kept in Kotlin (plugin) |
| `engine/AudioSynth.kt` | 254 | `scripts/engine/audio_synth.gd` | done |
| `engine/GameLoop.kt` | 85 | `scripts/engine/game_loop.gd, sim_clock.gd` | done |
| `engine/gl/FrameStats.kt` | 205 | `scripts/engine/gl/frame_stats.gd` | todo |
| `engine/gl/Gfx.kt` | 121 | `scripts/engine/gl/gfx.gd` | todo |
| `engine/gl/GfxFailureNotice.kt` | 46 | `scripts/engine/gl/gfx_failure_notice.gd` | todo |
| `engine/gl/GfxQuality.kt` | 197 | `scripts/engine/gl/gfx_quality.gd` | todo |
| `engine/gl/GlGeneration.kt` | 19 | `dropped: Godot owns the GL context and its loss` | todo |
| `engine/gl/GlRenderer.kt` | 1537 | `scripts/engine/gl/gfx.gd + scripts/engine/gl/post_chain.gd` | todo |
| `engine/gl/GlShaders.kt` | 666 | `shaders/scene.gdshader + shaders/post_*.gdshader` | todo |
| `engine/gl/GlSurface.kt` | 64 | `scripts/engine/gl/gfx.gd (SubViewport slots)` | todo |
| `engine/gl/GlThread.kt` | 327 | `Godot renderer thread (dropped: engine-owned)` | todo |
| `engine/gl/HdrMath.kt` | 217 | `scripts/engine/gl/hdr_math.gd` | todo |
| `engine/gl/HdrPipeline.kt` | 107 | `scripts/engine/gl/post_chain.gd` | todo |
| `engine/gl/RestartPolicy.kt` | 48 | `dropped: Godot owns the GL context and its loss` | todo |
| `engine/gl/ScalePacer.kt` | 219 | `scripts/engine/gl/scale_pacer.gd` | todo |
| `engine/gl/Warmup.kt` | 75 | `scripts/engine/gl/warmup.gd` | todo |
| `engine/Haptics.kt` | 256 | `scripts/engine/haptics.gd` | done |
| `engine/Juice.kt` | 339 | `scripts/engine/juice.gd` | todo |
| `engine/MathUtil.kt` | 59 | `scripts/engine/math_util.gd` | wip: `math_util.gd`, `k_random.gd`, `k_parse.gd` |
| `engine/Painter.kt` | 26 | `scripts/engine/painter.gd` | done |
| `engine/Palette.kt` | 68 | `scripts/engine/pal.gd` | done |
| `engine/Particles.kt` | 222 | `scripts/engine/particles.gd` | todo |
| `engine/Physics.kt` | 188 | `scripts/engine/physics.gd (CircleWorld, CircleWorld.Body, CircleWorld.Segment)` | done |
| `engine/r3d/Camera3D.kt` | 103 | `scripts/engine/r3d/camera3_d.gd` | todo |
| `engine/r3d/EnvMap.kt` | 203 | `scripts/engine/r3d/env_map.gd` | todo |
| `engine/r3d/Frustum.kt` | 147 | `scripts/engine/r3d/frustum.gd` | todo |
| `engine/r3d/Lighting.kt` | 59 | `scripts/engine/r3d/lighting.gd` | todo |
| `engine/r3d/Model.kt` | 553 | `scripts/engine/r3d/model.gd` | todo |
| `engine/r3d/Renderer3D.kt` | 930 | `scripts/engine/r3d/renderer3_d.gd` | todo |
| `engine/r3d/RenderPass.kt` | 220 | `scripts/engine/r3d/render_pass.gd` | todo |
| `engine/r3d/Stage3D.kt` | 183 | `scripts/engine/r3d/stage3_d.gd` | todo |
| `engine/r3d/TexKit.kt` | 34 | `scripts/engine/r3d/tex_kit.gd` | todo |
| `engine/r3d/TexPaint.kt` | 361 | `scripts/engine/r3d/tex_paint.gd` | todo |
| `engine/r3d/Texture.kt` | 112 | `scripts/engine/r3d/texture.gd` | todo |
| `engine/TiltSteer.kt` | 121 | `scripts/engine/tilt_steer.gd, tilt_math.gd + android-plugin TiltReader.kt` | done |
| `engine/Touch.kt` | 98 | `scripts/engine/touch_type.gd, flick_tracker.gd, touch_input.gd` | done (TouchType, FlickTracker, thumb zones) |
| `games/airhockey/AirHockeyGame.kt` | 561 | `scripts/games/airhockey/air_hockey_game.gd` | todo |
| `games/airhockey/HockeyArt.kt` | 219 | `scripts/games/airhockey/hockey_art.gd` | todo |
| `games/airhockey/HockeyScene.kt` | 513 | `scripts/games/airhockey/hockey_scene.gd` | todo |
| `games/BaseMiniGame.kt` | 158 | `scripts/games/base_mini_game.gd` | done |
| `games/claw/ClawArt.kt` | 184 | `scripts/games/claw/claw_art.gd` | todo |
| `games/claw/ClawMachineGame.kt` | 1124 | `scripts/games/claw/claw_machine_game.gd` | todo |
| `games/claw/Plush3D.kt` | 214 | `scripts/games/claw/plush3_d.gd` | todo |
| `games/coinpusher/CoinPusherGame.kt` | 784 | `scripts/games/coinpusher/coin_pusher_game.gd` | todo |
| `games/coinpusher/PusherArt.kt` | 196 | `scripts/games/coinpusher/pusher_art.gd` | todo |
| `games/fishing/FishingArt.kt` | 421 | `scripts/games/fishing/fishing_art.gd` | todo |
| `games/fishing/FishingCabinet.kt` | 139 | `scripts/games/fishing/fishing_cabinet.gd` | todo |
| `games/fishing/FishingGame.kt` | 2167 | `scripts/games/fishing/fishing_game.gd` | todo |
| `games/GameRegistry.kt` | 35 | `scripts/games/game_registry.gd` | done (loads machines by path) |
| `games/hoops/HoopsArt.kt` | 345 | `scripts/games/hoops/hoops_art.gd` | todo |
| `games/hoops/HoopsGame.kt` | 613 | `scripts/games/hoops/hoops_game.gd` | todo |
| `games/hoops/HoopsScene.kt` | 734 | `scripts/games/hoops/hoops_scene.gd` | todo |
| `games/MiniGame.kt` | 175 | `scripts/games/mini_game.gd, game_fx.gd` | done (contract; CabinetDesign comes with the hall) |
| `games/pinball/PinballArt.kt` | 737 | `scripts/games/pinball/pinball_art.gd` | todo |
| `games/pinball/PinballCabinet.kt` | 143 | `scripts/games/pinball/pinball_cabinet.gd` | todo |
| `games/pinball/PinballDisplay.kt` | 374 | `scripts/games/pinball/pinball_display.gd` | todo |
| `games/pinball/PinballGame.kt` | 1644 | `scripts/games/pinball/pinball_game.gd` | todo |
| `games/pinball/PinballTable.kt` | 206 | `scripts/games/pinball/pinball_table.gd` | todo |
| `games/racer/RacerArt.kt` | 391 | `scripts/games/racer/racer_art.gd` | todo |
| `games/racer/RacerCabinet.kt` | 157 | `scripts/games/racer/racer_cabinet.gd` | todo |
| `games/racer/RacerGame.kt` | 1416 | `scripts/games/racer/racer_game.gd` | todo |
| `games/racer/RacerScene.kt` | 294 | `scripts/games/racer/racer_scene.gd` | todo |
| `games/scenea/SceneFx.kt` | 147 | `scripts/games/scenea/scene_fx.gd` | done |
| `games/shooter/ShooterArt.kt` | 626 | `scripts/games/shooter/shooter_art.gd` | todo |
| `games/shooter/ShooterCabinet.kt` | 220 | `scripts/games/shooter/shooter_cabinet.gd` | todo |
| `games/shooter/ShooterGame.kt` | 1966 | `scripts/games/shooter/shooter_game.gd` | todo |
| `games/shooter/ShooterScene.kt` | 124 | `scripts/games/shooter/shooter_scene.gd` | todo |
| `games/skeeball/SkeeArt.kt` | 204 | `scripts/games/skeeball/skee_art.gd` | todo |
| `games/skeeball/SkeeBallGame.kt` | 1041 | `scripts/games/skeeball/skee_ball_game.gd` | todo |
| `games/stacker/StackerArt.kt` | 234 | `scripts/games/stacker/stacker_art.gd` | todo |
| `games/stacker/StackerGame.kt` | 423 | `scripts/games/stacker/stacker_game.gd` | todo |
| `games/stacker/StackerScene.kt` | 358 | `scripts/games/stacker/stacker_scene.gd` | todo |
| `games/whackamole/Moles.kt` | 118 | `scripts/games/whackamole/moles.gd` | todo |
| `games/whackamole/WhackAMoleGame.kt` | 792 | `scripts/games/whackamole/whack_a_mole_game.gd` | todo |
| `games/whackamole/WhackArt.kt` | 207 | `scripts/games/whackamole/whack_art.gd` | todo |
| `hub/AnimMath.kt` | 87 | `scripts/hub/anim_math.gd` | todo |
| `hub/CabinetDesign.kt` | 348 | `scripts/hub/cabinet_design.gd` | todo |
| `hub/CabinetForm.kt` | 237 | `scripts/hub/cabinet_form.gd` | todo |
| `hub/CabinetPaint.kt` | 347 | `scripts/hub/cabinet_paint.gd` | todo |
| `hub/CafeArt.kt` | 551 | `scripts/hub/cafe_art.gd` | todo |
| `hub/CafeLayout.kt` | 145 | `scripts/hub/cafe_layout.gd` | todo |
| `hub/CafeLife.kt` | 227 | `scripts/hub/cafe_life.gd` | todo |
| `hub/CafeScene.kt` | 71 | `scripts/hub/cafe_scene.gd` | todo |
| `hub/Camera.kt` | 318 | `scripts/hub/camera.gd` | todo |
| `hub/Collision.kt` | 219 | `scripts/hub/collision.gd` | todo |
| `hub/Emotes.kt` | 95 | `scripts/hub/emotes.gd` | todo |
| `hub/FigureAnim.kt` | 954 | `scripts/hub/figure_anim.gd` | todo |
| `hub/Figures.kt` | 483 | `scripts/hub/figures.gd` | todo |
| `hub/FigureShadow.kt` | 122 | `scripts/hub/figure_shadow.gd` | todo |
| `hub/Gait.kt` | 100 | `scripts/hub/gait.gd` | todo |
| `hub/HallArt.kt` | 798 | `scripts/hub/hall_art.gd` | todo |
| `hub/HallKit.kt` | 465 | `scripts/hub/hall_kit.gd` | todo |
| `hub/HallRig.kt` | 431 | `scripts/hub/hall_rig.gd` | todo |
| `hub/HallScene.kt` | 465 | `scripts/hub/hall_scene.gd` | todo |
| `hub/HallSoundscape.kt` | 182 | `scripts/hub/hall_soundscape.gd` | todo |
| `hub/HallStage.kt` | 116 | `scripts/hub/hall_stage.gd` | todo |
| `hub/Highlight.kt` | 72 | `scripts/hub/highlight.gd` | todo |
| `hub/HubMap.kt` | 600 | `scripts/hub/hub_map.gd` | todo |
| `hub/HubRenderer.kt` | 252 | `scripts/hub/hub_renderer.gd` | todo |
| `hub/HubScreen.kt` | 97 | `scripts/hub/hub_screen.gd` | todo |
| `hub/HubWorld.kt` | 890 | `scripts/hub/hub_world.gd` | todo |
| `hub/Joystick.kt` | 152 | `scripts/hub/joystick.gd` | todo |
| `hub/MachineArt.kt` | 326 | `scripts/hub/machine_art.gd` | todo |
| `hub/MachineKit.kt` | 518 | `scripts/hub/machine_kit.gd` | todo |
| `hub/Machines.kt` | 694 | `scripts/hub/machines.gd` | todo |
| `hub/Npc.kt` | 460 | `scripts/hub/npc.gd` | todo |
| `hub/PhotoWall.kt` | 134 | `scripts/hub/photo_wall.gd` | todo |
| `hub/Player.kt` | 180 | `scripts/hub/player.gd` | todo |
| `hub/PoseBlender.kt` | 87 | `scripts/hub/pose_blender.gd` | todo |
| `hub/PrizeWall.kt` | 108 | `scripts/hub/prize_wall.gd` | todo |
| `hub/PromptBubble.kt` | 207 | `scripts/hub/prompt_bubble.gd` | todo |
| `hub/Props.kt` | 493 | `scripts/hub/props.gd` | todo |
| `hub/ShowroomPath.kt` | 148 | `scripts/hub/showroom_path.gd` | todo |
| `hub/TitleShowcase.kt` | 179 | `scripts/hub/title_showcase.gd` | todo |
| `hub/TitleUnits.kt` | 87 | `scripts/hub/title_units.gd` | todo |
| `hub/WalkRoute.kt` | 169 | `scripts/hub/walk_route.gd` | todo |
| `MainActivity.kt` | 77 | `scripts/app/main.gd + android plugin` | done (Back, pause/resume, play extra, immersive via export) |
| `share/PhotoShare.kt` | 46 | `android-plugin PocketArcadePlugin.sharePng + AndroidBridge.share_png` | done (plugin sharePng, PhotoProvider) |
| `share/PhotoStore.kt` | 65 | `scripts/share/photo_store.gd` | todo |
| `share/PhotoStrip.kt` | 103 | `scripts/share/photo_strip.gd` | todo |
| `share/PhotoStripArt.kt` | 86 | `scripts/share/photo_strip_art.gd` | todo |
| `startup/LoadPlan.kt` | 198 | `scripts/startup/load_plan.gd` | todo |
| `startup/Startup.kt` | 129 | `scripts/startup/startup.gd` | todo |
| `startup/StartupGate.kt` | 154 | `scripts/startup/startup_gate.gd` | todo |
| `startup/WarmRun.kt` | 68 | `scripts/startup/warm_run.gd` | todo |
| `ui/CurrencyFx.kt` | 232 | `scripts/ui/currency_fx.gd` | todo |
| `ui/DailyBonus.kt` | 266 | `scripts/ui/daily_bonus.gd` | todo |
| `ui/GameHostScreen.kt` | 1051 | `scripts/ui/game_round.gd, game_host_screen.gd` | done (round, drawing, input); cards use stand-in buttons until the UI kit |
| `ui/Handoff.kt` | 142 | `scripts/ui/handoff.gd` | todo |
| `ui/Hud.kt` | 185 | `scripts/ui/hud.gd` | todo |
| `ui/LoadingScreen.kt` | 300 | `scripts/ui/loading_screen.gd` | todo |
| `ui/MapScreen.kt` | 514 | `scripts/ui/map_screen.gd` | todo |
| `ui/Onboarding.kt` | 360 | `scripts/ui/onboarding.gd` | todo |
| `ui/PhotoBoothPlan.kt` | 70 | `scripts/ui/photo_booth_plan.gd` | todo |
| `ui/PhotoBoothScreen.kt` | 381 | `scripts/ui/photo_booth_screen.gd` | todo |
| `ui/PrizeCounterScreen.kt` | 605 | `scripts/ui/prize_counter_screen.gd` | todo |
| `ui/ProfileScreen.kt` | 194 | `scripts/ui/profile_screen.gd` | todo |
| `ui/ResultsPlan.kt` | 106 | `scripts/ui/results_plan.gd` | done |
| `ui/RollingNumber.kt` | 190 | `scripts/ui/rolling_number.gd` | todo |
| `ui/SettingsScreen.kt` | 189 | `scripts/ui/settings_screen.gd` | todo |
| `ui/Thumbs.kt` | 188 | `scripts/ui/thumbs.gd` | todo |
| `ui/TitleScreen.kt` | 370 | `scripts/ui/title_screen.gd` | todo |
| `ui/TitleTimeline.kt` | 208 | `scripts/ui/title_timeline.gd` | todo |
| `ui/TokenMachineScreen.kt` | 340 | `scripts/ui/token_machine_screen.gd` | todo |
| `ui/TutorialSteps.kt` | 340 | `scripts/ui/tutorial_steps.gd` | todo |
| `ui/UiIcons.kt` | 350 | `scripts/ui/ui_icons.gd` | todo |
| `ui/UiParts.kt` | 245 | `scripts/ui/ui_parts.gd` | todo |
| `ui/UiTheme.kt` | 207 | `scripts/ui/ui_theme.gd` | todo |
| `ui/Widgets.kt` | 749 | `scripts/ui/widgets.gd` | todo |

## 11. Kotlin tests

Every Kotlin test file and its @Test count: (107 files, 911 @Test)

| Kotlin test file | @Test | Godot test | Status |
|---|---:|---|---|
| `AppFlowTest.kt` | 6 (3 @Ignore) | `tests/app_flow_test.gd` | todo |
| `data/ArcadeRepositoryTest.kt` | 28 | `tests/data/arcade_repository_test.gd` | done (28) |
| `data/FirstPersonPrefTest.kt` | 1 | `tests/data/first_person_pref_test.gd` | done (1) |
| `data/RepositoryRobustnessTest.kt` | 8 | `tests/data/repository_robustness_test.gd` | done (8 + 3 Godot-only) |
| `data/RepositoryTestBase.kt` | 0 | `tests/data/repository_test_base.gd` | done (fixture: `tests/data/repo_fixture.gd`) |
| `data/SaveCodecsTest.kt` | 20 | `tests/data/save_codecs_test.gd` | done (20) |
| `data/SaveExtrasTest.kt` | 22 | `tests/data/save_extras_test.gd` | done (22) |
| `data/SettingsStoreTest.kt` | 10 | `tests/data/settings_store_test.gd` | done (10 + 1 Godot-only) |
| `data/TokenGateTest.kt` | 3 | `tests/data/token_gate_test.gd` | done (3) |
| `engine/audio/HallAmbienceTest.kt` | 8 | `tests/engine/audio/hall_ambience_test.gd` | done (8 + 1 Godot) |
| `engine/audio/MixEngineTest.kt` | 12 | `tests/engine/audio/mix_engine_test.gd` | adapted (12 + 4 Godot; see section 11 note) |
| `engine/audio/MusicTest.kt` | 21 | `android-plugin/src/test (build-13's MusicTest.kt unchanged)` | kept in Kotlin: runs on the JVM in CI with the plugin's music |
| `engine/audio/ReverbTest.kt` | 8 | `tests/engine/audio/reverb_test.gd` | adapted (7 of 8 on a copy of Godot's reverb + 1 Godot; idle-cost test dropped) |
| `engine/audio/RoundMusicTest.kt` | 4 | `tests/engine/audio/round_music_test.gd` | done (4 + 1 Godot) |
| `engine/audio/SpatialTest.kt` | 11 | `tests/engine/audio/spatial_test.gd` | done (11) |
| `engine/AudioSynthTest.kt` | 1 | `tests/engine/audio_synth_test.gd` | done (1 + 5 Godot) |
| `engine/CameraPunchTest.kt` | 11 | `tests/engine/camera_punch_test.gd` | todo |
| `engine/FlickTrackerTest.kt` | 5 | `tests/engine/flick_tracker_test.gd` | done (5) |
| `engine/gl/GfxQualityTest.kt` | 15 | `tests/engine/gl/gfx_quality_test.gd` | todo |
| `engine/gl/GfxTakeTest.kt` | 3 | `tests/engine/gl/gfx_take_test.gd` | todo |
| `engine/gl/GlGenerationTest.kt` | 3 | `tests/engine/gl/gl_generation_test.gd` | todo |
| `engine/gl/HdrMathTest.kt` | 17 | `tests/engine/gl/hdr_math_test.gd` | todo |
| `engine/gl/HdrPlanTest.kt` | 11 | `tests/engine/gl/hdr_plan_test.gd` | todo |
| `engine/gl/RestartPolicyTest.kt` | 5 | `tests/engine/gl/restart_policy_test.gd` | todo |
| `engine/gl/ScalePacerTest.kt` | 16 | `tests/engine/gl/scale_pacer_test.gd` | todo |
| `engine/HapticsTest.kt` | 5 | `tests/engine/haptics_test.gd` | done (5 + 6 Godot) |
| `engine/ParticlesGlTest.kt` | 12 | `tests/engine/particles_gl_test.gd` | todo |
| `engine/r3d/EngineFixesTest.kt` | 8 | `tests/engine/r3d/engine_fixes_test.gd` | todo |
| `engine/r3d/FrustumTest.kt` | 6 | `tests/engine/r3d/frustum_test.gd` | todo |
| `engine/r3d/PostParamsTest.kt` | 3 | `tests/engine/r3d/post_params_test.gd` | todo |
| `engine/r3d/QualityLeversTest.kt` | 8 | `tests/engine/r3d/quality_levers_test.gd` | todo |
| `engine/r3d/ReflectionTest.kt` | 7 | `tests/engine/r3d/reflection_test.gd` | todo |
| `engine/r3d/Renderer3DTest.kt` | 6 | `tests/engine/r3d/renderer3_d_test.gd` | todo |
| `engine/SimClockTest.kt` | 5 | `tests/engine/sim_clock_test.gd` | todo |
| `engine/TiltMathTest.kt` | 5 | `tests/engine/tilt_math_test.gd` | done (5 + 1 Godot) |
| `engine/TimeScaleTest.kt` | 17 | `tests/engine/time_scale_test.gd` | todo |
| `games/airhockey/AirHockeyBuzzerTest.kt` | 3 | `tests/games/airhockey/air_hockey_buzzer_test.gd` | todo |
| `games/airhockey/HockeySceneTest.kt` | 3 | `tests/games/airhockey/hockey_scene_test.gd` | todo |
| `games/AttractScreensTest.kt` | 3 | `tests/games/attract_screens_test.gd` | todo |
| `games/fishing/FishingRulesTest.kt` | 16 | `tests/games/fishing/fishing_rules_test.gd` | todo |
| `games/fishing/FishingSimulationTest.kt` | 1 | `tests/games/fishing/fishing_simulation_test.gd` | todo |
| `games/GameFuzzTest.kt` | 6 | `tests/games/game_fuzz_test.gd` | todo |
| `games/GameSimulationTest.kt` | 5 | `tests/games/game_simulation_test.gd` | todo |
| `games/hoops/HoopsSceneTest.kt` | 3 | `tests/games/hoops/hoops_scene_test.gd` | todo |
| `games/InputCancelTest.kt` | 7 | `tests/games/input_cancel_test.gd` | todo |
| `games/pinball/PinballDisplayTest.kt` | 6 | `tests/games/pinball/pinball_display_test.gd` | todo |
| `games/pinball/PinballRulesTest.kt` | 17 | `tests/games/pinball/pinball_rules_test.gd` | todo |
| `games/pinball/PinballSimulationTest.kt` | 3 | `tests/games/pinball/pinball_simulation_test.gd` | todo |
| `games/pinball/PinballStuckBallTest.kt` | 2 | `tests/games/pinball/pinball_stuck_ball_test.gd` | todo |
| `games/racer/RacerControlsTest.kt` | 15 | `tests/games/racer/racer_controls_test.gd` | todo |
| `games/racer/RacerSceneTest.kt` | 3 | `tests/games/racer/racer_scene_test.gd` | todo |
| `games/racer/RacerSimulationTest.kt` | 9 | `tests/games/racer/racer_simulation_test.gd` | todo |
| `games/ReplayResetTest.kt` | 2 | `tests/games/replay_reset_test.gd` | todo |
| `games/scenea/AttractScreensTest.kt` | 4 | `tests/games/scenea/attract_screens_test.gd` | todo |
| `games/scenea/SceneFxTest.kt` | 7 | `tests/games/scenea/scene_fx_test.gd` | SceneFx half done (4); plush half with the claw |
| `games/shooter/ShooterRulesTest.kt` | 19 | `tests/games/shooter/shooter_rules_test.gd` | todo |
| `games/shooter/ShooterSimulationTest.kt` | 1 | `tests/games/shooter/shooter_simulation_test.gd` | todo |
| `games/SimHarness.kt` | 0 | `tests/games/sim_harness.gd` | todo |
| `games/skeeball/SkeeBallTest.kt` | 5 | `tests/games/skeeball/skee_ball_test.gd` | todo |
| `games/stacker/StackerSceneTest.kt` | 4 | `tests/games/stacker/stacker_scene_test.gd` | todo |
| `hub/CabinetFormTest.kt` | 14 | `tests/hub/cabinet_form_test.gd` | todo |
| `hub/CabinetModelsTest.kt` | 7 | `tests/hub/cabinet_models_test.gd` | todo |
| `hub/CafeLifeTest.kt` | 1 | `tests/hub/cafe_life_test.gd` | todo |
| `hub/CameraEntranceTest.kt` | 4 | `tests/hub/camera_entrance_test.gd` | todo |
| `hub/EmotesTest.kt` | 10 | `tests/hub/emotes_test.gd` | todo |
| `hub/FigureAnimTest.kt` | 7 | `tests/hub/figure_anim_test.gd` | todo |
| `hub/FigureFollowThroughTest.kt` | 9 | `tests/hub/figure_follow_through_test.gd` | todo |
| `hub/FigureLifeTest.kt` | 11 | `tests/hub/figure_life_test.gd` | todo |
| `hub/FigureLocomotionTest.kt` | 10 | `tests/hub/figure_locomotion_test.gd` | todo |
| `hub/FigureShadowTest.kt` | 10 | `tests/hub/figure_shadow_test.gd` | todo |
| `hub/FirstPersonControlsTest.kt` | 17 | `tests/hub/first_person_controls_test.gd` | todo |
| `hub/FirstPersonTest.kt` | 14 | `tests/hub/first_person_test.gd` | todo |
| `hub/HallControlsTest.kt` | 18 | `tests/hub/hall_controls_test.gd` | todo |
| `hub/HallLoadTest.kt` | 12 | `tests/hub/hall_load_test.gd` | todo |
| `hub/HallSimulationTest.kt` | 4 | `tests/hub/hall_simulation_test.gd` | todo |
| `hub/HallSoundscapeTest.kt` | 10 | `tests/hub/hall_soundscape_test.gd` | todo |
| `hub/HighlightTest.kt` | 7 | `tests/hub/highlight_test.gd` | todo |
| `hub/HubHapticsTest.kt` | 5 | `tests/hub/hub_haptics_test.gd` | todo |
| `hub/HubMapTest.kt` | 21 | `tests/hub/hub_map_test.gd` | todo |
| `hub/InteractivePropsTest.kt` | 11 | `tests/hub/interactive_props_test.gd` | todo |
| `hub/KidsGiveWayTest.kt` | 2 | `tests/hub/kids_give_way_test.gd` | todo |
| `hub/PhotoWallTest.kt` | 6 | `tests/hub/photo_wall_test.gd` | todo |
| `hub/PoseBlenderTest.kt` | 9 | `tests/hub/pose_blender_test.gd` | todo |
| `hub/PrizeWallTest.kt` | 5 | `tests/hub/prize_wall_test.gd` | todo |
| `hub/PromptBubbleTest.kt` | 3 | `tests/hub/prompt_bubble_test.gd` | todo |
| `hub/RigChecks.kt` | 0 | `tests/hub/rig_checks.gd` | todo |
| `hub/ShowroomPathTest.kt` | 14 | `tests/hub/showroom_path_test.gd` | todo |
| `share/PhotoShareWiringTest.kt` | 2 | `tests/share/photo_share_wiring_test.gd` | todo |
| `share/PhotoStoreTest.kt` | 7 | `tests/share/photo_store_test.gd` | todo |
| `share/PhotoStripTest.kt` | 7 | `tests/share/photo_strip_test.gd` | todo |
| `startup/LoadPlanTest.kt` | 20 | `tests/startup/load_plan_test.gd` | todo |
| `startup/StartupGateTest.kt` | 9 | `tests/startup/startup_gate_test.gd` | todo |
| `startup/WarmRunTest.kt` | 8 | `tests/startup/warm_run_test.gd` | todo |
| `ui/DailyBonusTest.kt` | 14 | `tests/ui/daily_bonus_test.gd` | todo |
| `ui/EntranceTest.kt` | 4 | `tests/ui/entrance_test.gd` | todo |
| `ui/HandoffTest.kt` | 12 | `tests/ui/handoff_test.gd` | todo |
| `ui/LoadingScreenTest.kt` | 7 | `tests/ui/loading_screen_test.gd` | todo |
| `ui/MapScreenTest.kt` | 10 | `tests/ui/map_screen_test.gd` | todo |
| `ui/PhotoBoothPlanTest.kt` | 6 | `tests/ui/photo_booth_plan_test.gd` | todo |
| `ui/PrizeCounterTest.kt` | 4 | `tests/ui/prize_counter_test.gd` | todo |
| `ui/ResultsPlanTest.kt` | 10 | `tests/ui/results_plan_test.gd` | done (10) |
| `ui/RollingNumberTest.kt` | 11 | `tests/ui/rolling_number_test.gd` | todo |
| `ui/SpokenTextTest.kt` | 4 | `tests/ui/spoken_text_test.gd` | todo |
| `ui/TitleTimelineTest.kt` | 23 | `tests/ui/title_timeline_test.gd` | todo |
| `ui/TokenAndProfileTest.kt` | 5 | `tests/ui/token_and_profile_test.gd` | todo |
| `ui/TutorialStepsTest.kt` | 28 | `tests/ui/tutorial_steps_test.gd` | todo |
| `ui/UiThemeTest.kt` | 4 | `tests/ui/ui_theme_test.gd` | todo |

## 12. Regression tests for the 2.0.0 bugs

| # | Bug in 2.0.0 | Regression test | Status |
|---|---|---|---|
| 1 | Android Back quit the app (`quit_on_go_back` left true; the test called the handler directly) | `tests/bugs/back_button_test.gd`: asserts the project setting, drives Back through the root window's `go_back_requested`, a screen claims it or the app goes to the background | done; on the emulator Back sent the app to the background (same process, audio paused) and it resumed |
| 2 | Numbers came back as decimals ("0.0 tickets", "BEST 450.0") | `tests/data/repository_robustness_test.gd::test_every_number_comes_back_as_an_int`, `tests/data/save_migration_test.gd::test_a_2_0_0_save_comes_back_with_whole_numbers`, `tests/bugs/numbers_test.gd` (UI strings) | partly done |
| 3 | Black bars on tall phones (540×960, aspect `keep`) | `tests/bugs/edge_to_edge_test.gd`: 16:9 … 21:9 fill the window, touch UI inside the safe area | done (UI in dp at full resolution with aspect expand; the host's field and bar inside the safe area at every aspect); the emulator with a cutout showed no bars |
| 4 | Hall cabinet high scores stale after a round | `tests/bugs/hall_labels_test.gd` | todo |
| 5 | 76 MB APK (three ABIs) | CI: arm64-v8a only (fails on any other ABI's code), size reported in the job summary | done: 26.1 MB (Godot's engine library is 67.6 MB uncompressed, 22 MB compressed; build-13 was 7.6 MB) |
| 6 | One material and mesh per object (583 hall / 1,201 racer draw calls) | `tests/bugs/draw_call_budget_test.gd` + measured budgets per scene | todo |
| 7 | Fixed version numbers | CI patches `version/code = 20000 + run`, `version/name = 2.1.<run>`, release named from them | done in `.github/workflows/build.yml`; first run: todo |
| 8 | All audio was one sine beep | `tests/engine/audio_synth_test.gd`, `tests/engine/audio/*` | done: 63 sounds synthesized from build-13's recipes, the pool, reverb and ambience; build-13's music in the plugin with its tests |
| 9 | Flaky GUI test tied to frame timing | Deterministic tests: seeded `KRandom`, fixed steps, no wall clock, `frames()` waits for layout | wip |
| 10 | Broken image links in `docs/KOTLIN_VERSION.md` | `tools/check_doc_links.py` in CI | done: `docs/KOTLIN_VERSION.md` is build-13's README with its images at `screenshots/`; the checker passes |

## Deviations and decisions

Decisions made without stopping to ask, and anything that could not be matched exactly.

| Area | Decision | Why |
|---|---|---|
| Saves | New versioned files `pocket_arcade_save_v1.json` / `pocket_arcade_settings_v1.json` keep build-13's preference keys and string encodings, with every value typed in the file (ints as decimal strings). | The import from the DataStore is a key copy read by the same code; numbers can't come back as floats; saturated Long counters survive. |
| Saves | Saves are synchronous: each mutation writes the file before returning. | DataStore's coroutines have no Godot counterpart; a synchronous write can't be dropped by leaving a screen (Kotlin's `persist`). Kotlin's two concurrency tests keep their assertions over the same call sequence. |
| Randomness | `KRandom` is a bit-exact port of Kotlin's `XorWowRandom` (checked against kotlin-stdlib 2.2.20); `MathUtil.hash01` emulates 32-bit Int overflow. | Seeded rounds, bot simulations and procedural art make the same choices as build-13. Float maths is 64-bit in GDScript (32-bit in Kotlin), so long simulations can drift by rounding; tests compare behaviour and thresholds, not bit-exact trajectories. |
| Tests | Kotlin tests of DataStore internals (custom corruption handler disabled) are ported against the Godot store's equivalent failure (writes refused). | Same promise (never crash, report failure), different storage. |
| Tests | `tools/run_tests.sh` imports the project then runs `res://tests/run_tests.tscn`. | New `class_name` scripts are only resolvable after an import. |
| Sound | The soundtrack is build-13's own synthesizer (`Music`, `ScorePlayer`, `MusicVoice`, `Patches`, `Score`, `Tracks` and its `Reverb`, unchanged) running natively in the Android plugin on its own low-latency AudioTrack, as build-13 ran it; GDScript drives it through `MusicControl`. Desktop runs have no music. build-13's `MusicTest` runs on the JVM in CI against those same classes. | A themed loop holds 20 to 30 subtractive voices at once. A MusicVoice-shaped loop measured 2.0M voice-samples/s on one desktop core in GDScript; a phone is 3 to 4 times slower and the music needs about 1M/s at a busy moment, so real-time GDScript music would take a whole phone core. Pre-rendering every theme's layers instead would take minutes on a phone and hundreds of MB (about 400 s of music in 3 stereo layers), and MusicTest renders minutes of music, which would take GDScript many minutes per run. |
| Sound | Sound effects and ambience are rendered once in GDScript and played by Godot's mixer: each voice is a dry player on its own bus with an AudioEffectPanner (pan and volume chosen so its left and right gains equal build-13's exactly) and a send player on the reverb bus. A stolen voice's player is restarted and Godot fades the old sound over 64 samples (build-13: a 96-frame fade in a spare slot). | The requested design (pre-rendered once, played from a player pool); Godot does the per-sample mixing natively. |
| Sound | Room reverb is Godot's AudioEffectReverb, set from 4.6.2's `reverb_filter.cpp`: comb feedback from the room's rt60 (Godot's shortest tail is about 0.6 s, so GAME rings for 0.6 s instead of 0.5 s), damping and wet matched to build-13's filter coefficient and return level, a 16 ms AudioEffectDelay as the pre-delay, and the 140 Hz high-pass. Rooms still glide (0.35 s time constant). build-13's five early-reflection taps per ear are not reproduced. | Godot's reverb is the same Freeverb design but has no early-reflection stage; building one would need a third player per voice. |
| Sound | build-13's soft clip (`x - x^3/6.75` after the 0.85 master gain) is an AudioEffectAmplify (0.85) and an AudioEffectHardLimiter (ceiling -0.3 dB) on the output bus. | Godot has no cubic soft clipper; the limiter stops stacked voices from clipping, which was the clip's job. |
| Sound | The hum (20 s, whole cycles of 55/110/165.3 Hz) and the crowd murmur (two cycles of its 0.11 Hz swell, about 18 s, the end crossfaded into the start) are pre-rendered loops; their levels (target, crowd, ambience volume) follow build-13's per-block easing exactly. The murmur's noise comes from Godot's RandomNumberGenerator with build-13's seeds, not Kotlin's Random. | build-13 synthesized them live. Kotlin's Random ported to GDScript made the loop take 4.7 s to render; Godot's generator takes 0.4 s, and the murmur is noise either way. |
| Sound | Synthesis runs in 64-bit floats; buffer lengths and note start samples use Kotlin's 32-bit float arithmetic (so the jackpot's buffer is 62401 samples, as in build-13). Noise in the sound effects comes from the same seeded Random, so the sequence is the same, though a phase wrap can land a sample apart. | Bit-exact 32-bit synthesis isn't practical in GDScript; the sounds are the same to the ear. |
| Sound | The sounds are synthesized on worker threads at first launch (about 0.9 s on a desktop core, an estimated 3 s on a phone) and cached in `user://cache` (16-bit PCM, about 7 MB, magic word and hash checked); later launches load them. Requests before they are ready are ignored, as build-13's were before its bank existed. | build-13 synthesized everything natively at every start (about 0.2 s); GDScript is slower, so the work is kept off the main thread and done once. |
| Sound | Godot 4.6.2's only Android audio driver is OpenSL ES at a fixed 44.1 kHz (two 1024-frame buffers), so the sound effects can't take Android's low-latency path that build-13's AudioTrack (USAGE_GAME, low-latency mode, the device's rate) used; the soundtrack still does (the plugin's AudioTrack). The effects are synthesized at Godot's mix rate so nothing is resampled. On the emulator both streams measured about 175 ms, so the difference needs a real phone to judge. | Engine limit; replacing Godot's audio output would mean mixing the effects in the plugin too. |
| Input | The raw touch stream comes from the plugin (an OnTouchListener on Godot's view that records every sample, historical ones included, with its event time, and never consumes the event); Godot's own touch events still drive the UI. | Godot's Android input drops MotionEvent history and timestamps; build-13's flicks are measured on every sample. |
| Platform | The plugin's FileProvider is a subclass (`com.pocketarcade.godot.PhotoProvider`) with build-13's authority `<package>.photos` and folder `files/photos`. | Godot's library already declares androidx's FileProvider; the manifest merger allows one element per provider class. |
| Platform | The launcher activity is Godot's (`com.godot.game.GodotAppLauncher`), not `com.pocketarcade.MainActivity`; the "play" extra is read from whichever intent started or resumed the app. | Godot owns the activity. Launchers re-resolve the app's launcher activity after an update. |
| Platform | The APK is 26.1 MB (arm64-v8a only, native libraries compressed): Godot's engine library alone is 67.6 MB uncompressed, 22 MB compressed. build-13 was 7.6 MB. A smaller APK would need a custom-built engine with unused modules removed. | Not attempted yet (see the final report). |
| Build | Exports run Gradle with `-Dorg.gradle.daemon=false`. | A Gradle daemon keeps Godot's output open after the build, so the export never returns. |
