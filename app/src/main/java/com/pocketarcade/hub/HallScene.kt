package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.HatStyle
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.gl.Warmup
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Frustum
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.MiniGame
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the whole arcade: the floor and walls, every cabinet and fixture, the crowd, the
 * lighting rig and all the glass, neon and chase lights, for whatever part of the hall the
 * camera can see.
 *
 * Building one takes seconds (hundreds of painted textures and models), so the game builds it
 * once, a few milliseconds a frame behind a loading screen ([HallKit.plan]), keeps it for as long
 * as the app runs, and only [adopt]s the small changes a purchase makes.
 */
class HallScene internal constructor(kit: HallKit) {
    /**
     * Builds the whole scene now, on the calling thread. The game builds it a few milliseconds a
     * frame behind a loading screen instead; this is for anything that just needs a scene, and for
     * a hall that was never preloaded.
     */
    constructor(map: HubMap, games: List<MiniGame>) : this(HallKit(map, games).also { it.buildAll() })

    private companion object {
        /** The renderer's point-light budget. */
        const val MAX_LIGHTS = 64
        /** Everything standing on the floor fits under this: cabinets with their toppers, fixtures. */
        const val STAND_HEIGHT = 170f
        /** A kid with a hat on. */
        const val FIGURE_HEIGHT = 62f
        /** The lighting rig, its beams and the hung signs reach up to here. */
        const val RIG_HEIGHT = 290f
        /** Slack round each thing's floor box for what overhangs it (marquee lips, shadows). */
        const val MARGIN = 14f
        /** Overhead the whole hall is in range; first person draws out to here, where the fog is black. */
        const val OVERHEAD_FAR = 3000f
        const val FP_DRAW_DISTANCE = 900f
        const val FP_FOG_NEAR = 260f
        val DISCO_COLORS = intArrayOf(0xFFFF4FA8.toInt(), 0xFF39E6F2.toInt(), 0xFFFFD84D.toInt(), 0xFF9B6BFF.toInt())

        /** A warm-up picture is packed with about this many polygons of models, so the GL thread's turn stays short. */
        const val WARM_POLYS = 2200
        /** Times an attract loop is sampled at, to draw the things that only move part of the time (a ball rolling up a lane). */
        val ATTRACT_TIMES = floatArrayOf(0f, 0.4f, 0.8f, 1.2f, 1.6f, 2.4f, 3.2f, 4.4f, 5.2f)
    }

    /**
     * The floor plan this scene shows. It changes when decorations are bought ([adopt]), never
     * because a cabinet or fixture moved: the scene is built once and kept.
     */
    var map: HubMap = kit.map
        private set
    private val games = kit.games
    /** Every cabinet, back to front: the order the see-through pass needs, fixed for the map's life. */
    private val units = kit.units
    private val fixtureProps = kit.fixtureProps
    private val fixtureModels = kit.fixtureModels
    /** The lights each fixture gives off (so a decoration taken away takes its lights with it). */
    private val fixtureLights = kit.fixtureLights
    /** The token kiosk and prize counter light up like a cabinet: each fixture's fade level and this frame's emissive boost. */
    private var fixtureLevel = FloatArray(fixtureProps.size)
    private var fixtureBoost = FloatArray(fixtureProps.size) { 1f }
    /** Hall time at the last frame, for the highlight fades (the world only hands over its clock). */
    private var lastTime = 0f
    private val lights = ArrayList<PointLight>()
    /** Each light's steady intensity (the per-frame flicker scales it). */
    private var baseIntensity: FloatArray
    private val structure: Model = kit.structure
    /** Trusses and spotlights, the racers' hung sign, the upper-wall murals, the entrance chase lights. */
    private val rig: HallRig = kit.rig
    private val floorShade: Texture = kit.floorShade
    /** The floor under each bought decoration: each has its own patch, so buying one repaints nothing big. */
    private val decorShades = kit.decorShades
    /** The plushies on the prize wall: won ones in colour, the rest dark silhouettes. */
    private val prizeWall = kit.prizeWall
    private var hasDisco = kit.map.props.any { it.decor == DecorStyle.DISCO_BALL }
    private val figures = kit.figures
    private val corners = FloatArray(2)
    /** The camera's view out to the draw distance; everything below is culled against it. */
    private val view = Frustum()
    private val footprint = FloatArray(4)
    private val bulb = HallArt.solid(-1).full
    private val halo = HallArt.glow.full
    /** Kids' shadows: a contact blob and their silhouette cast away from the lamps. */
    private val shadows = FigureShadow(HallArt.shadow.full)
    // Café: the barista, slushie tanks and steam are drawn by CafeScene.kt.
    private val cafe = kit.cafe

