package com.pocketarcade.engine.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** The oscillator shapes a music [Patch] can use. */
internal object Wave {
    const val SINE = 0
    const val TRIANGLE = 1
    const val SAW = 2
    const val SQUARE = 3
    const val NOISE = 4
}

/**
 * One instrument: an oscillator (or two), an envelope, a low-pass (or high-pass) filter with its
 * own envelope, and where it sits in the mix. Times are in seconds and pitches in Hz, so a patch
 * sounds the same at any sample rate.
 *
 * @property wave the main oscillator ([Wave]); [wave2] the second, at [ratio2] times the pitch
 * (1.006 detunes it a little for a fat pad, 4 makes a bell overtone) and [mix2] as loud (0 = off).
 * @property sub a sine an octave below, this loud.
 * @property pulse a square wave's pulse width.
 * @property attack how long the note takes to rise, linearly.
 * @property decay how long the fall from the peak toward [sustain] takes to reach 60 dB down (a
 * percussive patch has sustain 0 and simply dies away, so this is how long it rings); [release] the
 * same, after the note ends. A note is over once it is 60 dB down, so these also say how many voices
 * a lane needs.
 * @property cutoffHz the filter's cutoff; [cutEnvHz] more of it at the start of the note, dying
 * away with time constant [cutDecay], so a pluck opens and closes. [highpass] turns the low-pass
 * into a high-pass.
 * @property pitchEnv how far above its pitch the note starts (1 = one pitch higher, so twice the
 * frequency), falling back with time constant [pitchDecay]: a kick drum's thump.
 * @property vibHz a vibrato of [vibDepth] (a fraction of the pitch) that fades in over [vibDelay].
 * @property gain overall level; [pan] -1..1 and [spread] how far the two oscillators are set apart
 * either side of it (stereo width); [send] the share sent to the reverb; [pump] how much the
 * track's kick drum ducks this patch (the sidechain "pump").
 */
internal class Patch(
    val wave: Int = Wave.SAW,
    val wave2: Int = Wave.SAW,
    val ratio2: Float = 1f,
    val mix2: Float = 0f,
    val sub: Float = 0f,
    val pulse: Float = 0.5f,
    val attack: Float = 0.005f,
    val decay: Float = 0.3f,
    val sustain: Float = 0.7f,
    val release: Float = 0.3f,
    val cutoffHz: Float = 8000f,
    val cutEnvHz: Float = 0f,
    val cutDecay: Float = 0.2f,
    val highpass: Boolean = false,
    val pitchEnv: Float = 0f,
    val pitchDecay: Float = 0.03f,
    val vibHz: Float = 0f,
    val vibDepth: Float = 0f,
    val vibDelay: Float = 0.25f,
    val gain: Float = 0.3f,
    val pan: Float = 0f,
    val spread: Float = 0f,
    val send: Float = 0.15f,
    val pump: Float = 0f,
)

/**
 * One note being played: a small subtractive synth voice. It renders into the player's stereo
 * bus and reverb send a chunk at a time and frees itself when the note has faded away. Everything
 * is state in fields; rendering never allocates.
 */
internal class MusicVoice {
    private companion object {
        /** A note is over when its envelope falls below this (about -62 dB). */
        const val DEAD = 0.0008f

        /** The shortest attack: a few dozen samples, so no note starts with a click. */
        const val MIN_ATTACK = 0.0012f

        /** The filter coefficient's ceiling (a one-pole filter is only stable below 1). */
        const val MAX_COEF = 0.97f

        const val ATTACK = 0
        const val DECAY = 1
        const val RELEASE = 2

        const val TWO_PI = (2.0 * PI).toFloat()

        /** ln(1000): a fall of 60 dB in amplitude is a factor of 1000. */
        const val LN_60DB = 6.9077554f
        const val NOISE_SCALE = 1f / 2147483648f
    }

    var active = false
        private set

    /** Which lane started this voice, and that lane's layer (0..2): the player scales it by the layer's fade. */
    var lane = 0
        private set
    var layer = 0
        private set

    /** Samples since the note started (for choosing which voice to steal). */
    var age = 0
        private set

    /** The envelope's level: near 0 means this voice is the cheapest to lose. */
    val level: Float get() = env

