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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Color.shade(f: Float): Color = Color(Pal.shade(toArgb(), f))

/** Mixes towards white by [f]. */
fun Color.lift(f: Float): Color = Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha)

/** Card and panel colours shared by the menus. */
object UiColors {
    val cardTop = Color(0xFF221A3C)
    val cardBottom = Color(0xFF120C22)
    val glass = Color(0x16FFFFFF)
    val glassEdge = Color(0x24FFFFFF)
    val scrim = Color(0xCC07050E)
}

/**
 * Text in the game's type, sized in grid units (capitals are 7 units tall, tiny ones 5), with a
 * soft shadow. Multi-line text splits on '\n'.
 */
@Composable
fun ArcadeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    unit: Dp = 3.dp,
    shadow: Boolean = true,
    tiny: Boolean = false,
    centered: Boolean = false,
    alpha: Float = 1f,
) {
    val density = LocalDensity.current
    val u = with(density) { unit.toPx() }
    val lines = remember(text) { text.split('\n') }
    val gap = u * 3.2f
    val lineH = ArcadeFont.height(u, tiny) + gap
    val pad = if (shadow) u * 1.2f else 0f
    val widths = lines.map { ArcadeFont.width(it, u, tiny) }
    val w = (widths.maxOrNull() ?: 0f) + pad
    val h = lines.size * lineH - gap + pad
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }
    Canvas(modifier.size(wDp, hDp)) {
        lines.forEachIndexed { i, line ->
            val x = if (centered) (w - pad - widths[i]) / 2f else 0f
            val y = i * lineH
            if (shadow) ArcadeFont.drawShadowed(this, line, x, y, u, color, alpha = alpha, tiny = tiny)
            else ArcadeFont.draw(this, line, x, y, u, color, alpha, tiny)
        }
    }
}

/**
 * A glossy arcade push-button: a candy-coloured cap on a darker skirt that sinks when pressed.
 */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(Pal.PINK),
    textColor: Color = Color.White,
    enabled: Boolean = true,
    unit: Dp = 3.dp,
    tiny: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lip = 5.dp
    val down = pressed && enabled
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .drawBehind { buttonCap(if (enabled) color else Color(0xFF3A3450), lip.toPx(), down, round = false) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = if (down) lip * 0.3f else -lip * 0.5f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ArcadeText(text, color = if (enabled) textColor else Color(0xFF8A84A0), unit = unit, tiny = tiny, centered = true)
        }
    }
}

/** A round glossy button carrying a vector [icon]. */
@Composable
fun RoundButton(icon: UiIcon, onClick: () -> Unit, color: Color, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lip = 4.dp
    Canvas(
        modifier
            .size(size, size + lip)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val l = lip.toPx()
        buttonCap(color, l, pressed, round = true)
        val faceY = if (pressed) l * 0.8f else 0f
        val c = Offset(this.size.width / 2f, faceY + (this.size.height - l) / 2f)
        drawUiIcon(icon, c + Offset(0f, this.size.width * 0.03f), this.size.width * 0.5f, Color.Black.copy(alpha = 0.3f))
        drawUiIcon(icon, c, this.size.width * 0.5f, Color.White)
    }
}

/** The shared look of every button: drop shadow, skirt, gradient cap, gloss and rim. */
private fun DrawScope.buttonCap(base: Color, lip: Float, down: Boolean, round: Boolean) {
    val w = size.width
    val h = size.height - lip
    val r = if (round) h / 2f else min(16.dp.toPx(), h / 2f)
    val cr = CornerRadius(r, r)
    val faceY = if (down) lip * 0.8f else 0f
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(0f, lip + 2.dp.toPx()), Size(w, h), cr)
    drawRoundRect(base.shade(0.45f), Offset(0f, lip), Size(w, h), cr)
    drawRoundRect(
        Brush.verticalGradient(listOf(base.lift(0.28f), base, base.shade(0.78f)), startY = faceY, endY = faceY + h),
        Offset(0f, faceY), Size(w, h), cr,
    )
    val inset = 3.dp.toPx()
    val gh = h * 0.46f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.04f)), startY = faceY + inset, endY = faceY + inset + gh),
        Offset(inset, faceY + inset * 0.7f), Size(w - inset * 2f, gh), CornerRadius(r * 0.85f, r * 0.85f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(0f, faceY), Size(w, h), cr, style = Stroke(1.dp.toPx()))
}

