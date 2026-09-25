package com.pocketarcade.games.claw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.Plush
import com.pocketarcade.engine.Body
import com.pocketarcade.engine.CircleWorld
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.PixelPainter
import com.pocketarcade.engine.Segment
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.approach
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.drawPixelImage
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
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
        "HOLD ${PixelFont.LEFT} ${PixelFont.RIGHT} TO MOVE THE CLAW",
        "TAP DROP TO GRAB",
        "CENTER THE GRAB FOR",
        "A STRONGER GRIP",
        "WATCH THE SWING!",
    )
    override val look = CabinetLook(body = Pal.PINK, trim = Pal.YELLOW, glow = Pal.HOTPINK, shape = CabinetShape.WIDE)
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
            particles.spawn(
                cx + rng.range(-16f, 16f), cy + rng.range(-6f, 30f), rng.range(-20f, 20f), rng.range(-30f, 10f),
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
            particles.burst(clawX, clawY + 10f, 30, 60f, 200f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.8f, 4f, kind = Particles.SPARKLE)
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
        particles.burst(b.x, b.y - b.r, 8, 40f, 120f, intArrayOf(Pal.WHITE, Pal.LAVENDER), 0.4f, 3f)
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
                addScore(p.points, CHUTE_X, LIP_TOP - 30f, Color(Pal.YELLOW))
                banner(if (p.rare) "RARE PRIZE!" else "GOT IT!", Color(if (p.rare) Pal.GOLD else Pal.LIME))
                popups.add(p.name, CHUTE_X + 60f, LIP_TOP - 60f, Color.White, size = 2f, life = 1.4f)
                play(if (p.rare) Sfx.JACKPOT else Sfx.PRIZE)
                fx.haptics.win()
                shake.add(0.35f)
                particles.confetti(BOX_LEFT, BOX_TOP, BOX_RIGHT - BOX_LEFT, 50)
                particles.burst(CHUTE_X, LIP_TOP + 30f, 30, 80f, 260f, intArrayOf(Pal.YELLOW, Pal.PINK, Pal.CYAN, Pal.WHITE), 0.7f, 5f, grav = 300f)
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

    // ---------------------------------------------------------------- drawing

    override fun render(scope: DrawScope) {
        with(scope) {
            drawRect(Color(Pal.NIGHT), Offset(-40f, -40f), Size(GAME_W + 80f, GAME_H + 80f))
            // Back wall of the glass box with a starry print.
            drawRect(Color(Pal.PLUM), Offset(BOX_LEFT, BOX_TOP), Size(BOX_RIGHT - BOX_LEFT, FLOOR_Y - BOX_TOP))
            drawRect(Color(Pal.INDIGO), Offset(BOX_LEFT, BOX_TOP), Size(BOX_RIGHT - BOX_LEFT, 90f), alpha = 0.6f)
            for (i in 0 until 40) {
                val sx = BOX_LEFT + hash01(i, 3) * (BOX_RIGHT - BOX_LEFT)
                val sy = BOX_TOP + hash01(i, 7) * (FLOOR_Y - BOX_TOP - 60f)
                val tw = 0.3f + 0.7f * abs(sin(time * 2f + i))
                drawRect(Color(Pal.LAVENDER), Offset(sx, sy), Size(3f, 3f), alpha = 0.25f * tw)
            }
            // Neon strip along the top of the box.
            val neon = 0.7f + 0.3f * sin(time * 6f)
            drawRect(Color(Pal.HOTPINK), Offset(BOX_LEFT, BOX_TOP), Size(BOX_RIGHT - BOX_LEFT, 5f), alpha = neon)
            drawRect(Color(Pal.HOTPINK), Offset(BOX_LEFT, BOX_TOP + 5f), Size(BOX_RIGHT - BOX_LEFT, 12f), alpha = 0.15f * neon)

            // Prize chute.
            drawRect(Color(Pal.BLACK), Offset(BOX_LEFT, LIP_TOP), Size(LIP_X - BOX_LEFT, FLOOR_Y - LIP_TOP + 20f))
            val arrowBlink = state == State.CARRYING && (time * 4f).toInt() % 2 == 0
            PixelFont.drawCentered(this, "WIN", (BOX_LEFT + LIP_X) / 2f, LIP_TOP + 20f, 3f, Color(if (arrowBlink || chuteFlash > 0f) Pal.YELLOW else Pal.DARKGRAY), shadow = false)
            PixelFont.drawCentered(this, "${PixelFont.DOWN}", (BOX_LEFT + LIP_X) / 2f, LIP_TOP + 48f, 3f, Color(if (arrowBlink || chuteFlash > 0f) Pal.YELLOW else Pal.DARKGRAY), shadow = false)
            if (chuteFlash > 0f) drawRect(Color(Pal.YELLOW), Offset(BOX_LEFT, LIP_TOP), Size(LIP_X - BOX_LEFT, FLOOR_Y - LIP_TOP), alpha = chuteFlash * 0.4f)

            // Floor of the pile.
            drawRect(Color(Pal.VIOLET), Offset(LIP_X, FLOOR_Y), Size(BOX_RIGHT - LIP_X, 10f))

            // Rail and trolley.
            drawRect(Color(Pal.DARKGRAY), Offset(20f, 18f), Size(320f, 12f))
            drawRect(Color(Pal.GRAY), Offset(20f, 18f), Size(320f, 3f))
            for (i in 0..8) drawRect(Color(Pal.BLACK), Offset(28f + i * 38f, 24f), Size(3f, 3f))

            // Prizes (held prize is drawn with the claw so it stays in front of the prongs' back).
            for (b in world.bodies) {
                if (b === held) continue
                drawPlush(this, b)
            }
            drawClaw(this)
            held?.let { drawPlush(this, it) }
            drawClawFront(this)

            // Plexiglass lip over the chute and glass reflections.
            drawRect(Color(Pal.LIGHTGRAY), Offset(LIP_X - 5f, LIP_TOP), Size(7f, FLOOR_Y - LIP_TOP), alpha = 0.45f)
            drawRect(Color.White, Offset(LIP_X - 4f, LIP_TOP), Size(2f, FLOOR_Y - LIP_TOP), alpha = 0.6f)
            for (k in 0 until 3) {
                val gx = 70f + k * 110f
                drawLine(Color.White, Offset(gx, BOX_TOP), Offset(gx + 90f, FLOOR_Y), strokeWidth = 14f - k * 4f, alpha = 0.05f)
            }
            // Glass frame.
            val frame = Color(Pal.PINK)
            drawRect(frame, Offset(0f, 34f), Size(BOX_LEFT, FLOOR_Y + 20f - 34f))
            drawRect(frame, Offset(BOX_RIGHT, 34f), Size(GAME_W - BOX_RIGHT, FLOOR_Y + 20f - 34f))
            drawRect(frame, Offset(0f, FLOOR_Y + 10f), Size(GAME_W, 12f))
            drawRect(Color(Pal.HOTPINK), Offset(0f, FLOOR_Y + 10f), Size(GAME_W, 3f))

            if (bannerT > 0f) {
                val a = clamp01(bannerT / 0.3f)
                val pulse = 1f + 0.08f * sin(time * 12f)
                PixelFont.drawCentered(this, bannerText, GAME_W / 2f + 30f, 110f, 4f * pulse, bannerColor, a)
            } else if (lucky && state == State.IDLE) {
                PixelFont.drawCentered(this, "${PixelFont.STAR} LUCKY CLAW ${PixelFont.STAR}", GAME_W / 2f + 30f, 110f, 3f, Color(Pal.GOLD), 0.6f + 0.4f * abs(sin(time * 5f)))
            }

            drawControls(this)
        }
    }

    private fun drawPlush(scope: DrawScope, b: Body) {
        val p = b.data as? Plush ?: return
        val img = PlushArt.image(p)
        val size = b.r * 2.3f
        val scale = size / PlushArt.SIZE
        val deg = b.angle * 180f / PI.toFloat()
        scope.rotate(deg, Offset(b.x, b.y)) {
            drawPixelImage(img, b.x - size / 2f, b.y - size / 2f, scale)
        }
    }

    private fun clawColors(): Pair<Color, Color> =
        if (lucky) Color(Pal.GOLD) to Color(Pal.ORANGE) else Color(Pal.LIGHTGRAY) to Color(Pal.GRAY)

    private fun drawClaw(scope: DrawScope) {
        val cx = clawX
        val cy = clawY
        val (metal, dark) = clawColors()
        scope.drawLine(Color(Pal.LIGHTGRAY), Offset(trolleyX, RAIL_Y + 4f), Offset(cx, cy - 6f), strokeWidth = 2.5f)
        scope.drawRect(Color(Pal.GRAY), Offset(trolleyX - 16f, 14f), Size(32f, 18f))
        scope.drawRect(Color(Pal.LIGHTGRAY), Offset(trolleyX - 16f, 14f), Size(32f, 4f))
        scope.drawRect(Color(if (state == State.IDLE) Pal.LIME else Pal.RED), Offset(trolleyX - 3f, 22f), Size(6f, 5f))
        val deg = -theta * 180f / PI.toFloat()
        scope.withTransform({ rotate(deg, Offset(cx, cy)) }) {
            // Back prong.
            prong(this, cx, cy, 0f, dark, back = true)
        }
        if (lucky) {
            scope.drawCircle(Color(Pal.GOLD), 34f, Offset(cx, cy + 12f), alpha = 0.18f + 0.1f * sin(time * 8f))
        }
        scope.withTransform({ rotate(deg, Offset(cx, cy)) }) {
            drawRoundRect(metal, Offset(cx - 15f, cy - 9f), Size(30f, 16f), CornerRadius(5f, 5f))
            drawRect(dark, Offset(cx - 15f, cy + 3f), Size(30f, 4f))
            drawRect(Color.White, Offset(cx - 11f, cy - 7f), Size(10f, 3f), alpha = 0.7f)
        }
    }

    private fun drawClawFront(scope: DrawScope) {
        val cx = clawX
        val cy = clawY
        val (metal, _) = clawColors()
        val deg = -theta * 180f / PI.toFloat()
        scope.withTransform({ rotate(deg, Offset(cx, cy)) }) {
            prong(this, cx, cy, -1f, metal, back = false)
            prong(this, cx, cy, 1f, metal, back = false)
        }
    }

    private fun prong(scope: DrawScope, cx: Float, cy: Float, side: Float, color: Color, back: Boolean) {
        val spread = (-6f + openness * 46f) * (PI.toFloat() / 180f)
        val baseX = cx + side * 10f
        val baseY = cy + 6f
        val len = if (back) 20f else 22f
        val ang = if (back) 0f else spread * side
        val midX = baseX + sin(ang) * len
        val midY = baseY + cos(ang) * len
        val hookAng = ang - side * 0.9f
        val tipX = midX + sin(hookAng) * 10f
        val tipY = midY + cos(hookAng) * 10f
        scope.drawLine(color, Offset(baseX, baseY), Offset(midX, midY), strokeWidth = 5f, cap = StrokeCap.Round)
        scope.drawLine(color, Offset(midX, midY), Offset(tipX, tipY), strokeWidth = 5f, cap = StrokeCap.Round)
    }

    private fun drawControls(scope: DrawScope) {
        with(scope) {
            drawRect(Color(Pal.shade(Pal.PINK, 0.55f)), Offset(0f, 492f), Size(GAME_W, GAME_H - 492f + 40f))
            drawRect(Color(Pal.shade(Pal.PINK, 0.8f)), Offset(0f, 492f), Size(GAME_W, 6f))
            val enabled = state == State.IDLE && !timeUp
            arcadeButton(this, LEFT_X, BTN_Y, leftPointer >= 0, enabled, "${PixelFont.LEFT}")
            arcadeButton(this, RIGHT_X, BTN_Y, rightPointer >= 0, enabled, "${PixelFont.RIGHT}")

            val pressed = dropPressT > 0f
            val canDrop = enabled && cable <= CABLE_IDLE + 2f
            val base = if (canDrop) Pal.RED else Pal.DARKRED
            val press = if (pressed) 5f else 0f
            drawCircle(Color(Pal.BLACK), DROP_R + 6f, Offset(DROP_CX, DROP_CY + 8f), alpha = 0.5f)
            drawCircle(Color(Pal.shade(base, 0.6f)), DROP_R, Offset(DROP_CX, DROP_CY + 6f))
            drawCircle(Color(base), DROP_R, Offset(DROP_CX, DROP_CY + press))
            drawCircle(Color.White, DROP_R * 0.55f, Offset(DROP_CX - 14f, DROP_CY - 16f + press), alpha = 0.18f)
            val glow = if (canDrop) 0.75f + 0.25f * sin(time * 7f) else 0.5f
            PixelFont.drawCentered(this, "DROP", DROP_CX, DROP_CY - 10f + press, 4f, Color.White, glow)
            PixelFont.drawCentered(this, "WON: $wonCount", 116f, 618f, 2f, Color(Pal.YELLOW))
        }
    }

    private fun arcadeButton(scope: DrawScope, x: Float, y: Float, pressed: Boolean, enabled: Boolean, label: String) {
        with(scope) {
            val base = if (enabled) Pal.CYAN else Pal.TEAL
            val press = if (pressed) 5f else 0f
            drawRoundRect(Color(Pal.BLACK), Offset(x - 3f, y + 3f), Size(BTN_SIZE + 6f, BTN_SIZE + 6f), CornerRadius(16f, 16f), alpha = 0.5f)
            drawRoundRect(Color(Pal.shade(base, 0.55f)), Offset(x, y + 6f), Size(BTN_SIZE, BTN_SIZE), CornerRadius(14f, 14f))
            drawRoundRect(Color(if (pressed) Pal.shade(base, 0.85f) else base), Offset(x, y + press), Size(BTN_SIZE, BTN_SIZE - 6f), CornerRadius(14f, 14f))
            PixelFont.drawCentered(this, label, x + BTN_SIZE / 2f, y + 22f + press, 6f, Color(Pal.NAVY), shadow = false)
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

    override fun drawAttract(p: PixelPainter, w: Int, h: Int, time: Float) {
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
