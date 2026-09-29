package com.pocketarcade.engine.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** What the soundtrack is doing: which theme should be playing. */
sealed class MusicScene {
    /** No music: the current theme fades out. */
    object Silence : MusicScene()

    /** The title screen's theme. */
    object Title : MusicScene()

    /** The hall's theme, in layers that fade in with [Music.setIntensity]. */
    object Hall : MusicScene()

    /** The results screen's warm, resolving loop. */
    object Results : MusicScene()

    /** The theme of machine [id] (its `MiniGame.id`; an unknown one gets a generic upbeat theme). */
    data class Game(val id: String) : MusicScene()
}

/**
 * Short musical hits that play over the scene and duck it while they sound. The brief SFX beeps
 * of the countdown and the fanfare of a high score still play; these add the harmony and weight
 * under them.
 */
enum class Stinger {
    /** One count of the 3-2-1: a soft thump and tick. */
    COUNTDOWN,

    /** The round starts: a hit, a rising sparkle. */
    GO,

    /** The clock ran out: a falling power-down. */
    TIME_UP,

    /** The results appear: a resolving chime. */
    RESULTS,

    /** A new high score: a bright fanfare. */
    HIGH_SCORE,
}

/**
 * The procedural soundtrack. Themes are [Track] data played by a sequencer through small
 * synthesizer voices in real time, so there is nothing to load or store, layers respond the
 * instant intensity changes, and the same seed always renders the same samples.
 *
 * Control is thread-safe and cheap: [setScene], [setIntensity], [setQuiet], [stinger] and [duck]
 * only set flags the mixer thread reads once a block. [render] is called by the mixer, adds the
 * music (on its own bus, with its own reverb, limited to full scale) to the output buses and
 * allocates nothing. While muted, or with the volume at zero, it does no work at all and
 * restarts the current theme when it is turned back on.
 */
class Music internal constructor(private val sampleRate: Int, private val seed: Int = 1) {
    companion object {
        /** The crossfade between themes, in seconds. */
        const val FADE_TIME = 1f

        /** Loudness of the whole music bus before the player's volume: themes are mixed so this sits under the sound effects. */
        const val LEVEL = 0.8f

        /** Intensity follows its target: quickly up, slowly down (time constants, seconds). */
        const val INTENSITY_UP_TAU = 0.5f
        const val INTENSITY_DOWN_TAU = 2.5f

        /**
         * While quiet (under the intro card, a pause menu) the music sits at this level, easing over
         * [QUIET_TAU], and is filtered down to [QUIET_CUTOFF_HZ] (as if behind glass) rather than only turned down.
         */
        const val QUIET_LEVEL = 0.4f
        const val QUIET_TAU = 0.35f
        const val QUIET_CUTOFF_HZ = 1400f

        /** The filter's cutoff when the music is fully forward: above hearing, and the filter is bypassed. */
        private const val OPEN_CUTOFF_HZ = 16000f

        /** A filter coefficient at or above this is treated as open (no filtering). */
        private const val OPEN_COEF = 0.999f

        /** A duck drops fast and recovers slowly (time constants, seconds). */
        const val DUCK_ATTACK = 0.03f
        const val DUCK_RELEASE = 0.5f

        /** Themes that can sound at once (a crossfade needs two; a third covers a change of mind mid-fade), and stingers. */
        private const val SCENE_PLAYERS = 3
        private const val STINGER_PLAYERS = 2

        /** Voices in each player's pool: sized so no theme ever has to take over a sounding note (a test checks). */
        private const val SCENE_POOL = 34
        private const val STINGER_POOL = 24

        /** Stinger requests that can wait for the next block. */
        private const val PENDING = 8

        /** Below this a volume counts as off. */
        private const val OFF = 0.0005f

        /** The most frames one [render] may be given (the size of the buses). */
        const val MAX_BLOCK = 2048
    }

    /** The music volume the player chose, as a gain: 0 is off. */
    @Volatile var volume = 1f

    /** Silences the music entirely (the mute button). */
    @Volatile var muted = false

    @Volatile private var wanted: MusicScene = MusicScene.Silence
    @Volatile private var intensityTarget = 0f
    @Volatile private var quiet = false

    private val lock = Any()
    private val pendingStingers = arrayOfNulls<Stinger>(PENDING)
    private var pendingCount = 0
    private var pendingDuckDepth = 0f
    private var pendingDuckHold = 0f

    private val sr = sampleRate.toFloat()
    private val scenePlayers = Array(SCENE_PLAYERS) { ScorePlayer(sampleRate, SCENE_POOL, seed + it) }
    private val stingerPlayers = Array(STINGER_PLAYERS) { ScorePlayer(sampleRate, STINGER_POOL, seed + 100 + it) }
    private val fadeLevel = FloatArray(SCENE_PLAYERS)
    private val fadeDir = IntArray(SCENE_PLAYERS)

