package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Lighting
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A kid's shadow on the floor: a soft contact blob under the feet, and the figure's silhouette
 * flattened along the light, away from the lamps above it. The flattening is worked out for the
 * two parts that make a kid's outline (legs and torso as one stripe, the head as a disc), each a
 * soft ellipse, so the shadow is drawn once with no overlapping meshes to darken twice, and costs
 * three quads a figure instead of another copy of the figure.
 *
 * Which way it leans is a blend of the lights that reach the spot, weighted by how strongly each
 * lights it (the same falloff the shader uses), with a faint fixed key light as the tiebreak. So
 * a kid under a downlight stands over their own shadow, and one between two lamps gets a short
 * shadow that swings smoothly as they walk, with no jump when the nearest lamp changes.
 * [region] is the soft round shadow texture. Allocation-free: this runs for every kid in view
 * every frame.
 */
class FigureShadow(private val region: Region) {
    companion object {
        /**
         * Lamps lower than this (the floor glow of each cabinet, the café till strip) light a kid
         * from the side rather than from above, and cast no floor shadow.
         */
        const val KEY_MIN_Y = 100f
        /** The height on a kid the lean is measured at: about their middle. */
        const val REF_HEIGHT = 22f
        /** How much the fixed key light counts against the lamps (a downlight 45 units off weighs about this much). */
        const val KEY_WEIGHT = 0.15f
        /** The longest shadow, per unit of height: a kid's head lands at most this fraction of their height from their feet. */
        const val MAX_LEAN = 0.7f
        /** Below this lean the shadow is too short to tell from the contact blob, so only the blob is drawn. */
        const val MIN_LEAN = 0.06f

        /** Contact blob: size (the figure's scale multiplies it) and how dark. */
        const val CONTACT_W = 20f
        const val CONTACT_D = 14f
        const val CONTACT_ALPHA = 0.5f
        /** Legs and torso: the height they reach, their width and how dark the stripe is. */
        const val BODY_TOP = 27f
        const val BODY_WIDTH = 13f
        const val BODY_ALPHA = 0.5f
        /** The head: how high its centre is, how big and how dark. */
        const val HEAD_Y = 35.5f
        const val HEAD_SIZE = 15f
        const val HEAD_ALPHA = 0.4f
        /** How far from a figure's feet any part of its shadow can reach, for culling. */
        const val REACH = MAX_LEAN * HEAD_Y + HEAD_SIZE
        /** Heights above the floor: the cast shadow just under the contact blob, both over the floor decals. */
        const val Y_CAST = 0.19f
        const val Y_CONTACT = 0.2f
    }

    private val lean = FloatArray(2)

    /**
     * Draws the shadow of a figure of size [scale] standing at ([x], [z]), lit by what [r]'s
     * lighting holds this frame.
     */
    fun draw(r: Renderer3D, x: Float, z: Float, scale: Float = 1f) {
        cast(r.lighting, x, z, lean)
        val lx = lean[0]
        val lz = lean[1]
        val len = sqrt(lx * lx + lz * lz)
        if (len >= MIN_LEAN) {
            // The long axis of a flat quad is its local z, which the angle turns to point along the lean.
            val angle = atan2(-lx, lz)
            val bodyMid = BODY_TOP / 2f * scale
            r.flat(
                x + lx * bodyMid, z + lz * bodyMid, Y_CAST, BODY_WIDTH * scale, (BODY_TOP * len + BODY_WIDTH) * scale,
                region, angle, Blend.ALPHA, alpha = BODY_ALPHA,
            )
            val headAt = HEAD_Y * scale
            r.flat(x + lx * headAt, z + lz * headAt, Y_CAST, HEAD_SIZE * scale, HEAD_SIZE * scale, region, angle, Blend.ALPHA, alpha = HEAD_ALPHA)
        }
        r.flat(x, z + 1f, Y_CONTACT, CONTACT_W * scale, CONTACT_D * scale, region, blend = Blend.ALPHA, alpha = CONTACT_ALPHA)
    }

    /**
     * Where the shadow of something standing at ([x], [z]) falls, as how far it lands from the
     * feet across the floor per unit of height (x in `out[0]`, z in `out[1]`): away from the lamps
     * above, blended by how much each lights the spot, and away from the hall's key light where
     * none reach. Never longer than [MAX_LEAN].
     */
    fun cast(l: Lighting, x: Float, z: Float, out: FloatArray) {
        // The key light: [Lighting.dirX] and friends point towards it, so the shadow runs the other way.
        val up = l.dirY.coerceAtLeast(0.2f)
        var sx = -l.dirX / up * KEY_WEIGHT
        var sz = -l.dirZ / up * KEY_WEIGHT
        var total = KEY_WEIGHT
        val pts = l.points
        for (i in pts.indices) {
            val p = pts[i]
            if (p.y < KEY_MIN_Y) continue
            val dx = x - p.x
            val dz = z - p.z
            val d2 = dx * dx + dz * dz
            if (d2 >= p.radius * p.radius) continue
            val fall = 1f - sqrt(d2) / p.radius
            val w = fall * fall * p.intensity
            // A point REF_HEIGHT up is lit from a lamp p.y high and dx across: it lands dx * REF / (p.y - REF) away.
            val k = w / (p.y - REF_HEIGHT)
            sx += dx * k
            sz += dz * k
            total += w
        }
        var ox = sx / total
        var oz = sz / total
        val len = sqrt(ox * ox + oz * oz)
        if (len > MAX_LEAN) {
            ox *= MAX_LEAN / len
            oz *= MAX_LEAN / len
        }
        out[0] = ox
        out[1] = oz
    }
}
