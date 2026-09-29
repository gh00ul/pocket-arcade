package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame

/** What a hall object is, for building its model. */
enum class PropKind {
    MACHINE, COUNTER, PRIZE_WALL, TOKENS, CHANGE, VENDING, CAFE_TABLE, STOOL, PILLAR, PLANT, TRASH,
    PHOTO_BOOTH, DECOR, BENCH, DOORS, KIDDIE_RIDE,
    /** The café: its floor and lighting rig, back bar, service counter, booths and chairs. */
    CAFE_FLOOR, CAFE_BAR, CAFE_COUNTER, BOOTH, CHAIR,
}

/**
 * An object on the hall floor. x runs across the hall, z from the back wall (0) towards the
 * entrance. [height] is its top; machines face +z (towards the entrance and the camera).
 */
class Prop(
    val kind: PropKind,
    val x0: Float,
    val z0: Float,
    val x1: Float,
    val z1: Float,
    val height: Float,
    val machine: Int = -1,
    val decor: DecorStyle? = null,
    val solid: Boolean = true,
    val shape: CabinetShape = CabinetShape.UPRIGHT,
    val variant: Int = 0,
) {
    val foot: Box? = if (solid) Box(x0, z0, x1, z1) else null
    val centerX: Float get() = (x0 + x1) / 2f
    val centerZ: Float get() = (z0 + z1) / 2f
    val frontZ: Float get() = z1
}

/**
 * What a spot is for. The machines, the token kiosk and the prize counter are placed by hand;
 * every other type belongs to an interactive prop and is generated for it ([HubLayout.spotTypeOf],
 * [HubLayout.propSpots]). The prompt's words are in `HubRenderer.drawPrompt`, what tapping it
 * does is in `ArcadeApp.onSpot`.
 */
enum class SpotType {
    MACHINE,

    TOKENS,

    PRIZES,

    /** The photo booth. */
    PHOTO,

    /** The trophy case (a bought decoration). */
    TROPHY,

    /** The fish tank (a bought decoration). */
    TANK,

    /** The café's service counter, at the till. */
    CAFE,

    /** A kiddie ride ([Spot.prop]'s variant says which). */
    RIDE,

    /** The jukebox (a bought decoration). */
    JUKEBOX,

    /** A vending machine ([Spot.prop]'s variant says which). */
    VENDING,
}

/**
 * A place the player can stand to use something. [area] is where they must be; the prompt
 * floats at the anchor, and the enter transition flies the camera to the focus point. A spot
 * generated for an interactive prop carries that [prop], so a handler can tell which of several
 * (vending machine, ride) was tapped.
 */
class Spot(
    val type: SpotType,
    val machine: Int,
    val area: Box,
    val anchorX: Float,
    val anchorHeight: Float,
    val anchorZ: Float,
    val focusX: Float,
    val focusY: Float,
    val focusZ: Float,
    val prop: Prop? = null,
)

/**
 * Where a wandering kid can stop: position, which way they face (radians) and whether it's a
 * machine. Kids path to the walk-grid tile ([tileX], [tileY]) nearest the spot, then step onto it.
 * A [cafe] seat is where kids sit with what they bought; the tile is the one nearest the
 * approach point ([approachX], [approachZ]), so kids get into a booth from the aisle end.
 */
class Hangout(
    val x: Float,
    val z: Float,
    val yaw: Float,
    val playing: Boolean,
    val tileX: Int = (x / HubLayout.TILE).toInt(),
    val tileY: Int = (z / HubLayout.TILE).toInt(),
    val cafe: Boolean = false,
    val approachX: Float = x,
    val approachZ: Float = z,
)

class HubMap(
    val widthPx: Int,
    val heightPx: Int,
    val cols: Int,
    val rows: Int,
    val props: List<Prop>,
    val solids: List<Box>,
    val spots: List<Spot>,
    val walkable: BooleanArray,
    val spawnX: Float,
    val spawnY: Float,
    val clerkX: Float,
    val clerkY: Float,
    val hangouts: List<Hangout>,
    val discoX: Float,
    val discoY: Float,
    /** The bank each machine was given, by game index. */
    val machineSlots: List<Slot>,
    /** The café queue, from the till backwards. */
    val cafeQueue: List<Hangout> = emptyList(),
) {
    fun tileWalkable(tx: Int, ty: Int): Boolean =
        tx in 0 until cols && ty in 0 until rows && walkable[ty * cols + tx]
}

