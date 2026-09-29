package com.pocketarcade.games

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.airhockey.AirHockeyGame
import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.hoops.HoopsGame
import com.pocketarcade.games.hoops.HoopsTuning
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.stacker.StackerGame
import com.pocketarcade.games.whackamole.WhackAMoleGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The host stops forwarding touches while a round is paused, so a finger lifted meanwhile never
 * sends its UP. Each machine must let go of that pointer when told ([MiniGame.cancelInput]) so a
 * new finger works for the rest of the paid round, and the lost finger's late events must be
 * harmless.
 */
class InputCancelTest {
    @Test
    fun clawLetsGoOfTheStick() {
        val game = ClawMachineGame()
        val d = RoundDriver(game, 3L)
        d.play(0.5f)
        val b = game.botButtons()
        game.onTouch(TouchType.DOWN, 1L, b[0], b[1], d.ms)
        d.play(0.4f)
        assertTrue("LEFT should move the trolley", game.botTrolleyX < 180f)
        d.pause()
        d.play(0.3f)
        val stopped = game.botTrolleyX
        d.play(1f)
        assertEquals("the lost finger still holds LEFT", stopped, game.botTrolleyX, 0.5f)
        game.onTouch(TouchType.UP, 1L, b[0], b[1], d.ms)
        game.onTouch(TouchType.DOWN, 2L, b[2], b[3], d.ms)
        d.play(0.5f)
        assertTrue("a new finger on RIGHT should move it", game.botTrolleyX > stopped + 20f)
        game.onTouch(TouchType.UP, 2L, b[2], b[3], d.ms)
        d.play(0.3f)
        val parked = game.botTrolleyX
        d.play(0.5f)
        assertEquals(parked, game.botTrolleyX, 0.5f)
    }

    @Test
    fun skeeBallDropsTheDraggedBall() {
        val game = SkeeBallGame()
        val d = RoundDriver(game, 5L)
        d.play(0.3f)
        game.onTouch(TouchType.DOWN, 1L, 180f, 600f, d.ms)
        game.onTouch(TouchType.MOVE, 1L, 260f, 560f, d.ms + 16)
        assertTrue("the ball should follow the finger", abs(game.botReadyX - 180f) > 10f)
        d.pause()
        d.play(1.5f)
        assertEquals("the ball eases back to rest", 180f, game.botReadyX, 1f)
        game.onTouch(TouchType.MOVE, 1L, 120f, 520f, d.ms)
        game.onTouch(TouchType.UP, 1L, 120f, 300f, d.ms + 20)
        assertEquals(0, game.botBallsActive)
        flick(game, 2L, 180f, 600f, 0f, -1340f, d.ms + 100)
        assertEquals("a new finger should roll the ball", 1, game.botBallsActive)
    }

    @Test
    fun hoopsDropsTheBallBeingLinedUp() {
        val game = HoopsGame()
        val d = RoundDriver(game, 9L)
        d.play(0.3f)
        game.onTouch(TouchType.DOWN, 1L, 180f, 560f, d.ms)
        game.onTouch(TouchType.MOVE, 1L, 220f, 540f, d.ms + 16)
        d.pause()
        d.play(0.5f)
        game.onTouch(TouchType.UP, 1L, 220f, 300f, d.ms)
        assertEquals(0, game.botShots)
        flick(game, 2L, 180f, 560f, 0f, -HoopsTuning.FLICK_IDEAL, d.ms + 100)
        assertEquals("a new finger should shoot", 1, game.botShots)
    }

    @Test
    fun airHockeyMalletStopsChasingTheLostFinger() {
        val game = AirHockeyGame()
        val d = RoundDriver(game, 11L)
        d.play(0.2f)
        val (fx, fy) = game.botScreen(70f, 560f)
        game.onTouch(TouchType.DOWN, 1L, fx, fy, d.ms)
        d.play(0.02f)
        d.pause()
        val mx = game.botMalletX
        val my = game.botMalletY
        assertTrue("the mallet should be on its way", mx < 170f && mx > 80f)
        d.play(0.5f)
        assertEquals(mx, game.botMalletX, 0.5f)
        assertEquals(my, game.botMalletY, 0.5f)
        game.onTouch(TouchType.MOVE, 1L, fx, fy, d.ms)
        game.onTouch(TouchType.UP, 1L, fx, fy, d.ms)
        val (nx, ny) = game.botScreen(260f, 540f)
        game.onTouch(TouchType.DOWN, 2L, nx, ny, d.ms)
        d.play(0.3f)
        assertEquals("a new finger should move the mallet", 260f, game.botMalletX, 3f)
        assertEquals(540f, game.botMalletY, 3f)
    }

    @Test
    fun whackStillBonksAfterAPause() {
        val game = WhackAMoleGame()
        val d = RoundDriver(game, 5L)
        d.play(0.3f)
        game.onTouch(TouchType.DOWN, 1L, 20f, 20f, d.ms)
        d.pause()
        game.onTouch(TouchType.UP, 1L, 20f, 20f, d.ms)
        var hole = -1
        while (hole < 0 && d.t < 10f) {
            d.play(FIXED_DT)
            for (i in 0 until 9) if (game.botView(i) == 1 || game.botView(i) == 2) hole = i
        }
        assertTrue("no mole came up", hole >= 0)
        val before = game.score
        tap(game, 2L, game.holeX(hole), game.holeY(hole) - 40f, d.ms)
        assertTrue("a new finger should bonk the mole", game.score > before)
    }

    @Test
    fun pusherStillDropsCoinsAfterAPause() {
        val game = CoinPusherGame()
        val d = RoundDriver(game, 7L)
        d.play(0.3f)
        val coins = game.botCoinsLeft
        game.onTouch(TouchType.DOWN, 1L, 180f, 200f, d.ms)
        assertEquals(coins - 1, game.botCoinsLeft)
        d.pause()
        d.play(0.5f)
        game.onTouch(TouchType.UP, 1L, 180f, 200f, d.ms)
        tap(game, 2L, 180f, 200f, d.ms)
        assertEquals("a new finger should drop a coin", coins - 2, game.botCoinsLeft)
    }

    @Test
    fun stackerStillDropsAfterAPause() {
        val game = StackerGame()
        val d = RoundDriver(game, 13L)
        fun waitForLineUp() {
            while (!(game.botMoving && abs(game.botDelta()) < 4f) && d.t < 20f) d.play(FIXED_DT)
            assertTrue("the slab never lined up", game.botMoving)
        }
        waitForLineUp()
        game.onTouch(TouchType.DOWN, 1L, 180f, 300f, d.ms)
        assertEquals(1, game.botHeight)
        d.pause()
        d.play(0.2f)
        game.onTouch(TouchType.UP, 1L, 180f, 300f, d.ms)
        waitForLineUp()
        tap(game, 2L, 180f, 300f, d.ms)
        assertEquals("a new finger should drop the next slab", 2, game.botHeight)
    }
}
