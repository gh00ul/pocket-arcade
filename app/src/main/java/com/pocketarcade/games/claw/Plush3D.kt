package com.pocketarcade.games.claw

import com.pocketarcade.data.Plush
import com.pocketarcade.data.PlushShape
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb

/**
 * Soft 3D plush toys built from spheres and capsules, with a painted face. Models are about
 * two units across per unit of [Plush.radius] / 12 and sit on the ground at y = 0, facing +z.
 */
object Plush3D {
    private val models = HashMap<String, Model>()
    private val fabrics = HashMap<Int, Texture>()
    private val faces = HashMap<String, Texture>()

    /** A plush of size 1 (body radius ~6 units) sitting on y = 0; scale it with an [Xform]. */
    fun model(p: Plush): Model = models.getOrPut(p.id) { build(p) }

    private val centred = HashMap<String, Pair<Model, Float>>()

    /** [p] moved so the middle of its bounds is the origin, with the radius that encloses it (roughly). */
    fun centred(p: Plush): Pair<Model, Float> = centred.getOrPut(p.id) {
        val m = model(p)
        val cx = (m.minX + m.maxX) / 2f
        val cy = (m.minY + m.maxY) / 2f
        val cz = (m.minZ + m.maxZ) / 2f
        val radius = maxOf(m.maxX - m.minX, m.maxY - m.minY) / 2f
        ModelBuilder().add(m, Xform().set(-cx, -cy, -cz)).build() to radius
    }

    /** Soft fabric: the colour with a gentle fuzz. */
    private fun fabric(color: Int): Region = fabrics.getOrPut(color) {
        val tp = TexPaint(32, 32)
        tp.vgrad(0f, 0f, 32f, 32f, mixArgb(color, -1, 0.18f), mixArgb(color, 0xFF000000.toInt(), 0.12f))
        tp.grain(0.08f, color)
        tp.toTexture().also { tp.recycle() }
    }.full

