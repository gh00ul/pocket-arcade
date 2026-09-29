package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.HatStyle
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.MiniGame
import com.pocketarcade.startup.LoadDriver
import com.pocketarcade.startup.LoadPlan
import com.pocketarcade.startup.LoadStep
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.ceil

/**
 * One [MachineArt] per game, shared by everything that draws that game's cabinets (the hall and
 * the title showroom), so each game's printed artwork is painted once and uploaded once.
 */
object MachineArts {
    private val arts = IdentityHashMap<MiniGame, MachineArt>()
    private val painted: MutableSet<MiniGame> = Collections.newSetFromMap(IdentityHashMap())

    fun of(game: MiniGame): MachineArt = arts.getOrPut(game) { MachineArt(game) }

    /** Whether [paint] has run for [game]. */
    fun isPainted(game: MiniGame): Boolean = game in painted

    /** Paints the game's cabinet art, in two steps ([part] 0 and 1) so a loading plan can spread the work. */
    fun paint(game: MiniGame, part: Int) {
        val a = of(game)
        if (part == 0) {
            a.sideArt; a.topper; a.bodyPaint; a.darkPaint; a.black; a.trimTex; a.glowTex
        } else {
            a.marquee; a.kick; a.coinDoor; a.panel; a.bezel; a.display
            // The LED display's first paint builds its own painter and blur.
            a.updateDisplay(0, 0f)
            painted += game
        }
    }
}

/** The names the loading screen gives the hall's stages of work (drawn in capitals by the arcade font). */
internal object HallStages {
    const val FLOORS = "LAYING THE CARPET"
    const val WALLS = "HANGING THE WALLS"
    const val NEON = "LIGHTING THE NEON"
    const val BUILD = "BUILDING THE HALL"
    const val MACHINES = "ROLLING IN THE MACHINES"
    const val FURNITURE = "SETTING OUT THE FURNITURE"
    const val CROWD = "HIRING THE CROWD"
    const val PRIZES = "STOCKING THE PRIZE WALL"
}

/** A neon sign's texture request: what [HallArt.neon] is asked for, so loading and building agree. */
private class NeonSpec(val text: String, val color: Int, val w: Int, val h: Int, val size: Float) {
    fun texture(): Texture = HallArt.neon(text, color, w, h, size)
}

/**
 * The soft dark patch on the floor under a bought decoration, painted like the hall's floor
 * shade ([HallKit.buildFloorShade]) but on its own small texture, so a decoration added later
 * doesn't mean repainting the whole floor's.
 */
internal class DecorShade(val prop: Prop, val tex: Texture, val x0: Float, val z0: Float, val x1: Float, val z1: Float) {
    companion object {
        private const val S = 0.5f
        /** The blur reaches this far (world units) round the shape, so the patch is this much bigger each side. */
        private const val PAD = 24f

        fun build(p: Prop): DecorShade {
            val pw = ceil((p.x1 - p.x0 + 2f * PAD) * S).toInt()
            val ph = ceil((p.z1 - p.z0 + 2f * PAD) * S).toInt()
            val tp = TexPaint(pw, ph)
            tp.clear(0)
            tp.glow(10f, alpha(0xFF000000.toInt(), 0.55f)) {
                rect(PAD * S, PAD * S, (p.x1 - p.x0) * S, (p.z1 - p.z0) * S, -1)
            }
            val tex = tp.toTexture().also { tp.recycle() }
            val wx = pw / S
            val wz = ph / S
            val cx = p.centerX
            val cz = p.centerZ
            return DecorShade(p, tex, cx - wx / 2f, cz - wz / 2f, cx + wx / 2f, cz + wz / 2f)
        }
    }
}

/**
 * What changed between two floor plans that only differ in the bought decorations: which decor
 * props came and went, or null from [between] if anything else moved (a cabinet, a fixture),
 * which means the hall's scene can't just be adjusted. Pure floor-plan logic.
 */
