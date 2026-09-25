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
import androidx.compose.ui.input.pointer.pointerInput
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.games.MiniGame
import com.pocketarcade.hub.TitleShowcase
import kotlin.math.floor
import kotlin.math.sin

private class TitleState {
    var time = 0f
}

/** Attract-mode title: neon logo over a scrolling synthwave floor with the hall's machines. */
@Composable
fun TitleScreen(save: SaveState, games: List<MiniGame>, onStart: () -> Unit) {
    val state = remember { TitleState() }
    val start by rememberUpdatedState(onStart)
    val showcase = remember(games) { TitleShowcase(games) }
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

        // The machines of the hall, lined up in a 3D showroom.
        val showTop = logoTop + scale * 19.5f + small * 20f
        val horizon = h * 0.72f
        showcase.draw(this, 0f, showTop, w, horizon - showTop, t) { id -> save.highScore(id) }
        drawRect(Color(Pal.HOTPINK), Offset(0f, showTop - 3f), Size(w, 3f), alpha = 0.8f)

        // Synthwave floor grid below the showroom.
        drawRect(Color(Pal.DEEP), Offset(0f, horizon), Size(w, h - horizon))
        val scroll = (t * 0.6f) % 1f
        for (i in 0 until 14) {
            val z = (i + 1 - scroll)
            val y = horizon + (h - horizon) * (1f / z) * 0.9f
            if (y < horizon || y > h) continue
            drawLine(Color(Pal.PINK), Offset(0f, y), Offset(w, y), strokeWidth = 3f, alpha = 0.5f)
        }
        for (k in -8..8) {
            val bx = w / 2f + k * w / 7f
            drawLine(Color(Pal.PINK), Offset(w / 2f + k * w / 60f, horizon), Offset(bx + (bx - w / 2f) * 1.5f, h), strokeWidth = 3f, alpha = 0.4f)
        }
        drawRect(Color(Pal.HOTPINK), Offset(0f, horizon), Size(w, 4f), alpha = 0.9f)

        val blink = if ((t * 2f).toInt() % 2 == 0) 1f else 0.25f
        PixelFont.drawCentered(this, "TAP TO START", w / 2f, h * 0.79f, small * 1.4f, Color.White, blink)
        if (save.loaded) {
            PixelFont.drawCentered(
                this, "${PixelFont.TOKEN} ${save.tokens} TOKENS   ${PixelFont.TICKET} ${save.tickets} TICKETS",
                w / 2f, h * 0.87f, (small * 0.8f).coerceAtLeast(2f), Color(Pal.GOLD),
            )
        }
        PixelFont.drawCentered(this, "BEST WITH SOUND ON ${PixelFont.NOTE}", w / 2f, h * 0.92f, (small * 0.7f).coerceAtLeast(2f), Color(Pal.GRAY))
    }
}