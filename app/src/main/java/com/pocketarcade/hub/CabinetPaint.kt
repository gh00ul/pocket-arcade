package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import com.pocketarcade.games.CabinetShape
import kotlin.math.cos
import kotlin.math.sin

/**
 * The printed artwork of a cabinet: side art, marquee, topper, kick plate, control panel and
 * bezel, painted at a finer resolution than the texel size the models map them in (see
 * [paintTexture]) so decals stay crisp when a kid stands a step away from the glass. Everything is
 * painted once per game (the shared hardware once for all) from the game's [CabinetLook] colours.
 *
 * Painting is paint-time only and headless-unsafe (it needs the 2D canvas); the numbers that decide
 * how it looks are the named constants below.
 */
internal object CabinetPaint {
    /** Painting scale (pixels per texel) for the big printed panels: side art, marquee, kick. */
    const val PANEL_SCALE = 2
    /** Painting scale for small, detailed hardware plates (coin door, ticket plate, control panel). */
    const val PLATE_SCALE = 4

    /** Side art sizes in texels: the tall panel, a squarish one for skirts and a long strip for rails. */
    const val SIDE_TALL_W = 192
    const val SIDE_TALL_H = 384
    const val SIDE_SQUARE = 192
    const val SIDE_LONG_W = 384
    const val SIDE_LONG_H = 96

    /** Marquee and topper sizes in texels. */
    const val MARQUEE_W = 384
    const val MARQUEE_H = 120
    const val TOPPER_W = 192
    const val TOPPER_H = 144

    /** How much the paint darkens towards the foot of a printed panel (floor grime), 0..1. */
    const val GRIME = 0.34f
    /** How many light scuffs are scratched into the lowest part of a panel. */
    const val SCUFFS = 16

    private val CHROME = 0xFFC4C8D4.toInt()
    private val CHROME_DARK = 0xFF6E7282.toInt()
    private val INK = 0xFF120E18.toInt()

    /** The biggest text size (≤ [max]) at which every one of [lines] fits in [width]. */
    fun fitSize(tp: TexPaint, lines: List<String>, width: Float, max: Float): Float {
        var size = max
        for (s in lines) {
            val wd = tp.textWidth(s, max, Fonts.display, 0.02f)
            if (wd > width) size = minOf(size, max * width / wd)
        }
        return size
    }

    /** A slotted chrome screw head. */
    fun TexPaint.screw(x: Float, y: Float, r: Float, angle: Float = 0.6f) {
        circle(x, y + r * 0.25f, r * 1.05f, alpha(INK, 0.5f))
        circle(x, y, r, CHROME_DARK)
        circle(x - r * 0.12f, y - r * 0.12f, r * 0.82f, CHROME)
        line(x - cos(angle) * r * 0.7f, y - sin(angle) * r * 0.7f, x + cos(angle) * r * 0.7f, y + sin(angle) * r * 0.7f, r * 0.28f, 0xFF3A3C48.toInt(), round = false)
    }

    /** Light scratches and scuffs over the foot of a panel, so it looks handled, not printed yesterday. */
    private fun TexPaint.wear(w: Float, h: Float, seed: Int) {
        vgrad(0f, h * 0.68f, w, h * 0.32f, 0, alpha(0xFF000000.toInt(), GRIME))
        for (i in 0 until SCUFFS) {
            val x = hash01(i, seed) * w
            val y = h * (0.72f + hash01(i, seed + 1) * 0.26f)
            val len = 3f + hash01(i, seed + 2) * 9f
            val a = (hash01(i, seed + 3) - 0.5f) * 1.2f
            line(x, y, x + cos(a) * len, y + sin(a) * len * 0.4f, 0.5f + hash01(i, seed + 4), alpha(-1, 0.10f + hash01(i, seed + 5) * 0.08f))
        }
    }

    // ------------------------------------------------------------------ side art

