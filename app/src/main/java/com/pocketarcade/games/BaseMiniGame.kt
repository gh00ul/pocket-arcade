package com.pocketarcade.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.pocketarcade.engine.FloatingTexts
import com.pocketarcade.engine.Flash
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.r3d.GameViewport
import kotlin.random.Random

/**
 * Convenience base for mini-games: owns the juice (particles, shake, popups, flash), the score and
 * the round clock, so a game only implements [reset], [step] and [render].
 */
abstract class BaseMiniGame : MiniGame {
    protected lateinit var fx: GameFx
    protected val particles = Particles(700)
    protected val shake = ScreenShake(maxOffset = 16f)
    protected val popups = FloatingTexts()
    protected val flash = Flash(decayPerSec = 3f)
    protected var rng: Random = Random(System.nanoTime())
    /** Fixed seed for reproducible rounds in headless tests; null means a fresh random round. */
    internal var seed: Long? = null
    /** Seconds since the round started. */
    protected var time = 0f
    protected var timeLeft = 0f
    protected var timeUp = false
        private set

    final override var score: Int = 0
        protected set
    override var bonusTickets: Int = 0
        protected set

    final override fun start(fx: GameFx) {
        this.fx = fx
        rng = Random(seed ?: System.nanoTime())
        particles.clear()
        shake.reset()
        popups.clear()
        score = 0
        bonusTickets = 0
        time = 0f
        timeLeft = roundSeconds
        timeUp = false
        endedEarly = false
        reset()
    }

    final override fun update(dt: Float, timeLeft: Float) {
        time += dt
        this.timeLeft = timeLeft
        if (!timeUp && timeLeft <= 0f) {
            timeUp = true
            onTimeUp()
        }
        step(dt)
        particles.update(dt)
        shake.update(dt)
        popups.update(dt)
        flash.update(dt)
    }

    final override fun draw(scope: DrawScope) {
        GameViewport.shakeX = shake.offsetX
        GameViewport.shakeY = shake.offsetY
        // Offer the particles to the GPU picture, where they glow in the bloom; a game with a
        // 3D stage takes them in present(), and only if nothing did are they painted in 2D.
        GameViewport.particles = particles
        GameViewport.particlesInGl = false
        scope.translate(shake.offsetX, shake.offsetY) {
            render(this)
            GameViewport.shakeX = 0f
            GameViewport.shakeY = 0f
            GameViewport.particles = null
            if (!GameViewport.particlesInGl) particles.draw(this)
            popups.draw(this, 0f, 0f, 1f)
        }
        // Flashes are motion too: reduce motion (intensity 0) turns them off.
        val flashAlpha = flash.value * FLASH_ALPHA * ScreenShake.intensity.coerceIn(0f, 1f)
        if (flashAlpha > 0f) {
            scope.drawRect(Color.White, Offset(-40f, -40f), androidx.compose.ui.geometry.Size(GAME_W + 80f, GAME_H + 80f), flashAlpha)
        }
    }

    /** Set by games that can end before the clock does (e.g. out of coins). */
    protected var endedEarly = false

    override val finished: Boolean get() = (timeUp || endedEarly) && isSettled()

    protected abstract fun reset()
    protected abstract fun step(dt: Float)
    protected abstract fun render(scope: DrawScope)

    /** Every game must say how it lets go of its pointers; tap-only games implement it as a no-op. */
    abstract override fun cancelInput()

    /** Called once when the clock hits zero. */
    protected open fun onTimeUp() {}

    /** Whether nothing is still in motion (balls in flight, coins sliding, claw mid-grab). */
    protected open fun isSettled(): Boolean = true

    protected fun addScore(points: Int, x: Float, y: Float, color: Color, label: String? = null) {
        score = (score + points).coerceAtLeast(0)
        popups.add(label ?: (if (points >= 0) "+$points" else "$points"), x, y, color, size = 3f)
    }

    protected fun play(sfx: Sfx, volume: Float = 1f, pitch: Float = 1f) = fx.audio.play(sfx, volume, pitch)

    // ---- game feel: requests the host turns into a freeze, a slow beat and a camera kick

    /** Freezes the game for a few frames as a big hit lands ([GameFx.hitStop]); the host caps and spaces them. */
    protected fun hitStop(seconds: Float = HIT_STOP_DEFAULT) = fx.hitStop(seconds)

    /** A slow-motion beat with eased ramps ([GameFx.slowMo]): jackpots, a last-second win. Off with reduce motion. */
    protected fun slowMo(speed: Float = SLOW_MO_SPEED, seconds: Float = SLOW_MO_SECONDS) = fx.slowMo(speed, seconds)

    /** Kicks the 3D camera in and springs it back ([GameFx.punch]), [amount] 0..1. */
    protected fun punch(amount: Float = PUNCH_DEFAULT) = fx.punch(amount)

    /**
     * A solid hit in one call: screen shake, a short freeze and a camera kick, all scaled by
     * [weight] (0 = a tap, 1 = the hardest hit a round has). Sound and particles stay the game's own.
     */
    protected fun impact(weight: Float) {
        val w = weight.coerceIn(0f, 1f)
        shake.add(IMPACT_SHAKE_MIN + IMPACT_SHAKE_RANGE * w)
        fx.hitStop(IMPACT_STOP_MIN + IMPACT_STOP_RANGE * w)
        fx.punch(IMPACT_PUNCH_MIN + IMPACT_PUNCH_RANGE * w)
    }

    /** The round's biggest moment (a jackpot, a perfect finish): the hardest [impact], a white flash and a slow-motion beat. */
    protected fun bigMoment() {
        impact(1f)
        slowMo()
        flash.trigger(BIG_MOMENT_FLASH)
    }

    private companion object {
        const val HIT_STOP_DEFAULT = 0.06f
        const val SLOW_MO_SPEED = 0.4f
        const val SLOW_MO_SECONDS = 0.45f
        const val PUNCH_DEFAULT = 0.4f
        const val IMPACT_SHAKE_MIN = 0.12f
        const val IMPACT_SHAKE_RANGE = 0.4f
        const val IMPACT_STOP_MIN = 0.03f
        const val IMPACT_STOP_RANGE = 0.06f
        const val IMPACT_PUNCH_MIN = 0.15f
        const val IMPACT_PUNCH_RANGE = 0.55f
        const val BIG_MOMENT_FLASH = 0.5f
        const val FLASH_ALPHA = 0.55f
    }
}