/**
 * A pre-sized stretch of floor for one machine's bank: room for [count] cabinets of at most
 * [maxW] wide, [maxD] deep and [maxH] tall, [gap] apart, from [x0] with their backs at [back].
 * That floor and the play spots in front of it are kept clear of everything else, so any
 * cabinet that fits can't overlap anything. The cabinets stand [gap] apart whatever their size,
 * packed against the [anchor] side (-1 the left end, 1 the right end, 0 centred), so a slot by
 * a wall keeps its cabinets against the wall. A slot with a [shape] is that shape's bank; a
 * spare slot (no shape) takes the first machine that has no bank of its own.
 */
class Slot(
    val shape: CabinetShape?,
    val count: Int,
    val x0: Float,
    val back: Float,
    val gap: Float,
    val maxW: Float,
    val maxD: Float,
    val maxH: Float,
    val anchor: Int = 0,
) {
    val x1: Float get() = x0 + count * maxW + (count - 1) * gap

    /** The floor the slot reserves: every cabinet at its largest plus the play spots in front. */
    val area: Box get() = Box(x0, back, x1, back + maxD + HubLayout.PROMPT_DEPTH)

    /** Left edge of cabinet [k] when each is [w] wide. */
    fun cabinetX(k: Int, w: Float): Float {
        val span = count * w + (count - 1) * gap
        val start = when {
            anchor < 0 -> x0
            anchor > 0 -> x1 - span
            else -> (x0 + x1 - span) / 2f
        }
        return start + k * (w + gap)
    }
}

/**
 * A neon zone sign on a side wall ([right] or left): its words and colour, and the stretch of
 * wall it spans (z0..z1 along the wall, y0..y1 up it). Each one straddles its bank's front and
 * the cross aisle in front of it, so it reads from the hall camera over the bank and, at eye
 * level, from the main aisle down the cross aisle. A sign with a [shape] is only up while a
 * machine of that shape is on the floor.
 */
class WallSign(
    val text: String,
    val color: Int,
    val right: Boolean,
    val z0: Float,
    val z1: Float,
    val y0: Float = 100f,
    val y1: Float = 124f,
    /** The neon texture's width in pixels and its lettering size. */
    val texW: Int = 640,
    val size: Float = 100f,
    val shape: CabinetShape? = null,
)

/**
 * The arcade's floor plan. A wide main aisle runs straight from the doors to the prize counter,
 * with zones either side of it in rows across the hall, each row's banks facing the doors across
 * a cross aisle: the prize games along the back wall either side of the counter, the ticket
 * alleys and coin pushers, the table games, the video games, then the family floor by the
 * entrance with the fishing tubs, the café in the front-left corner, kiddie rides, the photo
 * booth and the token kiosk just inside the doors. The spare banks sit where the next machines
 * would go: beside the pinball tables and at the front of the family floor.
 */
object HubLayout {
    const val TILE = 16
    const val WIDTH = 608
    const val DEPTH = 1100
    const val WALL = 16f
    const val BACK_WALL = 24f
    const val FRONT_WALL = 1080f
    const val WALL_HEIGHT = 150f
    /** The walls carry on up into the dark to here. */
    const val CEILING = 380f
    const val DOOR_X0 = 264f
    const val DOOR_X1 = 344f
    /** How far in front of a cabinet its play spot reaches. */
    const val PROMPT_DEPTH = 26f
    /**
     * An interactive prop's spot is never narrower than this (half width), and never wider than
     * [MAX_STAND_HALF], however wide the prop: a long counter gets a spot the size of a till.
     */
    const val MIN_STAND_HALF = 12f
    const val MAX_STAND_HALF = 22f
    /**
     * The least depth a prop's spot may be cut down to by something standing in front of it:
     * enough for the body to stop at [HubWorld.STAND_DEPTH] and still be inside it.
     */
    const val MIN_STAND_DEPTH = 22f
    /**
     * The main aisle: nothing stands between these x from the doors to the prize counter, and
     * the play spots in front of the banks either side keep to their own side of it.
     */
    const val AISLE_X0 = 256f
    const val AISLE_X1 = 352f

