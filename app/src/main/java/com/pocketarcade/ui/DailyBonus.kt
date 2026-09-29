package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.rememberGameLoop
import kotlin.math.abs
import kotlin.math.cos

/**
 * The daily bonus moment as a timeline: tokens tumble into a card one after another, a counter
 * ticks up as each one lands, the card holds a moment and leaves. Pure functions of time, so the
 * pacing is testable; under reduce motion (the `calm` variants) the tokens simply fade in with the
 * card, the counter reads its final value from the start and nothing falls or flips.
 */
object DailyBonus {
    /** Most tokens shown tumbling (a bigger bonus is shown as this many, the counter still reaching the real total). */
    const val MAX_TOKENS = 10

    /** A token's fall, from the moment it is let go, and the gap between one and the next. */
    const val FALL_SECONDS = 0.6f
    const val STAGGER = 0.12f

    /** The card takes this long to arrive, holds after the last token has settled, and takes this long to leave. */
    const val ARRIVE_SECONDS = 0.35f
    const val HOLD_SECONDS = 1.7f
    const val LEAVE_SECONDS = 0.45f

    /** Calm: the card fades in over [CALM_ARRIVE_SECONDS] and holds for [CALM_HOLD_SECONDS]. */
    const val CALM_ARRIVE_SECONDS = 0.5f
    const val CALM_HOLD_SECONDS = 2.2f

    /** How long the counter's bump lasts after each landing. */
    const val PULSE_SECONDS = 0.18f

    /** Half-turns a token spins in its fall (it lands face up). */
    private const val SPINS = 2.5f

    /** Where in its fall a token first touches down (the bounce curve's first contact). */
    const val CONTACT = 1f / 2.75f

    /** How many tokens tumble for a bonus of [granted]. */
    fun tokenCount(granted: Int): Int = granted.coerceIn(0, MAX_TOKENS)

    /** When token [i] is let go. */
    fun startTime(i: Int): Float = ARRIVE_SECONDS * 0.6f + i * STAGGER

    /** When token [i] first touches down: the moment it clinks and the counter ticks. */
    fun contactTime(i: Int): Float = startTime(i) + FALL_SECONDS * CONTACT

    /** When the last token of a bonus of [granted] has settled (0 with none). */
    fun settledTime(granted: Int): Float {
        val n = tokenCount(granted)
        return if (n == 0) 0f else startTime(n - 1) + FALL_SECONDS
    }

    /** How many tokens have touched down by [t] for a bonus of [granted]. */
    fun landed(t: Float, granted: Int): Int {
        val n = tokenCount(granted)
        var count = 0
        while (count < n && contactTime(count) <= t) count++
        return count
    }

    /** The bonus counted so far at [t]: rises a step as each token lands and reads exactly [granted] once all have. */
    fun counter(t: Float, granted: Int, calm: Boolean): Int {
        if (calm) return granted
        val n = tokenCount(granted)
        if (n == 0) return granted
        val l = landed(t, granted)
        return if (l >= n) granted else granted * l / n
    }

    /** Seconds from the card appearing to it being gone. */
    fun totalSeconds(granted: Int, calm: Boolean): Float =
        if (calm) CALM_ARRIVE_SECONDS + CALM_HOLD_SECONDS + LEAVE_SECONDS else settledTime(granted) + HOLD_SECONDS + LEAVE_SECONDS

    /** The card's opacity at [t]: in over the arrival, out over the leave. */
    fun cardAlpha(t: Float, granted: Int, calm: Boolean): Float {
        val arrive = if (calm) CALM_ARRIVE_SECONDS else ARRIVE_SECONDS
        val total = totalSeconds(granted, calm)
        return smoothstep(0f, arrive, t) * (1f - smoothstep(total - LEAVE_SECONDS, total, t))
    }

