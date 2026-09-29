package com.pocketarcade.games.stacker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.sin

/** Difficulty and payout knobs for the stacker. */
object StackerTuning {
    const val ROUND_SECONDS = 60f
    const val LIVES = 3
    const val BASE_SIZE = 120f
    const val SLAB_HEIGHT = 18f
    /** How far either side of the tower a slab slides before turning back. */
    const val SWING = 170f
    const val SPEED_START = 150f
    const val SPEED_PER_LEVEL = 7f
    const val SPEED_MAX = 430f
    /** A drop within this distance of the slab below counts as perfect and loses nothing. */
    const val PERFECT_TOLERANCE = 5f
    /** After this many perfects in a row, each further perfect grows the slab back a little. */
    const val GROW_AFTER = 3
    const val GROW_AMOUNT = 8f
    const val LEVEL_POINTS = 10
    const val PERFECT_POINTS = 10
    /** Extra points per perfect in a row, counted up to [COMBO_CAP]. */
    const val COMBO_POINTS = 5
    const val COMBO_CAP = 4
    const val POINTS_PER_TICKET = 20
    const val BASE_TICKETS = 1
}

class StackerGame : BaseMiniGame() {
    override val id = "stacker"
    override val title = "STACKER"
    override val marquee = "STACK"
    override val instructions = listOf(
        "TAP TO DROP THE SLAB",
        "OVERHANG GETS CUT OFF",
        "LINE IT UP PERFECTLY",
        "TO KEEP IT WHOLE",
        "3 MISSES AND YOU'RE OUT",
    )
    override val look = CabinetLook(body = Pal.VIOLET, trim = Pal.CYAN, glow = Pal.PURPLE, shape = CabinetShape.TOWER)
    override val roundSeconds = StackerTuning.ROUND_SECONDS

    /** One placed layer of the tower: centre and size on the ground plane. */
    private class Slab(val x: Float, val z: Float, val w: Float, val d: Float)

    /** A cut-off piece or a missed slab tumbling away; [level] picks its colour. */
    private class Chunk {
        var x = 0f; var y = 0f; var z = 0f
        var w = 0f; var d = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var spin = 0f; var spinV = 0f
        var axisX = true
        var level = 0
        var active = false
    }

    private val tower = ArrayList<Slab>()
    private val chunks = Array(12) { Chunk() }
    private var moveX = 0f
    private var moveZ = 0f
    private var moveOnX = true
    private var moveDir = 1f
    private var moving = false
    private var lives = 0
    private var combo = 0
    private var height = 0
    private var perfectFlash = 0f
    private var camY = 0f
    private var landT = 0f
    private var missT = 0f

    /** All the presentation state (glows, shockwaves); the simulation never reads it. */
    private val scene = StackerScene()

    override fun reset() {
        tower.clear()
        tower += Slab(0f, 0f, StackerTuning.BASE_SIZE, StackerTuning.BASE_SIZE)
        chunks.forEach { it.active = false }
        lives = StackerTuning.LIVES
        combo = 0
        height = 0
        perfectFlash = 0f
        camY = 0f
        landT = 0f
        missT = 0f
        scene.reset()
        spawnSlab()
    }

    override fun ticketsFor(score: Int): Int = StackerTuning.BASE_TICKETS + score / StackerTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = chunks.none { it.active }

    override fun onTimeUp() {
        moving = false
    }

    private val top get() = tower.last()

    private fun spawnSlab() {
        moveOnX = tower.size % 2 == 1
        moveDir = if (rng.nextBoolean()) 1f else -1f
        moveX = if (moveOnX) top.x - StackerTuning.SWING * moveDir else top.x
        moveZ = if (moveOnX) top.z else top.z - StackerTuning.SWING * moveDir
        moving = true
    }

    private val speed: Float
        get() = (StackerTuning.SPEED_START + height * StackerTuning.SPEED_PER_LEVEL).coerceAtMost(StackerTuning.SPEED_MAX)

