package com.pocketarcade.engine.audio

import com.pocketarcade.engine.audio.Patches.BASS_PLUCK
import com.pocketarcade.engine.audio.Patches.BASS_SQUARE
import com.pocketarcade.engine.audio.Patches.BASS_SUB
import com.pocketarcade.engine.audio.Patches.BASS_SYNTH
import com.pocketarcade.engine.audio.Patches.BELL
import com.pocketarcade.engine.audio.Patches.BOOM
import com.pocketarcade.engine.audio.Patches.CLAP
import com.pocketarcade.engine.audio.Patches.CRASH
import com.pocketarcade.engine.audio.Patches.EP
import com.pocketarcade.engine.audio.Patches.HAT
import com.pocketarcade.engine.audio.Patches.HAT_OPEN
import com.pocketarcade.engine.audio.Patches.KICK
import com.pocketarcade.engine.audio.Patches.KICK_SOFT
import com.pocketarcade.engine.audio.Patches.LEAD_SAW
import com.pocketarcade.engine.audio.Patches.LEAD_SOFT
import com.pocketarcade.engine.audio.Patches.LEAD_SQUARE
import com.pocketarcade.engine.audio.Patches.MUSICBOX
import com.pocketarcade.engine.audio.Patches.PAD_DARK
import com.pocketarcade.engine.audio.Patches.PAD_GLASS
import com.pocketarcade.engine.audio.Patches.PAD_PUMP
import com.pocketarcade.engine.audio.Patches.PAD_SOFT
import com.pocketarcade.engine.audio.Patches.PAD_WARM
import com.pocketarcade.engine.audio.Patches.PLUCK
import com.pocketarcade.engine.audio.Patches.PLUCK_SAW
import com.pocketarcade.engine.audio.Patches.POWER
import com.pocketarcade.engine.audio.Patches.RIM
import com.pocketarcade.engine.audio.Patches.SHAKER
import com.pocketarcade.engine.audio.Patches.SINK
import com.pocketarcade.engine.audio.Patches.SNARE
import com.pocketarcade.engine.audio.Patches.SPARKLE
import com.pocketarcade.engine.audio.Patches.STAB
import com.pocketarcade.engine.audio.Patches.TICK
import com.pocketarcade.engine.audio.Patches.TOM
import com.pocketarcade.engine.audio.Patches.XYLO

/** A stinger: its music, and how hard and how long it ducks the scene under it. */
internal class StingerDef(val track: Track, val duckDepth: Float, val duckHold: Float)

/**
 * Every theme and stinger, written out as [Track] data. Tempos are chosen so a sixteenth is a whole
 * number of samples at 48 kHz (a divisor of 720000 beats per minute: 75, 80, 90, 96, 100, 120, 125,
 * 128, 144, 150...), which makes each loop exactly periodic there. Tracks are built the first time
 * they are asked for.
 */
internal object Tracks {
    private const val KICK_NOTE = 38
    private const val SNARE_NOTE = 55
    private const val NOISE_NOTE = 60

    /** The theme for [scene], or null for silence. */
    fun forScene(scene: MusicScene): Track? = when (scene) {
        MusicScene.Silence -> null
        MusicScene.Title -> TITLE
        MusicScene.Hall -> HALL
        MusicScene.Results -> RESULTS
        is MusicScene.Game -> games[scene.id] ?: FALLBACK_GAME
    }

    fun stinger(which: Stinger): StingerDef = when (which) {
        Stinger.COUNTDOWN -> COUNTDOWN
        Stinger.GO -> GO
        Stinger.TIME_UP -> TIME_UP
        Stinger.RESULTS -> RESULTS_STINGER
        Stinger.HIGH_SCORE -> HIGH_SCORE
    }

    /** Every theme, for tests. */
    val themes: List<Track> get() = listOf(TITLE, HALL, RESULTS) + games.values

    /**
     * Builds every theme and stinger now (the title's and the hall's first). The mixer calls this before
     * it starts playing, so no theme is ever built in the middle of a block.
     */
    fun warmUp() {
        TITLE
        HALL
        RESULTS
        themes
        stingers
    }

