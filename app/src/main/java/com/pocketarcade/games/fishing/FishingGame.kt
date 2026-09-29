package com.pocketarcade.games.fishing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.easeOutCubic
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.hub.CabinetDesign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Difficulty and payout knobs for fishing. */
object FishingTuning {
    const val ROUND_SECONDS = 55f

    // Casting.
    /** Seconds for the power meter to fill and empty again while the cast is held. */
    const val CHARGE_PERIOD = 1.4f
    /** Cast distance (world units from the dock) at no power and at full power. */
    const val MIN_CAST = 90f
    const val MAX_CAST = 470f
    /** Widest aim either side of straight out, in degrees (finger at the field's edge). */
    const val MAX_AIM_DEG = 38f
    /** Seconds the lure flies: a base plus a little per unit of distance. */
    const val FLIGHT_BASE = 0.35f
    const val FLIGHT_PER_UNIT = 0.0011f
    /** Fish this close to the splash dart away for a moment. */
    const val SPLASH_SPOOK_RADIUS = 18f

    // Bites.
    /** How far a fish notices a lure from. */
    const val NOTICE_RADIUS = 120f
    /** Nibbles before the bite, and the gaps between them. */
    const val NIBBLES_MIN = 1
    const val NIBBLES_MAX = 3
    const val NIBBLE_GAP_MIN = 0.45f
    const val NIBBLE_GAP_MAX = 0.85f
    /** Seconds the fish holds the lure under: crank in that time or it lets go. */
    const val STRIKE_WINDOW = 1f
    /** Radians of reel-in that set the hook (also what counts as striking too soon). */
    const val STRIKE_ANGLE = 0.6f
    /** Seconds a spooked fish stays away from lures. */
    const val SPOOK_SECONDS = 5f
    /** A lure landing this near the old boot snags it, after [BOOT_SNAG_DELAY]. */
    const val BOOT_SNAG_RADIUS = 40f
    const val BOOT_SNAG_DELAY = 1f
    const val BOOT_WINDOW = 1.4f
    /** A suitor that hasn't reached the lure after this long loses interest. */
    const val APPROACH_TIMEOUT = 6f

    // Reel and line.
    /** Crank speed (radians per second) of a brisk, steady wind: 1.0 on the tension scale. */
    const val CRANK_REF = 10f
    /** Line wound in per radian of crank, and how much a heavy fish slows that per pound. */
    const val REEL_PER_RAD = 12f
    const val REEL_WEIGHT_K = 0.08f
    /** Lure pulled in per radian while no fish is on. */
    const val RETRIEVE_PER_RAD = 9f
    /** Line given back per radian of cranking backwards. */
    const val LET_OUT_PER_RAD = 8f
    /** A fish this near the dock is landed. */
    const val LAND_DIST = 50f

    // Tension: 0 is a slack line, 1 snaps it.
    const val REST_TENSION = 0.14f
    const val RUN_TENSION = 0.25f
    const val RUN_PULL_TENSION = 0.8f
    const val BOOT_TENSION = 0.3f
    /** Tension added per unit of crank speed (see [CRANK_REF]). */
    const val CRANK_TENSION = 0.45f
    const val LET_OUT_RELIEF = 0.5f
    /** How fast the tension follows its target (per second). */
    const val TENSION_RATE = 6f
    /** Jolt at the start of every run, times the fish's pull. */
    const val YANK = 0.25f
    const val SNAP_TENSION = 1f
    /** Seconds over [SNAP_TENSION] before the line goes. */
    const val SNAP_GRACE = 0.35f
    const val SLACK_TENSION = 0.2f
    /** Seconds of slack before the fish throws the hook. */
    const val SLACK_GRACE = 1.2f
    /** Top of the green band on the gauge. */
    const val SAFE_HIGH = 0.85f

    // The fight's rhythm: rest, a warning thrash, then a run.
    const val FIRST_REST = 0.9f
    const val REST_MIN = 0.9f
    const val REST_MAX = 1.8f
    const val WARN_SECONDS = 0.5f
    const val RUN_MIN = 0.55f
    const val RUN_MAX = 1.1f
    /** Line a resting fish drifts out per second, times its pull. */
    const val REST_DRIFT = 8f
    /** Runs sway the fish sideways this fast (radians per second). */
    const val RUN_SWAY = 0.35f
    const val MAX_SWAY = 0.75f
    /** Each run leaves the fish this much of its strength. */
    const val STAMINA_DECAY = 0.82f
    /** Failsafe: a fight still going this long ends with the fish getting away. */
    const val FIGHT_TIMEOUT = 40f
    const val LAND_SECONDS = 0.9f
    const val RECOVER_SECONDS = 0.5f
    const val RESPAWN_SECONDS = 1.5f
    /** Failsafe: anything still busy this long after time-up is packed away. */
    const val SETTLE_FAILSAFE = 4f

    // Species: perch, bass, catfish, golden carp, and junk (the old boot).
    val NAMES = arrayOf("PERCH", "BASS", "CATFISH", "GOLDEN CARP", "OLD BOOT")
    val LENGTH = floatArrayOf(16f, 24f, 34f, 20f, 14f)
    val SPEED = floatArrayOf(36f, 30f, 20f, 50f, 0f)
    val WEIGHT_MIN = floatArrayOf(0.4f, 1.5f, 4f, 1f, 1f)
    val WEIGHT_MAX = floatArrayOf(1f, 3.5f, 8f, 2f, 1f)
    /** Points per pound. A catch scores weight × value. */
    val VALUE = floatArrayOf(18f, 12f, 9f, 60f, 3f)
    /** How hard each species pulls on a run (0..1) and how fast it takes line. */
    val PULL = floatArrayOf(0.25f, 0.4f, 0.55f, 0.5f, 0f)
    val RUN_SPEED = floatArrayOf(40f, 60f, 75f, 90f, 0f)
    /** Chance per second a fish near the lure goes for it. */
    val APPETITE = floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0f)
    /** Relative odds of each species when a fish swims into the pond. */
    val SPAWN_ODDS = floatArrayOf(45f, 33f, 16f, 6f, 0f)
    /** Fish swimming in the pond at once (the boot comes on top). */
    const val FISH_SLOTS = 10

    const val POINTS_PER_TICKET = 10
    const val BASE_TICKETS = 2
    /** A catch worth this much gets the big celebration. */
    const val BIG_CATCH = 50
}

/** What the player's line is doing. */
internal enum class CastPhase { IDLE, CHARGE, FLIGHT, WAIT, FIGHT, LANDING, RECOVER }

/** What one fish is doing. */
internal enum class FishMode { SWIM, APPROACH, NIBBLE, BITE, HOOKED, LANDED, GONE }

/** The rhythm of a hooked fish: resting, thrashing (a warning), then running with the line. */
internal enum class Pull { REST, WARN, RUN }

/**
 * The reel's crank: turns one finger drawing circles round the reel's centre into how far and
 * how fast the reel turns. Each sample's angle round ([cx], [cy]) is compared with the last one
 * from the same pointer; clockwise on screen (y down) is positive and reels in. Samples nearer
 * the hub than [hubRadius] are skipped, since the angle there is mostly finger jitter.
 */
internal class Crank(val cx: Float, val cy: Float, private val hubRadius: Float) {
    companion object {
        /** Share of each new sample's speed blended into [rate]. */
        const val SMOOTH = 0.35f
        /** Seconds without a sample before the reel spins down. */
        const val STALE = 0.07f
        const val SPIN_DOWN = 14f
        /** Shortest gap between samples used for a speed, and the fastest believable spin. */
        const val MIN_GAP_MS = 4L
        const val MAX_RATE = 60f
    }

    /** The pointer holding the crank, or -1. */
    var id = -1L
        private set
    /** Smoothed turning speed in radians per second, positive clockwise. */
    var rate = 0f
        private set
    /** Total radians turned (for drawing the handle). */
    var angle = 0f
        private set
    private var pending = 0f
    private var lastA = 0f
    private var lastMs = 0L
    private var hasA = false
    private var idle = 0f

    val held: Boolean get() = id >= 0

    /** Takes hold with pointer [pid]; false if another finger already has it. */
    fun grab(pid: Long, x: Float, y: Float, ms: Long): Boolean {
        if (id >= 0) return false
        id = pid
        hasA = false
        idle = 0f
        sample(x, y, ms)
        return true
    }

    fun move(pid: Long, x: Float, y: Float, ms: Long) {
        if (id >= 0 && pid == id) sample(x, y, ms)
    }

    fun release(pid: Long) {
        if (id >= 0 && pid == id) {
            id = -1L
            hasA = false
        }
    }

    /** Lets go at once and forgets any turning not yet taken. */
    fun cancel() {
        id = -1L
        hasA = false
        rate = 0f
        pending = 0f
    }

    /** Radians turned since the last call (positive reels in). */
    fun take(): Float {
        val p = pending
        pending = 0f
        return p
    }

    /** Advances [dt] seconds of game time: a still or released crank spins down. */
    fun age(dt: Float) {
        idle += dt
        if (id < 0 || idle > STALE) rate *= exp(-SPIN_DOWN * dt)
        if (abs(rate) < 1e-3f) rate = 0f
    }

    private fun sample(x: Float, y: Float, ms: Long) {
        val dx = x - cx
        val dy = y - cy
        if (dx * dx + dy * dy < hubRadius * hubRadius) {
            // Too near the hub to tell which way the finger is going: start afresh after it.
            hasA = false
            return
        }
        val a = atan2(dy, dx)
        if (hasA) {
            var d = a - lastA
            if (d > PI.toFloat()) d -= TAU else if (d < -PI.toFloat()) d += TAU
            val gap = (ms - lastMs).coerceAtLeast(MIN_GAP_MS) / 1000f
            val inst = (d / gap).coerceIn(-MAX_RATE, MAX_RATE)
            rate += (inst - rate) * SMOOTH
            pending += d
            angle += d
            idle = 0f
        }
        lastA = a
        lastMs = ms
        hasA = true
    }
}

/**
 * Gone Fishing: hold on the pond to wind up a cast (the power meter swings up and down, the
 * finger's position aims), let go to cast. Fish swim about the pond, notice the lure, nibble and
 * then bite: crank the reel within the strike window to set the hook. Then wind the fish in by
 * drawing circles on the reel, easing off when it runs so the line doesn't snap and keeping it
 * tight when it rests so it doesn't throw the hook. A landed fish scores weight × species value.
 */
class FishingGame : BaseMiniGame() {
    override val id = "fishing"
    override val title = "GONE FISHING"
    override val marquee = "FISH"
    override val instructions = listOf(
        "HOLD ON THE POND TO CAST",
        "LET GO AT THE RIGHT POWER",
        "BOBBER DIVES? CRANK FAST!",
        "CIRCLE THE REEL TO REEL IN",
        "EASE OFF WHEN IT PULLS!",
    )
    override val look = CabinetLook(body = Pal.TEAL, trim = Pal.YELLOW, glow = Pal.SKY, shape = CabinetShape.FISHING)
    override val cabinet: CabinetDesign get() = FishingCabinet
    override val roundSeconds = FishingTuning.ROUND_SECONDS

