package com.pocketarcade.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.core.graphics.withTranslation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.games.MiniGame
import com.pocketarcade.hub.TitleShowcase
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// Layout, as shares of the screen height.
private const val LOGO_TOP = 0.085f
private const val PROMPT_Y = 0.79f
private const val SAVE_Y = 0.862f
private const val WELCOME_Y = 0.905f
private const val TIP_Y = 0.945f

/** The stars keep to the sky above the sign's stage; this share of the height at most, so none shows over a cabinet. */
private const val STAR_BAND = 0.26f
private const val STAR_COUNT = 70

/** How far the stars slide across the screen per radian the camera turns (in screen widths). */
private const val STAR_PARALLAX = 0.9f

private const val EXTRUSION_LAYERS = 5

/** How bright an unlit tube's face is drawn, as a share of full. */
private const val FACE_UNLIT = 0.22f

/** Fraction of the screen the small print may fill before it shrinks to fit. */
private const val TEXT_FIT = 0.92f

private val DustWarm = Color(0xFFFFE9C8)
private val DustCool = Color(0xFFCDBBFF)

/** One word of the neon sign, laid out once per size: its letters, where each starts, and its face gradient. */
private class NeonWord(val word: String, val color: Int, val core: Int, val index: Int, val start: Float) {
    val letters: Array<String> = Array(word.length) { word.substring(it, it + 1) }
    val offset = FloatArray(word.length)
    var total = 0f
        private set
    var face: LinearGradient? = null
        private set
    private var laidOutFor = -1f

    fun layout(paint: Paint, size: Float) {
        if (size == laidOutFor) return
        laidOutFor = size
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Fonts.display
        paint.textSize = size
        paint.letterSpacing = 0.02f
        var x = 0f
        for (i in letters.indices) {
            offset[i] = x
            x += paint.measureText(letters[i])
        }
        total = x
        // Bright core at the top of the capitals, the tube's colour towards the baseline (letters are drawn at the origin).
        face = LinearGradient(0f, -size * 0.72f, 0f, 0f, core, color, Shader.TileMode.CLAMP)
    }
}

private class TitleState {
    var time = 0f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pocket = NeonWord("POCKET", 0xFF39E6F2.toInt(), 0xFFE8FFFF.toInt(), 0, TitleTimeline.POCKET_START)
    val arcade = NeonWord("ARCADE", 0xFFFFC83D.toInt(), 0xFFFFF6D0.toInt(), 1, TitleTimeline.ARCADE_START)

    /** The sign's size (px) everything was last laid out for. */
    var logoSize = 0f
        private set
    private var widthFor = -1f

    /** The light sweep: a soft white band, tilted, moved along each letter with [sweepMatrix]. */
    var sweep: LinearGradient? = null
        private set
    var sweepBand = 0f
        private set
    val sweepMatrix = Matrix()

    // Scrims behind the sign and the small print, so both read over any picture.
    var topScrim: Brush? = null
        private set
    var bottomScrim: Brush? = null
        private set
    private var scrimFor = -1f

