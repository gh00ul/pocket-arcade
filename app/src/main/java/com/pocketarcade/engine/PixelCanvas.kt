package com.pocketarcade.engine

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.sqrt

/**
 * A tiny software raster used to paint sprites and tiles pixel by pixel at 1x.
 * The result becomes an [ImageBitmap] that is drawn scaled up with nearest-neighbour filtering.
 */
class PixelCanvas(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun inBounds(x: Int, y: Int) = x in 0 until w && y in 0 until h

    fun set(x: Int, y: Int, c: Int) {
        if (x in 0 until w && y in 0 until h) px[y * w + x] = c
    }

    fun get(x: Int, y: Int): Int = if (inBounds(x, y)) px[y * w + x] else 0

    fun fill(x: Int, y: Int, fw: Int, fh: Int, c: Int) {
        val x0 = x.coerceAtLeast(0)
        val y0 = y.coerceAtLeast(0)
        val x1 = (x + fw).coerceAtMost(w)
        val y1 = (y + fh).coerceAtMost(h)
        for (yy in y0 until y1) {
            val row = yy * w
            for (xx in x0 until x1) px[row + xx] = c
        }
    }

    fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
        hline(x, x + rw - 1, y, c)
        hline(x, x + rw - 1, y + rh - 1, c)
        vline(x, y, y + rh - 1, c)
        vline(x + rw - 1, y, y + rh - 1, c)
    }

    fun hline(x0: Int, x1: Int, y: Int, c: Int) {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) set(x, y, c)
    }

    fun vline(x: Int, y0: Int, y1: Int, c: Int) {
        for (y in minOf(y0, y1)..maxOf(y0, y1)) set(x, y, c)
    }

    /** Checkerboard dither of [c] over a rectangle; [phase] picks which half of the checker. */
    fun dither(x: Int, y: Int, dw: Int, dh: Int, c: Int, phase: Int = 0) {
        for (yy in y until y + dh) for (xx in x until x + dw) {
            if ((xx + yy + phase) and 1 == 0) set(xx, yy, c)
        }
    }

    fun disc(cx: Float, cy: Float, r: Float, c: Int) {
        val x0 = (cx - r - 1).toInt()
        val x1 = (cx + r + 1).toInt()
        val y0 = (cy - r - 1).toInt()
        val y1 = (cy + r + 1).toInt()
        val r2 = r * r
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            if (dx * dx + dy * dy <= r2) set(x, y, c)
        }
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int) {
        val x0 = (cx - rx - 1).toInt()
        val x1 = (cx + rx + 1).toInt()
        val y0 = (cy - ry - 1).toInt()
        val y1 = (cy + ry + 1).toInt()
        for (y in y0..y1) for (x in x0..x1) {
            val dx = (x + 0.5f - cx) / rx
            val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) set(x, y, c)
        }
    }

    fun ring(cx: Float, cy: Float, r: Float, thickness: Float, c: Int) {
        val x0 = (cx - r - 1).toInt()
        val x1 = (cx + r + 1).toInt()
        val y0 = (cy - r - 1).toInt()
        val y1 = (cy + r + 1).toInt()
        for (y in y0..y1) for (x in x0..x1) {
            val d = sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy))
            if (d <= r && d > r - thickness) set(x, y, c)
        }
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy; x += sx
            }
            if (e2 <= dx) {
                err += dx; y += sy
            }
        }
    }

    /**
     * Paints a sprite described as rows of characters. Each character is looked up in [palette];
     * '.' and unmapped characters are transparent.
     */
    fun sprite(rows: Array<String>, x: Int, y: Int, palette: Map<Char, Int>, flipX: Boolean = false) {
        for (ry in rows.indices) {
            val row = rows[ry]
            for (rx in row.indices) {
                val ch = row[rx]
                if (ch == '.') continue
                val c = palette[ch] ?: continue
                val dx = if (flipX) row.length - 1 - rx else rx
                set(x + dx, y + ry, c)
            }
        }
    }

    fun text(s: String, x: Int, y: Int, c: Int, tiny: Boolean = false) {
        val adv = if (tiny) PixelFont.TADV else PixelFont.ADV
        for (i in s.indices) {
            val rows = PixelFont.rows(s[i], tiny) ?: continue
            for (ry in rows.indices) for (rx in rows[ry].indices) {
                if (rows[ry][rx] == '#') set(x + i * adv + rx, y + ry, c)
            }
        }
    }

    fun textWidth(s: String, tiny: Boolean = false): Int =
        if (s.isEmpty()) 0 else if (tiny) s.length * PixelFont.TADV - 1 else s.length * PixelFont.ADV - 1

    fun textCentered(s: String, cx: Int, y: Int, c: Int, tiny: Boolean = false) =
        text(s, cx - textWidth(s, tiny) / 2, y, c, tiny)

    /** Copies non-transparent pixels of [src] onto this canvas. */
    fun blit(src: PixelCanvas, x: Int, y: Int, flipX: Boolean = false) {
        for (sy in 0 until src.h) for (sx in 0 until src.w) {
            val c = src.px[sy * src.w + sx]
            if (c ushr 24 == 0) continue
            val dx = if (flipX) src.w - 1 - sx else sx
            set(x + dx, y + sy, c)
        }
    }

    /** Adds a one-pixel [c] outline around every opaque pixel (4-neighbourhood). */
    fun outline(c: Int) {
        val copy = px.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (copy[y * w + x] ushr 24 != 0) continue
            val n = (x > 0 && copy[y * w + x - 1] ushr 24 != 0) ||
                (x < w - 1 && copy[y * w + x + 1] ushr 24 != 0) ||
                (y > 0 && copy[(y - 1) * w + x] ushr 24 != 0) ||
                (y < h - 1 && copy[(y + 1) * w + x] ushr 24 != 0)
            if (n) px[y * w + x] = c
        }
    }

    fun flippedX(): PixelCanvas {
        val out = PixelCanvas(w, h)
        for (y in 0 until h) for (x in 0 until w) out.px[y * w + (w - 1 - x)] = px[y * w + x]
        return out
    }

    fun toImageBitmap(): ImageBitmap =
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Hash-based value noise in 0..1 for deterministic procedural detail. */
fun hash01(x: Int, y: Int, seed: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1103515245
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}
