package com.pocketarcade.engine.audio

import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.range
import kotlin.random.Random

/** What the mixer offers the ambience: start a one-shot voice, and say how many ambience ones are sounding. */
internal interface VoiceSink {
    fun startVoice(sfx: Sfx, gainL: Float, gainR: Float, send: Float, pitch: Float, priority: Int)

    /** How many lowest-priority (ambience) voices are sounding now. */
    fun ambientVoices(): Int
}

/** Voice priorities: when the mixer is full, lower ones are stolen first. */
internal object Priority {
    /** Machine bleeps and café clinks: the first to go. */
    const val AMBIENT = 0

    /** Footsteps and other small things. */
    const val MINOR = 1

    /** Everything a game plays. */
    const val NORMAL = 2

    /** Jingles and fanfares: never stolen for a lesser sound. */
    const val KEY = 3
}

/**
 * What each kind of machine sounds like when it bleeps to itself in attract mode, so the hall is
 * a mix of ping-pong clacks, coin rattles and light-gun pews rather than one repeated blip.
 */
internal object Attract {
    /** A machine's few attract sounds, the pitch range they're replayed at and how loud they are. */
    class Palette(val sfx: Array<Sfx>, val pitchLo: Float, val pitchHi: Float, val level: Float)

    /** The kind for a machine with no palette of its own. */
    const val GENERIC = 0

    private val PALETTES = arrayOf(
        Palette(arrayOf(Sfx.BLIP, Sfx.COIN, Sfx.POP, Sfx.SELECT, Sfx.CLINK), 0.5f, 1.6f, 1f),
        // Claw: music-box plinks and a motor whirr.
        Palette(arrayOf(Sfx.CLINK, Sfx.CLINK, Sfx.POP, Sfx.CLAW_MOTOR), 0.7f, 1.5f, 0.9f),
        // Whack-a-mole: bonks and boings.
        Palette(arrayOf(Sfx.BONK, Sfx.POP, Sfx.BONK), 0.8f, 1.5f, 0.9f),
        // Skee-ball: a ball rolling and a bell.
        Palette(arrayOf(Sfx.RIM, Sfx.THUD, Sfx.ROLL), 0.8f, 1.3f, 0.8f),
        // Hoops: bounces and swishes.
        Palette(arrayOf(Sfx.BOUNCE, Sfx.SWISH, Sfx.BOUNCE), 0.9f, 1.4f, 0.85f),
        // Coin pusher: coins and a rattle.
        Palette(arrayOf(Sfx.COIN, Sfx.CLINK, Sfx.SPILL), 0.7f, 1.3f, 1f),
        // Air hockey: puck clacks.
        Palette(arrayOf(Sfx.BOUNCE, Sfx.POP, Sfx.RIM), 1.4f, 2.2f, 0.7f),
        // Racer: an engine rev and the odd horn.
        Palette(arrayOf(Sfx.ENGINE, Sfx.ENGINE, Sfx.HORN), 1.2f, 2.4f, 0.7f),
        // Stacker: bare synth blips.
        Palette(arrayOf(Sfx.BLIP, Sfx.SELECT, Sfx.BLIP), 0.6f, 1.2f, 0.9f),
        // Shootout: pews and ricochets.
        Palette(arrayOf(Sfx.ENEMY_FIRE, Sfx.RICOCHET, Sfx.DRY_FIRE), 0.9f, 1.4f, 0.7f),
        // Pinball: bumpers and the spinner.
        Palette(arrayOf(Sfx.BUMPER, Sfx.SPINNER, Sfx.SLINGSHOT), 0.9f, 1.4f, 0.8f),
        // Fishing: splashes and the reel.
        Palette(arrayOf(Sfx.SPLASH, Sfx.REEL, Sfx.BITE), 0.9f, 1.4f, 0.7f),
    )

    /** The palette index for the machine with game id [gameId]. */
    fun kindFor(gameId: String): Int = when (gameId) {
        "claw" -> 1
        "whack" -> 2
        "skeeball" -> 3
        "hoops" -> 4
        "pusher" -> 5
        "airhockey" -> 6
        "racer" -> 7
        "stacker" -> 8
        "shooter" -> 9
        "pinball" -> 10
        "fishing" -> 11
        else -> GENERIC
    }

    fun palette(kind: Int): Palette = PALETTES[if (kind in PALETTES.indices) kind else GENERIC]
}

/**
 * The arcade's background sound, in stereo: a mains hum, a crowd murmur that swells with how busy
 * the hall is, machine bleeps that come from the cabinets themselves and the café's steam wand
 * and clinking cups. The hall tells it where things are ([setSources], [setCafe], [crowd]); the
 * mixer calls [render] once a block. Everything it plays is allocation-free.
 */
