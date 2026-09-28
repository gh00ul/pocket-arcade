package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame

/** What a hall object is, for building its model. */
enum class PropKind {
    MACHINE, COUNTER, PRIZE_WALL, TOKENS, CHANGE, VENDING, CAFE_TABLE, STOOL, PILLAR, PLANT, TRASH,
    PHOTO_BOOTH, DECOR, BENCH, DOORS, KIDDIE_RIDE,
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

enum class SpotType { MACHINE, TOKENS, PRIZES }

/**
 * A place the player can stand to use something. [area] is where they must be; the prompt
 * floats at the anchor, and the enter transition flies the camera to the focus point.
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
)

/**
 * Where a wandering kid can stop: position, which way they face (radians) and whether it's a
 * machine. Kids path to the walk-grid tile ([tileX], [tileY]) nearest the spot, then step onto it.
 */
class Hangout(
    val x: Float,
    val z: Float,
    val yaw: Float,
    val playing: Boolean,
    val tileX: Int = (x / HubLayout.TILE).toInt(),
    val tileY: Int = (z / HubLayout.TILE).toInt(),
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
) {
    fun tileWalkable(tx: Int, ty: Int): Boolean =
        tx in 0 until cols && ty in 0 until rows && walkable[ty * cols + tx]
}

/**
 * A pre-sized stretch of floor for one machine's bank: [count] cabinets, each in a cell of at
 * most [maxW] wide, [maxD] deep and [maxH] tall, [gap] apart, starting at [x0] with their backs
 * at [back]. The cells and the play spots in front of them are kept clear of everything else, so
 * any cabinet that fits a cell can't overlap anything. A slot with a [shape] is that shape's
 * bank; a spare slot (no shape) takes the first machine that has no bank of its own.
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
) {
    val x1: Float get() = x0 + count * maxW + (count - 1) * gap

    /** The floor the slot reserves: every cell plus the play spots in front of them. */
    val area: Box get() = Box(x0, back, x1, back + maxD + HubLayout.PROMPT_DEPTH)

    /** Left edge of cell [k]. */
    fun cellX(k: Int): Float = x0 + k * (maxW + gap)
}

