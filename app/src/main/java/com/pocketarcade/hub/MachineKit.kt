package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import kotlin.math.PI

/** Materials and small parts shared by every cabinet of a kind (nets, lanes, balls, claws...). */
object MachineKit {
    /** Diamond mesh netting (alpha-blended). */
    val net: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.clear(0)
        for (k in -8..16) {
            tp.line(k * 16f, 0f, k * 16f + 128f, 128f, 1.6f, alpha(-1, 0.55f), round = false)
            tp.line(k * 16f, 128f, k * 16f + 128f, 0f, 1.6f, alpha(-1, 0.55f), round = false)
        }
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    val velvet: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFF5A1640.toInt(), 0xFF2E0A22.toInt())
        tp.grain(0.12f, 17)
        tp.toTexture().also { tp.recycle() }
    }

    val laneWood: Texture by lazy { HallArt.wood(0xFFD69A5A.toInt(), 5) }

    /** Skee-ball target: concentric scoring rings with their values. */
    val skeeRings: Texture by lazy {
        val w = 256
        val h = 256
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), 0xFF22306E.toInt(), 0xFF141C44.toInt())
        val cx = w / 2f
        val cy = h * 0.55f
        val colors = intArrayOf(0xFF4DA6FF.toInt(), 0xFF8A4FFF.toInt(), 0xFFFF3FA4.toInt(), 0xFFFF9A3C.toInt(), 0xFFFFE14D.toInt(), 0xFFFF4D4D.toInt())
        val radii = floatArrayOf(118f, 96f, 76f, 56f, 36f, 16f)
        val points = intArrayOf(10, 20, 30, 40, 50, 100)
        for (i in radii.indices) {
            tp.circle(cx, cy, radii[i], dim(colors[i], 0.55f))
            tp.circle(cx, cy + 3f, radii[i] - 4f, dim(colors[i], 0.35f))
            tp.ring(cx, cy, radii[i] - 1.5f, 3f, colors[i])
        }
        tp.circle(cx, cy, 10f, 0xFF050308.toInt())
        for (i in 0 until radii.size - 1) {
            val r = (radii[i] + radii[i + 1]) / 2f
            tp.text(points[i].toString(), cx + (if (i % 2 == 0) r else -r), cy + 6f, 16f, -1, Fonts.display)
        }
        tp.circle(w - 26f, 26f, 14f, 0xFFFFC83D.toInt())
        tp.circle(w - 26f, 26f, 9f, 0xFF050308.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    val court: Texture by lazy {
        val tp = TexPaint(128, 256)
        for (x in 0 until 128 step 16) {
            val c = if ((x / 16) % 2 == 0) 0xFFD8A266.toInt() else 0xFFCC955A.toInt()
            tp.rect(x.toFloat(), 0f, 16f, 256f, c)
            tp.rect(x.toFloat(), 0f, 1f, 256f, 0xFF9A6A3A.toInt())
        }
        tp.rect(40f, 0f, 48f, 90f, alpha(0xFFFF3B30.toInt(), 0.45f))
        tp.strokeRound(40f, -10f, 48f, 100f, 2f, 3f, -1)
        tp.ring(64f, 90f, 24f, 3f, -1)
        tp.grain(0.05f, 3)
        tp.toTexture().also { tp.recycle() }
    }

    val hoopBoard: Texture by lazy {
        val tp = TexPaint(192, 128)
        tp.vgrad(0f, 0f, 192f, 128f, 0xFFF4F8FF.toInt(), 0xFFC8D6EA.toInt())
        tp.strokeRound(4f, 4f, 184f, 120f, 6f, 6f, 0xFFE8323C.toInt())
        tp.strokeRound(66f, 56f, 60f, 50f, 2f, 5f, 0xFFE8323C.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    val hockeySurface: Texture by lazy {
        val w = 128
        val h = 256
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), 0xFFF2F8FF.toInt(), 0xFFD8E8FA.toInt())
        for (y in 6 until h step 10) for (x in 6 until w step 10) tp.circle(x.toFloat(), y.toFloat(), 1f, 0xFF9DB4D0.toInt())
        tp.rect(0f, h / 2f - 2f, w.toFloat(), 4f, 0xFFE8323C.toInt())
        tp.ring(w / 2f, h / 2f, 22f, 3f, 0xFFE8323C.toInt())
        tp.ring(w / 2f, 0f, 30f, 3f, 0xFF2F5BE0.toInt())
        tp.ring(w / 2f, h.toFloat(), 30f, 3f, 0xFF2F5BE0.toInt())
        tp.rect(0f, h * 0.25f, w.toFloat(), 3f, 0xFF2F5BE0.toInt())
        tp.rect(0f, h * 0.75f, w.toFloat(), 3f, 0xFF2F5BE0.toInt())
        tp.rect(w / 2f - 22f, 0f, 44f, 5f, 0xFF101018.toInt())
        tp.rect(w / 2f - 22f, h - 5f, 44f, 5f, 0xFF101018.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /** Hole positions for the whack-a-mole table top (fractions of width and depth). */
    val whackHoles = arrayOf(0.2f to 0.62f, 0.35f to 0.38f, 0.5f to 0.66f, 0.65f to 0.38f, 0.8f to 0.62f)

    fun whackTop(color: Int): Texture {
        val w = 256
        val h = 208
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(color, 0.1f), dim(color, 0.7f))
        tp.grain(0.06f, 21)
        for ((fx, fz) in whackHoles) {
            val x = fx * w
            val y = fz * h
            tp.circle(x, y, 30f, 0xFF8A5A2B.toInt())
            tp.circle(x, y, 26f, 0xFF3A2414.toInt())
            tp.radial(x, y + 3f, 24f, 0xFF050303.toInt(), 0xFF2A1A0E.toInt())
        }
        tp.rect(0f, 0f, w.toFloat(), 8f, 0xFF8B5A2B.toInt())
        return tp.toTexture().also { tp.recycle() }
    }

    val deck: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.vgrad(0f, 0f, 128f, 128f, 0xFF1E2A6C.toInt(), 0xFF101640.toInt())
        for (y in 0 until 128 step 16) tp.rect(0f, y.toFloat(), 128f, 1.5f, 0xFF2E3C8E.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    val gold: Texture by lazy { HallArt.paint(0xFFFFC83D.toInt(), 0.35f, 0.7f) }
    val coinFace: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.circle(32f, 32f, 32f, 0xFFE0A020.toInt())
        tp.circle(32f, 32f, 26f, 0xFFFFD04A.toInt())
        tp.ring(32f, 32f, 20f, 2f, 0xFFE0A020.toInt())
        tp.text("★", 32f, 42f, 26f, 0xFFE0A020.toInt(), Fonts.heavy)
        tp.toTexture().also { tp.recycle() }
    }

    val seatLeather: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFF2C2A32.toInt(), 0xFF141218.toInt())
        for (x in 8 until 64 step 16) tp.rect(x.toFloat(), 0f, 1f, 64f, 0xFF3C3A44.toInt())
        tp.grain(0.06f, 5)
        tp.toTexture().also { tp.recycle() }
    }

    val rubber: Texture by lazy { HallArt.paint(0xFF1A1A20.toInt(), 0.08f, 0.8f) }

    val moleFur: Texture by lazy { HallArt.paint(0xFF8B5A2B.toInt(), 0.15f, 0.8f) }

    /** Mole head: fur with eyes, a pink nose and buck teeth at the front. */
    val moleFace: Texture by lazy {
        val tp = TexPaint(128, 64)
        tp.vgrad(0f, 0f, 128f, 64f, 0xFF9C6A38.toInt(), 0xFF6E4420.toInt())
        val cx = 32f
        tp.oval(cx, 40f, 16f, 11f, 0xFFD9A066.toInt())
        for (s in intArrayOf(-1, 1)) {
            tp.oval(cx + s * 9f, 26f, 3.2f, 4.2f, 0xFF120C08.toInt())
            tp.circle(cx + s * 9f - 1f, 24.5f, 1.2f, -1)
        }
        tp.oval(cx, 36f, 5f, 3.5f, 0xFFFF6FA0.toInt())
        tp.rect(cx - 3f, 43f, 2.6f, 4f, -1)
        tp.rect(cx + 0.4f, 43f, 2.6f, 4f, -1)
        tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ small parts

    private fun colored(color: Int) = HallArt.paint(color, 0.25f, 0.75f).full

    /** A glossy ball of the given colour, radius 1 (scale with an [Xform]). */
    fun ball(color: Int): Model = balls.getOrPut(color) {
        ModelBuilder().sphere(0f, 0f, 0f, 1f, colored(color), slices = 16, stacks = 10, gloss = 0.7f).build()
    }
    private val balls = HashMap<Int, Model>()

    val basketball: Model by lazy {
        val tp = TexPaint(128, 64)
        tp.fill(0xFFE8782A.toInt())
        tp.grain(0.08f, 3)
        for (x in intArrayOf(0, 32, 64, 96)) tp.rect(x.toFloat(), 0f, 2.5f, 64f, 0xFF3A1A08.toInt())
        tp.rect(0f, 31f, 128f, 2.5f, 0xFF3A1A08.toInt())
        val tex = tp.toTexture().also { tp.recycle() }
        ModelBuilder().sphere(0f, 0f, 0f, 1f, tex.full, slices = 16, stacks = 10, gloss = 0.25f).build()
    }

    val coin: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, -0.35f, 0.35f, 2.2f, 12, gold.full, top = coinFace.full, gloss = 0.9f).build()
    }

    val mole: Model by lazy {
        val b = ModelBuilder()
        b.cylinder(0f, 0f, -9f, 0f, 2.6f, 12, moleFur.full, gloss = 0.1f)
        b.sphere(0f, 0f, 0f, 2.9f, moleFace.full, slices = 16, stacks = 10, sy = 1.05f, gloss = 0.1f)
        b.build()
    }

    val mallet: Model by lazy {
        val b = ModelBuilder()
        b.capsule(0f, 0f, 0f, 0f, 0f, 9f, 0.5f, HallArt.wood().full)
        val head = ModelBuilder().cylinder(0f, 0f, -3f, 3f, 1.8f, 12, colored(0xFFE8323C.toInt()), top = colored(0xFFFFE0A0.toInt()), bottom = colored(0xFFFFE0A0.toInt()), gloss = 0.4f).build()
        b.add(head, Xform().set(0f, 0f, 9.5f, roll = PI.toFloat() / 2f))
        b.build()
    }

    /** Claw: hub, cable stub and three hooked prongs, hanging from y = 0 down to about -9. */
    val claw: Model by lazy {
        val b = ModelBuilder()
        val metal = HallArt.chrome.full
        b.cylinder(0f, 0f, -3f, 0f, 1.8f, 12, metal, top = metal, bottom = metal, gloss = 0.9f)
        for (k in 0 until 3) {
            val a = PI.toFloat() / 2f + k * 2f * PI.toFloat() / 3f
            val c = kotlin.math.cos(a)
            val s = kotlin.math.sin(a)
            b.capsule(c * 1.4f, -2.5f, s * 1.4f, c * 3.6f, -7f, s * 3.6f, 0.35f, metal, slices = 6, gloss = 0.9f)
            b.capsule(c * 3.6f, -7f, s * 3.6f, c * 2.4f, -9f, s * 2.4f, 0.35f, metal, slices = 6, gloss = 0.9f)
        }
        b.build()
    }

    val puck: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, 0f, 0.9f, 2.2f, 14, colored(0xFFE8323C.toInt()), top = colored(0xFFFF5A5A.toInt()), gloss = 0.6f).build()
    }

    fun hockeyMallet(color: Int): Model = mallets.getOrPut(color) {
        val c = colored(color)
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 1.6f, 3.2f, 16, c, top = c, gloss = 0.6f)
            .cylinder(0f, 0f, 1.6f, 4.6f, 1.2f, 10, c, top = c, gloss = 0.6f)
            .build()
    }
    private val mallets = HashMap<Int, Model>()

    /** Arcade push button: bezel ring and a coloured cap that can glow. */
    fun button(color: Int, radius: Float): Model = buttons.getOrPut(color * 31 + radius.toInt()) {
        val cap = colored(color)
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 0.8f, radius * 1.25f, 14, HallArt.darkMetal.full, top = HallArt.darkMetal.full)
            .sphere(0f, 0.8f, 0f, radius, cap, slices = 14, stacks = 6, sy = 0.45f, gloss = 0.8f, yFrom = 0f)
            .build()
    }
    private val buttons = HashMap<Int, Model>()

    val joystick: Model by lazy {
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 0.6f, 2f, 12, HallArt.darkMetal.full, top = HallArt.darkMetal.full)
            .capsule(0f, 0f, 0f, 0f, 4.5f, 0f, 0.4f, HallArt.chrome.full)
            .sphere(0f, 5.4f, 0f, 1.5f, colored(0xFFE8323C.toInt()), gloss = 0.8f)
            .build()
    }

    /** Random prize-wall and claw-pile arrangement helper. */
    fun jitter(i: Int, salt: Int, range: Float) = (hash01(i, salt) - 0.5f) * 2f * range
}
