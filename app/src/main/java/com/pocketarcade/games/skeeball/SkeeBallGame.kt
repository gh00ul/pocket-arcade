package com.pocketarcade.games.skeeball

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketarcade.engine.FlickTracker
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.Vec2
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.easeOutCubic
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.len
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Difficulty and payout knobs for skee-ball. */
object SkeeTuning {
    const val ROUND_SECONDS = 40f
    /** Flick speed (field units/s) → ball speed. */
    const val FLICK_TO_SPEED = 0.33f
    const val MIN_SPEED = 120f
    const val MAX_SPEED = 760f
    /** Upward flick speed needed to count as a roll. */
    const val MIN_FLICK = 320f
    /** Largest roll angle away from straight up, in degrees. */
    const val MAX_ANGLE_DEG = 32f
    const val ROLL_FRICTION = 60f
    /** Vertical launch speed off the ramp as a fraction of planar speed. */
    const val LAUNCH_RATIO = 0.5f
    const val FLIGHT_GRAVITY = 1000f
    const val RELOAD_SECONDS = 0.35f

    val RING_POINTS = intArrayOf(100, 50, 40, 30, 20, 10)
    /** Ring outer x-radii matching [RING_POINTS]; rings are ellipses squashed to 78% height. */
    val RING_RADII = floatArrayOf(13f, 30f, 48f, 68f, 90f, 118f)
    const val BONUS_POINTS = 200

    const val POINTS_PER_TICKET = 50
    const val BASE_TICKETS = 1
}

class SkeeBallGame : BaseMiniGame() {
    override val id = "skeeball"
    override val title = "SKEE-BALL"
    override val marquee = "SKEE"
    override val instructions = listOf(
        "DRAG THE BALL TO AIM",
        "FLICK UP TO ROLL",
        "FASTER FLICK = LONGER JUMP",
        "HIT THE CENTER FOR 100",
        "CORNER HOLE = 200 BONUS!",
    )
    override val look = CabinetLook(body = Pal.BLUE, trim = Pal.YELLOW, glow = Pal.SKY, shape = CabinetShape.LANE)
    override val roundSeconds = SkeeTuning.ROUND_SECONDS

    private companion object {
        const val LANE_L = 60f
        const val LANE_R = 300f
        const val RAMP_Y = 318f
        const val RAMP_TOP = 290f
        const val BOARD_TOP = 30f
        const val BOARD_BOTTOM = 252f
        const val CX = 180f
        const val CY = 146f
        const val SQUASH = 0.78f
        const val BONUS_X = 318f
        const val BONUS_Y = 60f
        const val BONUS_R = 13f
        const val BALL_R = 14f
        const val REST_X = 180f
        const val REST_Y = 586f
        const val GRAB_TOP = 430f
    }

    private enum class Phase { ROLLING, FLYING, SETTLING, GUTTER }

    private class Ball {
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var spin = 0f
        var phase = Phase.ROLLING
        var t = 0f
        var fromX = 0f
        var fromY = 0f
        var toX = 0f
        var toY = 0f
        var points = 0
        var ring = -1
        var active = false
    }

    private val balls = Array(6) { Ball() }
    private var readyX = REST_X
    private var readyY = REST_Y
    private var hasReady = true
    private var reloadT = 0f
    private var dragging = -1L
    private val flick = FlickTracker()
    private val tmp = Vec2()
    private val ringFlash = FloatArray(7)
    private var lastSpeed = 0f
    private var speedShowT = 0f
    private val readySquash = Spring()

    override fun reset() {
        balls.forEach { it.active = false }
        readyX = REST_X
        readyY = REST_Y
        hasReady = true
        reloadT = 0f
        dragging = -1L
        ringFlash.fill(0f)
        speedShowT = 0f
        readySquash.snap(1f)
    }

    override fun ticketsFor(score: Int): Int = SkeeTuning.BASE_TICKETS + score / SkeeTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = balls.none { it.active }

