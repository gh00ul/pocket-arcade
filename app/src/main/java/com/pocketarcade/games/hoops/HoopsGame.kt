package com.pocketarcade.games.hoops

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.FlickTracker
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.Vec2
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.atan
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
    override val look = CabinetLook(body = Pal.RED, trim = Pal.ORANGE, glow = Pal.ORANGE, shape = CabinetShape.HOOPS)
    override val roundSeconds = HoopsTuning.ROUND_SECONDS

    private companion object {
        const val G = HoopsGeo.G
        const val CAM_Y = HoopsGeo.CAM_Y
        const val CAM_Z = HoopsGeo.CAM_Z
        const val F = HoopsGeo.F
        const val CX = HoopsGeo.CX
        const val HORIZON = HoopsGeo.HORIZON
        const val BALL_R = HoopsGeo.BALL_R
        const val RIM_R = HoopsGeo.RIM_R
        const val RIM_Y = HoopsGeo.RIM_Y
        const val HOOP_Z = HoopsGeo.HOOP_Z
        const val BOARD_Z = HoopsGeo.BOARD_Z
        const val BOARD_HALF_W = HoopsGeo.BOARD_HALF_W
        const val BOARD_BOTTOM = HoopsGeo.BOARD_BOTTOM
        const val BOARD_TOP = HoopsGeo.BOARD_TOP
        const val START_Y = HoopsGeo.START_Y
        const val START_Z = HoopsGeo.START_Z
        const val CAGE_HALF_W = HoopsGeo.CAGE_HALF_W
        const val BACK_Z = HoopsGeo.BACK_Z
        /** World units (centimetres) per simulation metre. */
        const val S = HoopsGeo.S

        /** Colours of the sparks a rim hit throws. */
        val SPARK_COLORS = intArrayOf(Pal.WHITE, Pal.ORANGE, Pal.YELLOW)
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

    /** All the presentation state (glows, trails, shockwaves); the simulation never reads it. */
    private val scene = HoopsScene()

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
        scene.reset()
    }

    override fun ticketsFor(score: Int): Int = HoopsTuning.BASE_TICKETS + score / HoopsTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = balls.none { it.active && !it.resolved }

    override fun onTimeUp() = cancelInput()

    /** Drops the ball being lined up; it drifts back to the middle. */
    override fun cancelInput() {
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
        scene.step(dt)

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
        // Balls in the air leave a glowing trail (visual only).
        for (i in balls.indices) {
            val b = balls[i]
            if (b.active && !b.onFloor) scene.trail(i, b.x * S, b.y * S, -b.z * S)
        }

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
                        scene.rimHit(px * S, RIM_Y * S, -pz * S)
                        particles.burst(sx(px, pz), sy(RIM_Y, pz), 7, 50f, 170f, SPARK_COLORS, 0.35f, 3f, kind = Particles.SPARKLE)
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
            scene.boardHit(hoopX, (b.x - hoopX) * S, b.y * S)
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
                if (abs(b.vy) > 0.6f) {
                    play(Sfx.BOUNCE, 0.5f)
                    scene.floorHit(b.x * S, -b.z * S, clamp01(abs(b.vy) / 3f))
                }
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
        scene.made(hoopX, swish, streak)
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

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The alley in 3D. The simulation is already 3D (metres, z away from the player); the
     * world uses centimetres with z towards the viewer, and the camera matches the
     * simulation's own projection exactly, so [sx]/[sy] still place effects on screen.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        val fov = 2f * atan(GAME_H / 2f / F) * (180f / Math.PI.toFloat())
        look(0f, CAM_Y * S, -CAM_Z * S, 0f, CAM_Y * S, -CAM_Z * S - 1000f, fovDeg = fov, centerYFrac = HORIZON / GAME_H)
    }

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        scene.light(r, hoopX, streak, time)
        r.gradient(0xFF06030C.toInt(), Pal.NIGHT)
        scene.updateReadouts(makes, shots, streak)
        scene.drawBackdrop(r, time, streak, multSpring.value)
        scene.drawPools(r, hoopX, streak)
        scene.drawBoard(r, hoopX, time, streak)
        val wob = sin(time * 60f) * rimShake * 1.5f
        val rimY = RIM_Y * S + wob
        scene.rimXf.set(hoopX * S, rimY, -HOOP_Z * S)
        scene.rimModel.draw(r, xf = scene.rimXf)
        // A ball passing down through the net makes it bulge round it.
        var netBallY = -1f
        for (b in balls) {
            if (!b.active) continue
            drawBall(r, b.x, b.y, b.z, b.spin, 1f, 1f)
            if (b.scored && b.y < RIM_Y + BALL_R && b.y > RIM_Y - 0.55f) netBallY = b.y * S
        }
        if (hasReady) {
            val s = readySquash.value
            drawBall(r, readyX, START_Y, START_Z, 0f, 2f - s, s)
        }
        scene.drawNet(r, hoopX, rimY, time, netSwish, netBallY)
        scene.drawEffects(r, hoopX, rimY, time, streak, BALL_R * 2f * S)
        scene.drawCage(r)
        stage.present()

        if (hasReady && dragging < 0 && !timeUp) {
            ArcadeFont.drawCentered(scope, "FLICK ${ArcadeFont.UP}", CX, 610f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
        }
    }

    private val ballModel by lazy { HoopsArt.ball(BALL_R * S) }
    private val ballXf = Xform()

    private fun drawBall(r: Renderer3D, x: Float, y: Float, z: Float, spin: Float, sqx: Float, sqy: Float) {
        val wx = x * S
        val wz = -z * S
        val d = BALL_R * 2f * S
        // Contact shadow on the court, softer the higher the ball.
        val a = 0.45f / (1f + y * 0.6f)
        r.flat(wx, wz, 0.5f, d * 1.1f, d * 0.9f, TexKit.shadow.full, blend = Blend.ALPHA, alpha = a)
        ballXf.set(wx, y * S + (sqy - 1f) * d / 2f, wz, pitch = -spin).stretch(sqx, sqy, sqx)
        ballModel.draw(r, xf = ballXf)
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** Balls shot this round. */
    internal val botShots: Int get() = shots

    // ---------------------------------------------------------------- attract mode

    /**
     * A night court in miniature: crowd flashes, a lit backboard and a neon title, and a ball
     * that arcs up, swishes through the net (which kicks, with a glint and a "+13") and drops
     * away, every few seconds from a different spot. [w] × [h] is the cabinet's small screen.
     */
    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cx = wf / 2f
        // Night sky and arena, in bands.
        val bands = 6
        val skyH = hf * 0.66f
        for (i in 0 until bands) {
            p.fill(0f, i * skyH / bands, wf, skyH / bands + 0.3f, Color(Pal.mix(0xFF07040F.toInt(), 0xFF2A1750.toInt(), i / (bands - 1f))))
        }
        // The crowd, two rows of heads bobbing, with camera flashes.
        for (row in 0..1) {
            val y = hf * (0.52f + row * 0.09f)
            val shade = if (row == 0) 0xFF150C2C else 0xFF23164A
            for (i in 0 until 13) {
                val x = i * (wf / 12f) + row * 0.9f - 0.4f
                val bob = sin(time * 2.6f + i * 1.7f + row) * 0.25f
                p.disc(x, y + bob, 1.05f, Color(shade.toInt()))
                p.fill(x - 1.1f, y + 0.9f + bob, 2.2f, hf, Color(shade.toInt()))
            }
        }
        for (i in 0 until 3) {
            val u = (time * 1.3f + i * 0.7f) % 2.4f
            if (u < 0.12f) p.disc(1.5f + hash01(i, (time * 1.3f + i * 0.7f).toInt()) * (wf - 3f), hf * (0.5f + i * 0.04f), 0.55f, Color.White, 1f - u / 0.12f)
        }
        // The court and its key.
        val floorY = hf * 0.72f
        p.fill(0f, floorY, wf, hf - floorY, Color(0xFF6B4524.toInt()))
        p.fill(0f, floorY, wf, 0.5f, Color(0xFF2A1A0C.toInt()))
        p.fill(cx - 3.2f, floorY + 0.6f, 6.4f, hf - floorY, Color(0xFF8E2A38.toInt()), 0.6f)
        p.fill(cx - 3.2f, floorY + 0.6f, 0.4f, hf - floorY, Color(0xFFDCCFAE.toInt()))
        p.fill(cx + 2.8f, floorY + 0.6f, 0.4f, hf - floorY, Color(0xFFDCCFAE.toInt()))

        // Backboard, rim and net.
        val cycle = 3.4f
        val n = (time / cycle).toInt()
        val t = time % cycle
        val boardTop = hf * 0.06f
        p.fill(cx - 4.4f, boardTop, 8.8f, 5.2f, Color(0xFF16224A.toInt()))
        p.frame(cx - 4.4f, boardTop, 8.8f, 5.2f, Color(Pal.RED))
        p.frame(cx - 1.8f, boardTop + 1.8f, 3.6f, 2.6f, Color(Pal.RED), 0.8f)
        val rimY = boardTop + 5.2f
        val swish = if (t in 1.3f..1.9f) 1f - (t - 1.3f) / 0.6f else 0f
        // Net: five strands narrowing, kicking sideways after a make.
        for (k in -2..2) {
            val top = cx + k * 0.75f
            val foot = cx + k * 0.42f + sin(time * 22f) * swish * 0.5f
            var px = top
            var py = rimY
            for (seg in 1..3) {
                val u = seg / 3f
                val nx = lerp(top, foot, u)
                val ny = rimY + 2.6f * u * (1f + swish * 0.2f)
                p.fill(minOf(px, nx), py, maxOf(abs(nx - px), 0.35f), maxOf(ny - py, 0.35f), Color(0xFFE4E0F0.toInt()), 0.8f)
                px = nx; py = ny
            }
        }
        p.fill(cx - 2.5f, rimY - 0.2f, 5f, 0.6f, Color(Pal.ORANGE))
        p.disc(cx - 2.5f, rimY + 0.1f, 0.55f, Color(Pal.ORANGE))
        p.disc(cx + 2.5f, rimY + 0.1f, 0.55f, Color(Pal.ORANGE))
        if (swish > 0f) {
            // The rim flares and a ring runs out from it.
            p.disc(cx, rimY + 0.4f, 1.6f + (1f - swish) * 4f, Color(Pal.YELLOW), swish * 0.25f)
            p.frame(cx - 2.5f - (1f - swish) * 3f, rimY - 0.7f, 5f + (1f - swish) * 6f, 1.4f, Color(Pal.WHITE), swish * 0.7f)
        }

        // The shot: an arc from a different spot each time, a trail behind it, then down through the net.
        val startX = cx + (hash01(n, 7) - 0.5f) * 12f
        val flight = 1.05f
        val u = (t - 0.35f) / flight
        if (u in 0f..1f) {
            for (k in 4 downTo 0) {
                val uu = (u - k * 0.035f).coerceAtLeast(0f)
                p.disc(shotX(startX, cx, uu), shotY(hf, rimY, uu), 1.35f * (1f - k * 0.13f), Color(Pal.ORANGE), 0.9f - k * 0.17f)
            }
            p.disc(shotX(startX, cx, u) - 0.4f, shotY(hf, rimY, u) - 0.4f, 0.5f, Color.White, 0.6f)
        } else if (t in 0.35f + flight..1.9f + flight) {
            val fall = (t - 0.35f - flight) / 0.55f
            val by = rimY + 0.3f + fall * fall * (hf - rimY)
            p.disc(cx, by, 1.35f, Color(Pal.ORANGE))
        }
        if (t in 1.5f..2.5f) {
            val rise = (t - 1.5f) / 1.0f
            p.textCentered("+13", cx, boardTop - 0.3f - rise * 2f, Color(Pal.YELLOW), tiny = true, alpha = 1f - rise, size = 0.6f)
        }

        // Neon title, pulsing.
        val pulse = 0.75f + 0.25f * sin(time * 4f)
        p.textCentered("HOOP SHOT", cx, hf - 3.6f, Color(Pal.ORANGE), tiny = true, alpha = 0.5f * pulse, size = 0.66f)
        p.textCentered("HOOP SHOT", cx, hf - 3.6f, Color(Pal.CREAM), tiny = true, alpha = pulse, size = 0.62f)
    }

    /** The shot's path across the little screen: [u] runs 0 to 1 from the shooter's spot to the rim. */
    private fun shotX(startX: Float, cx: Float, u: Float): Float = lerp(startX, cx, u)

    private fun shotY(h: Float, rimY: Float, u: Float): Float = lerp(h - 2f, rimY - 0.4f, u) - sin(u * Math.PI.toFloat()) * (h * 0.36f)
}
