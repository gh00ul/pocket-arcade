package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame

enum class PropKind { MACHINE, BROKEN, COUNTER, TOKENS, SODA, BENCH, PLANT, TRASH, DECOR }

/**
 * Something standing in the hall. The footprint ([x0], [z0])–([x1], [z1]) is in hall units on
 * the floor (z grows toward the entrance); [height] is how tall its model is.
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
) {
    val foot: Box? = if (solid) Box(x0, z0, x1, z1) else null
    val centerX: Float get() = (x0 + x1) / 2f
    val frontZ: Float get() = z1
}

enum class SpotType { MACHINE, TOKENS, PRIZES }

/** A place to stand that offers an action, with where its prompt bubble floats. */
class Spot(
    val type: SpotType,
    val machine: Int,
    val area: Box,
    /** Where the prompt bubble points: a point in the hall (x, height, z). */
    val anchorX: Float,
    val anchorHeight: Float,
    val anchorZ: Float,
    /** The point the camera dives into when entering (the machine's screen). */
    val focusX: Float,
    val focusY: Float,
    val focusZ: Float,
)

/** Everything the floor painter needs to know about a machine slot. */
class FloorMat(val centerX: Float, val frontZ: Float, val glow: Int)

class HubMap(
    val widthPx: Int,
    val heightPx: Int,
    val cols: Int,
    val rows: Int,
    val entranceRow: Int,
    val props: List<Prop>,
    val solids: List<Box>,
    val spots: List<Spot>,
    val mats: List<FloorMat>,
    val walkable: BooleanArray,
    val spawnX: Float,
    val spawnY: Float,
    val clerkX: Float,
    val clerkY: Float,
    /** Tiles where NPC kids like to stand and "play". */
    val hangouts: List<Pair<Float, Float>>,
    val discoX: Float,
    val discoY: Float,
) {
    fun tileWalkable(tx: Int, ty: Int): Boolean =
        tx in 0 until cols && ty in 0 until rows && walkable[ty * cols + tx]
}

/**
 * Lays out the hall: the back wall with the prize counter, a lounge, then rows of two machines
 * (one per registered game, plus a "coming soon" cabinet to fill an odd row) and the entrance
 * with the token machine. The hall grows longer automatically as machines are registered.
 */
object HubLayout {
    const val TILE = 16
    const val COLS = 14
    const val WIDTH = COLS * TILE
    const val WALL_Z = 48f
    const val WALL_HEIGHT = 76f
    private const val MACHINE_START_ROW = 13
    private const val BLOCK_ROWS = 10
    private const val FRONT_OFFSET_ROWS = 6
    private const val ENTRANCE_ROWS = 8
    const val LEFT_SLOT_X = 56f
    const val RIGHT_SLOT_X = 168f
    const val AISLE_LEFT = 80
    const val AISLE_RIGHT = 144

    fun rowsFor(machineCount: Int): Int {
        val blocks = ((machineCount + 1) / 2).coerceAtLeast(1)
        return MACHINE_START_ROW + blocks * BLOCK_ROWS + ENTRANCE_ROWS
    }

    fun machineFrontZ(slot: Int): Float = ((MACHINE_START_ROW + FRONT_OFFSET_ROWS + (slot / 2) * BLOCK_ROWS) * TILE).toFloat()

    fun slotCenterX(slot: Int): Float = if (slot % 2 == 0) LEFT_SLOT_X else RIGHT_SLOT_X

    /** Cabinet footprint width, depth and height for each silhouette. */
    fun cabinetSize(shape: CabinetShape): Triple<Float, Float, Float> = when (shape) {
        CabinetShape.UPRIGHT -> Triple(30f, 16f, 52f)
        CabinetShape.WIDE -> Triple(46f, 20f, 56f)
        CabinetShape.LANE -> Triple(32f, 62f, 50f)
    }

    /** Height and depth set-back of the centre of a cabinet's screen, for the camera dive. */
    fun screenFocus(shape: CabinetShape): Pair<Float, Float> = when (shape) {
        CabinetShape.UPRIGHT -> 33f to 4f
        CabinetShape.WIDE -> 33f to 4f
        CabinetShape.LANE -> 30f to 52f
    }

