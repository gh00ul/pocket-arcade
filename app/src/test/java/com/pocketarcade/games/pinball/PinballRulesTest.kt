package com.pocketarcade.games.pinball

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.RoundDriver
import com.pocketarcade.games.Stats
import com.pocketarcade.games.playRound
import com.pocketarcade.games.simFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import com.pocketarcade.games.pinball.PinballTable as T

/** Rule-level tests for Star Flipper: physics that never tunnels, the controls, the rules and the failsafes. */
class PinballRulesTest {
    private companion object {
        const val LEFT_X = 70f
        const val RIGHT_X = 230f
        const val FLIP_Y = 560f
        const val PLUNGE_X = 320f
        const val PLUNGE_Y = 520f
        /** Plenty of clock left, so only the table decides what happens. */
        const val CLOCK = 30f
    }

    private fun startedGame(seed: Long = 1L): PinballGame = PinballGame().also {
        it.seed = seed
        it.start(simFx)
    }

    /** A started game with no ball anywhere, so a test can place exactly the balls it wants. */
    private fun emptyTable(seed: Long = 1L): PinballGame = startedGame(seed).also { g ->
        for (i in 0 until g.botMaxBalls) g.botRemove(i)
    }

    private fun steps(game: PinballGame, seconds: Float, clock: Float = CLOCK) {
        var t = 0f
        while (t < seconds) {
            game.update(FIXED_DT, clock)
            t += FIXED_DT
        }
    }

    private fun rad(deg: Float) = deg * PI.toFloat() / 180f

    // ---------------------------------------------------------------- tunnelling

    /**
     * Fires ball 0 from ([x], [y]) at [speed] along [angles] (degrees, y down) at the wall
     * from ([ax], [ay]) to ([bx], [by]) and checks after every step that it never gets to the
     * far side of the wall anywhere along its length, nor sinks deep into it.
     */
    private fun fireAtWall(
        name: String, x: Float, y: Float, angles: FloatArray, speed: Float,
        ax: Float, ay: Float, bx: Float, by: Float, seconds: Float = 0.25f,
    ): Int {
        var shots = 0
        for (deg in angles) {
            val game = emptyTable()
            val a = rad(deg)
            game.botPlace(0, x, y, cos(a) * speed, sin(a) * speed)
            val ex = bx - ax
            val ey = by - ay
            val len = sqrt(ex * ex + ey * ey)
            val side0 = sideOf(x, y, ax, ay, ex, ey)
            var t = 0f
            while (t < seconds && game.botBallInPlay(0)) {
                game.update(FIXED_DT, CLOCK)
                t += FIXED_DT
                val px = game.botBallX(0)
                val py = game.botBallY(0)
                val along = ((px - ax) * ex + (py - ay) * ey) / (len * len)
                if (along < 0.04f || along > 0.96f) continue
                val dist = sideOf(px, py, ax, ay, ex, ey) / len
                if (dist * side0 < 0f) fail("$name: a ${speed.toInt()} u/s ball at $deg° went through the wall (at $px, $py)")
                if (kotlin.math.abs(dist) < T.BALL_R * 0.4f) fail("$name: a ${speed.toInt()} u/s ball at $deg° sank into the wall (at $px, $py)")
            }
            shots++
        }
        return shots
    }

    private fun sideOf(x: Float, y: Float, ax: Float, ay: Float, ex: Float, ey: Float) = (x - ax) * ey - (y - ay) * ex

    private fun sweep(from: Float, to: Float, step: Float): FloatArray {
        val n = ((to - from) / step).toInt() + 1
        return FloatArray(n) { from + it * step }
    }

