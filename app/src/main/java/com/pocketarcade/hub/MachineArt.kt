package com.pocketarcade.hub

import android.graphics.Bitmap
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
     * Side panel art for a tall face (upright sides, backboards): speed stripes, a ringed emblem
     * badge over the title, a pinstripe frame with rivets, grime and scuffs at the foot. See
     * [CabinetPaint.sideArt]; [sideArtSquare] and [sideArtLong] are the same print laid out for
     * squarish skirts and long rails, so nothing is squashed onto a face of the wrong shape.
     */
    val sideArt: Texture by lazy {
        CabinetPaint.sideArt(CabinetPaint.SIDE_TALL_W, CabinetPaint.SIDE_TALL_H, body, trim, glow, shape, game.title)
    }
    val sideArtSquare: Texture by lazy {
        CabinetPaint.sideArt(CabinetPaint.SIDE_SQUARE, CabinetPaint.SIDE_SQUARE, body, trim, glow, shape, game.title)
    }
    val sideArtLong: Texture by lazy {
        CabinetPaint.sideArt(CabinetPaint.SIDE_LONG_W, CabinetPaint.SIDE_LONG_H, body, trim, glow, shape, game.title)
    }

    /** The lit sign on top of the cabinet: backlit acrylic with the emblem, title, neon keyline and chrome frame. */
    val marquee: Texture by lazy { CabinetPaint.marquee(body, trim, glow, shape, game.title) }

    /** The top of the marquee, which is what the hall camera sees most of. */
    val topper: Texture by lazy { CabinetPaint.topper(body, trim, glow, shape, game.marquee) }

    /** Kick panel: recessed speaker grille, chevron stripe, printed emblems, toe strip and scuffs. */
    val kick: Texture by lazy { CabinetPaint.kick(body, trim, glow, shape) }

    /** The face of the coin door, the same steel for every machine (its slots light up separately). */
    val coinDoor: Texture get() = MachineKit.coinDoor

    private val howTo get() = game.instructions.firstOrNull() ?: game.title

    /** The control panel's printing: a joystick well, button rings, a stripe and the how-to line. */
    val panel: Texture by lazy { CabinetPaint.panel(body, trim, glow, howTo) }

    /** The tower's panel, with one big button ring between two small ones. */
    val panelBig: Texture by lazy { CabinetPaint.panelBig(body, trim, glow, howTo) }

    /** Screen bezel: glossy black with a printed neon frame, corner flashes and the game's name. */
    val bezel: Texture by lazy { CabinetPaint.bezel(trim, glow, game.marquee) }

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
        tp.glowText(text, 128f, 46f, 42f, 0xFFFF5A3C.toInt(), 0xFFFF2010.toInt(), 6f, Fonts.condensed, 0.12f)
        // The LED matrix: dark gaps between the dots, one draw of a pre-painted grid.
        tp.canvas.drawBitmap(DotMatrix.mask(tp.w, tp.h), 0f, 0f, null)
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
    /** The tube's rounded corners: how dark the picture gets at the far corners (ARGB, black). */
    const val VIGNETTE_COLOR = 0x5C000000
    /** A soft highlight along the top edge of the glass, where a lamp overhead would reflect. */
    const val TOP_LIGHT_COLOR = 0x1CFFFFFF

    private val sizes = HashMap<Long, TexPaint>()

    fun overlay(w: Int, h: Int): Bitmap = synchronized(sizes) {
        sizes.getOrPut(w.toLong() shl 32 or h.toLong()) {
            val tp = TexPaint(w, h)
            for (y in 0 until h step LINE_GAP) tp.rect(0f, y.toFloat(), w.toFloat(), 1f, LINE_COLOR)
            tp.radial(w * 0.3f, h * 0.2f, w * 0.5f, SHEEN_COLOR, 0)
            // Curved glass: the corners fall away, the top catches the room.
            tp.radial(w / 2f, h / 2f, maxOf(w, h) * 0.78f, 0, VIGNETTE_COLOR)
            tp.vgrad(0f, 0f, w.toFloat(), h * 0.14f, TOP_LIGHT_COLOR, 0)
            tp
        }.bitmap
    }
}

/** The dark gaps that make a flat LED display read as a matrix of dots, painted once per size. */
private object DotMatrix {
    /** Pixels between dot centres. */
    const val PITCH = 4
    const val GAP_COLOR = 0x88000000.toInt()

    private val sizes = HashMap<Long, TexPaint>()

    fun mask(w: Int, h: Int): Bitmap = synchronized(sizes) {
        sizes.getOrPut(w.toLong() shl 32 or h.toLong()) {
            val tp = TexPaint(w, h)
            tp.clear(0)
            for (x in 0 until w step PITCH) tp.rect(x.toFloat(), 0f, 1f, h.toFloat(), GAP_COLOR)
            for (y in 0 until h step PITCH) tp.rect(0f, y.toFloat(), w.toFloat(), 1f, GAP_COLOR)
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
