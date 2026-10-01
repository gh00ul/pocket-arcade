package com.pocketarcade.godot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.pocketarcade.engine.audio.Music
import com.pocketarcade.engine.audio.MusicScene
import com.pocketarcade.engine.audio.Stinger
import com.pocketarcade.engine.audio.Tracks

/**
 * build-13's soundtrack, played the way build-13's AudioSynth played it: [Music] rendered a block at
 * a time on a dedicated thread into a low-latency stereo AudioTrack, through the same master gain
 * and soft clip. The sound effects play in Godot; only the music is here (see the Godot project's
 * MusicControl for why). Every control call only sets state the thread reads once a block.
 */
internal class MusicHost {
    companion object {
        /** Frames per block: 10 ms at 48 kHz (build-13's MixEngine.BLOCK). */
        const val BLOCK = 480

        /** build-13's master gain before its soft clip, and the clip's ceiling as a 16-bit value. */
        const val MASTER = 0.85f
        const val FULL_SCALE = 32000f

        /** Below this a volume counts as off (build-13's Music.OFF). */
        const val OFF = 0.0005f

        /** The scene for a Godot scene key: "" silence, "title", "hall", "results", "game:<id>". */
        fun sceneFor(key: String): MusicScene = when {
            key == "title" -> MusicScene.Title
            key == "hall" -> MusicScene.Hall
            key == "results" -> MusicScene.Results
            key.startsWith("game:") -> MusicScene.Game(key.removePrefix("game:"))
            else -> MusicScene.Silence
        }

        /** build-13's MixEngine.limit: the master gain and a cubic soft clip down to 16 bits. */
        fun limit(sample: Float): Short {
            var x = (sample * MASTER).coerceIn(-1.5f, 1.5f)
            x -= x * x * x / 6.75f
            return (x * FULL_SCALE).toInt().toShort()
        }
    }

    private val sampleRate: Int = try {
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it in 16000..96000 } ?: 44100
    } catch (_: Exception) {
        44100
    }

    val music = Music(sampleRate)

    @Volatile private var running = true
    @Volatile private var active = true
    private val runLock = Object()
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ run() }, "ArcadeMusic").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun setActive(value: Boolean) {
        active = value
        synchronized(runLock) { runLock.notifyAll() }
    }

    /** Music volume or mute changed: wake the thread so it can start (or settle) at once. */
    fun poke() {
        synchronized(runLock) { runLock.notifyAll() }
    }

    fun release() {
        running = false
        synchronized(runLock) { runLock.notifyAll() }
    }

    fun setScene(key: String) = music.setScene(sceneFor(key))

    fun stinger(which: Int) {
        val all = Stinger.entries
        if (which in all.indices) music.stinger(all[which])
    }

    private fun audible(): Boolean = active && !music.muted && music.volume > OFF

    private fun run() {
        // Build every theme before playing, so a first visit never stalls a block.
        try {
            Tracks.warmUp()
        } catch (_: Exception) {
        }
        val track = try {
            buildTrack()
        } catch (_: Exception) {
            null
        } ?: return
        val left = FloatArray(BLOCK)
        val right = FloatArray(BLOCK)
        val out = ShortArray(BLOCK * 2)
        var playing = false
        try {
            while (running) {
                if (!audible()) {
                    // Muted or at zero volume: let the music shut itself down (it restarts the theme when
                    // it is back). Merely paused: keep its place, as build-13 did. Then sleep.
                    if (music.muted || music.volume <= OFF) music.render(left, right, BLOCK)
                    if (playing) {
                        track.pause()
                        track.flush()
                        playing = false
                    }
                    synchronized(runLock) {
                        while (!audible() && running) runLock.wait(500)
                    }
                    continue
                }
                if (!playing) {
                    track.play()
                    playing = true
                }
                java.util.Arrays.fill(left, 0f)
                java.util.Arrays.fill(right, 0f)
                music.render(left, right, BLOCK)
                for (i in 0 until BLOCK) {
                    out[2 * i] = limit(left[i])
                    out[2 * i + 1] = limit(right[i])
                }
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
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, BLOCK * 2 * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }
}
