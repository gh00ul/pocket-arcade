package com.pocketarcade.hub

import android.graphics.Bitmap
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

    private val shape = game.look.shape

    /**
     * Side panel art: the body colour swept by bold speed bands, a halftone corner, the game's
     * emblem big in a ringed badge and the title across the bottom, with chrome edge stripes.
     */
    val sideArt: Texture by lazy {
        val w = 256
        val h = 512
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(body, 0.2f), body, dim(body, 0.45f))
        // Halftone dots fading out of the top corner.
        for (y in 0 until 12) for (x in 0 until 10) {
            val d = (x + y) / 18f
            if (d > 1f) continue
            tp.circle(w - 8f - x * 16f + (y % 2) * 8f, 10f + y * 16f, 6.5f * (1f - d), alpha(lift(glow, 0.5f), 0.35f))
        }
        // Speed bands sweeping up across the panel, each with a dark shadow edge.
        for (k in 0 until 4) {
            val y = 470f - k * 58f
            val c = when (k % 3) { 0 -> glow; 1 -> trim; else -> lift(body, 0.45f) }
            val t = 26f - k * 3f
            tp.polygon(floatArrayOf(0f, y + 8f, w.toFloat(), y - 190f + 8f, w.toFloat(), y - 190f + t + 8f, 0f, y + t + 8f), alpha(0xFF000000.toInt(), 0.35f))
            tp.polygon(floatArrayOf(0f, y, w.toFloat(), y - 190f, w.toFloat(), y - 190f + t, 0f, y + t), c)
            tp.polygon(floatArrayOf(0f, y, w.toFloat(), y - 190f, w.toFloat(), y - 190f + 3f, 0f, y + 3f), alpha(-1, 0.45f))
        }
        // The badge: a burst behind the emblem.
        val cx = w * 0.5f
        val cy = h * 0.34f
        for (k in 0 until 18) {
            val a = k * Math.PI.toFloat() / 9f
            tp.polygon(
                floatArrayOf(
                    cx, cy,
                    cx + kotlin.math.cos(a) * 190f, cy + kotlin.math.sin(a) * 190f,
                    cx + kotlin.math.cos(a + 0.17f) * 190f, cy + kotlin.math.sin(a + 0.17f) * 190f,
                ),
                alpha(lift(glow, 0.3f), 0.2f),
            )
        }
        tp.circle(cx, cy + 6f, 86f, alpha(0xFF000000.toInt(), 0.4f))
        tp.emblem(shape, cx, cy, 80f, body, trim, glow)
        // Title across the foot of the panel.
        val words = game.title.split(' ')
        val size = fitSize(tp, words, 220f, 60f)
        for ((i, word) in words.withIndex()) {
            val y = h - 34f - (words.size - 1 - i) * size * 0.95f
            tp.outlinedText(word, cx, y, size, lift(glow, 0.65f), dim(body, 0.2f), size * 0.16f, Fonts.display)
        }
        // Chrome T-molding catch-light down both edges.
        tp.hgrad(0f, 0f, 10f, h.toFloat(), alpha(-1, 0.35f), 0)
        tp.hgrad(w - 10f, 0f, 10f, h.toFloat(), 0, alpha(0xFF000000.toInt(), 0.35f))
        tp.grain(0.03f, 13)
        tp.toTexture().also { tp.recycle() }
    }

    /**
     * The lit sign on top of the cabinet: backlit plastic with a chrome bevel, a neon tube round
     * the edge, the game's emblem either side and its title in bold outlined lettering.
     */
    val marquee: Texture by lazy {
        val w = 512
        val h = 160
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), dim(body, 0.3f), lift(body, 0.12f), dim(body, 0.4f))
        // A sunburst of light behind the lettering.
        for (k in 0 until 28) {
            val a = k * Math.PI.toFloat() / 14f
            tp.polygon(
                floatArrayOf(
                    w / 2f, h / 2f,
                    w / 2f + kotlin.math.cos(a) * 420f, h / 2f + kotlin.math.sin(a) * 420f,
                    w / 2f + kotlin.math.cos(a + 0.11f) * 420f, h / 2f + kotlin.math.sin(a + 0.11f) * 420f,
                ),
                alpha(lift(glow, 0.4f), 0.13f),
            )
        }
        tp.radial(w / 2f, h / 2f, w * 0.4f, alpha(lift(glow, 0.55f), 0.6f), 0)
        // Emblems either side.
        tp.emblem(shape, 70f, h / 2f, 50f, body, trim, glow)
        tp.emblem(shape, w - 70f, h / 2f, 50f, body, trim, glow)
        // The title: one line, or two for a two-word name, as big as fits between the emblems.
        val words = game.title.split(' ')
        val lines = if (words.size == 2) words else listOf(game.title)
        val size = fitSize(tp, lines, 270f, if (lines.size == 2) 64f else 92f)
        for ((i, line) in lines.withIndex()) {
            val y = h / 2f + size * 0.36f + (i - (lines.size - 1) / 2f) * size * 0.92f
            tp.glowText(line, w / 2f, y, size, lift(glow, 0.8f), glow, 12f, Fonts.display, 0.02f)
            tp.outlinedText(line, w / 2f, y, size, lift(glow, 0.85f), dim(body, 0.15f), size * 0.12f, Fonts.display, 0.02f)
            tp.text(line, w / 2f, y, size, alpha(-1, 0.55f), Fonts.display, spacing = 0.02f)
        }
        // Plastic sheen over the top half.
        tp.vgrad(0f, 0f, w.toFloat(), h * 0.48f, alpha(-1, 0.2f), alpha(-1, 0.03f))
        // Neon tube and chrome bevel.
        tp.glow(6f, alpha(glow, 0.9f)) { strokeRound(14f, 14f, w - 28f, h - 28f, 16f, 5f, -1) }
        tp.strokeRound(14f, 14f, w - 28f, h - 28f, 16f, 3f, lift(glow, 0.7f))
        tp.strokeRound(3f, 3f, w - 6f, h - 6f, 12f, 6f, 0xFFB8BCC8.toInt())
        tp.rect(0f, 0f, w.toFloat(), 3f, 0xFFF4F6FF.toInt())
        tp.rect(0f, h - 3f, w.toFloat(), 3f, 0xFF2A2C34.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /**
     * The top of the marquee, which is what the hall camera sees most of: a backlit topper
     * with a pinstripe border, the emblem and the machine's short name.
     */
    val topper: Texture by lazy {
        val w = 256
        val h = 192
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), dim(body, 0.5f), dim(body, 0.8f))
        tp.radial(w / 2f, h / 2f, w * 0.55f, alpha(lift(glow, 0.3f), 0.45f), 0)
        for (k in 0 until 7) tp.rect(0f, 20f + k * 24f, w.toFloat(), 2f, alpha(dim(body, 0.3f), 0.5f))
        tp.round(8f, 8f, w - 16f, h - 16f, 14f, alpha(0xFF000000.toInt(), 0.18f))
        tp.strokeRound(8f, 8f, w - 16f, h - 16f, 14f, 5f, trim)
        tp.strokeRound(17f, 17f, w - 34f, h - 34f, 9f, 2f, alpha(glow, 0.8f))
        tp.emblem(shape, 62f, h / 2f, 40f, body, trim, glow)
        val size = fitSize(tp, listOf(game.marquee), 140f, 64f)
        tp.outlinedText(game.marquee, 164f, h / 2f + size * 0.36f, size, lift(glow, 0.75f), dim(body, 0.15f), size * 0.14f, Fonts.display)
        tp.vgrad(0f, 0f, w.toFloat(), h * 0.4f, alpha(-1, 0.14f), 0)
        tp.toTexture().also { tp.recycle() }
    }

    /** Kick panel: speaker grille, chevrons pointing up at the game, a stripe and a sticker. */
    val kick: Texture by lazy {
        val w = 256
        val h = 256
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), dim(body, 0.6f), dim(body, 0.28f))
        // Speaker grille.
        tp.round(40f, 18f, 176f, 44f, 10f, 0xFF16141C.toInt())
        for (x in 0 until 16) for (y in 0 until 4) tp.circle(52f + x * 10.2f, 28f + y * 8f, 2.2f, 0xFF2E2B38.toInt())
        tp.strokeRound(40f, 18f, 176f, 44f, 10f, 2f, 0xFF6E7280.toInt())
        // Racing stripe with chevrons.
        tp.rect(0f, 84f, w.toFloat(), 26f, trim)
        tp.rect(0f, 84f, w.toFloat(), 3f, alpha(-1, 0.4f))
        tp.rect(0f, 116f, w.toFloat(), 6f, glow)
        for (k in 0 until 8) {
            val x = 8f + k * 32f
            tp.polygon(floatArrayOf(x, 108f, x + 12f, 88f, x + 24f, 108f, x + 18f, 108f, x + 12f, 98f, x + 6f, 108f), dim(body, 0.35f))
        }
        // Printed emblems low on the panel, either side of where the coin door fits.
        tp.emblem(shape, 40f, 196f, 28f, body, trim, glow)
        tp.emblem(shape, w - 40f, 196f, 28f, body, trim, glow)
        tp.text("1 TOKEN PER PLAY", 128f, 244f, 13f, alpha(-1, 0.7f), Fonts.condensed)
        tp.grain(0.03f, 11)
        tp.toTexture().also { tp.recycle() }
    }

    /** The face of the coin door (its slots light up separately): brushed steel with two slot plates. */
    val coinDoor: Texture by lazy {
        val w = 128
        val h = 160
        val tp = TexPaint(w, h)
        tp.round(0f, 0f, w.toFloat(), h.toFloat(), 8f, 0xFF3A3C46.toInt())
        tp.roundGrad(4f, 4f, w - 8f, h - 8f, 6f, 0xFFC8CCD8.toInt(), 0xFF6E7280.toInt())
        for (y in 6 until h - 6 step 3) tp.rect(6f, y.toFloat(), w - 12f, 1f, alpha(-1, 0.06f))
        for (k in 0 until 2) {
            val x = 36f + k * 56f
            tp.round(x - 20f, 18f, 40f, 58f, 5f, 0xFF20222A.toInt())
            tp.round(x - 14f, 24f, 28f, 42f, 4f, 0xFF4A1010.toInt())
            tp.text("25", x, 88f, 12f, 0xFF20222A.toInt(), Fonts.condensed)
            tp.round(x - 4f, 98f, 8f, 26f, 3f, 0xFF20222A.toInt())
        }
        tp.circle(64f, 140f, 8f, 0xFF20222A.toInt())
        tp.circle(64f, 140f, 3f, 0xFF8A8C98.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** The control panel overlay: button rings, player labels, a stripe and the how-to line. */
    val panel: Texture by lazy {
        val w = 256
        val h = 128
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2A2833.toInt(), 0xFF121016.toInt())
        // Diagonal stripe of the game's colours across the panel.
        tp.polygon(floatArrayOf(0f, 70f, w.toFloat(), 20f, w.toFloat(), 44f, 0f, 94f), alpha(body, 0.55f))
        tp.polygon(floatArrayOf(0f, 94f, w.toFloat(), 44f, w.toFloat(), 50f, 0f, 100f), alpha(glow, 0.8f))
        tp.rect(0f, 0f, w.toFloat(), 8f, trim)
        tp.rect(0f, 8f, w.toFloat(), 2f, alpha(-1, 0.3f))
        // Printed rings where the buttons sit, with player labels.
        for (k in 0 until 3) tp.ring(150f + k * 32f, 52f, 13f, 3f, alpha(lift(glow, 0.3f), 0.8f))
        tp.ring(66f, 52f, 20f, 3f, alpha(trim, 0.8f))
        tp.text("1P", 22f, 30f, 14f, alpha(trim, 0.9f), Fonts.condensed)
        tp.text("2P", w - 22f, 30f, 14f, alpha(trim, 0.9f), Fonts.condensed)
        tp.text(game.instructions.firstOrNull() ?: game.title, w / 2f, 114f, 16f, alpha(-1, 0.85f), Fonts.condensed)
        tp.vgrad(0f, 10f, w.toFloat(), 40f, alpha(-1, 0.08f), 0)
        tp.toTexture().also { tp.recycle() }
    }

    /** Screen bezel: glossy black with a printed neon frame, corner flashes and the game's name. */
    val bezel: Texture by lazy {
        val tp = TexPaint(256, 256)
        tp.vgrad(0f, 0f, 256f, 256f, 0xFF1A1822.toInt(), 0xFF0A090E.toInt())
        tp.glow(5f, alpha(glow, 0.8f)) { strokeRound(10f, 10f, 236f, 236f, 18f, 4f, -1) }
        tp.strokeRound(10f, 10f, 236f, 236f, 18f, 3f, lift(glow, 0.5f))
        for ((x, y) in arrayOf(0f to 0f, 256f to 0f, 0f to 256f, 256f to 256f)) {
            val sx = if (x == 0f) 1f else -1f
            val sy = if (y == 0f) 1f else -1f
            tp.polygon(floatArrayOf(x, y, x + sx * 44f, y, x, y + sy * 44f), trim)
            tp.polygon(floatArrayOf(x + sx * 50f, y, x + sx * 58f, y, x, y + sy * 58f, x, y + sy * 50f), alpha(glow, 0.8f))
        }
        tp.text(game.marquee, 128f, 244f, 16f, alpha(trim, 0.9f), Fonts.condensed)
        tp.text("INSERT COIN", 128f, 22f, 11f, alpha(-1, 0.6f), Fonts.condensed)
        tp.vgrad(0f, 0f, 256f, 90f, alpha(-1, 0.08f), 0)
        tp.toTexture().also { tp.recycle() }
    }

    /** The biggest text size (≤ [max]) at which every one of [lines] fits in [width]. */
    private fun fitSize(tp: TexPaint, lines: List<String>, width: Float, max: Float): Float {
        var size = max
        for (s in lines) {
            val wd = tp.textWidth(s, max, Fonts.display, 0.02f)
            if (wd > width) size = minOf(size, max * width / wd)
        }
        return size
    }

    // ------------------------------------------------------------------ live displays

    private var displayText = ""
    private var displayBest = -1
    private val displayPaint by lazy { TexPaint(256, 64) }

    /** A red LED score display showing the machine's best score and a "PLAY" call. */
    val display: Texture by lazy { Texture(256, 64) }

    fun updateDisplay(best: Int, t: Float) {
        // Called every frame for every copy on screen: only build a new string when it changes.
        val hi = (t % 6f) < 3f
        if (hi && best == displayBest && displayText.startsWith("HI")) return
        if (!hi && displayText == "PLAY!") return
        val text = if (hi) "HI $best" else "PLAY!"
        displayText = text
        displayBest = best
        val tp = displayPaint
        tp.fill(0xFF0A0404.toInt())
        for (x in 0 until 256 step 4) tp.rect(x.toFloat(), 0f, 1f, 64f, 0xFF140808.toInt())
        tp.glowText(text, 128f, 46f, 42f, 0xFFFF5A3C.toInt(), 0xFFFF2010.toInt(), 6f, Fonts.condensed, 0.12f)
        tp.update(display)
    }

    /** Screen size in painter units: the game's own cabinet design's, else by shape (portrait for the tower). */
    fun screenUnits(): Pair<Int, Int> {
        val design = game.cabinet
        if (design != null) return design.screenUnits ?: error("${game.id}'s cabinet has no live screen")
        return when (game.look.shape) {
            CabinetShape.TOWER -> 16 to 24
            else -> 24 to 18
        }
    }
}

