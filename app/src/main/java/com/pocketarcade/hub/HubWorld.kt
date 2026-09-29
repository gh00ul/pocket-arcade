package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.GameSettings
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.len
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.engine.range
import com.pocketarcade.games.MiniGame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The walkable arcade hall: map, player, wandering kids, camera and the prompt the player is
 * standing at. Lives as long as the app so returning from a game puts you back where you were.
 */
class HubWorld(val games: List<MiniGame>, private val audio: AudioSynth?, private val haptics: Haptics? = null) {
    companion object {
        /** First-person look speed: degrees turned per dp dragged sideways... */
        const val LOOK_DEG_PER_DP = 0.3f
        /** ...and up and down this much of it (looking up and down wants a steadier hand). */
        const val LOOK_PITCH_SCALE = 0.7f
        /**
         * The look drag is smoothed over this long (seconds): enough to iron out a finger's
         * pixel jitter, far too short to feel. Every pixel dragged still turns the view in full.
         */
        const val LOOK_SMOOTH = 0.012f
        /** A look-side touch that moves less than this (dp) is a tap, not a turn. */
        const val TAP_SLOP_DP = 10f
        /** A touch held longer than this (seconds) isn't a tap. */
        const val TAP_TIME = 0.35f
        /** While walking with no look finger down, the view levels off after this long (seconds)... */
        const val LEVEL_DELAY = 0.6f
        /** ...easing back to the resting pitch at this rate (per second, at walking pace). */
        const val LEVEL_RATE = 1.4f
        /** After a walk overhead, the kid turns to face the machine at this rate (per second). */
        const val FACE_RATE = 10f
        /** Stepping into a play spot turns the view to its machine at this rate (per second). */
        const val ASSIST_RATE = 7f
        /** The assist only kicks in if you're facing within this of the machine (radians) or stopping. */
        const val ASSIST_CONE = 1.3f
        /**
         * Kids are this round (world units) for bumping into in first person: their big heads,
         * so one beside you never fills the view.
         */
        const val KID_RADIUS = 9f
        /** How much of an overlap with a kid is undone each step: a soft bump, not a wall. */
        const val KID_PUSH = 0.35f
        /** Tap-to-walk stops this far in front of a machine's (or counter's) front. */
        const val STAND_DEPTH = Body.RADIUS + Body.FRONT_GAP + 1f
        /** First-person tap-to-walk ignores floor taps further away than this (the horizon is a long way off). */
        const val TAP_REACH = 700f
        /**
         * A wall bump is the player, still pushing the stick, falling from [WALL_FROM] of the walking
         * speed or more to under [WALL_TO] within two steps (the last step into a wall is shortened,
         * so one step alone can miss it). Only a near head-on stop does that: sliding along a wall
         * keeps most of the speed, and braking (even a full reversal) is far too gradual.
         */
        const val WALL_FROM = 0.5f
        const val WALL_TO = 0.2f

        private const val DEG = PI.toFloat() / 180f

        /**
         * Whether a first-person touch at screen x [x] (of a [width]-wide screen) drags the view
         * rather than the stick: the right half, or the left half for a left-handed player.
         */
        fun isLookSide(x: Float, width: Float, leftHanded: Boolean): Boolean = (x >= width / 2f) != leftHanded

        /** Footstep pitch for walking at [speedFrac] of the walking speed: brisker steps, a touch higher. */
        fun stepPitch(speedFrac: Float): Float = 0.8f + 0.25f * speedFrac.coerceIn(0f, 1.4f)
    }

    var map: HubMap = HubLayout.build(games, emptySet())
        private set
    /** What the first-person body walks among: the map's solids with the play spots' fronts kept clear. */
    var bodySolids: List<Box> = Body.solidsFor(map)
        private set
    val player = Player()
    val npcs = ArrayList<Npc>()
    /** The café queue and the barista. */
    val cafe = CafeLife()
    /** The prize clerk's animation: standing behind the counter, turning to whoever comes up. */
    val clerk = FigureAnim(seed = 5, scale = 1.12f)
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
    /** Becomes true once the player has looked around in first person, which retires its hint. */
    var hasLooked = false
        private set