    /**
     * The printed side of a cabinet. [w] × [h] picks the layout: a tall panel (uprights, backboards)
     * stacks the badge over the title, a squarish one (skirts) centres the badge, a long strip (rails)
     * puts the badge at one end with the title beside it. Bold speed stripes with drop shadows and
     * a highlight line sweep across, a pinstripe frame with rivets runs round the edge and the foot
     * is grimy and scuffed.
     */
    fun sideArt(w: Int, h: Int, body: Int, trim: Int, glow: Int, shape: CabinetShape, title: String): Texture =
        paintTexture(w, h, PANEL_SCALE) {
            val fw = w.toFloat()
            val fh = h.toFloat()
            val long = w > h * 2
            val tall = h > w * 1.4f
            vgrad(0f, 0f, fw, fh, lift(body, 0.22f), body, dim(body, 0.48f))
            // Halftone dots fading out of the top corner.
            val dots = if (long) 6 else 10
            for (y in 0 until dots) for (x in 0 until dots + 2) {
                val d = (x + y) / (dots * 1.6f)
                if (d > 1f) continue
                val step = minOf(fw, fh) / 12f
                circle(fw - step * 0.6f - x * step + (y % 2) * step * 0.5f, step * 0.7f + y * step, step * 0.4f * (1f - d), alpha(lift(glow, 0.5f), 0.32f))
            }
            // Speed stripes sweeping across the panel, each with a shadow edge and a bright top line.
            val rise = if (long) fh * 0.9f else fw * 0.95f
            val bands = if (long) 3 else 4
            for (k in 0 until bands) {
                val y = if (long) fh * (0.95f - k * 0.3f) else fh * (0.92f - k * 0.115f)
                val c = when (k % 3) { 0 -> glow; 1 -> trim; else -> lift(body, 0.45f) }
                val t = (if (long) 9f else 13f) - k * 1.2f
                val dx = fw
                polygon(floatArrayOf(0f, y + 4f, dx, y - rise + 4f, dx, y - rise + t + 4f, 0f, y + t + 4f), alpha(0xFF000000.toInt(), 0.35f))
                polygon(floatArrayOf(0f, y, dx, y - rise, dx, y - rise + t, 0f, y + t), c)
                polygon(floatArrayOf(0f, y, dx, y - rise, dx, y - rise + 1.4f, 0f, y + 1.4f), alpha(-1, 0.5f))
                polygon(floatArrayOf(0f, y + t - 1.2f, dx, y - rise + t - 1.2f, dx, y - rise + t, 0f, y + t), alpha(0xFF000000.toInt(), 0.25f))
            }
            // The badge: a burst behind the emblem, on a dark ground.
            val badgeR = if (long) fh * 0.42f else fw * 0.34f
            val bx = if (long) fh * 0.62f else fw * 0.5f
            val by = if (long) fh * 0.5f else if (tall) fh * 0.3f else fh * 0.4f
            for (k in 0 until 18) {
                val a = k * Math.PI.toFloat() / 9f
                val rr = badgeR * 2.3f
                polygon(
                    floatArrayOf(bx, by, bx + cos(a) * rr, by + sin(a) * rr, bx + cos(a + 0.17f) * rr, by + sin(a + 0.17f) * rr),
                    alpha(lift(glow, 0.3f), 0.2f),
                )
            }
            circle(bx, by + badgeR * 0.08f, badgeR * 1.08f, alpha(0xFF000000.toInt(), 0.4f))
            emblem(shape, bx, by, badgeR, body, trim, glow)
            // The title: stacked one word a line on a tall panel, in one line beside the badge on a strip.
            val words = title.split(' ')
            if (long) {
                val size = fitSize(this, listOf(title), fw - bx - badgeR - 36f, fh * 0.42f)
                val tx = bx + badgeR + 14f + (fw - bx - badgeR - 26f) / 2f
                outlinedText(title, tx, fh * 0.5f + size * 0.34f, size, lift(glow, 0.65f), dim(body, 0.18f), size * 0.16f, Fonts.display)
            } else {
                val size = fitSize(this, words, fw * 0.86f, fw * 0.3f)
                for ((i, word) in words.withIndex()) {
                    val y = fh - fh * 0.075f - (words.size - 1 - i) * size * 0.95f
                    outlinedText(word, fw / 2f, y, size, lift(glow, 0.65f), dim(body, 0.18f), size * 0.16f, Fonts.display)
                }
            }
            // A pinstripe frame with rivets in its corners.
            val m = minOf(fw, fh) * 0.045f + 3f
            strokeRound(m, m, fw - 2f * m, fh - 2f * m, 6f, 1.4f, alpha(trim, 0.85f))
            strokeRound(m + 3f, m + 3f, fw - 2f * m - 6f, fh - 2f * m - 6f, 4f, 0.6f, alpha(lift(glow, 0.5f), 0.7f))
            for (sx in floatArrayOf(m, fw - m)) for (sy in floatArrayOf(m, fh - m)) screw(sx, sy, 2f)
            wear(fw, fh, title.length * 7 + w)
            // T-moulding's catch-light down the front edge, its shadow down the back.
            hgrad(0f, 0f, 7f, fh, alpha(-1, 0.32f), 0)
            hgrad(fw - 7f, 0f, 7f, fh, 0, alpha(0xFF000000.toInt(), 0.32f))
            grain(0.03f, 13)
        }