    /** The biggest sign size at which the longer word still fits comfortably across [w]; lays everything out. */
    fun layout(w: Float, h: Float) {
        if (w == widthFor && h == scrimFor) return
        widthFor = w
        scrimFor = h
        paint.reset()
        paint.typeface = Fonts.display
        paint.textSize = 100f
        paint.letterSpacing = 0.02f
        val widest = maxOf(paint.measureText("POCKET"), paint.measureText("ARCADE"))
        logoSize = min(w * 0.25f, w * 0.86f / widest * 100f)
        pocket.layout(paint, logoSize)
        arcade.layout(paint, logoSize)
        sweepBand = logoSize * 0.9f
        sweep = LinearGradient(
            0f, 0f, sweepBand, 0f,
            intArrayOf(0x00FFFFFF, 0xC0FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        val logoBottom = h * LOGO_TOP + logoSize * 2.5f
        topScrim = Brush.verticalGradient(listOf(Color(0xB0030208), Color(0x00030208)), startY = 0f, endY = logoBottom)
        bottomScrim = Brush.verticalGradient(listOf(Color(0x0005030C), Color(0xD005030C)), startY = h * 0.68f, endY = h)
    }
}

/**
 * The title: a neon sign lighting up over a night sky, and behind it the showroom, a full-screen 3D
 * stage where the camera glides past the hall's machines under spotlights ([TitleShowcase]). Dust
 * drifts in the air, the prompt pulses, and a tap starts ([onStart]); while the hall hands over,
 * [exit] (0..1, read every frame) carries the sign away and pushes the camera in. With [reduceMotion]
 * the sign fades in as one, the camera holds a single shot and nothing flickers, sweeps or drifts.
 */
@Composable
fun TitleScreen(
    save: SaveState,
    games: List<MiniGame>,
    reduceMotion: Boolean = false,
    exit: () -> Float = { 0f },
    onStart: () -> Unit,
) {
    val state = remember { TitleState() }
    val start by rememberUpdatedState(onStart)
    val showcase = remember(games) { TitleShowcase(games) }
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    val welcome = remember(save.loaded, save.tickets, save.totalPlays, save.collection, save.owned) { TitleCopy.welcome(save) }
    val scoreOf = remember(save) { { id: String -> save.highScore(id) } }
    DisposableEffect(showcase) {
        onDispose { Gfx.remove(TitleShowcase.SLOT) }
    }
    val frame = rememberGameLoop(state) { dt ->
        state.time += dt
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { start() }) }
            .semantics {
                contentDescription = "Pocket Arcade. Tap anywhere to start."
                role = Role.Button
                onClick(label = "Start") {
                    start()
                    true
                }
            },
    ) {
        frame.value
        val t = state.time
        val calm = reduceMotion
        val e = exit().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        state.layout(w, h)

        showcase.draw(w.toInt(), h.toInt(), t, e, calm, scoreOf)
        drawRect(state.topScrim!!, Offset.Zero, Size(w, h * 0.5f))
        drawRect(state.bottomScrim!!, Offset(0f, h * 0.68f), Size(w, h * 0.32f))

        val uiA = TitleTimeline.uiExitAlpha(e)
        drawStars(t, calm, w, showcase.wallTopY, showcase.yaw, 1f - smoothstep(0.3f, 0.8f, e))
        drawDust(t, calm, w, h, showcase.eyeX, e)

        // The sign.
        val logoSize = state.logoSize
        val logoTop = h * LOGO_TOP
        val logoA = TitleTimeline.logoExitAlpha(e)
        if (logoA > 0.004f) {
            val canvas = drawContext.canvas.nativeCanvas
            val grow = TitleTimeline.logoExitScale(e)
            canvas.withTranslation(0f, TitleTimeline.logoExitLift(e) * h) {
                scale(grow, grow, w / 2f, logoTop + logoSize * 1.2f)
                neonWord(this, state, state.pocket, w / 2f, logoTop + logoSize, t, calm, logoA)
                neonWord(this, state, state.arcade, w / 2f, logoTop + logoSize * 2.08f, t, calm, logoA)
                val tag = w * 0.0072f
                ArcadeFont.drawCentered(this@Canvas, "A WHOLE ARCADE HALL IN YOUR POCKET", w / 2f, logoTop + logoSize * 2.3f, tag, Color(0xFFD9C8FF), TitleTimeline.taglineAlpha(t) * logoA, tiny = true)
            }
        }

        // The small print. Everything below leaves quickly on the tap.
        val unit = w * 0.0095f
        val promptA = TitleTimeline.promptAlpha(t, calm) * uiA
        if (promptA > 0.004f) {
            val py = h * PROMPT_Y
            val s = TitleTimeline.promptScale(t, calm)
            withTransform({ scale(s, s, Offset(w / 2f, py + ArcadeFont.height(unit) / 2f)) }) {
                ArcadeFont.drawCentered(this, "TAP TO START", w / 2f, py, unit, Color.White, promptA)
            }
        }
        // The rest of the small print arrives with the prompt.
        val fadeIn = smoothstep(TitleTimeline.PROMPT_START, TitleTimeline.PROMPT_START + TitleTimeline.PROMPT_FADE, t) * uiA
        if (save.loaded) {
            val line = "${ArcadeFont.TOKEN} ${save.tokens} TOKENS      ${ArcadeFont.TICKET} ${save.tickets} TICKETS"
            ArcadeFont.drawCentered(this, line, w / 2f, h * SAVE_Y, fit(line, unit * 0.62f, w, false), Color(0xFFFFCF5A), fadeIn)
        }
        if (welcome.isNotEmpty()) {
            ArcadeFont.drawCentered(this, welcome, w / 2f, h * WELCOME_Y, fit(welcome, unit * 0.5f, w, true), Color(0xFFC9B8FF), fadeIn * 0.9f, tiny = true)
        }
        ArcadeFont.drawCentered(this, "BEST WITH SOUND ON ${ArcadeFont.NOTE}", w / 2f, h * TIP_Y, unit * 0.48f, Color(0xFF9A90B8), fadeIn, tiny = true)
        if (version.isNotEmpty()) {
            val vu = unit * 0.4f
            val margin = w * 0.035f
            ArcadeFont.draw(this, version, w - margin - ArcadeFont.width(version, vu, true), h - margin - ArcadeFont.height(vu, true), vu, Color(0xFF6E6690), fadeIn * 0.85f, tiny = true)
        }
    }
}

