# Parity appendix: the hall

Checklists to close for `hub/`, condensed from a read of build-13. World units: x across, z from
the back wall (0) toward the entrance, y up; yaw 0 faces the entrance. Part of
[PORT_PARITY.md](../PORT_PARITY.md).

## Floor plan (`HubMap.kt`, `CafeLayout.kt`)
- `HubLayout`: TILE 16, 608 × 1100, walls (16, back 24, front 1080), wall height 150, ceiling 380, doors 264..344, main aisle 256..352 kept clear, spawn (304, 1045), clerk (304, 58), disco (304, 470).
- `cabinetSize` per shape and `focus` per shape (dive camera); `CabinetDesign` overrides.
- 13 slots in order (CLAW×4, TOWER×3, SKEEBALL×4, HOOPS×3, PUSHER×4, WHACK×3, AIR_HOCKEY×2, PINBALL×4, RACER×4, GUN×3, FISHING×2, two spare banks); bank assignment by shape then spares, fit checks.
- Per cabinet: MACHINE prop, spot (stand area, prompt anchor, focus), "playing" hangout.
- Prize corner (wall, counter, solids, PRIZES spot), 4 pillars, family floor (2 kiddie rides, bench, photo booth), entrance (TOKENS + CHANGE kiosks, trash, plant, doors).
- Walk grid; 11 aisle hangouts; hangout snapping; `spotTypeOf` (PHOTO, RIDE, VENDING, CAFE, TROPHY, TANK, JUKEBOX); `standArea` rules.
- Wall signs (SKEE-BALL, JACKPOT, PINBALL*, FISHING*, SNACK BAR) and posters.
- Café floor plan: bar, counter, stations, slush tanks, tables + chairs, booths, queue spots.

## Collision (`Collision.kt`)
- Overhead feet box 10 × 5, x then y, flush stops by binary search.
- First-person body r 10 with front gap, sub-steps, push-out passes, refusal of penetrating steps, slide-round offsets.
- Kid vs player distance 19, push .35.

## Player, controls, cameras
- Player speeds (78, accel .18, stop .1, back .72, strafe .85, run 1.35); overhead turning 12 rad/s.
- First person: look drag 0.3°/dp × look scale (pitch ×.7, invert), smoothing .012, pitch levelling to −6°, run latch (.98/.5), assist toward the machine in reach.
- Joystick (r 56 dp, dead zone .1, curve, run from .9, floating base); tap-to-walk via ray casts against props and floor; `WalkRoute` (BFS + string pulling, slow-down, stuck detection).
- Bumps (wall, kid) with haptics; entering a spot BLIP + soft haptic; footsteps by gait phase.
- Overhead camera (pitch 55, fov 56, cover 370 × 420, lead, clamps); first-person camera (eye 54, FOV 70 widened for tall screens, FOV setting, bob, run kick 4°, blend .5 s); title entrance (pullback .2, fov +.08); dive into a cabinet (eye to focus, FOV 50).

## People
- 16 kids: looks, spawn, `Npc` states (IDLE/WALK/PLAY/SIT/QUEUE), choose target, routes, poses by state, café queue and service, give way (first person), gaze, notice the player.
- `Emotes`: wave (chance .35, cooldown 20), celebrate (clap/cheer), purchase cheer from the clerk.
- Animation: `AnimMath`, `Spring`, `Gait` (stride, phase, lift), `PoseBlender` (11 poses, blend times), `FigureAnim` (seat, gait weights, turning, arm swing, spine, lean, bank, head look, idle life, fidgets, blinks, follow-through, hat/ponytail springs, reduce-motion damping, cheer wind-up, wave, clap, per-pose joint targets).
- `Figure` geometry (44 tall), hair styles, eyelids, treats, hats (10 incl. spinning propeller, emissive halo), `Looks` palettes and picks, fixed player/clerk/barista looks.
- `FigureShadow`: lights above y 100 cast planar shadows (contact blob, body stripe, head disc).

## Café
- Barista state machine (IDLE/WALK/WIPE/TAKE/MAKE/SERVE) with timings and stations; treats (cup/cone).
- Slush columns, steam puffs, pendants (7) with pools, café lights, `CafeArt` (floor, back bar, menu board, neon CAFÉ, pastry case, till, booths, chairs).

## Props, decor, walls
- Fixtures and their lights: COUNTER (glass cases with plushies), PRIZE_WALL (5 shelves, toy boxes, plushies won in colour and the rest as silhouettes), TOKENS/CHANGE kiosks, VENDING (cola, snacks), PILLAR, KIDDIE_RIDE (rocket, race car), PHOTO_BOOTH (+ photo wall of the 4 newest strips), DOORS, BENCH, PLANT, TRASH, STOOL, CAFE_TABLE.
- 9 decor styles at fixed places with lights and floor shades; disco ball floor spots.
- Lighting rig: 45 ceiling downlights, 2 wall neons, 2 street lamps; per-frame pick of 64 nearest the key point; flicker; ambient/dir/fog/draw distance/exposure/bloom; floor glow .45, floor reflect 1.3, matte reflect.
- `HallRig`: trusses, rails, up to 32 spotlights aimed at banks with beams, pools, lens sprites.
- Murals, racer sign with start lights, entrance chase bulbs, indoor model (ceiling, troffers, ducts, sprinklers, shopfront, street backdrop), carpet/tiles, wall washers, neon lines, "POCKET ARCADE" / "HIGH SCORE" neon.
- Floor shade, terrazzo logo, cabinet floor glow, route marker.

## Cabinets
- `CabinetDesign` seam (`build`, `animate`), `CabinetBuild` helpers and glow constants, `CabinetBox.phase`.
- `beveledBox` (bevel .8, floor AO), `CabinetPaint` sizes, grime/scuffs, `emblem` per shape.
- `MachineArt` per game (paints, side art, marquee, topper, kick, coin door, panels, bezel, LED score display "HI n" / "PLAY!").
- `LiveScreen`: attract repaint on alternate frames, staggered; "HIGH SCORE" card every 9 s; scanlines, sheen, vignette; refresh only in view.
- `MachineUnit` per shape (upright, tall, claw, wide, whack, lane/skee, hoops, pusher, table/hockey, …) with attract motion; marquee chase bulbs; `MachineKit` parts and panel layout.
- `Highlight`: fade .25, emissive boost .2, pulse; pool alpha .08; bulb boost.

## Title showroom and loading
- `TitleShowcase`: one cabinet per game in a row (spacing 58), framing (h-FOV 42, lens shift .585), stage, spotlights, sky, floor, neon; `ShowroomPath` closed Catmull-Rom loop (8 keys, 7.5 s per segment); `TitlePush`.
- Load plans: `TitleUnits` and `HallKit.plan` steps with labels and weights; `HallStage` scene install + GPU warm-up.
- Visibility culling boxes; draw order of the hall's passes.

## Sound and prompts
- `HallSoundscape`: steps (.14, range 200, ≤3 per frame), cheers, crowd level formula, music intensity, listener yaw, cabinet sources with `Attract.kindFor`, café source.
- Prompt bubble per spot type (title, ▶ action, info line, accent, badge, cost icon), pop-in, bob, clamping under the HUD, hints (overhead and first person).
