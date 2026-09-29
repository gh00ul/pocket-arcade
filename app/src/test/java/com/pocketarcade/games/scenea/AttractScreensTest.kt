package com.pocketarcade.games.scenea

import androidx.compose.ui.graphics.Color
import com.pocketarcade.engine.Painter
import com.pocketarcade.games.MiniGame
import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.whackamole.WhackAMoleGame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hall paints every cabinet's attract loop into a live texture, and nothing there can be
 * seen from a unit test, so this plays each one against a recording painter instead: every
 * number must be finite, shapes must not have negative size, alphas stay in 0..1, a frame must
 * stay cheap to paint, and the loop must actually move.
 */
class AttractScreensTest {
    /** A painter that only remembers what it was asked to draw, and complains about nonsense. */
    private class Recorder : Painter {
        var calls = 0
        var problem: String? = null
        var signature = 0.0

        private fun note(what: String, alpha: Float, vararg v: Float) {
            calls++
            for (x in v) {
                if (x.isNaN() || x.isInfinite()) problem = problem ?: "$what got $x"
                signature += x * 0.37 + calls
            }
            if (alpha < -1e-3f || alpha > 1.001f) problem = problem ?: "$what alpha $alpha"
        }

        override fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
            note("fill", alpha, x, y, w, h)
            if (w < -1e-3f || h < -1e-3f) problem = problem ?: "fill of size $w x $h at $x, $y"
        }

        override fun disc(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
            note("disc", alpha, cx, cy, r)
            if (r < 0f) problem = problem ?: "disc of radius $r"
        }

        override fun frame(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
            note("frame", alpha, x, y, w, h)
            if (w < -1e-3f || h < -1e-3f) problem = problem ?: "frame of size $w x $h"
        }

        override fun text(text: String, x: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) {
            note("text", alpha, x, y, size)
            if (size <= 0f) problem = problem ?: "text size $size"
        }

        override fun textCentered(text: String, cx: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) {
            note("text", alpha, cx, y, size)
            if (size <= 0f) problem = problem ?: "text size $size"
        }
    }

    private val games: List<Pair<String, () -> MiniGame>> = listOf(
        "claw" to { ClawMachineGame() },
        "skeeball" to { SkeeBallGame() },
        "whack" to { WhackAMoleGame() },
        "pusher" to { CoinPusherGame() },
    )

    /** The hall's cabinets are 24 x 18 painter units; painting is at 30 frames a second per visible cabinet. */
    private fun paint(game: MiniGame, t: Float): Recorder = Recorder().also { game.drawAttract(it, 24, 18, t) }

    @Test
    fun everyLoopPaintsSaneShapesAtEveryMoment() {
        for ((name, make) in games) {
            val game = make()
            var t = 0f
            while (t < 45f) {
                val rec = paint(game, t)
                assertNull("$name at t=$t: ${rec.problem}", rec.problem)
                t += 0.031f
            }
        }
    }

    @Test
    fun aFrameStaysCheapToPaint() {
        for ((name, make) in games) {
            val game = make()
            var worst = 0
            var t = 0f
            while (t < 45f) {
                worst = maxOf(worst, paint(game, t).calls)
                t += 0.05f
            }
            assertTrue("$name needs $worst draw calls a frame", worst in 20..450)
        }
    }

    @Test
    fun everyLoopKeepsMoving() {
        for ((name, make) in games) {
            val game = make()
            val a = paint(game, 0.2f).signature
            var changed = 0
            for (k in 1..12) if (paint(game, 0.2f + k * 0.5f).signature != a) changed++
            assertTrue("$name looks frozen ($changed of 12 frames differ)", changed >= 10)
        }
    }

    @Test
    fun theLoopsAreDeterministicForATime() {
        for ((name, make) in games) {
            val game = make()
            assertTrue("$name differs between two paints of the same instant", paint(game, 3.3f).signature == paint(game, 3.3f).signature)
        }
    }
}