enum class UiIcon { TROPHY, SOUND, MUTED, CLOSE, PAUSE }

/** Simple white glyphs for round buttons, fitting a box [s] across centred on [c]. */
fun DrawScope.drawUiIcon(icon: UiIcon, c: Offset, s: Float, color: Color) {
    when (icon) {
        UiIcon.CLOSE -> {
            val d = s * 0.3f
            drawLine(color, c + Offset(-d, -d), c + Offset(d, d), s * 0.15f, StrokeCap.Round)
            drawLine(color, c + Offset(d, -d), c + Offset(-d, d), s * 0.15f, StrokeCap.Round)
        }
        UiIcon.PAUSE -> {
            val bw = s * 0.16f
            drawRoundRect(color, c + Offset(-s * 0.26f, -s * 0.32f), Size(bw, s * 0.64f), CornerRadius(bw / 3f))
            drawRoundRect(color, c + Offset(s * 0.1f, -s * 0.32f), Size(bw, s * 0.64f), CornerRadius(bw / 3f))
        }
        UiIcon.TROPHY -> {
            val cup = Path().apply {
                moveTo(c.x - s * 0.3f, c.y - s * 0.38f)
                lineTo(c.x + s * 0.3f, c.y - s * 0.38f)
                cubicTo(c.x + s * 0.3f, c.y + s * 0.02f, c.x + s * 0.18f, c.y + s * 0.12f, c.x, c.y + s * 0.14f)
                cubicTo(c.x - s * 0.18f, c.y + s * 0.12f, c.x - s * 0.3f, c.y + s * 0.02f, c.x - s * 0.3f, c.y - s * 0.38f)
                close()
            }
            drawPath(cup, color)
            val handle = Stroke(s * 0.08f, cap = StrokeCap.Round)
            drawArc(color, 90f, 180f, false, Offset(c.x - s * 0.46f, c.y - s * 0.32f), Size(s * 0.3f, s * 0.3f), style = handle)
            drawArc(color, -90f, 180f, false, Offset(c.x + s * 0.16f, c.y - s * 0.32f), Size(s * 0.3f, s * 0.3f), style = handle)
            drawRect(color, Offset(c.x - s * 0.06f, c.y + s * 0.12f), Size(s * 0.12f, s * 0.16f))
            drawRoundRect(color, Offset(c.x - s * 0.24f, c.y + s * 0.27f), Size(s * 0.48f, s * 0.12f), CornerRadius(s * 0.04f))
        }
        UiIcon.SOUND, UiIcon.MUTED -> {
            val body = Path().apply {
                moveTo(c.x - s * 0.42f, c.y - s * 0.14f)
                lineTo(c.x - s * 0.24f, c.y - s * 0.14f)
                lineTo(c.x - s * 0.02f, c.y - s * 0.36f)
                lineTo(c.x - s * 0.02f, c.y + s * 0.36f)
                lineTo(c.x - s * 0.24f, c.y + s * 0.14f)
                lineTo(c.x - s * 0.42f, c.y + s * 0.14f)
                close()
            }
            drawPath(body, color)
            if (icon == UiIcon.SOUND) {
                for (k in 1..2) {
                    val rr = s * (0.14f + k * 0.13f)
                    drawArc(color, -45f, 90f, false, Offset(c.x + s * 0.02f - rr, c.y - rr), Size(rr * 2f, rr * 2f), style = Stroke(s * 0.09f, cap = StrokeCap.Round))
                }
            } else {
                val x0 = c.x + s * 0.14f
                val d = s * 0.14f
                drawLine(color, Offset(x0, c.y - d), Offset(x0 + d * 2f, c.y + d), s * 0.1f, StrokeCap.Round)
                drawLine(color, Offset(x0 + d * 2f, c.y - d), Offset(x0, c.y + d), s * 0.1f, StrokeCap.Round)
            }
        }
    }
}

