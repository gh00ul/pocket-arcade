package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.MiniGame
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The hall's lighting rig and moving signage: trusses across the hall carrying spotlights aimed
 * at the front of every bank (their beams hanging in the haze and the pools they throw on the
 * carpet), the racers' hung sign with its start lights, backlit murals on the upper back wall and
 * chase lights round the entrance. The spots add no point lights: the beams and pools are
 * additive glow, so the light budget is untouched. Built once; [drawOpaque] and [drawGlow] run
 * every frame and allocate nothing.
 */
internal class HallRig(private val map: HubMap, private val games: List<MiniGame>) {
    private companion object {
        /** Depths of the trusses across the hall, between the rows of banks, and their height. */
        val TRUSS_Z = floatArrayOf(128f, 500f)
        const val TRUSS_Y = 236f
        /**
         * Banks further forward than this are lit from rails along the side walls instead (a
         * truss over the front floor would cut across the racers from the hall camera).
         */
        const val RAIL_FROM_Z = 620f
        const val RAIL_Y = 196f
        /** The racers' hung sign: its span is the racer bank's, at this depth and height. */
        const val RACE_SIGN_Z = 505f
        const val RACE_SIGN_Y0 = 86f
        const val RACE_SIGN_Y1 = 101f
        const val MAX_SPOTS = 32
        const val KNEE_SPACING = 9f
    }

    val model: Model

    /**
     * What only a camera down on the floor sees: the ceiling (acoustic tiles, light panels, open
     * ducts and sprinkler pipes) and the shopfront from inside (glass, mullions, the door frame and
     * the street across the road). The hall camera looks down from above the ceiling height, so
     * this is drawn only while the eye is below it and the overhead view is untouched.
     */
    private val indoor: Model

    // Spotlights: lens position, the floor point they're aimed at, and colour.
    private var spots = 0
    private val lensX = FloatArray(MAX_SPOTS)
    private val lensY = FloatArray(MAX_SPOTS)
    private val lensZ = FloatArray(MAX_SPOTS)
    private val aimX = FloatArray(MAX_SPOTS)
    private val aimZ = FloatArray(MAX_SPOTS)
    private val spotColor = IntArray(MAX_SPOTS)

    // The racer bank's span, if the racers are in (start lights hang under their sign).
    private var raceX0 = 0f
    private var raceX1 = 0f
    private val hasRace: Boolean

    private val kneeBulbs: FloatArray
    private val beam = HallArt.shaft.full
    private val halo = HallArt.glow.full
    private val bulb = HallArt.solid(-1).full
    private val cans = HashMap<Int, Model>()