internal class DecorDiff(val added: List<Prop>, val removed: List<Prop>) {
    companion object {
        /** Whether [a] and [b] are the same thing standing in the same place. */
        fun sameProp(a: Prop, b: Prop): Boolean =
            a.kind == b.kind && a.x0 == b.x0 && a.z0 == b.z0 && a.x1 == b.x1 && a.z1 == b.z1 &&
                a.height == b.height && a.machine == b.machine && a.decor == b.decor && a.solid == b.solid &&
                a.shape == b.shape && a.variant == b.variant

        fun between(old: HubMap, new: HubMap): DecorDiff? {
            val a = old.props.filter { it.kind != PropKind.DECOR }
            val b = new.props.filter { it.kind != PropKind.DECOR }
            if (a.size != b.size || old.clerkX != new.clerkX || old.clerkY != new.clerkY) return null
            for (i in a.indices) if (!sameProp(a[i], b[i])) return null
            val had = old.props.filter { it.kind == PropKind.DECOR }
            val has = new.props.filter { it.kind == PropKind.DECOR }
            val added = has.filter { n -> had.none { sameProp(it, n) } }
            val removed = had.filter { o -> has.none { sameProp(it, o) } }
            return DecorDiff(added, removed)
        }
    }

    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

/**
 * Everything a [HallScene] is made of, built one small step at a time. The hall has hundreds of
 * painted textures and models, and building them in one go froze the phone for seconds; [plan]
 * turns the same construction into [LoadStep]s that a [LoadDriver] runs a few milliseconds a frame
 * behind a loading screen. [buildAll] runs the same steps at once, for anything that just needs a
 * scene now (tests, a fallback if the plan never ran).
 *
 * Steps only ever add to the kit, in an order that keeps the scene identical to the one the old
 * all-at-once constructor made (the same lights in the same order, the cabinets back to front).
 * Runs on the UI thread: the painting code shares mutable state (fonts, paints, caches).
 */
internal class HallKit(val map: HubMap, val games: List<MiniGame>) {
    val arts: List<MachineArt> = games.map { MachineArts.of(it) }

    /** Every cabinet, back to front: the order the see-through pass needs, fixed for the map's life. */
    private val unitProps: List<Prop> = map.props.filter { it.kind == PropKind.MACHINE }.sortedBy { it.z0 }
    val units = ArrayList<MachineUnit>()

    /** The fixtures (everything on the floor that isn't a cabinet), their models and the lights each gives off. */
    val fixtureProps = ArrayList<Prop>()
    val fixtureModels = ArrayList<Model>()
    val fixtureLights = ArrayList<List<PointLight>>()

    /** Ceiling downlights in a grid and neon along the walls. */
    val hallLights = ArrayList<PointLight>()

    /** What [buildStructure] adds: the street lamps. */
    val structureLights = ArrayList<PointLight>()
    lateinit var structure: Model
    lateinit var floorShade: Texture
    lateinit var rig: HallRig
    lateinit var cafe: CafeScene
    var prizeWall: PrizeWallDisplay? = null
    val figures = HashMap<CharacterLook, Figure>()

    /** The floor under each bought decoration that stands in the hall now. */
    val decorShades = ArrayList<DecorShade>()

    init {
        // Ceiling downlights in a grid.
        var z = 90f
        while (z < HubLayout.FRONT_WALL) {
            var x = 76f
            while (x < HubLayout.WIDTH) {
                hallLights += PointLight(x, 150f, z, 1f, 0.9f, 0.78f, 175f, 0.55f)
                x += 120f
            }
            z += 120f
        }
        // Neon along the walls.
        hallLights += PointLight(92f, 110f, 40f, 0.3f, 0.9f, 1f, 130f, 0.7f)
        hallLights += PointLight(534f, 110f, 40f, 0.7f, 0.4f, 1f, 130f, 0.7f)
    }

    /** Builds the whole hall now. A step that fails is not carried on past here: its error is the scene's. */
    fun buildAll() {
        val driver = LoadDriver(plan(emptyList()))
        driver.runToEnd()
        driver.failures.firstOrNull()?.let { throw it }
    }

