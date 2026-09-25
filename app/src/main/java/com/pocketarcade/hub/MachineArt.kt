package com.pocketarcade.hub

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.CanvasPainter
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame

/**
 * The printed and lit artwork for one game's cabinets: side art, marquee, kick panel, control
 * panel and an LED score display, all in the game's colours.
 */
class MachineArt(val game: MiniGame) {
    val body = game.look.body
    val trim = game.look.trim
    val glow = game.look.glow

    val bodyPaint: Texture by lazy { HallArt.paint(dim(body, 0.85f)) }
    val darkPaint: Texture by lazy { HallArt.paint(dim(body, 0.3f), 0.05f, 0.7f) }
    val black: Texture by lazy { HallArt.paint(0xFF15131C.toInt(), 0.06f, 0.8f) }
    val trimTex: Texture by lazy { HallArt.solid(trim) }
    val glowTex: Texture by lazy { HallArt.solid(glow) }

    /** Side panel art: the body colour swept with stripes, a starburst and the title. */
    val sideArt: Texture by lazy {
        val w = 256
        val h = 512
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(body, 0.15f), dim(body, 0.55f))
        // Diagonal stripes.
        for (k in 0 until 3) {
            val y = 300f + k * 46f
            val c = if (k == 1) glow else trim
            tp.polygon(floatArrayOf(0f, y, w.toFloat(), y - 170f, w.toFloat(), y - 150f, 0f, y + 20f), c)
        }
        // A burst behind the title.
        val cx = w * 0.5f
        val cy = h * 0.28f
        for (k in 0 until 16) {
            val a = k * Math.PI.toFloat() / 8f
            tp.polygon(
                floatArrayOf(
                    cx, cy,
                    cx + kotlin.math.cos(a) * 170f, cy + kotlin.math.sin(a) * 170f,
                    cx + kotlin.math.cos(a + 0.18f) * 170f, cy + kotlin.math.sin(a + 0.18f) * 170f,
                ),
                alpha(lift(glow, 0.3f), 0.18f),
            )
        }
        tp.circle(cx, cy, 70f, alpha(dim(body, 0.4f), 0.85f))
        tp.ring(cx, cy, 70f, 6f, trim)
        val words = game.title.split(' ', '-')
        val size = 64f / words.maxOf { it.length.coerceAtLeast(3) }.coerceAtMost(8) * 3.2f
        for ((i, word) in words.withIndex()) {
            val y = cy - (words.size - 1) * size * 0.5f + i * size + size * 0.35f
            tp.outlinedText(word, cx, y, size.coerceAtMost(46f), lift(glow, 0.6f), dim(body, 0.3f), 5f, Fonts.display)
        }
        for (k in 0 until 40) {
            tp.circle(hash01(k, 3) * w, 360f + hash01(k, 4) * 150f, 1.5f + hash01(k, 5) * 2f, alpha(-1, 0.6f))
        }
        tp.toTexture().also { tp.recycle() }
    }

    /** The lit sign on top of the cabinet. */
    val marquee: Texture by lazy {
        val w = 512
        val h = 160
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(body, 0.35f), dim(body, 0.6f))
        tp.radial(w / 2f, h / 2f, w * 0.45f, alpha(lift(glow, 0.5f), 0.55f), 0)
        val size = when {
            game.title.length > 11 -> 62f
            game.title.length > 8 -> 74f
            else -> 92f
        }
        tp.glowText(game.title, w / 2f, h / 2f + size * 0.34f, size, -1, glow, 10f, Fonts.display, 0.02f)
        tp.strokeRound(5f, 5f, w - 10f, h - 10f, 14f, 6f, trim)
        tp.toTexture().also { tp.recycle() }
    }

    /** Kick panel: coin door with lit token slots, speaker grille and a "PLAY" sticker. */
    val kick: Texture by lazy {
        val w = 256
        val h = 256
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), dim(body, 0.55f), dim(body, 0.3f))
        // Speaker grille.
        tp.round(40f, 18f, 176f, 44f, 10f, 0xFF16141C.toInt())
        for (x in 0 until 16) for (y in 0 until 4) tp.circle(52f + x * 10.2f, 28f + y * 8f, 2.2f, 0xFF2E2B38.toInt())
        // Coin door.
        tp.round(58f, 86f, 140f, 150f, 8f, 0xFF3A3C46.toInt())
        tp.roundGrad(62f, 90f, 132f, 142f, 6f, 0xFFB8BCC8.toInt(), 0xFF6E7280.toInt())
        for (k in 0 until 2) {
            val x = 84f + k * 64f
            tp.round(x - 16f, 110f, 32f, 44f, 5f, 0xFF20222A.toInt())
            tp.round(x - 11f, 116f, 22f, 32f, 4f, 0xFFFF3B30.toInt())
            tp.text("TOKEN", x, 138f, 9f, -1, Fonts.condensed)
            tp.rect(x - 2f, 164f, 4f, 20f, 0xFF20222A.toInt())
        }
        tp.circle(128f, 206f, 9f, 0xFF20222A.toInt())
        tp.text("1 TOKEN PER PLAY", 128f, 226f, 13f, 0xFF20222A.toInt(), Fonts.condensed)
        tp.grain(0.03f, 11)
        tp.toTexture().also { tp.recycle() }
    }

    /** The control panel overlay: printed how-to lines and a logo strip. */
    val panel: Texture by lazy {
        val w = 256
        val h = 128
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), 0xFF24222C.toInt(), 0xFF121016.toInt())
        tp.rect(0f, 0f, w.toFloat(), 8f, trim)
        tp.text(game.instructions.firstOrNull() ?: game.title, w / 2f, 110f, 17f, alpha(-1, 0.85f), Fonts.condensed)
        tp.toTexture().also { tp.recycle() }
    }

    /** Screen bezel: glossy black with a printed frame. */
    val bezel: Texture by lazy {
        val tp = TexPaint(256, 256)
        tp.fill(0xFF0E0D12.toInt())
        tp.strokeRound(10f, 10f, 236f, 236f, 18f, 4f, alpha(glow, 0.8f))
        tp.text(game.marquee, 128f, 244f, 16f, alpha(trim, 0.9f), Fonts.condensed)
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ live displays

    private var displayText = ""
    private val displayPaint by lazy { TexPaint(256, 64) }

    /** A red LED score display showing the machine's best score and a "PLAY" call. */
    val display: Texture by lazy { Texture(256, 64) }

    fun updateDisplay(best: Int, t: Float) {
        val text = if ((t % 6f) < 3f) "HI $best" else "PLAY!"
        if (text == displayText) return
        displayText = text
        val tp = displayPaint
        tp.fill(0xFF0A0404.toInt())
        for (x in 0 until 256 step 4) tp.rect(x.toFloat(), 0f, 1f, 64f, 0xFF140808.toInt())
        tp.glowText(text, 128f, 46f, 42f, 0xFFFF5A3C.toInt(), 0xFFFF2010.toInt(), 6f, Fonts.condensed, 0.12f)
        tp.update(display)
    }

    /** Screen size in painter units for this cabinet shape (portrait for the tower). */
    fun screenUnits(): Pair<Int, Int> = when (game.look.shape) {
        CabinetShape.TOWER -> 16 to 24
        CabinetShape.RACER -> 26 to 18
        else -> 24 to 18
    }
}

