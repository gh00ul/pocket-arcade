# Parity appendix: the 11 machines

Checklists to close per game, condensed from a read of build-13's `games/` packages. Each bullet is
a behaviour the port must reproduce (values as in the Kotlin source, which stays the authority).
Part of [PORT_PARITY.md](../PORT_PARITY.md).

## Shared (games/)
- Field 360 × 640 units; fixed step 1/120 s. `GameRegistry` order: claw, whack, skeeball, hoops, pusher, airhockey, racer, stacker, shooter, pinball, fishing.
- `BaseMiniGame`: `Particles(700)`, `ScreenShake(maxOffset 16)`, `FloatingTexts`, `Flash(decay 3)`; `rng = Random(seed ?: nanoTime)` re-seeded in `start`; `update` order: time → onTimeUp once → step → particles/shake/popups/flash; `finished = (timeUp || endedEarly) && isSettled`; `addScore` clamps ≥ 0 and pops "+N"/label at size 3; white flash `flash × 0.55 × ScreenShake.intensity`; helpers hitStop/slowMo/punch/impact/bigMoment with the 12 companion constants (none of the 11 games calls them).
- `GameFx` (hitStop max, slowMo min speed + max seconds, punch max; flush hands them to `TimeScale`), `CabinetLook`, `CabinetShape` (15), `MiniGame` contract including `drawAttract(painter, w, h, t)` and `cancelInput`.
- `SceneFx`: ring/shaft/flare/streak textures, pool, glow, shockwave, burstRing, flare, shaft, dim (used by claw, pusher, skee-ball, whack).
- Visual randomness uses `hash01` (never the game rng); a few Kotlin draws use `System.identityHashCode` (not reproducible: the port uses a stable per-object hash).

## Claw machine (`claw`, CLAW, 45 s, pink/yellow/hotpink)
- `CircleWorld(gravity 900)`, 6 iterations; floor, right wall, lip wall, left wall segments with their bounces.
- Pile of 14 plushies by catalog weight, positions/angles from rng, 420 pre-settle steps, then frozen.
- States IDLE → DROPPING → CLOSING (0.45 s) → LIFTING → CARRYING → RELEASING (0.55 s).
- Trolley: accel 900 to ±170, brake 1400, x 46..322; carry speed 150 toward the chute (x 48), release rules (|toGo| < 1.5 with low speed and swing, or 4 s).
- Drop: cable +200/s; contact on floor or a plush under the tip; CLAW_GRAB + tick.
- `resolveGrab`: reach 16 (lucky 30), most-centred target, then `grip = range(.6, 1)`; hold chance `0.85·grip·centred²/√(r/24)` (lucky: 1); slipAt 99 / 0.12 / range(.2, 1.35); "MISSED!" path.
- Slip during lift/carry with warning jiggle in the last 0.22; "SO CLOSE!"; held body follows the claw (hang, velocity, angle easing).
- Pendulum with variable cable length (coupling .35, heavy factor, cable-speed term, damping .9, θ ±0.9).
- Uprighting of slow plushies; wins when a loose plush is past the lip into the chute ("GOT IT!"/"RARE PRIZE!", PRIZE/JACKPOT, confetti, `onCollectible`); respawn timers; lost plushies removed.
- Lucky claw: 12% after the first grab ("LUCKY CLAW!", LUCKY, sparkles, gold claw light).
- Input: LEFT/RIGHT buttons (multi-touch, slide on/off), DROP circle (r 60 hit); errors when busy; `cancelInput` drops both buttons.
- Motor sounds every 0.2 s; tickets `2 + score/10`.
- 3D: camera (180,340,860)→(180,215,0) fov 46; lights (top, front, rims, claw, chute, lucky); cabinet + neon models; trolley LED by state; 3 prongs; plush models (10 shapes, face texture, AO, pale cap); bulbs, shafts, motes, glass, glare; HUD buttons and banner; attract 7 s loop.

## Whack-a-mole (`whack`, WHACK, 45 s, green/brown/lime)
- 3×3 holes; mole kinds NORMAL/GOLD/BOMB; phases HIDDEN/RISING(.12)/UP/FALLING(.14)/BONKED(.55); squash spring.
- Spawn interval `lerp(.95, .38, progress)·range(.75, 1.25)`; bomb chance `lerp(.10, .22)`, gold .08; upTime `lerp(1.15, .55)·range(.85, 1.15)`.
- Escaping non-bomb mole resets the combo; onTimeUp drops moles.
- Hit test on projected holes (50s half-width, −92s..+26s), nearest wins; misses (THUD, combo reset); stun 0.5 s after a bomb.
- Scoring: normal 10 / gold 20 + `(combo/5)·2`; bomb −30 "OUCH!" + flash + heavy; combo popups every 5.
- Tickets `1 + score/30`. 3D table (grass with holes, wells, rims), mole/bomb models, mallet animation, gold glow, bomb pulse, stars, hit rings, bulbs, clouds, combo panel texture; attract.

