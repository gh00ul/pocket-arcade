# Pocket Arcade 2.0.0 — Godot edition

Pocket Arcade now runs natively in **Godot 4.6.2** with GDScript.

- Eleven playable games: Claw Machine, Skee-ball, Whack-a-mole, Coin Pusher, Hoop Shot, Air Hockey, Turbo Racer, Stacker, Shootout, Star Flipper, and Gone Fishing.
- A rebuilt procedural 3D hall with overhead and first-person views, touch movement, and machine selection.
- Portrait controls, game instructions, countdowns, pause/resume, results, synthesized sound, and persistent progression.
- Tokens, ticket payouts, plush collection, high scores, prize purchases, outfits, hats, and hall decorations.
- Import of available original Android progress when upgrading an existing installation.

The hall, UI, cameras, and machine visuals have been redesigned for Godot. Rules and progression carry forward, with retuned presentation and controls; visual effects and animations are not identical to the original Kotlin renderer.

## Install

Download **PocketArcade.apk** from this release and open it on your Android device. If prompted, allow installation from your browser or file manager. `SHA256SUMS.txt` contains the APK's SHA-256 checksum.

The APK uses package **com.pocketarcade** and the project's existing **app/debug.keystore** signing identity. Install it over an earlier official APK to retain app data. On the first Godot launch, available legacy tokens, tickets, purchases, equipped items, plush collection, high scores, and preferences are imported; the original preferences file is retained. Do not uninstall or clear app data if you want to keep that progress.

This release uses the Godot release export template, signed with the existing shared debug key for update compatibility. It contains ARMv7, ARM64, and x86-64 libraries. App version: **2.0.0**; Android version code: **20000**.

The GitHub build runs all five regression suites, verifies the APK signature, and generates the checksum before publication. The Godot source is in `godot/`; the original Kotlin source remains in `app/`.
