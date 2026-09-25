package com.pocketarcade.games.whackamole

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.drawPixelImageSquash
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.sin

/** Difficulty and payout knobs for whack-a-mole. */
object WhackTuning {
    const val ROUND_SECONDS = 45f
    /** Seconds between pop-ups at the start and at the end of the round (linear ramp). */
    const val SPAWN_INTERVAL_START = 0.95f
    const val SPAWN_INTERVAL_END = 0.38f
    /** How long a mole stays up at the start and at the end of the round. */
    const val UP_TIME_START = 1.15f
    const val UP_TIME_END = 0.55f
    const val GOLD_CHANCE = 0.08f
    const val BOMB_CHANCE_START = 0.10f
    const val BOMB_CHANCE_END = 0.22f
    const val POINTS_NORMAL = 10
    const val POINTS_GOLD = 20
    const val BOMB_PENALTY = 30
    /** Every this many hits in a row adds [COMBO_BONUS] points per hit. */
    const val COMBO_STEP = 5
    const val COMBO_BONUS = 2
    const val BOMB_STUN_SECONDS = 0.5f
    const val POINTS_PER_TICKET = 30
    const val BASE_TICKETS = 1
}

class WhackAMoleGame : BaseMiniGame() {
    override val id = "whack"
    override val title = "WHACK-A-MOLE"
    override val marquee = "WHACK"
    override val instructions = listOf(
        "TAP MOLES TO BONK THEM",
        "GOLD MOLES = DOUBLE POINTS",
        "DON'T HIT THE BOMBS!",
        "HITS IN A ROW BUILD A COMBO",
        "IT GETS FASTER...",
    )
    override val look = CabinetLook(body = Pal.GREEN, trim = Pal.BROWN, glow = Pal.LIME)
    override val roundSeconds = WhackTuning.ROUND_SECONDS

    private enum class Kind { NORMAL, GOLD, BOMB }
    private enum class Phase { HIDDEN, RISING, UP, FALLING, BONKED }

    private class Mole {
        var kind = Kind.NORMAL
        var phase = Phase.HIDDEN
        var t = 0f
        var upTime = 1f
        var rise = 0f
        val squash = Spring(stiffness = 520f, damping = 14f)
    }

    private companion object {
        val COLS = floatArrayOf(70f, 180f, 290f)
        val ROWS = floatArrayOf(250f, 385f, 520f)
        const val HOLE_RX = 48f
        const val HOLE_RY = 17f
        const val SPRITE_SCALE = 4f
        const val RISE_TIME = 0.12f
        const val FALL_TIME = 0.14f
    }

    private val moles = Array(9) { Mole() }
    private var spawnT = 0.6f
    private var combo = 0
    private var bestCombo = 0
    private var stunT = 0f
    private var malletX = 0f
    private var malletY = 0f
    private var malletT = 99f
    private var hits = 0

    private val sprites: Map<String, ImageBitmap> by lazy { buildSprites() }

    override fun reset() {
        moles.forEach {
            it.phase = Phase.HIDDEN; it.rise = 0f; it.t = 0f; it.squash.snap(1f)
        }
        spawnT = 0.6f
        combo = 0
        bestCombo = 0
        stunT = 0f
        malletT = 99f
        hits = 0
    }

    override fun ticketsFor(score: Int): Int = WhackTuning.BASE_TICKETS + score / WhackTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = moles.all { it.phase == Phase.HIDDEN }

    override fun onTimeUp() {
        for (m in moles) if (m.phase == Phase.RISING || m.phase == Phase.UP) {
            m.phase = Phase.FALLING; m.t = 0f
        }
    }

    private val progress: Float get() = clamp01(time / roundSeconds)

