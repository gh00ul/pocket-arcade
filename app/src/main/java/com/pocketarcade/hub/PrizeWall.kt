package com.pocketarcade.hub

import com.pocketarcade.data.Catalog
import com.pocketarcade.data.Plush
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.claw.Plush3D

/** A boxed toy on a prize-wall shelf: its left edge, the shelf it stands on, its size and which box art it wears. */
class ToyBox(val x: Float, val y: Float, val w: Float, val h: Float, val art: Int)

/** A plush on a prize-wall shelf: which one, where it stands, how it's turned and how big it is. */
class PlushSlot(val plush: Plush, val x: Float, val y: Float, val z: Float, val yaw: Float, val scale: Float)

/**
 * The shelves behind the prize counter: which boxed toys and which plushies stand where. The
 * pattern is a fixed function of the wall's footprint, so the static model ([Props]) and the
 * plushies drawn on top of it ([PrizeWallDisplay]) always agree. Pure floor-plan data, so the
 * JVM tests can build it.
 */
object PrizeWall {
    const val SHELVES = 5

    /** The shelf boards' height for shelf [k]. */
    fun shelfY(k: Int): Float = 14f + k * 17f

    class Layout(val boxes: List<ToyBox>, val plushes: List<PlushSlot>)

    /**
     * Fills [p]'s shelves left to right, shelf by shelf: a boxed toy where (shelf + place) is a
     * multiple of three, a plush everywhere else, the top shelf's plushes bigger.
     */
    fun layout(p: Prop): Layout {
        val boxes = ArrayList<ToyBox>()
        val plushes = ArrayList<PlushSlot>()
        for (k in 0 until SHELVES) {
            val y = shelfY(k)
            var x = p.x0 + 4f
            var i = 0
            while (x < p.x1 - 6f) {
                if ((k + i) % 3 == 0) {
                    val w = 9f + hash01(i, k) * 5f
                    val h = 8f + hash01(i, k + 9) * 6f
                    boxes += ToyBox(x, y, w, h, i + k * 3)
                    x += w + 2f
                } else {
                    val plush = Catalog.plushies[(i * 7 + k * 3) % Catalog.plushies.size]
                    val s = if (k == SHELVES - 1) 0.75f else 0.55f
                    plushes += PlushSlot(plush, x + 5f, y, (p.z0 + p.z1) / 2f + 1f, (hash01(i, k + 3) - 0.5f) * 0.6f, s)
                    x += 12f * s + 4f
                }
                i++
            }
        }
        return Layout(boxes, plushes)
    }

    /** Whether [plush] has been won: it's in the [collection] at least once. */
    fun isOwned(plush: Plush, collection: Map<String, Int>): Boolean = (collection[plush.id] ?: 0) > 0

    /** What plushies not won yet are painted: nearly black, a shape against the wall. */
    const val SILHOUETTE = 0xFF14101F.toInt()
}

/**
 * Which of a wall's [slots] hold a plush the player has won, worked out again only when the save
 * hands over a different collection (it replaces the map with each change), so a frame that sees
 * the same one does nothing.
 */
class OwnedSlots(private val slots: List<PlushSlot>) {
    val owned = BooleanArray(slots.size)
    private var seen: Map<String, Int>? = null

    /** Reads [collection] if it isn't the one already read; returns whether it had to. */
    fun update(collection: Map<String, Int>): Boolean {
        if (collection === seen) return false
        for (i in slots.indices) owned[i] = PrizeWall.isOwned(slots[i].plush, collection)
        seen = collection
        return true
    }
}

/**
 * The prize wall's plushies, drawn as instances of the shared plush models: the ones the player
 * has won in colour, the rest dark silhouettes. Everything is prepared once, so a frame only
 * queues the instances (each culled by the renderer) and re-reads the collection when the save
 * hands over a new one.
 */
class PrizeWallDisplay(val prop: Prop) {
    private val slots = PrizeWall.layout(prop).plushes
    private val models = Array(slots.size) { Plush3D.model(slots[it].plush) }
    private val xforms = Array(slots.size) { i ->
        val s = slots[i]
        Xform().set(s.x, s.y, s.z, yaw = s.yaw, scale = s.scale)
    }
    private val won = OwnedSlots(slots)

    /** Queues every plush on the wall. Allocation-free. */
    fun draw(r: Renderer3D, collection: Map<String, Int>) {
        won.update(collection)
        val owned = won.owned
        for (i in slots.indices) {
            models[i].draw(r, Blend.OPAQUE, xf = xforms[i], tint = if (owned[i]) -1 else PrizeWall.SILHOUETTE)
        }
    }
}
