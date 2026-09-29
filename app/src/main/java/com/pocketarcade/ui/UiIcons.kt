package com.pocketarcade.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    /** A padlock: something not yet unlocked. */
    LOCK,
    /** A tick: owned, done, on. */
    CHECK,
    /** A five-point star: favourites, rarity, best scores. */
    STAR,
    /** A twinkle: new, special. */
    SPARKLE,
    /** A play triangle. */
    PLAY,
}

/** The ink for a glyph's small details (a keyhole, a star stamped on a cup): dark, and as faint as its glyph is. */
private fun detailOf(glyph: Color): Color = Color(0xFF1B1030).copy(alpha = 0.32f * glyph.alpha)

/** A five-point star of outer radius [r] as a path, point up, its inner points at 45% of it. */
private fun starPath(c: Offset, r: Float): Path {
    val p = Path()
    for (k in 0 until 10) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val rr = if (k % 2 == 0) r else r * 0.45f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/**
 * A four-point twinkle of reach [r] round [c]: the concave star that says "shiny". Used for
 * glints on tokens, tickets and new things.
 */
fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    if (r <= 0.3f) return
    // How close the flanks pinch toward the middle: small is spiky, large is fat.
    val k = 0.16f
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * k, c.y - r * k, c.x + r, c.y)
        quadraticTo(c.x + r * k, c.y + r * k, c.x, c.y + r)
        quadraticTo(c.x - r * k, c.y + r * k, c.x - r, c.y)
        quadraticTo(c.x - r * k, c.y - r * k, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/** Simple glyphs for round buttons, fitting a box [s] across centred on [c]. */
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
                quadraticTo(c.x, c.y - h * 2f, c.x + w, c.y)
                quadraticTo(c.x, c.y + h * 2f, c.x - w, c.y)
                close()
            }
            drawPath(eye, color, style = Stroke(s * 0.09f, cap = StrokeCap.Round))
            drawCircle(color, s * 0.17f, c)
            drawCircle(detailOf(color), s * 0.07f, c + Offset(s * 0.06f, -s * 0.06f))
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
            drawCircle(detailOf(color), s * 0.06f, Offset(c.x - s * 0.03f, top + bh / 2f - s * 0.03f))
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
            // A star stamped on the cup.
            drawPath(starPath(c + Offset(0f, -s * 0.14f), s * 0.14f), detailOf(color))
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
        UiIcon.LOCK -> {
            val w = s * 0.56f
            val bodyTop = c.y - s * 0.04f
            val shackle = Stroke(s * 0.1f, cap = StrokeCap.Round)
            // The shackle: a half ring over two short legs, then the body over their feet.
            drawArc(color, 180f, 180f, false, Offset(c.x - w * 0.32f, c.y - s * 0.4f), Size(w * 0.64f, s * 0.62f), style = shackle)
            drawLine(color, Offset(c.x - w * 0.32f, c.y - s * 0.09f), Offset(c.x - w * 0.32f, bodyTop + s * 0.04f), s * 0.1f)
            drawLine(color, Offset(c.x + w * 0.32f, c.y - s * 0.09f), Offset(c.x + w * 0.32f, bodyTop + s * 0.04f), s * 0.1f)
            drawRoundRect(color, Offset(c.x - w / 2f, bodyTop), Size(w, s * 0.44f), CornerRadius(s * 0.08f))
            val ink = detailOf(color)
            drawCircle(ink, s * 0.07f, Offset(c.x, bodyTop + s * 0.17f))
            drawLine(ink, Offset(c.x, bodyTop + s * 0.17f), Offset(c.x, bodyTop + s * 0.32f), s * 0.06f, StrokeCap.Round)
        }
        UiIcon.CHECK -> {
            val tick = Path().apply {
                moveTo(c.x - s * 0.3f, c.y + s * 0.02f)
                lineTo(c.x - s * 0.08f, c.y + s * 0.25f)
                lineTo(c.x + s * 0.32f, c.y - s * 0.24f)
            }
            drawPath(tick, color, style = Stroke(s * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        UiIcon.STAR -> {
            val star = starPath(c + Offset(0f, s * 0.03f), s * 0.44f)
            drawPath(star, color)
            // Soften the points.
            drawPath(star, color, style = Stroke(s * 0.07f, join = StrokeJoin.Round))
        }
        UiIcon.SPARKLE -> {
            drawSparkle(c + Offset(-s * 0.06f, s * 0.05f), s * 0.42f, color)
            drawSparkle(c + Offset(s * 0.3f, -s * 0.3f), s * 0.17f, color)
        }
        UiIcon.PLAY -> {
            val tri = Path().apply {
                moveTo(c.x - s * 0.22f, c.y - s * 0.32f)
                lineTo(c.x + s * 0.34f, c.y)
                lineTo(c.x - s * 0.22f, c.y + s * 0.32f)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(s * 0.08f, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------- currency

/** How big the token's glint is when it isn't twinkling (0..1 of its full size). */
private const val TOKEN_GLINT_REST = 0.5f

/** How long one twinkle cycle of a token icon takes, and how much of it is the twinkle itself. */
private const val TWINKLE_MILLIS = 3600
private const val TWINKLE_SHARE = 0.16f

/**
 * The glint scale (between [TOKEN_GLINT_REST] and 1) at [phase] (0..1) of a twinkle cycle: rest
 * for most of it, then a quick swell and fade. Pure so it can be tested.
 */
internal fun twinkle(phase: Float): Float {
    val p = phase.coerceIn(0f, 1f)
    if (p >= TWINKLE_SHARE) return TOKEN_GLINT_REST
    val spike = sin(p / TWINKLE_SHARE * PI.toFloat())
    return TOKEN_GLINT_REST + (1f - TOKEN_GLINT_REST) * spike
}

/**
 * A gold arcade token, [r] in radius round [c]: a milled rim, a bevelled face with a raised star,
 * a crescent of gloss and a four-point glint that [glint] (0..1) scales.
 */
fun DrawScope.drawToken(c: Offset, r: Float, glint: Float = TOKEN_GLINT_REST) {
    // The coin's edge, seen below the face.
    drawCircle(Color(0xFF6E4006), r, c + Offset(0f, r * 0.1f))
    // The rim: a lit-from-above gradient.
    drawCircle(
        Brush.linearGradient(listOf(Color(0xFFFFF3B8), Color(0xFFE9A821), Color(0xFF8A5008)), start = c - Offset(r, r), end = c + Offset(r, r)),
        r, c,
    )
    // Milling: short ticks round the rim (skipped when the coin is too small for them to read).
    if (r >= 9f) {
        val ticks = 26
        val inner = r * 0.86f
        val outer = r * 0.97f
        val w = (r * 0.055f).coerceAtLeast(1f)
        for (k in 0 until ticks) {
            val a = k * 2f * PI.toFloat() / ticks
            val ca = cos(a)
            val sa = sin(a)
            // Ticks on the lit side catch light; the far side sinks into shadow.
            val lit = (-ca - sa) * 0.5f
            val tone = if (lit > 0f) Color(0xFFFFF0B0).copy(alpha = 0.5f * lit) else Color(0xFF5A3204).copy(alpha = 0.45f * -lit)
            drawLine(tone, c + Offset(ca * inner, sa * inner), c + Offset(ca * outer, sa * outer), w)
        }
    }
    val face = r * 0.8f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE89A), Color(0xFFF5B82E), Color(0xFFC77A12)), c - Offset(face * 0.35f, face * 0.4f), face * 1.7f),
        face, c,
    )
    // A bevelled groove: shadow along the lower right, light along the upper left.
    val ring = face * 0.84f
    val groove = Stroke((r * 0.085f).coerceAtLeast(1f))
    drawArc(Color(0xFF8E520A), 20f, 160f, false, c - Offset(ring, ring), Size(ring * 2f, ring * 2f), style = groove)
    drawArc(Color(0xFFFFF0B0).copy(alpha = 0.85f), 200f, 160f, false, c - Offset(ring, ring), Size(ring * 2f, ring * 2f), style = groove)
    // The raised star, with its own little shadow and a lit top edge.
    val star = starPath(c + Offset(0f, r * 0.03f), r * 0.48f)
    translate(r * 0.03f, r * 0.05f) { drawPath(star, Color(0xFF8A4E08).copy(alpha = 0.8f)) }
    drawPath(star, Brush.verticalGradient(listOf(Color(0xFFD98A16), Color(0xFFB36A0C)), startY = c.y - r * 0.45f, endY = c.y + r * 0.45f))
    rotate(-35f, c) {
        drawOval(Color.White.copy(alpha = 0.42f), c + Offset(-r * 0.55f, -r * 0.84f), Size(r * 0.7f, r * 0.24f))
    }
    if (glint > 0.02f) drawSparkle(c + Offset(r * 0.56f, -r * 0.6f), r * 0.44f * glint, Color.White.copy(alpha = 0.92f))
}

/**
 * A prize ticket [w] wide centred on [c]: notched ends, a perforated stub, a printed border, a
 * gradient star and a band of sheen.
 */
fun DrawScope.drawTicket(c: Offset, w: Float) {
    val h = w * 0.58f
    val tl = c - Offset(w / 2f, h / 2f)
    val notch = h * 0.18f
    val shape = Path().apply {
        addRoundRect(RoundRect(tl.x, tl.y, tl.x + w, tl.y + h, CornerRadius(h * 0.12f)))
    }
    val cut = Path().apply {
        addOval(Rect(Offset(tl.x, c.y), notch))
        addOval(Rect(Offset(tl.x + w, c.y), notch))
    }
    val ticket = Path.combine(PathOperation.Difference, shape, cut)
    rotate(-8f, c) {
        drawPath(ticket, Color(0xFF7A2A06), alpha = 0.6f)
        translate(0f, -h * 0.07f) {
            drawPath(ticket, Brush.verticalGradient(listOf(Color(0xFFFFC070), Color(0xFFF07A1A), Color(0xFFD9600C)), startY = tl.y, endY = tl.y + h))
            // The stub's perforation: a column of pin-holes a quarter of the way in.
            val perfX = tl.x + w * 0.24f
            val holes = 5
            for (k in 0 until holes) {
                drawCircle(Color(0xFF8A3406).copy(alpha = 0.55f), h * 0.035f, Offset(perfX, tl.y + h * (0.16f + 0.68f * k / (holes - 1))))
            }
            drawRoundRect(Color(0xFFFFE0A8), tl + Offset(w * 0.32f, h * 0.2f), Size(w * 0.52f, h * 0.6f), CornerRadius(h * 0.08f), style = Stroke(h * 0.06f))
            val starC = Offset(tl.x + w * 0.58f, c.y)
            drawPath(starPath(starC, h * 0.21f), Brush.verticalGradient(listOf(Color(0xFFD03A0C), Color(0xFF9A2606)), startY = c.y - h * 0.2f, endY = c.y + h * 0.2f))
            // A band of sheen across the upper half.
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0f)), startY = tl.y, endY = tl.y + h * 0.5f),
                tl + Offset(w * 0.03f, h * 0.04f), Size(w * 0.94f, h * 0.42f), CornerRadius(h * 0.1f),
            )
        }
    }
}

/**
 * The token, [size] across, on a soft gold glow. With [twinkle] a glint sweeps its face every few
 * seconds (never with reduce motion).
 */
@Composable
fun TokenIcon(size: Dp, modifier: Modifier = Modifier, twinkle: Boolean = false) {
    val phase = if (twinkle && UiMotion.enabled) {
        rememberInfiniteTransition(label = "tokenTwinkle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(TWINKLE_MILLIS, easing = LinearEasing)), label = "tokenTwinklePhase",
        )
    } else {
        null
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f * 0.92f
        glowCircle(UiColors.token, center, r, r * 0.7f, 0.28f)
        drawToken(center, r, if (phase != null) twinkle(phase.value) else TOKEN_GLINT_REST)
    }
}

/** The prize ticket, [size] tall, on a soft orange glow. */
@Composable
fun TicketIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size * 1.3f, size)) {
        val w = this.size.width * 0.95f
        glowCircle(UiColors.ticket, center, w * 0.36f, w * 0.26f, 0.22f)
        drawTicket(center, w)
    }
}
