package com.pocketarcade.games.pinball

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.approach
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.hub.CabinetDesign
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import com.pocketarcade.games.pinball.PinballTable as T

/** Difficulty and payout knobs for pinball. Speeds are table units per second. */
object PinballTuning {
    const val ROUND_SECONDS = 60f
    /** Balls per round; losing the last one ends the round early. */
    const val BALLS = 3

    // Physics.
    const val GRAVITY = 1000f
    const val MAX_SPEED = 2000f
    /** Fraction of speed lost per second rolling across the playfield. */
    const val DRAG = 0.04f
    /** Normal speeds below this don't bounce (a ball resting on a flipper or a guide stays put). */
    const val REST_SPEED = 45f
    const val FLIP_UP_SPEED = 24f
    const val FLIP_DOWN_SPEED = 14f
    const val FLIP_BOUNCE = 0.3f
    const val POST_BOUNCE = 0.55f
    const val BUMPER_BOUNCE = 0.5f
    /** Speed a pop bumper throws the ball away with. */
    const val BUMPER_KICK = 560f
    const val SLING_KICK = 470f
    /** Approach speed a slingshot or drop target needs to fire. */
    const val SLING_MIN = 70f
    const val DROP_MIN = 60f
    /** Largest movement per physics sub-step, as a fraction of the ball radius (never tunnel). */
    const val SUBSTEP_FRACTION = 0.45f

    // Plunger.
    const val LAUNCH_MIN = 500f
    const val LAUNCH_MAX = 1750f
    /** Pull (0..1) below which letting go doesn't fire the ball. */
    const val MIN_PULL = 0.08f
    /** Field units of drag for a full pull. */
    const val PULL_RANGE = 110f
    const val AUTO_LAUNCH_SPEED = 1650f
    /** The plunger answers touches below and right of this corner of the field (when a ball waits). */
    const val PLUNGER_ZONE_X = 250f
    const val PLUNGER_ZONE_Y = 380f

    // Help and failsafes.
    const val BALL_SAVE_SECONDS = 6f
    const val MULTIBALL_SAVE_SECONDS = 8f
    const val SERVE_DELAY = 1.1f
    /** A ball slower than this for [STUCK_SECONDS] (and not cradled on a flipper) gets kicked free. */
    const val STUCK_SPEED = 30f
    const val STUCK_SECONDS = 3f
    const val STUCK_KICK = 480f
    /** After this many kicks in a row the ball is taken off and served again (no ball lost). */
    const val STUCK_KICKS = 3
    /** A stall within this distance of the last kick counts as the same stuck spot. */
    const val STUCK_RADIUS = 40f
    /** Seconds of free play after a kick that clear the kick count. */
    const val STUCK_FREE_SECONDS = 8f

    // Nudge and tilt: a quick upward swipe of a flipper thumb.
    const val NUDGE_DIST = 45f
    const val NUDGE_MS = 170L
    const val NUDGE_KICK = 170f
    const val TILT_WARN = 1.6f
    const val TILT_AT = 2.6f
    /** Tilt meter drained per second. */
    const val TILT_DECAY = 0.45f

    // Scoring (every award is multiplied by the playfield multiplier).
    const val BUMPER_POINTS = 10
    const val SLING_POINTS = 5
    const val DROP_POINTS = 25
    const val BANK_POINTS = 100
    const val LANE_POINTS = 25
    const val LANE_REPEAT_POINTS = 5
    const val MULTIBALL_POINTS = 150
    const val SPINNER_POINTS = 3
    const val ORBIT_POINTS = 40
    const val JACKPOT_POINTS = 300
    const val MAX_MULT = 5

    const val POINTS_PER_TICKET = 60
    const val BASE_TICKETS = 1
}

/**
 * Pinball on a 3D table: hold the left or right half of the screen for that flipper (both at
 * once works), pull the plunger down and let go to launch. Pop bumpers, slingshots, a drop
 * target bank (completing it raises the multiplier), three top rollovers (light them all for
 * two-ball multiball with a jackpot on the left orbit's spinner) and a ball save for the first
 * seconds of every ball. Three balls, or the clock, whichever runs out first.
 *
 * The simulation is 2D on the playfield plane (see [PinballTable]), sub-stepped so the fastest
 * ball never passes through a flipper or a wall; the 3D view looks down the table from the
 * player's end.
 */
class PinballGame : BaseMiniGame() {
    override val id = "pinball"
    override val title = "STAR FLIPPER"
    override val marquee = "FLIP"
    override val instructions = listOf(
        "HOLD LEFT / RIGHT HALF TO FLIP",
        "PULL THE PLUNGER DOWN, LET GO",
        "LIGHT 3 TOP LANES = MULTIBALL",
        "DROP TARGETS = MULTIPLIER",
        "SWIPE UP TO NUDGE - DON'T TILT!",
    )
    override val look = CabinetLook(body = Pal.PURPLE, trim = Pal.CYAN, glow = Pal.HOTPINK, shape = CabinetShape.PINBALL)
    override val cabinet: CabinetDesign get() = PinballCabinet
    override val roundSeconds = PinballTuning.ROUND_SECONDS

