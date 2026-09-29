# Pocket Arcade

A 3D arcade for Android that you walk around in. Stroll a blacklight-carpeted arcade floor with a floating joystick, from overhead or in first person at a kid's eye height, past banks of claw machines, skee-ball alleys, linked racers, light-gun cabinets, pinball tables, fishing tubs and a coin-pusher island. Step up to a glowing cabinet, spend a token, and the camera dives into the machine's screen. Play a 40–75 second round, watch your tickets print out of the slot, then trade them at the prize counter for hats, outfits and decorations that show up in the hall.

Everything is made in code. The hall and every machine are rendered on the GPU with OpenGL ES 3: per-pixel lighting from dozens of coloured lights with rim light, 4× anti-aliasing, a multi-scale bloom glow on neon and screens, and colour grading. Every model, texture, font glyph and sound is generated at runtime. Textures are painted with Android's 2D canvas, and sounds are synthesized through `AudioTrack`. The app ships no image or audio files and uses no third-party libraries beyond AndroidX/Compose.

<p>
  <img src="docs/screenshots/title.png" width="160" alt="Title screen">
  <img src="docs/screenshots/hall.png" width="160" alt="The arcade hall">
  <img src="docs/screenshots/cafe.png" width="160" alt="The café">
  <img src="docs/screenshots/first-person.png" width="160" alt="First person">
  <img src="docs/screenshots/prize-counter.png" width="160" alt="Prize counter">
  <img src="docs/screenshots/claw.png" width="160" alt="Claw machine">
  <img src="docs/screenshots/skeeball.png" width="160" alt="Skee-ball">
</p>
<p>
  <img src="docs/screenshots/whack.png" width="160" alt="Whack-a-mole">
  <img src="docs/screenshots/pusher.png" width="160" alt="Coin pusher">
  <img src="docs/screenshots/hoops.png" width="160" alt="Hoop shot">
  <img src="docs/screenshots/airhockey.png" width="160" alt="Air hockey">
  <img src="docs/screenshots/racer.png" width="160" alt="Turbo racer">
  <img src="docs/screenshots/stacker.png" width="160" alt="Stacker">
</p>
<p>
  <img src="docs/screenshots/shooter.png" width="160" alt="Shootout light-gun shooter">
  <img src="docs/screenshots/pinball.png" width="160" alt="Star Flipper pinball">
  <img src="docs/screenshots/fishing.png" width="160" alt="Gone Fishing">
</p>

## Features

**The hall**
- A real arcade floor plan: a wide main aisle runs straight from the doors to the prize counter, with rows of banks either side facing the doors across cross aisles, and a neon sign on the wall over each zone:
  - at the back, a prize counter with a wall of plushies, flanked by banks of claw machines and stackers;
  - then the ticket games: skee-ball and basketball alleys side by side, and a four-machine coin-pusher row under a JACKPOT sign;
  - the table games: whack-a-moles and air hockey tables, and pinball tables along the right wall;
  - the video games: linked racers under their hung sign and light-gun cabinets, fronts in line across the aisle;
  - the family floor by the entrance: fishing tubs, kiddie rides, a photo booth, and the token kiosk just inside the doors;
  - in the front-left corner, open to the main aisle, a café: a service counter with a pastry case, slushie and soft-serve machines, a lit menu board and a neon sign, vending machines, diner booths and tables under pendant lamps, on a checked tile floor;
  - two spare banks, beside the pinball tables and on the family floor, ready for new machines.
