package com.pocketarcade.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Every sound effect in the game. All of them are synthesized at startup; there are no audio files. */
enum class Sfx {
    BLIP, SELECT, ERROR, COIN, TOKEN, TICKET, PRINT, WIN, JACKPOT, LOSE,
    WHACK, BONK, BOMB, POP, SWISH, RIM, BOUNCE, THUD, ROLL,
    CLAW_MOTOR, CLAW_GRAB, DROP, PRIZE, CHEER, STEP, WHOOSH,
    COUNTDOWN, GO, HIGHSCORE, CLINK, SPILL, BUZZER, LUCKY, GUTTER,

    // Light-gun shooter.
    GUNSHOT, RELOAD, DRY_FIRE, RICOCHET, EXPLOSION, ENEMY_FIRE, ALARM, SHELL,

    // Pinball.
    FLIPPER, BUMPER, SLINGSHOT, PLUNGER, DRAIN, SPINNER, TILT,

    // Fishing.
    CAST, SPLASH, REEL, BITE, LINE_SNAP, CATCH,

    // Racer. ENGINE is a short steady rev meant to be replayed back to back, pitched to the speed.
    ENGINE, SKID, BOOST, CRASH, LAP, FINISH, HORN,
}

/**
 * A tiny software synthesizer: sound effects are rendered once into float buffers, then a mixer
 * thread streams any number of overlapping voices plus a procedural arcade ambience (mains hum,
 * crowd murmur and distant machine bleeps) into a single low-latency [AudioTrack].
 */