    init {
        val b = ModelBuilder()
        val w = HubLayout.WIDTH.toFloat()
        val wl = HubLayout.WALL
        buildTrusses(b, w, wl)
        // Each bank: its extent across the hall and its front, from the machines on the floor.
        val n = games.size
        val bx0 = FloatArray(n) { Float.MAX_VALUE }
        val bx1 = FloatArray(n) { -Float.MAX_VALUE }
        val bz1 = FloatArray(n)
        for (p in map.props) {
            if (p.kind != PropKind.MACHINE) continue
            val m = p.machine
            bx0[m] = minOf(bx0[m], p.x0)
            bx1[m] = maxOf(bx1[m], p.x1)
            bz1[m] = maxOf(bz1[m], p.z1)
        }
        val xf = Xform()
        for (m in 0 until n) {
            if (bx0[m] > bx1[m]) continue
            val span = bx1[m] - bx0[m]
            val count = if (span > 110f) 2 else 1
            for (k in 0 until count) {
                if (spots == MAX_SPOTS) break
                val tx = bx0[m] + span * (k + 0.5f) / count
                val tz = bz1[m] + 14f
                val i = spots++
                aimX[i] = tx; aimZ[i] = tz
                spotColor[i] = lift(games[m].look.glow, 0.55f)
                val mountX: Float
                val mountY: Float
                val mountZ: Float
                if (tz < RAIL_FROM_Z) {
                    // Hung from the nearest truss, a little to the side so the beam reads as a slant.
                    var tz0 = TRUSS_Z[0]
                    for (z in TRUSS_Z) if (kotlin.math.abs(z - tz) < kotlin.math.abs(tz0 - tz)) tz0 = z
                    mountX = (tx + if (tx < w / 2f) 18f else -18f).coerceIn(wl + 10f, w - wl - 10f)
                    mountY = TRUSS_Y - 2.5f
                    mountZ = tz0
                    b.box(mountX - 0.4f, TRUSS_Y - 3f, tz0 - 0.4f, mountX + 0.4f, TRUSS_Y, tz0 + 0.4f, BoxFaces.all(HallArt.darkMetal.full))
                } else {
                    // On the rail along the nearer side wall, reaching in across the floor.
                    mountX = if (tx < w / 2f) wl + 7f else w - wl - 7f
                    mountY = RAIL_Y - 2.5f
                    mountZ = tz + 20f
                }
                val dx = tx - mountX
                val dy = -mountY
                val dz = tz - mountZ
                val len = sqrt(dx * dx + dy * dy + dz * dz)
                val hz = sqrt(dx * dx + dz * dz)
                // The lens sits 7.6 down the can's axis.
                lensX[i] = mountX + dx / len * 7.6f
                lensY[i] = mountY + dy / len * 7.6f
                lensZ[i] = mountZ + dz / len * 7.6f
                b.add(can(spotColor[i]), xf.set(mountX, mountY, mountZ, yaw = atan2(-dx, -dz), pitch = atan2(hz, -dy)))
            }
        }
        // Lighting rails along the side walls over the front floor.
        val rail = BoxFaces(front = HallArt.brushedMetal.full, top = HallArt.brushedMetal.full, left = HallArt.brushedMetal.full, right = HallArt.brushedMetal.full, gloss = 0.8f)
        b.box(wl, RAIL_Y, RAIL_FROM_Z, wl + 9f, RAIL_Y + 2f, HubLayout.FRONT_WALL - 60f, rail)
        b.box(w - wl - 9f, RAIL_Y, RAIL_FROM_Z, w - wl, RAIL_Y + 2f, HubLayout.FRONT_WALL - 60f, rail)
        under(b, wl, wl + 9f, RAIL_FROM_Z, HubLayout.FRONT_WALL - 60f, RAIL_Y, HallArt.darkMetal.full)
        under(b, w - wl - 9f, w - wl, RAIL_FROM_Z, HubLayout.FRONT_WALL - 60f, RAIL_Y, HallArt.darkMetal.full)
        // The racers' sign, hung from the truss behind them on two cables.
        val race = games.indexOfFirst { it.id == "racer" }
        hasRace = race >= 0 && bx0[race] < bx1[race]
        if (hasRace) {
            raceX0 = bx0[race] + 4f
            raceX1 = bx1[race] - 4f
            val z = RACE_SIGN_Z
            val frame = HallArt.darkMetal.full
            b.box(raceX0 - 1.5f, RACE_SIGN_Y0 - 1.5f, z - 2f, raceX1 + 1.5f, RACE_SIGN_Y1 + 1.5f, z, BoxFaces(front = frame, top = frame, left = frame, right = frame, back = frame, gloss = 0.6f))
            under(b, raceX0 - 1.5f, raceX1 + 1.5f, z - 2f, z, RACE_SIGN_Y0 - 1.5f, frame)
            b.quad(raceX0, RACE_SIGN_Y1, z + 0.05f, raceX1, RACE_SIGN_Y1, z + 0.05f, raceX1, RACE_SIGN_Y0, z + 0.05f, raceX0, RACE_SIGN_Y0, z + 0.05f, HallArt.raceSign.full, 0f, 0f, 1f, emissive = 1.3f)
            // The start-light bar under it.
            b.box(raceX0 + 20f, RACE_SIGN_Y0 - 7f, z - 1.6f, raceX1 - 20f, RACE_SIGN_Y0 - 1.5f, z, BoxFaces(front = HallArt.solid(0xFF0A080E.toInt()).full, top = frame, left = frame, right = frame, back = frame))
            under(b, raceX0 + 20f, raceX1 - 20f, z - 1.6f, z, RACE_SIGN_Y0 - 7f, frame)
            for (cx in floatArrayOf(raceX0 + 8f, raceX1 - 8f)) {
                b.capsule(cx, RACE_SIGN_Y1 + 1.5f, z - 1f, cx, TRUSS_Y - 1f, TRUSS_Z[1], 0.3f, HallArt.darkMetal.full, slices = 4)
            }
        }
        // Backlit murals on the upper back wall, either side of the sunset over the prize counter.
        val back = HubLayout.BACK_WALL
        val hgt = HubLayout.WALL_HEIGHT
        val ledge = HallArt.darkMetal.full
        for ((x0, tex) in arrayOf(26f to HallArt.muralSpace, 418f to HallArt.muralCity)) {
            val x1 = x0 + 164f
            b.box(x0 - 4f, hgt + 22f, back, x1 + 4f, hgt + 128f, back + 2f, BoxFaces(front = ledge, top = ledge, left = ledge, right = ledge))
            b.quad(x0, hgt + 124f, back + 2.1f, x1, hgt + 124f, back + 2.1f, x1, hgt + 26f, back + 2.1f, x0, hgt + 26f, back + 2.1f, tex.full, 0f, 0f, 1f, emissive = 0.85f)
        }
        // Chase bulbs along the entrance knee wall's cap, either side of the doors.
        val bulbs = ArrayList<Float>()
        for ((xa, xb) in arrayOf(wl + 4f to HubLayout.DOOR_X0 - 4f, HubLayout.DOOR_X1 + 4f to w - wl - 4f)) {
            var x = xa
            while (x <= xb) {
                bulbs += x
                x += KNEE_SPACING
            }
        }
        kneeBulbs = bulbs.toFloatArray()
        model = b.build()
        indoor = buildIndoor(w, wl)
    }

