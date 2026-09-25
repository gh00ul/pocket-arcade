package com.pocketarcade.hub

import com.pocketarcade.data.Catalog
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.claw.Plush3D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Models (and lights) for everything on the floor that isn't a game cabinet. */
object Props {
    private val xf = Xform()

    private fun light(x: Float, y: Float, z: Float, color: Int, radius: Float, intensity: Float) =
        PointLight(x, y, z, (color shr 16 and 255) / 255f, (color shr 8 and 255) / 255f, (color and 255) / 255f, radius, intensity)

    /** Builds [p]'s model, adding any lights it gives off to [lights]. */
    fun build(p: Prop, lights: MutableList<PointLight>): Model {
        val b = ModelBuilder()
        when (p.kind) {
            PropKind.COUNTER -> counter(b, p, lights)
            PropKind.PRIZE_WALL -> prizeWall(b, p, lights)
            PropKind.TOKENS -> kiosk(b, p, lights, "TOKENS", 0xFFFFC83D.toInt(), "BUY TOKENS")
            PropKind.CHANGE -> kiosk(b, p, lights, "TICKETS", 0xFFFF9A3C.toInt(), "SCAN TICKETS")
            PropKind.VENDING -> vending(b, p, lights)
            PropKind.CAFE_TABLE -> cafeTable(b, p)
            PropKind.STOOL -> stool(b, p)
            PropKind.PILLAR -> pillar(b, p, lights)
            PropKind.PLANT -> plant(b, p.centerX, p.centerZ, 1f)
            PropKind.TRASH -> trash(b, p)
            PropKind.PHOTO_BOOTH -> photoBooth(b, p, lights)
            PropKind.BENCH -> bench(b, p)
            PropKind.DOORS -> doors(b, p, lights)
            PropKind.KIDDIE_RIDE -> kiddieRide(b, p, lights)
            PropKind.DECOR -> decor(b, p, lights)
            PropKind.MACHINE -> Unit
        }
        return b.build()
    }

    // ------------------------------------------------------------------ prize corner

    private val counterFront: Texture by lazy {
        val tp = TexPaint(512, 96)
        tp.vgrad(0f, 0f, 512f, 96f, 0xFFB0213A.toInt(), 0xFF5A0A1C.toInt())
        for (x in 0 until 512 step 32) tp.rect(x.toFloat(), 10f, 2f, 76f, 0x33000000)
        tp.rect(0f, 0f, 512f, 6f, 0xFFFFC83D.toInt())
        tp.rect(0f, 90f, 512f, 6f, 0xFF3A0610.toInt())
        tp.glowText("PRIZES", 256f, 66f, 50f, 0xFFFFE9A0.toInt(), 0xFFFFB020.toInt(), 8f, Fonts.display, 0.1f)
        tp.toTexture().also { tp.recycle() }
    }