    @Test
    fun theFastestBallNeverTunnelsThroughAWall() {
        val speeds = floatArrayOf(PinballTuning.MAX_SPEED, PinballTuning.MAX_SPEED * 0.7f, PinballTuning.MAX_SPEED * 0.4f)
        var shots = 0
        for (s in speeds) {
            // The thin orbit wall, from the playfield and from inside the orbit.
            shots += fireAtWall("orbit wall from the playfield", 72f, 225f, sweep(125f, 235f, 5f), s, T.ORBIT_X, T.ORBIT_Y0, T.ORBIT_X, T.ORBIT_Y1)
            shots += fireAtWall("orbit wall from the orbit", 20f, 225f, sweep(-55f, 55f, 5f), s, T.ORBIT_X, T.ORBIT_Y0, T.ORBIT_X, T.ORBIT_Y1)
            // The shooter lane wall, both ways.
            shots += fireAtWall("lane wall from the playfield", 240f, 380f, sweep(-60f, 60f, 5f), s, T.PLAY_W, 200f, T.PLAY_W, 560f)
            shots += fireAtWall("lane wall from the lane", T.LANE_X, 380f, sweep(125f, 235f, 5f), s, T.PLAY_W, 200f, T.PLAY_W, 560f)
            // The outer walls and the top.
            shots += fireAtWall("left wall", 20f, 225f, sweep(125f, 235f, 5f), s, 0f, 130f, 0f, 320f)
            shots += fireAtWall("top wall", T.CX, 110f, sweep(-150f, -30f, 5f), s, 70f, 0f, 230f, 0f)
            // A slingshot's kicking face and the inlane guide behind it.
            shots += fireAtWall("left sling", 110f, 400f, sweep(120f, 170f, 5f), s, T.SLING_CX, T.SLING_CY, T.SLING_AX, T.SLING_AY)
            shots += fireAtWall("right sling", T.PLAY_W - 110f, 400f, sweep(10f, 60f, 5f), s, T.PLAY_W - T.SLING_CX, T.SLING_CY, T.PLAY_W - T.SLING_AX, T.SLING_AY)
            shots += fireAtWall("inlane guide", 36f, 420f, sweep(150f, 210f, 5f), s, 22f, 398f, 22f, 466f)
            // The shooter lane's one-way gate, from above.
            shots += fireAtWall("lane gate", 290f, 100f, sweep(60f, 120f, 5f), s, T.W, T.GATE_Y0, T.PLAY_W, T.LANE_TOP_Y)
        }
        println("wall sweep: $shots shots, none tunnelled")
    }

