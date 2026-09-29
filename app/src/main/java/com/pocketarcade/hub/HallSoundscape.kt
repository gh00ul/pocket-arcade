package com.pocketarcade.hub

import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.audio.Attract
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.range
import kotlin.math.PI
import kotlin.random.Random

/**
 * What the hall's soundscape needs from the audio system: the listener's ears, where the sources
 * are, and positional one-shots. `AudioSynth` is the real one; tests record.
 */
interface HallSoundSink {
    /** Puts the listener at world point ([x], [z]) facing [yawRad] (0 faces +z; the camera's yaw). */
    fun setListener(x: Float, z: Float, yawRad: Float)

    /** Tells the ambience where the machines are, and which attract-sound palette each has. */
    fun setHallSources(xs: FloatArray, zs: FloatArray, kinds: IntArray, count: Int)

    /** Tells the ambience where the café counter is. */
    fun setCafe(x: Float, z: Float)

    /** How busy the hall is around the listener, 0..1. */
    fun setCrowd(level: Float)

    /** Plays [sfx] from a world point as heard from the listener. */
    fun playAt(sfx: Sfx, x: Float, z: Float, volume: Float = 1f, pitch: Float = 1f)
}

/**
 * The hall as heard by the player. Each frame it puts the listener's ears where the player stands,
 * facing where the camera looks; it tells the ambience where the cabinets and the café are, so their
 * bleeps, steam and clinks come from there; it plays other kids' footsteps and the odd cheer from
 * where they are; and it works out how busy it is around the player, which swells the crowd murmur
 * (louder by the café and among many kids). Voice counts are capped so a full hall stays a bed.
 */
class HallSoundscape(private val world: HubWorld, private val sink: HallSoundSink?) {
    companion object {
        /** A kid's footstep is this loud (the player's own are 0.22-0.5): a soft pattering. */
        const val STEP_VOLUME = 0.14f

        /** Kids further than this (world units) aren't given footsteps at all: they'd be too quiet to hear. */
        const val STEP_RANGE = 200f

        /** At most this many kid footsteps start in one frame (a crowd doesn't all land at once). */
        const val STEPS_PER_FRAME = 3

        /** A far-off kid cheering: a faint "yay" (the game's own is 0.7-1), heard within this range, at most this often (seconds). */
        const val CHEER_VOLUME = 0.09f
        const val CHEER_RANGE = 220f
        const val CHEER_COOLDOWN = 4f

        /** How often the crowd level is re-worked (seconds): it only drifts, so ten times a second is plenty. */
        const val CROWD_INTERVAL = 0.1f

        /** Kids within this of the player count towards the crowd, more the closer they are. */
        const val CROWD_RADIUS = 260f

        /** This many kids' worth of crowd around you is as busy as the kids alone can make it. */
        const val CROWD_FULL = 4f

        /** The café counter's murmur reaches this far, and adds this much at the counter. */
        const val CAFE_RADIUS = 240f
        const val CAFE_WEIGHT = 0.5f

        /** The crowd level of an empty hall, and how much of the rest the kids can add. */
        const val CROWD_FLOOR = 0.15f
        const val CROWD_KIDS = 0.4f

        /** The café's counter: its middle, where the steam wand and the cups are heard from. */
        val CAFE_X = (CafeLayout.LANE_X0 + CafeLayout.LANE_X1) / 2f
        val CAFE_Z = CafeLayout.LANE_Z

        /**
         * How busy it is at a spot with [kidWeight] (kids around, each weighted by nearness) and [cafeDistance] from
         * the café counter: 0 to 1, rising with either.
         */
        fun crowdLevel(kidWeight: Float, cafeDistance: Float): Float {
            val kids = clamp01(kidWeight / CROWD_FULL)
            val cafe = clamp01(1f - cafeDistance / CAFE_RADIUS)
            return clamp01(CROWD_FLOOR + CROWD_KIDS * kids + CAFE_WEIGHT * cafe)
        }

        /**
         * The listener's yaw: overhead the camera looks at the back wall (pi), in first person along the
         * player's gaze, and while the view eases between them so do the ears. [firstPersonAmount] is the
         * camera's eased 0..1 blend, [gazeYaw] its first-person yaw.
         */
        fun listenerYaw(firstPersonAmount: Float, gazeYaw: Float): Float {
            val back = PI.toFloat()
            return HubCamera.wrap(back + HubCamera.wrap(gazeYaw - back) * firstPersonAmount)
        }
    }