/**
 * The arcade's floor plan. Machines stand in banks like a real arcade: a row of claw machines
 * and the prize counter along the back wall, skee-ball and basketball alleys down the sides,
 * air hockey tables in the middle, coin pushers, whack-a-moles and racers across the middle and
 * a lounge with vending machines. Nearer the doors a second floor holds pre-sized banks for
 * newer machines, then the token kiosk and kiddie rides by the entrance.
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
     * How much further from the back wall the foyer (kiosk, kiddie rides, bench, the doors) is
     * than in the first floor plan: the hall grew this much towards the street for more banks.
     */
    const val FOYER_SHIFT = 240f

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
    }

    /** Where the camera flies to when entering: height and how far behind the cabinet front. */
    fun focus(shape: CabinetShape): Pair<Float, Float> {
        val (_, d, _) = cabinetSize(shape)
        return when (shape) {
            CabinetShape.CLAW -> 38f to 3f
            CabinetShape.TOWER -> 48f to 4f
            CabinetShape.UPRIGHT -> 40f to 6f
            CabinetShape.WIDE -> 38f to 4f
            CabinetShape.PUSHER -> 36f to 6f
            CabinetShape.WHACK -> 44f to d - 4f
            CabinetShape.RACER -> 42f to d - 10f
            CabinetShape.SKEEBALL -> 36f to d - 10f
            CabinetShape.HOOPS -> 56f to d - 10f
            CabinetShape.LANE -> 34f to d - 10f
            CabinetShape.AIR_HOCKEY, CabinetShape.TABLE -> 46f to d - 2f
        }
    }

    /** A bank whose cells fit [shape]'s cabinet exactly. */
    private fun bank(shape: CabinetShape, count: Int, x0: Float, back: Float, gap: Float): Slot {
        val (w, d, h) = cabinetSize(shape)
        return Slot(shape, count, x0, back, gap, w, d, h)
    }

    /**
     * Every bank on the floor. The ones nearer the doors are pre-sized for machines still to
     * come: a cabinet must fit its cell, or the build fails rather than overlap its neighbours.
     */
    val slots: List<Slot> = listOf(
        // Along the back wall, either side of the prize counter.
        bank(CabinetShape.CLAW, 4, 26f, 34f, 4f),
        bank(CabinetShape.TOWER, 3, 488f, 34f, 4f),
        // Alleys down the side walls, air hockey tables between them.
        bank(CabinetShape.SKEEBALL, 4, 24f, 168f, 2f),
        bank(CabinetShape.HOOPS, 3, 476f, 172f, 2f),
        bank(CabinetShape.AIR_HOCKEY, 2, 216f, 190f, 104f),
        // Across the middle: the whack row, the pusher island and the linked racers.
        bank(CabinetShape.WHACK, 3, 26f, 360f, 6f),
        bank(CabinetShape.PUSHER, 4, 218f, 356f, 4f),
        bank(CabinetShape.RACER, 4, 236f, 520f, 2f),
        // The front floor: a centre row of wide cabinets, tall ones along the right wall, two
        // big round ones on the left, and two more banks behind them.
        Slot(null, 3, 232f, 630f, 6f, maxW = 44f, maxD = 40f, maxH = 86f),
        Slot(null, 4, 448f, 620f, 4f, maxW = 30f, maxD = 60f, maxH = 78f),
        Slot(null, 2, 24f, 720f, 16f, maxW = 64f, maxD = 64f, maxH = 64f),
        Slot(null, 3, 236f, 740f, 6f, maxW = 40f, maxD = 60f, maxH = 86f),
        Slot(null, 3, 448f, 750f, 6f, maxW = 40f, maxD = 60f, maxH = 86f),
    )

    /** Spots for bought decorations: centre x, front z, facing. */
    private val decorSpots = mapOf(
        DecorStyle.TROPHY_CASE to (182f to 42f),
        DecorStyle.PLUSH_BEAR to (442f to 92f),
        DecorStyle.JUKEBOX to (122f to 462f),
        DecorStyle.FISH_TANK to (562f to 470f),
        DecorStyle.LAVA_LAMP to (206f to 618f + FOYER_SHIFT),
        DecorStyle.FLAMINGO to (156f to 800f + FOYER_SHIFT),
        DecorStyle.GUMBALL to (476f to 702f + FOYER_SHIFT),
        DecorStyle.PALM to (30f to 818f + FOYER_SHIFT),
    )

    fun build(games: List<MiniGame>, ownedDecor: Set<DecorStyle>): HubMap {
        val w = WIDTH
        val h = DEPTH
        val props = ArrayList<Prop>()
        val spots = ArrayList<Spot>()
        val solids = ArrayList<Box>()
        val hangouts = ArrayList<Hangout>()
        val foyer = FOYER_SHIFT

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
        for ((index, g) in games.withIndex()) {
            val shape = g.look.shape
            val si = slots.indices.firstOrNull { !used[it] && slots[it].shape == shape }
                ?: slots.indices.firstOrNull { !used[it] && slots[it].shape == null }
                ?: error("No room on the hall floor for '${g.id}': every bank and spare slot is taken. Add a Slot to HubLayout.slots.")
            used[si] = true
            val slot = slots[si]
            val (cw, cd, ch) = cabinetSize(shape)
            check(cw <= slot.maxW && cd <= slot.maxD && ch <= slot.maxH) {
                "'${g.id}' cabinet $cw x $cd x $ch doesn't fit its slot's ${slot.maxW} x ${slot.maxD} x ${slot.maxH} cells"
            }
            for (k in 0 until slot.count) {
                // Centred in its cell, backs in line.
                val x0 = slot.cellX(k) + (slot.maxW - cw) / 2f
                addMachine(props, spots, hangouts, index, shape, x0, slot.back, cw, cd, ch, k)
            }
        }

        // Pillars: a pair mid-hall and a pair in the foyer.
        for ((px, pz) in listOf(192f to 450f, 416f to 450f, 192f to 690f + foyer, 416f to 690f + foyer)) {
            props += Prop(PropKind.PILLAR, px - 9f, pz - 9f, px + 9f, pz + 9f, WALL_HEIGHT)
        }

        // Lounge: café tables with stools and a pair of vending machines.
        props += Prop(PropKind.VENDING, 24f, 440f, 54f, 462f, 62f, variant = 0)
        props += Prop(PropKind.VENDING, 58f, 440f, 88f, 462f, 62f, variant = 1)
        for ((i, t) in listOf(64f to 530f, 140f to 580f, 64f to 640f).withIndex()) {
            val (tx, tz) = t
            props += Prop(PropKind.CAFE_TABLE, tx - 11f, tz - 11f, tx + 11f, tz + 11f, 24f, variant = i)
            for (k in 0 until 3) {
                val a = k * 2.094f + i
                val sx = tx + kotlin.math.cos(a) * 20f
                val sz = tz + kotlin.math.sin(a) * 20f
                props += Prop(PropKind.STOOL, sx - 4.5f, sz - 4.5f, sx + 4.5f, sz + 4.5f, 16f, solid = false)
                hangouts += Hangout(sx, sz + 4f, kotlin.math.atan2(tx - sx, tz - sz), playing = false)
            }
        }
        props += Prop(PropKind.BENCH, 20f, 716f + foyer, 90f, 730f + foyer, 16f)
        // Coin-op kiddie rides either side of the way in.
        props += Prop(PropKind.KIDDIE_RIDE, 214f, 716f + foyer, 242f, 748f + foyer, 46f, variant = 0)
        props += Prop(PropKind.KIDDIE_RIDE, 366f, 716f + foyer, 394f, 748f + foyer, 30f, variant = 1)
        props += Prop(PropKind.PHOTO_BOOTH, 532f, 346f, 582f, 396f, 78f)

        // Token kiosk and change machine on the way in from the doors.
        props += Prop(PropKind.TOKENS, 500f, 640f + foyer, 534f, 664f + foyer, 60f)
        props += Prop(PropKind.CHANGE, 540f, 642f + foyer, 568f, 664f + foyer, 56f)
        spots += Spot(SpotType.TOKENS, -1, Box(494f, 664f + foyer, 540f, 692f + foyer), 517f, 70f, 660f + foyer, 517f, 40f, 666f + foyer)
        props += Prop(PropKind.TRASH, 250f, FRONT_WALL - 22f, 262f, FRONT_WALL - 10f, 18f)
        props += Prop(PropKind.TRASH, 346f, FRONT_WALL - 22f, 358f, FRONT_WALL - 10f, 18f)
        props += Prop(PropKind.PLANT, 22f, FRONT_WALL - 22f, 40f, FRONT_WALL - 4f, 40f)
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

        // Walkable tile grid for the kids' path finding.
        val cols = w / TILE
        val rows = h / TILE
        val walkable = BooleanArray(cols * rows)
        for (ty in 0 until rows) for (tx in 0 until cols) {
            val cx = tx * TILE + TILE / 2f
            val cy = ty * TILE + TILE / 2f
            walkable[ty * cols + tx] = solids.none { it.intersects(cx - 6f, cy - 4f, cx + 6f, cy + 6f) }
        }
        // Standing spots in the aisles, on the old floor and the new.
        val aisles = listOf(
            304f to 150f, 150f to 310f, 460f to 310f, 304f to 470f, 470f to 560f,
            196f to 700f, 412f to 716f, 200f to 812f, 304f to 842f,
            360f to 650f + foyer, 250f to 740f + foyer,
        )
        for ((x, z) in aisles) hangouts += Hangout(x, z, 0f, playing = false)
        // A stool or a play spot can sit closer to its table or cabinet than a walkable tile's
        // centre, so kids aim for the nearest free tile and walk the last step.
        for (i in hangouts.indices) {
            val hg = hangouts[i]
            var best = -1
            var bestD = Float.MAX_VALUE
            val tx0 = (hg.x / TILE).toInt()
            val ty0 = (hg.z / TILE).toInt()
            for (ty in ty0 - 2..ty0 + 2) for (tx in tx0 - 2..tx0 + 2) {
                if (tx !in 0 until cols || ty !in 0 until rows || !walkable[ty * cols + tx]) continue
                val d = kotlin.math.hypot(tx * TILE + TILE / 2f - hg.x, ty * TILE + TILE / 2f - hg.z)
                if (d < bestD) {
                    bestD = d
                    best = ty * cols + tx
                }
            }
            if (best >= 0) hangouts[i] = Hangout(hg.x, hg.z, hg.yaw, hg.playing, best % cols, best / cols)
        }

        return HubMap(
            widthPx = w, heightPx = h, cols = cols, rows = rows,
            props = props, solids = solids, spots = spots, walkable = walkable,
            spawnX = 304f, spawnY = FRONT_WALL - 35f,
            clerkX = 304f, clerkY = 58f,
            hangouts = hangouts,
            discoX = discoX, discoY = discoY,
        )
    }

    private fun addMachine(
        props: MutableList<Prop>, spots: MutableList<Spot>, hangouts: MutableList<Hangout>,
        index: Int, shape: CabinetShape, x0: Float, back: Float, cw: Float, cd: Float, ch: Float, copy: Int,
    ) {
        val cx = x0 + cw / 2f
        val front = back + cd
        props += Prop(PropKind.MACHINE, x0, back, x0 + cw, front, ch, machine = index, shape = shape, variant = copy)
        val (fy, setBack) = focus(shape)
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