    private companion object {
        const val OFF = 0
        /** Resting on the plunger in the shooter lane. */
        const val LANE = 1
        const val PLAY = 2
        const val MAX_BALLS = 3
        /** Seconds before a saved or multiball ball is launched by the machine. */
        const val AUTO_DELAY = 0.6f
        const val MSG_SECONDS = 1.6f

        val BUMPER_COLORS = intArrayOf(Pal.HOTPINK, Pal.CYAN, Pal.YELLOW)
        val HIT_PINK = intArrayOf(Pal.HOTPINK, Pal.PINK, Pal.WHITE)
        val HIT_CYAN = intArrayOf(Pal.CYAN, Pal.SKY, Pal.WHITE)
        val HIT_GOLD = intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE)
        val HIT_LIME = intArrayOf(Pal.LIME, Pal.GREEN, Pal.WHITE)
        val DROP_COLORS = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.RED)

        // Message texts, so showing one never builds a string.
        const val M_NONE = 0
        const val M_SHOOT = 1
        const val M_SAVED = 2
        const val M_MULTIBALL = 3
        const val M_JACKPOT = 4
        const val M_TILT = 5
        const val M_DANGER = 6
        const val M_BANK = 7
        const val M_BALL = 8
        const val M_OVER = 9
        const val M_LANES = 10
        const val M_ORBIT = 11
        const val M_SEARCH = 12
        val MESSAGES = arrayOf(
            "", "SHOOT!", "BALL SAVED", "MULTIBALL!", "JACKPOT!", "TILT", "DANGER",
            "MULTIPLIER UP", "NEXT BALL", "GAME OVER", "LANES LIT", "ORBIT", "BALL SEARCH",
        )
        val MULT_TEXT = arrayOf("", "1X", "2X", "3X", "4X", "5X")
        val BALL_TEXT = arrayOf("BALL 1", "BALL 1", "BALL 2", "BALL 3", "BALL 4", "BALL 5")
        val PULL_TEXT = "PULL " + ArcadeFont.DOWN
        val HOLD_LEFT = ArcadeFont.LEFT + " HOLD"
        val HOLD_RIGHT = "HOLD " + ArcadeFont.RIGHT
    }

    private class Ball {
        var state = OFF
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        /** Position at the start of the step, for the rollover and spinner switches. */
        var px = 0f
        var py = 0f
        /** Seconds until the machine launches it from the lane; negative when the player plunges it. */
        var autoT = -1f
        /** Seconds spent crawling (the stuck-ball failsafe). */
        var slowT = 0f
        var searchKicks = 0
        /** Where the last failsafe kick happened, and seconds of free play since. */
        var kickX = 0f
        var kickY = 0f
        var freeT = 0f
        /** Resting on a raised, held flipper this step: crawling there is on purpose. */
        var cradled = false
    }

    private val balls = Array(MAX_BALLS) { Ball() }

    // Flippers: 0 left, 1 right.
    private val flipAng = FloatArray(2)
    private val flipOmega = FloatArray(2)
    private val flipId = longArrayOf(-1L, -1L)
    private val flipDownY = FloatArray(2)
    private val flipDownMs = LongArray(2)
    private val flipNudged = BooleanArray(2)

    // Plunger.
    private var plungerId = -1L
    private var plungerDownY = 0f
    private var pull = 0f
    /** What the plunger rod shows: follows the pull, snaps forward on release. */
    private var plungerVis = 0f

    // Rules state.
    private var ballsLeft = PinballTuning.BALLS
    private var ballNumber = 1
    private var serveT = 0f
    private var ballSaveT = 0f
    private var ballSaveArmed = true
    private var multiplier = 1
    private var multiball = false
    private val laneLit = BooleanArray(3)
    private val dropUp = BooleanArray(3)
    private var bankResetT = 0f
    private var tiltMeter = 0f
    private var tilted = false

    // Lamps and moving parts.
    private val bumperFlash = FloatArray(3)
    private val bumperCool = FloatArray(3)
    private val slingFlash = FloatArray(2)
    private val slingCool = FloatArray(2)
    private val dropSink = FloatArray(3)
    private val dropFlash = FloatArray(3)
    private val laneFlash = FloatArray(3)
    private var spinAngle = 0f
    private var spinSpeed = 0f
    private var spinScored = 0f
    private var jackpotFlash = 0f
    private var message = M_NONE
    private var messageT = 0f

    // Contact scratch (written by [contact]).
    private var hitNX = 0f
    private var hitNY = 0f

    // Counters for tests and tuning.
    private var drains = 0
    private var failsafeTrips = 0
    private var searchKicksTotal = 0
    private var bumperHits = 0
    private var multiballs = 0
    private var jackpots = 0
    private var lastLaunchSpeed = 0f
    private var launches = 0

    private val labels = arrayOfNulls<String>(4096)

    override fun reset() {
        for (b in balls) b.state = OFF
        for (s in 0..1) {
            flipAng[s] = T.restAngle(s)
            flipOmega[s] = 0f
            flipId[s] = -1L
            flipNudged[s] = false
        }
        plungerId = -1L
        pull = 0f
        plungerVis = 0f
        ballsLeft = PinballTuning.BALLS
        ballNumber = 1
        serveT = 0f
        ballSaveT = 0f
        multiplier = 1
        multiball = false
        laneLit.fill(false)
        dropUp.fill(true)
        dropSink.fill(0f)
        dropFlash.fill(0f)
        laneFlash.fill(0f)
        bankResetT = 0f
        tiltMeter = 0f
        tilted = false
        bumperFlash.fill(0f)
        bumperCool.fill(0f)
        slingFlash.fill(0f)
        slingCool.fill(0f)
        spinAngle = 0f
        spinSpeed = 0f
        spinScored = 0f
        jackpotFlash = 0f
        drains = 0
        failsafeTrips = 0
        searchKicksTotal = 0
        bumperHits = 0
        multiballs = 0
        jackpots = 0
        lastLaunchSpeed = 0f
        launches = 0
        serveBall(auto = false)
        ballSaveArmed = true
        show(M_SHOOT)
    }

    override fun ticketsFor(score: Int): Int = PinballTuning.BASE_TICKETS + score / PinballTuning.POINTS_PER_TICKET

    /** Nothing scores after the clock runs out, so the round is over the moment it does. */
    override fun isSettled(): Boolean = timeUp || liveBalls() == 0

    override fun onTimeUp() {
        cancelInput()
        serveT = 0f
    }

    /** Drops both flippers and lets the plunger go without firing. The balls roll on. */
    override fun cancelInput() {
        flipId[0] = -1L
        flipId[1] = -1L
        plungerId = -1L
        pull = 0f
    }

    // ---------------------------------------------------------------- input

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> {
                if (timeUp || id == plungerId || id == flipId[0] || id == flipId[1]) return
                if (plungerId < 0L && x >= PinballTuning.PLUNGER_ZONE_X && y >= PinballTuning.PLUNGER_ZONE_Y && laneBall() >= 0) {
                    plungerId = id
                    plungerDownY = y
                    pull = 0f
                    play(Sfx.CLINK, 0.3f, 0.7f)
                    return
                }
                val side = if (x < GAME_W / 2f) 0 else 1
                if (flipId[side] >= 0L) return
                flipId[side] = id
                flipDownY[side] = y
                flipDownMs[side] = timeMs
                flipNudged[side] = false
                if (!tilted) {
                    play(Sfx.FLIPPER, 0.9f, if (side == 0) 0.96f else 1.04f)
                    fx.haptics.tick()
                    changeLanes(side)
                }
            }
            TouchType.MOVE -> {
                if (id == plungerId) {
                    pull = ((y - plungerDownY) / PinballTuning.PULL_RANGE).coerceIn(0f, 1f)
                    return
                }
                for (s in 0..1) {
                    if (id != flipId[s] || flipNudged[s]) continue
                    if (flipDownY[s] - y > PinballTuning.NUDGE_DIST && timeMs - flipDownMs[s] <= PinballTuning.NUDGE_MS) {
                        flipNudged[s] = true
                        nudge()
                    }
                }
            }
            TouchType.UP -> {
                if (id == plungerId) {
                    plungerId = -1L
                    val p = pull
                    pull = 0f
                    val i = laneBall()
                    if (p >= PinballTuning.MIN_PULL && i >= 0 && !timeUp) {
                        launch(balls[i], PinballTuning.LAUNCH_MIN + (PinballTuning.LAUNCH_MAX - PinballTuning.LAUNCH_MIN) * p)
                        if (ballSaveArmed) {
                            ballSaveArmed = false
                            ballSaveT = PinballTuning.BALL_SAVE_SECONDS
                        }
                    }
                    return
                }
                for (s in 0..1) if (id == flipId[s]) {
                    flipId[s] = -1L
                    if (!tilted && !timeUp) play(Sfx.FLIPPER, 0.25f, 0.7f)
                }
            }
        }
    }

    /** Pressing a flipper shifts the lit top lanes that way (lane change). */
    private fun changeLanes(side: Int) {
        val a = laneLit[0]
        val b = laneLit[1]
        val c = laneLit[2]
        if (side == 0) {
            laneLit[0] = b; laneLit[1] = c; laneLit[2] = a
        } else {
            laneLit[0] = c; laneLit[1] = a; laneLit[2] = b
        }
    }

    private fun nudge() {
        if (tilted) return
        for (b in balls) {
            if (b.state != PLAY) continue
            b.vy -= PinballTuning.NUDGE_KICK
            b.vx += (rng.nextFloat() - 0.5f) * 100f
        }
        shake.add(0.35f)
        play(Sfx.THUD, 0.8f, 0.8f)
        fx.haptics.heavy()
        tiltMeter += 1f
        if (tiltMeter >= PinballTuning.TILT_AT) {
            tilted = true
            show(M_TILT)
            play(Sfx.TILT)
            flash.trigger(0.5f)
            // A tilted machine goes dead: the flippers drop until the ball drains.
            flipId[0] = -1L
            flipId[1] = -1L
        } else if (tiltMeter >= PinballTuning.TILT_WARN) {
            show(M_DANGER)
            play(Sfx.TILT, 0.5f, 1.3f)
        }
    }

    // ---------------------------------------------------------------- balls in and out

    private fun liveBalls(): Int {
        var n = 0
        for (b in balls) if (b.state != OFF) n++
        return n
    }

    /** The ball waiting for the player's plunger, or -1. */
    private fun laneBall(): Int {
        for (i in balls.indices) if (balls[i].state == LANE && balls[i].autoT < 0f) return i
        return -1
    }

    private fun serveBall(auto: Boolean) {
        val b = balls.firstOrNull { it.state == OFF } ?: return
        b.state = LANE
        b.x = T.LANE_X
        b.y = T.LANE_REST_Y
        b.vx = 0f
        b.vy = 0f
        b.px = b.x
        b.py = b.y
        b.autoT = if (auto) AUTO_DELAY else -1f
        b.slowT = 0f
        b.searchKicks = 0
        if (!auto) play(Sfx.CLINK, 0.5f, 1.2f)
    }

    private fun launch(b: Ball, speed: Float) {
        b.state = PLAY
        b.vx = 0f
        b.vy = -speed
        b.autoT = -1f
        b.slowT = 0f
        lastLaunchSpeed = speed
        launches++
        plungerVis = 0f
        play(Sfx.PLUNGER, 0.9f, 0.8f + speed / 3500f)
        fx.haptics.tick()
    }

    private fun drain(b: Ball) {
        b.state = OFF
        if (timeUp) return
        if (ballSaveT > 0f && !tilted) {
            serveBall(auto = true)
            show(M_SAVED)
            play(Sfx.LUCKY, 0.8f)
            return
        }
        val live = liveBalls()
        if (live > 0) {
            if (live == 1 && multiball) multiball = false
            play(Sfx.DRAIN, 0.4f, 1.3f)
            return
        }
        drains++
        play(Sfx.DRAIN)
        fx.haptics.heavy()
        shake.add(0.3f)
        multiball = false
        multiplier = 1
        tilted = false
        tiltMeter = 0f
        ballsLeft--
        if (ballsLeft > 0) {
            ballNumber++
            serveT = PinballTuning.SERVE_DELAY
            show(M_BALL)
        } else {
            endedEarly = true
            show(M_OVER)
        }
    }

    /** Failsafe: takes a ball the physics can't finish off the table and serves it again, free. */
    private fun retire(b: Ball) {
        failsafeTrips++
        b.state = OFF
        if (!timeUp) {
            serveBall(auto = true)
            show(M_SEARCH)
        }
    }

    private fun startMultiball() {
        multiball = true
        multiballs++
        ballSaveT = maxOf(ballSaveT, PinballTuning.MULTIBALL_SAVE_SECONDS)
        serveBall(auto = true)
        show(M_MULTIBALL)
        play(Sfx.JACKPOT, 0.8f)
        play(Sfx.CHEER, 0.5f)
        fx.haptics.win()
        flash.trigger(0.6f)
        particles.confetti(0f, 0f, GAME_W, 60)
    }

    // ---------------------------------------------------------------- simulation

    override fun step(dt: Float) {
        stepTimers(dt)
        if (serveT > 0f) {
            serveT -= dt
            if (serveT <= 0f && !timeUp) {
                serveBall(auto = false)
                ballSaveArmed = true
            }
        }
        for (b in balls) {
            if (b.state == LANE && b.autoT >= 0f) {
                b.autoT -= dt
                if (b.autoT < 0f) launch(b, PinballTuning.AUTO_LAUNCH_SPEED)
            }
        }
        for (b in balls) {
            b.px = b.x
            b.py = b.y
            b.cradled = false
        }
        val n = substeps(dt)
        val h = dt / n
        for (s in 0 until n) {
            moveFlippers(h)
            for (b in balls) if (b.state == PLAY) moveBall(b, h)
            collideBalls()
        }
        for (b in balls) if (b.state == PLAY) afterStep(b, dt)
    }

    private fun stepTimers(dt: Float) {
        for (i in 0 until 3) {
            bumperFlash[i] = (bumperFlash[i] - dt * 5f).coerceAtLeast(0f)
            bumperCool[i] -= dt
            dropFlash[i] = (dropFlash[i] - dt * 3f).coerceAtLeast(0f)
            laneFlash[i] = (laneFlash[i] - dt * 2f).coerceAtLeast(0f)
            dropSink[i] = approach(dropSink[i], if (dropUp[i]) 0f else 1f, dt * 8f)
        }
        for (i in 0..1) {
            slingFlash[i] = (slingFlash[i] - dt * 6f).coerceAtLeast(0f)
            slingCool[i] -= dt
        }
        jackpotFlash = (jackpotFlash - dt * 1.5f).coerceAtLeast(0f)
        messageT -= dt
        if (ballSaveT > 0f) ballSaveT -= dt
        tiltMeter = (tiltMeter - PinballTuning.TILT_DECAY * dt).coerceAtLeast(0f)
        // The plunger rod eases after the finger; it springs forward faster than it's pulled.
        plungerVis = approach(plungerVis, pull, dt * if (pull > plungerVis) 6f else 20f)
        // The spinner winds down; every whole turn scores.
        spinAngle += spinSpeed * dt
        spinSpeed *= exp(-1.6f * dt)
        if (spinSpeed < 0.3f) spinSpeed = 0f
        while (spinAngle - spinScored >= TAU) {
            spinScored += TAU
            award(PinballTuning.SPINNER_POINTS, 20f, T.SPINNER_Y, Pal.CYAN, popup = false)
        }
        if (bankResetT > 0f) {
            bankResetT -= dt
            if (bankResetT <= 0f) {
                if (bankClear()) {
                    dropUp.fill(true)
                    play(Sfx.CLINK, 0.6f, 0.8f)
                } else {
                    bankResetT = 0.2f
                }
            }
        }
    }

    /** Enough sub-steps that neither a ball nor a swinging flipper moves more than a fraction of a ball radius. */
    private fun substeps(dt: Float): Int {
        var fast = 0f
        for (b in balls) {
            if (b.state != PLAY) continue
            val s = sqrt(b.vx * b.vx + b.vy * b.vy)
            if (s > fast) fast = s
        }
        var tip = 0f
        for (s in 0..1) {
            val target = if (flipperHeld(s)) T.upAngle(s) else T.restAngle(s)
            if (flipAng[s] != target) tip = PinballTuning.FLIP_UP_SPEED * (T.FLIP_LEN + T.FLIP_R1)
        }
        val move = (fast + tip) * dt
        if (!move.isFinite()) return 24
        return ceil(move / (T.BALL_R * PinballTuning.SUBSTEP_FRACTION)).toInt().coerceIn(1, 24)
    }

    private fun flipperHeld(side: Int): Boolean = flipId[side] >= 0L && !tilted && !timeUp

    private fun moveFlippers(h: Float) {
        for (s in 0..1) {
            val held = flipperHeld(s)
            val target = if (held) T.upAngle(s) else T.restAngle(s)
            val speed = if (held) PinballTuning.FLIP_UP_SPEED else PinballTuning.FLIP_DOWN_SPEED
            val a = flipAng[s]
            val na = approach(a, target, speed * h)
            flipOmega[s] = (na - a) / h
            flipAng[s] = na
        }
    }

    private fun moveBall(b: Ball, h: Float) {
        b.vy += PinballTuning.GRAVITY * h
        val k = 1f - PinballTuning.DRAG * h
        b.vx *= k
        b.vy *= k
        clampSpeed(b)
        b.x += b.vx * h
        b.y += b.vy * h
        collide(b)
    }

    private fun clampSpeed(b: Ball) {
        val s2 = b.vx * b.vx + b.vy * b.vy
        val max = PinballTuning.MAX_SPEED
        if (s2 > max * max) {
            val k = max / sqrt(s2)
            b.vx *= k
            b.vy *= k
        }
    }

    private fun collide(b: Ball) {
        val r = T.BALL_R
        // Walls, guides and the slingshots.
        for (i in 0 until T.segCount) {
            val kind = T.kind[i]
            if (kind == T.GATE) {
                // One-way: only a ball above the gate and coming down onto it bounces.
                val s = (b.x - T.ax[i]) * T.gateNX + (b.y - T.ay[i]) * T.gateNY
                if (s < 0f || b.vx * T.gateNX + b.vy * T.gateNY > 0f) continue
            }
            val hit = segment(b, T.ax[i], T.ay[i], T.bx[i], T.by[i], r, T.bounce[i])
            if (hit > PinballTuning.SLING_MIN && kind == T.SLING) sling(b, T.tag[i])
        }
        for (i in 0 until T.postCount) contact(b, T.px[i], T.py[i], T.pr[i] + r, PinballTuning.POST_BOUNCE, 0f, 0f)
        for (i in 0 until 3) {
            val hit = contact(b, T.BUMPER_X[i], T.BUMPER_Y[i], T.BUMPER_R + r, PinballTuning.BUMPER_BOUNCE, 0f, 0f)
            if (hit >= 0f) bumper(b, i)
        }
        for (i in 0 until 3) {
            if (!dropUp[i]) continue
            val y0 = T.DROP_Y0[i]
            val hit = segment(b, T.DROP_X, y0, T.DROP_X, y0 + T.DROP_LEN, r + 2f, 0.3f)
            if (hit > PinballTuning.DROP_MIN) dropTarget(i)
        }
        for (s in 0..1) flipper(b, s)
    }

    /**
     * Keeps [b] at least [reach] from the point ([px], [py]) moving at ([svx], [svy]) and
     * bounces it with restitution [e]. Returns the approach speed, 0 for a resting touch, or
     * -1 when not touching. The contact normal is left in [hitNX], [hitNY].
     */
    private fun contact(b: Ball, px: Float, py: Float, reach: Float, e: Float, svx: Float, svy: Float): Float {
        val dx = b.x - px
        val dy = b.y - py
        val d2 = dx * dx + dy * dy
        if (d2 >= reach * reach) return -1f
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d < 1e-4f) {
            nx = 0f; ny = -1f
        } else {
            nx = dx / d; ny = dy / d
        }
        b.x += nx * (reach - d)
        b.y += ny * (reach - d)
        hitNX = nx
        hitNY = ny
        val vn = (b.vx - svx) * nx + (b.vy - svy) * ny
        if (vn >= 0f) return 0f
        val ee = if (-vn < PinballTuning.REST_SPEED) 0f else e
        b.vx -= (1f + ee) * vn * nx
        b.vy -= (1f + ee) * vn * ny
        return -vn
    }

    /** [contact] against the nearest point of a static segment. */
    private fun segment(b: Ball, ax: Float, ay: Float, bx: Float, by: Float, reach: Float, e: Float): Float {
        val ex = bx - ax
        val ey = by - ay
        val l2 = ex * ex + ey * ey
        var t = if (l2 > 0f) ((b.x - ax) * ex + (b.y - ay) * ey) / l2 else 0f
        t = t.coerceIn(0f, 1f)
        return contact(b, ax + ex * t, ay + ey * t, reach, e, 0f, 0f)
    }

    /** The flipper is a tapered capsule turning about its pivot; the ball takes its surface speed. */
    private fun flipper(b: Ball, side: Int) {
        val pvx = T.flipX(side)
        val pvy = T.FLIP_Y
        val a = flipAng[side]
        val ex = cos(a) * T.FLIP_LEN
        val ey = sin(a) * T.FLIP_LEN
        var t = ((b.x - pvx) * ex + (b.y - pvy) * ey) / (T.FLIP_LEN * T.FLIP_LEN)
        t = t.coerceIn(0f, 1f)
        val cx = pvx + ex * t
        val cy = pvy + ey * t
        val reach = T.FLIP_R0 + (T.FLIP_R1 - T.FLIP_R0) * t + T.BALL_R
        val w = flipOmega[side]
        val hit = contact(b, cx, cy, reach, PinballTuning.FLIP_BOUNCE, -w * (cy - pvy), w * (cx - pvx))
        if (hit >= 0f && flipperHeld(side) && w == 0f) b.cradled = true
    }

    private fun collideBalls() {
        for (i in 0 until MAX_BALLS) {
            val a = balls[i]
            if (a.state != PLAY) continue
            for (j in i + 1 until MAX_BALLS) {
                val c = balls[j]
                if (c.state != PLAY) continue
                val dx = c.x - a.x
                val dy = c.y - a.y
                val d2 = dx * dx + dy * dy
                val rs = T.BALL_R * 2f
                if (d2 >= rs * rs || d2 < 1e-6f) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val push = (rs - d) / 2f
                a.x -= nx * push; a.y -= ny * push
                c.x += nx * push; c.y += ny * push
                val vn = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
                if (vn < 0f) {
                    val j = -0.95f * vn
                    a.vx -= j * nx; a.vy -= j * ny
                    c.vx += j * nx; c.vy += j * ny
                }
            }
        }
    }

    /** Switches, the drain and the failsafes, once per step. */
    private fun afterStep(b: Ball, dt: Float) {
        if (!b.x.isFinite() || !b.y.isFinite() || !b.vx.isFinite() || !b.vy.isFinite() ||
            b.x < -30f || b.x > T.W + 30f || b.y < -40f
        ) {
            retire(b)
            return
        }
        if (b.y > T.DRAIN_Y) {
            if (b.x > T.PLAY_W) retire(b) else drain(b)
            return
        }
        // A plunge too weak to clear the gate rolls back onto the plunger.
        if (b.x > T.PLAY_W && b.y >= T.LANE_REST_Y - 0.5f && b.vy >= 0f) {
            b.state = LANE
            b.x = T.LANE_X
            b.y = T.LANE_REST_Y
            b.vx = 0f
            b.vy = 0f
            b.autoT = if (laneBall() >= 0 || liveBalls() > 1) AUTO_DELAY else -1f
            return
        }
        // Top rollovers.
        val ry = T.ROLLOVER_Y
        if ((b.py < ry) != (b.y < ry)) {
            for (i in 0 until 3) if (abs(b.x - T.ROLLOVER_X[i]) < T.ROLLOVER_HALF) rollover(i)
        }
        // The spinner across the left orbit; going up it is an orbit shot (a jackpot in multiball).
        val sy = T.SPINNER_Y
        if ((b.py < sy) != (b.y < sy) && b.x < T.ORBIT_X) {
            spinSpeed = maxOf(spinSpeed, abs(b.vy) * 0.05f)
            play(Sfx.SPINNER, 0.8f)
            if (b.vy < 0f) orbit()
        }
        // Stuck-ball failsafe: a ball crawling for too long (not held on a flipper) gets kicked,
        // and one that stays stuck is served again.
        val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
        if (b.cradled) {
            b.slowT = 0f
        } else if (speed < PinballTuning.STUCK_SPEED) {
            b.slowT += dt
            if (b.slowT > PinballTuning.STUCK_SECONDS) {
                b.slowT = 0f
                // Stalling somewhere new starts the count again; the same spot keeps counting,
                // however fast the last kick sent it (a kicked ball always moves fast for a moment).
                val kx = b.x - b.kickX
                val ky = b.y - b.kickY
                val r = PinballTuning.STUCK_RADIUS
                if (kx * kx + ky * ky > r * r) b.searchKicks = 0
                b.searchKicks++
                b.kickX = b.x
                b.kickY = b.y
                b.freeT = 0f
                if (b.searchKicks > PinballTuning.STUCK_KICKS) {
                    retire(b)
                } else {
                    searchKicksTotal++
                    // Mostly up the table; the sideways part alternates and varies so a kick that
                    // bounced straight back into the pocket isn't repeated.
                    val out = if (b.x < T.CX) 1f else -1f
                    val side = if (b.searchKicks % 2 == 1) out else -out
                    b.vy = -PinballTuning.STUCK_KICK
                    b.vx = side * (40f + rng.nextFloat() * 120f)
                    play(Sfx.THUD, 0.6f, 1.2f)
                    show(M_SEARCH)
                }
            }
        } else {
            b.slowT = 0f
            if (b.searchKicks > 0) {
                b.freeT += dt
                if (b.freeT > PinballTuning.STUCK_FREE_SECONDS) b.searchKicks = 0
            }
        }
    }

    // ---------------------------------------------------------------- rules and scoring

    private val pt = FloatArray(3)

    private fun label(points: Int): String {
        if (points !in labels.indices) return "+$points"
        return labels[points] ?: "+$points".also { labels[points] = it }
    }

    /** Scores [base] times the multiplier (nothing when tilted or after time-up). */
    private fun award(base: Int, tx: Float, ty: Float, color: Int, popup: Boolean): Boolean {
        if (timeUp || tilted) return false
        val pts = base * multiplier
        score += pts
        if (popup && stage.toField(tx, 18f, ty, pt)) popups.add(label(pts), pt[0], pt[1] - 10f, Color(color), size = 2.4f)
        return true
    }

    private fun burstAt(tx: Float, ty: Float, n: Int, colors: IntArray, speed: Float) {
        if (stage.toField(tx, 8f, ty, pt)) particles.burst(pt[0], pt[1], n, speed * 0.3f, speed, colors, 0.45f, 3f)
    }

    private fun show(m: Int) {
        message = m
        messageT = MSG_SECONDS
    }

    private fun bumper(b: Ball, i: Int) {
        if (tilted) return
        // A pop bumper throws the ball away from its centre, however gently it touched.
        val vn = b.vx * hitNX + b.vy * hitNY
        if (vn < PinballTuning.BUMPER_KICK) {
            b.vx += hitNX * (PinballTuning.BUMPER_KICK - vn)
            b.vy += hitNY * (PinballTuning.BUMPER_KICK - vn)
        }
        if (bumperCool[i] > 0f) return
        bumperCool[i] = 0.06f
        bumperFlash[i] = 1f
        bumperHits++
        award(PinballTuning.BUMPER_POINTS, T.BUMPER_X[i], T.BUMPER_Y[i], BUMPER_COLORS[i], popup = multiplier > 1 || multiball)
        play(Sfx.BUMPER, 0.8f, 0.9f + i * 0.08f)
        fx.haptics.tick()
        shake.add(0.07f)
        burstAt(T.BUMPER_X[i], T.BUMPER_Y[i], 10, if (i == 1) HIT_CYAN else HIT_PINK, 170f)
    }

    private fun sling(b: Ball, i: Int) {
        if (tilted || slingCool[i] > 0f) return
        b.vx += hitNX * PinballTuning.SLING_KICK
        b.vy += hitNY * PinballTuning.SLING_KICK
        slingCool[i] = 0.12f
        slingFlash[i] = 1f
        val sx = if (i == 0) (T.SLING_AX + T.SLING_CX) / 2f else T.PLAY_W - (T.SLING_AX + T.SLING_CX) / 2f
        val sy = (T.SLING_AY + T.SLING_CY) / 2f
        award(PinballTuning.SLING_POINTS, sx, sy, Pal.LIME, popup = false)
        play(Sfx.SLINGSHOT, 0.8f, 0.95f + i * 0.1f)
        fx.haptics.tick()
        shake.add(0.05f)
        burstAt(sx, sy, 8, HIT_LIME, 140f)
    }

    private fun dropTarget(i: Int) {
        if (tilted || timeUp) return
        dropUp[i] = false
        dropFlash[i] = 1f
        val ty = T.DROP_Y0[i] + T.DROP_LEN / 2f
        award(PinballTuning.DROP_POINTS, T.DROP_X, ty, Pal.ORANGE, popup = true)
        play(Sfx.THUD, 0.7f, 1.4f)
        fx.haptics.hit()
        burstAt(T.DROP_X, ty, 10, HIT_GOLD, 150f)
        if (!dropUp[0] && !dropUp[1] && !dropUp[2]) {
            if (multiplier < PinballTuning.MAX_MULT) multiplier++
            award(PinballTuning.BANK_POINTS, T.DROP_X - 40f, ty, Pal.GOLD, popup = true)
            show(M_BANK)
            play(Sfx.WIN, 0.7f)
            fx.haptics.win()
            flash.trigger(0.3f)
            bankResetT = 1.2f
        }
    }

    /** True when no ball is close enough to the bank for the targets to pop up into it. */
    private fun bankClear(): Boolean {
        for (b in balls) {
            if (b.state != PLAY) continue
            if (b.x > T.DROP_X - T.BALL_R - 8f && b.y > T.DROP_Y0[0] - T.BALL_R && b.y < T.DROP_Y0[2] + T.DROP_LEN + T.BALL_R) return false
        }
        return true
    }

    private fun rollover(i: Int) {
        if (tilted || timeUp) return
        laneFlash[i] = 1f
        if (laneLit[i]) {
            award(PinballTuning.LANE_REPEAT_POINTS, T.ROLLOVER_X[i], T.ROLLOVER_Y, Pal.SKY, popup = false)
            play(Sfx.BLIP, 0.4f, 0.9f)
            return
        }
        laneLit[i] = true
        award(PinballTuning.LANE_POINTS, T.ROLLOVER_X[i], T.ROLLOVER_Y, Pal.SKY, popup = true)
        play(Sfx.BLIP, 0.7f, 1.2f + i * 0.15f)
        if (laneLit[0] && laneLit[1] && laneLit[2]) {
            laneLit.fill(false)
            laneFlash.fill(1f)
            award(PinballTuning.MULTIBALL_POINTS, T.CX, T.ROLLOVER_Y + 20f, Pal.CYAN, popup = true)
            if (!multiball && liveBalls() < MAX_BALLS) startMultiball() else show(M_LANES)
        }
    }

    private fun orbit() {
        if (tilted || timeUp) return
        if (multiball) {
            jackpots++
            jackpotFlash = 1f
            award(PinballTuning.JACKPOT_POINTS, 60f, 260f, Pal.GOLD, popup = true)
            show(M_JACKPOT)
            play(Sfx.JACKPOT)
            fx.haptics.jackpot()
            shake.add(0.4f)
            flash.trigger(0.5f)
            burstAt(20f, T.SPINNER_Y, 30, HIT_GOLD, 260f)
        } else {
            award(PinballTuning.ORBIT_POINTS, 40f, T.SPINNER_Y, Pal.CYAN, popup = true)
            show(M_ORBIT)
        }
    }

    // ---------------------------------------------------------------- 3D presentation

    /** The player's view: standing at the lockdown bar, looking down the table at the backbox. */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(150f, 700f, 860f, 150f, 0f, 280f, fovDeg = 44f)
    }

    private val upperLight = PointLight(T.CX, 150f, 150f, 1f, 0.85f, 0.95f, 420f, 0.9f)
    private val lowerLight = PointLight(T.CX, 140f, 470f, 0.85f, 0.9f, 1f, 380f, 0.8f)
    private val hitLight = PointLight(T.CX, 40f, 200f, 1f, 1f, 1f, 170f, 0f)
    private val xf = Xform()

    /** Where the backbox's score display sits on the field (computed once from the camera). */
    private val dmd = FloatArray(4).also {
        stage.toField(40f, PinballArt.DMD_TOP, PinballArt.BACKBOX_Z, pt)
        it[0] = pt[0]; it[1] = pt[1]
        stage.toField(260f, PinballArt.DMD_BOTTOM, PinballArt.BACKBOX_Z, pt)
        it[2] = pt[0]; it[3] = pt[1]
    }
    private var scoreShown = -1
    private var scoreText = "0"

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        light(r)
        r.gradient(0xFF05030C.toInt(), Pal.NIGHT)
        PinballArt.table.draw(r)
        drawBumpers(r)
        drawSlings(r)
        drawTargets(r)
        drawSpinner(r)
        drawFlippers(r)
        drawPlunger(r)
        drawLamps(r)
        for (b in balls) if (b.state != OFF) drawBall(r, b)
        // The playfield glass: a faint sheen between the player and the table.
        r.quad(
            -4f, 30f, -6f, T.W + 4f, 30f, -6f, T.W + 4f, 30f, 650f, -4f, 30f, 650f,
            PinballArt.glassStreaks.full, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, alpha = 0.16f, cull = false,
        )
        stage.present()
        drawDmd(scope)
        drawHints(scope)
    }

    private fun light(r: Renderer3D) {
        val l = r.lighting
        l.ambR = 0.5f; l.ambG = 0.46f; l.ambB = 0.62f
        l.setDirection(-0.2f, 1f, 0.5f)
        l.dirR = 0.45f; l.dirG = 0.42f; l.dirB = 0.4f
        l.points.clear()
        l.points += upperLight
        l.points += lowerLight
        var hot = -1
        for (i in 0 until 3) if (bumperFlash[i] > 0f && (hot < 0 || bumperFlash[i] > bumperFlash[hot])) hot = i
        if (hot >= 0) {
            val c = BUMPER_COLORS[hot]
            hitLight.x = T.BUMPER_X[hot]; hitLight.z = T.BUMPER_Y[hot]
            hitLight.r = (c shr 16 and 255) / 255f
            hitLight.g = (c shr 8 and 255) / 255f
            hitLight.b = (c and 255) / 255f
            hitLight.intensity = bumperFlash[hot] * 1.4f
            l.points += hitLight
        }
    }

    private fun drawBumpers(r: Renderer3D) {
        val glow = TexKit.glow.full
        for (i in 0 until 3) {
            val x = T.BUMPER_X[i]
            val z = T.BUMPER_Y[i]
            val f = bumperFlash[i]
            val pop = 1f - f * 0.25f
            xf.set(x, 0f, z)
            PinballArt.bumperBase.draw(r, xf = xf)
            xf.set(x, 0f, z).stretch(1f, pop, 1f)
            PinballArt.bumperCap.draw(r, xf = xf, tint = BUMPER_COLORS[i], emissiveBoost = 1.35f + 0.15f * sin(time * 4f + i * 2f) + f * 1.5f)
            val a = 0.18f + 0.1f * sin(time * 3f + i) + f * 0.7f
            r.flat(x, z, 1f, 70f, 70f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = BUMPER_COLORS[i])
        }
    }

    private fun drawSlings(r: Renderer3D) {
        val glow = TexKit.glow.full
        for (i in 0..1) {
            val f = slingFlash[i]
            val sx = if (i == 0) (T.SLING_AX + T.SLING_CX) / 2f else T.PLAY_W - (T.SLING_AX + T.SLING_CX) / 2f
            val sy = (T.SLING_AY + T.SLING_CY) / 2f
            r.flat(sx, sy, 12.5f, 40f, 70f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.25f + f * 0.75f, tint = Pal.LIME)
        }
    }

    private fun drawTargets(r: Renderer3D) {
        val glow = TexKit.glow.full
        for (i in 0 until 3) {
            val y0 = T.DROP_Y0[i]
            val sink = dropSink[i]
            if (sink < 1f) {
                xf.set(T.DROP_X, -sink * 15f, y0 + T.DROP_LEN / 2f)
                PinballArt.dropTarget.draw(r, xf = xf, tint = DROP_COLORS[i])
            }
            // The lamp in front of each target: lit while it's still standing.
            val lit = dropUp[i]
            val a = if (lit) 0.45f + 0.2f * sin(time * 6f + i) else 0.08f
            r.flat(T.DROP_X - 22f, y0 + T.DROP_LEN / 2f, 0.6f, 18f + dropFlash[i] * 30f, 18f + dropFlash[i] * 30f, glow, blend = Blend.ADD, emissive = 1f, alpha = a + dropFlash[i], tint = DROP_COLORS[i])
        }
    }

    private fun drawSpinner(r: Renderer3D) {
        // A flat plate on a wire across the orbit, turning about the wire.
        val c = cos(spinAngle)
        val s = sin(spinAngle)
        val h = 7f
        val y = 11f
        val tex = PinballArt.spinnerPlate.full
        r.quad(
            1f, y + c * h, T.SPINNER_Y + s * h, T.ORBIT_X - 1f, y + c * h, T.SPINNER_Y + s * h,
            T.ORBIT_X - 1f, y - c * h, T.SPINNER_Y - s * h, 1f, y - c * h, T.SPINNER_Y - s * h,
            tex, 0f, -s, c, cull = false, gloss = 0.8f,
        )
        r.beam(0f, y, T.SPINNER_Y, T.ORBIT_X, y, T.SPINNER_Y, 1.4f, TexKit.white.full, tint = Pal.LIGHTGRAY)
    }

    private fun drawFlippers(r: Renderer3D) {
        for (s in 0..1) {
            xf.set(T.flipX(s), 0f, T.FLIP_Y, yaw = -flipAng[s])
            PinballArt.flipper.draw(r, xf = xf)
        }
    }

    private fun drawPlunger(r: Renderer3D) {
        val back = plungerVis * 34f
        xf.set(T.LANE_X, 7f, T.LANE_REST_Y + T.BALL_R + back)
        PinballArt.plunger.draw(r, xf = xf)
    }

    private fun drawLamps(r: Renderer3D) {
        val glow = TexKit.glow.full
        val blink = (time * 4f).toInt() % 2 == 0
        // Top lane inserts.
        for (i in 0 until 3) {
            val lit = laneLit[i]
            val a = (if (lit) 0.75f else 0.06f) + laneFlash[i] * 0.8f
            r.flat(T.ROLLOVER_X[i], T.ROLLOVER_Y + 34f, 0.6f, 30f, 30f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.SKY)
        }
        // Multiplier row.
        for (k in 0 until 4) {
            val lit = multiplier >= k + 2
            val x = T.CX - 54f + k * 36f
            r.flat(x, 372f, 0.6f, 34f, 34f, glow, blend = Blend.ADD, emissive = 1f, alpha = if (lit) 0.85f else 0.05f, tint = Pal.GOLD)
        }
        // Orbit arrow: jackpot when blinking gold.
        val orbitLit = multiball && blink
        r.flat(22f, 350f, 0.6f, 40f, 40f, glow, blend = Blend.ADD, emissive = 1f, alpha = if (orbitLit) 0.9f else 0.12f + jackpotFlash, tint = if (multiball) Pal.GOLD else Pal.CYAN)
        // Shoot again (ball save).
        val saveOn = ballSaveT > 0f && (ballSaveT > 2f || blink)
        r.flat(T.CX, 568f, 0.6f, 34f, 34f, glow, blend = Blend.ADD, emissive = 1f, alpha = if (saveOn) 0.9f else 0.06f, tint = Pal.RED)
    }

    private fun drawBall(r: Renderer3D, b: Ball) {
        // A draining ball drops under the apron.
        val sink = if (b.y > T.APRON_Y) (b.y - T.APRON_Y) * 0.6f else 0f
        val y = T.BALL_R - sink
        if (sink < 20f) r.flat(b.x + 3f, b.y + 4f, 0.4f, 24f, 24f, TexKit.shadow.full, blend = Blend.ALPHA, alpha = 0.55f)
        xf.set(b.x, y, b.y)
        PinballArt.ball.draw(r, xf = xf)
        r.sprite(b.x - 2.5f, y + 4f, b.y - 1f, 7f, 7f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.9f)
    }

    private fun drawDmd(scope: DrawScope) {
        val x0 = dmd[0]
        val y0 = dmd[1]
        val x1 = dmd[2]
        val y1 = dmd[3]
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0f || h <= 0f) return
        val amber = Color(0xFFFF8A1C.toInt())
        scope.drawRect(Color(0xFF120600.toInt()), Offset(x0, y0), Size(w, h))
        if (score != scoreShown) {
            scoreShown = score
            scoreText = score.toString()
        }
        val cx = (x0 + x1) / 2f
        if (messageT > 0f && message != M_NONE) {
            val on = message != M_TILT || (time * 5f).toInt() % 2 == 0
            if (on) ArcadeFont.drawCentered(scope, MESSAGES[message], cx, y0 + h * 0.25f, h / 16f, amber, shadow = false)
            if (message == M_BALL) ArcadeFont.drawCentered(scope, BALL_TEXT[ballNumber.coerceIn(1, 5)], cx, y0 + h * 0.62f, h / 26f, amber, shadow = false)
        } else {
            ArcadeFont.drawCentered(scope, scoreText, cx, y0 + h * 0.18f, h / 13f, amber, shadow = false)
            ArcadeFont.draw(scope, BALL_TEXT[ballNumber.coerceIn(1, 5)], x0 + 6f, y0 + h * 0.72f, h / 30f, amber)
            ArcadeFont.draw(scope, MULT_TEXT[multiplier], x1 - 22f, y0 + h * 0.72f, h / 30f, amber)
        }
        // Dot-matrix grille.
        var gy = y0 + 1.5f
        while (gy < y1) {
            scope.drawRect(Color.Black, Offset(x0, gy), Size(w, 0.7f), alpha = 0.45f)
            gy += 2.2f
        }
    }

    private fun drawHints(scope: DrawScope) {
        if (timeUp) return
        val a = 0.5f + 0.5f * sin(time * 6f)
        if (laneBall() >= 0 && plungerId < 0L && stage.toField(T.LANE_X, 0f, 600f, pt)) {
            ArcadeFont.drawCentered(scope, PULL_TEXT, pt[0] - 6f, pt[1] - 64f, 1.8f, Color.White, a)
        }
        if (time < 6f) {
            ArcadeFont.drawCentered(scope, HOLD_LEFT, 128f, 562f, 1.8f, Color(Pal.CYAN), a)
            ArcadeFont.drawCentered(scope, HOLD_RIGHT, 232f, 562f, 1.8f, Color(Pal.CYAN), a)
        }
        if (tilted) ArcadeFont.drawCentered(scope, "TILT", GAME_W / 2f, 300f, 6f, Color(Pal.RED), if ((time * 5f).toInt() % 2 == 0) 1f else 0.3f)
    }

    // ---------------------------------------------------------------- simulation-test hooks

    internal val botMaxBalls: Int get() = MAX_BALLS
    internal fun botBallInPlay(i: Int): Boolean = balls[i].state == PLAY
    internal fun botBallInLane(i: Int): Boolean = balls[i].state == LANE
    internal fun botBallX(i: Int): Float = balls[i].x
    internal fun botBallY(i: Int): Float = balls[i].y
    internal fun botBallVX(i: Int): Float = balls[i].vx
    internal fun botBallVY(i: Int): Float = balls[i].vy
    /** A ball waits on the plunger for the player. */
    internal val botLaneReady: Boolean get() = laneBall() >= 0
    internal fun botLaneBallIndex(): Int = laneBall()
    internal val botLiveBalls: Int get() = liveBalls()
    internal fun botFlipperHeld(side: Int): Boolean = flipperHeld(side)
    internal fun botFlipperAngle(side: Int): Float = flipAng[side]
    internal val botPull: Float get() = pull
    internal val botPlungerHeld: Boolean get() = plungerId >= 0L
    internal val botLastLaunchSpeed: Float get() = lastLaunchSpeed
    internal val botLaunches: Int get() = launches
    internal val botBallsLeft: Int get() = ballsLeft
    internal val botDrains: Int get() = drains
    internal val botFailsafeTrips: Int get() = failsafeTrips
    /** Kicks the stuck-ball failsafe gave a crawling ball this round. */
    internal val botSearchKicks: Int get() = searchKicksTotal
    internal val botBumperHits: Int get() = bumperHits
    internal val botMultiballs: Int get() = multiballs
    internal val botJackpots: Int get() = jackpots
    internal val botMultiplier: Int get() = multiplier
    internal val botTilted: Boolean get() = tilted
    internal val botBallSaveLeft: Float get() = ballSaveT
    internal val botDropsUp: Int get() = (if (dropUp[0]) 1 else 0) + (if (dropUp[1]) 1 else 0) + (if (dropUp[2]) 1 else 0)
    internal fun botLaneLit(i: Int): Boolean = laneLit[i]
    internal val botEndedEarly: Boolean get() = endedEarly

    /** Puts ball [i] in play at ([x], [y]) moving at ([vx], [vy]), bypassing the plunger. */
    internal fun botPlace(i: Int, x: Float, y: Float, vx: Float, vy: Float) {
        val b = balls[i]
        b.state = PLAY
        b.x = x; b.y = y; b.vx = vx; b.vy = vy
        b.px = x; b.py = y
        b.autoT = -1f
        b.slowT = 0f
        b.searchKicks = 0
    }

    /** Holds ball [i] still at ([x], [y]) without touching the failsafe's counters (a trap no kick frees). */
    internal fun botHold(i: Int, x: Float, y: Float) {
        val b = balls[i]
        b.x = x; b.y = y; b.vx = 0f; b.vy = 0f
        b.px = x; b.py = y
    }

    /** Takes ball [i] off the table without any rule firing (test setup). */
    internal fun botRemove(i: Int) {
        balls[i].state = OFF
    }

    /** Stops the ball save so a drain in a test counts. */
    internal fun botEndBallSave() {
        ballSaveT = 0f
        ballSaveArmed = false
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        // The backglass's dot-matrix display: the title over a ball weaving among flashing
        // bumpers, then a flashing "multiball" as the loop comes round.
        p.fill(0, 0, w, h, Color(0xFF0A0402.toInt()))
        val amber = Color(0xFFFF8A1C.toInt())
        val dim = Color(0xFF4A2206.toInt())
        val u = h / 30f
        val phase = time % 8f
        if (phase < 5.5f) {
            p.textCentered("STAR", w / 2f, 1.5f * u, amber, size = 0.85f * u)
            p.textCentered("FLIPPER", w / 2f, 8.5f * u, amber, size = 0.7f * u)
            val lit = (time * 3f).toInt() % 3
            val by = 19f * u
            p.disc(w * 0.3f, by, 2.2f * u, if (lit == 0) amber else dim)
            p.disc(w * 0.7f, by, 2.2f * u, if (lit == 1) amber else dim)
            p.disc(w * 0.5f, by + 4.5f * u, 2.2f * u, if (lit == 2) amber else dim)
            val a = time * 2.6f
            p.disc(w / 2f + sin(a) * w * 0.42f, 21f * u + cos(a * 1.7f) * 5f * u, 1f * u, Color.White)
        } else {
            val on = (time * 4f).toInt() % 2 == 0
            p.textCentered("MULTI", w / 2f, 3f * u, if (on) amber else dim, size = 1.1f * u)
            p.textCentered("BALL!", w / 2f, 13f * u, if (on) dim else amber, size = 1.1f * u)
        }
        // Chase lamps along the bottom edge.
        val n = 10
        for (k in 0 until n) {
            val on = ((time * 8f).toInt() + k) % 4 == 0
            p.disc(w * (k + 0.5f) / n, h - 1.4f * u, 0.7f * u, if (on) amber else dim)
        }
    }
}
