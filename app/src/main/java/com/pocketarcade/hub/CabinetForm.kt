package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region

/**
 * How cabinet boxes are shaped and shaded. Every strength is a constant here so the look can be
 * tuned in one place: the chamfers are subtle (they only have to catch the light along an edge),
 * and the occlusion only ever darkens paint that is already unlit, so nothing new can glow.
 */
object CabinetForm {
    /** Chamfer on a cabinet box's vertical and top edges, in hall units. */
    const val BEVEL = 0.8f
    /** A chamfer never takes more than this fraction of a box's thinnest side (thin panels stay panels). */
    const val BEVEL_MAX_FRACTION = 0.3f
    /** Boxes whose chamfer would come out smaller than this are left square. */
    const val MIN_BEVEL = 0.1f
    /** Gloss the chamfer strips get at least, so an edge catches a highlight even on matte paint. */
    const val BEVEL_GLOSS = 0.3f
    /** How much the paint darkens right at the floor (0 none, 1 black). */
    const val FLOOR_AO = 0.4f
    /** How far up a box the floor darkening reaches before it has faded out, in hall units. */
    const val FLOOR_AO_HEIGHT = 7f
}

/**
 * An axis-aligned box like [ModelBuilder.box], with its vertical and top edges chamfered and its
 * paint darkened towards the floor.
 *
 * - **Chamfers.** An edge is cut at 45° by [bevel] (at most a third of the box's thinnest side; 0
 *   for square edges, with just the occlusion)
 *   wherever both faces that meet there exist in [f]; a face that isn't there (a box tucked
 *   between two side panels) keeps a square edge on that side. Each cut is a narrow strip, and a
 *   small triangle closes each top corner where three cuts meet, so the surface stays closed.
 *   Faces keep their texture registered to the whole box, so painted art doesn't shift.
 * - **Occlusion.** With [floorAo] above zero the four sides (and the vertical cuts) fade from
 *   `1 - floorAo` at the bottom to plain paint [aoHeight] up, through a brightness stored per
 *   vertex ([com.pocketarcade.engine.r3d.Poly.shade]). By default that happens for boxes that
 *   stand on the floor. Glowing faces are never darkened.
 *
 * A box with all five faces goes from 5 polygons to 17 (4 vertical cuts, 4 top cuts, 4 corners),
 * and to 25 with occlusion (each side and vertical cut splits in two). The box's bounds don't
 * change, so culling is unaffected.
 */