    override fun step(dt: Float) {
        stunT = (stunT - dt).coerceAtLeast(0f)
        malletT += dt
        if (!timeUp) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawn()
                val interval = lerp(WhackTuning.SPAWN_INTERVAL_START, WhackTuning.SPAWN_INTERVAL_END, progress)
                spawnT = interval * rng.range(0.75f, 1.25f)
            }
        }
        for (m in moles) {
            m.t += dt
            m.squash.update(dt)
            when (m.phase) {
                Phase.HIDDEN -> m.rise = 0f
                Phase.RISING -> {
                    m.rise = easeOutBack(clamp01(m.t / RISE_TIME))
                    if (m.t >= RISE_TIME) {
                        m.phase = Phase.UP; m.t = 0f
                    }
                }
                Phase.UP -> {
                    m.rise = 1f
                    if (m.t >= m.upTime) {
                        m.phase = Phase.FALLING; m.t = 0f
                        if (m.kind != Kind.BOMB && !timeUp) {
                            combo = 0
                        }
                    }
                }
                Phase.FALLING -> {
                    m.rise = 1f - clamp01(m.t / FALL_TIME)
                    if (m.t >= FALL_TIME) {
                        m.phase = Phase.HIDDEN; m.t = 0f
                    }
                }
                Phase.BONKED -> {
                    m.rise = if (m.t < 0.35f) 1f else 1f - clamp01((m.t - 0.35f) / 0.2f)
                    if (m.t >= 0.55f) {
                        m.phase = Phase.HIDDEN; m.t = 0f
                    }
                }
            }
        }
        // Lit fuses sputter.
        for (i in moles.indices) {
            val m = moles[i]
            if (m.kind == Kind.BOMB && m.phase != Phase.HIDDEN && m.phase != Phase.BONKED && m.rise > 0.5f && rng.chance(0.5f)) {
                val hx = COLS[i % 3] + 14f
                val hy = ROWS[i / 3] - m.rise * 20f * SPRITE_SCALE + 2f
                particles.spawn(hx, hy, rng.range(-40f, 40f), rng.range(-90f, -20f), 0.25f, 3f, if (rng.nextBoolean()) Pal.YELLOW else Pal.ORANGE)
            }
        }
    }

    private fun spawn() {
        val free = moles.indices.filter { moles[it].phase == Phase.HIDDEN }
        if (free.isEmpty()) return
        val m = moles[free[rng.nextInt(free.size)]]
        val bombChance = lerp(WhackTuning.BOMB_CHANCE_START, WhackTuning.BOMB_CHANCE_END, progress)
        val roll = rng.nextFloat()
        m.kind = when {
            roll < bombChance -> Kind.BOMB
            roll < bombChance + WhackTuning.GOLD_CHANCE -> Kind.GOLD
            else -> Kind.NORMAL
        }
        m.phase = Phase.RISING
        m.t = 0f
        m.upTime = lerp(WhackTuning.UP_TIME_START, WhackTuning.UP_TIME_END, progress) * rng.range(0.85f, 1.15f)
        m.squash.snap(0.7f)
        play(Sfx.POP, 0.45f, if (m.kind == Kind.GOLD) 1.5f else rng.range(0.9f, 1.1f))
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp) return
        malletX = x
        malletY = y
        malletT = 0f
        if (stunT > 0f) {
            play(Sfx.ERROR, 0.3f)
            return
        }
        var hitIndex = -1
        var bestDist = Float.MAX_VALUE
        for (i in moles.indices) {
            val hx = COLS[i % 3]
            val hy = ROWS[i / 3]
            if (abs(x - hx) < 58f && y > hy - 96f && y < hy + 30f) {
                val d = abs(x - hx) + abs(y - (hy - 30f))
                if (d < bestDist) {
                    bestDist = d; hitIndex = i
                }
            }
        }
        if (hitIndex < 0) {
            play(Sfx.THUD, 0.25f, 1.3f)
            return
        }
        val m = moles[hitIndex]
        val hx = COLS[hitIndex % 3]
        val hy = ROWS[hitIndex / 3]
        val whackable = (m.phase == Phase.RISING || m.phase == Phase.UP || m.phase == Phase.FALLING) && m.rise > 0.3f
        if (!whackable) {
            combo = 0
            play(Sfx.THUD, 0.4f)
            particles.burst(hx, hy, 6, 30f, 90f, intArrayOf(Pal.DARKGREEN, Pal.BROWN), 0.4f, 4f, grav = 200f)
            return
        }
        m.phase = Phase.BONKED
        m.t = 0f
        m.squash.snap(1f)
        m.squash.kick(-9f)
        val headY = hy - 18f * SPRITE_SCALE
        when (m.kind) {
            Kind.BOMB -> {
                combo = 0
                stunT = WhackTuning.BOMB_STUN_SECONDS
                addScore(-WhackTuning.BOMB_PENALTY, hx, headY - 10f, Color(Pal.RED))
                popups.add("OUCH!", GAME_W / 2f, 130f, Color(Pal.RED), size = 5f, life = 1f)
                play(Sfx.BOMB)
                fx.haptics.heavy()
                shake.add(0.85f)
                flash.trigger(1f)
                particles.burst(hx, hy - 40f, 60, 80f, 420f, intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.RED, Pal.DARKGRAY), 0.9f, 7f, grav = 250f)
            }
            Kind.GOLD, Kind.NORMAL -> {
                combo++
                hits++
                bestCombo = maxOf(bestCombo, combo)
                val base = if (m.kind == Kind.GOLD) WhackTuning.POINTS_GOLD else WhackTuning.POINTS_NORMAL
                val bonus = (combo / WhackTuning.COMBO_STEP) * WhackTuning.COMBO_BONUS
                addScore(base + bonus, hx, headY - 10f, Color(if (m.kind == Kind.GOLD) Pal.GOLD else Pal.WHITE))
                if (combo % WhackTuning.COMBO_STEP == 0) {
                    popups.add("COMBO x$combo!", GAME_W / 2f, 130f, Color(Pal.CYAN), size = 4f, life = 1f)
                    play(Sfx.SELECT, 0.8f, 1.2f)
                }
                if (m.kind == Kind.GOLD) {
                    play(Sfx.COIN, 1f, 1.2f)
                    play(Sfx.WHACK, 0.8f, 1.1f)
                    fx.haptics.win()
                    shake.add(0.3f)
                    particles.burst(hx, headY + 20f, 30, 80f, 280f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.7f, 4f, kind = Particles.SPARKLE)
                } else {
                    play(Sfx.WHACK, 1f, rng.range(0.9f, 1.1f))
                    play(Sfx.BONK, 0.5f, 1f + (combo % 8) * 0.06f)
                    fx.haptics.hit()
                    shake.add(0.18f)
                }
                particles.burst(hx, headY + 30f, 12, 60f, 200f, intArrayOf(Pal.WHITE, Pal.YELLOW), 0.4f, 4f, kind = Particles.SPARKLE)
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    override fun render(scope: DrawScope) {
        with(scope) {
            // Wooden cabinet top with a grassy playfield.
            drawRect(Color(Pal.DARKBROWN), Offset(-40f, -40f), Size(GAME_W + 80f, GAME_H + 80f))
            drawRoundRect(Color(Pal.DARKGREEN), Offset(14f, 120f), Size(GAME_W - 28f, 500f), CornerRadius(24f, 24f))
            drawRoundRect(Color(Pal.GREEN), Offset(20f, 126f), Size(GAME_W - 40f, 488f), CornerRadius(20f, 20f))
            for (i in 0 until 90) {
                val gx = 26f + hash01(i, 11) * (GAME_W - 60f)
                val gy = 132f + hash01(i, 12) * 470f
                drawRect(Color(Pal.DARKGREEN), Offset(gx, gy), Size(3f, 6f), alpha = 0.5f)
                drawRect(Color(Pal.LIME), Offset(gx + 3f, gy - 2f), Size(2f, 4f), alpha = 0.4f)
            }
            // Title strip with blinking bulbs.
            drawRoundRect(Color(Pal.BROWN), Offset(14f, 20f), Size(GAME_W - 28f, 88f), CornerRadius(16f, 16f))
            for (i in 0 until 14) {
                val on = ((time * 5f).toInt() + i) % 2 == 0
                drawCircle(Color(if (on) Pal.YELLOW else Pal.shade(Pal.YELLOW, 0.3f)), 4f, Offset(30f + i * 23f, 28f))
                drawCircle(Color(if (!on) Pal.YELLOW else Pal.shade(Pal.YELLOW, 0.3f)), 4f, Offset(30f + i * 23f, 100f))
            }
            val comboText = if (combo >= 2) "COMBO x$combo" else "BONK 'EM!"
            PixelFont.drawCentered(this, comboText, GAME_W / 2f, 52f, 4f, Color(if (combo >= 5) Pal.CYAN else Pal.CREAM))

            for (i in moles.indices) drawHole(this, i)

            // Mallet.
            if (malletT < 0.3f) {
                val swing = clamp01(malletT / 0.07f)
                val deg = lerp(-50f, 10f, swing)
                val alpha = if (malletT > 0.15f) 1f - (malletT - 0.15f) / 0.15f else 1f
                rotate(deg, Offset(malletX + 40f, malletY + 40f)) {
                    drawRect(Color(Pal.TAN), Offset(malletX + 4f, malletY - 4f), Size(46f, 10f), alpha = alpha)
                    drawRoundRect(Color(Pal.RED), Offset(malletX - 26f, malletY - 22f), Size(34f, 46f), CornerRadius(8f, 8f), alpha = alpha)
                    drawRect(Color(Pal.CREAM), Offset(malletX - 22f, malletY - 18f), Size(8f, 38f), alpha = alpha * 0.5f)
                }
                if (malletT < 0.1f) {
                    drawCircle(Color.White, 30f * (1f + malletT * 8f), Offset(malletX, malletY), alpha = (0.1f - malletT) * 4f)
                }
            }
            if (stunT > 0f) {
                PixelFont.drawCentered(this, "STUNNED!", GAME_W / 2f, 600f, 3f, Color(Pal.RED), 0.5f + 0.5f * abs(sin(time * 20f)))
            }
        }
    }

    private fun drawHole(scope: DrawScope, i: Int) {
        val hx = COLS[i % 3]
        val hy = ROWS[i / 3]
        val m = moles[i]
        with(scope) {
            drawOval(Color(Pal.shade(Pal.DARKGREEN, 0.6f)), Offset(hx - HOLE_RX - 6f, hy - HOLE_RY - 4f), Size((HOLE_RX + 6f) * 2f, (HOLE_RY + 6f) * 2f))
            drawOval(Color(Pal.BLACK), Offset(hx - HOLE_RX, hy - HOLE_RY), Size(HOLE_RX * 2f, HOLE_RY * 2f))
            if (m.phase != Phase.HIDDEN && m.rise > 0.01f) {
                val key = when {
                    m.kind == Kind.BOMB -> if (m.phase == Phase.BONKED) "bomb_hit" else "bomb"
                    m.kind == Kind.GOLD -> if (m.phase == Phase.BONKED) "gold_hit" else "gold"
                    else -> if (m.phase == Phase.BONKED) "mole_hit" else "mole"
                }
                val img = sprites.getValue(key)
                val spriteH = img.height * SPRITE_SCALE
                val bottom = hy + spriteH * (1f - m.rise) + 6f
                val sq = m.squash.value
                clipRect(left = hx - 80f, top = hy - 200f, right = hx + 80f, bottom = hy + 4f) {
                    drawPixelImageSquash(img, hx, bottom, SPRITE_SCALE, 2f - sq, sq)
                }
                if (m.phase == Phase.BONKED) {
                    val headY = bottom - spriteH * sq
                    for (k in 0 until 3) {
                        val a = time * 8f + k * 2.1f
                        val sx = hx + kotlin.math.cos(a) * 26f
                        val sy = headY + 4f + sin(a) * 7f
                        PixelFont.draw(this, "${PixelFont.STAR}", sx - 6f, sy - 7f, 2f, Color(Pal.YELLOW))
                    }
                }
            }
            // Front lip of the hole overlaps the mole's base.
            drawOval(Color(Pal.shade(Pal.GREEN, 0.75f)), Offset(hx - HOLE_RX - 6f, hy - 1f), Size((HOLE_RX + 6f) * 2f, 14f))
            drawOval(Color(Pal.LIME), Offset(hx - HOLE_RX, hy + 1f), Size(HOLE_RX * 2f, 3f), alpha = 0.35f)
        }
    }

    private fun buildSprites(): Map<String, ImageBitmap> {
        fun mole(body: Int, face: Int, bonked: Boolean, sparkle: Boolean): ImageBitmap {
            val c = PixelCanvas(22, 24)
            c.disc(11f, 10f, 9f, body)
            c.fill(2, 10, 18, 14, body)
            c.ellipse(11f, 13f, 6.5f, 5.5f, face)
            if (bonked) {
                for (d in 0..2) {
                    c.set(6 + d, 7 + d, Pal.BLACK); c.set(8 - d, 7 + d, Pal.BLACK)
                    c.set(13 + d, 7 + d, Pal.BLACK); c.set(15 - d, 7 + d, Pal.BLACK)
                }
                c.fill(9, 15, 4, 2, Pal.DARKRED)
            } else {
                c.fill(7, 8, 2, 3, Pal.BLACK); c.fill(13, 8, 2, 3, Pal.BLACK)
                c.set(7, 8, Pal.WHITE); c.set(13, 8, Pal.WHITE)
                c.fill(10, 15, 1, 2, Pal.WHITE); c.fill(11, 15, 1, 2, Pal.WHITE)
            }
            c.ellipse(11f, 12.5f, 2.4f, 1.5f, Pal.HOTPINK)
            c.set(10, 12, Pal.WHITE)
            c.disc(4f, 20f, 2.4f, face); c.disc(18f, 20f, 2.4f, face)
            c.set(3, 18, Pal.WHITE); c.set(5, 18, Pal.WHITE); c.set(17, 18, Pal.WHITE); c.set(19, 18, Pal.WHITE)
            if (sparkle) {
                c.set(3, 3, Pal.WHITE); c.set(18, 2, Pal.WHITE); c.set(20, 12, Pal.WHITE)
            }
            c.outline(Pal.BLACK)
            return c.toImageBitmap()
        }

        fun bomb(bonked: Boolean): ImageBitmap {
            val c = PixelCanvas(22, 24)
            c.fill(10, 0, 2, 4, Pal.TAN)
            c.fill(8, 3, 6, 2, Pal.GRAY)
            c.disc(11f, 13f, 9.5f, Pal.DARKGRAY)
            c.fill(2, 13, 18, 11, Pal.DARKGRAY)
            c.disc(8f, 9f, 2.5f, Pal.GRAY)
            c.set(7, 8, Pal.WHITE)
            if (bonked) {
                c.disc(11f, 13f, 6f, Pal.ORANGE)
                c.disc(11f, 13f, 3.5f, Pal.YELLOW)
            } else {
                c.hline(5, 9, 10, Pal.RED); c.hline(13, 17, 10, Pal.RED)
                c.set(5, 9, Pal.RED); c.set(17, 9, Pal.RED)
                c.fill(7, 11, 2, 2, Pal.RED); c.fill(14, 11, 2, 2, Pal.RED)
                c.hline(8, 14, 17, Pal.BLACK)
                c.set(9, 16, Pal.WHITE); c.set(13, 16, Pal.WHITE)
            }
            c.outline(Pal.BLACK)
            return c.toImageBitmap()
        }

        return mapOf(
            "mole" to mole(Pal.BROWN, Pal.TAN, bonked = false, sparkle = false),
            "mole_hit" to mole(Pal.BROWN, Pal.TAN, bonked = true, sparkle = false),
            "gold" to mole(Pal.GOLD, Pal.YELLOW, bonked = false, sparkle = true),
            "gold_hit" to mole(Pal.GOLD, Pal.YELLOW, bonked = true, sparkle = true),
            "bomb" to bomb(false),
            "bomb_hit" to bomb(true),
        )
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** What a player sees at hole [i]: 0 nothing to hit, 1 mole, 2 gold mole, 3 bomb. */
    internal fun botView(i: Int): Int {
        val m = moles[i]
        val whackable = (m.phase == Phase.RISING || m.phase == Phase.UP || m.phase == Phase.FALLING) && m.rise > 0.3f
        if (!whackable) return 0
        return when (m.kind) {
            Kind.NORMAL -> 1
            Kind.GOLD -> 2
            Kind.BOMB -> 3
        }
    }

    internal fun holeX(i: Int): Float = COLS[i % 3]
    internal fun holeY(i: Int): Float = ROWS[i / 3]

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.GREEN))
        val cw = w / 3f
        val ch = h / 3f
        for (i in 0 until 9) {
            val cx = cw * (i % 3) + cw / 2f
            val cy = ch * (i / 3) + ch * 0.75f
            p.fill(cx - 2.5f, cy - 0.5f, 5f, 1.5f, Color(Pal.BLACK))
            val phase = (time * 1.7f + hash01(i, 9) * 7f) % 3f
            if (phase < 0.9f) {
                val up = sin(phase / 0.9f * Math.PI.toFloat())
                val top = cy - 1f - up * 3f
                val gold = i == ((time / 3f).toInt() % 9)
                p.fill(cx - 1.5f, top, 3f, cy - top, Color(if (gold) Pal.GOLD else Pal.BROWN))
                p.px(cx - 1f, top + 0.5f, Color(Pal.BLACK))
                p.px(cx + 1f, top + 0.5f, Color(Pal.BLACK))
            }
        }
    }
}