/**
 * One cabinet's live screen: the game's attract loop, drawn with smooth shapes and real text,
 * and a "HI score" card every few seconds, with CRT scanlines. The hall only repaints it while
 * the cabinet is in view (see [MachineUnit.refresh]).
 */
class LiveScreen(private val art: MachineArt, private val seed: Int) {
    private val units = art.screenUnits()
    private val scale = 10f
    private val tp by lazy { TexPaint((units.first * scale).toInt(), (units.second * scale).toInt()) }
    private val painter by lazy { CanvasPainter(tp, scale) }
    val texture: Texture by lazy { Texture((units.first * scale).toInt(), (units.second * scale).toInt()) }
    /** Screens repaint on alternate frames; which ones is set by the seed so the whole hall doesn't do it on the same frame. */
    private var frame = seed and 1
    private var bestText = ""
    private var bestShown = -1

    fun paint(best: Int, t: Float) {
        // Screens refresh at 30 fps; that's plenty for attract loops.
        frame++
        if (frame % 2 != 0) return
        val (w, h) = units
        val cycle = (t + seed * 1.7f) % 9f
        if (cycle > 7f) {
            tp.fill(0xFF05040A.toInt())
            tp.glowText("HIGH SCORE", tp.w / 2f, tp.h * 0.36f, tp.h * 0.13f, 0xFFFFE14D.toInt(), 0xFFFF9A3C.toInt(), 5f, Fonts.display)
            if (best != bestShown) {
                bestShown = best
                bestText = best.toString()
            }
            tp.glowText(bestText, tp.w / 2f, tp.h * 0.72f, tp.h * 0.26f, -1, art.glow, 8f, Fonts.display)
        } else {
            tp.fill(0xFF000000.toInt())
            art.game.drawAttract(painter, w, h, t + seed * 3.1f)
        }
        // Scanlines and a soft glass sheen: one draw of a pre-painted overlay, not a rect per line.
        tp.canvas.drawBitmap(ScreenGlass.overlay(tp.w, tp.h), 0f, 0f, null)
        tp.update(texture)
    }
}