    private var patch: Patch = Patch()

    private var ph1 = 0f
    private var ph2 = 0f
    private var phSub = 0f
    private var inc1 = 0f
    private var inc2 = 0f
    private var incSub = 0f

    private var stage = ATTACK
    private var env = 0f
    private var attackInc = 0f
    private var decayMul = 0f
    private var sustain = 0f
    private var releaseMul = 0f
    private var gate = 0

    private var coefBase = 0f
    private var coefEnv = 0f
    private var coefMul = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var b1 = 0f
    private var b2 = 0f

    private var pe = 0f
    private var peMul = 0f
    private var vibPhase = 0f
    private var vibInc = 0f
    private var vibFade = 0f
    private var vibFadeInc = 0f

    private var noise = 1
    private var gain = 0f
    private var leftA = 0f
    private var rightA = 0f
    private var leftB = 0f
    private var rightB = 0f
    private var send = 0f
    private var pumpAmount = 0f

    /**
     * Starts a note of [p] at MIDI note [midi] with loudness [vel] (0..1), held for [gateSamples]
     * before it is released. [seed] makes any noise in it the same every time.
     */
    fun noteOn(p: Patch, midi: Int, vel: Float, gateSamples: Int, sampleRate: Float, seed: Int, laneIndex: Int, layerIndex: Int) {
        patch = p
        active = true
        lane = laneIndex
        layer = layerIndex
        age = 0
        val hz = Dsp.noteHz(midi)
        ph1 = 0f
        ph2 = 0f
        phSub = 0f
        inc1 = hz / sampleRate
        inc2 = hz * p.ratio2 / sampleRate
        incSub = hz * 0.5f / sampleRate

        stage = ATTACK
        env = 0f
        attackInc = 1f / (maxOf(p.attack, MIN_ATTACK) * sampleRate)
        decayMul = exp(-LN_60DB / (maxOf(p.decay, 0.005f) * sampleRate))
        sustain = p.sustain
        releaseMul = exp(-LN_60DB / (maxOf(p.release, 0.01f) * sampleRate))
        gate = maxOf(gateSamples, 1)

        coefBase = minOf(MAX_COEF, TWO_PI * p.cutoffHz / sampleRate)
        coefEnv = TWO_PI * p.cutEnvHz / sampleRate
        coefMul = exp(-1f / (maxOf(p.cutDecay, 0.002f) * sampleRate))
        a1 = 0f
        a2 = 0f
        b1 = 0f
        b2 = 0f

        pe = 1f
        peMul = exp(-1f / (maxOf(p.pitchDecay, 0.002f) * sampleRate))
        vibPhase = 0f
        vibInc = p.vibHz / sampleRate
        vibFade = 0f
        vibFadeInc = 1f / (maxOf(p.vibDelay, 0.01f) * sampleRate)

        noise = seed or 1
        gain = vel * p.gain
        send = p.send
        pumpAmount = p.pump
        // Equal-power pans: the main oscillator at pan - spread, the second at pan + spread.
        val pa = ((p.pan - p.spread).coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        val pb = ((p.pan + p.spread).coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        leftA = cos(pa)
        rightA = sin(pa)
        leftB = cos(pb)
        rightB = sin(pb)
    }

    /** Silences the voice at once (only for shutting a whole player down). */
    fun kill() {
        active = false
    }

    /** Releases the note now, whatever its gate says. */
    fun release() {
        if (active && stage != RELEASE) stage = RELEASE
    }

    /**
     * Adds [count] frames of this voice to [outL]/[outR] from index [offset], and to [sendBus]
     * for the reverb. [gainStart] and [gainSlope] are the player's layer and fade gain at the
     * first frame and how much it changes per frame; [pumpBus] (index 0 = first frame of the
     * chunk) is the kick-driven sidechain envelope, used if this patch pumps.
     */
    fun render(
        outL: FloatArray, outR: FloatArray, sendBus: FloatArray, offset: Int, count: Int,
        gainStart: Float, gainSlope: Float, pumpBus: FloatArray,
    ) {
        val p = patch
        val wave = p.wave
        val wave2 = p.wave2
        val pulse = p.pulse
        val mix2 = p.mix2
        val sub = p.sub
        val hp = p.highpass
        val pitchEnv = p.pitchEnv
        val vibDepth = p.vibDepth
        var i = 0
        while (i < count) {
            when (stage) {
                ATTACK -> {
                    env += attackInc
                    if (env >= 1f) {
                        env = 1f
                        stage = DECAY
                    }
                }

                DECAY -> env = sustain + (env - sustain) * decayMul
                else -> env *= releaseMul
            }
            if (stage != RELEASE && --gate <= 0) stage = RELEASE
            if (env < DEAD && stage != ATTACK) {
                active = false
                break
            }

            var pm = 1f
            if (pitchEnv > 0f) {
                pm += pitchEnv * pe
                pe *= peMul
            }
            if (vibDepth > 0f) {
                vibPhase += vibInc
                if (vibPhase >= 1f) vibPhase -= 1f
                if (vibFade < 1f) vibFade = minOf(1f, vibFade + vibFadeInc)
                pm *= 1f + vibDepth * vibFade * Dsp.sine(vibPhase)
            }

            ph1 += inc1 * pm
            if (ph1 >= 1f) ph1 -= 1f
            var a = when (wave) {
                Wave.SINE -> Dsp.sine(ph1)
                Wave.TRIANGLE -> if (ph1 < 0.5f) ph1 * 4f - 1f else 3f - ph1 * 4f
                Wave.SAW -> sawBlep(ph1, inc1 * pm)
                Wave.SQUARE -> squareBlep(ph1, inc1 * pm, pulse)
                else -> nextNoise()
            }
            if (sub > 0f) {
                phSub += incSub * pm
                if (phSub >= 1f) phSub -= 1f
                a += sub * Dsp.sine(phSub)
            }
            var b = 0f
            if (mix2 > 0f) {
                ph2 += inc2 * pm
                if (ph2 >= 1f) ph2 -= 1f
                b = mix2 * when (wave2) {
                    Wave.SINE -> Dsp.sine(ph2)
                    Wave.TRIANGLE -> if (ph2 < 0.5f) ph2 * 4f - 1f else 3f - ph2 * 4f
                    Wave.SAW -> sawBlep(ph2, inc2 * pm)
                    Wave.SQUARE -> squareBlep(ph2, inc2 * pm, pulse)
                    else -> nextNoise()
                }
            }

            val c = minOf(MAX_COEF, coefBase + coefEnv)
            coefEnv *= coefMul
            a1 += c * (a - a1)
            a2 += c * (a1 - a2)
            a = if (hp) a - a2 else a2
            if (mix2 > 0f) {
                b1 += c * (b - b1)
                b2 += c * (b1 - b2)
                b = if (hp) b - b2 else b2
            }

            var e = env * gain * (gainStart + gainSlope * i)
            if (pumpAmount > 0f) e *= 1f - pumpAmount * pumpBus[i]
            val ya = a * e
            val yb = b * e
            val at = offset + i
            outL[at] += ya * leftA + yb * leftB
            outR[at] += ya * rightA + yb * rightB
            sendBus[at] += (ya + yb) * send
            i++
        }
        age += i
    }

    private fun nextNoise(): Float {
        var x = noise
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noise = x
        return x * NOISE_SCALE
    }

    /** A band-limited saw (polyBLEP): the corner at the wrap is rounded so high notes don't alias. */
    private fun sawBlep(t: Float, dt: Float): Float = 2f * t - 1f - blep(t, dt)

    private fun squareBlep(t: Float, dt: Float, pw: Float): Float {
        // A pulse that isn't 50% carries a DC offset; take it out so notes don't thump the speaker.
        var v = if (t < pw) 1f else -1f
        v -= 2f * pw - 1f
        v += blep(t, dt)
        var t2 = t - pw
        if (t2 < 0f) t2 += 1f
        v -= blep(t2, dt)
        return v
    }

    private fun blep(t: Float, dt: Float): Float {
        if (t < dt) {
            val x = t / dt
            return x + x - x * x - 1f
        }
        if (t > 1f - dt) {
            val x = (t - 1f) / dt
            return x * x + x + x + 1f
        }
        return 0f
    }
}