    /** A face looking down at height [y], closing the underside of something hung overhead. */
    private fun under(b: ModelBuilder, xa: Float, xb: Float, za: Float, zb: Float, y: Float, r: Region) {
        b.quad(xa, y, zb, xb, y, zb, xb, y, za, xa, y, za, r, 0f, -1f, 0f)
    }

    private fun buildIndoor(w: Float, wl: Float): Model {
        val b = ModelBuilder()
        val back = HubLayout.BACK_WALL
        val front = HubLayout.FRONT_WALL
        val top = HubLayout.CEILING
        // The ceiling: dark acoustic tiles, 24 units square (64 texels each).
        val tpu = 64f / 24f
        b.quad(
            wl, top, front + 8f, w - wl, top, front + 8f, w - wl, top, back, wl, top, back,
            HallArt.ceilingTiles.region(wrap = true), 0f, -1f, 0f,
            u0 = wl * tpu, v0 = (front + 8f) * tpu, u1 = (w - wl) * tpu, v1 = back * tpu,
        )
        // Recessed light panels in rows, over the pools the downlights throw.
        val panel = HallArt.troffer.full
        var z = back + 66f
        while (z < front - 20f) {
            var x = w / 2f - 240f
            while (x < w - wl - 20f) {
                b.quad(x - 12f, top - 0.4f, z + 24f, x + 12f, top - 0.4f, z + 24f, x + 12f, top - 0.4f, z - 24f, x - 12f, top - 0.4f, z - 24f, panel, 0f, -1f, 0f, emissive = 1.5f)
                x += 120f
            }
            z += 120f
        }
        // Open ducts running front to back, strapped to the ceiling, and red sprinkler pipes across.
        val duct = HallArt.duct.region(wrap = true)
        val strap = HallArt.darkMetal.full
        for (dx in floatArrayOf(w * 0.3f, w * 0.72f)) {
            duct(b, dx, top - 24f, 11f, back + 10f, front - 10f, duct)
            var sz = back + 40f
            while (sz < front - 20f) {
                b.box(dx - 0.5f, top - 14f, sz - 0.5f, dx + 0.5f, top, sz + 0.5f, BoxFaces(front = strap, back = strap, left = strap, right = strap))
                sz += 90f
            }
        }
        val red = HallArt.paint(0xFFB0213A.toInt(), 0.2f, 0.6f).full
        var pz = back + 190f
        while (pz < front - 60f) {
            b.capsule(wl, top - 8f, pz, w - wl, top - 8f, pz, 0.9f, red, slices = 5)
            var hx = wl + 70f
            while (hx < w - wl) {
                b.capsule(hx, top - 8f, pz, hx, top - 13f, pz, 0.35f, red, slices = 4)
                b.sphere(hx, top - 13.4f, pz, 0.8f, HallArt.chrome.full, slices = 6, stacks = 4)
                hx += 110f
            }
            pz += 260f
        }
        buildShopfront(b, w, wl, front)
        return b.build()
    }