    /** Every stinger, for tests. */
    val stingers: List<StingerDef> get() = Stinger.entries.map { stinger(it) }

    private val TITLE: Track by lazy {
        track("title", 100f, 16) {
            chords("Fmaj7*2 G6*2 Em7*2 Am7*2 Dm7*2 G6*2 Cmaj7*2 Am7*2")
            layers(0.2f, 0.5f)
            level(0.86f)
            pad(PAD_WARM, low = 53)
            pad(PAD_GLASS, low = 65, vel = 0.7f)
            bass(BASS_SUB, "r.......r.......", maxLen = 8)
            arp(PLUCK, "0.1.2.3.2.1.2.1.", layer = 1)
            drums(KICK_SOFT, KICK_NOTE, "x.......x.......|x...x...x...x...", layer = 1)
            drums(SHAKER, NOISE_NOTE, "..x...x...x...x.", layer = 1)
            // Bell melody in C major from C5, two bars a chord.
            melody(
                BELL,
                "9:6 7:2 5:4 7:4 9:8 .:8 | 8:6 6:2 4:4 6:4 8:8 .:8 | 9:6 6:2 4:4 6:4 9:8 .:8 | 7:4 9:4 11:8 9:4 7:4 5:8 |" +
                    " 10:6 8:2 7:4 5:4 3:8 .:8 | 11:4 8:4 6:4 8:4 11:8 .:8 | 9:4 11:4 13:8 11:4 9:4 7:8 | 9:6 7:2 5:6 4:2 5:16",
                "C5", Scales.MAJOR, layer = 2,
            )
        }
    }

    private val HALL: Track by lazy {
        track("hall", 96f, 16) {
            chords("Am7*2 Fmaj7*2 Cmaj7*2 G6*2 Am7*2 Dm9*2 Fmaj7*2 E7sus4 E7")
            layers(0.3f, 0.65f)
            level(0.9f)
            pad(PAD_WARM, low = 52)
            pad(PAD_GLASS, low = 64, vel = 0.55f)
            bass(BASS_SUB, "r.....r.r.......|r.....r...r.....", maxLen = 6)
            // Activity: a plucked arpeggio and a lazy beat.
            arp(PLUCK, "0.1.2.1.0.1.2.3.|0.2.1.3.2.1.0.1.", layer = 1)
            drums(KICK_SOFT, KICK_NOTE, "x.....x...x.....", layer = 1)
            drums(RIM, SNARE_NOTE, "....x.......x...", layer = 1, vel = 0.7f)
            drums(HAT, NOISE_NOTE, "x.x.x.x.x.x.x.x.", layer = 1, vel = 0.6f)
            // Lead: A minor pentatonic from A4, two bars a chord, plenty of air.
            melody(
                LEAD_SOFT,
                "5:6 6:2 5:4 3:4 .:16 | 6:6 5:2 3:4 2:4 .:16 | 4:4 6:4 8:8 6:4 5:4 .:8 | 7:6 5:2 3:8 .:16 |" +
                    " 5:4 6:4 8:8 6:4 5:4 .:8 | 7:6 6:2 5:4 3:4 2:16 | 6:4 8:4 9:8 8:4 6:4 .:8 | 8:8 7:4 5:4 3:16",
                "A4", Scales.PENTATONIC_MINOR, layer = 2,
            )
        }
    }

    private val RESULTS: Track by lazy {
        track("results", 90f, 8) {
            chords("Cmaj9*2 Am7*2 Fmaj7*2 G6 Csus2")
            layers(0.2f, 0.6f)
            level(0.78f)
            pad(PAD_SOFT, low = 52)
            pad(PAD_GLASS, low = 64, vel = 0.6f)
            bass(BASS_SUB, "r.......r.......", maxLen = 8)
            arp(MUSICBOX, "0.1.2.3.4.3.2.1.", layer = 1, octave = 5)
            drums(KICK_SOFT, KICK_NOTE, "x.......x.......", layer = 1, vel = 0.7f)
            drums(SHAKER, NOISE_NOTE, "..x...x...x...x.", layer = 1)
            melody(
                BELL,
                "4:4 6:4 8:8 | 7:4 6:4 4:8 | 5:4 7:4 9:8 | 8:4 6:4 4:4 3:4 | 4:4 6:4 8:8 | 7:4 6:4 4:8 | 5:6 7:2 9:8 | 8:8 7:8",
                "C5", Scales.MAJOR, layer = 2,
            )
        }
    }