    // ------------------------------------------------------------------ marquee and topper

    /**
     * The lit sign on top of the cabinet, painted as backlit acrylic: a hot glow behind the
     * lettering that fades into the corners, a sunburst, the emblem at both ends, the title in
     * glowing outlined letters, a neon keyline and a chrome frame with screws.
     */
    fun marquee(body: Int, trim: Int, glow: Int, shape: CabinetShape, title: String): Texture =
        paintTexture(MARQUEE_W, MARQUEE_H, PANEL_SCALE) {
            val w = MARQUEE_W.toFloat()
            val h = MARQUEE_H.toFloat()
            vgrad(0f, 0f, w, h, dim(body, 0.32f), lift(body, 0.1f), dim(body, 0.42f))
            for (k in 0 until 32) {
                val a = k * Math.PI.toFloat() / 16f
                val rr = 330f
                polygon(
                    floatArrayOf(w / 2f, h / 2f, w / 2f + cos(a) * rr, h / 2f + sin(a) * rr, w / 2f + cos(a + 0.1f) * rr, h / 2f + sin(a + 0.1f) * rr),
                    alpha(lift(glow, 0.4f), 0.12f),
                )
            }
            radial(w / 2f, h / 2f, w * 0.42f, alpha(lift(glow, 0.55f), 0.6f), 0)
            // Emblems at both ends, each on its own soft halo.
            for (ex in floatArrayOf(52f, w - 52f)) {
                radial(ex, h / 2f, 50f, alpha(lift(glow, 0.5f), 0.4f), 0)
                emblem(shape, ex, h / 2f, 37f, body, trim, glow)
            }
            // The title: one line, or two for a two-word name, as big as fits between the emblems.
            val words = title.split(' ')
            val lines = if (words.size == 2) words else listOf(title)
            val size = fitSize(this, lines, 206f, if (lines.size == 2) 50f else 68f)
            for ((i, line) in lines.withIndex()) {
                val y = h / 2f + size * 0.36f + (i - (lines.size - 1) / 2f) * size * 0.92f
                glowText(line, w / 2f, y, size, lift(glow, 0.8f), glow, 9f, Fonts.display, 0.02f)
                outlinedText(line, w / 2f, y + size * 0.05f, size, alpha(0xFF000000.toInt(), 0.45f), dim(body, 0.1f), size * 0.16f, Fonts.display, 0.02f)
                outlinedText(line, w / 2f, y, size, lift(glow, 0.86f), dim(body, 0.15f), size * 0.12f, Fonts.display, 0.02f)
                text(line, w / 2f, y, size, alpha(-1, 0.5f), Fonts.display, spacing = 0.02f)
            }
            // Acrylic: a plastic sheen across the top half and fine diagonal glints.
            vgrad(0f, 0f, w, h * 0.48f, alpha(-1, 0.18f), alpha(-1, 0.02f))
            for (k in 0 until 3) {
                val x = 70f + k * 120f
                polygon(floatArrayOf(x, 0f, x + 9f, 0f, x - 24f, h, x - 33f, h), alpha(-1, 0.045f))
            }
            // Neon keyline, then a frame: dark reveal, chrome bevel, corner screws.
            this.glow(4f, alpha(glow, 0.9f)) { strokeRound(10f, 10f, w - 20f, h - 20f, 12f, 3f, -1) }
            strokeRound(10f, 10f, w - 20f, h - 20f, 12f, 2.2f, lift(glow, 0.7f))
            strokeRound(2.2f, 2.2f, w - 4.4f, h - 4.4f, 9f, 4.6f, CHROME)
            strokeRound(4.4f, 4.4f, w - 8.8f, h - 8.8f, 8f, 1.2f, CHROME_DARK)
            rect(0f, 0f, w, 2.2f, 0xFFF4F6FF.toInt())
            rect(0f, h - 2.2f, w, 2.2f, 0xFF2A2C34.toInt())
            for (sx in floatArrayOf(7f, w - 7f)) for (sy in floatArrayOf(7f, h - 7f)) screw(sx, sy, 2.4f)
            grain(0.02f, 21)
        }

