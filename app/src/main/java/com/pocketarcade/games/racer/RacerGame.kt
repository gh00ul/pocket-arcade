package com.pocketarcade.games.racer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.approach
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.hash01
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
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Difficulty and payout knobs for the racer. */
object RacerTuning {
    const val ROUND_SECONDS = 45f
    const val MAX_SPEED = 1100f
    const val ACCEL = 380f
    const val OFFROAD_SPEED = 380f
    /** How hard curves push the car towards the outside (world units/s at top speed per unit of curve). */
    const val CENTRIFUGAL = 62f
    const val STEER_SPEED = 620f
    const val CRASH_SPEED_KEEP = 0.3f
    const val CRASH_GRACE = 1.2f
    const val TRAFFIC = 7
    const val TRAFFIC_SPEED_MIN = 360f
    const val TRAFFIC_SPEED_MAX = 620f
    /** One point per this many world units driven. */
    const val UNITS_PER_POINT = 50f
    const val TOKEN_POINTS = 20
    const val NEAR_MISS_POINTS = 20
    /** Distance between lines of tokens (a random spot in this range). */
    const val TOKEN_GAP_MIN = 2000f
    const val TOKEN_GAP_MAX = 3200f
    const val POINTS_PER_TICKET = 70
    const val BASE_TICKETS = 1
}

class RacerGame : BaseMiniGame() {
    override val id = "racer"
    override val title = "TURBO RACER"
    override val marquee = "RACER"
    override val instructions = listOf(
        "DRAG LEFT AND RIGHT TO STEER",
        "DODGE THE TRAFFIC",
        "GRAB TOKENS FOR BONUS",
        "SKIM PAST CARS FOR MORE",
        "STAY ON THE ROAD!",
    )
    override val look = CabinetLook(body = Pal.DARKRED, trim = Pal.WHITE, glow = Pal.RED)
    override val roundSeconds = RacerTuning.ROUND_SECONDS

    private companion object {
        const val SEG = 40f
        const val VIEW = 72
        /** Segments drawn behind the car, down to the bottom of the screen. */
        const val BEHIND = 8
        const val MAX_SEGS = 4096
        const val ROAD_HALF = 150f
        const val LANE = 100f
        const val CAR_HALF_W = 22f
        const val CAR_LEN = 80f
        const val GROUND_HALF = 2400f
        const val CAM_BACK = 250f
        const val CAM_UP = 104f
    }

    private class Car {
        var d = 0f
        var x = 0f
        var v = 0f
        var color = 0
        var passed = false
    }

    private class Token {
        var d = 0f
        var x = 0f
        var active = false
    }

    // Track: per segment, how much the heading bends (curve) and the slope (hill).
    private val curve = FloatArray(MAX_SEGS)
    private val slope = FloatArray(MAX_SEGS)
    private var generated = 0

    private var dist = 0f
    private var speed = 0f
    private var px = 0f
    private var steerTarget = 0f
    private var dragging = -1L
    private var dragStartX = 0f
    private var dragStartTarget = 0f
    private var crashT = 0f
    private var graceT = 0f
    private var tilt = 0f
    private var pointsCarry = 0f
    private var tokensGot = 0
    private val traffic = Array(RacerTuning.TRAFFIC) { Car() }
    private val tokens = Array(24) { Token() }
    private var nextTokenD = 0f
    private var engineT = 0f

    override fun reset() {
        generated = 0
        generate(VIEW + 400)
        dist = 0f
        speed = 0f
        px = 0f
        steerTarget = 0f
        dragging = -1L
        crashT = 0f
        graceT = 0f
        tilt = 0f
        pointsCarry = 0f
        tokensGot = 0
        for ((i, c) in traffic.withIndex()) spawnCar(c, 900f + i * 420f)
        tokens.forEach { it.active = false }
        nextTokenD = 700f
        engineT = 0f
    }