class AudioSynth {
    private val sampleRate: Int = try {
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it in 16000..96000 } ?: 44100
    } catch (_: Exception) {
        44100
    }

    private val sounds = arrayOfNulls<FloatArray>(Sfx.entries.size)

    private class Voice {
        var sound: FloatArray? = null
        var pos = 0f
        var rate = 1f
        var volume = 1f
    }

    private val voices = Array(24) { Voice() }
    private val voiceLock = Any()
    private val runLock = Object()

    @Volatile private var running = true
    @Volatile private var active = false
    @Volatile var muted = false

    /** Target loudness of the arcade ambience (0 = silent). Smoothly approached by the mixer. */
    @Volatile var ambientTarget = 0f

    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ runMixer() }, "ArcadeSynth").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun setActive(value: Boolean) {
        active = value
        synchronized(runLock) { runLock.notifyAll() }
    }

    fun release() {
        running = false
        synchronized(runLock) { runLock.notifyAll() }
    }

    /** Plays [sfx]; [pitch] scales playback speed, so 2.0 is an octave up. */
    fun play(sfx: Sfx, volume: Float = 1f, pitch: Float = 1f) {
        if (muted) return
        val snd = sounds[sfx.ordinal] ?: return
        synchronized(voiceLock) {
            var slot = voices.firstOrNull { it.sound == null }
            if (slot == null) {
                // Steal the voice that has played the longest.
                slot = voices.maxByOrNull { it.pos / (it.sound?.size ?: 1) } ?: return
            }
            slot.sound = snd
            slot.pos = 0f
            slot.rate = pitch.coerceIn(0.25f, 4f)
            slot.volume = volume
        }
    }

    // ---------------------------------------------------------------- mixer

    private fun runMixer() {
        try {
            generateAll()
        } catch (_: Exception) {
            return
        }
        val track = try {
            buildTrack()
        } catch (_: Exception) {
            null
        } ?: return

        val block = 480
        val mix = FloatArray(block)
        val out = ShortArray(block)
        val rng = Random(99)
        val sine = FloatArray(4096) { sin(it / 4096.0 * 2 * PI).toFloat() }
        var p1 = 0f
        var p2 = 0f
        var p3 = 0f
        var brown = 0f
        var murmur = 0f
        var lfo = 0f
        var ambient = 0f
        var bleepTimer = 1f
        var playing = false

        try {
            while (running) {
                if (!active) {
                    if (playing) {
                        track.pause(); track.flush(); playing = false
                    }
                    synchronized(runLock) {
                        while (!active && running) runLock.wait(500)
                    }
                    continue
                }
                if (!playing) {
                    track.play(); playing = true
                }

                ambient += (ambientTarget - ambient) * 0.02f
                val blockSeconds = block.toFloat() / sampleRate
                bleepTimer -= blockSeconds
                if (bleepTimer <= 0f) {
                    bleepTimer = rng.range(0.7f, 2.4f)
                    if (ambient > 0.05f) {
                        val pick = when (rng.nextInt(5)) {
                            0 -> Sfx.BLIP
                            1 -> Sfx.COIN
                            2 -> Sfx.POP
                            3 -> Sfx.SELECT
                            else -> Sfx.CLINK
                        }
                        play(pick, rng.range(0.035f, 0.075f) * ambient, rng.range(0.5f, 1.6f))
                    }
                }

                val humVol = ambient * 0.05f
                val crowdVol = ambient * 0.9f
                val sr = sampleRate.toFloat()
                for (i in 0 until block) {
                    p1 = (p1 + 55f / sr) % 1f
                    p2 = (p2 + 110f / sr) % 1f
                    p3 = (p3 + 165.3f / sr) % 1f
                    lfo = (lfo + 0.11f / sr) % 1f
                    val hum = sine[(p1 * 4095).toInt()] * 0.7f + sine[(p2 * 4095).toInt()] * 0.4f +
                        sine[(p3 * 4095).toInt()] * 0.15f
                    brown = (brown + (rng.nextFloat() - 0.5f) * 0.05f) * 0.996f
                    murmur += (brown - murmur) * 0.08f
                    val swell = 0.65f + 0.35f * sine[(lfo * 4095).toInt()]
                    mix[i] = hum * humVol + murmur * crowdVol * swell
                }

                synchronized(voiceLock) {
                    for (v in voices) {
                        val snd = v.sound ?: continue
                        var pos = v.pos
                        val rate = v.rate
                        val vol = v.volume
                        val last = snd.size - 1
                        var i = 0
                        while (i < block) {
                            val idx = pos.toInt()
                            if (idx >= last) break
                            val frac = pos - idx
                            mix[i] += (snd[idx] + (snd[idx + 1] - snd[idx]) * frac) * vol
                            pos += rate
                            i++
                        }
                        if (pos.toInt() >= last) v.sound = null else v.pos = pos
                    }
                }

                val master = if (muted) 0f else 0.85f
                for (i in 0 until block) {
                    var x = (mix[i] * master).coerceIn(-1.5f, 1.5f)
                    x -= x * x * x / 6.75f
                    out[i] = (x * 32000f).toInt().toShort()
                }
                track.write(out, 0, block)
            }
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, 480 * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    // ---------------------------------------------------------------- synthesis

    private companion object {
        const val SQUARE = 0
        const val TRIANGLE = 1
        const val SAW = 2
        const val SINE = 3
        const val NOISE = 4
    }

    private val noiseRng = Random(7)

    private fun buf(seconds: Float) = FloatArray((seconds * sampleRate).toInt() + 2)

    /**
     * Adds one note to [b]: frequency sweeps exponentially from [f0] to [f1] over [dur] seconds,
     * with a linear attack, optional exponential [decay] and a short click-free release.
     */
    private fun tone(
        b: FloatArray, start: Float, dur: Float, f0: Float, f1: Float = f0,
        wave: Int = SQUARE, vol: Float = 0.3f, attack: Float = 0.003f, decay: Float = 0f,
        duty: Float = 0.5f, vibHz: Float = 0f, vibDepth: Float = 0f, lowpass: Float = 1f,
    ) {
        val sr = sampleRate.toFloat()
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        var phase = 0f
        var held = 0f
        var filtered = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.size) break
            val t = i / sr
            val sweep = f0 * (f1 / f0).pow(t / dur)
            val f = if (vibHz > 0f) sweep * (1f + vibDepth * sin(2f * PI.toFloat() * vibHz * t)) else sweep
            val prev = phase
            phase = (phase + f / sr) % 1f
            val raw = when (wave) {
                SQUARE -> if (phase < duty) 1f else -1f
                TRIANGLE -> if (phase < 0.5f) phase * 4f - 1f else 3f - phase * 4f
                SAW -> phase * 2f - 1f
                SINE -> sin(phase * 2f * PI.toFloat())
                else -> {
                    if (phase < prev) held = noiseRng.nextFloat() * 2f - 1f
                    held
                }
            }
            filtered += (raw - filtered) * lowpass
            var env = if (t < attack) t / attack else 1f
            if (decay > 0f) env *= exp(-decay * (t - attack).coerceAtLeast(0f))
            val tail = (dur - t) / 0.008f
            if (tail < 1f) env *= tail.coerceAtLeast(0f)
            b[idx] += filtered * env * vol
        }
    }

    /** The synthesized samples of [sfx] once [generateAll] has run (for tests). */
    internal fun samples(sfx: Sfx): FloatArray? = sounds[sfx.ordinal]

    internal fun generateAll() {
        fun put(s: Sfx, b: FloatArray) {
            sounds[s.ordinal] = b
        }

        put(Sfx.BLIP, buf(0.06f).also { tone(it, 0f, 0.055f, 880f, 1320f, vol = 0.22f, duty = 0.25f) })
        put(Sfx.SELECT, buf(0.12f).also {
            tone(it, 0f, 0.045f, 660f, vol = 0.22f, duty = 0.25f)
            tone(it, 0.045f, 0.07f, 990f, vol = 0.22f, duty = 0.25f, decay = 20f)
        })
        put(Sfx.ERROR, buf(0.3f).also {
            tone(it, 0f, 0.12f, 220f, 190f, vol = 0.25f, duty = 0.3f)
            tone(it, 0.14f, 0.14f, 180f, 150f, vol = 0.25f, duty = 0.3f)
        })
        put(Sfx.COIN, buf(0.32f).also {
            tone(it, 0f, 0.06f, 988f, vol = 0.22f, duty = 0.5f)
            tone(it, 0.06f, 0.25f, 1319f, vol = 0.22f, duty = 0.5f, decay = 10f)
        })
        put(Sfx.TOKEN, buf(0.45f).also {
            tone(it, 0f, 0.03f, 6000f, wave = NOISE, vol = 0.25f, decay = 60f)
            tone(it, 0.02f, 0.12f, 520f, 240f, wave = TRIANGLE, vol = 0.45f, decay = 18f)
            tone(it, 0.12f, 0.32f, 1568f, wave = SINE, vol = 0.3f, decay = 9f)
            tone(it, 0.12f, 0.32f, 2352f, wave = SINE, vol = 0.12f, decay = 12f)
        })
        put(Sfx.TICKET, buf(0.05f).also {
            tone(it, 0f, 0.03f, 1800f, wave = TRIANGLE, vol = 0.25f, decay = 60f)
            tone(it, 0f, 0.02f, 9000f, wave = NOISE, vol = 0.08f, decay = 90f)
        })
        put(Sfx.PRINT, buf(0.08f).also {
            tone(it, 0f, 0.07f, 90f, 80f, vol = 0.12f, duty = 0.3f, lowpass = 0.3f)
            tone(it, 0f, 0.07f, 3000f, wave = NOISE, vol = 0.07f, lowpass = 0.4f)
        })
        put(Sfx.WIN, buf(0.7f).also {
            val notes = floatArrayOf(523f, 659f, 784f, 1047f)
            notes.forEachIndexed { i, f ->
                val last = i == notes.lastIndex
                tone(it, i * 0.08f, if (last) 0.4f else 0.08f, f, vol = 0.2f, duty = 0.25f, decay = if (last) 5f else 0f, vibHz = if (last) 7f else 0f, vibDepth = 0.01f)
            }
            tone(it, 0f, 0.6f, 131f, wave = TRIANGLE, vol = 0.3f, decay = 3f)
        })
        put(Sfx.JACKPOT, buf(1.3f).also {
            val notes = floatArrayOf(523f, 659f, 784f, 1047f, 1319f, 1568f)
            for (r in 0 until 3) notes.forEachIndexed { i, f ->
                tone(it, r * 0.3f + i * 0.045f, 0.05f, f * (1f + r * 0.12f), vol = 0.16f, duty = 0.25f)
            }
            tone(it, 0.9f, 0.4f, 2093f, vol = 0.18f, duty = 0.25f, decay = 5f, vibHz = 8f, vibDepth = 0.015f)
            tone(it, 0f, 1.2f, 131f, 262f, wave = TRIANGLE, vol = 0.3f, decay = 1.5f)
        })
        put(Sfx.LOSE, buf(0.6f).also {
            tone(it, 0f, 0.18f, 392f, vol = 0.2f, duty = 0.25f)
            tone(it, 0.18f, 0.18f, 330f, vol = 0.2f, duty = 0.25f)
            tone(it, 0.36f, 0.22f, 262f, 220f, vol = 0.2f, duty = 0.25f, decay = 4f)
        })
        put(Sfx.WHACK, buf(0.18f).also {
            tone(it, 0f, 0.05f, 5000f, wave = NOISE, vol = 0.5f, decay = 50f, lowpass = 0.5f)
            tone(it, 0f, 0.16f, 190f, 55f, wave = SINE, vol = 0.8f, decay = 18f)
        })
        put(Sfx.BONK, buf(0.2f).also {
            tone(it, 0f, 0.1f, 740f, 370f, vol = 0.22f, duty = 0.5f, decay = 18f)
            tone(it, 0f, 0.15f, 370f, 185f, wave = TRIANGLE, vol = 0.35f, decay = 14f)
        })
        put(Sfx.BOMB, buf(0.9f).also {
            tone(it, 0f, 0.85f, 2200f, 400f, wave = NOISE, vol = 0.7f, decay = 4.5f, lowpass = 0.25f)
            tone(it, 0f, 0.6f, 95f, 28f, wave = SINE, vol = 0.9f, decay = 5f)
        })
        put(Sfx.POP, buf(0.08f).also { tone(it, 0f, 0.07f, 300f, 900f, wave = SINE, vol = 0.35f, decay = 20f) })
        put(Sfx.SWISH, buf(0.35f).also {
            tone(it, 0f, 0.32f, 1500f, 7000f, wave = NOISE, vol = 0.35f, attack = 0.05f, decay = 7f, lowpass = 0.35f)
        })
        put(Sfx.RIM, buf(0.35f).also {
            tone(it, 0f, 0.3f, 523f, wave = SINE, vol = 0.3f, decay = 12f)
            tone(it, 0f, 0.3f, 1190f, wave = SINE, vol = 0.2f, decay = 14f)
            tone(it, 0f, 0.3f, 1870f, wave = SINE, vol = 0.14f, decay = 16f)
            tone(it, 0f, 0.02f, 6000f, wave = NOISE, vol = 0.2f, decay = 90f)
        })
        put(Sfx.BOUNCE, buf(0.12f).also { tone(it, 0f, 0.1f, 150f, 80f, wave = SINE, vol = 0.8f, decay = 25f) })
        put(Sfx.THUD, buf(0.16f).also {
            tone(it, 0f, 0.14f, 110f, 50f, wave = SINE, vol = 0.8f, decay = 20f)
            tone(it, 0f, 0.04f, 3000f, wave = NOISE, vol = 0.2f, decay = 60f, lowpass = 0.3f)
        })
        put(Sfx.ROLL, buf(0.6f).also {
            tone(it, 0f, 0.58f, 900f, wave = NOISE, vol = 0.35f, attack = 0.04f, lowpass = 0.08f, decay = 2f)
            tone(it, 0f, 0.58f, 70f, 55f, wave = TRIANGLE, vol = 0.18f, attack = 0.04f, decay = 2f)
        })
        put(Sfx.CLAW_MOTOR, buf(0.22f).also {
            tone(it, 0f, 0.21f, 118f, wave = SAW, vol = 0.1f, attack = 0.02f, vibHz = 30f, vibDepth = 0.05f, lowpass = 0.3f)
        })
        put(Sfx.CLAW_GRAB, buf(0.2f).also {
            tone(it, 0f, 0.03f, 7000f, wave = NOISE, vol = 0.25f, decay = 70f)
            tone(it, 0.01f, 0.15f, 320f, 200f, duty = 0.3f, vol = 0.16f, decay = 16f)
        })
        put(Sfx.DROP, buf(0.3f).also { tone(it, 0f, 0.28f, 700f, 200f, wave = SINE, vol = 0.3f, decay = 5f) })
        put(Sfx.PRIZE, buf(0.8f).also {
            val notes = floatArrayOf(784f, 988f, 1175f, 1568f, 1976f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.06f, 0.3f, f, wave = TRIANGLE, vol = 0.25f, decay = 7f) }
            tone(it, 0.3f, 0.45f, 3136f, wave = SINE, vol = 0.1f, decay = 6f, vibHz = 12f, vibDepth = 0.02f)
        })
        put(Sfx.CHEER, buf(1.3f).also {
            for (k in 0 until 7) {
                tone(it, k * 0.08f, 1.1f - k * 0.08f, 1200f + k * 300f, wave = NOISE, vol = 0.12f, attack = 0.1f, decay = 2.2f, lowpass = 0.12f, vibHz = 6f + k, vibDepth = 0.3f)
            }
        })
        put(Sfx.STEP, buf(0.03f).also { tone(it, 0f, 0.025f, 2000f, wave = NOISE, vol = 0.08f, decay = 80f, lowpass = 0.2f) })
        put(Sfx.WHOOSH, buf(0.5f).also {
            tone(it, 0f, 0.48f, 400f, 5000f, wave = NOISE, vol = 0.3f, attack = 0.2f, decay = 3f, lowpass = 0.2f)
        })
        put(Sfx.COUNTDOWN, buf(0.14f).also { tone(it, 0f, 0.12f, 440f, vol = 0.22f, duty = 0.5f, decay = 8f) })
        put(Sfx.GO, buf(0.4f).also { tone(it, 0f, 0.38f, 880f, vol = 0.22f, duty = 0.5f, decay = 4f, vibHz = 9f, vibDepth = 0.02f) })
        put(Sfx.HIGHSCORE, buf(1.6f).also {
            val melody = floatArrayOf(523f, 659f, 784f, 659f, 784f, 1047f)
            val times = floatArrayOf(0f, 0.12f, 0.24f, 0.4f, 0.52f, 0.7f)
            melody.forEachIndexed { i, f ->
                val last = i == melody.lastIndex
                tone(it, times[i], if (last) 0.8f else 0.12f, f, vol = 0.2f, duty = 0.25f, decay = if (last) 2.5f else 0f, vibHz = if (last) 6f else 0f, vibDepth = 0.012f)
                tone(it, times[i], if (last) 0.8f else 0.12f, f * 1.5f, wave = TRIANGLE, vol = 0.1f, decay = if (last) 2.5f else 0f)
            }
            tone(it, 0f, 1.5f, 131f, wave = TRIANGLE, vol = 0.3f, decay = 1.2f)
        })
        put(Sfx.CLINK, buf(0.16f).also {
            tone(it, 0f, 0.15f, 2100f, wave = SINE, vol = 0.22f, decay = 25f)
            tone(it, 0f, 0.15f, 3350f, wave = SINE, vol = 0.14f, decay = 30f)
        })
        put(Sfx.SPILL, buf(0.7f).also {
            val r = Random(3)
            for (k in 0 until 9) {
                val st = r.nextFloat() * 0.5f
                tone(it, st, 0.12f, r.range(1800f, 2600f), wave = SINE, vol = 0.14f, decay = 30f)
                tone(it, st, 0.12f, r.range(3000f, 3800f), wave = SINE, vol = 0.08f, decay = 35f)
            }
        })
        put(Sfx.BUZZER, buf(0.6f).also {
            tone(it, 0f, 0.55f, 150f, wave = SAW, vol = 0.18f, lowpass = 0.5f)
            tone(it, 0f, 0.55f, 155f, wave = SQUARE, vol = 0.1f, duty = 0.4f, lowpass = 0.5f)
        })
        put(Sfx.LUCKY, buf(1.0f).also {
            for (k in 0 until 10) {
                tone(it, k * 0.07f, 0.2f, 1047f * 2f.pow(k / 12f * 2f), wave = TRIANGLE, vol = 0.18f, decay = 10f)
            }
            tone(it, 0.7f, 0.3f, 2637f, wave = SINE, vol = 0.12f, decay = 8f, vibHz = 14f, vibDepth = 0.03f)
        })
        put(Sfx.GUTTER, buf(0.5f).also {
            tone(it, 0f, 0.45f, 300f, 90f, wave = TRIANGLE, vol = 0.3f, decay = 3f)
            tone(it, 0f, 0.45f, 600f, wave = NOISE, vol = 0.15f, lowpass = 0.1f, decay = 4f)
        })
        generateMachineSounds()
        // Normalise any buffer that clips so stacked partials never distort.
        for (i in sounds.indices) {
            val s = sounds[i] ?: continue
            var peak = 0f
            for (v in s) peak = maxOf(peak, abs(v))
            if (peak > 0.95f) {
                val k = 0.95f / peak
                for (j in s.indices) s[j] *= k
            }
        }
    }

    /** The light-gun, pinball, fishing and racer sounds. */
    private fun generateMachineSounds() {
        fun put(s: Sfx, b: FloatArray) {
            sounds[s.ordinal] = b
        }

        // ---- Light-gun shooter.
        put(Sfx.GUNSHOT, buf(0.3f).also {
            tone(it, 0f, 0.012f, 9000f, wave = NOISE, vol = 0.6f, decay = 150f)
            tone(it, 0f, 0.26f, 6000f, 700f, wave = NOISE, vol = 0.7f, decay = 16f, lowpass = 0.45f)
            tone(it, 0f, 0.2f, 170f, 42f, wave = SINE, vol = 0.9f, decay = 20f)
        })
        put(Sfx.RELOAD, buf(0.34f).also {
            // Magazine out, slide back, slide home.
            tone(it, 0f, 0.03f, 2600f, wave = SQUARE, vol = 0.18f, duty = 0.2f, decay = 90f)
            tone(it, 0f, 0.03f, 7000f, wave = NOISE, vol = 0.25f, decay = 90f)
            tone(it, 0.1f, 0.1f, 1500f, 3200f, wave = NOISE, vol = 0.18f, attack = 0.02f, lowpass = 0.3f)
            tone(it, 0.22f, 0.1f, 1900f, wave = SQUARE, vol = 0.22f, duty = 0.3f, decay = 60f)
            tone(it, 0.22f, 0.06f, 8000f, wave = NOISE, vol = 0.3f, decay = 70f)
            tone(it, 0.22f, 0.1f, 240f, 120f, wave = SINE, vol = 0.4f, decay = 35f)
        })
        put(Sfx.DRY_FIRE, buf(0.06f).also {
            tone(it, 0f, 0.04f, 3200f, wave = SQUARE, vol = 0.18f, duty = 0.15f, decay = 120f)
            tone(it, 0f, 0.02f, 6000f, wave = NOISE, vol = 0.15f, decay = 150f)
        })
        put(Sfx.RICOCHET, buf(0.5f).also {
            tone(it, 0f, 0.02f, 8000f, wave = NOISE, vol = 0.4f, decay = 120f)
            tone(it, 0.01f, 0.46f, 3400f, 1100f, wave = SINE, vol = 0.3f, decay = 5f, vibHz = 38f, vibDepth = 0.03f)
            tone(it, 0.01f, 0.4f, 5100f, 1600f, wave = SINE, vol = 0.1f, decay = 7f)
        })
        put(Sfx.EXPLOSION, buf(1.3f).also {
            tone(it, 0f, 1.25f, 2600f, 180f, wave = NOISE, vol = 0.75f, decay = 3f, lowpass = 0.22f)
            tone(it, 0f, 0.9f, 80f, 24f, wave = SINE, vol = 0.95f, decay = 3.5f)
            val r = Random(11)
            for (k in 0 until 8) tone(it, 0.15f + r.nextFloat() * 0.7f, 0.05f, 4000f, wave = NOISE, vol = 0.12f, decay = 60f)
        })
        put(Sfx.ENEMY_FIRE, buf(0.22f).also {
            tone(it, 0f, 0.2f, 1600f, 260f, wave = SQUARE, vol = 0.18f, duty = 0.3f, decay = 10f)
            tone(it, 0f, 0.08f, 5000f, wave = NOISE, vol = 0.2f, decay = 40f, lowpass = 0.5f)
        })
        put(Sfx.ALARM, buf(0.95f).also {
            for (k in 0 until 6) tone(it, k * 0.15f, 0.15f, if (k % 2 == 0) 880f else 660f, vol = 0.18f, duty = 0.5f, lowpass = 0.6f)
        })
        put(Sfx.SHELL, buf(0.2f).also {
            // A spent casing bouncing on the floor.
            for (k in 0 until 3) tone(it, k * 0.06f - k * k * 0.008f, 0.05f, 4200f - k * 300f, wave = SINE, vol = 0.14f / (k + 1), decay = 50f)
        })

        // ---- Pinball.
        put(Sfx.FLIPPER, buf(0.12f).also {
            tone(it, 0f, 0.015f, 5000f, wave = NOISE, vol = 0.35f, decay = 120f)
            tone(it, 0f, 0.1f, 140f, 60f, wave = SINE, vol = 0.7f, decay = 30f)
            tone(it, 0f, 0.05f, 420f, 220f, wave = SQUARE, vol = 0.12f, duty = 0.3f, decay = 40f)
        })
        put(Sfx.BUMPER, buf(0.3f).also {
            tone(it, 0f, 0.02f, 7000f, wave = NOISE, vol = 0.3f, decay = 100f)
            tone(it, 0f, 0.25f, 1100f, 700f, wave = SINE, vol = 0.35f, decay = 16f)
            tone(it, 0f, 0.25f, 2200f, wave = TRIANGLE, vol = 0.15f, decay = 14f)
            tone(it, 0f, 0.12f, 180f, 90f, wave = SINE, vol = 0.5f, decay = 25f)
        })
        put(Sfx.SLINGSHOT, buf(0.14f).also {
            tone(it, 0f, 0.05f, 6000f, wave = NOISE, vol = 0.4f, decay = 70f)
            tone(it, 0f, 0.12f, 620f, 260f, wave = SQUARE, vol = 0.15f, duty = 0.25f, decay = 22f)
        })
        put(Sfx.PLUNGER, buf(0.45f).also {
            tone(it, 0f, 0.4f, 90f, 240f, wave = SAW, vol = 0.2f, vibHz = 22f, vibDepth = 0.08f, decay = 5f, lowpass = 0.4f)
            tone(it, 0.02f, 0.3f, 800f, 4000f, wave = NOISE, vol = 0.2f, attack = 0.03f, decay = 8f, lowpass = 0.3f)
        })
        put(Sfx.DRAIN, buf(0.9f).also {
            tone(it, 0f, 0.3f, 392f, 370f, wave = TRIANGLE, vol = 0.25f)
            tone(it, 0.3f, 0.55f, 294f, 110f, wave = TRIANGLE, vol = 0.28f, decay = 3f)
            tone(it, 0f, 0.8f, 300f, 80f, wave = NOISE, vol = 0.15f, lowpass = 0.08f, decay = 3f)
        })
        put(Sfx.SPINNER, buf(0.45f).also {
            // Ticks slowing down as the spinner winds out.
            var at = 0f
            var gap = 0.022f
            while (at < 0.4f) {
                tone(it, at, 0.015f, 2300f, wave = SQUARE, vol = 0.15f, duty = 0.2f, decay = 150f)
                at += gap
                gap *= 1.18f
            }
        })
        put(Sfx.TILT, buf(0.8f).also {
            for (k in 0 until 4) tone(it, k * 0.2f, 0.14f, 110f, wave = SAW, vol = 0.2f, lowpass = 0.35f, vibHz = 30f, vibDepth = 0.04f)
        })

        // ---- Fishing.
        put(Sfx.CAST, buf(0.5f).also {
            tone(it, 0f, 0.45f, 700f, 5200f, wave = NOISE, vol = 0.3f, attack = 0.06f, decay = 5f, lowpass = 0.25f)
            tone(it, 0.02f, 0.4f, 1800f, 2600f, wave = SQUARE, vol = 0.04f, duty = 0.1f, vibHz = 60f, vibDepth = 0.3f, decay = 4f)
        })
        put(Sfx.SPLASH, buf(0.6f).also {
            tone(it, 0f, 0.55f, 3200f, 500f, wave = NOISE, vol = 0.45f, decay = 6f, lowpass = 0.3f)
            tone(it, 0f, 0.12f, 260f, 90f, wave = SINE, vol = 0.4f, decay = 22f)
            val r = Random(5)
            for (k in 0 until 5) tone(it, 0.08f + r.nextFloat() * 0.3f, 0.05f, r.range(900f, 1600f), r.range(1800f, 2600f), wave = SINE, vol = 0.1f, decay = 40f)
        })
        put(Sfx.REEL, buf(0.2f).also {
            // Ratchet clicks, short enough to repeat while the crank turns.
            for (k in 0 until 8) {
                tone(it, k * 0.025f, 0.012f, 1500f, wave = SQUARE, vol = 0.12f, duty = 0.2f, decay = 200f)
                tone(it, k * 0.025f, 0.008f, 5000f, wave = NOISE, vol = 0.08f, decay = 250f)
            }
        })
        put(Sfx.BITE, buf(0.28f).also {
            tone(it, 0f, 0.1f, 280f, 720f, wave = SINE, vol = 0.4f, decay = 14f)
            tone(it, 0.13f, 0.12f, 320f, 900f, wave = SINE, vol = 0.45f, decay = 12f)
        })
        put(Sfx.LINE_SNAP, buf(0.5f).also {
            tone(it, 0f, 0.02f, 9000f, wave = NOISE, vol = 0.45f, decay = 150f)
            tone(it, 0f, 0.45f, 950f, 180f, wave = SAW, vol = 0.18f, decay = 7f, vibHz = 24f, vibDepth = 0.06f, lowpass = 0.5f)
        })
        put(Sfx.CATCH, buf(0.8f).also {
            val notes = floatArrayOf(660f, 880f, 1100f, 1320f, 1760f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.07f, 0.25f, f, wave = TRIANGLE, vol = 0.22f, decay = 9f) }
            tone(it, 0f, 0.3f, 2400f, 700f, wave = NOISE, vol = 0.15f, decay = 9f, lowpass = 0.3f)
        })

        // ---- Racer.
        put(Sfx.ENGINE, buf(0.24f).also {
            // Steady (no decay) so back-to-back plays blur into one engine note.
            tone(it, 0f, 0.24f, 82f, wave = SAW, vol = 0.22f, attack = 0.01f, vibHz = 32f, vibDepth = 0.06f, lowpass = 0.25f)
            tone(it, 0f, 0.24f, 41f, wave = SQUARE, vol = 0.12f, attack = 0.01f, duty = 0.35f, lowpass = 0.2f)
            tone(it, 0f, 0.24f, 1200f, wave = NOISE, vol = 0.05f, attack = 0.01f, lowpass = 0.1f)
        })
        put(Sfx.SKID, buf(0.6f).also {
            tone(it, 0f, 0.55f, 2200f, 1500f, wave = NOISE, vol = 0.35f, attack = 0.03f, decay = 3f, lowpass = 0.55f, vibHz = 45f, vibDepth = 0.2f)
            tone(it, 0f, 0.5f, 900f, 760f, wave = SQUARE, vol = 0.05f, duty = 0.1f, decay = 3f, vibHz = 45f, vibDepth = 0.05f)
        })
        put(Sfx.BOOST, buf(0.7f).also {
            tone(it, 0f, 0.65f, 140f, 620f, wave = SAW, vol = 0.2f, attack = 0.05f, decay = 2.5f, lowpass = 0.35f)
            tone(it, 0f, 0.6f, 500f, 5000f, wave = NOISE, vol = 0.25f, attack = 0.1f, decay = 3f, lowpass = 0.3f)
        })
        put(Sfx.CRASH, buf(1.0f).also {
            tone(it, 0f, 0.95f, 4500f, 300f, wave = NOISE, vol = 0.65f, decay = 4f, lowpass = 0.35f)
            tone(it, 0f, 0.5f, 95f, 30f, wave = SINE, vol = 0.9f, decay = 6f)
            tone(it, 0.03f, 0.6f, 1320f, wave = SINE, vol = 0.14f, decay = 9f)
            tone(it, 0.05f, 0.6f, 1930f, wave = SINE, vol = 0.1f, decay = 11f)
        })
        put(Sfx.LAP, buf(0.5f).also {
            tone(it, 0f, 0.12f, 988f, wave = TRIANGLE, vol = 0.3f, decay = 8f)
            tone(it, 0.12f, 0.35f, 1319f, wave = TRIANGLE, vol = 0.3f, decay = 6f)
        })
        put(Sfx.FINISH, buf(1.4f).also {
            val melody = floatArrayOf(523f, 659f, 784f, 1047f, 784f, 1047f)
            val times = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.5f, 0.6f)
            melody.forEachIndexed { i, f ->
                val last = i == melody.lastIndex
                tone(it, times[i], if (last) 0.7f else 0.1f, f, vol = 0.2f, duty = 0.25f, decay = if (last) 3f else 0f, vibHz = if (last) 7f else 0f, vibDepth = 0.012f)
            }
            tone(it, 0f, 1.3f, 131f, 196f, wave = TRIANGLE, vol = 0.3f, decay = 1.5f)
        })
        put(Sfx.HORN, buf(0.5f).also {
            tone(it, 0f, 0.45f, 350f, vol = 0.18f, duty = 0.45f, lowpass = 0.35f)
            tone(it, 0f, 0.45f, 440f, vol = 0.14f, duty = 0.45f, lowpass = 0.35f)
        })
    }
}