    // The part of the floor under anything in view this frame (plus margins), the same for the
    // lighting rig overhead, and the lights picked for it.
    private var minX = 0f
    private var maxX = 0f
    private var minZ = 0f
    private var maxZ = 0f
    private var rigMinX = 0f
    private var rigMaxX = 0f
    private var rigMinZ = 0f
    private var rigMaxZ = 0f
    /** The floor point lights are ranked from: what the camera looks at, or just ahead of the eye. */
    private var keyX = 0f
    private var keyZ = 0f
    private val pickIndex = IntArray(MAX_LIGHTS)
    private val pickKey = FloatArray(MAX_LIGHTS)

    init {
        // The order the scene has always had them in: the fixtures', the cabinets', the ceiling
        // downlights and wall neon, then the street lamps.
        for (ls in fixtureLights) lights += ls
        for (u in units) lights += u.lights
        lights += kit.hallLights
        lights += kit.structureLights
        baseIntensity = FloatArray(lights.size) { lights[it].intensity }
    }

    // ------------------------------------------------------------------ changes to the hall

    /**
     * Brings the scene up to date with [newMap], which differs from [map] only in which
     * decorations are bought: the new ones are built (a model, a floor patch, their lights) and
     * the ones no longer there dropped, and everything else, the hundreds of textures and models
     * of the hall, stays as it is. Returns the models built, so the caller can have the GPU take
     * them before they are first drawn, or null if something other than a decoration changed and
     * the scene can't be adjusted (build a new one).
     */
    internal fun adopt(newMap: HubMap): List<Model>? {
        if (newMap === map) return emptyList()
        val diff = DecorDiff.between(map, newMap) ?: return null
        val built = ArrayList<Model>()
        for (gone in diff.removed) {
            val i = fixtureProps.indexOfFirst { DecorDiff.sameProp(it, gone) }
            if (i < 0) continue
            lights.removeAll(fixtureLights[i].toSet())
            fixtureProps.removeAt(i)
            fixtureModels.removeAt(i)
            fixtureLights.removeAt(i)
            fixtureLevel = fixtureLevel.without(i)
            decorShades.removeAll { DecorDiff.sameProp(it.prop, gone) }
        }
        for (p in diff.added) {
            val ls = ArrayList<PointLight>()
            val m = Props.build(p, ls)
            fixtureProps += p
            fixtureModels += m
            fixtureLights += ls
            fixtureLevel = fixtureLevel.copyOf(fixtureLevel.size + 1)
            lights += ls
            built += m
            if (p.decor != DecorStyle.DISCO_BALL) decorShades += DecorShade.build(p)
        }
        // The highlight levels of the fixtures that stay carry on (the counter is lit while its owner shops); the
        // boosts are worked out afresh every frame. The arrays only ever change size here, never in a frame.
        fixtureBoost = FloatArray(fixtureProps.size) { 1f }
        baseIntensity = FloatArray(lights.size) { lights[it].intensity }
        hasDisco = newMap.props.any { it.decor == DecorStyle.DISCO_BALL }
        map = newMap
        return built
    }