    /** An 8-sided duct of [radius] along z at ([x], [y]), from [za] to [zb], with end caps. */
    private fun duct(b: ModelBuilder, x: Float, y: Float, radius: Float, za: Float, zb: Float, r: Region) {
        val step = (Math.PI * 2 / 8).toFloat()
        for (i in 0 until 8) {
            val a0 = i * step
            val a1 = a0 + step
            val am = a0 + step / 2f
            val x0 = x + kotlin.math.cos(a0) * radius
            val y0 = y + kotlin.math.sin(a0) * radius
            val x1 = x + kotlin.math.cos(a1) * radius
            val y1 = y + kotlin.math.sin(a1) * radius
            b.quad(x0, y0, za, x1, y1, za, x1, y1, zb, x0, y0, zb, r, kotlin.math.cos(am), kotlin.math.sin(am), 0f, u0 = i * 8f, u1 = i * 8f + 8f, v0 = za * 2f, v1 = zb * 2f, gloss = 0.35f)
        }
        val xs = FloatArray(8) { x + kotlin.math.cos(it * step) * radius }
        val ys = FloatArray(8) { y + kotlin.math.sin(it * step) * radius }
        val cap = HallArt.darkMetal.full
        b.poly(xs, ys, FloatArray(8) { za }, FloatArray(8) { 32f }, FloatArray(8) { 32f }, cap, 0f, 0f, -1f)
        b.poly(xs, ys, FloatArray(8) { zb }, FloatArray(8) { 32f }, FloatArray(8) { 32f }, cap, 0f, 0f, 1f)
    }