    /** Width, depth and height of each cabinet. */
    fun cabinetSize(shape: CabinetShape): Triple<Float, Float, Float> = when (shape) {
        CabinetShape.UPRIGHT -> Triple(24f, 28f, 58f)
        CabinetShape.WIDE -> Triple(36f, 34f, 62f)
        CabinetShape.LANE -> Triple(28f, 92f, 58f)
        CabinetShape.TABLE -> Triple(36f, 64f, 58f)
        CabinetShape.CLAW -> Triple(30f, 30f, 68f)
        CabinetShape.WHACK -> Triple(34f, 32f, 62f)
        CabinetShape.SKEEBALL -> Triple(26f, 100f, 60f)
        CabinetShape.HOOPS -> Triple(36f, 90f, 82f)
        CabinetShape.PUSHER -> Triple(40f, 38f, 66f)
        CabinetShape.AIR_HOCKEY -> Triple(36f, 66f, 62f)
        CabinetShape.RACER -> Triple(32f, 50f, 62f)
        CabinetShape.TOWER -> Triple(28f, 26f, 76f)
        // These machines bring their own design; the sizes are for the stand-in cabinet.
        CabinetShape.GUN -> Triple(40f, 36f, 80f)
        CabinetShape.PINBALL -> Triple(28f, 54f, 70f)
        CabinetShape.FISHING -> Triple(60f, 60f, 56f)
    }

    /** Width, depth and height of [game]'s cabinet: its own design's, else its shape's. */
    fun cabinetSize(game: MiniGame): Triple<Float, Float, Float> =
        game.cabinet?.let { Triple(it.width, it.depth, it.height) } ?: cabinetSize(game.look.shape)

    /** The camera dive point for [game]'s cabinet: its own design's, else its shape's. */
    fun focus(game: MiniGame): Pair<Float, Float> =
        game.cabinet?.let { it.focusHeight to it.focusSetBack } ?: focus(game.look.shape)

    /** Where the camera flies to when entering: height and how far behind the cabinet front. */
    fun focus(shape: CabinetShape): Pair<Float, Float> {
        val (_, d, _) = cabinetSize(shape)
        return when (shape) {
            CabinetShape.CLAW -> 38f to 3f
            CabinetShape.TOWER -> 48f to 4f
            CabinetShape.UPRIGHT, CabinetShape.GUN -> 40f to 6f
            CabinetShape.WIDE -> 38f to 4f
            CabinetShape.PUSHER -> 36f to 6f
            CabinetShape.WHACK -> 44f to d - 4f
            CabinetShape.RACER -> 42f to d - 10f
            CabinetShape.SKEEBALL -> 36f to d - 10f
            CabinetShape.HOOPS -> 56f to d - 10f
            CabinetShape.LANE -> 34f to d - 10f
            CabinetShape.AIR_HOCKEY, CabinetShape.TABLE, CabinetShape.PINBALL, CabinetShape.FISHING -> 46f to d - 2f
        }
    }

    /** A bank whose cells fit [shape]'s cabinet exactly. */
    private fun bank(shape: CabinetShape, count: Int, x0: Float, back: Float, gap: Float): Slot {
        val (w, d, h) = cabinetSize(shape)
        return Slot(shape, count, x0, back, gap, w, d, h)
    }

