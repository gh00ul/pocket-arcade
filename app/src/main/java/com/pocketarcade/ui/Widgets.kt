package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.drawPixelImage
import kotlin.math.roundToInt

fun Color.shade(f: Float): Color = Color(Pal.shade(toArgb(), f))

/** Text in the game's bitmap font, sized in whole screen pixels per font pixel. */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    pixel: Dp = 3.dp,
    shadow: Boolean = true,
    tiny: Boolean = false,
    centered: Boolean = false,
    alpha: Float = 1f,
) {
    val density = LocalDensity.current
    val px = with(density) { pixel.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
    val lines = remember(text) { text.split('\n') }
    val gap = px * 3f
    val lineH = PixelFont.height(px, tiny) + gap
    val w = (lines.maxOfOrNull { PixelFont.width(it, px, tiny) } ?: 0f) + if (shadow) px else 0f
    val h = lines.size * lineH - gap + if (shadow) px else 0f
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }
    Canvas(modifier.size(wDp, hDp)) {
        lines.forEachIndexed { i, line ->
            val lw = PixelFont.width(line, px, tiny)
            val x = if (centered) ((w - lw) / 2f).roundToInt().toFloat() else 0f
            val y = i * lineH
            if (shadow) PixelFont.drawShadowed(this, line, x, y, px, color, alpha = alpha, isTiny = tiny)
            else PixelFont.draw(this, line, x, y, px, color, alpha, tiny)
        }
    }
}

/** Draws a 1x pixel-art image at [pixel] screen pixels per art pixel. */
@Composable
fun PixelImage(image: ImageBitmap, pixel: Dp, modifier: Modifier = Modifier, alpha: Float = 1f, tint: Color? = null) {
    val density = LocalDensity.current
    val px = with(density) { pixel.toPx() }.roundToInt().coerceAtLeast(1).toFloat()
    val wDp = with(density) { (image.width * px).toDp() }
    val hDp = with(density) { (image.height * px).toDp() }
    Canvas(modifier.size(wDp, hDp)) {
        drawPixelImage(image, 0f, 0f, px, alpha, tint)
    }
}

/** Chunky arcade push-button with a raised edge that sinks when pressed. */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(Pal.PINK),
    textColor: Color = Color.White,
    enabled: Boolean = true,
    pixel: Dp = 3.dp,
    tiny: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 5.dp
    val base = if (enabled) color else Color(Pal.DARKGRAY)
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .drawBehind {
                val d = depth.toPx()
                val b = 2.dp.toPx()
                val faceTop = if (pressed && enabled) d else 0f
                drawRect(Color(Pal.BLACK), Offset(0f, 0f), Size(size.width, size.height))
                drawRect(base.shade(0.55f), Offset(b, d), Size(size.width - b * 2, size.height - d - b))
                drawRect(base, Offset(b, faceTop + b), Size(size.width - b * 2, size.height - d - b * 2))
                drawRect(Color.White, Offset(b, faceTop + b), Size(size.width - b * 2, b), alpha = 0.35f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = if (pressed && enabled) depth / 2 else (-depth) / 2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            PixelText(text, color = if (enabled) textColor else Color(Pal.GRAY), pixel = pixel, tiny = tiny, centered = true)
        }
    }
}

/** Tiny pixel icons used in the HUD and menus. */
object UiIcons {
    val token: ImageBitmap by lazy {
        val c = PixelCanvas(9, 9)
        c.disc(4.5f, 4.5f, 4.5f, Pal.ORANGE)
        c.disc(4.5f, 4.5f, 3.3f, Pal.GOLD)
        c.vline(4, 2, 6, Pal.ORANGE)
        c.set(3, 2, Pal.YELLOW); c.set(2, 3, Pal.YELLOW)
        c.toImageBitmap()
    }

    val ticket: ImageBitmap by lazy {
        val c = PixelCanvas(11, 7)
        c.fill(0, 0, 11, 7, Pal.ORANGE)
        c.set(0, 3, 0); c.set(10, 3, 0)
        c.rect(1, 1, 9, 5, Pal.GOLD)
        c.set(5, 2, Pal.YELLOW); c.hline(4, 6, 3, Pal.YELLOW); c.set(5, 4, Pal.YELLOW)
        c.toImageBitmap()
    }
}

/**
 * A full-screen modal: dims the hall, blocks touches behind it and shows a neon-bordered card
 * with a title bar and close button.
 */
@Composable
fun ArcadePanel(
    title: String,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC08060F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
                .background(Color(Pal.NIGHT))
                .border(3.dp, accent)
                .padding(3.dp)
                .border(2.dp, accent.shade(0.5f))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixelText(title, color = accent, pixel = 3.dp)
                Spacer(Modifier.weight(1f))
                ArcadeButton("×", onClose, color = Color(Pal.RED), pixel = 3.dp)
            }
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/** Token and ticket counters shown side by side. */
@Composable
fun CurrencyRow(tokens: Int, tickets: Int, modifier: Modifier = Modifier, pixel: Dp = 3.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PixelImage(UiIcons.token, pixel)
        Spacer(Modifier.width(6.dp))
        PixelText(tokens.toString(), color = Color(Pal.GOLD), pixel = pixel)
        Spacer(Modifier.width(18.dp))
        PixelImage(UiIcons.ticket, pixel)
        Spacer(Modifier.width(6.dp))
        PixelText(tickets.toString(), color = Color(Pal.ORANGE), pixel = pixel)
    }
}
