package com.pocketarcade.games.racer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
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
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Difficulty and payout knobs for the racer. */
object RacerTuning {
    const val ROUND_SECONDS = 72f
    const val LAPS = 3
    /** Cars on the grid, the player's included. */
    const val CARS = 8
    /** The player's grid slot, 0 being pole position. */
    const val PLAYER_SLOT = 6
    const val MAX_SPEED = 1100f
    const val ACCEL = 520f
    const val OFFROAD_SPEED = 450f
    /** How hard curves push the car towards the outside (world units/s at top speed per unit of curve). */
    const val CENTRIFUGAL = 110f
    const val STEER_SPEED = 620f
    /** Road units the steering target moves per field unit of finger travel. */
    const val STEER_GAIN = 1.6f
    /** Speed the player's car coasts at once past the flag. */
    const val COAST_SPEED = 700f

    /** Share of top speed a bend of 1 scrubs off. */
    const val CORNER_SCRUB = 0.08f

    /** Share of the curve's push still felt while drifting. */
    const val DRIFT_GRIP = 0.35f
    /** Share of the corner scrub still felt while drifting. */
    const val DRIFT_SCRUB = 0.25f
    /** Below this bend a drift has nothing to slide on and drags instead: holding one all race costs. */
    const val DRIFT_MIN_BEND = 0.3f
    /** Share of top speed a drift gives up where the road runs (nearly) straight. */
    const val DRIFT_DRAG = 0.15f
    /** Boost charge per second of drifting through a curve of bend 1 at top speed. */
    const val DRIFT_CHARGE = 1f
    const val BOOST_CHARGE_1 = 1f
    const val BOOST_CHARGE_2 = 2.2f
    const val BOOST_TIME_1 = 0.8f
    const val BOOST_TIME_2 = 1.5f
    const val BOOST_SPEED = 1450f
    const val BOOST_ACCEL = 1400f

    /** Rivals' top speed as a share of the player's, from pole down to the back of the grid. */
    const val AI_SKILL_MAX = 1.0f
    const val AI_SKILL_MIN = 0.9f
    const val AI_ACCEL = 480f
    /** Share of top speed rivals give up per unit of bend. */
    const val AI_CORNER_SLOW = 0.045f
    const val AI_STEER = 240f
    /** Racing line: how far towards the inside of the coming bend cars aim, per unit of bend. */
    const val LINE_GAIN = 45f
    const val LINE_MAX = 85f
    /** Rubber band: rivals this far ahead of (behind) the player drive up to [RUBBER] slower (faster). */
    const val RUBBER = 0.05f
    const val RUBBER_RANGE = 1500f
    /** Sideways speed at which touching cars push apart. */
    const val BUMP_SHOVE = 320f
    /** A pass is clean if the player hasn't touched another car for this long. */
    const val CLEAN_SECONDS = 1.2f

    /** One point per this many world units driven. */
    const val UNITS_PER_POINT = 350f
    const val OVERTAKE_POINTS = 25
    /** Points for finishing 1st, 2nd, ... */
    val PLACE_POINTS = intArrayOf(500, 380, 290, 220, 160, 110, 70, 40)
    /** Share of the place points paid when the clock runs out before the flag. */
    const val DNF_SHARE = 0.4f
    const val TIME_BONUS_PER_SECOND = 12
    const val POINTS_PER_TICKET = 40
    const val BASE_TICKETS = 1
}

/**
 * Turbo Racer: a three-lap race round a synthwave circuit against seven rivals. Drag to steer
 * (the car accelerates by itself), hold a second finger to drift through corners and let go
 * for a turbo. The round ends at the flag (placed by finishing order) or when the clock runs
 * out (placed by how far round the course everyone got).
 */
class RacerGame : BaseMiniGame() {
    override val id = "racer"
    override val title = "TURBO RACER"
    override val marquee = "RACER"
    override val instructions = listOf(
        "DRAG LEFT AND RIGHT TO STEER",
        "3 LAPS - BEAT 7 RIVALS",
        "HOLD A 2ND FINGER TO DRIFT",
        "LET GO FOR A TURBO BOOST",
        "CLEAN PASSES PAY EXTRA",
    )
    override val cabinet get() = RacerCabinet
    override val look = CabinetLook(body = Pal.DARKRED, trim = Pal.WHITE, glow = Pal.RED, shape = CabinetShape.RACER)
    override val roundSeconds = RacerTuning.ROUND_SECONDS

    private companion object {
        const val SEG = 40f
        const val VIEW = 72
        /** Segments drawn behind the car, down to the bottom of the screen. */
        const val BEHIND = 8
        const val ROAD_HALF = 150f
        const val LANE = 100f
        const val CAR_HALF_W = 22f
        const val CAR_W = CAR_HALF_W * 2f
        const val CAR_LEN = 80f
        const val GROUND_HALF = 2400f
        const val CAM_BACK = 250f
        const val CAM_UP = 104f
        /** How far off the road the player's car can wander. */
        const val X_LIMIT = ROAD_HALF + 110f
        /** How far off the road a shove can push a rival. */
        const val AI_X_LIMIT = ROAD_HALF + 30f
        /** Segments ahead the racing line looks at. */
        const val LOOK = 14
        /** Progress gained on the inside of a bend (lost on the outside), per unit of offset and bend. */
        const val INSIDE_GAIN = 0.0002f
        /**
         * Grid rows are far enough apart that a car launching at once can't reach the one ahead
         * of it within half a second, even if that one hasn't moved yet (0.5 * ACCEL * 0.5 s^2 = 65).
         */
        const val GRID_ROW = CAR_LEN + 70f
        const val AVOID_RANGE = 260f
        const val PASS_GAP = 58f
        const val CARS = RacerTuning.CARS
        const val LAPS = RacerTuning.LAPS
    }

    /** One car in the race; index 0 is the player's. */
    private class Racer {
        /** Distance along the course from the start line (negative on the grid). */
        var d = 0f
        /** Offset across the road from its middle. */
        var x = 0f
        var v = 0f
        var color = 0
        /** Rival's top speed as a share of the player's. */
        var skill = 1f
        /** Rival's personal offset from the racing line. */
        var bias = 0f
        /** Rival's reaction time to the green light. */
        var launch = 0f
        /** Where a rival is pulling out to pass, and for how much longer. */
        var passX = 0f
        var passT = 0f
        /** Finishing order, 1-based; 0 while still racing. */
        var finish = 0
        /** How long this rival has been ahead of the player, and whether it was last step. */
        var aheadT = 0f
        var wasAhead = false
        var yaw = 0f
    }

    // The circuit: per segment, how much the heading bends (curve), the slope (hill) and the racing line.
    private val segs = SECTION_LEN.sum()
    private val curve = FloatArray(segs)
    private val slope = FloatArray(segs)
    private val lineAt = FloatArray(segs)
    private val lapLen = segs * SEG
    private val raceLen = lapLen * LAPS

