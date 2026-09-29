package com.pocketarcade.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/**
 * Trauma-based screen shake: [add] trauma (0..1), offsets grow with trauma² and decay smoothly.
 * Every shake in the game is scaled by [intensity].
 */
class ScreenShake(private val maxOffset: Float = 14f, private val decayPerSec: Float = 1.8f) {
    companion object {
        /** How much of every shake shows: 1 as designed, 0 with the reduce-motion setting. */
        @Volatile var intensity = 1f
    }

    var trauma = 0f
        private set
    private var time = 0f
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    fun add(amount: Float) {
        trauma = (trauma + amount).coerceAtMost(1f)
    }

    fun reset() {
        trauma = 0f; offsetX = 0f; offsetY = 0f
    }

    fun update(dt: Float) {
        time += dt
        trauma = (trauma - decayPerSec * dt).coerceAtLeast(0f)
        val k = trauma * trauma * maxOffset * intensity
        offsetX = k * (sin(time * 71f) * 0.6f + sin(time * 113f + 1.3f) * 0.4f)
        offsetY = k * (sin(time * 83f + 2.1f) * 0.6f + sin(time * 127f + 0.4f) * 0.4f)
    }
}

/**
 * Damped spring around 1.0 used for squash and stretch: [kick] it and read [value] as a scale.
 */
class Spring(
    private val stiffness: Float = 380f,
    private val damping: Float = 16f,
    var target: Float = 1f,
) {
    var value = target
    private var velocity = 0f

    fun kick(impulse: Float) {
        velocity += impulse
    }

    fun snap(v: Float) {
        value = v; velocity = 0f
    }

    fun update(dt: Float) {
        val force = (target - value) * stiffness - velocity * damping
        velocity += force * dt
        value += velocity * dt
    }
}

/** Short-lived popup texts ("+50", "SWISH!") that pop in, float upward and fade. */
class FloatingTexts(capacity: Int = 24) {
    private class Item {
        var text = ""
        var x = 0f
        var y = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var color = Color.White
        var size = 2f
        var active = false
    }

    private val items = Array(capacity) { Item() }
    private var next = 0

    fun add(text: String, x: Float, y: Float, color: Color, size: Float = 2f, life: Float = 0.9f, rise: Float = 60f) {
        val it = items[next]
        next = (next + 1) % items.size
        it.text = text; it.x = x; it.y = y; it.vy = -rise
        it.life = life; it.maxLife = life; it.color = color; it.size = size; it.active = true
    }

    fun clear() {
        items.forEach { it.active = false }
    }

    fun update(dt: Float) {
        for (it in items) {
            if (!it.active) continue
            it.life -= dt
            if (it.life <= 0f) {
                it.active = false; continue
            }
            it.y += it.vy * dt
            it.vy *= (1f - 2.5f * dt).coerceAtLeast(0f)
        }
    }

    /** Draws in a space where one unit is [unit] screen pixels, offset by the origin. */
    fun draw(scope: DrawScope, originX: Float, originY: Float, unit: Float) {
        for (it in items) {
            if (!it.active) continue
            val age = 1f - it.life / it.maxLife
            val pop = if (age < 0.15f) easeOutBack(age / 0.15f) else 1f
            val alpha = if (it.life < 0.3f) it.life / 0.3f else 1f
            val scale = it.size * unit * (0.6f + 0.4f * pop)
            ArcadeFont.drawCentered(scope, it.text, originX + it.x * unit, originY + it.y * unit, scale, it.color, alpha)
        }
    }
}

/** A value that jumps to 1 and decays to 0; used for white flashes and hit highlights. */
class Flash(private val decayPerSec: Float = 4f) {
    var value = 0f
        private set

    fun trigger(amount: Float = 1f) {
        value = maxOf(value, amount)
    }

    fun update(dt: Float) {
        value = (value - decayPerSec * dt).coerceAtLeast(0f)
    }
}

