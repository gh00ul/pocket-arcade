package com.pocketarcade.engine

import android.graphics.Bitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Two hand-made bitmap fonts: a 5x7 font for most text and a 3x5 "tiny" font for cabinet
 * marquees and small labels. Glyphs are packed into one white atlas that is tinted at draw time.
 */
object PixelFont {
    const val W = 5
    const val H = 7
    const val ADV = 6
    const val TW = 3
    const val TH = 5
    const val TADV = 4

    /** Special glyphs usable inline in any string. */
    const val PLAY = '▶'
    const val STAR = '★'
    const val HEART = '♥'
    const val TOKEN = '¤'
    const val TICKET = '¢'
    const val NOTE = '♪'
    const val LEFT = '←'
    const val RIGHT = '→'
    const val UP = '↑'
    const val DOWN = '↓'

    private val big = HashMap<Char, Array<String>>()
    private val tiny = HashMap<Char, Array<String>>()

    private fun g(c: Char, vararg rows: String) {
        big[c] = arrayOf(*rows)
    }

    private fun t(c: Char, vararg rows: String) {
        tiny[c] = arrayOf(*rows)
    }

    init {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.")
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.")
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.")
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####")
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....")
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####")
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.")
        g('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..")
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#")
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####")
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#")
        g('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#")
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....")
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#")
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#")
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.")
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..")
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..")
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.")
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#")
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..")
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####")
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.")
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.")
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####")
        g('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.")
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.")
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.")
        g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.")
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...")
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.")
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..")
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....")
        g('.', ".....", ".....", ".....", ".....", ".....", ".##..", ".##..")
        g(',', ".....", ".....", ".....", ".....", ".##..", "..#..", ".#...")
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..")
        g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..")
        g(':', ".....", ".##..", ".##..", ".....", ".##..", ".##..", ".....")
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....")
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....")
        g('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....")
        g('\'', "..#..", "..#..", ".#...", ".....", ".....", ".....", ".....")
        g('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.")
        g(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...")
        g('*', ".....", "..#..", "#.#.#", ".###.", "#.#.#", "..#..", ".....")
        g('#', ".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#.")
        g('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##")
        g('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....")
        g('<', "...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#.")
        g('>', ".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#...")
        g('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####")
        g('×', ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", ".....")
        g('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#")
        g('$', "..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#..")
        g(PLAY, "#....", "##...", "###..", "####.", "###..", "##...", "#....")
        g(STAR, "..#..", "..#..", "#####", ".###.", ".###.", ".#.#.", "#...#")
        g(HEART, ".....", ".#.#.", "#####", "#####", ".###.", "..#..", ".....")
        g(TOKEN, ".###.", "##.##", "##.##", "##.##", "##.##", "##.##", ".###.")
        g(TICKET, ".....", "#####", "##.##", "#...#", "##.##", "#####", ".....")
        g(NOTE, "..##.", "..#.#", "..#..", "..#..", "###..", "###..", ".....")
        g(LEFT, ".....", "..#..", ".#...", "#####", ".#...", "..#..", ".....")
        g(RIGHT, ".....", "..#..", "...#.", "#####", "...#.", "..#..", ".....")
        g(UP, "..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#..")
        g(DOWN, "..#..", "..#..", "..#..", "..#..", "#.#.#", ".###.", "..#..")

        t('A', ".#.", "#.#", "###", "#.#", "#.#")
        t('B', "##.", "#.#", "##.", "#.#", "##.")
        t('C', ".##", "#..", "#..", "#..", ".##")
        t('D', "##.", "#.#", "#.#", "#.#", "##.")
        t('E', "###", "#..", "##.", "#..", "###")
        t('F', "###", "#..", "##.", "#..", "#..")
        t('G', ".##", "#..", "#.#", "#.#", ".##")
        t('H', "#.#", "#.#", "###", "#.#", "#.#")
        t('I', "###", ".#.", ".#.", ".#.", "###")
        t('J', "..#", "..#", "..#", "#.#", ".#.")
        t('K', "#.#", "#.#", "##.", "#.#", "#.#")
        t('L', "#..", "#..", "#..", "#..", "###")
        t('M', "#.#", "###", "###", "#.#", "#.#")
        t('N', "##.", "#.#", "#.#", "#.#", "#.#")
        t('O', ".#.", "#.#", "#.#", "#.#", ".#.")
        t('P', "##.", "#.#", "##.", "#..", "#..")
        t('Q', ".#.", "#.#", "#.#", "##.", ".##")
        t('R', "##.", "#.#", "##.", "#.#", "#.#")
        t('S', ".##", "#..", ".#.", "..#", "##.")
        t('T', "###", ".#.", ".#.", ".#.", ".#.")
        t('U', "#.#", "#.#", "#.#", "#.#", "###")
        t('V', "#.#", "#.#", "#.#", "#.#", ".#.")
        t('W', "#.#", "#.#", "###", "###", "#.#")
        t('X', "#.#", "#.#", ".#.", "#.#", "#.#")
        t('Y', "#.#", "#.#", ".#.", ".#.", ".#.")
        t('Z', "###", "..#", ".#.", "#..", "###")
        t('0', "###", "#.#", "#.#", "#.#", "###")
        t('1', ".#.", "##.", ".#.", ".#.", "###")
        t('2', "##.", "..#", ".#.", "#..", "###")
        t('3', "##.", "..#", ".#.", "..#", "##.")
        t('4', "#.#", "#.#", "###", "..#", "..#")
        t('5', "###", "#..", "##.", "..#", "##.")
        t('6', ".##", "#..", "###", "#.#", "###")
        t('7', "###", "..#", ".#.", ".#.", ".#.")
        t('8', "###", "#.#", "###", "#.#", "###")
        t('9', "###", "#.#", "###", "..#", "##.")
        t(' ', "...", "...", "...", "...", "...")
        t('-', "...", "...", "###", "...", "...")
        t('!', ".#.", ".#.", ".#.", "...", ".#.")
        t('.', "...", "...", "...", "...", ".#.")
        t(':', "...", ".#.", "...", ".#.", "...")
        t('+', "...", ".#.", "###", ".#.", "...")
        t('?', "##.", "..#", ".#.", "...", ".#.")
        t('/', "..#", "..#", ".#.", "#..", "#..")
        t('*', "#.#", ".#.", "###", ".#.", "#.#")
        t('×', "...", "#.#", ".#.", "#.#", "...")
        t('\'', ".#.", ".#.", "...", "...", "...")
        t(',', "...", "...", "...", ".#.", "#..")
        t('(', ".#.", "#..", "#..", "#..", ".#.")
        t(')', ".#.", "..#", "..#", "..#", ".#.")
        t('%', "#.#", "..#", ".#.", "#..", "#.#")
        t('=', "...", "###", "...", "###", "...")
        t('#', "#.#", "###", "#.#", "###", "#.#")
        t('<', "..#", ".#.", "#..", ".#.", "..#")
        t('>', "#..", ".#.", "..#", ".#.", "#..")
        t('_', "...", "...", "...", "...", "###")
        t(NOTE, ".##", ".#.", ".#.", "##.", "##.")
        t(LEFT, ".#.", "#..", "###", "#..", ".#.")
        t(RIGHT, ".#.", "..#", "###", "..#", ".#.")
        t(UP, ".#.", "###", ".#.", ".#.", ".#.")
        t(DOWN, ".#.", ".#.", ".#.", "###", ".#.")
        t(PLAY, "#..", "##.", "###", "##.", "#..")
        t(TOKEN, ".#.", "#.#", "#.#", "#.#", ".#.")
        t(TICKET, "...", "###", "#.#", "###", "...")
        t(HEART, "#.#", "###", "###", ".#.", "...")
        t(STAR, ".#.", "###", ".#.", "#.#", "...")
    }

    private val bigIndex = HashMap<Char, Int>()
    private val tinyIndex = HashMap<Char, Int>()
    private var atlasWidth = 0

    /** White-on-transparent atlas: row 0 holds the 5x7 glyphs, row 1 the 3x5 glyphs. */
    val atlas: ImageBitmap by lazy { buildAtlas() }

    private fun buildAtlas(): ImageBitmap {
        val bigKeys = big.keys.toList()
        val tinyKeys = tiny.keys.toList()
        atlasWidth = maxOf(bigKeys.size * ADV, tinyKeys.size * TADV)
        val height = H + 1 + TH
        val pixels = IntArray(atlasWidth * height)
        bigKeys.forEachIndexed { i, ch ->
            bigIndex[ch] = i
            val rows = big.getValue(ch)
            for (y in 0 until H) for (x in 0 until W) {
                if (rows[y][x] == '#') pixels[y * atlasWidth + i * ADV + x] = Pal.WHITE
            }
        }
        tinyKeys.forEachIndexed { i, ch ->
            tinyIndex[ch] = i
            val rows = tiny.getValue(ch)
            for (y in 0 until TH) for (x in 0 until TW) {
                if (rows[y][x] == '#') pixels[(H + 1 + y) * atlasWidth + i * TADV + x] = Pal.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, atlasWidth, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    fun rows(c: Char, isTiny: Boolean): Array<String>? {
        val key = c.uppercaseChar()
        return if (isTiny) tiny[key] ?: tiny['?'] else big[key] ?: big['?']
    }

    fun width(text: String, scale: Float, isTiny: Boolean = false): Float {
        if (text.isEmpty()) return 0f
        val adv = if (isTiny) TADV else ADV
        val w = if (isTiny) TW else W
        return ((text.length - 1) * adv + w) * scale
    }

    fun height(scale: Float, isTiny: Boolean = false): Float = (if (isTiny) TH else H) * scale

    fun draw(
        scope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        alpha: Float = 1f,
        isTiny: Boolean = false,
    ) {
        val img = atlas
        val adv = if (isTiny) TADV else ADV
        val gw = if (isTiny) TW else W
        val gh = if (isTiny) TH else H
        val srcY = if (isTiny) H + 1 else 0
        val filter = TintCache.get(color)
        val dw = (gw * scale).roundToInt().coerceAtLeast(1)
        val dh = (gh * scale).roundToInt().coerceAtLeast(1)
        for (i in text.indices) {
            val ch = text[i].uppercaseChar()
            if (ch == ' ') continue
            val index = (if (isTiny) tinyIndex[ch] else bigIndex[ch])
                ?: (if (isTiny) tinyIndex['?'] else bigIndex['?'])
                ?: continue
            scope.drawImage(
                image = img,
                srcOffset = IntOffset(index * adv, srcY),
                srcSize = IntSize(gw, gh),
                dstOffset = IntOffset((x + i * adv * scale).roundToInt(), y.roundToInt()),
                dstSize = IntSize(dw, dh),
                alpha = alpha,
                colorFilter = filter,
                filterQuality = FilterQuality.None,
            )
        }
    }

    /** Draws text with a one-pixel drop shadow (in font pixels) for legibility over busy art. */
    fun drawShadowed(
        scope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        shadow: Color = Color(Pal.BLACK),
        alpha: Float = 1f,
        isTiny: Boolean = false,
    ) {
        draw(scope, text, x + scale, y + scale, scale, shadow, alpha, isTiny)
        draw(scope, text, x, y, scale, color, alpha, isTiny)
    }

    fun drawCentered(
        scope: DrawScope,
        text: String,
        cx: Float,
        y: Float,
        scale: Float,
        color: Color,
        alpha: Float = 1f,
        isTiny: Boolean = false,
        shadow: Boolean = true,
    ) {
        val x = cx - width(text, scale, isTiny) / 2f
        if (shadow) drawShadowed(scope, text, x, y, scale, color, alpha = alpha, isTiny = isTiny)
        else draw(scope, text, x, y, scale, color, alpha, isTiny)
    }
}

/** Caches one SrcIn tint filter per color so text and sprites can be tinted without allocating. */
object TintCache {
    private const val SIZE = 128
    private val keys = LongArray(SIZE)
    private val used = BooleanArray(SIZE)
    private val filters = arrayOfNulls<ColorFilter>(SIZE)

    fun get(color: Color): ColorFilter {
        val key = color.value.toLong()
        var i = ((key xor (key ushr 29) xor (key ushr 45)).toInt() and 0x7FFFFFFF) % SIZE
        repeat(SIZE) {
            if (!used[i]) {
                used[i] = true
                keys[i] = key
                val f = ColorFilter.tint(color, BlendMode.SrcIn)
                filters[i] = f
                return f
            }
            if (keys[i] == key) return filters[i]!!
            i = (i + 1) % SIZE
        }
        used.fill(false)
        return get(color)
    }
}