/**
 * The CRT scanlines and the soft sheen of the glass over a live screen, painted once for each
 * screen size and shared by every screen of that size. Laid over the game's picture in a single
 * draw call, this comes out the same as painting the lines and the gradient over it each frame
 * (which took a rect per third row, some 60 to 80 of them, 30 times a second per screen).
 */
private object ScreenGlass {
    /** Every third pixel row gets this dark line. */
    const val LINE_COLOR = 0x33000000
    const val LINE_GAP = 3
    /** The sheen: a pale radial patch near the top left. */
    const val SHEEN_COLOR = 0x22FFFFFF

    private val sizes = HashMap<Long, TexPaint>()

    fun overlay(w: Int, h: Int): Bitmap = synchronized(sizes) {
        sizes.getOrPut(w.toLong() shl 32 or h.toLong()) {
            val tp = TexPaint(w, h)
            for (y in 0 until h step LINE_GAP) tp.rect(0f, y.toFloat(), w.toFloat(), 1f, LINE_COLOR)
            tp.radial(w * 0.3f, h * 0.2f, w * 0.5f, SHEEN_COLOR, 0)
            tp
        }.bitmap
    }
}

/**
 * Paints a machine's emblem in a ringed badge of radius [r] at ([cx], [cy]): a claw over a
 * plush, a mole in its hole, skee rings and a ball, a basketball under a rim, a coin stack,
 * a mallet and puck, a block tower, a chequered flag, a crosshair, a pinball and flipper, a
 * fish. Used on marquees, side art, toppers and kick panels. Paint-time only.
 */