    /**
     * The entrance wall from inside: floor-to-header glass over the knee wall in black frames,
     * the sliding doors' frame and a lit sign over them, a fascia up to the ceiling, and the
     * street across the road beyond the glass.
     */
    private fun buildShopfront(b: ModelBuilder, w: Float, wl: Float, front: Float) {
        val d0 = HubLayout.DOOR_X0
        val d1 = HubLayout.DOOR_X1
        val sill = 15.5f
        val glassTop = 118f
        val headTop = 130f
        val doorTop = 100f
        val zg = front + 6.5f
        val frame = HallArt.paint(0xFF1C1A24.toInt(), 0.12f, 0.6f).full
        val glass = HallArt.glass.full
        for (k in 0..1) {
            val xa = if (k == 0) wl else d1 + 3f
            val xb = if (k == 0) d0 - 3f else w - wl
            // Seen from inside, +x runs right to left.
            b.quad(xb, glassTop, zg, xa, glassTop, zg, xa, sill, zg, xb, sill, zg, glass, 0f, 0f, -1f, blend = Blend.ALPHA, gloss = 1f)
            val n = kotlin.math.max(1, Math.round((xb - xa) / 46f))
            for (i in 1 until n) {
                val mx = xa + (xb - xa) * i / n
                b.box(mx - 1.2f, sill, zg - 1.8f, mx + 1.2f, glassTop, zg + 0.5f, BoxFaces(back = frame, left = frame, right = frame, gloss = 0.4f))
            }
            b.box(xa, sill, zg - 2.4f, xb, sill + 1.4f, zg, BoxFaces(back = frame, top = frame, gloss = 0.4f))
        }
        // The door frame: jambs over the sensor posts, a header with the sign, glass above.
        val f = BoxFaces(back = frame, left = frame, right = frame, front = frame, gloss = 0.4f)
        b.box(d0 - 3f, 22f, front + 0.2f, d0, doorTop, front + 7.8f, f)
        b.box(d1, 22f, front + 0.2f, d1 + 3f, doorTop, front + 7.8f, f)
        b.box(d0 - 3f, doorTop - 14f, front + 0.2f, d1 + 3f, doorTop, front + 7.8f, BoxFaces(back = frame, front = frame, gloss = 0.4f))
        under(b, d0, d1, front + 0.2f, front + 7.8f, doorTop - 14f, frame)
        val sign = HallArt.lightbox("SEE YOU SOON!", 0xFF1A2A6C.toInt(), 0xFF7FF4FF.toInt(), 512, 72, 44f).full
        b.quad(d1 - 4f, doorTop - 2f, front + 0.1f, d0 + 4f, doorTop - 2f, front + 0.1f, d0 + 4f, doorTop - 12f, front + 0.1f, d1 - 4f, doorTop - 12f, front + 0.1f, sign, 0f, 0f, -1f, emissive = 1.3f)
        b.quad(d1, glassTop, zg, d0, glassTop, zg, d0, doorTop, zg, d1, doorTop, zg, glass, 0f, 0f, -1f, blend = Blend.ALPHA, gloss = 1f)
        // The header beam across the whole front, the fascia up to the ceiling and a neon line
        // carrying on round from the side walls.
        b.box(wl, glassTop, zg - 2.5f, w - wl, headTop, front + 8f, BoxFaces(back = frame, gloss = 0.4f))
        under(b, wl, w - wl, zg - 2.5f, front + 8f, glassTop, frame)
        val upper = HallArt.upperWall.region(wrap = true)
        val ceil = HubLayout.CEILING
        b.quad(w - wl, ceil, front + 7.9f, wl, ceil, front + 7.9f, wl, headTop, front + 7.9f, w - wl, headTop, front + 7.9f, upper, 0f, 0f, -1f, u0 = w - wl, u1 = wl, v1 = ceil - headTop)
        val hgt = HubLayout.WALL_HEIGHT
        b.quad(w - wl, hgt - 8f, front + 7.8f, wl, hgt - 8f, front + 7.8f, wl, hgt - 10f, front + 7.8f, w - wl, hgt - 10f, front + 7.8f, HallArt.solid(0xFF39E6F2.toInt()).full, 0f, 0f, -1f, emissive = 1.8f)
        // Across the road: the shops opposite, and dark ground either side of the car park.
        val sz = front + 470f
        val half = 520f
        val tall = (w + 2f * half) * 320f / 1024f
        b.quad(w + half, tall - 3f, sz, -half, tall - 3f, sz, -half, -3f, sz, w + half, -3f, sz, HallArt.streetBackdrop.full, 0f, 0f, -1f, emissive = 0.75f)
        val ground = HallArt.asphalt.region(wrap = true)
        for (k in 0..1) {
            val xa = if (k == 0) -half else w + 60f
            val xb = if (k == 0) -60f else w + half
            b.quad(xa, -3f, front + 8f, xb, -3f, front + 8f, xb, -3f, sz, xa, -3f, sz, ground, 0f, 1f, 0f, u0 = xa * 3f, v0 = 0f, u1 = xb * 3f, v1 = (sz - front) * 3f, gloss = 0.15f)
        }
    }