    override fun onTimeUp() {
        dragging = -1L
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> {
                if (dragging < 0 && hasReady && !timeUp && y > GRAB_TOP) {
                    dragging = id
                    flick.reset(x, y, timeMs)
                    readySquash.kick(-3f)
                    play(Sfx.BLIP, 0.4f, 0.8f)
                }
            }
            TouchType.MOVE -> {
                if (id == dragging) {
                    flick.add(x, y, timeMs)
                    readyX = x.coerceIn(LANE_L + BALL_R, LANE_R - BALL_R)
                    readyY = y.coerceIn(GRAB_TOP + 60f, GAME_H - 30f)
                }
            }
            TouchType.UP -> {
                if (id == dragging) {
                    flick.add(x, y, timeMs)
                    dragging = -1L
                    tryRoll()
                }
            }
        }
    }

    private fun tryRoll() {
        flick.velocity(tmp)
        val up = -tmp.y
        if (up < SkeeTuning.MIN_FLICK || timeUp) {
            return
        }
        val speed = (len(tmp.x, tmp.y) * SkeeTuning.FLICK_TO_SPEED).coerceIn(SkeeTuning.MIN_SPEED, SkeeTuning.MAX_SPEED)
        val maxA = SkeeTuning.MAX_ANGLE_DEG * (Math.PI.toFloat() / 180f)
        val angle = atan2(tmp.x, up).coerceIn(-maxA, maxA)
        val b = balls.firstOrNull { !it.active } ?: return
        b.active = true
        b.phase = Phase.ROLLING
        b.x = readyX
        b.y = readyY
        b.z = 0f
        b.vx = sin(angle) * speed
        b.vy = -cos(angle) * speed
        b.vz = 0f
        b.t = 0f
        b.spin = 0f
        hasReady = false
        reloadT = SkeeTuning.RELOAD_SECONDS
        lastSpeed = speed
        speedShowT = 1.6f
        play(Sfx.ROLL, 0.8f, 0.8f + speed / 1500f)
        fx.haptics.tick()
    }

    override fun step(dt: Float) {
        readySquash.update(dt)
        speedShowT -= dt
        for (i in ringFlash.indices) ringFlash[i] = (ringFlash[i] - dt * 2f).coerceAtLeast(0f)
        if (!hasReady) {
            reloadT -= dt
            if (reloadT <= 0f && !timeUp) {
                hasReady = true
                readyX = REST_X
                readyY = REST_Y
                readySquash.snap(0.6f)
                play(Sfx.THUD, 0.4f, 1.4f)
            }
        } else if (dragging < 0) {
            readyX = damp(readyX, REST_X, 8f, dt)
            readyY = damp(readyY, REST_Y, 8f, dt)
        }
        for (b in balls) if (b.active) stepBall(b, dt)
    }

    private fun stepBall(b: Ball, dt: Float) {
        b.t += dt
        when (b.phase) {
            Phase.ROLLING -> {
                val sp = len(b.vx, b.vy)
                if (sp > 0f) {
                    val ns = (sp - SkeeTuning.ROLL_FRICTION * dt).coerceAtLeast(0f)
                    b.vx *= ns / sp
                    b.vy *= ns / sp
                }
                b.x += b.vx * dt
                b.y += b.vy * dt
                b.spin += sp * dt / BALL_R
                if (b.x < LANE_L + BALL_R) {
                    b.x = LANE_L + BALL_R; b.vx = abs(b.vx) * 0.6f; play(Sfx.BOUNCE, 0.3f, 1.5f)
                }
                if (b.x > LANE_R - BALL_R) {
                    b.x = LANE_R - BALL_R; b.vx = -abs(b.vx) * 0.6f; play(Sfx.BOUNCE, 0.3f, 1.5f)
                }
                if (b.y <= RAMP_Y) {
                    val planar = len(b.vx, b.vy)
                    if (b.vy < 0f && planar > 60f) {
                        b.phase = Phase.FLYING
                        b.vz = planar * SkeeTuning.LAUNCH_RATIO
                        play(Sfx.SWISH, 0.3f, 0.7f)
                    } else {
                        b.vy = abs(b.vy) + 40f
                    }
                }
                if (b.vy >= 0f && b.y > RAMP_Y + 4f && len(b.vx, b.vy) < 200f) {
                    // Too slow: it rolls back down the lane.
                    b.vy = (b.vy + 300f * dt)
                    if (b.y > GAME_H + BALL_R) gutter(b)
                }
                if (b.y > GAME_H + BALL_R) gutter(b)
            }
            Phase.FLYING -> {
                b.x += b.vx * dt
                b.y += b.vy * dt
                b.z += b.vz * dt
                b.vz -= SkeeTuning.FLIGHT_GRAVITY * dt
                b.spin += len(b.vx, b.vy) * dt / BALL_R
                if (b.x < 20f + BALL_R) {
                    b.x = 20f + BALL_R; b.vx = abs(b.vx) * 0.5f
                }
                if (b.x > GAME_W - 20f - BALL_R) {
                    b.x = GAME_W - 20f - BALL_R; b.vx = -abs(b.vx) * 0.5f
                }
                if (b.y < BOARD_TOP + BALL_R) {
                    // Hit the back wall: drop straight down into the outer ring.
                    b.y = BOARD_TOP + BALL_R
                    b.vy = abs(b.vy) * 0.3f
                    play(Sfx.THUD, 0.6f)
                }
                if (b.z <= 0f) land(b)
            }
            Phase.SETTLING -> {
                val t = clamp01(b.t / 0.45f)
                val e = easeOutCubic(t)
                b.x = b.fromX + (b.toX - b.fromX) * e
                b.y = b.fromY + (b.toY - b.fromY) * e
                b.z = abs(sin(t * Math.PI.toFloat() * 2f)) * 14f * (1f - t)
                if (b.t >= 0.5f) score(b)
            }
            Phase.GUTTER -> {
                if (b.t > 0.2f) b.active = false
            }
        }
    }

    private fun gutter(b: Ball) {
        b.phase = Phase.GUTTER
        b.t = 0f
        popups.add("GUTTER", CX, 420f, Color(Pal.GRAY), size = 3f)
        play(Sfx.GUTTER, 0.8f)
    }

    private fun land(b: Ball) {
        b.z = 0f
        b.phase = Phase.SETTLING
        b.t = 0f
        b.fromX = b.x
        b.fromY = b.y
        play(Sfx.THUD, 0.8f, 1.2f)
        fx.haptics.tick()
        val bdx = b.x - BONUS_X
        val bdy = (b.y - BONUS_Y) / SQUASH
        if (bdx * bdx + bdy * bdy < (BONUS_R + 6f) * (BONUS_R + 6f)) {
            b.ring = 6
            b.points = SkeeTuning.BONUS_POINTS
            b.toX = BONUS_X
            b.toY = BONUS_Y
            return
        }
        val dx = b.x - CX
        val dy = (b.y - CY) / SQUASH
        val d = sqrt(dx * dx + dy * dy)
        var ring = SkeeTuning.RING_RADII.indexOfFirst { d < it }
        if (ring < 0 || b.y > BOARD_BOTTOM) ring = SkeeTuning.RING_RADII.lastIndex
        b.ring = ring
        b.points = SkeeTuning.RING_POINTS[ring]
        // Roll into the cup at the top of the ring it landed in.
        val r = if (ring == 0) 0f else (SkeeTuning.RING_RADII[ring - 1] + SkeeTuning.RING_RADII[ring]) / 2f
        val ang = atan2(dy, dx)
        b.toX = CX + cos(ang) * r
        b.toY = CY + sin(ang) * r * SQUASH
    }

    private fun score(b: Ball) {
        b.active = false
        val pts = b.points
        ringFlash[b.ring] = 1f
        val color = Color(ringColor(b.ring))
        when {
            b.ring == 6 -> {
                addScore(pts, BONUS_X - 40f, BONUS_Y + 30f, Color(Pal.GOLD))
                popups.add("BONUS!!", CX, 200f, Color(Pal.GOLD), size = 5f, life = 1.4f)
                play(Sfx.JACKPOT)
                fx.haptics.jackpot()
                shake.add(0.6f)
                flash.trigger(0.8f)
                particles.confetti(0f, 0f, GAME_W, 90)
                particles.burst(BONUS_X, BONUS_Y, 40, 80f, 300f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.9f, 5f, kind = Particles.SPARKLE)
            }
            pts >= 100 -> {
                addScore(pts, b.toX, b.toY - 20f, color)
                popups.add("BULLSEYE!", CX, 270f, Color(Pal.RED), size = 4f, life = 1.2f)
                play(Sfx.WIN)
                fx.haptics.win()
                shake.add(0.45f)
                particles.burst(b.toX, b.toY, 36, 80f, 260f, intArrayOf(Pal.RED, Pal.YELLOW, Pal.WHITE), 0.8f, 5f)
            }
            else -> {
                addScore(pts, b.toX, b.toY - 20f, color)
                play(Sfx.COIN, 0.7f, 0.7f + pts / 100f)
                fx.haptics.hit()
                shake.add(0.08f + pts / 400f)
                particles.burst(b.toX, b.toY, 10 + pts / 4, 50f, 180f, intArrayOf(ringColor(b.ring), Pal.WHITE), 0.6f, 4f)
            }
        }
    }

    private fun ringColor(ring: Int): Int = when (ring) {
        0 -> Pal.RED
        1 -> Pal.YELLOW
        2 -> Pal.ORANGE
        3 -> Pal.PINK
        4 -> Pal.PURPLE
        5 -> Pal.SKY
        else -> Pal.GOLD
    }

    // ---------------------------------------------------------------- drawing

    override fun render(scope: DrawScope) {
        with(scope) {
            drawRect(Color(Pal.NIGHT), Offset(-40f, -40f), Size(GAME_W + 80f, GAME_H + 80f))
            // Side cabinet walls.
            drawRect(Color(Pal.shade(Pal.BLUE, 0.6f)), Offset(0f, 0f), Size(LANE_L, GAME_H))
            drawRect(Color(Pal.shade(Pal.BLUE, 0.6f)), Offset(LANE_R, 0f), Size(GAME_W - LANE_R, GAME_H))
            for (i in 0 until 12) {
                val on = ((time * 6f).toInt() + i) % 3 == 0
                val c = Color(if (on) Pal.YELLOW else Pal.shade(Pal.YELLOW, 0.35f))
                drawCircle(c, 5f, Offset(30f, 320f + i * 26f))
                drawCircle(c, 5f, Offset(GAME_W - 30f, 320f + i * 26f))
            }
            drawTarget(this)
            // Pit between ramp and target.
            drawRect(Color(Pal.BLACK), Offset(20f, BOARD_BOTTOM), Size(GAME_W - 40f, RAMP_TOP - BOARD_BOTTOM))
            // Ramp hump.
            for (i in 0 until 6) {
                val t = i / 5f
                val c = Pal.mix(Pal.TAN, Pal.WOOD, t)
                drawRect(Color(c), Offset(LANE_L, RAMP_TOP + i * 5f), Size(LANE_R - LANE_L, 5f))
            }
            drawRect(Color(Pal.CREAM), Offset(LANE_L, RAMP_TOP), Size(LANE_R - LANE_L, 2f), alpha = 0.7f)
            // Lane planks.
            drawRect(Color(Pal.WOOD), Offset(LANE_L, RAMP_Y), Size(LANE_R - LANE_L, GAME_H - RAMP_Y))
            for (i in 1 until 8) {
                val x = LANE_L + i * (LANE_R - LANE_L) / 8f
                drawRect(Color(Pal.shade(Pal.WOOD, 0.8f)), Offset(x, RAMP_Y), Size(2f, GAME_H - RAMP_Y))
            }
            for (i in 0 until 10) {
                val y = RAMP_Y + 10f + i * 32f
                val x = LANE_L + hash01(i, 5) * (LANE_R - LANE_L - 30f)
                drawRect(Color(Pal.shade(Pal.WOOD, 0.85f)), Offset(x, y), Size(20f, 2f))
            }
            // Guide arrows.
            for (i in 0 until 3) {
                val y = 470f - i * 44f
                val pulse = 0.25f + 0.25f * sin(time * 5f - i)
                PixelFont.drawCentered(this, "${PixelFont.UP}", CX, y, 4f, Color(Pal.CREAM), pulse, shadow = false)
            }
            drawRect(Color(Pal.shade(Pal.WOOD, 0.55f)), Offset(LANE_L - 6f, RAMP_Y), Size(6f, GAME_H - RAMP_Y))
            drawRect(Color(Pal.shade(Pal.WOOD, 0.55f)), Offset(LANE_R, RAMP_Y), Size(6f, GAME_H - RAMP_Y))

            // Balls on the target / in flight.
            for (b in balls) if (b.active && b.phase != Phase.ROLLING) drawBall(this, b.x, b.y, b.z, b.spin)
            for (b in balls) if (b.active && b.phase == Phase.ROLLING) drawBall(this, b.x, b.y, 0f, b.spin)

            // Ball tray and the ready ball.
            drawRoundRect(Color(Pal.shade(Pal.BLUE, 0.4f)), Offset(CX - 60f, REST_Y + 8f), Size(120f, 30f), CornerRadius(12f, 12f))
            if (hasReady) {
                val s = readySquash.value
                drawBall(this, readyX, readyY, 0f, 0f, sx = 2f - s, sy = s)
                if (dragging < 0 && !timeUp) {
                    val a = 0.5f + 0.5f * sin(time * 6f)
                    PixelFont.drawCentered(this, "FLICK ${PixelFont.UP}", CX, REST_Y + 36f, 1.8f, Color.White, a)
                }
            }
            drawSpeedMeter(this)
        }
    }

    private fun drawTarget(scope: DrawScope) {
        with(scope) {
            drawRoundRect(Color(Pal.NAVY), Offset(20f, BOARD_TOP - 10f), Size(GAME_W - 40f, BOARD_BOTTOM - BOARD_TOP + 10f), CornerRadius(18f, 18f))
            drawRoundRect(Color(Pal.SKY), Offset(20f, BOARD_TOP - 10f), Size(GAME_W - 40f, BOARD_BOTTOM - BOARD_TOP + 10f), CornerRadius(18f, 18f), style = Stroke(4f))
            for (ring in SkeeTuning.RING_RADII.indices.reversed()) {
                val r = SkeeTuning.RING_RADII[ring]
                val base = ringColor(ring)
                val lit = ringFlash[ring]
                val c = Pal.mix(Pal.shade(base, 0.55f), Pal.WHITE, lit * 0.6f)
                drawOval(Color(c), Offset(CX - r, CY - r * SQUASH), Size(r * 2f, r * 2f * SQUASH))
                drawOval(Color(base), Offset(CX - r, CY - r * SQUASH), Size(r * 2f, r * 2f * SQUASH), style = Stroke(3f))
            }
            drawOval(Color(Pal.BLACK), Offset(CX - 9f, CY - 9f * SQUASH), Size(18f, 18f * SQUASH))
            for (ring in 1 until SkeeTuning.RING_RADII.size) {
                val rIn = SkeeTuning.RING_RADII[ring - 1]
                val rOut = SkeeTuning.RING_RADII[ring]
                // Alternate labels right and left along the horizontal axis so they never overlap.
                val side = if (ring % 2 == 1) 1f else -1f
                val lx = CX + side * (rIn + rOut) / 2f
                PixelFont.drawCentered(this, SkeeTuning.RING_POINTS[ring].toString(), lx, CY - 7f, 2f, Color.White, 0.9f)
            }
            PixelFont.drawCentered(this, "100", CX, CY - 22f, 2f, Color(Pal.YELLOW), 0.9f)
            // Bonus corner hole.
            val blink = 0.5f + 0.5f * sin(time * 9f)
            drawOval(Color(Pal.GOLD), Offset(BONUS_X - BONUS_R - 4f, BONUS_Y - (BONUS_R + 4f) * SQUASH), Size((BONUS_R + 4f) * 2f, (BONUS_R + 4f) * 2f * SQUASH), alpha = 0.5f + 0.5f * blink)
            drawOval(Color(Pal.BLACK), Offset(BONUS_X - BONUS_R, BONUS_Y - BONUS_R * SQUASH), Size(BONUS_R * 2f, BONUS_R * 2f * SQUASH))
            PixelFont.drawCentered(this, "200", BONUS_X, BONUS_Y + 16f, 2f, Color(Pal.GOLD), 0.6f + 0.4f * blink)
            if (ringFlash[6] > 0f) drawCircle(Color(Pal.GOLD), 40f * (1f + ringFlash[6]), Offset(BONUS_X, BONUS_Y), alpha = ringFlash[6] * 0.5f)
        }
    }

    private fun drawBall(scope: DrawScope, x: Float, y: Float, z: Float, spin: Float, sx: Float = 1f, sy: Float = 1f) {
        with(scope) {
            val lift = z * 0.6f
            val size = BALL_R * (1f + z / 260f)
            drawOval(Color.Black, Offset(x - BALL_R * 0.9f, y - BALL_R * 0.35f + 4f), Size(BALL_R * 1.8f, BALL_R * 0.8f), alpha = 0.35f / (1f + z / 80f))
            val cy = y - lift
            val w = size * sx
            val h = size * sy
            drawOval(Color(Pal.DARKRED), Offset(x - w, cy - h * 2f + size), Size(w * 2f, h * 2f))
            val stripe = (spin % (Math.PI.toFloat() * 2f)) / (Math.PI.toFloat() * 2f)
            val sy2 = cy - h * 2f + size + h * 2f * stripe
            drawRect(Color(Pal.RED), Offset(x - w * 0.8f, sy2 - 2f), Size(w * 1.6f, 4f), alpha = 0.6f)
            drawCircle(Color.White, size * 0.3f, Offset(x - w * 0.35f, cy - h * 2f + size + h * 0.55f), alpha = 0.5f)
        }
    }

    private fun drawSpeedMeter(scope: DrawScope) {
        if (speedShowT <= 0f) return
        with(scope) {
            val a = clamp01(speedShowT / 0.4f)
            val x = LANE_R + 12f
            val top = 340f
            val h = 220f
            drawRect(Color(Pal.BLACK), Offset(x, top), Size(34f, h), alpha = 0.7f * a)
            // Speeds that land a straight roll from the tray inside the 50 ring.
            val idealLo = 425f
            val idealHi = 478f
            fun yFor(s: Float) = top + h - (s - SkeeTuning.MIN_SPEED) / (SkeeTuning.MAX_SPEED - SkeeTuning.MIN_SPEED) * h
            drawRect(Color(Pal.LIME), Offset(x, yFor(idealHi)), Size(34f, yFor(idealLo) - yFor(idealHi)), alpha = 0.35f * a)
            val fillTop = yFor(lastSpeed)
            drawRect(Color(Pal.ORANGE), Offset(x + 6f, fillTop), Size(22f, top + h - fillTop), alpha = a)
            PixelFont.drawCentered(this, "PWR", x + 17f, top + h + 6f, 2f, Color.White, a)
        }
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NAVY))
        val cx = w / 2f
        val cy = h * 0.38f
        p.disc(cx, cy, h * 0.34f, Color(Pal.PURPLE))
        p.disc(cx, cy, h * 0.24f, Color(Pal.PINK))
        p.disc(cx, cy, h * 0.14f, Color(Pal.YELLOW))
        p.disc(cx, cy, h * 0.06f, Color(Pal.RED))
        val t = (time % 2.2f) / 2.2f
        val by = h - 2f - t * (h - cy - 1f)
        val bx = cx + sin(time * 2f) * 2f
        val hop = if (t > 0.6f) sin((t - 0.6f) / 0.4f * Math.PI.toFloat()) * 3f else 0f
        p.disc(bx, by - hop, 1.6f, Color(Pal.DARKRED))
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("50", w - 5f, 1f, Color(Pal.YELLOW), tiny = true)
    }
}