/** [unit] shrunk (never grown) so [text] is no wider than [TEXT_FIT] of [w]. */
private fun fit(text: String, unit: Float, w: Float, tiny: Boolean): Float {
    val width = ArcadeFont.width(text, unit, tiny)
    val limit = w * TEXT_FIT
    return if (width > limit && width > 0f) unit * limit / width else unit
}

/** The app's version for the corner stamp ("V1.0.42"), or empty if the system won't say. */
private fun appVersion(context: Context): String = try {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName?.let { "V$it" } ?: ""
} catch (_: Exception) {
    ""
}

/**
 * Twinkling stars over the stage's sky. The camera turning slides them sideways (they are at
 * infinity, so nothing else moves them), a touch faster or slower each for depth; with [calm] they
 * hold still. [fade] takes them away as the title leaves.
 */
private fun DrawScope.drawStars(t: Float, calm: Boolean, w: Float, skyBottom: Float, yaw: Float, fade: Float) {
    if (fade <= 0.004f) return
    val band = min(skyBottom - 6f, size.height * STAR_BAND)
    if (band <= 8f) return
    val turn = if (calm) 0f else -yaw * STAR_PARALLAX
    for (i in 0 until STAR_COUNT) {
        val depth = 0.6f + 0.4f * hash01(i, 4)
        val u = hash01(i, 1) + turn * depth
        val sx = (u - floor(u)) * w
        val sy = hash01(i, 2) * band
        val tw = if (calm) 0.8f else 0.3f + 0.7f * abs(sin(t * (1f + hash01(i, 3) * 2f) + i))
        val r = if (i % 7 == 0) w * 0.0035f else w * 0.002f
        drawCircle(Color.White, r * 2.5f, Offset(sx, sy), alpha = 0.08f * tw * fade)
        drawCircle(Color.White, r, Offset(sx, sy), alpha = 0.7f * tw * fade)
    }
}