    private val cars = Array(CARS) { Racer() }
    private val me = cars[0]
    private val place = IntArray(CARS) { it + 1 }
    private var finishCount = 0
    private var raceDone = false
    /** The player's final place (at the flag, or by progress when the clock ran out). */
    private var finalPlace = 0
    private var lapShown = 0
    private var lastPlace = 0

    init {
        var i = 0
        for (s in SECTION_LEN.indices) {
            val len = SECTION_LEN[s]
            for (k in 0 until len) {
                val t = k / len.toFloat()
                curve[i] = SECTION_BEND[s] * sin(t * PI.toFloat())
                slope[i] = SECTION_HILL[s] * sin(t * 2f * PI.toFloat())
                i++
            }
        }
        // The racing line hugs the inside of the bends coming up.
        for (s in 0 until segs) {
            var sum = 0f
            for (k in 2 until 2 + LOOK) sum += curve[(s + k) % segs]
            lineAt[s] = (sum / LOOK * RacerTuning.LINE_GAIN).coerceIn(-RacerTuning.LINE_MAX, RacerTuning.LINE_MAX)
        }
        for (c in 1 until CARS) cars[c].color = RIVAL_COLORS[c - 1]
        me.color = PLAYER_COLOR
    }

    private var steerTarget = 0f
    private var steerId = -1L
    private var lastSteerX = 0f
    private var driftId = -1L
    private var drifting = false
    private var everDrifted = false
    private var charge = 0f
    private var boostT = 0f
    private var boostFull = 1f
    private var tilt = 0f
    private var driftYaw = 0f
    private var pointsCarry = 0f
    private var passes = 0
    private var contacts = 0
    private var lastContact = -9f
    private var bumpFxT = 0f
    private var engineT = 0f
    private var skidT = 0f

    private val placeLabels = Array(CARS) { "${PLACE_TEXT[it]} PLACE! +${RacerTuning.PLACE_POINTS[it]}" }
    private val dnfLabels = Array(CARS) { "${PLACE_TEXT[it]} +${(RacerTuning.PLACE_POINTS[it] * RacerTuning.DNF_SHARE).toInt()}" }

    override fun reset() {
        finishCount = 0
        raceDone = false
        finalPlace = 0
        lapShown = 0
        var rival = 0
        for (slot in 0 until CARS) {
            val c = if (slot == RacerTuning.PLAYER_SLOT) me else cars[++rival]
            val row = slot / 2
            val col = slot % 2
            c.d = -(30f + row * GRID_ROW + col * 25f)
            c.x = if (col == 0) -52f else 52f
            c.v = 0f
            c.finish = 0
            c.passT = 0f
            c.aheadT = 0f
            c.yaw = 0f
            if (c !== me) {
                // Faster cars start nearer the front.
                val rank = rival - 1
                c.skill = RacerTuning.AI_SKILL_MAX - (RacerTuning.AI_SKILL_MAX - RacerTuning.AI_SKILL_MIN) * rank / (CARS - 2) + rng.range(-0.012f, 0.012f)
                c.bias = rng.range(-18f, 18f)
                c.launch = rng.range(0.05f, 0.35f)
            }
        }
        steerTarget = me.x
        steerId = -1L
        driftId = -1L
        drifting = false
        everDrifted = false
        charge = 0f
        boostT = 0f
        tilt = 0f
        driftYaw = 0f
        pointsCarry = 0f
        passes = 0
        contacts = 0
        lastContact = -9f
        bumpFxT = 0f
        engineT = 0f
        skidT = 0f
        rankCars()
        lastPlace = place[0]
        for (i in 1 until CARS) cars[i].wasAhead = isAhead(i, 0)
    }

    private fun wrap(seg: Int): Int = Math.floorMod(seg, segs)
    private fun segOf(d: Float): Int = wrap(floor(d / SEG).toInt())
    private fun lapOf(d: Float): Int = floor(d / lapLen).toInt().coerceIn(0, LAPS - 1)

    override fun ticketsFor(score: Int): Int = RacerTuning.BASE_TICKETS + score / RacerTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = raceDone || me.v < 60f

    override fun onTimeUp() {
        cancelInput()
        if (raceDone) return
        // Out of time before the flag: placed by how far round everyone got.
        finalPlace = place[0]
        val points = (RacerTuning.PLACE_POINTS[finalPlace - 1] * RacerTuning.DNF_SHARE).toInt()
        addScore(points, GAME_W / 2f, 330f, Color(Pal.ORANGE), dnfLabels[finalPlace - 1])
    }