    /**
     * The head texture wraps round a sphere: u goes once round (the face at a quarter turn,
     * which points at +z), v runs from the crown down to the chin.
     */
    private fun face(p: Plush): Region = faces.getOrPut(p.id) {
        val w = 256
        val h = 128
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), mixArgb(p.main, -1, 0.15f), mixArgb(p.main, 0xFF000000.toInt(), 0.1f))
        tp.grain(0.07f, p.main)
        val cx = w * 0.25f
        val cy = h * 0.55f
        val ink = 0xFF1A1320.toInt()
        // Eyes with highlights, a little mouth and blush.
        for (s in intArrayOf(-1, 1)) {
            tp.oval(cx + s * 17f, cy - 6f, 6.5f, 8.5f, ink)
            tp.circle(cx + s * 17f - 2f, cy - 9f, 2.4f, -1)
            tp.oval(cx + s * 27f, cy + 8f, 7f, 4f, 0x66FF6FA0)
        }
        when (p.shape) {
            PlushShape.DUCK -> tp.oval(cx, cy + 9f, 12f, 6f, 0xFFFF9A3C.toInt())
            PlushShape.CAT -> {
                tp.oval(cx, cy + 4f, 3f, 2f, 0xFFFF77C8.toInt())
                for (s in intArrayOf(-1, 1)) tp.line(cx + s * 6f, cy + 6f, cx + s * 22f, cy + 3f, 1.5f, ink)
            }
            PlushShape.BEAR, PlushShape.BUNNY -> {
                tp.oval(cx, cy + 9f, 11f, 8f, mixArgb(p.accent, -1, 0.3f))
                tp.oval(cx, cy + 5f, 4f, 3f, ink)
            }
            else -> {
                tp.line(cx - 6f, cy + 8f, cx, cy + 12f, 2.5f, ink)
                tp.line(cx, cy + 12f, cx + 6f, cy + 8f, 2.5f, ink)
            }
        }
        if (p.rare) for (k in 0 until 6) tp.circle(cx - 60f + k * 24f, 18f, 2.5f, -1)
        tp.toTexture().also { tp.recycle() }
    }.full

    private fun build(p: Plush): Model {
        val b = ModelBuilder()
        val main = fabric(p.main)
        val accent = fabric(p.accent)
        val head = face(p)
        val gloss = if (p.rare) 0.8f else 0.05f
        fun sphere(x: Float, y: Float, z: Float, r: Float, t: Region, sy: Float = 1f) {
            b.sphere(x, y, z, r, t, slices = 14, stacks = 8, sy = sy, gloss = gloss)
        }
        // The head is a sphere rotated so its texture's face looks forward (+z).
        fun headAt(y: Float, r: Float, sy: Float = 1f) {
            val hm = ModelBuilder().sphere(0f, 0f, 0f, r, head, slices = 18, stacks = 12, sy = sy, gloss = gloss).build()
            b.add(hm, Xform().set(0f, y, 0f))
        }
        when (p.shape) {
            PlushShape.BEAR -> {
                sphere(0f, 5f, 0f, 5.5f, main, 0.95f)
                headAt(12.5f, 5f)
                sphere(-3.8f, 16.5f, -0.5f, 1.8f, main); sphere(3.8f, 16.5f, -0.5f, 1.8f, main)
                sphere(-5f, 4f, 2f, 1.9f, main); sphere(5f, 4f, 2f, 1.9f, main)
                sphere(-2.6f, 1.2f, 3f, 2f, accent); sphere(2.6f, 1.2f, 3f, 2f, accent)
            }
            PlushShape.BUNNY -> {
                sphere(0f, 5f, 0f, 5.2f, main, 0.95f)
                headAt(12f, 4.8f)
                b.capsule(-1.8f, 15.5f, -0.5f, -2.8f, 22f, -1f, 1.3f, accent)
                b.capsule(1.8f, 15.5f, -0.5f, 2.8f, 22f, -1f, 1.3f, accent)
                sphere(0f, 3f, -5f, 1.8f, fabric(Pal.WHITE))
            }
            PlushShape.CAT -> {
                sphere(0f, 5f, 0f, 5.2f, main, 0.95f)
                headAt(12f, 5f)
                b.cylinder(-3f, -0.5f, 15.2f, 19f, 1.8f, 6, accent, topRadius = 0.1f)
                b.cylinder(3f, -0.5f, 15.2f, 19f, 1.8f, 6, accent, topRadius = 0.1f)
                b.capsule(3f, 3f, -4f, 6f, 9f, -6f, 1f, accent)
            }
            PlushShape.DUCK -> {
                sphere(0f, 5f, 0f, 5.8f, main, 0.9f)
                headAt(12f, 4.6f)
                sphere(-5f, 6f, -1f, 2.2f, main, 0.6f); sphere(5f, 6f, -1f, 2.2f, main, 0.6f)
            }
            PlushShape.GHOST -> {
                b.sphere(0f, 6f, 0f, 6f, head, slices = 18, stacks = 12, sy = 1.35f, gloss = gloss)
                for (k in 0 until 5) {
                    val a = k * 1.2566f
                    sphere(kotlin.math.cos(a) * 4.2f, 0.9f, kotlin.math.sin(a) * 4.2f, 1.6f, main)
                }
            }
            PlushShape.SLIME -> b.sphere(0f, 3.4f, 0f, 6.4f, head, slices = 18, stacks = 12, sy = 0.62f, gloss = 0.5f)
            PlushShape.DINO -> {
                sphere(0f, 5f, 0f, 5.6f, main, 0.95f)
                headAt(12.5f, 4.8f, 1.05f)
                for (k in 0 until 4) b.cylinder(0f, -4.5f + k * 1.5f, 13f - k * 3f + 6f, 16f - k * 3f + 6f, 1.3f, 5, accent, topRadius = 0.1f)
                b.capsule(0f, 3f, -5f, 0f, 1.5f, -10f, 1.8f, main)
            }
            PlushShape.OCTO -> {
                b.sphere(0f, 8f, 0f, 6f, head, slices = 18, stacks = 12, sy = 1.1f, gloss = gloss)
                for (k in 0 until 6) {
                    val a = k * 1.047f
                    b.capsule(kotlin.math.cos(a) * 3f, 3f, kotlin.math.sin(a) * 3f, kotlin.math.cos(a) * 6.5f, 0.8f, kotlin.math.sin(a) * 6.5f, 1.2f, accent)
                }
            }
            PlushShape.FROG -> {
                b.sphere(0f, 4.5f, 0f, 6f, head, slices = 18, stacks = 12, sy = 0.8f, gloss = gloss)
                sphere(-3f, 9f, 1f, 2f, fabric(Pal.WHITE)); sphere(3f, 9f, 1f, 2f, fabric(Pal.WHITE))
                sphere(-3f, 9.2f, 2.8f, 0.8f, fabric(0xFF1A1320.toInt())); sphere(3f, 9.2f, 2.8f, 0.8f, fabric(0xFF1A1320.toInt()))
                b.cylinder(0f, 0f, 9.5f, 11.5f, 2.2f, 8, fabric(Pal.GOLD), topRadius = 2.6f)
            }
            PlushShape.WHALE -> {
                b.sphere(0f, 5f, 0f, 6.5f, head, slices = 18, stacks = 12, sy = 0.78f, gloss = gloss)
                b.capsule(0f, 5f, -6f, 0f, 7f, -10f, 1.6f, main)
                sphere(-2.5f, 7f, -10.5f, 1.6f, main, 0.5f); sphere(2.5f, 7f, -10.5f, 1.6f, main, 0.5f)
                b.cylinder(0f, 0f, 9.5f, 13f, 0.4f, 5, fabric(Pal.CYAN))
            }
        }
        return b.build()
    }
}
