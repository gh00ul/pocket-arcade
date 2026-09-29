package com.pocketarcade.games.airhockey

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.len
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Difficulty and payout knobs for air hockey. */
object HockeyTuning {
    const val ROUND_SECONDS = 60f
    /** First to this many goals ends the match early. */
    const val GOALS_TO_WIN = 7
    const val GOAL_POINTS = 100
    /** Extra points per goal for each goal in a row. */
    const val STREAK_BONUS = 25
    const val WIN_BONUS = 300
    const val PUCK_MAX_SPEED = 1300f
    /** Fraction of puck speed kept per second (the air cushion is nearly frictionless). */
    const val PUCK_DRAG = 0.45f
    const val WALL_BOUNCE = 0.9f
    const val MALLET_BOUNCE = 0.85f
    const val PLAYER_MALLET_SPEED = 2200f
    /** CPU mallet top speed at the start and once the player pulls ahead. */
    const val CPU_SPEED_START = 360f
    const val CPU_SPEED_MAX = 680f
    /** Seconds the CPU takes to react to where the puck is. */
    const val CPU_REACTION = 0.16f
    const val POINTS_PER_TICKET = 50
    const val BASE_TICKETS = 2
}

class AirHockeyGame : BaseMiniGame() {
    override val id = "airhockey"
    override val title = "AIR HOCKEY"
    override val marquee = "HOCKEY"
    override val instructions = listOf(
        "DRAG YOUR MALLET",
        "SMASH THE PUCK INTO",
        "THE FAR GOAL",
        "GOALS IN A ROW SCORE MORE",
        "FIRST TO 7 WINS!",
    )
    override val look = CabinetLook(body = Pal.SKY, trim = Pal.WHITE, glow = Pal.CYAN, shape = CabinetShape.AIR_HOCKEY)
    override val roundSeconds = HockeyTuning.ROUND_SECONDS

    private companion object {
        // Rink, in world units (x across, y from the CPU's end to the player's end).
        const val RL = HockeyGeo.RL
        const val RR = HockeyGeo.RR
        const val RT = HockeyGeo.RT
        const val RB = HockeyGeo.RB
        const val CX = HockeyGeo.CX
        const val CY = HockeyGeo.CY
        const val GOAL_HALF = HockeyGeo.GOAL_HALF
        const val PUCK_R = HockeyGeo.PUCK_R
        const val MALLET_R = HockeyGeo.MALLET_R
        const val RAIL_H = HockeyGeo.RAIL_H
        const val SUBSTEPS = 4
        /** Radius of the table's rounded corners, which steer pucks back into play. */
        const val CORNER_R = HockeyGeo.CORNER_R
        /** Height of the mallet's grip plane, where touches land. */
        const val MALLET_H = HockeyGeo.MALLET_H
    }

    private class Disc {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
    }

    private val puck = Disc()
    private val me = Disc()
    private val cpu = Disc()
    private var targetX = CX
    private var targetY = RB - 60f
    private var dragging = -1L
    private var playerGoals = 0
    private var cpuGoals = 0
    private var streak = 0
    private var serveT = 0f
    private var serveToCpu = false
    private var puckLive = false
    private var goalFlash = 0f
    private var goalByPlayer = false
    private var cpuSeeX = CX
    private var cpuSeeY = CY
    private var cpuSeeVX = 0f
    private var cpuSeeVY = 0f
    private var cpuThinkT = 0f
    private var hitCooldown = 0f
    private var stuckT = 0f

    /** All the presentation state (glows, the puck's trail); the simulation never reads it. */
    private val scene = HockeyScene()

    override fun reset() {
        me.x = CX; me.y = RB - 60f; me.vx = 0f; me.vy = 0f
        cpu.x = CX; cpu.y = RT + 60f; cpu.vx = 0f; cpu.vy = 0f
        targetX = me.x
        targetY = me.y
        dragging = -1L
        playerGoals = 0
        cpuGoals = 0
        streak = 0
        goalFlash = 0f
        goalByPlayer = false
        // PLAY AGAIN reuses this instance: clear the CPU's view of the puck and every timer too,
        // so a replay plays out exactly like a fresh machine.
        cpuSeeX = CX; cpuSeeY = CY; cpuSeeVX = 0f; cpuSeeVY = 0f
        cpuThinkT = 0f
        hitCooldown = 0f
        stuckT = 0f
        scene.reset()
        serve(toCpu = false, delay = 0.6f)
    }