internal class HallAmbience(private val sampleRate: Int) {
    private companion object {
        /** How fast the overall loudness eases to its target, per block (about half a second). */
        const val LEVEL_EASE = 0.02f

        /** ...and the crowd's busyness (a slower drift, so a passing kid doesn't pump it). */
        const val CROWD_EASE = 0.03f

        /** Mains hum level: three partials of 55 Hz, low under everything. */
        const val HUM_LEVEL = 0.05f

        /** Crowd murmur level at the quietest and busiest end of the hall's range. */
        const val MURMUR_QUIET = 0.4f
        const val MURMUR_BUSY = 0.9f
        const val MURMUR_LEVEL = 0.45f

        /** Machine bleeps come this often (seconds); only nearby cabinets are audible, so it is often. */
        const val BLEEP_MIN = 0.25f
        const val BLEEP_MAX = 0.9f

        /** Bleep loudness at a source close by, before the ambience volume. */
        const val BLEEP_LEVEL_MIN = 0.05f
        const val BLEEP_LEVEL_MAX = 0.11f

        /** No more than this many ambience voices at once, so a busy hall stays a bed and not a din. */
        const val MAX_AMBIENT_VOICES = 5

        /** How many times a bleep tries for a cabinet within earshot before giving up this round. */
        const val PICK_TRIES = 4

        /** Reverb send of a bleep: machines are a room away. */
        const val BLEEP_SEND = 0.5f

        /** Without positions (the title, before the hall reports in) a bleep comes from a random spot this wide and this far. */
        const val FALLBACK_PAN = 0.8f
        const val FALLBACK_LEVEL_MIN = 0.35f
        const val FALLBACK_LEVEL_MAX = 0.8f

        /** The café: a steam wand hiss every few seconds and a cup clink now and then. */
        const val STEAM_MIN = 8f
        const val STEAM_MAX = 17f
        const val CLINK_MIN = 2.4f
        const val CLINK_MAX = 6.5f
        const val STEAM_LEVEL = 0.16f
        const val CLINK_LEVEL = 0.1f
    }

    /** Cabinet positions and their bleep palettes (see [Attract]); immutable once published. */
    private class Sources(val x: FloatArray, val z: FloatArray, val kind: IntArray, val count: Int)

    @Volatile private var sources = Sources(FloatArray(0), FloatArray(0), IntArray(0), 0)

    @Volatile private var cafeAt = false
    @Volatile private var cafeX = 0f
    @Volatile private var cafeZ = 0f

    /** Loudness target (0 = silent); the hall sets it per screen. */
    @Volatile var target = 0f

    /** How busy the hall is around the listener, 0..1: more crowd murmur. */
    @Volatile var crowd = 0.5f

    /** Publishes the machines' positions: [count] cabinets at ([xs], [zs]) with palette [kinds]. Copies the arrays. */
    fun setSources(xs: FloatArray, zs: FloatArray, kinds: IntArray, count: Int) {
        val n = minOf(count, xs.size, zs.size, kinds.size).coerceAtLeast(0)
        sources = Sources(xs.copyOf(n), zs.copyOf(n), kinds.copyOf(n), n)
    }

    /** Publishes where the café's counter is. */
    fun setCafe(x: Float, z: Float) {
        cafeX = x
        cafeZ = z
        cafeAt = true
    }

    private val rng = Random(99)
    private val placement = Placement()
    private val sr = sampleRate.toFloat()

    private var ambient = 0f
    private var crowdNow = 0.5f
    private var p1 = 0f
    private var p2 = 0f
    private var p3 = 0f
    private var lfo = 0f
    private var bleepTimer = 1f
    private var steamTimer = 4f
    private var clinkTimer = 2f

    /** One ear's crowd murmur: a low rumble plus a voice-band babble that rises and falls like speech. */
    private class Babble(seed: Int) {
        val rng = Random(seed)
        var brown = 0f
        var rumble = 0f
        var hi = 0f
        var lo = 0f
        var mod = 0.6f
        var modTarget = 0.6f
        var modLeft = 0
    }

    private val earL = Babble(1234)
    private val earR = Babble(5678)

