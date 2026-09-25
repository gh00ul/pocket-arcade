package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.range
import com.pocketarcade.games.MiniGame
import kotlin.math.abs
import kotlin.random.Random

/**
 * The walkable arcade hall: map, player, wandering kids, camera and the prompt the player is
 * standing at. Lives as long as the app so returning from a game puts you back where you were.
 */
class HubWorld(val games: List<MiniGame>, private val audio: AudioSynth?) {
    var map: HubMap = HubLayout.build(games, emptySet())
        private set
    val player = Player()
    val npcs = ArrayList<Npc>()
    val camera = HubCamera()
    val joystick = Joystick()
    var time = 0f
        private set

    var screenW = 0f
        private set
    var screenH = 0f
        private set

    var activeSpot: Spot? = null
        private set
    /** Seconds since the current prompt appeared (drives its pop-in). */
    var promptT = 0f
        private set

    /** Screen-space rectangle of the prompt bubble, for tap hit-testing. */
    var bubbleLeft = 0f
    var bubbleTop = 0f
    var bubbleRight = 0f
    var bubbleBottom = 0f
    var bubblePressed = -1L
        private set

    /** Becomes true once the player has walked, which retires the "drag to walk" hint. */
    var hasWalked = false
        private set

    private var ownedDecor: Set<DecorStyle> = emptySet()
    private val rng = Random(42)

    init {
        player.x = map.spawnX
        player.y = map.spawnY
        camera.snapTo(player.x, player.y)
        repeat(13) { i ->
            val look = Looks.randomKid(i + 3)
            var tx: Int
            var ty: Int
            var tries = 0
            do {
                tx = rng.nextInt(1, map.cols - 1)
                ty = rng.nextInt(8, map.rows - 4)
                tries++
            } while (!map.tileWalkable(tx, ty) && tries < 50)
            npcs += Npc(look, tx * HubLayout.TILE + 8f, ty * HubLayout.TILE + 12f, Random(100 + i), i * 1.7f)
        }
    }

    fun setDecor(owned: Set<DecorStyle>) {
        if (owned == ownedDecor) return
        ownedDecor = owned
        map = HubLayout.build(games, owned)
        // If a new decoration landed on the player, nudge them to the nearest free spot.
        if (Collision.blocked(map.solids, player.x, player.y)) {
            for (r in 1..6) {
                val found = (-r..r).flatMap { dx -> (-r..r).map { dy -> dx to dy } }
                    .firstOrNull { (dx, dy) -> !Collision.blocked(map.solids, player.x + dx * 8f, player.y + dy * 8f) }
                if (found != null) {
                    player.x += found.first * 8f
                    player.y += found.second * 8f
                    break
                }
            }
        }
    }

    fun setPlayerLook(look: CharacterLook) = player.setLook(look)

    fun setViewport(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        screenW = widthPx
        screenH = heightPx
        joystick.radius = widthPx * 0.11f
    }

    fun update(dt: Float) {
        time += dt
        player.update(dt, joystick.outX, joystick.outY, map.solids)
        if (player.moving) hasWalked = true
        if (player.stepped) audio?.play(Sfx.STEP, 0.5f, rng.range(0.8f, 1.2f))
        for (n in npcs) n.update(dt, this)
        camera.follow(player.x, player.y, player.vx, player.vy, dt)

        val spot = map.spots.firstOrNull { it.area.contains(player.x, player.y) }
        if (spot !== activeSpot) {
            activeSpot = spot
            promptT = 0f
            bubblePressed = -1L
            if (spot != null) audio?.play(Sfx.BLIP, 0.35f, 1.4f)
        } else {
            promptT += dt
        }
    }

    /** Points the camera's dive at a machine's screen (used for the enter/exit transition). */
    fun setDive(spot: Spot?, amount: Float) {
        if (spot == null || amount <= 0f) {
            camera.dive = 0f
            return
        }
        camera.diveX = spot.focusX
        camera.diveY = spot.focusY
        camera.diveZ = spot.focusZ
        camera.dive = amount
    }

    /** Whether no other kid (and not the player) is using hangout [index]. */
    fun hangoutFree(index: Int, asker: Npc): Boolean {
        if (npcs.any { it !== asker && it.hangout == index }) return false
        val h = map.hangouts[index]
        return !(abs(player.x - h.x) < 20f && abs(player.y - h.z) < 24f)
    }

    /** Breadth-first search over walkable tiles; returns tile indices from start (exclusive) to goal. */
    fun findPath(sx: Int, sy: Int, gx: Int, gy: Int): IntArray? {
        val cols = map.cols
        val rows = map.rows
        if (!map.tileWalkable(gx, gy)) return null
        val start = sy.coerceIn(0, rows - 1) * cols + sx.coerceIn(0, cols - 1)
        val goal = gy * cols + gx
        if (start == goal) return IntArray(0)
        val prev = IntArray(cols * rows) { -2 }
        val queue = IntArray(cols * rows)
        var head = 0
        var tail = 0
        queue[tail++] = start
        prev[start] = -1
        while (head < tail) {
            val cur = queue[head++]
            if (cur == goal) break
            val cx = cur % cols
            val cy = cur / cols
            for (d in 0 until 4) {
                val nx = cx + if (d == 0) 1 else if (d == 1) -1 else 0
                val ny = cy + if (d == 2) 1 else if (d == 3) -1 else 0
                if (!map.tileWalkable(nx, ny)) continue
                val ni = ny * cols + nx
                if (prev[ni] != -2) continue
                prev[ni] = cur
                queue[tail++] = ni
            }
        }
        if (prev[goal] == -2) return null
        val out = ArrayList<Int>()
        var at = goal
        while (at != start && at >= 0) {
            out += at
            at = prev[at]
        }
        out.reverse()
        return out.toIntArray()
    }

    // ---------------------------------------------------------------- input (screen pixels)

    private fun inBubble(x: Float, y: Float): Boolean {
        if (activeSpot == null || bubbleRight <= bubbleLeft) return false
        val pad = 12f
        return x >= bubbleLeft - pad && x <= bubbleRight + pad && y >= bubbleTop - pad && y <= bubbleBottom + pad
    }

    fun pointerDown(id: Long, x: Float, y: Float) {
        if (inBubble(x, y) && bubblePressed < 0) {
            bubblePressed = id
            return
        }
        joystick.down(id, x, y)
    }

    fun pointerMove(id: Long, x: Float, y: Float) {
        joystick.move(id, x, y)
    }

    /** Returns the spot whose prompt was tapped, if this release completes a tap on it. */
    fun pointerUp(id: Long, x: Float, y: Float): Spot? {
        joystick.up(id)
        if (id == bubblePressed) {
            bubblePressed = -1L
            if (inBubble(x, y)) return activeSpot
        }
        return null
    }

    fun cancelInput() {
        joystick.release()
        bubblePressed = -1L
    }
}
