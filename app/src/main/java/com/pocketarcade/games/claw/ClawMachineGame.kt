package com.pocketarcade.games.claw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.Plush
import com.pocketarcade.engine.Body
import com.pocketarcade.engine.CircleWorld
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Segment
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.approach
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Difficulty and payout knobs for the claw machine. */
object ClawTuning {
    const val ROUND_SECONDS = 45f
    const val PILE_SIZE = 14

    const val TROLLEY_ACCEL = 900f
    const val TROLLEY_MAX_SPEED = 170f
    const val TROLLEY_BRAKE = 1400f
    const val CARRY_SPEED = 150f
    const val DROP_SPEED = 200f
    const val LIFT_SPEED = 150f

    /** How strongly trolley acceleration kicks the pendulum (1 = full physical coupling). */
    const val SWING_COUPLING = 0.35f
    const val SWING_DAMPING = 0.9f

    /** The machine picks a random grip strength per grab in this range. */
    const val GRIP_MIN = 0.6f
    const val GRIP_MAX = 1.0f
    /**
     * Chance a grab holds all the way to the chute:
     * BASE_HOLD × grip × centred^CENTER_POWER ÷ √(radius / 24), where centred is 1 dead-centre
     * and 0 at the edge of the prongs' reach.
     */
    const val BASE_HOLD = 0.85f
    const val CENTER_POWER = 2f
    /** Chance each new grab is a lucky claw (guaranteed hold, wider reach). */
    const val LUCKY_CHANCE = 0.12f
    /** Sideways reach of the prongs beyond half a prize's radius; smaller demands better aim. */
    const val GRAB_REACH = 16f
    const val LUCKY_REACH = 30f

    const val BASE_TICKETS = 2
    const val POINTS_PER_TICKET = 10
}

class ClawMachineGame : BaseMiniGame() {
    override val id = "claw"
    override val title = "CLAW MACHINE"
    override val marquee = "CLAW"
    override val instructions = listOf(
        "HOLD ${ArcadeFont.LEFT} ${ArcadeFont.RIGHT} TO MOVE THE CLAW",
        "TAP DROP TO GRAB",
        "CENTER THE GRAB FOR",
        "A STRONGER GRIP",
        "WATCH THE SWING!",
    )
    override val look = CabinetLook(body = Pal.PINK, trim = Pal.YELLOW, glow = Pal.HOTPINK, shape = CabinetShape.CLAW)
    override val roundSeconds = ClawTuning.ROUND_SECONDS

    private enum class State { IDLE, DROPPING, CLOSING, LIFTING, CARRYING, RELEASING }

    private companion object {
        const val RAIL_Y = 26f
        const val BOX_LEFT = 12f
        const val BOX_RIGHT = 348f
        const val BOX_TOP = 44f
        const val FLOOR_Y = 470f
        const val LIP_X = 88f
        const val LIP_TOP = 392f
        const val CHUTE_X = 48f
        const val TROLLEY_MIN = 46f
        const val TROLLEY_MAX = 322f
        const val CABLE_IDLE = 36f
        const val CLAW_REACH = 28f
        const val PENDULUM_G = 900f

        // 3D box: half its depth, how far the chute drops, where the gantry rails sit.
        const val BOX_D = 50f
        const val PIT_D = 70f
        const val RAIL_Z = 22f

        const val LEFT_X = 22f
        const val RIGHT_X = 120f
        const val BTN_Y = 522f
        const val BTN_SIZE = 88f
        const val DROP_CX = 292f
        const val DROP_CY = 566f
        const val DROP_R = 50f
    }

    private val world = CircleWorld(gravityY = 900f).apply { iterations = 6 }

    private var state = State.IDLE
    private var stateTime = 0f
    private var trolleyX = 200f
    private var trolleyV = 0f
    private var cable = CABLE_IDLE
    private var prevCable = CABLE_IDLE
    private var dropCable = CABLE_IDLE
    private var theta = 0f
    private var thetaV = 0f
    private var openness = 1f
    private var held: Body? = null
    private var hang = 0f
    private var slipAt = 99f
    private var progress = 0f
    private var carryStartX = 0f
    private var lucky = false
    private var grabs = 0
    private var grip = 0.6f
    private var motorTimer = 0f
    private var prevClawX = 0f
    private var prevClawY = 0f
    private var clawVX = 0f
    private var clawVY = 0f
    private var leftPointer = -1L
    private var rightPointer = -1L
    private var dropPressT = 0f
    private var wonCount = 0
    private var chuteFlash = 0f
    private var respawnTimers = FloatArray(0)
    private var bannerText = ""
    private var bannerT = 0f
    private var bannerColor = Color.White

    private val clawX get() = trolleyX + cable * sin(theta)
    private val clawY get() = RAIL_Y + cable * cos(theta)