    // ------------------------------------------------------------------------------------ machines

    private val games: Map<String, Track> by lazy {
        mapOf(
            "claw" to CLAW,
            "skeeball" to SKEEBALL,
            "hoops" to HOOPS,
            "whack" to WHACK,
            "pusher" to PUSHER,
            "airhockey" to AIRHOCKEY,
            "racer" to RACER,
            "stacker" to STACKER,
            "shooter" to SHOOTER,
            "pinball" to PINBALL,
            "fishing" to FISHING,
        )
    }

    /** What an unknown machine plays: the stacker's neighbour, plain and upbeat enough for anything. */
    private val FALLBACK_GAME: Track get() = games.getValue("stacker")

    /** Claw machine: a music-box lullaby with a wobble of carnival, G major. */
    private val CLAW: Track by lazy {
        track("claw", 100f, 8) {
            chords("G Em C D")
            layers(0.25f, 0.6f)
            level(1.4f)
            pad(PAD_SOFT, low = 55, vel = 0.6f)
            bass(BASS_PLUCK, "r.....r.r.......|r.....5.r.......", maxLen = 3)
            arp(MUSICBOX, "0.1.2.1.0.1.2.3.", layer = 0, octave = 4, vel = 0.7f)
            melody(
                MUSICBOX,
                "2:4 4:2 2:2 0:4 .:4 | 2:4 5:2 4:2 2:4 .:4 | 3:4 5:2 3:2 4:4 .:4 | 4:4 6:2 4:2 1:4 .:4 |" +
                    " 4:4 2:2 4:2 7:4 .:4 | 5:4 2:2 5:2 4:4 .:4 | 7:4 5:2 3:2 5:4 .:4 | 6:4 4:2 1:2 0:4 .:4",
                "G5", Scales.MAJOR, layer = 1,
            )
            drums(SHAKER, NOISE_NOTE, "..x...x...x...x.", layer = 1)
            drums(KICK_SOFT, KICK_NOTE, "x.......x.......", layer = 2)
            drums(RIM, SNARE_NOTE, "....o.......o.o.", layer = 2, vel = 0.6f)
            arp(BELL, "..2...1...3...2.", layer = 2, octave = 5, vel = 0.6f)
        }
    }

    /** Turbo racer: synthwave, A minor, pumping. */
    private val RACER: Track by lazy {
        track("racer", 120f, 8) {
            chords("Am F C G")
            layers(0.3f, 0.65f)
            level(1.12f)
            pad(PAD_PUMP, low = 55)
            bass(BASS_SYNTH, "r.r.r.r.r.r.r.R.", maxLen = 1, pump = false)
            drums(KICK, KICK_NOTE, "x...x...x...x...", pump = true)
            drums(CLAP, NOISE_NOTE, "....x.......x...")
            drums(HAT_OPEN, NOISE_NOTE, "..x...x...x...x.", vel = 0.7f)
            arp(PLUCK_SAW, "0123210101232101", layer = 1, octave = 4)
            drums(HAT, NOISE_NOTE, "x.x.x.x.x.x.x.x.", layer = 1, vel = 0.55f)
            melody(
                LEAD_SAW,
                "4:4 2:2 4:2 7:4 4:4 | 5:4 4:2 5:2 7:4 9:4 | 7:4 6:2 4:2 2:4 4:4 | 6:4 4:2 6:2 8:4 6:4 |" +
                    " 7:4 9:2 7:2 4:4 2:4 | 9:4 7:2 5:2 7:4 5:4 | 9:2 11:2 9:2 7:2 6:4 4:4 | 6:4 8:4 9:8",
                "A4", Scales.MINOR, layer = 2,
            )
        }
    }