    /** Three trusses across the hall: two lower chords and a top chord, laced with diagonals. */
    private fun buildTrusses(b: ModelBuilder, w: Float, wl: Float) {
        val chord = HallArt.brushedMetal.full
        val lace = HallArt.darkMetal.full
        val y0 = TRUSS_Y
        val y1 = TRUSS_Y + 7f
        for (z in TRUSS_Z) {
            val f = BoxFaces(front = chord, top = chord, back = chord, gloss = 0.8f)
            b.box(wl, y0 - 0.7f, z - 3.7f, w - wl, y0 + 0.7f, z - 2.3f, f)
            b.box(wl, y0 - 0.7f, z + 2.3f, w - wl, y0 + 0.7f, z + 3.7f, f)
            b.box(wl, y1 - 0.7f, z - 0.7f, w - wl, y1 + 0.7f, z + 0.7f, f)
            // Their undersides, for anyone looking up from the floor.
            under(b, wl, w - wl, z - 3.7f, z - 2.3f, y0 - 0.7f, chord)
            under(b, wl, w - wl, z + 2.3f, z + 3.7f, y0 - 0.7f, chord)
            var x = wl + 2f
            var up = true
            while (x < w - wl - 12f) {
                val nx = x + 12f
                val (ax, bx) = if (up) x to nx else nx to x
                b.capsule(ax, y0, z - 3f, bx, y1, z, 0.35f, lace, slices = 4)
                b.capsule(ax, y0, z + 3f, bx, y1, z, 0.35f, lace, slices = 4)
                x = nx
                up = !up
            }
            // Hung from the ceiling on rods.
            var hx = 90f
            while (hx < w) {
                b.capsule(hx, y1, z, hx, HubLayout.CEILING, z, 0.3f, lace, slices = 4)
                hx += 170f
            }
        }
    }

    /** A spotlight can pointing down its -y axis, its lens lit in [color]. */
    private fun can(color: Int): Model = cans.getOrPut(color) {
        val body = HallArt.paint(0xFF22212A.toInt(), 0.2f, 0.7f).full
        ModelBuilder()
            .cylinder(0f, 0f, -7f, 0f, 2.4f, 10, body, top = body, gloss = 0.6f)
            .cylinder(0f, 0f, -7.6f, -7f, 2.8f, 10, HallArt.darkMetal.full)
            .disc(0f, 0f, -7.65f, 2.3f, 10, HallArt.solid(color).full, ny = -1f, emissive = 2.4f)
            .build()
    }

    /** The start lights (red one by one, then green) and the entrance chase bulbs. */
    fun drawOpaque(r: Renderer3D, t: Float, minX: Float, maxX: Float, minZ: Float, maxZ: Float) {
        if (r.camera.ey < HubLayout.CEILING) indoor.draw(r, Blend.OPAQUE)
        if (hasRace && RACE_SIGN_Z > minZ - 200f && RACE_SIGN_Z < maxZ + 200f) {
            val cycle = t % 6f
            val span = raceX1 - raceX0 - 40f
            for (k in 0 until 5) {
                val x = raceX0 + 20f + span * (k + 0.5f) / 5f
                val lit = cycle < 3f && k < (cycle / 0.6f).toInt() + 1
                val green = cycle in 3.4f..4.8f
                val c = if (green) 0xFF3CFF6A.toInt() else if (lit) 0xFFFF2A2A.toInt() else 0xFF3A1414.toInt()
                val y = RACE_SIGN_Y0 - 4.2f
                r.sprite(x, y, RACE_SIGN_Z + 0.4f, 3.4f, 3.4f, TEX_DOT, emissive = if (lit || green) 2.2f else 0.6f, tint = c)
            }
        }
        val kz = HubLayout.FRONT_WALL + 4f
        if (kz > minZ && kz < maxZ + 100f) {
            val chase = (t * 10f).toInt()
            for (i in kneeBulbs.indices) {
                val x = kneeBulbs[i]
                if (x < minX || x > maxX) continue
                val on = (chase + i) % 4 == 0
                r.sprite(x, 16.6f, kz, 1.5f, 1.5f, bulb, emissive = if (on) 2f else 0.7f, tint = if (on) 0xFFFFF4C0.toInt() else 0xFF8A6A3A.toInt())
            }
        }
    }