    private fun FloatArray.without(i: Int): FloatArray {
        val out = FloatArray(size - 1)
        System.arraycopy(this, 0, out, 0, i)
        System.arraycopy(this, i + 1, out, i, size - i - 1)
        return out
    }

    /** Makes the figure for [look] now (a kid's new hat or outfit), rather than in the frame that first draws it. */
    internal fun prepareLook(look: CharacterLook): Figure = figureFor(look)

    /**
     * What a warm-up should draw, as pictures for [Warmup.submit]: between them every model and
     * texture the hall draws (the whole hall, seen or not), a small share each so the GL thread
     * isn't held up for long by any one.
     */
    internal fun warmJobs(): List<(Renderer3D) -> Unit> {
        val jobs = ArrayList<(Renderer3D) -> Unit>()
        jobs += { r ->
            structure.draw(r)
            for (t in listOf(floorShade, HallArt.floorLogo, HallArt.mat, HallArt.shaft, HallArt.beam, HallArt.glow, HallArt.shadow, HallArt.solid(-1))) {
                Warmup.touch(r, t.full)
            }
            for (d in decorShades) Warmup.touch(r, d.tex.full)
        }
        jobs += { r -> rig.warm(r) }
        jobs += batches(fixtureModels.toList(), { it.polys.size }) { r, m -> m.draw(r) }
        jobs += batches(units.toList(), { it.model.polys.size }) { r, u ->
            u.drawOpaque(r, 0f)
            u.drawTransparent(r)
        }
        // The attract loops' moving parts (claw, moles, puck, balls) are shared by every copy of
        // a cabinet: one copy per game, drawn at several moments, gets them all.
        val firstOfGame = units.distinctBy { it.game }
        jobs += batches(firstOfGame, { 300 }) { r, u -> for (t in ATTRACT_TIMES) u.drawOpaque(r, t) }
        jobs += batches(figures.values.toList(), { 500 }) { r, f -> f.draw(r, 0f, 0f, 0f, 0f, Pose.STAND, 0f, 0f) }
        jobs += { r ->
            for (h in HatStyle.entries) Figure.hat(h).draw(r)
            for (seed in 0 until 4) Figure.itemModel(Figure.ITEM_CUP, seed)?.draw(r)
            Figure.itemModel(Figure.ITEM_CONE, 0)?.draw(r)
            cafe.warm(r)
        }
        prizeWall?.let { wall -> jobs += { r -> wall.draw(r, emptyMap()) } }
        return jobs
    }

    /** Groups [items] into jobs of about [WARM_POLYS] polygons each (by [cost]), each drawing its items with [draw]. */
    private fun <T> batches(items: List<T>, cost: (T) -> Int, draw: (Renderer3D, T) -> Unit): List<(Renderer3D) -> Unit> {
        val out = ArrayList<(Renderer3D) -> Unit>()
        var batch = ArrayList<T>()
        var total = 0
        for (item in items) {
            batch += item
            total += cost(item)
            if (total >= WARM_POLYS) {
                val b = batch
                out += { r -> for (x in b) draw(r, x) }
                batch = ArrayList()
                total = 0
            }
        }
        if (batch.isNotEmpty()) {
            val b = batch
            out += { r -> for (x in b) draw(r, x) }
        }
        return out
    }

    // ------------------------------------------------------------------ per frame

    private fun figureFor(look: CharacterLook): Figure = figures.getOrPut(look) { Figure(look) }

    /**
     * Whether something standing on a floor rectangle (up to [height] tall) is in this frame's
     * view: its box, with [MARGIN] of slack, reaches the camera's view volume.
     */
    private fun visible(x0: Float, z0: Float, x1: Float, z1: Float, height: Float = STAND_HEIGHT) =
        x1 > minX && x0 < maxX && z1 > minZ && z0 < maxZ &&
            view.boxVisible(x0 - MARGIN, 0f, z0 - MARGIN, x1 + MARGIN, height, z1 + MARGIN)