    /**
     * The top of the marquee, which is what the hall camera sees most of: a backlit topper with a
     * pinstripe border, the emblem and the machine's short name.
     */
    fun topper(body: Int, trim: Int, glow: Int, shape: CabinetShape, name: String): Texture =
        paintTexture(TOPPER_W, TOPPER_H, PANEL_SCALE) {
            val w = TOPPER_W.toFloat()
            val h = TOPPER_H.toFloat()
            vgrad(0f, 0f, w, h, dim(body, 0.5f), dim(body, 0.8f))
            radial(w / 2f, h / 2f, w * 0.55f, alpha(lift(glow, 0.3f), 0.45f), 0)
            for (k in 0 until 7) rect(0f, 15f + k * 18f, w, 1.5f, alpha(dim(body, 0.3f), 0.5f))
            round(6f, 6f, w - 12f, h - 12f, 10f, alpha(0xFF000000.toInt(), 0.18f))
            strokeRound(6f, 6f, w - 12f, h - 12f, 10f, 3.6f, trim)
            strokeRound(12.5f, 12.5f, w - 25f, h - 25f, 6f, 1.4f, alpha(glow, 0.8f))
            emblem(shape, 46f, h / 2f, 30f, body, trim, glow)
            val size = fitSize(this, listOf(name), 106f, 48f)
            outlinedText(name, 122f, h / 2f + size * 0.36f, size, lift(glow, 0.75f), dim(body, 0.15f), size * 0.14f, Fonts.display)
            vgrad(0f, 0f, w, h * 0.4f, alpha(-1, 0.14f), 0)
            grain(0.02f, 23)
        }

    // ------------------------------------------------------------------ kick plate