    /** Every drop is a single tap: there is no pointer to forget. */
    override fun cancelInput() {}

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || !moving || timeUp || endedEarly) return
        drop()
    }

    private fun drop() {
        moving = false
        val t = top
        val delta = if (moveOnX) moveX - t.x else moveZ - t.z
        val size = if (moveOnX) t.w else t.d
        val overlap = size - abs(delta)
        val level = tower.size
        val y = level * StackerTuning.SLAB_HEIGHT
        if (overlap <= 0f) {
            miss(level, y)
            return
        }
        val perfect = abs(delta) <= StackerTuning.PERFECT_TOLERANCE
        var w = t.w
        var d = t.d
        var cx = t.x
        var cz = t.z
        if (perfect) {
            combo++
            if (combo > StackerTuning.GROW_AFTER) {
                w = (w + StackerTuning.GROW_AMOUNT).coerceAtMost(StackerTuning.BASE_SIZE)
                d = (d + StackerTuning.GROW_AMOUNT).coerceAtMost(StackerTuning.BASE_SIZE)
            }
            perfectFlash = 1f
            val bonus = StackerTuning.PERFECT_POINTS + StackerTuning.COMBO_POINTS * (combo - 1).coerceAtMost(StackerTuning.COMBO_CAP)
            addScore(StackerTuning.LEVEL_POINTS + bonus, GAME_W / 2f, 200f, Color(Pal.CYAN), "PERFECT +${StackerTuning.LEVEL_POINTS + bonus}")
            play(Sfx.SELECT, 0.9f, 1f + (combo % 8) * 0.08f)
            fx.haptics.hit()
            stage.toField(cx, y + StackerTuning.SLAB_HEIGHT, cz, pt)
            particles.burst(pt[0], pt[1], 18 + combo * 2, 60f, 220f, intArrayOf(Pal.WHITE, Pal.CYAN, StackerArt.glowColor(level)), 0.6f, 4f, kind = Particles.SPARKLE)
            scene.perfect(cx, y + StackerTuning.SLAB_HEIGHT, cz, w, d, level, combo)
        } else {
            combo = 0
            // Keep the overlap; the overhang breaks off and falls.
            val cut = abs(delta)
            val side = if (delta > 0f) 1f else -1f
            if (moveOnX) {
                w = overlap
                cx = t.x + delta / 2f
                spawnChunk(cx + side * (overlap / 2f + cut / 2f), y, moveZ, cut, d, level, axisX = true, dir = side)
                scene.cut(cx + side * overlap / 2f, y + StackerTuning.SLAB_HEIGHT, moveZ, level)
            } else {
                d = overlap
                cz = t.z + delta / 2f
                spawnChunk(moveX, y, cz + side * (overlap / 2f + cut / 2f), w, cut, level, axisX = false, dir = side)
                scene.cut(moveX, y + StackerTuning.SLAB_HEIGHT, cz + side * overlap / 2f, level)
            }
            scene.land(cx, y + StackerTuning.SLAB_HEIGHT, cz, w, d)
            addScore(StackerTuning.LEVEL_POINTS, GAME_W / 2f, 220f, Color.White)
            play(Sfx.THUD, 0.8f, 0.9f + clamp01(overlap / StackerTuning.BASE_SIZE) * 0.4f)
            fx.haptics.tick()
            shake.add(0.06f)
        }
        tower += Slab(cx, cz, w, d)
        height++
        landT = 0.2f
        if (height % 10 == 0) {
            popups.add("HEIGHT $height!", GAME_W / 2f, 150f, Color(Pal.YELLOW), size = 4f, life = 1.3f)
            play(Sfx.WIN, 0.8f)
            fx.haptics.win()
            scene.milestone(tower.size * StackerTuning.SLAB_HEIGHT)
        }
        if (!timeUp) spawnSlab()
    }

    private fun miss(level: Int, y: Float) {
        combo = 0
        lives--
        missT = 0.5f
        val t = top
        spawnChunk(moveX, y, moveZ, t.w, t.d, level, axisX = moveOnX, dir = moveDir)
        scene.miss(t.x, y, t.z)
        play(Sfx.DROP)
        fx.haptics.heavy()
        shake.add(0.3f)
        if (lives <= 0) {
            popups.add("TOWER TOPPLED", GAME_W / 2f, 240f, Color(Pal.RED), size = 4f, life = 1.5f)
            play(Sfx.GUTTER)
            endedEarly = true
        } else {
            popups.add("MISS! ${lives} LEFT", GAME_W / 2f, 240f, Color(Pal.ORANGE), size = 3f, life = 1.1f)
            spawnSlab()
        }
    }

    private fun spawnChunk(x: Float, y: Float, z: Float, w: Float, d: Float, level: Int, axisX: Boolean, dir: Float) {
        val c = chunks.firstOrNull { !it.active } ?: chunks[0]
        c.active = true
        c.x = x; c.y = y; c.z = z
        c.w = w; c.d = d
        c.vx = if (axisX) dir * 60f else rng.range(-10f, 10f)
        c.vz = if (!axisX) dir * 60f else rng.range(-10f, 10f)
        c.vy = 40f
        c.spin = 0f
        c.spinV = dir * rng.range(2f, 4f)
        c.axisX = axisX
        c.level = level
    }

    override fun step(dt: Float) {
        perfectFlash = (perfectFlash - dt * 2f).coerceAtLeast(0f)
        landT = (landT - dt).coerceAtLeast(0f)
        missT = (missT - dt).coerceAtLeast(0f)
        scene.step(dt, combo)
        if (moving) {
            val s = speed * dt * moveDir
            if (moveOnX) {
                moveX += s
                if (moveX > top.x + StackerTuning.SWING) { moveX = top.x + StackerTuning.SWING; moveDir = -1f }
                if (moveX < top.x - StackerTuning.SWING) { moveX = top.x - StackerTuning.SWING; moveDir = 1f }
            } else {
                moveZ += s
                if (moveZ > top.z + StackerTuning.SWING) { moveZ = top.z + StackerTuning.SWING; moveDir = -1f }
                if (moveZ < top.z - StackerTuning.SWING) { moveZ = top.z - StackerTuning.SWING; moveDir = 1f }
            }
        }
        for (c in chunks) {
            if (!c.active) continue
            c.vy -= 900f * dt
            c.x += c.vx * dt
            c.y += c.vy * dt
            c.z += c.vz * dt
            c.spin += c.spinV * dt
            if (c.y < camY - 400f) c.active = false
        }
        camY = damp(camY, tower.size * StackerTuning.SLAB_HEIGHT, 4f, dt)
    }

    // ---------------------------------------------------------------- 3D presentation

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt())
    private val pt = FloatArray(3)

    init {
        aim(0f)
    }

    /** The camera rises with the top of the tower, looking down on it from a fixed angle. */
    private fun aim(topY: Float) {
        stage.look(330f, topY + 330f, 420f, 0f, topY - 20f, 0f, fovDeg = 44f, centerYFrac = 0.5f)
    }

    override fun render(scope: DrawScope) {
        aim(camY)
        val r = stage.begin()
        val h = StackerTuning.SLAB_HEIGHT
        val topY = tower.size * h
        val climb = clamp01(camY / 1400f)
        scene.light(r, camY, topY, tower.size - 1, perfectFlash, climb)
        r.gradient(0xFF07030F.toInt(), 0xFF150A2A.toInt())
        scene.drawWorld(r, camY, time, climb, topY, tower.size - 1)

        // The last 26 courses; each one's neon outline dims the further it is below the top.
        val first = (tower.size - 26).coerceAtLeast(0)
        val lastIdx = tower.size - 1
        for (i in first until tower.size) {
            val s = tower[i]
            val bottom = if (i == 0) -60f else i * h
            val glowK = 1f - (lastIdx - i) / StackerLook.GLOW_FADE_LEVELS
            scene.slab(r, s.x, bottom, (i + 1) * h - bottom, s.z, s.w, s.d, i, glowK, 0f, true, if (i == lastIdx) perfectFlash else 0f)
        }
        if (moving) {
            // The sliding slab burns a little brighter than any placed one, breathing.
            scene.slab(r, moveX, topY, h, moveZ, top.w, top.d, tower.size, 1.1f + 0.15f * sin(time * 10f), 0f, true, 0f)
        }
        for (c in chunks) {
            if (!c.active) continue
            scene.slab(r, c.x, c.y, h, c.z, c.w, c.d, c.level, 0.7f, c.spin, c.axisX, 0f)
        }
        scene.drawEffects(r)
        stage.present()

        ArcadeFont.drawCentered(scope, height.toString(), GAME_W / 2f, 24f, 7f, Color.White)
        for (i in 0 until StackerTuning.LIVES) {
            val on = i < lives
            ArcadeFont.drawCentered(scope, "${ArcadeFont.HEART}", GAME_W / 2f - 30f + i * 30f, 86f, 3f, Color(if (on) Pal.RED else Pal.DARKGRAY))
        }
        if (combo >= 2) {
            ArcadeFont.drawCentered(scope, "PERFECT x$combo", GAME_W / 2f, 118f, 2f, Color(Pal.CYAN), 0.7f + 0.3f * sin(time * 10f))
        }
        if (moving && height == 0 && !timeUp) {
            ArcadeFont.drawCentered(scope, "TAP TO DROP", GAME_W / 2f, 600f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** How far the sliding slab is from lining up with the top of the tower (0 = perfect). */
    internal fun botDelta(): Float = if (moveOnX) moveX - top.x else moveZ - top.z
    internal val botMoving: Boolean get() = moving
    internal val botHeight: Int get() = height

    // ---------------------------------------------------------------- attract mode

    /**
     * A tower going up at night: slabs slide in over a city skyline and drop one by one (some
     * trimmed, the overhang falling away; every third one perfect, with a burst of sparks),
     * then the tower fades and starts again, under a pulsing "STACK". [w] × [h] is the
     * cabinet's small portrait screen.
     */
    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cx = wf / 2f
        val cycle = 9.6f
        val n = (time / cycle).toInt()
        val tt = time % cycle
        // Night sky, deepening towards a glow at the skyline, with a few stars.
        val bands = 8
        for (i in 0 until bands) {
            val k = i / (bands - 1f)
            p.fill(0f, i * hf / bands, wf, hf / bands + 0.3f, Color(Pal.mix(0xFF05030D.toInt(), 0xFF2A1450.toInt(), k * k)))
        }
        for (i in 0 until 9) {
            val twinkle = 0.5f + 0.5f * sin(time * 2f + i * 1.9f)
            p.disc(hash01(i, 3) * wf, hash01(i, 4) * hf * 0.5f, 0.22f, Color.White, 0.2f + 0.6f * twinkle)
        }
        // The city, with lit windows.
        var x = 0f
        var i = 0
        while (x < wf) {
            val bw = 1.6f + hash01(i, 5) * 1.6f
            val bh = 1.2f + hash01(i, 6) * 2.6f
            p.fill(x, hf - bh, bw, bh, Color(0xFF0B0718.toInt()))
            if (hash01(i, 7) > 0.35f) p.disc(x + bw * 0.5f, hf - bh + 0.6f, 0.18f, Color(0xFFFFC060.toInt()), 0.5f + 0.5f * sin(time * 1.3f + i))
            x += bw + 0.15f
            i++
        }
        // The rooftop the tower stands on.
        val baseY = hf - 3.2f
        p.fill(1.2f, baseY, wf - 2.4f, 0.5f, Color(0xFF3A2A66.toInt()))
        p.fill(1.2f, baseY, wf - 2.4f, 0.15f, Color(Pal.CYAN), 0.7f)

        // The tower: seven courses landing one after another, then fading out.
        val layers = 7
        val lh = 1.9f
        var prevW = 8.4f
        var prevX = cx
        val fade = if (tt > 8.6f) clamp01(1f - (tt - 8.6f) / 1f) else 1f
        for (k in 0 until layers) {
            val land = 0.6f + k * 1.1f
            val perfect = k % 3 == 2
            val over = if (perfect) 0f else 0.5f + hash01(n * 9 + k, 8) * 0.9f
            val side = if (hash01(n * 9 + k, 9) > 0.5f) 1f else -1f
            val finalW = (prevW - over).coerceAtLeast(3.2f)
            val finalX = if (perfect) prevX else (prevX + side * over / 2f).coerceIn(finalW / 2f + 1f, wf - finalW / 2f - 1f)
            val slot = baseY - (k + 1) * lh
            if (tt < land - 0.95f) {
                prevW = finalW; prevX = finalX
                continue
            }
            val glow = StackerArt.glowColor(k)
            val bodyC = Color(Pal.shade(glow, 0.62f))
            val u = clamp01((tt - (land - 0.12f)) / 0.12f)
            val slide = cx + sin(time * 5f + k * 1.7f) * (wf / 2f - prevW / 2f - 1f)
            val bx = if (tt < land) lerp(slide, finalX, u * u) else finalX
            // The course's glow, its body and its bright top edge.
            p.fill(bx - finalW / 2f - 0.5f, slot - 0.4f, finalW + 1f, lh + 0.8f, Color(glow), 0.16f * fade)
            p.fill(bx - finalW / 2f, slot, finalW, lh - 0.1f, bodyC, fade)
            p.fill(bx - finalW / 2f, slot, finalW, 0.4f, Color(glow), fade)
            if (tt >= land) {
                val since = tt - land
                if (!perfect && since < 0.9f) {
                    // The overhang breaks off and falls.
                    val cxo = finalX + side * (finalW / 2f + over / 2f)
                    p.fill(cxo - over / 2f, slot + since * since * 14f, over, lh - 0.1f, bodyC, (1f - since / 0.9f) * fade)
                }
                if (perfect && since < 0.5f) {
                    // A perfect drop: the outline flashes and sparks fly.
                    p.frame(bx - finalW / 2f - 0.3f, slot - 0.3f, finalW + 0.6f, lh + 0.5f, Color.White, (1f - since / 0.5f) * fade)
                    for (s in 0 until 6) {
                        val a = s / 5f * 3.1416f
                        p.disc(bx + kotlin.math.cos(a) * since * 12f, slot - kotlin.math.sin(a) * since * 6f, 0.3f, Color(Pal.WHITE), 1f - since / 0.5f)
                    }
                }
            }
            prevW = finalW; prevX = finalX
        }
        // Title, pulsing.
        val pulse = 0.7f + 0.3f * sin(time * 4f)
        p.textCentered("STACK", cx, 1.2f, Color(Pal.CYAN), tiny = true, alpha = 0.4f * pulse, size = 0.76f)
        p.textCentered("STACK", cx, 1.2f, Color(Pal.CREAM), tiny = true, alpha = pulse, size = 0.7f)
    }
}