    /** Stacker: tense and minimal, E phrygian. */
    private val STACKER: Track by lazy {
        track("stacker", 100f, 8) {
            chords("Em Em F Em Em Em Bb Em")
            layers(0.3f, 0.65f)
            level(1.1f)
            pad(PAD_DARK, low = 40)
            bass(BASS_SQUARE, "r.r.r.r.r.r.r.r.", maxLen = 1)
            drums(TICK, 81, "x.x.x.x.x.x.x.x.", vel = 0.35f)
            drums(KICK_SOFT, KICK_NOTE, "x.....x.........", layer = 1, vel = 0.8f)
            arp(MUSICBOX, "..0...1...0...2.", layer = 1, octave = 5, vel = 0.5f)
            drums(SNARE, SNARE_NOTE, "....o...o..o.ooo", layer = 2, vel = 0.5f)
            drums(HAT, NOISE_NOTE, "xxxxxxxxxxxxxxxx", layer = 2, vel = 0.4f)
            arp(PLUCK, "0.1.2.1.0.1.2.1.", layer = 2, octave = 5, vel = 0.6f)
        }
    }

    /** Skee-ball: upbeat boardwalk pop, C major. */
    private val SKEEBALL: Track by lazy {
        track("skeeball", 125f, 8) {
            chords("C F C G Am F G C")
            layers(0.3f, 0.65f)
            level(1.2f)
            bass(BASS_SYNTH, "r..r..r.r..r..5.", maxLen = 2)
            drums(KICK, KICK_NOTE, "x...x...x...x...", pump = true)
            drums(CLAP, NOISE_NOTE, "....x.......x...", fill = "....x...x.x.xxxx", fillEvery = 4)
            drums(HAT, NOISE_NOTE, "x.x.x.x.x.x.x.x.", vel = 0.5f)
            stab(STAB, "..x...x...x...x.", low = 60, len = 1, vel = 0.7f)
            melody(
                LEAD_SQUARE,
                "4:2 4:2 5:2 4:2 2:4 .:4 | 5:2 5:2 7:2 5:2 3:4 .:4 | 4:2 4:2 5:2 4:2 7:4 .:4 | 6:2 6:2 8:2 6:2 4:4 .:4 |" +
                    " 7:4 5:4 2:4 .:4 | 5:4 7:4 9:4 .:4 | 8:2 6:2 4:2 6:2 8:4 .:4 | 7:8 4:4 2:4",
                "C5", Scales.MAJOR, layer = 1,
            )
            arp(PLUCK, "0.1.2.1.0.1.2.1.", layer = 2, octave = 5, vel = 0.6f)
            drums(HAT_OPEN, NOISE_NOTE, "..x...x...x...x.", layer = 2, vel = 0.7f)
        }
    }

    /** Hoop shot: funky pop, F major. */
    private val HOOPS: Track by lazy {
        track("hoops", 120f, 8) {
            chords("F Bb Gm7 C7 F Bb C7 F")
            layers(0.3f, 0.65f)
            level(1.12f)
            bass(BASS_SYNTH, "r..r..R.r..5..r.", maxLen = 2)
            drums(KICK, KICK_NOTE, "x..x..x.x.......", pump = true)
            drums(CLAP, NOISE_NOTE, "....x.......x...")
            drums(HAT, NOISE_NOTE, "x.xox.xox.xox.xo", vel = 0.55f)
            stab(EP, "..x...x...x..x..", low = 58, len = 2, vel = 0.75f)
            melody(
                LEAD_SQUARE,
                "2:2 4:2 2:2 0:2 2:4 .:4 | 3:2 5:2 3:2 0:2 3:4 .:4 | 1:2 3:2 5:2 3:2 1:4 .:4 | 4:2 6:2 4:2 2:2 4:4 .:4 |" +
                    " 2:2 4:2 7:2 4:2 2:4 .:4 | 3:2 5:2 7:2 5:2 3:4 .:4 | 4:2 6:2 4:2 3:2 2:2 4:2 .:4 | 7:4 4:4 2:4 0:4",
                "F5", Scales.MAJOR, layer = 1,
            )
            arp(PLUCK, "0.1.2.1.0.1.2.1.", layer = 2, octave = 5, vel = 0.6f)
            drums(SHAKER, NOISE_NOTE, "xxxxxxxxxxxxxxxx", layer = 2, vel = 0.45f)
        }
    }

