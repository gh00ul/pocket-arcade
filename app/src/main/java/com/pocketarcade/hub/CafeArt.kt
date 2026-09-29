package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Models for the café: the tiled floor and lighting rig, the back bar with its machines and lit
 * menu, the service counter with the pastry case, slushie tanks and till, diner booths and
 * chairs. Textures are painted on first use (render side only).
 */
object CafeArt {
    private val xf = Xform()

    // Café palette: warm cream and mint with cherry red, under the hall's neon.
    private const val CREAM = 0xFFF2E6CE.toInt()
    private const val MINT = 0xFF5FC9B0.toInt()
    private const val CHERRY = 0xFFD8283E.toInt()
    private const val WARM = 0xFFFFD9A0.toInt()

    fun build(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        when (p.kind) {
            PropKind.CAFE_FLOOR -> floorAndRig(b, lights)
            PropKind.CAFE_BAR -> backBar(b, p)
            PropKind.CAFE_COUNTER -> counter(b, p)
            PropKind.BOOTH -> booth(b, p)
            PropKind.CHAIR -> chair(b, p)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ textures

    /** Diner floor tiles: cream and mint checks with grout. */
    private val tiles: Texture by lazy {
        val n = 128
        val tp = TexPaint(n, n)
        val s = n / 2f
        for (y in 0 until 2) for (x in 0 until 2) {
            // A shade down from the counter's cream and mint: a floor this big and this pale,
            // lit from above, crosses the bloom threshold and glows white like a sign.
            val base = if ((x + y) % 2 == 0) dim(CREAM, 0.7f) else dim(MINT, 0.62f)
            tp.vgrad(x * s, y * s, s, s, lift(base, 0.06f), dim(base, 0.9f))
        }
        for (k in 0..2) {
            tp.rect(k * s - 1f, 0f, 2f, n.toFloat(), 0xFF6A5A48.toInt())
            tp.rect(0f, k * s - 1f, n.toFloat(), 2f, 0xFF6A5A48.toInt())
        }
        tp.grain(0.05f, 11)
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    /** The lit menu board: three columns of treats with ticket prices. */
    private val menu: Texture by lazy {
        val w = 768
        val h = 256
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2A1E2E.toInt(), 0xFF140E18.toInt())
        tp.strokeRound(6f, 6f, w - 12f, h - 12f, 14f, 5f, WARM)
        tp.glowText("~ SNACK BAR MENU ~", w / 2f, 44f, 34f, 0xFFFFF2D0.toInt(), 0xFFFF8A3D.toInt(), 8f, Fonts.display, 0.08f)
        val cols = arrayOf(
            arrayOf("DRINKS", "SLUSHIE", "3", "COCOA", "2", "SODA", "2", "LATTE", "4"),
            arrayOf("TREATS", "DONUT", "2", "CUPCAKE", "3", "COOKIE", "1", "SOFT SERVE", "3"),
            arrayOf("HOT BITES", "PIZZA", "4", "HOT DOG", "3", "NACHOS", "3", "PRETZEL", "2"),
        )
        val heads = intArrayOf(0xFF39E6F2.toInt(), 0xFFFF77C8.toInt(), 0xFFFFC83D.toInt())
        val cw = (w - 40f) / 3f
        for (c in 0 until 3) {
            val x0 = 20f + c * cw
            if (c > 0) tp.rect(x0 - 2f, 64f, 2f, h - 84f, alpha(WARM, 0.35f))
            tp.glowText(cols[c][0], x0 + cw / 2f, 88f, 26f, heads[c], heads[c], 5f, Fonts.display, 0.06f)
            for (k in 0 until 4) {
                val y = 126f + k * 34f
                tp.text(cols[c][1 + k * 2], x0 + 14f, y, 22f, 0xFFF4ECDC.toInt(), Fonts.condensed, android.graphics.Paint.Align.LEFT)
                // The price, in tickets: a little orange ticket with the number on it.
                tp.round(x0 + cw - 58f, y - 20f, 44f, 24f, 5f, 0xFFFF9A3C.toInt())
                tp.circle(x0 + cw - 58f, y - 8f, 4f, 0xFF140E18.toInt())
                tp.circle(x0 + cw - 14f, y - 8f, 4f, 0xFF140E18.toInt())
                tp.text(cols[c][2 + k * 2], x0 + cw - 36f, y - 1f, 20f, 0xFF2A1400.toInt(), Fonts.display)
            }
        }
        tp.text("PRICES IN TICKETS  •  ASK ABOUT TODAY'S FLAVOUR", w / 2f, h - 18f, 15f, alpha(WARM, 0.8f), Fonts.condensed)
        tp.toTexture().also { tp.recycle() }
    }

    /** Beadboard front of the service counter. */
    private val counterFront: Texture by lazy {
        val tp = TexPaint(512, 112)
        tp.vgrad(0f, 0f, 512f, 112f, lift(MINT, 0.1f), dim(MINT, 0.7f))
        for (x in 0 until 512 step 16) tp.rect(x.toFloat(), 8f, 2f, 92f, alpha(0xFF000000.toInt(), 0.18f))
        tp.rect(0f, 0f, 512f, 8f, CREAM)
        tp.rect(0f, 100f, 512f, 12f, 0xFF2A2A30.toInt())
        tp.grain(0.03f, 21)
        tp.toTexture().also { tp.recycle() }
    }

    /** Under-counter fridges along the back bar: lit glass doors full of bottles. */
    private val barFront: Texture by lazy {
        val tp = TexPaint(512, 112)
        tp.fill(0xFF3A2A22.toInt())
        val bottles = intArrayOf(0xFFFF3D6E.toInt(), 0xFF2FB8FF.toInt(), 0xFF7CF25A.toInt(), 0xFFFFC83D.toInt(), 0xFFB070FF.toInt(), 0xFFFF8A3D.toInt())
        for (d in 0 until 4) {
            val x0 = 8f + d * 126f
            tp.round(x0, 8f, 118f, 96f, 6f, 0xFFB8C0CC.toInt())
            tp.vgrad(x0 + 5f, 13f, 108f, 86f, 0xFFE8F4FF.toInt(), 0xFFB8D8F0.toInt())
            for (row in 0 until 2) {
                tp.rect(x0 + 5f, 13f + (row + 1) * 43f - 3f, 108f, 3f, 0xFF8A94A4.toInt())
                for (k in 0 until 7) {
                    val bx = x0 + 12f + k * 15f
                    val by = 13f + row * 43f + 10f
                    tp.round(bx, by, 9f, 28f, 3f, bottles[(k + row * 3 + d) % bottles.size])
                    tp.rect(bx + 2.5f, by - 5f, 4f, 6f, 0xFFE8E8E8.toInt())
                }
            }
            tp.rect(x0 + 108f, 40f, 4f, 30f, 0xFFD8DCE4.toInt())
        }
        tp.toTexture().also { tp.recycle() }
    }

    /** Red-and-white stripes with a scalloped bottom edge, for the fascia over the menu. */
    private val awning: Texture by lazy {
        val tp = TexPaint(256, 64)
        tp.clear(0)
        for (k in 0 until 16) tp.rect(k * 16f, 0f, 16f, 48f, if (k % 2 == 0) CHERRY else 0xFFF8F0E4.toInt())
        for (k in 0 until 16) tp.circle(k * 16f + 8f, 48f, 8f, if (k % 2 == 0) CHERRY else 0xFFF8F0E4.toInt())
        tp.rect(0f, 0f, 256f, 4f, alpha(0xFF000000.toInt(), 0.25f))
        tp.toTexture().also { tp.recycle() }
    }

    /** Diner vinyl: cherry red with tuck lines. */
    private val vinyl: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, lift(CHERRY, 0.18f), dim(CHERRY, 0.72f))
        for (x in 0 until 64 step 16) tp.rect(x.toFloat(), 0f, 1.5f, 64f, alpha(0xFF000000.toInt(), 0.25f))
        tp.toTexture().also { tp.recycle() }
    }

