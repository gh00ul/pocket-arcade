package com.pocketarcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.easeOutCubic
import kotlinx.coroutines.delay
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
 * What a screen reader should say for arcade text: the symbols the font draws inline (play, star,
 * token...) named or dropped, line breaks as spaces and the capitals lowered, so TalkBack reads
 * words rather than spelling out shouted letters.
 */
fun spokenText(text: String): String {
    val out = StringBuilder(text.length + 8)
    for (ch in text) {
        when (ch) {
            // The play triangle only points at the word beside it, and a note says nothing.
            ArcadeFont.PLAY, ArcadeFont.NOTE -> out.append(' ')
            ArcadeFont.STAR -> out.append(" star ")
            ArcadeFont.HEART -> out.append(" heart ")
            ArcadeFont.TOKEN -> out.append(" token ")
            ArcadeFont.TICKET -> out.append(" tickets ")
            ArcadeFont.LEFT -> out.append(" left ")
            ArcadeFont.RIGHT -> out.append(" right ")
            ArcadeFont.UP -> out.append(" up ")
            ArcadeFont.DOWN -> out.append(" down ")
            '\n' -> out.append(' ')
            else -> out.append(ch)
        }
    }
    return out.toString().trim().replace(Regex(" {2,}"), " ").lowercase()
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
    /** If set, the text shrinks (never grows) to fit this width. */
    maxWidth: Dp = Dp.Unspecified,
) {
    val density = LocalDensity.current
    val lines = remember(text) { text.split('\n') }
    // The letters are painted, so give a screen reader the words.
    val spoken = remember(text) { spokenText(text) }
    var u = with(density) { unit.toPx() }
    if (maxWidth != Dp.Unspecified) {
        // Width is linear in the unit, shadow pad included, so one scale fits it exactly.
        val natural = (lines.maxOfOrNull { ArcadeFont.width(it, u, tiny) } ?: 0f) + (if (shadow) u * 1.2f else 0f)
        val limit = with(density) { maxWidth.toPx() }
        if (natural > limit && natural > 0f) u *= limit / natural
    }
    val gap = u * 3.2f
    val lineH = ArcadeFont.height(u, tiny) + gap
    val pad = if (shadow) u * 1.2f else 0f
    val widths = lines.map { ArcadeFont.width(it, u, tiny) }
    val w = (widths.maxOrNull() ?: 0f) + pad
    val h = lines.size * lineH - gap + pad
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }
    Canvas(modifier.size(wDp, hDp).semantics { contentDescription = spoken }) {
        lines.forEachIndexed { i, line ->
            val x = if (centered) (w - pad - widths[i]) / 2f else 0f
            val y = i * lineH
            if (shadow) ArcadeFont.drawShadowed(this, line, x, y, u, color, alpha = alpha, tiny = tiny)
            else ArcadeFont.draw(this, line, x, y, u, color, alpha, tiny)
        }
    }
}

/**
 * Press feedback shared by every button: 0 at rest, 1 fully pressed. It springs down fast and
 * back up with a little overshoot (below 0: the cap pops up past rest), so a release feels like
 * a real button. With reduce motion it jumps between the two with no overshoot.
 */
@Composable
private fun rememberPress(pressed: Boolean): State<Float> {
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (!UiMotion.enabled) {
            press.snapTo(if (pressed) 1f else 0f)
        } else if (pressed) {
            press.animateTo(1f, spring(dampingRatio = 1f, stiffness = PRESS_STIFFNESS))
        } else {
            press.animateTo(0f, spring(dampingRatio = RELEASE_DAMPING, stiffness = RELEASE_STIFFNESS))
        }
    }
    return press.asState()
}

/** How hard the button shrinks when fully pressed (a fraction of its size), and how much it brightens. */
private const val PRESS_SHRINK = 0.06f
private const val PRESS_BRIGHTEN = 0.16f
private const val PRESS_STIFFNESS = 1600f
private const val RELEASE_STIFFNESS = 700f
private const val RELEASE_DAMPING = 0.42f

/**
 * A glossy arcade push-button: a candy-coloured cap on a darker skirt. It sinks, shrinks a touch
 * and brightens under your finger, then springs back up past rest and settles.
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
    val press = rememberPress(pressed && enabled)
    val lip = 5.dp
    val spoken = remember(text) { spokenText(text) }
    Box(
        modifier
            // The touch area is the layout box; the spring below only moves what is drawn.
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .graphicsLayer {
                val s = 1f - PRESS_SHRINK * press.value
                scaleX = s
                scaleY = s
            }
            .drawBehind { buttonCap(if (enabled) color else Color(0xFF3A3450), lip.toPx(), press.value, round = false) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // The label rides the cap: up at rest, down when pressed.
                .graphicsLayer {
                    val p = press.value
                    translationY = (-0.5f + 0.8f * p) * lip.toPx()
                }
                // The button says its text once (above); its painted label stays out of the way.
                .clearAndSetSemantics {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ArcadeText(text, color = if (enabled) textColor else Color(0xFF8A84A0), unit = unit, tiny = tiny, centered = true)
        }
    }
}

/** What a screen reader calls a round button, by its icon (icons not listed here go by their name). */
private fun iconLabel(icon: UiIcon): String = when (icon) {
    UiIcon.CLOSE -> "Close"
    UiIcon.TROPHY -> "Profile"
    UiIcon.SOUND -> "Sound on"
    UiIcon.MUTED -> "Sound off"
    UiIcon.EYE -> "Camera view, first person"
    UiIcon.CAMERA -> "Camera view, overhead"
    else -> icon.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
}

