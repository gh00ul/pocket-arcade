package com.pocketarcade.engine.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A room for the sound effects to sit in. The reverb morphs between rooms over about a second
 * (see [Reverb.room]), so a change of screen is never a click.
 *
 * @property rt60 seconds for the tail to fall by 60 dB.
 * @property damping 0 for a bright tail, 1 for a dark one (how fast the highs die).
 * @property wet how much of the tail is returned to the mix. 1 is a tail as loud as the signal
 * that fed it, so a sound with a 0.25 reverb send in a room of 1 gets a tail a quarter its size.
 * @property early how much of the first few reflections (the sense of walls close by) to add.
 */
enum class Room(val rt60: Float, val damping: Float, val wet: Float, val early: Float) {
    /** The arcade hall: big, high-ceilinged, fairly wet. */
    HALL(rt60 = 2.3f, damping = 0.45f, wet = 0.9f, early = 0.5f),

    /** Inside a machine's screen: dry and close, a small cabinet's worth of room. */
    GAME(rt60 = 0.5f, damping = 0.55f, wet = 0.25f, early = 0.8f),

    /** The title: a big, glossy, mostly-tail space that lets the music bloom. */
    TITLE(rt60 = 3.2f, damping = 0.35f, wet = 1.2f, early = 0.3f),

    /** The music bus's own room, always the same wherever the player stands: lush, not for sound effects. */
    MUSIC(rt60 = 2f, damping = 0.5f, wet = 0.8f, early = 0.25f),
}

/**
 * A cheap stereo room reverb: a mono send is delayed a little (the pre-delay), thinned by a
 * high-pass, then fed to eight parallel damped feedback combs and four series all-pass filters
 * per ear (a Schroeder/Moorer "Freeverb" layout, with the two ears' delays a few samples apart so
 * the tail is wide), plus a handful of early-reflection taps. Every feedback loop is sized from
 * the reverb time, so each comb decays by the same amount per second whatever its length, and the
 * loops always lose energy: it cannot run away.
 *
 * Room changes ([room]) move the reverb time, damping, return level and early reflections toward the new
 * room's values a little every block; the delay lengths never change, so nothing clicks.
 * [process] allocates nothing.
 */
internal class Reverb(private val sampleRate: Int, initial: Room = Room.GAME) {
    companion object {
        /** The most frames one [process] call may be given. */
        const val MAX_BLOCK = 2048

        /** Comb delays in samples at 44.1 kHz (Freeverb's), with the right ear's a little longer. */
        private val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private const val EAR_SPREAD = 23

        /** All-pass delays at 44.1 kHz (Freeverb's), and the feedback each uses. */
        private val ALLPASSES = intArrayOf(556, 441, 341, 225)
        private const val ALLPASS_FEEDBACK = 0.5f

        /** The delays are scaled up this much: a bigger, smoother room than Freeverb's default. */
        private const val SIZE = 1.15f

        /** The tail starts this long after the sound (seconds). */
        private const val PRE_DELAY = 0.016f

        /** First reflections: delays in seconds for each ear, and how strongly each is heard. */
        private val EARLY_L = floatArrayOf(0.0065f, 0.011f, 0.0175f, 0.024f, 0.033f)
        private val EARLY_R = floatArrayOf(0.0085f, 0.0135f, 0.020f, 0.028f, 0.037f)
        private val EARLY_GAIN = floatArrayOf(0.85f, 0.7f, 0.55f, 0.45f, 0.35f)

        /** Time constant (seconds) of a room change: 95% of the way there in three times this. */
        const val MORPH_TIME = 0.35f

        /** The tail is high-passed here so low sounds don't turn it to mud. */
        private const val HIGH_PASS_HZ = 140f

        /** Damping filter coefficient in the combs' feedback: from bright (0.05) to dark (0.55). */
        private const val DAMP_MIN = 0.05f
        private const val DAMP_RANGE = 0.5f

        /**
         * Once nothing has been sent for this many reverb times (plus a little), the tail is more
         * than 70 dB down and processing stops until the next sound.
         */
        private const val IDLE_TAILS = 1.3f
        private const val IDLE_EXTRA = 0.4f
        private const val SILENCE = 1e-6f
    }

    private val sr = sampleRate.toFloat()

    private fun scaled(base: Int, extra: Int = 0): Int =
        ((base + extra) * sampleRate / 44100.0 * SIZE).toInt().coerceAtLeast(8)

    private val combLen = Array(2) { ear -> IntArray(COMBS.size) { scaled(COMBS[it], if (ear == 1) EAR_SPREAD else 0) } }
    private val combBuf = Array(2) { ear -> Array(COMBS.size) { FloatArray(combLen[ear][it]) } }
    private val combPos = Array(2) { IntArray(COMBS.size) }
    private val combStore = Array(2) { FloatArray(COMBS.size) }
    private val combGain = Array(2) { FloatArray(COMBS.size) }

    private val apBuf = Array(2) { ear -> Array(ALLPASSES.size) { FloatArray(scaled(ALLPASSES[it], if (ear == 1) EAR_SPREAD else 0)) } }
    private val apPos = Array(2) { IntArray(ALLPASSES.size) }

    /** The input line: pre-delay and the early-reflection taps read from it. */
    private val lineMask: Int
    private val line: FloatArray
    private var lineAt = 0
    private val preDelay = (PRE_DELAY * sr).toInt()
    private val earlyL = IntArray(EARLY_L.size) { (EARLY_L[it] * sr).toInt() }
    private val earlyR = IntArray(EARLY_R.size) { (EARLY_R[it] * sr).toInt() }

    init {
        var size = 64
        while (size < (0.05f * sr).toInt()) size *= 2
        line = FloatArray(size)
        lineMask = size - 1
    }

