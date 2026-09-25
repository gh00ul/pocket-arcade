package com.pocketarcade.games.skeeball

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.FlickTracker
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.Vec2
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.easeOutCubic
import com.pocketarcade.engine.len
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
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
    override val look = CabinetLook(body = Pal.BLUE, trim = Pal.YELLOW, glow = Pal.SKY, shape = CabinetShape.SKEEBALL)
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

        // 3D alley layout (world units = field units).
        const val BOARD_L = 20f
        const val BOARD_R = 340f
        const val LANE_END = 780f
        const val RAMP_H = 10f
        const val BOARD_BASE = 16f
        /** tan of the board's tilt; its cosine is [SQUASH], which turns the ring ellipses into circles. */
        const val BOARD_TAN = 0.802f
        const val PIT_Y = -40f
        const val RAIL_H = 20f
        const val SIGN_TOP = 300f
        const val SIDE_BACK_Z = 10f
        const val SIDE_FRONT_Z = 298f
        const val SIDE_BACK_Y = 340f
        const val SIDE_FRONT_Y = 100f
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
                    // The ball follows the finger across the lane.
                    if (stage.touchToPlane(x, y, 0f, pt)) {
                        readyX = pt[0].coerceIn(LANE_L + BALL_R, LANE_R - BALL_R)
                        readyY = pt[1].coerceIn(GRAB_TOP + 60f, GAME_H - 30f)
                    }
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
        // Effects appear where the cup is on screen.
        stage.toField(b.toX, surfaceY(b.toY) + BALL_R, b.toY, pt)
        val sx = pt[0]
        val sy = pt[1]
        when {
            b.ring == 6 -> {
                addScore(pts, sx - 40f, sy + 30f, Color(Pal.GOLD))
                popups.add("BONUS!!", CX, 200f, Color(Pal.GOLD), size = 5f, life = 1.4f)
                play(Sfx.JACKPOT)
                fx.haptics.jackpot()
                shake.add(0.6f)
                flash.trigger(0.8f)
                particles.confetti(0f, 0f, GAME_W, 90)
                particles.burst(sx, sy, 40, 80f, 300f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.9f, 5f, kind = Particles.SPARKLE)
            }
            pts >= 100 -> {
                addScore(pts, sx, sy - 20f, color)
                popups.add("BULLSEYE!", CX, 300f, Color(Pal.RED), size = 4f, life = 1.2f)
                play(Sfx.WIN)
                fx.haptics.win()
                shake.add(0.45f)
                particles.burst(sx, sy, 36, 80f, 260f, intArrayOf(Pal.RED, Pal.YELLOW, Pal.WHITE), 0.8f, 5f)
            }
            else -> {
                addScore(pts, sx, sy - 20f, color)
                play(Sfx.COIN, 0.7f, 0.7f + pts / 100f)
                fx.haptics.hit()
                shake.add(0.08f + pts / 400f)
                particles.burst(sx, sy, 10 + pts / 4, 50f, 180f, intArrayOf(ringColor(b.ring), Pal.WHITE), 0.6f, 4f)
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

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The alley in 3D. The simulation stays top-down (x across, y up the lane, z height); the
     * world uses x as is, the lane's y as depth and adds the surface height: flat lane, the
     * jump ramp, the pit, then the target board tilted back so the rings read as circles.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(CX, 300f, 1000f, CX, 40f, 300f, fovDeg = 44f)
    }
    private val pt = FloatArray(3)

    private val boardTex by lazy {
        SkeeArt.board(
            BOARD_L, BOARD_TOP - 10f, BOARD_R, BOARD_BOTTOM, CX, CY, SQUASH,
            SkeeTuning.RING_RADII, SkeeTuning.RING_POINTS, IntArray(6) { ringColor(it) },
            BONUS_X, BONUS_Y, BONUS_R,
        )
    }
    private val laneTex by lazy { SkeeArt.lane((LANE_R - LANE_L).toInt(), (LANE_END - RAMP_Y).toInt(), 1.5f) }
    private val boardLight = PointLight(CX, 330f, 70f, 1f, 0.95f, 1.05f, 520f, 1.1f)
    private val laneLight = PointLight(CX, 150f, 470f, 1f, 0.8f, 0.6f, 420f, 0.7f)
    private val marqueeLight = PointLight(CX, 260f, 40f, 1f, 0.35f, 0.55f, 260f, 0.8f)
    private val ringLight = PointLight(CX, 150f, CY, 1f, 1f, 1f, 260f, 0f)

    /** Raised walls standing up from the board between the scoring rings. */
    private val ringWalls: Model by lazy {
        val tex = SkeeArt.ringWall.full
        val b = ModelBuilder()
        val nY = SQUASH
        val nZ = 0.626f
        val n = 32
        for (ring in SkeeTuning.RING_RADII.indices) {
            val rad = SkeeTuning.RING_RADII[ring]
            val h = 5f + ring * 0.6f
            val tint = Pal.mix(ringColor(ring), Pal.WHITE, 0.25f)
            for (k in 0 until n) {
                val a0 = k * TAU / n
                val a1 = (k + 1) * TAU / n
                val x0 = CX + cos(a0) * rad
                val z0 = CY + sin(a0) * rad * SQUASH
                val x1 = CX + cos(a1) * rad
                val z1 = CY + sin(a1) * rad * SQUASH
                val y0 = boardY(z0)
                val y1 = boardY(z1)
                b.quad(
                    x0, y0 + nY * h, z0 + nZ * h, x1, y1 + nY * h, z1 + nZ * h, x1, y1, z1, x0, y0, z0,
                    tex, 0f, nY, nZ, cull = false, tint = tint,
                )
            }
        }
        b.build()
    }

    /** Height of the playfield surface under lane position [y]. */
    private fun surfaceY(y: Float): Float = when {
        y >= RAMP_Y -> 0f
        y >= RAMP_TOP -> {
            val t = (RAMP_Y - y) / (RAMP_Y - RAMP_TOP)
            RAMP_H * t * t
        }
        y >= BOARD_BOTTOM -> RAMP_H + (BOARD_BASE - RAMP_H) * (RAMP_TOP - y) / (RAMP_TOP - BOARD_BOTTOM)
        else -> boardY(y)
    }

    private fun boardY(y: Float) = BOARD_BASE + (BOARD_BOTTOM - y) * BOARD_TAN

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        lightScene(r)
        r.gradient(0xFF04020A.toInt(), Pal.NIGHT)
        drawCabinet(r)
        drawBoardFx(r)
        for (b in balls) if (b.active && b.phase != Phase.GUTTER) drawBall(r, b.x, b.y, b.z, b.spin, 1f, 1f)
        if (hasReady) {
            val s = readySquash.value
            drawBall(r, readyX, readyY, 0f, 0f, 2f - s, s)
        }
        stage.present()

        if (hasReady && dragging < 0 && !timeUp && stage.toField(readyX, 0f, readyY, pt)) {
            val a = 0.5f + 0.5f * sin(time * 6f)
            ArcadeFont.drawCentered(scope, "FLICK ${ArcadeFont.UP}", pt[0], pt[1] + 26f, 1.8f, Color.White, a)
        }
        drawSpeedMeter(scope)
    }

    private fun lightScene(r: Renderer3D) {
        val l = r.lighting
        l.ambR = 0.5f; l.ambG = 0.47f; l.ambB = 0.62f
        l.setDirection(0.2f, 1f, 0.7f)
        l.dirR = 0.35f; l.dirG = 0.33f; l.dirB = 0.3f
        l.points.clear()
        l.points += boardLight
        l.points += laneLight
        marqueeLight.intensity = 0.7f + 0.2f * sin(time * 5f)
        l.points += marqueeLight
        var hot = -1
        for (i in ringFlash.indices) if (ringFlash[i] > 0f && (hot < 0 || ringFlash[i] > ringFlash[hot])) hot = i
        if (hot >= 0) {
            val c = ringColor(hot)
            ringLight.r = (c shr 16 and 255) / 255f
            ringLight.g = (c shr 8 and 255) / 255f
            ringLight.b = (c and 255) / 255f
            ringLight.intensity = ringFlash[hot] * 1.6f
            if (hot == 6) {
                ringLight.x = BONUS_X; ringLight.z = BONUS_Y; ringLight.y = boardY(BONUS_Y) + 60f
            } else {
                ringLight.x = CX; ringLight.z = CY; ringLight.y = boardY(CY) + 70f
            }
            l.points += ringLight
        }
    }

    private fun drawCabinet(r: Renderer3D) {
        val white = TexKit.white.full
        // The pit between ramp and board, and the board's front edge.
        r.quad(
            BOARD_L, PIT_Y, BOARD_BOTTOM, BOARD_R, PIT_Y, BOARD_BOTTOM, BOARD_R, PIT_Y, RAMP_TOP + 4f, BOARD_L, PIT_Y, RAMP_TOP + 4f,
            SkeeArt.pit.full, 0f, 1f, 0f,
        )
        r.quad(
            BOARD_L, BOARD_BASE, BOARD_BOTTOM, BOARD_R, BOARD_BASE, BOARD_BOTTOM, BOARD_R, PIT_Y, BOARD_BOTTOM, BOARD_L, PIT_Y, BOARD_BOTTOM,
            SkeeArt.boardEdge.full, 0f, 0f, 1f,
        )
        // Jump ramp: a curved hump in three strips.
        val rampTex = SkeeArt.ramp.full
        val steps = 3
        for (i in 0 until steps) {
            val y0 = RAMP_Y - (RAMP_Y - RAMP_TOP) * i / steps
            val y1 = RAMP_Y - (RAMP_Y - RAMP_TOP) * (i + 1) / steps
            val h0 = surfaceY(y0)
            val h1 = surfaceY(y1)
            val v0 = rampTex.h * (1f - (i + 1f) / steps)
            val v1 = rampTex.h * (1f - i.toFloat() / steps)
            r.quad(
                LANE_L, h1, y1, LANE_R, h1, y1, LANE_R, h0, y0, LANE_L, h0, y0,
                rampTex, 0f, 0.95f, 0.3f, v0 = v0, v1 = v1,
            )
        }
        // The lane.
        r.quad(
            LANE_L, 0f, RAMP_Y, LANE_R, 0f, RAMP_Y, LANE_R, 0f, LANE_END, LANE_L, 0f, LANE_END,
            laneTex.full, 0f, 1f, 0f,
        )
        // Rails along both sides of the lane and ramp.
        val railTex = SkeeArt.railSide.full
        val rt = SkeeArt.railTop.full
        r.quad(BOARD_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, LANE_END, BOARD_L, RAIL_H, LANE_END, rt, 0f, 1f, 0f)
        r.quad(LANE_R, RAIL_H, BOARD_BOTTOM, BOARD_R, RAIL_H, BOARD_BOTTOM, BOARD_R, RAIL_H, LANE_END, LANE_R, RAIL_H, LANE_END, rt, 0f, 1f, 0f, u0 = rt.w.toFloat(), u1 = 0f)
        r.quad(LANE_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, LANE_END, LANE_L, PIT_Y, LANE_END, LANE_L, PIT_Y, BOARD_BOTTOM, railTex, 1f, 0f, 0f)
        r.quad(LANE_R, RAIL_H, LANE_END, LANE_R, RAIL_H, BOARD_BOTTOM, LANE_R, PIT_Y, BOARD_BOTTOM, LANE_R, PIT_Y, LANE_END, railTex, -1f, 0f, 0f)
        // The target board, tilted back.
        val far = BOARD_TOP - 10f
        r.quad(
            BOARD_L, boardY(far), far, BOARD_R, boardY(far), far, BOARD_R, BOARD_BASE, BOARD_BOTTOM, BOARD_L, BOARD_BASE, BOARD_BOTTOM,
            boardTex.full, 0f, SQUASH, 0.626f,
        )
        ringWalls.draw(r)
        // Cabinet sides: tall at the back, sloping down towards the player.
        val side = SkeeArt.sidePanel.full
        sidePanel(r, BOARD_L, 1f, side)
        sidePanel(r, BOARD_R, -1f, side)
        // Back wall and marquee.
        val topY = boardY(far)
        r.quad(BOARD_L, SIGN_TOP + 30f, far, BOARD_R, SIGN_TOP + 30f, far, BOARD_R, topY - 4f, far, BOARD_L, topY - 4f, far, SkeeArt.boardEdge.full, 0f, 0f, 1f)
        val m = SkeeArt.marquee.full
        r.quad(BOARD_L + 6f, SIGN_TOP, far + 2f, BOARD_R - 6f, SIGN_TOP, far + 2f, BOARD_R - 6f, SIGN_TOP - 84f, far + 2f, BOARD_L + 6f, SIGN_TOP - 84f, far + 2f, m, 0f, 0f, 1f, emissive = 1f)
        // Marquee bulbs, chasing.
        val glow = TexKit.glow.full
        for (i in 0 until 18) {
            val bx = BOARD_L + 6f + (9f + i * 17.8f) * (BOARD_R - BOARD_L - 12f) / 320f
            val on = ((time * 8f).toInt() + i) % 3 == 0
            val c = if (on) Pal.YELLOW else Pal.shade(Pal.ORANGE, 0.5f)
            for (row in 0..1) {
                val by = if (row == 0) SIGN_TOP - 10f * 84f / 90f else SIGN_TOP - 80f * 84f / 90f
                r.sprite(bx, by, far + 3f, 6f, 6f, TexKit.dot.full, emissive = 1.2f, tint = c)
                if (on) r.sprite(bx, by, far + 4f, 22f, 22f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = Pal.YELLOW)
            }
        }
        // Bulbs along each side's sloping top edge.
        for (s in 0..1) {
            val x = if (s == 0) BOARD_L + 2f else BOARD_R - 2f
            for (i in 0 until 11) {
                val t = i / 10f
                val z = SIDE_BACK_Z + (SIDE_FRONT_Z - SIDE_BACK_Z) * t
                val y = SIDE_BACK_Y + (SIDE_FRONT_Y - SIDE_BACK_Y) * t - 6f
                val on = ((time * 6f).toInt() - i) % 4 == 0
                r.sprite(x, y, z, 7f, 7f, TexKit.dot.full, emissive = 1.2f, tint = if (on) Pal.YELLOW else Pal.shade(Pal.GOLD, 0.45f))
                if (on) r.sprite(x, y, z, 26f, 26f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.GOLD)
            }
        }
        // Guide chevrons pulse up the lane.
        val chev = SkeeArt.chevron.full
        for (i in 0 until 3) {
            val y = 470f - i * 44f
            val a = 0.2f + 0.3f * (0.5f + 0.5f * sin(time * 5f - i))
            r.flat(CX, y, 0.5f, 34f, 20f, chev, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.CREAM)
        }
    }

    private fun sidePanel(r: Renderer3D, x: Float, nx: Float, tex: Region) {
        val w = tex.w.toFloat()
        val h = tex.h.toFloat()
        val spanZ = SIDE_FRONT_Z - SIDE_BACK_Z
        val spanY = SIDE_BACK_Y - PIT_Y
        r.begin(tex)
        r.normal(nx, 0f, 0f)
        r.vertex(x, SIDE_BACK_Y, SIDE_BACK_Z, 0f, 0f)
        r.vertex(x, SIDE_FRONT_Y, SIDE_FRONT_Z, w, (SIDE_BACK_Y - SIDE_FRONT_Y) / spanY * h)
        r.vertex(x, RAIL_H, SIDE_FRONT_Z, w, (SIDE_BACK_Y - RAIL_H) / spanY * h)
        r.vertex(x, PIT_Y, SIDE_FRONT_Z - spanZ * 0.1f, w * 0.9f, h)
        r.vertex(x, PIT_Y, SIDE_BACK_Z, 0f, h)
        r.end()
    }

    /** Ring flashes and the blinking bonus hole, drawn as light on the board. */
    private fun drawBoardFx(r: Renderer3D) {
        val white = TexKit.white.full
        for (ring in 0 until 6) {
            val f = ringFlash[ring]
            if (f <= 0f) continue
            val rIn = if (ring == 0) 9f else SkeeTuning.RING_RADII[ring - 1]
            val rOut = SkeeTuning.RING_RADII[ring]
            annulus(r, rIn, rOut, ringColor(ring), f * 0.75f, white)
        }
        val glow = TexKit.glow.full
        val blink = 0.5f + 0.5f * sin(time * 9f)
        val by = boardY(BONUS_Y) + 3f
        r.sprite(BONUS_X, by, BONUS_Y, 44f, 44f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.25f + 0.35f * blink, tint = Pal.GOLD)
        val bf = ringFlash[6]
        if (bf > 0f) r.sprite(BONUS_X, by, BONUS_Y, 90f * (1f + bf), 90f * (1f + bf), glow, blend = Blend.ADD, emissive = 1f, alpha = bf, tint = Pal.GOLD)
    }

    private fun annulus(r: Renderer3D, rIn: Float, rOut: Float, color: Int, alpha: Float, tex: Region) {
        val n = 28
        for (k in 0 until n) {
            val a0 = k * TAU / n
            val a1 = (k + 1) * TAU / n
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            fun px(c: Float, rr: Float) = CX + c * rr
            fun py(s: Float, rr: Float) = CY + s * rr * SQUASH
            val ax = px(c0, rOut); val az = py(s0, rOut)
            val bx = px(c1, rOut); val bz = py(s1, rOut)
            val cx = px(c1, rIn); val cz = py(s1, rIn)
            val dx = px(c0, rIn); val dz = py(s0, rIn)
            r.quad(
                ax, boardY(az) + 0.6f, az, bx, boardY(bz) + 0.6f, bz, cx, boardY(cz) + 0.6f, cz, dx, boardY(dz) + 0.6f, dz,
                tex, 0f, SQUASH, 0.626f, blend = Blend.ADD, emissive = 1f, alpha = alpha, cull = false, tint = color,
            )
        }
    }

    private val ballModel by lazy { SkeeArt.ball(BALL_R) }
    private val ballXf = Xform()

    private fun drawBall(r: Renderer3D, x: Float, y: Float, z: Float, spin: Float, sx: Float, sy: Float) {
        val base = surfaceY(y)
        // Contact shadow following the surface under the ball.
        val sh = TexKit.shadow.full
        val a = 0.55f / (1f + z / 60f)
        val sw = BALL_R * 1.1f
        r.quad(
            x - sw, surfaceY(y - sw) + 0.8f, y - sw, x + sw, surfaceY(y - sw) + 0.8f, y - sw,
            x + sw, surfaceY(y + sw) + 0.8f, y + sw, x - sw, surfaceY(y + sw) + 0.8f, y + sw,
            sh, 0f, 1f, 0f, blend = Blend.ALPHA, alpha = a, cull = false,
        )
        // Rolling up the lane (towards -z) turns the ball about the x axis.
        ballXf.set(x, base + z + BALL_R * sy, y, pitch = -spin).stretch(sx, sy, sx)
        ballModel.draw(r, xf = ballXf)
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
            ArcadeFont.drawCentered(this, "PWR", x + 17f, top + h + 6f, 2f, Color.White, a)
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