/** The dust motes between the lens and the machines; see [DustField]. */
private fun DrawScope.drawDust(t: Float, calm: Boolean, w: Float, h: Float, camX: Float, exit: Float) {
    val fade = 1f - smoothstep(0.7f, 1f, exit)
    if (fade <= 0.004f) return
    for (i in 0 until DustField.COUNT) {
        val x = DustField.stream(DustField.x(i, t, camX, calm), i, exit) * w
        val y = DustField.stream(DustField.y(i, t, calm), i, exit) * h
        if (x < -20f || x > w + 20f || y < -20f || y > h + 20f) continue
        val a = DustField.alpha(i, t, calm) * fade
        val r = DustField.radius(i) * w
        val c = if (i % 3 == 0) DustCool else DustWarm
        drawCircle(c, r * 3f, Offset(x, y), alpha = a * 0.22f)
        drawCircle(c, r, Offset(x, y), alpha = a)
    }
}

/**
 * One word of the neon sign: an extruded shadow, a coloured glow and a bright gradient face on
 * each letter, lit in turn (see [TitleTimeline.letterOn]), bobbing gently, with the odd buzz and a
 * light sweep across the face. Nothing here allocates: the letters, gradients and matrix are made
 * once by [NeonWord.layout], and each letter is drawn at the origin of a translated canvas.
 */
private fun neonWord(canvas: android.graphics.Canvas, st: TitleState, wd: NeonWord, cx: Float, baseline: Float, t: Float, calm: Boolean, alphaMul: Float) {
    val paint = st.paint
    val size = st.logoSize
    paint.reset()
    paint.isAntiAlias = true
    paint.typeface = Fonts.display
    paint.textSize = size
    paint.letterSpacing = 0.02f
    val left = cx - wd.total / 2f
    val n = wd.letters.size
    val breath = TitleTimeline.breath(t, calm)
    val sweep = TitleTimeline.sweep(t, calm)
    val extrude = 0xFF3A1454.toInt()
    for (i in 0 until n) {
        val on = (TitleTimeline.letterOn(t, wd.start, i, calm) * TitleTimeline.buzz(t, i, n, wd.index, calm)).coerceIn(0f, 1f)
        val bob = if (calm) 0f else sin(t * 2.6f + i * 0.6f + wd.index * 1.7f) * size * 0.035f * on
        val ch = wd.letters[i]
        canvas.withTranslation(left + wd.offset[i], baseline + bob) {
            // Extrusion: solid, so it shows even on an unlit tube.
            paint.shader = null
            paint.clearShadowLayer()
            paint.style = Paint.Style.FILL
            for (k in EXTRUSION_LAYERS downTo 1) {
                paint.color = Pal.shade(extrude, 1f - k * 0.09f)
                paint.alpha = (255f * alphaMul).toInt()
                drawText(ch, k * size * 0.012f, k * size * 0.016f, paint)
            }
            // Glow: only from a lit tube, breathing slowly.
            if (on > 0.03f) {
                val glow = on * alphaMul
                paint.color = wd.color
                paint.alpha = (200f * glow).toInt()
                paint.setShadowLayer(size * 0.22f * breath * (0.4f + 0.6f * on), 0f, 0f, (wd.color and 0xFFFFFF) or ((255f * glow).toInt() shl 24))
                drawText(ch, 0f, 0f, paint)
                paint.clearShadowLayer()
            }
            // Face: a dim ghost of the letter until it catches.
            paint.shader = wd.face
            paint.alpha = (255f * (FACE_UNLIT + (1f - FACE_UNLIT) * on) * alphaMul).toInt()
            drawText(ch, 0f, 0f, paint)
            // The sweep: a tilted band of light that slides across the whole word.
            if (sweep >= 0f && on > 0.95f) {
                val centre = sweep * (wd.total + size * 1.2f) - size * 0.6f
                st.sweepMatrix.setRotate(-16f, st.sweepBand / 2f, 0f)
                st.sweepMatrix.postTranslate(centre - wd.offset[i] - st.sweepBand / 2f, 0f)
                st.sweep!!.setLocalMatrix(st.sweepMatrix)
                paint.shader = st.sweep
                paint.alpha = (255f * alphaMul).toInt()
                drawText(ch, 0f, 0f, paint)
            }
            paint.shader = null
        }
    }
}
