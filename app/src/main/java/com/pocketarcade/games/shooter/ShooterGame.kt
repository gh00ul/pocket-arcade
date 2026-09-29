package com.pocketarcade.games.shooter

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.easeOutCubic
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Camera3D
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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Difficulty and payout knobs for the light-gun shootout. */
object ShooterTuning {
    const val ROUND_SECONDS = 55f
    /** When waves 2 and 3 start, and when the boss arrives (seconds into the round). */
    const val WAVE2_AT = 13f
    const val WAVE3_AT = 26f
    const val BOSS_AT = 38f

    // Per wave: wave 1, wave 2, wave 3, the boss wave.
    /** Seconds between spawns (±25 %). */
    val SPAWN_INTERVAL = floatArrayOf(1.0f, 0.78f, 0.6f, 1.4f)
    /** Most figures out of cover at once. */
    val MAX_UP = intArrayOf(2, 3, 4, 2)
    /** How long a figure stays out of cover (±15 %). */
    val UP_TIME = floatArrayOf(2.3f, 1.9f, 1.55f, 1.7f)
    /** Chance a bandit shoots back, and how long its warning lasts before the shot. */
    val GUNNER_CHANCE = floatArrayOf(0.15f, 0.28f, 0.4f, 0.35f)
    val TELL_TIME = floatArrayOf(1.05f, 0.9f, 0.75f, 0.75f)
    /** Chance a figure is a civilian (don't shoot). */
    val CIVILIAN_CHANCE = floatArrayOf(0.12f, 0.15f, 0.18f, 0.12f)
    /** Chance a spawn is a drone flying across instead, and how fast drones fly (world units/s). */
    val DRONE_CHANCE = floatArrayOf(0f, 0.25f, 0.3f, 0.5f)
    val DRONE_SPEED = floatArrayOf(110f, 120f, 150f, 150f)
    /** Chance a drone is the gold bonus one; one is also guaranteed between GOLD_FROM and GOLD_TO. */
    const val GOLD_CHANCE = 0.06f
    const val GOLD_SPEED = 250f
    const val GOLD_FROM = 15f
    const val GOLD_TO = 34f

    /** Shots in the gun, and how long a reload takes. */
    const val CLIP = 6
    const val RELOAD_SECONDS = 0.75f
    /** Health; losing the last heart knocks you down (no shooting) for DOWN_SECONDS, then it refills. */
    const val HEARTS = 3
    const val DOWN_SECONDS = 1.6f

    const val GOON_POINTS = 50
    /** Extra for dropping a bandit while it takes aim at you. */
    const val QUICK_DRAW_BONUS = 25
    const val DRONE_POINTS = 75
    const val GOLD_POINTS = 300
    const val CIVILIAN_PENALTY = 100
    const val HURT_PENALTY = 50
    /** Every COMBO_STEP hits in a row adds one to the multiplier, up to MAX_MULT. */
    const val COMBO_STEP = 5
    const val MAX_MULT = 4

    const val BOSS_HP = 100
    const val BOSS_WEAK_DAMAGE = 6
    const val BOSS_BODY_DAMAGE = 1
    const val BOSS_WEAK_POINTS = 40
    const val BOSS_BODY_POINTS = 5
    const val BOSS_KILL_POINTS = 1500
    const val BOSS_ENTER_SECONDS = 2.2f
    /** Seconds between the boss's cannon shots, how long it charges, and the eye damage that interrupts a charge. */
    const val BOSS_CHARGE_EVERY = 3.4f
    const val BOSS_CHARGE_TIME = 1.3f
    const val BOSS_STAGGER_DAMAGE = 12
    /** Each pod's core is open for BOSS_OPEN of every BOSS_CYCLE seconds (the two pods alternate). */
    const val BOSS_CYCLE = 3.2f
    const val BOSS_OPEN = 2f
    /** Points per second left on the clock when the boss goes down, and tickets for beating it. */
    const val CLEAR_BONUS_PER_SECOND = 20
    const val BOSS_BONUS_TICKETS = 5

    /** Accuracy bonus at the end: this many points times accuracy squared. */
    const val ACCURACY_BONUS_MAX = 600

    const val POINTS_PER_TICKET = 400
    const val BASE_TICKETS = 2
}

/**
 * The shootout's world: a camera looking straight down -z at a bank on a night street, the spots
 * where figures pop out of cover, and the cover itself. The camera never pitches, so every face at
 * one depth lands on screen as an axis-aligned rectangle; hit tests use that and never need render().
 */
internal object ShooterWorld {
    const val EYE_X = 180f
    const val EYE_Y = 130f
    const val EYE_Z = 700f
    const val FOV = 52f

    /** Spawn spots: windows upstairs (row 0), far crates (1), mid barrels and car (2), the near sandbags (3). */
    val SPOT_X = floatArrayOf(40f, 180f, 320f, 45f, 180f, 315f, 100f, 264f, 125f, 235f)
    val SPOT_Z = floatArrayOf(-125f, -125f, -125f, 5f, 0f, 5f, 185f, 185f, 400f, 400f)
    /** Top of the cover in front of each spot: figures rise from behind it. */
    val SPOT_TOP = floatArrayOf(192f, 192f, 192f, 62f, 58f, 62f, 58f, 58f, 46f, 46f)
    val SPOT_ROW = intArrayOf(0, 0, 0, 1, 1, 1, 2, 2, 3, 3)
    /** Which rows each wave uses (bit per row). */
    val WAVE_ROWS = intArrayOf(0b0110, 0b1111, 0b1111, 0b1100)
    const val SPOTS = 10

    /** How much of a figure shows above its cover when it's out. */
    const val EXPOSE = 56f

    const val FACADE_Z = -100f
    const val ROOM_Z = -165f
    const val WINDOW_Y0 = 190f
    const val WINDOW_Y1 = 272f
    const val WINDOW_HALF = 40f
    val WINDOW_X = floatArrayOf(40f, 180f, 320f)

    /** Cover faces as x0, y0, x1, y1, z (the face towards the camera). */
    val COVER = floatArrayOf(
        -120f, 0f, 480f, 192f, FACADE_Z,
        -120f, 190f, 0f, 272f, FACADE_Z,
        80f, 190f, 140f, 272f, FACADE_Z,
        220f, 190f, 280f, 272f, FACADE_Z,
        360f, 190f, 480f, 272f, FACADE_Z,
        -120f, 272f, 480f, 376f, FACADE_Z,
        0f, 190f, 80f, 272f, ROOM_Z,
        140f, 190f, 220f, 272f, ROOM_Z,
        280f, 190f, 360f, 272f, ROOM_Z,
        5f, 0f, 85f, 62f, 60f,
        140f, 0f, 220f, 58f, 50f,
        275f, 0f, 355f, 62f, 60f,
        58f, 0f, 142f, 58f, 229f,
        190f, 0f, 330f, 36f, 240f,
        224f, 36f, 308f, 58f, 240f,
        -200f, 0f, 560f, 46f, 444f,
    )
    val COVERS = COVER.size / 5

    // The mothership, in its own frame (centre of the hull).
    const val BOSS_RADIUS = 100f
    const val BOSS_Z = 20f
    const val BOSS_Y = 300f
    const val BOSS_POD_X = 72f
    const val BOSS_POD_Y = -14f
    const val BOSS_POD_Z = 50f
    const val BOSS_EYE_Y = -24f
    const val BOSS_EYE_Z = 80f
    const val BOSS_CORE_R = 13f

    /** Top of the reload strip along the bottom of the screen (field units). */
    const val RELOAD_Y = 590f
}

/**
 * SHOOTOUT: a light-gun shooter. Robo-bandits pop out from behind crates, barrels, a car, the
 * sandbags and the bank's windows; tap one to shoot it — the shot lands exactly where the finger
 * does. Six shots, then tap the RELOAD strip (or anywhere off the screen). Bandits that take aim
 * flash a red lock-on ring and then shoot you; civilians with their hands up must not be shot;
 * gold drones pay big. Three waves ramp up to a mothership boss with a health bar and weak points.
 */
class ShooterGame : BaseMiniGame() {
    override val id = "shooter"
    override val title = "SHOOTOUT"
    override val marquee = "SHOOT"
    override val instructions = listOf(
        "TAP TO SHOOT WHERE YOU TAP",
        "6 SHOTS: TAP THE RELOAD BAR",
        "RED RINGS FIRE BACK: HIT FIRST",
        "DON'T SHOOT HANDS-UP CIVILIANS",
        "GOLD DRONES PAY BIG, THEN BOSS",
    )
    override val look = CabinetLook(body = Pal.NAVY, trim = Pal.ORANGE, glow = Pal.ORANGE, shape = CabinetShape.GUN)
    override val cabinet: CabinetDesign get() = ShooterCabinet
    override val roundSeconds = ShooterTuning.ROUND_SECONDS

