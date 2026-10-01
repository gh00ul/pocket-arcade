package com.pocketarcade.godot

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import java.io.File

/**
 * The platform calls Pocket Arcade's Godot port needs, each one build-13 made from its Activity,
 * views or engine (see the Godot project's AndroidBridge): VibrationEffect haptics, edge-swipe
 * exclusion, Back to the background, the FileProvider share sheet, the launch intent's "play"
 * extra, the raw touch stream, the tilt sensor, and build-13's soundtrack ([MusicHost]).
 */
class PocketArcadePlugin(godot: Godot) : GodotPlugin(godot) {
    companion object {
        /** Intent extra naming a machine id to walk straight into (build-13's MainActivity.EXTRA_PLAY). */
        const val EXTRA_PLAY = "play"

        /** The FileProvider's authority suffix (build-13's PhotoShare.AUTHORITY_SUFFIX). */
        const val AUTHORITY_SUFFIX = ".photos"
        const val MIME = "image/png"

        // VibrationEffect.Composition primitive ids (also in Haptics.gd).
        private const val CLICK = 1
        private const val THUD = 2
        private const val QUICK_RISE = 4
        private const val TICK = 7
        private const val LOW_TICK = 8
    }

    private val touches = TouchRecorder()
    private val music = MusicHost()
    private var tilt: TiltReader? = null