## Skee-ball (`skeeball`, SKEEBALL, 40 s, blue/yellow/sky)
- Pool of 6 balls; ready ball drag (y > 430), flick release (min up 320), speed `clamp(|v|·.33, 120, 760)`, angle ±32°.
- Rolling friction 60, lane walls (×.6), ramp launch at y 318 (`vz = planar·.5`), roll-back, gutter.
- Flight gravity 1000, side walls, back wall; landing → ring by squashed distance from (180,146), bonus pocket (318,60) = 200.
- Ring points {100,50,40,30,20,10}, radii {13,30,48,68,90,118}; settling hop; score events (BONUS!! / BULLSEYE! / coins).
- 8 s failsafe; reload 0.35 s; tickets `1 + score/50`.
- 3D lane/ramp/board with `surfaceY`, ring walls, marquee + bulbs, chevrons, impacts, trail, speed meter (ideal band), attract.

## Hoop shot (`hoops`, HOOPS, 44 s, red/orange/orange)
- Metres; G 9.8; projection shared with the camera; ideal power from 68° to the rim.
- Flick (min 380): power `clamp(ideal·(1 + (up − 1700)·.0001), 3, 11)`, aim assist .6 + lateral .0012.
- Moving hoop after 22 s (amplitude .45 m, period 3.2 s) with popup.
- Rim torus collision (restitution .55, rng deflection), backboard, cage, floor bounces; basket detection; funnelling.
- Streak multiplier ×1..×5, swish +3; "STREAK OVER"; on fire at streak 5 (flames).
- Tickets `2 + score/20`. Scene: gym, court, board/LED strip, net (10 strands, twist, swish, bulge), cage, readouts, neon, flashes, trails, bursts; attract.

## Coin pusher (`pusher`, PUSHER, 50 s, orange/gold/yellow)
- No-gravity `CircleWorld`, hex-packed deck (gaps .06, jitter), 4 starting items, 90 settle steps.
- Shelf `191 − cos(phase)·41` with period 3.2 s pushing bodies.
- 25 coins; drop cooldown .18; tap position → drop x; 14% item drop; "5 COINS LEFT".
- Removal over the front edge (scores) or into the gutters ("LOST").
- Kinds: COIN +10, GEM +50, BIG +50, TICKETS +10 bonus tickets (no score), STAR → coin shower (6 free drops).
- Avalanche (≥5 spills in .9 s) +30; ends 3.5 s after the last coin.
- Tickets `1 + score/15` + bonus tickets. Scene: deck, shelf with LEDs, tray pile, LED panels, items, bulbs, impacts; attract.

## Air hockey (`airhockey`, AIR_HOCKEY, 60 s, sky/white/cyan)
- Rink 40..320 × 60..600, goal half 62, puck r 13, mallet r 22, corner r 46; 4 substeps.
- Player mallet chases the finger (2200 u/s) in its half; puck drag .45, walls .9, mallets .85, max 1300.
- CPU: reaction .16 s, speed `lerp(360, 680, …)` with lead rubber band, attack/defend/home/stuck rules.
- Goals: player 100 + 25 per streak, "HAT TRICK!", first to 7 wins +300 and ends; CPU goals reset the streak; serves.
- Tickets `2 + score/50`. Scene: neon table, scoreboard texture, glows, trails, bursts; attract.

## Turbo racer (`racer`, RACER, 72 s, darkred/white/red, `RacerCabinet`, tilt)
- 14-section track (len/bend/hill tables), 3 laps, racing line, 8 cars (player slot 6), rival skill/bias/launch.
- Player speed model (1100, boost 1450, corner scrub, drift-on-straight drag, off-road 450), steering 620/s, centrifugal push, inside-line gain.
- Distance points every 350 units on road only; drift charge → TURBO (≥1, .8 s) / SUPER TURBO (≥2.2, 1.5 s).
- AI targets, passing, rubber band, contacts (shove, rear-end transfer), clean passes +25, ranking, lap popups.
- Flag: place points [500,380,290,220,160,110,70,40] + time bonus 12/s; DNF pays 40% of the place.
- Input: steer finger (relative ×1.6), drift button (302,528 r 46) or any second finger; tilt (TiltMath) with recentre.
- Haptic rumble on the verge and by pace; engine and skid sounds.
- Tickets `1 + score/40`. Scene: sky (stars, sun, searchlights, parallax skylines), road segments with neon ground, rumble strips, props, gantry lights, cars (hi/lo models), flames, speed lines, smoke, skids, HUD boxes; attract; cabinet.

