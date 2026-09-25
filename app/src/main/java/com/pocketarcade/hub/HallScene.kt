package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.MiniGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the whole arcade: the floor and walls, every cabinet and fixture, the crowd, the
 * lighting rig and all the glass, neon and chase lights, for whatever part of the hall the
 * camera can see.
 */
class HallScene(val map: HubMap, private val games: List<MiniGame>) {
    private val arts = games.map { MachineArt(it) }
    private val units = map.props.filter { it.kind == PropKind.MACHINE }.map { MachineUnit(it, games[it.machine], arts[it.machine]) }
    private val fixtures = ArrayList<Pair<Prop, Model>>()
    private val lights = ArrayList<PointLight>()
    private val baseIntensity = HashMap<PointLight, Float>()
    private val structure: Model
    private val floorShade: Texture
    private val figures = HashMap<CharacterLook, Figure>()
    private val corners = FloatArray(2)
    private val bulb = HallArt.solid(-1).full
    private val halo = HallArt.glow.full
    private val shadow = HallArt.shadow.full

    init {
        for (p in map.props) if (p.kind != PropKind.MACHINE) fixtures += p to Props.build(p, lights)
        for (u in units) lights += u.lights
        // Ceiling downlights in a grid.
        var z = 90f
        while (z < HubLayout.FRONT_WALL) {
            var x = 76f
            while (x < HubLayout.WIDTH) {
                lights += PointLight(x, 150f, z, 1f, 0.9f, 0.78f, 175f, 0.55f)
                x += 120f
            }
            z += 120f
        }
        // Neon along the walls.
        lights += PointLight(92f, 110f, 40f, 0.3f, 0.9f, 1f, 130f, 0.7f)
        lights += PointLight(534f, 110f, 40f, 0.7f, 0.4f, 1f, 130f, 0.7f)
        for (l in lights) baseIntensity[l] = l.intensity
        structure = buildStructure()
        floorShade = buildFloorShade()
    }

    // ------------------------------------------------------------------ static structure

