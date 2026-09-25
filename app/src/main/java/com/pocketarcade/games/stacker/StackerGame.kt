package com.pocketarcade.games.stacker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
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
    override val look = CabinetLook(body = Pal.VIOLET, trim = Pal.CYAN, glow = Pal.PURPLE)
    override val roundSeconds = StackerTuning.ROUND_SECONDS

    /** One placed layer of the tower: centre and size on the ground plane. */
    private class Slab(val x: Float, val z: Float, val w: Float, val d: Float, val color: Int)

    /** A cut-off piece or a missed slab tumbling away. */
    private class Chunk {
        var x = 0f; var y = 0f; var z = 0f
        var w = 0f; var d = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var spin = 0f; var spinV = 0f
        var axisX = true
        var color = 0
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

    override fun reset() {
        tower.clear()
        tower += Slab(0f, 0f, StackerTuning.BASE_SIZE, StackerTuning.BASE_SIZE, colorFor(0))
        chunks.forEach { it.active = false }
        lives = StackerTuning.LIVES
        combo = 0
        height = 0
        perfectFlash = 0f
        camY = 0f
        landT = 0f
        missT = 0f
        spawnSlab()
    }

    override fun ticketsFor(score: Int): Int = StackerTuning.BASE_TICKETS + score / StackerTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = chunks.none { it.active }

    override fun onTimeUp() {
        moving = false
    }

    private val top get() = tower.last()

    private fun colorFor(level: Int): Int {
        // Walk round the hue wheel, a step per level.
        val h = (level * 13f + 260f) % 360f
        return hsv(h, 0.62f, 1f)
    }

    private fun spawnSlab() {
        moveOnX = tower.size % 2 == 1
        moveDir = if (rng.nextBoolean()) 1f else -1f
        moveX = if (moveOnX) top.x - StackerTuning.SWING * moveDir else top.x
        moveZ = if (moveOnX) top.z else top.z - StackerTuning.SWING * moveDir
        moving = true
    }

    private val speed: Float
        get() = (StackerTuning.SPEED_START + height * StackerTuning.SPEED_PER_LEVEL).coerceAtMost(StackerTuning.SPEED_MAX)

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
        val color = colorFor(level)
        val y = level * StackerTuning.SLAB_HEIGHT
        if (overlap <= 0f) {
            miss(color, y)
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
            particles.burst(pt[0], pt[1], 18 + combo * 2, 60f, 220f, intArrayOf(Pal.WHITE, Pal.CYAN, color), 0.6f, 4f, kind = Particles.SPARKLE)
        } else {
            combo = 0
            // Keep the overlap; the overhang breaks off and falls.
            val cut = abs(delta)
            val side = if (delta > 0f) 1f else -1f
            if (moveOnX) {
                w = overlap
                cx = t.x + delta / 2f
                spawnChunk(cx + side * (overlap / 2f + cut / 2f), y, moveZ, cut, d, color, axisX = true, dir = side)
            } else {
                d = overlap
                cz = t.z + delta / 2f
                spawnChunk(moveX, y, cz + side * (overlap / 2f + cut / 2f), w, cut, color, axisX = false, dir = side)
            }
            addScore(StackerTuning.LEVEL_POINTS, GAME_W / 2f, 220f, Color.White)
            play(Sfx.THUD, 0.8f, 0.9f + clamp01(overlap / StackerTuning.BASE_SIZE) * 0.4f)
            fx.haptics.tick()
            shake.add(0.06f)
        }
        tower += Slab(cx, cz, w, d, color)
        height++
        landT = 0.2f
        if (height % 10 == 0) {
            popups.add("HEIGHT $height!", GAME_W / 2f, 150f, Color(Pal.YELLOW), size = 4f, life = 1.3f)
            play(Sfx.WIN, 0.8f)
            fx.haptics.win()
        }
        if (!timeUp) spawnSlab()
    }

    private fun miss(color: Int, y: Float) {
        combo = 0
        lives--
        missT = 0.5f
        val t = top
        spawnChunk(moveX, y, moveZ, t.w, t.d, color, axisX = moveOnX, dir = moveDir)
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

    private fun spawnChunk(x: Float, y: Float, z: Float, w: Float, d: Float, color: Int, axisX: Boolean, dir: Float) {
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
        c.color = color
    }

    override fun step(dt: Float) {
        perfectFlash = (perfectFlash - dt * 2f).coerceAtLeast(0f)
        landT = (landT - dt).coerceAtLeast(0f)
        missT = (missT - dt).coerceAtLeast(0f)
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

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt(), "stacker")
    private val pt = FloatArray(3)
    private val xf = Xform()
    private val key = PointLight(0f, 0f, 0f, 1f, 0.95f, 0.9f, 900f, 0.5f)

    init {
        aim(0f)
    }

    /** The camera circles slowly and rises with the top of the tower. */
    private fun aim(topY: Float) {
        stage.look(330f, topY + 330f, 420f, 0f, topY - 20f, 0f, fovDeg = 44f, centerYFrac = 0.5f)
    }

    override fun render(scope: DrawScope) {
        aim(camY)
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.45f; l.ambG = 0.45f; l.ambB = 0.55f
        l.setDirection(0.55f, 1f, 0.35f)
        l.dirR = 0.75f; l.dirG = 0.72f; l.dirB = 0.68f
        l.points.clear()
        key.x = 200f; key.y = camY + 200f; key.z = 260f
        l.points += key
        // The sky darkens into space as the tower climbs.
        val climb = clamp01(camY / 1400f)
        r.gradient(mixColor(0xFF2A1450.toInt(), 0xFF020108.toInt(), climb), mixColor(Pal.HOTPINK, Pal.INDIGO, climb))
        drawStars(r, climb)

        val first = (tower.size - 26).coerceAtLeast(0)
        if (first == 0) drawGround(r)
        val h = StackerTuning.SLAB_HEIGHT
        for (i in first until tower.size) {
            val s = tower[i]
            val bottom = if (i == 0) -60f else (i - 1) * h + h
            box(r, s.x, bottom, (i + 1) * h - bottom, s.z, s.w, s.d, s.color, 0f, true)
        }
        if (moving) {
            val y = tower.size * h
            box(r, moveX, y, h, moveZ, top.w, top.d, colorFor(tower.size), 0f, true)
        }
        for (c in chunks) {
            if (!c.active) continue
            box(r, c.x, c.y, h, c.z, c.w, c.d, c.color, c.spin, c.axisX)
        }
        if (perfectFlash > 0f) {
            val s = top
            val y = tower.size * h + 0.5f
            val grow = 1f + (1f - perfectFlash) * 0.5f
            r.flat(s.x, s.z, y, s.w * grow, s.d * grow, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = perfectFlash, tint = Pal.CYAN)
        }
        stage.present(scope)

        PixelFont.drawCentered(scope, height.toString(), GAME_W / 2f, 24f, 7f, Color.White)
        for (i in 0 until StackerTuning.LIVES) {
            val on = i < lives
            PixelFont.drawCentered(scope, "${PixelFont.HEART}", GAME_W / 2f - 30f + i * 30f, 86f, 3f, Color(if (on) Pal.RED else Pal.DARKGRAY))
        }
        if (combo >= 2) {
            PixelFont.drawCentered(scope, "PERFECT x$combo", GAME_W / 2f, 118f, 2f, Color(Pal.CYAN), 0.7f + 0.3f * sin(time * 10f))
        }
        if (moving && height == 0 && !timeUp) {
            PixelFont.drawCentered(scope, "TAP TO DROP", GAME_W / 2f, 600f, 2f, Color.White, 0.5f + 0.5f * sin(time * 6f))
        }
    }

    private fun drawStars(r: Renderer3D, climb: Float) {
        if (climb <= 0.05f) return
        // Stars fixed in screen space, painted straight into the framebuffer.
        val w = r.width
        val hgt = r.height
        for (i in 0 until 40) {
            val x = (hash01(i, 71) * w).toInt()
            val y = (hash01(i, 72) * hgt * 0.7f).toInt()
            val tw = 0.5f + 0.5f * sin(time * 2f + i)
            val c = mixColor(0xFF000000.toInt(), -1, climb * tw)
            val idx = y * w + x
            if (idx in r.color.indices) r.color[idx] = c or -0x1000000
        }
    }

    private fun drawGround(r: Renderer3D) {
        r.flat(0f, 0f, -60f, 900f, 900f, TexKit.white.full, tint = Pal.shade(Pal.PLUM, 0.6f))
        r.flat(0f, 0f, -59.5f, 360f, 360f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.3f, tint = Pal.PINK)
    }

    /**
     * A slab [w] × [d] × [height] standing on [y], centred on ([x], [z]), optionally turned by
     * [spin] about the axis it was sliding along (for tumbling pieces).
     */
    private fun box(r: Renderer3D, x: Float, y: Float, height: Float, z: Float, w: Float, d: Float, color: Int, spin: Float, axisX: Boolean) {
        val tex = TexKit.white.full
        if (axisX) xf.set(x, y + height / 2f, z, roll = -spin) else xf.set(x, y + height / 2f, z, pitch = spin)
        val hw = w / 2f
        val hh = height / 2f
        val hd = d / 2f
        val top = Pal.mix(color, Pal.WHITE, 0.12f)
        face(r, tex, top, 0f, 1f, 0f, -hw, hh, -hd, hw, hh, -hd, hw, hh, hd, -hw, hh, hd)
        face(r, tex, color, 0f, 0f, 1f, -hw, hh, hd, hw, hh, hd, hw, -hh, hd, -hw, -hh, hd)
        face(r, tex, color, 1f, 0f, 0f, hw, hh, hd, hw, hh, -hd, hw, -hh, -hd, hw, -hh, hd)
        face(r, tex, color, -1f, 0f, 0f, -hw, hh, -hd, -hw, hh, hd, -hw, -hh, hd, -hw, -hh, -hd)
        face(r, tex, color, 0f, 0f, -1f, hw, hh, -hd, -hw, hh, -hd, -hw, -hh, -hd, hw, -hh, -hd)
        face(r, tex, Pal.shade(color, 0.6f), 0f, -1f, 0f, -hw, -hh, hd, hw, -hh, hd, hw, -hh, -hd, -hw, -hh, -hd)
    }

    private fun face(
        r: Renderer3D, tex: com.pocketarcade.engine.r3d.Region, color: Int, nx: Float, ny: Float, nz: Float,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
    ) {
        r.begin(tex)
        r.normal(xf.dirX(nx, ny, nz), xf.dirY(nx, ny, nz), xf.dirZ(nx, ny, nz))
        r.tint(color)
        r.vertex(xf.x(ax, ay, az), xf.y(ax, ay, az), xf.z(ax, ay, az), 0f, 0f)
        r.vertex(xf.x(bx, by, bz), xf.y(bx, by, bz), xf.z(bx, by, bz), 4f, 0f)
        r.vertex(xf.x(cx, cy, cz), xf.y(cx, cy, cz), xf.z(cx, cy, cz), 4f, 4f)
        r.vertex(xf.x(dx, dy, dz), xf.y(dx, dy, dz), xf.z(dx, dy, dz), 0f, 4f)
        r.end()
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** How far the sliding slab is from lining up with the top of the tower (0 = perfect). */
    internal fun botDelta(): Float = if (moveOnX) moveX - top.x else moveZ - top.z
    internal val botMoving: Boolean get() = moving
    internal val botHeight: Int get() = height

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NIGHT))
        val levels = 6
        val cycle = (time * 1.2f) % (levels + 2f)
        for (i in 0 until levels) {
            if (i > cycle) break
            val sw = 10f - i * 1.2f
            val y = h - 2f - i * 2f
            val slide = if (i.toFloat() >= cycle - 1f) sin(time * 5f) * 4f else 0f
            p.fill(w / 2f - sw / 2f + slide, y, sw, 2f, Color(colorFor(i)))
        }
        if ((time * 2f).toInt() % 2 == 0) p.textCentered("STACK", w / 2f, 1f, Color(Pal.CYAN), tiny = true)
    }

    // ---------------------------------------------------------------- colour helpers

    private fun mixColor(a: Int, b: Int, t: Float): Int = com.pocketarcade.engine.r3d.mixArgb(a, b, t)

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return (0xFF shl 24) or (((r1 + m) * 255).toInt() shl 16) or (((g1 + m) * 255).toInt() shl 8) or ((b1 + m) * 255).toInt()
    }
}