    fun build(games: List<MiniGame>, ownedDecor: Set<DecorStyle>): HubMap {
        val blocks = ((games.size + 1) / 2).coerceAtLeast(1)
        val rows = rowsFor(games.size)
        val entranceRow = MACHINE_START_ROW + blocks * BLOCK_ROWS
        val w = WIDTH
        val h = rows * TILE
        val props = ArrayList<Prop>()
        val spots = ArrayList<Spot>()
        val solids = ArrayList<Box>()
        val mats = ArrayList<FloorMat>()

        // Walls, plus a block behind the counter so only the clerk stands there.
        solids += Box(0f, 0f, w.toFloat(), WALL_Z)
        solids += Box(0f, 0f, 16f, h.toFloat())
        solids += Box((w - 16).toFloat(), 0f, w.toFloat(), h.toFloat())
        solids += Box(0f, (h - 16).toFloat(), w.toFloat(), h.toFloat())
        solids += Box(60f, WALL_Z, 164f, 80f)

        // Prize counter.
        props += Prop(PropKind.COUNTER, 64f, 78f, 160f, 96f, 20f)
        spots += Spot(SpotType.PRIZES, -1, Box(80f, 96f, 144f, 122f), 112f, 44f, 104f, 112f, 26f, 90f)

        // Machines, two per row.
        for (slot in 0 until blocks * 2) {
            val cx = slotCenterX(slot)
            val front = machineFrontZ(slot)
            if (slot < games.size) {
                val g = games[slot]
                val (cw, cd, ch) = cabinetSize(g.look.shape)
                props += Prop(PropKind.MACHINE, cx - cw / 2f, front - cd, cx + cw / 2f, front, ch, machine = slot, shape = g.look.shape)
                val (fy, setBack) = screenFocus(g.look.shape)
                spots += Spot(
                    SpotType.MACHINE, slot,
                    Box(cx - 18f, front, cx + 18f, front + 26f),
                    cx, 40f, front - 2f,
                    cx, fy, front - setBack,
                )
                mats += FloorMat(cx, front, g.look.glow)
            } else {
                val (cw, cd, ch) = cabinetSize(CabinetShape.UPRIGHT)
                props += Prop(PropKind.BROKEN, cx - cw / 2f, front - cd, cx + cw / 2f, front, ch)
                mats += FloorMat(cx, front, Pal.GRAY)
            }
        }

        // Lounge benches.
        props += Prop(PropKind.BENCH, 20f, 186f, 56f, 196f, 14f)
        props += Prop(PropKind.BENCH, (w - 56).toFloat(), 186f, (w - 20).toFloat(), 196f, 14f)

        // Entrance: token machine, soda machine, bin and plants by the doors.
        val entranceFront = ((entranceRow + 3) * TILE).toFloat()
        props += Prop(PropKind.TOKENS, 20f, entranceFront - 12f, 42f, entranceFront, 42f)
        spots += Spot(
            SpotType.TOKENS, -1,
            Box(16f, entranceFront, 50f, entranceFront + 26f),
            31f, 50f, entranceFront - 6f,
            31f, 30f, entranceFront,
        )
        props += Prop(PropKind.SODA, (w - 42).toFloat(), entranceFront - 12f, (w - 20).toFloat(), entranceFront, 42f)
        props += Prop(PropKind.TRASH, (w - 58).toFloat(), entranceFront - 10f, (w - 48).toFloat(), entranceFront, 16f)
        props += Prop(PropKind.PLANT, 74f, (h - 24).toFloat(), 88f, (h - 16).toFloat(), 24f)
        props += Prop(PropKind.PLANT, 136f, (h - 24).toFloat(), 150f, (h - 16).toFloat(), 24f)

        // Decorations the player has bought, each with its own spot.
        val discoX = 112f
        val discoY = 150f
        val decorSpots = ArrayList<Pair<Float, Float>>()
        decorSpots += 37f to 96f
        decorSpots += (w - 35f) to 96f
        decorSpots += 33f to 164f
        decorSpots += (w - 38f) to 164f
        for (b in 0 until blocks) {
            val aisle = machineFrontZ(b * 2) + 64f
            decorSpots += 27f to aisle
            decorSpots += (w - 29f) to aisle
        }
        val order = listOf(
            DecorStyle.TROPHY_CASE, DecorStyle.PLUSH_BEAR, DecorStyle.JUKEBOX, DecorStyle.FISH_TANK,
            DecorStyle.LAVA_LAMP, DecorStyle.FLAMINGO, DecorStyle.GUMBALL, DecorStyle.PALM,
        )
        for ((i, style) in order.withIndex()) {
            if (style !in ownedDecor || i >= decorSpots.size) continue
            val (cx, front) = decorSpots[i]
            val (dw, dd, dh) = decorSize(style)
            props += Prop(PropKind.DECOR, cx - dw / 2f, front - dd, cx + dw / 2f, front, dh, decor = style)
        }
        if (DecorStyle.DISCO_BALL in ownedDecor) {
            props += Prop(PropKind.DECOR, discoX - 7f, discoY - 7f, discoX + 7f, discoY + 7f, 14f, decor = DecorStyle.DISCO_BALL, solid = false)
        }

        for (p in props) p.foot?.let { solids += it }

        // Walkable tile grid for NPC path finding.
        val walkable = BooleanArray(COLS * rows)
        for (ty in 0 until rows) for (tx in 0 until COLS) {
            val cx = tx * TILE + TILE / 2f
            val cy = ty * TILE + TILE / 2f
            walkable[ty * COLS + tx] = solids.none { it.intersects(cx - 6f, cy - 4f, cx + 6f, cy + 6f) }
        }

        val hangouts = spots.filter { it.type == SpotType.MACHINE }.map { it.area.centerX to it.area.top + 14f }

        return HubMap(
            widthPx = w, heightPx = h, cols = COLS, rows = rows, entranceRow = entranceRow,
            props = props, solids = solids, spots = spots, mats = mats, walkable = walkable,
            spawnX = 112f, spawnY = (h - 16 - 30).toFloat(),
            clerkX = 112f, clerkY = 72f,
            hangouts = hangouts,
            discoX = discoX, discoY = discoY,
        )
    }

    /** Footprint width, depth and height of each decoration. */
    fun decorSize(style: DecorStyle): Triple<Float, Float, Float> = when (style) {
        DecorStyle.TROPHY_CASE -> Triple(34f, 12f, 36f)
        DecorStyle.PLUSH_BEAR -> Triple(24f, 10f, 34f)
        DecorStyle.JUKEBOX -> Triple(26f, 12f, 36f)
        DecorStyle.FISH_TANK -> Triple(36f, 12f, 30f)
        DecorStyle.LAVA_LAMP -> Triple(16f, 8f, 32f)
        DecorStyle.FLAMINGO -> Triple(12f, 6f, 36f)
        DecorStyle.GUMBALL -> Triple(10f, 7f, 28f)
        DecorStyle.PALM -> Triple(10f, 6f, 32f)
        DecorStyle.DISCO_BALL -> Triple(14f, 14f, 14f)
    }
}