    /**
     * The kick plate under the coin door: a recessed speaker grille, a racing stripe with chevrons,
     * printed emblems either side of where the coin door fits, a rubber toe strip and plenty of scuffs.
     */
    fun kick(body: Int, trim: Int, glow: Int, shape: CabinetShape): Texture =
        paintTexture(160, 160, PANEL_SCALE) {
            val w = 160f
            val h = 160f
            vgrad(0f, 0f, w, h, dim(body, 0.6f), dim(body, 0.26f))
            // Speaker grille: a recessed slot of holes in a chrome ring.
            round(23f, 9f, 114f, 30f, 7f, alpha(0xFF000000.toInt(), 0.5f))
            round(25f, 11f, 110f, 26f, 6f, 0xFF16141C.toInt())
            for (x in 0 until 17) for (y in 0 until 3) circle(31f + x * 6.4f, 17.5f + y * 6.2f, 1.45f, 0xFF302D3C.toInt())
            strokeRound(25f, 11f, 110f, 26f, 6f, 1.3f, 0xFF7C8090.toInt())
            // Racing stripe with chevrons.
            rect(0f, 52f, w, 16f, trim)
            rect(0f, 52f, w, 1.8f, alpha(-1, 0.42f))
            rect(0f, 73f, w, 3.6f, glow)
            for (k in 0 until 8) {
                val x = 5f + k * 20f
                polygon(floatArrayOf(x, 66f, x + 7.5f, 54f, x + 15f, 66f, x + 11f, 66f, x + 7.5f, 60f, x + 4f, 66f), dim(body, 0.35f))
            }
            emblem(shape, 25f, 122f, 17f, body, trim, glow)
            emblem(shape, w - 25f, 122f, 17f, body, trim, glow)
            text("1 TOKEN PER PLAY", 80f, 148f, 8f, alpha(-1, 0.7f), Fonts.condensed)
            wear(w, h, 41)
            // A rubber toe strip along the floor and screws at the corners.
            rect(0f, h - 5f, w, 5f, 0xFF0E0D12.toInt())
            rect(0f, h - 5f, w, 0.8f, alpha(-1, 0.14f))
            for (sx in floatArrayOf(6f, w - 6f)) for (sy in floatArrayOf(6f, h - 12f)) screw(sx, sy, 1.7f)
            grain(0.03f, 11)
        }

    // ------------------------------------------------------------------ control panels