    private fun counter(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val top = HallArt.wood(0xFF3A2A22.toInt(), 4).full
        val side = HallArt.paint(0xFF6A0A1C.toInt()).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height - 2f, p.z1, BoxFaces(front = counterFront.full, left = side, right = side, back = side, gloss = 0.4f))
        b.box(p.x0 - 1f, p.height - 2f, p.z0 - 1f, p.x1 + 1f, p.height, p.z1 + 1f, BoxFaces(front = top, top = top, left = top, right = top, back = top, gloss = 0.7f))
        // LED strip under the lip.
        b.quad(p.x0, p.height - 2.3f, p.z1 + 1.05f, p.x1, p.height - 2.3f, p.z1 + 1.05f, p.x1, p.height - 3.3f, p.z1 + 1.05f, p.x0, p.height - 3.3f, p.z1 + 1.05f, HallArt.solid(0xFFFF4FA8.toInt()).full, 0f, 0f, 1f, emissive = 1.5f)
        // Glass display cases on the counter, full of small prizes.
        val caseTop = p.height + 12f
        for ((xa, xb) in listOf(p.x0 + 4f to p.x0 + 56f, p.x1 - 56f to p.x1 - 4f)) {
            b.box(xa, p.height, p.z0 + 3f, xb, p.height + 1f, p.z1 - 3f, BoxFaces(top = HallArt.solid(0xFF1A1030.toInt()).full))
            for (k in 0 until 6) {
                val plush = Catalog.plushies[(k * 5 + xa.toInt()) % Catalog.plushies.size]
                b.add(Plush3D.model(plush), xf.set(xa + 5f + k * ((xb - xa - 10f) / 5f), p.height + 1f, (p.z0 + p.z1) / 2f, yaw = (k - 2.5f) * 0.15f, scale = 0.42f))
            }
            val g = HallArt.glass.full
            b.quad(xa, caseTop, p.z1 - 3f, xb, caseTop, p.z1 - 3f, xb, p.height, p.z1 - 3f, xa, p.height, p.z1 - 3f, g, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
            b.quad(xa, caseTop, p.z0 + 3f, xb, caseTop, p.z0 + 3f, xb, caseTop, p.z1 - 3f, xa, caseTop, p.z1 - 3f, g, 0f, 1f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
            b.quad(xa, caseTop, p.z0 + 3f, xa, caseTop, p.z1 - 3f, xa, p.height, p.z1 - 3f, xa, p.height, p.z0 + 3f, g, -1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
            b.quad(xb, caseTop, p.z1 - 3f, xb, caseTop, p.z0 + 3f, xb, p.height, p.z0 + 3f, xb, p.height, p.z1 - 3f, g, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
            lights += light((xa + xb) / 2f, caseTop + 4f, (p.z0 + p.z1) / 2f, 0xFFFFF0D0.toInt(), 36f, 0.8f)
        }
        // Register and ticket eater in the middle.
        val cx = p.centerX
        b.box(cx - 6f, p.height, p.z0 + 4f, cx + 6f, p.height + 7f, p.z0 + 14f, BoxFaces(front = HallArt.darkMetal.full, top = HallArt.darkMetal.full, left = HallArt.darkMetal.full, right = HallArt.darkMetal.full, gloss = 0.5f))
        b.quad(cx - 5f, p.height + 7.5f, p.z0 + 5f, cx + 5f, p.height + 7.5f, p.z0 + 5f, cx + 5f, p.height + 4.5f, p.z0 + 8f, cx - 5f, p.height + 4.5f, p.z0 + 8f, HallArt.lightbox("TICKETS", 0xFF1A6A3A.toInt(), -1, 256, 96, 40f).full, 0f, 0.7f, 0.7f, emissive = 1.2f)
        lights += light(cx, 40f, p.z1 + 12f, 0xFFFF8AB0.toInt(), 90f, 0.9f)
    }

    private fun prizeWall(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val back = HallArt.paint(0xFF1C1440.toInt(), 0.08f, 0.6f).full
        val shelf = HallArt.wood(0xFFE8E0D0.toInt(), 3).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height, p.z0 + 2f, BoxFaces(front = back, top = back))
        val shelves = 5
        for (k in 0 until shelves) {
            val y = 14f + k * 17f
            b.box(p.x0, y - 1.2f, p.z0 + 2f, p.x1, y, p.z1, BoxFaces(front = shelf, top = shelf, gloss = 0.3f))
            // Alternate plush rows and boxed toys.
            var x = p.x0 + 4f
            var i = 0
            while (x < p.x1 - 6f) {
                if ((k + i) % 3 == 0) {
                    val w = 9f + hash01(i, k) * 5f
                    val hgt = 8f + hash01(i, k + 9) * 6f
                    val tex = boxTextures[(i + k * 3) % boxTextures.size]
                    b.box(x, y, p.z0 + 4f, x + w, y + hgt, p.z1 - 2f, BoxFaces(front = tex.full, top = tex.full, left = tex.full, right = tex.full, gloss = 0.35f))
                    x += w + 2f
                } else {
                    val plush = Catalog.plushies[(i * 7 + k * 3) % Catalog.plushies.size]
                    val s = if (k == shelves - 1) 0.75f else 0.55f
                    b.add(Plush3D.model(plush), xf.set(x + 5f, y, (p.z0 + p.z1) / 2f + 1f, yaw = (hash01(i, k + 3) - 0.5f) * 0.6f, scale = s))
                    x += 12f * s + 4f
                }
                i++
            }
        }
        // Neon over the wall.
        b.quad(p.centerX - 70f, p.height + 36f, p.z0 + 2.2f, p.centerX + 70f, p.height + 36f, p.z0 + 2.2f, p.centerX + 70f, p.height + 1f, p.z0 + 2.2f, p.centerX - 70f, p.height + 1f, p.z0 + 2.2f, HallArt.neon("PRIZES", 0xFFFF4FA8.toInt()).full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.6f, cull = false)
        lights += light(p.centerX, p.height + 20f, p.z0 + 16f, 0xFFFF6FB8.toInt(), 150f, 1.1f)
        for (k in 0 until 3) lights += light(p.x0 + 34f + k * 70f, 90f, p.z1 + 30f, 0xFFFFF0D8.toInt(), 110f, 0.8f)
    }

    private val boxTextures: List<Texture> by lazy { List(6) { HallArt.prizeBox(it) } }

    // ------------------------------------------------------------------ service machines

    private fun kiosk(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>, title: String, color: Int, screenText: String) {
        val body = HallArt.paint(0xFF22242E.toInt(), 0.12f, 0.7f).full
        val trim = HallArt.solid(color).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height - 12f, p.z1, BoxFaces(front = body, left = body, right = body, top = body, back = body, gloss = 0.5f))
        // Touch screen.
        val scr = TexPaint(256, 192).let { tp ->
            tp.vgrad(0f, 0f, 256f, 192f, 0xFF1A2A6C.toInt(), 0xFF0A0E24.toInt())
            tp.glowText(screenText, 128f, 70f, 30f, -1, color, 8f, Fonts.display)
            tp.round(48f, 104f, 160f, 50f, 25f, color)
            tp.text("TAP", 128f, 140f, 30f, 0xFF1A1A1A.toInt(), Fonts.display)
            tp.toTexture().also { tp.recycle() }
        }
        b.quad(p.x0 + 3f, 44f, p.z1 + 0.1f, p.x1 - 3f, 44f, p.z1 + 0.1f, p.x1 - 3f, 28f, p.z1 + 0.1f, p.x0 + 3f, 28f, p.z1 + 0.1f, scr.full, 0f, 0f, 1f, emissive = 1.15f)
        // Dispenser cup and card slot.
        b.box(p.centerX - 5f, 10f, p.z1, p.centerX + 5f, 17f, p.z1 + 3f, BoxFaces(front = HallArt.chrome.full, top = HallArt.chrome.full, left = HallArt.chrome.full, right = HallArt.chrome.full, gloss = 0.9f))
        b.quad(p.x0, p.height - 12f, p.z1 + 0.1f, p.x1, p.height - 12f, p.z1 + 0.1f, p.x1, p.height - 13.5f, p.z1 + 0.1f, p.x0, p.height - 13.5f, p.z1 + 0.1f, trim, 0f, 0f, 1f, emissive = 1.4f)
        // Lightbox on top.
        b.box(p.x0 - 1f, p.height - 12f, p.z0, p.x1 + 1f, p.height, p.z1, BoxFaces(front = HallArt.lightbox(title, color, 0xFF1A1A1A.toInt(), 384, 128, 72f).full, top = body, left = body, right = body, back = body, frontEmissive = 1.3f))
        lights += light(p.centerX, 40f, p.z1 + 10f, color, 70f, 0.9f)
    }

    private fun vending(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val soda = p.variant == 0
        val color = if (soda) 0xFFD81E2E.toInt() else 0xFF2F5BE0.toInt()
        val body = HallArt.paint(color).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height, p.z1, BoxFaces(left = body, right = body, top = body, back = body, gloss = 0.5f))
        // Front: lit window of products plus a side panel with the buttons.
        val front = TexPaint(192, 384).let { tp ->
            tp.vgrad(0f, 0f, 192f, 384f, lift(color, 0.15f), dim(color, 0.7f))
            tp.round(10f, 40f, 124f, 270f, 6f, 0xFFE8F0FF.toInt())
            val items = if (soda) intArrayOf(0xFFD81E2E.toInt(), 0xFF22A04A.toInt(), 0xFFFF9A20.toInt(), 0xFF2F5BE0.toInt()) else intArrayOf(0xFFFFB020.toInt(), 0xFF8A4FFF.toInt(), 0xFFE8394A.toInt(), 0xFF3DDC84.toInt())
            for (row in 0 until 5) for (col in 0 until 4) {
                val x = 22f + col * 28f
                val y = 56f + row * 52f
                val c = items[(row + col) % items.size]
                if (soda) {
                    tp.round(x, y, 18f, 34f, 5f, c)
                    tp.rect(x + 2f, y + 12f, 14f, 6f, alpha(-1, 0.8f))
                } else {
                    tp.round(x - 2f, y + 4f, 22f, 30f, 3f, c)
                    tp.text("●", x + 9f, y + 26f, 14f, alpha(-1, 0.7f), Fonts.heavy)
                }
                tp.rect(x - 6f, y + 38f, 30f, 3f, 0xFF9AA0B4.toInt())
            }
            tp.round(142f, 60f, 40f, 110f, 5f, 0xFF16141C.toInt())
            for (k in 0 until 6) tp.circle(162f, 76f + k * 16f, 5f, 0xFF9AA0B4.toInt())
            tp.round(142f, 300f, 40f, 40f, 4f, 0xFF16141C.toInt())
            tp.glowText(if (soda) "COLA" else "SNACKS", 72f, 30f, 28f, -1, lift(color, 0.5f), 6f, Fonts.display)
            tp.rect(10f, 330f, 124f, 40f, 0xFF16141C.toInt())
            tp.toTexture().also { tp.recycle() }
        }
        b.quad(p.x0, p.height, p.z1, p.x1, p.height, p.z1, p.x1, 0f, p.z1, p.x0, 0f, p.z1, front.full, 0f, 0f, 1f, emissive = 0.95f, gloss = 0.6f)
        lights += light(p.centerX, 30f, p.z1 + 10f, lift(color, 0.5f), 60f, 0.8f)
    }

