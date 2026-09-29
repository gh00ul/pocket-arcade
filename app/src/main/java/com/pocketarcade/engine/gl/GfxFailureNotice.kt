package com.pocketarcade.engine.gl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import kotlin.math.floor

/** What the notice says for a [failure], one entry per line. */
internal fun failureLines(failure: GfxFailure): List<String> = when (failure) {
    GfxFailure.NEEDS_ES3 -> listOf("THIS DEVICE CAN'T", "DRAW THE ARCADE", "", "IT NEEDS OPENGL ES 3.0")
    GfxFailure.STOPPED -> listOf("GRAPHICS STOPPED", "", "RESTART THE APP")
}

/**
 * Full-screen notice shown when the GL thread has given up ([Gfx.failure]): without it the
 * user would be left with the interface floating over a blank screen. Swallows touches so the
 * screens underneath can't be played blind.
 */
@Composable
fun GfxFailureNotice(failure: GfxFailure, modifier: Modifier = Modifier) {
    val lines = failureLines(failure)
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        drawRect(Color(Pal.NIGHT), Offset.Zero, Size(size.width, size.height))
        val unit = floor(size.width / 130f).coerceAtLeast(2f)
        val lineH = ArcadeFont.height(unit) + unit * 3.2f
        var y = (size.height - lines.size * lineH) / 2f
        for ((i, line) in lines.withIndex()) {
            if (line.isNotEmpty()) {
                val c = if (i == 0) Color(Pal.YELLOW) else Color.White
                ArcadeFont.drawCentered(this, line, size.width / 2f, y, if (i == 0) unit * 1.3f else unit, c)
            }
            y += lineH
        }
    }
}
