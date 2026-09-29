package com.pocketarcade.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** Pure numbers of the loading screen's animation, so the tests can check them. */
object LoadingLook {
    /** Seconds the screen takes to fade out once the load is done. */
    const val FADE_OUT = 0.42f

    /** Seconds each tip stays up. */
    const val TIP_SECONDS = 3.2f

    /** How fast the bar's eased progress chases the real one (per second), with motion and without. */
    const val CHASE = 9f
    const val CHASE_CALM = 30f

    val TIPS = listOf(
        "TAP A MACHINE TO WALK RIGHT UP TO IT",
        "THE EYE BUTTON PUTS YOU IN A KID'S SHOES",
        "40 TICKETS BUY A TOKEN AT THE KIOSK",
        "WIN PLUSHIES FROM THE CLAW MACHINE",
        "THE MAP BUTTON FINDS ANY MACHINE",
        "SNAP A PHOTO STRIP IN THE BOOTH",
        "PUSH THE STICK TO ITS RIM TO RUN",
    )

    /** The bar's eased progress one frame on: chases [target] at [rate] per second, and never goes down. */
    fun ease(shown: Float, target: Float, dt: Float, rate: Float): Float {
        val next = shown + (target.coerceIn(0f, 1f) - shown) * (1f - exp(-rate * dt.coerceAtLeast(0f)))
        return max(shown, next).coerceIn(0f, 1f)
    }

    /** Which tip is up at [time] seconds (the first one for good when [motion] is off), and how visible it is (fades at each end). */
    fun tipIndex(time: Float, motion: Boolean, count: Int): Int = if (!motion || count <= 0) 0 else ((time / TIP_SECONDS).toInt()) % count

    fun tipAlpha(time: Float, motion: Boolean): Float {
        if (!motion) return 1f
        val phase = (time % TIP_SECONDS) / TIP_SECONDS
        return (minOf(phase / 0.12f, (1f - phase) / 0.12f, 1f)).coerceIn(0f, 1f)
    }
}

/** True when the phone's own "remove animations" (animator duration scale 0) is on. */
@Composable
private fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * The loading screen: the arcade's night sky and neon sign, a spinning token and a glowing
 * progress bar that names what is being made ("LAYING THE CARPET"), with a tip underneath. It
 * covers everything (and takes the touches) while [active]; when the load is done it fills the
 * bar, then fades away over [LoadingLook.FADE_OUT] and takes no space or time afterwards.
 *
 * It keeps moving on its own clock while the loader works a slice a frame on the same thread.
 * With [reduceMotion] (or the system's animations off) nothing loops: the token faces front, the
 * stars hold still, no shimmer runs along the bar, the tip stays put and the bar moves in short
 * steps to where the progress is.
 */
@Composable
fun LoadingScreen(active: Boolean, progress: Float, label: String, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val motion = !reduceMotion && !systemAnimationsOff()
    val latest by rememberUpdatedState(progress)
    val motionNow by rememberUpdatedState(motion)
    val activeNow by rememberUpdatedState(active)
    var time by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var gone by remember { mutableStateOf(false) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    LaunchedEffect(active) {
        if (active) {
            // Shown again for a new load: start the bar over, at full strength.
            alpha = 1f
            gone = false
            shown = 0f
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                if (motionNow) time += dt
                val target = if (activeNow) latest else 1f
                shown = LoadingLook.ease(shown, target, dt, if (motionNow) LoadingLook.CHASE else LoadingLook.CHASE_CALM)
                if (!activeNow) alpha = (alpha - dt / LoadingLook.FADE_OUT).coerceAtLeast(0f)
            }
            if (!activeNow && alpha <= 0f) break
        }
        gone = true
    }
    if (gone && !active) return

    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            // Nothing underneath is reachable while this is up.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .semantics { contentDescription = "Loading Pocket Arcade" },
    ) {
        drawLoading(paint, time, shown, label, motion)
    }
}

