package com.pocketarcade.hub

import androidx.compose.ui.graphics.Color
import com.pocketarcade.data.Catalog
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.RasterPainter
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigText
import com.pocketarcade.engine.r3d.bigTextWidth
import com.pocketarcade.engine.r3d.vgrad
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame
import com.pocketarcade.games.claw.PlushArt
import kotlin.math.sin
import kotlin.math.sqrt

/** Texels per hall unit for every hall texture. */
const val TPU = 2

/** Procedural textures for the hall: floor, walls, signs and furniture, at [TPU] texels per unit. */
object HubTextures {

    // ------------------------------------------------------------------ floor

    fun floor(map: HubMap): Texture {
        val w = map.widthPx * TPU
        val h = map.heightPx * TPU
        val c = PixelCanvas(w, h)
        val carpet = Pal.DEEP
        c.fill(0, 0, w, h, carpet)
        val weave = Pal.mix(carpet, Pal.PLUM, 0.4f)
        for (y in 0 until h step 2) {
            var x = (y / 2) % 4
            while (x < w) {
                c.set(x, y, weave)
                x += 4
            }
        }

        // Neon blacklight carpet: squiggles, zigzags, rings, triangles and sparkles.
        val motif = intArrayOf(Pal.CYAN, Pal.PINK, Pal.YELLOW, Pal.LIME, Pal.ORANGE, Pal.PURPLE)
        for (ty in 3 until map.rows) for (tx in 1 until map.cols - 1) {
            repeat(2) { k ->
                val ox = tx * 32 + (hash01(tx, ty, 11 + k) * 20).toInt()
                val oy = ty * 32 + (hash01(tx, ty, 23 + k) * 20).toInt()
                val col = Pal.mix(motif[(hash01(tx, ty, 37 + k) * motif.size).toInt().coerceAtMost(motif.size - 1)], carpet, 0.3f)
                when ((hash01(tx, ty, 51 + k) * 5).toInt()) {
                    0 -> for (i in 0..11) {
                        val yy = oy + 3 + (sin(i * 0.8f) * 2.5f).toInt()
                        c.set(ox + i, yy, col); c.set(ox + i, yy + 1, col)
                    }
                    1 -> {
                        c.line(ox, oy + 5, ox + 3, oy, col); c.line(ox + 3, oy, ox + 6, oy + 5, col)
                        c.line(ox + 6, oy + 5, ox + 9, oy, col); c.line(ox + 9, oy, ox + 12, oy + 5, col)
                    }
                    2 -> c.ring(ox + 4.5f, oy + 4.5f, 4.5f, 1.5f, col)
                    3 -> {
                        c.line(ox + 4, oy, ox, oy + 7, col); c.line(ox + 4, oy, ox + 8, oy + 7, col); c.line(ox, oy + 7, ox + 8, oy + 7, col)
                    }
                    else -> {
                        c.fill(ox + 3, oy, 2, 8, col); c.fill(ox, oy + 3, 8, 2, col)
                        c.set(ox + 3, oy + 3, Pal.WHITE)
                    }
                }
            }
        }

        // Red carpet in front of the prize counter.
        val redBase = Pal.mix(Pal.DARKRED, carpet, 0.2f)
        val redHi = Pal.mix(Pal.DARKRED, Pal.RED, 0.35f)
        val rx0 = 16 * TPU
        val rz0 = HubLayout.WALL_Z.toInt() * TPU
        val rx1 = (map.widthPx - 16) * TPU
        val rz1 = 128 * TPU
        for (y in rz0 until rz1) for (x in rx0 until rx1) {
            val d1 = (x + y) % 24
            val d2 = ((x - y) % 24 + 24) % 24
            c.set(x, y, if (d1 == 0 || d2 == 0) redHi else redBase)
        }
        c.fill(rx0 + 4, rz1 - 10, rx1 - rx0 - 8, 3, Pal.GOLD)
        c.fill(rx0 + 4, rz0 + 4, 3, rz1 - rz0 - 12, Pal.GOLD)
        c.fill(rx1 - 7, rz0 + 4, 3, rz1 - rz0 - 12, Pal.GOLD)
        var sx = rx0 + 16
        while (sx < rx1 - 12) {
            c.fill(sx, rz1 - 11, 3, 5, Pal.YELLOW)
            sx += 24
        }

        // Checkered centre aisle with neon edges.
        val ax0 = HubLayout.AISLE_LEFT * TPU
        val ax1 = HubLayout.AISLE_RIGHT * TPU
        val az0 = 128 * TPU
        val az1 = map.entranceRow * HubLayout.TILE * TPU
        val dark = 0xFF151027.toInt()
        val light = 0xFF5E5688.toInt()
        for (y in az0 until az1) for (x in ax0 until ax1) {
            val lx = (x - ax0) % 16
            val ly = (y - az0) % 16
            val isLight = ((x - ax0) / 16 + (y - az0) / 16) % 2 == 1
            val v = if (isLight) {
                when {
                    lx == 0 || ly == 0 -> Pal.mix(light, Pal.WHITE, 0.18f)
                    lx == 15 || ly == 15 -> Pal.shade(light, 0.75f)
                    else -> light
                }
            } else dark
            c.set(x, y, v)
        }
        for (edge in intArrayOf(ax0 - 3, ax1 + 1)) {
            c.fill(edge, az0, 2, az1 - az0, Pal.PINK)
            c.fill(edge - 1, az0, 1, az1 - az0, Pal.mix(Pal.PINK, carpet, 0.6f))
            c.fill(edge + 2, az0, 1, az1 - az0, Pal.mix(Pal.PINK, carpet, 0.6f))
        }

        // Play mats in front of every cabinet, in the machine's glow colour.
        for (m in map.mats) {
            val x0 = ((m.centerX - 20f) * TPU).toInt()
            val y0 = ((m.frontZ + 1f) * TPU).toInt()
            val mw = 40 * TPU
            val mh = 26 * TPU
            c.fill(x0, y0, mw, mh, Pal.mix(m.glow, carpet, 0.78f))
            c.rect(x0, y0, mw, mh, Pal.mix(m.glow, carpet, 0.15f))
            c.rect(x0 + 1, y0 + 1, mw - 2, mh - 2, Pal.mix(m.glow, carpet, 0.45f))
            c.rect(x0 + 3, y0 + 3, mw - 6, mh - 6, Pal.mix(m.glow, carpet, 0.6f))
            val cx = x0 + mw / 2
            for (k in 0 until 3) {
                val cy = y0 + 12 + k * 12
                val col = Pal.mix(m.glow, carpet, 0.3f + k * 0.15f)
                for (i in 0 until 7) {
                    c.fill(cx - 7 + i, cy + 6 - i, 2, 2, col)
                    c.fill(cx + 5 - i, cy + 6 - i, 2, 2, col)
                }
            }
        }

        // Entrance tiles and the welcome mat.
        val ez0 = map.entranceRow * HubLayout.TILE * TPU
        val ez1 = (map.heightPx - 16) * TPU
        val tile = Pal.mix(Pal.TAN, carpet, 0.55f)
        for (y in ez0 until ez1) for (x in 32 until w - 32) {
            val lx = x % 32
            val ly = (y - ez0) % 32
            c.set(x, y, when {
                lx == 0 || ly == 0 -> Pal.shade(tile, 0.55f)
                lx <= 2 || ly <= 2 -> Pal.mix(tile, Pal.WHITE, 0.15f)
                lx >= 29 || ly >= 29 -> Pal.shade(tile, 0.8f)
                else -> tile
            })
        }
        val matX = 92 * TPU
        val matY = (map.heightPx - 38) * TPU
        c.fill(matX, matY, 80, 40, Pal.BROWN)
        c.rect(matX, matY, 80, 40, Pal.DARKBROWN)
        c.rect(matX + 3, matY + 3, 74, 34, Pal.shade(Pal.BROWN, 0.8f))
        val tw = bigTextWidth("WELCOME", 1)
        c.bigText("WELCOME", matX + (80 - tw) / 2, matY + 16, Pal.TAN, 1)
        return Texture.of(c)
    }