    // ------------------------------------------------------------------ furniture

    private fun cafeTable(b: ModelBuilder, p: Prop) {
        val cx = p.centerX
        val cz = p.centerZ
        val metal = HallArt.brushedMetal.full
        val tops = intArrayOf(0xFFFF4FA8.toInt(), 0xFF39E6F2.toInt(), 0xFFFFD84D.toInt())
        b.cylinder(cx, cz, 0f, 0.8f, 7f, 16, metal, top = metal, gloss = 0.6f)
        b.cylinder(cx, cz, 0.8f, p.height - 1f, 1.1f, 10, metal, gloss = 0.8f)
        b.cylinder(cx, cz, p.height - 1f, p.height, 11f, 22, HallArt.paint(tops[p.variant % 3]).full, top = HallArt.paint(lift(tops[p.variant % 3], 0.3f)).full, gloss = 0.8f)
        // A soda cup and a tray of fries.
        b.cylinder(cx + 3f, cz - 2f, p.height, p.height + 5f, 1.3f, 10, HallArt.paint(0xFFE8323C.toInt()).full, topRadius = 1.6f, top = HallArt.solid(-1).full)
        b.box(cx - 5f, p.height, cz + 1f, cx - 1f, p.height + 1f, cz + 5f, BoxFaces.all(HallArt.paint(0xFFFFC83D.toInt()).full))
    }