    override fun reset() {
        world.bodies.clear()
        world.segments.clear()
        world.segments.add(Segment(LIP_X, FLOOR_Y, BOX_RIGHT, FLOOR_Y, 0.1f))
        world.segments.add(Segment(BOX_RIGHT, BOX_TOP, BOX_RIGHT, FLOOR_Y, 0.2f))
        world.segments.add(Segment(LIP_X, LIP_TOP, LIP_X, FLOOR_Y, 0.2f))
        world.segments.add(Segment(BOX_LEFT, BOX_TOP, BOX_LEFT, LIP_TOP, 0.2f))
        repeat(ClawTuning.PILE_SIZE) { i ->
            val p = pickPlush()
            val x = rng.range(LIP_X + p.radius + 6f, BOX_RIGHT - p.radius - 4f)
            val y = FLOOR_Y - 30f - (i / 5) * 55f - rng.range(0f, 30f)
            addPlushBody(p, x, y)
        }
        // Let the pile settle before the player sees it.
        repeat(420) { world.step(FIXED_DT) }
        for (b in world.bodies) if (!b.kinematic) {
            b.vx = 0f; b.vy = 0f; b.angle = 0f
        }
        state = State.IDLE
        stateTime = 0f
        trolleyX = 200f
        trolleyV = 0f
        cable = CABLE_IDLE
        prevCable = CABLE_IDLE
        theta = 0f
        thetaV = 0f
        openness = 1f
        held = null
        lucky = false
        grabs = 0
        wonCount = 0
        chuteFlash = 0f
        respawnTimers = FloatArray(0)
        leftPointer = -1L
        rightPointer = -1L
        bannerT = 0f
        botCounters.fill(0)
        prevClawX = clawX
        prevClawY = clawY
    }

    private fun pickPlush(): Plush {
        val total = Catalog.plushies.sumOf { it.weight }
        var r = rng.nextInt(total)
        for (p in Catalog.plushies) {
            r -= p.weight
            if (r < 0) return p
        }
        return Catalog.plushies.first()
    }

    private fun addPlushBody(p: Plush, x: Float, y: Float): Body {
        val b = Body(x, y, p.radius).apply {
            restitution = 0.12f
            friction = 0.9f
            damping = 0.9f
            data = p
            angle = rng.range(-0.3f, 0.3f)
        }
        world.bodies.add(b)
        return b
    }

    private fun banner(text: String, color: Color, seconds: Float = 1.4f) {
        bannerText = text
        bannerColor = color
        bannerT = seconds
    }

    private fun enter(s: State) {
        state = s
        stateTime = 0f
    }

