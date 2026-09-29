package com.pocketarcade.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.Pal

// Small pieces the screens are built from: a rule, a chip, a bar, a ring, a switch, a card frame.

/** A hairline rule that starts at [color] on the left and fades away to the right. */
@Composable
fun ArcadeDivider(color: Color = UiColors.glassEdge, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(UiEdge.line)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.3f), Color.Transparent))),
    )
}

/** A small heading over a section: an accent tick, the [text] and a rule running on after it. */
@Composable
fun SectionHeader(text: String, color: Color, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 14.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(UiSpace.sm))
        ArcadeText(text, UiText.HEADING, color = color.lift(0.15f))
        Spacer(Modifier.width(UiSpace.sm))
        Box(
            Modifier
                .weight(1f)
                .height(UiEdge.hair)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent))),
        )
        if (trailing != null) {
            Spacer(Modifier.width(UiSpace.sm))
            ArcadeText(trailing, UiText.LABEL, color = UiColors.textMid)
        }
    }
}

/**
 * A small pill label: tinted glass with a coloured edge, or with [filled] a solid gradient with
 * dark type (for the one tag that must be seen, like a price or NEW). [text] shrinks to fit.
 */
@Composable
fun ArcadeChip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false, style: UiText = UiText.CAPTION) {
    val shape = RoundedCornerShape(UiRadius.chip)
    Box(
        modifier
            .clip(shape)
            .background(
                if (filled) Brush.verticalGradient(listOf(color.lift(0.28f), color.shade(0.82f)))
                else Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.1f))),
            )
            .border(UiEdge.hair, if (filled) color.lift(0.45f) else color.copy(alpha = 0.7f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        ArcadeText(text, style, color = if (filled) Color(0xFF1B1030) else color.lift(0.3f), shadow = !filled, centered = true)
    }
}

/** How long a bar takes to catch up with its new value. */
private const val PROGRESS_MILLIS = 420

/**
 * A progress bar: a sunken track and a glossy fill in [color] that eases to [fraction] (0..1)
 * and glows at its leading edge. [description] is what a screen reader says with the value.
 */
@Composable
fun ArcadeProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, description: String = "Progress") {
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        target, if (UiMotion.enabled) tween(PROGRESS_MILLIS, easing = FastOutSlowInEasing) else snap(), label = "progress",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
    ) {
        val r = size.height / 2f
        val cr = CornerRadius(r, r)
        drawRoundRect(UiColors.well, Offset.Zero, size, cr)
        drawRoundRect(UiColors.wellEdge, Offset.Zero, size, cr, style = Stroke(1.dp.toPx()))
        val fill = size.width * shown
        if (fill > 1f) {
            val w = maxOf(fill, size.height)
            glowRoundRect(color, Offset.Zero, Size(w, size.height), r, 5.dp.toPx(), 0.22f)
            drawRoundRect(
                Brush.verticalGradient(listOf(color.lift(0.35f), color, color.shade(0.7f)), startY = 0f, endY = size.height),
                Offset.Zero, Size(w, size.height), cr,
            )
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), startY = 0f, endY = size.height * 0.55f),
                Offset(r * 0.4f, size.height * 0.12f), Size((w - r * 0.8f).coerceAtLeast(0f), size.height * 0.42f), CornerRadius(r * 0.5f),
            )
        }
    }
}

/**
 * A ring that fills clockwise from the top as [fraction] (0..1) grows, its head glowing, with
 * [content] (a number, an icon) in the middle. [size] is the ring's diameter.
 */
@Composable
fun CountdownRing(
    fraction: Float,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    thickness: Dp = 7.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        target, if (UiMotion.enabled) tween(PROGRESS_MILLIS, easing = FastOutSlowInEasing) else snap(), label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val t = thickness.toPx()
            val d = this.size.minDimension - t
            val tl = Offset((this.size.width - d) / 2f, (this.size.height - d) / 2f)
            drawCircle(UiColors.well, d / 2f + t / 2f, center)
            drawArc(UiColors.wellEdge, 0f, 360f, false, tl, Size(d, d), style = Stroke(t))
            if (shown > 0.003f) {
                drawArc(
                    Brush.sweepGradient(listOf(color.shade(0.7f), color.lift(0.25f), color.shade(0.7f)), center),
                    -90f, 360f * shown, false, tl, Size(d, d), style = Stroke(t, cap = StrokeCap.Round),
                )
                // The head of the arc catches the light.
                val a = Math.toRadians((-90f + 360f * shown).toDouble())
                val head = Offset(center.x + Math.cos(a).toFloat() * d / 2f, center.y + Math.sin(a).toFloat() * d / 2f)
                glowCircle(color, head, t * 0.5f, t * 1.2f, 0.4f)
                drawCircle(Color.White.copy(alpha = 0.85f), t * 0.22f, head)
            }
        }
        content()
    }
}

/**
 * A switch: a track that fills with [accent] and a glossy knob that springs across. The whole
 * 48 dp-high row is the touch target, and a screen reader hears [label] and its state.
 */
@Composable
fun ArcadeToggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, accent: Color = Color(Pal.GREEN)) {
    val t by animateFloatAsState(
        if (checked) 1f else 0f,
        if (UiMotion.enabled) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "toggle",
    )
    Box(
        modifier
            .size(64.dp, 48.dp)
            .toggleable(
                value = checked, interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Switch, onValueChange = onChange,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(52.dp, 30.dp)) {
            val h = size.height
            val r = h / 2f
            val on = t.coerceIn(0f, 1f)
            drawRoundRect(UiColors.well, Offset.Zero, size, CornerRadius(r))
            drawRoundRect(accent.copy(alpha = 0.85f * on), Offset.Zero, size, CornerRadius(r))
            glowRoundRect(accent, Offset.Zero, size, r, 6.dp.toPx(), 0.3f * on)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset.Zero, size, CornerRadius(r), style = Stroke(1.dp.toPx()))
            val pad = 3.dp.toPx()
            val kr = r - pad
            val cx = pad + kr + (size.width - pad * 2f - kr * 2f) * t
            val c = Offset(cx, r)
            drawCircle(Color.Black.copy(alpha = 0.35f), kr, c + Offset(0f, 1.5.dp.toPx()))
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFCFC7E6)), startY = c.y - kr, endY = c.y + kr), kr, c)
            drawCircle(Color.White.copy(alpha = 0.7f), kr, c, style = Stroke(1.dp.toPx()))
        }
    }
}

/**
 * The frame of a card in a grid: glass lit from the top, with a hairline bevel, [edge] round it
 * (a strong edge when [thick]), a glow of it at [glow] strength when above 0 and, if given, a
 * wash of [tint] over the glass (rarity, ownership).
 */
fun Modifier.cardFrame(edge: Color, thick: Boolean = false, glow: Float = 0f, tint: Color = Color.Transparent): Modifier {
    val shape = RoundedCornerShape(UiRadius.card)
    return this
        .then(if (glow > 0f) Modifier.uiGlow(edge, UiRadius.card, 7.dp, glow) else Modifier)
        .clip(shape)
        .background(
            if (tint.alpha > 0f) Brush.verticalGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.07f)))
            else Brush.verticalGradient(listOf(UiColors.glassHi, UiColors.glassLo)),
        )
        .drawBehind { paintGlassBevel(UiRadius.card.toPx()) }
        .border(if (thick) UiEdge.strong else UiEdge.hair, edge, shape)
}