    /**
     * The control panel's printing for an upright: a joystick well, six button rings in two rows,
     * a diagonal stripe of the game's colours, player labels and the how-to line.
     * Button and joystick positions match [MachineKit.PANEL_STICK_U] and [MachineKit.PANEL_BUTTON_U].
     */
    fun panel(body: Int, trim: Int, glow: Int, howTo: String): Texture =
        paintTexture(256, 128, PANEL_SCALE) {
            val w = 256f
            val h = 128f
            vgrad(0f, 0f, w, h, 0xFF2C2A36.toInt(), 0xFF100E14.toInt())
            polygon(floatArrayOf(0f, 76f, w, 26f, w, 46f, 0f, 96f), alpha(body, 0.55f))
            polygon(floatArrayOf(0f, 96f, w, 46f, w, 52f, 0f, 102f), alpha(glow, 0.8f))
            rect(0f, 0f, w, 6f, trim)
            rect(0f, 6f, w, 1.5f, alpha(-1, 0.3f))
            // The joystick's well: a shaded dish with an engraved ring.
            val sx = MachineKit.PANEL_STICK_U * w
            circle(sx, 58f, 24f, alpha(0xFF000000.toInt(), 0.45f))
            radial(sx, 56f, 22f, 0xFF050408.toInt(), 0xFF22202A.toInt())
            ring(sx, 58f, 24f, 2.2f, alpha(trim, 0.85f))
            // Six button rings, each ringed in its own colour over a dark well.
            val ringColors = intArrayOf(glow, trim, lift(glow, 0.4f))
            for (row in 0 until 2) for (k in 0 until 3) {
                val bx = (MachineKit.PANEL_BUTTON_U + k * MachineKit.PANEL_BUTTON_STEP_U + row * MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * w
                val by = (if (row == 0) 42f else 80f)
                circle(bx, by + 1.5f, 13f, alpha(0xFF000000.toInt(), 0.4f))
                circle(bx, by, 12f, 0xFF0A090E.toInt())
                ring(bx, by, 12.5f, 2.4f, alpha(ringColors[k], 0.9f))
            }
            text("1P", 22f, 26f, 12f, alpha(trim, 0.9f), Fonts.condensed)
            text("START", w - 30f, 26f, 10f, alpha(-1, 0.75f), Fonts.condensed)
            round(24f, 106f, w - 48f, 15f, 5f, alpha(0xFF000000.toInt(), 0.5f))
            text(howTo, w / 2f, 117.5f, 11f, alpha(-1, 0.85f), Fonts.condensed)
            vgrad(0f, 8f, w, 34f, alpha(-1, 0.08f), 0)
            for (sx2 in floatArrayOf(7f, w - 7f)) for (sy in floatArrayOf(14f, h - 7f)) screw(sx2, sy, 2f)
            grain(0.03f, 29)
        }

    /** The tower's panel: one big ringed button in the middle between two small ones. */
    fun panelBig(body: Int, trim: Int, glow: Int, howTo: String): Texture =
        paintTexture(256, 128, PANEL_SCALE) {
            val w = 256f
            val h = 128f
            vgrad(0f, 0f, w, h, 0xFF2C2A36.toInt(), 0xFF100E14.toInt())
            polygon(floatArrayOf(0f, 84f, w, 34f, w, 52f, 0f, 102f), alpha(body, 0.55f))
            polygon(floatArrayOf(0f, 102f, w, 52f, w, 58f, 0f, 108f), alpha(glow, 0.8f))
            rect(0f, 0f, w, 6f, trim)
            rect(0f, 6f, w, 1.5f, alpha(-1, 0.3f))
            circle(w / 2f, 60f, 34f, alpha(0xFF000000.toInt(), 0.4f))
            circle(w / 2f, 58f, 31f, 0xFF0A090E.toInt())
            ring(w / 2f, 58f, 32f, 3f, alpha(glow, 0.9f))
            for (s in intArrayOf(-1, 1)) {
                circle(w / 2f + s * 78f, 62f, 12f, 0xFF0A090E.toInt())
                ring(w / 2f + s * 78f, 62f, 12.5f, 2.2f, alpha(trim, 0.9f))
            }
            text("DROP", w / 2f, 24f, 12f, alpha(-1, 0.75f), Fonts.condensed)
            round(24f, 106f, w - 48f, 15f, 5f, alpha(0xFF000000.toInt(), 0.5f))
            text(howTo, w / 2f, 117.5f, 11f, alpha(-1, 0.85f), Fonts.condensed)
            vgrad(0f, 8f, w, 34f, alpha(-1, 0.08f), 0)
            for (sx2 in floatArrayOf(7f, w - 7f)) for (sy in floatArrayOf(14f, h - 7f)) screw(sx2, sy, 2f)
            grain(0.03f, 31)
        }

    // ------------------------------------------------------------------ screen bezel

    /** Screen surround: glossy black with subtle corner flashes and the game's name, "INSERT COIN" above. */
    fun bezel(trim: Int, glow: Int, name: String): Texture =
        paintTexture(128, 128, 3) {
            vgrad(0f, 0f, 128f, 128f, 0xFF1C1A24.toInt(), 0xFF08070C.toInt())
            this.glow(2.5f, alpha(glow, 0.7f)) { strokeRound(5f, 5f, 118f, 118f, 9f, 2f, -1) }
            strokeRound(5f, 5f, 118f, 118f, 9f, 1.5f, lift(glow, 0.5f))
            for ((x, y) in arrayOf(0f to 0f, 128f to 0f, 0f to 128f, 128f to 128f)) {
                val sx = if (x == 0f) 1f else -1f
                val sy = if (y == 0f) 1f else -1f
                polygon(floatArrayOf(x, y, x + sx * 22f, y, x, y + sy * 22f), trim)
                polygon(floatArrayOf(x + sx * 25f, y, x + sx * 29f, y, x, y + sy * 29f, x, y + sy * 25f), alpha(glow, 0.8f))
            }
            text(name, 64f, 122f, 8f, alpha(trim, 0.9f), Fonts.condensed)
            text("INSERT COIN", 64f, 11f, 5.5f, alpha(-1, 0.6f), Fonts.condensed)
            vgrad(0f, 0f, 128f, 45f, alpha(-1, 0.08f), 0)
        }
}