- Every cabinet is modelled for its game, from glass claw boxes with a working gantry to long lanes, basketball cages, racer seats and pusher shelves that sweep. Each has a live attract-mode screen, a backlit marquee and topper with the game's emblem, painted side art, a lit coin door, chasing bulbs, a neon glow and its own coloured light. Bodies, side panels, rails and backboards have chamfered edges that catch the light and paint that darkens towards the floor, so the cabinets sit in it.
- A lighting rig: trusses and wall rails with spotlights that throw beams through the haze and coloured pools in front of every bank, backlit murals, a hung TURBO RACEWAY sign with start lights, and chase bulbs round the entrance.
- Blacklight carpet that fluoresces, terrazzo at the entrance, walls rising into the dark with acoustic panels, uplights and a backlit mural, and the street outside through the cut-away shopfront.
- 3D kids with a walk cycle, poses for playing, cheering and sitting, and hats. Each throws a soft shadow away from the lamps above (a stripe for legs and torso, a disc for the head, and a blob under the feet) that swings smoothly as they walk between lights. Yours follows a floating joystick that appears wherever your thumb lands. The other kids find their way between machines, play them, queue at the café till and sit down with a drink or a cone. A barista wipes the counter and makes each order at the slushie, espresso or soft-serve station.
- **First person.** The eye button next to the trophy switches between the overhead camera and a kid's-eye view, easing between the two. In first person the left thumb walks (forward, back and strafe), the right thumb drags to look, and your choice is remembered. Up close the hall is finished on every side: closed cabinet backs with service panels, a ceiling of acoustic tiles, light panels, ducts and sprinklers, and a glass shopfront onto the street.
- **Run lock.** In first person, push the stick to its rim to lock a run: ease your thumb off and keep running until you lift it or come back under half a push. A ring and RUN on the stick show it's locked. (Settings can go back to running only while the thumb is at the rim.)
- **Tap to walk, in either view.** Tap a machine, the token kiosk, the prize counter or a patch of floor and your kid walks there and turns to face it. Touching the stick takes over at once.
- **Map.** The map button (under the trophy) opens the floor plan: props as tinted boxes, a marker for every machine in its neon colour and marquee text, the café, the token kiosk, the prize counter and the doors, and a pulsing dot where you stand. Tap a place and your kid walks there.
- **Settings.** The gear button opens look speed, invert look, left-handed (the walk and look halves swap), first-person field of view, run mode, reduce motion (no head bob, run zoom or screen shake), haptics, and effects and ambience volume.
- Collision against walls, cabinets and furniture, with sliding along edges.
- Walk up to a machine and a **▶ PLAY (1 token)** prompt pops up, and the cabinet you're standing at (or the token kiosk, or the prize counter) glows a little brighter and pulses gently, with its floor light pool swelling, fading in and out as you step up and away. Tap the prompt and the camera flies into the screen of the cabinet you're standing at. Exiting flies you back out to exactly where you stood (in first person, facing the machine).
- An ambient arcade soundscape: mains hum, crowd murmur and distant machine bleeps.