    /**
     * Every bank on the floor, row by row from the back wall, left of the main aisle then right.
     * Rows leave a cross aisle of at least 80 between one row's fronts and the next row's backs,
     * and nothing tall stands close enough in front of a row to hide its players from the hall
     * camera. The spare banks are pre-sized for machines still to come: a cabinet must fit its
     * cell, or the build fails rather than overlap its neighbours. A new machine takes the first
     * spare in this list first.
     */
    val slots: List<Slot> = listOf(
        // Prize games along the back wall, either side of the prize counter.
        bank(CabinetShape.CLAW, 4, 26f, 34f, 4f),
        bank(CabinetShape.TOWER, 3, 488f, 34f, 4f),
        // Ticket alleys: skee-ball lanes along the left wall with the basketball alleys beside
        // them, fronts in line; the coin pushers across the aisle against the right wall.
        bank(CabinetShape.SKEEBALL, 4, 24f, 150f, 2f),
        bank(CabinetShape.HOOPS, 3, 140f, 160f, 2f),
        Slot(CabinetShape.PUSHER, 4, 412f, 150f, 4f, maxW = 40f, maxD = 38f, maxH = 66f, anchor = 1),
        // Table games: whack-a-moles by the left wall, the air hockey tables by the aisle; the
        // pinball tables against the right wall with a spare pair of cells beside them.
        Slot(CabinetShape.WHACK, 3, 24f, 374f, 6f, maxW = 34f, maxD = 32f, maxH = 62f, anchor = -1),
        bank(CabinetShape.AIR_HOCKEY, 2, 146f, 340f, 30f),
        Slot(CabinetShape.PINBALL, 4, 452f, 346f, 4f, maxW = 30f, maxD = 60f, maxH = 78f, anchor = 1),
        // Video games, fronts in line across the aisle: the linked racers (their own cabinet
        // design, so the cells leave it room to grow) and the light-gun cabinets.
        Slot(CabinetShape.RACER, 4, 24f, 516f, 2f, maxW = 34f, maxD = 58f, maxH = 72f, anchor = -1),
        Slot(CabinetShape.GUN, 3, 440f, 534f, 6f, maxW = 44f, maxD = 40f, maxH = 86f, anchor = 1),
        // The family floor: two big fishing tubs by the café, a spare bank across the aisle.
        Slot(CabinetShape.FISHING, 2, 24f, 670f, 24f, maxW = 64f, maxD = 64f, maxH = 64f, anchor = -1),
        Slot(null, 3, 452f, 674f, 6f, maxW = 40f, maxD = 60f, maxH = 86f, anchor = 1),
        Slot(null, 2, 360f, 346f, 6f, maxW = 40f, maxD = 60f, maxH = 86f, anchor = -1),
    )

    /** Spots for bought decorations: centre x, front z. */
    private val decorSpots = mapOf(
        // Back wall, either side of the prize counter.
        DecorStyle.TROPHY_CASE to (182f to 42f),
        DecorStyle.PLUSH_BEAR to (442f to 92f),
        // In the café, against the wall between the booths and the snack machine. Its front is
        // clear of the lava lamp's corner, so there's room to stand and pick a song.
        DecorStyle.JUKEBOX to (34f to 1030f),
        // At the right-wall end of the cross aisle between the table and video games.
        DecorStyle.FISH_TANK to (562f to 486f),
        // In the café's front corner, by the window.
        DecorStyle.LAVA_LAMP to (26f to 1070f),
        // Round the entrance: a palm left of the doors, the flamingo by the front-right plant,
        // the gumball machine beside the change machine.
        DecorStyle.PALM to (234f to 1070f),
        DecorStyle.FLAMINGO to (556f to 1070f),
        DecorStyle.GUMBALL to (450f to 1014f),
    )

    /** Pillars (centres), in pairs flanking the main aisle through the middle of the hall. */
    private val pillars = floatArrayOf(238f, 545f, 370f, 545f, 238f, 700f, 370f, 700f)

    /** Standing spots in the aisles: the prize counter, the cross aisles, the entrance. */
    private val aisleSpots = floatArrayOf(
        304f, 150f, 200f, 305f, 440f, 280f, 304f, 470f, 190f, 470f, 440f, 470f,
        200f, 632f, 420f, 632f, 304f, 780f, 430f, 930f, 330f, 960f,
    )

    /** Neon zone signs on the side walls. */
    val wallSigns: List<WallSign> = listOf(
        WallSign("SKEE-BALL", 0xFFFFD84D.toInt(), right = false, z0 = 200f, z1 = 300f),
        WallSign("JACKPOT", 0xFFFFB03D.toInt(), right = true, z0 = 150f, z1 = 250f, texW = 512, size = 104f),
        WallSign("PINBALL", 0xFFFF77C8.toInt(), right = true, z0 = 360f, z1 = 450f, texW = 512, shape = CabinetShape.PINBALL),
        WallSign("FISHING", 0xFF4DA6FF.toInt(), right = false, z0 = 690f, z1 = 790f, texW = 512, shape = CabinetShape.FISHING),
        WallSign("SNACK BAR", 0xFF5CF08A.toInt(), right = false, z0 = CafeLayout.FLOOR_Z0 + 86f, z1 = CafeLayout.FLOOR_Z0 + 206f, y0 = 92f, y1 = 116f),
    )

    /** Where the posters hang along the left and right walls (centre z), clear of the signs and the tall banks. */
    val leftPosters = floatArrayOf(320f, 470f, 625f, 1030f)
    val rightPosters = floatArrayOf(290f, 480f, 630f, 800f, 900f)

