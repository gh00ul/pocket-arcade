package com.pocketarcade.games.racer

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.sin

/** Painted art for the synthwave racer. */
internal object RacerArt {
    /** Setting sun, sliced by bands that widen towards the horizon. */
    val sun: Texture by lazy {
        val n = 64
        paintTexture(n, n, 8) {
            clear(0)
            val m = n / 2f
            vgradCircle(m, m, m - 1f)
            var band = m + 2f
            var gap = 1f
            while (band < n) {
                paint.reset()
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                canvas.drawRect(0f, band, n.toFloat(), band + gap, paint)
                paint.xfermode = null
                band += gap + 6f
                gap += 1f
            }
        }
    }

    private fun TexPaint.vgradCircle(cx: Float, cy: Float, r: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = android.graphics.LinearGradient(0f, cy - r, 0f, cy + r, Pal.YELLOW, Pal.PINK, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** City skyline silhouette for the horizon, windows lit here and there. */
    val skyline: Texture by lazy {
        paintTexture(256, 48, 4) {
            clear(0)
            var x = 0f
            var k = 0
            while (x < 256f) {
                val w = 8f + (k * 37 % 14)
                val h = 12f + (k * 53 % 30)
                vgrad(x, 48f - h, w, h, Pal.shade(Pal.PLUM, 0.78f), Pal.shade(Pal.PLUM, 0.6f))
                if (k % 4 == 1) rect(x + w / 2f - 0.3f, 48f - h - 5f, 0.6f, 5f, Pal.shade(Pal.PLUM, 0.7f))
                var wy = 48f - h + 3f
                while (wy < 46f) {
                    var wx = x + 2f
                    while (wx < x + w - 2f) {
                        if (((wx * 7).toInt() + (wy * 13).toInt() + k) % 5 == 0) rect(wx, wy, 1.2f, 1.6f, Pal.shade(Pal.YELLOW, 0.75f))
                        wx += 3f
                    }
                    wy += 4f
                }
                x += w + (k % 3)
                k++
            }
        }
    }

    val mountains: Texture by lazy {
        paintTexture(256, 40, 4) {
            clear(0)
            val pts = ArrayList<Float>()
            pts += 0f; pts += 40f
            var x = 0f
            while (x <= 256f) {
                pts += x; pts += 40f - (18f + sin(x / 19f) * 9f + sin(x / 7f + 1f) * 4f + sin(x / 43f) * 6f)
                x += 1f
            }
            pts += 256f; pts += 40f
            val arr = pts.toFloatArray()
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = android.graphics.LinearGradient(0f, 5f, 0f, 40f, Pal.mix(Pal.INDIGO, Pal.VIOLET, 0.4f), Pal.INDIGO, android.graphics.Shader.TileMode.CLAMP)
            val path = android.graphics.Path()
            path.moveTo(arr[0], arr[1])
            var i = 2
            while (i < arr.size) {
                path.lineTo(arr[i], arr[i + 1]); i += 2
            }
            path.close()
            canvas.drawPath(path, paint)
            paint.shader = null
            // A neon ridge line.
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 0.7f
            paint.color = Pal.HOTPINK
            val ridge = android.graphics.Path()
            ridge.moveTo(arr[2], arr[3])
            i = 4
            while (i < arr.size - 2) {
                ridge.lineTo(arr[i], arr[i + 1]); i += 2
            }
            canvas.drawPath(ridge, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }
    }

    /** Ground with a neon grid; each segment shows one cross line along its start. */
    val ground: Texture by lazy {
        paintTexture(128, 8, 4) {
            fill(0xFF14082A.toInt())
            for (x in 0 until 128 step 8) rect(x.toFloat(), 0f, 0.7f, 8f, Pal.shade(Pal.PINK, 0.8f))
            rect(0f, 0f, 128f, 0.8f, Pal.PINK)
        }
    }

    private fun asphaltTex(base: Int, speck: Int, seed: Int) = paintTexture(16, 8, 8) {
        fill(base)
        for (i in 0 until 40) circle(hash01(i, seed) * 16f, hash01(i, seed + 1) * 8f, 0.18f, speck)
    }
    val asphalt: Texture by lazy { asphaltTex(0xFF24203A.toInt(), 0xFF34304E.toInt(), 3) }
    val asphaltDark: Texture by lazy { asphaltTex(0xFF1C1830.toInt(), 0xFF2A2644.toInt(), 5) }

    val white: Texture get() = TexKit.white

    /** A palm tree silhouette with a neon rim. */
    val palm: Texture by lazy {
        paintTexture(28, 44, 8) {
            clear(0)
            val trunk = Pal.shade(Pal.BROWN, 0.5f)
            fun tree(color: Int, grow: Float) {
                var prevX = 14.5f
                var prevY = 44f
                for (k in 1..15) {
                    val y = 44f - k * 2f
                    val x = 14.5f + sin(y / 9f) * 2f
                    line(prevX, prevY, x, y, 2.4f + grow - k * 0.06f, color)
                    prevX = x; prevY = y
                }
                val fronds = arrayOf(-1f to -0.35f, 1f to -0.35f, -1f to 0.25f, 1f to 0.25f, -0.4f to -0.9f, 0.4f to -0.9f)
                for ((dx, dy) in fronds) {
                    var px = 14f
                    var py = 14f
                    for (t in 1..12) {
                        val nx = 14f + dx * t
                        val ny = 14f + dy * t + t * t * 0.05f
                        line(px, py, nx, ny, (2.2f - t * 0.12f) + grow, color)
                        px = nx; py = ny
                    }
                }
                circle(14f, 14f, 2.4f + grow, color)
            }
            tree(Pal.PINK, 0.9f)
            tree(0xFF1A0A24.toInt(), 0f)
            for (k in 0 until 7) line(13.6f, 40f - k * 4f, 15.4f, 39.4f - k * 4f, 0.25f, trunk)
        }
    }

    /** A roadside billboard. */
    private fun sign(text: String, color: Int): Texture = paintTexture(60, 22, 6) {
        fill(0xFF08060C.toInt())
        strokeRound(0.6f, 0.6f, 58.8f, 20.8f, 1.5f, 1.2f, color)
        strokeRound(2.5f, 2.5f, 55f, 17f, 1f, 0.5f, Pal.shade(color, 0.5f))
        glow(1.2f, Pal.withAlpha(color, 0.8f)) { label(text, 30f, 7f, 8f, -1) }
        label(text, 30f, 7f, 8f, Pal.mix(color, Pal.WHITE, 0.3f))
    }
    val signs: Array<Texture> by lazy {
        arrayOf(
            sign("ARCADE", Pal.CYAN),
            sign("TURBO!", Pal.PINK),
            sign("TOKENS", Pal.YELLOW),
            sign("HI-SCORE", Pal.LIME),
        )
    }
    val post: Texture by lazy { TexKit.solid(4, 4, Pal.DARKGRAY) }

    /** A spinning token pickup (drawn squashed to fake the spin). */
    val token: Texture by lazy {
        paintTexture(14, 14, 10) {
            clear(0)
            circle(7f, 7f, 6.6f, Pal.ORANGE)
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = android.graphics.RadialGradient(5.5f, 5f, 9f, Pal.mix(Pal.GOLD, Pal.WHITE, 0.35f), Pal.GOLD, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(7f, 7f, 5.2f, paint)
            paint.shader = null
            star(7f, 7.2f, 3f, Pal.ORANGE)
        }
    }

    // ------------------------------------------------------------------ cars

    fun bodySide(color: Int): Texture = paintTexture(40, 10) {
        vgrad(0f, 0f, 40f, 10f, Pal.mix(color, Pal.WHITE, 0.25f), Pal.shade(color, 0.6f))
        rect(0f, 2f, 40f, 0.7f, Pal.mix(color, Pal.WHITE, 0.5f))
    }
    fun bodyTop(color: Int): Texture = paintTexture(20, 40) {
        vgrad(0f, 0f, 20f, 40f, Pal.mix(color, Pal.WHITE, 0.15f), Pal.shade(color, 0.85f))
        rect(8f, 0f, 4f, 40f, Pal.mix(color, Pal.WHITE, 0.4f))
    }
    fun rear(color: Int): Texture = paintTexture(24, 10, 8) {
        vgrad(0f, 0f, 24f, 10f, Pal.shade(color, 0.85f), Pal.shade(color, 0.55f))
        round(1f, 2f, 6f, 3f, 1f, Pal.RED)
        round(17f, 2f, 6f, 3f, 1f, Pal.RED)
        round(1.8f, 2.3f, 4.4f, 0.9f, 0.4f, Pal.mix(Pal.RED, Pal.WHITE, 0.5f))
        round(17.8f, 2.3f, 4.4f, 0.9f, 0.4f, Pal.mix(Pal.RED, Pal.WHITE, 0.5f))
        round(9f, 6f, 6f, 2f, 0.5f, Pal.shade(Pal.DARKGRAY, 0.8f))
        rect(0f, 8f, 24f, 2f, Pal.BLACK)
    }
    val glass: Texture by lazy {
        paintTexture(16, 8) {
            vgrad(0f, 0f, 16f, 8f, Pal.shade(Pal.SKY, 0.6f), Pal.shade(Pal.NAVY, 0.8f))
            polygon(floatArrayOf(2f, 1f, 7f, 1f, 5f, 3f, 2f, 3f), Pal.withAlpha(Pal.mix(Pal.SKY, Pal.WHITE, 0.4f), 0.8f))
        }
    }
    val tyre: Texture by lazy { TexKit.solid(4, 4, 0xFF101018.toInt()) }
    val tailGlow: Texture get() = TexKit.glow
}