    private fun stool(b: ModelBuilder, p: Prop) {
        val cx = p.centerX
        val cz = p.centerZ
        val metal = HallArt.chrome.full
        b.cylinder(cx, cz, 0f, 0.6f, 4f, 12, metal, top = metal, gloss = 0.8f)
        b.cylinder(cx, cz, 0.6f, p.height - 2f, 0.7f, 8, metal, gloss = 0.9f)
        val seat = HallArt.paint(0xFFE8323C.toInt()).full
        b.cylinder(cx, cz, p.height - 2f, p.height, 4.6f, 16, seat, top = seat, gloss = 0.5f)
    }

    private fun bench(b: ModelBuilder, p: Prop) {
        val wood = HallArt.wood(0xFF9A6A3A.toInt(), 5).full
        val metal = HallArt.darkMetal.full
        val leg = BoxFaces(front = metal, left = metal, right = metal, back = metal)
        b.box(p.x0 + 2f, 0f, p.z0 + 2f, p.x0 + 4f, 8f, p.z1 - 2f, leg)
        b.box(p.x1 - 4f, 0f, p.z0 + 2f, p.x1 - 2f, 8f, p.z1 - 2f, leg)
        b.box(p.x0, 8f, p.z0, p.x1, 10f, p.z1, BoxFaces(front = wood, top = wood, left = wood, right = wood, gloss = 0.3f))
        b.box(p.x0, 10f, p.z0, p.x1, 18f, p.z0 + 2f, BoxFaces(front = wood, top = wood, left = wood, right = wood, gloss = 0.3f))
    }

