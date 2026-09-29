package com.pocketarcade.engine.audio

import com.pocketarcade.engine.Sfx

/**
 * The heart of the sound: everything between "play this" and a block of stereo samples, with no
 * thread and no `AudioTrack` in it (`AudioSynth` owns those), so it runs the same in a unit test.
 *
 * Any thread may ask for sounds ([play], [playAt]); the requests wait in a small queue that only
 * holds its lock for a few instructions. The mixer thread alone owns the voices: [render] drains
 * the queue, then mixes one block: the hall's ambience, every sound effect voice (each with its
 * own pan and gain), then the master limiter, into interleaved 16-bit stereo.
 */
internal class MixEngine(val sampleRate: Int) : VoiceSink {
    companion object {
        /** Frames per block: 10 ms at 48 kHz, the size `AudioSynth` writes to the track. */
        const val BLOCK = 480

        /** Sounds that may play at once. A busier moment steals the least important. */
        const val MAX_VOICES = 28

        /** Spare slots for voices being stolen, which fade out over [STEAL_FADE] instead of being cut. */
        const val DYING_SLOTS = 12

        /** A stolen voice fades out over this many frames (2 ms): too short to hear, long enough not to click. */
        const val STEAL_FADE = 96

        /** Requests that can wait between blocks; a flood past this is dropped rather than blocking anyone. */
        const val QUEUE_CAP = 96

        /** Master gain before the limiter (headroom for stacked voices). */
        const val MASTER = 0.85f

        /** The limiter's ceiling as a 16-bit sample value. */
        const val FULL_SCALE = 32000f
    }

    /** The synthesized sound effects. */
    val bank = SfxBank(sampleRate)

    @Volatile var muted = false

    /** Target loudness of the arcade ambience (0 = silent). Smoothly approached by the mixer. */
    var ambientTarget: Float
        get() = ambience.target
        set(v) {
            ambience.target = v
        }

    /** The player's volume settings: master multipliers on the sound effects and on the ambience. */
    @Volatile var sfxVolume = 1f
    @Volatile var ambienceVolume = 1f

    /** The background sound (see [HallAmbience]). */
    val ambience = HallAmbience(sampleRate)

    /** The room the sound effects sound in; changing it crossfades (see [Reverb]). */
    val reverb = Reverb(sampleRate)

    /** The soundtrack, on its own bus with its own reverb and volume. */
    val music = Music(sampleRate)

    // ------------------------------------------------------------ the listener

    @Volatile private var listenerX = 0f
    @Volatile private var listenerZ = 0f
    @Volatile private var listenerYaw = Math.PI.toFloat()

    /** Where the player's ears are: world position and heading (see [Spatial] for the conventions). */
    fun setListener(x: Float, z: Float, yaw: Float) {
        listenerX = x
        listenerZ = z
        listenerYaw = yaw
    }

    // ------------------------------------------------------------ requests

    private val queueLock = Any()
    private var queued = 0
    private val qSfx = IntArray(QUEUE_CAP)
    private val qGainL = FloatArray(QUEUE_CAP)
    private val qGainR = FloatArray(QUEUE_CAP)
    private val qSend = FloatArray(QUEUE_CAP)
    private val qPitch = FloatArray(QUEUE_CAP)
    private val qPriority = IntArray(QUEUE_CAP)

    /** The caller's scratch for [playAt] (one per engine: callers share the app's UI thread). */
    private val callerPlacement = Placement()

    /** Plays [sfx] from dead centre; [pitch] scales playback speed, so 2.0 is an octave up. */
    fun play(sfx: Sfx, volume: Float, pitch: Float) {
        val v = volume * sfxVolume
        if (muted || v <= 0f) return
        // Dead centre: the equal-power pan law's 1/sqrt(2) each side, made up (see Spatial.CENTRE_MAKEUP) to unity.
        enqueue(sfx, v, v, pitch, Priority.NORMAL, v)
    }

    /** Plays [sfx] from the world point ([x], [z]) as heard from the listener. Inaudible sounds cost nothing. */
    fun playAt(sfx: Sfx, x: Float, z: Float, volume: Float, pitch: Float, priority: Int = Priority.NORMAL) {
        val v = volume * sfxVolume
        if (muted || v <= 0f) return
        val p = callerPlacement
        Spatial.place(listenerX, listenerZ, listenerYaw, x, z, p)
        if (p.silent) return
        enqueue(sfx, p.left * v, p.right * v, pitch, priority, p.send * v)
    }