/**
 * Time control for the game host: hit-stops (a freeze of a few frames on a big hit) and slow-motion
 * beats (a jackpot, a new high score, a last-second win) with eased ramps. Pure and headless: the
 * host feeds [update] the real frame step and gets back how much of it the game gets.
 *
 * Only the host loop uses it, and the game still steps by exactly [FIXED_DT] (see [SimClock]), so
 * headless simulations never see a scaled time and payouts don't depend on it.
 *
 * Rules, so a run of big moments never turns the round into a slideshow:
 *  - a hit-stop is at most [MAX_HIT_STOP] s, a chain of requests can't extend one past that, and a
 *    new one is ignored for [HIT_STOP_COOLDOWN] s after one ends;
 *  - slow-mo is at least [MIN_SCALE] × speed for at most [MAX_SLOW_SECONDS] s; a second request while
 *    one runs deepens it (the lower scale wins) and extends it up to that cap, and a new one is
 *    ignored for [SLOW_COOLDOWN] s after one ends;
 *  - with reduce motion on ([motion] is 0) both are off.
 */
class TimeScale(private val motion: () -> Float = { ScreenShake.intensity }) {
    companion object {
        /** The longest freeze, in seconds (about 14 steps at 120 Hz). */
        const val MAX_HIT_STOP = 0.12f
        /** Freezes shorter than this can't be felt and are ignored. */
        const val MIN_HIT_STOP = 0.015f
        /** After a freeze ends, this long passes before another can start, so combos don't stutter. */
        const val HIT_STOP_COOLDOWN = 0.22f
        /** The longest slow-motion hold, in seconds (the ramps are on top). */
        const val MAX_SLOW_SECONDS = 0.9f
        /** The slowest the game may run: below this a beat feels like a hang. */
        const val MIN_SCALE = 0.25f
        /** After a slow-mo hold ends, this long passes before another can start. */
        const val SLOW_COOLDOWN = 1.6f
        /** How fast the scale eases down into a beat and back out (per second, exponential): snappy in, gentle out. */
        const val RAMP_IN_RATE = 28f
        const val RAMP_OUT_RATE = 7f
        /** Within this of full speed the scale snaps back to 1 so the game doesn't crawl at 0.997×. */
        private const val SNAP = 0.012f
    }

    private var stopLeft = 0f
    private var stopSpent = 0f
    private var stopCooldown = 0f
    private var slowLeft = 0f
    private var slowSpent = 0f
    private var slowCooldown = 0f
    private var slowTarget = 1f

    /** The eased speed of the game right now, 1 at full speed (a freeze is separate: see [frozen]). */
    var scale = 1f
        private set

    /** True while a hit-stop holds the game still. */
    val frozen: Boolean get() = stopLeft > 0f

    /** How deep into a slow-motion beat the game is, 0 at full speed up to about 0.75; for a visual cue. */
    val depth: Float get() = 1f - scale

    /** Whether anything is slowing or holding the game. */
    val active: Boolean get() = frozen || scale < 1f || slowLeft > 0f

    /** Freezes the game for about [seconds] (at most [MAX_HIT_STOP]). Returns whether it took. */
    fun hitStop(seconds: Float): Boolean {
        if (motion() <= 0f) return false
        val s = if (seconds.isNaN()) 0f else seconds.coerceAtMost(MAX_HIT_STOP)
        if (s < MIN_HIT_STOP) return false
        if (stopLeft <= 0f) {
            if (stopCooldown > 0f) return false
            stopLeft = s
            stopSpent = 0f
        } else {
            // Already frozen: a longer request extends it, but never past the cap in total.
            val budget = MAX_HIT_STOP - stopSpent
            if (budget <= 0f) return false
            stopLeft = minOf(maxOf(stopLeft, s), budget)
        }
        return true
    }

    /**
     * Runs the game at [speed] (clamped to [MIN_SCALE]..1) for [seconds] (at most [MAX_SLOW_SECONDS]),
     * easing in and out. Returns whether it took.
     */
    fun slowMo(speed: Float, seconds: Float): Boolean {
        if (motion() <= 0f) return false
        val sp = if (speed.isNaN()) 1f else speed.coerceIn(MIN_SCALE, 1f)
        val s = if (seconds.isNaN()) 0f else seconds.coerceAtMost(MAX_SLOW_SECONDS)
        if (sp > 0.98f || s <= 0f) return false
        if (slowLeft <= 0f) {
            if (slowCooldown > 0f) return false
            slowTarget = sp
            slowLeft = s
            slowSpent = 0f
        } else {
            val budget = MAX_SLOW_SECONDS - slowSpent
            if (budget <= 0f) return false
            slowTarget = minOf(slowTarget, sp)
            slowLeft = minOf(maxOf(slowLeft, s), budget)
        }
        return true
    }