## Stacker (`stacker`, TOWER, 60 s, violet/cyan/purple)
- Slab 120×120×18, alternating axes, speed `min(150 + 7·height, 430)`, swing ±170.
- Drop: miss (lose a life of 3; topple ends), perfect (≤5, combo growth after 3), cut (overhang chunk).
- Points 10 + perfect bonus `10 + 5·min(combo − 1, 4)`; milestones every 10 levels.
- Tickets `1 + score/20`. Scene: rooftop over a night city, clouds, light column, markers, neon-edged slabs with fading glow, bursts; HUD; attract.

## Shootout (`shooter`, GUN, 55 s, navy/orange/orange, `ShooterCabinet`)
- Waves by time (13/26/38 s, boss), spawn tables per wave, gold drone at 15..34 s.
- Target states OFF/RISE/UP/TELL/DUCK/HIT/FLY/FLEE; gunners fire back after the tell; civilians.
- Hit test through the fixed aiming camera with cover occlusion, boss parts priority.
- Clip of 6, reload by tapping the bar (.75 s), hearts 3, down 1.6 s; combo multiplier to ×4.
- Scoring: goon 50 (+25 quick), drone 75, gold 300, civilian −100, hurt −50; boss 100 HP (pods, eye stagger, cannon charge), kill 1500 + time bonus + 5 bonus tickets; accuracy bonus.
- Tickets `2 + score/400` + 5 on a boss kill. Scene: bank street, cover, figures, drones, mothership, pistol + recoil, casings, decals, lights; HUD; attract; cabinet with blinking guns.

## Star flipper (`pinball`, PINBALL, 60 s, purple/cyan/hotpink, `PinballCabinet`)
- 2D table 300×640, 3 balls, gravity 1000, drag .04, substeps by speed (≤24).
- Walls/arcs/gate/orbit/deflectors/inlanes/slings/guides from `PinballTable.init`; posts, 3 bumpers, 3 drop targets, spinner, rollovers.
- Flippers (pivots, angles, speeds 24/14, tapered capsule contact with surface velocity, cradling).
- Plunger (pull 110 → 500..1750), auto launch 1650, ball save 6 s (multiball 8 s), stuck failsafe "BALL SEARCH".
- Scoring ×multiplier (≤5): bumper 10, sling 5, drop 25 (+bank 100 → multiplier up), lanes 25/5 (all lit 150 → multiball), orbit 40 / jackpot 300 in multiball, spinner 3 per turn.
- Nudge (swipe up > 45 within 170 ms) with tilt meter (warn 1.6, tilt 2.6).
- Tickets `1 + score/60`. Scene: playfield art, rails, backbox with 128×32 amber DMD (fonts, views), chase bulbs, mood colour, bumper caps, slings, targets, spinner, lamps, chrome ball + trail, glass; hints; attract; cabinet.

## Gone fishing (`fishing`, FISHING, 55 s, teal/yellow/sky, `FishingCabinet`)
- Phases IDLE/CHARGE/FLIGHT/WAIT/FIGHT/LANDING/RECOVER; fish modes; pull states REST/WARN/RUN.
- Cast power triangle wave (1.4 s), aim ±38°, distance 90..470; splash spooks.
- Bites: suitor search (appetite), approach, nibbles, BITE window 1 s (boot 1.4), strike by cranking .6 rad, too-soon spooks.
- Crank (angle around the reel, rate smoothing .35, spin-down) reels in; fight tension model (rest/warn/run, stamina .82, snap > 1 for .35 s, slack < .2 for 1.2 s, timeout 40 s).
- Species PERCH/BASS/CATFISH/GOLDEN CARP/OLD BOOT with length/speed/weight/value/pull/run/appetite; spawn odds; boids-lite swimming.
- Scoring `round(weight × value)`; WHOPPER ≥ 50; golden carp; boot "JUNK!".
- Tickets `2 + score/10`. Scene: golden-hour pond, scenery, fish (lathe bodies, tails), pads, rod bend, bobber, water layers, ripples, splashes, line sag/strain, aim, fireflies; reel + gauge HUD; attract; tub cabinet.
