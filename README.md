# Pocket Arcade — Godot edition

A native Godot 4.6.2 arcade for Android. Walk through a procedural 3D hall, spend tokens at eleven playable machines, earn tickets, collect plushies, and buy hats, outfits, and hall decorations.

**[Download the latest APK](https://github.com/gh00ul/pocket-arcade/releases/latest/download/PocketArcade.apk)** · [Release notes](docs/GODOT_RELEASE.md)

The game now runs in Godot with GDScript, native 3D meshes, Godot controls, and synthesized sound. The hall, cameras, UI, and machine presentation have been rebuilt and retuned for portrait touch play. Gameplay rules and progression carry forward; this edition does not reproduce every visual effect or animation from the original Kotlin renderer.

## Play

- **Claw Machine:** position the swinging claw, grab plushies, and fill your collection.
- **Skee-ball:** aim and flick, with scoring rings and a corner bonus.
- **Whack-a-mole:** build combos, find gold moles, and avoid bombs.
- **Coin Pusher:** time the shelf, collect front-edge spills, and trigger bonus items.
- **Hoop Shot:** flick basketballs and build a scoring streak.
- **Air Hockey:** control your paddle against the house.
- **Turbo Racer:** steer through three laps.
- **Stacker:** time each row to build the tower.
- **Shootout:** hit targets and spare civilians.
- **Star Flipper:** launch the pinball and work the flippers.
- **Gone Fishing:** cast, strike, and manage the reel.

Each machine explains its controls before the round starts. The hall supports an overhead view and a first-person view, with a touch joystick, camera controls, and a machine selection menu. Desktop mouse input is supported for development.

## Install or update

Download `PocketArcade.apk` from the [latest release](https://github.com/gh00ul/pocket-arcade/releases/latest), open it on your Android device, and allow installation from that browser or file manager if prompted. Releases also include `SHA256SUMS.txt` to check the download.

The APK keeps the original package ID, `com.pocketarcade`, and the repository's existing signing identity, `app/debug.keystore`. Install it over a previous official Pocket Arcade APK to retain Android app data. On the first Godot launch, the game imports available legacy tokens, tickets, purchases, equipped items, plush collection, high scores, and preferences into `user://pocket_arcade_godot.json`; the original AndroidX Preferences file is left intact. Uninstalling or clearing app data removes local progress.

This is a sideloaded release APK signed with the project's existing shared debug key. The current app version is **2.0.0**, Android version code **20000**. The APK includes ARMv7, ARM64, and x86-64 libraries.

## Develop

Open `godot/project.godot` in the **standard, non-.NET Godot 4.6.2** editor and run the main scene. The project uses the Compatibility renderer and a 540 × 960 portrait viewport.

```text
godot/
  project.godot        Project and platform settings
  export_presets.cfg  Android export and signing configuration
  games/              Eleven native GDScript mini-games
  scripts/            Hall, UI, shared game host, catalog, and save migration
  tests/              Headless game, input, host, and save regression tests
app/                   Original Kotlin app and the existing signing key
docs/KOTLIN_VERSION.md Original edition documentation
```

The original Kotlin sources remain available for reference. The current APK build uses `godot/`, not the Gradle application.

With the Godot executable available as `godot`, run these commands from the repository root:

```sh
godot --headless --path godot --editor --import
godot --headless --path godot res://tests/skill_games_test.tscn
godot --headless --path godot res://tests/sports_tests.tscn
godot --headless --path godot --script res://tests/test_new_games.gd
godot --headless --path godot res://tests/host_save_tests.tscn
godot --headless --path godot res://tests/hall_tests.tscn
```

These tests cover game scoring and settlement, input cancellation, payouts and bonuses, and host/save behavior. Render and inspect the game on a device when changing cameras, layout, or controls; headless tests do not verify visual appearance or device performance.

## Build the APK

Install Godot 4.6.2's matching **standard export templates**, JDK 17, and the Android SDK with Platform Tools, Platform 35, and Build Tools 35.0.1. In Godot's Editor Settings, set `Export > Android > Java SDK Path` and `Android SDK Path`. See the [Godot Android export instructions](https://docs.godotengine.org/en/4.6/tutorials/export/exporting_for_android.html) and [official 4.6.2 downloads](https://github.com/godotengine/godot-builds/releases/tag/4.6.2-stable).

The `Android` preset uses prebuilt APK templates, so no custom Gradle build is required. From the repository root, create `releases/`, then run:

```sh
godot --headless --path godot --export-release Android ../releases/PocketArcade.apk
```

Keep `app/debug.keystore`, its alias, and the package ID unchanged when producing an update. Before a future app release, increment `version/code` and update `version/name` in `godot/export_presets.cfg`.

## GitHub releases

[Build APK](.github/workflows/build.yml) runs on pushes to `main` and manual dispatch. It installs the pinned Godot engine and Android templates, runs the regression suites, exports and verifies the signed APK, and publishes the APK and SHA-256 checksum as both a workflow artifact and a GitHub Release. Release tags use `godot-build-<run number>`; app version metadata comes from the export preset.