/**
 * A round glossy button carrying a vector [icon]. Screen readers say [label] (by default what the
 * icon means: "Close", "Profile", "Sound on"...) and call it a button.
 */
@Composable
fun RoundButton(
    icon: UiIcon,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    label: String = iconLabel(icon),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    val lip = 4.dp
    Canvas(
        modifier
            .size(size, size + lip)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer {
                val sc = 1f - PRESS_SHRINK * press.value
                scaleX = sc
                scaleY = sc
            },
    ) {
        val l = lip.toPx()
        val p = press.value
        buttonCap(color, l, p, round = true)
        val faceY = pressOffset(l, p)
        val c = Offset(this.size.width / 2f, faceY + (this.size.height - l) / 2f)
        drawUiIcon(icon, c + Offset(0f, this.size.width * 0.03f), this.size.width * 0.5f, Color.Black.copy(alpha = 0.3f))
        drawUiIcon(icon, c, this.size.width * 0.5f, Color.White)
    }
}

/** How far the cap has sunk into the skirt at [press] (1 = fully down); the release overshoot lifts it a little above rest. */
internal fun pressOffset(lip: Float, press: Float): Float = if (press >= 0f) lip * 0.8f * press else lip * 0.5f * press

/** The shared look of every button: drop shadow, skirt, gradient cap, gloss and rim, at press depth [press] (0 rest, 1 down). */
private fun DrawScope.buttonCap(color: Color, lip: Float, press: Float, round: Boolean) {
    val w = size.width
    val h = size.height - lip
    val r = if (round) h / 2f else min(16.dp.toPx(), h / 2f)
    val cr = CornerRadius(r, r)
    val faceY = pressOffset(lip, press)
    // The cap brightens as it is pressed, as if the light behind it came up.
    val base = if (press > 0f) color.lift(PRESS_BRIGHTEN * press.coerceAtMost(1f)) else color
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

enum class UiIcon {
    TROPHY, SOUND, MUTED, CLOSE, PAUSE,
    /** First person: walking the hall through your own eyes. */
    EYE,
    /** The overhead camera following you round the hall. */
    CAMERA,
    /** The quick-travel map of the hall. */
    MAP,
    /** The settings screen. */
    GEAR,
}

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
        UiIcon.EYE -> {
            // An almond outline with a round iris and a glint.
            val w = s * 0.46f
            val h = s * 0.26f
            val eye = Path().apply {
                moveTo(c.x - w, c.y)
                quadraticBezierTo(c.x, c.y - h * 2f, c.x + w, c.y)
                quadraticBezierTo(c.x, c.y + h * 2f, c.x - w, c.y)
                close()
            }
            drawPath(eye, color, style = Stroke(s * 0.09f, cap = StrokeCap.Round))
            drawCircle(color, s * 0.17f, c)
            drawCircle(color.copy(alpha = color.alpha * 0.35f), s * 0.07f, c + Offset(s * 0.06f, -s * 0.06f))
        }
        UiIcon.CAMERA -> {
            // A camera body with its viewfinder bump and a solid lens.
            val bw = s * 0.84f
            val bh = s * 0.56f
            val top = c.y - bh / 2f + s * 0.06f
            val body = Stroke(s * 0.09f)
            drawRoundRect(color, Offset(c.x - s * 0.2f, top - s * 0.14f), Size(s * 0.4f, s * 0.16f), CornerRadius(s * 0.05f))
            drawRoundRect(color, Offset(c.x - bw / 2f, top), Size(bw, bh), CornerRadius(s * 0.12f), style = body)
            drawCircle(color, s * 0.15f, Offset(c.x, top + bh / 2f))
            drawCircle(color, s * 0.045f, Offset(c.x + bw * 0.32f, top + s * 0.12f))
        }
        UiIcon.MAP -> drawMapIcon(c, s, color)
        UiIcon.GEAR -> drawGearIcon(c, s, color)
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
 * a glowing edge, a title and a close button. It arrives: the scrim fades in, the card rises and
 * settles with a small overshoot, and the title row lands just after it. Every [GlassBox] inside
 * then slides up in turn, in the order it is composed. With reduce motion it simply fades in.
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
    val progress = rememberEntrance(PANEL_ENTRANCE_MILLIS)
    val entrance = remember { PanelEntrance(progress) }
    CompositionLocalProvider(LocalEntrance provides entrance) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(UiColors.scrim, alpha = clamp01(progress.value * 3.2f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
                    .graphicsLayer {
                        val e = stageOf(progress.value, 0f, 0.55f)
                        alpha = clamp01(e * 2.4f)
                        if (UiMotion.enabled) {
                            val k = easeOutBack(e)
                            val sc = 0.95f + 0.05f * k
                            scaleX = sc
                            scaleY = sc
                            translationY = (1f - k) * 44.dp.toPx()
                        }
                    }
                    .shadow(24.dp, shape, ambientColor = accent, spotColor = accent)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(UiColors.cardTop, UiColors.cardBottom)))
                    .border(2.dp, Brush.verticalGradient(listOf(accent, accent.shade(0.45f))), shape)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().enterStage(progress, 0.1f, 0.5f, rise = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArcadeText(title, color = accent.lift(0.15f), unit = 3.dp)
                    Spacer(Modifier.weight(1f))
                    RoundButton(UiIcon.CLOSE, onClose, Color(Pal.RED), size = 48.dp, label = "Close")
                }
                Spacer(Modifier.size(12.dp))
                content()
            }
        }
    }
}

