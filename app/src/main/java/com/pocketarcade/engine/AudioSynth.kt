package com.pocketarcade.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.pocketarcade.engine.audio.Attract
import com.pocketarcade.engine.audio.MixEngine
import com.pocketarcade.engine.audio.Music
import com.pocketarcade.engine.audio.MusicScene
import com.pocketarcade.engine.audio.Room
import com.pocketarcade.engine.audio.Tracks
import com.pocketarcade.hub.HallSoundSink

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

    // The café's steam wand (played from the café, see `setCafe`).
    STEAM,
}

/**
 * The game's sound: synthesized sound effects, a procedural hall ambience and (from `audio/`)
 * positional voices, room reverb and a music engine, mixed by [MixEngine] on a dedicated thread
 * into a low-latency stereo [AudioTrack]. Nothing is loaded from a file.
 *
 * Any thread may call [play] and [playAt]; the rest of the settings and the listener are plain
 * volatile fields, so the UI thread can update them every frame.
 */
class AudioSynth : HallSoundSink {
    private val sampleRate: Int = try {
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it in 16000..96000 } ?: 44100
    } catch (_: Exception) {
        44100
    }

    private val engine = MixEngine(sampleRate)
    private val runLock = Object()

    @Volatile private var running = true
    @Volatile private var active = false

    /** Silences everything (the player's mute button). */
    var muted: Boolean
        get() = engine.muted
        set(v) {
            engine.muted = v
        }

    /** Target loudness of the arcade ambience (0 = silent). Smoothly approached by the mixer. */
    var ambientTarget: Float
        get() = engine.ambientTarget
        set(v) {
            engine.ambientTarget = v
        }

    /** The player's volume settings: master multipliers on every sound effect, and on the ambience (with its bleeps). */
    var sfxVolume: Float
        get() = engine.sfxVolume
        set(v) {
            engine.sfxVolume = v
        }
    var ambienceVolume: Float
        get() = engine.ambienceVolume
        set(v) {
            engine.ambienceVolume = v
        }

    /** The music volume as a gain (0 = off), beside [sfxVolume] and [ambienceVolume]. */
    var musicVolume: Float
        get() = engine.music.volume
        set(v) {
            engine.music.volume = v
        }

    /**
     * The soundtrack: scenes, intensity, stingers and ducking (see [Music]). Silenced by [muted] and
     * [musicVolume]. Most callers want [enterScene], which also moves the room reverb.
     */
    val music: Music get() = engine.music

    /**
     * Goes to a screen's sound: the theme for [scene] (crossfading) and the room to match (the title's
     * big glossy space, the hall's, or the dry close room inside a game and its results).
     */
    fun enterScene(scene: MusicScene) {
        engine.music.setScene(scene)
        room = when (scene) {
            MusicScene.Title -> Room.TITLE
            MusicScene.Hall -> Room.HALL
            else -> Room.GAME
        }
    }

    /**
     * The room the sound effects sound in (hall, inside a game, the title). Changing it
     * crossfades over about a second.
     */
    var room: Room
        get() = engine.reverb.room
        set(v) {
            engine.reverb.room = v
        }

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

    /** Plays [sfx] from the middle of the stereo field; [pitch] scales playback speed, so 2.0 is an octave up. */
    fun play(sfx: Sfx, volume: Float = 1f, pitch: Float = 1f) = engine.play(sfx, volume, pitch)

    /**
     * Plays [sfx] from the world point ([x], [z]) as heard from the [setListener] position: panned
     * left or right by where it is, quieter and wetter the further away, silent (and free) when
     * out of earshot.
     */
    override fun playAt(sfx: Sfx, x: Float, z: Float, volume: Float, pitch: Float) =
        engine.playAt(sfx, x, z, volume, pitch)

    /**
     * Puts the listener's ears at world point ([x], [z]) facing [yawRad] (0 faces +z, the
     * entrance; the same yaw as the hall's first-person camera). Cheap: call it every frame.
     */
    override fun setListener(x: Float, z: Float, yawRad: Float) = engine.setListener(x, z, yawRad)

    /**
     * Tells the ambience where the machines are, so their attract-mode bleeps come from the
     * cabinets: [count] cabinets at ([xs], [zs]), each with the [Attract.kindFor] palette of its
     * game. The arrays are copied.
     */
    override fun setHallSources(xs: FloatArray, zs: FloatArray, kinds: IntArray, count: Int) =
        engine.ambience.setSources(xs, zs, kinds, count)

    /** Tells the ambience where the café counter is (its steam wand and cups sound from there). */
    override fun setCafe(x: Float, z: Float) = engine.ambience.setCafe(x, z)

    /** How busy the hall is around the player, 0..1: the crowd murmur swells with it. */
    override fun setCrowd(level: Float) {
        engine.ambience.crowd = level.coerceIn(0f, 1f)
    }

    override fun setMusicIntensity(level: Float) = engine.music.setIntensity(level)

    /** The synthesized samples of [sfx] once [generateAll] has run (for tests). */
    internal fun samples(sfx: Sfx): FloatArray? = engine.bank.samples(sfx)

    internal fun generateAll() = engine.bank.generateAll()

    // ---------------------------------------------------------------- mixer thread

    private fun runMixer() {
        try {
            generateAll()
        } catch (_: Exception) {
            return
        }
        // Build every theme now, before the track is playing, so a first visit to the hall or a machine
        // never stalls the mixer in the middle of a block. A bad score costs its music, not the sound.
        try {
            Tracks.warmUp()
        } catch (_: Exception) {
        }
        val track = try {
            buildTrack()
        } catch (_: Exception) {
            null
        } ?: return

        val out = ShortArray(MixEngine.BLOCK * 2)
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
                engine.render(out)
                track.write(out, 0, out.size)
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
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, MixEngine.BLOCK * 2 * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }
}