    /**
     * Adds one block of ambience to [mixL]/[mixR] and starts any bleeps that fall due through
     * [sink]. [volume] is the player's ambience setting, ([lx], [lz], [yaw]) the listener.
     */
    fun render(
        mixL: FloatArray, mixR: FloatArray, n: Int, sink: VoiceSink,
        volume: Float, lx: Float, lz: Float, yaw: Float,
    ) {
        ambient += (target - ambient) * LEVEL_EASE
        crowdNow += (crowd - crowdNow) * CROWD_EASE
        if (volume <= 0f || ambient < 0.001f) return

        val seconds = n / sr
        bleepTimer -= seconds
        if (bleepTimer <= 0f) {
            bleepTimer = rng.range(BLEEP_MIN, BLEEP_MAX)
            if (ambient > 0.05f) bleep(sink, volume, lx, lz, yaw)
        }
        steamTimer -= seconds
        if (steamTimer <= 0f) {
            steamTimer = rng.range(STEAM_MIN, STEAM_MAX)
            cafeSound(sink, Sfx.STEAM, STEAM_LEVEL, volume, lx, lz, yaw, rng.range(0.9f, 1.1f))
        }
        clinkTimer -= seconds
        if (clinkTimer <= 0f) {
            clinkTimer = rng.range(CLINK_MIN, CLINK_MAX)
            cafeSound(sink, Sfx.CLINK, CLINK_LEVEL, volume, lx, lz, yaw, rng.range(0.8f, 1.25f))
        }

        val humVol = ambient * volume * HUM_LEVEL
        val crowdVol = ambient * volume * MURMUR_LEVEL * (MURMUR_QUIET + (MURMUR_BUSY - MURMUR_QUIET) * crowdNow)
        for (i in 0 until n) {
            p1 += 55f / sr
            if (p1 >= 1f) p1 -= 1f
            p2 += 110f / sr
            if (p2 >= 1f) p2 -= 1f
            p3 += 165.3f / sr
            if (p3 >= 1f) p3 -= 1f
            lfo += 0.11f / sr
            if (lfo >= 1f) lfo -= 1f
            val hum = Dsp.sine(p1) * 0.7f + Dsp.sine(p2) * 0.4f + Dsp.sine(p3) * 0.15f
            val swell = 0.65f + 0.35f * Dsp.sine(lfo)
            val h = hum * humVol
            val g = crowdVol * swell
            mixL[i] += h + babble(earL) * g
            mixR[i] += h + babble(earR) * g
        }
    }

    private fun babble(e: Babble): Float {
        val w = e.rng.nextFloat() - 0.5f
        e.brown = (e.brown + w * 0.05f) * 0.996f
        e.rumble += (e.brown - e.rumble) * 0.08f
        e.hi += (w - e.hi) * 0.16f
        e.lo += (w - e.lo) * 0.035f
        if (--e.modLeft <= 0) {
            // A new "syllable" every 90 to 250 ms.
            e.modLeft = (sr * e.rng.range(0.09f, 0.25f)).toInt()
            e.modTarget = e.rng.range(0.3f, 1f)
        }
        e.mod += (e.modTarget - e.mod) * 0.0006f
        return e.rumble * 0.55f + (e.hi - e.lo) * 1.5f * e.mod
    }

    /** One machine bleep, from a cabinet within earshot (or from nowhere in particular before the hall reports). */
    private fun bleep(sink: VoiceSink, volume: Float, lx: Float, lz: Float, yaw: Float) {
        if (sink.ambientVoices() >= MAX_AMBIENT_VOICES) return
        val src = sources
        val level = rng.range(BLEEP_LEVEL_MIN, BLEEP_LEVEL_MAX) * ambient * volume
        val p = placement
        val kind: Int
        if (src.count == 0) {
            Spatial.panGains(rng.range(-FALLBACK_PAN, FALLBACK_PAN), p)
            val k = rng.range(FALLBACK_LEVEL_MIN, FALLBACK_LEVEL_MAX) * Spatial.CENTRE_MAKEUP
            p.left *= k
            p.right *= k
            p.send = 0.6f
            kind = Attract.GENERIC
        } else {
            var picked = -1
            for (t in 0 until PICK_TRIES) {
                val i = rng.nextInt(src.count)
                Spatial.place(lx, lz, yaw, src.x[i], src.z[i], p)
                if (!p.silent) {
                    picked = i
                    break
                }
            }
            if (picked < 0) return
            kind = src.kind[picked]
        }
        val pal = Attract.palette(kind)
        val sfx = pal.sfx[rng.nextInt(pal.sfx.size)]
        val v = level * pal.level
        sink.startVoice(sfx, p.left * v, p.right * v, BLEEP_SEND * p.send, rng.range(pal.pitchLo, pal.pitchHi), Priority.AMBIENT)
    }

    /** A sound from the café counter, if the café is known and the listener is within earshot of it. */
    private fun cafeSound(
        sink: VoiceSink, sfx: Sfx, level: Float, volume: Float, lx: Float, lz: Float, yaw: Float, pitch: Float,
    ) {
        if (!cafeAt || sink.ambientVoices() >= MAX_AMBIENT_VOICES) return
        val p = placement
        Spatial.place(lx, lz, yaw, cafeX, cafeZ, p)
        if (p.silent) return
        val v = level * ambient * volume
        sink.startVoice(sfx, p.left * v, p.right * v, BLEEP_SEND * p.send, pitch, Priority.AMBIENT)
    }
}