    /** Whether a ball at ([x], [y]) would start overlapping some wall or post of the table. */
    private fun overlapsTable(x: Float, y: Float): Boolean {
        val r = T.BALL_R + 1f
        for (i in 0 until T.segCount) {
            val ex = T.bx[i] - T.ax[i]
            val ey = T.by[i] - T.ay[i]
            val t = (((x - T.ax[i]) * ex + (y - T.ay[i]) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
            val dx = x - (T.ax[i] + ex * t)
            val dy = y - (T.ay[i] + ey * t)
            if (dx * dx + dy * dy < r * r) return true
        }
        for (i in 0 until T.postCount) {
            val dx = x - T.px[i]
            val dy = y - T.py[i]
            if (dx * dx + dy * dy < (r + T.pr[i]) * (r + T.pr[i])) return true
        }
        return false
    }

    /** Whether segments p0-p1 and q0-q1 cross. */
    private fun crosses(p0x: Float, p0y: Float, p1x: Float, p1y: Float, q0x: Float, q0y: Float, q1x: Float, q1y: Float): Boolean {
        fun side(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        val d1 = side(q0x, q0y, q1x, q1y, p0x, p0y)
        val d2 = side(q0x, q0y, q1x, q1y, p1x, p1y)
        val d3 = side(p0x, p0y, p1x, p1y, q0x, q0y)
        val d4 = side(p0x, p0y, p1x, p1y, q1x, q1y)
        return d1 * d2 < 0f && d3 * d4 < 0f
    }

    /**
     * Drops ball 0 onto flipper [side] from above at [speed] along [angles], with the flipper
     * resting, held up, or swinging up as the ball arrives, and checks after every step that
     * the ball's path never crossed the bat's centre line and it never sank halfway into it.
     */
    private fun fireAtFlipper(side: Int, mode: Int, speed: Float, angles: FloatArray): Int {
        var shots = 0
        val px = T.flipX(side)
        val py = T.FLIP_Y
        val len = T.FLIP_LEN
        for (deg in angles) {
            for (along in floatArrayOf(0.3f, 0.55f, 0.8f, 0.95f)) {
                val game = emptyTable()
                if (mode == 1) {
                    game.onTouch(TouchType.DOWN, 1L, if (side == 0) LEFT_X else RIGHT_X, FLIP_Y, 0L)
                    steps(game, 0.2f)
                }
                val d = game.botFlipperAngle(side)
                // Aim so the ball would cross the bat [along] of the way out, from 45 units away.
                val tx = px + cos(d) * len * along
                val ty = py + sin(d) * len * along
                val a = rad(deg)
                val sx = tx - cos(a) * 45f
                val sy = ty - sin(a) * 45f
                if (overlapsTable(sx, sy)) continue
                game.botPlace(0, sx, sy, cos(a) * speed, sin(a) * speed)
                if (mode == 2) {
                    // Arrives mid-swing: pressed so the bat meets the ball on its way up.
                    game.update(FIXED_DT, CLOCK)
                    game.onTouch(TouchType.DOWN, 1L, if (side == 0) LEFT_X else RIGHT_X, FLIP_Y, 0L)
                }
                var t = 0f
                var lastX = game.botBallX(0)
                var lastY = game.botBallY(0)
                var lastAng = game.botFlipperAngle(side)
                while (t < 0.2f && game.botBallInPlay(0)) {
                    game.update(FIXED_DT, CLOCK)
                    t += FIXED_DT
                    val ang = game.botFlipperAngle(side)
                    val bx = game.botBallX(0)
                    val by = game.botBallY(0)
                    // The ball's path this step against the bat's centre line, before and after it moved.
                    for (g in floatArrayOf(lastAng, ang)) {
                        if (crosses(lastX, lastY, bx, by, px, py, px + cos(g) * len * 0.97f, py + sin(g) * len * 0.97f)) {
                            fail("flipper $side mode $mode: a ${speed.toInt()} u/s ball at $deg° hitting $along passed through the bat ($lastX, $lastY → $bx, $by)")
                        }
                    }
                    val ex = cos(ang) * len
                    val ey = sin(ang) * len
                    val u = ((bx - px) * ex + (by - py) * ey) / (len * len)
                    if (u in 0f..1f) {
                        val dx = bx - (px + ex * u)
                        val dy = by - (py + ey * u)
                        val reach = T.FLIP_R0 + (T.FLIP_R1 - T.FLIP_R0) * u + T.BALL_R
                        if (sqrt(dx * dx + dy * dy) < reach * 0.5f) fail("flipper $side mode $mode: a ${speed.toInt()} u/s ball at $deg° hitting $along sank into the bat (at $bx, $by)")
                    }
                    lastX = bx
                    lastY = by
                    lastAng = ang
                }
                shots++
            }
        }
        return shots
    }

    @Test
    fun theFastestBallNeverTunnelsThroughAFlipper() {
        var shots = 0
        for (s in floatArrayOf(PinballTuning.MAX_SPEED, PinballTuning.MAX_SPEED * 0.6f, 600f)) {
            for (mode in 0..2) {
                shots += fireAtFlipper(0, mode, s, sweep(60f, 120f, 5f))
                shots += fireAtFlipper(1, mode, s, sweep(60f, 120f, 5f))
            }
        }
        println("flipper sweep: $shots shots, none tunnelled")
        assertTrue(shots > 400)
    }

    // ---------------------------------------------------------------- controls

    @Test
    fun bothFlippersHoldAtOnceAndEachLetsGoWithItsOwnFinger() {
        val game = startedGame()
        game.onTouch(TouchType.DOWN, 1L, LEFT_X, FLIP_Y, 0L)
        game.onTouch(TouchType.DOWN, 2L, RIGHT_X, FLIP_Y, 5L)
        steps(game, 0.15f)
        assertTrue(game.botFlipperHeld(0))
        assertTrue(game.botFlipperHeld(1))
        assertEquals(T.upAngle(0), game.botFlipperAngle(0), 1e-3f)
        assertEquals(T.upAngle(1), game.botFlipperAngle(1), 1e-3f)
        // Strangers' events change nothing.
        game.onTouch(TouchType.UP, 7L, LEFT_X, FLIP_Y, 20L)
        game.onTouch(TouchType.MOVE, 8L, RIGHT_X, FLIP_Y, 20L)
        assertTrue(game.botFlipperHeld(0) && game.botFlipperHeld(1))
        // Lifting the left finger drops only the left flipper.
        game.onTouch(TouchType.UP, 1L, LEFT_X, FLIP_Y, 30L)
        steps(game, 0.15f)
        assertFalse(game.botFlipperHeld(0))
        assertTrue(game.botFlipperHeld(1))
        assertEquals(T.restAngle(0), game.botFlipperAngle(0), 1e-3f)
        assertEquals(T.upAngle(1), game.botFlipperAngle(1), 1e-3f)
        game.onTouch(TouchType.UP, 2L, RIGHT_X, FLIP_Y, 40L)
        steps(game, 0.15f)
        assertFalse(game.botFlipperHeld(1))
        assertEquals(T.restAngle(1), game.botFlipperAngle(1), 1e-3f)
    }

    @Test
    fun aFlipperFollowsTheFingerThatPressedItAcrossTheMiddle() {
        val game = startedGame()
        game.onTouch(TouchType.DOWN, 1L, LEFT_X, FLIP_Y, 0L)
        game.onTouch(TouchType.MOVE, 1L, 300f, FLIP_Y, 40L)
        assertTrue(game.botFlipperHeld(0))
        assertFalse("sliding across doesn't press the other flipper", game.botFlipperHeld(1))
        // A second finger on the left while the first still holds it does nothing new.
        game.onTouch(TouchType.DOWN, 2L, LEFT_X, FLIP_Y, 50L)
        game.onTouch(TouchType.UP, 2L, LEFT_X, FLIP_Y, 60L)
        assertTrue(game.botFlipperHeld(0))
        game.onTouch(TouchType.UP, 1L, 300f, FLIP_Y, 70L)
        assertFalse(game.botFlipperHeld(0))
        assertFalse(game.botFlipperHeld(1))
    }

    private fun plungeWithPull(pull: Float): PinballGame {
        val game = startedGame()
        assertTrue(game.botLaneReady)
        game.onTouch(TouchType.DOWN, 3L, PLUNGE_X, PLUNGE_Y, 0L)
        assertTrue(game.botPlungerHeld)
        game.onTouch(TouchType.MOVE, 3L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * pull, 100L)
        assertEquals(pull.coerceAtMost(1f), game.botPull, 1e-4f)
        game.onTouch(TouchType.UP, 3L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * pull, 200L)
        return game
    }

    @Test
    fun plungerStrengthScalesWithThePull() {
        var last = 0f
        for (pull in floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 1f, 1.5f)) {
            val game = plungeWithPull(pull)
            val speed = game.botLastLaunchSpeed
            val want = PinballTuning.LAUNCH_MIN + (PinballTuning.LAUNCH_MAX - PinballTuning.LAUNCH_MIN) * pull.coerceAtMost(1f)
            assertEquals(want, speed, 0.5f)
            assertTrue("pull $pull launched at $speed, not faster than $last", speed >= last)
            last = speed
            assertFalse(game.botLaneReady)
        }
        // A tap on the plunger (no pull) doesn't fire the ball.
        val tapped = plungeWithPull(0.02f)
        assertEquals(0, tapped.botLaunches)
        assertTrue(tapped.botLaneReady)
        // A full plunge clears the gate and reaches the playfield.
        val full = plungeWithPull(1f)
        steps(full, 1.2f)
        assertTrue(full.botBallInPlay(0) && full.botBallX(0) < T.PLAY_W)
    }

    /** Steps until ball 0 is back on the plunger (or [limit] seconds pass); true if it got there. */
    private fun rollBack(game: PinballGame, limit: Float, each: () -> Unit = {}): Boolean {
        var t = 0f
        while (!game.botBallInLane(0) && t < limit) {
            each()
            game.update(FIXED_DT, CLOCK)
            t += FIXED_DT
        }
        return game.botBallInLane(0)
    }

    @Test
    fun aWeakPlungeRollsBackForTheNextPullNotAFreeAutoLaunch() {
        val game = plungeWithPull(0.2f)
        assertEquals(1, game.botLaunches)
        assertTrue("a weak plunge should fall short of the gate and roll back", rollBack(game, 6f))
        // Long enough for an automatic relaunch (AUTO_DELAY) to have happened if one were coming.
        steps(game, 1.5f)
        assertEquals("the machine must not relaunch a ball the player plunged too weakly", 1, game.botLaunches)
        assertTrue("the ball waits on the plunger for the player again", game.botLaneReady)
        // The player's second try goes.
        game.onTouch(TouchType.DOWN, 4L, PLUNGE_X, PLUNGE_Y, 0L)
        game.onTouch(TouchType.MOVE, 4L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 100L)
        game.onTouch(TouchType.UP, 4L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 200L)
        assertEquals(2, game.botLaunches)
    }

    @Test
    fun aWeakPlungeDuringMultiballIsRelaunchedByTheMachine() {
        val game = plungeWithPull(0.2f)
        // The player is busy with another ball elsewhere on the table (parked so it can't drain).
        game.botPlace(1, T.CX, 200f, 0f, 0f)
        val park = { game.botHold(1, T.CX, 200f) }
        assertTrue(rollBack(game, 6f, park))
        assertFalse("with another ball in play the player can't be plunging", game.botLaneReady)
        var t = 0f
        while (t < 1.5f) {
            park()
            game.update(FIXED_DT, CLOCK)
            t += FIXED_DT
        }
        assertEquals("the machine plunges the returned ball itself", 2, game.botLaunches)
    }

    @Test
    fun cancelInputDropsBothFlippersAndThePlungerAndANewFingerWorks() {
        val game = startedGame()
        game.onTouch(TouchType.DOWN, 1L, LEFT_X, FLIP_Y, 0L)
        game.onTouch(TouchType.DOWN, 2L, RIGHT_X, FLIP_Y, 0L)
        game.onTouch(TouchType.DOWN, 3L, PLUNGE_X, PLUNGE_Y, 0L)
        game.onTouch(TouchType.MOVE, 3L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 50L)
        steps(game, 0.1f)
        assertTrue(game.botFlipperHeld(0) && game.botFlipperHeld(1) && game.botPlungerHeld)
        game.cancelInput()
        assertFalse(game.botFlipperHeld(0))
        assertFalse(game.botFlipperHeld(1))
        assertFalse(game.botPlungerHeld)
        assertEquals(0f, game.botPull, 0f)
        steps(game, 0.2f)
        assertEquals(T.restAngle(0), game.botFlipperAngle(0), 1e-3f)
        assertEquals(T.restAngle(1), game.botFlipperAngle(1), 1e-3f)
        // The lost fingers' late events are harmless: nothing fires, nothing flips.
        game.onTouch(TouchType.MOVE, 3L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 300L)
        game.onTouch(TouchType.UP, 3L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 310L)
        game.onTouch(TouchType.UP, 1L, LEFT_X, FLIP_Y, 310L)
        assertEquals(0, game.botLaunches)
        assertTrue(game.botLaneReady)
        // New fingers work.
        game.onTouch(TouchType.DOWN, 4L, LEFT_X, FLIP_Y, 400L)
        assertTrue(game.botFlipperHeld(0))
        game.onTouch(TouchType.DOWN, 5L, PLUNGE_X, PLUNGE_Y, 400L)
        game.onTouch(TouchType.MOVE, 5L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 450L)
        game.onTouch(TouchType.UP, 5L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, 500L)
        assertEquals(1, game.botLaunches)
    }

    // ---------------------------------------------------------------- rules

    /** Drops ball 0 straight down the middle, between the resting flippers. */
    private fun drainOne(game: PinballGame) {
        game.botPlace(0, T.CX, 540f, 0f, 300f)
        var t = 0f
        while (game.botBallInPlay(0) && t < 2f) {
            game.update(FIXED_DT, CLOCK)
            t += FIXED_DT
        }
        assertFalse("the ball should have drained", game.botBallInPlay(0))
    }

    @Test
    fun drainingEndsTheBallAndTheLastBallEndsTheRound() {
        val game = emptyTable()
        game.botEndBallSave()
        for (ball in 1..PinballTuning.BALLS) {
            drainOne(game)
            assertEquals(ball, game.botDrains)
            assertEquals(PinballTuning.BALLS - ball, game.botBallsLeft)
            steps(game, PinballTuning.SERVE_DELAY + 0.2f)
            if (ball < PinballTuning.BALLS) {
                assertTrue("the next ball waits on the plunger", game.botLaneReady)
                assertFalse(game.finished)
                game.botRemove(game.botLaneBallIndex())
                game.botEndBallSave()
            }
        }
        assertTrue(game.botEndedEarly)
        assertTrue("out of balls ends the round", game.finished)
    }

    @Test
    fun aDrainDuringBallSaveGivesTheBallBack() {
        val game = plungeWithPull(1f)
        assertTrue(game.botBallSaveLeft > 0f)
        game.botRemove(0)
        drainOne(game)
        assertEquals(0, game.botDrains)
        assertEquals(PinballTuning.BALLS, game.botBallsLeft)
        steps(game, 1.5f)
        assertEquals("the saved ball is launched again", 1, game.botLiveBalls)
    }

    @Test
    fun nothingScoresAfterTimeUp() {
        val game = PinballGame()
        val d = RoundDriver(game, 11L)
        d.play(game.roundSeconds + 1f)
        assertTrue(game.finished)
        val before = game.score
        // A ball thrown into a bumper, through the rollovers and across the spinner during ENDING.
        game.botPlace(0, T.BUMPER_X[0], T.BUMPER_Y[0] - 40f, 0f, 900f)
        game.botPlace(1, T.ROLLOVER_X[1], T.ROLLOVER_Y - 12f, 0f, 400f)
        game.botPlace(2, 20f, T.SPINNER_Y + 20f, 0f, -1500f)
        game.onTouch(TouchType.DOWN, 9L, LEFT_X, FLIP_Y, d.ms)
        assertFalse("flippers are dead after time-up", game.botFlipperHeld(0))
        d.end()
        assertEquals(before, game.score)
    }

    @Test
    fun scoringRules() {
        // A bumper hit scores and flashes.
        val game = emptyTable()
        game.botPlace(0, T.BUMPER_X[2], T.BUMPER_Y[2] - 40f, 0f, 600f)
        steps(game, 0.1f)
        assertTrue(game.botBumperHits >= 1)
        assertTrue(game.score >= PinballTuning.BUMPER_POINTS)
        // Knocking down all three drop targets raises the multiplier.
        val g2 = emptyTable()
        for (i in 0 until 3) {
            g2.botPlace(0, T.DROP_X - 60f, T.DROP_Y0[i] + T.DROP_LEN / 2f, 900f, 0f)
            steps(g2, 0.12f)
        }
        assertEquals(0, g2.botDropsUp)
        assertEquals(2, g2.botMultiplier)
        g2.botRemove(0)
        steps(g2, 2f)
        assertEquals("the bank resets", 3, g2.botDropsUp)
        // Lighting all three top lanes starts multiball; the orbit then pays the jackpot.
        val g3 = emptyTable()
        for (i in 0 until 3) {
            g3.botPlace(0, T.ROLLOVER_X[i], T.ROLLOVER_Y - 12f, 0f, 300f)
            steps(g3, 0.1f)
            g3.botRemove(0)
        }
        assertEquals(1, g3.botMultiballs)
        steps(g3, 1f)
        assertEquals("the second ball is launched", 1, g3.botLiveBalls)
        g3.botPlace(0, 20f, T.SPINNER_Y + 30f, 0f, -1400f)
        steps(g3, 0.1f)
        assertEquals(1, g3.botJackpots)
    }

    @Test
    fun nudgingTooMuchTilts() {
        val game = startedGame()
        game.botRemove(0)
        game.botPlace(0, T.CX, 300f, 0f, 0f)
        var ms = 0L
        var id = 20L
        repeat(3) {
            game.onTouch(TouchType.DOWN, id, LEFT_X, FLIP_Y, ms)
            game.onTouch(TouchType.MOVE, id, LEFT_X, FLIP_Y - 80f, ms + 60)
            game.onTouch(TouchType.UP, id, LEFT_X, FLIP_Y - 80f, ms + 80)
            id++
            ms += 200
            game.update(FIXED_DT, CLOCK)
        }
        assertTrue(game.botTilted)
        game.onTouch(TouchType.DOWN, 99L, LEFT_X, FLIP_Y, ms)
        steps(game, 0.1f)
        assertFalse("a tilted machine's flippers are dead", game.botFlipperHeld(0))
        val s = game.score
        game.botPlace(0, T.BUMPER_X[0], T.BUMPER_Y[0] - 40f, 0f, 600f)
        steps(game, 0.2f)
        assertEquals("no scoring while tilted", s, game.score)
        // The tilt lasts until the ball drains.
        game.onTouch(TouchType.UP, 99L, LEFT_X, FLIP_Y, ms)
        game.botEndBallSave()
        drainOne(game)
        assertFalse(game.botTilted)
    }

    // ---------------------------------------------------------------- failsafes

    @Test
    fun aBallBalancedOnAPostIsKickedFree() {
        val game = emptyTable()
        // Exactly on top of a lane guide's round tip: the forces balance and it never moves.
        val px = T.LANE_GUIDE_X[1]
        game.botPlace(0, px, T.LANE_GUIDE_Y0 - 3.5f - T.BALL_R, 0f, 0f)
        steps(game, 1f)
        assertEquals("balanced", px, game.botBallX(0), 1e-3f)
        steps(game, PinballTuning.STUCK_SECONDS + 0.2f)
        assertEquals(1, game.botSearchKicks)
        steps(game, 0.5f)
        assertTrue("kicked away", kotlin.math.abs(game.botBallX(0) - px) > 5f || !game.botBallInPlay(0))
    }

    @Test
    fun aBallThePhysicsCannotFinishIsServedAgain() {
        val game = emptyTable()
        game.botPlace(0, T.CX, 300f, Float.NaN, 100f)
        steps(game, 0.1f)
        assertEquals(1, game.botFailsafeTrips)
        assertEquals("no ball lost", PinballTuning.BALLS, game.botBallsLeft)
        steps(game, 1.5f)
        assertEquals(1, game.botLiveBalls)
        for (i in 0 until game.botMaxBalls) if (game.botBallInPlay(i)) assertTrue(game.botBallX(i).isFinite())
    }

    @Test
    fun aRoundAlwaysFinishesAndTheNextStartsClean() {
        val game = PinballGame()
        val rng = Random(3)
        val stats = Stats("clean")
        // A busy round: the good bot plays it, building up multipliers, lanes and targets.
        val bot = PinballSimulationTest.FlipperBot(game, rng, 0f, 0f, 0.035f, 0f, 1f)
        playRound(game, 5L, stats) { t, ms -> bot.tick(t, ms) }
        // Leave some state lying around, as a real round would.
        game.onTouch(TouchType.DOWN, 1L, LEFT_X, FLIP_Y, 0L)
        game.seed = 6L
        game.start(simFx)
        assertEquals(0, game.score)
        assertEquals(1, game.botMultiplier)
        assertEquals(3, game.botDropsUp)
        assertEquals(PinballTuning.BALLS, game.botBallsLeft)
        assertEquals(1, game.botLiveBalls)
        assertTrue(game.botLaneReady)
        assertFalse(game.botFlipperHeld(0) || game.botFlipperHeld(1) || game.botPlungerHeld)
        assertFalse(game.botTilted)
        assertEquals(0, game.botDrains + game.botFailsafeTrips + game.botBumperHits + game.botMultiballs + game.botLaunches)
        for (i in 0 until 3) assertFalse(game.botLaneLit(i))
        assertEquals(T.restAngle(0), game.botFlipperAngle(0), 1e-4f)
        assertEquals(T.restAngle(1), game.botFlipperAngle(1), 1e-4f)
        assertFalse(game.finished)
    }

    @Test
    fun noNaNOrEscapesOverLongWildRounds() {
        val game = PinballGame()
        val rng = Random(9)
        val stats = Stats("wild")
        var checked = 0L
        repeat(10) { round ->
            var id = 1000L
            val held = longArrayOf(-1L, -1L)
            playRound(game, 100L + round, stats) { _, ms ->
                // Mash everything: random flips, plunges and the odd nudge.
                for (s in 0..1) {
                    if (held[s] < 0L && rng.nextFloat() < 0.04f) {
                        held[s] = id++
                        game.onTouch(TouchType.DOWN, held[s], if (s == 0) LEFT_X else RIGHT_X, FLIP_Y, ms)
                        if (rng.nextFloat() < 0.05f) game.onTouch(TouchType.MOVE, held[s], if (s == 0) LEFT_X else RIGHT_X, FLIP_Y - 90f, ms + 40)
                    } else if (held[s] >= 0L && rng.nextFloat() < 0.08f) {
                        game.onTouch(TouchType.UP, held[s], LEFT_X, FLIP_Y, ms)
                        held[s] = -1L
                    }
                }
                if (game.botLaneReady && rng.nextFloat() < 0.02f) {
                    val p = id++
                    game.onTouch(TouchType.DOWN, p, PLUNGE_X, PLUNGE_Y, ms)
                    game.onTouch(TouchType.MOVE, p, PLUNGE_X, PLUNGE_Y + rng.nextFloat() * 120f, ms + 30)
                    game.onTouch(TouchType.UP, p, PLUNGE_X, PLUNGE_Y + 60f, ms + 60)
                }
                assertTrue(game.botLiveBalls <= game.botMaxBalls)
                for (i in 0 until game.botMaxBalls) {
                    if (!game.botBallInPlay(i)) continue
                    val x = game.botBallX(i)
                    val y = game.botBallY(i)
                    assertTrue("ball $i at ($x, $y)", x.isFinite() && y.isFinite() && game.botBallVX(i).isFinite() && game.botBallVY(i).isFinite())
                    assertTrue("ball $i escaped to ($x, $y)", x > -1f && x < T.W + 1f && y > -1f)
                    checked++
                }
            }
            assertEquals("no failsafe retirements in round $round", 0, game.botFailsafeTrips)
        }
        println("wild rounds: $checked ball-steps checked, scores ${stats.scores}")
    }
}