/** A gold arcade token: milled rim, raised star and a shine, [r] in radius round [c]. */
fun DrawScope.drawToken(c: Offset, r: Float) {
    drawCircle(Color(0xFF7A4A08), r, c + Offset(0f, r * 0.08f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE89A), Color(0xFFF5B82E), Color(0xFFC77A12)), c - Offset(r * 0.35f, r * 0.4f), r * 1.6f), r, c)
    drawCircle(Color(0xFF9A5A0A), r * 0.74f, c, style = Stroke(r * 0.1f))
    val star = Path()
    for (k in 0 until 10) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val rr = if (k % 2 == 0) r * 0.46f else r * 0.2f
        val p = c + Offset(cos(a) * rr, sin(a) * rr)
        if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close()
    drawPath(star, Color(0xFFB36A0C))
    rotate(-35f, c) {
        drawOval(Color.White.copy(alpha = 0.45f), c + Offset(-r * 0.55f, -r * 0.82f), Size(r * 0.7f, r * 0.26f))
    }
}

/** A prize ticket [w] wide centred on [c]: notched ends, a printed border and a star. */
fun DrawScope.drawTicket(c: Offset, w: Float) {
    val h = w * 0.58f
    val tl = c - Offset(w / 2f, h / 2f)
    val notch = h * 0.18f
    val shape = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + w, tl.y + h, CornerRadius(h * 0.12f)))
    }
    val cut = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(tl.x, c.y), notch))
        addOval(androidx.compose.ui.geometry.Rect(Offset(tl.x + w, c.y), notch))
    }
    val ticket = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, shape, cut)
    rotate(-8f, c) {
        drawPath(ticket, Color(0xFF7A2A06), alpha = 0.6f)
        translate(0f, -h * 0.07f) {
            drawPath(ticket, Brush.verticalGradient(listOf(Color(0xFFFFB35A), Color(0xFFF07A1A)), startY = tl.y, endY = tl.y + h))
            drawRoundRect(Color(0xFFFFE0A8), tl + Offset(w * 0.17f, h * 0.2f), Size(w * 0.66f, h * 0.6f), CornerRadius(h * 0.08f), style = Stroke(h * 0.06f))
            val star = Path()
            for (k in 0 until 10) {
                val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                val rr = if (k % 2 == 0) h * 0.22f else h * 0.09f
                val p = c + Offset(cos(a) * rr, sin(a) * rr)
                if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
            }
            star.close()
            drawPath(star, Color(0xFFB0300A))
        }
    }
}

@Composable
fun TokenIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawToken(center, this.size.minDimension / 2f * 0.92f) }
}

@Composable
fun TicketIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size * 1.3f, size)) { drawTicket(center, this.size.width * 0.95f) }
}

/**
 * A full-screen modal: dims the hall, blocks touches behind it and shows a dark glass card with
 * a glowing edge, a title and a close button.
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
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
                .shadow(24.dp, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(UiColors.cardTop, UiColors.cardBottom)))
                .border(2.dp, Brush.verticalGradient(listOf(accent, accent.shade(0.45f))), shape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ArcadeText(title, color = accent.lift(0.15f), unit = 3.dp)
                Spacer(Modifier.weight(1f))
                RoundButton(UiIcon.CLOSE, onClose, Color(Pal.RED), size = 42.dp)
            }
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/** A frosted inset box inside a panel. */
@Composable
fun GlassBox(modifier: Modifier = Modifier, highlight: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (highlight != null) highlight.copy(alpha = 0.16f) else UiColors.glass)
            .border(if (highlight != null) 2.dp else 1.dp, highlight ?: UiColors.glassEdge, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Token and ticket counters shown side by side. */
@Composable
fun CurrencyRow(tokens: Int, tickets: Int, modifier: Modifier = Modifier, unit: Dp = 3.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TokenIcon(unit * 9f)
        Spacer(Modifier.width(6.dp))
        ArcadeText(tokens.toString(), color = Color(0xFFFFD35A), unit = unit)
        Spacer(Modifier.width(18.dp))
        TicketIcon(unit * 8f)
        Spacer(Modifier.width(6.dp))
        ArcadeText(tickets.toString(), color = Color(0xFFFFA24A), unit = unit)
    }
}
