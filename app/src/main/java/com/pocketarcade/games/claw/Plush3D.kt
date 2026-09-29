package com.pocketarcade.games.claw

import com.pocketarcade.data.Plush
import com.pocketarcade.data.PlushShape
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Poly
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb

/**
 * How plush toys are shaded and toned. Baked occlusion darkens the underside and the foot of a toy
 * so a pile reads as soft, weighty forms instead of flat cut-outs, and pale fabric is capped so a
 * white ghost or a grey cat lit from above sits under the bloom threshold instead of glowing.
 * Constants, not magic numbers: both only ever darken paint.
 */
internal object PlushShade {
    /** Brightness (of the paint) at the very bottom of a toy, facing down. 1 = no darkening. */
    const val AO_MIN = 0.72f
    /** How much of the darkening comes from the surface facing down (the rest is height). */
    const val AO_NORMAL_SHARE = 0.55f
    /** Fabric never gets brighter than this (0..1 of a channel): pale plush stays a soft off-white. */
    const val PALE_CAP = 0.84f

    /** Scales [argb] so that its brightest channel is at most [PALE_CAP]. */
    fun soften(argb: Int): Int {
        val r = argb shr 16 and 255
        val g = argb shr 8 and 255
        val b = argb and 255
        val top = maxOf(r, maxOf(g, b)) / 255f
        if (top <= PALE_CAP) return argb
        val k = PALE_CAP / top
        return (argb and -0x1000000) or ((r * k).toInt() shl 16) or ((g * k).toInt() shl 8) or (b * k).toInt()
    }

    /** Brightness for a vertex at height fraction [h] (0 bottom, 1 top) with normal y [ny]. */
    fun shadeAt(h: Float, ny: Float): Float {
        val facing = (ny * 0.5f + 0.5f).coerceIn(0f, 1f)
        val up = (h * 1.6f).coerceIn(0f, 1f)
        val k = AO_NORMAL_SHARE * facing + (1f - AO_NORMAL_SHARE) * up
        return AO_MIN + (1f - AO_MIN) * k
    }

    /** A copy of [m] with occlusion baked into every polygon that doesn't glow. */
    fun bake(m: Model): Model {
        val span = (m.maxY - m.minY).coerceAtLeast(1e-3f)
        return Model(
            m.polys.map { p ->
                if (p.emissive > 0f || p.shade != null) return@map p
                val sh = FloatArray(p.n) { i ->
                    val ny = p.vny?.get(i) ?: p.ny
                    shadeAt((p.ys[i] - m.minY) / span, ny)
                }
                Poly(p.region, p.n, p.xs, p.ys, p.zs, p.us, p.vs, p.nx, p.ny, p.nz, p.blend, p.emissive, p.cull, p.tint, p.gloss, p.vnx, p.vny, p.vnz, sh)
            },
        )
    }
}

/**
 * Soft 3D plush toys built from spheres and capsules, with a painted face. Models are about
 * two units across per unit of [Plush.radius] / 12 and sit on the ground at y = 0, facing +z.
 * Every toy carries baked occlusion and toned fabric (see [PlushShade]).
 */
object Plush3D {
    private val models = HashMap<String, Model>()
    private val fabrics = HashMap<Int, Texture>()
    private val faces = HashMap<String, Texture>()

    /** A plush of size 1 (body radius ~6 units) sitting on y = 0; scale it with an [Xform]. */
    fun model(p: Plush): Model = models.getOrPut(p.id) { PlushShade.bake(build(p)) }

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
        val tone = PlushShade.soften(color)
        tp.vgrad(0f, 0f, 32f, 32f, PlushShade.soften(mixArgb(tone, -1, 0.14f)), mixArgb(tone, 0xFF000000.toInt(), 0.12f))
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
        val tone = PlushShade.soften(p.main)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), PlushShade.soften(mixArgb(tone, -1, 0.12f)), mixArgb(tone, 0xFF000000.toInt(), 0.1f))
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