    // ------------------------------------------------------------------ walls

    /** Back wall, 192 x 76 units: panels, chair rail, prize shelves and posters. */
    val backWall: Texture by lazy {
        val w = 192 * TPU
        val h = HubLayout.WALL_HEIGHT.toInt() * TPU
        val c = PixelCanvas(w, h)
        wallBase(c, w, h, seamEvery = 64)
        // Shelves behind the counter, stocked with prizes from the claw machine.
        val shelfX0 = (64 - 16) * TPU
        val shelfX1 = (160 - 16) * TPU
        val plush = Catalog.plushies.filter { !it.rare }
        for ((s, height) in intArrayOf(38, 54).withIndex()) {
            val row = (HubLayout.WALL_HEIGHT.toInt() - height) * TPU
            var x = shelfX0 + 6
            var k = s * 3
            while (x + 18 < shelfX1) {
                val art = PlushArt.build(plush[k % plush.size])
                c.blit(art, x, row - 18)
                x += 20
                k++
            }
            c.fill(shelfX0, row, shelfX1 - shelfX0, 4, Pal.BROWN)
            c.fill(shelfX0, row, shelfX1 - shelfX0, 1, Pal.TAN)
            c.fill(shelfX0, row + 4, shelfX1 - shelfX0, 2, Pal.shade(Pal.PLUM, 0.5f))
        }
        poster(c, 12, 28, Pal.NAVY, Pal.CYAN, 0)
        poster(c, w - 12 - 64, 28, Pal.DARKRED, Pal.YELLOW, 1)
        Texture.of(c)
    }