fun ModelBuilder.beveledBox(
    x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, f: BoxFaces, tint: Int = -1,
    bevel: Float = CabinetForm.BEVEL,
    floorAo: Float = if (y0 <= 1f) CabinetForm.FLOOR_AO else 0f,
    aoHeight: Float = CabinetForm.FLOOR_AO_HEIGHT,
): ModelBuilder {
    val w = x1 - x0
    val d = z1 - z0
    val h = y1 - y0
    val limited = minOf(bevel, CabinetForm.BEVEL_MAX_FRACTION * minOf(w, minOf(d, h)))
    // A cut too small to see means square edges (which may still have occlusion at the foot).
    val b = if (limited >= CabinetForm.MIN_BEVEL) limited else 0f
    val ao = floorAo.coerceIn(0f, 0.9f)
    if (b == 0f && (ao == 0f || h < aoHeight)) return box(x0, y0, z0, x1, y1, z1, f, tint)

    val front = f.front
    val back = f.back
    val left = f.left
    val right = f.right
    val top = f.top
    val fl = front != null && left != null
    val fr = front != null && right != null
    val bl = back != null && left != null
    val br = back != null && right != null
    val tf = top != null && front != null
    val tb = top != null && back != null
    val tl = top != null && left != null
    val tr = top != null && right != null
    fun cut(on: Boolean) = if (on) b else 0f
    val cuts = b > 0f

    val tpu = f.texelsPerUnit
    // Texture size a face maps across: whole region, or texels per unit for wrapping regions.
    fun uw(r: Region, faceW: Float) = if (r.wrap && tpu > 0f) faceW * tpu else r.w.toFloat()
    fun vh(r: Region, faceH: Float) = if (r.wrap && tpu > 0f) faceH * tpu else r.h.toFloat()

    // Up to here the sides darken towards the floor; never more than most of the way up a short box.
    val ya = y0 + minOf(aoHeight, 0.6f * (y1 - b - y0))
    val stripGloss = maxOf(f.gloss, CabinetForm.BEVEL_GLOSS)

    /**
     * A vertical quad from ([ax], [az]) to ([bx], [bz]) between [yLo] and [yHi]. Its texture
     * columns run [u0]..[u1] and its rows follow the box's height ([vSize] rows over [h]). Split
     * at [ya] when the bottom is to darken.
     */
    fun wall(
        ax: Float, az: Float, bx: Float, bz: Float, yLo: Float, yHi: Float, r: Region, u0: Float, u1: Float, vSize: Float,
        nx: Float, nz: Float, emissive: Float, gloss: Float,
    ) {
        if (yHi - yLo < 1e-4f) return
        fun v(y: Float) = vSize * (y1 - y) / h
        if (ao > 0f && emissive <= 0f && yLo < ya - 1e-4f) {
            val yMid = minOf(ya, yHi)
            quad(
                ax, yMid, az, bx, yMid, bz, bx, yLo, bz, ax, yLo, az, r, nx, 0f, nz,
                u0 = u0, v0 = v(yMid), u1 = u1, v1 = v(yLo), emissive = emissive, tint = tint, gloss = gloss,
                shade = floatArrayOf(1f, 1f, 1f - ao, 1f - ao),
            )
            if (yHi > yMid + 1e-4f) {
                quad(ax, yHi, az, bx, yHi, bz, bx, yMid, bz, ax, yMid, az, r, nx, 0f, nz, u0 = u0, v0 = v(yHi), u1 = u1, v1 = v(yMid), emissive = emissive, tint = tint, gloss = gloss)
            }
        } else {
            quad(ax, yHi, az, bx, yHi, bz, bx, yLo, bz, ax, yLo, az, r, nx, 0f, nz, u0 = u0, v0 = v(yHi), u1 = u1, v1 = v(yLo), emissive = emissive, tint = tint, gloss = gloss)
        }
    }

    val fe = f.frontEmissive
    val te = f.topEmissive
    val sideTop = y1 - cut(top != null)

    // The four sides, each inset by whatever chamfers meet it and topped by its top chamfer.
    if (front != null) {
        val xa = x0 + cut(fl)
        val xb = x1 - cut(fr)
        val ru = uw(front, w)
        wall(xa, z1, xb, z1, y0, y1 - cut(tf), front, ru * (xa - x0) / w, ru * (xb - x0) / w, vh(front, h), 0f, 1f, fe, f.gloss)
    }
    if (back != null) {
        val xa = x1 - cut(br)
        val xb = x0 + cut(bl)
        val ru = uw(back, w)
        wall(xa, z0, xb, z0, y0, y1 - cut(tb), back, ru * (x1 - xa) / w, ru * (x1 - xb) / w, vh(back, h), 0f, -1f, 0f, f.gloss)
    }
    if (left != null) {
        val za = z0 + cut(bl)
        val zb = z1 - cut(fl)
        val ru = uw(left, d)
        wall(x0, za, x0, zb, y0, y1 - cut(tl), left, ru * (za - z0) / d, ru * (zb - z0) / d, vh(left, h), -1f, 0f, 0f, f.gloss)
    }
    if (right != null) {
        val za = z1 - cut(fr)
        val zb = z0 + cut(br)
        val ru = uw(right, d)
        wall(x1, za, x1, zb, y0, y1 - cut(tr), right, ru * (z1 - za) / d, ru * (z1 - zb) / d, vh(right, h), 1f, 0f, 0f, f.gloss)
    }
    if (top != null) {
        val xa = x0 + cut(tl)
        val xb = x1 - cut(tr)
        val za = z0 + cut(tb)
        val zb = z1 - cut(tf)
        val ru = uw(top, w)
        val rv = vh(top, d)
        quad(
            xa, y1, za, xb, y1, za, xb, y1, zb, xa, y1, zb, top, 0f, 1f, 0f,
            u0 = ru * (xa - x0) / w, v0 = rv * (za - z0) / d, u1 = ru * (xb - x0) / w, v1 = rv * (zb - z0) / d,
            emissive = te, tint = tint, gloss = f.gloss,
        )
    }

    val k = 0.70710678f
    // Vertical cuts down each corner where two sides meet: sample the side's own edge column. The
    // front ones glow as much as the front does, so a lit T-molding just rounds off, not dims.
    if (fl && cuts) {
        val r = left ?: front!!
        val u = if (r === left) uw(r, d) - 0.5f else 0.5f
        wall(x0, z1 - b, x0 + b, z1, y0, sideTop, r, u, u, vh(r, h), -k, k, fe, stripGloss)
    }
    if (fr && cuts) {
        val r = right ?: front!!
        val u = if (r === right) 0.5f else uw(r, w) - 0.5f
        wall(x1 - b, z1, x1, z1 - b, y0, sideTop, r, u, u, vh(r, h), k, k, fe, stripGloss)
    }
    if (bl && cuts) {
        val r = left ?: back!!
        val u = if (r === left) 0.5f else uw(r, w) - 0.5f
        wall(x0 + b, z0, x0, z0 + b, y0, sideTop, r, u, u, vh(r, h), -k, -k, 0f, stripGloss)
    }
    if (br && cuts) {
        val r = right ?: back!!
        val u = if (r === right) uw(r, d) - 0.5f else 0.5f
        wall(x1, z0 + b, x1 - b, z0, y0, sideTop, r, u, u, vh(r, h), k, -k, 0f, stripGloss)
    }

    // Chamfers along the top edges: sample the side's top row, its columns following the side's.
    if (tf && cuts) {
        val fr0 = front!!
        val xa = x0 + cut(fl)
        val xb = x1 - cut(fr)
        val ru = uw(fr0, w)
        quad(
            xa, y1, z1 - b, xb, y1, z1 - b, xb, y1 - b, z1, xa, y1 - b, z1, fr0, 0f, k, k,
            u0 = ru * (xa - x0) / w, v0 = 0.5f, u1 = ru * (xb - x0) / w, v1 = 0.5f, emissive = (fe + te) * 0.5f, tint = tint, gloss = stripGloss,
        )
    }
    if (tb && cuts) {
        val bk = back!!
        val xa = x1 - cut(br)
        val xb = x0 + cut(bl)
        val ru = uw(bk, w)
        quad(
            xa, y1, z0 + b, xb, y1, z0 + b, xb, y1 - b, z0, xa, y1 - b, z0, bk, 0f, k, -k,
            u0 = ru * (x1 - xa) / w, v0 = 0.5f, u1 = ru * (x1 - xb) / w, v1 = 0.5f, emissive = te * 0.5f, tint = tint, gloss = stripGloss,
        )
    }
    if (tl && cuts) {
        val lf = left!!
        val za = z0 + cut(bl)
        val zb = z1 - cut(fl)
        val ru = uw(lf, d)
        quad(
            x0 + b, y1, za, x0 + b, y1, zb, x0, y1 - b, zb, x0, y1 - b, za, lf, -k, k, 0f,
            u0 = ru * (za - z0) / d, v0 = 0.5f, u1 = ru * (zb - z0) / d, v1 = 0.5f, emissive = te * 0.5f, tint = tint, gloss = stripGloss,
        )
    }
    if (tr && cuts) {
        val rt = right!!
        val za = z1 - cut(fr)
        val zb = z0 + cut(br)
        val ru = uw(rt, d)
        quad(
            x1 - b, y1, za, x1 - b, y1, zb, x1, y1 - b, zb, x1, y1 - b, za, rt, k, k, 0f,
            u0 = ru * (z1 - za) / d, v0 = 0.5f, u1 = ru * (z1 - zb) / d, v1 = 0.5f, emissive = te * 0.5f, tint = tint, gloss = stripGloss,
        )
    }

    // A triangle closes each top corner where two top cuts and a vertical cut meet.
    val c = 0.57735027f
    val half = floatArrayOf(0.5f, 0.5f, 0.5f)
    fun corner(on: Boolean, r: Region?, sx: Float, sz: Float, cx: Float, cz: Float) {
        if (!on || !cuts || r == null) return
        // The three corners: on the top face, on the side facing x, on the side facing z.
        poly(
            floatArrayOf(cx - sx * b, cx, cx - sx * b), floatArrayOf(y1, y1 - b, y1 - b), floatArrayOf(cz - sz * b, cz - sz * b, cz),
            half, half, r, sx * c, c, sz * c, emissive = (fe + te) * 0.3f, tint = tint, gloss = stripGloss,
        )
    }
    corner(fl && tf && tl, top, -1f, 1f, x0, z1)
    corner(fr && tf && tr, top, 1f, 1f, x1, z1)
    corner(bl && tb && tl, top, -1f, -1f, x0, z0)
    corner(br && tb && tr, top, 1f, -1f, x1, z0)
    return this
}