    /** Whack-a-mole: playful, bouncy, staccato, D major. */
    private val WHACK: Track by lazy {
        track("whack", 144f, 8) {
            chords("D G D A Bm G A D")
            layers(0.3f, 0.65f)
            level(1.45f)
            bass(BASS_PLUCK, "r.R.r.R.r.R.r.R.", maxLen = 1)
            drums(KICK_SOFT, KICK_NOTE, "x.......x.......")
            drums(RIM, SNARE_NOTE, "....x.......x...")
            drums(HAT, NOISE_NOTE, "x.x.x.x.x.x.x.x.", vel = 0.45f)
            stab(XYLO, "x.....x.x.....x.", low = 62, len = 1, vel = 0.6f)
            melody(
                XYLO,
                "4:1 2:1 0:2 2:2 4:2 2:2 0:2 .:4 | 3:1 5:1 7:2 5:2 3:2 5:2 3:2 .:4 | 4:1 2:1 0:2 2:2 4:2 2:2 0:2 .:4 |" +
                    " 4:1 6:1 8:2 6:2 4:2 6:2 4:2 .:4 | 5:2 7:2 9:2 7:2 5:4 .:4 | 3:2 5:2 7:2 5:2 3:4 .:4 |" +
                    " 4:2 6:2 8:2 6:2 4:2 6:2 4:2 .:2 | 7:4 4:4 2:4 0:4",
                "D5", Scales.MAJOR, layer = 1,
            )
            arp(BELL, "..0...1...2...1.", layer = 2, octave = 5, vel = 0.6f)
            drums(CLAP, NOISE_NOTE, "....x.......x...", layer = 2, vel = 0.6f)
            drums(SHAKER, NOISE_NOTE, "..x...x...x...x.", layer = 2)
        }
    }

    /** Coin pusher: a casino shuffle with a walking bass, C major. */
    private val PUSHER: Track by lazy {
        track("pusher", 100f, 8, swing = 0.25f) {
            chords("Cmaj7 A7 Dm7 G7 Em7 A7 Dm7 G7")
            layers(0.3f, 0.65f)
            level(1.6f)
            bass(BASS_PLUCK, "r...3...5...3...", maxLen = 4)
            drums(KICK_SOFT, KICK_NOTE, "x.......x.......")
            drums(SNARE, SNARE_NOTE, "....o.......o...", vel = 0.5f)
            drums(HAT, NOISE_NOTE, "xoxoxoxoxoxoxoxo", vel = 0.4f)
            stab(EP, "x.....x...x.....|..x.....x.....x.", low = 58, len = 3, vel = 0.7f)
            arp(BELL, "0.1.2.3.2.1.0.1.", layer = 1, octave = 5, vel = 0.55f)
            melody(
                LEAD_SQUARE,
                "2:3 4:1 6:4 4:4 .:4 | 5:3 4:1 2:4 4:4 .:4 | 3:3 5:1 7:4 5:4 .:4 | 4:3 6:1 8:4 3:4 .:4 |" +
                    " 2:3 4:1 6:4 4:4 .:4 | 5:3 4:1 2:4 4:4 .:4 | 5:3 7:1 9:4 7:4 .:4 | 8:4 6:4 4:4 2:4",
                "C5", Scales.MAJOR, layer = 2, vel = 0.75f,
            )
        }
    }

    /** Air hockey: driving four-on-the-floor, A minor. */
    private val AIRHOCKEY: Track by lazy {
        track("airhockey", 144f, 8) {
            chords("Am F C G Am F G Am")
            layers(0.3f, 0.65f)
            level(1.07f)
            pad(PAD_PUMP, low = 55)
            bass(BASS_SYNTH, "rr.rr.rrr.rr.rr.", maxLen = 1)
            drums(KICK, KICK_NOTE, "x...x...x...x...", pump = true)
            drums(CLAP, NOISE_NOTE, "....x.......x...", fill = "....x.......xxxx", fillEvery = 4)
            drums(HAT_OPEN, NOISE_NOTE, "..x...x...x...x.", vel = 0.7f)
            arp(PLUCK_SAW, "0120120120120120", layer = 1, octave = 4)
            drums(HAT, NOISE_NOTE, "x.x.x.x.x.x.x.x.", layer = 1, vel = 0.5f)
            melody(
                LEAD_SAW,
                "7:2 7:2 7:4 4:2 5:2 7:4 | 5:2 5:2 5:4 3:2 4:2 5:4 | 7:2 7:2 9:4 7:2 6:2 4:4 | 6:2 6:2 6:4 4:2 6:2 8:4 |" +
                    " 7:2 9:2 11:4 9:2 7:2 4:4 | 10:2 9:2 7:4 5:4 3:4 | 8:4 6:4 4:4 6:4 | 7:8 4:4 2:4",
                "A4", Scales.MINOR, layer = 2,
            )
        }
    }