    /** Side wall panel that repeats every 32 units along the hall. */
    val sideWall: Texture by lazy {
        val w = 32 * TPU
        val h = HubLayout.WALL_HEIGHT.toInt() * TPU
        val c = PixelCanvas(w, h)
        wallBase(c, w, h, seamEvery = 64)
        // A little framed arcade flyer on every panel.
        c.fill(18, 40, 28, 36, Pal.LIGHTGRAY)
        c.fill(20, 42, 24, 32, Pal.INDIGO)
        c.disc(32f, 54f, 6f, Pal.PINK)
        c.disc(32f, 54f, 3f, Pal.YELLOW)
        c.fill(24, 66, 16, 2, Pal.CYAN)
        Texture(w, h, c.px)
    }

    private fun wallBase(c: PixelCanvas, w: Int, h: Int, seamEvery: Int) {
        c.vgrad(0, 0, w, h, Pal.mix(Pal.NIGHT, Pal.PLUM, 0.35f), Pal.PLUM)
        val rail = h - 28 * TPU
        for (y in rail until h) for (x in 0 until w) {
            c.set(x, y, if ((x % 16) == 0) Pal.shade(Pal.PLUM, 0.6f) else Pal.shade(Pal.PLUM, 0.82f))
        }
        var x = 0
        while (x < w) {
            c.fill(x, 0, 2, rail, Pal.shade(Pal.PLUM, 0.55f))
            c.fill(x + 2, 0, 1, rail, Pal.mix(Pal.PLUM, Pal.WHITE, 0.12f))
            x += seamEvery
        }
        c.fill(0, rail - 2, w, 5, Pal.VIOLET)
        c.fill(0, rail - 2, w, 1, Pal.LAVENDER)
        c.fill(0, h - 10, w, 10, Pal.DEEP)
        c.fill(0, h - 10, w, 1, Pal.VIOLET)
    }

    private fun poster(c: PixelCanvas, x: Int, y: Int, bg: Int, fg: Int, art: Int) {
        c.fill(x, y, 64, 56, Pal.LIGHTGRAY)
        c.fill(x + 2, y + 2, 60, 52, bg)
        if (art == 0) {
            val rows = arrayOf(
                "..#.....#..",
                "...#...#...",
                "..#######..",
                ".##.###.##.",
                "###########",
                "#.#######.#",
                "#.#.....#.#",
                "...##.##...",
            )
            val inv = PixelCanvas(11, 8).apply { sprite(rows, 0, 0, mapOf('#' to fg)) }
            val big = com.pocketarcade.engine.SpriteFX.scale2x(com.pocketarcade.engine.SpriteFX.scale2x(inv))
            c.blit(big, x + 10, y + 8)
            c.bigText("1UP", x + 32 - bigTextWidth("1UP", 1) / 2, y + 44, Pal.WHITE, 1)
        } else {
            c.bigText("HI", x + 32 - bigTextWidth("HI", 2) / 2, y + 8, fg, 2)
            c.bigText("SCORE", x + 32 - bigTextWidth("SCORE", 1) / 2, y + 26, fg, 1)
            c.fill(x + 12, y + 37, 40, 2, Pal.WHITE)
            c.bigText("99999", x + 32 - bigTextWidth("99999", 1) / 2, y + 42, Pal.WHITE, 1)
        }
    }

