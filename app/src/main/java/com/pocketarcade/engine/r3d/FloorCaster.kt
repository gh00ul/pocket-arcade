package com.pocketarcade.engine.r3d

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Fast renderer for a textured floor plane (y = 0) seen by a camera with no yaw — the classic
 * "floor casting" trick. Each framebuffer row maps to one depth on the plane, so texture
 * coordinates step linearly across the row with no per-pixel divide. The floor leaves the depth
 * buffer cleared: everything else stands on top of it, so nothing can ever be hidden by it.
 * Lighting comes from a coarse grid of samples, stepped linearly inside each cell.
 */
class FloorCaster(private val cellSize: Float = 16f) {
    private var cols = 0
    private var rows = 0
    private var gx0 = 0f
    private var gz0 = 0f
    private var lr = FloatArray(0)
    private var lg = FloatArray(0)
    private var lb = FloatArray(0)
    private var rowR = IntArray(0)
    private var rowG = IntArray(0)
    private var rowB = IntArray(0)
    private val tmp = FloatArray(3)

    /**
     * Draws the rectangle [minX]..[maxX] × [minZ]..[maxZ] of the plane with [tex] mapped at
     * [texelsPerUnit] (texture texel = world coordinate × texelsPerUnit). Returns false (and
     * draws nothing) when the camera is yawed, so the caller can fall back to polygons.
     */
    fun draw(r: Renderer3D, tex: Texture, texelsPerUnit: Float, minX: Float, maxX: Float, minZ: Float, maxZ: Float): Boolean {
        val cam = r.camera
        if (abs(cam.rz) > 1e-4f || abs(cam.ry) > 1e-4f) return false
        if (maxZ <= minZ || maxX <= minX) return true
        buildLightGrid(r, minX, maxX, minZ, maxZ)

        val w = r.width
        val h = r.height
        val col = r.color
        val px = tex.pixels
        val tw = tex.width
        val th = tex.height
        val focal = cam.focal
        val cxs = cam.cx
        val cys = cam.cy
        val fogSpan = (r.fogFar - r.fogNear).coerceAtLeast(1f)
        val lastCol = cols - 1

        for (sy in 0 until h) {
            val py = -(sy + 0.5f - cys) / focal
            val dy = cam.fy + cam.uy * py
            if (dy > -1e-5f) continue
            val t = -cam.ey / dy
            if (t < cam.near) continue
            val dz = cam.fz + cam.uz * py
            val wz = cam.ez + dz * t
            if (wz < minZ || wz >= maxZ) continue
            // Pixel span covering minX..maxX at this depth.
            var xs = ceil(cxs + (minX - cam.ex) / t * focal - 0.5f).toInt()
            var xe = floor(cxs + (maxX - cam.ex) / t * focal - 0.5f).toInt()
            if (xs < 0) xs = 0
            if (xe > w - 1) xe = w - 1
            if (xs > xe) continue

            // Light samples for this row: interpolate the grid along z, apply fog.
            val gz = ((wz - gz0) / cellSize).coerceIn(0f, (rows - 1).toFloat() - 1e-3f)
            val j = gz.toInt()
            val fz = gz - j
            val fog = if (t <= r.fogNear) 1f else (1f - (t - r.fogNear) / fogSpan).coerceAtLeast(r.fogFloor)
            val k = fog * 256f
            val o0 = j * cols
            val o1 = (j + 1).coerceAtMost(rows - 1) * cols
            for (i in 0 until cols) {
                rowR[i] = ((lr[o0 + i] + (lr[o1 + i] - lr[o0 + i]) * fz) * k).toInt()
                rowG[i] = ((lg[o0 + i] + (lg[o1 + i] - lg[o0 + i]) * fz) * k).toInt()
                rowB[i] = ((lb[o0 + i] + (lb[o1 + i] - lb[o0 + i]) * fz) * k).toInt()
            }

            val step = t / focal
            val wx0 = cam.ex + (xs + 0.5f - cxs) * step
            var fu = (wx0 * texelsPerUnit * 65536f).toInt()
            val du = (step * texelsPerUnit * 65536f).toInt()
            var fl = ((wx0 - gx0) / cellSize * 65536f).toInt()
            val dl = (step / cellSize * 65536f).toInt()
            var tv = (wz * texelsPerUnit).toInt()
            if (tv < 0) tv = 0 else if (tv >= th) tv = th - 1
            val rowBase = tv * tw
            var idx = sy * w + xs
            var x = xs
            // Walk the row one light cell at a time; inside a cell light changes linearly.
            while (x <= xe) {
                var li = fl shr 16
                if (li < 0) li = 0 else if (li >= lastCol) li = lastCol - 1
                val f = fl and 0xFFFF
                val toEdge = ((li + 1).toLong() shl 16) - fl
                var n = if (dl > 0) ((toEdge + dl - 1) / dl).toInt() else xe - x + 1
                if (n < 1) n = 1
                if (n > xe - x + 1) n = xe - x + 1
                val a0 = rowR[li]; val a1 = rowR[li + 1]
                val b0 = rowG[li]; val b1 = rowG[li + 1]
                val c0 = rowB[li]; val c1 = rowB[li + 1]
                // 16.16 light at the segment start and per-pixel step.
                var lrr = (a0 shl 8) + (((a1 - a0) * f) shr 8)
                var lgg = (b0 shl 8) + (((b1 - b0) * f) shr 8)
                var lbb = (c0 shl 8) + (((c1 - c0) * f) shr 8)
                val dr = ((a1 - a0).toLong() * dl shr 8).toInt()
                val dg = ((b1 - b0).toLong() * dl shr 8).toInt()
                val db = ((c1 - c0).toLong() * dl shr 8).toInt()
                var k = 0
                while (k < n) {
                    var tu = fu shr 16
                    if (tu < 0) tu = 0 else if (tu >= tw) tu = tw - 1
                    val c = px[rowBase + tu]
                    var cr = ((c shr 16 and 255) * (lrr shr 8)) shr 8
                    var cg = ((c shr 8 and 255) * (lgg shr 8)) shr 8
                    var cb = ((c and 255) * (lbb shr 8)) shr 8
                    if (cr > 255) cr = 255
                    if (cg > 255) cg = 255
                    if (cb > 255) cb = 255
                    col[idx] = -0x1000000 or (cr shl 16) or (cg shl 8) or cb
                    fu += du
                    lrr += dr; lgg += dg; lbb += db
                    idx++
                    k++
                }
                fl += dl * n
                x += n
            }
        }
        return true
    }

    private fun buildLightGrid(r: Renderer3D, minX: Float, maxX: Float, minZ: Float, maxZ: Float) {
        gx0 = floor(minX / cellSize) * cellSize
        gz0 = floor(minZ / cellSize) * cellSize
        cols = ((maxX - gx0) / cellSize).toInt() + 2
        rows = ((maxZ - gz0) / cellSize).toInt() + 2
        val n = cols * rows
        if (lr.size < n) {
            lr = FloatArray(n); lg = FloatArray(n); lb = FloatArray(n)
        }
        if (rowR.size < cols) {
            rowR = IntArray(cols); rowG = IntArray(cols); rowB = IntArray(cols)
        }
        for (j in 0 until rows) {
            val z = gz0 + j * cellSize
            for (i in 0 until cols) {
                r.lighting.shade(gx0 + i * cellSize, 0f, z, 0f, 1f, 0f, tmp)
                val o = j * cols + i
                lr[o] = tmp[0]; lg[o] = tmp[1]; lb[o] = tmp[2]
            }
        }
    }
}