    /** [sendScale] is how much of the sound's default reverb send to use: its loudness, times how near it is. */
    private fun enqueue(sfx: Sfx, gainL: Float, gainR: Float, pitch: Float, priority: Int, sendScale: Float) {
        if (bank.samples(sfx) == null) return
        synchronized(queueLock) {
            if (queued == QUEUE_CAP) return
            val i = queued++
            qSfx[i] = sfx.ordinal
            qGainL[i] = gainL
            qGainR[i] = gainR
            qSend[i] = sendScale
            qPitch[i] = pitch
            qPriority[i] = priority
        }
    }

    // ------------------------------------------------------------ voices

    /** One sound effect being played. Only the mixer thread touches these. */
    private class Voice {
        var sound: FloatArray? = null
        var pos = 0f
        var rate = 1f
        var gainL = 0f
        var gainR = 0f
        var send = 0f
        var priority = 0

        /** 0 for a voice playing normally; otherwise frames of fade-out left before it is freed. */
        var fade = 0
    }

    private val voices = Array(MAX_VOICES + DYING_SLOTS) { Voice() }

    /** Whether the last block was muted (so the reverb's tail is emptied once, not every block). */
    private var wasMuted = false

    private val mixL = FloatArray(BLOCK)
    private val mixR = FloatArray(BLOCK)

    /** The mono sum of every voice's reverb send. */
    private val sendBus = FloatArray(BLOCK)

    override fun ambientVoices(): Int {
        var c = 0
        for (v in voices) if (v.sound != null && v.fade == 0 && v.priority == Priority.AMBIENT) c++
        return c
    }

    /** How many voices are sounding (fading ones included); for tests. */
    internal fun activeVoices(): Int = voices.count { it.sound != null }

    override fun startVoice(sfx: Sfx, gainL: Float, gainR: Float, send: Float, pitch: Float, priority: Int) {
        val snd = bank.samples(sfx) ?: return
        startVoiceOf(snd, gainL, gainR, send, pitch, priority)
    }

    /** [startVoice] for a raw buffer (tests feed it their own). Steals the least important voice when full. */
    internal fun startVoiceOf(snd: FloatArray, gainL: Float, gainR: Float, send: Float, pitch: Float, priority: Int) {
        var free = -1
        var live = 0
        var victim = -1
        var victimScore = Float.MAX_VALUE
        for (i in voices.indices) {
            val v = voices[i]
            val s = v.sound
            if (s == null) {
                if (free < 0) free = i
                continue
            }
            if (v.fade > 0) continue
            live++
            // The best voice to lose: the least important, then the one furthest through its sound.
            val score = v.priority * 2f - v.pos / s.size
            if (score < victimScore) {
                victimScore = score
                victim = i
            }
        }
        if (live >= MAX_VOICES) {
            // A less important sound never takes the place of a more important one.
            if (voices[victim].priority > priority) return
            if (free < 0) return
            voices[victim].fade = STEAL_FADE
        }
        if (free < 0) return
        val v = voices[free]
        v.sound = snd
        v.pos = 0f
        v.rate = pitch.coerceIn(0.25f, 4f)
        v.gainL = gainL
        v.gainR = gainR
        v.send = send
        v.priority = priority
        v.fade = 0
    }

    /**
     * Starts the voice a queued request asks for, taking its send from the sound's own default; a big
     * sound (a jackpot, a fanfare) also ducks the music, as deep as the sound is loud here.
     */
    private fun startQueued(i: Int) {
        val sfx = Sfx.entries[qSfx[i]]
        startVoice(sfx, qGainL[i], qGainR[i], SfxMix.sendFor(sfx) * qSend[i], qPitch[i], qPriority[i])
        val depth = SfxMix.duckDepth(sfx)
        if (depth > 0f) music.duck(depth * minOf(1f, maxOf(qGainL[i], qGainR[i])), SfxMix.duckHold(sfx))
    }

    private fun drainQueue() {
        synchronized(queueLock) {
            for (i in 0 until queued) startQueued(i)
            queued = 0
        }
    }

    private fun mixVoices(n: Int) {
        for (v in voices) {
            val snd = v.sound ?: continue
            var pos = v.pos
            val rate = v.rate
            val gl = v.gainL
            val gr = v.gainR
            val sd = v.send
            val last = snd.size - 1
            var fade = v.fade
            var ended = false
            var i = 0
            while (i < n) {
                val idx = pos.toInt()
                if (idx >= last) {
                    ended = true
                    break
                }
                var s = snd[idx] + (snd[idx + 1] - snd[idx]) * (pos - idx)
                if (fade > 0) {
                    s *= fade * (1f / STEAL_FADE)
                    if (--fade == 0) ended = true
                }
                mixL[i] += s * gl
                mixR[i] += s * gr
                sendBus[i] += s * sd
                pos += rate
                i++
                if (ended) break
            }
            if (ended) {
                v.sound = null
                v.fade = 0
            } else {
                v.pos = pos
                v.fade = fade
            }
        }
    }