    private val reverb = Reverb(sampleRate, Room.MUSIC)
    private val busL = FloatArray(MAX_BLOCK)
    private val busR = FloatArray(MAX_BLOCK)
    private val sendBus = FloatArray(MAX_BLOCK)

    private var current: MusicScene? = null
    private var wasOn = false
    private var intensity = 0f
    private var quietGain = 1f
    private var duckGain = 1f
    private var duckHold = 0f
    private var duckDepth = 0f
    private var masterGain = 0f
    private var glassL = 0f
    private var glassR = 0f

    // ------------------------------------------------------------ control

    /** Moves to the theme for [scene], crossfading over about a second. Asking for the current scene again does nothing. */
    fun setScene(scene: MusicScene) {
        wanted = scene
    }

    /**
     * How intense the moment is, 0..1: higher layers of the theme fade in as it rises (the hall's
     * arpeggio and lead with activity, a machine's extra parts as the round heats up).
     */
    fun setIntensity(level: Float) {
        intensityTarget = level.coerceIn(0f, 1f)
    }

    /** Sits the music back (true) under something that needs the ear, such as the intro card, and brings it back (false). */
    fun setQuiet(on: Boolean) {
        quiet = on
    }

    /** Plays [which] over the scene, ducking it while it sounds. */
    fun stinger(which: Stinger) {
        synchronized(lock) {
            if (pendingCount < PENDING) pendingStingers[pendingCount++] = which
        }
    }

    /**
     * Dips the music by [depth] (0..1, 1 is silence) for [holdSeconds], then lets it back over about
     * half a second. Overlapping ducks keep the deeper and the longer.
     */
    fun duck(depth: Float, holdSeconds: Float) {
        synchronized(lock) {
            pendingDuckDepth = maxOf(pendingDuckDepth, depth.coerceIn(0f, 1f))
            pendingDuckHold = maxOf(pendingDuckHold, holdSeconds)
        }
    }

    // ------------------------------------------------------------ mixing

    /**
     * Adds [n] frames of music to [outL]/[outR]. Called by the mixer thread once a block, [n] at most
     * [MAX_BLOCK].
     */
    internal fun render(outL: FloatArray, outR: FloatArray, n: Int) {
        val on = !muted && volume > OFF
        if (!on) {
            if (wasOn) shutDown()
            return
        }
        if (!wasOn) {
            wasOn = true
            current = null
        }
        val dt = n / sr
        control(dt)

        java.util.Arrays.fill(busL, 0, n, 0f)
        java.util.Arrays.fill(busR, 0, n, 0f)
        java.util.Arrays.fill(sendBus, 0, n, 0f)

        val sceneGain = duckGain * quietGain
        for (i in scenePlayers.indices) {
            val p = scenePlayers[i]
            if (!p.active) continue
            val from = fadeCurve(fadeLevel[i])
            fadeLevel[i] = (fadeLevel[i] + fadeDir[i] * dt / FADE_TIME).coerceIn(0f, 1f)
            val to = fadeCurve(fadeLevel[i])
            if (fadeDir[i] > 0 && fadeLevel[i] >= 1f) fadeDir[i] = 0
            p.render(busL, busR, sendBus, n, intensity, from * sceneGain, to * sceneGain)
            if (fadeDir[i] < 0 && fadeLevel[i] <= 0f) {
                p.stop()
                fadeDir[i] = 0
            }
        }
        for (p in stingerPlayers) if (p.active) p.render(busL, busR, sendBus, n, 1f, 1f, 1f)

        reverb.process(sendBus, n, busL, busR)
        behindGlass(n)

        val to = LEVEL * volume
        val step = (to - masterGain) / n
        var g = masterGain
        for (i in 0 until n) {
            g += step
            outL[i] += Dsp.softLimit(busL[i] * g)
            outR[i] += Dsp.softLimit(busR[i] * g)
        }
        masterGain = to
    }

    /**
     * A one-pole low-pass on the music bus that closes as the music sits back ([quietGain] falls), so
     * a quiet moment is muffled as well as lower. Bypassed (and its state kept in step) when the music
     * is fully forward.
     */
    private fun behindGlass(n: Int) {
        val open = ((quietGain - QUIET_LEVEL) / (1f - QUIET_LEVEL)).coerceIn(0f, 1f)
        val hz = QUIET_CUTOFF_HZ + (OPEN_CUTOFF_HZ - QUIET_CUTOFF_HZ) * open * open
        val c = (2f * PI.toFloat() * hz / sr).coerceAtMost(1f)
        if (c >= OPEN_COEF) {
            glassL = busL[n - 1]
            glassR = busR[n - 1]
            return
        }
        var l = glassL
        var r = glassR
        for (i in 0 until n) {
            l += c * (busL[i] - l)
            r += c * (busR[i] - r)
            busL[i] = l
            busR[i] = r
        }
        glassL = l
        glassR = r
    }