    /** Advances real time by [realDt] and returns how much of it the game gets: 0 while frozen, else [scale] × [realDt]. */
    fun update(realDt: Float): Float {
        if (motion() <= 0f) {
            // Reduce motion switched on mid-beat: drop everything at once.
            if (active) reset()
            return realDt
        }
        val frozenNow = stopLeft > 0f
        if (frozenNow) {
            stopLeft -= realDt
            stopSpent += realDt
            if (stopLeft <= 0f) {
                stopLeft = 0f; stopSpent = 0f; stopCooldown = HIT_STOP_COOLDOWN
            }
        } else if (stopCooldown > 0f) {
            stopCooldown = (stopCooldown - realDt).coerceAtLeast(0f)
        }
        val target: Float
        if (slowLeft > 0f) {
            slowLeft -= realDt
            slowSpent += realDt
            target = slowTarget
            if (slowLeft <= 0f) {
                slowLeft = 0f; slowSpent = 0f; slowCooldown = SLOW_COOLDOWN
            }
        } else {
            target = 1f
            if (slowCooldown > 0f) slowCooldown = (slowCooldown - realDt).coerceAtLeast(0f)
        }
        scale = damp(scale, target, if (target < scale) RAMP_IN_RATE else RAMP_OUT_RATE, realDt)
        if (scale > 1f - SNAP) scale = 1f
        return if (frozenNow) 0f else realDt * scale
    }

    /** Back to full speed at once, forgetting every request and cooldown (a new round, a pause). */
    fun reset() {
        stopLeft = 0f; stopSpent = 0f; stopCooldown = 0f
        slowLeft = 0f; slowSpent = 0f; slowCooldown = 0f
        slowTarget = 1f
        scale = 1f
    }
}

/**
 * The spring behind a camera punch. [kick] gives it a shove and it swings out (a push-in, the
 * value rising to about the kicked amount in 60 ms), overshoots a little the other way (a soft
 * pull-back of about a sixth) and settles within half a second. [value] is what the camera reads:
 * 1 is the biggest punch, 0 rest.
 *
 * The response is fixed by [STIFFNESS] and [DAMPING] (slightly under-damped: ζ ≈ 0.49), and
 * [KICK_SPEED] is calibrated so that kicking [amount] peaks at about [amount]. Sub-stepped, so a
 * long frame can't blow it up, and allocation-free. It does nothing while reduce motion is on
 * ([motion] is 0), and a punch in flight is cut dead if that is switched on.
 */
class PunchSpring(private val motion: () -> Float = { ScreenShake.intensity }) {
    companion object {
        const val STIFFNESS = 420f
        const val DAMPING = 20f
        /** The velocity a full-strength kick adds, calibrated so its peak is about 1 (see the class notes). */
        const val KICK_SPEED = 37f
        /** Kicks stronger than this are clamped, so a stack of them can't tear the picture. */
        const val MAX_AMOUNT = 1.5f
        private const val SUB_STEP = 1f / 240f
        private const val REST = 0.0005f
    }

    /** How far the punch is out, 1 at the biggest; slightly negative while it swings back. */
    var value = 0f
        private set
    private var velocity = 0f

    /** True while the spring is moving. */
    val active: Boolean get() = value != 0f || velocity != 0f

    fun kick(amount: Float) {
        if (amount.isNaN() || amount <= 0f || motion() <= 0f) return
        // Capped as a whole, so a stack of kicks in one moment can't exceed the strongest single one.
        velocity = (velocity + amount.coerceAtMost(MAX_AMOUNT) * KICK_SPEED).coerceAtMost(MAX_AMOUNT * KICK_SPEED)
    }

    fun update(dt: Float) {
        if (!active) return
        if (motion() <= 0f) {
            reset(); return
        }
        var left = dt.coerceIn(0f, 0.1f)
        while (left > 0f) {
            val h = minOf(left, SUB_STEP)
            // Semi-implicit Euler: stable at these rates and plenty accurate for a camera.
            velocity += (-STIFFNESS * value - DAMPING * velocity) * h
            value += velocity * h
            left -= h
        }
        value = value.coerceIn(-0.5f, MAX_AMOUNT)
        if (kotlin.math.abs(value) < REST && kotlin.math.abs(velocity) < 0.02f) reset()
    }

    fun reset() {
        value = 0f
        velocity = 0f
    }
}