    /** Appends track sections: straights, bends and hills, eased in and out. */
    private fun generate(upTo: Int) {
        while (generated < upTo.coerceAtMost(MAX_SEGS)) {
            val len = 24 + rng.nextInt(40)
            val bend = when (rng.nextInt(5)) {
                0 -> 0f
                1, 2 -> rng.range(0.8f, 2.2f)
                else -> -rng.range(0.8f, 2.2f)
            } * if (generated < 40) 0f else 1f
            val hill = if (rng.nextInt(3) == 0) 0f else rng.range(-7f, 7f)
            for (k in 0 until len) {
                if (generated >= MAX_SEGS) break
                val e = sin(k / len.toFloat() * PI.toFloat())
                curve[generated] = bend * e
                slope[generated] = hill * sin(k / len.toFloat() * 2f * PI.toFloat())
                generated++
            }
        }
    }

    private fun spawnCar(c: Car, d: Float) {
        c.d = d
        c.x = (rng.nextInt(3) - 1) * LANE
        c.v = rng.range(RacerTuning.TRAFFIC_SPEED_MIN, RacerTuning.TRAFFIC_SPEED_MAX)
        c.color = CAR_COLORS[rng.nextInt(CAR_COLORS.size)]
        c.passed = false
    }

    override fun ticketsFor(score: Int): Int = RacerTuning.BASE_TICKETS + score / RacerTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = speed < 60f