    override fun getPluginName(): String = "PocketArcade"

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        music.start()
        runOnUiThread {
            godot.renderView?.view?.setOnTouchListener(touches)
        }
    }

    override fun onMainResume() {
        super.onMainResume()
        music.setActive(true)
    }

    override fun onMainPause() {
        music.setActive(false)
        tilt?.stop()
        super.onMainPause()
    }

    override fun onMainDestroy() {
        music.release()
        tilt?.stop()
        super.onMainDestroy()
    }

    // ------------------------------------------------------------------ haptics

    private val vibrator: Vibrator? by lazy {
        try {
            val context: Context = activity ?: return@lazy null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /** [vibrator present, amplitude control, click/tick/quick rise, thud, low tick] as 0/1. */
    @UsedByGodot
    fun hapticsInfo(): IntArray {
        val v = vibrator ?: return intArrayOf(0, 0, 0, 0, 0)
        val amplitude = try {
            v.hasAmplitudeControl()
        } catch (_: Exception) {
            false
        }
        val basics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supported(v, CLICK, TICK, QUICK_RISE)
        val thud = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supported(v, THUD)
        val lowTick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supported(v, LOW_TICK)
        return intArrayOf(1, if (amplitude) 1 else 0, if (basics) 1 else 0, if (thud) 1 else 0, if (lowTick) 1 else 0)
    }

    private fun supported(v: Vibrator, vararg ids: Int): Boolean = try {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v.areAllPrimitivesSupported(*ids)
    } catch (_: Exception) {
        false
    }

    /** A buzz of [ms] at [amplitude] (1..255, or -1 for the vibrator's default). */
    @UsedByGodot
    fun vibrateOneShot(ms: Int, amplitude: Int) {
        val v = vibrator ?: return
        try {
            start(v, VibrationEffect.createOneShot(ms.toLong(), if (amplitude < 0) VibrationEffect.DEFAULT_AMPLITUDE else amplitude))
        } catch (_: Exception) {
        }
    }

    /** A waveform of off/on [timings]; empty [amplitudes] for a vibrator without amplitude control. */
    @UsedByGodot
    fun vibrateWaveform(timings: IntArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        try {
            val ms = LongArray(timings.size) { timings[it].toLong() }
            val effect = if (amplitudes.isEmpty()) {
                VibrationEffect.createWaveform(ms, -1)
            } else {
                VibrationEffect.createWaveform(ms, amplitudes, -1)
            }
            start(v, effect)
        } catch (_: Exception) {
        }
    }

    /** Composed primitives (API 30+): ids, scales 0..1, delays in ms. */
    @UsedByGodot
    fun vibrateComposition(prims: IntArray, scales: FloatArray, delays: IntArray) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val c = VibrationEffect.startComposition()
            for (i in prims.indices) {
                c.addPrimitive(prims[i], scales.getOrElse(i) { 1f }.coerceIn(0f, 1f), delays.getOrElse(i) { 0 })
            }
            start(v, c.compose())
        } catch (_: Exception) {
        }
    }

    /** Plays [effect], as touch feedback from API 33 so the system's setting for it applies. */
    private fun start(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, TouchUsage.attributes)
        } else {
            v.vibrate(effect)
        }
    }

    // ------------------------------------------------------------------ the window and the app

    /** Keeps the Back swipe away from these view rectangles (x0 y0 x1 y1 each, pixels); API 29+. */
    @UsedByGodot
    fun setGestureExclusion(rects: IntArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val list = ArrayList<Rect>()
        var i = 0
        while (i + 3 < rects.size) {
            list += Rect(rects[i], rects[i + 1], rects[i + 2], rects[i + 3])
            i += 4
        }
        runOnUiThread {
            godot.renderView?.view?.systemGestureExclusionRects = list
        }
    }

    /** Sends the app to the background, as Back at the title or in the hall did in build-13. */
    @UsedByGodot
    fun moveTaskToBack() {
        runOnUiThread {
            activity?.moveTaskToBack(true)
        }
    }

    /** The share sheet for the PNG at [path] (which must be in files/photos); false if it couldn't open. */
    @UsedByGodot
    fun sharePng(path: String, title: String): Boolean {
        val act = activity ?: return false
        return try {
            val file = File(path)
            if (!file.isFile) return false
            val uri = FileProvider.getUriForFile(act, act.packageName + AUTHORITY_SUFFIX, file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                // The clip is what the grant follows through the chooser to whichever app is picked.
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, title).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runOnUiThread {
                try {
                    act.startActivity(chooser)
                } catch (_: Exception) {
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The machine id the app was launched (or brought back) to play, once; "" when there is none. */
    @UsedByGodot
    fun takeLaunchGame(): String {
        val intent = activity?.intent ?: return ""
        val id = intent.getStringExtra(EXTRA_PLAY) ?: return ""
        intent.removeExtra(EXTRA_PLAY)
        return id
    }

    // ------------------------------------------------------------------ touch

    @UsedByGodot
    fun touchCapture(on: Boolean) {
        if (on) touches.start() else touches.stop()
    }

    @UsedByGodot
    fun touchUnbuffered(on: Boolean) {
        touches.unbuffered = on
    }

    @UsedByGodot
    fun touchTake(): IntArray = touches.take()

    // ------------------------------------------------------------------ tilt

    @UsedByGodot
    fun tiltStart(): Int {
        val context = activity ?: return TiltReader.NONE
        val reader = tilt ?: TiltReader(context).also { tilt = it }
        return reader.start()
    }

    @UsedByGodot
    fun tiltStop() {
        tilt?.stop()
    }

    @UsedByGodot
    fun tiltTake(): FloatArray = tilt?.take() ?: FloatArray(0)

    // ------------------------------------------------------------------ music

    @UsedByGodot
    fun musicSetScene(scene: String) = music.setScene(scene)

    @UsedByGodot
    fun musicSetIntensity(level: Float) = music.music.setIntensity(level)

    @UsedByGodot
    fun musicSetQuiet(on: Boolean) = music.music.setQuiet(on)

    @UsedByGodot
    fun musicStinger(which: Int) = music.stinger(which)

    @UsedByGodot
    fun musicDuck(depth: Float, hold: Float) = music.music.duck(depth, hold)

    @UsedByGodot
    fun musicSetVolume(volume: Float) {
        music.music.volume = volume
        music.poke()
    }

    @UsedByGodot
    fun musicSetMuted(muted: Boolean) {
        music.music.muted = muted
        music.poke()
    }

    @UsedByGodot
    fun musicSetActive(active: Boolean) = music.setActive(active)
}

/** The touch usage's attributes, in their own object so an older phone never loads them. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object TouchUsage {
    val attributes: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
}
