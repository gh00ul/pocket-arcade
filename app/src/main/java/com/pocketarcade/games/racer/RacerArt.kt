package com.pocketarcade.games.racer

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Painted art for the synthwave racer. */
internal object RacerArt {
    /** Transparent white: soft gradients fade to this so their middles don't turn grey. */
    private const val CLEAR_WHITE = 0x00FFFFFF

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

    /**
     * City skyline silhouette for the horizon: plum towers rim-lit in magenta on their right edge,
     * stepped tops, antennas with red beacons, and windows lit in a few colours.
     */
    val skyline: Texture by lazy { skylineTexture(haze = 0f, seed = 0) }

    /** A second, hazier skyline behind the first, so the city has depth as it slides past. */
    val skylineFar: Texture by lazy { skylineTexture(haze = 0.55f, seed = 7) }

    private fun skylineTexture(haze: Float, seed: Int): Texture = paintTexture(256, 48, 4) {
        clear(0)
        val windows = intArrayOf(Pal.YELLOW, Pal.CYAN, Pal.HOTPINK)
        var x = 0f
        var k = seed
        while (x < 256f) {
            val w = 8f + (k * 37 % 14)
            val h = (12f + (k * 53 % 30)) * (if (haze > 0f) 0.8f else 1f)
            val body = Pal.mix(Pal.shade(Pal.PLUM, 0.7f), Pal.mix(Pal.PLUM, Pal.PINK, 0.4f), haze)
            vgrad(x, 48f - h, w, h, Pal.shade(body, 1.05f), Pal.shade(body, 0.7f))
            rect(x + w - 0.6f, 48f - h, 0.6f, h, Pal.withAlpha(Pal.HOTPINK, 0.55f - haze * 0.25f))
            if (k % 3 == 0) rect(x + w * 0.25f, 48f - h - 3f, w * 0.5f, 3f, Pal.shade(body, 0.85f))
            if (k % 4 == 1) {
                rect(x + w / 2f - 0.3f, 48f - h - 7f, 0.6f, 7f, Pal.shade(body, 0.8f))
                circle(x + w / 2f, 48f - h - 7f, 0.7f, Pal.withAlpha(Pal.RED, 0.9f - haze * 0.5f))
            }
            var wy = 48f - h + 3f
            while (wy < 46f) {
                var wx = x + 2f
                while (wx < x + w - 2f) {
                    val n = (wx * 7).toInt() + (wy * 13).toInt() + k
                    if (n % 5 == 0) rect(wx, wy, 1.2f, 1.6f, Pal.withAlpha(windows[(n / 5) % 3], (0.85f - haze * 0.6f)))
                    wx += 3f
                }
                wy += 4f
            }
            x += w + (k % 3)
            k++
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

    /**
     * Road surface, one tile across the road's width: speckled asphalt with two darker wheel
     * tracks (where the lanes' cars run) and a faintly polished middle.
     */
    private fun asphaltTex(base: Int, speck: Int, seed: Int) = paintTexture(16, 8, 8) {
        fill(base)
        for (i in 0 until 40) circle(hash01(i, seed) * 16f, hash01(i, seed + 1) * 8f, 0.18f, speck)
        rect(3.2f, 0f, 2.6f, 8f, Pal.withAlpha(Pal.BLACK, 0.2f))
        rect(10.2f, 0f, 2.6f, 8f, Pal.withAlpha(Pal.BLACK, 0.2f))
        rect(6.6f, 0f, 2.8f, 8f, Pal.withAlpha(Pal.WHITE, 0.03f))
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
            sign("DRIFT!", Pal.YELLOW),
            sign("HI-SCORE", Pal.LIME),
        )
    }
    val post: Texture by lazy { TexKit.solid(4, 4, Pal.DARKGRAY) }

    /** Start light lenses, lit red and green and dark, and the black box they sit in. */
    private fun lamp(color: Int, lit: Boolean) = paintTexture(16, 16, 8) {
        clear(0)
        circle(8f, 8f, 7.2f, Pal.shade(color, if (lit) 0.7f else 0.25f))
        circle(8f, 8f, 5.6f, Pal.shade(color, if (lit) 1f else 0.35f))
        if (lit) circle(6.5f, 6.5f, 2f, Pal.mix(color, Pal.WHITE, 0.6f))
    }
    val lampRed: Texture by lazy { lamp(0xFFFF2020.toInt(), true) }
    val lampGreen: Texture by lazy { lamp(0xFF20FF50.toInt(), true) }
    val lampOff: Texture by lazy { lamp(Pal.GRAY, false) }
    val lampBox: Texture by lazy { TexKit.solid(4, 4, 0xFF06040A.toInt()) }

    /** Black and white chequers painted across the road at the start/finish line. */
    val checker: Texture by lazy {
        paintTexture(32, 4, 4) {
            fill(Pal.WHITE)
            for (x in 0 until 16) for (y in 0 until 2) if ((x + y) % 2 == 0) rect(x * 2f, y * 2f, 2f, 2f, Pal.BLACK)
        }
    }

    /**
     * The lit plate on the back of a hall cabinet's racing seat, the pod's number [n] in a
     * roundel under a chequered strip, so the pods read from across the hall.
     */
    fun seatPlate(n: Int): Texture = seatPlates.getOrPut(n) {
        paintTexture(32, 48, 6) {
            vgrad(0f, 0f, 32f, 48f, 0xFF3A0A12.toInt(), 0xFF140408.toInt())
            for (x in 0 until 8) for (y in 0 until 2) rect(x * 4f, 2f + y * 4f, 4f, 4f, if ((x + y) % 2 == 0) Pal.WHITE else Pal.BLACK)
            glow(2.5f, Pal.withAlpha(Pal.RED, 0.9f)) { strokeRound(2f, 12f, 28f, 34f, 4f, 1.4f, -1) }
            strokeRound(2f, 12f, 28f, 34f, 4f, 1f, 0xFFFF8A80.toInt())
            circle(16f, 26f, 9f, Pal.WHITE)
            ring(16f, 26f, 9f, 1.2f, Pal.RED)
            label(n.toString(), 16f, 21.5f, 10f, 0xFF140408.toInt())
            label("TURBO", 16f, 38.5f, 4.2f, Pal.WHITE)
        }
    }
    private val seatPlates = HashMap<Int, Texture>()

    /** The hall cabinet's dash: a lit speedo and rev counter either side of a digital readout. */
    val dash: Texture by lazy {
        paintTexture(64, 20, 6) {
            fill(0xFF100C18.toInt())
            rect(0f, 0f, 64f, 0.8f, Pal.shade(Pal.PINK, 0.7f))
            for (g in 0..1) {
                val cx = if (g == 0) 12f else 52f
                val color = if (g == 0) Pal.CYAN else Pal.PINK
                circle(cx, 11f, 8f, 0xFF1C1828.toInt())
                ring(cx, 11f, 7.4f, 0.8f, color)
                for (k in 0..8) {
                    val a = (0.75f + k * 0.1875f) * PI.toFloat()
                    line(cx + cos(a) * 5.6f, 11f + sin(a) * 5.6f, cx + cos(a) * 6.8f, 11f + sin(a) * 6.8f, 0.5f, if (k >= 7) Pal.RED else Pal.WHITE)
                }
                val n = (if (g == 0) 1.95f else 2.1f) * PI.toFloat()
                line(cx, 11f, cx + cos(n) * 6f, 11f + sin(n) * 6f, 0.8f, Pal.ORANGE)
                circle(cx, 11f, 1.2f, Pal.GRAY)
            }
            round(23f, 6f, 18f, 9f, 1f, 0xFF06040A.toInt())
            label("288", 32f, 7.5f, 6f, Pal.CYAN)
        }
    }

    /** The neon FINISH banner on the start/finish gantry, chequered at both ends. */
    val banner: Texture by lazy {
        paintTexture(120, 20, 6) {
            fill(0xFF08060C.toInt())
            for (x in 0 until 4) for (y in 0 until 4) {
                val c = if ((x + y) % 2 == 0) Pal.WHITE else Pal.BLACK
                rect(x * 4f, y * 5f, 4f, 5f, c)
                rect(104f + x * 4f, y * 5f, 4f, 5f, c)
            }
            strokeRound(17f, 1f, 86f, 18f, 2f, 1f, Pal.CYAN)
            glow(1.4f, Pal.withAlpha(Pal.PINK, 0.8f)) { label("FINISH", 60f, 5f, 10f, -1) }
            label("FINISH", 60f, 5f, 10f, Pal.mix(Pal.PINK, Pal.WHITE, 0.3f))
        }
    }

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

    /** Car paint is a touch under full brightness so pale colours (white, yellow) stay under the bloom. */
    private const val PAINT = 0.86f

    /** A car's flank: clearcoat light at the shoulder, a white racing stripe, a dark sill. */
    fun carSide(color: Int): Texture = paintTexture(48, 12, 4) {
        val c = Pal.shade(color, PAINT)
        vgrad(0f, 0f, 48f, 12f, Pal.mix(c, Pal.WHITE, 0.22f), c, Pal.shade(c, 0.55f))
        rect(0f, 2.2f, 48f, 0.6f, Pal.withAlpha(Pal.WHITE, 0.45f))
        rect(0f, 6f, 48f, 1.4f, Pal.withAlpha(Pal.WHITE, 0.55f))
        rect(0f, 10.2f, 48f, 1.8f, 0xFF0A0810.toInt())
    }

    /** Roof, hood and deck: a centre stripe bordered in black, with a little metallic flake. */
    fun carTop(color: Int): Texture = paintTexture(24, 48, 4) {
        val c = Pal.shade(color, PAINT)
        vgrad(0f, 0f, 24f, 48f, Pal.mix(c, Pal.WHITE, 0.16f), c, Pal.shade(c, 0.85f))
        rect(9.6f, 0f, 4.8f, 48f, Pal.withAlpha(Pal.WHITE, 0.6f))
        rect(9f, 0f, 0.6f, 48f, Pal.withAlpha(Pal.BLACK, 0.3f))
        rect(14.4f, 0f, 0.6f, 48f, Pal.withAlpha(Pal.BLACK, 0.3f))
        for (i in 0 until 40) circle(hash01(i, 71) * 24f, hash01(i, 72) * 48f, 0.25f, Pal.withAlpha(Pal.WHITE, 0.25f))
    }

    /** The tail: body colour above, a dark finned diffuser below, tail-light housings and a plate. */
    fun carRear(color: Int): Texture = paintTexture(44, 12, 6) {
        val c = Pal.shade(color, PAINT)
        vgrad(0f, 0f, 44f, 6.4f, Pal.mix(c, Pal.WHITE, 0.18f), c)
        rect(0f, 6.4f, 44f, 5.6f, 0xFF0A0810.toInt())
        var fx = 4f
        while (fx < 40f) {
            rect(fx, 7.2f, 0.7f, 4.2f, 0xFF1C1A28.toInt())
            fx += 4.5f
        }
        round(2.5f, 2.2f, 14f, 3.4f, 1.2f, 0xFF3A0810.toInt())
        round(27.5f, 2.2f, 14f, 3.4f, 1.2f, 0xFF3A0810.toInt())
        round(17f, 7.4f, 10f, 3.2f, 0.6f, Pal.WHITE)
        label("TURBO", 22f, 7.9f, 2.4f, 0xFF140408.toInt(), tiny = true)
    }

    /** Tinted glass with a sky reflection streak. */
    val glass: Texture by lazy {
        paintTexture(16, 8, 4) {
            vgrad(0f, 0f, 16f, 8f, Pal.shade(Pal.SKY, 0.55f), 0xFF0C1030.toInt())
            polygon(floatArrayOf(2f, 0.5f, 8f, 0.5f, 5f, 3.5f, 1.5f, 3.5f), Pal.withAlpha(Pal.mix(Pal.SKY, Pal.WHITE, 0.4f), 0.7f))
            polygon(floatArrayOf(10f, 0.5f, 12f, 0.5f, 10f, 3.5f, 8.5f, 3.5f), Pal.withAlpha(Pal.WHITE, 0.3f))
        }
    }
    val tyre: Texture by lazy { TexKit.solid(4, 4, 0xFF101018.toInt()) }

    /** A wheel's face: black tyre round a silver rim with five spokes. */
    val wheel: Texture by lazy {
        paintTexture(16, 16, 6) {
            fill(0xFF101018.toInt())
            circle(8f, 8f, 7.4f, 0xFF16161E.toInt())
            circle(8f, 8f, 5.4f, 0xFF9098B0.toInt())
            circle(8f, 8f, 4.4f, 0xFF262838.toInt())
            for (k in 0 until 5) {
                val a = k * 1.2566f
                line(8f, 8f, 8f + cos(a) * 4.6f, 8f + sin(a) * 4.6f, 1.1f, 0xFFB4BCD0.toInt())
            }
            circle(8f, 8f, 1.3f, 0xFFD0D6E6.toInt())
        }
    }

    /** A flame or exhaust plume: a hot bell across, fading to nothing along its length (the top is the car's end). */
    val plume: Texture by lazy {
        paintTexture(16, 32, 3) {
            clear(0)
            hgrad(0f, 0f, 16f, 32f, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE)
            paint.reset()
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, 32f, Pal.WHITE, CLEAR_WHITE, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 16f, 32f, paint)
            paint.shader = null
            paint.xfermode = null
        }
    }

    /** A puff of tyre smoke: a soft, slightly lumpy grey disc. */
    val smoke: Texture by lazy {
        paintTexture(32, 32, 3) {
            clear(0)
            for (i in 0 until 7) {
                val x = 16f + (hash01(i, 81) - 0.5f) * 10f
                val y = 16f + (hash01(i, 82) - 0.5f) * 10f
                radial(x, y, 9f + hash01(i, 83) * 4f, Pal.withAlpha(0xFFB8B4D0.toInt(), 0.32f), 0x00B8B4D0)
            }
        }
    }

    /** A glow that fades from a colour at the horizon to nothing towards the sky. */
    val horizon: Texture by lazy {
        paintTexture(4, 32, 4) { vgrad(0f, 0f, 4f, 32f, CLEAR_WHITE, Pal.withAlpha(Pal.WHITE, 0.55f), Pal.withAlpha(Pal.WHITE, 0.95f)) }
    }

    /** A soft dark streak for tyre marks: dense in the middle, feathered at the sides. */
    val skid: Texture by lazy { paintTexture(8, 8, 4) { hgrad(0f, 0f, 8f, 8f, 0x00000000, 0xFF000000.toInt(), 0x00000000) } }

    val tailGlow: Texture get() = TexKit.glow
}