**Eleven machines**, each a full 3D game that pays out tickets:
1. **Claw machine**: a glass box full of 3D plushies, with a gantry and a claw that swings on its cable (a real variable-length pendulum), with hinged prongs that open and close. Prizes are a physics pile. Whether a prize holds depends on the machine's grip for that grab and how centred you were, and it can slip on the way up. Now and then you get a gold **lucky claw** turn. Prizes you win go into your plush collection (11 to find, including a rare golden cat).
2. **Skee-ball**: a player's-eye alley with a tilted target board and raised ring walls. Drag the ball to aim, then flick up. Flick speed sets how far it jumps. Rings are worth 10–100, with a 200-point bonus hole in the corner.
3. **Whack-a-mole**: a table with real holes. Moles, golden moles and bombs rise out of them and you bonk them with a 3D mallet. It speeds up as the round goes on, and hits in a row build a combo.
4. **Coin pusher**: tap to drop coins onto a packed deck while the shelf sweeps back and forth. Coins that spill over the lip land in the win tray. Gems, big coins, ticket bundles and coin-shower stars are mixed in, and five spills in quick succession trigger an avalanche bonus.
5. **Hoop shot**: flick to shoot at a real 3D rim, net and backboard, with rim bounces and banks. Makes in a row multiply your points up to ×5, and the hoop starts moving in the second half.
6. **Air hockey**: drag your mallet and smash the puck past the CPU on an air table with rounded corners. The CPU speeds up as you pull ahead. Goals in a row score more, and the first to 7 wins.
7. **Turbo racer**: a three-lap synthwave race against seven rivals on a closed circuit. Start from the grid under the start lights, drag to steer through the bends and over the hills, and hold a second finger to drift through corners and charge a turbo. Rivals take racing lines, overtake and bump. A live position and lap counter tracks the race, and you're paid for your finishing place, clean passes and time left.
8. **Stacker**: tap to drop each sliding slab onto the tower. Any overhang is cut off and tumbles away. Line one up perfectly to keep it whole and build a combo. You get three misses.
9. **Shootout** (new): a light-gun cabinet with two mounted pistols. Tap to shoot exactly where you tap. Targets pop out of cover and fly in: red-ringed gunmen fire back, gold drones are rare and pay big, and hands-up civilians must not be shot. Six shots, then tap the reload bar. Hits in a row build a combo, accuracy pays a bonus, and the round ends with a boss.
10. **Star Flipper** (new): a pinball table seen down the glass. Hold the left or right half of the screen to flip, pull the plunger down and let go to launch, and nudge with a swipe up (don't tilt). Pop bumpers, slingshots, drop targets for a multiplier and three top lanes that light multiball.
11. **Gone Fishing** (new): a round pond tub. Hold on the pond to charge a cast and let go at the right power, strike when the bobber dives, then draw circles on the reel to wind the fish in. Ease off when it pulls, or the line snaps. Bigger and rarer fish, up to a golden one, are worth more.

**Game feel**
- Screen shake, particles, squash and stretch, popping score text and a 3-2-1-GO countdown.
- Ticket strips print out of each machine with a counter ticking up; tap to skip.
- Haptics on hits, wins and jackpots.
- A high score for every machine, shown on the cabinet screens in the hall. Beating one sets off confetti and a fanfare.
- Smooth type with vector icons for tokens, tickets and stars, glossy arcade buttons, and one bright palette across the whole app.
- The prize counter shows each prize as a 3D model on a turntable, photographed by the GPU.

**Progress** is saved with DataStore: tokens, tickets, prize collection, owned and equipped cosmetics, bought decorations, high scores and the daily refill date. The settings live in a separate DataStore file, so options and progress never touch.

- You start with **20 tokens** and get **10 free every day**.
- The token machine trades **40 tickets for 1 token**. If you are completely out, you can grab a free spare token every 3 minutes, so tokens never run out for good.
- A round's tickets, score and prizes are saved even if you leave the moment it ends, and a quick double tap on PLAY AGAIN or EXIT never spends or refunds a token twice.
- The save can't crash the app. A save file that is damaged is reset (you start afresh) instead of blocking every launch, and a write that fails on disk is logged and treated as "couldn't do it".

`ArcadeRepository` (in `data/`) is the one place that reads and writes the save, and takes its `DataStore<Preferences>` as a constructor argument so tests run it on a temp file. Besides the above it keeps building blocks for later features, saved as plain strings that skip bad parts when read: `spendTickets`, free-form counters (`addStat("plays:racer")`), unlock ids (`unlock`), namespaced collectibles (`addCollectible("fish:trout")`), the arcade's name (`setArcadeName`, A-Z, 0-9 and spaces, up to 14 characters) and a top-5 score table per machine with three-character initials (`recordScoreEntry`, `ScoreTables`). Their on-disk key names are the save format, so never rename one.

## The 3D engine

`engine/r3d` records scenes and `engine/gl` draws them with OpenGL ES 3:

- **Threads.** Each screen records a frame of textured polygons and model instances on the UI thread. A dedicated GL thread draws it into a `TextureView` under the Compose UI.
- **Geometry.** Static models live on the GPU as vertex buffers and draw as instances. Polygons are sorted into opaque, alpha-blended and additive passes, so glass, glows, neon and sparks all work.
- **Lighting.**
  - Per-pixel: ambient, one directional light and up to 64 coloured point lights. A light grid across the floor keeps the many lights cheap.
  - Glossy highlights with per-material sharpness, rim light that lifts cabinets and kids off the dark floor, darkening fog and filmic tone mapping.
  - Normals are transformed with the inverse-transpose, so squashed and stretched models light correctly.
- **Reflections.** Glossy materials (chrome, glass, polished paint and tiles) reflect a small arcade room painted into a cube map in code. Neon, screens, marquees and bulbs reflect in the glossy floor, gathered from the previous frame's glow at no extra draw cost.
- **Models.** They are built from boxes, cylinders, lathes, spheres, capsules and tori, and move as jointed parts: the claw's prongs, the mallet's swing, the kids' limbs. A polygon can carry a brightness per vertex (baked ambient occlusion, used at the foot of cabinets); `beveledBox` in `hub/CabinetForm.kt` is the chamfered, floor-shaded box the cabinets are built from.
- **Image quality.**
  - Scenes render with 4× multisampling, alpha-to-coverage for cut-outs, a multi-scale bloom glow, colour grading with dither, and a vignette.
  - Textures use premultiplied alpha, so cut-outs and glows have no dark fringes.
  - The multisampled buffers are discarded after the resolve, which saves memory bandwidth on tile-based phone GPUs.
  - The render resolution eases down if frames run long and recovers after a hitch; on fast GPUs with timer queries it can go above the usual 0.8.
- **Textures.** They are painted with Android's `Canvas` (gradients, real fonts, glows and grain). A texture can be stored at a finer resolution than the texel size the code maps it in. Live textures such as cabinet screens reuse their upload buffers instead of allocating a copy every frame, repaint only while their cabinet is in view (alternating frames, staggered between cabinets), and lay their scanlines and glass sheen on in one draw of a pre-painted overlay.
- **Culling.** The hall culls cabinets, fixtures, kids and lights against the camera's view frustum and a draw distance hidden by fog, so the same scene works looking straight down or level at eye height. Each pass sets its own near plane.
- **Stage3D.** Gives each game a 3D view that maps touches onto world planes and world points back to the screen.
- **GL context loss.** GPU resources are tagged with a process-wide generation, so models and textures cached for the whole app are re-uploaded after the activity is recreated.

## Install the APK on your phone

Every push to `main` builds a debug APK and publishes it as a GitHub Release.

1. On your Android phone (Android 8.0 or newer), open **https://github.com/gh00ul/pocket-arcade/releases/latest**.
2. Tap **PocketArcade.apk** to download it.
3. Open the download. If Android asks, allow your browser to *install unknown apps*, then tap **Install**.

All builds are signed with the same key (`app/debug.keystore`), so newer builds install over older ones and keep your progress.

## Build it yourself

You need JDK 17 or newer and the Android SDK with platform 36.

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or open the project in Android Studio and press Run. The debug build is compiled non-debuggable, because recording each 3D frame needs ART's optimising compiler to hit 60 fps. The phone needs OpenGL ES 3.0, which nearly every phone running Android 8.0 or newer supports.

To jump straight into a machine (it still costs a token), pass its id:

```bash
adb shell am start -n com.pocketarcade/.MainActivity --es play racer
```

The ids are `claw`, `whack`, `skeeball`, `hoops`, `pusher`, `airhockey`, `racer`, `stacker`, `shooter`, `pinball` and `fishing`. To log frame times once a second, run `adb shell setprop log.tag.PocketArcade3D DEBUG` and restart the app. Each line shows the UI-thread record time, the GL draw time, the swap interval, GPU time where the driver supports timer queries, and the render scale. To compare builds like for like, also run `adb shell setprop log.tag.PocketArcade3DPin DEBUG`, which holds the render scale at 0.8.

The unit tests play every machine headlessly with seeded bots of different skill, check that every round finishes and pays out within the target bands, and print average tickets per round. They also check the hall floor plan (no overlaps, every cabinet reachable on the same walk grid the kids use, a clear main aisle from the doors to the prize counter, no bank hiding another's players from the hall camera):

```bash
./gradlew testDebugUnitTest
```

## Add a new machine

Write one class and register it in one place. The hall gives it a cabinet, a play mat, a prompt, a high-score table and an attract screen automatically, and puts it in one of the spare banks. If every bank is taken, the floor plan fails loudly instead of overlapping anything: add a `Slot` to `HubLayout.slots`.

1. Create `app/src/main/java/com/pocketarcade/games/<name>/<Name>Game.kt` and extend `BaseMiniGame`, which implements the `MiniGame` interface and provides particles, screen shake, popups, a score and a round clock. Draw with a `Stage3D`, or straight onto the Compose `DrawScope` for a flat game:

```kotlin
class BowlingGame : BaseMiniGame() {
    override val id = "bowling"                 // save key for the high score: never change it
    override val title = "BOWLING"              // intro card and results
    override val marquee = "BOWL"               // up to 6 characters, printed on the cabinet
    override val instructions = listOf("FLICK THE BALL", "KNOCK DOWN THE PINS")
    override val look = CabinetLook(body = Pal.TEAL, trim = Pal.WHITE, glow = Pal.CYAN, shape = CabinetShape.LANE)
    override val roundSeconds = 45f

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 200f, 700f, 180f, 0f, 100f, fovDeg = 45f)   // camera eye, target, field of view
    }

    override fun reset() { /* set up a fresh round */ }
    override fun step(dt: Float) { /* fixed 1/120 s simulation step; call addScore(...) */ }
    override fun render(scope: DrawScope) {
        val r = stage.begin()
        // r.quad(...), r.sprite(...), model.draw(r, xf = ...) and so on
        stage.present()
    }
    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        // x, y are field units (360 x 640); stage.touchToPlane(x, y, 0f, out) finds the spot in the world
    }
    override fun cancelInput() { /* the host stopped forwarding touches (pause): forget every tracked pointer */ }
    override fun ticketsFor(score: Int) = 1 + score / 20
    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) { /* tiny cabinet screen */ }
}
```

2. Add it to the list in `games/GameRegistry.kt`:

```kotlin
fun createAll(): List<MiniGame> = listOf(
    ClawMachineGame(), WhackAMoleGame(), SkeeBallGame(), HoopsGame(),
    CoinPusherGame(), AirHockeyGame(), RacerGame(), StackerGame(),
    ShooterGame(), PinballGame(), FishingGame(),
    BowlingGame(),
)
```

That's all. The host takes care of the intro card, countdown, timer, pause/quit, results, ticket printing, token spending and saving. Override `isSettled()` if your round has things still in motion after the clock runs out, and set `endedEarly = true` to finish a round before the clock does. `cancelInput()` is called whenever the host stops forwarding touches (pause, Back, the app going to the background, the round ending), so a finger lifted meanwhile can't leave a control stuck.

To give the machine its own hall cabinet instead of a generic one, point `override val cabinet` at an object implementing `CabinetDesign` (`hub/CabinetDesign.kt`) in the game's package. It declares the footprint, the camera dive point and the attract screen size, and builds the model from shared pieces (side panels, a marquee with chase bulbs, an LED display, glass, posts, lights and a live screen). `RacerCabinet`, `ShooterCabinet`, `PinballCabinet` and `FishingCabinet` are examples.

Each game gets a headless test in `app/src/test/java/com/pocketarcade/games/<name>/` built on `SimHarness`, which plays rounds exactly like the host (including the 1.2 s ending) and checks the payout bands.

## Project layout

```
app/src/main/java/com/pocketarcade/
├── MainActivity.kt        single activity: immersive, portrait, audio lifecycle, launch shortcut
├── ArcadeApp.kt           title → hall ↔ machine flow and the camera dive transitions
├── engine/                fixed-step loop, touch/flick tracking, circle physics, audio synth,
│   │                      particles, shake/springs, haptics, the game's type and icons
│   ├── r3d/               scene recorder, camera, lighting, models, painted textures, Stage3D
│   └── gl/                OpenGL ES 3 thread, renderer, shaders and the render surface
├── hub/                   hall floor plan and banks, scene, cabinets and the CabinetDesign seam,
│                          fixtures, 3D kids, camera, joystick
├── games/                 MiniGame interface, BaseMiniGame, GameRegistry
│   ├── claw/  skeeball/  whackamole/  coinpusher/  hoops/
│   ├── airhockey/  racer/  stacker/
│   └── shooter/  pinball/  fishing/
├── data/                  DataStore repository, save state, prize catalog, settings store
└── ui/                    HUD, title, prize counter, token machine, profile, map, settings,
                           game host, widgets, and the thumbnail studio
```

Tuning knobs for every game (difficulty and payouts) are grouped at the top of each game file in a `*Tuning` object: `ClawTuning`, `SkeeTuning`, `WhackTuning`, `PusherTuning`, `HoopsTuning`, `HockeyTuning`, `RacerTuning`, `StackerTuning`, `ShooterTuning`, `PinballTuning` and `FishingTuning`.