    /** Shootout: tense and martial, D minor. */
    private val SHOOTER: Track by lazy {
        track("shooter", 128f, 8) {
            chords("Dm Dm Bb A Dm Dm Bb A")
            layers(0.3f, 0.65f)
            level(0.9f)
            pad(PAD_DARK, low = 38)
            bass(BASS_SQUARE, "rr.rr.r.rr.rr.r.", maxLen = 1)
            drums(KICK, KICK_NOTE, "x.....x.x.......")
            drums(SNARE, SNARE_NOTE, "....x.......x...", fill = "....x...x.x.xxxx", fillEvery = 4)
            drums(TICK, 81, "x.x.x.x.x.x.x.x.", vel = 0.35f)
            stab(STAB, "x.....x.x.....x.", layer = 1, low = 55, len = 1, vel = 0.65f)
            drums(HAT, NOISE_NOTE, "xxxxxxxxxxxxxxxx", layer = 1, vel = 0.4f)
            melody(
                LEAD_SAW,
                "0:2 .:2 0:2 2:2 4:4 2:2 .:2 | 0:2 .:2 0:2 2:2 4:2 5:2 4:4 | 5:4 4:2 2:2 0:4 .:4 | 4:4 1:2 4:2 1:4 .:4 |" +
                    " 0:2 .:2 0:2 2:2 4:4 2:2 .:2 | 0:2 .:2 0:2 2:2 4:2 5:2 7:4 | 5:4 7:2 4:2 2:4 .:4 | 4:2 1:2 4:2 1:2 4:8",
                "D5", Scales.MINOR, layer = 2, vel = 0.8f,
            )
        }
    }

    /** Star Flipper: funky, a little swing, A dorian. */
    private val PINBALL: Track by lazy {
        track("pinball", 100f, 8, swing = 0.15f) {
            chords("Am7 D7 Am7 D7 Gmaj7 Cmaj7 Am7 D7")
            layers(0.3f, 0.65f)
            level(1.25f)
            bass(BASS_PLUCK, "r..r..R.r..r.5..", maxLen = 2)
            drums(KICK_SOFT, KICK_NOTE, "x.....x..x......")
            drums(CLAP, NOISE_NOTE, "....x.......x...")
            drums(HAT, NOISE_NOTE, "x.xxx.xxx.xxx.xx", vel = 0.45f)
            stab(EP, "x..x..x...x..x..", low = 58, len = 2, vel = 0.75f)
            melody(
                LEAD_SQUARE,
                "4:2 2:2 0:2 2:2 4:4 .:4 | 5:2 3:2 5:2 7:2 5:4 .:4 | 7:2 4:2 2:2 4:2 7:4 .:4 | 7:2 5:2 3:2 5:2 3:4 .:4 |" +
                    " 6:2 8:2 10:2 8:2 6:4 .:4 | 9:2 11:2 8:2 6:2 4:4 .:4 | 7:2 9:2 11:2 9:2 7:4 .:4 | 10:2 8:2 7:2 5:2 3:8",
                "A4", Scales.DORIAN, layer = 1,
            )
            arp(BELL, "..0...1...2...1.", layer = 2, octave = 5, vel = 0.6f)
            drums(HAT_OPEN, NOISE_NOTE, "..x...x...x...x.", layer = 2, vel = 0.6f)
        }
    }