    /** The neon "POCKET ARCADE" sign with a soft halo, for additive blending. */
    val sign: Texture by lazy {
        val text = "POCKET ARCADE"
        val scale = 2
        val tw = bigTextWidth(text, scale)
        val w = tw + 16
        val h = 7 * scale + 16
        val mask = PixelCanvas(w, h)
        mask.bigText(text, 8, 8, Pal.WHITE, scale)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (mask.px[y * w + x] != 0) {
                out[y * w + x] = if ((x + y) % 5 == 0) 0xFFFFD0F0.toInt() else Pal.HOTPINK
                continue
            }
            var best = 99f
            for (dy in -4..4) for (dx in -4..4) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && mask.px[yy * w + xx] != 0) {
                    val d = sqrt((dx * dx + dy * dy).toFloat())
                    if (d < best) best = d
                }
            }
            if (best < 4.5f) {
                val a = ((1f - best / 4.5f) * 150).toInt()
                out[y * w + x] = (a shl 24) or (Pal.PINK and 0xFFFFFF)
            }
        }
        Texture(w, h, out)
    }

    /** A thin horizontal neon tube (additive), repeating along its length. */
    val neonStrip: Texture by lazy {
        val alphas = intArrayOf(40, 110, 255, 255, 110, 40)
        Texture(8, alphas.size, IntArray(8 * alphas.size) { i -> (alphas[i / 8] shl 24) or (Pal.CYAN and 0xFFFFFF) })
    }

    val glow: Texture get() = TexKit.glow
    val shadow: Texture get() = TexKit.shadow
    val spark: Texture get() = TexKit.spark

    // ------------------------------------------------------------------ furniture

    val counterFront: Texture by lazy {
        val c = PixelCanvas(96 * TPU, 18 * TPU)
        c.vgrad(0, 0, c.w, c.h, Pal.DARKRED, Pal.shade(Pal.DARKRED, 0.6f))
        for (x in 0 until c.w step 12) c.fill(x, 4, 1, c.h - 8, Pal.shade(Pal.DARKRED, 0.5f))
        c.fill(0, 0, c.w, 3, Pal.GOLD)
        c.fill(0, c.h - 3, c.w, 3, Pal.GOLD)
        val tw = bigTextWidth("PRIZES", 2)
        c.bigText("PRIZES", (c.w - tw) / 2 + 1, 12, Pal.BLACK, 2)
        c.bigText("PRIZES", (c.w - tw) / 2, 11, Pal.GOLD, 2)
        for (sx in intArrayOf(18, c.w - 22)) {
            c.fill(sx, 14, 4, 10, Pal.YELLOW); c.fill(sx - 3, 17, 10, 4, Pal.YELLOW)
        }
        Texture.of(c)
    }

    val counterTop: Texture by lazy {
        val c = PixelCanvas(96 * TPU, 18 * TPU)
        c.fill(0, 0, c.w, c.h, Pal.CREAM)
        for (y in 3 until c.h step 5) for (x in 0 until c.w) {
            if (hash01(x / 7, y, 3) > 0.35f) c.set(x, y, Pal.mix(Pal.CREAM, Pal.TAN, 0.5f))
        }
        c.fill(0, c.h - 3, c.w, 3, Pal.TAN)
        Texture.of(c)
    }

    val counterSide: Texture by lazy { solid(36, 36, Pal.shade(Pal.DARKRED, 0.7f)) }

    val glassCaseFront: Texture by lazy {
        val c = PixelCanvas(38 * TPU, 12 * TPU)
        c.fill(0, 0, c.w, c.h, Pal.NAVY)
        val plush = Catalog.plushies
        for (i in 0 until 4) c.blit(PlushArt.build(plush[(i * 3) % plush.size]), 2 + i * 18, 4)
        c.line(8, 0, 20, c.h - 1, Pal.mix(Pal.NAVY, Pal.WHITE, 0.45f))
        c.line(12, 0, 24, c.h - 1, Pal.mix(Pal.NAVY, Pal.WHITE, 0.25f))
        c.rect(0, 0, c.w, c.h, Pal.LIGHTGRAY)
        Texture.of(c)
    }

    val glassCaseTop: Texture by lazy {
        val c = PixelCanvas(38 * TPU, 12 * TPU)
        c.fill(0, 0, c.w, c.h, Pal.mix(Pal.NAVY, Pal.SKY, 0.35f))
        c.line(0, c.h - 1, c.w / 2, 0, Pal.mix(Pal.SKY, Pal.WHITE, 0.5f))
        c.rect(0, 0, c.w, c.h, Pal.LIGHTGRAY)
        Texture.of(c)
    }

    val tokenFront: Texture by lazy {
        val c = PixelCanvas(22 * TPU, 34 * TPU)
        c.vgrad(0, 0, c.w, c.h, Pal.YELLOW, Pal.shade(Pal.GOLD, 0.8f))
        c.fill(0, 0, 3, c.h, Pal.shade(Pal.GOLD, 0.7f))
        c.fill(c.w - 3, 0, 3, c.h, Pal.shade(Pal.GOLD, 0.7f))
        c.fill(6, 6, 32, 18, Pal.BLACK)
        c.fill(12, 30, 20, 4, Pal.DARKGRAY)
        c.fill(14, 31, 16, 1, Pal.BLACK)
        c.disc(22f, 42f, 5f, Pal.RED)
        c.disc(21f, 41f, 2f, Pal.HOTPINK)
        c.fill(8, 52, 28, 10, Pal.BLACK)
        for (i in 0 until 5) c.disc(12f + i * 5f, 58f, 2f, Pal.GOLD)
        c.fill(0, c.h - 4, c.w, 4, Pal.shade(Pal.GOLD, 0.45f))
        Texture.of(c)
    }

    val tokenSign: Texture by lazy {
        val c = PixelCanvas(22 * TPU, 8 * TPU)
        c.fill(0, 0, c.w, c.h, Pal.RED)
        c.fill(0, 0, c.w, 2, Pal.HOTPINK)
        val tw = bigTextWidth("TOKENS", 1)
        c.bigText("TOKENS", (c.w - tw) / 2, 5, Pal.YELLOW, 1)
        Texture.of(c)
    }

    val goldSide: Texture by lazy { solid(24, 68, Pal.shade(Pal.GOLD, 0.75f)) }

    val sodaFront: Texture by lazy {
        val c = PixelCanvas(22 * TPU, 42 * TPU)
        c.vgrad(0, 0, c.w, c.h, Pal.RED, Pal.DARKRED)
        for (x in 0 until c.w) {
            val y = 14 + (sin(x * 0.2f) * 3f).toInt()
            c.fill(x, y, 1, 3, Pal.WHITE)
        }
        val tw = bigTextWidth("SODA", 1)
        c.bigText("SODA", (c.w - tw) / 2, 4, Pal.WHITE, 1)
        c.fill(6, 66, 20, 8, Pal.BLACK)
        c.fill(32, 24, 6, 12, Pal.DARKGRAY)
        c.fill(34, 26, 2, 4, Pal.GOLD)
        Texture.of(c)
    }

    /** The lit can panel of the soda machine (drawn emissive). */
    val sodaPanel: Texture by lazy {
        val c = PixelCanvas(12 * TPU, 20 * TPU)
        c.fill(0, 0, c.w, c.h, Pal.mix(Pal.SKY, Pal.WHITE, 0.3f))
        val cans = intArrayOf(Pal.LIME, Pal.ORANGE, Pal.SKY, Pal.YELLOW, Pal.PINK)
        for (row in 0 until 4) for (col in 0 until 3) {
            val x = 2 + col * 7
            val y = 2 + row * 9
            c.fill(x, y, 5, 8, cans[(row + col) % cans.size])
            c.fill(x + 1, y + 1, 1, 6, Pal.WHITE)
        }
        c.line(0, c.h - 1, c.w - 1, 0, Pal.WHITE)
        Texture.of(c)
    }

    val redSide: Texture by lazy { solid(24, 84, Pal.shade(Pal.RED, 0.7f)) }

    val wood: Texture by lazy {
        val c = PixelCanvas(32, 16)
        c.fill(0, 0, 32, 16, Pal.WOOD)
        for (y in 0 until 16 step 4) c.fill(0, y, 32, 1, Pal.shade(Pal.WOOD, 0.75f))
        c.fill(0, 1, 32, 1, Pal.mix(Pal.WOOD, Pal.WHITE, 0.2f))
        Texture(32, 16, c.px)
    }

    val darkMetal: Texture by lazy { solid(8, 8, Pal.DARKGRAY) }

    val trashSide: Texture by lazy {
        val c = PixelCanvas(32, 28)
        c.vgrad(0, 0, 32, 28, Pal.LIGHTGRAY, Pal.GRAY)
        for (x in 0 until 32 step 4) c.fill(x, 3, 1, 22, Pal.DARKGRAY)
        Texture.of(c)
    }

    val trashLid: Texture by lazy { solid(16, 16, Pal.LIGHTGRAY) }

    val entranceWall: Texture by lazy {
        val c = PixelCanvas(64, 20)
        c.vgrad(0, 0, 64, 20, Pal.PLUM, Pal.NIGHT)
        c.fill(0, 0, 64, 2, Pal.VIOLET)
        Texture(64, 20, c.px)
    }

    fun solid(w: Int, h: Int, color: Int) = TexKit.solid(w, h, color)
}