private fun DrawScope.drawLoading(paint: Paint, t: Float, shown: Float, label: String, motion: Boolean) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05030C), Color(0xFF1A0F36), Color(0xFF3A1650)), endY = h * 0.75f), Offset.Zero, Size(w, h))

    // Stars: twinkling with motion, a still sky without.
    for (i in 0 until 70) {
        val sx = hash01(i, 1) * w
        val sy = hash01(i, 2) * h * 0.62f
        val tw = if (motion) 0.3f + 0.7f * abs(sin(t * (1f + hash01(i, 3) * 2f) + i)) else 0.7f
        val r = if (i % 7 == 0) w * 0.0035f else w * 0.002f
        drawCircle(Color.White, r * 2.5f, Offset(sx, sy), alpha = 0.08f * tw)
        drawCircle(Color.White, r, Offset(sx, sy), alpha = 0.7f * tw)
    }
    // The synthwave floor's horizon glow, the same as under the title.
    val horizon = h * 0.62f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF4FA8).copy(alpha = 0f), Color(0xFFFF4FA8).copy(alpha = 0.16f)), startY = horizon - h * 0.12f, endY = horizon), Offset(0f, horizon - h * 0.12f), Size(w, h * 0.12f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A0F3E), Color(0xFF0B0616)), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
    val scroll = if (motion) (t * 0.5f) % 1f else 0f
    for (i in 0 until 10) {
        val z = i + 1 - scroll
        if (z <= 0.2f) continue
        val y = horizon + (h - horizon) * (1f / z) * 0.9f
        if (y < horizon || y > h) continue
        drawLine(Color(0xFFFF4FA8), Offset(0f, y), Offset(w, y), strokeWidth = 2f, alpha = 0.06f + 0.22f * ((y - horizon) / (h - horizon)))
    }
    for (k in -6..6) {
        val bx = w / 2f + k * w / 6f
        drawLine(Color(0xFFFF4FA8), Offset(w / 2f + k * w / 60f, horizon), Offset(bx + (bx - w / 2f) * 1.5f, h), strokeWidth = 2f, alpha = 0.22f)
    }

    // The sign.
    val logoSize = minOf(w * 0.2f, w * 0.78f / 3.4f)
    val logoTop = h * 0.13f
    neonWord(paint, "POCKET", w / 2f, logoTop + logoSize, logoSize, 0xFF39E6F2.toInt(), 0xFFE8FFFF.toInt(), t, 0f, motion)
    neonWord(paint, "ARCADE", w / 2f, logoTop + logoSize * 2.08f, logoSize, 0xFFFFC83D.toInt(), 0xFFFFF6D0.toInt(), t, 1.7f, motion)

    // The token, spinning on its edge (facing front when calm).
    val coinR = w * 0.075f
    val coinY = h * 0.5f
    drawCoin(w / 2f, coinY, coinR, if (motion) cos(t * 2.6f) else 1f, if (motion) sin(t * 2.6f) else 0f)

    // The bar: a dark track, a glowing fill with a gloss, and a shimmer sliding along it.
    val barW = w * 0.72f
    val barH = w * 0.05f
    val barX = (w - barW) / 2f
    val barY = h * 0.68f
    val radius = CornerRadius(barH / 2f)
    drawRoundRect(Color(0xFF0B0616), Offset(barX - 3f, barY - 3f), Size(barW + 6f, barH + 6f), CornerRadius(barH / 2f + 3f))
    drawRoundRect(Color(0xFF241A44), Offset(barX, barY), Size(barW, barH), radius)
    val fillW = (barW * shown).coerceIn(0f, barW)
    if (fillW > barH * 0.6f) {
        val glow = Color(Pal.PINK)
        drawRoundRect(glow.copy(alpha = 0.22f), Offset(barX - barH * 0.25f, barY - barH * 0.25f), Size(fillW + barH * 0.5f, barH * 1.5f), CornerRadius(barH * 0.75f))
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(Pal.PINK), Color(Pal.ORANGE), Color(Pal.GOLD)), startX = barX, endX = barX + barW),
            Offset(barX, barY), Size(fillW, barH), radius,
        )
        // A pale gloss over the top half.
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(barX + barH * 0.2f, barY + barH * 0.1f), Size(fillW - barH * 0.4f, barH * 0.38f), CornerRadius(barH * 0.2f))
        if (motion) {
            val band = barH * 3f
            val sx = barX + ((t * 0.55f) % 1.4f - 0.2f) * (fillW + band) - band
            withTransform({ clipRect(barX, barY, barX + fillW, barY + barH) }) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0f)), startX = sx, endX = sx + band),
                    Offset(sx, barY), Size(band, barH), radius,
                )
            }
        }
    }
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(barX, barY), Size(barW, barH), radius, style = Stroke(width = 2f))

    // What it is doing, and how far along: "LAYING THE CARPET..." over the bar, the percentage at its right.
    val unit = w * 0.0058f
    val dots = if (motion) ".".repeat(1 + (t * 2.4f).toInt() % 3) else "..."
    ArcadeFont.draw(this, if (label.isEmpty()) "" else "$label$dots", barX, barY - ArcadeFont.height(unit * 1.15f, true) - barH * 0.5f, unit * 1.15f, Color(0xFFD9C8FF), tiny = true)
    val pct = "${(shown * 100f).toInt().coerceIn(0, 100)}%"
    ArcadeFont.draw(this, pct, barX + barW - ArcadeFont.width(pct, unit * 1.15f, true), barY - ArcadeFont.height(unit * 1.15f, true) - barH * 0.5f, unit * 1.15f, Color(Pal.GOLD), tiny = true)

    // A tip near the foot.
    val tip = LoadingLook.TIPS[LoadingLook.tipIndex(t, motion, LoadingLook.TIPS.size)]
    ArcadeFont.drawCentered(this, tip, w / 2f, h * 0.86f, unit * 1.05f, Color(0xFF9A90B8), alpha = LoadingLook.tipAlpha(t, motion), tiny = true)
}