    /** Gone Fishing: calm, spacious, G major. */
    private val FISHING: Track by lazy {
        track("fishing", 75f, 8) {
            chords("Gmaj7 Cmaj7 Em7 D6 Gmaj7 Cmaj7 Am7 D7sus4")
            layers(0.3f, 0.65f)
            level(0.67f)
            pad(PAD_SOFT, low = 52)
            pad(PAD_GLASS, low = 64, vel = 0.6f)
            bass(BASS_SUB, "r.......5.......", maxLen = 8)
            drums(SHAKER, NOISE_NOTE, "x...x...x...x...", vel = 0.35f)
            arp(BELL, "0.1.2.3.2.1.0.1.", layer = 1, octave = 4, vel = 0.55f)
            melody(
                LEAD_SOFT,
                "4:6 2:2 4:4 6:4 | 5:6 4:2 2:4 .:4 | 2:4 4:4 5:8 | 6:6 4:2 2:8 | 7:6 6:2 4:4 2:4 | 5:4 7:4 9:8 |" +
                    " 8:4 7:4 5:4 4:4 | 4:8 3:4 2:4",
                "G4", Scales.MAJOR, layer = 2,
            )
        }
    }

    // ------------------------------------------------------------------------------------ stingers

    private val COUNTDOWN: StingerDef by lazy {
        StingerDef(
            track("count", 120f, 1) {
                once()
                notes(TOM, 0, Triple(0, "A2", 3), vel = 0.8f)
                notes(TICK, 0, Triple(0, "A6", 1), vel = 0.9f)
            },
            duckDepth = 0.15f, duckHold = 0.25f,
        )
    }

    private val GO: StingerDef by lazy {
        StingerDef(
            track("go", 125f, 2) {
                once()
                notes(BOOM, 0, Triple(0, "A1", 6))
                notes(POWER, 0, Triple(0, "A3", 8), Triple(0, "E4", 8), Triple(0, "A4", 8), vel = 0.9f)
                notes(CRASH, 0, Triple(0, "C4", 16), vel = 0.8f)
                notes(
                    SPARKLE, 0,
                    Triple(2, "E5", 2), Triple(3, "A5", 2), Triple(4, "C#6", 2), Triple(5, "E6", 2), Triple(6, "A6", 4),
                    vel = 0.8f,
                )
            },
            duckDepth = 0.35f, duckHold = 0.7f,
        )
    }

    private val TIME_UP: StingerDef by lazy {
        StingerDef(
            track("timeup", 100f, 2) {
                once()
                notes(SINK, 0, Triple(0, "A3", 3), Triple(3, "E3", 3), Triple(6, "C3", 3), Triple(9, "A2", 8), vel = 0.9f)
                notes(BOOM, 0, Triple(9, "A1", 6), vel = 0.7f)
            },
            duckDepth = 0.4f, duckHold = 0.7f,
        )
    }

    private val RESULTS_STINGER: StingerDef by lazy {
        StingerDef(
            track("results-stinger", 96f, 2) {
                once()
                chords("Fmaj7 Cmaj9")
                pad(PAD_SOFT, low = 52)
                arp(MUSICBOX, "0.1.2.3.4.3.2.1.", octave = 5)
                arp(SPARKLE, "..............4.", octave = 5, len = 4)
            },
            duckDepth = 0.25f, duckHold = 0.9f,
        )
    }

    private val HIGH_SCORE: StingerDef by lazy {
        StingerDef(
            track("highscore", 125f, 4) {
                once()
                chords("C F G C")
                pad(PAD_WARM, low = 55)
                stab(STAB, "x.....x.x.......|x.....x.x.......|x...x...x...x...|X...............", low = 60, len = 3)
                drums(KICK, KICK_NOTE, "x...x...x...x...|x...x...x...x...|x...x...x...x...|x...............")
                drums(CLAP, NOISE_NOTE, "....x.......x...|....x.......x...|....x...x.x.x.x.|X...............")
                arp(SPARKLE, "0.1.2.3.4.5.6.7.|0.1.2.3.4.5.6.7.|0.1.2.3.4.5.6.7.|4...............", octave = 5, len = 2)
                notes(CRASH, 0, Triple(0, "C4", 16), Triple(48, "C4", 16), vel = 0.8f)
            },
            duckDepth = 0.5f, duckHold = 2.6f,
        )
    }
}