    /** How far token fall progress [p] (0..1) has got: drops in and bounces, coming to rest at 1. */
    fun bounce(p: Float): Float {
        var x = clamp01(p)
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { x -= 1.5f / d1; n1 * x * x + 0.75f }
            x < 2.5f / d1 -> { x -= 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { x -= 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }

    /** A token's width as it flips in its fall (1 face on, near 0 edge on); exactly 1 at rest. */
    fun flip(p: Float): Float = if (p >= 1f) 1f else abs(cos(TAU * SPINS * (1f - clamp01(p)) / 2f)).coerceAtLeast(0.12f)

    /** Token [i]'s fall progress at [t]: 0 before it is let go, 1 once it has settled. */
    fun fallProgress(i: Int, t: Float): Float = clamp01((t - startTime(i)) / FALL_SECONDS)

    /** The counter's bump at [t] (1 the instant a token lands, easing to 0 over [PULSE_SECONDS]). */
    fun pulse(t: Float, granted: Int): Float {
        val l = landed(t, granted)
        if (l == 0) return 0f
        val since = t - contactTime(l - 1)
        if (since >= PULSE_SECONDS) return 0f
        val x = 1f - since / PULSE_SECONDS
        return x * x
    }

    /** Where token [i] of [n] rests across a card [w] wide, as a share of the width. */
    fun restX(i: Int, n: Int): Float = 0.12f + 0.76f * (i + 0.5f) / n.coerceAtLeast(1)

    /** How much token [i] sits up from the card's baseline, as a share of its radius: a loose heap, not a ruler. */
    fun restLift(i: Int): Float = (i % 2) * 0.5f + hash01(i, 9, 33) * 0.3f
}

private class RevealState {
    var t = 0f
    var sounded = 0
    var done = false
}

private val Gold = Color(Pal.GOLD)

/**
 * The daily bonus: a card that drops in with [granted] tokens tumbling into it, a "+N" counter
 * ticking up as each lands ([onLand] is called with each token's index, for a clink; [onSettled]
 * once when the last has landed, for the chime), then leaving ([onDone]). With [calm] (reduce
 * motion) it fades in with the tokens at rest and the total showing, and [onSettled] follows at once.
 * Position it with [modifier].
 */
@Composable
fun DailyBonusReveal(
    granted: Int,
    calm: Boolean,
    onLand: (Int) -> Unit,
    onSettled: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(granted) { RevealState() }
    val land by rememberUpdatedState(onLand)
    val settled by rememberUpdatedState(onSettled)
    val finish by rememberUpdatedState(onDone)
    val frame = rememberGameLoop(state, calm) { dt ->
        if (!state.done) {
            state.t += dt
            if (calm) {
                if (state.sounded == 0) {
                    state.sounded = 1
                    settled()
                }
            } else {
                val l = DailyBonus.landed(state.t, granted)
                while (state.sounded < l) {
                    land(state.sounded)
                    state.sounded++
                    if (state.sounded == DailyBonus.tokenCount(granted)) settled()
                }
            }
            if (state.t >= DailyBonus.totalSeconds(granted, calm)) {
                state.done = true
                finish()
            }
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Canvas(
        modifier
            .width(268.dp)
            .height(146.dp)
            .graphicsLayer {
                frame.value
                val a = DailyBonus.cardAlpha(state.t, granted, calm)
                alpha = a
                translationY = if (calm) 0f else -(1f - a) * 24.dp.toPx()
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF0261C48), Color(0xF0120C22))))
            .border(2.dp, Gold, shape)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Daily bonus: $granted free tokens."
            },
    ) {
        frame.value
        drawReveal(state.t, granted, calm)
    }
}

private fun DrawScope.drawReveal(t: Float, granted: Int, calm: Boolean) {
    val w = size.width
    val unit = 3.2.dp.toPx()

    // The tokens, falling into a heap along the card's baseline.
    val n = DailyBonus.tokenCount(granted)
    val r = 11.dp.toPx()
    val floorY = 80.dp.toPx()
    val fade = if (calm) smoothstep(0f, DailyBonus.CALM_ARRIVE_SECONDS, t) else 1f
    for (i in 0 until n) {
        val p = if (calm) 1f else DailyBonus.fallProgress(i, t)
        if (p <= 0f) continue
        val x = w * DailyBonus.restX(i, n)
        val restY = floorY - DailyBonus.restLift(i) * r
        val y = -r + (restY + r) * DailyBonus.bounce(p)
        val flat = if (calm) 1f else DailyBonus.flip(p)
        val centre = Offset(x, y)
        withTransform({ scale(flat, 1f, centre) }) {
            fadedToken(this, centre, r, if (calm) fade else clamp01(p * 6f))
        }
    }

    ArcadeFont.drawCentered(this, "DAILY BONUS", w / 2f, 10.dp.toPx(), unit, Color(Pal.YELLOW))
    // The counter, bumped a little as each token lands.
    val shown = DailyBonus.counter(t, granted, calm)
    val bump = if (calm) 0f else DailyBonus.pulse(t, granted)
    val base = unit * 1.25f
    val u = base * (1f + 0.16f * bump)
    // Grown about its middle, not its top, so the bump doesn't shove the line down.
    val y = 94.dp.toPx() - (u - base) * ArcadeFont.CAP / 2f
    ArcadeFont.drawCentered(this, "${ArcadeFont.TOKEN} +$shown", w / 2f, y, u, Gold)
    ArcadeFont.drawCentered(this, "SEE YOU TOMORROW FOR MORE!", w / 2f, 130.dp.toPx(), 1.5.dp.toPx(), Color(0xFFC9B8FF), tiny = true)
}

/** A token at [c] of radius [r], faded to [alpha]. */
private fun fadedToken(scope: DrawScope, c: Offset, r: Float, alpha: Float) {
    with(scope) {
        if (alpha < 0.999f) {
            // Only while fading in: the real token has a gradient we can't fade cheaply, so a flat disc stands in.
            drawCircle(Color(0xFFF5B82E), r, c, alpha = alpha)
            drawCircle(Color(0xFF9A5A0A), r * 0.74f, c, alpha = alpha * 0.8f, style = Stroke(r * 0.1f))
        } else {
            drawToken(c, r)
        }
    }
}
