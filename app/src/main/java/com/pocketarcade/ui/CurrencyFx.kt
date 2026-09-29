package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** The arc a flying coin or ticket follows: a quadratic curve lifted above the straight line. */
internal object FlyPath {
    /** Eased progress: a slow lift-off, quick through the middle, a soft landing. */
    fun ease(t: Float): Float {
        val x = clamp01(t)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
    }

    /** How high the arc rises above the midpoint of a flight of ([dx], [dy]) pixels. */
    fun lift(dx: Float, dy: Float): Float = maxOf(56f, sqrt(dx * dx + dy * dy) * 0.32f)

    /** One coordinate of the quadratic curve from [a] via [control] to [b] at eased progress [e]. */
    fun bezier(a: Float, control: Float, b: Float, e: Float): Float {
        val k = 1f - e
        return k * k * a + 2f * k * e * control + e * e * b
    }
}

/**
 * Coins and tickets that arc across the screen: a token flying from the HUD counter toward the
 * machine you are entering, tickets tossed from the results printer into the counter. Each flight
 * calls back when it lands (a counter ticks up, a pill pops), so what you see arrive is what the
 * number does. Compose-side and cheap: a small fixed pool, no allocation while it plays.
 *
 * With reduce motion on nothing flies; the landing callback runs at once so the counters still
 * update. Draw it with [CurrencyFxLayer].
 */
class CurrencyFx {
    enum class Kind { TOKEN, TICKET }

    private class Fly {
        var active = false
        var kind = Kind.TOKEN
        var x0 = 0f
        var y0 = 0f
        var x1 = 0f
        var y1 = 0f
        var cx = 0f
        var cy = 0f
        var delay = 0f
        var t = 0f
        var dur = 0.6f
        var spin = 0f
        var onLand: (() -> Unit)? = null
    }

    private val flies = Array(MAX_FLIES) { Fly() }
    private var activeCount = 0

    /** Centre of the HUD's token icon in the layer's pixels, set by the counter; unspecified until it is on screen. */
    var tokenAnchor: Offset = Offset.Unspecified

    /** Centre of a ticket counter's icon in the layer's pixels (the results pill), set by the counter. */
    var ticketAnchor: Offset = Offset.Unspecified

    /** Size of the layer in pixels and of one dp, set by [CurrencyFxLayer]. */
    var viewW = 0f
    var viewH = 0f
    var dp = 1f

    /** Bumped on every launch so the layer's frame loop wakes up. */
    var epoch by mutableIntStateOf(0)
        private set

    val any: Boolean get() = activeCount > 0

    /**
     * Starts a flight of [kind] from [from] to [to] (unspecified: the middle of the layer, a little
     * above centre, where a machine's screen ends up), after [delay] seconds, taking [duration].
     * [onLand] runs when it arrives. If the pool is full or motion is off, it lands at once.
     */
    fun launch(
        kind: Kind,
        from: Offset,
        to: Offset = Offset.Unspecified,
        delay: Float = 0f,
        duration: Float = 0.6f,
        onLand: (() -> Unit)? = null,
    ) {
        if (!UiMotion.enabled || from == Offset.Unspecified) {
            onLand?.invoke(); return
        }
        val f = flies.firstOrNull { !it.active }
        if (f == null) {
            onLand?.invoke(); return
        }
        f.active = true
        f.kind = kind
        f.x0 = from.x
        f.y0 = from.y
        f.x1 = to.x // Unspecified is NaN: resolved against the layer's size while it flies
        f.y1 = to.y
        f.delay = delay
        f.t = 0f
        f.dur = duration.coerceAtLeast(0.1f)
        f.spin = if (kind == Kind.TOKEN) 9f + (f.x0 % 3f) else 0f
        f.onLand = onLand
        activeCount++
        aim(f)
        epoch++
    }

    private fun aim(f: Fly) {
        if (f.x1.isNaN() || f.y1.isNaN()) {
            f.x1 = viewW * 0.5f
            f.y1 = viewH * 0.42f
        }
        val lift = FlyPath.lift(f.x1 - f.x0, f.y1 - f.y0)
        f.cx = (f.x0 + f.x1) / 2f
        f.cy = minOf(f.y0, f.y1) - lift * 0.6f + abs(f.y1 - f.y0) * 0.15f
    }

    /** Advances every flight by [dt] seconds. */
    fun update(dt: Float) {
        for (f in flies) {
            if (!f.active) continue
            var step = dt
            if (f.delay > 0f) {
                f.delay -= step
                if (f.delay > 0f) continue
                step = -f.delay
                f.delay = 0f
                aim(f)
            }
            f.t += step
            if (f.t >= f.dur) {
                f.active = false
                activeCount--
                val land = f.onLand
                f.onLand = null
                land?.invoke()
            }
        }
    }

    /** Drops every flight without landing it (the screen is going away). */
    fun clear() {
        for (f in flies) {
            f.active = false; f.onLand = null
        }
        activeCount = 0
    }

    /** Paints every flight in the layer's pixels. */
    fun draw(scope: DrawScope) {
        with(scope) {
            val tokenR = 11f * dp
            val ticketW = 30f * dp
            for (f in flies) {
                if (!f.active || f.delay > 0f) continue
                val t = clamp01(f.t / f.dur)
                val e = FlyPath.ease(t)
                val c = Offset(FlyPath.bezier(f.x0, f.cx, f.x1, e), FlyPath.bezier(f.y0, f.cy, f.y1, e))
                // Pops out of where it starts, then shrinks a little as it heads into the counter.
                val size = easeOutBack(clamp01(t / 0.18f)) * (1f - 0.4f * e * e)
                val fade = if (t > 0.9f) (1f - t) / 0.1f else 1f
                if (f.kind == Kind.TOKEN) {
                    drawCircle(Color(0xFFFFD35A).copy(alpha = 0.22f * fade), tokenR * 1.9f * size, c)
                    // A coin turning over as it goes.
                    val flip = abs(cos(t * f.spin)).coerceAtLeast(0.28f)
                    scale(flip, 1f, c) { drawToken(c, tokenR * size) }
                } else {
                    drawCircle(Color(0xFFFFA24A).copy(alpha = 0.2f * fade), ticketW * 0.6f * size, c)
                    rotate(-24f + 40f * e, c) { drawTicket(c, ticketW * size) }
                }
            }
        }
    }

    private companion object {
        const val MAX_FLIES = 32
    }
}

/**
 * Draws a [CurrencyFx]'s flights over the whole screen and runs its frames while anything is in
 * the air (and not otherwise). Put it above the screens whose counters the flights end at.
 */
@Composable
fun CurrencyFxLayer(fx: CurrencyFx, modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    fx.dp = LocalDensity.current.density
    LaunchedEffect(fx.epoch) {
        var last = -1L
        while (fx.any) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                fx.update(dt)
                frame.longValue = frame.longValue + 1
            }
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                fx.viewW = it.width.toFloat()
                fx.viewH = it.height.toFloat()
            },
    ) {
        frame.longValue
        fx.draw(this)
    }
}
