package com.pocketarcade.games.hoops

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.pocketarcade.engine.FlickTracker
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.PixelPainter
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.Vec2
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.drawPixelImage
import com.pocketarcade.engine.drawPixelImageSquash
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Difficulty and payout knobs for basketball hoops. */
object HoopsTuning {
    const val ROUND_SECONDS = 44f
    /** Launch angle above horizontal, degrees. */
    const val LAUNCH_ANGLE_DEG = 68f
    /** Upward flick speed (field units/s) that produces the perfect-power shot. */
    const val FLICK_IDEAL = 1700f
    /** Fractional power change per field unit/s away from [FLICK_IDEAL]. Lower is more forgiving. */
    const val POWER_SENSITIVITY = 0.0001f
    const val MIN_FLICK = 380f
    /** Sideways flick speed → sideways ball speed (m/s per field unit/s). */
    const val LATERAL_SCALE = 0.0012f
    /** 0..1 share of the sideways aim the machine corrects for you (aimed at the hoop's current spot). */
    const val AIM_ASSIST = 0.6f
    const val RELOAD_SECONDS = 0.3f
    const val MOVE_AMPLITUDE_M = 0.45f
    const val MOVE_PERIOD = 3.2f
    const val BASKET_POINTS = 10
    const val SWISH_BONUS = 3
    const val MAX_MULTIPLIER = 5
    const val POINTS_PER_TICKET = 20
    const val BASE_TICKETS = 2
}

class HoopsGame : BaseMiniGame() {
    override val id = "hoops"
    override val title = "HOOP SHOT"
    override val marquee = "HOOPS"
    override val instructions = listOf(
        "FLICK UP TO SHOOT",
        "FLICK SPEED = SHOT POWER",
        "MAKES IN A ROW MULTIPLY",
        "YOUR POINTS (UP TO x5)",
        "SECOND HALF: HOOP MOVES!",
    )
    override val look = CabinetLook(body = Pal.RED, trim = Pal.ORANGE, glow = Pal.ORANGE, shape = CabinetShape.LANE)
    override val roundSeconds = HoopsTuning.ROUND_SECONDS

    private companion object {
        const val G = 9.8f
        const val CAM_Y = 1.7f
        const val CAM_Z = -1.2f
        const val F = 420f
        const val CX = 180f
        const val HORIZON = 258f
        const val BALL_R = 0.12f
        const val RIM_R = 0.23f
        const val RIM_Y = 2.3f
        const val HOOP_Z = 2.6f
        const val BOARD_Z = 2.87f
        const val BOARD_HALF_W = 0.6f
        const val BOARD_BOTTOM = 2.15f
        const val BOARD_TOP = 3.0f
        const val START_Y = 0.7f
        const val START_Z = 0.2f
        const val CAGE_HALF_W = 1.0f
        const val BACK_Z = 3.2f
    }

    private class Ball {
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var spin = 0f
        var t = 0f
        var active = false
        var scored = false
        var touchedRim = false
        var resolved = false
        var onFloor = false
        var rimCooldown = 0f
    }

    private val balls = Array(6) { Ball() }
    private var hasReady = true
    private var readyX = 0f
    private var reloadT = 0f
    private var dragging = -1L
    private val flick = FlickTracker()
    private val tmp = Vec2()
    private var hoopX = 0f
    private var hoopVX = 0f
    private var moving = false
    private var streak = 0
    private var netSwish = 0f
    private var rimShake = 0f
    private val readySquash = Spring()
    private val multSpring = Spring(stiffness = 300f, damping = 10f)
    private var makes = 0
    private var shots = 0

    private val ballImg: ImageBitmap by lazy { buildBall() }

    private val idealPower: Float by lazy {
        val th = HoopsTuning.LAUNCH_ANGLE_DEG * (Math.PI.toFloat() / 180f)
        val dz = HOOP_Z - START_Z
        val dy = RIM_Y - START_Y
        val c = cos(th)
        sqrt(G * dz * dz / (2f * c * c * (tan(th) * dz - dy)))
    }

    override fun reset() {
        balls.forEach { it.active = false }
        hasReady = true
        readyX = 0f
        reloadT = 0f
        dragging = -1L
        hoopX = 0f
        hoopVX = 0f
        moving = false
        streak = 0
        netSwish = 0f
        rimShake = 0f
        makes = 0
        shots = 0
        readySquash.snap(1f)
        multSpring.snap(1f)
    }