/** How long a panel takes to arrive, all its staggered pieces included. */
private const val PANEL_ENTRANCE_MILLIS = 620

/**
 * A frosted inset box inside a panel. Inside an [ArcadePanel] it arrives with it: each box
 * composed while the panel is still coming in slides up a beat after the one before it.
 */
@Composable
fun GlassBox(modifier: Modifier = Modifier, highlight: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val entrance = LocalEntrance.current
    val index = remember { entrance?.claim() ?: 0 }
    Column(
        modifier
            .then(if (entrance != null) Modifier.enterStage(entrance.progress, entrance.slotStart(index), entrance.slotStart(index) + 0.3f, rise = 14.dp) else Modifier)
            .clip(shape)
            .background(if (highlight != null) highlight.copy(alpha = 0.16f) else UiColors.glass)
            .border(if (highlight != null) 2.dp else 1.dp, highlight ?: UiColors.glassEdge, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** At this many tokens or fewer the token counter pulses, nudging the player toward the token machine. */
const val LOW_TOKENS = 2

/** The 0..1 breathing of the low-token pulse: idle (a constant 0, and no frames run) unless [active]. */
@Composable
private fun rememberLowPulse(active: Boolean): State<Float> {
    if (!active || !UiMotion.enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "lowTokens").animateFloat(
        0f, 1f, infiniteRepeatable(tween(820, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lowTokensPulse",
    )
}

/**
 * Token and ticket counters shown side by side. The numbers roll like an odometer whenever they
 * change ([RollingNumber]), calling [onTick] as they pass values, and the token count breathes
 * while it is at [LOW_TOKENS] or under and turns red at zero. [tokenIconModifier] and
 * [ticketIconModifier] go on the icons, for a screen that needs to know where they are (a coin
 * flying into a counter).
 */
@Composable
fun CurrencyRow(
    tokens: Int,
    tickets: Int,
    modifier: Modifier = Modifier,
    unit: Dp = 3.dp,
    onTick: (() -> Unit)? = null,
    tokenIconModifier: Modifier = Modifier,
    ticketIconModifier: Modifier = Modifier,
) {
    val pulse = rememberLowPulse(tokens <= LOW_TOKENS)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.graphicsLayer {
                val s = 1f + 0.1f * pulse.value
                scaleX = s
                scaleY = s
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenIcon(unit * 9f, tokenIconModifier)
            Spacer(Modifier.width(6.dp))
            RollingNumber(tokens, if (tokens == 0) Color(0xFFFF7A66) else Color(0xFFFFD35A), unit, onTick = onTick)
        }
        Spacer(Modifier.width(18.dp))
        TicketIcon(unit * 8f, ticketIconModifier)
        Spacer(Modifier.width(6.dp))
        RollingNumber(tickets, Color(0xFFFFA24A), unit, onTick = onTick)
    }
}

/**
 * The short notice at the top of the screen ("DAILY BONUS", "COMING SOON"): [text] null hides it.
 * It drops in with a little spring, and fades away rather than vanishing, keeping the words it
 * last showed while it goes. A new text while it is up gives it a small pop. Reduce motion cuts
 * straight between shown and hidden.
 */
@Composable
fun ArcadeBanner(text: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(text ?: "") }
    if (text != null) last = text
    val shown = remember { Animatable(if (text != null) 1f else 0f) }
    LaunchedEffect(text) {
        if (!UiMotion.enabled) {
            shown.snapTo(if (text != null) 1f else 0f)
        } else if (text != null) {
            // Dropping in from above, or a quick re-pop if it was already up with other words.
            if (shown.value > 0.5f) shown.snapTo(0.82f)
            shown.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        } else {
            shown.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    if (text != null || shown.value > 0.002f) {
        Box(
            modifier.graphicsLayer {
                val p = shown.value
                alpha = p.coerceIn(0f, 1f)
                translationY = -(1f - p) * 36.dp.toPx()
                val sc = 0.94f + 0.06f * p
                scaleX = sc
                scaleY = sc
            },
        ) {
            GlassBox(Modifier.background(Color(0xE6120C22), RoundedCornerShape(16.dp)), highlight = Color(Pal.YELLOW)) {
                ArcadeText(last, unit = 2.2.dp, color = Color(Pal.YELLOW), centered = true)
            }
        }
    }
}

// ---------------------------------------------------------------- entrances

/** How far through the slot [from]..[to] a progress [p] is, 0..1: one step of a staggered sequence. */
fun stageOf(p: Float, from: Float, to: Float): Float = clamp01((p - from) / (to - from).coerceAtLeast(1e-4f))

/**
 * A 0..1 progress that runs once, linearly over [millis], when first composed (at once with reduce
 * motion). Give it to [enterStage] for each piece of a card that should arrive in turn.
 */
@Composable
fun rememberEntrance(millis: Int = 700): State<Float> {
    val progress = remember { Animatable(if (UiMotion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (UiMotion.enabled) progress.animateTo(1f, tween(millis, easing = LinearEasing)) else progress.snapTo(1f)
    }
    return progress.asState()
}

/**
 * Brings a piece of a card in during [from]..[to] of an [entrance]: it fades in and rises [rise]
 * into place, or with [pop] scales up from 70% with a little overshoot. Reduce motion only fades.
 * Reads the progress while drawing, so it costs no recomposition.
 */
fun Modifier.enterStage(entrance: State<Float>, from: Float, to: Float, rise: Dp = 16.dp, pop: Boolean = false): Modifier =
    graphicsLayer {
        val e = stageOf(entrance.value, from, to)
        alpha = clamp01(e * 2.2f)
        if (UiMotion.enabled) {
            val k = if (pop) easeOutBack(e) else easeOutCubic(e)
            translationY = (1f - k) * rise.toPx()
            if (pop) {
                val s = 0.7f + 0.3f * k
                scaleX = s
                scaleY = s
            }
        }
    }

/**
 * The entrance of the panel a composable sits in: its [progress] and a counter that hands the
 * pieces inside their place in the queue ([claim]), so they arrive one after another.
 */
class PanelEntrance(val progress: State<Float>) {
    private var next = 0

    /** The next free place in the queue (0 for the first piece). */
    fun claim(): Int = next++

    /** When (0..1 of the entrance) the piece at [index] starts arriving: a step behind the one before, never later than 0.7. */
    fun slotStart(index: Int): Float = (0.2f + 0.06f * index).coerceAtMost(0.7f)
}

/** The entrance of the panel this is inside, if any; [GlassBox] and [staggerIn] join it. */
val LocalEntrance = compositionLocalOf<PanelEntrance?> { null }

/**
 * Brings a piece of a panel in after the pieces before it: item [index] of a list starts
 * [index] steps after the first, sliding up and fading in. Does nothing outside a panel that
 * provides [LocalEntrance] ([ArcadePanel] does).
 */
@Composable
fun Modifier.staggerIn(index: Int, rise: Dp = 14.dp): Modifier {
    val entrance = LocalEntrance.current ?: return this
    val start = entrance.slotStart(index)
    return enterStage(entrance.progress, start, start + 0.3f, rise)
}

/**
 * Pops the composable in after [delayMillis]: fades up from 70% scale with a springy overshoot.
 * The animation restarts whenever [key] changes (give it `visible` to pop things in as they
 * become available). Reduce motion shows it at once.
 */
fun Modifier.popIn(delayMillis: Int = 0, key: Any? = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(if (UiMotion.enabled) 0f else 1f) }
    LaunchedEffect(key) {
        if (UiMotion.enabled) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        } else {
            progress.snapTo(1f)
        }
    }
    graphicsLayer {
        val v = progress.value
        alpha = clamp01(v * 1.8f)
        if (UiMotion.enabled) {
            val s = 0.7f + 0.3f * v
            scaleX = s
            scaleY = s
            translationY = (1f - v.coerceAtMost(1f)) * 18.dp.toPx()
        }
    }
}

/** Takes up its space in the layout but draws and takes touches only while [show]: buttons that arrive later must not move what is above them. */
fun Modifier.reserveSpace(show: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (show) placeable.place(0, 0) }
}