    /** Screen pixels per dp, for the look speed, the tap slop and the stick's size. */
    var density = 1f

    private var hudBase = 0f
    /** Extra rows of HUD buttons under the first one, in pixels ([hudBottom] includes them). */
    var hudExtra = 0f

    /** Pixels down from the top of the screen that the HUD's buttons cover (the prompt stays below). */
    var hudBottom: Float
        get() = hudBase + hudExtra
        set(v) {
            hudBase = v
        }

    /**
     * The player's options (see [applySettings]): look speed and direction, which hand walks,
     * and the run latch. The camera and the joystick carry the rest.
     */
    var settings = GameSettings()
        private set

    /** The finger dragging the first-person view (the right half of the screen), or -1. */
    var lookPointer = -1L
        private set
    private var lookStartX = 0f
    private var lookStartY = 0f
    private var lookLastX = 0f
    private var lookLastY = 0f
    /** Whether the look finger has moved past the tap slop (only then does the view turn). */
    var lookDragging = false
        private set
    private val move = FloatArray(2)
    private val tmp = FloatArray(2)
    /** Look drag not yet applied to the view (radians), smoothed in over [LOOK_SMOOTH]. */
    private var pendingYaw = 0f
    private var pendingPitch = 0f
    /** Seconds since the view was last dragged. */
    private var sinceLook = 99f
    private var lookDownT = 0f
    private var stickDownT = 0f

    /** Tap-to-walk's route (see [tapToWalk]); [WalkRoute.active] while on the way. */
    val route = WalkRoute()
    private val pickCam = Camera3D()
    /** Overhead: the spot whose machine the kid is turning to face after walking there, or null. */
    private var faceTarget: Spot? = null

    /** The spot whose machine the view is turning to face, or null. */
    var assistSpot: Spot? = null
        private set
    private var assistT = 0f
    /** The spot the view last turned to face (so it only does it once a visit). */
    private var assistedSpot: Spot? = null

    /** For the bumps: the walking speed one and two steps ago, whether the player was stopped, and whether a kid overlapped them. */
    private var speed1 = 0f
    private var speed2 = 0f
    private var wasStopped = true
    private var kidTouching = false

    /** Footsteps played so far, and the last one's pitch. */
    var steps = 0
        private set
    var lastStepPitch = 1f
        private set

    /** Whether first person is on (the camera may still be easing there). */
    val firstPerson: Boolean get() = camera.firstPerson

    private var ownedDecor: Set<DecorStyle> = emptySet()
    private val rng = Random(42)