    fun build(games: List<MiniGame>, ownedDecor: Set<DecorStyle>): HubMap {
        val w = WIDTH
        val h = DEPTH
        val props = ArrayList<Prop>()
        val spots = ArrayList<Spot>()
        val solids = ArrayList<Box>()
        val hangouts = ArrayList<Hangout>()

        // Walls, with the entrance gap in the front wall.
        solids += Box(0f, 0f, w.toFloat(), BACK_WALL)
        solids += Box(0f, 0f, WALL, h.toFloat())
        solids += Box(w - WALL, 0f, w.toFloat(), h.toFloat())
        solids += Box(0f, FRONT_WALL, DOOR_X0, h.toFloat())
        solids += Box(DOOR_X1, FRONT_WALL, w.toFloat(), h.toFloat())
        solids += Box(DOOR_X0, FRONT_WALL + 12f, DOOR_X1, h.toFloat())

        // Prize counter with the prize wall behind it and room for the clerk.
        props += Prop(PropKind.PRIZE_WALL, 200f, BACK_WALL, 408f, 40f, 104f)
        props += Prop(PropKind.COUNTER, 224f, 70f, 384f, 94f, 26f)
        solids += Box(200f, 40f, 224f, 94f)
        solids += Box(384f, 40f, 408f, 94f)
        spots += Spot(SpotType.PRIZES, -1, Box(262f, 94f, 346f, 124f), 304f, 52f, 90f, 304f, 34f, 84f)

        // Machines, bank by bank. A game whose shape has no bank (or whose bank another game
        // already took) gets the next spare slot; when none is left the build fails loudly.
        val used = BooleanArray(slots.size)
        val machineSlots = ArrayList<Slot>()
        for ((index, g) in games.withIndex()) {
            val shape = g.look.shape
            val si = slots.indices.firstOrNull { !used[it] && slots[it].shape == shape }
                ?: slots.indices.firstOrNull { !used[it] && slots[it].shape == null }
                ?: error("No room on the hall floor for '${g.id}': every bank and spare slot is taken. Add a Slot to HubLayout.slots.")
            used[si] = true
            val slot = slots[si]
            machineSlots += slot
            val (cw, cd, ch) = cabinetSize(g)
            check(cw <= slot.maxW && cd <= slot.maxD && ch <= slot.maxH) {
                "'${g.id}' cabinet $cw x $cd x $ch doesn't fit its slot's ${slot.maxW} x ${slot.maxD} x ${slot.maxH} cells"
            }
            for (k in 0 until slot.count) {
                val x0 = slot.cabinetX(k, cw)
                addMachine(props, spots, hangouts, index, shape, x0, slot.back, cw, cd, ch, focus(g), k)
            }
        }

        // Pillars flanking the main aisle.
        for (i in 0 until pillars.size / 2) {
            val px = pillars[i * 2]
            val pz = pillars[i * 2 + 1]
            props += Prop(PropKind.PILLAR, px - 9f, pz - 9f, px + 9f, pz + 9f, WALL_HEIGHT)
        }

        // The café in the front-left corner, open to the main aisle: counter, booths, tables,
        // vending machines.
        val cafeQueue = ArrayList<Hangout>()
        CafeLayout.add(props, hangouts, cafeQueue)

        // The family floor right of the doors: kiddie rides with a bench beside them for the
        // grown-ups, and the photo booth against the wall.
        props += Prop(PropKind.KIDDIE_RIDE, 452f, 830f, 480f, 862f, 46f, variant = 0)
        props += Prop(PropKind.KIDDIE_RIDE, 506f, 830f, 534f, 862f, 30f, variant = 1)
        props += Prop(PropKind.BENCH, 470f, 912f, 540f, 926f, 16f)
        props += Prop(PropKind.PHOTO_BOOTH, 534f, 960f, 584f, 1010f, 78f)

        // Token kiosk and change machine just inside the doors, on the right as you come in.
        props += Prop(PropKind.TOKENS, 372f, 990f, 406f, 1014f, 60f)
        props += Prop(PropKind.CHANGE, 412f, 992f, 440f, 1014f, 56f)
        spots += Spot(SpotType.TOKENS, -1, Box(366f, 1014f, 412f, 1042f), 389f, 70f, 1010f, 389f, 40f, 1016f)
        // Bins either side of the doors; the café keeps the front-left corner, a plant the right.
        props += Prop(PropKind.TRASH, AISLE_X0 - 12f, FRONT_WALL - 22f, AISLE_X0, FRONT_WALL - 10f, 18f)
        props += Prop(PropKind.TRASH, AISLE_X1, FRONT_WALL - 22f, AISLE_X1 + 12f, FRONT_WALL - 10f, 18f)
        props += Prop(PropKind.PLANT, 568f, FRONT_WALL - 22f, 586f, FRONT_WALL - 4f, 40f)
        props += Prop(PropKind.DOORS, DOOR_X0, FRONT_WALL, DOOR_X1, FRONT_WALL + 12f, 80f, solid = false)

        // Bought decorations.
        for ((style, pos) in decorSpots) {
            if (style !in ownedDecor) continue
            val (cx, front) = pos
            val (dw, dd, dh) = decorSize(style)
            props += Prop(PropKind.DECOR, cx - dw / 2f, front - dd, cx + dw / 2f, front, dh, decor = style)
        }
        val discoX = 304f
        val discoY = 470f
        if (DecorStyle.DISCO_BALL in ownedDecor) {
            props += Prop(PropKind.DECOR, discoX - 8f, discoY - 8f, discoX + 8f, discoY + 8f, 120f, decor = DecorStyle.DISCO_BALL, solid = false)
        }

        for (p in props) p.foot?.let { solids += it }

        // A spot in front of every interactive prop, now that all the solids are known.
        spots += propSpots(props, solids)

        // Walkable tile grid for the kids' path finding.
        val cols = w / TILE
        val rows = h / TILE
        val walkable = BooleanArray(cols * rows)
        for (ty in 0 until rows) for (tx in 0 until cols) {
            val cx = tx * TILE + TILE / 2f
            val cy = ty * TILE + TILE / 2f
            walkable[ty * cols + tx] = solids.none { it.intersects(cx - 6f, cy - 4f, cx + 6f, cy + 6f) }
        }
        // Standing spots in the aisles.
        for (i in 0 until aisleSpots.size / 2) hangouts += Hangout(aisleSpots[i * 2], aisleSpots[i * 2 + 1], 0f, playing = false)
        // A seat or a play spot can sit closer to its table or cabinet than a walkable tile's
        // centre, so kids aim for the free tile nearest its approach and walk the last step.
        fun snapToTiles(list: MutableList<Hangout>) {
            for (i in list.indices) {
                val hg = list[i]
                var best = -1
                var bestD = Float.MAX_VALUE
                val tx0 = (hg.approachX / TILE).toInt()
                val ty0 = (hg.approachZ / TILE).toInt()
                for (ty in ty0 - 2..ty0 + 2) for (tx in tx0 - 2..tx0 + 2) {
                    if (tx !in 0 until cols || ty !in 0 until rows || !walkable[ty * cols + tx]) continue
                    val d = kotlin.math.hypot(tx * TILE + TILE / 2f - hg.approachX, ty * TILE + TILE / 2f - hg.approachZ)
                    if (d < bestD) {
                        bestD = d
                        best = ty * cols + tx
                    }
                }
                if (best >= 0) list[i] = Hangout(hg.x, hg.z, hg.yaw, hg.playing, best % cols, best / cols, hg.cafe, hg.approachX, hg.approachZ)
            }
        }
        snapToTiles(hangouts)
        snapToTiles(cafeQueue)

        return HubMap(
            widthPx = w, heightPx = h, cols = cols, rows = rows,
            props = props, solids = solids, spots = spots, walkable = walkable,
            spawnX = 304f, spawnY = FRONT_WALL - 35f,
            clerkX = 304f, clerkY = 58f,
            hangouts = hangouts,
            discoX = discoX, discoY = discoY,
            machineSlots = machineSlots,
            cafeQueue = cafeQueue,
        )
    }

