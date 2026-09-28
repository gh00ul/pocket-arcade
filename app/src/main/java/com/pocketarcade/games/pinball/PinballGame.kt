package com.pocketarcade.games.pinball

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.hub.CabinetDesign
import kotlin.math.sin

/** Difficulty and payout knobs for pinball. */
object PinballTuning {
    const val ROUND_SECONDS = 60f
    /** How long a bumper stays lit, waiting to be hit. */
    const val LIT_SECONDS = 1.3f
    const val HIT_RADIUS = 40f
    const val BUMPER_POINTS = 10
    const val POINTS_PER_TICKET = 20
    const val BASE_TICKETS = 2
}

/**
 * Placeholder pinball: one bumper at a time lights up on a tilted playfield and a tap scores it;
 * taps elsewhere flick the flippers. A stand-in so the hall, registry and tests have the
 * machine; the real game replaces it.
 */
class PinballGame : BaseMiniGame() {
    override val id = "pinball"
    override val title = "PINBALL"
    override val marquee = "FLIP"
    override val instructions = listOf("TAP THE LIT BUMPER", "LEFT AND RIGHT FLIP")
    override val look = CabinetLook(body = Pal.PURPLE, trim = Pal.CYAN, glow = Pal.HOTPINK, shape = CabinetShape.PINBALL)
    override val cabinet: CabinetDesign get() = PinballCabinet
    override val roundSeconds = PinballTuning.ROUND_SECONDS

    private companion object {
        /** Bumper positions on the playfield (world x, z). */
        val BX = floatArrayOf(110f, 250f, 180f, 90f, 270f)
        val BZ = floatArrayOf(140f, 140f, 240f, 330f, 330f)
        val HIT_COLORS = intArrayOf(Pal.HOTPINK, Pal.CYAN, Pal.WHITE)
    }

    private var lit = -1
    private var litT = 0f
    private var flipL = 0f
    private var flipR = 0f

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 520f, 700f, 180f, 0f, 260f, fovDeg = 48f)
    }
    private val pt = FloatArray(3)

    override fun reset() {
        lit = -1
        litT = 0.5f
        flipL = 0f
        flipR = 0f
    }

    override fun ticketsFor(score: Int): Int = PinballTuning.BASE_TICKETS + score / PinballTuning.POINTS_PER_TICKET

    override fun onTimeUp() {
        lit = -1
    }

    /** Every press is a single tap: there is no pointer to forget. */
    override fun cancelInput() {}

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp) return
        if (lit >= 0 && stage.toField(BX[lit], 10f, BZ[lit], pt)) {
            val dx = pt[0] - x
            val dy = pt[1] - y
            if (dx * dx + dy * dy < PinballTuning.HIT_RADIUS * PinballTuning.HIT_RADIUS) {
                addScore(PinballTuning.BUMPER_POINTS, pt[0], pt[1] - 20f, Color(Pal.CYAN))
                particles.burst(pt[0], pt[1], 14, 50f, 200f, HIT_COLORS, 0.5f, 4f)
                play(Sfx.BUMPER)
                fx.haptics.hit()
                lit = -1
                litT = 0.3f
                return
            }
        }
        if (x < GAME_W / 2f) flipL = 1f else flipR = 1f
        play(Sfx.FLIPPER, 0.8f)
    }

    override fun step(dt: Float) {
        flipL = (flipL - dt * 6f).coerceAtLeast(0f)
        flipR = (flipR - dt * 6f).coerceAtLeast(0f)
        if (timeUp) return
        litT -= dt
        if (litT <= 0f) {
            lit = if (lit >= 0) -1 else rng.nextInt(BX.size)
            litT = if (lit >= 0) PinballTuning.LIT_SECONDS else 0.4f
        }
    }

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.6f; l.ambG = 0.55f; l.ambB = 0.7f
        l.points.clear()
        r.gradient(0xFF0B0718.toInt(), Pal.DEEP)
        val white = TexKit.white.full
        r.quad(20f, 0f, 20f, 340f, 0f, 20f, 340f, 0f, 520f, 20f, 0f, 520f, white, 0f, 1f, 0f, tint = Pal.INDIGO)
        val dot = TexKit.dot.full
        for (i in BX.indices) {
            val on = i == lit
            r.flat(BX[i], BZ[i], 1f, 44f, 44f, dot, tint = if (on) Pal.HOTPINK else Pal.VIOLET, emissive = if (on) 1f else 0f)
            if (on) r.flat(BX[i], BZ[i], 2f, 110f, 110f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.HOTPINK)
        }
        // Flippers at the bottom, kicking up when pressed.
        r.flat(130f, 470f, 2f, 70f, 12f, white, angle = 0.35f - flipL * 0.7f, tint = Pal.CYAN)
        r.flat(230f, 470f, 2f, 70f, 12f, white, angle = -0.35f + flipR * 0.7f, tint = Pal.CYAN)
        stage.present()
    }

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.INDIGO))
        val lit = (time * 2f).toInt() % 3
        for (k in 0 until 3) p.disc(w * (0.25f + 0.25f * k), h * 0.35f, 2.5f, Color(if (k == lit) Pal.HOTPINK else Pal.VIOLET))
        val bx = w / 2f + sin(time * 2.3f) * w * 0.35f
        val by = h * 0.55f + sin(time * 3.1f) * h * 0.3f
        p.disc(bx, by, 1.2f, Color.White)
        p.fill(w * 0.2f, h - 3f, w * 0.2f, 1f, Color(Pal.CYAN))
        p.fill(w * 0.6f, h - 3f, w * 0.2f, 1f, Color(Pal.CYAN))
    }
}