    /**
     * Mixes one block ([BLOCK] frames) into [out] as interleaved 16-bit stereo (left first).
     * Silence while muted; sounds that were playing are dropped so they don't burst out later.
     */
    fun render(out: ShortArray) {
        val n = BLOCK
        drainQueue()
        music.muted = muted
        if (muted) {
            for (v in voices) {
                v.sound = null
                v.fade = 0
            }
            if (!wasMuted) reverb.reset()
            wasMuted = true
            music.render(mixL, mixR, n) // lets it shut down; it adds nothing while muted
            java.util.Arrays.fill(out, 0, n * 2, 0)
            return
        }
        wasMuted = false
        java.util.Arrays.fill(mixL, 0, n, 0f)
        java.util.Arrays.fill(mixR, 0, n, 0f)
        java.util.Arrays.fill(sendBus, 0, n, 0f)
        ambience.render(mixL, mixR, n, this, ambienceVolume, listenerX, listenerZ, listenerYaw)
        mixVoices(n)
        reverb.process(sendBus, n, mixL, mixR)
        music.render(mixL, mixR, n)
        for (i in 0 until n) {
            out[2 * i] = limit(mixL[i])
            out[2 * i + 1] = limit(mixR[i])
        }
    }

    /** The master gain and a soft clip (cubic, unity slope at zero) down to 16 bits. */
    private fun limit(sample: Float): Short {
        var x = (sample * MASTER).coerceIn(-1.5f, 1.5f)
        x -= x * x * x / 6.75f
        return (x * FULL_SCALE).toInt().toShort()
    }
}

/** How much of each sound effect goes to the room reverb, and other per-sound mixing defaults. */
internal object SfxMix {
    /** Reverb send of small, close, mechanical sounds: hardly any, so they stay crisp. */
    private const val SEND_DRY = 0.06f

    /** Menu and UI sounds, and the ticks and beeps of a round. */
    private const val SEND_UI = 0.1f

    /** Most sounds. */
    private const val SEND_NORMAL = 0.16f

    /** Bright, ringing sounds (coins, glass, chimes) bloom into a room. */
    private const val SEND_RING = 0.26f

    /** Fanfares and big moments get the most room. */
    private const val SEND_BIG = 0.34f

    /** How deeply (0 = not at all) a sound ducks the music while it plays, and for how many seconds. */
    fun duckDepth(sfx: Sfx): Float = when (sfx) {
        Sfx.JACKPOT -> 0.55f
        Sfx.HIGHSCORE -> 0.6f
        Sfx.WIN, Sfx.FINISH, Sfx.LUCKY -> 0.35f
        Sfx.EXPLOSION -> 0.4f
        Sfx.BOMB, Sfx.CRASH, Sfx.PRIZE -> 0.28f
        Sfx.CHEER -> 0.15f
        else -> 0f
    }

    fun duckHold(sfx: Sfx): Float = when (sfx) {
        Sfx.HIGHSCORE -> 2f
        Sfx.JACKPOT -> 1.3f
        Sfx.FINISH -> 1.2f
        Sfx.WIN, Sfx.LUCKY, Sfx.CHEER -> 0.9f
        Sfx.EXPLOSION -> 0.7f
        else -> 0.5f
    }

    /** Reverb send when a sound is played without saying otherwise (0 = dry, 1 = as loud as the sound). */
    fun sendFor(sfx: Sfx): Float = when (sfx) {
        Sfx.STEP, Sfx.ENGINE, Sfx.ROLL, Sfx.CLAW_MOTOR, Sfx.REEL, Sfx.PRINT, Sfx.SKID, Sfx.SPINNER,
        Sfx.SHELL, Sfx.DRY_FIRE, Sfx.TICKET, Sfx.FLIPPER, Sfx.SLINGSHOT -> SEND_DRY

        Sfx.BLIP, Sfx.SELECT, Sfx.ERROR, Sfx.COUNTDOWN, Sfx.WHOOSH, Sfx.BUZZER, Sfx.TILT, Sfx.PLUNGER -> SEND_UI

        Sfx.COIN, Sfx.TOKEN, Sfx.CLINK, Sfx.SPILL, Sfx.RIM, Sfx.PRIZE, Sfx.LAP, Sfx.BITE, Sfx.CATCH,
        Sfx.BUMPER, Sfx.RICOCHET, Sfx.STEAM -> SEND_RING

        Sfx.WIN, Sfx.JACKPOT, Sfx.HIGHSCORE, Sfx.LUCKY, Sfx.FINISH, Sfx.GO, Sfx.CHEER, Sfx.EXPLOSION,
        Sfx.BOMB, Sfx.CRASH, Sfx.GUNSHOT, Sfx.ALARM -> SEND_BIG

        else -> SEND_NORMAL
    }
}
