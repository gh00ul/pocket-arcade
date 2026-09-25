package com.pocketarcade.hub

import android.graphics.Paint
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.mixArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Colour helpers for the hall's materials. */
internal fun dim(c: Int, f: Float): Int = mixArgb(c, 0xFF000000.toInt(), 1f - f)
internal fun lift(c: Int, f: Float): Int = mixArgb(c, -1, f)
internal fun alpha(c: Int, a: Float): Int = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

/**
 * Smooth procedural materials for the arcade hall: the blacklight carpet, walls, tiles, wood,
 * metal, glass, signage and prize packaging. Everything is painted once, at a resolution meant
 * for a full-HD phone screen.
 */
object HallArt {
    // ------------------------------------------------------------------ floors

    /**
     * The classic arcade carpet: black with neon planets, stars, zigzags and squiggles that
     * glow under the blacklights. Tiles seamlessly.
     */
    val carpet: Texture by lazy {
        val n = 512
        val tp = TexPaint(n, n)
        tp.fill(0xFF0B0816.toInt())
        tp.grain(0.35f, 3)
        val colors = intArrayOf(0xFF39E6F2.toInt(), 0xFFFF4FA8.toInt(), 0xFFFFD84D.toInt(), 0xFF9B6BFF.toInt(), 0xFF5CF08A.toInt(), 0xFFFF8A3D.toInt())
        // Draw each motif, wrapped round the edges so the pattern tiles.
        fun wrapped(x: Float, y: Float, r: Float, draw: (Float, Float) -> Unit) {
            for (ox in -1..1) for (oy in -1..1) {
                val cx = x + ox * n
                val cy = y + oy * n
                if (cx + r < 0 || cx - r > n || cy + r < 0 || cy - r > n) continue
                draw(cx, cy)
            }
        }
        for (i in 0 until 46) {
            val x = hash01(i, 11) * n
            val y = hash01(i, 12) * n
            val c = dim(colors[i % colors.size], 0.72f)
            val kind = i % 7
            val size = 10f + hash01(i, 13) * 18f
            val rot = hash01(i, 14) * 6.28f
            wrapped(x, y, size * 2f) { cx, cy ->
                when (kind) {
                    0 -> { // ringed planet
                        tp.circle(cx, cy, size * 0.6f, c)
                        tp.circle(cx - size * 0.15f, cy - size * 0.15f, size * 0.22f, lift(c, 0.4f))
                        tp.canvas.save()
                        tp.canvas.rotate(rot * 57.3f, cx, cy)
                        tp.oval(cx, cy, size * 1.25f, size * 0.32f, 0)
                        tp.paint.reset()
                        tp.paint.isAntiAlias = true
                        tp.paint.style = Paint.Style.STROKE
                        tp.paint.strokeWidth = 3f
                        tp.paint.color = dim(colors[(i + 2) % colors.size], 0.72f)
                        tp.canvas.drawOval(cx - size * 1.25f, cy - size * 0.32f, cx + size * 1.25f, cy + size * 0.32f, tp.paint)
                        tp.canvas.restore()
                    }
                    1 -> { // star
                        val pts = FloatArray(20)
                        for (k in 0 until 10) {
                            val a = rot + k * PI.toFloat() / 5f
                            val rr = if (k % 2 == 0) size * 0.8f else size * 0.35f
                            pts[k * 2] = cx + cos(a) * rr
                            pts[k * 2 + 1] = cy + sin(a) * rr
                        }
                        tp.polygon(pts, c)
                    }
                    2 -> { // zigzag
                        var px = cx - size
                        var py = cy
                        for (k in 0 until 5) {
                            val nx = px + size * 0.45f
                            val ny = cy + if (k % 2 == 0) -size * 0.35f else size * 0.35f
                            tp.line(px, py, nx, ny, 3.5f, c)
                            px = nx; py = ny
                        }
                    }
                    3 -> tp.ring(cx, cy, size * 0.55f, 3.5f, c)
                    4 -> { // triangle outline
                        val pts = FloatArray(6)
                        for (k in 0 until 3) {
                            val a = rot + k * 2.094f
                            pts[k * 2] = cx + cos(a) * size * 0.7f
                            pts[k * 2 + 1] = cy + sin(a) * size * 0.7f
                        }
                        for (k in 0 until 3) {
                            val k2 = (k + 1) % 3
                            tp.line(pts[k * 2], pts[k * 2 + 1], pts[k2 * 2], pts[k2 * 2 + 1], 3f, c)
                        }
                    }
                    5 -> { // squiggle
                        var px = cx - size
                        var py = cy
                        for (k in 1..12) {
                            val nx = cx - size + k * size / 6f
                            val ny = cy + sin(k * 1.1f + rot) * size * 0.3f
                            tp.line(px, py, nx, ny, 3f, c)
                            px = nx; py = ny
                        }
                    }
                    else -> { // sparkle dots
                        for (k in 0 until 5) {
                            val a = rot + k * 1.256f
                            tp.circle(cx + cos(a) * size * 0.6f, cy + sin(a) * size * 0.6f, 2.2f, c)
                        }
                    }
                }
            }
        }
        // Tiny stars everywhere.
        for (i in 0 until 400) {
            val x = hash01(i, 21) * n
            val y = hash01(i, 22) * n
            tp.circle(x, y, 0.8f + hash01(i, 23) * 1.2f, alpha(colors[i % colors.size], 0.7f))
        }
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Polished dark floor tiles for the entrance and the prize counter. */
    val tiles: Texture by lazy {
        val n = 256
        val tp = TexPaint(n, n)
        val cells = 4
        val s = n / cells.toFloat()
        for (y in 0 until cells) for (x in 0 until cells) {
            val base = if ((x + y) % 2 == 0) 0xFF1C1826.toInt() else 0xFF2A2538.toInt()
            tp.vgrad(x * s, y * s, s, s, lift(base, 0.05f), base)
        }
        for (k in 0..cells) {
            tp.rect(k * s - 1f, 0f, 2f, n.toFloat(), 0xFF0A0810.toInt())
            tp.rect(0f, k * s - 1f, n.toFloat(), 2f, 0xFF0A0810.toInt())
        }
        tp.grain(0.06f, 5)
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Pavement slabs for the sidewalk out front. */
    val concrete: Texture by lazy {
        val n = 256
        val tp = TexPaint(n, n)
        tp.vgrad(0f, 0f, n.toFloat(), n.toFloat(), 0xFF4C4A50.toInt(), 0xFF424047.toInt())
        tp.grain(0.22f, 21)
        for (k in 0 until 7) {
            val x = hash01(k, 31) * n
            val y = hash01(k, 32) * n
            tp.radial(x, y, 14f + hash01(k, 33) * 26f, 0x22000000, 0)
        }
        tp.rect(0f, 0f, n.toFloat(), 3f, 0xFF2C2B30.toInt())
        tp.rect(0f, 0f, 3f, n.toFloat(), 0xFF2C2B30.toInt())
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Parking-lot blacktop. */
    val asphalt: Texture by lazy {
        val n = 256
        val tp = TexPaint(n, n)
        tp.fill(0xFF1D1D22.toInt())
        tp.grain(0.5f, 23)
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Terrazzo medallion set into the floor inside the entrance. */
    val floorLogo: Texture by lazy {
        val n = 512
        val c = n / 2f
        val tp = TexPaint(n, n)
        tp.clear(0)
        tp.radial(c, c, c, 0xFF2A1F44.toInt(), 0xFF1A1330.toInt())
        tp.ring(c, c, c - 8f, 12f, 0xFFB8B4C8.toInt())
        tp.ring(c, c, c - 30f, 4f, 0xFF39E6F2.toInt())
        // A compass of coloured terrazzo points.
        val colors = intArrayOf(0xFFFF4FA8.toInt(), 0xFFFFD84D.toInt(), 0xFF39E6F2.toInt(), 0xFF9B6BFF.toInt())
        for (k in 0 until 8) {
            val a = k * 0.7854f
            val r0 = if (k % 2 == 0) c - 40f else c - 90f
            val pts = floatArrayOf(
                c + kotlin.math.cos(a) * r0, c + kotlin.math.sin(a) * r0,
                c + kotlin.math.cos(a + 0.18f) * 90f, c + kotlin.math.sin(a + 0.18f) * 90f,
                c + kotlin.math.cos(a - 0.18f) * 90f, c + kotlin.math.sin(a - 0.18f) * 90f,
            )
            tp.polygon(pts, dim(colors[k % colors.size], 0.8f))
        }
        tp.circle(c, c, 110f, 0xFF120C22.toInt())
        tp.ring(c, c, 110f, 5f, 0xFFFFD84D.toInt())
        tp.outlinedText("POCKET", c, c - 8f, 52f, 0xFF39E6F2.toInt(), 0xFF0A0614.toInt(), 6f, Fonts.display)
        tp.outlinedText("ARCADE", c, c + 50f, 52f, 0xFFFFD84D.toInt(), 0xFF0A0614.toInt(), 6f, Fonts.display)
        tp.grain(0.12f, 41)
        tp.toTexture().also { tp.recycle() }
    }

    /** Coir entrance mat. */
    val mat: Texture by lazy {
        val tp = TexPaint(256, 128)
        tp.fill(0xFF2B1F1A.toInt())
        tp.grain(0.35f, 9)
        tp.strokeRound(6f, 6f, 244f, 116f, 10f, 5f, 0xFFB08040.toInt())
        tp.text("WELCOME", 128f, 82f, 44f, 0xFFD9A066.toInt(), Fonts.display)
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ walls

    /** Dark wall panels with a rail and baseboard; tiles horizontally. */
    val wall: Texture by lazy {
        val tp = TexPaint(256, 256)
        tp.vgrad(0f, 0f, 256f, 256f, 0xFF1A1330.toInt(), 0xFF120D22.toInt())
        for (x in 0 until 256 step 64) {
            tp.rect(x.toFloat(), 0f, 2f, 200f, 0xFF0C0818.toInt())
            tp.rect(x + 2f, 0f, 1f, 200f, 0xFF2A2046.toInt())
        }
        tp.rect(0f, 196f, 256f, 10f, 0xFF3A2E5E.toInt())
        tp.rect(0f, 196f, 256f, 2f, 0xFF5A4A8E.toInt())
        tp.vgrad(0f, 226f, 256f, 30f, 0xFF201A30.toInt(), 0xFF0A0810.toInt())
        tp.grain(0.05f, 7)
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Acoustic panels on the upper walls, fading into the dark towards the ceiling. */
    val upperWall: Texture by lazy {
        val n = 256
        val tp = TexPaint(n, n)
        tp.vgrad(0f, 0f, n.toFloat(), n.toFloat(), 0xFF06040C.toInt(), 0xFF171029.toInt())
        for (y in 0 until 2) for (x in 0 until 2) {
            val px = x * 128f
            val py = y * 128f
            tp.rect(px + 3f, py + 3f, 122f, 122f, 0x10FFFFFF)
            tp.rect(px + 3f, py + 3f, 122f, 2f, 0x14FFFFFF)
            tp.rect(px + 3f, py + 123f, 122f, 2f, 0x30000000)
        }
        tp.grain(0.04f, 17)
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** A backlit synthwave sunset for the wall above the prize counter. */
    val mural: Texture by lazy {
        val w = 512
        val h = 240
        val tp = TexPaint(w, h)
        val horizon = h * 0.62f
        tp.vgrad(0f, 0f, w.toFloat(), horizon, 0xFF1A0B3A.toInt(), 0xFFFF4F8A.toInt())
        for (i in 0 until 40) tp.circle(hash01(i, 51) * w, hash01(i, 52) * horizon * 0.6f, 1f + hash01(i, 53) * 1.2f, 0xCCFFFFFF.toInt())
        // The sun, sliced by the horizon bands.
        val sunR = h * 0.3f
        tp.canvas.save()
        tp.canvas.clipRect(0f, 0f, w.toFloat(), horizon)
        tp.radial(w / 2f, horizon - sunR * 0.35f, sunR * 1.7f, 0x66FFB040, 0)
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.shader = android.graphics.LinearGradient(0f, horizon - sunR * 1.35f, 0f, horizon, 0xFFFFE45A.toInt(), 0xFFFF3F8E.toInt(), android.graphics.Shader.TileMode.CLAMP)
        tp.canvas.drawCircle(w / 2f, horizon - sunR * 0.35f, sunR, tp.paint)
        tp.paint.shader = null
        var band = horizon - sunR * 0.55f
        var gap = 3f
        while (band < horizon) {
            tp.rect(0f, band, w.toFloat(), gap, 0xFF6A1A6A.toInt())
            band += gap + 9f
            gap += 1.5f
        }
        tp.canvas.restore()
        // Mountains.
        tp.polygon(floatArrayOf(0f, horizon, 70f, horizon - 48f, 130f, horizon - 18f, 190f, horizon - 60f, 250f, horizon), 0xFF2A0F4A.toInt())
        tp.polygon(floatArrayOf(300f, horizon, 360f, horizon - 40f, 420f, horizon - 70f, 470f, horizon - 30f, w.toFloat(), horizon - 44f, w.toFloat(), horizon), 0xFF2A0F4A.toInt())
        // The grid floor.
        tp.vgrad(0f, horizon, w.toFloat(), h - horizon, 0xFF12062A.toInt(), 0xFF05020E.toInt())
        for (k in -12..12) tp.line(w / 2f + k * 12f, horizon, w / 2f + k * 70f, h.toFloat(), 1.6f, 0xFF39E6F2.toInt())
        var gy = horizon + 3f
        var step = 4f
        while (gy < h) {
            tp.line(0f, gy, w.toFloat(), gy, 1.6f, 0xFFFF4FA8.toInt())
            gy += step
            step *= 1.45f
        }
        tp.rect(0f, horizon - 1f, w.toFloat(), 2f, 0xFFFFB0E0.toInt())
        tp.strokeRound(3f, 3f, w - 6f, h - 6f, 8f, 6f, 0xFF2A2440.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** A fan of coloured light thrown up a wall by a floor-level fixture (additive, white). */
    val washer: Texture by lazy {
        val tp = TexPaint(64, 256)
        for (y in 0 until 256) {
            val t = 1f - y / 255f
            val half = 5f + t * 27f
            val a = (1f - t) * (1f - t) * 0.5f + 0.02f
            tp.hgrad(32f - half, y.toFloat(), half, 1f, 0x00FFFFFF, alpha(-1, a))
            tp.hgrad(32f, y.toFloat(), half, 1f, alpha(-1, a), 0x00FFFFFF)
        }
        tp.toTexture().also { tp.recycle() }
    }

    /** A framed poster for the walls, one of a few designs. */
    fun poster(kind: Int): Texture {
        val tp = TexPaint(160, 240)
        val palettes = arrayOf(
            intArrayOf(0xFF2A0F5C.toInt(), 0xFFFF4FA8.toInt(), 0xFFFFD84D.toInt()),
            intArrayOf(0xFF06243A.toInt(), 0xFF39E6F2.toInt(), 0xFFFF8A3D.toInt()),
            intArrayOf(0xFF1C3A12.toInt(), 0xFF5CF08A.toInt(), 0xFFFFFFFF.toInt()),
            intArrayOf(0xFF3A0A14.toInt(), 0xFFFF5A5A.toInt(), 0xFFFFE14D.toInt()),
        )
        val (bg, a, b) = palettes[kind % palettes.size].let { Triple(it[0], it[1], it[2]) }
        tp.fill(0xFF0A0A0A.toInt())
        tp.vgrad(8f, 8f, 144f, 224f, lift(bg, 0.15f), bg)
        when (kind % 4) {
            0 -> { // sunset over a grid
                tp.circle(80f, 100f, 46f, b)
                for (k in 0 until 6) tp.rect(30f, 104f + k * 9f, 100f, 3f + k * 0.6f, bg)
                for (k in 0 until 8) tp.line(8f + k * 21f, 150f, 80f + (k - 3.5f) * 60f, 232f, 2f, a)
                for (k in 0 until 5) tp.line(8f, 160f + k * k * 3.2f, 152f, 160f + k * k * 3.2f, 2f, a)
            }
            1 -> { // rocket
                tp.oval(80f, 110f, 22f, 52f, lift(a, 0.4f))
                tp.circle(80f, 96f, 10f, bg)
                tp.polygon(floatArrayOf(58f, 150f, 80f, 130f, 102f, 150f, 102f, 166f, 58f, 166f), b)
                for (k in 0 until 30) tp.circle(hash01(k, 3) * 160f, hash01(k, 4) * 230f, 1.5f, -1)
            }
            2 -> { // joystick
                tp.round(40f, 140f, 80f, 44f, 10f, dim(a, 0.7f))
                tp.line(80f, 150f, 80f, 90f, 8f, 0xFF333333.toInt())
                tp.ball(80f, 82f, 18f, 0xFFE83A3A.toInt())
                tp.ball(104f, 160f, 7f, b)
            }
            else -> { // lightning
                tp.polygon(floatArrayOf(90f, 50f, 50f, 130f, 78f, 130f, 62f, 196f, 112f, 104f, 84f, 104f, 104f, 50f), b)
            }
        }
        val titles = arrayOf("NEON NIGHTS", "STAR BLASTER", "HIGH SCORE", "POWER UP!")
        tp.glowText(titles[kind % titles.size], 80f, 36f, 20f, -1, a, 6f, Fonts.display)
        tp.text("NOW PLAYING", 80f, 222f, 12f, alpha(-1, 0.7f), Fonts.condensed)
        return tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ materials

    fun solid(color: Int): Texture = Texture(4, 4, IntArray(16) { color })

    /** A plastic/paint surface: soft vertical gradient with fine grain. */
    fun paint(color: Int, top: Float = 0.12f, bottom: Float = 0.8f): Texture {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, lift(color, top), dim(color, bottom))
        tp.grain(0.03f, color)
        return tp.toTexture().also { tp.recycle() }
    }

    val brushedMetal: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.vgrad(0f, 0f, 128f, 128f, 0xFFC8CAD6.toInt(), 0xFF8A8C98.toInt())
        for (y in 0 until 128 step 2) tp.rect(0f, y.toFloat(), 128f, 1f, alpha(-1, 0.06f + hash01(y, 3) * 0.08f))
        tp.toTexture().also { tp.recycle() }
    }

    val darkMetal: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFF4A4C58.toInt(), 0xFF22232A.toInt())
        tp.grain(0.05f, 2)
        tp.toTexture().also { tp.recycle() }
    }

    val chrome: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFFFFFFFF.toInt(), 0xFF9AA0B4.toInt(), 0xFFE8ECF6.toInt(), 0xFF6A6E7E.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** Light wood (counters, skee-ball lanes, benches). */
    fun wood(base: Int = 0xFFC9884A.toInt(), planks: Int = 6): Texture {
        val tp = TexPaint(256, 256)
        val pw = 256f / planks
        for (i in 0 until planks) {
            val tone = 0.9f + hash01(i, 5) * 0.2f
            val c = if (tone > 1f) lift(base, tone - 1f) else dim(base, tone)
            tp.rect(i * pw, 0f, pw, 256f, c)
            for (k in 0 until 18) {
                val x = i * pw + hash01(i * 31 + k, 6) * pw
                tp.line(x, hash01(k, i + 7) * 256f, x + (hash01(k, i) - 0.5f) * 4f, hash01(k, i + 9) * 256f, 1.2f, alpha(dim(c, 0.7f), 0.35f))
            }
            tp.rect(i * pw, 0f, 1.5f, 256f, dim(base, 0.55f))
        }
        tp.grain(0.05f, 4)
        return tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** Clear glass with a faint tint and diagonal reflections (alpha-blended). */
    val glass: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.fill(alpha(0xFFB8D8FF.toInt(), 0.10f))
        tp.paint.reset()
        for (k in 0 until 3) {
            val x = 20f + k * 40f
            tp.polygon(floatArrayOf(x, 0f, x + 14f - k * 4f, 0f, x - 50f + 14f - k * 4f, 128f, x - 50f, 128f), alpha(-1, 0.10f - k * 0.02f))
        }
        tp.toTexture().also { tp.recycle() }
    }

    val shadow: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.radial(32f, 32f, 32f, alpha(0xFF000000.toInt(), 0.75f), 0)
        tp.toTexture().also { tp.recycle() }
    }

    val glow: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.radial(32f, 32f, 32f, -1, 0x00FFFFFF)
        tp.toTexture().also { tp.recycle() }
    }

    /** A cone of light seen from the side, for beams under spotlights (additive). */
    val beam: Texture by lazy {
        val tp = TexPaint(64, 128)
        for (y in 0 until 128) {
            val t = y / 127f
            val half = 6f + t * 26f
            tp.hgrad(32f - half, y.toFloat(), half, 1f, 0x00FFFFFF, alpha(-1, 0.22f * (1f - t)))
            tp.hgrad(32f, y.toFloat(), half, 1f, alpha(-1, 0.22f * (1f - t)), 0x00FFFFFF)
        }
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ signs

    /**
     * A neon sign: glowing tube lettering on a transparent background (drawn additively, so
     * the bloom makes it bleed light like the real thing).
     */
    fun neon(text: String, color: Int, w: Int = 512, h: Int = 128, size: Float = 86f): Texture {
        val tp = TexPaint(w, h)
        tp.clear(0)
        tp.glow(18f, alpha(color, 0.55f)) { text(text, w / 2f, h / 2f + size * 0.36f, size, -1, Fonts.display, spacing = 0.06f) }
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.typeface = Fonts.display
        tp.paint.textSize = size
        tp.paint.textAlign = Paint.Align.CENTER
        tp.paint.letterSpacing = 0.06f
        tp.paint.style = Paint.Style.STROKE
        tp.paint.strokeWidth = size * 0.08f
        tp.paint.color = lift(color, 0.35f)
        tp.canvas.drawText(text, w / 2f, h / 2f + size * 0.36f, tp.paint)
        tp.paint.strokeWidth = size * 0.03f
        tp.paint.color = lift(color, 0.85f)
        tp.canvas.drawText(text, w / 2f, h / 2f + size * 0.36f, tp.paint)
        return tp.toTexture().also { tp.recycle() }
    }

    /** A lit box sign: coloured panel with bold lettering. */
    fun lightbox(text: String, bg: Int, fg: Int, w: Int = 512, h: Int = 128, size: Float = 70f): Texture {
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(bg, 0.25f), dim(bg, 0.75f))
        tp.strokeRound(4f, 4f, w - 8f, h - 8f, 10f, 5f, alpha(-1, 0.5f))
        tp.outlinedText(text, w / 2f, h / 2f + size * 0.36f, size, fg, dim(bg, 0.35f), size * 0.12f, Fonts.display)
        return tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ prizes

    /** Printed packaging for the boxed prizes on the prize wall. */
    fun prizeBox(seed: Int): Texture {
        val tp = TexPaint(128, 128)
        val colors = intArrayOf(0xFFE8394A.toInt(), 0xFF2F6BFF.toInt(), 0xFF22C06A.toInt(), 0xFFFFB020.toInt(), 0xFF9B4DFF.toInt(), 0xFF00C2D8.toInt())
        val c = colors[seed % colors.size]
        tp.vgrad(0f, 0f, 128f, 128f, lift(c, 0.2f), dim(c, 0.7f))
        tp.round(12f, 20f, 104f, 70f, 10f, alpha(-1, 0.85f))
        val names = arrayOf("ROBOT", "RC CAR", "BLASTER", "DRONE", "YO-YO", "SLIME", "LASER", "PUZZLE")
        tp.text(names[seed % names.size], 64f, 66f, 22f, dim(c, 0.5f), Fonts.display)
        tp.ball(96f, 104f, 12f, colors[(seed + 2) % colors.size])
        tp.text("TOYS", 36f, 116f, 16f, -1, Fonts.condensed)
        return tp.toTexture().also { tp.recycle() }
    }

    val ticketStack: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.fill(0xFFFFA23C.toInt())
        for (y in 0 until 64 step 6) tp.rect(0f, y.toFloat(), 64f, 1.5f, 0xFFE07A20.toInt())
        tp.toTexture().also { tp.recycle() }
    }
}
