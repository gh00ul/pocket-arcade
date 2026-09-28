package com.pocketarcade.games.shooter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.hub.CabinetDesign
import kotlin.math.sin

/** Difficulty and payout knobs for the shooter. */
object ShooterTuning {
    const val ROUND_SECONDS = 45f
    /** Seconds between targets popping up, and how long each stays up. */
    const val SPAWN_INTERVAL = 0.7f
    const val TARGET_LIFE = 1.8f
    /** Hit radius around a target's centre, in field units. */
    const val HIT_RADIUS = 34f
    const val HIT_POINTS = 10
    const val CLIP = 6
    const val RELOAD_SECONDS = 0.9f
    const val POINTS_PER_TICKET = 20
    const val BASE_TICKETS = 2
}

/**
 * Placeholder light-gun shooter: targets pop up on the back wall of a range and a tap shoots
 * where it lands, six shots to a clip. A stand-in so the hall, registry and tests have the
 * machine; the real game replaces it.
 */
class ShooterGame : BaseMiniGame() {
    override val id = "shooter"
    override val title = "SHOOTOUT"
    override val marquee = "SHOOT"
    override val instructions = listOf("TAP A TARGET TO SHOOT IT", "SIX SHOTS, THEN RELOAD", "HIT THEM BEFORE THEY DROP")
    override val look = CabinetLook(body = Pal.NAVY, trim = Pal.ORANGE, glow = Pal.ORANGE, shape = CabinetShape.GUN)
    override val cabinet: CabinetDesign get() = ShooterCabinet
    override val roundSeconds = ShooterTuning.ROUND_SECONDS

    private companion object {
        const val TARGETS = 8
        val HIT_COLORS = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.WHITE)
    }

    // Targets stand on the range's back wall (world x across, y up, z = 0).
    private val tx = FloatArray(TARGETS)
    private val ty = FloatArray(TARGETS)
    private val age = FloatArray(TARGETS)
    private val up = BooleanArray(TARGETS)
    private var spawnT = 0f
    private var shots = ShooterTuning.CLIP
    private var reloadT = 0f

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 220f, 620f, 180f, 220f, 0f, fovDeg = 50f)
    }
    private val pt = FloatArray(3)

    override fun reset() {
        up.fill(false)
        spawnT = 0.4f
        shots = ShooterTuning.CLIP
        reloadT = 0f
    }

    override fun ticketsFor(score: Int): Int = ShooterTuning.BASE_TICKETS + score / ShooterTuning.POINTS_PER_TICKET

    override fun onTimeUp() = up.fill(false)

    /** Every shot is a single tap: there is no pointer to forget. */
    override fun cancelInput() {}

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp) return
        if (reloadT > 0f) {
            play(Sfx.DRY_FIRE)
            return
        }
        play(Sfx.GUNSHOT, 0.8f)
        shake.add(0.08f)
        if (--shots <= 0) reloadT = ShooterTuning.RELOAD_SECONDS
        for (i in 0 until TARGETS) {
            if (!up[i] || !stage.toField(tx[i], ty[i], 0f, pt)) continue
            val dx = pt[0] - x
            val dy = pt[1] - y
            if (dx * dx + dy * dy > ShooterTuning.HIT_RADIUS * ShooterTuning.HIT_RADIUS) continue
            up[i] = false
            addScore(ShooterTuning.HIT_POINTS, pt[0], pt[1] - 20f, Color(Pal.YELLOW))
            particles.burst(pt[0], pt[1], 16, 60f, 220f, HIT_COLORS, 0.5f, 4f)
            play(Sfx.RICOCHET, 0.5f, rng.range(0.9f, 1.2f))
            fx.haptics.hit()
            return
        }
    }

    override fun step(dt: Float) {
        if (reloadT > 0f) {
            reloadT -= dt
            if (reloadT <= 0f) {
                shots = ShooterTuning.CLIP
                play(Sfx.RELOAD, 0.8f)
            }
        }
        for (i in 0 until TARGETS) if (up[i]) {
            age[i] += dt
            if (age[i] > ShooterTuning.TARGET_LIFE) up[i] = false
        }
        if (timeUp) return
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = ShooterTuning.SPAWN_INTERVAL
            val i = up.indexOfFirst { !it }
            if (i >= 0) {
                up[i] = true
                age[i] = 0f
                tx[i] = rng.range(40f, 320f)
                ty[i] = rng.range(90f, 330f)
            }
        }
    }

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.6f; l.ambG = 0.55f; l.ambB = 0.65f
        l.points.clear()
        r.gradient(0xFF0A0E24.toInt(), Pal.NAVY)
        val white = TexKit.white.full
        // Back wall and floor of the range.
        r.quad(-200f, 460f, 0f, 560f, 460f, 0f, 560f, 0f, 0f, -200f, 0f, 0f, white, 0f, 0f, 1f, tint = Pal.DEEP)
        r.quad(-200f, 0f, 0f, 560f, 0f, 0f, 560f, 0f, 600f, -200f, 0f, 600f, white, 0f, 1f, 0f, tint = Pal.PLUM)
        val dot = TexKit.dot.full
        for (i in 0 until TARGETS) if (up[i]) {
            val pop = (age[i] / 0.15f).coerceAtMost(1f)
            r.sprite(tx[i], ty[i], 1f, 56f * pop, 56f * pop, dot, tint = Pal.RED)
            r.sprite(tx[i], ty[i], 2f, 36f * pop, 36f * pop, dot, tint = Pal.WHITE)
            r.sprite(tx[i], ty[i], 3f, 16f * pop, 16f * pop, dot, tint = Pal.RED, emissive = 0.5f)
            r.sprite(tx[i], ty[i], 4f, 90f * pop, 90f * pop, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.3f, tint = Pal.ORANGE)
        }
        stage.present()
        val text = if (reloadT > 0f) "RELOADING" else "AMMO $shots"
        ArcadeFont.drawCentered(scope, text, GAME_W / 2f, 600f, 2.5f, Color(if (reloadT > 0f) Pal.RED else Pal.YELLOW))
    }

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NAVY))
        for (k in 0 until 3) {
            val cycle = (time * 0.8f + k * 0.33f) % 1f
            if (cycle > 0.7f) continue
            val x = w * (0.2f + 0.3f * k)
            val y = h * (0.35f + 0.2f * sin(k * 2.1f + time * 0.3f))
            p.disc(x, y, 3f, Color(Pal.RED))
            p.disc(x, y, 2f, Color.White)
            p.disc(x, y, 1f, Color(Pal.RED))
        }
        val cx = w / 2f + sin(time * 1.3f) * w * 0.35f
        val cy = h / 2f + sin(time * 0.9f) * h * 0.25f
        p.fill(cx - 3f, cy, 6f, 0.5f, Color(Pal.YELLOW))
        p.fill(cx, cy - 3f, 0.5f, 6f, Color(Pal.YELLOW))
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("SHOOT", w / 2f, h - 7f, Color(Pal.ORANGE), tiny = true)
    }
}