    /** A fixture's box runs up past its own height (the disco ball hangs on a chain above it). */
    private fun fixtureVisible(p: Prop) = visible(p.x0, p.z0, p.x1, p.z1, maxOf(STAND_HEIGHT, p.height + 64f))

    /** A cabinet counts as in view while any of it, or the glow it throws on the floor in front, is. */
    private fun unitVisible(p: Prop) = visible(p.x0 - 12f, p.z0, p.x1 + 12f, p.z1 + 40f)

    /**
     * Works out what is in view: the view volume out to the draw distance, the floor under
     * anything standing in it, the same for the rig overhead, and the point the lights are
     * ranked from. Right for any camera, looking down on the hall or straight along it.
     */
    private fun cullFor(r: Renderer3D, fp: Float) {
        val cam = r.camera
        val far = OVERHEAD_FAR + (FP_DRAW_DISTANCE - OVERHEAD_FAR) * fp
        view.set(cam, far)
        if (view.footprint(0f, STAND_HEIGHT, footprint)) {
            minX = footprint[0] - MARGIN; maxX = footprint[1] + MARGIN
            minZ = footprint[2] - MARGIN; maxZ = footprint[3] + MARGIN
        } else {
            // Looking wholly above everything: nothing on the floor is in view.
            minX = 0f; maxX = -1f; minZ = 0f; maxZ = -1f
        }
        if (view.footprint(0f, RIG_HEIGHT, footprint)) {
            rigMinX = footprint[0] - MARGIN; rigMaxX = footprint[1] + MARGIN
            rigMinZ = footprint[2] - MARGIN; rigMaxZ = footprint[3] + MARGIN
        } else {
            rigMinX = 0f; rigMaxX = -1f; rigMinZ = 0f; rigMaxZ = -1f
        }
        // Rank lights from the floor at the middle of the screen overhead, and from just ahead of
        // the eye in first person, where the nearest (biggest on screen) surfaces are.
        var cx = (minX + maxX) / 2f
        var cz = (minZ + maxZ) / 2f
        if (cam.rayToPlaneY(r.width / 2f, r.height / 2f, 0f, corners)) {
            cx = corners[0]; cz = corners[1]
        }
        val fl = sqrt(cam.fx * cam.fx + cam.fz * cam.fz)
        val ahead = 60f
        val ex = cam.ex + if (fl > 1e-3f) cam.fx / fl * ahead else 0f
        val ez = cam.ez + if (fl > 1e-3f) cam.fz / fl * ahead else 0f
        keyX = cx + (ex - cx) * fp
        keyZ = cz + (ez - cz) * fp
    }

    /**
     * Picks the lights that reach what's in view, nearest the key point first (big lights count
     * as a little nearer), up to the renderer's budget, and sets their flicker. Allocation-free:
     * this runs every frame.
     */
    private fun pickLights(r: Renderer3D, t: Float) {
        val camX = keyX
        val camZ = keyZ
        var n = 0
        for (i in lights.indices) {
            val pl = lights[i]
            if (pl.x + pl.radius <= rigMinX || pl.x - pl.radius >= rigMaxX || pl.z + pl.radius <= rigMinZ || pl.z - pl.radius >= rigMaxZ) continue
            if (!view.sphereVisible(pl.x, pl.y, pl.z, pl.radius)) continue
            val dx = pl.x - camX
            val dz = pl.z - camZ
            val key = sqrt(dx * dx + dz * dz) - 0.35f * pl.radius
            if (n == MAX_LIGHTS && key >= pickKey[n - 1]) continue
            // Insertion into the sorted pick list, dropping the farthest when it's full.
            var j = if (n < MAX_LIGHTS) n++ else n - 1
            while (j > 0 && pickKey[j - 1] > key) {
                pickKey[j] = pickKey[j - 1]
                pickIndex[j] = pickIndex[j - 1]
                j--
            }
            pickKey[j] = key
            pickIndex[j] = i
        }
        val l = r.lighting
        for (k in 0 until n) {
            val i = pickIndex[k]
            val pl = lights[i]
            pl.intensity = baseIntensity[i] * (0.94f + 0.06f * sin(t * 2.3f + pl.x * 0.05f + pl.z * 0.03f))
            l.points += pl
        }
    }