    private fun buildStructure(): Model {
        val b = ModelBuilder()
        val w = HubLayout.WIDTH.toFloat()
        val wl = HubLayout.WALL
        val back = HubLayout.BACK_WALL
        val front = HubLayout.FRONT_WALL
        val hgt = HubLayout.WALL_HEIGHT
        val carpet = HallArt.carpet.region(wrap = true)
        val tiles = HallArt.tiles.region(wrap = true)
        val cTpu = 7f
        val tTpu = 6f
        fun floor(x0: Float, z0: Float, x1: Float, z1: Float, tex: com.pocketarcade.engine.r3d.Region, tpu: Float, gloss: Float) {
            b.quad(x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, x0, 0f, z1, tex, 0f, 1f, 0f, u0 = x0 * tpu, v0 = z0 * tpu, u1 = x1 * tpu, v1 = z1 * tpu, gloss = gloss)
        }
        // Tiles by the prize counter and at the entrance; carpet everywhere else.
        floor(180f, back, 428f, 128f, tiles, tTpu, 0.6f)
        floor(wl, front - 80f, w - wl, front, tiles, tTpu, 0.6f)
        floor(wl, back, 180f, 128f, carpet, cTpu, 0f)
        floor(428f, back, w - wl, 128f, carpet, cTpu, 0f)
        floor(wl, 128f, w - wl, front - 80f, carpet, cTpu, 0f)
        // Outside: the sidewalk, a kerb and the parking lot, seen through the cut-away front.
        val out = front + 8f
        b.quad(-60f, 0f, out, w + 60f, 0f, out, w + 60f, 0f, out + 84f, -60f, 0f, out + 84f, HallArt.concrete.region(wrap = true), 0f, 1f, 0f, u0 = -60f * 6f, v0 = 0f, u1 = (w + 60f) * 6f, v1 = 84f * 6f, gloss = 0.1f)
        val kerb = HallArt.solid(0xFF6A6870.toInt()).full
        b.box(-60f, -3f, out + 84f, w + 60f, 0f, out + 88f, BoxFaces(top = kerb, back = kerb))
        b.quad(-60f, -3f, out + 88f, w + 60f, -3f, out + 88f, w + 60f, -3f, out + 480f, -60f, -3f, out + 480f, HallArt.asphalt.region(wrap = true), 0f, 1f, 0f, u0 = 0f, v0 = 0f, u1 = (w + 120f) * 3f, v1 = 392f * 3f, gloss = 0.15f)
        val paint = HallArt.solid(0xFFE8E4D8.toInt()).full
        var lx = 40f
        while (lx < w) {
            b.quad(lx, -2.9f, out + 104f, lx + 2f, -2.9f, out + 104f, lx + 2f, -2.9f, out + 220f, lx, -2.9f, out + 220f, paint, 0f, 1f, 0f)
            lx += 64f
        }
        // Planters either side of the entrance.
        val planter = HallArt.darkMetal.full
        val hedge = HallArt.paint(0xFF2F6A34.toInt(), 0.25f, 0.6f).full
        for (px in floatArrayOf(HubLayout.DOOR_X0 - 44f, HubLayout.DOOR_X1 + 14f)) {
            b.box(px, 0f, out + 10f, px + 30f, 10f, out + 26f, BoxFaces.all(planter, 0.5f))
            b.box(px + 2f, 10f, out + 12f, px + 28f, 15f, out + 24f, BoxFaces.all(hedge, 0.1f))
        }
        // Street lamps along the kerb.
        val pole = HallArt.darkMetal.full
        for (sx in floatArrayOf(104f, w - 104f)) {
            b.cylinder(sx, out + 76f, 0f, 120f, 1.6f, 10, pole, top = pole, gloss = 0.6f)
            b.box(sx - 1.2f, 116f, out + 60f, sx + 1.2f, 119f, out + 78f, BoxFaces.all(pole, 0.6f))
            b.box(sx - 5f, 112f, out + 56f, sx + 5f, 116f, out + 66f, BoxFaces.all(pole, 0.6f))
            b.quad(sx - 4.5f, 111.9f, out + 56.5f, sx + 4.5f, 111.9f, out + 56.5f, sx + 4.5f, 111.9f, out + 65.5f, sx - 4.5f, 111.9f, out + 65.5f, HallArt.solid(0xFFFFE6B8.toInt()).full, 0f, -1f, 0f, emissive = 1.8f, cull = false)
            lights += PointLight(sx, 100f, out + 62f, 1f, 0.82f, 0.6f, 150f, 0.9f)
        }
        b.quad(270f, 0.1f, front - 52f, 338f, 0.1f, front - 52f, 338f, 0.1f, front - 6f, 270f, 0.1f, front - 6f, HallArt.mat.full, 0f, 1f, 0f)

        // Walls with a baseboard, a neon strip along the top and posters.
        val wall = HallArt.wall.region(wrap = true)
        val wTpu = 1.2f
        b.quad(wl, hgt, back, w - wl, hgt, back, w - wl, 0f, back, wl, 0f, back, wall, 0f, 0f, 1f, u0 = wl * wTpu, u1 = (w - wl) * wTpu, v1 = 256f)
        b.quad(wl, hgt, front, wl, hgt, back, wl, 0f, back, wl, 0f, front, wall, 1f, 0f, 0f, u0 = front * wTpu, u1 = back * wTpu, v1 = 256f)
        b.quad(w - wl, hgt, back, w - wl, hgt, front, w - wl, 0f, front, w - wl, 0f, back, wall, -1f, 0f, 0f, u0 = back * wTpu, u1 = front * wTpu, v1 = 256f)
        // Above the lower walls they carry on up into the dark: acoustic panels, a ledge, a
        // backlit mural over the prize counter and coloured uplights washing up the walls.
        val top = HubLayout.CEILING
        val upper = HallArt.upperWall.region(wrap = true)
        b.quad(wl, top, back, w - wl, top, back, w - wl, hgt, back, wl, hgt, back, upper, 0f, 0f, 1f, u0 = wl, u1 = w - wl, v1 = top - hgt)
        b.quad(wl, top, front + 8f, wl, top, back, wl, hgt, back, wl, hgt, front + 8f, upper, 1f, 0f, 0f, u0 = front, u1 = back, v1 = top - hgt)
        b.quad(w - wl, top, back, w - wl, top, front + 8f, w - wl, hgt, front + 8f, w - wl, hgt, back, upper, -1f, 0f, 0f, u0 = back, u1 = front, v1 = top - hgt)
        val ledge = HallArt.darkMetal.full
        b.box(wl, hgt, back, w - wl, hgt + 3f, back + 4f, BoxFaces(front = ledge, top = ledge, gloss = 0.5f))
        b.box(wl, hgt, back, wl + 4f, hgt + 3f, front, BoxFaces(right = ledge, top = ledge, gloss = 0.5f))
        b.box(w - wl - 4f, hgt, back, w - wl, hgt + 3f, front, BoxFaces(left = ledge, top = ledge, gloss = 0.5f))
        b.box(200f, hgt + 22f, back, 408f, hgt + 128f, back + 2f, BoxFaces(front = ledge, top = ledge, left = ledge, right = ledge))
        b.quad(204f, hgt + 124f, back + 2.1f, 404f, hgt + 124f, back + 2.1f, 404f, hgt + 26f, back + 2.1f, 204f, hgt + 26f, back + 2.1f, HallArt.mural.full, 0f, 0f, 1f, emissive = 0.9f)
        val washColors = intArrayOf(0xFFFF4FA8.toInt(), 0xFF39E6F2.toInt(), 0xFF9B6BFF.toInt())
        var wz = 200f
        var k = 0
        while (wz < front - 60f) {
            val c = washColors[k % washColors.size]
            b.quad(wl + 0.8f, hgt + 190f, wz - 34f, wl + 0.8f, hgt + 190f, wz + 34f, wl + 0.8f, hgt + 4f, wz + 34f, wl + 0.8f, hgt + 4f, wz - 34f, HallArt.washer.full, 1f, 0f, 0f, blend = Blend.ADD, emissive = 1f, cull = false, tint = c)
            val c2 = washColors[(k + 1) % washColors.size]
            b.quad(w - wl - 0.8f, hgt + 190f, wz + 34f, w - wl - 0.8f, hgt + 190f, wz - 34f, w - wl - 0.8f, hgt + 4f, wz - 34f, w - wl - 0.8f, hgt + 4f, wz + 34f, HallArt.washer.full, -1f, 0f, 0f, blend = Blend.ADD, emissive = 1f, cull = false, tint = c2)
            wz += 140f
            k++
        }
        for ((i, wx) in floatArrayOf(96f, 512f).withIndex()) {
            b.quad(wx - 40f, hgt + 190f, back + 0.8f, wx + 40f, hgt + 190f, back + 0.8f, wx + 40f, hgt + 4f, back + 0.8f, wx - 40f, hgt + 4f, back + 0.8f, HallArt.washer.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, cull = false, tint = washColors[i + 1])
        }

        // The front wall is cut away to a knee wall so the camera can always see in.
        val stub = 14f
        val cap = HallArt.brushedMetal.full
        val face = HallArt.paint(0xFF2A2140.toInt(), 0.1f, 0.6f).full
        for ((xa, xb) in listOf(wl to HubLayout.DOOR_X0, HubLayout.DOOR_X1 to w - wl)) {
            b.box(xa, 0f, front, xb, stub, front + 8f, BoxFaces(front = face, back = face, left = face, right = face))
            b.box(xa - 0.5f, stub, front - 0.5f, xb + 0.5f, stub + 1.5f, front + 8.5f, BoxFaces.all(cap, 0.8f))
        }
        // The side walls end in a pillar at the front corners.
        b.box(0f, 0f, front, wl, HubLayout.CEILING, front + 8f, BoxFaces(front = face, top = cap, left = face, right = face))
        b.box(w - wl, 0f, front, w, HubLayout.CEILING, front + 8f, BoxFaces(front = face, top = cap, left = face, right = face))
        val neonCyan = HallArt.solid(0xFF39E6F2.toInt()).full
        val neonPink = HallArt.solid(0xFFFF4FA8.toInt()).full
        b.quad(wl, hgt - 8f, back + 0.3f, w - wl, hgt - 8f, back + 0.3f, w - wl, hgt - 10f, back + 0.3f, wl, hgt - 10f, back + 0.3f, neonPink, 0f, 0f, 1f, emissive = 1.8f)
        b.quad(wl + 0.3f, hgt - 8f, front, wl + 0.3f, hgt - 8f, back, wl + 0.3f, hgt - 10f, back, wl + 0.3f, hgt - 10f, front, neonCyan, 1f, 0f, 0f, emissive = 1.8f)
        b.quad(w - wl - 0.3f, hgt - 8f, back, w - wl - 0.3f, hgt - 8f, front, w - wl - 0.3f, hgt - 10f, front, w - wl - 0.3f, hgt - 10f, back, neonCyan, -1f, 0f, 0f, emissive = 1.8f)
        // Posters on the side walls.
        for (k in 0 until 4) {
            val pz = 300f + k * 130f
            val tex = HallArt.poster(k).full
            b.quad(wl + 0.4f, 100f, pz - 12f, wl + 0.4f, 100f, pz + 12f, wl + 0.4f, 64f, pz + 12f, wl + 0.4f, 64f, pz - 12f, tex, 1f, 0f, 0f, gloss = 0.5f)
            val tex2 = HallArt.poster(k + 1).full
            b.quad(w - wl - 0.4f, 100f, pz + 12f, w - wl - 0.4f, 100f, pz - 12f, w - wl - 0.4f, 64f, pz - 12f, w - wl - 0.4f, 64f, pz + 12f, tex2, -1f, 0f, 0f, gloss = 0.5f)
        }
        // Big neon signs on the back wall.
        b.quad(22f, 132f, back + 0.5f, 164f, 132f, back + 0.5f, 164f, 96f, back + 0.5f, 22f, 96f, back + 0.5f, HallArt.neon("POCKET ARCADE", 0xFF39E6F2.toInt(), 768, 160, 96f).full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        b.quad(470f, 132f, back + 0.5f, 590f, 132f, back + 0.5f, 590f, 100f, back + 0.5f, 470f, 100f, back + 0.5f, HallArt.neon("HIGH SCORE", 0xFFB080FF.toInt(), 640, 160, 96f).full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        // Zone signs on the side walls.
        b.quad(wl + 0.6f, 124f, 190f, wl + 0.6f, 124f, 290f, wl + 0.6f, 100f, 290f, wl + 0.6f, 100f, 190f, HallArt.neon("SKEE-BALL", 0xFFFFD84D.toInt(), 640, 160, 100f).full, 1f, 0f, 0f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        b.quad(w - wl - 0.6f, 124f, 280f, w - wl - 0.6f, 124f, 180f, w - wl - 0.6f, 100f, 180f, w - wl - 0.6f, 100f, 280f, HallArt.neon("HOOPS", 0xFFFF8A3D.toInt(), 512, 160, 110f).full, -1f, 0f, 0f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        b.quad(wl + 0.6f, 116f, 510f, wl + 0.6f, 116f, 630f, wl + 0.6f, 92f, 630f, wl + 0.6f, 92f, 510f, HallArt.neon("SNACK BAR", 0xFF5CF08A.toInt(), 640, 160, 100f).full, 1f, 0f, 0f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        // Baseboards.
        val base = HallArt.darkMetal.full
        b.box(wl, 0f, back, w - wl, 4f, back + 1f, BoxFaces(front = base, top = base))
        b.box(wl, 0f, back, wl + 1f, 4f, front, BoxFaces(right = base, top = base))
        b.box(w - wl - 1f, 0f, back, w - wl, 4f, front, BoxFaces(left = base, top = base))
        return b.build()
    }

    /** Soft darkening of the floor under and around everything that stands on it. */
    private fun buildFloorShade(): Texture {
        val s = 0.5f
        val w = (HubLayout.WIDTH * s).toInt()
        val h = (HubLayout.DEPTH * s).toInt()
        val tp = TexPaint(w, h)
        tp.clear(0)
        tp.glow(10f, alpha(0xFF000000.toInt(), 0.55f)) {
            for (p in map.props) {
                if (p.decor == DecorStyle.DISCO_BALL || p.kind == PropKind.DOORS) continue
                rect(p.x0 * s, p.z0 * s, (p.x1 - p.x0) * s, (p.z1 - p.z0) * s, -1)
            }
            rect(0f, 0f, w.toFloat(), HubLayout.BACK_WALL * s + 4f, -1)
            rect(0f, 0f, HubLayout.WALL * s + 4f, h.toFloat(), -1)
            rect(w - HubLayout.WALL * s - 4f, 0f, HubLayout.WALL * s + 4f, h.toFloat(), -1)
        }
        return tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ per frame

    private fun figureFor(look: CharacterLook): Figure = figures.getOrPut(look) { Figure(look) }

    fun render(r: Renderer3D, world: HubWorld, save: SaveState) {
        val t = world.time
        val cam = r.camera

        // What part of the floor is on screen (plus margins for tall things).
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for ((sx, sy) in listOf(0f to 0f, r.width.toFloat() to 0f, 0f to r.height.toFloat(), r.width.toFloat() to r.height.toFloat())) {
            if (cam.rayToPlaneY(sx, sy, 0f, corners)) {
                minX = minOf(minX, corners[0]); maxX = maxOf(maxX, corners[0])
                minZ = minOf(minZ, corners[1]); maxZ = maxOf(maxZ, corners[1])
            } else {
                minZ = -200f
            }
        }
        if (minX > maxX) {
            minX = 0f; maxX = HubLayout.WIDTH.toFloat(); minZ = 0f; maxZ = HubLayout.DEPTH.toFloat()
        }
        minX -= 60f; maxX += 60f; minZ -= 140f; maxZ += 60f
        fun visible(x0: Float, z0: Float, x1: Float, z1: Float) = x1 > minX && x0 < maxX && z1 > minZ && z0 < maxZ

        // Lighting: dim hall, warm downlights, every machine glowing its colour.
        val l = r.lighting
        l.ambR = 0.30f; l.ambG = 0.27f; l.ambB = 0.38f
        l.setDirection(0.1f, 1f, 0.35f)
        l.dirR = 0.16f; l.dirG = 0.15f; l.dirB = 0.18f
        l.points.clear()
        val camX = (minX + maxX) / 2f
        val camZ = (minZ + maxZ) / 2f
        val candidates = lights.filter { it.x + it.radius > minX && it.x - it.radius < maxX && it.z + it.radius > minZ && it.z - it.radius < maxZ }
            .sortedBy { abs(it.x - camX) + abs(it.z - camZ) }
        for ((i, pl) in candidates.withIndex()) {
            if (i >= 64) break
            val base = baseIntensity[pl] ?: pl.intensity
            pl.intensity = base * (0.94f + 0.06f * sin(t * 2.3f + pl.x * 0.05f + pl.z * 0.03f))
            l.points += pl
        }
        r.fogNear = 760f
        r.fogFar = 1800f
        r.fogFloor = 0.35f
        r.exposure = 1.25f
        r.bloom = 0.85f
        r.clear(0xFF07050E.toInt())

        structure.draw(r, Blend.OPAQUE)

        for ((p, m) in fixtures) {
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            m.draw(r, Blend.OPAQUE)
        }
        for (u in units) {
            val p = u.prop
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            u.refresh(save.highScore(u.game.id), t)
            u.drawOpaque(r, t)
        }

        // The crowd.
        val pl = world.player
        pl.look?.let { figureFor(it).draw(r, pl.x, 0f, pl.y, pl.yaw, pl.pose, pl.phase, t) }
        for (n in world.npcs) {
            if (!visible(n.x - 10f, n.y - 10f, n.x + 10f, n.y + 10f)) continue
            figureFor(n.look).draw(r, n.x, 0f, n.y, n.yaw, n.pose, n.phase, t + n.seed)
        }
        if (visible(map.clerkX - 10f, map.clerkY - 10f, map.clerkX + 10f, map.clerkY + 10f)) {
            figureFor(Looks.clerk).draw(r, map.clerkX, 0f, map.clerkY, sin(t * 0.4f) * 0.4f, Pose.STAND, 0f, t, 1.12f)
        }

        // ---- see-through layers, back to front where it matters.
        r.decal(304f - 62f, 606f, 304f + 62f, 730f, 0.05f, HallArt.floorLogo.full, blend = Blend.ALPHA)
        r.decal(0f, 0f, HubLayout.WIDTH.toFloat(), HubLayout.DEPTH.toFloat(), 0.08f, floorShade.full, blend = Blend.ALPHA)
        for (u in units) {
            val p = u.prop
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            val pulse = 0.28f + 0.06f * sin(t * 2f + p.centerX * 0.1f)
            r.decal(p.x0 - 12f, p.z1 - 4f, p.x1 + 12f, p.z1 + 40f, 0.15f, halo, Blend.ADD, emissive = 1f, alpha = pulse, tint = u.art.glow)
        }
        shadowAt(r, pl.x, pl.y, 1f)
        for (n in world.npcs) if (visible(n.x, n.y, n.x, n.y)) shadowAt(r, n.x, n.y, 1f)
        shadowAt(r, map.clerkX, map.clerkY, 1.1f)
        for ((p, m) in fixtures) {
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            m.draw(r, Blend.ALPHA)
        }
        for (u in units.sortedBy { it.prop.z0 }) {
            val p = u.prop
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            u.drawTransparent(r)
            u.drawBulbs(r, t, bulb, halo)
        }
        structure.draw(r, Blend.ADD)
        for ((p, m) in fixtures) {
            if (!visible(p.x0, p.z0, p.x1, p.z1)) continue
            m.draw(r, Blend.ADD)
        }
        if (map.props.any { it.decor == DecorStyle.DISCO_BALL }) drawDiscoSpots(r, t)
    }

    private fun shadowAt(r: Renderer3D, x: Float, z: Float, s: Float) {
        r.flat(x, z + 1f, 0.2f, 20f * s, 14f * s, shadow, blend = Blend.ALPHA, alpha = 0.55f)
    }

    private fun drawDiscoSpots(r: Renderer3D, t: Float) {
        val colors = intArrayOf(0xFFFF4FA8.toInt(), 0xFF39E6F2.toInt(), 0xFFFFD84D.toInt(), 0xFF9B6BFF.toInt())
        for (k in 0 until 14) {
            val a = t * 0.6f + k * 0.45f
            val d = 60f + hash01(k, 3) * 120f
            val x = map.discoX + cos(a) * d
            val z = map.discoY + sin(a * 1.1f) * d
            r.flat(x, z, 0.3f, 14f, 14f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = colors[k % colors.size])
        }
    }
}