internal fun TexPaint.emblem(shape: CabinetShape, cx: Float, cy: Float, r: Float, body: Int, trim: Int, glow: Int) {
    val ink = 0xFF120E18.toInt()
    val chrome = 0xFFDCE0EA.toInt()
    circle(cx, cy, r, dim(body, 0.32f))
    radial(cx, cy - r * 0.2f, r, alpha(lift(glow, 0.4f), 0.55f), 0)
    ring(cx, cy, r * 0.94f, r * 0.1f, trim)
    ring(cx, cy, r * 0.8f, r * 0.03f, alpha(lift(glow, 0.5f), 0.8f))
    val s = r * 0.8f
    when (shape) {
        CabinetShape.CLAW, CabinetShape.WIDE -> {
            ball(cx + s * 0.1f, cy + s * 0.55f, s * 0.3f, 0xFFFF6FB0.toInt())
            circle(cx + s * 0.02f, cy + s * 0.48f, s * 0.05f, ink)
            circle(cx + s * 0.2f, cy + s * 0.48f, s * 0.05f, ink)
            line(cx, cy - s, cx, cy - s * 0.2f, s * 0.08f, chrome)
            circle(cx, cy - s * 0.2f, s * 0.17f, chrome)
            for (k in -1..1) {
                val ex = cx + k * s * 0.42f
                line(cx, cy - s * 0.15f, ex, cy + s * 0.2f, s * 0.08f, chrome)
                line(ex, cy + s * 0.2f, cx + k * s * 0.3f + (if (k == 0) 0f else 0f), cy + s * 0.4f, s * 0.08f, chrome)
            }
        }
        CabinetShape.WHACK -> {
            oval(cx, cy + s * 0.5f, s * 0.72f, s * 0.2f, ink)
            oval(cx, cy + s * 0.05f, s * 0.4f, s * 0.48f, 0xFF9C6A38.toInt())
            oval(cx, cy + s * 0.18f, s * 0.24f, s * 0.2f, 0xFFD9A066.toInt())
            circle(cx - s * 0.14f, cy - s * 0.1f, s * 0.07f, ink)
            circle(cx + s * 0.14f, cy - s * 0.1f, s * 0.07f, ink)
            oval(cx, cy + s * 0.08f, s * 0.09f, s * 0.06f, 0xFFFF6FA0.toInt())
            oval(cx, cy + s * 0.56f, s * 0.72f, s * 0.14f, dim(body, 0.32f))
            line(cx + s * 0.25f, cy - s * 0.25f, cx + s * 0.8f, cy - s * 0.75f, s * 0.1f, 0xFFC9884A.toInt())
            round(cx + s * 0.02f, cy - s * 0.98f, s * 0.5f, s * 0.36f, s * 0.08f, 0xFFE8323C.toInt())
        }
        CabinetShape.SKEEBALL, CabinetShape.LANE -> {
            val colors = intArrayOf(0xFF4DA6FF.toInt(), 0xFFFF3FA4.toInt(), 0xFFFFE14D.toInt())
            for (k in 0 until 3) {
                circle(cx, cy - s * 0.1f, s * (0.7f - k * 0.22f), dim(colors[k], 0.55f))
                ring(cx, cy - s * 0.1f, s * (0.7f - k * 0.22f), s * 0.07f, colors[k])
            }
            circle(cx, cy - s * 0.1f, s * 0.1f, ink)
            ball(cx + s * 0.5f, cy + s * 0.5f, s * 0.24f, 0xFFB0213A.toInt())
        }
        CabinetShape.HOOPS -> {
            round(cx - s * 0.62f, cy - s * 0.98f, s * 1.24f, s * 0.62f, s * 0.08f, 0xFFF4F8FF.toInt())
            strokeRound(cx - s * 0.24f, cy - s * 0.78f, s * 0.48f, s * 0.36f, s * 0.04f, s * 0.06f, 0xFFE8323C.toInt())
            ball(cx, cy + s * 0.28f, s * 0.5f, 0xFFE8782A.toInt(), 0.35f)
            line(cx - s * 0.5f, cy + s * 0.28f, cx + s * 0.5f, cy + s * 0.28f, s * 0.05f, 0xFF3A1A08.toInt())
            line(cx, cy - s * 0.22f, cx, cy + s * 0.78f, s * 0.05f, 0xFF3A1A08.toInt())
            line(cx - s * 0.45f, cy - s * 0.42f, cx + s * 0.45f, cy - s * 0.42f, s * 0.1f, 0xFFFF7A1A.toInt())
        }
        CabinetShape.PUSHER -> {
            for (k in 0 until 4) {
                val y = cy + s * 0.5f - k * s * 0.2f
                oval(cx - s * 0.2f, y + s * 0.05f, s * 0.46f, s * 0.16f, 0xFFB07818.toInt())
                oval(cx - s * 0.2f, y, s * 0.46f, s * 0.16f, 0xFFFFD04A.toInt())
            }
            circle(cx + s * 0.42f, cy + s * 0.2f, s * 0.3f, 0xFFE0A020.toInt())
            circle(cx + s * 0.42f, cy + s * 0.2f, s * 0.24f, 0xFFFFD04A.toInt())
            star(cx + s * 0.42f, cy + s * 0.2f, s * 0.16f, 0xFFE0A020.toInt())
        }
        CabinetShape.AIR_HOCKEY, CabinetShape.TABLE -> {
            for (k in 0 until 3) line(cx + s * 0.05f, cy + s * (0.1f + k * 0.14f), cx + s * 0.5f, cy + s * (0.1f + k * 0.14f), s * 0.05f, alpha(-1, 0.6f))
            oval(cx + s * 0.45f, cy + s * 0.32f, s * 0.26f, s * 0.12f, ink)
            oval(cx + s * 0.45f, cy + s * 0.28f, s * 0.26f, s * 0.12f, 0xFF2A2A34.toInt())
            circle(cx - s * 0.25f, cy, s * 0.46f, 0xFFB01A24.toInt())
            circle(cx - s * 0.25f, cy - s * 0.05f, s * 0.44f, 0xFFE8323C.toInt())
            circle(cx - s * 0.25f, cy - s * 0.1f, s * 0.2f, 0xFFFF7A7A.toInt())
        }
        CabinetShape.TOWER, CabinetShape.UPRIGHT -> {
            val colors = intArrayOf(glow, trim, 0xFF39E6F2.toInt(), 0xFFFF4FA8.toInt(), 0xFFFFD84D.toInt())
            for (k in 0 until 5) {
                val bw = s * (1.1f - k * 0.18f)
                val y = cy + s * 0.62f - k * s * 0.3f
                round(cx - bw / 2f + (k % 2) * s * 0.06f, y - s * 0.26f, bw, s * 0.26f, s * 0.04f, colors[k])
                rect(cx - bw / 2f + (k % 2) * s * 0.06f, y - s * 0.26f, bw, s * 0.05f, alpha(-1, 0.4f))
            }
        }
        CabinetShape.RACER -> {
            line(cx - s * 0.62f, cy - s * 0.62f, cx - s * 0.62f, cy + s * 0.85f, s * 0.08f, chrome)
            val n = 5
            val q = s * 1.2f / n
            for (i in 0 until n) for (j in 0 until 4) {
                val wave = kotlin.math.sin(i * 1.1f) * s * 0.08f
                rect(cx - s * 0.58f + i * q, cy - s * 0.62f + j * q + wave, q + 0.5f, q + 0.5f, if ((i + j) % 2 == 0) -1 else ink)
            }
        }
        CabinetShape.GUN -> {
            ring(cx, cy, s * 0.6f, s * 0.09f, 0xFFFF3B30.toInt())
            ring(cx, cy, s * 0.3f, s * 0.05f, 0xFFFF3B30.toInt())
            for (k in 0 until 4) {
                val dx = if (k < 2) (if (k == 0) 1f else -1f) else 0f
                val dy = if (k >= 2) (if (k == 2) 1f else -1f) else 0f
                line(cx + dx * s * 0.4f, cy + dy * s * 0.4f, cx + dx * s * 0.9f, cy + dy * s * 0.9f, s * 0.09f, -1)
            }
            circle(cx, cy, s * 0.08f, 0xFFFF3B30.toInt())
        }
        CabinetShape.PINBALL -> {
            star(cx - s * 0.3f, cy - s * 0.45f, s * 0.3f, 0xFFFFE14D.toInt())
            ball(cx + s * 0.2f, cy - s * 0.15f, s * 0.32f, 0xFFD8DCE8.toInt(), 0.6f)
            line(cx - s * 0.62f, cy + s * 0.35f, cx + s * 0.18f, cy + s * 0.62f, s * 0.26f, 0xFFE8323C.toInt())
            line(cx - s * 0.6f, cy + s * 0.33f, cx + s * 0.16f, cy + s * 0.58f, s * 0.17f, -1)
            circle(cx - s * 0.6f, cy + s * 0.33f, s * 0.06f, 0xFFE8323C.toInt())
        }
        CabinetShape.FISHING -> {
            for (k in 0 until 2) line(cx - s * 0.8f, cy + s * (0.62f + k * 0.18f), cx + s * 0.8f, cy + s * (0.62f + k * 0.18f), s * 0.06f, alpha(0xFF7AD8FF.toInt(), 0.8f))
            polygon(floatArrayOf(cx + s * 0.35f, cy, cx + s * 0.82f, cy - s * 0.32f, cx + s * 0.82f, cy + s * 0.32f), 0xFFFF8A3D.toInt())
            oval(cx - s * 0.08f, cy, s * 0.52f, s * 0.3f, 0xFFFF9A3C.toInt())
            oval(cx - s * 0.08f, cy + s * 0.08f, s * 0.42f, s * 0.16f, 0xFFFFD08A.toInt())
            circle(cx - s * 0.34f, cy - s * 0.06f, s * 0.08f, -1)
            circle(cx - s * 0.36f, cy - s * 0.06f, s * 0.045f, ink)
            circle(cx - s * 0.3f, cy - s * 0.62f, s * 0.08f, alpha(-1, 0.7f))
            circle(cx - s * 0.12f, cy - s * 0.78f, s * 0.05f, alpha(-1, 0.7f))
        }
    }
}