    init {
        player.x = map.spawnX
        player.y = map.spawnY
        camera.snapTo(player.x, player.y)
        // About as many kids per square metre as before the hall grew; each one is a lot of
        // vertices, and only the ones in view are drawn.
        repeat(16) { i ->
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
        bodySolids = Body.solidsFor(map)
        route.clear()
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

    /** Takes the player's options: the controls, the view and the comfort settings. */
    fun applySettings(s: GameSettings) {
        val v = s.sanitized()
        settings = v
        camera.fovScale = v.fovDeg / HubCamera.FP_FOV_DEG
        camera.bobScale = if (v.reduceMotion) 0f else 1f
        camera.kickScale = if (v.reduceMotion) 0f else 1f
    }

    fun setViewport(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        screenW = widthPx
        screenH = heightPx
        joystick.radius = Joystick.radiusFor(density, widthPx, heightPx)
    }

    fun update(dt: Float) {
        time += dt
        val fp = camera.firstPerson
        if (fp) walkFirstPerson(dt) else walkOverhead(dt)
        if (player.moving) hasWalked = true
        if (player.stepped) footstep(fp)
        feelWalls()
        for (i in npcs.indices) npcs[i].update(dt, this)
        cafe.update(dt, this)
        clerk.update(dt, map.clerkX, map.clerkY, sin(time * 0.4f) * 0.4f, Pose.STAND)
        val gait = if (fp) player.speedFrac else if (player.moving) 1f else 0f
        val run = if (fp) (player.speedFrac - 1f) / (Player.RUN_SCALE - 1f) else 0f
        camera.update(player.x, player.y, player.vx, player.vy, player.moving, player.phase, dt, gait, run)

        var spot: Spot? = null
        val spots = map.spots
        for (i in spots.indices) {
            if (spots[i].area.contains(player.x, player.y)) {
                spot = spots[i]
                break
            }
        }
        if (spot !== activeSpot) {
            activeSpot = spot
            promptT = 0f
            bubblePressed = -1L
            if (spot != null) {
                audio?.play(Sfx.BLIP, 0.35f, 1.4f)
                haptics?.soft()
            }
            if (spot == null) assistedSpot = null
        } else {
            promptT += dt
        }
        if (fp) assist(dt)
    }

    /** Walking into a wall or a cabinet gives a bump: brought to a sudden stop while still pushing on (see [WALL_FROM]). */
    private fun feelWalls() {
        val speed = player.speedFrac
        val stopped = speed < WALL_TO
        val pushed = joystick.outX * joystick.outX + joystick.outY * joystick.outY > 0.25f
        if (pushed && stopped && !wasStopped && maxOf(speed1, speed2) >= WALL_FROM) haptics?.bump()
        wasStopped = stopped
        speed2 = speed1
        speed1 = speed
    }

    /** A footstep: overhead as ever; in first person quieter, and brisker steps sound a touch higher. */
    private fun footstep(fp: Boolean) {
        val pitch: Float
        val volume: Float
        if (fp) {
            val f = player.speedFrac
            pitch = stepPitch(f) + rng.range(-0.05f, 0.05f)
            volume = 0.22f + 0.1f * f.coerceAtMost(1.4f)
        } else {
            pitch = rng.range(0.8f, 1.2f)
            volume = 0.5f
        }
        steps++
        lastStepPitch = pitch
        audio?.play(Sfx.STEP, volume, pitch)
    }

    /**
     * Overhead's step: the stick walks the kid, or the tap-to-walk route does (the stick
     * takes over at once); arriving at a machine turns the kid to face it.
     */
    private fun walkOverhead(dt: Float) {
        var jx = joystick.outX
        var jy = joystick.outY
        if (jx != 0f || jy != 0f) {
            route.clear()
            faceTarget = null
        } else if (route.active) {
            val wasFor = route.spot
            if (route.steer(player.x, player.y, dt, move)) {
                jx = move[0]
                jy = move[1]
            } else if (wasFor != null && wasFor.area.contains(player.x, player.y)) {
                faceTarget = wasFor
            }
        }
        player.update(dt, jx, jy, map.solids)
        val face = faceTarget ?: return
        if (player.moving) {
            faceTarget = null
            return
        }
        val d = HubCamera.wrap(atan2(face.focusX - player.x, face.focusZ - player.y) - player.yaw)
        player.yaw += d * (1f - exp(-dt * FACE_RATE))
        if (abs(d) < 0.02f) faceTarget = null
    }

    /**
     * First person's step: the smoothed look drag, then the walk — the stick (forward where you
     * look, a little slower backwards and sideways, a run at the rim) or the tap-to-walk route —
     * then a soft bump off any kid, and the view levelling off as you walk.
     */
    private fun walkFirstPerson(dt: Float) {
        applyLook(dt)
        val jx = joystick.outX
        val jy = joystick.outY
        if (jx != 0f || jy != 0f) route.clear()
        if (route.active) {
            val wasFor = route.spot
            if (route.steer(player.x, player.y, dt, move)) {
                // Turn to face the way you're being walked, as you would.
                val m = len(move[0], move[1])
                if (m > 0.3f && lookPointer < 0L) {
                    val target = atan2(move[0], move[1])
                    val k = 1f - exp(-dt * 5f)
                    camera.setLook(camera.yaw + HubCamera.wrap(target - camera.yaw) * k, camera.pitch)
                }
            } else if (wasFor != null && wasFor.area.contains(player.x, player.y)) {
                // Arrived: face the machine.
                startAssist(wasFor)
            }
        } else {
            // Stick: up is forward, sideways strafes; backwards and sideways are a little slower
            // and pushing forward at the rim breaks into a run.
            val strafe = jx * Player.STRAFE_SCALE
            var fwd = -jy
            val mag = len(jx, jy)
            fwd *= if (fwd > 0f) {
                val forwardness = if (mag > 0f) fwd / mag else 0f
                1f + (Player.RUN_SCALE - 1f) * joystick.run * forwardness
            } else {
                Player.BACK_SCALE
            }
            HubCamera.moveRelative(strafe, -fwd, camera.yaw, move)
        }
        player.walkFirstPerson(dt, move[0], move[1], bodySolids, camera.yaw)
        bumpKids()
        // Walking along with no finger on the view: let it drift back to level.
        if (lookPointer < 0L && sinceLook > LEVEL_DELAY && player.moving && assistSpot == null) {
            val rest = HubCamera.REST_PITCH_DEG * DEG
            val pace = player.speedFrac.coerceAtMost(1f)
            camera.setLook(camera.yaw, damp(camera.pitch, rest, LEVEL_RATE * pace, dt))
        }
    }

    /** Feeds the look drag into the view, smoothed over [LOOK_SMOOTH]. */
    private fun applyLook(dt: Float) {
        sinceLook += dt
        if (pendingYaw == 0f && pendingPitch == 0f) return
        val k = 1f - exp(-dt / LOOK_SMOOTH)
        var dy = pendingYaw * k
        var dp = pendingPitch * k
        if (abs(pendingYaw - dy) < 1e-5f) dy = pendingYaw
        if (abs(pendingPitch - dp) < 1e-5f) dp = pendingPitch
        pendingYaw -= dy
        pendingPitch -= dp
        camera.look(dy, dp)
    }

    /** Applies any look drag still being smoothed in, at once. */
    private fun flushLook() {
        if (pendingYaw != 0f || pendingPitch != 0f) camera.look(pendingYaw, pendingPitch)
        pendingYaw = 0f
        pendingPitch = 0f
    }

    /** Soft bumps: the player eases out of any kid they walk into, never into a wall. */
    private fun bumpKids() {
        val minD = Body.RADIUS + KID_RADIUS
        var px = player.x
        var py = player.y
        var touching = false
        for (i in npcs.indices) {
            val n = npcs[i]
            val dx = px - n.x
            val dy = py - n.y
            val d2 = dx * dx + dy * dy
            if (d2 >= minD * minD) continue
            touching = true
            val d = sqrt(d2)
            val push = (minD - d) * KID_PUSH
            if (d > 1e-3f) {
                px += dx / d * push
                py += dy / d * push
            } else {
                py += push
            }
        }
        // A bump as you walk into a kid (once per meeting, not while they stay overlapped).
        if (touching && !kidTouching && player.moving) haptics?.bump()
        kidTouching = touching
        if (px == player.x && py == player.y) return
        Body.pushOut(bodySolids, px, py, Body.RADIUS, tmp)
        if (Body.clear(bodySolids, tmp[0], tmp[1])) player.place(tmp[0], tmp[1])
    }

    /**
     * Stepping into a play spot turns the view to its machine — if you walked in roughly facing
     * it (or stopped there) and aren't steering the view yourself.
     */
    private fun assist(dt: Float) {
        val spot = activeSpot
        if (assistSpot == null && spot != null && spot !== assistedSpot && !route.active && lookPointer < 0L && sinceLook > 0.3f) {
            val toMachine = atan2(spot.focusX - player.x, spot.focusZ - player.y)
            val off = abs(HubCamera.wrap(toMachine - camera.yaw))
            if (off < ASSIST_CONE || player.speedFrac < 0.15f) startAssist(spot)
        }
        val a = assistSpot ?: return
        if (lookDragging || route.active || activeSpot !== a) {
            assistSpot = null
            return
        }
        assistT += dt
        val dx = a.focusX - player.x
        val dz = a.focusZ - player.y
        val flat = sqrt(dx * dx + dz * dz)
        if (flat < 1e-3f) {
            assistSpot = null
            return
        }
        val ty = atan2(dx, dz)
        val tp = atan2(a.focusY - HubCamera.EYE_HEIGHT, flat).coerceIn(-20f * DEG, 10f * DEG)
        val k = 1f - exp(-dt * ASSIST_RATE)
        val dYaw = HubCamera.wrap(ty - camera.yaw)
        val dPitch = tp - camera.pitch
        camera.setLook(camera.yaw + dYaw * k, camera.pitch + dPitch * k)
        if ((abs(dYaw) < 0.3f * DEG && abs(dPitch) < 0.3f * DEG) || assistT > 1.5f) assistSpot = null
    }

    private fun startAssist(spot: Spot) {
        assistSpot = spot
        assistedSpot = spot
        assistT = 0f
    }

    /** The prompt spot of machine [index]'s cabinet nearest the player, or null if it has none. */
    fun nearestMachineSpot(index: Int): Spot? {
        var best: Spot? = null
        var bestD = Float.MAX_VALUE
        for (s in map.spots) {
            if (s.type != SpotType.MACHINE || s.machine != index) continue
            val d = abs(s.area.centerX - player.x) + abs(s.area.centerY - player.y)
            if (d < bestD) {
                bestD = d
                best = s
            }
        }
        return best
    }

    /**
     * Points the camera's dive at a machine's screen (used for the enter/exit transition). While
     * fully inside, the first-person view turns to face that machine, so coming back out lands
     * looking at it.
     */
    fun setDive(spot: Spot?, amount: Float) {
        if (spot == null || amount <= 0f) {
            camera.dive = 0f
            return
        }
        camera.diveX = spot.focusX
        camera.diveY = spot.focusY
        camera.diveZ = spot.focusZ
        camera.dive = amount
        if (amount >= 0.999f) faceSpot(spot)
    }

    /** Turns the player (and the first-person view) toward a spot's machine or counter. */
    fun faceSpot(spot: Spot) {
        camera.face(player.x, player.y, spot.focusX, spot.focusY, spot.focusZ)
        player.yaw = camera.yaw
    }

    /**
     * Switches between the overhead camera and first person; [animate] eases the camera between
     * the two, otherwise it cuts. Entering first person looks the way the kid is facing.
     */
    fun setFirstPerson(on: Boolean, animate: Boolean) {
        if (on == camera.firstPerson) {
            if (!animate) camera.setFirstPerson(on, false)
            return
        }
        if (on) camera.setLook(player.yaw, HubCamera.REST_PITCH_DEG * DEG)
        // A finger mid-look or mid-walk belongs to the old controls.
        cancelInput()
        player.halt()
        camera.setFirstPerson(on, animate)
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

    // ---------------------------------------------------------------- tap to walk

    /**
     * Walks to what's under screen pixel ([sx], [sy]) — a machine (anywhere on it) or a counter
     * walks to its play spot and faces it; the floor walks to that point. The tap is projected
     * through whichever camera is showing, the kid's eyes or the overhead view. Returns whether
     * a route was set.
     */
    fun tapToWalk(sx: Float, sy: Float): Boolean {
        if (camera.dive > 0f || screenW <= 0f) return false
        // Halfway between the two views nothing on screen is where it seems to be.
        val fp = camera.firstPerson
        if (if (fp) camera.fpAmount < 0.99f else camera.fpAmount > 0.01f) return false
        camera.apply(pickCam, screenW.toInt(), screenH.toInt())
        val c = pickCam
        val u = (sx - c.cx) / c.focal
        val v = -(sy - c.cy) / c.focal
        val dx = c.fx + c.rx * u + c.ux * v
        val dy = c.fy + c.ry * u + c.uy * v
        val dz = c.fz + c.rz * u + c.uz * v
        var bestT = Float.MAX_VALUE
        var hit: Prop? = null
        val props = map.props
        for (i in props.indices) {
            val p = props[i]
            if (!p.solid) continue
            val t = rayBox(c.ex, c.ey, c.ez, dx, dy, dz, p.x0, p.z0, p.x1, p.height, p.z1)
            if (t > 0f && t < bestT) {
                bestT = t
                hit = p
            }
        }
        val floorT = if (dy < -1e-4f) -c.ey / dy else Float.MAX_VALUE
        if (hit != null && bestT < floorT) {
            val spot = spotOf(hit)
            if (spot != null) return walkTo(spot)
            // Something without a spot (a pillar, a table): walk up to where you tapped it.
            return walkToPoint(c.ex + dx * bestT, c.ez + dz * bestT)
        }
        // From the eyes the floor stretches to the horizon: only what's near counts. The overhead
        // view only ever shows a slice of it, all of it within reach.
        if (fp && floorT > TAP_REACH) return false
        return walkToPoint(c.ex + dx * floorT, c.ez + dz * floorT)
    }

    /** Walks (either camera) to [spot]'s standing point and faces its machine there. */
    fun walkTo(spot: Spot): Boolean {
        val x = spot.area.centerX
        val y = (spot.area.top + STAND_DEPTH).coerceAtMost(spot.area.bottom - 2f)
        if (spot.area.contains(player.x, player.y) && abs(player.x - x) < 4f && abs(player.y - y) < 4f) {
            route.clear()
            startAssist(spot)
            if (!camera.firstPerson) faceTarget = spot
            return true
        }
        return plan(x, y, spot)
    }

    /** Walks (either camera) to the nearest place the body fits by floor point ([x], [z]). */
    fun walkToPoint(x: Float, z: Float): Boolean {
        val r = Body.RADIUS
        val cx = x.coerceIn(HubLayout.WALL + r, HubLayout.WIDTH - HubLayout.WALL - r)
        val cz = z.coerceIn(HubLayout.BACK_WALL + r, HubLayout.FRONT_WALL - r)
        Body.pushOut(bodySolids, cx, cz, r, tmp)
        return plan(tmp[0], tmp[1], null)
    }

    private fun plan(x: Float, y: Float, spot: Spot?): Boolean {
        assistSpot = null
        faceTarget = null
        val ok = route.plan(this, bodySolids, player.x, player.y, x, y, spot)
        if (ok) hasWalked = true
        return ok
    }

    /**
     * The play spot belonging to a prop: a machine's own, the token kiosk's, the prize counter's,
     * or the one generated for an interactive prop ([Spot.prop]).
     */
    internal fun spotOf(p: Prop): Spot? {
        val spots = map.spots
        for (i in spots.indices) {
            val s = spots[i]
            if (s.prop === p) return s
            val mine = when (p.kind) {
                PropKind.MACHINE -> s.type == SpotType.MACHINE && s.machine == p.machine &&
                    abs(s.area.centerX - p.centerX) < 1f && abs(s.area.top - p.z1) < 1f
                PropKind.TOKENS, PropKind.CHANGE -> s.type == SpotType.TOKENS
                PropKind.COUNTER, PropKind.PRIZE_WALL -> s.type == SpotType.PRIZES
                else -> false
            }
            if (mine) return s
        }
        return null
    }

    /** Distance along a ray to an upright box standing on the floor, or -1 if it misses. */
    private fun rayBox(
        ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
        x0: Float, z0: Float, x1: Float, h: Float, z1: Float,
    ): Float {
        var tMin = 0f
        var tMax = Float.MAX_VALUE
        for (axis in 0 until 3) {
            val o = if (axis == 0) ox else if (axis == 1) oy else oz
            val d = if (axis == 0) dx else if (axis == 1) dy else dz
            val lo = if (axis == 0) x0 else if (axis == 1) 0f else z0
            val hi = if (axis == 0) x1 else if (axis == 1) h else z1
            if (abs(d) < 1e-6f) {
                if (o < lo || o > hi) return -1f
            } else {
                var a = (lo - o) / d
                var b = (hi - o) / d
                if (a > b) {
                    val t = a
                    a = b
                    b = t
                }
                if (a > tMin) tMin = a
                if (b < tMax) tMax = b
                if (tMin > tMax) return -1f
            }
        }
        return tMin
    }

    // ---------------------------------------------------------------- input (screen pixels)

    private fun inBubble(x: Float, y: Float): Boolean {
        if (activeSpot == null || bubbleRight <= bubbleLeft) return false
        val pad = 12f
        return x >= bubbleLeft - pad && x <= bubbleRight + pad && y >= bubbleTop - pad && y <= bubbleBottom + pad
    }

    /**
     * A finger went down. The prompt bubble takes it first. Overhead, anywhere else starts the
     * floating joystick; in first person the left half does, and the right half drags the view
     * (the other way round for a left-handed player).
     */
    fun pointerDown(id: Long, x: Float, y: Float) {
        if (inBubble(x, y) && bubblePressed < 0) {
            bubblePressed = id
            return
        }
        if (camera.firstPerson && isLookSide(x, screenW, settings.leftHanded)) {
            if (lookPointer < 0L) {
                lookPointer = id
                lookStartX = x
                lookStartY = y
                lookLastX = x
                lookLastY = y
                lookDragging = false
                lookDownT = time
            }
            return
        }
        if (!joystick.active) {
            joystick.radius = Joystick.radiusFor(density, screenW, screenH)
            // Only running (first person) has a rim to lock; overhead the stick is as it always was.
            joystick.runLatch = settings.runLatch && camera.firstPerson
            stickDownT = time
        }
        joystick.down(id, x, y)
    }

    fun pointerMove(id: Long, x: Float, y: Float) {
        if (id == lookPointer) {
            val slop = TAP_SLOP_DP * density
            if (!lookDragging) {
                val dx = x - lookStartX
                val dy = y - lookStartY
                if (dx * dx + dy * dy > slop * slop) lookDragging = true
            }
            if (lookDragging) {
                // Drag right to turn right, up to look up; the same angle per dp on any screen.
                // Steering the view yourself stops a walk to a machine and the turn to face one.
                val k = LOOK_DEG_PER_DP * DEG / density.coerceAtLeast(0.1f) * settings.lookScale
                pendingYaw += -(x - lookLastX) * k
                pendingPitch += -(y - lookLastY) * k * LOOK_PITCH_SCALE * (if (settings.invertY) -1f else 1f)
                lookLastX = x
                lookLastY = y
                hasLooked = true
                sinceLook = 0f
                route.clear()
                assistSpot = null
            }
            return
        }
        joystick.move(id, x, y)
    }

    /**
     * Returns the spot whose prompt was tapped, if this release completes a tap on it. A tap
     * anywhere else (either half, either camera) walks you to what you tapped ([tapToWalk]).
     */
    fun pointerUp(id: Long, x: Float, y: Float): Spot? {
        var tap = false
        if (id == lookPointer) {
            tap = !lookDragging && time - lookDownT < TAP_TIME
            lookPointer = -1L
            lookDragging = false
        }
        if (id == joystick.pointerId && joystick.active) {
            tap = joystick.travel < TAP_SLOP_DP * density && time - stickDownT < TAP_TIME
        }
        joystick.up(id)
        if (id == bubblePressed) {
            bubblePressed = -1L
            if (inBubble(x, y)) return activeSpot
            return null
        }
        if (tap) tapToWalk(x, y)
        return null
    }

    /** Lets go of every finger: the hall lost focus (a pause, a machine, a dialog). */
    fun cancelInput() {
        joystick.release()
        bubblePressed = -1L
        lookPointer = -1L
        lookDragging = false
        flushLook()
        route.clear()
        assistSpot = null
        faceTarget = null
    }
}
