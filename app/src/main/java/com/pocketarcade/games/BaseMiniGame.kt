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
        scope.translate(shake.offsetX, shake.offsetY) {
            render(this)
            GameViewport.shakeX = 0f
            GameViewport.shakeY = 0f
            particles.draw(this)
            popups.draw(this, 0f, 0f, 1f)
        }
        if (flash.value > 0f) {
            scope.drawRect(Color.White, Offset(-40f, -40f), androidx.compose.ui.geometry.Size(GAME_W + 80f, GAME_H + 80f), flash.value * 0.55f)
        }
    }

    /** Set by games that can end before the clock does (e.g. out of coins). */
    protected var endedEarly = false

    override val finished: Boolean get() = (timeUp || endedEarly) && isSettled()

    protected abstract fun reset()
    protected abstract fun step(dt: Float)
    protected abstract fun render(scope: DrawScope)

    /** Called once when the clock hits zero. */
    protected open fun onTimeUp() {}

    /** Whether nothing is still in motion (balls in flight, coins sliding, claw mid-grab). */
    protected open fun isSettled(): Boolean = true

    protected fun addScore(points: Int, x: Float, y: Float, color: Color, label: String? = null) {
        score = (score + points).coerceAtLeast(0)
        popups.add(label ?: (if (points >= 0) "+$points" else "$points"), x, y, color, size = 3f)
    }

    protected fun play(sfx: Sfx, volume: Float = 1f, pitch: Float = 1f) = fx.audio.play(sfx, volume, pitch)
}