    /** Beams in the haze, the pools the spots throw, and the halos of the lit bulbs. */
    fun drawGlow(r: Renderer3D, t: Float, minX: Float, maxX: Float, minZ: Float, maxZ: Float) {
        if (r.camera.ey < HubLayout.CEILING) indoor.draw(r, Blend.ALPHA)
        for (i in 0 until spots) {
            val ax = aimX[i]
            val az = aimZ[i]
            if (ax < minX - 40f || ax > maxX + 40f || az < minZ - 40f || az > maxZ + 40f) continue
            // A slow shimmer, as if dust drifts through the light.
            val shimmer = 0.85f + 0.15f * sin(t * 1.3f + i * 1.7f)
            r.beam(lensX[i], lensY[i], lensZ[i], ax, 0f, az, 46f, beam, Blend.ADD, emissive = 1f, alpha = 0.3f * shimmer, tint = spotColor[i])
            r.flat(ax, az, 0.25f, 58f, 40f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.32f * shimmer, tint = spotColor[i])
            r.flat(ax, az, 0.3f, 22f, 16f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.12f, tint = -1)
            r.sprite(lensX[i], lensY[i], lensZ[i], 10f, 10f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.75f, tint = spotColor[i])
        }
        if (hasRace && RACE_SIGN_Z > minZ - 200f && RACE_SIGN_Z < maxZ + 200f) {
            val cycle = t % 6f
            val span = raceX1 - raceX0 - 40f
            for (k in 0 until 5) {
                val lit = cycle < 3f && k < (cycle / 0.6f).toInt() + 1
                val green = cycle in 3.4f..4.8f
                if (!lit && !green) continue
                val x = raceX0 + 20f + span * (k + 0.5f) / 5f
                r.sprite(x, RACE_SIGN_Y0 - 4.2f, RACE_SIGN_Z + 0.8f, 11f, 11f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.75f, tint = if (green) 0xFF3CFF6A.toInt() else 0xFFFF2A2A.toInt())
            }
            // The sign's neon hums, and now and then stutters.
            val stutter = hash01((t * 14f).toInt(), 5) > 0.97f
            r.quad(
                raceX0, RACE_SIGN_Y1 + 3f, RACE_SIGN_Z + 0.9f, raceX1, RACE_SIGN_Y1 + 3f, RACE_SIGN_Z + 0.9f,
                raceX1, RACE_SIGN_Y0 - 3f, RACE_SIGN_Z + 0.9f, raceX0, RACE_SIGN_Y0 - 3f, RACE_SIGN_Z + 0.9f,
                halo, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = if (stutter) 0.05f else 0.28f + 0.04f * sin(t * 9f), cull = false, tint = 0xFFFF3B40.toInt(),
            )
        }
        val kz = HubLayout.FRONT_WALL + 4f
        if (kz > minZ && kz < maxZ + 100f) {
            val chase = (t * 10f).toInt()
            for (i in kneeBulbs.indices) {
                val x = kneeBulbs[i]
                if (x < minX || x > maxX || (chase + i) % 4 != 0) continue
                r.sprite(x, 16.6f, kz + 0.3f, 6f, 6f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.6f, tint = 0xFFFFD27A.toInt())
            }
        }
    }
}

private val TEX_DOT by lazy { com.pocketarcade.engine.r3d.TexKit.dot.full }
