package com.pocketarcade.engine.r3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pocketarcade.engine.Painter
import kotlin.math.floor

/**
 * A [Painter] that draws into a rectangle of a [Texture], [scale] texels per painter unit.
 * Used to paint live attract screens, scoreboards and signs that the 3D renderer then maps
 * onto geometry.
 */
class RasterPainter : Painter {
    private var tex: Texture? = null
    private var ox = 0
    private var oy = 0
    private var rw = 0
    private var rh = 0
    private var s = 1f

    fun begin(texture: Texture, x: Int, y: Int, w: Int, h: Int, scale: Float): RasterPainter {
        tex = texture
        ox = x
        oy = y
        rw = w
        rh = h
        s = scale
        return this
    }

    fun begin(region: Region, scale: Float) = begin(region.tex, region.x, region.y, region.w, region.h, scale)

    override fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
        val t = tex ?: return
        var x0 = floor(x * s).toInt()
        var y0 = floor(y * s).toInt()
        var x1 = floor((x + w) * s).toInt()
        var y1 = floor((y + h) * s).toInt()
        if (x0 < 0) x0 = 0
        if (y0 < 0) y0 = 0
        if (x1 > rw) x1 = rw
        if (y1 > rh) y1 = rh
        if (x1 <= x0 || y1 <= y0) return
        val argb = color.toArgb()
        val a = (alpha * (argb ushr 24)).toInt().coerceIn(0, 255)
        if (a == 0) return
        val px = t.pixels
        val tw = t.width
        if (a >= 250) {
            val c = argb or -0x1000000
            for (yy in y0 until y1) {
                val row = (oy + yy) * tw + ox
                px.fill(c, row + x0, row + x1)
            }
        } else {
            val sr = argb shr 16 and 255
            val sg = argb shr 8 and 255
            val sb = argb and 255
            for (yy in y0 until y1) {
                val row = (oy + yy) * tw + ox
                for (xx in x0 until x1) {
                    val d = px[row + xx]
                    val dr = d shr 16 and 255
                    val dg = d shr 8 and 255
                    val db = d and 255
                    px[row + xx] = -0x1000000 or
                        ((dr + (sr - dr) * a / 255) shl 16) or
                        ((dg + (sg - dg) * a / 255) shl 8) or
                        (db + (sb - db) * a / 255)
                }
            }
        }
    }
}