    /**
     * Fades the highlight in on whatever [active] is the play spot of (a cabinet, the kiosk, the
     * prize counter) and out on everything else. Runs for every cabinet, seen or not, so one the
     * kid walked away from finishes fading even if it left the screen meanwhile.
     */
    private fun stepHighlights(active: Spot?, t: Float) {
        val dt = (t - lastTime).coerceIn(0f, 0.1f)
        lastTime = t
        for (i in units.indices) units[i].stepHighlight(active, dt, t)
        for (i in fixtureProps.indices) {
            val level = Highlight.step(fixtureLevel[i], Highlight.isSpotOf(active, fixtureProps[i]), dt)
            fixtureLevel[i] = level
            fixtureBoost[i] = Highlight.boost(level, t)
        }
    }

    fun render(r: Renderer3D, world: HubWorld, save: SaveState) {
        val t = world.time
        // 0 overhead … 1 first person (eased while switching).
        val fp = world.camera.fpAmount
        cullFor(r, fp)
        stepHighlights(world.activeSpot, t)

        // Lighting: dim hall, warm downlights, every machine glowing its colour.
        val l = r.lighting
        l.ambR = 0.30f; l.ambG = 0.27f; l.ambB = 0.38f
        l.setDirection(0.1f, 1f, 0.35f)
        l.dirR = 0.16f; l.dirG = 0.15f; l.dirB = 0.18f
        l.points.clear()
        pickLights(r, t)
        // At eye level the far end of the hall fades to black by the draw distance, so nothing
        // visibly pops in or out there; overhead keeps the old gentle haze.
        r.fogNear = 760f + (FP_FOG_NEAR - 760f) * fp
        r.fogFar = 1800f + (FP_DRAW_DISTANCE - 1800f) * fp
        r.fogFloor = 0.35f * (1f - fp)
        r.cullModels = true
        r.drawDistance = if (fp > 0f) view.far else 0f
        r.exposure = 1.25f
        r.bloom = 0.85f
        r.clear(0xFF07050E.toInt())

        structure.draw(r, Blend.OPAQUE)
        rig.model.draw(r, Blend.OPAQUE)
        rig.drawOpaque(r, t, rigMinX, rigMaxX, rigMinZ, rigMaxZ)
        prizeWall?.let { if (fixtureVisible(it.prop)) it.draw(r, save.collection) }

        for (i in fixtureProps.indices) {
            val p = fixtureProps[i]
            if (!fixtureVisible(p)) continue
            fixtureModels[i].draw(r, Blend.OPAQUE, emissiveBoost = fixtureBoost[i])
        }
        for (i in units.indices) {
            val u = units[i]
            val p = u.prop
            if (!unitVisible(p)) continue
            u.refresh(save.highScore(u.game.id), t)
            u.drawOpaque(r, t)
        }

        // The crowd. In first person you are the camera, so your own kid isn't drawn.
        val pl = world.player
        val showPlayer = fp < 0.85f
        if (showPlayer) pl.look?.let { figureFor(it).draw(r, pl.x, 0f, pl.y, pl.anim) }
        val npcs = world.npcs
        for (i in npcs.indices) {
            val n = npcs[i]
            if (!visible(n.x - 10f, n.y - 10f, n.x + 10f, n.y + 10f, FIGURE_HEIGHT)) continue
            figureFor(n.look).draw(r, n.x, 0f, n.y, n.anim)
        }
        val clerkVisible = visible(map.clerkX - 10f, map.clerkY - 10f, map.clerkX + 10f, map.clerkY + 10f, FIGURE_HEIGHT)
        if (clerkVisible) {
            figureFor(Looks.clerk).draw(r, map.clerkX, 0f, map.clerkY, world.clerk, 1.12f)
        }
        cafe.draw(r, world, t, minX, maxX, minZ, maxZ) // Café

        // ---- see-through layers, back to front where it matters.
        val logoZ = HubLayout.FRONT_WALL - 234f
        r.decal(304f - 62f, logoZ, 304f + 62f, logoZ + 124f, 0.05f, HallArt.floorLogo.full, blend = Blend.ALPHA)
        r.decal(0f, 0f, HubLayout.WIDTH.toFloat(), HubLayout.DEPTH.toFloat(), 0.08f, floorShade.full, blend = Blend.ALPHA)
        for (i in decorShades.indices) {
            val d = decorShades[i]
            if (visible(d.x0, d.z0, d.x1, d.z1, 1f)) r.decal(d.x0, d.z0, d.x1, d.z1, 0.08f, d.tex.full, blend = Blend.ALPHA)
        }
        for (i in units.indices) {
            val u = units[i]
            val p = u.prop
            if (!unitVisible(p)) continue
            val pulse = 0.28f + 0.06f * sin(t * 2f + p.centerX * 0.1f) + Highlight.POOL_ALPHA * Highlight.ease(u.highlight)
            r.decal(p.x0 - 12f, p.z1 - 4f, p.x1 + 12f, p.z1 + 40f, 0.15f, halo, Blend.ADD, emissive = 1f, alpha = pulse, tint = u.art.glow)
        }
        if (showPlayer) shadows.draw(r, pl.x, pl.y, 1f)
        val reach = FigureShadow.REACH
        for (i in npcs.indices) {
            val n = npcs[i]
            if (visible(n.x - reach, n.y - reach, n.x + reach, n.y + reach, 1f)) shadows.draw(r, n.x, n.y, 1f)
        }
        if (clerkVisible) shadows.draw(r, map.clerkX, map.clerkY, 1.1f)
        // First person's tap-to-walk: a pulsing glow where you're headed.
        val route = world.route
        if (route.active && fp > 0.5f) {
            val s = 1f + 0.12f * sin(t * 7f)
            r.flat(route.goalX, route.goalY, 0.35f, 26f * s, 26f * s, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.55f * fp, tint = 0xFFFFD84D.toInt())
            r.flat(route.goalX, route.goalY, 0.4f, 9f, 9f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.9f * fp, tint = -1)
        }
        for (i in fixtureProps.indices) {
            val p = fixtureProps[i]
            if (!fixtureVisible(p)) continue
            fixtureModels[i].draw(r, Blend.ALPHA, emissiveBoost = fixtureBoost[i])
        }
        for (i in units.indices) {
            val u = units[i]
            val p = u.prop
            if (!unitVisible(p)) continue
            u.drawTransparent(r)
            u.drawBulbs(r, t, bulb, halo)
        }
        structure.draw(r, Blend.ADD)
        for (i in fixtureProps.indices) {
            val p = fixtureProps[i]
            if (!fixtureVisible(p)) continue
            fixtureModels[i].draw(r, Blend.ADD, emissiveBoost = fixtureBoost[i])
        }
        rig.drawGlow(r, t, rigMinX, rigMaxX, rigMinZ, rigMaxZ)
        if (hasDisco) drawDiscoSpots(r, t)
    }

    private fun drawDiscoSpots(r: Renderer3D, t: Float) {
        val colors = DISCO_COLORS
        for (k in 0 until 14) {
            val a = t * 0.6f + k * 0.45f
            val d = 60f + hash01(k, 3) * 120f
            val x = map.discoX + cos(a) * d
            val z = map.discoY + sin(a * 1.1f) * d
            r.flat(x, z, 0.3f, 14f, 14f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = colors[k % colors.size])
        }
    }
}