    /** Forgets both fingers: the car holds its line, and a drift in progress ends without a boost. */
    override fun cancelInput() {
        steerId = -1L
        driftId = -1L
        drifting = false
        charge = 0f
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> if (steerId < 0L && id != driftId) {
                steerId = id
                lastSteerX = x
            } else if (driftId < 0L && id != steerId) {
                driftId = id
                startDrift()
            }
            TouchType.MOVE -> if (id == steerId) {
                // Relative steering, one finger delta at a time, so the curve's push (applied to
                // the target in step) is never thrown away by the finger moving.
                steerTarget = (steerTarget + (x - lastSteerX) * RacerTuning.STEER_GAIN).coerceIn(-X_LIMIT, X_LIMIT)
                lastSteerX = x
            }
            TouchType.UP -> {
                if (id == steerId) steerId = -1L
                if (id == driftId) {
                    driftId = -1L
                    releaseDrift()
                }
            }
        }
    }

    private fun startDrift() {
        if (raceDone || timeUp) return
        drifting = true
        everDrifted = true
        charge = 0f
        skidT = 0f
    }

    private fun releaseDrift() {
        if (!drifting) return
        drifting = false
        val t = when {
            charge >= RacerTuning.BOOST_CHARGE_2 -> RacerTuning.BOOST_TIME_2
            charge >= RacerTuning.BOOST_CHARGE_1 -> RacerTuning.BOOST_TIME_1
            else -> 0f
        }
        charge = 0f
        if (t <= 0f) return
        boostT = maxOf(boostT, t)
        boostFull = boostT
        play(Sfx.BOOST, 0.8f, if (t > RacerTuning.BOOST_TIME_1) 1.15f else 1f)
        fx.haptics.hit()
        popups.add(if (t > RacerTuning.BOOST_TIME_1) "SUPER TURBO!" else "TURBO!", GAME_W / 2f, 450f, Color(Pal.CYAN), size = 3f)
    }

    override fun step(dt: Float) {
        bumpFxT -= dt
        if (boostT > 0f) boostT -= dt
        val racing = !timeUp && !raceDone
        stepPlayer(dt, racing)
        if (!soloForTests) {
            stepRivals(dt)
            resolveContacts(dt)
        }
        checkFinishes()
        rankCars()
        if (!timeUp && !raceDone) scorePasses(dt)

        engineT -= dt
        if (engineT <= 0f && me.v > 40f) {
            engineT = 0.2f
            play(Sfx.ENGINE, 0.3f, 0.55f + me.v / RacerTuning.MAX_SPEED * 0.9f)
        }
        if (drifting) {
            skidT -= dt
            if (skidT <= 0f && abs(curve[segOf(me.d)]) > 0.3f) {
                skidT = 0.45f
                play(Sfx.SKID, 0.35f, 0.9f + clamp01(charge / RacerTuning.BOOST_CHARGE_2) * 0.4f)
            }
        }
    }

    private fun stepPlayer(dt: Float, racing: Boolean) {
        val bend = curve[segOf(me.d)]
        val offroad = abs(me.x) > ROAD_HALF + 4f
        var top = when {
            raceDone -> RacerTuning.COAST_SPEED
            timeUp -> 0f
            boostT > 0f -> RacerTuning.BOOST_SPEED
            else -> RacerTuning.MAX_SPEED
        }
        // Bends scrub speed off; a drift carries most of it through.
        top *= 1f - minOf(abs(bend), 2.5f) * RacerTuning.CORNER_SCRUB * (if (drifting) RacerTuning.DRIFT_SCRUB else 1f)
        // ...but sliding sideways down a straight only scrubs speed off.
        if (drifting && abs(bend) < RacerTuning.DRIFT_MIN_BEND) top *= 1f - RacerTuning.DRIFT_DRAG
        if (offroad) top = minOf(top, RacerTuning.OFFROAD_SPEED)
        me.v = if (me.v < top) {
            (me.v + (RacerTuning.ACCEL + if (boostT > 0f) RacerTuning.BOOST_ACCEL else 0f) * dt).coerceAtMost(top)
        } else {
            approach(me.v, top, (if (timeUp && !raceDone) 900f else 600f) * dt)
        }
        // Past the flag the car eases back to the middle of the road by itself.
        if (raceDone) steerTarget = approach(steerTarget, 0f, 160f * dt)
        val prevX = me.x
        me.x = approach(me.x, steerTarget, RacerTuning.STEER_SPEED * dt)
        // The curve pushes the car, and where the finger is steering it, towards the outside.
        val push = bend * (me.v / RacerTuning.MAX_SPEED) * RacerTuning.CENTRIFUGAL * dt * (if (drifting) RacerTuning.DRIFT_GRIP else 1f)
        me.x = (me.x - push).coerceIn(-X_LIMIT, X_LIMIT)
        steerTarget = (steerTarget - push).coerceIn(-X_LIMIT, X_LIMIT)
        tilt = damp(tilt, ((me.x - prevX) / dt / 900f).coerceIn(-0.25f, 0.25f), 8f, dt)

        if (drifting) {
            if (me.v > 300f) {
                charge = (charge + abs(bend) * (me.v / RacerTuning.MAX_SPEED) * RacerTuning.DRIFT_CHARGE * dt).coerceAtMost(RacerTuning.BOOST_CHARGE_2 + 0.4f)
            }
            // The tail steps out: the nose points into the bend.
            driftYaw = damp(driftYaw, if (bend > 0.05f) -0.32f else if (bend < -0.05f) 0.32f else 0f, 6f, dt)
            if (charge >= RacerTuning.BOOST_CHARGE_1 && rng.nextFloat() < 0.5f) {
                val c = if (charge >= RacerTuning.BOOST_CHARGE_2) Pal.ORANGE else Pal.CYAN
                val side = if (rng.nextBoolean()) -18f else 18f
                stage.toField(me.x + side, carY + 2f, 34f, pt)
                particles.spawn(pt[0], pt[1], rng.range(-50f, 50f), rng.range(-90f, -30f), 0.3f, 3f, c)
            }
        } else {
            driftYaw = damp(driftYaw, 0f, 8f, dt)
        }

        val before = me.d
        me.d += me.v * dt * (1f + me.x * bend * INSIDE_GAIN)
        // Distance only pays on the road: a car left to wander along the verge earns nothing.
        if (racing && me.d > 0f && !offroad) {
            pointsCarry += (me.d - maxOf(before, 0f)) / RacerTuning.UNITS_PER_POINT
            val whole = pointsCarry.toInt()
            if (whole > 0) {
                pointsCarry -= whole
                score += whole
            }
        }
        if (offroad && me.v > 200f && rng.nextFloat() < 0.4f) {
            stage.toField(me.x, 0f, 0f, pt)
            particles.spawn(pt[0] + rng.range(-20f, 20f), pt[1], rng.range(-40f, 40f), rng.range(-80f, -20f), 0.4f, 4f, Pal.shade(Pal.PURPLE, 0.8f))
        }
        if (boostT > 0f && rng.nextFloat() < 0.6f) {
            stage.toField(me.x, carY + 10f, 44f, pt)
            particles.spawn(pt[0] + rng.range(-6f, 6f), pt[1], rng.range(-30f, 30f), rng.range(20f, 90f), 0.25f, 4f, if (rng.nextBoolean()) Pal.CYAN else Pal.WHITE)
        }
    }

    private fun stepRivals(dt: Float) {
        val lim = ROAD_HALF - CAR_HALF_W - 6f
        for (i in 1 until CARS) {
            val c = cars[i]
            val seg = segOf(c.d)
            val bend = curve[seg]
            val line = lineAt[seg] + c.bias
            if (c.passT > 0f) {
                c.passT -= dt
            } else {
                // A slower car just ahead in our path: pull out and pass it.
                var nearest = AVOID_RANGE
                var found = -1
                for (j in 0 until CARS) {
                    if (j == i) continue
                    val o = cars[j]
                    val dd = o.d - c.d
                    if (dd > 0f && dd < nearest && abs(o.x - c.x) < CAR_W + 14f && o.v < c.v + 40f) {
                        nearest = dd
                        found = j
                    }
                }
                if (found >= 0) {
                    val ox = cars[found].x
                    val left = ox - PASS_GAP
                    val right = ox + PASS_GAP
                    c.passX = when {
                        left < -lim -> right
                        right > lim -> left
                        abs(line - left) < abs(line - right) -> left
                        else -> right
                    }.coerceIn(-lim, lim)
                    c.passT = 0.9f
                }
            }
            val target = if (c.passT > 0f) c.passX else line.coerceIn(-lim, lim)
            val prevX = c.x
            c.x = approach(c.x, target, RacerTuning.AI_STEER * dt)
            c.yaw = damp(c.yaw, -((c.x - prevX) / dt / 900f).coerceIn(-0.2f, 0.2f), 8f, dt)
            var top = RacerTuning.MAX_SPEED * c.skill * (1f - minOf(abs(bend), 2.5f) * RacerTuning.AI_CORNER_SLOW)
            if (c.finish > 0) {
                top = RacerTuning.COAST_SPEED
            } else if (!raceDone && !timeUp) {
                // Mild rubber band: ease off when far ahead of the player, push on when far behind.
                top *= 1f - RacerTuning.RUBBER * ((c.d - me.d) / RacerTuning.RUBBER_RANGE).coerceIn(-1f, 1f)
            }
            if (time < c.launch) top = 0f
            c.v = if (c.v < top) (c.v + RacerTuning.AI_ACCEL * dt).coerceAtMost(top) else approach(c.v, top, 500f * dt)
            c.d += c.v * dt * (1f + c.x * bend * INSIDE_GAIN)
        }
    }

    /** Cars that touch push apart sideways; one running into the back of another is slowed to its pace. */
    private fun resolveContacts(dt: Float) {
        for (i in 0 until CARS - 1) {
            for (j in i + 1 until CARS) {
                val a = cars[i]
                val b = cars[j]
                val dd = b.d - a.d
                if (dd >= CAR_LEN || dd <= -CAR_LEN) continue
                val dx = b.x - a.x
                if (dx >= CAR_W || dx <= -CAR_W) continue
                val side = if (dx >= 0f) 1f else -1f
                val shove = minOf((CAR_W - abs(dx)) * 0.5f, RacerTuning.BUMP_SHOVE * dt)
                nudge(i, -side * shove)
                nudge(j, side * shove)
                if (abs(dd) > CAR_LEN * 0.45f) {
                    val rear = if (dd > 0f) a else b
                    val front = if (dd > 0f) b else a
                    if (rear.v > front.v) {
                        front.v += (rear.v - front.v) * 0.2f
                        rear.v = (front.v - 10f).coerceAtLeast(0f)
                    }
                }
                if (i == 0) touched(b)
            }
        }
    }

    private fun nudge(i: Int, dx: Float) {
        val c = cars[i]
        if (i == 0) {
            c.x = (c.x + dx).coerceIn(-X_LIMIT, X_LIMIT)
            steerTarget = (steerTarget + dx).coerceIn(-X_LIMIT, X_LIMIT)
        } else {
            c.x = (c.x + dx).coerceIn(-AI_X_LIMIT, AI_X_LIMIT)
        }
    }

    private fun touched(other: Racer) {
        if (time - lastContact > 0.3f) contacts++
        lastContact = time
        if (bumpFxT > 0f || timeUp) return
        bumpFxT = 0.4f
        play(Sfx.CRASH, 0.35f, 1.5f)
        fx.haptics.hit()
        shake.add(0.2f)
        stage.toField((me.x + other.x) / 2f, carY + 12f, ((me.d - other.d) * 0.5f).coerceIn(-40f, 40f), pt)
        particles.burst(pt[0], pt[1], 10, 60f, 200f, SPARK_COLORS, 0.35f, 3f)
    }

    private fun checkFinishes() {
        for (i in 0 until CARS) {
            val c = cars[i]
            if (c.finish != 0 || c.d < raceLen) continue
            if (i == 0) {
                // Rolling over the line after the clock ran out doesn't count.
                if (timeUp) continue
                c.finish = ++finishCount
                flagFall()
            } else {
                c.finish = ++finishCount
            }
        }
        val lap = lapOf(me.d)
        if (lap > lapShown && !raceDone && !timeUp) {
            lapShown = lap
            play(Sfx.LAP, 0.8f)
            fx.haptics.tick()
            val last = lap == LAPS - 1
            popups.add(if (last) "FINAL LAP!" else LAP_POPUPS[lap], GAME_W / 2f, 250f, Color(if (last) Pal.ORANGE else Pal.CYAN), size = 4f)
        }
    }

    private fun flagFall() {
        raceDone = true
        endedEarly = true
        finalPlace = me.finish
        drifting = false
        charge = 0f
        addScore(RacerTuning.PLACE_POINTS[finalPlace - 1], GAME_W / 2f, 300f, Color(Pal.GOLD), placeLabels[finalPlace - 1])
        val bonus = (timeLeft * RacerTuning.TIME_BONUS_PER_SECOND).toInt()
        if (bonus > 0) addScore(bonus, GAME_W / 2f, 345f, Color(Pal.CYAN), "TIME BONUS +$bonus")
        play(Sfx.FINISH, 1f)
        if (finalPlace == 1) play(Sfx.CHEER, 0.7f)
        fx.haptics.win()
        flash.trigger(0.35f)
        particles.burst(GAME_W / 2f, 260f, 60, 80f, 320f, CONFETTI_COLORS, 1.2f, 5f, kind = Particles.SPARKLE)
    }

    /** Whether car [j] is placed ahead of car [i]: finishers by order, the rest by distance. */
    private fun isAhead(j: Int, i: Int): Boolean {
        val a = cars[j]
        val b = cars[i]
        return if (a.finish > 0) b.finish == 0 || a.finish < b.finish else b.finish == 0 && a.d > b.d
    }

    private fun rankCars() {
        for (i in 0 until CARS) {
            var r = 1
            for (j in 0 until CARS) if (j != i && isAhead(j, i)) r++
            place[i] = r
        }
    }

    /** Pays for clean passes: a rival that had been ahead for a while, overtaken without contact. */
    private fun scorePasses(dt: Float) {
        for (i in 1 until CARS) {
            val c = cars[i]
            val ahead = isAhead(i, 0)
            if (ahead) {
                c.aheadT += dt
            } else {
                if (c.wasAhead && c.aheadT >= 1f && time - lastContact >= RacerTuning.CLEAN_SECONDS) {
                    passes++
                    addScore(RacerTuning.OVERTAKE_POINTS, GAME_W / 2f, 380f, Color(Pal.CYAN), PASS_LABEL)
                    play(Sfx.WHOOSH, 0.6f, 1.2f)
                    fx.haptics.tick()
                }
                c.aheadT = 0f
            }
            c.wasAhead = ahead
        }
        if (place[0] < lastPlace) play(Sfx.SELECT, 0.4f, 1.3f)
        lastPlace = place[0]
    }

    // ---------------------------------------------------------------- 3D presentation

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt())
    private val pt = FloatArray(3)
    private val xf = Xform()
    // Segment start points, indexed from BEHIND segments behind the car to VIEW ahead.
    private val segX = FloatArray(VIEW + BEHIND + 1)
    private val segY = FloatArray(VIEW + BEHIND + 1)
    private val segZ = FloatArray(VIEW + BEHIND + 1)
    private var carY = 0f
    private var camY = CAM_UP
    private var frac = 0f

    init {
        stage.look(0f, CAM_UP, CAM_BACK, 0f, 20f, -420f, fovDeg = 56f, centerYFrac = 0.44f)
    }

    private val carModels = arrayOfNulls<Model>(CARS)
    private fun carModel(i: Int): Model = carModels[i] ?: buildCar(cars[i].color).also { carModels[i] = it }

    private fun buildCar(color: Int): Model {
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
        for (sx in 0..1) for (sz in 0..1) {
            val x0 = if (sx == 0) -CAR_HALF_W - 2f else CAR_HALF_W - 6f
            val z = if (sz == 0) -26f else 26f
            b.box(x0, 0f, z - 8f, x0 + 8f, 11f, z + 8f, BoxFaces(front = tyre, back = tyre, left = tyre, right = tyre, top = tyre))
        }
        return b.build()
    }

    private val headlight = PointLight(0f, 40f, -120f, 0.8f, 0.9f, 1f, 420f, 0.9f)
    private val sunLight = PointLight(0f, 300f, -2600f, 1f, 0.4f, 0.6f, 2600f, 0.6f)

    override fun render(scope: DrawScope) {
        buildTrack()
        camY = carY + CAM_UP
        val px = me.x
        // The camera follows the car most of the way across, so it stays in view off the road.
        stage.look(px * 0.8f, camY, CAM_BACK, px * 0.5f, carY + 20f, -420f, fovDeg = if (boostT > 0f) 60f else 56f, centerYFrac = 0.44f)
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
        drawGrid(r)
        drawProps(r)
        drawGantries(r)
        drawRivals(r)
        drawPlayer(r)
        stage.present()
        drawHud(scope)
    }

    /** Lays the road out ahead of the car: each segment bends and climbs a little more. */
    private fun buildTrack() {
        val pos = me.d / SEG
        val base = floor(pos).toInt()
        frac = pos - base
        var x = 0f
        var dx = 0f
        var y = 0f
        for (k in 0..VIEW) {
            val i = wrap(base + k)
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
            y -= slope[wrap(base + k)]
            segX[k + BEHIND] = 0f
            segY[k + BEHIND] = y
            segZ[k + BEHIND] = (frac - k) * SEG
        }
        carY = slope[wrap(base)] * frac
    }

    /** World position of a point [d] along the course and [x] across it (into [pt]); false if out of view. */
    private fun trackPoint(d: Float, x: Float): Boolean {
        val k = (d - me.d) / SEG + frac + BEHIND
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
        val base = floor(me.d / SEG).toInt()
        val white = RacerArt.white.full
        val ground = RacerArt.ground.full
        for (j in 0 until VIEW + BEHIND) {
            val i = wrap(base + j - BEHIND)
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
            val a = ROAD_HALF
            val b = ROAD_HALF + 16f
            r.quad(x1 - b, y1 + 0.3f, z1, x1 - a, y1 + 0.3f, z1, x0 - a, y0 + 0.3f, z0, x0 - b, y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 1.1f, tint = rumble)
            r.quad(x1 + a, y1 + 0.3f, z1, x1 + b, y1 + 0.3f, z1, x0 + b, y0 + 0.3f, z0, x0 + a, y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 1.1f, tint = rumble)
            // Dashed lane lines.
            if (i % 3 == 0) {
                val h = LANE / 2f
                r.quad(x1 - h - 2.5f, y1 + 0.3f, z1, x1 - h + 2.5f, y1 + 0.3f, z1, x0 - h + 2.5f, y0 + 0.3f, z0, x0 - h - 2.5f, y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 0.9f)
                r.quad(x1 + h - 2.5f, y1 + 0.3f, z1, x1 + h + 2.5f, y1 + 0.3f, z1, x0 + h + 2.5f, y0 + 0.3f, z0, x0 + h - 2.5f, y0 + 0.3f, z0, white, 0f, 1f, 0f, emissive = 0.9f)
            }
            // The chequered start/finish line.
            if (i == 0) {
                r.quad(x0 - ROAD_HALF, y0 + 0.4f, z0 - 18f, x0 + ROAD_HALF, y0 + 0.4f, z0 - 18f, x0 + ROAD_HALF, y0 + 0.4f, z0, x0 - ROAD_HALF, y0 + 0.4f, z0, RacerArt.checker.full, 0f, 1f, 0f, emissive = 0.9f)
            }
        }
    }

    /** Painted grid boxes behind the start line. */
    private fun drawGrid(r: Renderer3D) {
        val lapStart = floor(me.d / lapLen + 0.5f) * lapLen
        val white = RacerArt.white.full
        for (slot in 0 until CARS) {
            val d = lapStart - (30f + (slot / 2) * GRID_ROW + (slot % 2) * 25f) + CAR_LEN / 2f + 6f
            if (!trackPoint(d, if (slot % 2 == 0) -52f else 52f)) continue
            r.flat(pt[0], pt[2], pt[1] + 0.4f, 56f, 3f, white, emissive = 0.9f, alpha = 0.8f, blend = Blend.ALPHA)
        }
    }

    private fun drawProps(r: Renderer3D) {
        val base = floor(me.d / SEG).toInt()
        for (j in VIEW + BEHIND - 1 downTo 1) {
            val i = wrap(base + j - BEHIND)
            if (i % 5 == 0) {
                val offL = ROAD_HALF + 70f + hash01(i, 2) * 60f
                val offR = ROAD_HALF + 70f + hash01(i, 4) * 60f
                r.billboard(segX[j] - offL, segY[j], segZ[j], 90f, 140f, RacerArt.palm.full, lean = 0f)
                r.billboard(segX[j] + offR, segY[j], segZ[j], 90f, 140f, RacerArt.palm.full, lean = 0f)
            }
            if (i % 23 == 11) {
                val side = if (hash01(i, 9) > 0.5f) 1 else -1
                val x = segX[j] + side * (ROAD_HALF + 110f)
                val sign = RacerArt.signs[(i / 23) % RacerArt.signs.size].full
                r.billboard(x, segY[j] + 70f, segZ[j], 150f, 55f, sign, lean = 0f, emissive = 1.1f)
                r.billboard(x, segY[j], segZ[j] - 1f, 6f, 70f, RacerArt.post.full, lean = 0f)
            }
        }
    }

    /** The start/finish gantry (with its start lights) over each line in view. */
    private fun drawGantries(r: Renderer3D) {
        val first = floor(me.d / lapLen) * lapLen
        val glow = TexKit.glow.full
        // Five start lights: red on the grid, green at the start, dark after that.
        val lightColor = when {
            time <= 0f -> Pal.RED
            time < 3f -> Pal.GREEN
            else -> 0
        }
        for (k in 0..1) {
            if (!trackPoint(first + k * lapLen, 0f)) continue
            val x = pt[0]
            val y = pt[1]
            val z = pt[2]
            val span = ROAD_HALF + 40f
            r.billboard(x - span, y, z, 12f, 160f, RacerArt.post.full, lean = 0f)
            r.billboard(x + span, y, z, 12f, 160f, RacerArt.post.full, lean = 0f)
            r.quad(x - span, y + 160f, z, x + span, y + 160f, z, x + span, y + 124f, z, x - span, y + 124f, z, RacerArt.banner.full, 0f, 0f, 1f, emissive = 1.1f, cull = false)
            for (n in -2..2) {
                val lx = x + n * 34f
                r.quad(lx - 13f, y + 124f, z + 0.5f, lx + 13f, y + 124f, z + 0.5f, lx + 13f, y + 102f, z + 0.5f, lx - 13f, y + 102f, z + 0.5f, RacerArt.lampBox.full, 0f, 0f, 1f, cull = false)
                if (lightColor != 0) {
                    val lens = if (lightColor == Pal.RED) RacerArt.lampRed.full else RacerArt.lampGreen.full
                    r.sprite(lx, y + 113f, z + 1.5f, 18f, 18f, lens, emissive = 1f)
                    r.sprite(lx, y + 113f, z + 3f, 56f, 56f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.3f, tint = lightColor)
                } else {
                    r.sprite(lx, y + 113f, z + 1.5f, 18f, 18f, RacerArt.lampOff.full, emissive = 0.6f)
                }
            }
        }
    }

    private fun drawRivals(r: Renderer3D) {
        val glow = RacerArt.tailGlow.full
        for (i in 1 until CARS) {
            val c = cars[i]
            if (!trackPoint(c.d, c.x)) continue
            xf.set(pt[0], pt[1], pt[2], yaw = c.yaw)
            carModel(i).draw(r, xf = xf)
            r.sprite(pt[0], pt[1] + 11f, pt[2] + 41f, 70f, 26f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.RED)
        }
    }

    private fun drawPlayer(r: Renderer3D) {
        val px = me.x
        val bounce = sin(time * 30f) * (me.v / RacerTuning.MAX_SPEED) * 0.8f
        xf.set(px, carY + bounce, 0f, yaw = -tilt * 0.6f + driftYaw, roll = -tilt)
        carModel(0).draw(r, xf = xf)
        val glow = RacerArt.tailGlow.full
        r.sprite(px - 15f, carY + 11f, 41f, 40f, 24f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = Pal.RED)
        r.sprite(px + 15f, carY + 11f, 41f, 40f, 24f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = Pal.RED)
        if (boostT > 0f) {
            val a = 0.6f + 0.3f * sin(time * 60f)
            r.sprite(px, carY + 9f, 50f, 46f, 30f, glow, blend = Blend.ADD, emissive = 1.2f, alpha = a, tint = Pal.CYAN)
            r.sprite(px, carY + 9f, 60f, 26f, 60f, glow, blend = Blend.ADD, emissive = 1.2f, alpha = a * 0.7f, tint = Pal.WHITE)
        } else if (me.v > RacerTuning.MAX_SPEED * 0.8f) {
            val a = 0.35f + 0.25f * sin(time * 50f)
            r.sprite(px, carY + 8f, 46f, 30f, 18f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.CYAN)
        }
    }

    private fun drawHud(scope: DrawScope) {
        val over = raceDone || timeUp
        val p = (if (over && finalPlace > 0) finalPlace else place[0]).coerceIn(1, CARS)
        val lap = lapOf(me.d)
        with(scope) {
            // Position and lap.
            drawRect(Color.Black, Offset(10f, 10f), Size(118f, 50f), alpha = 0.55f)
            ArcadeFont.draw(this, "POS", 18f, 15f, 1.4f, Color(Pal.LAVENDER))
            ArcadeFont.draw(this, PLACE_TEXT[p - 1], 18f, 29f, 3.6f, Color(if (p == 1) Pal.GOLD else Pal.WHITE))
            ArcadeFont.draw(this, OF_CARS, 88f, 38f, 2f, Color(Pal.LAVENDER))
            drawRect(Color.Black, Offset(250f, 10f), Size(100f, 50f), alpha = 0.55f)
            ArcadeFont.draw(this, "LAP", 258f, 15f, 1.4f, Color(Pal.LAVENDER))
            ArcadeFont.draw(this, LAP_TEXT[lap], 258f, 29f, 3.6f, Color(if (lap == LAPS - 1) Pal.ORANGE else Pal.CYAN))
            // Everyone's progress towards the flag.
            drawRect(Color.Black, Offset(10f, 66f), Size(340f, 14f), alpha = 0.45f)
            drawRect(Color(Pal.LAVENDER), Offset(16f, 72f), Size(328f, 2f), alpha = 0.6f)
            for (k in 1 until LAPS) drawRect(Color(Pal.LAVENDER), Offset(16f + 328f * k / LAPS, 68f), Size(1.5f, 10f), alpha = 0.6f)
            drawRect(Color.White, Offset(342f, 67f), Size(4f, 12f))
            for (i in CARS - 1 downTo 0) {
                val c = cars[i]
                val x = 16f + 328f * clamp01(c.d / raceLen)
                if (i == 0) drawCircle(Color.White, 6f, Offset(x, 73f))
                drawCircle(Color(c.color), if (i == 0) 4.5f else 3.5f, Offset(x, 73f))
            }

            // Speed.
            val frac = clamp01(me.v / RacerTuning.BOOST_SPEED)
            drawRect(Color.Black, Offset(10f, 592f), Size(150f, 36f), alpha = 0.55f)
            drawRect(Color(if (boostT > 0f) Pal.CYAN else Pal.PINK), Offset(16f, 618f), Size(138f * frac, 5f))
            ArcadeFont.draw(this, SPEED_TEXT[(me.v * 0.04f).toInt().coerceIn(0, SPEED_TEXT.size - 1)], 16f, 598f, 2f, Color.White)
            // Drift charge, then the turbo burning.
            drawRect(Color.Black, Offset(200f, 592f), Size(150f, 36f), alpha = 0.55f)
            val labelColor = if (boostT > 0f) Pal.CYAN else if (drifting) Pal.ORANGE else Pal.GRAY
            ArcadeFont.draw(this, if (boostT > 0f) "TURBO!" else "DRIFT", 206f, 598f, 2f, Color(labelColor))
            drawRect(Color(Pal.DEEP), Offset(206f, 618f), Size(138f, 5f))
            if (boostT > 0f) {
                drawRect(Color(Pal.CYAN), Offset(206f, 618f), Size(138f * clamp01(boostT / boostFull), 5f))
            } else {
                val cc = when {
                    charge >= RacerTuning.BOOST_CHARGE_2 -> Pal.ORANGE
                    charge >= RacerTuning.BOOST_CHARGE_1 -> Pal.CYAN
                    else -> Pal.LAVENDER
                }
                drawRect(Color(cc), Offset(206f, 618f), Size(138f * clamp01(charge / RacerTuning.BOOST_CHARGE_2), 5f))
            }
            drawRect(Color.White, Offset(206f + 138f * RacerTuning.BOOST_CHARGE_1 / RacerTuning.BOOST_CHARGE_2, 616f), Size(1.5f, 9f))

            if (!over) {
                if (time < 3f) {
                    ArcadeFont.drawCentered(this, "DRAG TO STEER", GAME_W / 2f, 520f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
                } else if (!everDrifted && abs(curve[segOf(me.d + 6f * SEG)]) > 0.8f) {
                    val a = 0.6f + 0.4f * sin(time * 8f)
                    ArcadeFont.drawCentered(this, "HOLD A 2ND FINGER: DRIFT", GAME_W / 2f, 520f, 2f, Color(Pal.ORANGE), a)
                    ArcadeFont.drawCentered(this, "LET GO: TURBO!", GAME_W / 2f, 544f, 2f, Color(Pal.CYAN), a)
                }
                if (abs(me.x) > ROAD_HALF + 4f) {
                    ArcadeFont.drawCentered(this, "OFF ROAD!", GAME_W / 2f, 470f, 3f, Color(Pal.ORANGE), 0.5f + 0.5f * sin(time * 12f))
                }
            } else if (finalPlace > 0) {
                ArcadeFont.drawCentered(this, if (raceDone) "FINISHED" else "PLACED", GAME_W / 2f, 392f, 2.5f, Color(Pal.LAVENDER))
                ArcadeFont.drawCentered(this, PLACE_TEXT[finalPlace - 1], GAME_W / 2f, 416f, 7f, Color(if (finalPlace == 1) Pal.GOLD else Pal.WHITE))
            }
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** Runs the player alone (rivals parked on the grid), for tests of the car's own handling. */
    internal var soloForTests = false
    internal val botPX: Float get() = me.x
    /** Road position the car is steering for. */
    internal val botSteerTarget: Float get() = steerTarget
    internal val botSpeed: Float get() = me.v
    internal val botProgress: Float get() = me.d
    internal val botRaceLength: Float get() = raceLen
    /** The player's live position, or the final one once the race is over for them. */
    internal val botPlace: Int get() = if ((raceDone || timeUp) && finalPlace > 0) finalPlace else place[0]
    internal val botLap: Int get() = lapOf(me.d) + 1
    internal val botRaceDone: Boolean get() = raceDone
    internal val botDrifting: Boolean get() = drifting
    internal val botBoosting: Boolean get() = boostT > 0f
    internal val botCharge: Float get() = charge
    internal val botPasses: Int get() = passes
    /** Separate bumps the player's car has been in. */
    internal val botContacts: Int get() = contacts
    /** The bend under the car (positive bends right and pushes the car left). */
    internal val botBend: Float get() = curve[segOf(me.d)]
    /** The bend [segments] ahead of the car. */
    internal fun botBendAhead(segments: Int): Float = curve[segOf(me.d + segments * SEG)]
    /** Where the racing line runs at the car's position. */
    internal val botRacingLine: Float get() = lineAt[segOf(me.d)]
    internal fun botCarD(i: Int): Float = cars[i].d
    internal fun botCarX(i: Int): Float = cars[i].x
    internal fun botCarV(i: Int): Float = cars[i].v
    internal fun botFinishOrder(i: Int): Int = cars[i].finish

    /** Offset across the road of the nearest rival within [range] ahead in the player's path, or NaN. */
    internal fun botBlockerX(range: Float): Float {
        var best = range
        var x = Float.NaN
        for (i in 1 until CARS) {
            val c = cars[i]
            val dd = c.d - me.d
            if (dd > -CAR_LEN && dd < best && abs(c.x - me.x) < CAR_W + 34f) {
                best = dd
                x = c.x
            }
        }
        return x
    }

    /** Puts car [i] at [d] along the course and [x] across it, doing [v] (for rule tests). */
    internal fun botPlaceCar(i: Int, d: Float, x: Float, v: Float) {
        val c = cars[i]
        c.d = d
        c.x = x
        c.v = v
        if (i == 0) {
            steerTarget = x
            lapShown = lapOf(d)
        }
        rankCars()
        lastPlace = place[0]
        for (k in 1 until CARS) {
            cars[k].wasAhead = isAhead(k, 0)
            cars[k].aheadT = 0f
        }
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        val fw = w.toFloat()
        val fh = h.toFloat()
        val hz = fh * 0.42f
        val night = Color(0xFF3A1250.toInt())
        // Sky, setting sun and skyline.
        p.fill(0f, 0f, fw, hz * 0.55f, Color(0xFF0A0420.toInt()))
        p.fill(0f, hz * 0.55f, fw, hz * 0.45f, night)
        p.disc(fw / 2f, hz - 0.2f, 3.4f, Color(Pal.ORANGE))
        p.disc(fw / 2f, hz - 1.2f, 2.6f, Color(Pal.PINK), 0.8f)
        p.fill(fw / 2f - 4f, hz - 1.6f, 8f, 0.35f, night)
        p.fill(fw / 2f - 4f, hz - 0.8f, 8f, 0.45f, night)
        for (k in 0 until 10) {
            val bh = 0.8f + hash01(k, 7) * 2.2f
            p.fill(k * 2.7f, hz - bh, 2.2f, bh, Color(Pal.PLUM))
        }
        p.fill(0f, hz, fw, fh - hz, Color(0xFF14082A.toInt()))

        // A 14 s loop: the grid under the start lights, lights out, then the race.
        val loop = time % 14f
        val go = loop - 2.4f
        val run = maxOf(go, 0f)
        val scroll = run * 7f
        val bend = if (go > 0f) sin(run * 0.5f) * 5f else 0f
        val rows = ((fh - hz) * 2f).toInt()
        for (row in 0 until rows) {
            val y = hz + row * 0.5f
            val t = (row + 1f) / rows
            val half = 0.4f + t * fw * 0.62f
            val cx = fw / 2f + bend * (1f - t) * (1f - t)
            p.fill(cx - half, y, half * 2f, 0.5f, Color(0xFF24203A.toInt()))
            val band = (scroll + 3f / t).toInt()
            val rc = Color(if (band % 2 == 0) Pal.CYAN else Pal.PINK)
            p.fill(cx - half - 0.5f, y, 0.5f, 0.5f, rc)
            p.fill(cx + half, y, 0.5f, 0.5f, rc)
            if (band % 3 == 0) p.fill(cx - 0.15f, y, 0.3f, 0.5f, Color.White, 0.6f)
        }

        // Rivals, [attractGap] car lengths ahead of the player; the slower ones drop back as they're passed.
        var ahead = 0
        var drawn = 0
        for (pass in 0 until ATTRACT_RIVALS) {
            // Far to near.
            var pick = -1
            var pickGap = 0f
            for (k in 0 until ATTRACT_RIVALS) {
                if (drawn and (1 shl k) != 0) continue
                val g = attractGap(k, run)
                if (pick < 0 || g > pickGap) {
                    pick = k
                    pickGap = g
                }
            }
            drawn = drawn or (1 shl pick)
            if (pickGap > 0f) ahead++
            if (pickGap < -0.6f) continue
            val t = 0.86f / (1f + maxOf(pickGap, -0.4f) * 0.55f)
            val y = hz + t * (fh - hz)
            val half = 0.4f + t * fw * 0.62f
            val cx = fw / 2f + bend * (1f - t) * (1f - t) + (if (pick % 2 == 0) -0.45f else 0.45f) * half
            val cw = 0.5f + t * 3.4f
            val ch = cw * 0.55f
            p.fill(cx - cw / 2f, y - ch, cw, ch, Color(RIVAL_COLORS[pick]))
            p.fill(cx - cw / 2f, y - ch * 0.35f, cw * 0.25f, ch * 0.2f, Color(Pal.RED))
            p.fill(cx + cw / 4f, y - ch * 0.35f, cw * 0.25f, ch * 0.2f, Color(Pal.RED))
        }

        // The start gantry over the grid: lights coming on one by one, then green.
        if (go < 1.2f) {
            val t = 0.3f
            val y = hz + t * (fh - hz)
            val half = 0.4f + t * fw * 0.62f
            p.fill(fw / 2f - half - 0.6f, y - 5f, 0.5f, 5f, Color(Pal.GRAY))
            p.fill(fw / 2f + half + 0.1f, y - 5f, 0.5f, 5f, Color(Pal.GRAY))
            p.fill(fw / 2f - half - 0.6f, y - 5.6f, half * 2f + 1.2f, 1.6f, Color(0xFF08060C.toInt()))
            val lit = if (go >= 0f) 5 else (loop / 0.45f).toInt().coerceAtMost(5)
            val on = Color(if (go >= 0f) Pal.GREEN else Pal.RED)
            for (n in 0 until 5) p.disc(fw / 2f + (n - 2) * 1.5f, y - 4.8f, 0.5f, if (n < lit) on else Color(Pal.DARKGRAY))
            // Chequered line across the road.
            for (n in 0 until 12) {
                p.fill(fw / 2f - half + n * half / 6f, y, half / 6f, 0.4f, Color(if (n % 2 == 0) Pal.WHITE else Pal.BLACK))
            }
        }

        // The player's car.
        p.fill(fw / 2f - 2.2f, fh - 2.6f, 4.4f, 2.2f, Color(Pal.RED))
        p.fill(fw / 2f - 1.3f, fh - 3.3f, 2.6f, 0.9f, Color(Pal.shade(Pal.SKY, 0.6f)))
        p.fill(fw / 2f - 2f, fh - 1.6f, 1f, 0.5f, Color(Pal.YELLOW))
        p.fill(fw / 2f + 1f, fh - 1.6f, 1f, 0.5f, Color(Pal.YELLOW))
        if (go > 0f && (time * 12f).toInt() % 2 == 0) p.fill(fw / 2f - 0.6f, fh - 0.5f, 1.2f, 0.5f, Color(Pal.CYAN))

        // Race HUD: position (the seventh rival trails behind, out of sight) and lap.
        val pos = 1 + ahead
        p.text(ATTRACT_POS[pos - 1], 0.6f, 0.5f, Color(if (pos == 1) Pal.GOLD else Pal.WHITE), tiny = true)
        val lap = (run / 3.6f).toInt().coerceIn(0, LAPS - 1)
        p.textCentered(ATTRACT_LAP[lap], fw - 4.2f, 0.5f, Color(Pal.CYAN), tiny = true)
        if (go >= 0f && go < 0.9f && (time * 6f).toInt() % 2 == 0) {
            p.textCentered("GO!", fw / 2f, fh * 0.55f, Color(Pal.LIME), tiny = true)
        }
        if (loop > 12.6f && (time * 3f).toInt() % 2 == 0) {
            p.textCentered("FINISH!", fw / 2f, fh * 0.55f, Color(Pal.YELLOW), tiny = true)
        }
    }

    /** How many car lengths attract-mode rival [k] runs ahead of the player, [run] seconds after the start. */
    private fun attractGap(k: Int, run: Float): Float = ATTRACT_START[k] + run * (ATTRACT_PACE[k] - 1f)
}

/** The circuit, one entry per section: length in segments, peak bend (positive bends right) and hill height. */
private val SECTION_LEN = intArrayOf(66, 64, 28, 50, 40, 36, 36, 32, 72, 34, 44, 28, 40, 50)
private val SECTION_BEND = floatArrayOf(0f, 1.4f, 0f, -2.4f, 0f, 1.9f, -1.9f, 0f, -1.2f, 0f, 2.2f, 0f, -1.6f, 0f)
private val SECTION_HILL = floatArrayOf(0f, 3f, 0f, 0f, 7f, 0f, 0f, -6f, 4f, 0f, 0f, 3f, 0f, 0f)

private val RIVAL_COLORS = intArrayOf(Pal.SKY, Pal.LIME, Pal.YELLOW, Pal.PURPLE, Pal.ORANGE, Pal.WHITE, Pal.TEAL)
private val PLAYER_COLOR = Pal.RED
private val SPARK_COLORS = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.WHITE)
private val CONFETTI_COLORS = intArrayOf(Pal.GOLD, Pal.PINK, Pal.CYAN, Pal.LIME, Pal.WHITE)

private val PLACE_TEXT = arrayOf("1ST", "2ND", "3RD", "4TH", "5TH", "6TH", "7TH", "8TH")
private val OF_CARS = "/${RacerTuning.CARS}"
private val LAP_TEXT = Array(RacerTuning.LAPS) { "${it + 1}/${RacerTuning.LAPS}" }
private val LAP_POPUPS = Array(RacerTuning.LAPS) { "LAP ${it + 1}" }
private val SPEED_TEXT = Array(64) { "${it * 5} KM/H" }
private val PASS_LABEL = "CLEAN PASS +${RacerTuning.OVERTAKE_POINTS}"

private const val ATTRACT_RIVALS = 6
private val ATTRACT_START = floatArrayOf(0.4f, 0.4f, 1.4f, 1.4f, 2.4f, 2.4f)
private val ATTRACT_PACE = floatArrayOf(0.9f, 0.75f, 0.82f, 0.95f, 0.7f, 0.86f)
private val ATTRACT_POS = Array(RacerTuning.CARS) { "${PLACE_TEXT[it]}/${RacerTuning.CARS}" }
private val ATTRACT_LAP = Array(RacerTuning.LAPS) { "LAP ${it + 1}/${RacerTuning.LAPS}" }