    override fun ticketsFor(score: Int): Int = HoopsTuning.BASE_TICKETS + score / HoopsTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = balls.none { it.active && !it.resolved }

    override fun onTimeUp() {
        dragging = -1L
    }

    private val multiplier: Int get() = streak.coerceIn(1, HoopsTuning.MAX_MULTIPLIER)

    // ---------------------------------------------------------------- projection

    private fun depth(z: Float) = (z - CAM_Z).coerceAtLeast(0.2f)
    private fun sx(x: Float, z: Float) = CX + x / depth(z) * F
    private fun sy(y: Float, z: Float) = HORIZON - (y - CAM_Y) / depth(z) * F
    private fun sr(r: Float, z: Float) = r / depth(z) * F

    // ---------------------------------------------------------------- input

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> if (dragging < 0 && hasReady && !timeUp && y > 330f) {
                dragging = id
                flick.reset(x, y, timeMs)
                readySquash.kick(-3f)
            }
            TouchType.MOVE -> if (id == dragging) {
                flick.add(x, y, timeMs)
                readyX = ((x - CX) / 300f).coerceIn(-0.45f, 0.45f)
            }
            TouchType.UP -> if (id == dragging) {
                flick.add(x, y, timeMs)
                dragging = -1L
                shoot()
            }
        }
    }

    private fun shoot() {
        flick.velocity(tmp)
        val up = -tmp.y
        if (up < HoopsTuning.MIN_FLICK || timeUp) return
        val b = balls.firstOrNull { !it.active } ?: return
        val th = HoopsTuning.LAUNCH_ANGLE_DEG * (Math.PI.toFloat() / 180f)
        val power = (idealPower * (1f + (up - HoopsTuning.FLICK_IDEAL) * HoopsTuning.POWER_SENSITIVITY)).coerceIn(3f, 11f)
        b.active = true
        b.resolved = false
        b.scored = false
        b.touchedRim = false
        b.onFloor = false
        b.t = 0f
        b.x = readyX
        b.y = START_Y
        b.z = START_Z
        b.vy = power * sin(th)
        b.vz = power * cos(th)
        val tCross = (HOOP_Z - START_Z) / b.vz
        val need = (hoopX - readyX) / tCross
        b.vx = need * HoopsTuning.AIM_ASSIST + tmp.x * HoopsTuning.LATERAL_SCALE
        b.spin = 0f
        b.rimCooldown = 0f
        hasReady = false
        reloadT = HoopsTuning.RELOAD_SECONDS
        shots++
        play(Sfx.WHOOSH, 0.5f, 1.2f)
        fx.haptics.tick()
    }

    // ---------------------------------------------------------------- simulation

    override fun step(dt: Float) {
        readySquash.update(dt)
        multSpring.update(dt)
        netSwish = (netSwish - dt * 2.2f).coerceAtLeast(0f)
        rimShake = (rimShake - dt * 5f).coerceAtLeast(0f)

        val half = roundSeconds / 2f
        if (!moving && time >= half) {
            moving = true
            popups.add("HOOP ON THE MOVE!", CX, 120f, Color(Pal.CYAN), size = 3f, life = 1.6f)
            play(Sfx.GO, 0.8f)
        }
        val newX = if (moving) {
            val ramp = clamp01((time - half) / 1f)
            sin((time - half) / HoopsTuning.MOVE_PERIOD * TAU) * HoopsTuning.MOVE_AMPLITUDE_M * ramp
        } else 0f
        hoopVX = (newX - hoopX) / dt
        hoopX = newX

        if (!hasReady) {
            reloadT -= dt
            if (reloadT <= 0f && !timeUp) {
                hasReady = true
                readySquash.snap(0.65f)
                play(Sfx.BOUNCE, 0.4f, 1.3f)
            }
        } else if (dragging < 0) {
            readyX = damp(readyX, 0f, 3f, dt)
        }

        for (b in balls) if (b.active) stepBall(b, dt)

        // The hoop catches fire at the max multiplier.
        if (streak >= HoopsTuning.MAX_MULTIPLIER && rng.nextFloat() < 0.5f) {
            val rx = sx(hoopX, HOOP_Z)
            val ry = sy(RIM_Y, HOOP_Z)
            particles.spawn(
                rx + rng.range(-24f, 24f), ry, rng.range(-10f, 10f), rng.range(-120f, -60f),
                0.5f, 5f, if (rng.nextBoolean()) Pal.ORANGE else Pal.YELLOW,
            )
        }
    }

    private fun stepBall(b: Ball, dt: Float) {
        b.t += dt
        b.rimCooldown -= dt
        val prevY = b.y
        b.vy -= G * dt
        b.x += b.vx * dt
        b.y += b.vy * dt
        b.z += b.vz * dt
        b.spin += dt * (3f + abs(b.vz) * 2f)

        // Rim: treat as a thin torus.
        val dxh = b.x - hoopX
        val dzh = b.z - HOOP_Z
        val dh = sqrt(dxh * dxh + dzh * dzh)
        if (dh > 1e-4f) {
            val px = hoopX + dxh / dh * RIM_R
            val pz = HOOP_Z + dzh / dh * RIM_R
            val ex = b.x - px
            val ey = b.y - RIM_Y
            val ez = b.z - pz
            val d = sqrt(ex * ex + ey * ey + ez * ez)
            val minD = BALL_R + 0.02f
            if (d < minD && d > 1e-4f) {
                val nx = ex / d
                val ny = ey / d
                val nz = ez / d
                b.x += nx * (minD - d)
                b.y += ny * (minD - d)
                b.z += nz * (minD - d)
                val vn = b.vx * nx + b.vy * ny + b.vz * nz
                if (vn < 0f) {
                    val e = 0.55f
                    b.vx -= (1f + e) * vn * nx
                    b.vy -= (1f + e) * vn * ny
                    b.vz -= (1f + e) * vn * nz
                    b.vx += rng.range(-0.25f, 0.25f)
                    if (b.rimCooldown <= 0f) {
                        b.rimCooldown = 0.1f
                        b.touchedRim = true
                        rimShake = 1f
                        play(Sfx.RIM, 0.8f, rng.range(0.9f, 1.1f))
                        fx.haptics.tick()
                    }
                }
            }
        }

        // Backboard.
        if (b.z + BALL_R > BOARD_Z && b.vz > 0f && b.y in BOARD_BOTTOM - BALL_R..BOARD_TOP + BALL_R &&
            abs(b.x - hoopX) < BOARD_HALF_W + BALL_R
        ) {
            b.z = BOARD_Z - BALL_R
            b.vz = -b.vz * 0.55f
            b.vx += hoopVX * 0.3f
            play(Sfx.THUD, 0.7f, 1.1f)
            shake.add(0.06f)
        }
        // Back wall of the cage.
        if (b.z + BALL_R > BACK_Z && b.vz > 0f) {
            b.z = BACK_Z - BALL_R; b.vz = -b.vz * 0.4f
        }
        // Side nets.
        if (abs(b.x) + BALL_R > CAGE_HALF_W) {
            b.x = (CAGE_HALF_W - BALL_R) * if (b.x > 0f) 1f else -1f
            b.vx = -b.vx * 0.4f
        }

        // Score: passes down through the rim plane inside the ring.
        if (!b.scored && !b.resolved && prevY > RIM_Y && b.y <= RIM_Y && b.vy < 0f) {
            val hx = b.x - hoopX
            val hz = b.z - HOOP_Z
            if (sqrt(hx * hx + hz * hz) < RIM_R - BALL_R * 0.4f) {
                b.scored = true
                basket(b)
            }
        }
        if (b.scored) {
            // Funnel through the net.
            b.x = damp(b.x, hoopX, 10f, dt)
            b.z = damp(b.z, HOOP_Z, 10f, dt)
            b.vx *= 0.9f
            b.vz *= 0.9f
        }

        // Floor bounce, then roll back down the ramp to the player.
        if (b.y < BALL_R) {
            b.y = BALL_R
            if (b.vy < 0f) {
                b.vy = -b.vy * 0.45f
                if (abs(b.vy) > 0.6f) play(Sfx.BOUNCE, 0.5f)
            }
            if (!b.onFloor) {
                b.onFloor = true
                resolve(b)
            }
            b.vz = damp(b.vz, -2.6f, 3f, dt)
            b.vx = damp(b.vx, 0f, 2f, dt)
        }
        if (!b.resolved && b.t > 3.5f) resolve(b)
        if (b.z < START_Z - 0.1f || b.t > 6f) {
            if (!b.resolved) resolve(b)
            b.active = false
        }
    }

    private fun resolve(b: Ball) {
        if (b.resolved) return
        b.resolved = true
        if (!b.scored) {
            if (streak >= 2) popups.add("STREAK OVER", CX, 250f, Color(Pal.GRAY), size = 2f)
            streak = 0
        }
    }

    private fun basket(b: Ball) {
        streak++
        makes++
        val swish = !b.touchedRim
        val mult = multiplier
        val points = (HoopsTuning.BASKET_POINTS + if (swish) HoopsTuning.SWISH_BONUS else 0) * mult
        val rx = sx(hoopX, HOOP_Z)
        val ry = sy(RIM_Y, HOOP_Z)
        addScore(points, rx, ry + 40f, Color(Pal.YELLOW))
        netSwish = 1f
        multSpring.snap(1.6f)
        if (swish) popups.add("SWISH!", CX, 110f, Color(Pal.CYAN), size = 4f)
        when {
            streak >= HoopsTuning.MAX_MULTIPLIER -> {
                popups.add("ON FIRE! x$mult", CX, 290f, Color(Pal.ORANGE), size = 4f, life = 1.2f)
                play(Sfx.CHEER, 0.8f)
                play(Sfx.WIN, 0.8f)
                fx.haptics.jackpot()
                shake.add(0.4f)
                particles.burst(rx, ry, 36, 80f, 300f, intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.RED), 0.8f, 5f, grav = -80f)
            }
            streak >= 2 -> {
                popups.add("x$mult COMBO", CX, 290f, Color(Pal.PINK), size = 3f)
                play(Sfx.SWISH, 1f)
                play(Sfx.COIN, 0.6f, 0.8f + streak * 0.1f)
                fx.haptics.win()
                shake.add(0.22f)
            }
            else -> {
                play(Sfx.SWISH, 1f)
                fx.haptics.hit()
                shake.add(0.12f)
            }
        }
        particles.burst(rx, ry + 20f, 16, 60f, 200f, intArrayOf(Pal.WHITE, Pal.YELLOW), 0.5f, 4f, kind = Particles.SPARKLE)
    }

    // ---------------------------------------------------------------- drawing

    override fun render(scope: DrawScope) {
        with(scope) {
            drawRect(Color(Pal.NIGHT), Offset(-40f, -40f), Size(GAME_W + 80f, GAME_H + 80f))
            drawCage(this)
            drawBoard(this)
            for (b in balls) if (b.active && b.z >= HOOP_Z + 0.25f) drawBall(this, b.x, b.y, b.z, b.spin)
            drawRim(this, back = true)
            for (b in balls) if (b.active && b.z < HOOP_Z + 0.25f && b.z >= HOOP_Z - 0.25f) drawBall(this, b.x, b.y, b.z, b.spin)
            drawNet(this)
            drawRim(this, back = false)
            for (b in balls) if (b.active && b.z < HOOP_Z - 0.25f) drawBall(this, b.x, b.y, b.z, b.spin)

            if (hasReady) {
                val s = readySquash.value
                val x = sx(readyX, START_Z)
                val y = sy(START_Y, START_Z)
                val r = sr(BALL_R, START_Z)
                drawOval(Color.Black, Offset(x - r, y + r * 0.7f), Size(r * 2f, r * 0.6f), alpha = 0.35f)
                drawPixelImageSquash(ballImg, x, y + r, r * 2f / ballImg.width, 2f - s, s)
                if (dragging < 0 && !timeUp) {
                    PixelFont.drawCentered(this, "FLICK ${PixelFont.UP}", CX, 610f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
                }
            }
            // Multiplier display.
            val m = multiplier
            val label = if (streak >= 1) "x$m" else "x1"
            val c = when {
                streak >= HoopsTuning.MAX_MULTIPLIER -> Pal.ORANGE
                streak >= 2 -> Pal.PINK
                else -> Pal.GRAY
            }
            PixelFont.drawCentered(this, label, 318f, 26f, 4f * multSpring.value, Color(c))
            PixelFont.drawCentered(this, "MULT", 318f, 62f, 2f, Color(Pal.LIGHTGRAY))
            PixelFont.drawCentered(this, "$makes/$shots", 42f, 34f, 2f, Color(Pal.LIGHTGRAY))
        }
    }

    private fun drawCage(scope: DrawScope) {
        with(scope) {
            val zNear = 0f
            val zFar = BACK_Z
            // Floor ramp.
            val floor = Path().apply {
                moveTo(sx(-CAGE_HALF_W, zNear), sy(0f, zNear))
                lineTo(sx(CAGE_HALF_W, zNear), sy(0f, zNear))
                lineTo(sx(CAGE_HALF_W, zFar), sy(0f, zFar))
                lineTo(sx(-CAGE_HALF_W, zFar), sy(0f, zFar))
                close()
            }
            drawPath(floor, Color(Pal.WOOD))
            for (i in 1 until 10) {
                val z = zFar * i / 10f
                drawLine(Color(Pal.shade(Pal.WOOD, 0.8f)), Offset(sx(-CAGE_HALF_W, z), sy(0f, z)), Offset(sx(CAGE_HALF_W, z), sy(0f, z)), strokeWidth = 2f)
            }
            // Back wall.
            drawRect(Color(Pal.PLUM), Offset(sx(-CAGE_HALF_W, zFar), sy(4f, zFar)), Size(sx(CAGE_HALF_W, zFar) - sx(-CAGE_HALF_W, zFar), sy(0f, zFar) - sy(4f, zFar)))
            // Side nets as a grid of lines.
            for (k in 0..1) {
                val x = (if (k == 0) -1f else 1f) * CAGE_HALF_W
                for (i in 0..8) {
                    val z = zFar * i / 8f
                    drawLine(Color(Pal.LAVENDER), Offset(sx(x, z), sy(0f, z)), Offset(sx(x, z), sy(4f, z)), strokeWidth = 1.5f, alpha = 0.35f)
                }
                for (j in 0..10) {
                    val y = j * 0.4f
                    drawLine(Color(Pal.LAVENDER), Offset(sx(x, zNear + 0.3f), sy(y, zNear + 0.3f)), Offset(sx(x, zFar), sy(y, zFar)), strokeWidth = 1.5f, alpha = 0.35f)
                }
            }
            // Scoreboard lights on the back wall.
            for (i in 0 until 10) {
                val on = ((time * 6f).toInt() + i) % 3 != 0
                val x = sx(-0.9f + i * 0.2f, zFar)
                drawCircle(Color(if (on) Pal.RED else Pal.DARKRED), 4f, Offset(x, sy(3.7f, zFar)))
            }
        }
    }

    private fun drawBoard(scope: DrawScope) {
        with(scope) {
            val l = sx(hoopX - BOARD_HALF_W, BOARD_Z)
            val r = sx(hoopX + BOARD_HALF_W, BOARD_Z)
            val t = sy(BOARD_TOP, BOARD_Z)
            val b = sy(BOARD_BOTTOM, BOARD_Z)
            // Pole behind.
            drawRect(Color(Pal.GRAY), Offset(sx(hoopX, BOARD_Z) - 5f, b), Size(10f, sy(0f, BACK_Z) - b))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t))
            drawRect(Color(Pal.RED), Offset(l, t), Size(r - l, 5f))
            drawRect(Color(Pal.RED), Offset(l, b - 5f), Size(r - l, 5f))
            drawRect(Color(Pal.RED), Offset(l, t), Size(5f, b - t))
            drawRect(Color(Pal.RED), Offset(r - 5f, t), Size(5f, b - t))
            val il = sx(hoopX - 0.2f, BOARD_Z)
            val ir = sx(hoopX + 0.2f, BOARD_Z)
            val innerTop = sy(2.62f, BOARD_Z)
            val innerBottom = sy(RIM_Y, BOARD_Z)
            drawRect(Color(Pal.RED), Offset(il, innerTop), Size(ir - il, 3f))
            drawRect(Color(Pal.RED), Offset(il, innerTop), Size(3f, innerBottom - innerTop))
            drawRect(Color(Pal.RED), Offset(ir - 3f, innerTop), Size(3f, innerBottom - innerTop))
            // Bracket from board to rim.
            drawRect(Color(Pal.GRAY), Offset(sx(hoopX, BOARD_Z) - 4f, sy(RIM_Y, BOARD_Z) - 2f), Size(8f, sy(RIM_Y, HOOP_Z + RIM_R) - sy(RIM_Y, BOARD_Z) + 4f))
        }
    }

    private fun drawRim(scope: DrawScope, back: Boolean) {
        val n = 28
        val wob = sin(time * 60f) * rimShake * 2f
        for (i in 0 until n) {
            val a0 = i / n.toFloat() * TAU
            val a1 = (i + 1) / n.toFloat() * TAU
            val z0 = HOOP_Z + sin(a0) * RIM_R
            val z1 = HOOP_Z + sin(a1) * RIM_R
            val isBack = (z0 + z1) / 2f >= HOOP_Z
            if (isBack != back) continue
            val x0 = hoopX + cos(a0) * RIM_R
            val x1 = hoopX + cos(a1) * RIM_R
            scope.drawLine(
                Color(if (back) Pal.shade(Pal.ORANGE, 0.75f) else Pal.ORANGE),
                Offset(sx(x0, z0), sy(RIM_Y, z0) + wob),
                Offset(sx(x1, z1), sy(RIM_Y, z1) + wob),
                strokeWidth = if (back) 4f else 5f,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun drawNet(scope: DrawScope) {
        val n = 10
        val drop = 0.36f + netSwish * 0.1f
        val bottomR = RIM_R * (0.6f - netSwish * 0.15f)
        val sway = sin(time * 20f) * netSwish * 0.03f
        for (i in 0 until n) {
            val a = i / n.toFloat() * TAU
            val topX = hoopX + cos(a) * RIM_R
            val topZ = HOOP_Z + sin(a) * RIM_R
            val a2 = a + 0.35f
            val botX = hoopX + cos(a2) * bottomR + sway
            val botZ = HOOP_Z + sin(a2) * bottomR
            val a3 = a - 0.35f
            val botX2 = hoopX + cos(a3) * bottomR + sway
            val botZ2 = HOOP_Z + sin(a3) * bottomR
            val alpha = if (topZ < HOOP_Z) 0.95f else 0.5f
            scope.drawLine(Color.White, Offset(sx(topX, topZ), sy(RIM_Y, topZ)), Offset(sx(botX, botZ), sy(RIM_Y - drop, botZ)), strokeWidth = 2f, alpha = alpha)
            scope.drawLine(Color.White, Offset(sx(topX, topZ), sy(RIM_Y, topZ)), Offset(sx(botX2, botZ2), sy(RIM_Y - drop, botZ2)), strokeWidth = 2f, alpha = alpha)
        }
    }

    private fun drawBall(scope: DrawScope, x: Float, y: Float, z: Float, spin: Float) {
        val px = sx(x, z)
        val py = sy(y, z)
        val r = sr(BALL_R, z)
        // Shadow on the floor.
        val fy = sy(0f, z)
        val fr = r * (1f / (1f + y * 0.3f))
        scope.drawOval(Color.Black, Offset(px - fr, fy - fr * 0.3f), Size(fr * 2f, fr * 0.6f), alpha = 0.3f)
        scope.rotate(spin * 57.3f, Offset(px, py)) {
            drawPixelImage(ballImg, px - r, py - r, r * 2f / ballImg.width)
        }
    }

    private fun buildBall(): ImageBitmap {
        val c = PixelCanvas(18, 18)
        c.disc(9f, 9f, 8.6f, Pal.ORANGE)
        c.disc(7f, 6f, 3f, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.45f))
        c.vline(9, 1, 16, Pal.DARKBROWN)
        c.hline(1, 16, 9, Pal.DARKBROWN)
        for (y in 2..15) {
            val dx = (sqrt(49f - (y - 9f) * (y - 9f)).coerceAtLeast(0f) * 0.55f).toInt()
            c.set(4 + (3 - dx).coerceAtLeast(0), y, Pal.DARKBROWN)
            c.set(13 - (3 - dx).coerceAtLeast(0), y, Pal.DARKBROWN)
        }
        c.set(6, 4, Pal.WHITE)
        c.outline(Pal.DARKBROWN)
        return c.toImageBitmap()
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: PixelPainter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.PLUM))
        val bx = w / 2f + sin(time * 1.1f) * (w / 5f)
        p.fill(bx - 5f, 1f, 10f, 5f, Color.White)
        p.frame(bx - 5f, 1f, 10f, 5f, Color(Pal.RED))
        p.fill(bx - 3f, 6f, 6f, 1f, Color(Pal.ORANGE))
        p.fill(bx - 2f, 7f, 1f, 2f, Color.White)
        p.fill(bx + 1f, 7f, 1f, 2f, Color.White)
        val t = (time % 1.6f) / 1.6f
        val ballX = lerp(w / 2f, bx, t)
        val ballY = lerp(h - 2f, 5f, t) - sin(t * Math.PI.toFloat()) * 3f
        p.disc(ballX, ballY, 1.6f, Color(Pal.ORANGE))
        p.fill(0, h - 1, w, 1, Color(Pal.WOOD))
    }
}
