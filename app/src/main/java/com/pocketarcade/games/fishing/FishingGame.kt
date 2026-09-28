package com.pocketarcade.games.fishing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.cos
import kotlin.math.sin

/** Difficulty and payout knobs for fishing. */
object FishingTuning {
    const val ROUND_SECONDS = 50f
    /** Seconds between fish rising, and how long a ripple lasts. */
    const val SPAWN_INTERVAL = 1.1f
    const val RIPPLE_LIFE = 2.2f
    const val HIT_RADIUS = 40f
    const val CATCH_POINTS = 20
    const val POINTS_PER_TICKET = 20
    const val BASE_TICKETS = 2
}

/**
 * Placeholder fishing game: fish rise as ripples on a pond and a tap on a ripple hooks one. A
 * stand-in so the hall, registry and tests have the machine; the real game replaces it.
 */
class FishingGame : BaseMiniGame() {
    override val id = "fishing"
    override val title = "GONE FISHING"
    override val marquee = "FISH"
    override val instructions = listOf("TAP A RIPPLE TO HOOK A FISH", "BE QUICK BEFORE IT SWIMS OFF")
    override val look = CabinetLook(body = Pal.TEAL, trim = Pal.YELLOW, glow = Pal.SKY, shape = CabinetShape.FISHING)
    override val cabinet: CabinetDesign get() = FishingCabinet
    override val roundSeconds = FishingTuning.ROUND_SECONDS

    private companion object {
        const val RIPPLES = 6
        val SPLASH_COLORS = intArrayOf(Pal.SKY, Pal.CYAN, Pal.WHITE)
    }

    // Ripples on the pond surface (world x, z; the water is at y = 0).
    private val rx = FloatArray(RIPPLES)
    private val rz = FloatArray(RIPPLES)
    private val age = FloatArray(RIPPLES)
    private val on = BooleanArray(RIPPLES)
    private var spawnT = 0f

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 480f, 640f, 180f, 0f, 240f, fovDeg = 50f)
    }
    private val pt = FloatArray(3)

    override fun reset() {
        on.fill(false)
        spawnT = 0.5f
    }

    override fun ticketsFor(score: Int): Int = FishingTuning.BASE_TICKETS + score / FishingTuning.POINTS_PER_TICKET

    override fun onTimeUp() = on.fill(false)

    /** Every cast is a single tap: there is no pointer to forget. */
    override fun cancelInput() {}

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp) return
        for (i in 0 until RIPPLES) {
            if (!on[i] || !stage.toField(rx[i], 0f, rz[i], pt)) continue
            val dx = pt[0] - x
            val dy = pt[1] - y
            if (dx * dx + dy * dy > FishingTuning.HIT_RADIUS * FishingTuning.HIT_RADIUS) continue
            on[i] = false
            addScore(FishingTuning.CATCH_POINTS, pt[0], pt[1] - 20f, Color(Pal.YELLOW))
            particles.burst(pt[0], pt[1], 18, 40f, 180f, SPLASH_COLORS, 0.6f, 4f, grav = 300f)
            play(Sfx.CATCH)
            fx.haptics.hit()
            return
        }
        play(Sfx.SPLASH, 0.5f)
    }

    override fun step(dt: Float) {
        for (i in 0 until RIPPLES) if (on[i]) {
            age[i] += dt
            if (age[i] > FishingTuning.RIPPLE_LIFE) on[i] = false
        }
        if (timeUp) return
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = FishingTuning.SPAWN_INTERVAL
            val i = on.indexOfFirst { !it }
            if (i >= 0) {
                on[i] = true
                age[i] = 0f
                val a = rng.range(0f, 6.28f)
                val d = rng.range(0f, 130f)
                rx[i] = 180f + cos(a) * d
                rz[i] = 240f + sin(a) * d * 0.8f
                play(Sfx.BITE, 0.4f)
            }
        }
    }

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.6f; l.ambG = 0.65f; l.ambB = 0.7f
        l.points.clear()
        r.gradient(0xFF06243A.toInt(), Pal.TEAL)
        r.flat(180f, 240f, -1f, 420f, 360f, TexKit.dot.full, tint = Pal.BLUE, emissive = 0.4f)
        val glow = TexKit.glow.full
        for (i in 0 until RIPPLES) if (on[i]) {
            val k = age[i] / FishingTuning.RIPPLE_LIFE
            val s = 40f + 50f * (time * 2f + i).rem(1f)
            r.flat(rx[i], rz[i], 0.5f, s, s * 0.8f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.6f * (1f - k), tint = Pal.CYAN)
            r.flat(rx[i], rz[i], 0.6f, 20f, 10f, TexKit.dot.full, tint = Pal.ORANGE, angle = time * 2f + i)
        }
        stage.present()
    }

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NAVY))
        p.disc(w / 2f, h * 0.6f, h * 0.35f, Color(Pal.BLUE))
        for (k in 0 until 3) {
            val a = time * (0.8f + k * 0.3f) + k * 2.1f
            p.disc(w / 2f + cos(a) * w * 0.25f, h * 0.6f + sin(a) * h * 0.18f, 1f, Color(Pal.ORANGE))
        }
        val bob = sin(time * 3f)
        p.fill(w * 0.7f, 2f, 0.5f, h * 0.45f + bob, Color(Pal.LIGHTGRAY))
        p.disc(w * 0.7f, h * 0.47f + bob, 1f, Color(Pal.RED))
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("FISH", w * 0.3f, 2f, Color(Pal.YELLOW), tiny = true)
    }
}
