package com.pocketarcade.engine.r3d

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Blend {
    /** Z-tested and z-written; texels with alpha < 128 are cut out (alpha test). */
    OPAQUE,
    /** Z-tested, not written; blended by texel alpha × polygon alpha. */
    ALPHA,
    /** Z-tested, not written; added on top (glows, light pools, sparks). */
    ADD,
}

/**
 * A small software 3D rasterizer drawing into an ARGB framebuffer with a 1/z depth buffer.
 *
 * Polygons (up to 8 vertices) are lit per vertex, clipped against the near plane, projected and
 * scan-converted with perspective-correct texture coordinates and Gouraud-interpolated light.
 * Rendering at a low resolution and scaling up with nearest-neighbour keeps the pixel-art look.
 */
class Renderer3D(w: Int, h: Int) {
    var width = w
        private set
    var height = h
        private set
    var color = IntArray(w * h)
        private set
    var depth = FloatArray(w * h)
        private set
    val camera = Camera3D()
    val lighting = Lighting()

    /** Distance fog: full brightness before [fogNear], fading to [fogFloor] at [fogFar]. */
    var fogNear = 1e8f
    var fogFar = 2e8f
    var fogFloor = 0.15f

    var polysDrawn = 0
        private set

    private companion object {
        const val MAXV = 8
        const val MAXC = 16
    }

    fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        color = IntArray(w * h)
        depth = FloatArray(w * h)
    }

    fun clear(argb: Int) {
        color.fill(argb or -0x1000000)
        depth.fill(0f)
        polysDrawn = 0
    }

    /** Fills rows [y0, y1) with a vertical gradient (skies, backdrops) and clears their depth. */
    fun gradient(top: Int, bottom: Int, y0: Int = 0, y1: Int = height) {
        val a = y0.coerceAtLeast(0)
        val b = y1.coerceAtMost(height)
        for (y in a until b) {
            val t = if (y1 - y0 <= 1) 0f else (y - y0).toFloat() / (y1 - y0 - 1)
            val c = mixArgb(top, bottom, t) or -0x1000000
            color.fill(c, y * width, y * width + width)
        }
        if (b > a) depth.fill(0f, a * width, b * width)
        if (a == 0 && b == height) polysDrawn = 0
    }

    // ------------------------------------------------------------------ polygon assembly

    private var n = 0
    private val wx = FloatArray(MAXV)
    private val wy = FloatArray(MAXV)
    private val wz = FloatArray(MAXV)
    private val wu = FloatArray(MAXV)
    private val wv = FloatArray(MAXV)
    private val vxA = FloatArray(MAXV)
    private val vyA = FloatArray(MAXV)
    private val vzA = FloatArray(MAXV)
    private val lrA = FloatArray(MAXV)
    private val lgA = FloatArray(MAXV)
    private val lbA = FloatArray(MAXV)

    private val cxA = FloatArray(MAXC)
    private val cyA = FloatArray(MAXC)
    private val czA = FloatArray(MAXC)
    private val cuA = FloatArray(MAXC)
    private val cvA = FloatArray(MAXC)
    private val crA = FloatArray(MAXC)
    private val cgA = FloatArray(MAXC)
    private val cbA = FloatArray(MAXC)

    private val sx = FloatArray(MAXC)
    private val sy = FloatArray(MAXC)
    private val siz = FloatArray(MAXC)
    private val suz = FloatArray(MAXC)
    private val svz = FloatArray(MAXC)
    private val sr = FloatArray(MAXC)
    private val sg = FloatArray(MAXC)
    private val sb = FloatArray(MAXC)

    private var region: Region? = null
    private var blend = Blend.OPAQUE
    private var emissive = 0f
    private var alphaK = 1f
    private var bias = 1f
    private var cull = true
    private var hasNormal = false
    private var nX = 0f
    private var nY = 0f
    private var nZ = 1f
    private var tR = 1f
    private var tG = 1f
    private var tB = 1f
    private val lightOut = FloatArray(3)

    /**
     * Starts a polygon. [emissive] > 0 ignores lighting and uses that brightness (1 = texture
     * colour). [depthBias] > 1 pulls the polygon toward the camera for depth tests.
     */
    fun begin(
        region: Region,
        blend: Blend = Blend.OPAQUE,
        emissive: Float = 0f,
        alpha: Float = 1f,
        depthBias: Float = 1f,
        cull: Boolean = true,
    ) {
        this.region = region
        this.blend = blend
        this.emissive = emissive
        this.alphaK = alpha
        this.bias = depthBias
        this.cull = cull
        n = 0
        hasNormal = false
        tR = 1f; tG = 1f; tB = 1f
    }

    fun normal(x: Float, y: Float, z: Float) {
        nX = x; nY = y; nZ = z
        hasNormal = true
    }

    fun tint(r: Float, g: Float, b: Float) {
        tR = r; tG = g; tB = b
    }

    fun tint(argb: Int) {
        if (argb == -1) return
        tR = (argb shr 16 and 255) / 255f
        tG = (argb shr 8 and 255) / 255f
        tB = (argb and 255) / 255f
    }

    /** Adds a vertex: world position and texel coordinates inside the region. */
    fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        if (n >= MAXV) return
        wx[n] = x; wy[n] = y; wz[n] = z; wu[n] = u; wv[n] = v
        n++
    }

    fun end() {
        val reg = region ?: return
        if (n < 3) return
        val cam = camera
        if (!hasNormal) {
            val ax = wx[2] - wx[0]; val ay = wy[2] - wy[0]; val az = wz[2] - wz[0]
            val bx = wx[1] - wx[0]; val by = wy[1] - wy[0]; val bz = wz[1] - wz[0]
            var cxn = ay * bz - az * by
            var cyn = az * bx - ax * bz
            var czn = ax * by - ay * bx
            val l = sqrt(cxn * cxn + cyn * cyn + czn * czn).coerceAtLeast(1e-6f)
            cxn /= l; cyn /= l; czn /= l
            nX = cxn; nY = cyn; nZ = czn
        }
        if (cull) {
            var mx = 0f
            var my = 0f
            var mz = 0f
            for (i in 0 until n) {
                mx += wx[i]; my += wy[i]; mz += wz[i]
            }
            mx /= n; my /= n; mz /= n
            if ((mx - cam.ex) * nX + (my - cam.ey) * nY + (mz - cam.ez) * nZ >= 0f) return
        }
        for (i in 0 until n) {
            if (emissive > 0f) {
                lrA[i] = emissive * tR; lgA[i] = emissive * tG; lbA[i] = emissive * tB
            } else {
                lighting.shade(wx[i], wy[i], wz[i], nX, nY, nZ, lightOut)
                lrA[i] = lightOut[0] * tR; lgA[i] = lightOut[1] * tG; lbA[i] = lightOut[2] * tB
            }
            vxA[i] = cam.viewX(wx[i], wy[i], wz[i])
            vyA[i] = cam.viewY(wx[i], wy[i], wz[i])
            vzA[i] = cam.viewZ(wx[i], wy[i], wz[i])
        }
        val m = clipNear(cam.near)
        if (m < 3) return
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val fogSpan = (fogFar - fogNear).coerceAtLeast(1f)
        for (j in 0 until m) {
            val z = czA[j]
            val iz = 1f / z
            val px = cam.cx + cxA[j] * iz * cam.focal
            val py = cam.cy - cyA[j] * iz * cam.focal
            sx[j] = px; sy[j] = py
            siz[j] = iz
            suz[j] = cuA[j] * iz
            svz[j] = cvA[j] * iz
            val fog = if (z <= fogNear) 1f else (1f - (z - fogNear) / fogSpan).coerceAtLeast(fogFloor)
            sr[j] = crA[j] * fog * 256f
            sg[j] = cgA[j] * fog * 256f
            sb[j] = cbA[j] * fog * 256f
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }
        if (maxX < 0f || minX > width || maxY < 0f || minY > height) return
        polysDrawn++
        for (k in 1 until m - 1) raster(0, k, k + 1, reg)
    }

    /** Sutherland–Hodgman clip of the view-space polygon against z >= near. */
    private fun clipNear(near: Float): Int {
        var out = 0
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val zi = vzA[i]
            val zj = vzA[j]
            val inI = zi >= near
            val inJ = zj >= near
            if (inI && out < MAXC) {
                cxA[out] = vxA[i]; cyA[out] = vyA[i]; czA[out] = zi
                cuA[out] = wu[i]; cvA[out] = wv[i]
                crA[out] = lrA[i]; cgA[out] = lgA[i]; cbA[out] = lbA[i]
                out++
            }
            if (inI != inJ && out < MAXC) {
                val t = (near - zi) / (zj - zi)
                cxA[out] = vxA[i] + (vxA[j] - vxA[i]) * t
                cyA[out] = vyA[i] + (vyA[j] - vyA[i]) * t
                czA[out] = near
                cuA[out] = wu[i] + (wu[j] - wu[i]) * t
                cvA[out] = wv[i] + (wv[j] - wv[i]) * t
                crA[out] = lrA[i] + (lrA[j] - lrA[i]) * t
                cgA[out] = lgA[i] + (lgA[j] - lgA[i]) * t
                cbA[out] = lbA[i] + (lbA[j] - lbA[i]) * t
                out++
            }
        }
        return out
    }

    // ------------------------------------------------------------------ scan conversion

    private fun raster(a: Int, b: Int, c: Int, reg: Region) {
        val x0 = sx[a]; val y0 = sy[a]
        val x1 = sx[b]; val y1 = sy[b]
        val x2 = sx[c]; val y2 = sy[c]
        val dx1 = x1 - x0; val dy1 = y1 - y0
        val dx2 = x2 - x0; val dy2 = y2 - y0
        val denom = dx1 * dy2 - dx2 * dy1
        if (denom > -1e-5f && denom < 1e-5f) return
        val inv = 1f / denom

        // Plane gradients for every interpolated attribute.
        val izX = ((siz[b] - siz[a]) * dy2 - (siz[c] - siz[a]) * dy1) * inv
        val izY = ((siz[c] - siz[a]) * dx1 - (siz[b] - siz[a]) * dx2) * inv
        val uX = ((suz[b] - suz[a]) * dy2 - (suz[c] - suz[a]) * dy1) * inv
        val uY = ((suz[c] - suz[a]) * dx1 - (suz[b] - suz[a]) * dx2) * inv
        val vX = ((svz[b] - svz[a]) * dy2 - (svz[c] - svz[a]) * dy1) * inv
        val vY = ((svz[c] - svz[a]) * dx1 - (svz[b] - svz[a]) * dx2) * inv
        val rX = ((sr[b] - sr[a]) * dy2 - (sr[c] - sr[a]) * dy1) * inv
        val rY = ((sr[c] - sr[a]) * dx1 - (sr[b] - sr[a]) * dx2) * inv
        val gX = ((sg[b] - sg[a]) * dy2 - (sg[c] - sg[a]) * dy1) * inv
        val gY = ((sg[c] - sg[a]) * dx1 - (sg[b] - sg[a]) * dx2) * inv
        val bX = ((sb[b] - sb[a]) * dy2 - (sb[c] - sb[a]) * dy1) * inv
        val bY = ((sb[c] - sb[a]) * dx1 - (sb[b] - sb[a]) * dx2) * inv

        // Sort by y: t (top), m (middle), o (bottom).
        var t = a
        var m = b
        var o = c
        if (sy[m] < sy[t]) { val s = t; t = m; m = s }
        if (sy[o] < sy[t]) { val s = t; t = o; o = s }
        if (sy[o] < sy[m]) { val s = m; m = o; o = s }
        val xt = sx[t]; val yt = sy[t]
        val xm = sx[m]; val ym = sy[m]
        val xo = sx[o]; val yo = sy[o]
        if (yo - yt < 1e-6f) return
        var yStart = ceil(yt - 0.5f).toInt()
        var yEnd = ceil(yo - 0.5f).toInt() - 1
        if (yStart < 0) yStart = 0
        if (yEnd > height - 1) yEnd = height - 1
        if (yStart > yEnd) return
        val slopeLong = (xo - xt) / (yo - yt)
        val slopeTop = if (ym - yt > 1e-6f) (xm - xt) / (ym - yt) else 0f
        val slopeBot = if (yo - ym > 1e-6f) (xo - xm) / (yo - ym) else 0f

        val tex = reg.tex.pixels
        val tw = reg.tex.width
        val rx = reg.x
        val ry = reg.y
        val rw = reg.w
        val rh = reg.h
        val wrap = reg.wrap
        val w = width
        val col = color
        val dep = depth
        val bz = bias
        val op = blend.ordinal
        val aK = (alphaK * 256f).toInt().coerceIn(0, 256)

        for (y in yStart..yEnd) {
            val yc = y + 0.5f
            val xa = xt + (yc - yt) * slopeLong
            val xb = if (yc < ym) xt + (yc - yt) * slopeTop else xm + (yc - ym) * slopeBot
            val xl: Float
            val xr: Float
            if (xa < xb) { xl = xa; xr = xb } else { xl = xb; xr = xa }
            var xs = ceil(xl - 0.5f).toInt()
            var xe = ceil(xr - 0.5f).toInt() - 1
            if (xs < 0) xs = 0
            if (xe > w - 1) xe = w - 1
            if (xs > xe) continue
            val fx = xs + 0.5f - x0
            val fy = yc - y0
            var iz = siz[a] + fx * izX + fy * izY
            var uz = suz[a] + fx * uX + fy * uY
            var vz = svz[a] + fx * vX + fy * vY
            var lr = sr[a] + fx * rX + fy * rY
            var lg = sg[a] + fx * gX + fy * gY
            var lb = sb[a] + fx * bX + fy * bY
            var idx = y * w + xs
            if (!wrap) {
                // Fast path (most pixels): perspective-correct every 8 pixels, fixed-point
                // texture and light steps in between.
                var zq = 1f / iz
                var u0 = uz * zq
                var v0 = vz * zq
                var lri = (lr * 256f).toInt()
                var lgi = (lg * 256f).toInt()
                var lbi = (lb * 256f).toInt()
                val dlr = (rX * 256f).toInt()
                val dlg = (gX * 256f).toInt()
                val dlb = (bX * 256f).toInt()
                var x = xs
                while (x <= xe) {
                    val n = if (xe - x + 1 < 8) xe - x + 1 else 8
                    val izN = iz + izX * n
                    zq = 1f / izN
                    val u1 = (uz + uX * n) * zq
                    val v1 = (vz + vX * n) * zq
                    var fu = (u0 * 65536f).toInt()
                    var fv = (v0 * 65536f).toInt()
                    val dfu = ((u1 - u0) * 65536f).toInt() / n
                    val dfv = ((v1 - v0) * 65536f).toInt() / n
                    var k = 0
                    while (k < n) {
                        val dz = iz * bz
                        if (dz > dep[idx]) {
                            var tu = fu shr 16
                            var tv = fv shr 16
                            if (tu < 0) tu = 0 else if (tu >= rw) tu = rw - 1
                            if (tv < 0) tv = 0 else if (tv >= rh) tv = rh - 1
                            val tx = tex[(ry + tv) * tw + rx + tu]
                            if (op == 0) {
                                if (tx < 0) {
                                    var cr = ((tx shr 16 and 255) * lri) shr 16
                                    var cg = ((tx shr 8 and 255) * lgi) shr 16
                                    var cb = ((tx and 255) * lbi) shr 16
                                    if (cr > 255) cr = 255
                                    if (cg > 255) cg = 255
                                    if (cb > 255) cb = 255
                                    col[idx] = -0x1000000 or (cr shl 16) or (cg shl 8) or cb
                                    dep[idx] = dz
                                }
                            } else {
                                val ta = ((tx ushr 24) * aK) shr 8
                                if (ta > 0) {
                                    var cr = ((tx shr 16 and 255) * lri) shr 16
                                    var cg = ((tx shr 8 and 255) * lgi) shr 16
                                    var cb = ((tx and 255) * lbi) shr 16
                                    if (cr > 255) cr = 255
                                    if (cg > 255) cg = 255
                                    if (cb > 255) cb = 255
                                    val d = col[idx]
                                    val dr = d shr 16 and 255
                                    val dg = d shr 8 and 255
                                    val db = d and 255
                                    if (op == 1) {
                                        col[idx] = -0x1000000 or
                                            ((dr + (((cr - dr) * ta) shr 8)) shl 16) or
                                            ((dg + (((cg - dg) * ta) shr 8)) shl 8) or
                                            (db + (((cb - db) * ta) shr 8))
                                    } else {
                                        var nr = dr + ((cr * ta) shr 8)
                                        var ng = dg + ((cg * ta) shr 8)
                                        var nb = db + ((cb * ta) shr 8)
                                        if (nr > 255) nr = 255
                                        if (ng > 255) ng = 255
                                        if (nb > 255) nb = 255
                                        col[idx] = -0x1000000 or (nr shl 16) or (ng shl 8) or nb
                                    }
                                }
                            }
                        }
                        iz += izX
                        fu += dfu
                        fv += dfv
                        lri += dlr; lgi += dlg; lbi += dlb
                        idx++
                        k++
                    }
                    uz += uX * n
                    vz += vX * n
                    u0 = u1
                    v0 = v1
                    x += n
                }
                continue
            }
            var x = xs
            while (x <= xe) {
                val dz = iz * bz
                if (dz > dep[idx]) {
                    val z = 1f / iz
                    val fu = uz * z
                    val fv = vz * z
                    var tu = fu.toInt()
                    var tv = fv.toInt()
                    if (wrap) {
                        if (fu < 0f) tu -= 1
                        if (fv < 0f) tv -= 1
                        tu %= rw; if (tu < 0) tu += rw
                        tv %= rh; if (tv < 0) tv += rh
                    } else {
                        if (tu < 0) tu = 0 else if (tu >= rw) tu = rw - 1
                        if (tv < 0) tv = 0 else if (tv >= rh) tv = rh - 1
                    }
                    val tx = tex[(ry + tv) * tw + rx + tu]
                    if (op == 0) {
                        if (tx < 0) {
                            var cr = ((tx shr 16 and 255) * lr).toInt() shr 8
                            var cg = ((tx shr 8 and 255) * lg).toInt() shr 8
                            var cb = ((tx and 255) * lb).toInt() shr 8
                            if (cr > 255) cr = 255
                            if (cg > 255) cg = 255
                            if (cb > 255) cb = 255
                            col[idx] = -0x1000000 or (cr shl 16) or (cg shl 8) or cb
                            dep[idx] = dz
                        }
                    } else if (op == 1) {
                        val ta = ((tx ushr 24) * aK) shr 8
                        if (ta > 0) {
                            var cr = ((tx shr 16 and 255) * lr).toInt() shr 8
                            var cg = ((tx shr 8 and 255) * lg).toInt() shr 8
                            var cb = ((tx and 255) * lb).toInt() shr 8
                            if (cr > 255) cr = 255
                            if (cg > 255) cg = 255
                            if (cb > 255) cb = 255
                            val d = col[idx]
                            val dr = d shr 16 and 255
                            val dg = d shr 8 and 255
                            val db = d and 255
                            col[idx] = -0x1000000 or
                                ((dr + (((cr - dr) * ta) shr 8)) shl 16) or
                                ((dg + (((cg - dg) * ta) shr 8)) shl 8) or
                                (db + (((cb - db) * ta) shr 8))
                        }
                    } else {
                        val ta = ((tx ushr 24) * aK) shr 8
                        if (ta > 0) {
                            val cr = (((tx shr 16 and 255) * lr).toInt() shr 8) * ta shr 8
                            val cg = (((tx shr 8 and 255) * lg).toInt() shr 8) * ta shr 8
                            val cb = (((tx and 255) * lb).toInt() shr 8) * ta shr 8
                            val d = col[idx]
                            var nr = (d shr 16 and 255) + cr
                            var ng = (d shr 8 and 255) + cg
                            var nb = (d and 255) + cb
                            if (nr > 255) nr = 255
                            if (ng > 255) ng = 255
                            if (nb > 255) nb = 255
                            col[idx] = -0x1000000 or (nr shl 16) or (ng shl 8) or nb
                        }
                    }
                }
                iz += izX; uz += uX; vz += vX
                lr += rX; lg += gX; lb += bX
                idx++
                x++
            }
        }
    }

    // ------------------------------------------------------------------ convenience shapes

    /**
     * A quad from four corners given clockwise as seen from its front: top-left, top-right,
     * bottom-right, bottom-left. The region maps across it from (u0, v0) to (u1, v1).
     */
    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        region: Region,
        nx: Float, ny: Float, nz: Float,
        u0: Float = 0f, v0: Float = 0f, u1: Float = region.w.toFloat(), v1: Float = region.h.toFloat(),
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, cull: Boolean = true,
        tint: Int = -1, depthBias: Float = 1f,
    ) {
        begin(region, blend, emissive, alpha, depthBias, cull)
        normal(nx, ny, nz)
        tint(tint)
        vertex(ax, ay, az, u0, v0)
        vertex(bx, by, bz, u1, v0)
        vertex(cx, cy, cz, u1, v1)
        vertex(dx, dy, dz, u0, v1)
        end()
    }

    /**
     * A camera-facing sprite standing on ([x], [y], [z]) (bottom centre). [lean] tilts it back
     * toward the camera's up axis so tall sprites don't look squashed from above.
     */
    fun billboard(
        x: Float, y: Float, z: Float, w: Float, h: Float, region: Region,
        flipX: Boolean = false, lean: Float = 0.5f, blend: Blend = Blend.OPAQUE,
        emissive: Float = 0f, alpha: Float = 1f, depthBias: Float = 1.03f, tint: Int = -1,
    ) {
        val cam = camera
        val rx = cam.rx
        val rz = cam.rz
        var upx = cam.ux * lean
        var upy = (1f - lean) + cam.uy * lean
        var upz = cam.uz * lean
        val ul = sqrt(upx * upx + upy * upy + upz * upz).coerceAtLeast(1e-5f)
        upx /= ul; upy /= ul; upz /= ul
        val hw = w / 2f
        val blx = x - rx * hw
        val blz = z - rz * hw
        val brx = x + rx * hw
        val brz = z + rz * hw
        val u0 = if (flipX) region.w.toFloat() else 0f
        val u1 = if (flipX) 0f else region.w.toFloat()
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(blx + upx * h, y + upy * h, blz + upz * h, u0, 0f)
        vertex(brx + upx * h, y + upy * h, brz + upz * h, u1, 0f)
        vertex(brx, y, brz, u1, region.h.toFloat())
        vertex(blx, y, blz, u0, region.h.toFloat())
        end()
    }

    /**
     * A sprite centred on ([x], [y], [z]) that faces the camera squarely, turned by [roll]
     * radians in the screen plane (balls, plush toys, sparks).
     */
    fun sprite(
        x: Float, y: Float, z: Float, w: Float, h: Float, region: Region, roll: Float = 0f,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, depthBias: Float = 1f,
        tint: Int = -1, flipX: Boolean = false,
    ) {
        val cam = camera
        val c = cos(roll)
        val s = sin(roll)
        val hw = w / 2f
        val hh = h / 2f
        // Screen-plane axes turned by the roll.
        val ax = (cam.rx * c + cam.ux * s) * hw
        val ay = (cam.ry * c + cam.uy * s) * hw
        val az = (cam.rz * c + cam.uz * s) * hw
        val bx = (cam.ux * c - cam.rx * s) * hh
        val by = (cam.uy * c - cam.ry * s) * hh
        val bz = (cam.uz * c - cam.rz * s) * hh
        val u0 = if (flipX) region.w.toFloat() else 0f
        val u1 = if (flipX) 0f else region.w.toFloat()
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(x - ax + bx, y - ay + by, z - az + bz, u0, 0f)
        vertex(x + ax + bx, y + ay + by, z + az + bz, u1, 0f)
        vertex(x + ax - bx, y + ay - by, z + az - bz, u1, region.h.toFloat())
        vertex(x - ax - bx, y - ay - by, z - az - bz, u0, region.h.toFloat())
        end()
    }

    /**
     * A ribbon [width] wide from one point to another, turned to face the camera (cables,
     * table lines, light beams).
     */
    fun beam(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, width: Float, region: Region,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
    ) {
        val cam = camera
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        val vx = (x0 + x1) / 2f - cam.ex
        val vy = (y0 + y1) / 2f - cam.ey
        val vz = (z0 + z1) / 2f - cam.ez
        var sx = dy * vz - dz * vy
        var sy = dz * vx - dx * vz
        var sz = dx * vy - dy * vx
        val l = sqrt(sx * sx + sy * sy + sz * sz)
        if (l < 1e-5f) return
        val k = width / 2f / l
        sx *= k; sy *= k; sz *= k
        begin(region, blend, emissive, alpha, 1f, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(x0 - sx, y0 - sy, z0 - sz, 0f, 0f)
        vertex(x0 + sx, y0 + sy, z0 + sz, region.w.toFloat(), 0f)
        vertex(x1 + sx, y1 + sy, z1 + sz, region.w.toFloat(), region.h.toFloat())
        vertex(x1 - sx, y1 - sy, z1 - sz, 0f, region.h.toFloat())
        end()
    }

    /**
     * A horizontal quad [w] × [d] lying at height [y], centred on ([x], [z]) and turned by
     * [angle] around the vertical axis (coins, pucks, spinning shadows).
     */
    fun flat(
        x: Float, z: Float, y: Float, w: Float, d: Float, region: Region, angle: Float = 0f,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
        depthBias: Float = 1f,
    ) {
        val c = cos(angle)
        val s = sin(angle)
        val ax = c * w / 2f
        val az = s * w / 2f
        val bx = -s * d / 2f
        val bz = c * d / 2f
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(0f, 1f, 0f)
        tint(tint)
        vertex(x - ax - bx, y, z - az - bz, 0f, 0f)
        vertex(x + ax - bx, y, z + az - bz, region.w.toFloat(), 0f)
        vertex(x + ax + bx, y, z + az + bz, region.w.toFloat(), region.h.toFloat())
        vertex(x - ax + bx, y, z - az + bz, 0f, region.h.toFloat())
        end()
    }

    /** A horizontal quad lying at height [y] (floor decals, light pools, shadows). */
    fun decal(
        x0: Float, z0: Float, x1: Float, z1: Float, y: Float, region: Region,
        blend: Blend = Blend.ALPHA, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
    ) {
        quad(
            x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, region, 0f, 1f, 0f,
            blend = blend, emissive = emissive, alpha = alpha, cull = false, tint = tint,
        )
    }
}

/** Blends two ARGB colours. */
fun mixArgb(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
    val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255
    val r = (ar + (br - ar) * u).toInt()
    val g = (ag + (bg - ag) * u).toInt()
    val bl = (ab + (bb - ab) * u).toInt()
    return (a and -0x1000000) or (r shl 16) or (g shl 8) or bl
}