    private var lastStep = IntArray(0)
    private var wasCheering = BooleanArray(0)
    private var crowdTimer = 0f
    private var cheerCooldown = 0f
    private val rng = Random(2024)

    /** Tells the ambience where every cabinet and the café are. Call once the map exists, and again if it is rebuilt. */
    fun publish() {
        val s = sink ?: return
        val machines = world.map.props.filter { it.kind == PropKind.MACHINE && it.machine in world.games.indices }
        val xs = FloatArray(machines.size) { machines[it].centerX }
        val zs = FloatArray(machines.size) { machines[it].centerZ }
        val kinds = IntArray(machines.size) { Attract.kindFor(world.games[machines[it].machine].id) }
        s.setHallSources(xs, zs, kinds, machines.size)
        s.setCafe(CAFE_X, CAFE_Z)
        // Until the first frame, the ears are at the doors (so the title's bleeps come from the front of the hall).
        s.setListener(world.player.x, world.player.y, listenerYaw(world.camera.fpAmount, world.camera.yaw))
    }

    /** One frame: the ears, the kids' footsteps and cheers, and every so often the crowd level. */
    fun update(dt: Float) {
        val s = sink ?: return
        val px = world.player.x
        val pz = world.player.y
        s.setListener(px, pz, listenerYaw(world.camera.fpAmount, world.camera.yaw))

        val npcs = world.npcs
        if (lastStep.size != npcs.size) {
            lastStep = IntArray(npcs.size) { (npcs[it].phase / PI.toFloat()).toInt() }
            wasCheering = BooleanArray(npcs.size)
        }
        cheerCooldown -= dt
        var steps = 0
        for (i in npcs.indices) {
            val n = npcs[i]
            val idx = (n.phase / PI.toFloat()).toInt()
            if (idx != lastStep[i]) {
                lastStep[i] = idx
                if (n.state == Npc.State.WALK && steps < STEPS_PER_FRAME && near(n.x, n.y, px, pz, STEP_RANGE)) {
                    steps++
                    s.playAt(Sfx.STEP, n.x, n.y, STEP_VOLUME, rng.range(0.85f, 1.2f))
                }
            }
            val cheering = n.pose == Pose.CHEER
            if (cheering && !wasCheering[i] && cheerCooldown <= 0f && near(n.x, n.y, px, pz, CHEER_RANGE)) {
                cheerCooldown = CHEER_COOLDOWN
                s.playAt(Sfx.CHEER, n.x, n.y, CHEER_VOLUME, rng.range(0.9f, 1.15f))
            }
            wasCheering[i] = cheering
        }

        crowdTimer -= dt
        if (crowdTimer <= 0f) {
            crowdTimer = CROWD_INTERVAL
            var weight = 0f
            for (i in npcs.indices) {
                val d = kotlin.math.hypot(npcs[i].x - px, npcs[i].y - pz)
                if (d < CROWD_RADIUS) weight += 1f - d / CROWD_RADIUS
            }
            s.setCrowd(crowdLevel(weight, kotlin.math.hypot(CAFE_X - px, CAFE_Z - pz)))
        }
    }

    private fun near(x: Float, z: Float, px: Float, pz: Float, range: Float): Boolean {
        val dx = x - px
        val dz = z - pz
        return dx * dx + dz * dz <= range * range
    }
}
