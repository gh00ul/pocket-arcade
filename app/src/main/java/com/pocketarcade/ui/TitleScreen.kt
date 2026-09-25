package com.pocketarcade.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.sin

private class TitleState {
    var time = 0f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
}

/** Attract-mode title: a neon sign over a night sky, the hall's machines in a showroom, and a synthwave floor. */
@Composable
fun TitleScreen(save: SaveState, games: List<MiniGame>, onStart: () -> Unit) {
    val state = remember { TitleState() }
    val start by rememberUpdatedState(onStart)
    val showcase = remember(games) { TitleShowcase(games) }
    DisposableEffect(showcase) {
        onDispose { Gfx.remove(TitleShowcase.SLOT) }
    }
    val frame = rememberGameLoop(state) { dt ->
        state.time += dt
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { start() }) },
    ) {
        frame.value
        val t = state.time
        val w = size.width
        val h = size.height
        val logoSize = logoSizeFor(state.paint, w)
        val logoTop = h * 0.1f
        val showTop = logoTop + logoSize * 2.25f + w * 0.12f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF05030C), Color(0xFF1A0F36), Color(0xFF3A1650)), endY = showTop), Offset.Zero, Size(w, showTop))

        // Twinkling stars.
        for (i in 0 until 80) {
            val sx = hash01(i, 1) * w
            val sy = hash01(i, 2) * (showTop - 8f)
            val tw = 0.3f + 0.7f * abs(sin(t * (1f + hash01(i, 3) * 2f) + i))
            val r = if (i % 7 == 0) w * 0.0035f else w * 0.002f
            drawCircle(Color.White, r * 2.5f, Offset(sx, sy), alpha = 0.08f * tw)
            drawCircle(Color.White, r, Offset(sx, sy), alpha = 0.7f * tw)
        }

        neonWord(state.paint, "POCKET", w / 2f, logoTop + logoSize, logoSize, 0xFF39E6F2.toInt(), 0xFFE8FFFF.toInt(), t, 0f)
        neonWord(state.paint, "ARCADE", w / 2f, logoTop + logoSize * 2.08f, logoSize, 0xFFFFC83D.toInt(), 0xFFFFF6D0.toInt(), t, 1.7f)
        val tag = w * 0.0072f
        ArcadeFont.drawCentered(this, "A WHOLE ARCADE HALL IN YOUR POCKET", w / 2f, logoTop + logoSize * 2.3f, tag, Color(0xFFD9C8FF), tiny = true)

        // The machines of the hall, lined up in a 3D showroom.
        val horizon = h * 0.72f
        showcase.draw(0f, showTop, w, horizon - showTop, t) { id -> save.highScore(id) }
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFF4FA8).copy(alpha = 0f), Color(0xFFFF4FA8).copy(alpha = 0.9f), Color(0xFFFF4FA8).copy(alpha = 0f)), startY = showTop - 6f, endY = showTop + 6f), Offset(0f, showTop - 6f), Size(w, 12f))

        // Synthwave floor grid below the showroom.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A0F3E), Color(0xFF0B0616)), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
        val scroll = (t * 0.6f) % 1f
        for (i in 0 until 14) {
            val z = (i + 1 - scroll)
            val y = horizon + (h - horizon) * (1f / z) * 0.9f
            if (y < horizon || y > h) continue
            drawLine(Color(0xFFFF4FA8), Offset(0f, y), Offset(w, y), strokeWidth = 2.5f, alpha = 0.18f + 0.4f * ((y - horizon) / (h - horizon)))
        }
        for (k in -8..8) {
            val bx = w / 2f + k * w / 7f
            drawLine(Color(0xFFFF4FA8), Offset(w / 2f + k * w / 60f, horizon), Offset(bx + (bx - w / 2f) * 1.5f, h), strokeWidth = 2.5f, alpha = 0.35f)
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFF7AC8), Color(0xFFFF4FA8).copy(alpha = 0f)), startY = horizon, endY = horizon + 40f), Offset(0f, horizon), Size(w, 40f), alpha = 0.6f)

        val unit = w * 0.0095f
        val blink = 0.55f + 0.45f * sin(t * 4f)
        ArcadeFont.drawCentered(this, "TAP TO START", w / 2f, h * 0.785f, unit, Color.White, blink)
        if (save.loaded) {
            val u = unit * 0.62f
            val line = "${ArcadeFont.TOKEN} ${save.tokens} TOKENS      ${ArcadeFont.TICKET} ${save.tickets} TICKETS"
            ArcadeFont.drawCentered(this, line, w / 2f, h * 0.865f, u, Color(0xFFFFCF5A))
        }
        ArcadeFont.drawCentered(this, "BEST WITH SOUND ON ${ArcadeFont.NOTE}", w / 2f, h * 0.92f, unit * 0.48f, Color(0xFF9A90B8), tiny = true)
    }
}

/** The biggest sign size at which the longer word still fits comfortably across [w]. */
private fun logoSizeFor(paint: Paint, w: Float): Float {
    paint.reset()
    paint.typeface = Fonts.display
    paint.textSize = 100f
    paint.letterSpacing = 0.02f
    val widest = maxOf(paint.measureText("POCKET"), paint.measureText("ARCADE"))
    return minOf(w * 0.25f, w * 0.86f / widest * 100f)
}

/**
 * One word of the neon sign: a deep extruded shadow, a coloured glow and a bright gradient face,
 * each letter bobbing on its own.
 */
private fun DrawScope.neonWord(paint: Paint, word: String, cx: Float, baseline: Float, size: Float, color: Int, core: Int, t: Float, phase: Float) {
    val canvas = drawContext.canvas.nativeCanvas
    paint.reset()
    paint.isAntiAlias = true
    paint.typeface = Fonts.display
    paint.textSize = size
    paint.letterSpacing = 0.02f
    val total = paint.measureText(word)
    var x = cx - total / 2f
    val flicker = if (sin(t * 23f + phase) > 0.985f) 0.55f else 1f
    for (i in word.indices) {
        val ch = word.substring(i, i + 1)
        val lw = paint.measureText(ch)
        val y = baseline + sin(t * 2.6f + i * 0.6f + phase) * size * 0.035f
        // Extrusion.
        paint.shader = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        for (k in 6 downTo 1) {
            paint.color = Pal.shade(0xFF3A1454.toInt(), 1f - k * 0.08f)
            canvas.drawText(ch, x + k * size * 0.012f, y + k * size * 0.016f, paint)
        }
        // Glow.
        paint.color = color
        paint.alpha = (200 * flicker).toInt()
        paint.setShadowLayer(size * 0.22f, 0f, 0f, color)
        canvas.drawText(ch, x, y, paint)
        paint.clearShadowLayer()
        // Face.
        paint.shader = LinearGradient(0f, y - size * 0.72f, 0f, y, core, color, Shader.TileMode.CLAMP)
        paint.alpha = 255
        canvas.drawText(ch, x, y, paint)
        paint.shader = null
        x += lw
    }
}
