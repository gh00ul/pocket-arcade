package com.pocketarcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.ScreenShake
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Whether the interface may move: false with the reduce-motion setting (the same switch that
 * silences the screen shake). Read where the motion is decided, not remembered, so the setting
 * takes effect at once.
 */
object UiMotion {
    val enabled: Boolean get() = ScreenShake.intensity > 0f
}

/**
 * The maths of a mechanical counter, so a number can roll instead of jumping. Each digit is a
 * wheel: the units wheel turns continuously with the value, and a higher wheel only turns during
 * the last whole unit before it carries over (19 to 20 for the tens wheel, 99 to 100 for the
 * hundreds), exactly like an odometer. At a whole number every wheel is at rest on its digit.
 */
internal object Odometer {
    private val POW10 = floatArrayOf(1f, 10f, 100f, 1_000f, 10_000f, 100_000f, 1_000_000f, 10_000_000f, 100_000_000f, 1_000_000_000f)

    /** How many digits [n] is written with (at least 1). */
    fun digitCount(n: Int): Int {
        var c = 1
        var v = n.coerceAtLeast(0)
        while (v >= 10) {
            v /= 10; c++
        }
        return c
    }

    /** The whole part of what the wheel at [place] (0 = units) has passed: its digit, before the mod 10. */
    fun turns(value: Float, place: Int): Int = floor(value.coerceAtLeast(0f) / POW10[place.coerceIn(0, POW10.size - 1)]).toInt()

    /** How far the wheel at [place] has rolled from its digit toward the next, 0..1, eased at both ends. */
    fun roll(value: Float, place: Int): Float {
        val v = value.coerceAtLeast(0f)
        val pow = POW10[place.coerceIn(0, POW10.size - 1)]
        // The remainder is exact in floats, so a whole number leaves every wheel at exactly 0.
        val raw = if (place == 0) v - floor(v) else ((v % pow) - (pow - 1f)).coerceIn(0f, 1f)
        return raw * raw * (3f - 2f * raw)
    }
}

/** Every digit as a string, so drawing a wheel never builds one. */
private val DIGITS = Array(10) { it.toString() }

/** How long the roll from one value to another takes: quick for a nudge, longer for a big jump. */
internal fun rollMillis(from: Float, to: Float): Int = (260f + 70f * sqrt(abs(to - from))).coerceIn(300f, 1100f).toInt()

private const val TICK_GAP_NANOS = 45_000_000L
private const val BUMP_PEAK = 1.14f

/**
 * A number in the game's type that rolls like an odometer to whatever it becomes: digits slide
 * over each other, counting up or down through the values in between, with a small pop when it
 * goes up. The box eases to fit when the number gains or loses a digit, so what sits beside it
 * glides instead of jumping. [onTick] (if given) is called as the number passes whole values, no
 * more than about twenty times a second, for a soft sound.
 *
 * With reduce motion on it just shows the number.
 */
@Composable
fun RollingNumber(
    value: Int,
    color: Color,
    unit: Dp,
    modifier: Modifier = Modifier,
    onTick: (() -> Unit)? = null,
) {
    val target = value.coerceAtLeast(0)
    val u = with(LocalDensity.current) { unit.toPx() }
    val cap = ArcadeFont.height(u)
    val pad = u * 1.2f
    // Digits are drawn in equal-width cells so the number doesn't wobble as it rolls.
    val advance = remember(u) {
        var m = 0f
        for (d in DIGITS) m = maxOf(m, ArcadeFont.width(d, u))
        m + u * 0.35f
    }
    val height = (cap + pad).roundToInt()
    val shown = remember { Animatable(target.toFloat()) }
    val bump = remember { Animatable(1f) }
    var reserved by remember { mutableIntStateOf(Odometer.digitCount(target)) }
    val width = remember { Animatable(reserved * advance + pad) }
    val tick by rememberUpdatedState(onTick)

    LaunchedEffect(target) {
        reserved = maxOf(reserved, Odometer.digitCount(target))
        val from = shown.value
        if (!UiMotion.enabled || abs(target - from) < 0.001f) {
            shown.snapTo(target.toFloat())
        } else {
            if (target > from) {
                // A small pop as it goes up, springing back with a little life.
                launch {
                    bump.snapTo(BUMP_PEAK)
                    bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                }
            }
            var lastWhole = floor(from).toInt()
            var lastTickAt = 0L
            shown.animateTo(target.toFloat(), tween(rollMillis(from, target.toFloat()), easing = FastOutSlowInEasing)) {
                val whole = floor(this.value).toInt()
                if (whole != lastWhole) {
                    lastWhole = whole
                    val now = System.nanoTime()
                    if (now - lastTickAt > TICK_GAP_NANOS) {
                        lastTickAt = now
                        tick?.invoke()
                    }
                }
            }
        }
        reserved = Odometer.digitCount(target)
    }
    LaunchedEffect(reserved, advance, pad) {
        val w = reserved * advance + pad
        if (UiMotion.enabled) width.animateTo(w, spring(stiffness = Spring.StiffnessMediumLow)) else width.snapTo(w)
    }

    Spacer(
        modifier
            .layout { _, _ ->
                val w = width.value.roundToInt().coerceAtLeast(1)
                layout(w, height) {}
            }
            .graphicsLayer {
                val s = bump.value
                scaleX = s
                scaleY = s
            }
            .clipToBounds()
            .semantics { contentDescription = target.toString() }
            .drawBehind {
                val v = shown.value.coerceAtLeast(0f)
                val right = width.value - pad
                val travel = cap * 1.3f
                val places = maxOf(reserved, Odometer.digitCount(floor(v).toInt() + 1))
                for (p in 0 until places) {
                    val turns = Odometer.turns(v, p)
                    val roll = Odometer.roll(v, p)
                    // Blank wheels above the leading digit stay empty until they roll in.
                    val blank = p > 0 && turns == 0
                    if (blank && roll <= 0f) continue
                    val cellX = right - (p + 1) * advance
                    if (!blank) {
                        val d = DIGITS[turns % 10]
                        val x = cellX + (advance - ArcadeFont.width(d, u)) / 2f
                        ArcadeFont.drawShadowed(this, d, x, -roll * travel, u, color, alpha = 1f - roll * roll)
                    }
                    if (roll > 0f) {
                        val d = DIGITS[(turns + 1) % 10]
                        val x = cellX + (advance - ArcadeFont.width(d, u)) / 2f
                        ArcadeFont.drawShadowed(this, d, x, (1f - roll) * travel, u, color, alpha = roll)
                    }
                }
            },
    )
}