    internal companion object {
        // The pond, in world units (y up, z towards the viewer; the water is at y = 0).
        const val POND_CX = 180f
        const val POND_CZ = 270f
        const val POND_RX = 200f
        const val POND_RZ = 290f
        const val BED_Y = -46f
        /** Where the line meets the water below the rod: the dock's edge. */
        const val DOCK_X = 180f
        const val DOCK_Z = 548f
        /** Casts land at least this far inside the bank. */
        const val EDGE_MARGIN = 14f
        // The reel on screen, in field units.
        const val REEL_CX = 266f
        const val REEL_CY = 552f
        const val REEL_R = 58f
        /** A finger landing this near the reel's centre takes the crank. */
        const val REEL_GRAB_R = 92f
        const val REEL_HUB_R = 16f
        /** The old boot's slot, after the fish. */
        const val BOOT = FishingTuning.FISH_SLOTS
        const val BOOT_SPECIES = 4
        const val SLOTS = FishingTuning.FISH_SLOTS + 1
        const val RIPPLES = 16
        const val PADS = 8
        /** Radians of crank per reel click. */
        const val REEL_TICK = 0.9f
        const val SEP_RADIUS = 28f
        const val FLOCK_RADIUS = 70f

        // Look (presentation only): the low sun, and how many of each little effect there are.
        const val SUN_X = 330f
        const val SUN_Y = 178f
        const val SUN_Z = -440f
        const val SPLASHES = 3
        const val SPLASH_LIFE = 0.8f
        const val DROPLETS = 9
        const val FIREFLIES = 12
        const val GLITTER = 26
        /** Strengths of the sun's halo, the beams of light and the mist over the far water. */
        const val HALO_ALPHA = 0.5f
        const val SHAFT_ALPHA = 0.11f
        const val MIST_ALPHA = 0.17f

        val SPLASH_COLORS = intArrayOf(Pal.WHITE, Pal.CYAN, Pal.SKY, 0xFFBFF4FF.toInt())
        val GOLD_COLORS = intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE, Pal.ORANGE)
        val ATTRACT_FISH = intArrayOf(Pal.ORANGE, 0xFF6E8A2A.toInt(), Pal.GOLD)
        /** The grass square the pond is cut out of. */
        const val BANK_X0 = -480f
        const val BANK_X1 = 840f
        const val BANK_Z0 = -370f
        const val BANK_Z1 = 910f
    }

    /** One fish (or the boot) in the pond's fixed pool. */
    private class Fish {
        var species = 0
        var mode = FishMode.GONE
        var x = 0f
        var y = -20f
        var z = 0f
        var vx = 0f
        var vz = 0f
        var heading = 0f
        var weight = 1f
        var scale = 1f
        var wx = 0f
        var wz = 0f
        var wanderT = 0f
        var spookT = 0f
        var fleeX = 0f
        var fleeZ = 0f
        /** Timer for the current mode (nibble gaps, the bite window, approach, respawn). */
        var modeT = 0f
        var nibbles = 0
        var depthPhase = 0f
        var wiggle = 0f
    }

    private val fish = Array(SLOTS) { Fish() }

    private var phase = CastPhase.IDLE
    private var phaseT = 0f

    // The cast being wound up.
    private var castId = -1L
    private var aimFx = GAME_W / 2f
    private var chargeT = 0f
    private var power = 0f

    // The lure: in flight, floating, or dragged about by a fish.
    private var lureX = DOCK_X
    private var lureY = 0f
    private var lureZ = DOCK_Z
    private var fromX = 0f
    private var fromY = 0f
    private var fromZ = 0f
    private var toX = 0f
    private var toZ = 0f
    private var flightDur = 1f
    private var suitor = -1
    private var strikeAcc = 0f
    private var earlyAcc = 0f
    private var bootIgnored = false
    private var bobDip = 0f

    // The fight.
    private var hooked = -1
    private var tension = 0f
    private var pull = Pull.REST
    private var pullT = 0f
    private var runDir = 1f
    private var stamina = 1f
    private var lineD = 0f
    private var lineAng = 0f
    private var strainT = 0f
    private var slackT = 0f
    private var fightT = 0f
    private var landX = 0f
    private var landY = 0f
    private var landZ = 0f

    private val crank = Crank(REEL_CX, REEL_CY, REEL_HUB_R)
    private var tickAcc = 0f
    private var snapFlash = 0f
    private var settleT = 0f
    private var riseT = 0f
    private var lastCatch = 0
    private var catchName = ""
    private var catchShowT = 0f

    // Tallies for the tests and the round's story.
    private var casts = 0
    private var landed = 0
    private var snaps = 0
    private var thrown = 0
    private var missed = 0
    private var tooSoon = 0
    private var failsafeTrips = 0

    // Ripples on the water.
    private val rx = FloatArray(RIPPLES)
    private val rz = FloatArray(RIPPLES)
    private val rAge = FloatArray(RIPPLES)
    private val rLife = FloatArray(RIPPLES)
    private val rSize = FloatArray(RIPPLES)
    private val rOn = BooleanArray(RIPPLES)

    // Presentation only: splash sprays. Written by the game's splash events, read by render();
    // a spray's droplets fly from hashes of its seed, never from the game's random numbers.
    private val splashX = FloatArray(SPLASHES)
    private val splashZ = FloatArray(SPLASHES)
    private val splashAge = FloatArray(SPLASHES) { SPLASH_LIFE }
    private val splashPower = FloatArray(SPLASHES)
    private val splashSeed = IntArray(SPLASHES)
    private var splashNext = 0
    private var splashCount = 0

    // Lily pads (placed per round; they only decorate).
    private val padX = FloatArray(PADS)
    private val padZ = FloatArray(PADS)
    private val padS = FloatArray(PADS)
    private val padA = FloatArray(PADS)

    // ---------------------------------------------------------------- camera and fixed points

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 290f, 800f, 180f, 0f, 250f, fovDeg = 48f)
    }
    private val pt = FloatArray(3)

    /** The rod: butt near the viewer's hands at the bottom right, tip up over the dock. */
    private val buttX: Float
    private val buttY = 196f
    private val buttZ: Float
    private val tipX: Float
    private val tipY = 214f
    private val tipZ: Float
    /** Where a landed fish is held up for the camera. */
    private val showX: Float
    private val showY = 120f
    private val showZ: Float

    init {
        val o = FloatArray(3)
        stage.touchToPlane(330f, 700f, buttY, o)
        buttX = o[0]; buttZ = o[1]
        stage.touchToPlane(204f, 236f, tipY, o)
        tipX = o[0]; tipZ = o[1]
        stage.touchToPlane(180f, 250f, showY, o)
        showX = o[0]; showZ = o[1]
    }

    // ---------------------------------------------------------------- round flow

    override fun reset() {
        phase = CastPhase.IDLE
        phaseT = 0f
        castId = -1L
        aimFx = GAME_W / 2f
        chargeT = 0f
        power = 0f
        lureX = DOCK_X; lureY = 0f; lureZ = DOCK_Z
        splashAge.fill(SPLASH_LIFE)
        suitor = -1
        strikeAcc = 0f
        earlyAcc = 0f
        bootIgnored = false
        bobDip = 0f
        hooked = -1
        tension = 0f
        pull = Pull.REST
        pullT = 0f
        stamina = 1f
        lineD = 0f
        lineAng = 0f
        strainT = 0f
        slackT = 0f
        fightT = 0f
        crank.cancel()
        tickAcc = 0f
        snapFlash = 0f
        settleT = 0f
        riseT = 1f
        lastCatch = 0
        catchShowT = 0f
        casts = 0; landed = 0; snaps = 0; thrown = 0; missed = 0; tooSoon = 0; failsafeTrips = 0
        rOn.fill(false)
        for (i in 0 until FishingTuning.FISH_SLOTS) spawnFish(fish[i], anywhere = true)
        placeBoot(fish[BOOT])
        for (i in 0 until PADS) {
            // Pads float round the edges, clear of the dock.
            val a = rng.range(PI.toFloat() * 1.05f, PI.toFloat() * 1.95f) + if (i % 2 == 0) 0f else rng.range(-1.2f, 1.2f)
            val d = rng.range(0.72f, 0.9f)
            padX[i] = POND_CX + cos(a) * POND_RX * d
            padZ[i] = (POND_CZ + sin(a) * POND_RZ * d).coerceAtMost(DOCK_Z - 90f)
            padS[i] = rng.range(18f, 30f)
            padA[i] = rng.range(0f, TAU)
        }
    }

    override fun ticketsFor(score: Int): Int = FishingTuning.BASE_TICKETS + score / FishingTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = phase == CastPhase.IDLE

    /**
     * Time's up: nothing new is cast or hooked. A fish already being lifted out finishes and
     * scores; a fish still fighting gets away and the line is wound in.
     */
    override fun onTimeUp() {
        castId = -1L
        when (phase) {
            CastPhase.CHARGE -> phase = CastPhase.IDLE
            CastPhase.FLIGHT, CastPhase.WAIT -> {
                releaseSuitor()
                recover()
            }
            CastPhase.FIGHT -> {
                val f = fish[hooked]
                spook(f, DOCK_X, DOCK_Z, FishingTuning.SPOOK_SECONDS)
                hooked = -1
                stage.toField(f.x, 0f, f.z, pt)
                popups.add("TIME!", pt[0], pt[1] - 20f, Color(Pal.GRAY), size = 3f)
                recover()
            }
            CastPhase.IDLE, CastPhase.LANDING, CastPhase.RECOVER -> Unit
        }
    }

    /** Lets go of the cast being wound up (no cast is made) and of the reel. */
    override fun cancelInput() {
        castId = -1L
        if (phase == CastPhase.CHARGE) {
            phase = CastPhase.IDLE
            power = 0f
        }
        crank.cancel()
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> {
                val dx = x - REEL_CX
                val dy = y - REEL_CY
                if (dx * dx + dy * dy <= REEL_GRAB_R * REEL_GRAB_R) {
                    crank.grab(id, x, y, timeMs)
                } else if (phase == CastPhase.IDLE && castId < 0 && !timeUp) {
                    castId = id
                    aimFx = x
                    chargeT = 0f
                    power = 0f
                    phase = CastPhase.CHARGE
                    play(Sfx.BLIP, 0.35f, 0.8f)
                }
            }
            TouchType.MOVE -> {
                if (id == castId) aimFx = x
                crank.move(id, x, y, timeMs)
            }
            TouchType.UP -> {
                if (id == castId) {
                    aimFx = x
                    castId = -1L
                    releaseCast()
                }
                crank.release(id)
            }
        }
    }

    private fun releaseCast() {
        if (phase != CastPhase.CHARGE) return
        if (timeUp) {
            phase = CastPhase.IDLE
            return
        }
        landingSpot(aimFx, power, pt)
        toX = pt[0]
        toZ = pt[1]
        fromX = tipX; fromY = tipY; fromZ = tipZ
        lureX = fromX; lureY = fromY; lureZ = fromZ
        val dist = hypot(toX - DOCK_X, toZ - DOCK_Z)
        flightDur = FishingTuning.FLIGHT_BASE + dist * FishingTuning.FLIGHT_PER_UNIT
        phase = CastPhase.FLIGHT
        phaseT = 0f
        casts++
        play(Sfx.CAST, 0.85f, 0.85f + power * 0.4f)
        fx.haptics.tick()
    }

    /** Where a cast aimed by a finger at field x [fx] with [pow] power lands: (x, z) into [out]. */
    private fun landingSpot(fx: Float, pow: Float, out: FloatArray) {
        val a = aimAngle(fx)
        val dist = FishingTuning.MIN_CAST + clamp01(pow) * (FishingTuning.MAX_CAST - FishingTuning.MIN_CAST)
        val dx = sin(a)
        val dz = -cos(a)
        val d = minOf(dist, reach(dx, dz) - EDGE_MARGIN)
        out[0] = DOCK_X + dx * d
        out[1] = DOCK_Z + dz * d
    }

    private fun aimAngle(fx: Float): Float =
        ((fx - GAME_W / 2f) / 160f).coerceIn(-1f, 1f) * FishingTuning.MAX_AIM_DEG * (PI.toFloat() / 180f)

    /** Distance from the dock to the bank along direction ([dx], [dz]) (a unit vector). */
    private fun reach(dx: Float, dz: Float): Float {
        val px = (DOCK_X - POND_CX) / POND_RX
        val pz = (DOCK_Z - POND_CZ) / POND_RZ
        val ux = dx / POND_RX
        val uz = dz / POND_RZ
        val a = ux * ux + uz * uz
        val b = 2f * (px * ux + pz * uz)
        val c = px * px + pz * pz - 1f
        val disc = b * b - 4f * a * c
        if (disc <= 0f || a <= 0f) return 0f
        return ((-b + sqrt(disc)) / (2f * a)).coerceAtLeast(0f)
    }

    // ---------------------------------------------------------------- simulation

    override fun step(dt: Float) {
        crank.age(dt)
        val turn = crank.take()
        reelClicks(turn)
        bobDip = (bobDip - dt * 2.5f).coerceAtLeast(0f)
        snapFlash = (snapFlash - dt * 2f).coerceAtLeast(0f)
        catchShowT = (catchShowT - dt).coerceAtLeast(0f)
        stepRipples(dt)
        for (i in 0 until SPLASHES) if (splashAge[i] < SPLASH_LIFE) splashAge[i] += dt
        if (timeUp) {
            settleT += dt
            if (settleT > FishingTuning.SETTLE_FAILSAFE && phase != CastPhase.IDLE) {
                failsafeTrips++
                if (hooked >= 0 && fish[hooked].mode != FishMode.GONE) spook(fish[hooked], DOCK_X, DOCK_Z, 1f)
                hooked = -1
                releaseSuitor()
                phase = CastPhase.IDLE
            }
        }
        when (phase) {
            CastPhase.IDLE -> Unit
            CastPhase.CHARGE -> {
                chargeT += dt
                val f = chargeT / FishingTuning.CHARGE_PERIOD
                power = 1f - abs(1f - 2f * (f - floor(f)))
            }
            CastPhase.FLIGHT -> stepFlight(dt)
            CastPhase.WAIT -> stepWait(dt, turn)
            CastPhase.FIGHT -> stepFight(dt, turn)
            CastPhase.LANDING -> {
                phaseT += dt
                if (phaseT >= FishingTuning.LAND_SECONDS) scoreCatch()
            }
            CastPhase.RECOVER -> {
                phaseT += dt
                val k = clamp01(phaseT / FishingTuning.RECOVER_SECONDS)
                lureX = fromX + (tipX - fromX) * k
                lureY = fromY + (tipY - 30f - fromY) * k
                lureZ = fromZ + (tipZ - fromZ) * k
                if (phaseT >= FishingTuning.RECOVER_SECONDS) phase = CastPhase.IDLE
            }
        }
        stepFish(dt)
        // Now and then a fish rises and rings the surface, showing where they are.
        riseT -= dt
        if (riseT <= 0f) {
            riseT = rng.range(0.8f, 1.8f)
            val i = rng.nextInt(FishingTuning.FISH_SLOTS)
            val f = fish[i]
            if (f.mode == FishMode.SWIM) addRipple(f.x, f.z, 16f + f.scale * 6f, 1.3f)
        }
    }

    /** The reel clicks as it turns, faster clicks pitched higher. */
    private fun reelClicks(turn: Float) {
        tickAcc += abs(turn)
        if (tickAcc >= REEL_TICK) {
            tickAcc = tickAcc.rem(REEL_TICK)
            val busy = phase == CastPhase.WAIT || phase == CastPhase.FIGHT
            val speed = (abs(crank.rate) / FishingTuning.CRANK_REF).coerceAtMost(2f)
            play(Sfx.REEL, if (busy) 0.45f else 0.25f, 0.7f + speed * 0.45f)
            // The click under your thumb, while a lure is out (a spinning empty reel stays quiet).
            if (busy) fx.haptics.soft()
        }
    }

    private fun stepFlight(dt: Float) {
        phaseT += dt
        val k = clamp01(phaseT / flightDur)
        val dist = hypot(toX - DOCK_X, toZ - DOCK_Z)
        val arc = 50f + dist * 0.3f
        lureX = fromX + (toX - fromX) * k
        lureZ = fromZ + (toZ - fromZ) * k
        lureY = fromY * (1f - k) + 4f * k * (1f - k) * arc
        if (k >= 1f) splashDown()
    }

    private fun splashDown() {
        phase = CastPhase.WAIT
        phaseT = 0f
        lureX = toX; lureY = 0f; lureZ = toZ
        suitor = -1
        strikeAcc = 0f
        earlyAcc = 0f
        bootIgnored = false
        addRipple(lureX, lureZ, 44f, 1.2f)
        addRipple(lureX, lureZ, 24f, 0.8f)
        splashAt(lureX, lureZ, 1f)
        if (stage.toField(lureX, 0f, lureZ, pt)) {
            particles.burst(pt[0], pt[1], 16, 40f, 150f, SPLASH_COLORS, 0.55f, 3.5f, grav = 380f, angleFrom = PI.toFloat() * 1.1f, angleTo = PI.toFloat() * 1.9f)
        }
        play(Sfx.SPLASH, 0.75f, 1.1f)
        val r2 = FishingTuning.SPLASH_SPOOK_RADIUS * FishingTuning.SPLASH_SPOOK_RADIUS
        for (i in 0 until FishingTuning.FISH_SLOTS) {
            val f = fish[i]
            if (f.mode != FishMode.SWIM) continue
            val dx = f.x - lureX
            val dz = f.z - lureZ
            if (dx * dx + dz * dz < r2) spook(f, lureX, lureZ, 1.2f)
        }
        // Landing next to the old boot snags it.
        val b = fish[BOOT]
        if (b.mode == FishMode.SWIM && hypot(b.x - lureX, b.z - lureZ) < FishingTuning.BOOT_SNAG_RADIUS) {
            suitor = BOOT
            b.mode = FishMode.NIBBLE
            b.nibbles = 0
            b.modeT = FishingTuning.BOOT_SNAG_DELAY
        }
    }

    private fun stepWait(dt: Float, turn: Float) {
        phaseT += dt
        earlyAcc = (earlyAcc - dt * 0.8f).coerceAtLeast(0f)
        val s = suitor
        val biting = s >= 0 && fish[s].mode == FishMode.BITE
        if (biting) {
            val f = fish[s]
            if (turn > 0f) strikeAcc += turn
            if (strikeAcc >= FishingTuning.STRIKE_ANGLE) {
                hook(s)
                return
            }
            f.modeT -= dt
            if (f.modeT <= 0f) missStrike(s)
            return
        }
        if (turn > 0f) {
            // Winding in with nothing on: the lure comes back (too soon if a fish is nibbling).
            if (s >= 0 && fish[s].mode == FishMode.NIBBLE) {
                earlyAcc += turn
                if (earlyAcc >= FishingTuning.STRIKE_ANGLE) {
                    strikeTooSoon(s)
                }
            }
            val dx = DOCK_X - lureX
            val dz = DOCK_Z - lureZ
            val d = hypot(dx, dz)
            val step = turn * FishingTuning.RETRIEVE_PER_RAD
            if (d - step <= FishingTuning.LAND_DIST * 0.8f) {
                lureOut()
                return
            }
            lureX += dx / d * step
            lureZ += dz / d * step
        }
        if (suitor < 0) {
            findSuitor(dt)
            return
        }
        val f = fish[suitor]
        when (f.mode) {
            FishMode.APPROACH -> {
                f.modeT += dt
                if (hypot(f.x - lureX, f.z - lureZ) < 9f) {
                    f.mode = FishMode.NIBBLE
                    f.nibbles = FishingTuning.NIBBLES_MIN + rng.nextInt(FishingTuning.NIBBLES_MAX - FishingTuning.NIBBLES_MIN + 1)
                    f.modeT = rng.range(FishingTuning.NIBBLE_GAP_MIN, FishingTuning.NIBBLE_GAP_MAX)
                } else if (f.modeT > FishingTuning.APPROACH_TIMEOUT) {
                    f.mode = FishMode.SWIM
                    suitor = -1
                }
            }
            FishMode.NIBBLE -> {
                f.modeT -= dt
                if (f.modeT <= 0f) {
                    if (f.nibbles > 0) {
                        f.nibbles--
                        f.modeT = rng.range(FishingTuning.NIBBLE_GAP_MIN, FishingTuning.NIBBLE_GAP_MAX)
                        bobDip = 0.45f
                        addRipple(lureX, lureZ, 18f, 0.7f)
                        play(Sfx.BITE, 0.3f, 1.6f)
                    } else {
                        bite(f)
                    }
                }
            }
            else -> Unit
        }
    }

    /** Some fish near the lure decides to go for it. */
    private fun findSuitor(dt: Float) {
        val r2 = FishingTuning.NOTICE_RADIUS * FishingTuning.NOTICE_RADIUS
        for (i in 0 until FishingTuning.FISH_SLOTS) {
            val f = fish[i]
            if (f.mode != FishMode.SWIM || f.spookT > 0f) continue
            val dx = f.x - lureX
            val dz = f.z - lureZ
            if (dx * dx + dz * dz > r2) continue
            if (rng.nextFloat() < FishingTuning.APPETITE[f.species] * dt) {
                suitor = i
                f.mode = FishMode.APPROACH
                f.modeT = 0f
                return
            }
        }
    }

    private fun bite(f: Fish) {
        f.mode = FishMode.BITE
        f.modeT = if (f.species == BOOT_SPECIES) FishingTuning.BOOT_WINDOW else FishingTuning.STRIKE_WINDOW
        strikeAcc = 0f
        bobDip = 1f
        addRipple(lureX, lureZ, 36f, 1f)
        play(Sfx.BITE, 1f, if (f.species == BOOT_SPECIES) 0.7f else 1f)
        fx.haptics.tick()
        if (stage.toField(lureX, 0f, lureZ, pt)) {
            popups.add("STRIKE!", pt[0], pt[1] - 34f, Color(Pal.YELLOW), size = 3.5f, life = 0.8f)
            particles.burst(pt[0], pt[1], 10, 30f, 110f, SPLASH_COLORS, 0.45f, 3f, grav = 300f)
        }
    }

    private fun missStrike(s: Int) {
        missed++
        val f = fish[s]
        if (f.species == BOOT_SPECIES) {
            f.mode = FishMode.SWIM
            bootIgnored = true
        } else {
            spook(f, lureX, lureZ, FishingTuning.SPOOK_SECONDS)
        }
        suitor = -1
        addRipple(lureX, lureZ, 30f, 0.9f)
        play(Sfx.SPLASH, 0.35f, 1.4f)
        if (stage.toField(lureX, 0f, lureZ, pt)) popups.add("MISSED!", pt[0], pt[1] - 30f, Color(Pal.LIGHTGRAY), size = 3f)
    }

    private fun strikeTooSoon(s: Int) {
        tooSoon++
        val f = fish[s]
        if (f.species == BOOT_SPECIES) {
            f.mode = FishMode.SWIM
            bootIgnored = true
        } else {
            spook(f, lureX, lureZ, FishingTuning.SPOOK_SECONDS)
        }
        suitor = -1
        earlyAcc = 0f
        addRipple(lureX, lureZ, 26f, 0.8f)
        if (stage.toField(lureX, 0f, lureZ, pt)) popups.add("TOO SOON!", pt[0], pt[1] - 30f, Color(Pal.LIGHTGRAY), size = 3f)
    }

    /** The lure is wound all the way back in: ready to cast again. */
    private fun lureOut() {
        releaseSuitor()
        phase = CastPhase.IDLE
        play(Sfx.BLIP, 0.3f, 1.3f)
    }

    private fun releaseSuitor() {
        val s = suitor
        if (s >= 0) {
            val f = fish[s]
            if (f.mode == FishMode.APPROACH || f.mode == FishMode.NIBBLE || f.mode == FishMode.BITE) f.mode = FishMode.SWIM
        }
        suitor = -1
    }

    /** Winds the lure back to the rod from wherever it is. */
    private fun recover() {
        fromX = lureX; fromY = lureY; fromZ = lureZ
        phase = CastPhase.RECOVER
        phaseT = 0f
        tension = 0f
        strainT = 0f
        slackT = 0f
    }

    private fun spook(f: Fish, px: Float, pz: Float, seconds: Float) {
        f.mode = FishMode.SWIM
        f.spookT = seconds
        f.fleeX = px
        f.fleeZ = pz
    }

    private fun hook(s: Int) {
        val f = fish[s]
        hooked = s
        suitor = -1
        f.mode = FishMode.HOOKED
        phase = CastPhase.FIGHT
        phaseT = 0f
        fightT = 0f
        lineD = hypot(f.x - DOCK_X, f.z - DOCK_Z).coerceAtLeast(FishingTuning.LAND_DIST + 1f)
        lineAng = atan2(f.x - DOCK_X, DOCK_Z - f.z).coerceIn(-FishingTuning.MAX_SWAY, FishingTuning.MAX_SWAY)
        tension = 0.45f
        pull = Pull.REST
        pullT = FishingTuning.FIRST_REST
        stamina = 1f
        strainT = 0f
        slackT = 0f
        play(Sfx.SPLASH, 0.8f, 0.9f)
        fx.haptics.hit()
        shake.add(0.15f)
        addRipple(f.x, f.z, 40f, 1f)
        if (stage.toField(f.x, 0f, f.z, pt)) {
            popups.add(if (f.species == BOOT_SPECIES) "SNAGGED!" else "HOOKED!", pt[0], pt[1] - 36f, Color(Pal.LIME), size = 3.5f)
            particles.burst(pt[0], pt[1], 18, 50f, 170f, SPLASH_COLORS, 0.6f, 3.5f, grav = 380f)
        }
    }

    private fun stepFight(dt: Float, turn: Float) {
        phaseT += dt
        fightT += dt
        val f = fish[hooked]
        val sp = f.species
        val boot = sp == BOOT_SPECIES
        val pullK = FishingTuning.PULL[sp]
        if (turn > 0f) lineD -= turn * FishingTuning.REEL_PER_RAD / (1f + f.weight * FishingTuning.REEL_WEIGHT_K)
        if (turn < 0f) lineD -= turn * FishingTuning.LET_OUT_PER_RAD
        val c = (crank.rate / FishingTuning.CRANK_REF).coerceAtLeast(0f)
        val cOut = (-crank.rate / FishingTuning.CRANK_REF).coerceAtLeast(0f)
        var target: Float
        if (boot) {
            target = FishingTuning.BOOT_TENSION + FishingTuning.CRANK_TENSION * c * 1.3f
        } else {
            val restTarget = FishingTuning.REST_TENSION + FishingTuning.CRANK_TENSION * c * (1f + f.weight * 0.05f)
            when (pull) {
                Pull.REST -> {
                    lineD += FishingTuning.REST_DRIFT * pullK * dt
                    target = restTarget
                    pullT -= dt
                    if (pullT <= 0f) {
                        pull = Pull.WARN
                        pullT = FishingTuning.WARN_SECONDS
                    }
                }
                Pull.WARN -> {
                    target = restTarget + 0.08f
                    pullT -= dt
                    if (rng.nextFloat() < dt * 10f && stage.toField(f.x, 0f, f.z, pt)) {
                        particles.burst(pt[0], pt[1], 4, 30f, 120f, SPLASH_COLORS, 0.4f, 3f, grav = 400f, angleFrom = PI.toFloat() * 1.15f, angleTo = PI.toFloat() * 1.85f)
                    }
                    if (pullT <= 0f) startRun(f)
                }
                Pull.RUN -> {
                    lineD += FishingTuning.RUN_SPEED[sp] * stamina * dt
                    lineAng += runDir * FishingTuning.RUN_SWAY * dt
                    target = FishingTuning.RUN_TENSION + pullK * FishingTuning.RUN_PULL_TENSION * stamina +
                        FishingTuning.CRANK_TENSION * c * (1.6f + pullK)
                    pullT -= dt
                    if (pullT <= 0f) {
                        pull = Pull.REST
                        pullT = rng.range(FishingTuning.REST_MIN, FishingTuning.REST_MAX)
                        stamina *= FishingTuning.STAMINA_DECAY
                    }
                }
            }
        }
        target -= FishingTuning.LET_OUT_RELIEF * cOut
        lineAng = lineAng.coerceIn(-FishingTuning.MAX_SWAY, FishingTuning.MAX_SWAY)
        val dx = sin(lineAng)
        val dz = -cos(lineAng)
        val maxD = reach(dx, dz) - EDGE_MARGIN
        if (lineD > maxD) {
            lineD = maxD
            if (pull == Pull.RUN && !boot) target += 0.25f
        }
        tension = damp(tension, target.coerceAtLeast(0f), FishingTuning.TENSION_RATE, dt)
        // The fish (and the float, dragged along the surface just this side of it).
        f.x = DOCK_X + dx * lineD
        f.z = DOCK_Z + dz * lineD
        if (!boot) {
            f.y = damp(f.y, if (pull == Pull.WARN) -3f else -9f - 8f * clamp01(lineD / 300f), 4f, dt)
            f.heading = if (pull == Pull.RUN) atan2(dx, dz) + runDir * 0.4f else atan2(-dx, -dz) + sin(fightT * 7f) * 0.5f
        } else {
            f.y = damp(f.y, -12f, 1.5f, dt)
        }
        lureX = f.x - dx * 10f
        lureZ = f.z - dz * 10f
        lureY = 0f
        if (tension > FishingTuning.SNAP_TENSION) {
            strainT += dt
            if (strainT > FishingTuning.SNAP_GRACE) {
                snapLine(f)
                return
            }
        } else {
            strainT = (strainT - dt).coerceAtLeast(0f)
        }
        if (!boot && tension < FishingTuning.SLACK_TENSION) {
            slackT += dt
            if (slackT > FishingTuning.SLACK_GRACE) {
                throwHook(f)
                return
            }
        } else {
            slackT = (slackT - dt * 2f).coerceAtLeast(0f)
        }
        if (lineD <= FishingTuning.LAND_DIST) {
            startLanding(f)
            return
        }
        if (fightT > FishingTuning.FIGHT_TIMEOUT) {
            failsafeTrips++
            throwHook(f)
        }
    }

    private fun startRun(f: Fish) {
        pull = Pull.RUN
        pullT = rng.range(FishingTuning.RUN_MIN, FishingTuning.RUN_MAX) * (0.7f + FishingTuning.PULL[f.species])
        // Mostly away from the side it's already on, so it stays out in open water.
        runDir = if (rng.nextFloat() < 0.5f + lineAng) -1f else 1f
        tension += FishingTuning.YANK * FishingTuning.PULL[f.species]
        play(Sfx.SPLASH, 0.55f, 1.3f)
        fx.haptics.tick()
        shake.add(0.08f)
        addRipple(f.x, f.z, 30f, 0.9f)
        splashAt(f.x, f.z, 0.55f)
    }

    private fun snapLine(f: Fish) {
        snaps++
        spook(f, DOCK_X, DOCK_Z, FishingTuning.SPOOK_SECONDS)
        hooked = -1
        play(Sfx.LINE_SNAP, 1f)
        fx.haptics.heavy()
        shake.add(0.45f)
        snapFlash = 1f
        popups.add("SNAP!", GAME_W / 2f, 300f, Color(Pal.RED), size = 5f, life = 1f)
        // The lure's gone with the fish: a fresh one is tied on at the rod.
        lureX = tipX; lureY = tipY - 30f; lureZ = tipZ
        recover()
    }

    private fun throwHook(f: Fish) {
        thrown++
        spook(f, DOCK_X, DOCK_Z, FishingTuning.SPOOK_SECONDS)
        hooked = -1
        play(Sfx.SPLASH, 0.6f, 1.2f)
        fx.haptics.tick()
        addRipple(f.x, f.z, 34f, 1f)
        splashAt(f.x, f.z, 0.8f)
        if (stage.toField(f.x, 0f, f.z, pt)) popups.add("IT GOT AWAY!", pt[0], pt[1] - 30f, Color(Pal.LIGHTGRAY), size = 3f)
        recover()
    }

    private fun startLanding(f: Fish) {
        phase = CastPhase.LANDING
        phaseT = 0f
        f.mode = FishMode.LANDED
        landX = f.x; landY = f.y; landZ = f.z
        tension = 0f
        play(Sfx.SPLASH, 0.9f, 0.8f)
        addRipple(f.x, f.z, 50f, 1.2f)
        splashAt(f.x, f.z, 1.5f)
        if (stage.toField(f.x, 0f, f.z, pt)) {
            particles.burst(pt[0], pt[1], 26, 60f, 220f, SPLASH_COLORS, 0.7f, 4f, grav = 420f, angleFrom = PI.toFloat() * 1.05f, angleTo = PI.toFloat() * 1.95f)
        }
    }

    /** The fish is out of the water: it scores its weight times its species' value. */
    private fun scoreCatch() {
        val f = fish[hooked]
        val sp = f.species
        val pts = catchPoints(sp, f.weight)
        landed++
        lastCatch = pts
        stage.toField(showX, showY, showZ, pt)
        val sx = pt[0]
        val sy = pt[1]
        val tenths = (f.weight * 10f).roundToInt()
        catchName = FishingTuning.NAMES[sp] + "  " + (tenths / 10) + "." + (tenths % 10) + " LB"
        catchShowT = 1.8f
        when {
            sp == BOOT_SPECIES -> {
                addScore(pts, sx, sy - 40f, Color(Pal.TAN))
                popups.add("JUNK!", sx, sy + 10f, Color(Pal.TAN), size = 4f)
                play(Sfx.THUD, 0.9f)
                fx.haptics.hit()
            }
            sp == 3 -> {
                addScore(pts, sx, sy - 40f, Color(Pal.GOLD))
                popups.add("GOLDEN!!", sx, sy + 10f, Color(Pal.GOLD), size = 5f, life = 1.4f)
                play(Sfx.JACKPOT)
                fx.haptics.jackpot()
                shake.add(0.5f)
                flash.trigger(0.7f)
                particles.confetti(0f, 0f, GAME_W, 80)
                particles.burst(sx, sy, 40, 80f, 300f, GOLD_COLORS, 0.9f, 5f, kind = Particles.SPARKLE)
            }
            pts >= FishingTuning.BIG_CATCH -> {
                addScore(pts, sx, sy - 40f, Color(Pal.LIME))
                popups.add("WHOPPER!", sx, sy + 10f, Color(Pal.LIME), size = 4.5f, life = 1.2f)
                play(Sfx.CATCH)
                play(Sfx.WIN, 0.7f)
                fx.haptics.win()
                shake.add(0.3f)
                flash.trigger(0.4f)
                particles.confetti(0f, 0f, GAME_W, 50)
            }
            else -> {
                addScore(pts, sx, sy - 40f, Color(Pal.CYAN))
                play(Sfx.CATCH, 0.9f)
                fx.haptics.hit()
                particles.burst(sx, sy, 24, 60f, 220f, SPLASH_COLORS, 0.7f, 4f, grav = 200f)
            }
        }
        f.mode = FishMode.GONE
        f.modeT = if (sp == BOOT_SPECIES) FishingTuning.RESPAWN_SECONDS * 3f else FishingTuning.RESPAWN_SECONDS
        hooked = -1
        phase = CastPhase.IDLE
        lureX = tipX; lureY = tipY - 30f; lureZ = tipZ
    }

    private fun catchPoints(species: Int, weight: Float): Int =
        (weight * FishingTuning.VALUE[species]).roundToInt().coerceAtLeast(1)

    // ---------------------------------------------------------------- the fish

    private fun rollSpecies(): Int {
        var total = 0f
        for (w in FishingTuning.SPAWN_ODDS) total += w
        var r = rng.nextFloat() * total
        for (i in FishingTuning.SPAWN_ODDS.indices) {
            r -= FishingTuning.SPAWN_ODDS[i]
            if (r < 0f) return i
        }
        return 0
    }

    /** A new fish anywhere in the pond ([anywhere]) or swimming in from the far side. */
    private fun spawnFish(f: Fish, anywhere: Boolean) {
        val sp = rollSpecies()
        f.species = sp
        f.weight = rng.range(FishingTuning.WEIGHT_MIN[sp], FishingTuning.WEIGHT_MAX[sp])
        val mid = (FishingTuning.WEIGHT_MIN[sp] + FishingTuning.WEIGHT_MAX[sp]) / 2f
        f.scale = 0.8f + 0.4f * (f.weight / mid - 0.5f).coerceIn(0f, 1f)
        val a = if (anywhere) rng.range(0f, TAU) else rng.range(PI.toFloat() * 1.15f, PI.toFloat() * 1.85f)
        val d = if (anywhere) sqrt(rng.nextFloat()) * 0.8f else rng.range(0.75f, 0.85f)
        f.x = POND_CX + cos(a) * POND_RX * d
        f.z = (POND_CZ + sin(a) * POND_RZ * d).coerceAtMost(DOCK_Z - 110f)
        f.y = -18f
        val h = rng.range(0f, TAU)
        f.vx = sin(h) * FishingTuning.SPEED[sp]
        f.vz = cos(h) * FishingTuning.SPEED[sp]
        f.heading = h
        f.wanderT = 0f
        f.spookT = 0f
        f.modeT = 0f
        f.mode = FishMode.SWIM
        f.depthPhase = rng.range(0f, TAU)
        f.wiggle = rng.range(0f, TAU)
    }

    /** The old boot lies somewhere on the bottom, away from the dock. */
    private fun placeBoot(f: Fish) {
        f.species = BOOT_SPECIES
        f.weight = FishingTuning.WEIGHT_MIN[BOOT_SPECIES]
        f.scale = 1f
        val a = rng.range(0f, TAU)
        val d = sqrt(rng.nextFloat()) * 0.6f
        f.x = POND_CX + cos(a) * POND_RX * d
        f.z = (POND_CZ + sin(a) * POND_RZ * d).coerceAtMost(DOCK_Z - 160f)
        f.y = BED_Y + 1.5f
        f.vx = 0f
        f.vz = 0f
        f.heading = rng.range(0f, TAU)
        f.mode = FishMode.SWIM
        f.spookT = 0f
        f.modeT = 0f
    }

    private fun stepFish(dt: Float) {
        for (i in 0 until SLOTS) {
            val f = fish[i]
            when (f.mode) {
                FishMode.GONE -> {
                    f.modeT -= dt
                    if (f.modeT <= 0f) {
                        if (i == BOOT) placeBoot(f) else spawnFish(f, anywhere = false)
                    }
                }
                FishMode.HOOKED, FishMode.LANDED -> f.wiggle += dt * 16f
                else -> if (i != BOOT) swim(f, i, dt)
            }
        }
    }

    /** Boids-lite: wander, keep apart, school loosely with the same kind, stay in the pond. */
    private fun swim(f: Fish, i: Int, dt: Float) {
        val speed = FishingTuning.SPEED[f.species]
        var dx: Float
        var dz: Float
        var pace = 1f
        val seeking = f.mode == FishMode.APPROACH || f.mode == FishMode.NIBBLE || f.mode == FishMode.BITE
        if (seeking) {
            // Nose up to the lure; circle it a little while nibbling.
            val jig = if (f.mode == FishMode.APPROACH) 0f else 3f
            val tx = lureX + sin(time * 3f + i) * jig
            val tz = lureZ + cos(time * 3f + i) * jig
            dx = tx - f.x
            dz = tz - f.z
            val d = hypot(dx, dz)
            pace = if (f.mode == FishMode.APPROACH) 1.1f * clamp01(d / 20f + 0.25f) else clamp01(d / 12f)
            f.y = damp(f.y, -5f, 2f, dt)
        } else {
            f.spookT -= dt
            f.wanderT -= dt
            if (f.wanderT <= 0f || hypot(f.wx - f.x, f.wz - f.z) < 15f) {
                val a = rng.range(0f, TAU)
                val d = sqrt(rng.nextFloat()) * 0.78f
                f.wx = POND_CX + cos(a) * POND_RX * d
                f.wz = (POND_CZ + sin(a) * POND_RZ * d).coerceAtMost(DOCK_Z - 60f)
                f.wanderT = rng.range(2f, 5f)
            }
            dx = f.wx - f.x
            dz = f.wz - f.z
            var l = hypot(dx, dz).coerceAtLeast(1e-3f)
            dx /= l; dz /= l
            if (f.spookT > 0f) {
                val ex = f.x - f.fleeX
                val ez = f.z - f.fleeZ
                val el = hypot(ex, ez)
                if (el < 160f) {
                    val k = 2f / el.coerceAtLeast(1e-3f)
                    dx += ex * k; dz += ez * k
                    pace = 1.8f
                }
            }
            // Separation, alignment and cohesion.
            var ax = 0f
            var az = 0f
            var cx = 0f
            var cz = 0f
            var mates = 0
            for (j in 0 until FishingTuning.FISH_SLOTS) {
                if (j == i) continue
                val o = fish[j]
                if (o.mode == FishMode.GONE || o.mode == FishMode.LANDED) continue
                val ox = f.x - o.x
                val oz = f.z - o.z
                val d2 = ox * ox + oz * oz
                if (d2 < SEP_RADIUS * SEP_RADIUS && d2 > 1e-4f) {
                    val d = sqrt(d2)
                    val k = (SEP_RADIUS - d) / SEP_RADIUS * 1.5f / d
                    dx += ox * k; dz += oz * k
                }
                if (o.species == f.species && d2 < FLOCK_RADIUS * FLOCK_RADIUS) {
                    ax += o.vx; az += o.vz
                    cx += o.x; cz += o.z
                    mates++
                }
            }
            if (mates > 0) {
                val al = hypot(ax, az)
                if (al > 1e-3f) {
                    dx += ax / al * 0.3f; dz += az / al * 0.3f
                }
                dx += (cx / mates - f.x) * 0.004f
                dz += (cz / mates - f.z) * 0.004f
            }
            val e = ellipse(f.x, f.z)
            if (e > 0.72f) {
                val bx = POND_CX - f.x
                val bz = POND_CZ - f.z
                l = hypot(bx, bz).coerceAtLeast(1e-3f)
                val k = (e - 0.72f) * 6f
                dx += bx / l * k; dz += bz / l * k
            }
            if (f.z > DOCK_Z - 70f) dz -= 1.5f
            f.y = -10f - 16f * (0.5f + 0.5f * sin(time * 0.5f + f.depthPhase))
        }
        val l = hypot(dx, dz)
        val want = speed * pace
        val tx = if (l > 1e-4f) dx / l * want else 0f
        val tz = if (l > 1e-4f) dz / l * want else 0f
        f.vx = damp(f.vx, tx, 2.5f, dt)
        f.vz = damp(f.vz, tz, 2.5f, dt)
        f.x += f.vx * dt
        f.z += f.vz * dt
        // Hard edge: never through the bank or under the dock.
        val e = ellipse(f.x, f.z)
        if (e > 0.9f) {
            val k = sqrt(0.9f / e)
            f.x = POND_CX + (f.x - POND_CX) * k
            f.z = POND_CZ + (f.z - POND_CZ) * k
        }
        if (f.z > DOCK_Z - 30f) f.z = DOCK_Z - 30f
        val sp2 = f.vx * f.vx + f.vz * f.vz
        if (sp2 > 1f) f.heading = atan2(f.vx, f.vz)
        f.wiggle += dt * (4f + sqrt(sp2) * 0.15f)
    }

    private fun ellipse(x: Float, z: Float): Float {
        val u = (x - POND_CX) / POND_RX
        val v = (z - POND_CZ) / POND_RZ
        return u * u + v * v
    }

    private fun hypot(x: Float, z: Float): Float = sqrt(x * x + z * z)

    // ---------------------------------------------------------------- ripples

    private fun addRipple(x: Float, z: Float, size: Float, life: Float) {
        var slot = 0
        var oldest = -1f
        for (i in 0 until RIPPLES) {
            if (!rOn[i]) {
                slot = i
                break
            }
            val left = rLife[i] - rAge[i]
            if (oldest < 0f || left < oldest) {
                oldest = left
                slot = i
            }
        }
        rOn[slot] = true
        rx[slot] = x
        rz[slot] = z
        rAge[slot] = 0f
        rLife[slot] = life
        rSize[slot] = size
    }

    /** Starts a spray of droplets at ([x], [z]) on the water, [power] times a normal splash. */
    private fun splashAt(x: Float, z: Float, power: Float) {
        val i = splashNext
        splashNext = (splashNext + 1) % SPLASHES
        splashX[i] = x
        splashZ[i] = z
        splashAge[i] = 0f
        splashPower[i] = power
        splashSeed[i] = ++splashCount
    }

    private fun stepRipples(dt: Float) {
        for (i in 0 until RIPPLES) {
            if (!rOn[i]) continue
            rAge[i] += dt
            if (rAge[i] >= rLife[i]) rOn[i] = false
        }
    }

    // ---------------------------------------------------------------- 3D presentation

    /** The low sun's warm light over the pond (golden hour: long, warm, a little dim). */
    private val sun = PointLight(360f, 260f, 120f, 1f, 0.72f, 0.42f, 900f, 0.42f)

    /** The mist's wrapping texture view, fetched once. */
    private val fogRegion: Region by lazy { FishingArt.fog }
    private val goldLight = PointLight(0f, 20f, 0f, 1f, 0.8f, 0.3f, 90f, 0f)
    private val xf = Xform()
    private val bankTex by lazy {
        FishingArt.bank(BANK_X0, BANK_Z0, BANK_X1, BANK_Z1, POND_CX, POND_CZ, POND_RX, POND_RZ).full
    }
    private val lineEnd = FloatArray(3)
    private val tipNow = FloatArray(3)

    private object Scenery {
        /** Trees on the far bank: x, z, height. */
        val TREES = floatArrayOf(
            -150f, -90f, 150f, -60f, -140f, 175f, 40f, -110f, 140f, 120f, -150f, 190f, 215f, -120f, 150f,
            300f, -95f, 170f, 400f, -130f, 185f, 500f, -80f, 150f, -40f, 20f, 120f, 430f, 30f, 130f,
        )
        /** Reed clumps round the far rim: angles (radians) on the pond's ellipse. */
        val REEDS = floatArrayOf(3.3f, 3.6f, 3.95f, 4.3f, 4.75f, 5.1f, 5.5f, 5.85f, 6.1f, 2.9f, 0.3f)
    }

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        // Golden hour: soft violet shade, warm low sun. Bright enough that the fish still read.
        l.ambR = 0.70f; l.ambG = 0.68f; l.ambB = 0.76f
        l.setDirection(-0.4f, 1f, 0.5f)
        l.dirR = 0.62f; l.dirG = 0.49f; l.dirB = 0.35f
        l.points.clear()
        l.points += sun
        r.vignette = 0.3f
        r.gradient(0xFF3D68AE.toInt(), 0xFFF3B27A.toInt())
        drawSky(r)
        drawScenery(r)
        drawFish(r)
        drawPads(r)
        rodTip(tipNow)
        drawRod(r)
        drawBobber(r)
        drawWater(r)
        drawMist(r)
        drawGlitter(r)
        drawRipples(r)
        drawSplashes(r)
        drawLine(r)
        drawAim(r)
        drawGlints(r)
        drawFireflies(r)
        stage.present()
        drawHud(scope)
    }

    /**
     * The low sun behind the far bank: a wide warm halo, its core, a band of horizon haze over the
     * tree line and a few slow beams of light slanting down between the trees.
     */
    private fun drawSky(r: Renderer3D) {
        val glow = TexKit.glow.full
        val breathe = 1f + 0.05f * sin(time * 0.7f)
        r.sprite(SUN_X, SUN_Y, SUN_Z, 640f, 470f, glow, blend = Blend.ADD, emissive = 1f, alpha = HALO_ALPHA * breathe, tint = 0xFFFF9A50.toInt())
        r.sprite(SUN_X, SUN_Y, SUN_Z + 1f, 240f, 240f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = 0xFFFFD890.toInt())
        r.sprite(SUN_X, SUN_Y, SUN_Z + 2f, 72f, 72f, TexKit.dot.full, blend = Blend.ADD, emissive = 1.2f, alpha = 0.95f, tint = 0xFFFFF4D0.toInt())
        val haze = FishingArt.haze
        r.quad(-620f, 200f, -418f, 980f, 200f, -418f, 980f, 40f, -418f, -620f, 40f, -418f, haze, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.32f, cull = false, tint = 0xFFFFB068.toInt())
        // Beams: each slants from near the sun down and away to the left, swelling and fading.
        val shaft = FishingArt.shaft
        for (i in 0 until 4) {
            val xt = SUN_X - 30f + i * 26f
            val xb = xt - 210f - i * 60f
            val hwT = 14f
            val hwB = 62f + i * 12f
            val a = SHAFT_ALPHA * (0.65f + 0.35f * sin(time * 0.5f + i * 1.9f))
            r.quad(
                xt - hwT, 178f, -200f, xt + hwT, 178f, -200f, xb + hwB, 0f, -200f, xb - hwB, 0f, -200f, shaft, 0f, 0f, 1f,
                blend = Blend.ADD, emissive = 1f, alpha = a, cull = false, tint = 0xFFFFC880.toInt(),
            )
        }
    }

    private fun drawScenery(r: Renderer3D) {
        // Distant tree line and hills.
        val tl = FishingArt.treeline
        r.quad(-620f, 190f, -420f, 980f, 190f, -420f, 980f, -10f, -420f, -620f, -10f, -420f, tl, 0f, 0f, 1f, emissive = 0.85f, tint = 0xFFFFDDB4.toInt())
        // Grass round the pond, with the pond cut out of it.
        r.quad(BANK_X0, 0.4f, BANK_Z0, BANK_X1, 0.4f, BANK_Z0, BANK_X1, 0.4f, BANK_Z1, BANK_X0, 0.4f, BANK_Z1, bankTex, 0f, 1f, 0f)
        // The pond bed.
        val bed = FishingArt.bed
        val bw = bed.w.toFloat()
        r.quad(
            POND_CX - POND_RX - 20f, BED_Y, POND_CZ - POND_RZ - 20f, POND_CX + POND_RX + 20f, BED_Y, POND_CZ - POND_RZ - 20f,
            POND_CX + POND_RX + 20f, BED_Y, POND_CZ + POND_RZ + 20f, POND_CX - POND_RX - 20f, BED_Y, POND_CZ + POND_RZ + 20f,
            bed, 0f, 1f, 0f, u1 = bw * 4f, v1 = bw * 5.5f,
        )
        // Trees and reeds.
        val tree = FishingArt.tree
        val trees = Scenery.TREES
        var k = 0
        while (k < trees.size) {
            val h = trees[k + 2]
            r.billboard(trees[k], 0f, trees[k + 1], h * 0.75f, h, tree, flipX = k % 2 == 0, lean = 0.2f, tint = 0xFFFFE6C4.toInt())
            k += 3
        }
        val reeds = FishingArt.reeds
        for (i in Scenery.REEDS.indices) {
            val a = Scenery.REEDS[i]
            val x = POND_CX + cos(a) * (POND_RX + 4f)
            val z = POND_CZ + sin(a) * (POND_RZ + 4f)
            r.billboard(x, 0f, z, 34f, 34f, reeds, flipX = i % 2 == 1, lean = 0.3f)
        }
        // The dock: planks running towards the viewer, its edge and two posts.
        val pl = FishingArt.planks
        val pw = pl.w.toFloat()
        r.quad(92f, 8f, DOCK_Z + 10f, 268f, 8f, DOCK_Z + 10f, 268f, 8f, 900f, 92f, 8f, 900f, pl, 0f, 1f, 0f, u1 = pw * 1.4f, v1 = pw * 2.7f)
        r.quad(92f, 8f, DOCK_Z + 10f, 268f, 8f, DOCK_Z + 10f, 268f, -6f, DOCK_Z + 10f, 92f, -6f, DOCK_Z + 10f, pl, 0f, 0f, 1f, v1 = 12f)
        r.beam(98f, 14f, DOCK_Z + 12f, 98f, -30f, DOCK_Z + 12f, 9f, pl)
        r.beam(262f, 14f, DOCK_Z + 12f, 262f, -30f, DOCK_Z + 12f, 9f, pl)
    }

    private fun drawFish(r: Renderer3D) {
        val shadow = TexKit.shadow.full
        for (i in 0 until SLOTS) {
            val f = fish[i]
            if (f.mode == FishMode.GONE) continue
            val len = FishingTuning.LENGTH[f.species] * f.scale
            if (f.mode == FishMode.LANDED) {
                drawLandedFish(r, f)
                continue
            }
            if (f.species == BOOT_SPECIES) {
                FishingArt.boot.draw(r, Blend.OPAQUE, xf = xf.set(f.x, f.y, f.z, yaw = f.heading, roll = PI.toFloat() / 2f))
                continue
            }
            // Soft shadow on the bed, then the fish itself swishing its tail.
            r.flat(f.x, f.z, BED_Y + 0.5f, len * 0.45f, len, shadow, angle = -f.heading, blend = Blend.ALPHA, alpha = 0.3f)
            val swish = sin(f.wiggle) * (if (f.mode == FishMode.HOOKED) 0.35f else 0.14f)
            FishingArt.fish(f.species).draw(r, Blend.OPAQUE, xf = xf.set(f.x, f.y, f.z, yaw = f.heading + swish, scale = f.scale))
        }
    }

    private fun drawLandedFish(r: Renderer3D, f: Fish) {
        val k = easeOutCubic(clamp01(phaseT / (FishingTuning.LAND_SECONDS * 0.7f)))
        val x = landX + (showX - landX) * k
        val z = landZ + (showZ - landZ) * k
        val y = landY + (showY - landY) * k + sin(k * PI.toFloat()) * 30f
        val wig = sin(f.wiggle) * 0.35f
        if (f.species == BOOT_SPECIES) {
            FishingArt.boot.draw(r, Blend.OPAQUE, xf = xf.set(x, y, z, yaw = 1.2f + wig * 0.3f, roll = wig, scale = 1.6f))
        } else {
            // Held up sideways by the mouth, tail flapping.
            FishingArt.fish(f.species).draw(r, Blend.OPAQUE, xf = xf.set(x, y, z, yaw = PI.toFloat() / 2f + wig, pitch = -1.2f, scale = f.scale * 1.7f))
        }
    }

    private fun drawPads(r: Renderer3D) {
        val pad = FishingArt.lilyPad
        for (i in 0 until PADS) {
            val a = padA[i] + sin(time * 0.4f + i) * 0.08f
            r.flat(padX[i], padZ[i], 0.7f, padS[i], padS[i], pad, angle = a)
            if (i % 3 == 0) r.sprite(padX[i] + 3f, 4f, padZ[i] - 2f, 12f, 12f, FishingArt.flower)
        }
    }

    /** Where the rod tip is this frame: bent towards the line by the tension, drawn back while charging. */
    private fun rodTip(out: FloatArray) {
        var bend = when (phase) {
            CastPhase.FIGHT -> tension * 1.1f
            CastPhase.WAIT -> 0.06f + bobDip * 0.25f
            CastPhase.LANDING -> 0.5f
            else -> 0.03f
        }
        var x = tipX
        var y = tipY
        var z = tipZ
        if (phase == CastPhase.CHARGE) {
            y += power * 26f
            z += power * 40f
        }
        if (phase == CastPhase.FLIGHT) {
            val k = clamp01(phaseT / 0.25f)
            y -= (1f - k) * 20f
        }
        if (phase == CastPhase.FIGHT && pull != Pull.REST) bend += sin(time * 38f) * 0.04f
        val tx = lureX - x
        val ty = lureY - y
        val tz = lureZ - z
        val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1f)
        x += tx / tl * bend * 40f
        y += ty / tl * bend * 40f - bend * 26f
        z += tz / tl * bend * 40f
        out[0] = x; out[1] = y; out[2] = z
    }

    private fun drawRod(r: Renderer3D) {
        val n = 10
        // Quadratic curve: butt, a control point on the straight rod, and the (bent) tip.
        val cx = buttX + (tipX - buttX) * 0.55f
        val cy = buttY + (tipY - buttY) * 0.55f
        val cz = buttZ + (tipZ - buttZ) * 0.55f
        var px = buttX
        var py = buttY
        var pz = buttZ
        for (s in 1..n) {
            val t = s / n.toFloat()
            val u = 1f - t
            val x = u * u * buttX + 2f * u * t * cx + t * t * tipNow[0]
            val y = u * u * buttY + 2f * u * t * cy + t * t * tipNow[1]
            val z = u * u * buttZ + 2f * u * t * cz + t * t * tipNow[2]
            val w = 4.2f - 3.2f * t
            r.beam(px, py, pz, x, y, z, if (s <= 2) w + 1.6f else w, if (s <= 2) FishingArt.cork else FishingArt.rod)
            // A warm sheen of low sun along the top of the blank.
            if (s > 2) r.beam(px, py + w * 0.3f, pz, x, y + w * 0.3f, z, w * 0.3f, FishingArt.line, blend = Blend.ADD, emissive = 1f, alpha = 0.32f * (1f - t * 0.7f), tint = 0xFFFFE0B0.toInt())
            if (s in 3..9 && s % 2 == 1) r.sprite(x, y + w * 0.6f, z, w * 0.9f, w * 0.9f, TexKit.dot.full, tint = Pal.LIGHTGRAY)
            px = x; py = y; pz = z
        }
        // The tip glints, and burns hotter as the rod bends under a hard-pulling fish.
        val hot = if (phase == CastPhase.FIGHT) ((tension - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
        r.sprite(tipNow[0], tipNow[1] + 1f, tipNow[2], 9f + 12f * hot, 9f + 12f * hot, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.3f + 0.5f * hot, tint = Pal.mix(0xFFFFE0B0.toInt(), Pal.RED, hot))
    }

    /** The float: at the line's end (hanging from the rod when nothing's cast). */
    private fun drawBobber(r: Renderer3D) {
        lineTarget(lineEnd)
        if (phase == CastPhase.LANDING) return
        var y = lineEnd[1]
        if (phase == CastPhase.WAIT) {
            val biting = suitor >= 0 && fish[suitor].mode == FishMode.BITE
            y = if (biting) -5f + sin(time * 30f) * 0.8f else sin(time * 2.4f) * 0.7f - bobDip * 4f
        }
        if (phase == CastPhase.FIGHT) y = sin(time * 9f) * 1f
        val bx = if (phase == CastPhase.FIGHT) lureX else lineEnd[0]
        val bz = if (phase == CastPhase.FIGHT) lureZ else lineEnd[2]
        FishingArt.bobber.draw(r, Blend.OPAQUE, xf = xf.set(bx, y, bz, roll = if (phase == CastPhase.FIGHT) sin(time * 5f) * 0.5f else 0f))
        if (phase == CastPhase.WAIT) {
            val biting = suitor >= 0 && fish[suitor].mode == FishMode.BITE
            if (biting) {
                // The dive is the cue to strike: rings pulse out from the bobber, and it glows.
                val k = (time * 3.2f).rem(1f)
                r.flat(bx, bz, 0.9f, 16f + 46f * k, 16f + 46f * k, FishingArt.ripple, blend = Blend.ADD, emissive = 1f, alpha = (1f - k) * 0.9f, tint = Pal.YELLOW)
                r.sprite(bx, y + 4f, bz, 24f, 24f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.4f + 0.3f * sin(time * 18f), tint = Pal.YELLOW)
            } else {
                r.sprite(bx, y + 3.5f, bz, 12f, 12f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.22f + 0.08f * sin(time * 2.4f), tint = 0xFFFF6A50.toInt())
            }
        }
    }

    /** Where the line ends: the lure, the hooked fish, or dangling under the rod tip. */
    private fun lineTarget(out: FloatArray) {
        when (phase) {
            CastPhase.IDLE, CastPhase.CHARGE -> {
                out[0] = tipNow[0]; out[1] = tipNow[1] - 34f; out[2] = tipNow[2]
            }
            CastPhase.FIGHT -> {
                val f = fish[hooked]
                out[0] = f.x; out[1] = f.y; out[2] = f.z
            }
            CastPhase.LANDING -> {
                val k = easeOutCubic(clamp01(phaseT / (FishingTuning.LAND_SECONDS * 0.7f)))
                out[0] = landX + (showX - landX) * k
                out[1] = landY + (showY - landY) * k + sin(k * PI.toFloat()) * 30f + 14f
                out[2] = landZ + (showZ - landZ) * k
            }
            else -> {
                out[0] = lureX; out[1] = lureY; out[2] = lureZ
            }
        }
    }

    private fun drawWater(r: Renderer3D) {
        val x0 = POND_CX - POND_RX - 10f
        val x1 = POND_CX + POND_RX + 10f
        val z0 = POND_CZ - POND_RZ - 10f
        val z1 = POND_CZ + POND_RZ + 10f
        val w = FishingArt.water
        val tw = w.w.toFloat()
        val su = time * 5f
        val sv = time * 3f
        r.quad(x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, x0, 0f, z1, w, 0f, 1f, 0f, u0 = su, v0 = sv, u1 = su + tw * 4.2f, v1 = sv + tw * 6f, blend = Blend.ALPHA, alpha = 0.72f, cull = false)
        val c = FishingArt.caustics
        val cw = c.w.toFloat()
        val cu = -time * 7f
        val cv = time * 4f
        r.quad(x0, 0.1f, z0, x1, 0.1f, z0, x1, 0.1f, z1, x0, 0.1f, z1, c, 0f, 1f, 0f, u0 = cu, v0 = cv, u1 = cu + cw * 3f, v1 = cv + cw * 4.4f, blend = Blend.ADD, emissive = 1f, alpha = 0.16f, cull = false, tint = 0xFFB8F4FF.toInt())
        r.quad(x0, 0.2f, z0, x1, 0.2f, z0, x1, 0.2f, z1, x0, 0.2f, z1, c, 0f, 1f, 0f, u0 = time * 3f, v0 = -time * 6f, u1 = time * 3f + cw * 5f, v1 = -time * 6f + cw * 7f, blend = Blend.ADD, emissive = 1f, alpha = 0.1f, cull = false)
    }

    /** A bank of mist drifting over the far water (a horizontal sheet that fades out at its edges). */
    private fun drawMist(r: Renderer3D) {
        val m = time * 4f
        r.quad(
            POND_CX - POND_RX, 7f, POND_CZ - POND_RZ - 10f, POND_CX + POND_RX, 7f, POND_CZ - POND_RZ - 10f,
            POND_CX + POND_RX, 7f, POND_CZ + 10f, POND_CX - POND_RX, 7f, POND_CZ + 10f, fogRegion, 0f, 1f, 0f,
            u0 = m, v0 = 0f, u1 = m + 256f, v1 = 32f, blend = Blend.ALPHA, emissive = 0.9f, alpha = MIST_ALPHA, cull = false, tint = 0xFFFFE2C8.toInt(),
        )
    }

    /** The sun's glitter on the water: a broad warm sheen and a scatter of twinkling streaks along the path to the sun. */
    private fun drawGlitter(r: Renderer3D) {
        // The glowing sky reflected in the far water.
        r.quad(
            POND_CX - POND_RX, 0.6f, POND_CZ - POND_RZ, POND_CX + POND_RX, 0.6f, POND_CZ - POND_RZ,
            POND_CX + POND_RX, 0.6f, 200f, POND_CX - POND_RX, 0.6f, 200f, FishingArt.haze, 0f, 1f, 0f,
            blend = Blend.ADD, emissive = 1f, alpha = 0.16f, cull = false, tint = 0xFFFF9A58.toInt(),
        )
        r.flat(246f, 240f, 0.8f, 130f, 520f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.10f, tint = 0xFFFFB060.toInt())
        val dot = TexKit.dot.full
        for (i in 0 until GLITTER) {
            val z = 20f + hash01(i, 301) * 500f
            val t = (800f - z) / 1230f
            val spread = 10f + (560f - z).coerceAtLeast(0f) * 0.05f + 30f * (1f - t)
            val x = 180f + 150f * t + (hash01(i, 302) - 0.5f) * 2f * spread
            val tw = sin(time * (1.6f + hash01(i, 303) * 3f) + hash01(i, 304) * 9f)
            val a = if (tw > 0f) tw * tw * tw * tw else 0f
            if (a < 0.02f) continue
            val w = 6f + hash01(i, 305) * 12f
            r.flat(x, z, 0.7f, w, w * 0.34f, dot, blend = Blend.ADD, emissive = 1f, alpha = 0.85f * a, tint = 0xFFFFF0D0.toInt())
        }
    }

    private fun drawRipples(r: Renderer3D) {
        val ring = FishingArt.ripple
        for (i in 0 until RIPPLES) {
            if (!rOn[i]) continue
            val k = rAge[i] / rLife[i]
            val s = rSize[i] * (0.3f + 0.9f * easeOutCubic(k))
            r.flat(rx[i], rz[i], 0.5f, s, s, ring, blend = Blend.ADD, emissive = 1f, alpha = 0.55f * (1f - k), tint = 0xFFDFF8FF.toInt())
            // A second, smaller wave following the first.
            val k2 = (k - 0.18f) / 0.82f
            if (k2 > 0f) {
                val s2 = rSize[i] * (0.3f + 0.75f * easeOutCubic(k2)) * 0.7f
                r.flat(rx[i], rz[i], 0.5f, s2, s2, ring, blend = Blend.ADD, emissive = 1f, alpha = 0.3f * (1f - k2), tint = 0xFFDFF8FF.toInt())
            }
        }
    }

    /** Sprays: droplets thrown up from a splash, each on its own arc, plus a bright column at the start. */
    private fun drawSplashes(r: Renderer3D) {
        val dot = TexKit.dot.full
        val glow = TexKit.glow.full
        for (i in 0 until SPLASHES) {
            val t = splashAge[i]
            if (t >= SPLASH_LIFE) continue
            val k = t / SPLASH_LIFE
            val pw = splashPower[i]
            val seed = splashSeed[i] * 7
            val x = splashX[i]
            val z = splashZ[i]
            if (t < 0.25f) {
                val c = 1f - t / 0.25f
                r.sprite(x, 8f + t * 60f * pw, z, 10f * pw, 26f * pw * (1f - 0.3f * (1f - c)), glow, blend = Blend.ADD, emissive = 1f, alpha = 0.6f * c, tint = 0xFFE8FFFF.toInt())
            }
            for (n in 0 until DROPLETS) {
                val ang = hash01(n, seed + 1) * 6.2832f
                val out = (14f + hash01(n, seed + 2) * 30f) * pw
                val up = (46f + hash01(n, seed + 3) * 70f) * pw
                val y = up * t - 0.5f * 300f * t * t
                if (y < 0f) continue
                val sz = 2.4f + hash01(n, seed + 4) * 2f
                r.sprite(x + kotlin.math.cos(ang) * out * t, y + 1f, z + kotlin.math.sin(ang) * out * t * 0.6f, sz, sz, dot, blend = Blend.ADD, emissive = 1f, alpha = 0.9f * (1f - k), tint = 0xFFEAFBFF.toInt())
            }
        }
    }

    /** Fireflies drifting over the far bank and the reeds, blinking on and off. */
    private fun drawFireflies(r: Renderer3D) {
        val dot = TexKit.dot.full
        val glow = TexKit.glow.full
        for (i in 0 until FIREFLIES) {
            val a = PI.toFloat() * 0.9f + hash01(i, 201) * PI.toFloat() * 1.2f
            val ring = 8f + hash01(i, 202) * 70f
            val x = POND_CX + cos(a) * (POND_RX + ring) + sin(time * 0.5f + i) * 10f
            val z = POND_CZ + sin(a) * (POND_RZ + ring * 0.6f) + cos(time * 0.4f + i * 1.3f) * 10f
            val y = 10f + hash01(i, 203) * 44f + sin(time * 0.8f + i * 2f) * 6f
            val b = 0.5f + 0.5f * sin(time * 2.2f + i * 1.9f)
            val a3 = b * b * b
            r.sprite(x, y, z, 3.6f, 3.6f, dot, blend = Blend.ADD, emissive = 1f, alpha = 0.15f + 0.85f * a3, tint = 0xFFDFFF80.toInt())
            r.sprite(x, y, z, 17f, 17f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f * a3, tint = 0xFFC8FF60.toInt())
        }
    }

    private fun drawLine(r: Renderer3D) {
        val sx = tipNow[0]
        val sy = tipNow[1]
        val sz = tipNow[2]
        val ex = lineEnd[0]
        val ey = lineEnd[1]
        val ez = lineEnd[2]
        val tight = when (phase) {
            CastPhase.FIGHT -> clamp01(tension * 1.4f)
            CastPhase.FLIGHT, CastPhase.LANDING, CastPhase.IDLE, CastPhase.CHARGE -> 1f
            else -> 0.15f
        }
        val len = sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz))
        val sag = (1f - tight) * len * 0.12f
        val color = if (phase == CastPhase.FIGHT && tension > FishingTuning.SAFE_HIGH) Pal.mix(Pal.WHITE, Pal.RED, (tension - FishingTuning.SAFE_HIGH) * 5f) else Pal.WHITE
        // A glow along the line as it comes under strain: cool, then warm, then hot, flickering at the snap.
        val strain = if (phase == CastPhase.FIGHT) (((tension - 0.55f) / 0.45f).coerceIn(0f, 1f)).let { it * it * (if (tension > FishingTuning.SNAP_TENSION - 0.05f) 0.7f + 0.3f * sin(time * 50f) else 1f) } else 0f
        val glowCol = Pal.mix(Pal.YELLOW, Pal.RED, ((tension - 0.85f) * 6f).coerceIn(0f, 1f))
        val segs = 8
        var px = sx
        var py = sy
        var pz = sz
        for (s in 1..segs) {
            val t = s / segs.toFloat()
            val x = sx + (ex - sx) * t
            val y = sy + (ey - sy) * t - sag * 4f * t * (1f - t)
            val z = sz + (ez - sz) * t
            r.beam(px, py, pz, x, y, z, 0.9f, FishingArt.line, blend = Blend.ALPHA, emissive = 1f, alpha = 0.85f, tint = color)
            if (strain > 0.02f) r.beam(px, py, pz, x, y, z, 3.4f, FishingArt.line, blend = Blend.ADD, emissive = 1f, alpha = strain * 0.45f, tint = glowCol)
            px = x; py = y; pz = z
        }
    }

    /** While a cast is wound up: its landing spot on the water and the arc to it. */
    private fun drawAim(r: Renderer3D) {
        if (phase != CastPhase.CHARGE) return
        landingSpot(aimFx, power, pt)
        val tx = pt[0]
        val tz = pt[1]
        val pulse = 0.75f + 0.25f * sin(time * 10f)
        r.flat(tx, tz, 0.6f, 40f, 40f, FishingArt.target, angle = time, blend = Blend.ADD, emissive = 1f, alpha = 0.8f * pulse, tint = Pal.YELLOW)
        val dist = hypot(tx - DOCK_X, tz - DOCK_Z)
        val arc = 50f + dist * 0.3f
        for (k in 1..9) {
            val t = k / 10f
            val x = tipNow[0] + (tx - tipNow[0]) * t
            val z = tipNow[2] + (tz - tipNow[2]) * t
            val y = tipNow[1] * (1f - t) + 4f * t * (1f - t) * arc
            r.sprite(x, y, z, 5f, 5f, TexKit.dot.full, blend = Blend.ADD, emissive = 1f, alpha = 0.7f, tint = Pal.YELLOW)
        }
    }

    /** Golden carp glint under the water; a hooked golden one lights the water. */
    private fun drawGlints(r: Renderer3D) {
        val glow = TexKit.glow.full
        for (i in 0 until FishingTuning.FISH_SLOTS) {
            val f = fish[i]
            if (f.species != 3 || f.mode == FishMode.GONE || f.mode == FishMode.LANDED) continue
            val a = 0.35f + 0.25f * sin(time * 6f + i)
            r.sprite(f.x, f.y + 4f, f.z, 46f, 46f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.GOLD)
            if ((time * 3f + i).rem(1f) < 0.25f) {
                r.sprite(f.x + sin(time * 7f + i) * 8f, 2f, f.z + cos(time * 5f) * 6f, 8f, 8f, TexKit.dot.full, blend = Blend.ADD, emissive = 1f, alpha = 0.9f, tint = Pal.YELLOW)
            }
        }
        if (phase == CastPhase.LANDING) {
            val k = clamp01(phaseT / FishingTuning.LAND_SECONDS)
            val gold = hooked >= 0 && fish[hooked].species == 3
            r.sprite(showX, showY, showZ, 120f * k, 120f * k, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f * k, tint = if (gold) Pal.GOLD else Pal.CREAM)
            // Sparkles circling the catch as it is held up.
            for (n in 0 until 8) {
                val ang = time * 2.4f + n * 0.7854f
                val rad = 30f + 26f * k
                r.sprite(showX + cos(ang) * rad, showY + sin(ang) * rad * 0.55f + 6f, showZ + 4f, 6f, 6f, TexKit.dot.full, blend = Blend.ADD, emissive = 1f, alpha = 0.9f * k * (0.5f + 0.5f * sin(time * 9f + n)), tint = if (gold) Pal.YELLOW else Pal.WHITE)
            }
        }
    }

    // ---------------------------------------------------------------- HUD overlay (field units)

    private val rimStroke = Stroke(width = 4f)
    private val arrowStroke = Stroke(width = 6f, cap = StrokeCap.Round)
    private val thinStroke = Stroke(width = 2f)

    private fun drawHud(scope: DrawScope) {
        drawReel(scope)
        drawGauge(scope)
        drawStatus(scope)
        if (catchShowT > 0f) {
            val a = clamp01(catchShowT / 0.3f)
            ArcadeFont.drawCentered(scope, catchName, GAME_W / 2f, 92f, 2.4f, Color(Pal.CREAM), a)
        }
        if (snapFlash > 0f) {
            scope.drawRect(Color(Pal.RED), Offset(0f, 0f), Size(GAME_W, GAME_H), alpha = snapFlash * 0.25f)
        }
    }

    private fun drawReel(scope: DrawScope) {
        with(scope) {
            val c = Offset(REEL_CX, REEL_CY)
            val biting = phase == CastPhase.WAIT && suitor >= 0 && fish[suitor].mode == FishMode.BITE
            if (crank.held) {
                drawCircle(Color(Pal.CYAN), REEL_R + 14f, c, alpha = 0.18f + 0.08f * sin(time * 8f))
            }
            drawCircle(Color(0xFF0E1622.toInt()), REEL_R + 8f, c, alpha = 0.88f)
            drawCircle(Color(0xFF8A96AA.toInt()), REEL_R + 6f, c, style = rimStroke)
            // A knurled edge (a tick every 15 degrees) and a sheen catching the low sun.
            for (k in 0 until 24) {
                val ang = k * TAU / 24f
                drawLine(Color(0xFF56647E.toInt()), Offset(REEL_CX + cos(ang) * (REEL_R + 2f), REEL_CY + sin(ang) * (REEL_R + 2f)), Offset(REEL_CX + cos(ang) * (REEL_R + 9f), REEL_CY + sin(ang) * (REEL_R + 9f)), strokeWidth = 2f)
            }
            drawArc(Color.White, 195f, 75f, false, Offset(REEL_CX - REEL_R - 6f, REEL_CY - REEL_R - 6f), Size((REEL_R + 6f) * 2f, (REEL_R + 6f) * 2f), alpha = 0.3f, style = rimStroke)
            drawCircle(Color(0xFF26344A.toInt()), REEL_R * 0.66f, c)
            drawCircle(Color(0xFF3E5270.toInt()), REEL_R * 0.66f, c, style = thinStroke)
            val a = crank.angle
            for (k in 0 until 3) {
                val s = a + k * TAU / 3f
                drawLine(Color(0xFF6A7C98.toInt()), c, Offset(REEL_CX + cos(s) * REEL_R * 0.62f, REEL_CY + sin(s) * REEL_R * 0.62f), strokeWidth = 5f)
            }
            // The handle arm and its knob.
            val kx = REEL_CX + cos(a) * REEL_R * 0.84f
            val ky = REEL_CY + sin(a) * REEL_R * 0.84f
            drawLine(Color(0xFFB8C4D8.toInt()), c, Offset(kx, ky), strokeWidth = 8f)
            drawCircle(Color(0xFF1A1A22.toInt()), 14f, Offset(kx + 1.5f, ky + 2f), alpha = 0.6f)
            drawCircle(Color(Pal.CREAM), 13f, Offset(kx, ky))
            drawCircle(Color(Pal.TAN), 7f, Offset(kx, ky))
            drawCircle(Color(0xFFC8D0E0.toInt()), 7f, c)
            val hint = biting || (phase == CastPhase.FIGHT && !crank.held) || ((phase == CastPhase.WAIT || phase == CastPhase.FLIGHT) && !crank.held)
            if (hint) {
                // A clockwise arrow round the reel.
                val pulse = 0.55f + 0.45f * sin(time * (if (biting) 16f else 6f))
                val col = Color(if (biting) Pal.YELLOW else Pal.CYAN)
                val rr = REEL_R + 20f
                val sweepFrom = (time * (if (biting) 400f else 120f)).rem(360f)
                drawArc(col, sweepFrom, 250f, false, Offset(REEL_CX - rr, REEL_CY - rr), Size(rr * 2f, rr * 2f), alpha = pulse, style = arrowStroke)
                val end = (sweepFrom + 250f) * (PI.toFloat() / 180f)
                val hx = REEL_CX + cos(end) * rr
                val hy = REEL_CY + sin(end) * rr
                val tx = -sin(end)
                val ty = cos(end)
                drawLine(col, Offset(hx, hy), Offset(hx - tx * 12f + cos(end) * 8f, hy - ty * 12f + sin(end) * 8f), strokeWidth = 6f, alpha = pulse)
                drawLine(col, Offset(hx, hy), Offset(hx - tx * 12f - cos(end) * 8f, hy - ty * 12f - sin(end) * 8f), strokeWidth = 6f, alpha = pulse)
            }
        }
    }

    private fun drawGauge(scope: DrawScope) {
        with(scope) {
            val x = 12f
            val top = 440f
            val w = 20f
            val h = 180f
            val charging = phase == CastPhase.CHARGE
            val fighting = phase == CastPhase.FIGHT
            val a = if (charging || fighting) 1f else 0.45f
            fun yFor(v: Float) = top + h - clamp01(v / 1.1f) * h
            drawRect(Color(0xFF0E1622.toInt()), Offset(x - 4f, top - 4f), Size(w + 8f, h + 8f), alpha = 0.85f * a)
            if (charging) {
                drawRect(Color(Pal.DARKGRAY), Offset(x, top), Size(w, h), alpha = a)
                val fy = top + h - power * h
                drawRect(Color(Pal.mix(Pal.YELLOW, Pal.ORANGE, power)), Offset(x, fy), Size(w, top + h - fy))
                ArcadeFont.drawCentered(this, "POWER", x + w / 2f + 8f, top + h + 8f, 1.3f, Color(Pal.YELLOW))
            } else {
                // Zones: slack (blue), safe (green), snap (red).
                drawRect(Color(Pal.SKY), Offset(x, yFor(FishingTuning.SLACK_TENSION)), Size(w, top + h - yFor(FishingTuning.SLACK_TENSION)), alpha = 0.45f * a)
                drawRect(Color(Pal.GREEN), Offset(x, yFor(FishingTuning.SAFE_HIGH)), Size(w, yFor(FishingTuning.SLACK_TENSION) - yFor(FishingTuning.SAFE_HIGH)), alpha = 0.4f * a)
                drawRect(Color(Pal.RED), Offset(x, top), Size(w, yFor(FishingTuning.SAFE_HIGH) - top), alpha = 0.5f * a)
                if (fighting) {
                    val ty = yFor(tension)
                    val col = when {
                        tension > FishingTuning.SAFE_HIGH -> Pal.RED
                        tension < FishingTuning.SLACK_TENSION -> Pal.SKY
                        else -> Pal.LIME
                    }
                    drawRect(Color(col), Offset(x + 4f, ty), Size(w - 8f, top + h - ty))
                    drawRect(Color(col), Offset(x - 2f, ty - 6f), Size(w + 4f, 12f), alpha = 0.22f)
                    drawRect(Color.White, Offset(x - 5f, ty - 2f), Size(w + 10f, 4f))
                    if (strainT > 0f && (time * 12f).toInt() % 2 == 0) {
                        drawRect(Color(Pal.RED), Offset(x - 4f, top - 4f), Size(w + 8f, h + 8f), style = rimStroke)
                    }
                }
                ArcadeFont.drawCentered(this, "LINE", x + w / 2f + 4f, top + h + 8f, 1.3f, Color(Pal.LAVENDER), a)
            }
        }
    }

    private fun drawStatus(scope: DrawScope) {
        if (timeUp) return
        val blink = 0.6f + 0.4f * sin(time * 6f)
        val biting = phase == CastPhase.WAIT && suitor >= 0 && fish[suitor].mode == FishMode.BITE
        val text: String
        var color = Pal.WHITE
        var size = 2f
        var alpha = 1f
        when {
            biting -> {
                text = "STRIKE! CRANK!"
                color = Pal.YELLOW
                size = 3.2f
                alpha = if ((time * 10f).toInt() % 2 == 0) 1f else 0.5f
            }
            phase == CastPhase.IDLE -> {
                text = "HOLD ON THE POND TO CAST"
                alpha = blink
            }
            phase == CastPhase.CHARGE -> {
                text = "LET GO TO CAST!"
                color = Pal.YELLOW
            }
            phase == CastPhase.FLIGHT || phase == CastPhase.WAIT -> {
                text = if (crank.held) "WAIT FOR A BITE..." else "HOLD THE REEL"
                color = Pal.CREAM
            }
            phase == CastPhase.FIGHT -> {
                when {
                    tension > FishingTuning.SAFE_HIGH || pull == Pull.RUN -> {
                        text = "IT'S PULLING! EASE OFF!"
                        color = Pal.RED
                        alpha = blink
                    }
                    pull == Pull.WARN -> {
                        text = "WATCH IT..."
                        color = Pal.ORANGE
                    }
                    tension < FishingTuning.SLACK_TENSION -> {
                        text = "KEEP REELING!"
                        color = Pal.SKY
                        alpha = blink
                    }
                    else -> {
                        text = "REEL IT IN!"
                        color = Pal.LIME
                    }
                }
            }
            else -> return
        }
        ArcadeFont.drawCentered(scope, text, GAME_W / 2f, 40f, size, Color(color), alpha)
    }

    // ---------------------------------------------------------------- simulation-test hooks

    internal val botPhase: CastPhase get() = phase
    internal val botPower: Float get() = power
    internal val botTension: Float get() = tension
    internal val botPull: Pull get() = pull
    internal val botLineDist: Float get() = lineD
    internal val botHooked: Int get() = hooked
    internal val botSuitor: Int get() = suitor
    internal val botBiting: Boolean get() = phase == CastPhase.WAIT && suitor >= 0 && fish[suitor].mode == FishMode.BITE
    internal val botLureX: Float get() = lureX
    internal val botLureZ: Float get() = lureZ
    internal val botCrankRate: Float get() = crank.rate
    internal val botCrankHeld: Boolean get() = crank.held
    internal val botCasts: Int get() = casts
    internal val botLanded: Int get() = landed
    internal val botSnaps: Int get() = snaps
    internal val botThrown: Int get() = thrown
    internal val botMissed: Int get() = missed
    internal val botTooSoon: Int get() = tooSoon
    internal val botFailsafeTrips: Int get() = failsafeTrips
    internal val botLastCatch: Int get() = lastCatch
    internal val botSlots: Int get() = fish.size
    internal fun botFishX(i: Int): Float = fish[i].x
    internal fun botFishY(i: Int): Float = fish[i].y
    internal fun botFishZ(i: Int): Float = fish[i].z
    internal fun botFishSpecies(i: Int): Int = fish[i].species
    internal fun botFishMode(i: Int): FishMode = fish[i].mode
    internal fun botFishWeight(i: Int): Float = fish[i].weight
    internal fun botPoints(species: Int, weight: Float): Int = catchPoints(species, weight)

    /** Where a cast held at field x [fx] and let go at [pow] lands: world (x, z) into [out]. */
    internal fun botLanding(fx: Float, pow: Float, out: FloatArray) = landingSpot(fx, pow, out)

    /** The field x to hold and the power to let go at to land a cast on world ([x], [z]): into [out]. */
    internal fun botAimFor(x: Float, z: Float, out: FloatArray) {
        val a = atan2(x - DOCK_X, DOCK_Z - z)
        out[0] = GAME_W / 2f + a / (FishingTuning.MAX_AIM_DEG * (PI.toFloat() / 180f)) * 160f
        out[1] = clamp01((hypot(x - DOCK_X, z - DOCK_Z) - FishingTuning.MIN_CAST) / (FishingTuning.MAX_CAST - FishingTuning.MIN_CAST))
    }

    /** Test setup: makes fish [i] a swimming [species] of [weight] pounds at world ([x], [z]). */
    internal fun botPlaceFish(i: Int, species: Int, weight: Float, x: Float, z: Float) {
        val f = fish[i]
        f.species = species
        f.weight = weight
        f.scale = 1f
        f.x = x
        f.z = z
        f.vx = 0f
        f.vz = 0f
        f.mode = FishMode.SWIM
        f.spookT = 0f
    }

    /** Test setup: hooks fish [i] as if the strike had just gone in (the line out to it). */
    internal fun botHookNow(i: Int) {
        castId = -1L
        releaseSuitor()
        lureX = fish[i].x
        lureZ = fish[i].z
        hook(i)
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        // A pond at golden hour in miniature: a warm sky and low sun over a tree line, the water
        // glittering along the path to the sun, fish circling under the surface, and a rod whose
        // float dives every few seconds (rings, a splash and a jumping fish). Fireflies blink.
        val wf = w.toFloat()
        val hf = h.toFloat()
        val horizon = hf * 0.3f
        // Sky in warm bands, the sun with its halo, and the tree line.
        p.fill(0f, 0f, wf, horizon, Color(0xFF3D68AE.toInt()))
        p.fill(0f, hf * 0.09f, wf, hf * 0.08f, Color(0xFF8A5A98.toInt()))
        p.fill(0f, hf * 0.17f, wf, hf * 0.08f, Color(0xFFE0905E.toInt()))
        p.fill(0f, hf * 0.25f, wf, horizon - hf * 0.25f + 0.1f, Color(0xFFF3B27A.toInt()))
        val sunX = wf * 0.74f
        val sunY = hf * 0.2f
        p.disc(sunX, sunY, 4.4f, Color(0xFFFFB068.toInt()), 0.16f)
        p.disc(sunX, sunY, 2.9f, Color(0xFFFFD08C.toInt()), 0.30f)
        p.disc(sunX, sunY, 1.5f, Color(0xFFFFF4D0.toInt()))
        for (k in 0 until 9) {
            val tx = k * wf / 8f
            val th = hf * (0.10f + 0.06f * hash01(k, 3))
            p.disc(tx, horizon - th * 0.2f, 2.3f + hash01(k, 4), Color(0xFF1F4A38.toInt()))
            p.fill(tx - 1.6f, horizon - th, 3.2f, th, Color(0xFF1F4A38.toInt()), 0.9f)
        }
        p.fill(0f, horizon - 0.6f, wf, 0.6f, Color(0xFFFFC080.toInt()), 0.5f)
        // The pond, a little darker towards the viewer, and a warm sheen under the sun.
        for (y in 0 until (hf - horizon).toInt() + 1) {
            val t = y / (hf - horizon)
            p.fill(0f, horizon + y, wf, 1f, Color(Pal.mix(0xFF2E8088.toInt(), 0xFF135060.toInt(), t)))
        }
        p.fill(sunX - 2.2f, horizon, 4.4f, hf - horizon, Color(0xFFFFB060.toInt()), 0.10f)
        // Glitter down the sun's path, and drifting streaks of ripple.
        for (i in 0 until 16) {
            val gy = horizon + 0.8f + hash01(i, 21) * (hf - horizon - 1.6f)
            val spread = 0.8f + (gy - horizon) * 0.22f
            val gx = sunX - (gy - horizon) * 0.3f + (hash01(i, 22) - 0.5f) * 2f * spread
            val tw = sin(time * (1.5f + hash01(i, 23) * 2.5f) + hash01(i, 24) * 9f)
            if (tw > 0.2f) p.fill(gx, gy, 0.9f + hash01(i, 25) * 1.4f, 0.28f, Color(0xFFFFF0D0.toInt()), tw)
        }
        for (k in 0 until 4) {
            val y = hf * (0.42f + k * 0.14f)
            val x = ((time * (3f + k) + k * 7f) % (wf + 6f)) - 4f
            p.fill(x, y, 3f, 0.4f, Color(0xFF8FE3E0.toInt()), alpha = 0.5f)
        }
        // Fish circling under the surface.
        val colors = ATTRACT_FISH
        for (k in 0 until 3) {
            val a = time * (0.7f + k * 0.25f) + k * 2.1f
            val fx = wf * 0.45f + cos(a) * wf * (0.18f + k * 0.07f)
            val fy = hf * 0.68f + sin(a) * hf * (0.12f + k * 0.03f)
            val dir = if (-sin(a) >= 0f) 1f else -1f
            p.disc(fx, fy, 1.3f, Color(colors[k]), alpha = 0.85f)
            p.disc(fx - dir * 1.6f, fy, 0.8f, Color(colors[k]), alpha = 0.85f)
            p.disc(fx, fy + 0.9f, 1.7f, Color.Black, alpha = 0.10f)
        }
        // A rod from the corner, its line to a bobbing float that dives every few seconds.
        val cycle = time % 4f
        val dive = if (cycle > 3f) 1.2f else 0f
        val bx = wf * 0.62f
        val by = hf * 0.55f + sin(time * 3f) * 0.3f + dive
        p.fill(wf - 5f, 1f, 0.6f, hf * 0.4f, Color(0xFF0E1838.toInt()))
        val steps = 8
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            p.fill(wf - 4.7f + (bx - wf + 4.7f) * t, 1.5f + (by - 1.5f) * t + t * (1f - t) * 2f, 0.35f, 0.35f, Color.White, alpha = 0.8f)
        }
        val ringK = (time * 0.5f) % 1f
        p.frame(bx - 1f - 2.4f * ringK, by + 0.1f - 0.5f * ringK, 2f + 4.8f * ringK, 1f + 1f * ringK, Color.White, alpha = 0.45f * (1f - ringK))
        p.disc(bx, by, 0.9f, Color(Pal.RED))
        p.fill(bx - 0.9f, by, 1.8f, 0.6f, Color.White)
        if (cycle > 3f) {
            // Splash and a jumping fish.
            val j = (cycle - 3f)
            p.disc(bx - 2f + j * 4f, by - sin(j * PI.toFloat()) * 5f, 1.4f, Color(Pal.GOLD))
            p.frame(bx - 3f, by - 0.5f, 6f, 1.5f, Color.White, alpha = 1f - j)
            for (d in 0 until 5) p.px(bx - 1.5f + d * 0.8f, by - sin(j * PI.toFloat()) * (1.5f + d * 0.6f), Color(0xFFEAFBFF.toInt()), 1f - j)
        }
        // Fireflies over the far bank.
        for (i in 0 until 6) {
            val fx = wf * hash01(i, 31) + sin(time * 0.6f + i) * 1.2f
            val fy = horizon - 1.2f - hash01(i, 32) * hf * 0.12f + cos(time * 0.5f + i * 1.7f) * 0.6f
            val b = sin(time * 2.2f + i * 1.9f) * 0.5f + 0.5f
            p.px(fx, fy, Color(0xFFDFFF80.toInt()), b * b)
        }
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("GONE FISHING", wf / 2f, 1f, Color(Pal.YELLOW), tiny = true)
    }
}
