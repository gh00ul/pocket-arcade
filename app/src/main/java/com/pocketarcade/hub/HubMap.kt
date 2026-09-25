package com.pocketarcade.hub

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.hash01
import com.pocketarcade.games.MiniGame

enum class PropKind { MACHINE, BROKEN, COUNTER, TOKENS, SODA, BENCH, PLANT, TRASH, DECOR }

/** Something standing in the hall, drawn depth-sorted by the bottom of its footprint. */
class Prop(
    val kind: PropKind,
    val sprite: PropSprite,
    /** Top-left of the sprite in hall pixels. */
    val x: Float,
    val y: Float,
    val machine: Int = -1,
    val decor: DecorStyle? = null,
    sortOverride: Float? = null,
) {
    val foot: Box? = if (sprite.foot.w > 0 && sprite.foot.h > 0) {
        Box.ofSize(x + sprite.foot.x, y + sprite.foot.y, sprite.foot.w.toFloat(), sprite.foot.h.toFloat())
    } else null
    val sortY: Float = sortOverride ?: (y + sprite.h)
    val bottom: Float get() = y + sprite.h
}

enum class SpotType { MACHINE, TOKENS, PRIZES }

/** A place to stand that offers an action, with where its prompt bubble floats. */
class Spot(
    val type: SpotType,
    val machine: Int,
    val area: Box,
    /** Bottom-centre of the prompt bubble. */
    val anchorX: Float,
    val anchorY: Float,
    /** Point the camera zooms into when entering (the machine's screen). */
    val focusX: Float,
    val focusY: Float,
    /** Draw the bubble under the anchor (pointer on top) instead of above it. */
    val bubbleBelow: Boolean = false,
)

class HubMap(
    val widthPx: Int,
    val heightPx: Int,
    val cols: Int,
    val rows: Int,
    val background: ImageBitmap,
    val props: List<Prop>,
    val solids: List<Box>,
    val spots: List<Spot>,
    val walkable: BooleanArray,
    val spawnX: Float,
    val spawnY: Float,
    val clerkX: Float,
    val clerkY: Float,
    val signCenterX: Float,
    val signY: Float,
    /** Tiles where NPC kids like to stand and "play". */
    val hangouts: List<Pair<Float, Float>>,
    val discoX: Float,
    val discoY: Float,
) {
    fun tileWalkable(tx: Int, ty: Int): Boolean =
        tx in 0 until cols && ty in 0 until rows && walkable[ty * cols + tx]
}

/**
 * Builds the hall: a back wall with the prize counter, a lounge, then rows of two machines each
 * (one per registered game, plus "coming soon" cabinets to fill a row), and the entrance with
 * the token machine. The hall grows taller automatically as machines are registered.
 */
object HubLayout {
    const val TILE = 16
    const val COLS = 14
    const val WIDTH = COLS * TILE
    private const val MACHINE_START_ROW = 13
    private const val BLOCK_ROWS = 10
    private const val FRONT_OFFSET_ROWS = 6
    private const val ENTRANCE_ROWS = 8
    const val LEFT_SLOT_X = 56f
    const val RIGHT_SLOT_X = 168f
    const val AISLE_LEFT = 80
    const val AISLE_RIGHT = 144

    private var cachedBackground: ImageBitmap? = null
    private var cachedKey = ""

    fun rowsFor(machineCount: Int): Int {
        val blocks = ((machineCount + 1) / 2).coerceAtLeast(1)
        return MACHINE_START_ROW + blocks * BLOCK_ROWS + ENTRANCE_ROWS
    }

    fun machineFrontY(slot: Int): Float = ((MACHINE_START_ROW + FRONT_OFFSET_ROWS + (slot / 2) * BLOCK_ROWS) * TILE).toFloat()

    fun slotCenterX(slot: Int): Float = if (slot % 2 == 0) LEFT_SLOT_X else RIGHT_SLOT_X

