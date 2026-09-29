package com.pocketarcade.games

import androidx.compose.ui.graphics.Color
import com.pocketarcade.engine.Painter
import com.pocketarcade.games.fishing.FishingGame
import com.pocketarcade.games.pinball.PinballGame
import com.pocketarcade.games.shooter.ShooterGame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hall cabinets' attract loops for the three scene games run through a recording painter for
 * a few minutes of clock: they must never throw, draw something every frame, and keep their
 * numbers finite (an animation that divides by a phase can go NaN at one unlucky instant).
 */
class AttractScreensTest {
    private class Recorder : Painter {
        var calls = 0
        var badNumber = false

        private fun note(vararg v: Float) {
            calls++
            for (x in v) if (!x.isFinite()) badNumber = true
        }

        override fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) = note(x, y, w, h, alpha)
        override fun disc(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) = note(cx, cy, r, alpha)
        override fun frame(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) = note(x, y, w, h, alpha)
        override fun text(text: String, x: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) = note(x, y, alpha, size)
        override fun textCentered(text: String, cx: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) = note(cx, y, alpha, size)
    }

    private fun run(game: MiniGame, w: Int, h: Int) {
        val p = Recorder()
        var t = 0f
        while (t < 240f) {
            val before = p.calls
            game.drawAttract(p, w, h, t)
            assertTrue("${game.id} drew nothing at t=$t", p.calls > before)
            t += 0.083f
        }
        assertTrue("${game.id} produced a NaN or infinite number", !p.badNumber)
    }

    @Test
    fun pinballAttractRuns() = run(PinballGame(), 40, 30)

    @Test
    fun shooterAttractRuns() = run(ShooterGame(), 24, 18)

    @Test
    fun fishingAttractRuns() = run(FishingGame(), 24, 18)
}