    /** Speckled cream laminate for table tops. */
    private val laminate: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.fill(0xFFF4ECDC.toInt())
        for (k in 0 until 60) {
            val c = intArrayOf(0xFFFF77C8.toInt(), 0xFF39B8E6.toInt(), 0xFFFFC83D.toInt())[k % 3]
            tp.rect(hash01(k, 1) * 62f, hash01(k, 2) * 62f, 2f, 1f, alpha(c, 0.7f))
        }
        tp.toTexture().also { tp.recycle() }
    }

    private val icingPink: Texture by lazy { sprinkles(0xFFFF8EC0.toInt()) }
    private val icingChoc: Texture by lazy { sprinkles(0xFF5A321E.toInt()) }

    private fun sprinkles(base: Int): Texture {
        val tp = TexPaint(64, 32)
        tp.fill(base)
        val cs = intArrayOf(0xFFFFFFFF.toInt(), 0xFF39E6F2.toInt(), 0xFFFFE14D.toInt(), 0xFF7CF25A.toInt())
        for (k in 0 until 40) tp.rect(hash01(k, 7) * 62f, hash01(k, 8) * 30f, 2.5f, 1f, cs[k % cs.size])
        return tp.toTexture().also { tp.recycle() }
    }

    private val pizza: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.fill(0xFFFFC94A.toInt())
        for (k in 0 until 8) tp.circle(10f + hash01(k, 3) * 44f, 10f + hash01(k, 4) * 44f, 5f, 0xFFC0322A.toInt())
        tp.grain(0.08f, 3)
        tp.toTexture().also { tp.recycle() }
    }

    private val cookie: Texture by lazy {
        val tp = TexPaint(32, 32)
        tp.fill(0xFFD8A060.toInt())
        for (k in 0 until 7) tp.circle(4f + hash01(k, 5) * 24f, 4f + hash01(k, 6) * 24f, 2.2f, 0xFF3A2010.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** The till's customer display. */
    private val tillScreen: Texture by lazy {
        val tp = TexPaint(128, 48)
        tp.fill(0xFF0A1A10.toInt())
        tp.glowText("THANKS!", 64f, 32f, 24f, 0xFF9CFFB0.toInt(), 0xFF2AE06A.toInt(), 4f, Fonts.display)
        tp.toTexture().also { tp.recycle() }
    }

    /** Soft-serve machine front: lit header, nozzles panel. */
    private val softServe: Texture by lazy {
        val tp = TexPaint(128, 160)
        tp.vgrad(0f, 0f, 128f, 160f, 0xFFE8ECF4.toInt(), 0xFF9AA0B4.toInt())
        tp.round(6f, 6f, 116f, 40f, 6f, 0xFFFF77C8.toInt())
        tp.outlinedText("SOFT", 64f, 23f, 18f, -1, 0xFF8A1A50.toInt(), 3f, Fonts.display)
        tp.outlinedText("SERVE", 64f, 42f, 18f, -1, 0xFF8A1A50.toInt(), 3f, Fonts.display)
        for (k in 0 until 3) tp.round(20f + k * 34f, 70f, 20f, 14f, 3f, 0xFF2A2A34.toInt())
        tp.rect(0f, 150f, 128f, 10f, 0xFF4A4C58.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** Espresso machine front: dark panel with a pressure gauge. */
    private val espresso: Texture by lazy {
        val tp = TexPaint(128, 96)
        tp.vgrad(0f, 0f, 128f, 96f, 0xFF3A3C46.toInt(), 0xFF16161C.toInt())
        tp.circle(64f, 26f, 14f, 0xFFE8E4D8.toInt())
        tp.ring(64f, 26f, 14f, 2.5f, 0xFFC8CAD6.toInt())
        tp.line(64f, 26f, 72f, 18f, 2f, CHERRY)
        tp.outlinedText("ESPRESSO", 64f, 88f, 13f, 0xFFFFD9A0.toInt(), 0xFF000000.toInt(), 2f, Fonts.condensed)
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ floor and lights

    private fun floorAndRig(b: ModelBuilder, lights: MutableList<PointLight>) {
        val x0 = CafeLayout.FLOOR_X0
        val x1 = CafeLayout.FLOOR_X1
        val z0 = CafeLayout.FLOOR_Z0
        val z1 = CafeLayout.FLOOR_Z1
        // One check every 12 units.
        val tpu = 128f / 24f
        b.quad(x0, 0.06f, z0, x1, 0.06f, z0, x1, 0.06f, z1, x0, 0.06f, z1, tiles.region(wrap = true), 0f, 1f, 0f, u0 = x0 * tpu, v0 = z0 * tpu, u1 = x1 * tpu, v1 = z1 * tpu, gloss = 0.3f)
        // A brass edge where the tiles meet the carpet.
        val brass = HallArt.paint(0xFFE8B84A.toInt(), 0.35f, 0.8f).full
        b.quad(x1 - 1.5f, 0.1f, z0, x1, 0.1f, z0, x1, 0.1f, z1, x1 - 1.5f, 0.1f, z1, brass, 0f, 1f, 0f, gloss = 1f)
        b.quad(x0, 0.1f, z1 - 1.5f, x1, 0.1f, z1 - 1.5f, x1, 0.1f, z1, x0, 0.1f, z1, brass, 0f, 1f, 0f, gloss = 1f)

        // Pendant lamps over the tables and booths: a cord up into the dark, a coloured shade,
        // a glowing bulb and a warm pool on the table top.
        val cord = HallArt.solid(0xFF16161C.toInt()).full
        val shadeColors = intArrayOf(CHERRY, MINT, 0xFFFFC83D.toInt())
        val t = CafeLayout.TABLES
        for (i in 0 until t.size / 2 + CafeLayout.BOOTHS.size) {
            val px: Float
            val pz: Float
            val topY: Float
            if (i < t.size / 2) {
                px = t[i * 2]; pz = t[i * 2 + 1]; topY = 24f
            } else {
                val z = CafeLayout.BOOTHS[i - t.size / 2]
                px = (CafeLayout.BOOTH_X0 + CafeLayout.BOOTH_X1) / 2f; pz = z + CafeLayout.BOOTH_D / 2f; topY = 20f
            }
            val y = 68f
            b.capsule(px, y + 7f, pz, px, 140f, pz, 0.3f, cord, slices = 4)
            val shade = HallArt.paint(shadeColors[i % shadeColors.size], 0.25f, 0.7f).full
            b.lathe(px, y, pz, floatArrayOf(5.5f, 0f, 4.4f, 2f, 1.8f, 6f, 1f, 7.5f), 14, shade, gloss = 0.8f)
            b.disc(px, pz, y + 0.2f, 5.3f, 14, HallArt.solid(0xFFFFF0C8.toInt()).full, ny = -1f, emissive = 1.6f)
            b.sphere(px, y - 0.6f, pz, 2.2f, HallArt.solid(0xFFFFF6DC.toInt()).full, slices = 8, stacks = 5, emissive = 2.2f)
            val g = HallArt.glow.full
            // Additive glows add their colour, so a dark warm tint keeps the pools soft.
            b.quad(px - 12f, topY + 0.3f, pz - 12f, px + 12f, topY + 0.3f, pz - 12f, px + 12f, topY + 0.3f, pz + 12f, px - 12f, topY + 0.3f, pz + 12f, g, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, cull = false, tint = 0xFF3A2410.toInt())
            b.quad(px - 28f, 0.2f, pz - 28f, px + 28f, 0.2f, pz - 28f, px + 28f, 0.2f, pz + 28f, px - 28f, 0.2f, pz + 28f, g, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, cull = false, tint = 0xFF2A1A0C.toInt())
        }
        CafeLayout.lights(lights)
    }

    // ------------------------------------------------------------------ back bar

    private fun backBar(b: ModelBuilder, p: Prop) {
        val x0 = p.x0
        val x1 = p.x1
        val z0 = p.z0
        val z1 = p.z1
        val h = p.height
        val wood = HallArt.wood(0xFF5A3A28.toInt(), 5).full
        b.box(x0, 0f, z0, x1, h - 2f, z1, BoxFaces(front = barFront.full, left = wood, right = wood, top = wood, back = wood, frontEmissive = 0.55f, gloss = 0.4f))
        val steel = HallArt.brushedMetal.full
        b.box(x0 - 0.5f, h - 2f, z0 - 0.5f, x1 + 0.5f, h, z1 + 0.5f, BoxFaces(front = steel, top = steel, left = steel, right = steel, back = steel, gloss = 0.9f))
        // The wall behind it: a tiled splashback carrying the menu board, with a striped fascia on top.
        val splash = HallArt.paint(0xFF2A3A44.toInt(), 0.1f, 0.7f).full
        b.box(x0, h, z0 - 1f, x1, 60f, z0 + 1f, BoxFaces(front = splash, left = splash, right = splash, top = splash, back = splash))
        // Its back, for the aisle behind the café: a lit sign saying what's round the other side.
        val back = HallArt.lightbox("CAFE  •  SHAKES  •  SNACKS", 0xFF1E5A4A.toInt(), 0xFFFFF4DC.toInt(), 768, 96, 52f).full
        val bx0 = (x0 + x1) / 2f - 44f
        val bx1 = (x0 + x1) / 2f + 44f
        b.quad(bx1, 56f, z0 - 1.06f, bx0, 56f, z0 - 1.06f, bx0, 45f, z0 - 1.06f, bx1, 45f, z0 - 1.06f, back, 0f, 0f, -1f, emissive = 1.1f)
        // The awning's underside, seen from behind and below.
        b.quad(x1, 66f, z0 - 1f, x0, 66f, z0 - 1f, x0, 58f, z0 + 3f, x1, 58f, z0 + 3f, HallArt.darkMetal.full, 0f, -0.45f, -1f)
        val frame = HallArt.darkMetal.full
        val mx0 = x0 + 28f
        val mx1 = x1 - 26f
        b.box(mx0 - 1.2f, h + 3f, z0 + 1f, mx1 + 1.2f, 57f, z0 + 2.2f, BoxFaces(front = frame, top = frame, left = frame, right = frame))
        b.quad(mx0, 56f, z0 + 2.3f, mx1, 56f, z0 + 2.3f, mx1, h + 4f, z0 + 3.4f, mx0, h + 4f, z0 + 3.4f, menu.full, 0f, 0.1f, 1f, emissive = 1.15f)
        b.quad(x0, 66f, z0 - 1f, x1, 66f, z0 - 1f, x1, 58f, z0 + 3f, x0, 58f, z0 + 3f, awning.full, 0f, 0.45f, 1f, gloss = 0.3f)
        // A little neon coffee cup either side of the menu.
        for (cx in floatArrayOf(mx0 - 12f, mx1 + 12f)) {
            b.quad(cx - 8f, 56f, z0 + 1.3f, cx + 8f, 56f, z0 + 1.3f, cx + 8f, 40f, z0 + 1.3f, cx - 8f, 40f, z0 + 1.3f, cupNeon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.6f, cull = false)
        }

        // Espresso machine at the left end, steaming away.
        val ex0 = x0 + 4f
        val ex1 = x0 + 26f
        val ez0 = z0 + 3f
        val ez1 = z1 - 3f
        val chrome = HallArt.chrome.full
        b.box(ex0, h, ez0, ex1, h + 14f, ez1, BoxFaces(front = espresso.full, left = chrome, right = chrome, top = chrome, gloss = 1f))
        b.box(ex0 - 0.5f, h + 14f, ez0 - 0.5f, ex1 + 0.5f, h + 15f, ez1 + 0.5f, BoxFaces.all(chrome, 1f))
        for (k in 0 until 2) {
            val gx = ex0 + 6f + k * 10f
            b.cylinder(gx, ez1 + 1.2f, h + 7f, h + 9.5f, 1.8f, 10, chrome, top = chrome, gloss = 1f)
            b.capsule(gx, h + 7.4f, ez1 + 2.4f, gx, h + 7.4f, ez1 + 7f, 0.7f, HallArt.solid(0xFF16161C.toInt()).full, slices = 6)
            b.cylinder(gx, ez1 + 1.2f, h + 2f, h + 4.6f, 1.4f, 10, HallArt.solid(-1).full, top = HallArt.solid(0xFF3A1E10.toInt()).full)
        }
        b.box(ex0 + 1f, h, ez1, ex1 - 1f, h + 1.5f, ez1 + 3.5f, BoxFaces.all(HallArt.darkMetal.full, 0.6f))
        for (k in 0 until 4) {
            b.cylinder(ex0 + 3f + k * 5.2f, (ez0 + ez1) / 2f, h + 15f, h + 17.6f, 1.5f, 8, HallArt.solid(-1).full, top = HallArt.solid(0xFFE8E0D0.toInt()).full)
        }
        b.sphere(ex1 - 3f, h + 12f, ez1 + 0.1f, 0.8f, HallArt.solid(0xFFFF4040.toInt()).full, slices = 6, stacks = 4, emissive = 2f)

        // Soft-serve machine at the right end.
        val sx0 = x1 - 22f
        val sx1 = x1 - 4f
        val body = HallArt.paint(0xFFD8DCE6.toInt(), 0.2f, 0.75f).full
        b.box(sx0, h, ez0, sx1, h + 24f, ez1, BoxFaces(front = softServe.full, left = body, right = body, top = body, frontEmissive = 0.45f, gloss = 0.8f))
        for (k in 0 until 2) {
            val nx = sx0 + 5.5f + k * 7f
            b.cylinder(nx, ez1 + 1.5f, h + 9f, h + 12f, 1.3f, 8, chrome, top = chrome, gloss = 1f)
            b.cylinder(nx, ez1 + 1.5f, h + 7f, h + 9f, 0.6f, 6, chrome, topRadius = 1.2f)
            b.capsule(nx, h + 15f, ez1 + 0.5f, nx, h + 18f, ez1 + 3.5f, 0.5f, HallArt.solid(0xFFFF4FA8.toInt()).full, slices = 5)
        }
        // A tube of cones on the side.
        glassTube(b, sx0 - 3f, (ez0 + ez1) / 2f, h, h + 20f, 2f, 2f, 10)
        for (k in 0 until 6) b.lathe(sx0 - 3f, h + 1f + k * 3f, (ez0 + ez1) / 2f, floatArrayOf(0.2f, 0f, 1.7f, 3.4f), 8, HallArt.paint(0xFFD89A4A.toInt()).full)
        // Syrup bottles along the back.
        val syrups = intArrayOf(0xFFFF3D6E.toInt(), 0xFF2FB8FF.toInt(), 0xFF7CF25A.toInt(), 0xFFB070FF.toInt(), 0xFFFFC83D.toInt())
        for (k in 0 until 5) {
            val bx = ex1 + 4f + k * 3.6f
            b.cylinder(bx, z0 + 4f, h, h + 6f, 1.3f, 8, HallArt.paint(syrups[k]).full, gloss = 0.6f)
            b.cylinder(bx, z0 + 4f, h + 6f, h + 8.5f, 0.5f, 6, HallArt.solid(0xFFE8E8E8.toInt()).full)
        }
    }

    /** A neon coffee cup with steam curls, for either side of the menu. */
    private val cupNeon: Texture by lazy {
        val tp = TexPaint(96, 96)
        tp.clear(0)
        val c = 0xFFFFB050.toInt()
        tp.glow(8f, alpha(c, 0.6f)) {
            round(18f, 46f, 44f, 34f, 10f, -1)
            ring(68f, 60f, 9f, 5f, -1)
            line(30f, 38f, 34f, 18f, 4f, -1)
            line(46f, 38f, 50f, 14f, 4f, -1)
        }
        tp.strokeRound(18f, 46f, 44f, 34f, 10f, 3f, lift(c, 0.6f))
        tp.ring(68f, 60f, 9f, 2.5f, lift(c, 0.6f))
        tp.line(30f, 38f, 34f, 18f, 2f, lift(c, 0.7f))
        tp.line(46f, 38f, 50f, 14f, 2f, lift(c, 0.7f))
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ service counter

    private fun counter(b: ModelBuilder, p: Prop) {
        val x0 = p.x0
        val x1 = p.x1
        val z0 = CafeLayout.COUNTER_BACK
        val z1 = p.z1
        val h = p.height
        val side = HallArt.paint(MINT, 0.1f, 0.7f).full
        b.box(x0, 0f, z0, x1, h - 2f, z1, BoxFaces(front = counterFront.full, left = side, right = side, back = side, gloss = 0.3f))
        val top = HallArt.wood(0xFFB07A48.toInt(), 6).full
        b.box(x0 - 1f, h - 2f, z0 - 1f, x1 + 1f, h, z1 + 1.5f, BoxFaces(front = top, top = top, left = top, right = top, back = top, gloss = 0.8f))
        val chrome = HallArt.chrome.full
        b.box(x0, 0f, z1, x1, 2.5f, z1 + 0.4f, BoxFaces(front = chrome, top = chrome, gloss = 1f))
        // Neon CAFÉ across the front.
        b.quad(x0 + 36f, 24f, z1 + 0.6f, x0 + 96f, 24f, z1 + 0.6f, x0 + 96f, 5f, z1 + 0.6f, x0 + 36f, 5f, z1 + 0.6f, HallArt.neon("CAFÉ", 0xFFFF4FA8.toInt(), 512, 160, 120f).full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.8f, cull = false)
        b.quad(x0 + 100f, 22f, z1 + 0.6f, x0 + 118f, 22f, z1 + 0.6f, x0 + 118f, 6f, z1 + 0.6f, x0 + 100f, 6f, z1 + 0.6f, cupNeon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.6f, cull = false)
        b.quad(x0 + 14f, 22f, z1 + 0.6f, x0 + 32f, 22f, z1 + 0.6f, x0 + 32f, 6f, z1 + 0.6f, x0 + 14f, 6f, z1 + 0.6f, cupNeon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.6f, cull = false)
        // The barista's floor mat in the lane.
        b.quad(x0 + 6f, 0.15f, CafeLayout.COUNTER_Z0 + 2f, x1 - 6f, 0.15f, CafeLayout.COUNTER_Z0 + 2f, x1 - 6f, 0.15f, z0 - 2f, x0 + 6f, 0.15f, z0 - 2f, HallArt.solid(0xFF22222A.toInt()).full, 0f, 1f, 0f)

        pastryCase(b, x0 + 4f, x0 + 44f, z0 + 2f, z1 - 1.5f, h)
        // Napkins and the tip jar.
        val nz = (z0 + z1) / 2f
        for (nx in floatArrayOf(x0 + 48f, x1 - 40f)) {
            b.box(nx, h, nz - 2f, nx + 4f, h + 3f, nz + 2f, BoxFaces.all(chrome, 1f))
            b.box(nx + 0.4f, h + 3f, nz - 1.6f, nx + 3.6f, h + 4.2f, nz + 1.6f, BoxFaces.all(HallArt.solid(-1).full))
        }
        val jx = x0 + 57f
        glassTube(b, jx, nz + 2f, h, h + 6f, 2.4f, 2.2f, 10)
        for (k in 0 until 4) b.disc(jx + (k - 1.5f) * 0.9f, nz + 2f + (k % 2) * 0.8f, h + 0.3f + k * 0.5f, 0.9f, 8, HallArt.paint(0xFFFFC83D.toInt(), 0.4f, 0.7f).full, gloss = 1f)
        b.capsule(jx - 0.4f, h + 2f, nz + 4.4f, jx + 0.4f, h + 3.2f, nz + 4.5f, 0.35f, HallArt.solid(0xFF3DDC84.toInt()).full, slices = 4)

        // Slushie machine: a dark base with a lit label, three glass tanks (the slush inside
        // turns, drawn live) with white lids and taps.
        val tanks = CafeLayout.SLUSH_TANKS
        val sx0 = tanks[0] - 6f
        val sx1 = tanks[tanks.size - 1] + 6f
        val sz = CafeLayout.SLUSH_Z
        val base = HallArt.paint(0xFF22242E.toInt(), 0.1f, 0.7f).full
        b.box(sx0, h, sz - 7f, sx1, CafeLayout.SLUSH_Y0, sz + 7f, BoxFaces(front = base, left = base, right = base, top = base, gloss = 0.5f))
        b.quad(sx0 + 1f, h + 5.5f, sz + 7.1f, sx1 - 1f, h + 5.5f, sz + 7.1f, sx1 - 1f, h + 2f, sz + 7.1f, sx0 + 1f, h + 2f, sz + 7.1f, HallArt.lightbox("SLUSH", 0xFF2FB8FF.toInt(), -1, 256, 64, 44f).full, 0f, 0f, 1f, emissive = 1.3f)
        val lid = HallArt.paint(0xFFF4F4F8.toInt(), 0.2f, 0.8f).full
        for (tx in tanks) {
            b.cylinder(tx, sz, CafeLayout.SLUSH_Y1, CafeLayout.SLUSH_Y1 + 2f, CafeLayout.SLUSH_R + 0.3f, 14, lid, top = lid, gloss = 0.7f)
            b.sphere(tx, CafeLayout.SLUSH_Y1 + 2f, sz, 1.2f, HallArt.solid(0xFFFF4FA8.toInt()).full, slices = 8, stacks = 4, yFrom = 0f)
            b.box(tx - 1.2f, CafeLayout.SLUSH_Y0 + 0.5f, sz + CafeLayout.SLUSH_R - 0.5f, tx + 1.2f, CafeLayout.SLUSH_Y0 + 3.5f, sz + CafeLayout.SLUSH_R + 2.2f, BoxFaces.all(HallArt.darkMetal.full, 0.6f))
            b.capsule(tx, CafeLayout.SLUSH_Y0 + 4.5f, sz + CafeLayout.SLUSH_R + 1.4f, tx, CafeLayout.SLUSH_Y0 + 7f, sz + CafeLayout.SLUSH_R + 1.6f, 0.5f, HallArt.solid(CHERRY).full, slices = 5)
            glassTube(b, tx, sz, CafeLayout.SLUSH_Y0, CafeLayout.SLUSH_Y1, CafeLayout.SLUSH_R, CafeLayout.SLUSH_R, 14)
        }
        // Stacks of cups and lids.
        val white = HallArt.paint(0xFFF8F6F0.toInt(), 0.2f, 0.85f).full
        for (k in 0 until 3) {
            val cx = sx1 + 4f + k * 4.2f
            val tall = 8f + k * 1.5f
            b.lathe(cx, h, nz, floatArrayOf(1.3f, 0f, 1.7f, tall - 0.8f, 1.9f, tall), 10, if (k == 1) HallArt.paint(CHERRY, 0.2f, 0.8f).full else white, gloss = 0.4f)
        }
        // The till: register body, sloped keys, a customer display and a bell.
        val tx = CafeLayout.TILL_X
        val dark = HallArt.paint(0xFF2A2C36.toInt(), 0.15f, 0.7f).full
        b.box(tx - 7f, h, z0 + 2f, tx + 7f, h + 3f, z1 - 3f, BoxFaces(front = dark, left = dark, right = dark, top = dark, back = dark, gloss = 0.5f))
        b.quad(tx - 6f, h + 6f, z0 + 3f, tx + 6f, h + 6f, z0 + 3f, tx + 6f, h + 3f, z0 + 9f, tx - 6f, h + 3f, z0 + 9f, keypad.full, 0f, 0.9f, 0.4f, gloss = 0.4f)
        b.box(tx - 6f, h + 3f, z0 + 2f, tx + 6f, h + 6f, z0 + 3f, BoxFaces(left = dark, right = dark, top = dark, back = dark))
        b.box(tx - 0.6f, h + 3f, z1 - 7f, tx + 0.6f, h + 8f, z1 - 6f, BoxFaces.all(dark))
        b.box(tx - 5f, h + 8f, z1 - 7.5f, tx + 5f, h + 12f, z1 - 5.5f, BoxFaces(front = tillScreen.full, top = dark, left = dark, right = dark, back = dark, frontEmissive = 1.4f))
        b.sphere(tx + 11f, h, z1 - 5f, 1.8f, HallArt.chrome.full, slices = 10, stacks = 5, yFrom = 0f, gloss = 1f)
    }

    private val keypad: Texture by lazy {
        val tp = TexPaint(64, 32)
        tp.fill(0xFF1A1A22.toInt())
        for (y in 0 until 3) for (x in 0 until 5) tp.round(4f + x * 12f, 3f + y * 10f, 9f, 7f, 2f, if (x == 4) 0xFF3DDC84.toInt() else 0xFFC8CAD6.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /**
     * A glass pastry case on the counter: three stepped rows of treats (donuts at the front,
     * cupcakes in the middle, cookies and pizza slices at the back) under a sloped glass front.
     */
    private fun pastryCase(b: ModelBuilder, x0: Float, x1: Float, z0: Float, z1: Float, h: Float) {
        val frame = HallArt.chrome.full
        val shelf = HallArt.solid(0xFFFFF4E0.toInt()).full
        val top = h + 15f
        b.box(x0, h, z0, x1, h + 1.2f, z1, BoxFaces(front = frame, top = shelf, left = frame, right = frame, topEmissive = 0.35f, gloss = 0.8f))
        // Stepped shelves.
        val rowZ = floatArrayOf(z1 - 3f, (z0 + z1) / 2f, z0 + 2.6f)
        val rowY = floatArrayOf(h + 1.2f, h + 4.2f, h + 7.4f)
        for (k in 1 until 3) {
            b.box(x0 + 0.5f, h + 1.2f, rowZ[k] - 2.4f, x1 - 0.5f, rowY[k], rowZ[k] + 2.4f, BoxFaces(front = shelf, top = shelf, topEmissive = 0.35f, frontEmissive = 0.2f))
        }
        val n = ((x1 - x0 - 4f) / 5.4f).toInt()
        for (i in 0 until n) {
            val cx = x0 + 3.5f + i * 5.4f
            // Donuts, pink and chocolate.
            val dz = rowZ[0]
            b.torus(cx, rowY[0] + 1f, dz, 1.6f, 0.95f, HallArt.paint(0xFFD89A4A.toInt(), 0.3f, 0.8f).full, segments = 12, sides = 6)
            b.torus(cx, rowY[0] + 1.5f, dz, 1.6f, 0.7f, (if (i % 2 == 0) icingPink else icingChoc).full, segments = 12, sides = 5)
            // Cupcakes in striped cases.
            val cz = rowZ[1]
            b.lathe(cx, rowY[1], cz, floatArrayOf(1.2f, 0f, 1.6f, 1.8f), 10, HallArt.paint(if (i % 2 == 0) 0xFF39B8E6.toInt() else 0xFFFFC83D.toInt()).full)
            b.sphere(cx, rowY[1] + 2f, cz, 1.7f, HallArt.paint(if (i % 3 == 0) 0xFFFFF4E4.toInt() else 0xFFFF9EC8.toInt(), 0.3f, 0.85f).full, slices = 10, stacks = 5, yFrom = 0f, sy = 1.1f, gloss = 0.4f)
            b.sphere(cx, rowY[1] + 3.9f, cz, 0.55f, HallArt.solid(CHERRY).full, slices = 6, stacks = 4, gloss = 0.9f)
            // Cookies and pizza slices, alternating.
            val bz = rowZ[2]
            if (i % 2 == 0) {
                b.cylinder(cx, bz, rowY[2], rowY[2] + 0.8f, 2f, 12, HallArt.paint(0xFFB07A40.toInt()).full, top = cookie.full)
            } else {
                val y = rowY[2] + 0.6f
                b.poly(
                    floatArrayOf(cx - 2.4f, cx + 2.4f, cx), floatArrayOf(y, y, y), floatArrayOf(bz - 2f, bz - 2f, bz + 2.4f),
                    floatArrayOf(0f, 64f, 32f), floatArrayOf(0f, 0f, 64f), pizza.full, 0f, 1f, 0f,
                )
                b.box(cx - 2.6f, rowY[2], bz - 2.6f, cx + 2.6f, y, bz - 1.6f, BoxFaces.all(HallArt.paint(0xFFC8883A.toInt()).full))
            }
        }
        // Lit from inside, then glassed in.
        b.quad(x0 + 0.5f, top - 0.3f, z0 + 0.5f, x1 - 0.5f, top - 0.3f, z0 + 0.5f, x1 - 0.5f, top - 0.3f, z0 + 3f, x0 + 0.5f, top - 0.3f, z0 + 3f, HallArt.solid(0xFFFFF4D8.toInt()).full, 0f, -1f, 0f, emissive = 1.6f, cull = false)
        val g = HallArt.glass.full
        b.quad(x0, top, z1 - 5f, x1, top, z1 - 5f, x1, h + 1.2f, z1, x0, h + 1.2f, z1, g, 0f, 0.3f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.quad(x0, top, z0, x1, top, z0, x1, top, z1 - 5f, x0, top, z1 - 5f, g, 0f, 1f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.poly(floatArrayOf(x0, x0, x0, x0), floatArrayOf(top, top, h + 1.2f, h + 1.2f), floatArrayOf(z1 - 5f, z0, z0, z1), floatArrayOf(0f, 128f, 128f, 0f), floatArrayOf(0f, 0f, 128f, 128f), g, -1f, 0f, 0f, blend = Blend.ALPHA, cull = false)
        b.poly(floatArrayOf(x1, x1, x1, x1), floatArrayOf(top, top, h + 1.2f, h + 1.2f), floatArrayOf(z0, z1 - 5f, z1, z0), floatArrayOf(0f, 128f, 128f, 0f), floatArrayOf(0f, 0f, 128f, 128f), g, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false)
        b.box(x0, top, z0, x1, top + 0.8f, z0 + 1f, BoxFaces.all(frame, 1f))
    }

    // ------------------------------------------------------------------ seating

    private fun booth(b: ModelBuilder, p: Prop) {
        val x0 = p.x0
        val x1 = p.x1
        val z0 = p.z0
        val z1 = p.z1
        val red = vinyl.full
        val chrome = HallArt.chrome.full
        val frame = HallArt.paint(0xFF3A2A22.toInt(), 0.1f, 0.7f).full
        // Two benches facing each other with tall padded backs, the table between them.
        for (north in booleanArrayOf(true, false)) {
            val bz0 = if (north) z0 else z1 - 12f
            val bz1 = if (north) z0 + 12f else z1
            val backZ0 = if (north) z0 else z1 - 3.5f
            val backZ1 = if (north) z0 + 3.5f else z1
            b.box(x0, 0f, bz0, x1, 7f, bz1, BoxFaces(front = frame, back = frame, right = frame, left = frame))
            b.box(x0, 7f, bz0, x1, 10f, bz1, BoxFaces(front = red, back = red, right = red, top = red, gloss = 0.6f))
            b.box(x0, 10f, backZ0, x1, 24f, backZ1, BoxFaces(front = red, back = red, right = red, top = red, gloss = 0.6f))
            b.box(x0, 23.5f, backZ0 - 0.3f, x1 + 0.3f, 24.8f, backZ1 + 0.3f, BoxFaces.all(chrome, 1f))
        }
        // Table on a single chrome post from the wall.
        val tz0 = z0 + 14f
        val tz1 = z1 - 14f
        b.box(x0 + 8f, 0f, (tz0 + tz1) / 2f - 1f, x0 + 11f, 17f, (tz0 + tz1) / 2f + 1f, BoxFaces.all(chrome, 1f))
        b.box(x0, 17f, tz0, x1 - 4f, 18.5f, tz1, BoxFaces(front = chrome, back = chrome, right = chrome, top = laminate.full, gloss = 0.7f))
        // Something on the table: a milkshake, fries and the condiments.
        val mx = x1 - 12f
        val mz = (tz0 + tz1) / 2f
        b.lathe(mx, 18.5f, mz - 3f, floatArrayOf(0.8f, 0f, 0.6f, 2f, 1.7f, 5.4f), 10, HallArt.paint(0xFFFFB8D8.toInt(), 0.3f, 0.8f).full, gloss = 0.8f)
        b.sphere(mx, 23.6f, mz - 3f, 1.9f, HallArt.paint(0xFFFF9EC8.toInt(), 0.3f, 0.85f).full, slices = 10, stacks = 4, yFrom = 0f)
        b.sphere(mx, 25.5f, mz - 3f, 0.5f, HallArt.solid(CHERRY).full, slices = 6, stacks = 4)
        b.box(mx - 9f, 18.5f, mz, mx - 5f, 20.5f, mz + 3f, BoxFaces.all(HallArt.paint(CHERRY).full))
        for (k in 0 until 5) b.box(mx - 8.5f + k * 0.7f, 20.5f, mz + 1f, mx - 8f + k * 0.7f, 23f, mz + 1.5f, BoxFaces.all(HallArt.solid(0xFFFFD84D.toInt()).full))
        b.cylinder(x0 + 5f, mz - 2f, 18.5f, 23f, 0.9f, 8, HallArt.paint(CHERRY).full, top = HallArt.solid(-1).full)
        b.cylinder(x0 + 5f, mz + 1f, 18.5f, 23f, 0.9f, 8, HallArt.paint(0xFFFFD84D.toInt()).full, top = HallArt.solid(-1).full)
    }

    private fun chair(b: ModelBuilder, p: Prop) {
        val cx = p.centerX
        val cz = p.centerZ
        val yaw = CafeLayout.chairYaw(p.variant)
        val chrome = HallArt.chrome.full
        val seat = vinyl.full
        // Built facing +z, then turned to face the table: the back is behind the sitter.
        val m = ModelBuilder()
        for ((lx, lz) in LEGS) m.capsule(lx, 0f, lz, lx * 0.9f, 10f, lz * 0.9f, 0.45f, chrome, slices = 5, gloss = 1f)
        m.cylinder(0f, 0f, 10f, 12f, 4.4f, 14, seat, top = seat, gloss = 0.6f)
        m.capsule(-3.4f, 11.5f, -3.2f, -3.4f, 22f, -3.8f, 0.45f, chrome, slices = 5, gloss = 1f)
        m.capsule(3.4f, 11.5f, -3.2f, 3.4f, 22f, -3.8f, 0.45f, chrome, slices = 5, gloss = 1f)
        m.box(-3.8f, 17f, -4.6f, 3.8f, 22.5f, -3.2f, BoxFaces(front = seat, back = seat, top = seat, left = seat, right = seat, gloss = 0.6f))
        b.add(m.build(), xf.set(cx, 0f, cz, yaw = yaw))
    }

    private val LEGS = arrayOf(-3f to -3f, 3f to -3f, -3f to 3f, 3f to 3f)

    /** A see-through (alpha-blended) glass tube, outward faces only, so what's inside shows. */
    private fun glassTube(b: ModelBuilder, cx: Float, cz: Float, y0: Float, y1: Float, r0: Float, r1: Float, sides: Int) {
        val g = HallArt.glass.full
        val step = (PI * 2 / sides).toFloat()
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val am = (a0 + a1) / 2f
            b.quad(
                cx + cos(a1) * r1, y1, cz + sin(a1) * r1, cx + cos(a0) * r1, y1, cz + sin(a0) * r1,
                cx + cos(a0) * r0, y0, cz + sin(a0) * r0, cx + cos(a1) * r0, y0, cz + sin(a1) * r0,
                g, cos(am), 0f, sin(am), u0 = g.w * i / sides.toFloat(), u1 = g.w * (i + 1) / sides.toFloat(),
                blend = Blend.ALPHA, gloss = 1f,
            )
        }
    }
}