    private fun pillar(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val paint = HallArt.paint(0xFF1A1330.toInt(), 0.08f, 0.6f).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height, p.z1, BoxFaces(front = paint, left = paint, right = paint, back = paint, top = paint, gloss = 0.3f))
        // LED strips up the corners.
        val led = HallArt.solid(0xFF39E6F2.toInt()).full
        for ((x, z) in listOf(p.x0 to p.z1, p.x1 to p.z1)) {
            b.box(x - 0.6f, 0f, z - 0.6f, x + 0.6f, p.height, z + 0.6f, BoxFaces(front = led, left = led, right = led, frontEmissive = 1.6f))
        }
        // A wrap-around screen band.
        val band = HallArt.lightbox("PLAY MORE", 0xFF8A4FFF.toInt(), -1, 256, 96, 34f).full
        b.box(p.x0 - 0.5f, 56f, p.z0 - 0.5f, p.x1 + 0.5f, 66f, p.z1 + 0.5f, BoxFaces(front = band, left = band, right = band, frontEmissive = 1.2f))
        lights += light(p.centerX, 50f, p.z1 + 6f, 0xFF7A8CFF.toInt(), 55f, 0.6f)
    }

    fun plant(b: ModelBuilder, cx: Float, cz: Float, s: Float) {
        val pot = HallArt.paint(0xFFE8E0D0.toInt(), 0.2f, 0.7f).full
        b.lathe(cx, 0f, cz, floatArrayOf(4f * s, 0f, 5.5f * s, 10f * s, 6f * s, 11f * s, 5.4f * s, 11.5f * s), 16, pot, gloss = 0.4f)
        b.disc(cx, cz, 11f * s, 5.4f * s, 16, HallArt.solid(0xFF3A2414.toInt()).full)
        val leaf = HallArt.paint(0xFF2E8A3E.toInt(), 0.3f, 0.7f).full
        for (k in 0 until 9) {
            val a = k * 0.7f
            val len = (16f + (k % 3) * 5f) * s
            val tipX = cx + cos(a) * len * 0.55f
            val tipZ = cz + sin(a) * len * 0.55f
            b.capsule(cx, 10f * s, cz, tipX, 10f * s + len, tipZ, 1.6f * s, leaf, slices = 6)
            b.sphere(tipX, 10f * s + len, tipZ, 3.4f * s, leaf, slices = 8, stacks = 5, sy = 0.5f)
        }
    }

    private fun trash(b: ModelBuilder, p: Prop) {
        val body = HallArt.paint(0xFF3A3C46.toInt()).full
        b.lathe(p.centerX, 0f, p.centerZ, floatArrayOf(5f, 0f, 5.6f, p.height - 3f, 6f, p.height - 2f), 16, body, gloss = 0.5f)
        b.sphere(p.centerX, p.height - 2f, p.centerZ, 6f, body, slices = 16, stacks = 6, yFrom = 0f, sy = 0.45f, gloss = 0.5f)
    }

    private fun photoBooth(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val body = HallArt.paint(0xFFE8E8F0.toInt(), 0.1f, 0.75f).full
        b.box(p.x0, 0f, p.z0, p.x1, p.height, p.z1 - 18f, BoxFaces(front = body, left = body, right = body, top = body, back = body, gloss = 0.4f))
        val curtain = TexPaint(128, 128).let { tp ->
            for (x in 0 until 128 step 8) tp.hgrad(x.toFloat(), 0f, 8f, 128f, 0xFFB0213A.toInt(), 0xFF6A0A1C.toInt())
            tp.toTexture().also { tp.recycle() }
        }
        b.box(p.x0, 0f, p.z1 - 18f, p.x1, p.height, p.z1, BoxFaces(left = body, right = body, top = body))
        b.quad(p.x0 + 2f, p.height - 12f, p.z1 - 0.5f, p.x1 - 2f, p.height - 12f, p.z1 - 0.5f, p.x1 - 2f, 4f, p.z1 - 0.5f, p.x0 + 2f, 4f, p.z1 - 0.5f, curtain.full, 0f, 0f, 1f)
        b.quad(p.x0, p.height, p.z1 + 0.1f, p.x1, p.height, p.z1 + 0.1f, p.x1, p.height - 12f, p.z1 + 0.1f, p.x0, p.height - 12f, p.z1 + 0.1f, HallArt.lightbox("PHOTO", 0xFF2FB8FF.toInt(), -1, 384, 128, 76f).full, 0f, 0f, 1f, emissive = 1.3f)
        lights += light(p.centerX, 50f, p.z1 + 8f, 0xFF7ACBFF.toInt(), 60f, 0.8f)
    }

    private fun doors(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        // Sliding-door tracks set into the threshold, and the sensor posts either side.
        val metal = HallArt.brushedMetal.full
        b.box(p.x0, 0f, p.z0, p.x1, 0.4f, p.z0 + 8f, BoxFaces(top = metal, back = metal, front = metal, gloss = 0.9f))
        val groove = HallArt.solid(0xFF101014.toInt()).full
        b.quad(p.x0, 0.45f, p.z0 + 3f, p.x1, 0.45f, p.z0 + 3f, p.x1, 0.45f, p.z0 + 4f, p.x0, 0.45f, p.z0 + 4f, groove, 0f, 1f, 0f)
        val f = BoxFaces.all(metal, 0.8f)
        b.box(p.x0 - 3f, 0f, p.z0, p.x0, 22f, p.z0 + 8f, f)
        b.box(p.x1, 0f, p.z0, p.x1 + 3f, 22f, p.z0 + 8f, f)
        val led = HallArt.solid(0xFF5CF08A.toInt()).full
        b.box(p.x0 - 2.4f, 18f, p.z0 + 8f, p.x0 - 0.6f, 19.5f, p.z0 + 8.3f, BoxFaces(front = led, frontEmissive = 1.6f))
        b.box(p.x1 + 0.6f, 18f, p.z0 + 8f, p.x1 + 2.4f, 19.5f, p.z0 + 8.3f, BoxFaces(front = led, frontEmissive = 1.6f))
        lights += light(p.centerX, 60f, p.z0 - 20f, 0xFFD8E8FF.toInt(), 110f, 0.7f)
    }

    private fun kiddieRide(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val cx = p.centerX
        val cz = p.centerZ
        val chrome = HallArt.chrome.full
        val base = HallArt.paint(0xFF2A2F6A.toInt(), 0.15f, 0.6f).full
        // Rounded platform with a chrome rim and the coin box at the front corner.
        b.lathe(cx, 0f, cz, floatArrayOf(13f, 0f, 14f, 1.5f, 13.5f, 4f, 0f, 4.2f), 24, base, gloss = 0.4f)
        b.torus(cx, 3.6f, cz, 13.6f, 0.7f, chrome, segments = 28, sides = 6, gloss = 0.9f)
        val box = HallArt.paint(0xFFE8323C.toInt()).full
        b.box(p.x1 - 7f, 0f, p.z1 - 7f, p.x1 - 1f, 16f, p.z1 - 2f, BoxFaces(front = HallArt.darkMetal.full, left = box, right = box, top = chrome, back = box, gloss = 0.5f))
        b.box(p.x1 - 6f, 11f, p.z1 - 1.9f, p.x1 - 2f, 14f, p.z1 - 1.6f, BoxFaces(front = HallArt.solid(0xFFFFD84D.toInt()).full, frontEmissive = 1.2f))
        if (p.variant == 0) {
            // A space rocket with a seat in its open cockpit.
            val white = HallArt.paint(0xFFF2F0F6.toInt(), 0.2f, 0.7f).full
            val red = HallArt.paint(0xFFE8323C.toInt(), 0.2f, 0.7f).full
            b.lathe(cx, 4f, cz - 2f, floatArrayOf(4f, 0f, 7f, 3f, 7.5f, 12f, 7.5f, 26f, 6f, 33f), 20, white, gloss = 0.7f)
            b.lathe(cx, 37f, cz - 2f, floatArrayOf(6f, 0f, 4.5f, 5f, 2f, 10f, 0f, 12f), 20, red, gloss = 0.7f)
            for (k in 0 until 3) {
                val a = k * 2.094f + 0.5f
                val fx = cx + kotlin.math.sin(a) * 8f
                val fz = cz - 2f + kotlin.math.cos(a) * 8f
                b.capsule(fx, 6f, fz, cx + kotlin.math.sin(a) * 11f, 4.5f, cz - 2f + kotlin.math.cos(a) * 11f, 1.6f, red, slices = 6, gloss = 0.6f)
                b.capsule(fx, 6f, fz, fx, 16f, fz, 1.4f, red, slices = 6, gloss = 0.6f)
            }
            // Cockpit opening and seat.
            b.box(cx - 5f, 14f, cz + 4.6f, cx + 5f, 24f, cz + 6f, BoxFaces(front = HallArt.solid(0xFF101018.toInt()).full, top = HallArt.solid(0xFF101018.toInt()).full))
            b.box(cx - 4f, 14f, cz + 3f, cx + 4f, 16f, cz + 8f, BoxFaces.all(HallArt.paint(0xFF3040A0.toInt()).full, 0.3f))
            b.disc(cx, cz - 2f + 7.55f, 28f, 2.2f, 12, HallArt.solid(0xFF7FE8FF.toInt()).full, emissive = 0.6f)
            b.sphere(cx, 50.5f, cz - 2f, 1.4f, HallArt.solid(0xFFFF4F4F.toInt()).full, slices = 8, stacks = 5, emissive = 2f)
            lights += light(cx, 44f, cz + 8f, 0xFFFF8080.toInt(), 50f, 0.6f)
        } else {
            // A little red race car.
            val red = HallArt.paint(0xFFE8323C.toInt(), 0.25f, 0.7f).full
            val black = HallArt.solid(0xFF16161C.toInt()).full
            b.box(cx - 8f, 5f, cz - 12f, cx + 8f, 12f, cz + 10f, BoxFaces.all(red, 0.8f))
            b.box(cx - 7f, 12f, cz - 12f, cx + 7f, 15f, cz - 5f, BoxFaces.all(red, 0.8f))
            b.box(cx - 5.5f, 12f, cz - 3f, cx + 5.5f, 13f, cz + 5f, BoxFaces(top = black, front = black, left = black, right = black, back = black))
            b.box(cx - 6f, 12f, cz - 5f, cx + 6f, 18f, cz - 4f, BoxFaces(front = HallArt.glass.full, top = chrome))
            b.torus(cx, 16f, cz - 2.5f, 2.2f, 0.45f, black, segments = 12, sides = 5)
            for ((wx, wz) in listOf(-8.5f to -8f, 8.5f to -8f, -8.5f to 6f, 8.5f to 6f)) {
                b.capsule(cx + wx - 1.2f, 4.5f, cz + wz, cx + wx + 1.2f, 4.5f, cz + wz, 3.4f, black, slices = 10, gloss = 0.2f)
            }
            b.sphere(cx - 5f, 9f, cz + 10.2f, 1.6f, HallArt.solid(0xFFFFF4C0.toInt()).full, slices = 8, stacks = 5, sy = 0.6f, emissive = 1.6f)
            b.sphere(cx + 5f, 9f, cz + 10.2f, 1.6f, HallArt.solid(0xFFFFF4C0.toInt()).full, slices = 8, stacks = 5, sy = 0.6f, emissive = 1.6f)
            b.box(cx - 1.5f, 15f, cz - 12f, cx + 1.5f, 15.3f, cz + 10f, BoxFaces(top = HallArt.solid(-1).full))
            lights += light(cx, 26f, cz + 12f, 0xFFFFE0A0.toInt(), 44f, 0.5f)
        }
    }

    // ------------------------------------------------------------------ decorations

    private fun decor(b: ModelBuilder, p: Prop, lights: MutableList<PointLight>) {
        val cx = p.centerX
        val cz = p.centerZ
        when (p.decor) {
            DecorStyle.PALM -> {
                val pot = HallArt.paint(0xFFB5763C.toInt()).full
                b.lathe(cx, 0f, cz, floatArrayOf(5f, 0f, 7f, 10f, 7.5f, 11f), 16, pot, gloss = 0.3f)
                val trunk = HallArt.paint(0xFF8B5A2B.toInt()).full
                for (k in 0 until 6) b.capsule(cx + sin(k * 0.5f) * 1.2f, 10f + k * 7f, cz, cx + sin((k + 1) * 0.5f) * 1.2f, 17f + k * 7f, cz, 2.2f - k * 0.2f, trunk, slices = 8)
                val leaf = HallArt.paint(0xFF2E9A3E.toInt(), 0.3f, 0.7f).full
                for (k in 0 until 7) {
                    val a = k * 0.9f
                    b.capsule(cx, 55f, cz, cx + cos(a) * 16f, 48f, cz + sin(a) * 16f, 1.8f, leaf, slices = 6)
                }
            }
            DecorStyle.LAVA_LAMP -> {
                val base = HallArt.chrome.full
                b.lathe(cx, 0f, cz, floatArrayOf(4f, 0f, 4.5f, 6f, 2.5f, 12f), 16, base, gloss = 0.9f)
                b.lathe(cx, 12f, cz, floatArrayOf(2.5f, 0f, 3.6f, 10f, 2.6f, 18f), 16, HallArt.solid(0xFFFF6A2A.toInt()).full, emissive = 1.2f)
                b.lathe(cx, 30f, cz, floatArrayOf(2.6f, 0f, 1.6f, 4f), 12, base, gloss = 0.9f)
                lights += light(cx, 22f, cz + 4f, 0xFFFF6A2A.toInt(), 40f, 1f)
            }
            DecorStyle.GUMBALL -> {
                val red = HallArt.paint(0xFFE8323C.toInt()).full
                b.lathe(cx, 0f, cz, floatArrayOf(3f, 0f, 4.5f, 14f, 5.5f, 16f), 14, red, gloss = 0.6f)
                for (k in 0 until 26) {
                    val c = intArrayOf(0xFFFF4FA8.toInt(), 0xFFFFE14D.toInt(), 0xFF39E6F2.toInt(), 0xFF5CF08A.toInt(), 0xFFFFFFFF.toInt())[k % 5]
                    b.add(MachineKit.ball(c), xf.set(cx + MachineKit.jitter(k, 31, 3.5f), 19f + MachineKit.jitter(k, 32, 3.5f), cz + MachineKit.jitter(k, 33, 3.5f), scale = 1.2f))
                }
                b.sphere(cx, 22f, cz, 6.5f, HallArt.glass.full, slices = 14, stacks = 10, gloss = 1f)
            }
            DecorStyle.FLAMINGO -> {
                b.box(cx - 1f, 0f, cz - 1f, cx + 1f, 20f, cz + 1f, BoxFaces.all(HallArt.darkMetal.full))
                b.quad(cx - 10f, p.height + 4f, cz + 1.2f, cx + 10f, p.height + 4f, cz + 1.2f, cx + 10f, 16f, cz + 1.2f, cx - 10f, 16f, cz + 1.2f, flamingoNeon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.6f, cull = false)
                lights += light(cx, 30f, cz + 8f, 0xFFFF4FA8.toInt(), 50f, 0.9f)
            }
            DecorStyle.FISH_TANK -> {
                val stand = HallArt.wood(0xFF3A2A22.toInt(), 3).full
                b.box(p.x0, 0f, p.z0, p.x1, 18f, p.z1, BoxFaces(front = stand, left = stand, right = stand, top = stand, gloss = 0.4f))
                b.box(p.x0 + 1f, 18f, p.z0 + 1f, p.x1 - 1f, 20f, p.z1 - 1f, BoxFaces(top = HallArt.solid(0xFFE8D8A0.toInt()).full))
                b.quad(p.x0 + 1f, p.height, p.z0 + 1.2f, p.x1 - 1f, p.height, p.z0 + 1.2f, p.x1 - 1f, 18f, p.z0 + 1.2f, p.x0 + 1f, 18f, p.z0 + 1.2f, HallArt.paint(0xFF1A6AB0.toInt()).full, 0f, 0f, 1f, emissive = 0.7f)
                for (k in 0 until 4) b.capsule(p.x0 + 6f + k * 9f, 20f, p.z0 + 5f, p.x0 + 7f + k * 9f, 30f + (k % 2) * 6f, p.z0 + 6f, 0.8f, HallArt.paint(0xFF2E9A3E.toInt()).full, slices = 5)
                val water = TexPaint(64, 64).let { tp ->
                    tp.fill(alpha(0xFF4DB8FF.toInt(), 0.35f))
                    tp.toTexture().also { tp.recycle() }
                }
                val w = water.full
                b.quad(p.x0, p.height, p.z1, p.x1, p.height, p.z1, p.x1, 18f, p.z1, p.x0, 18f, p.z1, w, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
                b.quad(p.x0, p.height, p.z0, p.x0, p.height, p.z1, p.x0, 18f, p.z1, p.x0, 18f, p.z0, w, -1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
                b.quad(p.x1, p.height, p.z1, p.x1, p.height, p.z0, p.x1, 18f, p.z0, p.x1, 18f, p.z1, w, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
                b.quad(p.x0, p.height, p.z0, p.x1, p.height, p.z0, p.x1, p.height, p.z1, p.x0, p.height, p.z1, w, 0f, 1f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
                lights += light(cx, p.height - 4f, cz + 6f, 0xFF4DB8FF.toInt(), 50f, 1f)
            }
            DecorStyle.JUKEBOX -> {
                val wood = HallArt.wood(0xFF7A3A1A.toInt(), 3).full
                b.box(p.x0, 0f, p.z0, p.x1, p.height - 12f, p.z1, BoxFaces(left = wood, right = wood, top = wood, back = wood, gloss = 0.5f))
                val face = TexPaint(128, 192).let { tp ->
                    tp.vgrad(0f, 0f, 128f, 192f, 0xFF3A1060.toInt(), 0xFF120420.toInt())
                    for (k in 0 until 4) tp.round(14f + k * 26f, 20f, 18f, 90f, 9f, intArrayOf(0xFFFF4FA8.toInt(), 0xFFFFD84D.toInt(), 0xFF39E6F2.toInt(), 0xFF5CF08A.toInt())[k])
                    tp.round(20f, 120f, 88f, 50f, 8f, 0xFF16141C.toInt())
                    tp.glowText("♪ HITS ♪", 64f, 152f, 18f, -1, 0xFFFF4FA8.toInt(), 5f, Fonts.display)
                    tp.toTexture().also { tp.recycle() }
                }
                b.quad(p.x0 + 1f, p.height - 12f, p.z1 + 0.1f, p.x1 - 1f, p.height - 12f, p.z1 + 0.1f, p.x1 - 1f, 2f, p.z1 + 0.1f, p.x0 + 1f, 2f, p.z1 + 0.1f, face.full, 0f, 0f, 1f, emissive = 1.1f, gloss = 0.6f)
                val arch = ModelBuilder().cylinder(0f, 0f, -(p.z1 - p.z0) / 2f, (p.z1 - p.z0) / 2f, (p.x1 - p.x0) / 2f, 16, wood, top = HallArt.solid(0xFFFFD84D.toInt()).full, gloss = 0.5f).build()
                b.add(arch, xf.set(cx, p.height - 12f, cz, pitch = PI.toFloat() / 2f))
                lights += light(cx, 30f, p.z1 + 8f, 0xFFFF4FA8.toInt(), 60f, 1f)
            }
            DecorStyle.PLUSH_BEAR -> {
                b.add(Plush3D.model(Catalog.plushies.first()), xf.set(cx, 0f, cz, scale = 2.1f))
            }
            DecorStyle.TROPHY_CASE -> {
                val wood = HallArt.wood(0xFF3A2A22.toInt(), 3).full
                b.box(p.x0, 0f, p.z0, p.x1, 16f, p.z1, BoxFaces(front = wood, left = wood, right = wood, top = wood, gloss = 0.4f))
                b.box(p.x0, p.height - 3f, p.z0, p.x1, p.height, p.z1, BoxFaces(front = wood, left = wood, right = wood, top = wood, gloss = 0.4f))
                val gold = HallArt.paint(0xFFFFC83D.toInt(), 0.4f, 0.7f).full
                for (k in 0 until 3) {
                    val tx = p.x0 + 7f + k * 10f
                    val tz = cz
                    b.lathe(tx, 16f, tz, floatArrayOf(3f, 0f, 3f, 2f, 0.8f, 3f, 0.8f, 7f, 3.2f, 9f, 3.6f, 14f, 0.2f, 14.2f), 14, gold, gloss = 1f)
                    b.lathe(tx, 34f, tz, floatArrayOf(2.4f, 0f, 2.4f, 1.5f, 0.6f, 2.5f, 0.6f, 5f, 2.4f, 6.5f, 2.7f, 10f, 0.2f, 10.2f), 14, gold, gloss = 1f)
                }
                b.box(p.x0 + 1f, 32f, p.z0 + 1f, p.x1 - 1f, 33f, p.z1 - 1f, BoxFaces(top = HallArt.glass.full))
                val g = HallArt.glass.full
                b.quad(p.x0, p.height - 3f, p.z1, p.x1, p.height - 3f, p.z1, p.x1, 16f, p.z1, p.x0, 16f, p.z1, g, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
                lights += light(cx, p.height - 6f, cz + 6f, 0xFFFFE8B0.toInt(), 44f, 0.9f)
            }
            DecorStyle.DISCO_BALL -> {
                val mirror = TexPaint(64, 64).let { tp ->
                    for (y in 0 until 64 step 4) for (x in 0 until 64 step 4) {
                        val v = 0.5f + hash01(x, y) * 0.5f
                        tp.rect(x.toFloat(), y.toFloat(), 3.5f, 3.5f, lift(0xFF8A8C98.toInt(), v))
                    }
                    tp.toTexture().also { tp.recycle() }
                }
                b.capsule(cx, p.height + 60f, cz, cx, p.height + 8f, cz, 0.3f, HallArt.darkMetal.full)
                b.sphere(cx, p.height, cz, 8f, mirror.full, slices = 18, stacks = 12, gloss = 1f)
            }
            null -> Unit
        }
    }

    private val flamingoNeon: Texture by lazy {
        val tp = TexPaint(128, 160)
        tp.clear(0)
        val pink = 0xFFFF4FA8.toInt()
        tp.glow(10f, alpha(pink, 0.6f)) {
            oval(60f, 70f, 26f, 16f, -1)
            line(72f, 60f, 82f, 20f, 6f, -1)
            circle(84f, 16f, 7f, -1)
            line(58f, 86f, 56f, 150f, 4f, -1)
        }
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.style = android.graphics.Paint.Style.STROKE
        tp.paint.strokeWidth = 4f
        tp.paint.color = lift(pink, 0.4f)
        tp.canvas.drawOval(34f, 54f, 86f, 86f, tp.paint)
        tp.canvas.drawLine(72f, 60f, 82f, 20f, tp.paint)
        tp.canvas.drawCircle(84f, 16f, 7f, tp.paint)
        tp.canvas.drawLine(58f, 86f, 56f, 150f, tp.paint)
        tp.toTexture().also { tp.recycle() }
    }
}
