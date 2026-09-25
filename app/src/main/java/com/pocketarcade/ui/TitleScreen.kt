package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelPainter
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.drawPixelImage
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.games.MiniGame
import com.pocketarcade.hub.PropArt
import kotlin.math.floor
import kotlin.math.sin

private class TitleState {
    var time = 0f
    val painter = PixelPainter()
}

/** Attract-mode title: neon logo over a scrolling synthwave floor with the hall's machines. */
@Composable
fun TitleScreen(save: SaveState, games: List<MiniGame>, onStart: () -> Unit) {
    val state = remember { TitleState() }
    val start by rememberUpdatedState(onStart)
    val cabinets = remember(games) { games.map { PropArt.cabinet(it.look, it.marquee) } }
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
        drawRect(Color(Pal.NIGHT), Offset.Zero, size)

        // Twinkling stars.
        for (i in 0 until 70) {
            val sx = hash01(i, 1) * w
            val sy = hash01(i, 2) * h * 0.6f
            val tw = 0.3f + 0.7f * kotlin.math.abs(sin(t * (1f + hash01(i, 3) * 2f) + i))
            val sz = if (i % 7 == 0) 6f else 3f
            drawRect(Color.White, Offset(sx, sy), Size(sz, sz), alpha = 0.6f * tw)
        }

        // Synthwave floor grid.
        val horizon = h * 0.62f
        drawRect(Color(Pal.DEEP), Offset(0f, horizon), Size(w, h - horizon))
        val scroll = (t * 0.6f) % 1f
        for (i in 0 until 14) {
            val z = (i + 1 - scroll)
            val y = horizon + (h - horizon) * (1f / z) * 0.9f
            if (y < horizon || y > h) continue
            drawLine(Color(Pal.PINK), Offset(0f, y), Offset(w, y), strokeWidth = 3f, alpha = 0.6f)
        }
        for (k in -8..8) {
            val bx = w / 2f + k * w / 7f
            drawLine(Color(Pal.PINK), Offset(w / 2f + k * w / 60f, horizon), Offset(bx * 1f + (bx - w / 2f) * 1.5f, h), strokeWidth = 3f, alpha = 0.45f)
        }
        drawRect(Color(Pal.HOTPINK), Offset(0f, horizon - 2f), Size(w, 4f), alpha = 0.9f)

        // The hall's machines lined up on the horizon.
        if (cabinets.isNotEmpty()) {
            val artW = cabinets.sumOf { it.w + 4 }.toFloat()
            val px = floor(w * 0.9f / artW).coerceIn(1f, floor(h * 0.16f / 84f).coerceAtLeast(1f))
            var totalW = 0f
            for (c in cabinets) totalW += c.w * px + px * 4f
            var x = (w - totalW) / 2f
            for ((i, c) in cabinets.withIndex()) {
                val y = horizon - c.h * px + px * 2f
                drawRect(Color(games[i].look.glow), Offset(x - px * 2f, horizon - px * 2f), Size(c.w * px + px * 4f, px * 3f), alpha = 0.3f)
                drawPixelImage(c.image, x, y, px)
                c.screen?.let { scr ->
                    val sx = x + scr.x * px
                    val sy = y + scr.y * px
                    clipRect(sx, sy, sx + scr.w * px, sy + scr.h * px) {
                        games[i].drawAttract(state.painter.begin(this, sx, sy, px), scr.w, scr.h, t + i * 1.3f)
                    }
                }
                x += c.w * px + px * 4f
            }
        }

        // Neon logo, letter by letter so it can wave.
        val scale = floor(w * 0.82f / PixelFont.width("ARCADE", 1f)).coerceAtLeast(4f)
        fun logo(word: String, top: Float, color: Int) {
            val x0 = (w - PixelFont.width(word, scale)) / 2f
            for (i in word.indices) {
                val ch = word.substring(i, i + 1)
                val wob = sin(t * 3f + i * 0.6f) * scale * 0.5f
                val lx = x0 + i * PixelFont.ADV * scale
                PixelFont.draw(this, ch, lx + scale, top + wob + scale, scale, Color(Pal.PLUM))
                PixelFont.draw(this, ch, lx - scale * 0.5f, top + wob, scale, Color(Pal.PINK), 0.35f)
                PixelFont.draw(this, ch, lx, top + wob, scale, Color(color))
            }
        }
        val logoTop = h * 0.14f
        logo("POCKET", logoTop, Pal.CYAN)
        logo("ARCADE", logoTop + scale * 9.5f, Pal.YELLOW)

        val small = floor(scale / 3f).coerceAtLeast(3f)
        PixelFont.drawCentered(this, "A WHOLE ARCADE HALL", w / 2f, logoTop + scale * 19.5f, small, Color(Pal.LAVENDER))
        PixelFont.drawCentered(this, "IN YOUR POCKET", w / 2f, logoTop + scale * 19.5f + small * 10f, small, Color(Pal.LAVENDER))

        val blink = if ((t * 2f).toInt() % 2 == 0) 1f else 0.25f
        PixelFont.drawCentered(this, "TAP TO START", w / 2f, h * 0.76f, small * 1.4f, Color.White, blink)
        if (save.loaded) {
            PixelFont.drawCentered(
                this, "${PixelFont.TOKEN} ${save.tokens} TOKENS   ${PixelFont.TICKET} ${save.tickets} TICKETS",
                w / 2f, h * 0.86f, (small * 0.8f).coerceAtLeast(2f), Color(Pal.GOLD),
            )
        }
        PixelFont.drawCentered(this, "BEST WITH SOUND ON ${PixelFont.NOTE}", w / 2f, h * 0.92f, (small * 0.7f).coerceAtLeast(2f), Color(Pal.GRAY))
    }
}