    private fun fadeCurve(level: Float): Float = sin(level * (PI.toFloat() / 2f))

    /** Once a block: picks up scene and stinger requests, and eases intensity, quiet and the duck. */
    private fun control(dt: Float) {
        val scene = wanted
        if (scene != current) {
            current = scene
            switchTo(scene)
        }

        var stingers = 0
        var depth = 0f
        var hold = 0f
        var todo: Stinger? = null
        synchronized(lock) {
            depth = pendingDuckDepth
            hold = pendingDuckHold
            pendingDuckDepth = 0f
            pendingDuckHold = 0f
            if (pendingCount > 0) {
                // One at a time (the rest wait for the next block): keeps the lock short and the order right.
                todo = pendingStingers[0]
                for (i in 1 until pendingCount) pendingStingers[i - 1] = pendingStingers[i]
                pendingCount--
                pendingStingers[pendingCount] = null
                stingers = 1
            }
        }
        if (depth > 0f) {
            duckDepth = maxOf(duckDepth, depth)
            duckHold = maxOf(duckHold, hold)
        }
        if (stingers > 0) todo?.let { startStinger(it) }

        val target = intensityTarget
        val tau = if (target > intensity) INTENSITY_UP_TAU else INTENSITY_DOWN_TAU
        intensity += (target - intensity) * (1f - exp(-dt / tau))

        val quietTo = if (quiet) QUIET_LEVEL else 1f
        quietGain += (quietTo - quietGain) * (1f - exp(-dt / QUIET_TAU))

        val duckTo: Float
        if (duckHold > 0f) {
            duckHold -= dt
            duckTo = 1f - duckDepth
        } else {
            duckDepth = 0f
            duckTo = 1f
        }
        val duckTau = if (duckTo < duckGain) DUCK_ATTACK else DUCK_RELEASE
        duckGain += (duckTo - duckGain) * (1f - exp(-dt / duckTau))
    }

    private fun switchTo(scene: MusicScene) {
        // Everything playing now fades out...
        for (i in scenePlayers.indices) if (scenePlayers[i].active) fadeDir[i] = -1
        val track = try {
            Tracks.forScene(scene)
        } catch (_: Exception) {
            null
        } ?: return
        // ...and the new theme fades in on a free player (or the one nearest to gone).
        var slot = -1
        for (i in scenePlayers.indices) if (!scenePlayers[i].active) {
            slot = i
            break
        }
        if (slot < 0) {
            slot = 0
            for (i in scenePlayers.indices) if (fadeLevel[i] < fadeLevel[slot]) slot = i
        }
        scenePlayers[slot].start(track, intensity)
        fadeLevel[slot] = 0f
        fadeDir[slot] = 1
    }

    private fun startStinger(which: Stinger) {
        val stinger = try {
            Tracks.stinger(which)
        } catch (_: Exception) {
            return
        }
        var slot = stingerPlayers.indexOfFirst { !it.active }
        if (slot < 0) slot = 0
        stingerPlayers[slot].start(stinger.track, 1f)
        duckDepth = maxOf(duckDepth, stinger.duckDepth)
        duckHold = maxOf(duckHold, stinger.duckHold)
    }

    /** Muted or at zero volume: stop everything so it costs nothing, and start the theme afresh when it comes back. */
    private fun shutDown() {
        for (p in scenePlayers) p.stop()
        for (p in stingerPlayers) p.stop()
        java.util.Arrays.fill(fadeLevel, 0f)
        java.util.Arrays.fill(fadeDir, 0)
        reverb.reset()
        masterGain = 0f
        glassL = 0f
        glassR = 0f
        duckGain = 1f
        duckHold = 0f
        wasOn = false
    }

    /** For tests: where the intensity, the quiet and the duck have got to (1 = untouched for quiet and duck). */
    internal val intensityLevel: Float get() = intensity
    internal val quietLevel: Float get() = quietGain
    internal val duckLevel: Float get() = duckGain

    /** For tests: the track of each scene player that is sounding. */
    internal fun playing(): List<String> = scenePlayers.filter { it.active }.mapNotNull { it.track?.name }

    /** For tests: the most voices any player has needed at once, and whether any had to steal one. */
    internal fun peakVoices(): Int = (scenePlayers + stingerPlayers).maxOf { it.peakVoices }
    internal fun steals(): Int = (scenePlayers + stingerPlayers).sumOf { it.steals }
}