/** A coin turning about its vertical axis: [facing] (cos of the turn, -1 to 1) squeezes it, [edge] (sin) shows its thickness. */
private fun DrawScope.drawCoin(cx: Float, cy: Float, r: Float, facing: Float, edge: Float) {
    val gold = Color(Pal.GOLD)
    val dark = Color(0xFFB87A10)
    val squeeze = abs(facing).coerceAtLeast(0.05f)
    val thick = r * 0.22f * abs(edge)
    // A soft glow behind.
    drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = 0.32f), gold.copy(alpha = 0f)), Offset(cx, cy), r * 2.4f), r * 2.4f, Offset(cx, cy))
    // The rim: the same oval pushed back by the coin's thickness.
    val dir = if (facing >= 0f) 1f else -1f
    drawOval(dark, Offset(cx - r * squeeze - dir * thick, cy - r), Size(r * squeeze * 2f, r * 2f))
    // The face.
    drawOval(Brush.verticalGradient(listOf(Color(0xFFFFE9A0), gold, Color(0xFFE09A1A)), startY = cy - r, endY = cy + r), Offset(cx - r * squeeze, cy - r), Size(r * squeeze * 2f, r * 2f))
    withTransform({ scale(squeeze, 1f, Offset(cx, cy)) }) {
        drawCircle(dark, r * 0.78f, Offset(cx, cy), style = Stroke(width = r * 0.08f))
        if (facing >= 0f) {
            // The star on its face; turned away, the back is plain.
            val star = "${ArcadeFont.STAR}"
            val unit = r * 0.9f / ArcadeFont.CAP
            ArcadeFont.drawCentered(this, star, cx, cy - ArcadeFont.height(unit) / 2f, unit, dark, shadow = false)
        }
    }
}

/** One word of the neon sign: an extruded shadow, a glow and a bright face, each letter bobbing gently when there is motion. */
private fun DrawScope.neonWord(paint: Paint, word: String, cx: Float, baseline: Float, size: Float, color: Int, core: Int, t: Float, phase: Float, motion: Boolean) {
    val canvas = drawContext.canvas.nativeCanvas
    paint.reset()
    paint.isAntiAlias = true
    paint.typeface = Fonts.display
    paint.textSize = size
    paint.letterSpacing = 0.02f
    val total = paint.measureText(word)
    var x = cx - total / 2f
    val flicker = if (motion && sin(t * 23f + phase) > 0.985f) 0.55f else 1f
    for (i in word.indices) {
        val ch = word.substring(i, i + 1)
        val lw = paint.measureText(ch)
        val y = baseline + if (motion) sin(t * 2.6f + i * 0.6f + phase) * size * 0.035f else 0f
        paint.shader = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        for (k in 5 downTo 1) {
            paint.color = Pal.shade(0xFF3A1454.toInt(), 1f - k * 0.09f)
            canvas.drawText(ch, x + k * size * 0.012f, y + k * size * 0.016f, paint)
        }
        paint.color = color
        paint.alpha = (200 * flicker).toInt()
        paint.setShadowLayer(size * 0.22f, 0f, 0f, color)
        canvas.drawText(ch, x, y, paint)
        paint.clearShadowLayer()
        paint.shader = LinearGradient(0f, y - size * 0.72f, 0f, y, core, color, Shader.TileMode.CLAMP)
        paint.alpha = 255
        canvas.drawText(ch, x, y, paint)
        paint.shader = null
        x += lw
    }
}