/**
 * One cabinet's live screen: the game's attract loop, drawn with smooth shapes and real text,
 * and a "HI score" card every few seconds, with CRT scanlines.
 */
class LiveScreen(private val art: MachineArt, private val seed: Int) {
    private val units = art.screenUnits()
    private val scale = 10f
    private val tp by lazy { TexPaint((units.first * scale).toInt(), (units.second * scale).toInt()) }
    private val painter by lazy { CanvasPainter(tp, scale) }
    val texture: Texture by lazy { Texture((units.first * scale).toInt(), (units.second * scale).toInt()) }
    private var frame = 0

    fun paint(best: Int, t: Float) {
        // Screens refresh at 30 fps; that's plenty for attract loops.
        frame++
        if (frame % 2 != 0) return
        val (w, h) = units
        val cycle = (t + seed * 1.7f) % 9f
        if (cycle > 7f) {
            tp.fill(0xFF05040A.toInt())
            tp.glowText("HIGH SCORE", tp.w / 2f, tp.h * 0.36f, tp.h * 0.13f, 0xFFFFE14D.toInt(), 0xFFFF9A3C.toInt(), 5f, Fonts.display)
            tp.glowText(best.toString(), tp.w / 2f, tp.h * 0.72f, tp.h * 0.26f, -1, art.glow, 8f, Fonts.display)
        } else {
            tp.fill(0xFF000000.toInt())
            art.game.drawAttract(painter, w, h, t + seed * 3.1f)
        }
        // Scanlines and a soft glass sheen.
        for (y in 0 until tp.h step 3) tp.rect(0f, y.toFloat(), tp.w.toFloat(), 1f, 0x33000000)
        tp.radial(tp.w * 0.3f, tp.h * 0.2f, tp.w * 0.5f, 0x22FFFFFF, 0)
        tp.update(texture)
    }
}