    private val feed = FloatArray(MAX_BLOCK)
    private val accL = FloatArray(MAX_BLOCK)
    private val accR = FloatArray(MAX_BLOCK)
    private val earlyOutL = FloatArray(MAX_BLOCK)
    private val earlyOutR = FloatArray(MAX_BLOCK)

    private var hp = 0f
    private val hpCoef = (2.0 * Math.PI * HIGH_PASS_HZ / sampleRate).toFloat().coerceAtMost(0.5f)

    /** The room being moved toward: set it and the reverb glides there over the next second or so. */
    @Volatile var room: Room = initial

    /**
     * A master multiplier on the tail's return level (1 = the room's own, 0 = no reverb heard). The
     * mixer leaves it at 1; tests set it to 0 to hear only the dry sound.
     */
    @Volatile internal var returnScale = 1f

    /** The return gain reached at the end of the last block (the next block glides from it). */
    private var applied = initial.wet

    private var rt60 = initial.rt60
    private var damping = initial.damping
    private var wet = initial.wet
    private var early = initial.early
    private var quiet = 0

    /** The reverb time and return level the reverb is at this moment (mid-morph they are between rooms'), for tests. */
    internal val currentRt60: Float get() = rt60
    internal val currentWet: Float get() = wet

    /** Empties the tail. */
    fun reset() {
        for (ear in 0..1) {
            for (b in combBuf[ear]) java.util.Arrays.fill(b, 0f)
            java.util.Arrays.fill(combStore[ear], 0f)
            for (b in apBuf[ear]) java.util.Arrays.fill(b, 0f)
        }
        java.util.Arrays.fill(line, 0f)
        hp = 0f
        quiet = 0
    }

    /**
     * Runs [n] frames of the mono [send] through the room and ADDS the stereo tail to [outL] and
     * [outR]. Costs almost nothing once the tail has died away and nothing new is sent.
     */
    fun process(send: FloatArray, n: Int, outL: FloatArray, outR: FloatArray) {
        require(n <= MAX_BLOCK) { "block of $n frames is too long" }
        val target = room
        val k = 1f - exp(-(n / sr) / MORPH_TIME)
        rt60 += (target.rt60 - rt60) * k
        damping += (target.damping - damping) * k
        wet += (target.wet - wet) * k
        early += (target.early - early) * k
        val wetTo = wet * returnScale

        var peak = 0f
        for (i in 0 until n) peak = maxOf(peak, abs(send[i]))
        quiet = if (peak < SILENCE) quiet + n else 0
        if (quiet > sr * (rt60 * IDLE_TAILS + IDLE_EXTRA)) {
            applied = wetTo
            return
        }

        // Each comb loses the same 60 dB over the reverb time, however long it is; the input is scaled so a
        // white-noise send comes back at about its own level (the sum of eight uncorrelated combs).
        var g2 = 0f
        for (ear in 0..1) {
            for (c in COMBS.indices) {
                val g = 10.0.pow(-3.0 * combLen[ear][c] / (sr * rt60)).toFloat()
                combGain[ear][c] = g
                g2 += g * g
            }
        }
        val inGain = sqrt((1f - g2 / (2 * COMBS.size)) / COMBS.size)
        val damp = DAMP_MIN + DAMP_RANGE * damping

        var at = lineAt
        for (i in 0 until n) {
            line[at] = send[i]
            val x = line[(at - preDelay) and lineMask]
            hp += (x - hp) * hpCoef
            feed[i] = (x - hp) * inGain
            var eL = 0f
            var eR = 0f
            for (t in earlyL.indices) {
                eL += EARLY_GAIN[t] * line[(at - earlyL[t]) and lineMask]
                eR += EARLY_GAIN[t] * line[(at - earlyR[t]) and lineMask]
            }
            earlyOutL[i] = eL
            earlyOutR[i] = eR
            at = (at + 1) and lineMask
        }
        lineAt = at

        combs(0, n, damp, accL)
        combs(1, n, damp, accR)
        allpasses(0, n, accL)
        allpasses(1, n, accR)

        // The return level glides from last block's to this one's, so it never steps.
        val step = (wetTo - applied) / n
        val er = early
        var w = applied
        for (i in 0 until n) {
            w += step
            outL[i] += (accL[i] + earlyOutL[i] * er) * w
            outR[i] += (accR[i] + earlyOutR[i] * er) * w
        }
        applied = wetTo
    }

    private fun combs(ear: Int, n: Int, damp: Float, acc: FloatArray) {
        java.util.Arrays.fill(acc, 0, n, 0f)
        val keep = 1f - damp
        for (c in COMBS.indices) {
            val buf = combBuf[ear][c]
            val len = buf.size
            val g = combGain[ear][c]
            var pos = combPos[ear][c]
            var store = combStore[ear][c]
            for (i in 0 until n) {
                val y = buf[pos]
                store = y * keep + store * damp
                buf[pos] = feed[i] + store * g + Dsp.ANTI_DENORMAL
                acc[i] += y
                if (++pos == len) pos = 0
            }
            combPos[ear][c] = pos
            combStore[ear][c] = store
        }
    }

    private fun allpasses(ear: Int, n: Int, acc: FloatArray) {
        for (a in ALLPASSES.indices) {
            val buf = apBuf[ear][a]
            val len = buf.size
            var pos = apPos[ear][a]
            for (i in 0 until n) {
                // The canonical all-pass: unit gain at every frequency, so it only smears the tail in time.
                val b = buf[pos]
                val w = acc[i] + b * ALLPASS_FEEDBACK + Dsp.ANTI_DENORMAL
                acc[i] = b - w * ALLPASS_FEEDBACK
                buf[pos] = w
                if (++pos == len) pos = 0
            }
            apPos[ear][a] = pos
        }
    }
}
