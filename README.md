# Pocket Arcade

A pixel-art arcade hall for Android that you walk around in. Stroll a neon-carpeted hall with a floating joystick, step up to a glowing cabinet, spend a token, and the camera zooms into the machine. Play a 40–50 second round, watch your tickets print out of the slot, then trade them at the prize counter for hats, outfits and decorations that show up in the hall.

Everything is drawn in code on a Compose `Canvas`: sprites, tiles, fonts, particles, even the launcher icon. Every sound is synthesized at runtime with `AudioTrack`. The app ships no image or audio files and uses no third-party libraries beyond AndroidX/Compose.

<p>
  <img src="docs/screenshots/title.png" width="180" alt="Title screen">
  <img src="docs/screenshots/hall.png" width="180" alt="The arcade hall">
  <img src="docs/screenshots/prize-counter.png" width="180" alt="Prize counter">
  <img src="docs/screenshots/claw.png" width="180" alt="Claw machine">
  <img src="docs/screenshots/hoops.png" width="180" alt="Hoop shot">
</p>

## Features

**The hall**
- Top-down 3/4 view of an arcade about 2–2.5 screens tall. The camera follows you smoothly with a little look-ahead.
- A 4-direction walk cycle for your kid, driven by a floating virtual joystick that appears wherever your thumb lands.
- Collision against walls, cabinets and furniture, with sliding along edges. Props and characters are depth-sorted so you can walk behind things.
- Checkered aisle, retro neon carpet, glowing cabinets with animated attract-mode screens, chasing marquee bulbs, a flickering neon sign and kids wandering between machines (BFS pathfinding).
- Walk up to a machine and a **▶ PLAY (1 token)** prompt floats up. Tap it to zoom and fade into the game. Exiting puts you back exactly where you stood.
- A token machine by the entrance, and a prize counter with a clerk at the back of the hall.
- An ambient arcade soundscape: mains hum, crowd murmur and distant machine bleeps.

**Five machines**, each a full game that pays out tickets:
1. **Claw machine**: move the gantry, drop the claw and watch it swing on its cable (a real variable-length pendulum). Prizes are a physics pile. Whether a prize holds depends on the machine's grip for that grab and how centred you were, and it can slip on the way up. Now and then you get a gold **lucky claw** turn. Prizes you win go into your plush collection (11 to find, including a rare golden cat).
2. **Skee-ball**: drag the ball to aim, then flick up. Flick speed sets how far it jumps and flick angle sets the line. Rings are worth 10–100, with a 200-point bonus hole in the corner. A power meter shows how hard you threw.
3. **Whack-a-mole**: a 3×3 grid that speeds up. Golden moles are worth double, bombs cost points and stun you, and hits in a row build a combo.
4. **Coin pusher**: tap to drop coins onto a packed deck while the shelf sweeps back and forth. Coins spill over the front lip for points and fall into the side gutters for nothing. Gems, big coins, ticket bundles and coin-shower stars are mixed in, and five spills in quick succession trigger an avalanche bonus.
5. **Hoop shot**: flick to shoot in pseudo-3D, with rim bounces, backboard banks and a swaying net. Makes in a row multiply your points up to ×5, and the hoop starts moving in the second half.

**Game feel**
- Screen shake, particles, squash and stretch, popping score text and a 3-2-1-GO countdown.
- Ticket strips print out of each machine with a counter ticking up; tap to skip.
- Haptics on hits, wins and jackpots.
- A high score for every machine, shown on the cabinet screens in the hall. Beating one sets off confetti and a fanfare.
- A bitmap pixel font drawn in code, and one bright retro palette across the whole app.

**Progress** is saved with DataStore: tokens, tickets, prize collection, owned and equipped cosmetics, bought decorations, high scores and the daily refill date.

- You start with **20 tokens** and get **10 free every day**.
- The token machine trades **40 tickets for 1 token**. If you are completely out, you can grab a free spare token every 3 minutes, so tokens never run out for good.

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

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or open the project in Android Studio and press Run.

The headless payout simulation plays every machine with bots of different skill and prints average tickets per round:

```bash
./gradlew testDebugUnitTest
```

## Add a new machine

Write one class and register it in one place. The hall gives it a cabinet, a play mat, a prompt, a high-score table and an attract screen automatically, and grows a new row when it needs room.

1. Create `app/src/main/java/com/pocketarcade/games/<name>/<Name>Game.kt` and extend `BaseMiniGame`, which implements the `MiniGame` interface and provides particles, screen shake, popups, a score and a round clock:

```kotlin
class AirHockeyGame : BaseMiniGame() {
    override val id = "airhockey"              // save key for the high score: never change it
    override val title = "AIR HOCKEY"          // intro card and results
    override val marquee = "HOCKEY"            // up to 6 characters, printed on the cabinet
    override val instructions = listOf("DRAG YOUR PADDLE", "SCORE ON THE ROBOT")
    override val look = CabinetLook(body = Pal.SKY, trim = Pal.WHITE, glow = Pal.CYAN, shape = CabinetShape.WIDE)
    override val roundSeconds = 45f

    override fun reset() { /* set up a fresh round */ }
    override fun step(dt: Float) { /* fixed 1/120 s simulation step; call addScore(...) */ }
    override fun render(scope: DrawScope) { /* draw in a 360 x 640 field */ }
    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) { /* field units */ }
    override fun ticketsFor(score: Int) = 1 + score / 20
    override fun drawAttract(p: PixelPainter, w: Int, h: Int, time: Float) { /* tiny cabinet screen */ }
}
```

2. Add it to the list in `games/GameRegistry.kt`:

```kotlin
fun createAll(): List<MiniGame> = listOf(
    ClawMachineGame(), WhackAMoleGame(), SkeeBallGame(), HoopsGame(), CoinPusherGame(),
    AirHockeyGame(),
)
```

That's all. The host takes care of the intro card, countdown, timer, pause/quit, results, ticket printing, token spending and saving. Override `isSettled()` if your round has things still in motion after the clock runs out, and set `endedEarly = true` to finish a round before the clock does.

## Project layout

```
app/src/main/java/com/pocketarcade/
├── MainActivity.kt        single activity: immersive, portrait, audio lifecycle
├── ArcadeApp.kt           title → hall ↔ machine flow and the zoom/fade transitions
├── engine/                fixed-step loop, touch/flick tracking, circle physics, audio synth,
│                          particles, shake/springs, haptics, pixel font, sprite raster, palette
├── hub/                   hall layout, world, player, NPCs, camera, collision, joystick, sprites, renderer
├── games/                 MiniGame interface, BaseMiniGame, GameRegistry
│   ├── claw/  skeeball/  whackamole/  coinpusher/  hoops/
├── data/                  DataStore repository, save state, prize catalog
└── ui/                    HUD, title, prize counter, token machine, profile, game host, widgets
```

Tuning knobs for every game (difficulty and payouts) are grouped at the top of each game file in a `*Tuning` object: `ClawTuning`, `SkeeTuning`, `WhackTuning`, `PusherTuning` and `HoopsTuning`.
