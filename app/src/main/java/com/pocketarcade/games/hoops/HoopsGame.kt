package com.pocketarcade.games.hoops

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.FlickTracker
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.Vec2
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
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
        /** World units (centimetres) per simulation metre. */
        const val S = 100f
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

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The alley in 3D. The simulation is already 3D (metres, z away from the player); the
     * world uses centimetres with z towards the viewer, and the camera matches the
     * simulation's own projection exactly, so [sx]/[sy] still place effects on screen.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt(), "hoops").apply {
        val fov = 2f * atan(GAME_H / 2f / F) * (180f / Math.PI.toFloat())
        look(0f, CAM_Y * S, -CAM_Z * S, 0f, CAM_Y * S, -CAM_Z * S - 1000f, fovDeg = fov, centerYFrac = HORIZON / GAME_H)
    }

    private val court: Model by lazy {
        val b = ModelBuilder()
        val w = CAGE_HALF_W * S
        val back = -BACK_Z * S
        b.quad(-w, 0f, back, w, 0f, back, w, 0f, 60f, -w, 0f, 60f, HoopsArt.floor.full, 0f, 1f, 0f)
        b.quad(-w - 60f, 420f, back, w + 60f, 420f, back, w + 60f, 0f, back, -w - 60f, 0f, back, HoopsArt.backWall.full, 0f, 0f, 1f)
        // Outside the cage: dark carpet and cabinet sides.
        val side = HoopsArt.cabinet.full
        b.box(-w - 60f, 0f, back, -w - 4f, 60f, 60f, BoxFaces(top = side, right = side))
        b.box(w + 4f, 0f, back, w + 60f, 60f, 60f, BoxFaces(top = side, left = side))
        // Cage posts.
        val post = HoopsArt.post.full
        for (sx in floatArrayOf(-w, w)) for (z in floatArrayOf(back + 4f, -100f)) {
            b.box(sx - 3f, 0f, z - 3f, sx + 3f, 420f, z + 3f, BoxFaces(front = post, left = post, right = post, top = post))
        }
        b.box(-w, 414f, -104f, w, 420f, -96f, BoxFaces(front = post, top = post))
        b.build()
    }

    /** Backboard, bracket and pole, centred on the hoop (moved each frame). */
    private val board: Model by lazy {
        val b = ModelBuilder()
        val hw = BOARD_HALF_W * S
        val bz = -BOARD_Z * S
        val top = BOARD_TOP * S
        val bottom = BOARD_BOTTOM * S
        val edge = HoopsArt.boardEdge.full
        b.quad(-hw, top, bz, hw, top, bz, hw, bottom, bz, -hw, bottom, bz, HoopsArt.board.full, 0f, 0f, 1f)
        b.box(-hw, bottom, bz - 5f, hw, top, bz, BoxFaces(top = edge, left = edge, right = edge))
        val pole = HoopsArt.post.full
        b.box(-6f, 0f, bz - 30f, 6f, top - 20f, bz - 18f, BoxFaces(front = pole, left = pole, right = pole))
        b.box(-6f, top - 40f, bz - 30f, 6f, top - 28f, bz - 5f, BoxFaces(front = pole, top = pole, left = pole, right = pole))
        // Bracket from the board to the rim.
        val rimY = RIM_Y * S
        b.box(-4f, rimY - 6f, -(HOOP_Z + RIM_R) * S, 4f, rimY, bz, BoxFaces(top = pole, left = pole, right = pole))
        b.build()
    }

    private val rim: Model by lazy {
        val b = ModelBuilder()
        val r = RIM_R * S
        val t = HoopsArt.rim.full
        b.cylinder(0f, 0f, -1.6f, 1.6f, r + 1.2f, 18, t)
        b.cylinder(0f, 0f, -1.6f, 1.6f, r - 1.2f, 18, t, inward = true)
        b.annulus(0f, 0f, 1.6f, r - 1.2f, r + 1.2f, 18, t)
        b.build()
    }

    private val boardXf = Xform()
    private val rimXf = Xform()
    private val gymLight = PointLight(0f, 380f, -200f, 1f, 0.95f, 0.85f, 520f, 1f)
    private val fireLight = PointLight(0f, RIM_Y * S, -HOOP_Z * S, 1f, 0.5f, 0.15f, 220f, 0f)

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.55f; l.ambG = 0.52f; l.ambB = 0.62f
        l.setDirection(0f, 1f, 0.6f)
        l.dirR = 0.35f; l.dirG = 0.33f; l.dirB = 0.3f
        l.points.clear()
        l.points += gymLight
        if (streak >= HoopsTuning.MAX_MULTIPLIER) {
            fireLight.x = hoopX * S
            fireLight.intensity = 1.2f + 0.4f * sin(time * 14f)
            l.points += fireLight
        }
        r.gradient(0xFF06030C.toInt(), Pal.NIGHT)
        court.draw(r)
        boardXf.set(hoopX * S, 0f, 0f)
        board.draw(r, xf = boardXf)
        val wob = sin(time * 60f) * rimShake * 1.5f
        rimXf.set(hoopX * S, RIM_Y * S + wob, -HOOP_Z * S)
        rim.draw(r, xf = rimXf)
        drawBackLights(r)
        for (b in balls) if (b.active) drawBall(r, b.x, b.y, b.z, b.spin, 1f, 1f)
        if (hasReady) {
            val s = readySquash.value
            drawBall(r, readyX, START_Y, START_Z, 0f, 2f - s, s)
        }
        drawNet(r, wob)
        drawSideNets(r)
        stage.present(scope)

        if (hasReady && dragging < 0 && !timeUp) {
            PixelFont.drawCentered(scope, "FLICK ${PixelFont.UP}", CX, 610f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
        }
        // Multiplier display.
        val m = multiplier
        val label = if (streak >= 1) "x$m" else "x1"
        val c = when {
            streak >= HoopsTuning.MAX_MULTIPLIER -> Pal.ORANGE
            streak >= 2 -> Pal.PINK
            else -> Pal.GRAY
        }
        PixelFont.drawCentered(scope, label, 318f, 26f, 4f * multSpring.value, Color(c))
        PixelFont.drawCentered(scope, "MULT", 318f, 62f, 2f, Color(Pal.LIGHTGRAY))
        PixelFont.drawCentered(scope, "$makes/$shots", 42f, 34f, 2f, Color(Pal.LIGHTGRAY))
    }

    private fun drawBackLights(r: Renderer3D) {
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        val z = -BACK_Z * S + 1f
        for (i in 0 until 10) {
            val on = ((time * 6f).toInt() + i) % 3 != 0
            val x = (-0.9f + i * 0.2f) * S
            r.sprite(x, 370f, z, 8f, 8f, white, emissive = 1.2f, tint = if (on) Pal.RED else Pal.DARKRED)
            if (on) r.sprite(x, 370f, z + 1f, 30f, 30f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.45f, tint = Pal.RED)
        }
    }

    private fun drawNet(r: Renderer3D, wob: Float) {
        val tex = TexKit.white.full
        val n = 12
        val rimR = RIM_R * S
        val drop = (0.36f + netSwish * 0.1f) * S
        val bottomR = rimR * (0.6f - netSwish * 0.15f)
        val sway = sin(time * 20f) * netSwish * 3f
        val cx = hoopX * S
        val cz = -HOOP_Z * S
        val top = RIM_Y * S + wob - 1f
        for (i in 0 until n) {
            val a = i / n.toFloat() * TAU
            val tx = cx + cos(a) * rimR
            val tz = cz + sin(a) * rimR
            for (d in intArrayOf(-1, 1)) {
                val a2 = a + d * 0.3f
                val bx = cx + cos(a2) * bottomR + sway
                val bz = cz + sin(a2) * bottomR
                r.beam(tx, top, tz, bx, top - drop, bz, 1.4f, tex, blend = Blend.ALPHA, alpha = 0.85f)
            }
        }
    }

    private fun drawSideNets(r: Renderer3D) {
        val w = CAGE_HALF_W * S
        val net = HoopsArt.net.full
        for (x in floatArrayOf(-w, w)) {
            r.quad(x, 420f, -BACK_Z * S, x, 420f, -100f, x, 0f, -100f, x, 0f, -BACK_Z * S, net, if (x < 0f) 1f else -1f, 0f, 0f, blend = Blend.ALPHA, cull = false)
        }
        r.quad(-w, 420f, -100f, w, 420f, -100f, w, 420f, -BACK_Z * S, -w, 420f, -BACK_Z * S, net, 0f, -1f, 0f, blend = Blend.ALPHA, cull = false)
    }

    private fun drawBall(r: Renderer3D, x: Float, y: Float, z: Float, spin: Float, sqx: Float, sqy: Float) {
        val wx = x * S
        val wz = -z * S
        val d = BALL_R * 2f * S
        // Contact shadow on the court, softer the higher the ball.
        val a = 0.45f / (1f + y * 0.6f)
        r.flat(wx, wz, 0.5f, d * 1.1f, d * 0.9f, TexKit.shadow.full, blend = Blend.ALPHA, alpha = a)
        r.sprite(wx, y * S + (sqy - 1f) * d / 2f, wz, d * sqx, d * sqy, HoopsArt.ball.full, roll = -spin, depthBias = 1.02f)
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
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