    private fun serve(toCpu: Boolean, delay: Float) {
        serveToCpu = toCpu
        serveT = delay
        puckLive = false
        puck.x = CX + rng.range(-30f, 30f)
        puck.y = if (toCpu) CY - 110f else CY + 110f
        puck.vx = 0f
        puck.vy = 0f
    }

    override fun ticketsFor(score: Int): Int = HockeyTuning.BASE_TICKETS + score / HockeyTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = goalFlash <= 0.3f

    /** The buzzer freezes the player's mallet; a puck still in play can no longer score (see [goal]). */
    override fun onTimeUp() {
        cancelInput()
    }

    /** Lets go of the mallet: it stops where it is instead of chasing the lost finger. */
    override fun cancelInput() {
        dragging = -1L
        targetX = me.x
        targetY = me.y
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> if (dragging < 0 && !timeUp && !endedEarly) {
                dragging = id
                aimAt(x, y)
            }
            TouchType.MOVE -> if (id == dragging) aimAt(x, y)
            TouchType.UP -> if (id == dragging) dragging = -1L
        }
    }

    /** The mallet chases the spot on the table under the finger. */
    private fun aimAt(fx: Float, fy: Float) {
        if (stage.touchToPlane(fx, fy, MALLET_H, pt)) {
            targetX = pt[0]
            targetY = pt[1]
        }
    }

    override fun step(dt: Float) {
        goalFlash = (goalFlash - dt).coerceAtLeast(0f)
        hitCooldown -= dt
        if (!puckLive) {
            serveT -= dt
            if (serveT <= 0f && !timeUp && !endedEarly) {
                puckLive = true
                play(Sfx.BLIP, 0.5f, 1.3f)
            }
        }
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) { subStep(h) }
        // A short glowing trail behind a fast puck (visual only).
        scene.step(dt)
        scene.trail(puck.x, puck.y, dt)
    }

    private fun subStep(h: Float) {
        // Player mallet: rate-limited chase of the finger, kept in the near half.
        val tx = targetX.coerceIn(RL + MALLET_R, RR - MALLET_R)
        val ty = targetY.coerceIn(CY + MALLET_R, RB - MALLET_R)
        moveMallet(me, tx, ty, HockeyTuning.PLAYER_MALLET_SPEED, h)
        cpuThink(h)

        if (!puckLive) return
        puck.x += puck.vx * h
        puck.y += puck.vy * h
        val drag = 1f - HockeyTuning.PUCK_DRAG * h
        puck.vx *= drag
        puck.vy *= drag

        // Side rails.
        if (puck.x < RL + PUCK_R) {
            puck.x = RL + PUCK_R; puck.vx = abs(puck.vx) * HockeyTuning.WALL_BOUNCE; clack(puck.vx)
        }
        if (puck.x > RR - PUCK_R) {
            puck.x = RR - PUCK_R; puck.vx = -abs(puck.vx) * HockeyTuning.WALL_BOUNCE; clack(puck.vx)
        }
        // End rails, with a goal slot in the middle of each.
        val inMouth = abs(puck.x - CX) < GOAL_HALF - PUCK_R * 0.3f
        if (puck.y < RT + PUCK_R) {
            if (inMouth) {
                if (puck.y < RT - PUCK_R) goal(byPlayer = true)
            } else {
                puck.y = RT + PUCK_R; puck.vy = abs(puck.vy) * HockeyTuning.WALL_BOUNCE; clack(puck.vy)
            }
        }
        if (puck.y > RB - PUCK_R) {
            if (inMouth) {
                if (puck.y > RB + PUCK_R) goal(byPlayer = false)
            } else {
                puck.y = RB - PUCK_R; puck.vy = -abs(puck.vy) * HockeyTuning.WALL_BOUNCE; clack(puck.vy)
            }
        }
        if (!puckLive) return
        corners()
        collide(me)
        collide(cpu)
        // A mallet can shove the puck into a rail; keep it on the table.
        puck.x = puck.x.coerceIn(RL + PUCK_R, RR - PUCK_R)
        if (abs(puck.x - CX) >= GOAL_HALF - PUCK_R * 0.3f) puck.y = puck.y.coerceIn(RT + PUCK_R, RB - PUCK_R)
        val sp = len(puck.vx, puck.vy)
        stuckT = if (sp < 60f) stuckT + h else 0f
        if (sp > HockeyTuning.PUCK_MAX_SPEED) {
            puck.vx *= HockeyTuning.PUCK_MAX_SPEED / sp
            puck.vy *= HockeyTuning.PUCK_MAX_SPEED / sp
        }
    }

    /** Bounces the puck off the four rounded corners of the rink. */
    private fun corners() {
        val cx = if (puck.x < CX) RL + CORNER_R else RR - CORNER_R
        val cy = if (puck.y < CY) RT + CORNER_R else RB - CORNER_R
        val inX = if (puck.x < CX) puck.x < cx else puck.x > cx
        val inY = if (puck.y < CY) puck.y < cy else puck.y > cy
        if (!inX || !inY) return
        val dx = puck.x - cx
        val dy = puck.y - cy
        val d = len(dx, dy)
        val maxD = CORNER_R - PUCK_R
        if (d <= maxD || d < 1e-3f) return
        val nx = dx / d
        val ny = dy / d
        puck.x = cx + nx * maxD
        puck.y = cy + ny * maxD
        val vn = puck.vx * nx + puck.vy * ny
        if (vn > 0f) {
            puck.vx -= (1f + HockeyTuning.WALL_BOUNCE) * vn * nx
            puck.vy -= (1f + HockeyTuning.WALL_BOUNCE) * vn * ny
            clack(vn)
        }
    }

    private fun moveMallet(m: Disc, tx: Float, ty: Float, maxSpeed: Float, h: Float) {
        val dx = tx - m.x
        val dy = ty - m.y
        val d = len(dx, dy)
        val step = maxSpeed * h
        val nx: Float
        val ny: Float
        if (d <= step) {
            nx = tx; ny = ty
        } else {
            nx = m.x + dx / d * step; ny = m.y + dy / d * step
        }
        m.vx = (nx - m.x) / h
        m.vy = (ny - m.y) / h
        m.x = nx
        m.y = ny
    }

    private fun collide(m: Disc) {
        val dx = puck.x - m.x
        val dy = puck.y - m.y
        val d = len(dx, dy)
        val minD = PUCK_R + MALLET_R
        if (d >= minD || d < 1e-3f) return
        val nx = dx / d
        val ny = dy / d
        puck.x = m.x + nx * minD
        puck.y = m.y + ny * minD
        val rel = (puck.vx - m.vx) * nx + (puck.vy - m.vy) * ny
        if (rel < 0f) {
            val e = HockeyTuning.MALLET_BOUNCE
            puck.vx -= (1f + e) * rel * nx
            puck.vy -= (1f + e) * rel * ny
            if (hitCooldown <= 0f) {
                hitCooldown = 0.08f
                val power = clamp01(-rel / 900f)
                play(Sfx.CLINK, 0.4f + power * 0.6f, 0.7f + power * 0.6f)
                if (m === me) fx.haptics.tick()
                scene.malletHit(puck.x - nx * PUCK_R, puck.y - ny * PUCK_R, power, m === me)
                if (power > 0.6f) {
                    shake.add(0.08f)
                    stage.toField(puck.x, MALLET_H, puck.y, pt)
                    particles.burst(pt[0], pt[1], 8, 40f, 160f, intArrayOf(Pal.WHITE, Pal.CYAN), 0.3f, 3f)
                }
            }
        }
    }

    private fun clack(v: Float) {
        if (abs(v) > 120f && hitCooldown <= 0f) {
            hitCooldown = 0.05f
            play(Sfx.BOUNCE, (abs(v) / 1200f).coerceIn(0.15f, 0.6f), 1.6f)
            scene.wallHit(puck.x, puck.y, clamp01(abs(v) / 1200f))
        }
    }

    /**
     * The CPU sees the puck with a short delay, guards its goal when the puck is in the far
     * half of the table, and lines up behind it to shoot when it drifts into its own half.
     */
    private fun cpuThink(h: Float) {
        cpuThinkT -= h
        if (cpuThinkT <= 0f) {
            cpuThinkT = HockeyTuning.CPU_REACTION
            cpuSeeX = puck.x; cpuSeeY = puck.y; cpuSeeVX = puck.vx; cpuSeeVY = puck.vy
        }
        val lead = (playerGoals - cpuGoals).coerceAtLeast(0)
        val ramp = clamp01(time / roundSeconds * 0.6f + lead * 0.12f)
        val speed = lerp(HockeyTuning.CPU_SPEED_START, HockeyTuning.CPU_SPEED_MAX, ramp)
        val homeY = RT + 50f
        var tx: Float
        var ty: Float
        if (!puckLive) {
            tx = CX; ty = homeY
        } else if (stuckT > 1.2f && cpuSeeY < CY) {
            // Don't pin the puck against the rails: back off and give it room.
            tx = CX; ty = homeY
            if (stuckT > 2.2f) stuckT = 0f
        } else if (cpuSeeY < CY - 10f && len(cpuSeeVX, cpuSeeVY) < 700f) {
            // Attack: get behind the puck (on the far side from the player's goal) and drive through it.
            val gx = CX - cpuSeeX
            val gy = RB - cpuSeeY
            val gl = len(gx, gy).coerceAtLeast(1f)
            val behind = if (cpu.y < cpuSeeY - 8f) -6f else PUCK_R + MALLET_R + 6f
            tx = cpuSeeX - gx / gl * behind
            ty = cpuSeeY - gy / gl * behind
        } else {
            // Defend: stay between the puck and the goal, a little off the goal line.
            val t = clamp01((cpuSeeY - RT) / (RB - RT))
            tx = lerp(CX, cpuSeeX, 0.35f + 0.4f * (1f - t))
            ty = homeY + if (cpuSeeVY < 0f) 0f else 30f * (1f - t)
        }
        tx = tx.coerceIn(RL + MALLET_R, RR - MALLET_R)
        ty = ty.coerceIn(RT + MALLET_R, CY - MALLET_R)
        moveMallet(cpu, tx, ty, speed, h)
    }

    private fun goal(byPlayer: Boolean) {
        puckLive = false
        if (timeUp) {
            // After the buzzer the puck just drops into the slot: no score, no goal flash to wait on.
            scene.clearTrail()
            return
        }
        goalFlash = 1.2f
        goalByPlayer = byPlayer
        scene.clearTrail()
        scene.goal(byPlayer)
        if (byPlayer) {
            playerGoals++
            streak++
            val pts = HockeyTuning.GOAL_POINTS + (streak - 1) * HockeyTuning.STREAK_BONUS
            stage.toField(CX, 30f, RT, pt)
            addScore(pts, pt[0], pt[1] + 40f, Color(Pal.YELLOW))
            popups.add(if (streak >= 3) "HAT TRICK!" else "GOAL!", CX, 250f, Color(Pal.CYAN), size = 5f, life = 1.2f)
            play(Sfx.WIN)
            play(Sfx.CHEER, 0.6f)
            fx.haptics.win()
            shake.add(0.35f)
            particles.burst(pt[0], pt[1], 40, 80f, 280f, intArrayOf(Pal.CYAN, Pal.WHITE, Pal.YELLOW), 0.8f, 5f, kind = Particles.SPARKLE)
            if (playerGoals >= HockeyTuning.GOALS_TO_WIN) {
                addScore(HockeyTuning.WIN_BONUS, CX, 320f, Color(Pal.GOLD), "YOU WIN +${HockeyTuning.WIN_BONUS}")
                play(Sfx.JACKPOT)
                particles.confetti(0f, 0f, GAME_W, 90)
                endedEarly = true
            } else {
                serve(toCpu = true, delay = 1.2f)
            }
        } else {
            cpuGoals++
            streak = 0
            popups.add("CPU SCORES", CX, 420f, Color(Pal.RED), size = 3f, life = 1.1f)
            play(Sfx.BOMB, 0.5f, 1.3f)
            fx.haptics.heavy()
            shake.add(0.25f)
            if (cpuGoals >= HockeyTuning.GOALS_TO_WIN) {
                endedEarly = true
            } else {
                serve(toCpu = false, delay = 1.2f)
            }
        }
    }

    // ---------------------------------------------------------------- 3D presentation

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(CX, 560f, 800f, CX, 0f, 320f, fovDeg = 50f)
    }
    private val pt = FloatArray(3)

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        scene.light(r, puck.x, puck.y, puckLive, goalFlash, goalByPlayer)
        r.gradient(0xFF040812.toInt(), Pal.shade(Pal.NAVY, 0.7f))
        scene.updateBoard(playerGoals, cpuGoals)
        scene.drawRoom(r, time, goalFlash, goalByPlayer)
        scene.drawTable(r, time, goalFlash, goalByPlayer)
        scene.drawScoreboard(r, time, goalFlash, goalByPlayer)
        scene.drawPieces(
            r, time,
            puck.x, puck.y, puck.vx, puck.vy, puckLive || serveT < 0.9f, puckLive,
            me.x, me.y, len(me.vx, me.vy), cpu.x, cpu.y, len(cpu.vx, cpu.vy),
            serveT, !timeUp && !endedEarly,
        )
        scene.drawEffects(r)
        stage.present()

        if (dragging < 0 && !timeUp && time < 4f) {
            val a = 0.5f + 0.5f * sin(time * 6f)
            ArcadeFont.drawCentered(scope, "DRAG YOUR MALLET", GAME_W / 2f, 612f, 2f, Color.White, a)
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    internal val botPuckX get() = puck.x
    internal val botPuckY get() = puck.y
    internal val botPuckVX get() = puck.vx
    internal val botPuckVY get() = puck.vy
    internal val botMalletX get() = me.x
    internal val botMalletY get() = me.y
    internal val botCpuX get() = cpu.x
    internal val botCpuY get() = cpu.y
    internal val botGoals get() = playerGoals to cpuGoals

    /** Puts the puck in play at ([x], [y]) moving at ([vx], [vy]), for tests of edge cases. */
    internal fun botPlacePuck(x: Float, y: Float, vx: Float, vy: Float) {
        puck.x = x; puck.y = y; puck.vx = vx; puck.vy = vy
        puckLive = true
    }

    /** Where the table point ([x], [y]) is on screen, for bots that play by touch. */
    internal fun botScreen(x: Float, y: Float): Pair<Float, Float> {
        stage.toField(x, MALLET_H, y, pt)
        return pt[0] to pt[1]
    }

    // ---------------------------------------------------------------- attract mode

    /** A triangle wave: 0 to 1 and back, once per 2 units of [v]. */
    private fun tri(v: Float): Float {
        val m = ((v % 2f) + 2f) % 2f
        return if (m < 1f) m else 2f - m
    }

    /**
     * The table seen from above in miniature: neon markings on a dark surface, a puck with a hot
     * trail ricocheting between two mallets, and every seven seconds a goal in the far slot with
     * a flash, a burst and GOAL!, after an "AIR HOCKEY" title card. [w] × [h] is the cabinet's
     * small screen.
     */
    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cx = wf / 2f
        val cyc = 7f
        val tt = time % cyc
        val goalAt = 5.6f
        // The table: navy playfield in a glowing frame, a wash of each team's colour, air holes.
        p.fill(0f, 0f, wf, hf, Color(0xFF071228.toInt()))
        p.fill(1.2f, 1.2f, wf - 2.4f, hf - 2.4f, Color(0xFF0C1E44.toInt()))
        p.fill(1.2f, 1.2f, wf - 2.4f, (hf - 2.4f) / 2f, Color(Pal.PINK), 0.07f)
        p.fill(1.2f, hf / 2f, wf - 2.4f, (hf - 2.4f) / 2f, Color(Pal.CYAN), 0.07f)
        for (gy in 0 until 6) for (gx in 0 until 8) p.disc(2.4f + gx * 2.7f, 2.4f + gy * 2.6f, 0.16f, Color(0xFF2E5C96.toInt()), 0.8f)
        p.frame(0.3f, 0.3f, wf - 0.6f, hf - 0.6f, Color(Pal.SKY), 0.85f)
        // Centre line and ring, and the two goal slots with their team glow.
        p.fill(1.2f, hf / 2f - 0.25f, wf - 2.4f, 0.5f, Color(0xFFB8F4FF.toInt()), 0.9f)
        for (k in 0 until 16) {
            val a = k / 16f * 6.2832f
            p.disc(cx + cos(a) * 3.4f, hf / 2f + sin(a) * 3.4f, 0.3f, Color(0xFFB8F4FF.toInt()), 0.8f)
        }
        p.fill(cx - 3.4f, 0f, 6.8f, 1.3f, Color(0xFF020308.toInt()))
        p.fill(cx - 3.4f, hf - 1.3f, 6.8f, 1.3f, Color(0xFF020308.toInt()))
        p.fill(cx - 3.4f, 1.3f, 6.8f, 0.4f, Color(Pal.HOTPINK), 0.9f)
        p.fill(cx - 3.4f, hf - 1.7f, 6.8f, 0.4f, Color(Pal.CYAN), 0.9f)

        // The puck ricochets round the table; in the goal spell it is driven into the far slot.
        val px: Float
        val py: Float
        fun bounceX(t: Float) = 2.4f + (wf - 4.8f) * tri(t * 0.62f + 0.2f)
        fun bounceY(t: Float) = 2.8f + (hf - 5.6f) * tri(t * 0.91f + 0.35f)
        if (tt < goalAt) {
            px = bounceX(tt)
            py = bounceY(tt)
            for (k in 4 downTo 1) {
                val u = (tt - k * 0.05f).coerceAtLeast(0f)
                p.disc(bounceX(u), bounceY(u), 0.9f * (1f - k * 0.16f), Color(Pal.ORANGE), 0.5f - k * 0.09f)
            }
        } else {
            val u = clamp01((tt - goalAt) / 0.45f)
            px = lerp(bounceX(goalAt), cx, u)
            py = lerp(bounceY(goalAt), -0.8f, u)
            for (k in 4 downTo 1) {
                val uu = clamp01((tt - goalAt - k * 0.04f) / 0.45f)
                p.disc(lerp(bounceX(goalAt), cx, uu), lerp(bounceY(goalAt), -0.8f, uu), 0.9f * (1f - k * 0.16f), Color(Pal.ORANGE), 0.5f - k * 0.09f)
            }
        }
        // Mallets follow it: cyan at the near end, pink at the far.
        val meX = cx + (bounceX(tt - 0.25f) - cx) * 0.85f
        val cpuX = cx + (cx - bounceX(tt - 0.1f)) * 0.7f
        p.disc(meX, hf - 3.1f, 2.3f, Color(Pal.CYAN), 0.22f)
        p.disc(meX, hf - 3.1f, 1.5f, Color(0xFF29C8E8.toInt()))
        p.disc(meX - 0.35f, hf - 3.45f, 0.55f, Color(0xFFB8F4FF.toInt()), 0.8f)
        p.disc(cpuX, 3.1f, 2.3f, Color(Pal.PINK), 0.22f)
        p.disc(cpuX, 3.1f, 1.5f, Color(0xFFE8408C.toInt()))
        p.disc(cpuX - 0.35f, 2.75f, 0.55f, Color(0xFFFFD0E8.toInt()), 0.8f)
        p.disc(px, py, 1.05f, Color(0xFFE0421E.toInt()))
        p.disc(px, py, 0.5f, Color(0xFFFFE0B0.toInt()))

        // The goal: the far end floods cyan, sparks fly from the slot and GOAL! flashes.
        val age = tt - goalAt - 0.45f
        if (age in 0f..1.5f) {
            val fade = 1f - age / 1.5f
            p.fill(1.2f, 1.2f, wf - 2.4f, hf * 0.4f, Color(Pal.CYAN), 0.3f * fade)
            for (i in 0 until 10) {
                val a = 0.35f + i / 9f * 2.4f
                val d = age * 9f
                p.disc(cx + cos(a) * d, 0.6f + sin(a) * d, 0.35f, if (i % 2 == 0) Color.White else Color(Pal.YELLOW), fade)
            }
            if ((age * 6f).toInt() % 2 == 0) p.textCentered("GOAL!", cx, hf / 2f - 2.4f, Color(Pal.YELLOW), tiny = true, size = 0.9f)
        }
        // Title card at the top of each loop.
        if (tt < 1.6f) {
            val a = clamp01(minOf(tt / 0.2f, (1.6f - tt) / 0.4f))
            p.fill(1.2f, hf / 2f - 3.1f, wf - 2.4f, 6.2f, Color(0xFF020308.toInt()), 0.6f * a)
            p.textCentered("AIR HOCKEY", cx, hf / 2f - 1.6f, Color(Pal.CYAN), tiny = true, alpha = a, size = 0.62f)
        }
    }
}
