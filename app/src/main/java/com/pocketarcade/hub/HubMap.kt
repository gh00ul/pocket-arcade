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

/** Where a wandering kid can stop: position, which way they face (radians) and whether it's a machine. */
class Hangout(val x: Float, val z: Float, val yaw: Float, val playing: Boolean)

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
 * The arcade's floor plan. Machines stand in banks like a real arcade: a row of claw machines
 * and the prize counter along the back wall, skee-ball and basketball alleys down the sides,
 * air hockey tables in the middle, coin pushers, whack-a-moles and racers nearer the front, a
 * lounge with vending machines, and the token kiosk by the doors.
 */
object HubLayout {
    const val TILE = 16
    const val WIDTH = 608
    const val DEPTH = 860
    const val WALL = 16f
    const val BACK_WALL = 24f
    const val FRONT_WALL = 840f
    const val WALL_HEIGHT = 150f
    /** The walls carry on up into the dark to here. */
    const val CEILING = 380f
    const val DOOR_X0 = 264f
    const val DOOR_X1 = 344f

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

    /** How many cabinets of each machine the arcade has, and where each bank starts. */
    private class Bank(val shape: CabinetShape, val count: Int, val x0: Float, val back: Float, val gap: Float)

    private val banks = listOf(
        Bank(CabinetShape.CLAW, 4, 26f, 34f, 4f),
        Bank(CabinetShape.TOWER, 3, 488f, 34f, 4f),
        Bank(CabinetShape.SKEEBALL, 4, 24f, 168f, 2f),
        Bank(CabinetShape.HOOPS, 3, 476f, 172f, 2f),
        Bank(CabinetShape.AIR_HOCKEY, 2, 216f, 190f, 104f),
        Bank(CabinetShape.WHACK, 3, 26f, 360f, 6f),
        Bank(CabinetShape.PUSHER, 4, 218f, 356f, 4f),
        Bank(CabinetShape.RACER, 4, 236f, 520f, 2f),
    )

    /** Spots for bought decorations: centre x, front z, facing. */
    private val decorSpots = mapOf(
        DecorStyle.TROPHY_CASE to (182f to 42f),
        DecorStyle.PLUSH_BEAR to (432f to 92f),
        DecorStyle.JUKEBOX to (122f to 462f),
        DecorStyle.FISH_TANK to (562f to 470f),
        DecorStyle.LAVA_LAMP to (206f to 618f),
        DecorStyle.FLAMINGO to (156f to 800f),
        DecorStyle.GUMBALL to (476f to 702f),
        DecorStyle.PALM to (30f to 818f),
    )

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

        // Machines, bank by bank. A game whose bank is taken goes to the extra row.
        val usedBanks = HashSet<CabinetShape>()
        var extraX = 240f
        var extraRow = 0
        for ((index, g) in games.withIndex()) {
            val shape = g.look.shape
            val bank = banks.firstOrNull { it.shape == shape && it.shape !in usedBanks }
            val (cw, cd, ch) = cabinetSize(shape)
            if (bank != null) {
                usedBanks += shape
                for (k in 0 until bank.count) {
                    val x0 = bank.x0 + k * (cw + bank.gap)
                    addMachine(props, spots, hangouts, index, shape, x0, bank.back, cw, cd, ch, k)
                }
            } else {
                // Extra games line up in the open floor between the lounge and the kiosk.
                if (extraX + cw > 456f) {
                    extraX = 240f
                    extraRow++
                }
                val back = 620f + extraRow * 90f
                addMachine(props, spots, hangouts, index, shape, extraX, back, cw, cd, ch, 0)
                extraX += cw + 10f
            }
        }

        // Pillars.
        for ((px, pz) in listOf(192f to 450f, 416f to 450f, 192f to 690f, 416f to 690f)) {
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
        props += Prop(PropKind.BENCH, 20f, 716f, 90f, 730f, 16f)
        // Coin-op kiddie rides either side of the way in.
        props += Prop(PropKind.KIDDIE_RIDE, 214f, 716f, 242f, 748f, 46f, variant = 0)
        props += Prop(PropKind.KIDDIE_RIDE, 366f, 716f, 394f, 748f, 30f, variant = 1)
        props += Prop(PropKind.PHOTO_BOOTH, 532f, 346f, 582f, 396f, 78f)

        // Token kiosk and change machine on the way in from the doors.
        props += Prop(PropKind.TOKENS, 500f, 640f, 534f, 664f, 60f)
        props += Prop(PropKind.CHANGE, 540f, 642f, 568f, 664f, 56f)
        spots += Spot(SpotType.TOKENS, -1, Box(494f, 664f, 540f, 692f), 517f, 70f, 660f, 517f, 40f, 666f)
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
        // Standing spots in the aisles.
        for ((x, z) in listOf(304f to 150f, 150f to 310f, 460f to 310f, 304f to 470f, 360f to 650f, 470f to 560f, 250f to 740f)) {
            hangouts += Hangout(x, z, 0f, playing = false)
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
            Box(cx - half, front, cx + half, front + 26f),
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