    fun build(games: List<MiniGame>, ownedDecor: Set<DecorStyle>): HubMap {
        val blocks = ((games.size + 1) / 2).coerceAtLeast(1)
        val rows = rowsFor(games.size)
        val entranceRow = MACHINE_START_ROW + blocks * BLOCK_ROWS
        val w = WIDTH
        val h = rows * TILE
        val props = ArrayList<Prop>()
        val spots = ArrayList<Spot>()
        val solids = ArrayList<Box>()

        // Walls, plus a block behind the counter so only the clerk stands there.
        solids += Box(0f, 0f, w.toFloat(), 48f)
        solids += Box(0f, 0f, 16f, h.toFloat())
        solids += Box((w - 16).toFloat(), 0f, w.toFloat(), h.toFloat())
        solids += Box(0f, (h - 16).toFloat(), w.toFloat(), h.toFloat())
        solids += Box(60f, 48f, 164f, 80f)

        // Prize counter.
        val counter = PropArt.counter()
        props += Prop(PropKind.COUNTER, counter, 64f, 96f - counter.h)
        spots += Spot(SpotType.PRIZES, -1, Box(80f, 96f, 144f, 122f), 112f, 126f, 112f, 80f, bubbleBelow = true)

        // Machines, two per row.
        val slotCount = blocks * 2
        val mats = ArrayList<Triple<Float, Float, Int>>()
        for (slot in 0 until slotCount) {
            val cx = slotCenterX(slot)
            val front = machineFrontY(slot)
            if (slot < games.size) {
                val g = games[slot]
                val sprite = PropArt.cabinet(g.look, g.marquee)
                val px = cx - sprite.w / 2f
                val py = front - sprite.h
                props += Prop(PropKind.MACHINE, sprite, px, py, machine = slot)
                val scr = sprite.screen!!
                spots += Spot(
                    SpotType.MACHINE, slot,
                    Box(cx - 18f, front, cx + 18f, front + 26f),
                    cx, front - 18f,
                    px + scr.x + scr.w / 2f, py + scr.y + scr.h / 2f,
                )
                mats += Triple(cx, front, g.look.glow)
            } else {
                val sprite = PropArt.brokenCabinet()
                props += Prop(PropKind.BROKEN, sprite, cx - sprite.w / 2f, front - sprite.h)
                mats += Triple(cx, front, Pal.GRAY)
            }
        }

        // Lounge furniture.
        val bench = PropArt.bench()
        props += Prop(PropKind.BENCH, bench, 20f, 182f)
        props += Prop(PropKind.BENCH, bench, (w - 20 - bench.w).toFloat(), 182f)

        // Entrance: token machine, soda machine, bin and plants by the doors.
        val entranceFront = ((entranceRow + 3) * TILE).toFloat()
        val tokens = PropArt.tokenMachine()
        val tokenX = 20f
        props += Prop(PropKind.TOKENS, tokens, tokenX, entranceFront - tokens.h)
        val tokScreen = tokens.screen!!
        spots += Spot(
            SpotType.TOKENS, -1,
            Box(tokenX - 4f, entranceFront, tokenX + tokens.w + 6f, entranceFront + 26f),
            tokenX + tokens.w / 2f + 10f, entranceFront - 14f,
            tokenX + tokScreen.x + tokScreen.w / 2f, entranceFront - tokens.h + tokScreen.y + tokScreen.h / 2f,
        )
        val soda = PropArt.sodaMachine()
        props += Prop(PropKind.SODA, soda, (w - 20 - soda.w).toFloat(), entranceFront - soda.h)
        val trash = PropArt.trashCan()
        props += Prop(PropKind.TRASH, trash, (w - 20 - soda.w - trash.w - 4).toFloat(), entranceFront - trash.h)
        val plant = PropArt.plant()
        props += Prop(PropKind.PLANT, plant, 74f, (h - 16 - plant.h).toFloat())
        props += Prop(PropKind.PLANT, plant, 136f, (h - 16 - plant.h).toFloat())

        // Decorations the player has bought, each in its own spot.
        val discoX = 112f
        val discoY = 150f
        val decorSpots = ArrayList<Pair<Float, Float>>()
        decorSpots += 20f to 96f
        decorSpots += (w - 20f - 30f) to 96f
        decorSpots += 20f to 164f
        decorSpots += (w - 20f - 36f) to 164f
        for (b in 0 until blocks) {
            val aisle = machineFrontY(b * 2) + 64f
            decorSpots += 18f to aisle
            decorSpots += (w - 18f - 22f) to aisle
        }
        val order = listOf(
            DecorStyle.TROPHY_CASE, DecorStyle.PLUSH_BEAR, DecorStyle.JUKEBOX, DecorStyle.FISH_TANK,
            DecorStyle.LAVA_LAMP, DecorStyle.FLAMINGO, DecorStyle.GUMBALL, DecorStyle.PALM,
        )
        for ((i, style) in order.withIndex()) {
            if (style !in ownedDecor || i >= decorSpots.size) continue
            val sprite = PropArt.decor(style)
            val (sx, bottom) = decorSpots[i]
            props += Prop(PropKind.DECOR, sprite, sx, bottom - sprite.h, decor = style)
        }
        if (DecorStyle.DISCO_BALL in ownedDecor) {
            val sprite = PropArt.decor(DecorStyle.DISCO_BALL)
            props += Prop(PropKind.DECOR, sprite, discoX - sprite.w / 2f, discoY - sprite.h / 2f, decor = DecorStyle.DISCO_BALL, sortOverride = Float.MAX_VALUE)
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

        val key = "$rows:" + mats.joinToString(",") { "${it.third}" }
        val bg = if (key == cachedKey) cachedBackground!! else paintBackground(w, h, rows, entranceRow, mats).also {
            cachedBackground = it
            cachedKey = key
        }

        return HubMap(
            widthPx = w, heightPx = h, cols = COLS, rows = rows,
            background = bg,
            props = props, solids = solids, spots = spots, walkable = walkable,
            spawnX = 112f, spawnY = (h - 16 - 30).toFloat(),
            clerkX = 112f, clerkY = 76f,
            signCenterX = 112f, signY = 6f,
            hangouts = hangouts,
            discoX = discoX, discoY = discoY,
        )
    }

    // ---------------------------------------------------------------- floor and walls

    private fun paintBackground(w: Int, h: Int, rows: Int, entranceRow: Int, mats: List<Triple<Float, Float, Int>>): ImageBitmap {
        val c = PixelCanvas(w, h)
        val carpet = Pal.DEEP
        c.fill(0, 0, w, h, carpet)

        // Neon carpet: scattered retro squiggles, triangles, rings and zigzags.
        val motif = intArrayOf(Pal.CYAN, Pal.PINK, Pal.YELLOW, Pal.LIME, Pal.ORANGE, Pal.PURPLE)
        for (ty in 3 until rows) for (tx in 1 until COLS - 1) {
            repeat(2) { k ->
                val hx = (hash01(tx, ty, 11 + k) * 11).toInt()
                val hy = (hash01(tx, ty, 23 + k) * 11).toInt()
                val col = Pal.mix(motif[(hash01(tx, ty, 37 + k) * motif.size).toInt().coerceAtMost(motif.size - 1)], carpet, 0.45f)
                val x = tx * TILE + hx
                val y = ty * TILE + hy
                when ((hash01(tx, ty, 51 + k) * 5).toInt()) {
                    0 -> {
                        c.set(x, y + 1, col); c.set(x + 1, y, col); c.set(x + 2, y + 1, col); c.set(x + 3, y + 2, col); c.set(x + 4, y + 1, col)
                    }
                    1 -> {
                        c.set(x + 2, y, col); c.hline(x + 1, x + 3, y + 1, col); c.hline(x, x + 4, y + 2, col)
                    }
                    2 -> {
                        c.set(x + 1, y, col); c.set(x, y + 1, col); c.set(x + 2, y + 1, col); c.set(x + 1, y + 2, col)
                    }
                    3 -> {
                        c.set(x, y, col); c.set(x + 1, y + 1, col); c.set(x + 2, y, col); c.set(x + 3, y + 1, col); c.set(x + 4, y, col)
                    }
                    else -> {
                        c.set(x + 1, y, col); c.hline(x, x + 2, y + 1, col); c.set(x + 1, y + 2, col)
                    }
                }
            }
        }

        // Red carpet in front of the prize counter.
        val red = Pal.mix(Pal.DARKRED, carpet, 0.25f)
        c.fill(16, 48, w - 32, 80, red)
        c.rect(17, 49, w - 34, 78, Pal.mix(Pal.GOLD, red, 0.3f))
        for (i in 0 until 12) c.set(24 + i * 16, 124, Pal.mix(Pal.GOLD, red, 0.5f))

        // Checkered centre aisle with neon edges.
        val aisleTop = 128
        val aisleBottom = entranceRow * TILE
        val checkA = Pal.NIGHT
        val checkB = 0xFF5E5688.toInt()
        for (y in aisleTop until aisleBottom) for (x in AISLE_LEFT until AISLE_RIGHT) {
            c.set(x, y, if (((x - AISLE_LEFT) / 8 + (y - aisleTop) / 8) % 2 == 0) checkA else checkB)
        }
        c.vline(AISLE_LEFT, aisleTop, aisleBottom - 1, Pal.PINK)
        c.vline(AISLE_RIGHT - 1, aisleTop, aisleBottom - 1, Pal.PINK)
        c.vline(AISLE_LEFT - 1, aisleTop, aisleBottom - 1, Pal.mix(Pal.PINK, carpet, 0.6f))
        c.vline(AISLE_RIGHT, aisleTop, aisleBottom - 1, Pal.mix(Pal.PINK, carpet, 0.6f))

        // Play mats in front of every cabinet, in the machine's glow colour.
        for ((cx, front, glow) in mats) {
            val x0 = (cx - 20).toInt()
            val y0 = front.toInt() + 1
            c.fill(x0, y0, 40, 26, Pal.mix(glow, carpet, 0.78f))
            c.rect(x0, y0, 40, 26, Pal.mix(glow, carpet, 0.35f))
            c.set(x0 + 20, y0 + 20, Pal.mix(glow, carpet, 0.35f))
            c.set(x0 + 19, y0 + 21, Pal.mix(glow, carpet, 0.35f))
            c.set(x0 + 21, y0 + 21, Pal.mix(glow, carpet, 0.35f))
        }

        // Entrance tiles.
        val tileA = Pal.mix(Pal.TAN, carpet, 0.6f)
        val grout = Pal.mix(Pal.TAN, carpet, 0.8f)
        for (y in entranceRow * TILE until h - 16) for (x in 16 until w - 16) {
            c.set(x, y, if (x % 16 == 0 || y % 16 == 0) grout else tileA)
        }
        val matX = 92
        val matY = h - 36
        c.fill(matX, matY, 40, 18, Pal.BROWN)
        c.rect(matX, matY, 40, 18, Pal.DARKBROWN)
        c.textCentered("WELCOME", matX + 20, matY + 7, Pal.TAN, tiny = true)

        // Back wall with posters and prize shelves.
        c.fill(0, 0, w, 48, Pal.PLUM)
        c.fill(0, 0, w, 4, Pal.NIGHT)
        for (x in 0 until w step 16) c.vline(x, 4, 43, Pal.INDIGO)
        c.fill(0, 44, w, 4, Pal.VIOLET)
        c.hline(0, w - 1, 44, Pal.LAVENDER)
        // Shelves behind the counter.
        for (sy in intArrayOf(24, 36)) {
            c.fill(62, sy, 100, 2, Pal.BROWN)
            c.hline(62, 161, sy + 2, Pal.DARKBROWN)
        }
        val shelfItems = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE, Pal.WHITE, Pal.GOLD, Pal.SKY)
        for (i in 0 until 12) {
            val ix = 66 + i * 8
            val col = shelfItems[i % shelfItems.size]
            c.fill(ix, 19, 5, 5, col); c.set(ix + 1, 20, Pal.BLACK); c.set(ix + 3, 20, Pal.BLACK)
            val col2 = shelfItems[(i + 3) % shelfItems.size]
            c.fill(ix + 1, 31, 4, 5, col2); c.set(ix + 1, 31, Pal.WHITE)
        }
        // Posters.
        poster(c, 22, 12, Pal.NAVY, Pal.CYAN, 0)
        poster(c, w - 22 - 30, 12, Pal.DARKRED, Pal.YELLOW, 1)

        // Side walls seen from above.
        for (x0 in intArrayOf(0, w - 16)) {
            c.fill(x0, 0, 16, h, Pal.NIGHT)
            c.dither(x0 + 2, 0, 12, h, Pal.DEEP)
        }
        c.vline(15, 48, h - 1, Pal.PLUM)
        c.vline(w - 16, 48, h - 1, Pal.PLUM)
        // Entrance wall with glass doors.
        c.fill(0, h - 16, w, 16, Pal.NIGHT)
        c.hline(16, w - 17, h - 16, Pal.PLUM)
        c.fill(96, h - 16, 32, 16, Pal.mix(Pal.SKY, Pal.NIGHT, 0.55f))
        c.rect(96, h - 16, 32, 16, Pal.LIGHTGRAY)
        c.vline(111, h - 16, h - 1, Pal.LIGHTGRAY)
        c.vline(112, h - 16, h - 1, Pal.LIGHTGRAY)
        c.set(108, h - 9, Pal.GOLD); c.set(115, h - 9, Pal.GOLD)
        return c.toImageBitmap()
    }

    private fun poster(c: PixelCanvas, x: Int, y: Int, bg: Int, fg: Int, art: Int) {
        c.fill(x, y, 30, 26, bg)
        c.rect(x, y, 30, 26, Pal.LIGHTGRAY)
        if (art == 0) {
            val rows = arrayOf(
                "..#.....#..",
                "...#...#...",
                "..#######..",
                ".##.###.##.",
                "###########",
                "#.#######.#",
                "#.#.....#.#",
                "...##.##...",
            )
            c.sprite(rows, x + 10, y + 5, mapOf('#' to fg))
            c.textCentered("1UP", x + 15, y + 17, Pal.WHITE, tiny = true)
        } else {
            c.textCentered("HI", x + 15, y + 4, fg, tiny = true)
            c.textCentered("SCORE", x + 15, y + 11, fg, tiny = true)
            c.hline(x + 5, x + 24, y + 18, Pal.WHITE)
            c.textCentered("*", x + 15, y + 19, Pal.WHITE, tiny = true)
        }
    }
}