    override fun onTimeUp() {
        dragging = -1L
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> if (dragging < 0) {
                dragging = id
                dragStartX = x
                dragStartTarget = steerTarget
            }
            TouchType.MOVE -> if (id == dragging) {
                // Relative steering: slide a finger anywhere, the car follows its sideways motion.
                steerTarget = (dragStartTarget + (x - dragStartX) * 1.6f).coerceIn(-ROAD_HALF - 60f, ROAD_HALF + 60f)
            }
            TouchType.UP -> if (id == dragging) dragging = -1L
        }
    }

    override fun step(dt: Float) {
        crashT = (crashT - dt).coerceAtLeast(0f)
        graceT = (graceT - dt).coerceAtLeast(0f)
        val offroad = abs(px) > ROAD_HALF
        val top = when {
            timeUp -> 0f
            offroad -> RacerTuning.OFFROAD_SPEED
            else -> RacerTuning.MAX_SPEED
        }
        speed = if (speed < top) {
            (speed + RacerTuning.ACCEL * dt * (if (crashT > 0f) 0.5f else 1f)).coerceAtMost(top)
        } else {
            approach(speed, top, (if (timeUp) 900f else 700f) * dt)
        }
        val seg = (dist / SEG).toInt()
        val bend = curve[seg.coerceIn(0, MAX_SEGS - 1)]
        // Steering, and the curve pulling the car outwards.
        val prevPx = px
        px = approach(px, steerTarget, RacerTuning.STEER_SPEED * dt)
        val push = bend * (speed / RacerTuning.MAX_SPEED) * RacerTuning.CENTRIFUGAL * dt
        px -= push
        steerTarget -= push
        px = px.coerceIn(-ROAD_HALF - 110f, ROAD_HALF + 110f)
        steerTarget = steerTarget.coerceIn(-ROAD_HALF - 110f, ROAD_HALF + 110f)
        tilt = damp(tilt, ((px - prevPx) / dt / 900f).coerceIn(-0.25f, 0.25f), 8f, dt)

        val before = dist
        dist += speed * dt
        if (seg + VIEW + 20 > generated) generate(seg + VIEW + 400)
        if (!timeUp) {
            pointsCarry += (dist - before) / RacerTuning.UNITS_PER_POINT
            val whole = pointsCarry.toInt()
            if (whole > 0) {
                pointsCarry -= whole
                score += whole
            }
        }
        if (offroad && speed > 200f && rng.nextFloat() < 0.4f) {
            stage.toField(px, 0f, 0f, pt)
            particles.spawn(pt[0] + rng.range(-20f, 20f), pt[1], rng.range(-40f, 40f), rng.range(-80f, -20f), 0.4f, 4f, Pal.shade(Pal.PURPLE, 0.8f))
        }

        stepTraffic(dt)
        stepTokens()
        engineT -= dt
        if (engineT <= 0f && speed > 50f && !timeUp) {
            engineT = 0.35f
            play(Sfx.CLAW_MOTOR, 0.25f, 0.6f + speed / RacerTuning.MAX_SPEED * 0.9f)
        }
    }

    private fun stepTraffic(dt: Float) {
        for (c in traffic) {
            c.d += c.v * dt
            val dz = c.d - dist
            if (dz < -300f) {
                spawnCar(c, dist + rng.range(2400f, 3200f))
                continue
            }
            if (timeUp) continue
            val dx = abs(c.x - px)
            if (abs(dz) < CAR_LEN * 0.9f && dx < CAR_HALF_W * 2f - 4f && graceT <= 0f) {
                crash(c)
            } else if (!c.passed && dz < -CAR_LEN) {
                c.passed = true
                if (dx < CAR_HALF_W * 2f + 26f && graceT <= 0f) {
                    addScore(RacerTuning.NEAR_MISS_POINTS, GAME_W / 2f, 360f, Color(Pal.CYAN), "CLOSE! +${RacerTuning.NEAR_MISS_POINTS}")
                    play(Sfx.WHOOSH, 0.6f, 1.3f)
                }
            }
        }
    }

    private fun crash(c: Car) {
        speed *= RacerTuning.CRASH_SPEED_KEEP
        crashT = 0.8f
        graceT = RacerTuning.CRASH_GRACE
        c.v += 250f
        c.d = dist + CAR_LEN * 1.2f
        play(Sfx.BOMB, 0.8f, 1.2f)
        fx.haptics.heavy()
        shake.add(0.6f)
        flash.trigger(0.5f)
        stage.toField(px, 20f, -40f, pt)
        particles.burst(pt[0], pt[1], 30, 80f, 300f, intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.WHITE), 0.6f, 5f)
        popups.add("CRASH!", GAME_W / 2f, 300f, Color(Pal.RED), size = 4f)
    }

    private fun stepTokens() {
        if (!timeUp && dist + 2600f > nextTokenD) {
            // A short line of tokens down one lane.
            val lane = (rng.nextInt(3) - 1) * LANE
            val n = 3 + rng.nextInt(3)
            var placed = 0
            for (t in tokens) {
                if (placed >= n) break
                if (t.active) continue
                t.active = true
                t.d = nextTokenD + placed * 70f
                t.x = lane
                placed++
            }
            nextTokenD += rng.range(RacerTuning.TOKEN_GAP_MIN, RacerTuning.TOKEN_GAP_MAX)
        }
        for (t in tokens) {
            if (!t.active) continue
            val dz = t.d - dist
            if (dz < -120f) {
                t.active = false
            } else if (abs(dz) < 40f && abs(t.x - px) < 40f && !timeUp) {
                t.active = false
                tokensGot++
                addScore(RacerTuning.TOKEN_POINTS, GAME_W / 2f + 40f, 420f, Color(Pal.GOLD))
                play(Sfx.COIN, 0.8f, 1f + (tokensGot % 5) * 0.1f)
                fx.haptics.tick()
                stage.toField(px, 20f, -20f, pt)
                particles.burst(pt[0], pt[1] - 20f, 10, 50f, 180f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.5f, 4f, kind = Particles.SPARKLE)
            }
        }
    }

    // ---------------------------------------------------------------- 3D presentation

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt(), "racer")
    private val pt = FloatArray(3)
    private val xf = Xform()
    // Segment start points, indexed from BEHIND segments behind the car to VIEW ahead.
    private val segX = FloatArray(VIEW + BEHIND + 1)
    private val segY = FloatArray(VIEW + BEHIND + 1)
    private val segZ = FloatArray(VIEW + BEHIND + 1)
    private var carY = 0f
    private var camY = CAM_UP

    init {
        stage.look(0f, CAM_UP, CAM_BACK, 0f, 20f, -420f, fovDeg = 56f, centerYFrac = 0.44f)
    }

    private val carModels = HashMap<Int, Model>()
    private fun carModel(color: Int): Model = carModels.getOrPut(color) {
        val side = RacerArt.bodySide(color).full
        val topT = RacerArt.bodyTop(color).full
        val rear = RacerArt.rear(color).full
        val glass = RacerArt.glass.full
        val tyre = RacerArt.tyre.full
        val b = ModelBuilder()
        b.box(-CAR_HALF_W, 5f, -40f, CAR_HALF_W, 17f, 40f, BoxFaces(front = rear, back = side, top = topT, left = side, right = side))
        b.box(-16f, 17f, -12f, 16f, 28f, 18f, BoxFaces(front = glass, back = glass, top = topT, left = glass, right = glass))
        b.box(-CAR_HALF_W - 1f, 23f, 32f, CAR_HALF_W + 1f, 26f, 40f, BoxFaces(front = side, top = topT, left = side, right = side))
        b.box(-14f, 17f, 34f, -11f, 23f, 37f, BoxFaces(front = side, left = side, right = side))
        b.box(11f, 17f, 34f, 14f, 23f, 37f, BoxFaces(front = side, left = side, right = side))
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-26f, 26f)) {
            val x0 = if (sx < 0f) -CAR_HALF_W - 2f else CAR_HALF_W - 6f
            b.box(x0, 0f, sz - 8f, x0 + 8f, 11f, sz + 8f, BoxFaces(front = tyre, back = tyre, left = tyre, right = tyre, top = tyre))
        }
        b.build()
    }

    private val headlight = PointLight(0f, 40f, -120f, 0.8f, 0.9f, 1f, 420f, 0.9f)
    private val sunLight = PointLight(0f, 300f, -2600f, 1f, 0.4f, 0.6f, 2600f, 0.6f)

    override fun render(scope: DrawScope) {
        buildTrack()
        camY = carY + CAM_UP
        val bob = if (crashT > 0f) sin(time * 40f) * 4f * crashT else 0f
        stage.look(px * 0.55f, camY + bob, CAM_BACK, px * 0.2f, carY + 20f, -420f, fovDeg = 56f, centerYFrac = 0.44f)
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.55f; l.ambG = 0.5f; l.ambB = 0.7f
        l.setDirection(0f, 1f, 0.3f)
        l.dirR = 0.3f; l.dirG = 0.25f; l.dirB = 0.35f
        l.points.clear()
        headlight.x = px; headlight.y = carY + 40f
        l.points += headlight
        l.points += sunLight
        r.gradient(0xFF0A0420.toInt(), 0xFF5A1850.toInt(), 0, (r.height * 0.5f).toInt())
        r.gradient(0xFF5A1850.toInt(), 0xFF14082A.toInt(), (r.height * 0.5f).toInt(), r.height)
        // The sky is unaffected by fog; everything on the ground fades into the night.
        r.fogNear = 1e8f
        r.fogFar = 2e8f
        drawSky(r)
        r.fogNear = 900f
        r.fogFar = 2900f
        r.fogFloor = 0.12f
        drawRoad(r)
        drawProps(r)
        drawTraffic(r)
        drawTokens(r)
        drawPlayer(r)
        stage.present(scope)
        drawHud(scope)
    }

    /** Lays the road out ahead of the car: each segment bends and climbs a little more. */
    private fun buildTrack() {
        val base = (dist / SEG).toInt()
        val frac = dist / SEG - base
        var x = 0f
        var dx = 0f
        var y = 0f
        for (k in 0..VIEW) {
            val i = (base + k).coerceAtMost(MAX_SEGS - 1)
            segX[k + BEHIND] = x
            segY[k + BEHIND] = y
            segZ[k + BEHIND] = (frac - k) * SEG
            x += dx
            dx += curve[i]
            y += slope[i]
        }
        // Behind the car the road runs straight back, following the hills.
        y = 0f
        for (k in -1 downTo -BEHIND) {
            y -= slope[(base + k).coerceIn(0, MAX_SEGS - 1)]
            segX[k + BEHIND] = 0f
            segY[k + BEHIND] = y
            segZ[k + BEHIND] = (frac - k) * SEG
        }
        carY = slope[base.coerceAtMost(MAX_SEGS - 1)] * frac
    }

    /** World position of a point [d] along the track and [x] across it (into [pt]); false if out of view. */
    private fun trackPoint(d: Float, x: Float): Boolean {
        val k = (d - dist) / SEG + (dist / SEG - (dist / SEG).toInt()) + BEHIND
        if (k < 0f || k >= VIEW + BEHIND) return false
        val i = k.toInt()
        val f = k - i
        pt[0] = segX[i] + (segX[i + 1] - segX[i]) * f + x
        pt[1] = segY[i] + (segY[i + 1] - segY[i]) * f
        pt[2] = segZ[i] + (segZ[i + 1] - segZ[i]) * f
        return true
    }

    private fun drawSky(r: Renderer3D) {
        val far = -3200f
        val glow = TexKit.glow.full
        // The horizon drifts sideways against the bend ahead.
        val shift = -segX[VIEW + BEHIND] * 0.08f
        r.sprite(shift, 260f, far, 900f, 900f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = Pal.PINK)
        r.sprite(shift, 250f, far + 10f, 520f, 520f, RacerArt.sun.full, emissive = 1.1f)
        val m = RacerArt.mountains.full
        r.quad(shift * 1.3f - 3200f, 330f, far + 60f, shift * 1.3f + 3200f, 330f, far + 60f, shift * 1.3f + 3200f, -40f, far + 60f, shift * 1.3f - 3200f, -40f, far + 60f, m, 0f, 0f, 1f, emissive = 0.9f)
        val s = RacerArt.skyline.full
        r.quad(shift * 1.6f - 2200f, 190f, far + 120f, shift * 1.6f + 2200f, 190f, far + 120f, shift * 1.6f + 2200f, -40f, far + 120f, shift * 1.6f - 2200f, -40f, far + 120f, s, 0f, 0f, 1f, emissive = 0.8f)
    }

    private fun drawRoad(r: Renderer3D) {
        val base = (dist / SEG).toInt()
        val white = RacerArt.white.full
        val ground = RacerArt.ground.full
        for (j in 0 until VIEW + BEHIND) {
            val i = (base + j - BEHIND).coerceAtLeast(0)
            val x0 = segX[j]; val y0 = segY[j]; val z0 = segZ[j]
            val x1 = segX[j + 1]; val y1 = segY[j + 1]; val z1 = segZ[j + 1]
            r.quad(
                x1 - GROUND_HALF, y1 - 0.5f, z1, x1 + GROUND_HALF, y1 - 0.5f, z1, x0 + GROUND_HALF, y0 - 0.5f, z0, x0 - GROUND_HALF, y0 - 0.5f, z0,
                ground, 0f, 1f, 0f, emissive = 0.9f,
            )
            val asphalt = if ((i / 2) % 2 == 0) RacerArt.asphalt.full else RacerArt.asphaltDark.full
            r.quad(x1 - ROAD_HALF, y1, z1, x1 + ROAD_HALF, y1, z1, x0 + ROAD_HALF, y0, z0, x0 - ROAD_HALF, y0, z0, asphalt, 0f, 1f, 0f)
            // Neon rumble strips.
            val rumble = if (i % 2 == 0) Pal.CYAN else Pal.PINK
            for (s in floatArrayOf(-1f, 1f)) {
                val a = ROAD_HALF * s
                val b = (ROAD_HALF + 16f) * s
                r.quad(x1 + minOf(a, b), y1 + 0.3f, z1, x1 + maxOf(a, b), y1 + 0.3f, z1, x0 + maxOf(a, b), y0 + 0.3f, z0, x0 + minOf(a, b), y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 1.1f, tint = rumble)
            }
            // Dashed lane lines.
            if (i % 3 == 0) {
                for (lane in floatArrayOf(-LANE / 2f, LANE / 2f)) {
                    r.quad(x1 + lane - 2.5f, y1 + 0.3f, z1, x1 + lane + 2.5f, y1 + 0.3f, z1, x0 + lane + 2.5f, y0 + 0.3f, z0, x0 + lane - 2.5f, y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 0.9f)
                }
            }
        }
    }

    private fun drawProps(r: Renderer3D) {
        val base = (dist / SEG).toInt()
        for (j in VIEW + BEHIND - 1 downTo 1) {
            val i = base + j - BEHIND
            if (i < 0) continue
            val k = j
            if (i % 5 == 0) {
                for (side in intArrayOf(-1, 1)) {
                    val off = side * (ROAD_HALF + 70f + hash01(i, side + 3) * 60f)
                    r.billboard(segX[k] + off, segY[k], segZ[k], 90f, 140f, RacerArt.palm.full, lean = 0f)
                }
            }
            if (i % 23 == 11) {
                val side = if (hash01(i, 9) > 0.5f) 1 else -1
                val x = segX[k] + side * (ROAD_HALF + 110f)
                val sign = RacerArt.signs[(i / 23) % RacerArt.signs.size].full
                r.billboard(x, segY[k] + 70f, segZ[k], 150f, 55f, sign, lean = 0f, emissive = 1.1f)
                r.billboard(x, segY[k], segZ[k] - 1f, 6f, 70f, RacerArt.post.full, lean = 0f)
            }
        }
    }

    private fun drawTraffic(r: Renderer3D) {
        for (c in traffic) {
            if (!trackPoint(c.d, c.x)) continue
            xf.set(pt[0], pt[1], pt[2])
            carModel(c.color).draw(r, xf = xf)
            r.sprite(pt[0], pt[1] + 11f, pt[2] + 41f, 70f, 26f, RacerArt.tailGlow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.RED)
        }
    }

    private fun drawTokens(r: Renderer3D) {
        for (t in tokens) {
            if (!t.active || !trackPoint(t.d, t.x)) continue
            val spin = abs(sin(time * 5f + t.d * 0.01f))
            r.sprite(pt[0], pt[1] + 26f, pt[2], 34f * (0.2f + 0.8f * spin), 34f, RacerArt.token.full)
            r.sprite(pt[0], pt[1] + 26f, pt[2] + 1f, 70f, 70f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.35f, tint = Pal.GOLD)
        }
    }

    private fun drawPlayer(r: Renderer3D) {
        val flicker = graceT > 0f && (time * 16f).toInt() % 2 == 0
        if (flicker) return
        val bounce = sin(time * 30f) * (speed / RacerTuning.MAX_SPEED) * 0.8f
        xf.set(px, carY + bounce, 0f, yaw = -tilt * 0.6f, roll = -tilt)
        carModel(PLAYER_COLOR).draw(r, xf = xf)
        val glow = RacerArt.tailGlow.full
        r.sprite(px - 15f, carY + 11f, 41f, 40f, 24f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = Pal.RED)
        r.sprite(px + 15f, carY + 11f, 41f, 40f, 24f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = Pal.RED)
        if (speed > RacerTuning.MAX_SPEED * 0.8f) {
            val a = 0.35f + 0.25f * sin(time * 50f)
            r.sprite(px, carY + 8f, 46f, 30f, 18f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.CYAN)
        }
    }

    private fun drawHud(scope: DrawScope) {
        val kmh = (speed * 0.2f).toInt()
        val frac = clamp01(speed / RacerTuning.MAX_SPEED)
        with(scope) {
            drawRect(Color.Black, Offset(20f, 596f), Size(150f, 28f), alpha = 0.55f)
            drawRect(Color(if (frac > 0.9f) Pal.CYAN else Pal.PINK), Offset(24f, 616f), Size(142f * frac, 5f))
            PixelFont.draw(this, "$kmh KM/H", 26f, 600f, 2f, Color.White)
            drawRect(Color.Black, Offset(250f, 596f), Size(90f, 28f), alpha = 0.55f)
            drawCircle(Color(Pal.ORANGE), 8f, Offset(266f, 610f))
            drawCircle(Color(Pal.GOLD), 6f, Offset(266f, 610f))
            PixelFont.draw(this, "x$tokensGot", 280f, 602f, 2f, Color(Pal.GOLD))
            if (time < 3f) {
                PixelFont.drawCentered(this, "DRAG TO STEER", GAME_W / 2f, 520f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
            }
            if (abs(px) > ROAD_HALF && !timeUp) {
                PixelFont.drawCentered(this, "OFF ROAD!", GAME_W / 2f, 470f, 3f, Color(Pal.ORANGE), 0.5f + 0.5f * sin(time * 12f))
            }
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    internal val botPX: Float get() = px
    internal val botSpeed: Float get() = speed
    internal val botCrashed: Boolean get() = graceT > 0f

    /** Distance ahead to the nearest car in each of the three lanes (left, middle, right). */
    internal fun botLaneClearance(): FloatArray {
        val out = floatArrayOf(9999f, 9999f, 9999f)
        for (c in traffic) {
            val dz = c.d - dist
            if (dz < -CAR_LEN) continue
            val lane = ((c.x / LANE).let { if (it < -0.5f) 0 else if (it > 0.5f) 2 else 1 })
            if (dz < out[lane]) out[lane] = dz
        }
        return out
    }

    /** Lane (0 left, 1 middle, 2 right) of the nearest token within [range] ahead, or -1. */
    internal fun botTokenLane(range: Float): Int {
        var best = -1
        var bestDz = range
        for (t in tokens) {
            if (!t.active) continue
            val dz = t.d - dist
            if (dz in 0f..bestDz) {
                bestDz = dz
                best = (t.x / LANE + 1f).toInt().coerceIn(0, 2)
            }
        }
        return best
    }

    /** The bend under the car (positive pushes the car left). */
    internal val botBend: Float get() = curve[(dist / SEG).toInt().coerceIn(0, MAX_SEGS - 1)]

    /** Steers towards a road position, as a player dragging would. */
    internal fun botSteer(targetX: Float) {
        steerTarget = targetX.coerceIn(-ROAD_HALF, ROAD_HALF)
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h / 2, Color(0xFF2A1450.toInt()))
        p.fill(0, h / 2, w, h - h / 2, Color(0xFF14082A.toInt()))
        p.disc(w / 2f, h / 2f - 1f, 3f, Color(Pal.PINK))
        val horizon = h / 2f
        for (y in 0 until h / 2) {
            val t = (y + 1f) / (h / 2f)
            val half = 1f + t * (w / 2f - 1f)
            val bend = sin(time * 0.8f) * (1f - t) * 4f
            val cx = w / 2f + bend
            p.fill(cx - half, horizon + y, half * 2f, 1f, Color(0xFF24203A.toInt()))
            val stripe = ((time * 8f + 10f / t).toInt() % 2 == 0)
            p.fill(cx - half - 1f, horizon + y, 1f, 1f, Color(if (stripe) Pal.CYAN else Pal.PINK))
            p.fill(cx + half, horizon + y, 1f, 1f, Color(if (stripe) Pal.CYAN else Pal.PINK))
        }
        p.fill(w / 2f - 2f, h - 3f, 4f, 2f, Color(Pal.RED))
    }
}

private val CAR_COLORS = intArrayOf(Pal.SKY, Pal.LIME, Pal.YELLOW, Pal.PURPLE, Pal.ORANGE, Pal.WHITE)
private val PLAYER_COLOR = Pal.RED