    private companion object {
        // Target kinds.
        const val GOON = 0
        const val CIVILIAN = 1
        const val DRONE = 2
        const val GOLD = 3

        // Target states.
        const val OFF = 0
        const val RISE = 1
        const val UP = 2
        const val TELL = 3
        const val DUCK = 4
        const val HIT = 5
        const val FLY = 6
        const val FLEE = 7

        // Boss states.
        const val B_OFF = 0
        const val B_ENTER = 1
        const val B_FIGHT = 2
        const val B_DYING = 3
        const val B_LEAVE = 4
        const val B_GONE = 5

        // What a shot hits.
        const val SHOT_NOTHING = 0
        const val SHOT_TARGET = 1
        const val SHOT_BOSS = 2
        const val SHOT_COVER = 3

        const val TARGETS = 12
        const val POINTERS = 6
        const val DECALS = 40
        const val RISE_TIME = 0.16f
        const val DUCK_TIME = 0.2f
        const val HIT_TIME = 0.45f
        const val FLEE_TIME = 0.6f
        const val AFTER_SHOT = 0.35f
        const val BOSS_DYING_SECONDS = 1.6f
        const val BOSS_LEAVE_SECONDS = 1.2f
        /** However the round ends, it's over this long after the clock stops. */
        const val SETTLE_FAILSAFE = 4f
        const val DECAL_LIFE = 8f

        // Look (presentation only): brass casings thrown from the gun, and flashes where bullets strike.
        const val CASINGS = 6
        const val CASING_LIFE = 0.9f
        const val CASING_GRAVITY = 150f
        const val STRIKES = 3
        const val STRIKE_LIFE = 0.16f
        /** How long the gun's smoke hangs, and the lamp shaft's drifting dust motes. */
        const val SMOKE_LIFE = 0.9f
        const val MOTES = 9
        /** Ground mist: how fast it drifts (texels a second), how tall its banks are and how thick they are at most. */
        const val MIST_SPEED = 9f
        const val MIST_HEIGHT = 74f
        const val MIST_ALPHA = 0.20f
        /** Clouds: drift speed in texels a second and how strongly they show. */
        const val CLOUD_SPEED = 1.6f
        const val CLOUD_ALPHA = 0.42f
        /** Extra reach around a target so near misses by a fingertip still count (field units). */
        const val SLOP = 5f

        val SPARKS = intArrayOf(Pal.WHITE, Pal.YELLOW, Pal.ORANGE)
        val DEBRIS = intArrayOf(Pal.DARKGRAY, Pal.GRAY, Pal.DARKRED, 0xFF2A2433.toInt())
        val DUST = intArrayOf(0xFFB8A48E.toInt(), 0xFF8C7866.toInt(), Pal.GRAY)
        val BOOM = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.RED, Pal.WHITE, Pal.DARKGRAY)
        val GOLDS = intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE)
        val CIV = intArrayOf(Pal.CYAN, Pal.WHITE, Pal.RED)
        val SHELLS = intArrayOf(Pal.GOLD, 0xFFC8A040.toInt())
        val WAVE_LABELS = arrayOf("WAVE 1", "WAVE 2", "WAVE 3", "WARNING: BOSS!")
        val MULT_LABELS = arrayOf("", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8")
        const val HEART = "♥"
    }

    // ---------------------------------------------------------------- state

    private class Target {
        var kind = GOON
        var state = OFF
        var spot = -1
        var x = 0f
        var y = 0f
        var z = 0f
        /** Seconds in the current state. */
        var t = 0f
        /** Seconds since it could first be shot (for bots). */
        var seen = 0f
        var upTime = 0f
        var gunner = false
        var fireDelay = 0f
        var tellTime = 0f
        /** Seconds since this bandit fired (for its muzzle flash), or a large number. */
        var shotT = 99f
        var vx = 0f
        var baseY = 0f
        var phase = 0f
        var spin = 0f
        var tellHit = false
    }

    private val targets = Array(TARGETS) { Target() }
    private val spotBusy = BooleanArray(ShooterWorld.SPOTS)
    private var wave = 0
    private var spawnT = 0f
    private var goldAt = 0f
    private var goldSpawned = false

    private var ammo = ShooterTuning.CLIP
    private var reloadT = 0f
    private var hearts = ShooterTuning.HEARTS
    private var downT = 0f
    private var combo = 0
    private var bestCombo = 0
    private var shots = 0
    private var hits = 0
    private var hurts = 0
    private var civiliansShot = 0
    private var tallied = false
    private var cleared = false
    private var settleT = 0f
    /** Test hook: keeps the boss away. */
    private var holdBoss = false

    private var bossState = B_OFF
    private var bossT = 0f
    private var bossClock = 0f
    private var bossHp = 0
    private var bossX = ShooterWorld.EYE_X
    private var bossY = ShooterWorld.BOSS_Y
    private var bossChargeT = 0f
    private var bossCharging = false
    private var bossChargeLeft = 0f
    private var bossStagger = 0
    private var bossHitT = 99f
    private var bossBoomT = 0f

    // Feel.
    private var recoil = 0f
    private var muzzleT = 99f
    private var shellT = -1f
    private var hurtFlash = 0f
    private var bannerT = 99f
    private var emptyT = 99f
    private var boomT = 99f
    private var boomX = 0f
    private var boomY = 0f
    private var boomZ = 0f
    private var lastShotX = GAME_W / 2f
    private var lastShotY = 300f
    private var lastShotT = 99f
    private var multShown = 1

    // Pointers: each id fires once, when it lands.
    private val ptrId = LongArray(POINTERS)
    private val ptrUsed = BooleanArray(POINTERS)
    private val ptrX = FloatArray(POINTERS)
    private val ptrY = FloatArray(POINTERS)

    // Presentation only: brass casings in flight and bullet-strike flashes. Written by fire() and
    // the step, read by render(); they never touch the rules or the game's random numbers (the
    // variation in a casing's flight comes from hashing the shot count).
    private val casingX = FloatArray(CASINGS)
    private val casingY = FloatArray(CASINGS)
    private val casingZ = FloatArray(CASINGS)
    private val casingVX = FloatArray(CASINGS)
    private val casingVY = FloatArray(CASINGS)
    private val casingVZ = FloatArray(CASINGS)
    private val casingAge = FloatArray(CASINGS) { CASING_LIFE }
    private val casingSpin = FloatArray(CASINGS)
    private var casingNext = 0
    private val strikeX = FloatArray(STRIKES)
    private val strikeY = FloatArray(STRIKES)
    private val strikeZ = FloatArray(STRIKES)
    private val strikeAge = FloatArray(STRIKES) { STRIKE_LIFE }
    private var strikeNext = 0

    // Bullet holes, a ring buffer of world points.
    private val decalX = FloatArray(DECALS)
    private val decalY = FloatArray(DECALS)
    private val decalZ = FloatArray(DECALS)
    private val decalAge = FloatArray(DECALS) { DECAL_LIFE }
    private var decalNext = 0

    // ---------------------------------------------------------------- the camera and aiming

    /**
     * The camera every hit test uses, fixed for the whole game (the rendered camera kicks with the
     * recoil; this one never moves, so aiming is exactly where the finger lands).
     */
    private val aim = Camera3D().apply {
        lookAt(ShooterWorld.EYE_X, ShooterWorld.EYE_Y, ShooterWorld.EYE_Z, ShooterWorld.EYE_X, ShooterWorld.EYE_Y, 0f, ShooterWorld.FOV * PI.toFloat() / 180f, GAME_W.toInt(), GAME_H.toInt())
    }
    private val pt = FloatArray(3)
    private val ray = FloatArray(3)

    // Cover faces on screen: x0, y0, x1, y1, view depth.
    private val coverRect = FloatArray(ShooterWorld.COVERS * 5)

    init {
        val c = ShooterWorld.COVER
        for (i in 0 until ShooterWorld.COVERS) {
            val z = c[i * 5 + 4]
            aim.project(c[i * 5], c[i * 5 + 3], z, pt)
            coverRect[i * 5] = pt[0]
            coverRect[i * 5 + 1] = pt[1]
            coverRect[i * 5 + 4] = pt[2]
            aim.project(c[i * 5 + 2], c[i * 5 + 1], z, pt)
            coverRect[i * 5 + 2] = pt[0]
            coverRect[i * 5 + 3] = pt[1]
        }
    }

    /** Field units per world unit at depth [z] (the camera looks straight down -z). */
    private fun scaleAtZ(z: Float): Float = aim.focal / (ShooterWorld.EYE_Z - z)

    /** The nearest cover face under field point ([fx], [fy]): its index, or -1. */
    private fun coverAt(fx: Float, fy: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until ShooterWorld.COVERS) {
            val o = i * 5
            if (fx < coverRect[o] || fx > coverRect[o + 2] || fy < coverRect[o + 1] || fy > coverRect[o + 3]) continue
            if (coverRect[o + 4] < bestD) {
                bestD = coverRect[o + 4]
                best = i
            }
        }
        return best
    }

    /** Where the ray through field point ([fx], [fy]) crosses the plane z = [z]; world x, y into [ray]. */
    private fun rayAtZ(fx: Float, fy: Float, z: Float) {
        val px = (fx - aim.cx) / aim.focal
        val py = -(fy - aim.cy) / aim.focal
        val dx = aim.fx + aim.rx * px + aim.ux * py
        val dy = aim.fy + aim.ry * px + aim.uy * py
        val dz = aim.fz + aim.rz * px + aim.uz * py
        val t = (z - aim.ez) / dz
        ray[0] = aim.ex + dx * t
        ray[1] = aim.ey + dy * t
        ray[2] = z
    }

    // ---------------------------------------------------------------- round flow

    override fun reset() {
        for (tg in targets) {
            tg.state = OFF
            tg.spot = -1
            tg.shotT = 99f
        }
        spotBusy.fill(false)
        wave = 0
        spawnT = 0.8f
        goldAt = rng.range(ShooterTuning.GOLD_FROM, ShooterTuning.GOLD_TO)
        goldSpawned = false
        ammo = ShooterTuning.CLIP
        reloadT = 0f
        hearts = ShooterTuning.HEARTS
        downT = 0f
        combo = 0
        bestCombo = 0
        shots = 0
        hits = 0
        hurts = 0
        civiliansShot = 0
        tallied = false
        cleared = false
        settleT = 0f
        holdBoss = false
        bossState = B_OFF
        bossT = 0f
        bossClock = 0f
        bossHp = 0
        bossX = ShooterWorld.EYE_X
        bossY = ShooterWorld.BOSS_Y
        bossCharging = false
        bossHitT = 99f
        recoil = 0f
        muzzleT = 99f
        shellT = -1f
        hurtFlash = 0f
        bannerT = 0f
        emptyT = 99f
        boomT = 99f
        lastShotT = 99f
        multShown = 1
        ptrUsed.fill(false)
        decalAge.fill(DECAL_LIFE)
        decalNext = 0
        casingAge.fill(CASING_LIFE)
        strikeAge.fill(STRIKE_LIFE)
    }

    override fun ticketsFor(score: Int): Int = ShooterTuning.BASE_TICKETS + score / ShooterTuning.POINTS_PER_TICKET

    override fun onTimeUp() {
        tally()
        endEverything()
    }

    /** Sends every target back into cover (or away) and the boss up and out: nothing scores any more. */
    private fun endEverything() {
        for (tg in targets) {
            when (tg.state) {
                RISE, UP, TELL -> leave(tg)
                FLY -> {
                    tg.state = FLEE
                    tg.t = 0f
                }
                else -> Unit
            }
        }
        if (bossState == B_ENTER || bossState == B_FIGHT) {
            bossState = B_LEAVE
            bossT = 0f
            bossCharging = false
        }
    }

    override fun isSettled(): Boolean {
        if (settleT > SETTLE_FAILSAFE) return true
        for (tg in targets) if (tg.state != OFF) return false
        return bossState == B_OFF || bossState == B_GONE
    }

    /** The end-of-round tally: the accuracy bonus. Paid once, when the clock stops or the boss falls. */
    private fun tally() {
        if (tallied) return
        tallied = true
        if (shots == 0) return
        val acc = hits.toFloat() / shots
        val bonus = (ShooterTuning.ACCURACY_BONUS_MAX * acc * acc / 10f).toInt() * 10
        popups.add("ACCURACY ${(acc * 100f).toInt()}%", GAME_W / 2f, 330f, Color(Pal.CYAN), size = 3.5f, life = 1.6f, rise = 20f)
        if (bonus > 0) addScore(bonus, GAME_W / 2f, 370f, Color(Pal.YELLOW), "BONUS +$bonus")
    }

    private val progressWave: Int
        get() = when {
            time < ShooterTuning.WAVE2_AT -> 0
            time < ShooterTuning.WAVE3_AT -> 1
            time < ShooterTuning.BOSS_AT -> 2
            else -> 3
        }

    override fun step(dt: Float) {
        recoil = (recoil - dt * 9f).coerceAtLeast(0f)
        muzzleT += dt
        lastShotT += dt
        emptyT += dt
        boomT += dt
        bannerT += dt
        bossHitT += dt
        hurtFlash = (hurtFlash - dt * 2.2f).coerceAtLeast(0f)
        if (shellT >= 0f) {
            shellT -= dt
            if (shellT < 0f) play(Sfx.SHELL, 0.5f, rng.range(0.9f, 1.15f))
        }
        if (reloadT > 0f) {
            reloadT -= dt
            if (reloadT <= 0f) {
                reloadT = 0f
                ammo = ShooterTuning.CLIP
                play(Sfx.BLIP, 0.35f, 1.6f)
            }
        }
        if (downT > 0f) {
            downT -= dt
            if (downT <= 0f) {
                downT = 0f
                hearts = ShooterTuning.HEARTS
                play(Sfx.SELECT, 0.6f)
            }
        }
        for (i in 0 until DECALS) if (decalAge[i] < DECAL_LIFE) decalAge[i] += dt
        for (i in 0 until CASINGS) {
            if (casingAge[i] >= CASING_LIFE) continue
            casingAge[i] += dt
            casingVY[i] -= CASING_GRAVITY * dt
            casingX[i] += casingVX[i] * dt
            casingY[i] += casingVY[i] * dt
            casingZ[i] += casingVZ[i] * dt
        }
        for (i in 0 until STRIKES) if (strikeAge[i] < STRIKE_LIFE) strikeAge[i] += dt
        if (timeUp || endedEarly) settleT += dt

        if (!timeUp && !cleared) {
            val w = progressWave
            if (w != wave) {
                wave = w
                bannerT = 0f
                if (w == 3) {
                    play(Sfx.ALARM, 0.8f)
                    if (!holdBoss) startBoss()
                } else {
                    play(Sfx.SELECT, 0.7f, 1.2f)
                }
            }
            if (!goldSpawned && time >= goldAt) spawnDrone(true)
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnT = ShooterTuning.SPAWN_INTERVAL[wave] * rng.range(0.75f, 1.25f)
                if (rng.chance(ShooterTuning.DRONE_CHANCE[wave])) {
                    spawnDrone(rng.chance(ShooterTuning.GOLD_CHANCE))
                } else if (figuresOut() < ShooterTuning.MAX_UP[wave]) {
                    spawnFigure()
                }
            }
        }
        for (i in 0 until TARGETS) stepTarget(targets[i], dt)
        stepBoss(dt)
    }

    private fun figuresOut(): Int {
        var n = 0
        for (tg in targets) if (tg.spot >= 0 && tg.state != OFF) n++
        return n
    }

    private fun freeTarget(): Target? {
        for (tg in targets) if (tg.state == OFF) return tg
        return null
    }

    private fun spawnFigure() {
        val rows = ShooterWorld.WAVE_ROWS[wave]
        var free = 0
        for (s in 0 until ShooterWorld.SPOTS) if (!spotBusy[s] && (rows shr ShooterWorld.SPOT_ROW[s]) and 1 == 1) free++
        if (free == 0) return
        var pick = rng.nextInt(free)
        var spot = -1
        for (s in 0 until ShooterWorld.SPOTS) {
            if (spotBusy[s] || (rows shr ShooterWorld.SPOT_ROW[s]) and 1 == 0) continue
            if (pick == 0) {
                spot = s
                break
            }
            pick--
        }
        val civilian = rng.chance(ShooterTuning.CIVILIAN_CHANCE[wave])
        val gunner = !civilian && rng.chance(ShooterTuning.GUNNER_CHANCE[wave])
        val tg = freeTarget() ?: return
        popFigure(tg, spot, if (civilian) CIVILIAN else GOON, gunner, rising = true)
    }

    private fun popFigure(tg: Target, spot: Int, kind: Int, gunner: Boolean, rising: Boolean) {
        tg.kind = kind
        tg.spot = spot
        spotBusy[spot] = true
        tg.x = ShooterWorld.SPOT_X[spot]
        tg.z = ShooterWorld.SPOT_Z[spot]
        tg.y = if (rising) hiddenY(spot) else exposedY(spot)
        tg.state = if (rising) RISE else UP
        tg.t = 0f
        tg.seen = 0f
        tg.upTime = ShooterTuning.UP_TIME[wave] * rng.range(0.85f, 1.15f) * (if (kind == CIVILIAN) 1.1f else 1f)
        tg.gunner = gunner
        tg.fireDelay = rng.range(0.15f, 0.45f)
        tg.tellTime = ShooterTuning.TELL_TIME[wave]
        tg.shotT = 99f
        tg.tellHit = false
        tg.spin = 0f
        if (rising) play(Sfx.WHOOSH, 0.18f, rng.range(1.3f, 1.6f))
    }

    private fun spawnDrone(gold: Boolean) {
        val tg = freeTarget() ?: return
        if (gold) goldSpawned = true
        tg.kind = if (gold) GOLD else DRONE
        tg.spot = -1
        val fromLeft = rng.nextBoolean()
        val speed = if (gold) ShooterTuning.GOLD_SPEED else ShooterTuning.DRONE_SPEED[wave] * rng.range(0.85f, 1.2f)
        tg.x = if (fromLeft) -110f else 470f
        tg.vx = if (fromLeft) speed else -speed
        tg.z = if (wave == 3) rng.range(90f, 160f) else rng.range(-60f, 150f)
        tg.baseY = rng.range(260f, 350f)
        tg.y = tg.baseY
        tg.phase = rng.range(0f, 6.28f)
        tg.state = FLY
        tg.t = 0f
        tg.seen = 0f
        tg.spin = 0f
        tg.shotT = 99f
        play(Sfx.WHOOSH, if (gold) 0.5f else 0.25f, if (gold) 1.8f else 0.8f)
        if (gold) popups.add("GOLD DRONE!", GAME_W / 2f, 150f, Color(Pal.GOLD), size = 3.5f, life = 1.2f)
    }

    /** Feet height of a figure fully behind its cover (civilians' raised hands included). */
    private fun hiddenY(spot: Int) = ShooterWorld.SPOT_TOP[spot] - ShooterArt.FIGURE_H - 12f
    private fun exposedY(spot: Int) = ShooterWorld.SPOT_TOP[spot] - ShooterArt.FIGURE_H + ShooterWorld.EXPOSE

    private fun leave(tg: Target) {
        if (tg.spot >= 0) {
            tg.state = DUCK
            tg.t = 0f
        } else {
            tg.state = FLEE
            tg.t = 0f
        }
    }

    private fun free(tg: Target) {
        tg.state = OFF
        if (tg.spot >= 0) spotBusy[tg.spot] = false
        tg.spot = -1
    }

    private fun stepTarget(tg: Target, dt: Float) {
        if (tg.state == OFF) return
        tg.t += dt
        tg.shotT += dt
        if (hittable(tg)) tg.seen += dt
        when (tg.state) {
            RISE -> {
                val k = easeOutBack(clamp01(tg.t / RISE_TIME))
                tg.y = hiddenY(tg.spot) + (exposedY(tg.spot) - hiddenY(tg.spot)) * k
                if (tg.t >= RISE_TIME) {
                    tg.state = UP
                    tg.t = 0f
                    tg.y = exposedY(tg.spot)
                }
            }
            UP -> {
                if (tg.gunner && tg.shotT > 50f && tg.t >= tg.fireDelay && !timeUp && !cleared) {
                    tg.state = TELL
                    tg.t = 0f
                    play(Sfx.ALARM, 0.12f, 1.8f)
                } else if (tg.t >= tg.upTime) {
                    leave(tg)
                }
            }
            TELL -> if (tg.t >= tg.tellTime) enemyFires(tg)
            DUCK -> {
                val k = clamp01(tg.t / DUCK_TIME)
                tg.y = exposedY(tg.spot) + (hiddenY(tg.spot) - exposedY(tg.spot)) * k * k
                if (tg.t >= DUCK_TIME) free(tg)
            }
            HIT -> {
                if (tg.spot < 0) {
                    // A shot-down drone tumbles out of the sky.
                    tg.y -= (180f + tg.t * 500f) * dt
                    tg.x += tg.vx * 0.3f * dt
                    tg.spin += dt * 14f
                }
                if (tg.t >= HIT_TIME) free(tg)
            }
            FLY -> {
                tg.x += tg.vx * dt
                tg.phase += dt * (if (tg.kind == GOLD) 5f else 2.2f)
                tg.y = tg.baseY + sin(tg.phase) * (if (tg.kind == GOLD) 34f else 16f)
                tg.spin += dt * 3f
                if (tg.x < -150f || tg.x > 510f) free(tg)
            }
            FLEE -> {
                tg.y += 520f * dt
                tg.x += tg.vx * dt
                if (tg.t >= FLEE_TIME) free(tg)
            }
            else -> Unit
        }
    }

    /** A bandit's warning ran out: it fires. It stays out a moment longer, then ducks. */
    private fun enemyFires(tg: Target) {
        tg.state = UP
        tg.shotT = 0f
        tg.upTime = AFTER_SHOT
        tg.t = 0f
        project(tg.x + 14f, tg.y + 65f, tg.z + 36f)
        particles.burst(pt[0], pt[1], 8, 60f, 200f, SPARKS, 0.25f, 3f)
        hurt(pt[0], pt[1])
    }

    /** The player is hit by enemy fire. */
    private fun hurt(fromX: Float, fromY: Float) {
        play(Sfx.ENEMY_FIRE, 0.9f, rng.range(0.9f, 1.1f))
        if (downT > 0f || timeUp || cleared) return
        hurts++
        hearts--
        combo = 0
        hurtFlash = 1f
        shake.add(0.65f)
        fx.haptics.heavy()
        play(Sfx.THUD, 0.8f, 0.7f)
        addScore(-ShooterTuning.HURT_PENALTY, fromX, fromY - 20f, Color(Pal.RED), "HIT! -${ShooterTuning.HURT_PENALTY}")
        if (hearts <= 0) {
            hearts = 0
            downT = ShooterTuning.DOWN_SECONDS
            popups.add("DOWN!", GAME_W / 2f, 280f, Color(Pal.RED), size = 7f, life = 1.3f, rise = 10f)
            play(Sfx.LOSE, 0.7f)
        }
    }

    private fun startBoss() {
        bossState = B_ENTER
        bossT = 0f
        bossClock = 0f
        bossHp = ShooterTuning.BOSS_HP
        bossCharging = false
        bossChargeT = 2f
        bossStagger = 0
    }

    private fun stepBoss(dt: Float) {
        when (bossState) {
            B_ENTER -> {
                bossT += dt
                val k = easeOutCubic(clamp01(bossT / ShooterTuning.BOSS_ENTER_SECONDS))
                bossX = ShooterWorld.EYE_X
                bossY = ShooterWorld.BOSS_Y + 380f * (1f - k)
                if (bossT >= ShooterTuning.BOSS_ENTER_SECONDS) {
                    bossState = B_FIGHT
                    bossT = 0f
                }
            }
            B_FIGHT -> {
                bossT += dt
                bossClock += dt
                bossX = ShooterWorld.EYE_X + sin(bossClock * 0.6f) * 55f
                bossY = ShooterWorld.BOSS_Y + sin(bossClock * 1.4f) * 10f
                if (!bossCharging) {
                    bossChargeT -= dt
                    if (bossChargeT <= 0f) {
                        bossCharging = true
                        bossChargeLeft = ShooterTuning.BOSS_CHARGE_TIME
                        bossStagger = 0
                        play(Sfx.ALARM, 0.45f, 1.25f)
                    }
                } else {
                    bossChargeLeft -= dt
                    if (bossStagger >= ShooterTuning.BOSS_STAGGER_DAMAGE) {
                        bossCharging = false
                        bossChargeT = ShooterTuning.BOSS_CHARGE_EVERY
                        project(bossX, bossY + ShooterWorld.BOSS_EYE_Y, ShooterWorld.BOSS_Z + ShooterWorld.BOSS_EYE_Z)
                        popups.add("STAGGERED!", pt[0], pt[1] + 40f, Color(Pal.CYAN), size = 3.5f, life = 1f)
                        play(Sfx.BONK, 0.8f, 0.7f)
                    } else if (bossChargeLeft <= 0f) {
                        bossCharging = false
                        bossChargeT = ShooterTuning.BOSS_CHARGE_EVERY
                        project(bossX, bossY + ShooterWorld.BOSS_EYE_Y, ShooterWorld.BOSS_Z + ShooterWorld.BOSS_EYE_Z)
                        particles.burst(pt[0], pt[1], 24, 80f, 300f, BOOM, 0.4f, 5f)
                        hurt(pt[0], pt[1] + 40f)
                    }
                }
            }
            B_DYING -> {
                bossT += dt
                bossY -= dt * 40f
                bossBoomT -= dt
                if (bossBoomT <= 0f) {
                    bossBoomT = 0.18f
                    val ox = rng.range(-90f, 90f)
                    val oy = rng.range(-30f, 30f)
                    project(bossX + ox, bossY + oy, ShooterWorld.BOSS_Z + 60f)
                    particles.burst(pt[0], pt[1], 26, 60f, 320f, BOOM, 0.7f, 6f, grav = 120f)
                    play(Sfx.EXPLOSION, 0.6f, rng.range(0.8f, 1.2f))
                    boomAt(bossX + ox, bossY + oy, ShooterWorld.BOSS_Z + 60f)
                    shake.add(0.25f)
                }
                if (bossT >= BOSS_DYING_SECONDS) {
                    bossState = B_GONE
                    project(bossX, bossY, ShooterWorld.BOSS_Z)
                    particles.burst(pt[0], pt[1], 90, 80f, 520f, BOOM, 1.1f, 8f, grav = 160f)
                    particles.confetti(0f, 0f, GAME_W, 80)
                    play(Sfx.EXPLOSION, 1f, 0.7f)
                    play(Sfx.WIN, 0.9f)
                    fx.haptics.jackpot()
                    shake.add(0.9f)
                    endedEarly = true
                }
            }
            B_LEAVE -> {
                bossT += dt
                bossY += dt * 380f
                if (bossT >= BOSS_LEAVE_SECONDS) bossState = B_GONE
            }
            else -> Unit
        }
    }

    private fun boomAt(x: Float, y: Float, z: Float) {
        boomT = 0f
        boomX = x
        boomY = y
        boomZ = z
    }

    // ---------------------------------------------------------------- input

    override fun cancelInput() {
        ptrUsed.fill(false)
    }

    private fun pointer(id: Long): Int {
        for (k in 0 until POINTERS) if (ptrUsed[k] && ptrId[k] == id) return k
        return -1
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        when (type) {
            TouchType.DOWN -> {
                // Each finger fires once, when it lands; a repeated DOWN for a held id is ignored.
                // With every slot held there is nowhere to remember this id, so the finger is
                // ignored rather than left free to fire again on a repeated DOWN.
                if (pointer(id) >= 0) return
                for (k in 0 until POINTERS) if (!ptrUsed[k]) {
                    ptrUsed[k] = true
                    ptrId[k] = id
                    ptrX[k] = x
                    ptrY[k] = y
                    press(x, y)
                    return
                }
            }
            TouchType.MOVE -> {
                val k = pointer(id)
                if (k < 0) return
                ptrX[k] = x
                ptrY[k] = y
            }
            TouchType.UP -> {
                val k = pointer(id)
                if (k >= 0) ptrUsed[k] = false
            }
        }
    }

    private fun inReloadZone(x: Float, y: Float) = y >= ShooterWorld.RELOAD_Y || y < 0f || x < 0f || x > GAME_W || y > GAME_H

    private fun press(x: Float, y: Float) {
        if (timeUp || cleared) return
        if (inReloadZone(x, y)) {
            startReload()
            return
        }
        if (downT > 0f) {
            play(Sfx.DRY_FIRE, 0.5f, 0.8f)
            return
        }
        if (reloadT > 0f || ammo <= 0) {
            // Click. Nothing leaves the barrel, nothing scores.
            play(Sfx.DRY_FIRE, 0.9f)
            fx.haptics.tick()
            if (reloadT <= 0f) {
                emptyT = 0f
                popups.add("RELOAD!", x, y - 24f, Color(Pal.RED), size = 3f, life = 0.6f, rise = 30f)
            }
            return
        }
        fire(x, y)
    }

    private fun startReload() {
        if (reloadT > 0f || ammo >= ShooterTuning.CLIP || downT > 0f) return
        val spent = ShooterTuning.CLIP - ammo
        reloadT = ShooterTuning.RELOAD_SECONDS
        ammo = 0
        play(Sfx.RELOAD, 0.9f)
        fx.haptics.tick()
        for (k in 0 until spent) {
            particles.spawn(300f + k * 6f, ShooterWorld.RELOAD_Y - 4f, rng.range(-60f, 60f), rng.range(-240f, -140f), 0.7f, 4f, SHELLS[k % 2], grav = 900f, dragPerSec = 0.5f, kind = Particles.CONFETTI)
        }
    }

    private fun mult(): Int = (1 + combo / ShooterTuning.COMBO_STEP).coerceAtMost(ShooterTuning.MAX_MULT)

    private fun fire(x: Float, y: Float) {
        ammo--
        shots++
        recoil = 1f
        muzzleT = 0f
        shellT = 0.3f
        lastShotX = x
        lastShotY = y
        lastShotT = 0f
        play(Sfx.GUNSHOT, 0.9f, rng.range(0.92f, 1.08f))
        shake.add(0.12f)
        fx.haptics.tick()
        ejectCasing()

        when (resolve(x, y)) {
            SHOT_BOSS -> shootBoss(hitPart, x, y)
            SHOT_TARGET -> shootTarget(hitTarget!!, x, y)
            SHOT_COVER -> {
                miss()
                val z = ShooterWorld.COVER[hitCover * 5 + 4]
                rayAtZ(x, y, z)
                addDecal(ray[0], ray[1], z + 0.4f)
                strikeAt(ray[0], ray[1], z + 1.5f)
                particles.burst(x, y, 7, 40f, 160f, DUST, 0.45f, 3f, grav = 300f)
                particles.burst(x, y, 4, 80f, 220f, SPARKS, 0.15f, 2f)
                play(Sfx.RICOCHET, 0.35f, rng.range(0.85f, 1.3f))
            }
            else -> {
                miss()
                particles.burst(x, y, 5, 30f, 110f, DUST, 0.4f, 2.5f, grav = 200f)
            }
        }
    }

    /** Throws a brass casing out of the gun's right side (a little different every shot, from a hash of the shot count). */
    private fun ejectCasing() {
        val i = casingNext
        casingNext = (casingNext + 1) % CASINGS
        casingX[i] = ShooterWorld.EYE_X + 15.6f
        casingY[i] = ShooterWorld.EYE_Y - 21.5f
        casingZ[i] = ShooterWorld.EYE_Z - 80f
        casingVX[i] = 16f + hash01(shots, 21) * 12f
        casingVY[i] = 20f + hash01(shots, 22) * 16f
        casingVZ[i] = 5f + hash01(shots, 23) * 10f
        casingSpin[i] = 5f + hash01(shots, 24) * 9f
        casingAge[i] = 0f
    }

    /** A flash of sparks where a bullet struck cover, at a world point. */
    private fun strikeAt(x: Float, y: Float, z: Float) {
        val i = strikeNext
        strikeNext = (strikeNext + 1) % STRIKES
        strikeX[i] = x; strikeY[i] = y; strikeZ[i] = z
        strikeAge[i] = 0f
    }

    // What the last resolved shot hit.
    private var hitTarget: Target? = null
    private var hitPart = -1
    private var hitCover = -1

    /**
     * Works out what a shot at field point ([x], [y]) hits first along its ray: a target
     * ([hitTarget]), a part of the boss ([hitPart]), a piece of cover ([hitCover]) or nothing.
     * Pure hit-testing against the fixed aiming camera, with no side effects.
     */
    private fun resolve(x: Float, y: Float): Int {
        var best: Target? = null
        var bestD = Float.MAX_VALUE
        for (tg in targets) {
            if (!hittable(tg)) continue
            val d = targetDepthUnder(tg, x, y)
            if (d < bestD) {
                bestD = d
                best = tg
            }
        }
        val part = if (bossState == B_FIGHT) bossPartUnder(x, y) else -1
        val bossD = if (part >= 0) pt[2] else Float.MAX_VALUE
        val cover = coverAt(x, y)
        val coverD = if (cover >= 0) coverRect[cover * 5 + 4] else Float.MAX_VALUE
        hitTarget = best
        hitPart = part
        hitCover = cover
        return when {
            part >= 0 && bossD <= bestD && bossD <= coverD -> SHOT_BOSS
            best != null && bestD <= coverD -> SHOT_TARGET
            cover >= 0 -> SHOT_COVER
            else -> SHOT_NOTHING
        }
    }

    private fun miss() {
        if (combo >= ShooterTuning.COMBO_STEP) popups.add("COMBO LOST", GAME_W / 2f, 120f, Color(Pal.LAVENDER), size = 2.2f, life = 0.7f, rise = 10f)
        combo = 0
        multShown = 1
    }

    private fun addDecal(x: Float, y: Float, z: Float) {
        decalX[decalNext] = x
        decalY[decalNext] = y
        decalZ[decalNext] = z
        decalAge[decalNext] = 0f
        decalNext = (decalNext + 1) % DECALS
    }

    private fun hittable(tg: Target): Boolean = when (tg.state) {
        RISE -> tg.t > RISE_TIME * 0.35f
        UP, TELL, FLY -> true
        DUCK -> tg.t < DUCK_TIME * 0.5f
        else -> false
    }

    /** Projects a world point into [pt] with the aiming camera. */
    private fun project(x: Float, y: Float, z: Float) = aim.project(x, y, z, pt)

    /**
     * View depth of [tg] if field point ([fx], [fy]) is on it and not hidden by cover in front of
     * it, else Float.MAX_VALUE.
     */
    private fun targetDepthUnder(tg: Target, fx: Float, fy: Float): Float {
        val s = scaleAtZ(tg.z)
        val cx: Float
        val y0: Float
        val y1: Float
        val hw: Float
        if (tg.spot >= 0) {
            val civ = tg.kind == CIVILIAN
            hw = if (civ) 22f else 19f
            y0 = tg.y + 44f
            y1 = tg.y + if (civ) 110f else ShooterArt.FIGURE_H
            cx = tg.x
        } else {
            hw = if (tg.kind == GOLD) 20f else ShooterArt.DRONE_R + 3f
            y0 = tg.y - 12f
            y1 = tg.y + 14f
            cx = tg.x
        }
        val sx0 = ShooterWorld.EYE_X + (cx - hw - ShooterWorld.EYE_X) * s - SLOP
        val sx1 = ShooterWorld.EYE_X + (cx + hw - ShooterWorld.EYE_X) * s + SLOP
        val sy0 = aim.cy - (y1 - ShooterWorld.EYE_Y) * s - SLOP
        val sy1 = aim.cy - (y0 - ShooterWorld.EYE_Y) * s + SLOP
        if (fx < sx0 || fx > sx1 || fy < sy0 || fy > sy1) return Float.MAX_VALUE
        val depth = ShooterWorld.EYE_Z - tg.z
        // Hidden behind something nearer (the figure's own cover, usually)?
        val c = coverAt(fx, fy)
        if (c >= 0 && coverRect[c * 5 + 4] < depth) return Float.MAX_VALUE
        return depth
    }

    private fun shootTarget(tg: Target, x: Float, y: Float) {
        when (tg.kind) {
            CIVILIAN -> {
                civiliansShot++
                combo = 0
                multShown = 1
                addScore(-ShooterTuning.CIVILIAN_PENALTY, x, y - 20f, Color(Pal.RED), "-${ShooterTuning.CIVILIAN_PENALTY}")
                popups.add("DON'T SHOOT CIVILIANS!", GAME_W / 2f, 150f, Color(Pal.RED), size = 2.6f, life = 1.2f, rise = 20f)
                play(Sfx.BUZZER, 0.8f)
                fx.haptics.heavy()
                shake.add(0.3f)
                hurtFlash = maxOf(hurtFlash, 0.5f)
                particles.burst(x, y, 10, 40f, 160f, CIV, 0.5f, 3f, grav = 200f)
                // They dive for cover at once (and can't be hit twice on the way down).
                tg.state = DUCK
                tg.t = DUCK_TIME * 0.5f
                return
            }
            GOON -> {
                val quick = tg.state == TELL
                val m = mult()
                val pts = (ShooterTuning.GOON_POINTS + if (quick) ShooterTuning.QUICK_DRAW_BONUS else 0) * m
                addScore(pts, x, y - 26f, Color(if (quick) Pal.CYAN else Pal.WHITE), if (quick) "QUICK +$pts" else null)
                play(Sfx.CLINK, 0.8f, rng.range(0.7f, 0.9f))
                play(Sfx.THUD, 0.5f, 1.3f)
                fx.haptics.hit()
                shake.add(0.12f)
                particles.burst(x, y, 14, 80f, 300f, SPARKS, 0.35f, 3f, kind = Particles.SPARKLE)
                particles.burst(x, y, 10, 60f, 220f, DEBRIS, 0.7f, 4f, grav = 500f)
                tg.tellHit = quick
                rayAtZ(x, y, tg.z + 10f)
                strikeAt(ray[0], ray[1], tg.z + 12f)
                tg.state = HIT
                tg.t = 0f
            }
            DRONE, GOLD -> {
                val gold = tg.kind == GOLD
                val m = mult()
                val pts = (if (gold) ShooterTuning.GOLD_POINTS else ShooterTuning.DRONE_POINTS) * m
                addScore(pts, x, y - 26f, Color(if (gold) Pal.GOLD else Pal.ORANGE), if (gold) "BONUS +$pts" else null)
                play(Sfx.EXPLOSION, if (gold) 0.6f else 0.45f, if (gold) 1.4f else 1.25f)
                if (gold) {
                    play(Sfx.JACKPOT, 0.8f)
                    fx.haptics.win()
                    particles.burst(x, y, 40, 80f, 340f, GOLDS, 0.9f, 4f, kind = Particles.SPARKLE)
                } else {
                    fx.haptics.hit()
                }
                particles.burst(x, y, 22, 60f, 280f, BOOM, 0.55f, 5f, grav = 150f)
                shake.add(0.2f)
                boomAt(tg.x, tg.y, tg.z)
                tg.state = HIT
                tg.t = 0f
            }
        }
        hit()
    }

    /** A shot that landed on something hostile: accuracy and the combo. */
    private fun hit() {
        hits++
        combo++
        bestCombo = maxOf(bestCombo, combo)
        val m = mult()
        if (m > multShown) {
            multShown = m
            popups.add("COMBO ${MULT_LABELS[m]}!", GAME_W / 2f, 120f, Color(Pal.LIME), size = 4f, life = 1f, rise = 20f)
            play(Sfx.SELECT, 0.7f, 1f + m * 0.1f)
        }
    }

    // ---------------------------------------------------------------- the boss's parts

    /** Boss part under a field point: 0/1 the pod cores, 2 the eye, 3 the hull; -1 none. Its depth goes in pt[2]. */
    private fun bossPartUnder(fx: Float, fy: Float): Int {
        for (k in 0 until 3) {
            if (!bossPartWorld(k)) continue
            val wx = pt[0]
            val wy = pt[1]
            val wz = pt[2]
            val s = scaleAtZ(wz)
            project(wx, wy, wz)
            val r = ShooterWorld.BOSS_CORE_R * s + SLOP
            val dx = fx - pt[0]
            val dy = fy - pt[1]
            if (dx * dx + dy * dy <= r * r) return k
        }
        val hz = ShooterWorld.BOSS_Z + 60f
        val s = scaleAtZ(hz)
        project(bossX, bossY, hz)
        val dx = (fx - pt[0]) / (ShooterWorld.BOSS_RADIUS * s + SLOP)
        val dy = (fy - pt[1]) / (34f * s + SLOP)
        if (dx * dx + dy * dy <= 1f) return 3
        project(bossX, bossY + 30f, hz)
        val ddx = fx - pt[0]
        val ddy = fy - pt[1]
        val r = 26f * s + SLOP
        if (ddx * ddx + ddy * ddy <= r * r) {
            pt[2] = ShooterWorld.EYE_Z - hz
            return 3
        }
        return -1
    }

    /** World centre of boss part [k] (0/1 pods, 2 eye) into pt; false while it can't be hit (a closed pod). */
    private fun bossPartWorld(k: Int): Boolean {
        bossPartPos(k)
        return k == 2 || podOpen(k)
    }

    private fun bossPartPos(k: Int) {
        if (k == 2) {
            pt[0] = bossX
            pt[1] = bossY + ShooterWorld.BOSS_EYE_Y
            pt[2] = ShooterWorld.BOSS_Z + ShooterWorld.BOSS_EYE_Z
        } else {
            pt[0] = bossX + (if (k == 0) -ShooterWorld.BOSS_POD_X else ShooterWorld.BOSS_POD_X)
            pt[1] = bossY + ShooterWorld.BOSS_POD_Y
            pt[2] = ShooterWorld.BOSS_Z + ShooterWorld.BOSS_POD_Z
        }
    }

    private fun podOpen(k: Int): Boolean =
        ((bossClock + k * ShooterTuning.BOSS_CYCLE / 2f) % ShooterTuning.BOSS_CYCLE) < ShooterTuning.BOSS_OPEN

    /** The eye is a weak point only while the cannon charges. */
    private fun weak(part: Int): Boolean = part < 2 || (part == 2 && bossCharging)

    private fun shootBoss(part: Int, x: Float, y: Float) {
        val m = mult()
        bossHitT = 0f
        if (weak(part)) {
            bossHp -= ShooterTuning.BOSS_WEAK_DAMAGE
            if (part == 2) bossStagger += ShooterTuning.BOSS_WEAK_DAMAGE
            addScore(ShooterTuning.BOSS_WEAK_POINTS * m, x, y - 26f, Color(Pal.YELLOW), "CRIT +${ShooterTuning.BOSS_WEAK_POINTS * m}")
            play(Sfx.BONK, 0.8f, rng.range(1.1f, 1.3f))
            play(Sfx.CLINK, 0.6f, 0.6f)
            fx.haptics.hit()
            shake.add(0.18f)
            particles.burst(x, y, 20, 80f, 320f, SPARKS, 0.45f, 4f, kind = Particles.SPARKLE)
        } else {
            bossHp -= ShooterTuning.BOSS_BODY_DAMAGE
            addScore(ShooterTuning.BOSS_BODY_POINTS * m, x, y - 20f, Color(Pal.LIGHTGRAY))
            play(Sfx.RICOCHET, 0.5f, rng.range(1f, 1.4f))
            particles.burst(x, y, 6, 60f, 200f, SPARKS, 0.2f, 2f)
        }
        hit()
        if (bossHp <= 0) killBoss()
    }

    private fun killBoss() {
        bossHp = 0
        bossState = B_DYING
        bossT = 0f
        bossBoomT = 0f
        bossCharging = false
        cleared = true
        project(bossX, bossY, ShooterWorld.BOSS_Z)
        addScore(ShooterTuning.BOSS_KILL_POINTS, pt[0], pt[1], Color(Pal.GOLD), "BOSS DOWN +${ShooterTuning.BOSS_KILL_POINTS}")
        val secs = ceil(timeLeft).toInt().coerceAtLeast(0)
        if (secs > 0) {
            val bonus = secs * ShooterTuning.CLEAR_BONUS_PER_SECOND
            addScore(bonus, GAME_W / 2f, 250f, Color(Pal.LIME), "TIME BONUS +$bonus")
        }
        bonusTickets += ShooterTuning.BOSS_BONUS_TICKETS
        popups.add("STAGE CLEAR!", GAME_W / 2f, 200f, Color(Pal.YELLOW), size = 5.5f, life = 2f, rise = 10f)
        play(Sfx.JACKPOT, 0.9f)
        fx.haptics.heavy()
        tally()
        endEverything()
    }

    // ---------------------------------------------------------------- 3D presentation

    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(ShooterWorld.EYE_X, ShooterWorld.EYE_Y, ShooterWorld.EYE_Z, ShooterWorld.EYE_X, ShooterWorld.EYE_Y, 0f, ShooterWorld.FOV)
    }
    private val xf = Xform()
    private val xf2 = Xform()
    private val xf3 = Xform()

    private val signLight = PointLight(180f, 330f, -40f, 1f, 0.3f, 0.7f, 380f, 0.8f)
    private val streetLight = PointLight(180f, 260f, 330f, 1f, 0.72f, 0.45f, 520f, 0.55f)
    private val windowLights = Array(3) { PointLight(ShooterWorld.WINDOW_X[it], 236f, -135f, 1f, 0.75f, 0.45f, 80f, 0.9f) }
    private val muzzleLight = PointLight(0f, 0f, 0f, 1f, 0.85f, 0.5f, 260f, 0f)
    private val boomLight = PointLight(0f, 0f, 0f, 1f, 0.6f, 0.25f, 320f, 0f)
    private val bossLight = PointLight(0f, 0f, 0f, 0.3f, 1f, 0.9f, 260f, 0f)
    private val tellLight = PointLight(0f, 0f, 0f, 1f, 0.15f, 0.1f, 120f, 0f)

    /** The street lamp itself (the fill light above is the general glow of the street). */
    private val lampLight = PointLight(36f, 228f, -46f, 1f, 0.8f, 0.5f, 250f, 0.9f)
    /** Sparks where a bullet strikes: a brief warm light on the cover around it. */
    private val strikeLight = PointLight(0f, 0f, 0f, 1f, 0.85f, 0.55f, 110f, 0f)
    /** Red light over the whole street when the player is hit. */
    private val hurtLight = PointLight(ShooterWorld.EYE_X, ShooterWorld.EYE_Y, ShooterWorld.EYE_Z - 260f, 1f, 0.12f, 0.1f, 700f, 0f)

    private val thin by lazy { Stroke(2f) }
    private val thick by lazy { Stroke(3f) }

    /** Wrapping views of the cloud and mist textures, made once (drawing must not allocate). */
    private val cloudRegion: Region by lazy { ShooterArt.clouds.region(wrap = true) }
    private val mistRegion: Region by lazy { ShooterArt.mist.region(wrap = true) }

    /** Reduce motion turns flashes down to a third of their strength (persistent glow is unchanged). */
    private val fxK: Float get() = 0.35f + 0.65f * ScreenShake.intensity

    override fun render(scope: DrawScope) {
        // The recoil kicks the view up and back a touch (drawing only; aiming never moves).
        val kick = recoil * recoil * ScreenShake.intensity
        stage.look(
            ShooterWorld.EYE_X, ShooterWorld.EYE_Y + kick * 2f, ShooterWorld.EYE_Z + kick * 6f,
            ShooterWorld.EYE_X, ShooterWorld.EYE_Y + kick * 16f, 0f, ShooterWorld.FOV,
        )
        val r = stage.begin()
        lightScene(r)
        r.gradient(0xFF05040E.toInt(), 0xFF2A1848.toInt())
        drawSky(r)
        ShooterScene.model.draw(r)
        drawAtmosphere(r)
        drawDecals(r)
        for (tg in targets) drawTarget(r, tg)
        drawBoss(r)
        drawStrikes(r)
        drawGun(r)
        drawCasings(r)
        stage.present()
        drawHud(scope)
    }

    private fun lightScene(r: Renderer3D) {
        val l = r.lighting
        l.ambR = 0.34f; l.ambG = 0.32f; l.ambB = 0.46f
        l.setDirection(-0.35f, 0.8f, 0.55f)
        l.dirR = 0.34f; l.dirG = 0.34f; l.dirB = 0.5f
        l.points.clear()
        signLight.intensity = 0.75f + 0.1f * sin(time * 9f) * (if (hash01((time * 3f).toInt(), 5) > 0.93f) 3f else 0.3f)
        l.points += signLight
        l.points += streetLight
        for (w in windowLights) l.points += w
        // The lamp buzzes: now and then it dips (a hash of the clock, so it never needs the game's dice).
        lampLight.intensity = if (hash01((time * 9f).toInt(), 17) > 0.965f) 0.35f else 0.9f
        l.points += lampLight
        if (hurtFlash > 0f) {
            hurtLight.intensity = 0.9f * hurtFlash * fxK
            l.points += hurtLight
        }
        var strike = -1
        for (i in 0 until STRIKES) if (strikeAge[i] < STRIKE_LIFE && (strike < 0 || strikeAge[i] < strikeAge[strike])) strike = i
        if (strike >= 0) {
            strikeLight.x = strikeX[strike]; strikeLight.y = strikeY[strike]; strikeLight.z = strikeZ[strike] + 25f
            strikeLight.intensity = 1.5f * (1f - strikeAge[strike] / STRIKE_LIFE)
            l.points += strikeLight
        }
        if (muzzleT < 0.08f) {
            muzzleLight.x = ShooterWorld.EYE_X + 10f
            muzzleLight.y = ShooterWorld.EYE_Y - 10f
            muzzleLight.z = ShooterWorld.EYE_Z - 120f
            muzzleLight.intensity = 1.6f * (1f - muzzleT / 0.08f)
            l.points += muzzleLight
        }
        if (boomT < 0.4f) {
            boomLight.x = boomX; boomLight.y = boomY; boomLight.z = boomZ + 30f
            boomLight.intensity = 2f * (1f - boomT / 0.4f)
            l.points += boomLight
        }
        if (bossState == B_ENTER || bossState == B_FIGHT || bossState == B_DYING || bossState == B_LEAVE) {
            bossLight.x = bossX; bossLight.y = bossY - 40f; bossLight.z = ShooterWorld.BOSS_Z + 120f
            if (bossCharging) {
                bossLight.r = 1f; bossLight.g = 0.2f; bossLight.b = 0.15f
                bossLight.intensity = 1f + (1f - bossChargeLeft / ShooterTuning.BOSS_CHARGE_TIME) * 1.2f
            } else {
                bossLight.r = 0.3f; bossLight.g = 1f; bossLight.b = 0.9f
                bossLight.intensity = 0.8f
            }
            l.points += bossLight
        }
        // The nearest bandit taking aim lights up red.
        var tell: Target? = null
        for (tg in targets) if (tg.state == TELL && (tell == null || tg.t > tell.t)) tell = tg
        if (tell != null) {
            tellLight.x = tell.x; tellLight.y = tell.y + 80f; tellLight.z = tell.z + 40f
            tellLight.intensity = 0.8f + 0.6f * sin(time * 30f)
            l.points += tellLight
        }
    }

    /**
     * Clouds drifting over the moon, in front of the far backdrop. Drawn before the set so they
     * are laid down (see-through things blend in the order they are recorded) behind the nearer
     * skyline that is part of it.
     */
    private fun drawSky(r: Renderer3D) {
        val cu = time * CLOUD_SPEED
        r.quad(
            -220f, 640f, -395f, 580f, 640f, -395f, 580f, 380f, -395f, -220f, 380f, -395f, cloudRegion, 0f, 0f, 1f,
            u0 = cu, v0 = 0f, u1 = cu + 192f, v1 = 32f, blend = Blend.ALPHA, emissive = 0.4f, alpha = CLOUD_ALPHA, cull = false, tint = 0xFF8C76C4.toInt(),
        )
    }

    /**
     * The air of the street: banks of mist rolling along the
     * ground, the lamp's dust, a halo round the neon sign and the lit windows, and the pools
     * of light they spill onto the street. All additive or alpha, all a function of the clock.
     */
    private fun drawAtmosphere(r: Renderer3D) {
        val glow = TexKit.glow.full
        val fz = ShooterWorld.FACADE_Z
        // Ground mist: two banks at different depths drifting opposite ways.
        val m0 = time * MIST_SPEED
        r.quad(
            -400f, MIST_HEIGHT, -70f, 760f, MIST_HEIGHT, -70f, 760f, 0.2f, -70f, -400f, 0.2f, -70f, mistRegion, 0f, 0f, 1f,
            u0 = m0, v0 = 0f, u1 = m0 + 384f, v1 = 32f, blend = Blend.ALPHA, emissive = 0.55f, alpha = MIST_ALPHA, cull = false, tint = 0xFF8E80C8.toInt(),
        )
        r.quad(
            -400f, MIST_HEIGHT * 0.8f, 120f, 760f, MIST_HEIGHT * 0.8f, 120f, 760f, 0.2f, 120f, -400f, 0.2f, 120f, mistRegion, 0f, 0f, 1f,
            u0 = -m0 * 0.7f, v0 = 0f, u1 = -m0 * 0.7f + 384f, v1 = 32f, blend = Blend.ALPHA, emissive = 0.5f, alpha = MIST_ALPHA * 0.8f, cull = false, tint = 0xFF7C70B4.toInt(),
        )
        // The neon sign: a halo behind it and a pool of pink light on the street, both with the
        // sign's occasional stutter (a hash of the clock).
        val flick = if (hash01((time * 11f).toInt(), 7) > 0.94f) 0.5f else 1f
        r.sprite(180f, 320f, fz + 3f, 260f, 150f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.30f * flick, tint = Pal.PINK)
        r.sprite(180f, 320f, fz + 3f, 140f, 84f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.26f * flick, tint = 0xFFFF9AD0.toInt())
        r.flat(180f, 40f, 0.5f, 320f, 230f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.10f * flick, tint = Pal.PINK)
        // The lit windows glow a little into the night.
        for (k in 0 until 3) r.sprite(ShooterWorld.WINDOW_X[k], 232f, fz + 4f, 120f, 108f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.16f, tint = 0xFFFFB060.toInt())
        // The lamp's pool of light on the pavement, and dust drifting in its shaft.
        r.flat(36f, -40f, 0.5f, 180f, 150f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.16f, tint = 0xFFFFC070.toInt())
        for (i in 0 until MOTES) {
            val ph = time * 0.25f + i * 1.7f
            val y = 30f + hash01(i, 2) * 195f + sin(ph) * 8f
            val half = (7f + (238f - y) / 238f * 63f) * 0.8f
            val x = 36f + (hash01(i, 1) - 0.5f) * 2f * half + sin(ph * 1.3f) * 5f
            val a = 0.25f + 0.25f * sin(time * 1.7f + i * 2.3f)
            r.sprite(x, y, -48f, 2.6f, 2.6f, TexKit.dot.full, blend = Blend.ADD, emissive = 1f, alpha = a, tint = 0xFFFFE0A0.toInt())
        }
    }

    /** Flashes where bullets struck, and the shock ring of a drone or boss explosion. */
    private fun drawStrikes(r: Renderer3D) {
        val flash = ShooterArt.flash.full
        val glow = TexKit.glow.full
        for (i in 0 until STRIKES) {
            val age = strikeAge[i]
            if (age >= STRIKE_LIFE) continue
            val k = age / STRIKE_LIFE
            val size = 14f + 26f * k
            r.sprite(strikeX[i], strikeY[i], strikeZ[i], size, size, flash, roll = i * 1.3f + age * 8f, blend = Blend.ADD, emissive = 1.6f, alpha = 1f - k, tint = Pal.YELLOW)
            r.sprite(strikeX[i], strikeY[i], strikeZ[i] - 1f, 44f + 30f * k, 44f + 30f * k, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f * (1f - k), tint = Pal.ORANGE)
        }
        if (boomT < 0.5f) {
            val k = boomT / 0.5f
            val size = 30f + 380f * k
            r.sprite(boomX, boomY, boomZ + 40f, size, size, ShooterArt.shock.full, blend = Blend.ADD, emissive = 1.4f, alpha = (1f - k) * 0.8f * fxK, tint = Pal.ORANGE)
        }
    }

    /** Brass casings tumbling out of the gun. */
    private fun drawCasings(r: Renderer3D) {
        for (i in 0 until CASINGS) {
            val age = casingAge[i]
            if (age >= CASING_LIFE) continue
            val k = ((CASING_LIFE - age) / 0.2f).coerceIn(0f, 1f)
            val a = casingSpin[i] * age
            xf.set(casingX[i], casingY[i], casingZ[i], yaw = a, pitch = a * 0.7f, roll = a * 1.3f, scale = k)
            ShooterArt.shell.draw(r, xf = xf)
        }
    }

    private fun drawDecals(r: Renderer3D) {
        val hole = ShooterArt.hole.full
        for (i in 0 until DECALS) {
            val age = decalAge[i]
            if (age >= DECAL_LIFE) continue
            val a = clamp01((DECAL_LIFE - age) / 2f)
            val x = decalX[i]
            val y = decalY[i]
            val z = decalZ[i]
            val h = 3.4f
            r.quad(x - h, y + h, z, x + h, y + h, z, x + h, y - h, z, x - h, y - h, z, hole, 0f, 0f, 1f, blend = Blend.ALPHA, alpha = a, cull = false)
        }
    }

    private fun drawTarget(r: Renderer3D, tg: Target) {
        if (tg.state == OFF) return
        val glow = TexKit.glow.full
        if (tg.spot >= 0) {
            val model = if (tg.kind == CIVILIAN) ShooterArt.civilian else ShooterArt.goon
            if (tg.state == HIT) {
                // Knocked back and down behind the cover.
                val k = clamp01(tg.t / HIT_TIME)
                xf.set(tg.x, tg.y - k * k * 60f, tg.z - k * 10f, yaw = (if (tg.spot % 2 == 0) 0.4f else -0.4f) * k, pitch = -1.3f * easeOutCubic(k))
            } else {
                val sway = sin(time * 3f + tg.spot) * 0.05f
                xf.set(tg.x, tg.y, tg.z, yaw = sway, roll = sway * 0.4f)
            }
            model.draw(r, xf = xf)
            if (tg.kind == CIVILIAN && tg.state != DUCK) {
                // A green "don't shoot" halo over their head.
                val hy = tg.y + 122f
                r.sprite(tg.x, hy, tg.z + 4f, 36f, 36f, ShooterArt.ring.full, blend = Blend.ADD, emissive = 1.2f, alpha = 0.9f, tint = Pal.GREEN, roll = time * 1.5f)
                r.sprite(tg.x, hy, tg.z + 3f, 60f, 60f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.35f, tint = Pal.GREEN)
            }
            if (tg.kind == GOON && tg.state == TELL) {
                // The lock-on ring closes in on the bandit taking aim, and a warning badge blinks.
                val k = clamp01(tg.t / tg.tellTime)
                val size = 150f - 100f * k
                val cx = tg.x
                val cy = tg.y + 76f
                r.sprite(cx, cy, tg.z + 30f, size, size, ShooterArt.ring.full, blend = Blend.ADD, emissive = 1.5f, alpha = 0.6f + 0.4f * k, tint = Pal.RED, roll = -time * 4f)
                r.sprite(tg.x + 14f, tg.y + 65f, tg.z + 38f, 22f + 20f * k, 22f + 20f * k, glow, blend = Blend.ADD, emissive = 1.5f, alpha = 0.5f + 0.5f * k, tint = Pal.RED)
                if ((time * 10f).toInt() % 2 == 0) r.sprite(cx, tg.y + ShooterArt.FIGURE_H + 20f, tg.z + 20f, 26f, 26f, ShooterArt.warn.full, emissive = 1.4f, depthBias = 1.05f)
            }
            if (tg.shotT < 0.09f) {
                r.sprite(tg.x + 14f, tg.y + 65f, tg.z + 42f, 50f, 50f, ShooterArt.flash.full, blend = Blend.ADD, emissive = 1.6f, tint = Pal.YELLOW, roll = tg.shotT * 20f)
            }
        } else {
            val gold = tg.kind == GOLD
            val bank = if (tg.state == FLY) -tg.vx * 0.0012f + cos(tg.phase) * 0.12f else 0f
            xf.set(tg.x, tg.y, tg.z, yaw = tg.spin, pitch = 0.25f + (if (tg.state == HIT) tg.spin * 0.7f else 0f), roll = bank + (if (tg.state == HIT) tg.spin else 0f))
            (if (gold) ShooterArt.goldDrone else ShooterArt.drone).draw(r, xf = xf)
            if (tg.state != HIT) {
                r.sprite(tg.x, tg.y - 6f, tg.z + 2f, 70f, 34f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.45f, tint = if (gold) Pal.GOLD else Pal.CYAN)
                if (gold) r.sprite(tg.x, tg.y, tg.z + 6f, 90f, 90f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.3f + 0.2f * sin(time * 12f), tint = Pal.YELLOW)
            } else {
                r.sprite(tg.x, tg.y, tg.z + 6f, 40f, 40f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.6f * (1f - tg.t / HIT_TIME), tint = Pal.ORANGE)
            }
        }
    }

    private fun drawBoss(r: Renderer3D) {
        if (bossState == B_OFF || bossState == B_GONE) return
        val z = ShooterWorld.BOSS_Z
        val dying = bossState == B_DYING
        val wobble = if (dying) sin(bossT * 40f) * 0.06f * bossT else sin(bossClock * 0.6f) * -0.08f
        val tint = if (bossHitT < 0.06f) Pal.WHITE else if (dying && (bossT * 12f).toInt() % 2 == 0) 0xFFFF8060.toInt() else -1
        xf.set(bossX, bossY, z, pitch = 0.12f, roll = wobble)
        ShooterArt.bossHull.draw(r, xf = xf, tint = tint)
        val glow = TexKit.glow.full
        for (k in 0 until 3) {
            val px = if (k == 2) 0f else if (k == 0) -ShooterWorld.BOSS_POD_X else ShooterWorld.BOSS_POD_X
            val py = if (k == 2) ShooterWorld.BOSS_EYE_Y else ShooterWorld.BOSS_POD_Y
            val pz = if (k == 2) ShooterWorld.BOSS_EYE_Z else ShooterWorld.BOSS_POD_Z
            // Parts ride on the hull without its tilt: close enough, and the hit tests match exactly.
            val wx = bossX + px
            val wy = bossY + py
            val wz = z + pz
            val open = bossState == B_FIGHT && (if (k == 2) true else podOpen(k))
            if (open) {
                val c = if (k == 2) (if (bossCharging) Pal.RED else 0xFF8A2A3A.toInt()) else Pal.CYAN
                val pulse = 1f + 0.08f * sin(time * 10f + k)
                xf2.set(wx, wy, wz, scale = pulse)
                ShooterArt.core.draw(r, xf = xf2, tint = c, emissiveBoost = if (k == 2 && !bossCharging) 0.4f else 1.3f)
                val ga = if (k == 2) (if (bossCharging) 0.4f + 0.5f * (1f - bossChargeLeft / ShooterTuning.BOSS_CHARGE_TIME) else 0.1f) else 0.45f
                r.sprite(wx, wy, wz + 14f, 70f, 70f, glow, blend = Blend.ADD, emissive = 1f, alpha = ga, tint = c)
            } else {
                xf2.set(wx, wy, wz)
                ShooterArt.shutter.draw(r, xf = xf2)
            }
        }
        if (bossCharging) {
            val k = 1f - bossChargeLeft / ShooterTuning.BOSS_CHARGE_TIME
            val size = 200f - 150f * k
            r.sprite(bossX, bossY + ShooterWorld.BOSS_EYE_Y, z + ShooterWorld.BOSS_EYE_Z + 20f, size, size, ShooterArt.ring.full, blend = Blend.ADD, emissive = 1.5f, alpha = 0.5f + 0.5f * k, tint = Pal.RED, roll = time * 5f)
        }
        // Thrusters underneath.
        r.sprite(bossX, bossY - 38f, z + 40f, 160f, 50f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.35f + 0.1f * sin(time * 20f), tint = Pal.CYAN)
    }

    private fun drawGun(r: Renderer3D) {
        // A toy light-gun low on the right, turned towards the last shot, kicking up as it fires.
        val gx = ShooterWorld.EYE_X + 13f
        val gy = ShooterWorld.EYE_Y - 26f
        val gz = ShooterWorld.EYE_Z - 72f
        rayAtZ(lastShotX, lastShotY, 150f)
        var dx = ray[0] - gx
        var dy = ray[1] - gy
        var dz = 150f - gz
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        dx /= l; dy /= l; dz /= l
        val yaw = atan2(-dx, -dz)
        val pitch = asin(dy.coerceIn(-1f, 1f)) + recoil * 0.5f
        xf.set(gx, gy, gz + recoil * 3f, yaw = yaw, pitch = pitch, roll = -0.15f)
        ShooterArt.pistol.draw(r, xf = xf)
        if (muzzleT < SMOKE_LIFE) {
            xf2.set(0f, 4.3f, -22f)
            xf3.setProduct(xf, xf2)
            val mx = xf3.x(0f, 0f, 0f)
            val my = xf3.y(0f, 0f, 0f)
            val mz = xf3.z(0f, 0f, 0f)
            if (muzzleT < 0.07f) {
                val s = 18f + 60f * muzzleT
                r.sprite(mx, my, mz, s, s, ShooterArt.flash.full, blend = Blend.ADD, emissive = 1.8f, tint = Pal.YELLOW, roll = muzzleT * 30f)
                val g = 30f + 46f * muzzleT
                r.sprite(mx, my, mz - 2f, g, g, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.6f * (1f - muzzleT / 0.07f), tint = Pal.ORANGE)
            }
            // A curl of smoke off the muzzle: three soft puffs that drift up and away and thin out.
            val fade = 1f - muzzleT / SMOKE_LIFE
            for (i in 0 until 3) {
                val t = muzzleT - 0.05f - i * 0.05f
                if (t <= 0f) continue
                val sz = 2.5f + t * 9f + i * 1.2f
                r.sprite(mx + (i - 1) * t * 2.2f, my + t * 4f, mz - t * 6f, sz, sz, TexKit.glow.full, blend = Blend.ALPHA, emissive = 0.45f, alpha = 0.20f * fade, tint = 0xFFB4B4CC.toInt())
            }
        }
    }

    // ---------------------------------------------------------------- HUD

    private fun drawHud(scope: DrawScope) {
        // A hit flashes the screen red; being knocked down greys it out.
        if (hurtFlash > 0f) {
            scope.drawRect(Color(Pal.RED), Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.4f * hurtFlash)
            val e = 26f
            scope.drawRect(Color(Pal.DARKRED), Offset.Zero, Size(GAME_W, e), alpha = 0.6f * hurtFlash)
            scope.drawRect(Color(Pal.DARKRED), Offset(0f, GAME_H - e), Size(GAME_W, e), alpha = 0.6f * hurtFlash)
            scope.drawRect(Color(Pal.DARKRED), Offset.Zero, Size(e, GAME_H), alpha = 0.6f * hurtFlash)
            scope.drawRect(Color(Pal.DARKRED), Offset(GAME_W - e, 0f), Size(e, GAME_H), alpha = 0.6f * hurtFlash)
        }
        // On the last heart the edges of the screen pulse a dull red.
        if (hearts == 1 && downT <= 0f && !timeUp && !cleared) {
            val a = (0.12f + 0.08f * sin(time * 6f)) * fxK
            val e = 22f
            scope.drawRect(Color(Pal.DARKRED), Offset.Zero, Size(GAME_W, e), alpha = a)
            scope.drawRect(Color(Pal.DARKRED), Offset(0f, GAME_H - e), Size(GAME_W, e), alpha = a)
            scope.drawRect(Color(Pal.DARKRED), Offset.Zero, Size(e, GAME_H), alpha = a)
            scope.drawRect(Color(Pal.DARKRED), Offset(GAME_W - e, 0f), Size(e, GAME_H), alpha = a)
        }
        if (downT > 0f) {
            scope.drawRect(Color.Black, Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.45f)
            ArcadeFont.drawCentered(scope, "GET UP...", GAME_W / 2f, 330f, 3f, Color(Pal.LAVENDER), 0.6f + 0.4f * sin(time * 8f))
        }

        // Crosshairs under held fingers, and a fading ring where the last shot landed.
        for (k in 0 until POINTERS) if (ptrUsed[k]) crosshair(scope, ptrX[k], ptrY[k], 1f)
        if (lastShotT < 0.3f) {
            val a = 1f - lastShotT / 0.3f
            scope.drawCircle(Color(Pal.YELLOW), 8f + 30f * lastShotT, Offset(lastShotX, lastShotY), alpha = a, style = thin)
        }

        // Hearts, top left.
        for (i in 0 until ShooterTuning.HEARTS) {
            val on = i < hearts
            ArcadeFont.drawCentered(scope, HEART, 18f + i * 22f, 12f, 2.6f, Color(if (on) Pal.RED else Pal.DARKGRAY), if (on) 1f else 0.6f)
        }
        // Combo multiplier, top right.
        val m = mult()
        if (m > 1) {
            val pulse = 1f + 0.1f * sin(time * 10f)
            ArcadeFont.drawCentered(scope, MULT_LABELS[m], GAME_W - 30f, 10f, 3.2f * pulse, Color(if (m >= 4) Pal.PINK else if (m == 3) Pal.CYAN else Pal.LIME))
        }
        // Wave banner.
        if (bannerT < 1.8f) {
            val pop = easeOutBack(clamp01(bannerT / 0.3f))
            val a = if (bannerT > 1.4f) (1.8f - bannerT) / 0.4f else 1f
            val boss = wave == 3
            val show = !boss || (bannerT * 6f).toInt() % 2 == 0
            if (show) ArcadeFont.drawCentered(scope, WAVE_LABELS[wave], GAME_W / 2f, 230f, (if (boss) 3.6f else 5f) * pop, Color(if (boss) Pal.RED else Pal.YELLOW), a)
        }
        // The boss's health.
        if (bossState == B_FIGHT || bossState == B_ENTER || bossState == B_DYING) {
            val frac = if (bossState == B_ENTER) clamp01(bossT / ShooterTuning.BOSS_ENTER_SECONDS) else bossHp / ShooterTuning.BOSS_HP.toFloat()
            val x0 = 80f
            val w = 200f
            ArcadeFont.drawCentered(scope, "MOTHERSHIP", GAME_W / 2f, 8f, 1.6f, Color(Pal.LAVENDER))
            scope.drawRect(Color(Pal.NIGHT), Offset(x0 - 2f, 22f), Size(w + 4f, 11f))
            scope.drawRect(Color(if (bossCharging) Pal.ORANGE else Pal.RED), Offset(x0, 24f), Size(w * frac, 7f))
            scope.drawRect(Color.White, Offset(x0 - 2f, 22f), Size(w + 4f, 11f), style = thin)
        }

        drawAmmoBar(scope)
    }

    private fun crosshair(scope: DrawScope, x: Float, y: Float, alpha: Float) {
        val c = Color(Pal.YELLOW)
        // A soft halo under the reticle so it reads over bright and busy backgrounds alike.
        scope.drawCircle(c, 22f, Offset(x, y), alpha = alpha * 0.14f)
        scope.drawCircle(c, 14f, Offset(x, y), alpha = alpha * 0.9f, style = thin)
        scope.drawLine(c, Offset(x - 22f, y), Offset(x - 8f, y), 2f, alpha = alpha)
        scope.drawLine(c, Offset(x + 8f, y), Offset(x + 22f, y), 2f, alpha = alpha)
        scope.drawLine(c, Offset(x, y - 22f), Offset(x, y - 8f), 2f, alpha = alpha)
        scope.drawLine(c, Offset(x, y + 8f), Offset(x, y + 22f), 2f, alpha = alpha)
    }

    /**
     * The reload strip along the bottom: a dark panel with a lit rail, the rounds left as brass
     * cartridges (spent ones are empty outlines), and the RELOAD button, which pulses when empty.
     */
    private fun drawAmmoBar(scope: DrawScope) {
        val y0 = ShooterWorld.RELOAD_Y
        val h = GAME_H - y0
        val empty = ammo == 0 && reloadT <= 0f
        val blink = empty && (time * 5f).toInt() % 2 == 0
        val rail = Color(if (blink) Pal.RED else Pal.ORANGE)
        // A soft shadow cast up onto the street, then the panel, an upper bevel and the lit rail.
        scope.drawRect(Color.Black, Offset(0f, y0 - 9f), Size(GAME_W, 9f), alpha = 0.20f)
        scope.drawRect(Color(0xFF0A0812), Offset(0f, y0), Size(GAME_W, h), alpha = 0.88f)
        scope.drawRect(Color(0xFF181428), Offset(0f, y0 + 3f), Size(GAME_W, 5f), alpha = 0.9f)
        scope.drawRect(rail, Offset(0f, y0), Size(GAME_W, 3f))
        scope.drawRect(rail, Offset(0f, y0 + 3f), Size(GAME_W, 7f), alpha = 0.12f)
        // Rounds: brass cases with copper tips and a highlight; spent ones are dark outlines.
        for (i in 0 until ShooterTuning.CLIP) {
            val x = 16f + i * 19f
            val loaded = if (reloadT > 0f) (1f - reloadT / ShooterTuning.RELOAD_SECONDS) * ShooterTuning.CLIP > i else i < ammo
            if (loaded) {
                scope.drawRoundRect(Color(Pal.GOLD), Offset(x, y0 + 17f), Size(11f, 21f), CornerRadius(1.5f, 1.5f))
                scope.drawRect(Color(0xFFC8A040), Offset(x - 0.5f, y0 + 33f), Size(12f, 5f))
                scope.drawRect(Color.White, Offset(x + 2f, y0 + 19f, ), Size(2.2f, 12f), alpha = 0.35f)
                scope.drawRoundRect(Color(Pal.RED), Offset(x + 1f, y0 + 9f), Size(9f, 9f), CornerRadius(4f, 4f))
                scope.drawRect(Color.White, Offset(x + 2.6f, y0 + 11f), Size(1.8f, 4f), alpha = 0.4f)
            } else {
                scope.drawRoundRect(Color(0xFF14101E), Offset(x, y0 + 10f), Size(11f, 28f), CornerRadius(3f, 3f))
                scope.drawRoundRect(Color(Pal.DARKGRAY), Offset(x, y0 + 10f), Size(11f, 28f), CornerRadius(3f, 3f), style = thin)
            }
        }
        val label = if (reloadT > 0f) "LOADING" else "RELOAD"
        val lx = 272f
        val bx = lx - 62f
        val by = y0 + 9f
        val bg = if (empty) Pal.RED else Pal.PLUM
        scope.drawRoundRectCompat(bx, by, 124f, 32f, bg, if (blink) 1f else 0.9f)
        // A sheen on the top half, and an edge in the rail's colour.
        scope.drawRoundRect(Color.White, Offset(bx + 2f, by + 2f), Size(120f, 13f), CornerRadius(6f, 6f), alpha = 0.10f)
        scope.drawRoundRect(if (empty) Color.White else Color(Pal.ORANGE), Offset(bx, by), Size(124f, 32f), CornerRadius(8f, 8f), style = thin, alpha = if (empty) 0.9f else 0.55f)
        ArcadeFont.drawCentered(scope, label, lx, y0 + 17f, 2.2f, Color(if (empty) Pal.WHITE else Pal.ORANGE))
        if (empty) {
            val bob = sin(time * 10f) * 4f
            // A glow that swells round the button while the gun is empty.
            val pulse = 0.35f + 0.35f * sin(time * 8f)
            scope.drawRoundRect(Color(Pal.RED), Offset(bx - 4f, by - 4f), Size(132f, 40f), CornerRadius(11f, 11f), style = thick, alpha = pulse * fxK + 0.15f)
            ArcadeFont.drawCentered(scope, "TAP RELOAD ↓", GAME_W / 2f, y0 - 34f + bob, 2.6f, Color(Pal.RED))
        }
    }

    private fun DrawScope.drawRoundRectCompat(x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Float) {
        drawRoundRect(Color(color), Offset(x, y), Size(w, h), CornerRadius(8f, 8f), alpha = alpha)
    }

    // ---------------------------------------------------------------- simulation-test hooks

    internal val botSlots: Int get() = TARGETS
    internal val botAmmo: Int get() = ammo
    internal val botReloading: Boolean get() = reloadT > 0f
    internal val botHearts: Int get() = hearts
    internal val botDown: Boolean get() = downT > 0f
    internal val botShots: Int get() = shots
    internal val botHits: Int get() = hits
    internal val botHurts: Int get() = hurts
    internal val botCiviliansShot: Int get() = civiliansShot
    internal val botCombo: Int get() = combo
    internal val botWave: Int get() = wave
    internal val botActive: Int get() = targets.count { it.state != OFF }
    internal val botDecals: Int get() = decalAge.count { it < DECAL_LIFE }
    internal val botPointers: Int get() = ptrUsed.count { it }
    internal val botBossFighting: Boolean get() = bossState == B_FIGHT
    internal val botBossPresent: Boolean get() = bossState != B_OFF && bossState != B_GONE
    internal val botBossHp: Int get() = bossHp
    internal val botBossCharging: Boolean get() = bossCharging
    internal val botCleared: Boolean get() = cleared

    /**
     * What a shot at field point ([x], [y]) would hit right now: -1 nothing or cover, 0..3 a
     * target's kind (see [botKind]), 10 + k a boss part (see [botBossAim]).
     */
    internal fun botWouldHit(x: Float, y: Float): Int = when (resolve(x, y)) {
        SHOT_TARGET -> hitTarget!!.kind
        SHOT_BOSS -> 10 + hitPart
        else -> -1
    }

    /** Where to tap to reload. */
    internal val botReloadX = 272f
    internal val botReloadY = 615f

    /** What a player sees in slot [i]: -1 nothing to shoot, else 0 bandit, 1 civilian, 2 drone, 3 gold drone. */
    internal fun botKind(i: Int): Int = if (hittable(targets[i])) targets[i].kind else -1

    /** Whether the bandit in slot [i] is taking aim (its red ring is closing). */
    internal fun botTelling(i: Int): Boolean = targets[i].state == TELL

    /** How long the target in slot [i] has been shootable. */
    internal fun botSeen(i: Int): Float = targets[i].seen

    /** A good spot to aim at on the target in slot [i] (field units), into [out]. */
    internal fun botAim(i: Int, out: FloatArray) {
        val tg = targets[i]
        if (tg.spot >= 0) {
            val top = tg.y + ShooterArt.FIGURE_H - 6f
            val bottom = maxOf(tg.y + 48f, ShooterWorld.SPOT_TOP[tg.spot] + 2f)
            project(tg.x, (top + bottom) / 2f, tg.z)
        } else {
            project(tg.x, tg.y, tg.z)
        }
        out[0] = pt[0]
        out[1] = pt[1]
    }

    /** Boss part [k] (0/1 pod cores, 2 the eye, 3 the hull) if it's a weak point right now. */
    internal fun botBossWeak(k: Int): Boolean = bossState == B_FIGHT && (k == 3 || ((k != 2 || bossCharging) && bossPartWorld(k)))

    /** Where boss part [k] is on screen, into [out]. */
    internal fun botBossAim(k: Int, out: FloatArray) {
        if (k == 3) {
            project(bossX, bossY + 8f, ShooterWorld.BOSS_Z + 60f)
        } else {
            bossPartPos(k)
            project(pt[0], pt[1], pt[2])
        }
        out[0] = pt[0]
        out[1] = pt[1]
    }

    /** Test setup: puts a figure fully out at [spot] (or a drone in the sky if [spot] < 0). Returns its slot. */
    internal fun botSpawn(kind: Int, spot: Int, gunner: Boolean = false, fireDelay: Float = 0.3f): Int {
        val tg = freeTarget() ?: return -1
        if (kind == DRONE || kind == GOLD) {
            spawnDrone(kind == GOLD)
            tg.x = ShooterWorld.EYE_X
            tg.vx = 1f
            tg.baseY = 300f
            tg.y = 300f
            tg.z = 50f
            tg.phase = 0f
        } else {
            popFigure(tg, spot, kind, gunner, rising = false)
            tg.upTime = 30f
            tg.fireDelay = fireDelay
        }
        return targets.indexOf(tg)
    }

    /** Test setup: brings the boss straight into the fight. */
    internal fun botStartBoss() {
        startBoss()
        bossState = B_FIGHT
        bossT = 0f
    }

    /** Test setup: no natural spawns for [seconds] (the next spawn is pushed back), no gold drone and no boss. */
    internal fun botHoldSpawns(seconds: Float) {
        spawnT = seconds
        goldSpawned = true
        holdBoss = true
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        // A night street in miniature: a sky with a moon and skyline, the bank with its glowing
        // sign and upstairs windows, sandbags in front. Bandits pop up, the crosshair glides to
        // one and fires (a flash, sparks and a "+50"), and the title and "insert coin" take turns.
        val wf = w.toFloat()
        val hf = h.toFloat()
        for (y in 0 until h) p.fill(0f, y.toFloat(), wf, 1f, Color(Pal.mix(0xFF07061A.toInt(), 0xFF3A1E5C.toInt(), y / hf)))
        for (i in 0 until 10) {
            p.px(hash01(i, 5) * wf, hash01(i, 6) * hf * 0.26f, Color(Pal.CREAM), 0.3f + 0.7f * abs(sin(time * 1.3f + i * 1.9f)))
        }
        p.disc(wf * 0.82f, hf * 0.16f, 2.7f, Color(Pal.LAVENDER), 0.16f)
        p.disc(wf * 0.82f, hf * 0.16f, 1.6f, Color(Pal.CREAM))
        val top = hf * 0.28f
        // Skyline behind the bank, with a few lit windows.
        var sx = 0f
        var sk = 0
        while (sx < wf) {
            val bw = 1.6f + hash01(sk, 7) * 2.4f
            val bh = 1.4f + hash01(sk, 8) * 3.0f
            p.fill(sx, top - bh, bw, bh, Color(0xFF120E1E.toInt()))
            if (hash01(sk, 9) > 0.45f) p.px(sx + bw * 0.5f, top - bh * 0.6f, Color(0xFFFFD890.toInt()), 0.85f)
            sx += bw + 0.15f
            sk++
        }
        // The bank: a stone front with pilasters and a lit cornice, and its neon sign glowing pink.
        p.fill(0f, top, wf, hf - top, Color(0xFF6E5E50.toInt()))
        p.fill(0f, top, wf, 0.8f, Color(0xFF8E7A68.toInt()))
        p.fill(0f, top + 0.8f, wf, 0.5f, Color(0xFF3A2E28.toInt()), 0.6f)
        for (k in 0..3) p.fill(wf * (0.05f + 0.3f * k) - 0.45f, top + 1.3f, 0.9f, hf - top, Color(0xFF8E7A68.toInt()), 0.6f)
        p.disc(wf / 2f, top + 2.3f, 4.6f, Color(Pal.PINK), 0.10f + 0.05f * sin(time * 7f))
        p.textCentered("BANK", wf / 2f, top + 1.2f, Color(Pal.PINK), tiny = true)
        val cycle = 2.4f
        val beat = (time / cycle).toInt()
        val phase = (time % cycle) / cycle
        val who = beat % 3
        for (k in 0 until 3) {
            val wx = wf * (0.2f + 0.3f * k)
            val wy = hf * 0.52f
            p.fill(wx - 2.9f, wy - 3.3f, 5.8f, 5.6f, Color(0xFF8E7A68.toInt()))
            p.fill(wx - 2.6f, wy - 3f, 5.2f, 5f, Color(0xFF2A1A28.toInt()))
            p.fill(wx - 2.6f, wy - 3f, 5.2f, 0.6f, Color(0xFFFFC77A.toInt()), 0.6f)
            p.disc(wx, wy - 0.5f, 2.6f, Color(0xFFFFB060.toInt()), 0.10f)
            val up = k == who && phase < 0.7f || k == (beat + 1) % 3 && phase > 0.85f
            if (up) {
                val civ = hash01(beat, k) > 0.75f
                p.disc(wx, wy - 0.4f, 1.3f, Color(if (civ) Pal.SKIN_LIGHT else Pal.LIGHTGRAY))
                if (civ) {
                    p.fill(wx - 2.2f, wy - 2.6f, 0.6f, 2f, Color(Pal.SKIN_LIGHT))
                    p.fill(wx + 1.6f, wy - 2.6f, 0.6f, 2f, Color(Pal.SKIN_LIGHT))
                } else {
                    p.fill(wx - 1.8f, wy - 2.2f, 3.6f, 0.7f, Color(Pal.BLACK))
                    p.fill(wx - 1f, wy - 0.7f, 2f, 0.5f, Color(Pal.RED))
                }
                p.fill(wx - 1.6f, wy + 0.9f, 3.2f, 1.1f, Color(if (civ) Pal.CYAN else Pal.DARKRED))
            }
        }
        // Sandbags along the bottom, in two staggered rows.
        val bagY = hf * 0.84f
        for (row in 0..1) {
            val off = if (row == 0) 0f else wf / 12f
            for (i in -1 until 6) {
                p.fill(off + i * wf / 6f + 0.15f, bagY + row * hf * 0.08f, wf / 6f - 0.3f, hf * 0.075f, Color(if ((i + row) % 2 == 0) 0xFFA08A60.toInt() else 0xFF8E7A52.toInt()))
            }
        }
        // The crosshair glides to this beat's bandit and fires.
        val tx = wf * (0.2f + 0.3f * who)
        val ty = hf * 0.5f
        val px = wf * (0.2f + 0.3f * ((beat + 2) % 3))
        val k = clamp01(phase / 0.45f)
        val e = k * k * (3f - 2f * k)
        val cx = px + (tx - px) * e
        val cy = ty - sin(e * PI.toFloat()) * hf * 0.2f
        if (phase in 0.45f..0.55f) {
            val f = 1f - (phase - 0.45f) / 0.1f
            p.disc(tx, ty, 3.2f, Color(Pal.YELLOW), 0.8f * f)
            p.disc(tx, ty, 1.5f, Color.White, f)
            for (a in 0 until 6) {
                val ang = a * 1.047f + 0.3f
                p.px(tx + cos(ang) * (2f + 3.2f * (1f - f)), ty + sin(ang) * (2f + 3.2f * (1f - f)), Color(Pal.ORANGE), f)
            }
            // A flash at the gun, low on the right.
            p.disc(wf * 0.86f, hf * 0.9f, 1.6f, Color(Pal.YELLOW), 0.9f * f)
        }
        if (phase in 0.5f..0.85f) {
            val u = (phase - 0.5f) / 0.35f
            p.textCentered("+50", tx, ty - 3f - 3.2f * u, Color(Pal.WHITE), tiny = true, alpha = 1f - u)
        }
        p.frame(cx - 2f, cy - 2f, 4f, 4f, Color(Pal.RED))
        p.fill(cx - 3.5f, cy - 0.2f, 2f, 0.4f, Color(Pal.RED))
        p.fill(cx + 1.5f, cy - 0.2f, 2f, 0.4f, Color(Pal.RED))
        p.fill(cx - 0.2f, cy - 3.5f, 0.4f, 2f, Color(Pal.RED))
        p.fill(cx - 0.2f, cy + 1.5f, 0.4f, 2f, Color(Pal.RED))
        if ((time * 1.5f).toInt() % 2 == 0) p.textCentered("SHOOTOUT", wf / 2f, hf - 5.5f, Color(Pal.ORANGE), tiny = true)
        else p.textCentered("INSERT COIN", wf / 2f, hf - 5.5f, Color(Pal.YELLOW), tiny = true)
        p.textCentered("♥♥♥", wf * 0.16f, 0.8f, Color(Pal.RED), tiny = true)
    }
}