    /**
     * What [p] does when the player uses it, or null for a prop that only decorates: the photo
     * booth, the kiddie rides, the vending machines, the café's service counter and the bought
     * trophy case, fish tank and jukebox. Making another prop interactive starts here: give it a
     * [SpotType] (and a line in `HubRenderer.drawPrompt` and `ArcadeApp.onSpot`).
     */
    fun spotTypeOf(p: Prop): SpotType? = when (p.kind) {
        PropKind.PHOTO_BOOTH -> SpotType.PHOTO

        PropKind.KIDDIE_RIDE -> SpotType.RIDE

        PropKind.VENDING -> SpotType.VENDING

        PropKind.CAFE_COUNTER -> SpotType.CAFE

        PropKind.DECOR -> when (p.decor) {
            DecorStyle.TROPHY_CASE -> SpotType.TROPHY

            DecorStyle.FISH_TANK -> SpotType.TANK

            DecorStyle.JUKEBOX -> SpotType.JUKEBOX

            else -> null
        }

        else -> null
    }

    /**
     * The floor where the player stands to use [p]: the stand-in-front rule. A strip along the
     * prop's front, [PROMPT_DEPTH] deep, centred on it (a long counter is served at its till, the
     * rest at their middle) and at most [MAX_STAND_HALF] either side, cut short before any of
     * [solids] that stands in it. The strip starts exactly at the prop's front, so first person
     * keeps its body [Body.FRONT_GAP] off it, as it does for a cabinet. Fails loudly if less than
     * [MIN_STAND_DEPTH] is left, like a bank that doesn't fit: move the prop.
     */
    fun standArea(p: Prop, solids: List<Box>): Box {
        val cx = if (p.kind == PropKind.CAFE_COUNTER) CafeLayout.TILL_X else p.centerX
        val half = ((p.x1 - p.x0) / 2f - 1f).coerceIn(MIN_STAND_HALF, MAX_STAND_HALF)
        val left = cx - half
        val right = cx + half
        val front = p.frontZ
        var bottom = front + PROMPT_DEPTH
        for (b in solids) {
            if (b.left < right && b.right > left && b.bottom > front && b.top < bottom) bottom = maxOf(b.top, front)
        }
        check(bottom - front >= MIN_STAND_DEPTH) {
            "No room to stand in front of the ${p.kind} at ($left..$right, $front): only ${bottom - front} clear, it needs $MIN_STAND_DEPTH"
        }
        return Box(left, front, right, bottom)
    }