    /**
     * The steps that build the kit. [looks] are the kids to make figures for ahead of time (the
     * crowd, the player); the clerk and the barista are always made.
     */
    fun plan(looks: List<CharacterLook>): LoadPlan {
        val s = ArrayList<LoadStep>()
        fun work(name: String, weight: Float, run: () -> Unit) {
            s += LoadStep.Work(name, weight, run)
        }

        // Floors and the plain materials every model reaches for.
        repeat(HallArt.CARPET_STAGES) { work(HallStages.FLOORS, 1.4f) { HallArt.paintCarpetStage() } }
        work(HallStages.FLOORS, 2f) { HallArt.tiles; HallArt.concrete; HallArt.asphalt; HallArt.mat }
        work(HallStages.FLOORS, 2f) { HallArt.floorLogo }
        work(HallStages.FLOORS, 2f) {
            HallArt.brushedMetal; HallArt.darkMetal; HallArt.chrome; HallArt.glass
            HallArt.shadow; HallArt.glow; HallArt.beam; HallArt.shaft; HallArt.ticketStack
        }

        // Walls, murals, posters and the street seen through the shopfront.
        work(HallStages.WALLS, 1f) { HallArt.wall; HallArt.upperWall }
        work(HallStages.WALLS, 2f) { HallArt.mural }
        work(HallStages.WALLS, 2f) { HallArt.muralCity }
        work(HallStages.WALLS, 2f) { HallArt.muralSpace; HallArt.raceSign }
        work(HallStages.WALLS, 2f) { HallArt.ceilingTiles; HallArt.troffer; HallArt.duct; HallArt.washer }
        work(HallStages.WALLS, 4f) { HallArt.streetBackdrop }
        work(HallStages.WALLS, 2f) { for (k in 0 until 4) HallArt.poster(k) }

        // Neon signs: each a blurred glow.
        for (spec in neonSigns()) work(HallStages.NEON, 2f) { spec.texture() }

        // The shell, the floor's shading and the rig over it.
        work(HallStages.BUILD, 3f) { structure = buildStructure() }
        work(HallStages.BUILD, 3f) { floorShade = buildFloorShade() }
        work(HallStages.BUILD, 4f) { rig = HallRig(map, games) }

        // Cabinets: each game's printed art first (a few big textures), then a model and a
        // first picture on the screen of every copy.
        for (g in games) {
            if (MachineArts.isPainted(g)) continue
            work(HallStages.MACHINES, 3f) { MachineArts.paint(g, 0) }
            work(HallStages.MACHINES, 3f) { MachineArts.paint(g, 1) }
        }
        for (p in unitProps) {
            work(HallStages.MACHINES, 2f) {
                val u = MachineUnit(p, games[p.machine], arts[p.machine])
                u.screen?.warm(0)
                units += u
            }
        }

        // Fixtures, in map order, a few small ones to a step.
        var pending = ArrayList<Prop>()
        var pendingCost = 0f
        fun flush() {
            if (pending.isEmpty()) return
            val batch = pending
            work(HallStages.FURNITURE, maxOf(1f, pendingCost)) { for (p in batch) buildFixture(p) }
            pending = ArrayList()
            pendingCost = 0f
        }
        for (p in map.props) {
            if (p.kind == PropKind.MACHINE) continue
            pending += p
            pendingCost += fixtureCost(p)
            if (pendingCost >= BATCH_COST) flush()
        }
        flush()
        work(HallStages.FURNITURE, 1f) { rebuildDecorShades() }

        // The crowd, their hats, the café's staff and what's on the prize wall.
        val distinct = LinkedHashSet<CharacterLook>().apply {
            add(Looks.clerk)
            addAll(looks)
        }
        for (look in distinct) work(HallStages.CROWD, 2f) { figures.getOrPut(look) { Figure(look) } }
        work(HallStages.CROWD, 2f) { for (h in HatStyle.entries) Figure.hat(h) }
        work(HallStages.CROWD, 3f) { cafe = CafeScene() }
        work(HallStages.PRIZES, 3f) {
            prizeWall = map.props.firstOrNull { it.kind == PropKind.PRIZE_WALL }?.let { PrizeWallDisplay(it) }
        }
        return LoadPlan(s)
    }

    /** Builds [p]'s model and lights and adds them to the fixtures. */
    fun buildFixture(p: Prop) {
        val ls = ArrayList<PointLight>()
        val m = Props.build(p, ls)
        fixtureProps += p
        fixtureModels += m
        fixtureLights += ls
    }

    /** (Re)makes the floor patches under the decorations in the map (all but the disco ball, which hangs above the floor). */
    fun rebuildDecorShades() {
        decorShades.clear()
        for (p in map.props) if (p.kind == PropKind.DECOR && p.decor != DecorStyle.DISCO_BALL) decorShades += DecorShade.build(p)
    }

    /** What each fixture is expected to cost to build, in loading-plan weight. */
    private fun fixtureCost(p: Prop): Float = when (p.kind) {
        PropKind.PRIZE_WALL -> 4f
        PropKind.COUNTER, PropKind.PHOTO_BOOTH -> 3f
        PropKind.TOKENS, PropKind.CHANGE, PropKind.VENDING, PropKind.KIDDIE_RIDE, PropKind.DECOR -> 2f
        PropKind.CAFE_FLOOR, PropKind.CAFE_BAR, PropKind.CAFE_COUNTER, PropKind.BOOTH -> 2f
        PropKind.PILLAR, PropKind.PLANT, PropKind.BENCH, PropKind.DOORS -> 1f
        PropKind.CAFE_TABLE, PropKind.STOOL, PropKind.TRASH, PropKind.CHAIR -> 0.3f
        PropKind.MACHINE -> 0f
    }

    // ------------------------------------------------------------------ static structure

