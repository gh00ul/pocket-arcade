package com.pocketarcade.games.airhockey

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.len
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
    override val look = CabinetLook(body = Pal.SKY, trim = Pal.WHITE, glow = Pal.CYAN, shape = CabinetShape.TABLE)
    override val roundSeconds = HockeyTuning.ROUND_SECONDS

    private companion object {
        // Rink, in world units (x across, y from the CPU's end to the player's end).
        const val RL = 40f
        const val RR = 320f
        const val RT = 60f
        const val RB = 600f
        const val CX = 180f
        const val CY = (RT + RB) / 2f
        const val GOAL_HALF = 62f
        const val PUCK_R = 13f
        const val MALLET_R = 22f
        const val RAIL_H = 12f
        const val SUBSTEPS = 4
        /** Radius of the table's rounded corners, which steer pucks back into play. */
        const val CORNER_R = 46f
        /** Height of the mallet's grip plane, where touches land. */
        const val MALLET_H = 8f
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
    private val trailX = FloatArray(10)
    private val trailY = FloatArray(10)
    private var trailN = 0
    private var trailT = 0f

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
        trailN = 0
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

    override fun onTimeUp() {
        dragging = -1L
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
        // A short glowing trail behind a fast puck.
        trailT -= dt
        if (trailT <= 0f) {
            trailT = 0.02f
            for (i in trailX.size - 1 downTo 1) {
                trailX[i] = trailX[i - 1]; trailY[i] = trailY[i - 1]
            }
            trailX[0] = puck.x; trailY[0] = puck.y
            trailN = (trailN + 1).coerceAtMost(trailX.size)
        }
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
        goalFlash = 1.2f
        goalByPlayer = byPlayer
        trailN = 0
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

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt(), "hockey").apply {
        look(CX, 560f, 800f, CX, 0f, 320f, fovDeg = 50f)
    }
    private val pt = FloatArray(3)
    private val board = HockeyArt.Scoreboard()

    private val table: Model by lazy {
        val b = ModelBuilder()
        b.quad(RL, 0f, RT, RR, 0f, RT, RR, 0f, RB, RL, 0f, RB, HockeyArt.surface((RR - RL).toInt(), (RB - RT).toInt(), GOAL_HALF).full, 0f, 1f, 0f)
        val rail = HockeyArt.rail.full
        val body = HockeyArt.body.full
        // Side rails and the end rails either side of each goal slot.
        b.box(RL - 16f, 0f, RT - 16f, RL, RAIL_H, RB + 16f, BoxFaces(top = rail, right = rail, front = rail))
        b.box(RR, 0f, RT - 16f, RR + 16f, RAIL_H, RB + 16f, BoxFaces(top = rail, left = rail, front = rail))
        for (end in 0..1) {
            val z0 = if (end == 0) RT - 16f else RB
            val z1 = z0 + 16f
            val faces = BoxFaces(top = rail, front = rail)
            b.box(RL, 0f, z0, CX - GOAL_HALF, RAIL_H, z1, faces)
            b.box(CX + GOAL_HALF, 0f, z0, RR, RAIL_H, z1, faces)
            // The goal slot: a dark pocket below the rail.
            b.quad(CX - GOAL_HALF, -30f, z0, CX + GOAL_HALF, -30f, z0, CX + GOAL_HALF, -30f, z1, CX - GOAL_HALF, -30f, z1, HockeyArt.slot.full, 0f, 1f, 0f)
            b.box(CX - GOAL_HALF, RAIL_H - 4f, z0, CX + GOAL_HALF, RAIL_H, z1, BoxFaces(top = rail, front = rail))
        }
        // Rounded corners: a curved rail filling each corner of the rink.
        val halfW = (RR - RL) / 2f
        val halfH = (RB - RT) / 2f
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            val ccx = CX + sx * (halfW - CORNER_R)
            val ccz = CY + sz * (halfH - CORNER_R)
            val kx = CX + sx * halfW
            val kz = CY + sz * halfH
            val n = 6
            for (i in 0 until n) {
                val t0 = i / n.toFloat() * (Math.PI.toFloat() / 2f)
                val t1 = (i + 1) / n.toFloat() * (Math.PI.toFloat() / 2f)
                val ax = ccx + sx * cos(t0) * CORNER_R
                val az = ccz + sz * sin(t0) * CORNER_R
                val bx = ccx + sx * cos(t1) * CORNER_R
                val bz = ccz + sz * sin(t1) * CORNER_R
                b.quad(kx, RAIL_H, kz, kx, RAIL_H, kz, ax, RAIL_H, az, bx, RAIL_H, bz, rail, 0f, 1f, 0f, cull = false)
                val tm = (t0 + t1) / 2f
                b.quad(ax, RAIL_H, az, bx, RAIL_H, bz, bx, 0f, bz, ax, 0f, az, rail, -sx * cos(tm), 0f, -sz * sin(tm), cull = false)
            }
        }
        // Table body under the rink, and the base below it.
        b.box(RL - 16f, -160f, RT - 16f, RR + 16f, 0f, RB + 16f, BoxFaces(front = body, left = body, right = body))
        // Scoreboard post behind the far goal.
        val post = HockeyArt.post.full
        b.box(CX - 6f, 0f, RT - 40f, CX + 6f, 150f, RT - 30f, BoxFaces(front = post, left = post, right = post))
        b.box(CX - 100f, 150f, RT - 44f, CX + 100f, 220f, RT - 32f, BoxFaces(top = HockeyArt.body.full, left = post, right = post, front = post))
        b.build()
    }

    private val puckModel: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, 0f, 5f, PUCK_R, 14, HockeyArt.puckSide.full, top = HockeyArt.puckTop.full).build()
    }
    private fun malletModel(side: Int, top: Int): Model {
        val s = HockeyArt.malletSide(side).full
        val t = HockeyArt.malletTop(top).full
        return ModelBuilder()
            .cylinder(0f, 0f, 0f, 8f, MALLET_R, 16, s, top = t)
            .cylinder(0f, 0f, 8f, 20f, 8f, 10, s, top = HockeyArt.knob(side).full)
            .build()
    }
    private val myMallet by lazy { malletModel(Pal.BLUE, Pal.CYAN) }
    private val cpuMallet by lazy { malletModel(Pal.DARKRED, Pal.PINK) }
    private val xf = Xform()

    private val lamp = PointLight(CX, 380f, CY, 1f, 0.95f, 0.9f, 640f, 1.05f)
    private val goalLight = PointLight(CX, 60f, RT, 0.4f, 1f, 1f, 260f, 0f)

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.5f; l.ambG = 0.52f; l.ambB = 0.62f
        l.setDirection(0f, 1f, 0.5f)
        l.dirR = 0.3f; l.dirG = 0.3f; l.dirB = 0.32f
        l.points.clear()
        l.points += lamp
        if (goalFlash > 0f) {
            goalLight.z = if (goalByPlayer) RT else RB
            if (goalByPlayer) {
                goalLight.r = 0.4f; goalLight.g = 1f; goalLight.b = 1f
            } else {
                goalLight.r = 1f; goalLight.g = 0.3f; goalLight.b = 0.3f
            }
            goalLight.intensity = clamp01(goalFlash) * 2f
            l.points += goalLight
        }
        r.gradient(0xFF040812.toInt(), Pal.shade(Pal.NAVY, 0.7f))
        table.draw(r)
        board.paint(playerGoals, cpuGoals, goalFlash > 0f && (time * 8f).toInt() % 2 == 0)
        r.quad(CX - 94f, 214f, RT - 31.5f, CX + 94f, 214f, RT - 31.5f, CX + 94f, 156f, RT - 31.5f, CX - 94f, 156f, RT - 31.5f, board.tex.full, 0f, 0f, 1f, emissive = 1f)
        drawNeon(r)

        // Shadows, then the puck and mallets.
        val sh = TexKit.shadow.full
        r.flat(puck.x + 3f, puck.y + 4f, 0.4f, PUCK_R * 2.6f, PUCK_R * 2.6f, sh, blend = Blend.ALPHA, alpha = 0.5f)
        r.flat(me.x + 4f, me.y + 5f, 0.4f, MALLET_R * 2.6f, MALLET_R * 2.6f, sh, blend = Blend.ALPHA, alpha = 0.5f)
        r.flat(cpu.x + 4f, cpu.y + 5f, 0.4f, MALLET_R * 2.6f, MALLET_R * 2.6f, sh, blend = Blend.ALPHA, alpha = 0.5f)
        if (puckLive || serveT < 0.9f) {
            xf.set(puck.x, 0f, puck.y)
            puckModel.draw(r, xf = xf)
        }
        xf.set(cpu.x, 0f, cpu.y)
        cpuMallet.draw(r, xf = xf)
        xf.set(me.x, 0f, me.y)
        myMallet.draw(r, xf = xf)

        val glow = TexKit.glow.full
        val sp = len(puck.vx, puck.vy)
        if (puckLive && sp > 300f) {
            val a = clamp01((sp - 300f) / 700f)
            for (i in 1 until trailN) {
                val k = 1f - i / trailN.toFloat()
                r.flat(trailX[i], trailY[i], 1f, PUCK_R * 3f * k, PUCK_R * 3f * k, glow, blend = Blend.ADD, emissive = 1f, alpha = a * k * 0.6f, tint = Pal.CYAN)
            }
        }
        if (!puckLive && serveT > 0f && !timeUp && !endedEarly) {
            val blink = 0.5f + 0.5f * sin(time * 12f)
            r.flat(puck.x, puck.y, 1f, 60f, 60f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.4f * blink, tint = Pal.WHITE)
        }
        stage.present(scope)

        if (dragging < 0 && !timeUp && time < 4f) {
            val a = 0.5f + 0.5f * sin(time * 6f)
            PixelFont.drawCentered(scope, "DRAG YOUR MALLET", GAME_W / 2f, 612f, 2f, Color.White, a)
        }
    }

    private fun drawNeon(r: Renderer3D) {
        val glow = TexKit.glow.full
        val pulse = 0.35f + 0.1f * sin(time * 3f)
        // Neon strips along the table's sides.
        for (x in floatArrayOf(RL - 17f, RR + 17f)) {
            r.beam(x, -2f, RT - 16f, x, -2f, RB + 16f, 3f, TexKit.white.full, emissive = 1.3f, tint = Pal.CYAN)
            r.beam(x, -2f, RT - 16f, x, -2f, RB + 16f, 18f, glow, blend = Blend.ADD, emissive = 1f, alpha = pulse, tint = Pal.CYAN)
        }
        if (goalFlash > 0f) {
            val z = if (goalByPlayer) RT - 8f else RB + 8f
            r.flat(CX, z, RAIL_H + 1f, GOAL_HALF * 3f, 60f, glow, blend = Blend.ADD, emissive = 1f, alpha = clamp01(goalFlash), tint = if (goalByPlayer) Pal.CYAN else Pal.RED)
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

    /** Where the table point ([x], [y]) is on screen, for bots that play by touch. */
    internal fun botScreen(x: Float, y: Float): Pair<Float, Float> {
        stage.toField(x, MALLET_H, y, pt)
        return pt[0] to pt[1]
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NAVY))
        p.fill(0f, h / 2f, w.toFloat(), 0.5f, Color(Pal.RED))
        p.fill(w / 2f - 3f, 0f, 6f, 1f, Color(Pal.BLACK))
        p.fill(w / 2f - 3f, h - 1f, 6f, 1f, Color(Pal.BLACK))
        val t = time * 1.7f
        val px = w / 2f + sin(t * 1.3f) * (w / 2f - 2f)
        val py = h / 2f + sin(t) * (h / 2f - 2f)
        p.disc(px, py, 1f, Color.White)
        val mx = w / 2f + sin(t * 1.3f - 0.4f) * (w / 2f - 4f)
        p.disc(mx, h - 2.5f, 1.8f, Color(Pal.CYAN))
        p.disc(w / 2f + cos(t) * 4f, 2.5f, 1.8f, Color(Pal.PINK))
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("GOAL", w / 2f, h / 2f - 6f, Color(Pal.YELLOW), tiny = true)
    }
}