    /**
     * A spot for every interactive prop in [props] ([spotTypeOf]), standing in front of it
     * ([standArea]) with its prompt floating over the top and the view turning to face it. Each
     * spot keeps its [Spot.prop].
     */
    fun propSpots(props: List<Prop>, solids: List<Box>): List<Spot> {
        val out = ArrayList<Spot>()
        for (p in props) {
            val type = spotTypeOf(p) ?: continue
            val area = standArea(p, solids)
            out += Spot(
                type, -1, area,
                area.centerX, p.height + 6f, p.frontZ - 4f,
                area.centerX, p.height * 0.6f, p.frontZ - 2f,
                prop = p,
            )
        }
        return out
    }

    private fun addMachine(
        props: MutableList<Prop>, spots: MutableList<Spot>, hangouts: MutableList<Hangout>,
        index: Int, shape: CabinetShape, x0: Float, back: Float, cw: Float, cd: Float, ch: Float,
        focus: Pair<Float, Float>, copy: Int,
    ) {
        val cx = x0 + cw / 2f
        val front = back + cd
        props += Prop(PropKind.MACHINE, x0, back, x0 + cw, front, ch, machine = index, shape = shape, variant = copy)
        val (fy, setBack) = focus
        val half = maxOf(cw / 2f - 1f, 12f)
        spots += Spot(
            SpotType.MACHINE, index,
            Box(cx - half, front, cx + half, front + PROMPT_DEPTH),
            cx, ch + 6f, front - 4f,
            cx, fy, front - setBack,
        )
        hangouts += Hangout(cx, front + 12f, Math.PI.toFloat(), playing = true)
    }

    /** Footprint width, depth and height of each decoration. */
    fun decorSize(style: DecorStyle): Triple<Float, Float, Float> = when (style) {
        DecorStyle.TROPHY_CASE -> Triple(34f, 14f, 60f)
        DecorStyle.PLUSH_BEAR -> Triple(22f, 18f, 44f)
        DecorStyle.JUKEBOX -> Triple(26f, 14f, 50f)
        DecorStyle.FISH_TANK -> Triple(40f, 16f, 44f)
        DecorStyle.LAVA_LAMP -> Triple(10f, 10f, 34f)
        DecorStyle.FLAMINGO -> Triple(12f, 6f, 40f)
        DecorStyle.GUMBALL -> Triple(10f, 10f, 34f)
        DecorStyle.PALM -> Triple(16f, 16f, 60f)
        DecorStyle.DISCO_BALL -> Triple(16f, 16f, 16f)
    }
}