    override fun step(dt: Float) {
        stateTime += dt
        bannerT -= dt
        dropPressT = (dropPressT - dt).coerceAtLeast(0f)
        chuteFlash = (chuteFlash - dt * 2f).coerceAtLeast(0f)

        val oldV = trolleyV
        when (state) {
            State.IDLE -> {
                val dir = (if (rightPointer >= 0) 1 else 0) - (if (leftPointer >= 0) 1 else 0)
                trolleyV = if (dir != 0 && !timeUp) {
                    approach(trolleyV, dir * ClawTuning.TROLLEY_MAX_SPEED, ClawTuning.TROLLEY_ACCEL * dt)
                } else {
                    approach(trolleyV, 0f, ClawTuning.TROLLEY_BRAKE * dt)
                }
                openness = approach(openness, 1f, dt * 3f)
                cable = approach(cable, CABLE_IDLE, ClawTuning.LIFT_SPEED * dt)
            }
            State.DROPPING -> {
                trolleyV = approach(trolleyV, 0f, ClawTuning.TROLLEY_BRAKE * dt)
                cable += ClawTuning.DROP_SPEED * dt
                val tipY = clawY + CLAW_REACH
                var contact = tipY >= FLOOR_Y - 2f
                if (!contact) {
                    val cx = clawX
                    for (b in world.bodies) {
                        if (b.kinematic || b.data !is Plush) continue
                        if (abs(b.x - cx) < b.r * 0.85f + 8f && tipY > b.y - b.r * 0.3f) {
                            contact = true; break
                        }
                    }
                }
                if (contact) {
                    dropCable = cable
                    enter(State.CLOSING)
                    play(Sfx.CLAW_GRAB)
                    fx.haptics.tick()
                }
            }
            State.CLOSING -> {
                val t = clamp01(stateTime / 0.45f)
                openness = lerp(1f, 0.1f, t)
                if (stateTime >= 0.45f) resolveGrab()
            }
            State.LIFTING -> {
                cable = approach(cable, CABLE_IDLE, ClawTuning.LIFT_SPEED * dt)
                val span = (dropCable - CABLE_IDLE).coerceAtLeast(1f)
                progress = 1f - (cable - CABLE_IDLE) / span
                if (cable <= CABLE_IDLE + 0.01f) {
                    carryStartX = trolleyX
                    enter(State.CARRYING)
                }
            }
            State.CARRYING -> {
                val toGo = CHUTE_X - trolleyX
                val want = (toGo * 4f).coerceIn(-ClawTuning.CARRY_SPEED, ClawTuning.CARRY_SPEED)
                trolleyV = approach(trolleyV, want, ClawTuning.TROLLEY_ACCEL * dt)
                val span = abs(carryStartX - CHUTE_X).coerceAtLeast(1f)
                progress = 1f + clamp01(1f - abs(toGo) / span)
                if (abs(toGo) < 1.5f && abs(trolleyV) < 12f && abs(thetaV) < 1.5f) {
                    trolleyV = 0f
                    enter(State.RELEASING)
                    releaseHeld(dropped = true)
                } else if (abs(toGo) < 1.5f && stateTime > 4f) {
                    trolleyV = 0f
                    enter(State.RELEASING)
                    releaseHeld(dropped = true)
                }
            }
            State.RELEASING -> {
                openness = approach(openness, 1f, dt * 3f)
                if (stateTime > 0.55f) startIdle()
            }
        }

        if (state == State.IDLE || state == State.CARRYING) {
            if (abs(trolleyV) > 10f) {
                motorTimer -= dt
                if (motorTimer <= 0f) {
                    motorTimer = 0.2f
                    play(Sfx.CLAW_MOTOR, 0.6f)
                }
            }
        }
        if (state == State.DROPPING || state == State.LIFTING) {
            motorTimer -= dt
            if (motorTimer <= 0f) {
                motorTimer = 0.2f
                play(Sfx.CLAW_MOTOR, 0.5f, if (state == State.DROPPING) 0.8f else 1.1f)
            }
        }

        trolleyX += trolleyV * dt
        if (trolleyX < TROLLEY_MIN) {
            trolleyX = TROLLEY_MIN; trolleyV = 0f
        }
        if (trolleyX > TROLLEY_MAX) {
            trolleyX = TROLLEY_MAX; trolleyV = 0f
        }

        // Pendulum: the claw hangs from the trolley and swings from its acceleration.
        val accel = (trolleyV - oldV) / dt * ClawTuning.SWING_COUPLING
        val heavy = held?.let { 1f + it.r / 60f } ?: 1f
        // Variable-length pendulum: paying out cable (L' > 0) calms the swing, reeling in pumps it.
        val cableV = (cable - prevCable) / dt
        prevCable = cable
        val thetaAcc = -(PENDULUM_G / cable) * sin(theta) - (accel * heavy / cable) * cos(theta) -
            (2f * cableV / cable) * thetaV - ClawTuning.SWING_DAMPING * thetaV
        thetaV += thetaAcc * dt
        theta = (theta + thetaV * dt).coerceIn(-0.9f, 0.9f)

        val cx = clawX
        val cy = clawY
        clawVX = (cx - prevClawX) / dt
        clawVY = (cy - prevClawY) / dt
        prevClawX = cx
        prevClawY = cy

        held?.let { b ->
            val warn = progress > slipAt - 0.22f
            val jiggle = if (warn) sin(time * 60f) * 3f else 0f
            hang = approach(hang, CLAW_REACH * 0.55f + b.r * 0.35f, dt * 80f)
            b.x = cx + jiggle
            b.y = cy + hang
            b.vx = clawVX
            b.vy = clawVY
            b.angle = approach(b.angle, 0f, dt * 3f)
            if (progress >= slipAt) slip()
        }

        if (lucky && (state == State.IDLE || state == State.DROPPING) && rng.chance(0.35f)) {
            screenOf(cx, cy)
            particles.spawn(
                pt[0] + rng.range(-16f, 16f), pt[1] + rng.range(-6f, 30f), rng.range(-20f, 20f), rng.range(-30f, 10f),
                0.5f, 3f, if (rng.nextBoolean()) Pal.GOLD else Pal.WHITE, kind = Particles.SPARKLE,
            )
        }

        world.step(dt)
        for (b in world.bodies) {
            if (b.kinematic) continue
            val speed = abs(b.vx) + abs(b.vy)
            if (b.touching && speed < 30f) {
                val target = Math.round(b.angle / (2 * PI)).toFloat() * (2 * PI).toFloat()
                b.angle = approach(b.angle, target, dt * 0.8f)
            }
        }
        checkWins()
        tickRespawns(dt)
    }

    private fun startIdle() {
        enter(State.IDLE)
        grabs++
        lucky = grabs >= 1 && !timeUp && rng.chance(ClawTuning.LUCKY_CHANCE)
        if (lucky) {
            banner("LUCKY CLAW!", Color(Pal.GOLD), 2.2f)
            play(Sfx.LUCKY)
            fx.haptics.win()
            screenOf(clawX, clawY + 10f)
            particles.burst(pt[0], pt[1], 30, 60f, 200f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.8f, 4f, kind = Particles.SPARKLE)
        }
    }

    private fun resolveGrab() {
        val cx = clawX
        val tipY = clawY + CLAW_REACH
        val reach = if (lucky) ClawTuning.LUCKY_REACH else ClawTuning.GRAB_REACH
        var best: Body? = null
        var bestCentered = 0f
        for (b in world.bodies) {
            val p = b.data as? Plush ?: continue
            if (b.kinematic) continue
            val span = reach + p.radius * 0.5f
            val dx = abs(b.x - cx)
            if (dx > span) continue
            val top = b.y - b.r
            if (tipY < top - 4f || b.y < clawY - 6f) continue
            val centered = 1f - dx / span
            if (centered > bestCentered) {
                best = b
                bestCentered = centered
            }
        }
        grip = rng.range(ClawTuning.GRIP_MIN, ClawTuning.GRIP_MAX)
        botCounters[0]++
        val b = best
        if (b == null) {
            openness = 0f
            banner("MISSED!", Color(Pal.LAVENDER))
            play(Sfx.BONK, 0.6f)
            enter(State.LIFTING)
            progress = 0f
            return
        }
        val weight = sqrt(b.r / 24f)
        val holdChance = if (lucky) 1f else {
            clamp01(ClawTuning.BASE_HOLD * grip * bestCentered.pow(ClawTuning.CENTER_POWER) / weight)
        }
        slipAt = when {
            rng.nextFloat() < holdChance -> 99f
            // A hopeless grab lets go almost immediately; others slip somewhere on the way up.
            holdChance < 0.08f -> 0.12f
            else -> rng.range(0.2f, 1.35f)
        }
        if (slipAt > 90f) botCounters[1]++
        openness = 0.35f
        held = b
        b.kinematic = true
        hang = b.y - clawY
        progress = 0f
        enter(State.LIFTING)
        fx.haptics.hit()
        screenOf(b.x, b.y - b.r)
        particles.burst(pt[0], pt[1], 8, 40f, 120f, intArrayOf(Pal.WHITE, Pal.LAVENDER), 0.4f, 3f)
    }