/**
 * Everything a machine's cabinet looks like: static faces plus a live marquee (chasing bulbs)
 * and a live screen texture that the game's attract mode paints every frame.
 */
class CabinetSkin(val look: CabinetLook, private val marqueeText: String) {
    val shape: CabinetShape = look.shape
    private val body = look.body
    private val trim = look.trim
    private val glow = look.glow

    /** Attract-screen size in painter units (2 texels each). */
    val screenW: Int = when (shape) {
        CabinetShape.UPRIGHT -> 22
        CabinetShape.WIDE -> 38
        CabinetShape.LANE -> 22
        CabinetShape.TABLE -> 30
    }
    val screenH: Int = when (shape) {
        CabinetShape.UPRIGHT -> 16
        CabinetShape.WIDE -> 22
        CabinetShape.LANE -> 13
        CabinetShape.TABLE -> 14
    }
    val screen = Texture(screenW * TPU, screenH * TPU)

    private val innerW = when (shape) {
        CabinetShape.UPRIGHT -> 26
        CabinetShape.WIDE -> 42
        CabinetShape.LANE -> 32
        CabinetShape.TABLE -> 32
    }
    private val marqueeH = if (shape == CabinetShape.UPRIGHT) 8 else 10
    val marquee = Texture(innerW * TPU, marqueeH * TPU)
    private val marqueeBase: IntArray
    private val bulbXs: IntArray