    private fun neonSigns(): List<NeonSpec> {
        val out = ArrayList<NeonSpec>()
        out += NeonSpec("POCKET ARCADE", 0xFF39E6F2.toInt(), 768, 160, 96f)
        out += NeonSpec("HIGH SCORE", 0xFFB080FF.toInt(), 640, 160, 96f)
        for (sg in HubLayout.wallSigns) {
            if (sg.shape != null && games.none { it.look.shape == sg.shape }) continue
            out += NeonSpec(sg.text, sg.color, sg.texW, 160, sg.size)
        }
        return out
    }

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
        fun floor(x0: Float, z0: Float, x1: Float, z1: Float, tex: Region, tpu: Float, gloss: Float) {
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
            structureLights += PointLight(sx, 100f, out + 62f, 1f, 0.82f, 0.6f, 150f, 0.9f)
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
        // Posters on the side walls, clear of the zone signs and the tall banks against them.
        // Wall art runs its first corner to its second left to right as seen from the hall.
        for ((k, pz) in HubLayout.leftPosters.withIndex()) {
            val tex = HallArt.poster(k).full
            b.quad(wl + 0.4f, 100f, pz + 12f, wl + 0.4f, 100f, pz - 12f, wl + 0.4f, 64f, pz - 12f, wl + 0.4f, 64f, pz + 12f, tex, 1f, 0f, 0f, gloss = 0.5f)
        }
        for ((k, pz) in HubLayout.rightPosters.withIndex()) {
            val tex = HallArt.poster(k + 1).full
            b.quad(w - wl - 0.4f, 100f, pz - 12f, w - wl - 0.4f, 100f, pz + 12f, w - wl - 0.4f, 64f, pz + 12f, w - wl - 0.4f, 64f, pz - 12f, tex, -1f, 0f, 0f, gloss = 0.5f)
        }
        // Big neon signs on the back wall.
        val signs = neonSigns()
        b.quad(22f, 132f, back + 0.5f, 164f, 132f, back + 0.5f, 164f, 96f, back + 0.5f, 22f, 96f, back + 0.5f, signs[0].texture().full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        b.quad(470f, 132f, back + 0.5f, 590f, 132f, back + 0.5f, 590f, 100f, back + 0.5f, 470f, 100f, back + 0.5f, signs[1].texture().full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.7f, cull = false)
        // Zone signs on the side walls, from the floor plan: each over its bank and the cross
        // aisle in front of it. A machine's sign is only up while that machine is on the floor.
        for (sg in HubLayout.wallSigns) {
            if (sg.shape != null && games.none { it.look.shape == sg.shape }) continue
            val tex = HallArt.neon(sg.text, sg.color, sg.texW, 160, sg.size).full
            if (sg.right) {
                val x = w - wl - 0.6f
                b.quad(x, sg.y1, sg.z0, x, sg.y1, sg.z1, x, sg.y0, sg.z1, x, sg.y0, sg.z0, tex, -1f, 0f, 0f, blend = Blend.ADD, emissive = 1.7f, cull = false)
            } else {
                val x = wl + 0.6f
                b.quad(x, sg.y1, sg.z1, x, sg.y1, sg.z0, x, sg.y0, sg.z0, x, sg.y0, sg.z1, tex, 1f, 0f, 0f, blend = Blend.ADD, emissive = 1.7f, cull = false)
            }
        }
        // Baseboards.
        val base = HallArt.darkMetal.full
        b.box(wl, 0f, back, w - wl, 4f, back + 1f, BoxFaces(front = base, top = base))
        b.box(wl, 0f, back, wl + 1f, 4f, front, BoxFaces(right = base, top = base))
        b.box(w - wl - 1f, 0f, back, w - wl, 4f, front, BoxFaces(left = base, top = base))
        return b.build()
    }

    /** Soft darkening of the floor under and around everything that stands on it (bought decorations have their own, see [DecorShade]). */
    private fun buildFloorShade(): Texture {
        val s = 0.5f
        val w = (HubLayout.WIDTH * s).toInt()
        val h = (HubLayout.DEPTH * s).toInt()
        val tp = TexPaint(w, h)
        tp.clear(0)
        tp.glow(10f, alpha(0xFF000000.toInt(), 0.55f)) {
            for (p in map.props) {
                if (p.kind == PropKind.DECOR || p.kind == PropKind.DOORS) continue
                rect(p.x0 * s, p.z0 * s, (p.x1 - p.x0) * s, (p.z1 - p.z0) * s, -1)
            }
            rect(0f, 0f, w.toFloat(), HubLayout.BACK_WALL * s + 4f, -1)
            rect(0f, 0f, HubLayout.WALL * s + 4f, h.toFloat(), -1)
            rect(w - HubLayout.WALL * s - 4f, 0f, HubLayout.WALL * s + 4f, h.toFloat(), -1)
        }
        return tp.toTexture().also { tp.recycle() }
    }

    private companion object {
        /** A fixtures step is filled up to about this much expected work. */
        const val BATCH_COST = 3f
    }
}
