package com.pocketarcade.games.pinball

import com.pocketarcade.engine.r3d.Texture
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A 128 x 32 dot-matrix display, like the amber ones on real machines: a grid of dot brightnesses
 * ([lit], 0..1), a 5 x 7 pixel font, and a [bake] that turns the grid into a texture of round
 * glowing dots with dark gaps between them (mapped onto the backbox as an emissive quad, so it
 * blooms like a lamp). Pure numbers and one plain [Texture] (no bitmaps), so it is safe in tests.
 */
internal class DotMatrix {
    companion object {
        const val COLS = 128
        const val ROWS = 32

        /** Texture pixels per dot along each axis: enough for a round dot with a visible gap. */
        const val CELL = 6

        /** Brightness levels in the dot colour ramp. */
        private const val LEVELS = 48

        /** Glyph height in dots; the font is 5 wide, trimmed to each glyph's ink. */
        const val GLYPH_H = 7

        /** Width of a space, and the gap between letters, in dots at scale 1. */
        private const val SPACE_W = 3
        private const val GAP = 1

        /**
         * The ramp a dot's brightness runs through, off to hot: a dim brown (an unlit dot is still
         * visible as part of the grid), deep orange, amber, then a pale yellow core. Kept
         * saturated below the very top so only a fully lit dot can reach the bloom.
         */
        private val RAMP_STOPS = arrayOf(
            floatArrayOf(0.00f, 30f, 12f, 4f),
            floatArrayOf(0.12f, 92f, 34f, 6f),
            floatArrayOf(0.45f, 214f, 96f, 14f),
            floatArrayOf(0.80f, 255f, 150f, 30f),
            floatArrayOf(1.00f, 255f, 208f, 110f),
        )

        /** Every level's 6 x 6 dot pixels, packed: [level * CELL * CELL + row * CELL + col]. */
        private val DOT_PIXELS: IntArray by lazy { buildDotPixels() }

        private fun buildDotPixels(): IntArray {
            val out = IntArray(LEVELS * CELL * CELL)
            // Round dot coverage: the dot fills most of the cell, with a soft edge.
            val cover = FloatArray(CELL * CELL)
            val c = CELL / 2f
            for (y in 0 until CELL) for (x in 0 until CELL) {
                val d = sqrt((x + 0.5f - c) * (x + 0.5f - c) + (y + 0.5f - c) * (y + 0.5f - c))
                cover[y * CELL + x] = (CELL * 0.43f - d + 0.5f).coerceIn(0f, 1f)
            }
            for (l in 0 until LEVELS) {
                val t = l / (LEVELS - 1f)
                var i = 1
                while (i < RAMP_STOPS.size - 1 && RAMP_STOPS[i][0] < t) i++
                val a = RAMP_STOPS[i - 1]
                val b = RAMP_STOPS[i]
                val k = ((t - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
                val r = a[1] + (b[1] - a[1]) * k
                val g = a[2] + (b[2] - a[2]) * k
                val bl = a[3] + (b[3] - a[3]) * k
                for (p in 0 until CELL * CELL) {
                    val m = cover[p]
                    // The gap between dots is near black, not transparent (the quad is opaque).
                    val gap = 0.10f
                    val mm = gap + (1f - gap) * m
                    out[l * CELL * CELL + p] = (255 shl 24) or ((r * mm).toInt() shl 16) or ((g * mm).toInt() shl 8) or (bl * mm).toInt()
                }
            }
            return out
        }

        /** The font: 5 x 7 glyphs as rows of `#` and `.`, one entry per character. */
        private val FONT: Map<Char, Array<String>> = mapOf(
            'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
            'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
            'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
            'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
            'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
            'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
            'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'I' to arrayOf("###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"),
            'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
            'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
            'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
            'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
            'N' to arrayOf("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
            'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
            'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
            'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
            'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
            'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
            'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
            'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
            'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
            'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
            'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
            '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to arrayOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
            '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to arrayOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
            '!' to arrayOf("#", "#", "#", "#", "#", ".", "#"),
            '.' to arrayOf(".", ".", ".", ".", ".", ".", "#"),
            ',' to arrayOf(".", ".", ".", ".", ".", "#", "#"),
            '-' to arrayOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
            '+' to arrayOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
            ':' to arrayOf(".", "#", ".", ".", ".", "#", "."),
            '?' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."),
            '*' to arrayOf(".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", "....."),
            '>' to arrayOf("#..", ".#.", "..#", ".#.", "#..", "...", "..."),
            '<' to arrayOf("..#", ".#.", "#..", ".#.", "..#", "...", "..."),
        )

        /** Per character: the first inked column, the ink's width (0 for a space) and one bit mask per row. */
        private class Glyph(val first: Int, val width: Int, val rows: IntArray)

        private val GLYPHS: Map<Char, Glyph> by lazy {
            FONT.mapValues { (_, art) ->
                var lo = Int.MAX_VALUE
                var hi = -1
                for (row in art) for (c in row.indices) if (row[c] == '#') {
                    if (c < lo) lo = c
                    if (c > hi) hi = c
                }
                val rows = IntArray(GLYPH_H) { r ->
                    var m = 0
                    for (c in art[r].indices) if (art[r][c] == '#') m = m or (1 shl c)
                    m
                }
                // '.' rows can be blank in a glyph that is all ink on one row; keep the trimmed span.
                Glyph(if (hi < 0) 0 else lo, if (hi < 0) 0 else hi - lo + 1, rows)
            }
        }

        /** Whether the font has a glyph for [c] (letters are looked up in capitals). */
        fun hasGlyph(c: Char): Boolean = c == ' ' || GLYPHS.containsKey(c.uppercaseChar())
    }

    /** Dot brightnesses, row by row, 0 (off) .. 1 (fully lit). */
    val lit = FloatArray(COLS * ROWS)

    /** The baked picture: [COLS] x [ROWS] dots at [CELL] pixels a dot. */
    val texture = Texture(COLS, ROWS, IntArray(COLS * CELL * ROWS * CELL), CELL)

    fun clear() {
        lit.fill(0f)
    }

    /** Lights the dot at ([x], [y]) to at least [v] (off the grid is ignored). */
    fun dot(x: Int, y: Int, v: Float) {
        if (x < 0 || y < 0 || x >= COLS || y >= ROWS) return
        val i = y * COLS + x
        if (v > lit[i]) lit[i] = v
    }

    /** Fills a block of dots. */
    fun rect(x: Int, y: Int, w: Int, h: Int, v: Float) {
        for (yy in y until y + h) for (xx in x until x + w) dot(xx, yy, v)
    }

    /** Width of [s] in dots at [scale] (letters are 1 dot apart at scale 1). */
    fun textWidth(s: String, scale: Int = 1): Int {
        var w = 0
        for (i in s.indices) {
            w += glyphWidth(s[i]) * scale
            if (i < s.length - 1) w += GAP * scale
        }
        return w
    }

    private fun glyphWidth(c: Char): Int {
        if (c == ' ') return SPACE_W
        return GLYPHS[c.uppercaseChar()]?.width ?: SPACE_W
    }

    /** Draws [s] with its top-left at ([x], [y]); returns the x just past it. Every dot is [scale] dots square. */
    fun text(s: String, x: Int, y: Int, scale: Int, v: Float): Int {
        var pen = x
        for (i in s.indices) {
            val c = s[i]
            val g = if (c == ' ') null else GLYPHS[c.uppercaseChar()]
            if (g != null) {
                for (r in 0 until GLYPH_H) {
                    val m = g.rows[r]
                    if (m == 0) continue
                    for (col in 0 until g.width) {
                        if (m and (1 shl (g.first + col)) == 0) continue
                        rect(pen + col * scale, y + r * scale, scale, scale, v)
                    }
                }
            }
            pen += glyphWidth(c) * scale + GAP * scale
        }
        return pen - GAP * scale
    }

    fun textCentered(s: String, cx: Int, y: Int, scale: Int, v: Float) {
        text(s, cx - textWidth(s, scale) / 2, y, scale, v)
    }

    /** The largest scale (up to [maxScale]) at which [s] fits in [room] dots. */
    fun fitScale(s: String, room: Int, maxScale: Int): Int {
        var k = maxScale
        while (k > 1 && textWidth(s, k) > room) k--
        return k
    }

    /** Turns the dot grid into the texture's pixels and marks it changed, ready for the GPU. */
    fun bake() {
        val px = texture.pixels
        val stride = COLS * CELL
        val cell2 = CELL * CELL
        val top = LEVELS - 1
        for (gy in 0 until ROWS) {
            for (gx in 0 until COLS) {
                val v = lit[gy * COLS + gx]
                val level = (v.coerceIn(0f, 1f) * top + 0.5f).toInt()
                val src = level * cell2
                var dst = (gy * CELL) * stride + gx * CELL
                for (cy in 0 until CELL) {
                    for (cx in 0 until CELL) px[dst + cx] = DOT_PIXELS[src + cy * CELL + cx]
                    dst += stride
                }
            }
        }
        texture.touch()
    }
}

/**
 * What the backbox display shows, composed from the game's state each time it repaints. Read-only:
 * it looks at numbers the game hands it and never touches the simulation or the game's random
 * numbers, and every animation is a function of the round clock.
 */
internal class PinballDmd {
    val matrix = DotMatrix()

    /** The 1/16 s tick the display was last painted for; it repaints when the tick moves. */
    private var lastTick = -1

    /** What the last paint showed, so an unchanged display costs nothing. */
    private var lastKey = Long.MIN_VALUE

    /**
     * Repaints if anything visible changed (the score, message, lamps or the animation tick).
     * [message] is null when none is up; [messageAge] is seconds since it appeared, [messageLife]
     * how long it stays. [hot] marks a big moment: 1 multiball, 2 jackpot, 0 nothing special.
     */
    fun update(
        time: Float, scoreText: String, ballText: String, multText: String,
        message: String?, messageAge: Float, messageLife: Float, hot: Int,
        multiball: Boolean, saving: Boolean, tilted: Boolean,
    ) {
        val tick = (time * TICKS_PER_SECOND).toInt()
        var key = scoreText.hashCode().toLong()
        key = key * 31 + ballText.hashCode()
        key = key * 31 + multText.hashCode()
        key = key * 31 + (message?.hashCode() ?: 0)
        key = key * 31 + (if (multiball) 1 else 0) + (if (saving) 2 else 0) + (if (tilted) 4 else 0) + hot * 8
        if (tick == lastTick && key == lastKey) return
        lastTick = tick
        lastKey = key
        val m = matrix
        m.clear()
        val phase = tick / TICKS_PER_SECOND.toFloat()
        if (tilted) {
            val on = tick % 6 < 4
            if (on) m.textCentered("TILT", DotMatrix.COLS / 2, 6, 3, 1f)
            m.textCentered("NO BONUS", DotMatrix.COLS / 2, 25, 1, 0.7f)
        } else if (message != null) {
            paintMessage(m, message, messageAge, messageLife, hot, tick)
        } else {
            paintPlay(m, scoreText, ballText, multText, multiball, saving, phase, tick)
        }
        m.bake()
    }

    private fun paintPlay(
        m: DotMatrix, scoreText: String, ballText: String, multText: String,
        multiball: Boolean, saving: Boolean, phase: Float, tick: Int,
    ) {
        val cx = DotMatrix.COLS / 2
        // The score: big, with a faint echo below it (the dot-matrix "shadow").
        val scale = m.fitScale(scoreText, DotMatrix.COLS - 10, 2)
        val y = if (scale == 2) 5 else 8
        m.textCentered(scoreText, cx, y, scale, 1f)
        // Bottom row: ball on the left, multiplier on the right, lamps between.
        m.text(ballText, 4, 24, 1, 0.75f)
        val mw = m.textWidth(multText, 1)
        m.text(multText, DotMatrix.COLS - 4 - mw, 24, 1, if (multText == "1X") 0.55f else 1f)
        if (multiball) {
            val on = tick % 8 < 5
            m.textCentered("MULTIBALL", cx, 24, 1, if (on) 1f else 0.35f)
        } else if (saving) {
            val on = tick % 8 < 5
            m.textCentered("BALL SAVE", cx, 24, 1, if (on) 0.9f else 0.3f)
        } else {
            // A little runner of dots along the bottom edge, so the display is never dead.
            val run = (phase * 22f).toInt() % (DotMatrix.COLS + 24)
            for (k in 0 until 12) m.dot(run - k, 31, 0.5f * (1f - k / 12f))
        }
        // Top corners: small stars that twinkle.
        val tw = 0.4f + 0.6f * abs(((tick % 10) - 5) / 5f)
        m.dot(2, 2, tw); m.dot(1, 3, tw); m.dot(3, 3, tw); m.dot(2, 4, tw)
        m.dot(DotMatrix.COLS - 3, 2, 1.2f - tw); m.dot(DotMatrix.COLS - 4, 3, 1.2f - tw); m.dot(DotMatrix.COLS - 2, 3, 1.2f - tw); m.dot(DotMatrix.COLS - 3, 4, 1.2f - tw)
    }

    private fun paintMessage(m: DotMatrix, text: String, age: Float, life: Float, hot: Int, tick: Int) {
        val cx = DotMatrix.COLS / 2
        val scale = m.fitScale(text, DotMatrix.COLS - 8, 3)
        // Fades out over the last fifth of its life; flashes on and off as it arrives.
        val fadeOut = ((life - age) / (life * 0.2f)).coerceIn(0f, 1f)
        val arriving = age < 0.42f
        val flash = if (arriving) (if ((age * 16f).toInt() % 2 == 0) 1f else 0.25f) else 1f
        val v = flash * (0.35f + 0.65f * fadeOut)
        val h = DotMatrix.GLYPH_H * scale
        val y = ((DotMatrix.ROWS - h) / 2).coerceAtLeast(1)
        if (hot > 0) {
            // A border of running dots round the edge, and beams sweeping out either side.
            val perimeter = 2 * (DotMatrix.COLS + DotMatrix.ROWS) - 4
            val step = if (hot == 2) 3 else 4
            for (i in 0 until perimeter) {
                if ((i + tick * 2) % step != 0) continue
                m.dot(perimeterX(i), perimeterY(i), 0.9f * fadeOut)
            }
        }
        m.textCentered(text, cx, y, scale, v)
        if (scale == 1 && text.length < 12) {
            // Short text at the smallest size gets an echo underline so it doesn't look lost.
            val w = m.textWidth(text, 1)
            for (x in cx - w / 2 until cx + (w + 1) / 2) m.dot(x, y + h + 3, 0.4f * v)
        }
    }

    // The i-th dot walking clockwise round the display's edge, from the top left.
    private fun perimeterX(i: Int): Int {
        val w = DotMatrix.COLS
        val h = DotMatrix.ROWS
        return when {
            i < w -> i
            i < w + h - 1 -> w - 1
            i < 2 * w + h - 2 -> w - 1 - (i - (w + h - 2))
            else -> 0
        }
    }

    private fun perimeterY(i: Int): Int {
        val w = DotMatrix.COLS
        val h = DotMatrix.ROWS
        return when {
            i < w -> 0
            i < w + h - 1 -> i - w + 1
            i < 2 * w + h - 2 -> h - 1
            else -> h - 1 - (i - (2 * w + h - 3))
        }
    }

    private companion object {
        const val TICKS_PER_SECOND = 16
    }
}