    val sideArt: Texture
    val sideEdge: Texture = HubTextures.solid(4, 32, Pal.mix(trim, Pal.WHITE, 0.15f))
    val top: Texture
    val lowerFront: Texture
    val panelTop: Texture
    val bezel: Texture
    val laneTex: Texture?
    val railTex: Texture?
    val consoleFront: Texture?
    val backboard: Texture?
    /** Playing surface of a table machine. */
    val tableTop: Texture?
    /** Faint reflections for the glass front of wide machines (alpha-blended). */
    val glassShine: Texture by lazy {
        val w = innerW * TPU
        val h = 26 * TPU
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val d = (x + y) % 40
            px[y * w + x] = if (d < 3) 0x50FFFFFF else if (d < 5) 0x20FFFFFF else 0
        }
        Texture(w, h, px)
    }

    init {
        // Marquee: gradient, bolted ends, title in the body colour with a light shadow.
        val m = PixelCanvas(marquee.width, marquee.height)
        m.vgrad(0, 0, m.w, m.h, Pal.mix(trim, Pal.WHITE, 0.35f), Pal.shade(trim, 0.8f))
        val scale = if (shape != CabinetShape.UPRIGHT && bigTextWidth(marqueeText, 2) <= m.w - 6) 2 else 1
        val tw = bigTextWidth(marqueeText, scale)
        val ty = (m.h - 7 * scale) / 2
        m.bigText(marqueeText, (m.w - tw) / 2 + 1, ty + 1, Pal.mix(trim, Pal.WHITE, 0.6f), scale)
        m.bigText(marqueeText, (m.w - tw) / 2, ty, Pal.shade(body, 0.35f), scale)
        m.fill(1, m.h / 2 - 1, 2, 2, Pal.shade(trim, 0.5f))
        m.fill(m.w - 3, m.h / 2 - 1, 2, 2, Pal.shade(trim, 0.5f))
        System.arraycopy(m.px, 0, marquee.pixels, 0, m.px.size)
        marqueeBase = m.px.copyOf()
        bulbXs = IntArray((m.w - 4) / 6) { 3 + it * 6 }

        val depth = HubLayout.cabinetSize(shape).second.toInt()
        val height = HubLayout.cabinetSize(shape).third.toInt()
        sideArt = PixelCanvas(depth * TPU, height * TPU).let { s ->
            s.vgrad(0, 0, s.w, s.h, Pal.shade(body, 0.95f), Pal.shade(body, 0.6f))
            // A diagonal trim stripe and a glowing star decal.
            for (y in 0 until s.h) {
                val x = (s.w - 1) - (y * s.w / s.h)
                s.fill(x - 3, y, 6, 1, trim)
            }
            s.disc(s.w * 0.4f, s.h * 0.35f, s.w * 0.18f, Pal.mix(glow, body, 0.3f))
            s.disc(s.w * 0.4f, s.h * 0.35f, s.w * 0.08f, Pal.mix(glow, Pal.WHITE, 0.4f))
            s.fill(s.w - 3, 0, 3, s.h, Pal.mix(glow, body, 0.4f))
            Texture.of(s)
        }
        top = PixelCanvas(innerW * TPU, depth * TPU).let { t ->
            t.fill(0, 0, t.w, t.h, Pal.shade(body, 0.45f))
            for (y in 6 until t.h - 4 step 4) t.fill(6, y, t.w - 12, 1, Pal.shade(body, 0.25f))
            Texture.of(t)
        }
        val lowerH = if (shape == CabinetShape.WIDE) 20 else 22
        lowerFront = PixelCanvas(innerW * TPU, lowerH * TPU).let { f ->
            f.vgrad(0, 0, f.w, f.h, body, Pal.shade(body, 0.75f))
            f.fill(0, 4, f.w, 2, trim)
            f.fill(0, f.h - 6, f.w, 6, Pal.shade(body, 0.35f))
            if (shape == CabinetShape.WIDE) {
                // Prize door on the left, coin door on the right.
                f.fill(8, 10, 30, 22, Pal.BLACK)
                f.rect(7, 9, 32, 24, trim)
                f.fill(10, 12, 26, 3, Pal.shade(body, 0.5f))
                f.bigText("PRIZE", 23 - bigTextWidth("PRIZE", 1, tiny = true) / 2, 18, Pal.mix(trim, Pal.WHITE, 0.3f), 1, tiny = true)
            }
            val dx = if (shape == CabinetShape.WIDE) f.w - 34 else f.w / 2 - 12
            f.fill(dx, 10, 24, 22, Pal.shade(body, 0.3f))
            f.rect(dx, 10, 24, 22, trim)
            f.fill(dx + 6, 16, 2, 8, Pal.GOLD)
            f.fill(dx + 16, 16, 2, 8, Pal.GOLD)
            f.fill(dx + 4, 12, 6, 3, Pal.RED)
            f.fill(dx + 14, 12, 6, 3, Pal.RED)
            f.set(dx + 5, 12, Pal.HOTPINK)
            f.set(dx + 15, 12, Pal.HOTPINK)
            Texture.of(f)
        }
        panelTop = PixelCanvas(innerW * TPU, depth * TPU).let { p ->
            p.fill(0, 0, p.w, p.h, Pal.shade(body, 0.3f))
            p.fill(0, p.h - 3, p.w, 3, trim)
            val y = p.h - 10
            p.fill(10, y - 2, 2, 6, Pal.BLACK)
            p.disc(11f, y - 3f, 3.5f, Pal.RED)
            p.set(10, y - 5, Pal.WHITE)
            val colors = intArrayOf(Pal.YELLOW, Pal.CYAN, Pal.PINK)
            for (i in colors.indices) {
                val bx = 24f + i * 9f
                p.disc(bx, y.toFloat(), 3f, Pal.shade(colors[i], 0.6f))
                p.disc(bx, y - 1f, 2.5f, colors[i])
                p.set(bx.toInt() - 1, y - 2, Pal.WHITE)
            }
            Texture.of(p)
        }
        val bezelH = if (shape == CabinetShape.WIDE) 26 else 22
        bezel = PixelCanvas(innerW * TPU, bezelH * TPU).let { b ->
            b.vgrad(0, 0, b.w, b.h, Pal.shade(body, 0.55f), Pal.shade(body, 0.35f))
            b.fill(4, 3, b.w - 8, b.h - 6, Pal.BLACK)
            b.fill(4, 3, b.w - 8, 1, Pal.mix(trim, Pal.WHITE, 0.3f))
            for (x in 10 until b.w - 10 step 4) b.set(x, b.h - 2, Pal.shade(body, 0.2f))
            Texture.of(b)
        }
        if (shape == CabinetShape.LANE) {
            laneTex = PixelCanvas(24 * TPU, 44 * TPU).let { l ->
                l.fill(0, 0, l.w, l.h, Pal.WOOD)
                for (x in 0 until l.w step 8) l.fill(x, 0, 1, l.h, Pal.shade(Pal.WOOD, 0.8f))
                for (i in 0 until 12) {
                    val y = (hash01(i, 7) * l.h).toInt()
                    val x = (hash01(i, 9) * (l.w - 10)).toInt()
                    l.fill(x, y, 8, 1, Pal.shade(Pal.WOOD, 0.85f))
                }
                for (k in 0 until 3) {
                    val y = 30 + k * 12
                    for (i in 0 until 6) {
                        l.fill(l.w / 2 - 6 + i, y + 6 - i, 2, 2, Pal.CREAM)
                        l.fill(l.w / 2 + 4 - i, y + 6 - i, 2, 2, Pal.CREAM)
                    }
                }
                l.fill(0, 0, l.w, 10, Pal.shade(body, 0.35f))
                l.disc(l.w / 2f, 5f, 4f, Pal.BLACK)
                Texture.of(l)
            }
            railTex = HubTextures.solid(8, 32, Pal.mix(body, trim, 0.2f))
            consoleFront = PixelCanvas(32 * TPU, 14 * TPU).let { f ->
                f.vgrad(0, 0, f.w, f.h, body, Pal.shade(body, 0.7f))
                f.fill(0, 3, f.w, 2, trim)
                f.fill(18, 8, 28, 10, Pal.BLACK)
                f.bigText("000", 32 - bigTextWidth("000", 1) / 2, 9, Pal.RED, 1)
                f.fill(16, 21, 32, 4, Pal.BLACK)
                Texture.of(f)
            }
            backboard = PixelCanvas(32 * TPU, 40 * TPU).let { b ->
                b.vgrad(0, 0, b.w, b.h, Pal.mix(trim, Pal.WHITE, 0.2f), Pal.shade(trim, 0.7f))
                b.fill(4, 4, b.w - 8, b.h - 8, Pal.shade(body, 0.35f))
                // Screen window (the live screen quad sits here) and a target below it.
                b.fill(8, 4, b.w - 16, 30, Pal.BLACK)
                val cx = b.w / 2f
                val cy = b.h - 22f
                b.disc(cx, cy, 16f, Pal.shade(glow, 0.5f))
                b.disc(cx, cy, 11f, Pal.shade(trim, 0.7f))
                b.disc(cx, cy, 6f, Pal.shade(glow, 0.8f))
                b.disc(cx, cy, 3f, Pal.BLACK)
                Texture.of(b)
            }
        } else {
            laneTex = null
            railTex = if (shape == CabinetShape.TABLE) HubTextures.solid(8, 32, Pal.mix(body, Pal.WHITE, 0.25f)) else null
            consoleFront = null
            backboard = null
        }
        tableTop = if (shape == CabinetShape.TABLE) {
            PixelCanvas(34 * TPU, 46 * TPU).let { t ->
                t.vgrad(0, 0, t.w, t.h, Pal.mix(Pal.WHITE, glow, 0.2f), Pal.mix(Pal.WHITE, glow, 0.35f))
                for (y in 3 until t.h step 6) for (x in 3 until t.w step 6) t.set(x, y, Pal.mix(glow, body, 0.5f))
                t.fill(0, t.h / 2 - 1, t.w, 2, Pal.RED)
                t.ring(t.w / 2f, t.h / 2f, 12f, 1.5f, Pal.RED)
                for (end in 0..1) {
                    val y = if (end == 0) 0 else t.h - 3
                    t.fill(t.w / 2 - 14, y, 28, 3, Pal.BLACK)
                    t.ring(t.w / 2f, if (end == 0) 0f else t.h.toFloat(), 18f, 1.5f, Pal.BLUE)
                }
                t.disc(t.w * 0.3f, t.h * 0.7f, 5f, Pal.shade(body, 0.8f))
                t.disc(t.w * 0.62f, t.h * 0.3f, 3f, Pal.RED)
                t.rect(0, 0, t.w, t.h, Pal.shade(body, 0.7f))
                Texture.of(t)
            }
        } else {
            null
        }
    }

    /** Redraws the marquee's chasing bulbs for time [t]. */
    fun updateMarquee(t: Float) {
        System.arraycopy(marqueeBase, 0, marquee.pixels, 0, marqueeBase.size)
        val phase = (t * 7f).toInt()
        val w = marquee.width
        val h = marquee.height
        for (i in bulbXs.indices) {
            val x = bulbXs[i]
            val on = (phase + i) % 3 == 0
            val c = if (on) Pal.YELLOW else Pal.shade(trim, 0.5f)
            val c2 = if (on) Pal.WHITE else Pal.shade(trim, 0.6f)
            for (yy in intArrayOf(0, h - 2)) {
                marquee.pixels[yy * w + x] = c
                marquee.pixels[yy * w + x + 1] = c
                marquee.pixels[(yy + 1) * w + x] = c
                marquee.pixels[(yy + 1) * w + x + 1] = c2
            }
        }
    }

    /**
     * Repaints the live screen: the game's attract mode, a "HI score" card every few seconds,
     * or static noise for a "coming soon" cabinet ([game] null). Also advances the marquee.
     */
    fun paint(game: MiniGame?, highScore: Int, t: Float, seed: Int, painter: RasterPainter) {
        updateMarquee(t + seed * 0.37f)
        val p = painter.begin(screen.full, TPU.toFloat())
        if (game == null) {
            p.fill(0, 0, screenW, screenH, Color(Pal.BLACK))
            val frameNo = (t * 12f).toInt()
            for (k in 0 until 5) {
                val row = (hash01(frameNo, k, 5) * screenH).toInt()
                p.fill(0f, row.toFloat(), screenW.toFloat(), 1f, Color(Pal.GRAY), 0.5f)
            }
            if ((t * 1.5f).toInt() % 2 == 0) p.textCentered("SOON", screenW / 2f, screenH / 2f - 2.5f, Color(Pal.LIGHTGRAY), tiny = true)
        } else if ((t + seed * 1.7f) % 8f > 6.2f) {
            p.fill(0, 0, screenW, screenH, Color(Pal.BLACK))
            p.textCentered("HI", screenW / 2f, 1f, Color(Pal.YELLOW), tiny = true)
            p.textCentered(highScore.toString(), screenW / 2f, 8f, Color.White, tiny = true)
        } else {
            game.drawAttract(p, screenW, screenH, t + seed * 3.1f)
        }
        finishScreen()
    }

    /** CRT scanlines and a glass glint over the freshly painted screen. */
    fun finishScreen() {
        val px = screen.pixels
        val w = screen.width
        for (y in 1 until screen.height step 2) {
            val row = y * w
            for (x in 0 until w) {
                val v = px[row + x]
                px[row + x] = Pal.shade(v, 0.78f)
            }
        }
        for (i in 0 until 3) px[(1) * w + 2 + i] = Pal.mix(px[w + 2 + i], Pal.WHITE, 0.6f)
    }
}