    private fun slip() {
        botCounters[2]++
        releaseHeld(dropped = false)
        banner("SO CLOSE!", Color(Pal.ORANGE))
        play(Sfx.DROP)
        fx.haptics.tick()
        shake.add(0.2f)
    }

    private fun releaseHeld(dropped: Boolean) {
        val b = held ?: return
        held = null
        slipAt = 99f
        b.kinematic = false
        // A slipping prize drops out of the claw almost straight down; a released one keeps the swing.
        b.vx = if (dropped) clawVX * 0.8f else clawVX * 0.15f
        b.vy = if (dropped) 40f else clawVY.coerceAtLeast(0f)
        if (dropped) play(Sfx.DROP, 0.7f, 1.3f)
    }

    private fun checkWins() {
        val it = world.bodies.iterator()
        var removed = 0
        while (it.hasNext()) {
            val b = it.next()
            val p = b.data as? Plush ?: continue
            if (b.kinematic) continue
            if (b.x < LIP_X - 2f && b.y > LIP_TOP + 14f) {
                it.remove()
                removed++
                wonCount++
                botCounters[3]++
                chuteFlash = 1f
                screenOf(CHUTE_X, LIP_TOP)
                val sx = pt[0]
                val sy = pt[1]
                addScore(p.points, sx, sy - 30f, Color(Pal.YELLOW))
                banner(if (p.rare) "RARE PRIZE!" else "GOT IT!", Color(if (p.rare) Pal.GOLD else Pal.LIME))
                popups.add(p.name, sx + 60f, sy - 60f, Color.White, size = 2f, life = 1.4f)
                play(if (p.rare) Sfx.JACKPOT else Sfx.PRIZE)
                fx.haptics.win()
                shake.add(0.35f)
                particles.confetti(BOX_LEFT, 40f, BOX_RIGHT - BOX_LEFT, 50)
                particles.burst(sx, sy + 30f, 30, 80f, 260f, intArrayOf(Pal.YELLOW, Pal.PINK, Pal.CYAN, Pal.WHITE), 0.7f, 5f, grav = 300f)
                fx.onCollectible(p.id)
            } else if (b.y > GAME_H + 40f) {
                it.remove()
                removed++
            }
        }
        if (removed > 0) {
            val grown = FloatArray(respawnTimers.size + removed)
            respawnTimers.copyInto(grown)
            for (i in respawnTimers.size until grown.size) grown[i] = 1.2f + (i - respawnTimers.size) * 0.5f
            respawnTimers = grown
        }
    }

    private fun tickRespawns(dt: Float) {
        if (respawnTimers.isEmpty()) return
        var keep = 0
        for (i in respawnTimers.indices) {
            respawnTimers[i] -= dt
            if (respawnTimers[i] <= 0f) {
                val p = pickPlush()
                addPlushBody(p, rng.range(LIP_X + 40f, BOX_RIGHT - 30f), BOX_TOP + 10f)
                play(Sfx.POP, 0.5f, 0.8f)
            } else {
                respawnTimers[keep++] = respawnTimers[i]
            }
        }
        respawnTimers = respawnTimers.copyOf(keep)
    }

    override fun onTimeUp() {
        leftPointer = -1L
        rightPointer = -1L
    }

    override fun isSettled(): Boolean = state == State.IDLE && held == null

    override fun ticketsFor(score: Int): Int = ClawTuning.BASE_TICKETS + score / ClawTuning.POINTS_PER_TICKET

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        val inLeft = x in LEFT_X..LEFT_X + BTN_SIZE && y in BTN_Y..BTN_Y + BTN_SIZE
        val inRight = x in RIGHT_X..RIGHT_X + BTN_SIZE && y in BTN_Y..BTN_Y + BTN_SIZE
        val dx = x - DROP_CX
        val dy = y - DROP_CY
        val inDrop = dx * dx + dy * dy < (DROP_R + 10f) * (DROP_R + 10f)
        when (type) {
            TouchType.DOWN -> {
                if (inLeft) {
                    leftPointer = id; play(Sfx.BLIP, 0.4f)
                }
                if (inRight) {
                    rightPointer = id; play(Sfx.BLIP, 0.4f)
                }
                if (inDrop) pressDrop()
            }
            TouchType.MOVE -> {
                if (leftPointer == id && !inLeft) leftPointer = -1L
                if (rightPointer == id && !inRight) rightPointer = -1L
                if (inLeft && leftPointer < 0 && rightPointer != id) leftPointer = id
                if (inRight && rightPointer < 0 && leftPointer != id) rightPointer = id
            }
            TouchType.UP -> {
                if (leftPointer == id) leftPointer = -1L
                if (rightPointer == id) rightPointer = -1L
            }
        }
    }

    private fun pressDrop() {
        dropPressT = 0.15f
        if (state != State.IDLE || timeUp || cable > CABLE_IDLE + 2f) {
            play(Sfx.ERROR, 0.4f)
            return
        }
        trolleyV = 0f
        enter(State.DROPPING)
        play(Sfx.SELECT)
        fx.haptics.tick()
    }

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The machine in 3D. The simulation is a 2D slice through the middle of the glass box
     * (x across, y down); the world keeps x, turns y into height above the prize floor and
     * gives each prize a little depth so the pile looks full.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 340f, 860f, 180f, 215f, 0f, fovDeg = 46f, centerYFrac = 0.40f)
    }
    private val pt = FloatArray(3)

    /** World height of a simulation y. */
    private fun wy(y: Float) = FLOOR_Y - y

    /** Projects a simulation point into field units (into [pt]); used for effects. */
    private fun screenOf(x: Float, y: Float) {
        stage.toField(x, wy(y), 0f, pt)
    }

    private fun depthOf(b: Body): Float = ((System.identityHashCode(b) ushr 3) % 7 - 3) * 5f

    private val cabinet: Model by lazy {
        val b = ModelBuilder()
        val cab = ClawArt.cabinet.full
        val top = wy(BOX_TOP) + 60f
        // Inside of the box.
        b.quad(LIP_X, 0f, -BOX_D, BOX_RIGHT, 0f, -BOX_D, BOX_RIGHT, 0f, BOX_D, LIP_X, 0f, BOX_D, ClawArt.floor.full, 0f, 1f, 0f)
        b.quad(BOX_LEFT, top, -BOX_D, BOX_RIGHT, top, -BOX_D, BOX_RIGHT, -PIT_D, -BOX_D, BOX_LEFT, -PIT_D, -BOX_D, ClawArt.backWall.full, 0f, 0f, 1f)
        b.quad(BOX_LEFT, top, BOX_D, BOX_LEFT, top, -BOX_D, BOX_LEFT, -PIT_D, -BOX_D, BOX_LEFT, -PIT_D, BOX_D, ClawArt.sideWall.full, 1f, 0f, 0f)
        b.quad(BOX_RIGHT, top, -BOX_D, BOX_RIGHT, top, BOX_D, BOX_RIGHT, -PIT_D, BOX_D, BOX_RIGHT, -PIT_D, -BOX_D, ClawArt.sideWall.full, -1f, 0f, 0f)
        // The prize chute: a dark pit behind a clear lip.
        b.quad(BOX_LEFT, -PIT_D, -BOX_D, LIP_X, -PIT_D, -BOX_D, LIP_X, -PIT_D, BOX_D, BOX_LEFT, -PIT_D, BOX_D, ClawArt.pit.full, 0f, 1f, 0f)
        b.quad(LIP_X, 0f, -BOX_D, LIP_X, 0f, BOX_D, LIP_X, -PIT_D, BOX_D, LIP_X, -PIT_D, -BOX_D, ClawArt.pit.full, -1f, 0f, 0f)
        // Gantry rails the trolley runs on.
        val metal = ClawArt.metal.full
        for (z in floatArrayOf(-RAIL_Z, RAIL_Z)) {
            b.box(BOX_LEFT, wy(RAIL_Y) - 3f, z - 3f, BOX_RIGHT, wy(RAIL_Y) + 3f, z + 3f, BoxFaces(front = metal, top = metal, left = metal, right = metal))
        }
        // Cabinet frame around the glass.
        b.box(-20f, -200f, BOX_D, BOX_LEFT, top + 40f, BOX_D + 12f, BoxFaces(front = cab, right = cab, top = cab))
        b.box(BOX_RIGHT, -200f, BOX_D, GAME_W + 20f, top + 40f, BOX_D + 12f, BoxFaces(front = cab, left = cab, top = cab))
        b.box(BOX_LEFT, top, BOX_D, BOX_RIGHT, top + 40f, BOX_D + 12f, BoxFaces(front = ClawArt.marquee.full, top = cab))
        b.box(BOX_LEFT, -200f, BOX_D, BOX_RIGHT, 0f, BOX_D + 12f, BoxFaces(front = ClawArt.base.full, top = cab))
        b.build()
    }
    private val marqueeFront: Model by lazy {
        val top = wy(BOX_TOP) + 60f
        ModelBuilder().quad(
            BOX_LEFT, top + 40f, BOX_D + 12.5f, BOX_RIGHT, top + 40f, BOX_D + 12.5f, BOX_RIGHT, top, BOX_D + 12.5f, BOX_LEFT, top, BOX_D + 12.5f,
            ClawArt.marquee.full, 0f, 0f, 1f, emissive = 1f,
        ).build()
    }

    private val trolleyModel: Model by lazy {
        val d = ClawArt.darkMetal.full
        ModelBuilder().box(-16f, -6f, -RAIL_Z - 6f, 16f, 10f, RAIL_Z + 6f, BoxFaces(front = d, top = ClawArt.metal.full, left = d, right = d)).build()
    }
    private fun hubModel(gold: Boolean): Model {
        val side = if (gold) ClawArt.goldMetal.full else ClawArt.metal.full
        return ModelBuilder()
            .cylinder(0f, 0f, -8f, 8f, 13f, 10, side, top = side, bottom = side)
            .cylinder(0f, 0f, 8f, 16f, 13f, 10, side, top = side, topRadius = 4f)
            .build()
    }
    private val hubSilver by lazy { hubModel(false) }
    private val hubGold by lazy { hubModel(true) }
    private fun prongModel(gold: Boolean, length: Float, thick: Float): Model {
        val t = if (gold) ClawArt.goldMetal.full else ClawArt.metal.full
        val f = BoxFaces(front = t, back = t, left = t, right = t, top = t)
        return ModelBuilder().box(-thick, -length, -thick, thick, 0f, thick, f).build()
    }
    private val prongSilver by lazy { prongModel(false, 24f, 2.5f) }
    private val prongGold by lazy { prongModel(true, 24f, 2.5f) }
    private val hookSilver by lazy { prongModel(false, 12f, 2f) }
    private val hookGold by lazy { prongModel(true, 12f, 2f) }
    private val headXf = Xform()
    private val partXf = Xform()
    private val localXf = Xform()
    private val tipXf = Xform()

    private val topLight = PointLight(180f, 470f, 20f, 1f, 0.85f, 0.95f, 620f, 0.9f)
    private val frontLight = PointLight(180f, 200f, 260f, 0.9f, 0.8f, 1f, 520f, 0.6f)
    private val chuteLight = PointLight(CHUTE_X, 60f, 20f, 1f, 0.9f, 0.3f, 220f, 0f)
    private val luckyLight = PointLight(0f, 0f, 30f, 1f, 0.8f, 0.3f, 160f, 0f)

    private val plushXf = Xform()

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.55f; l.ambG = 0.5f; l.ambB = 0.62f
        l.setDirection(0.1f, 1f, 0.7f)
        l.dirR = 0.35f; l.dirG = 0.32f; l.dirB = 0.35f
        l.points.clear()
        l.points += topLight
        l.points += frontLight
        chuteLight.intensity = chuteFlash * 2f + (if (state == State.CARRYING) 0.4f else 0f)
        if (chuteLight.intensity > 0f) l.points += chuteLight
        if (lucky) {
            luckyLight.x = clawX; luckyLight.y = wy(clawY)
            luckyLight.intensity = 0.8f + 0.3f * sin(time * 8f)
            l.points += luckyLight
        }
        r.gradient(0xFF07030E.toInt(), Pal.shade(Pal.PLUM, 0.5f))
        cabinet.draw(r)
        marqueeFront.draw(r)

        for ((i, b) in world.bodies.withIndex()) {
            val p = b.data as? Plush ?: continue
            val z = if (b === held) 0f else depthOf(b)
            val (model, radius) = Plush3D.centred(p)
            // A little turn each so the pile doesn't look stamped out.
            val turn = (hash01(i, 71) - 0.5f) * 0.9f
            plushXf.set(b.x, wy(b.y), z, yaw = turn, roll = -b.angle, scale = b.r * 1.12f / radius)
            model.draw(r, xf = plushXf)
        }
        drawClaw(r)
        drawBulbs(r)

        // Transparent layer: the chute's clear lip, glow, the front glass.
        val lip = ClawArt.lipGlass.full
        r.quad(LIP_X, wy(LIP_TOP), BOX_D, LIP_X, wy(LIP_TOP), -BOX_D, LIP_X, 0f, -BOX_D, LIP_X, 0f, BOX_D, lip, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false)
        r.quad(BOX_LEFT, wy(LIP_TOP), BOX_D - 1f, LIP_X, wy(LIP_TOP), BOX_D - 1f, LIP_X, 0f, BOX_D - 1f, BOX_LEFT, 0f, BOX_D - 1f, lip, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false)
        val blink = state == State.CARRYING && (time * 4f).toInt() % 2 == 0
        val signGlow = if (blink || chuteFlash > 0f) 1.3f else 0.5f
        r.quad(
            (BOX_LEFT + LIP_X) / 2f - 22f, wy(LIP_TOP) - 14f, BOX_D - 0.5f, (BOX_LEFT + LIP_X) / 2f + 22f, wy(LIP_TOP) - 14f, BOX_D - 0.5f,
            (BOX_LEFT + LIP_X) / 2f + 22f, wy(LIP_TOP) - 34f, BOX_D - 0.5f, (BOX_LEFT + LIP_X) / 2f - 22f, wy(LIP_TOP) - 34f, BOX_D - 0.5f,
            ClawArt.winSign.full, 0f, 0f, 1f, emissive = signGlow,
        )
        val glow = TexKit.glow.full
        if (chuteFlash > 0f) {
            r.sprite(CHUTE_X, 40f, 20f, 160f, 160f, glow, blend = Blend.ADD, emissive = 1f, alpha = chuteFlash, tint = Pal.YELLOW)
        }
        if (lucky) {
            r.sprite(clawX, wy(clawY) - 12f, 10f, 90f, 90f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.3f + 0.12f * sin(time * 8f), tint = Pal.GOLD)
        }
        // Neon tube along the top of the box.
        val neonA = 0.7f + 0.3f * sin(time * 6f)
        r.quad(BOX_LEFT, wy(BOX_TOP) + 58f, BOX_D - 2f, BOX_RIGHT, wy(BOX_TOP) + 58f, BOX_D - 2f, BOX_RIGHT, wy(BOX_TOP) + 52f, BOX_D - 2f, BOX_LEFT, wy(BOX_TOP) + 52f, BOX_D - 2f, ClawArt.neon.full, 0f, 0f, 1f, emissive = 1.3f * neonA)
        r.quad(BOX_LEFT, wy(BOX_TOP) + 70f, BOX_D - 1f, BOX_RIGHT, wy(BOX_TOP) + 70f, BOX_D - 1f, BOX_RIGHT, wy(BOX_TOP) + 30f, BOX_D - 1f, BOX_LEFT, wy(BOX_TOP) + 30f, BOX_D - 1f, glow, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.25f * neonA, tint = Pal.HOTPINK)
        r.quad(BOX_LEFT, wy(BOX_TOP) + 60f, BOX_D, BOX_RIGHT, wy(BOX_TOP) + 60f, BOX_D, BOX_RIGHT, 0f, BOX_D, BOX_LEFT, 0f, BOX_D, ClawArt.glass.full, 0f, 0f, 1f, blend = Blend.ALPHA)
        r.quad(BOX_LEFT, wy(BOX_TOP) + 60f, BOX_D + 0.5f, BOX_RIGHT, wy(BOX_TOP) + 60f, BOX_D + 0.5f, BOX_RIGHT, 0f, BOX_D + 0.5f, BOX_LEFT, 0f, BOX_D + 0.5f, ClawArt.glare.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.3f)
        stage.present()

        if (bannerT > 0f) {
            val a = clamp01(bannerT / 0.3f)
            val pulse = 1f + 0.08f * sin(time * 12f)
            ArcadeFont.drawCentered(scope, bannerText, GAME_W / 2f + 30f, 110f, 4f * pulse, bannerColor, a)
        } else if (lucky && state == State.IDLE) {
            ArcadeFont.drawCentered(scope, "${ArcadeFont.STAR} LUCKY CLAW ${ArcadeFont.STAR}", GAME_W / 2f + 30f, 110f, 3f, Color(Pal.GOLD), 0.6f + 0.4f * abs(sin(time * 5f)))
        }
        drawControls(scope)
    }

    private fun drawClaw(r: Renderer3D) {
        val cx = clawX
        val cy = wy(clawY)
        val railY = wy(RAIL_Y)
        partXf.set(trolleyX, railY, 0f)
        trolleyModel.draw(r, xf = partXf)
        r.sprite(trolleyX, railY + 2f, RAIL_Z + 7f, 6f, 5f, TexKit.white.full, emissive = 1.2f, tint = if (state == State.IDLE) Pal.LIME else Pal.RED)
        r.beam(trolleyX, railY - 4f, 0f, cx, cy + 14f, 0f, 2.5f, ClawArt.metal.full)
        // The head swings with the pendulum; each prong hinges outward by the claw's openness.
        headXf.set(cx, cy, 0f, roll = theta)
        (if (lucky) hubGold else hubSilver).draw(r, xf = headXf)
        val spread = (-6f + openness * 46f) * (PI.toFloat() / 180f)
        val prong = if (lucky) prongGold else prongSilver
        for (k in 0 until 3) {
            val yaw = PI.toFloat() / 2f + k * (2f * PI.toFloat() / 3f)
            localXf.set(cos(yaw) * 10f, -6f, -sin(yaw) * 10f, yaw = yaw, roll = spread)
            partXf.setProduct(headXf, localXf)
            prong.draw(r, xf = partXf)
            // The hooked tip bends back inwards.
            tipXf.set(0f, -24f, 0f, roll = -0.95f)
            localXf.setProduct(partXf, tipXf)
            (if (lucky) hookGold else hookSilver).draw(r, xf = localXf)
        }
    }

    private fun drawBulbs(r: Renderer3D) {
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        val top = wy(BOX_TOP) + 60f
        for (s in 0..1) {
            val x = if (s == 0) (BOX_LEFT - 20f) / 2f else (BOX_RIGHT + GAME_W + 20f) / 2f
            for (i in 0 until 12) {
                val y = top + 20f - i * (top + 150f) / 11f
                val on = ((time * 5f).toInt() + i) % 3 == 0
                r.sprite(x, y, BOX_D + 13f, 7f, 7f, TexKit.dot.full, emissive = 1.2f, tint = if (on) Pal.YELLOW else Pal.shade(Pal.GOLD, 0.45f))
                if (on) r.sprite(x, y, BOX_D + 14f, 26f, 26f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.GOLD)
            }
        }
    }

    private fun drawControls(scope: DrawScope) {
        with(scope) {
            drawRect(Color.Black, Offset(0f, 492f), Size(GAME_W, GAME_H - 492f + 40f), alpha = 0.3f)
            drawRect(Color(Pal.HOTPINK), Offset(0f, 492f), Size(GAME_W, 3f), alpha = 0.8f)
            val enabled = state == State.IDLE && !timeUp
            arcadeButton(this, LEFT_X, BTN_Y, leftPointer >= 0, enabled, "${ArcadeFont.LEFT}")
            arcadeButton(this, RIGHT_X, BTN_Y, rightPointer >= 0, enabled, "${ArcadeFont.RIGHT}")

            val pressed = dropPressT > 0f
            val canDrop = enabled && cable <= CABLE_IDLE + 2f
            val base = if (canDrop) Pal.RED else Pal.DARKRED
            val press = if (pressed) 5f else 0f
            drawCircle(Color(Pal.BLACK), DROP_R + 6f, Offset(DROP_CX, DROP_CY + 8f), alpha = 0.5f)
            drawCircle(Color(Pal.shade(base, 0.6f)), DROP_R, Offset(DROP_CX, DROP_CY + 6f))
            drawCircle(Color(base), DROP_R, Offset(DROP_CX, DROP_CY + press))
            drawCircle(Color.White, DROP_R * 0.55f, Offset(DROP_CX - 14f, DROP_CY - 16f + press), alpha = 0.18f)
            val glow = if (canDrop) 0.75f + 0.25f * sin(time * 7f) else 0.5f
            ArcadeFont.drawCentered(this, "DROP", DROP_CX, DROP_CY - 10f + press, 4f, Color.White, glow)
            ArcadeFont.drawCentered(this, "WON: $wonCount", 116f, 618f, 2f, Color(Pal.YELLOW))
        }
    }

    private fun arcadeButton(scope: DrawScope, x: Float, y: Float, pressed: Boolean, enabled: Boolean, label: String) {
        with(scope) {
            val base = if (enabled) Pal.CYAN else Pal.TEAL
            val press = if (pressed) 5f else 0f
            drawRoundRect(Color(Pal.BLACK), Offset(x - 3f, y + 3f), Size(BTN_SIZE + 6f, BTN_SIZE + 6f), CornerRadius(16f, 16f), alpha = 0.5f)
            drawRoundRect(Color(Pal.shade(base, 0.55f)), Offset(x, y + 6f), Size(BTN_SIZE, BTN_SIZE), CornerRadius(14f, 14f))
            drawRoundRect(Color(if (pressed) Pal.shade(base, 0.85f) else base), Offset(x, y + press), Size(BTN_SIZE, BTN_SIZE - 6f), CornerRadius(14f, 14f))
            ArcadeFont.drawCentered(this, label, x + BTN_SIZE / 2f, y + 22f + press, 6f, Color(Pal.NAVY), shadow = false)
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** Grabs tried, grabs planned to hold, slips, prizes won (for tuning). */
    internal val botCounters = IntArray(4)

    internal val botReady: Boolean get() = state == State.IDLE && !timeUp && cable <= CABLE_IDLE + 2f
    internal val botTrolleyX: Float get() = trolleyX
    internal val botSwing: Float get() = abs(theta) * cable + abs(thetaV) * 4f

    /** (x, top y) of every loose prize. */
    internal fun botPrizes(): List<Pair<Float, Float>> =
        world.bodies.filter { it.data is Plush && !it.kinematic }.map { it.x to it.y - it.r }

    /** Centres of the LEFT, RIGHT and DROP buttons as x0, y0, x1, y1, x2, y2. */
    internal fun botButtons(): FloatArray = floatArrayOf(
        LEFT_X + BTN_SIZE / 2f, BTN_Y + BTN_SIZE / 2f,
        RIGHT_X + BTN_SIZE / 2f, BTN_Y + BTN_SIZE / 2f,
        DROP_CX, DROP_CY,
    )

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.PLUM))
        val colors = intArrayOf(Pal.BROWN, Pal.LIME, Pal.YELLOW, Pal.PINK, Pal.SKY, Pal.WHITE, Pal.GREEN)
        for (i in 0 until w / 3) {
            val c = colors[i % colors.size]
            val yy = h - 3 - (i % 2)
            p.fill(i * 3f + 1f, yy.toFloat(), 3f, 3f, Color(c))
        }
        val cycle = time % 4f
        val clawXPos = w / 2f + sin(time * 1.3f) * (w / 2f - 4f)
        val dropDepth = when {
            cycle < 2.5f -> 0f
            cycle < 3.1f -> (cycle - 2.5f) / 0.6f
            else -> 1f - (cycle - 3.1f) / 0.9f
        }
        val clawYPos = 2f + dropDepth * (h - 8f)
        p.fill(0, 0, w, 1, Color(Pal.GRAY))
        p.fill(clawXPos, 1f, 1f, clawYPos - 1f, Color(Pal.LIGHTGRAY))
        p.fill(clawXPos - 1f, clawYPos, 3f, 1f, Color(Pal.LIGHTGRAY))
        p.px(clawXPos - 1f, clawYPos + 1f, Color(Pal.LIGHTGRAY))
        p.px(clawXPos + 1f, clawYPos + 1f, Color(Pal.LIGHTGRAY))
        if ((time * 2f).toInt() % 2 == 0) p.textCentered("CLAW", w / 2f, 2f, Color(Pal.YELLOW), tiny = true)
    }
}
