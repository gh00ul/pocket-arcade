package com.pocketarcade.games.hoops

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Lighting
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb
import com.pocketarcade.hub.beveledBox
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Measurements the simulation and the 3D scene share: the simulation works in metres, the scene
 * in centimetres with z towards the viewer ([S] converts, and the scene's z is minus the
 * simulation's). The camera in [HoopsGame] matches the simulation's own projection exactly.
 */
internal object HoopsGeo {
    const val G = 9.8f
    const val CAM_Y = 1.7f
    const val CAM_Z = -1.2f
    const val F = 420f
    const val CX = 180f
    const val HORIZON = 258f
    const val BALL_R = 0.12f
    const val RIM_R = 0.23f
    const val RIM_Y = 2.3f
    const val HOOP_Z = 2.6f
    const val BOARD_Z = 2.87f
    const val BOARD_HALF_W = 0.6f
    const val BOARD_BOTTOM = 2.15f
    const val BOARD_TOP = 3.0f
    const val START_Y = 0.7f
    const val START_Z = 0.2f
    const val CAGE_HALF_W = 1.0f
    const val BACK_Z = 3.2f

    /** World units (centimetres) per simulation metre. */
    const val S = 100f

    /** The wall behind the hoop, in scene units. */
    const val WALL_Z = -BACK_Z * S
}

/** Every number that shapes how the hoops scene looks, in one place so it can be tuned blind. */
internal object HoopsLook {
    // ---- Lights. The ambient is dim and cool so the pools of light carry the mood.
    const val AMB_R = 0.36f
    const val AMB_G = 0.34f
    const val AMB_B = 0.48f
    const val DIR_R = 0.26f
    const val DIR_G = 0.25f
    const val DIR_B = 0.30f
    /** The warm lamp over the court, and the cool spot that picks out the backboard and the wall round it. */
    const val GYM_Y = 330f
    const val GYM_Z = -170f
    const val GYM_RADIUS = 760f
    const val GYM_INTENSITY = 1.3f
    const val SPOT_Y = 310f
    const val SPOT_Z = -215f
    const val SPOT_RADIUS = 430f
    const val SPOT_INTENSITY = 0.85f
    /** A soft light in front of the shooter, so the ball reads first. */
    const val READY_Y = 190f
    const val READY_Z = 40f
    const val READY_RADIUS = 320f
    const val READY_INTENSITY = 0.7f
    /** The light at the rim: a glow at rest, swelling with makes and rim hits. */
    const val ACCENT_RADIUS = 260f
    const val ACCENT_REST = 0.22f
    const val ACCENT_MAKE = 1.5f
    const val ACCENT_RIM = 0.7f

    // ---- Post effects the scene asks the renderer for.
    const val VIGNETTE = 0.3f
    const val BLOOM = 0.85f
    /** Streaks of the neon and the hoop in the glossy floor (0 off). */
    const val FLOOR_REFLECT = 0.45f
    const val FLOOR_GLOSS = 0.55f
    const val BOARD_GLOSS = 0.7f
    const val FRAME_GLOSS = 0.55f

    // ---- Emissive strengths (1 = the colour as painted, which the bloom then softens).
    const val AD_EMISSIVE = 0.8f
    const val PANEL_EMISSIVE = 1f
    const val LED_BASE = 0.75f
    const val LED_PULSE = 0.25f
    const val LED_FLASH = 1.1f

    // ---- Additive glows (alpha of each).
    const val SHAFT_ALPHA = 0.09f
    const val SHAFT_HYPE = 0.10f
    const val SIGN_HALO_ALPHA = 0.9f
    const val POOL_ALPHA = 0.1f
    const val POOL_FLASH = 0.22f
    const val BOARD_GLOW_ALPHA = 0.55f
    const val TRAIL_ALPHA = 0.45f

    /** How much of each flash and pulse survives the reduce-motion setting. */
    const val CALM_K = 0.4f

    // ---- Where things hang on the wall (centimetres).
    const val WALL_HALF_W = 260f
    const val WALL_TOP = 520f
    const val SIGN_Z = -316.7f
    const val SIGN_Y = 372f
    const val SIGN_HW = 98f
    const val SIGN_HH = 23.5f
    const val PANEL_X = 138f
    const val PANEL_HW = 32f
    const val PANEL_HH = 17f
    const val PANEL_Z = -316.6f
    const val AD_Z = -318.5f
    const val AD_HW = 200f
    const val AD_H = 34f
    const val LAMP_X = 150f
    const val LAMP_Y = 405f
    const val LAMP_Z = -316f
    const val RAIL_Y = 256f
    const val RAIL_Z = -306f
    const val FRAME_W = 4f

    // ---- The net.
    const val NET_STRANDS = 10
    const val NET_RINGS = 3
    const val NET_DROP = 36f
    /** Fraction of the rim's radius the net narrows to at its foot. */
    const val NET_FOOT = 0.58f
    /** Angular twist of a strand from top to foot, so the two families of strands weave diamonds. */
    const val NET_TWIST = 0.94f
    /** How far a ball passing through pushes the net out, and how tall that bulge is (cm). */
    const val NET_BULGE = 9f
    const val NET_BULGE_H = 11f
}

/**
 * The hoops alley as a 3D scene: an arena wall with a painted crowd, LED ad boards, a neon sign
 * and wall readouts, a glowing backboard on a rail, the rim and net, and every effect that
 * answers the game (makes, rim and board hits, streaks). It keeps all the *visual* state (glows
 * that decay, trails, shockwaves) so the game's simulation never reads it and rendering never
 * touches the game's random generator: visual randomness is hash-based.
 *
 * Nothing here builds a texture until it is drawn, so headless tests can step a game freely.
 */
internal class HoopsScene {
    private companion object {
        const val S = HoopsGeo.S
        const val BALLS = 6
        const val TRAIL_N = 7
        /** Seconds between trail samples, and how long a sample lives. */
        const val TRAIL_DT = 0.014f
        const val TRAIL_LIFE = 0.13f
        const val BURSTS = 10
        const val K_RING_FLAT = 0
        const val K_RING_UP = 1
        const val K_FLARE = 2
        const val K_GLOW = 3
        const val FLASHES = 12
        const val MOTES = 12
    }

    private class Burst {
        var kind = 0
        var x = 0f
        var y = 0f
        var z = 0f
        var age = 1f
        var life = 1f
        var s0 = 0f
        var s1 = 0f
        var alpha = 0f
        var tint = -1
    }

    // ---------------------------------------------------------------- visual state

    private val bursts = Array(BURSTS) { Burst() }
    private var burstNext = 0
    private var flash = 0f
    private var rimFlash = 0f
    private var boardFlash = 0f
    private var boardHitX = 0f
    private var boardHitY = 0f
    private var hype = 0f
    private val trailPos = FloatArray(BALLS * TRAIL_N * 3)
    private val trailAge = FloatArray(BALLS * TRAIL_N) { 99f }
    private val trailHead = IntArray(BALLS)
    private val trailGap = FloatArray(BALLS)

    /** Clears every glow and trail, for a fresh round. */
    fun reset() {
        for (b in bursts) b.age = b.life
        flash = 0f; rimFlash = 0f; boardFlash = 0f; hype = 0f
        trailAge.fill(99f)
        trailHead.fill(0)
        trailGap.fill(0f)
    }

    private fun calmK(): Float = if (ScreenShake.intensity <= 0f) HoopsLook.CALM_K else 1f

    /** Decays the glows and ages the trails (called from the game's fixed step). */
    fun step(dt: Float) {
        flash = (flash - dt * 2.4f).coerceAtLeast(0f)
        rimFlash = (rimFlash - dt * 6f).coerceAtLeast(0f)
        boardFlash = (boardFlash - dt * 3.5f).coerceAtLeast(0f)
        hype = (hype - dt * 0.12f).coerceAtLeast(0f)
        for (b in bursts) if (b.age < b.life) b.age += dt
        for (i in trailAge.indices) trailAge[i] += dt
        for (i in 0 until BALLS) trailGap[i] -= dt
    }

    /** Records where ball [i] is (scene units) so a glowing trail can follow it. */
    fun trail(i: Int, x: Float, y: Float, z: Float) {
        if (trailGap[i] > 0f) return
        trailGap[i] = TRAIL_DT
        val slot = (trailHead[i] + 1) % TRAIL_N
        trailHead[i] = slot
        val at = (i * TRAIL_N + slot)
        trailPos[at * 3] = x; trailPos[at * 3 + 1] = y; trailPos[at * 3 + 2] = z
        trailAge[at] = 0f
    }

    private fun burst(kind: Int, x: Float, y: Float, z: Float, s0: Float, s1: Float, life: Float, alpha: Float, tint: Int, delay: Float = 0f) {
        val b = bursts[burstNext]
        burstNext = (burstNext + 1) % BURSTS
        b.kind = kind; b.x = x; b.y = y; b.z = z
        b.s0 = s0; b.s1 = s1; b.life = life; b.age = -delay
        b.alpha = alpha * calmK(); b.tint = tint
    }

    /** Colour of the game's state: orange at rest, hot pink on a combo, gold on fire. */
    private fun stateColor(streak: Int): Int = when {
        streak >= HoopsTuning.MAX_MULTIPLIER -> Pal.GOLD
        streak >= 2 -> Pal.PINK
        else -> Pal.ORANGE
    }

    /** [stateColor], flickering between orange and yellow while the hoop is on fire. */
    private fun accent(streak: Int, t: Float): Int =
        if (streak >= HoopsTuning.MAX_MULTIPLIER) mixArgb(Pal.ORANGE, Pal.YELLOW, 0.5f + 0.5f * sin(t * 13f)) else stateColor(streak)

    // ---------------------------------------------------------------- events

    /** A ball dropped through the hoop: a shockwave round the rim, a glint and a pulse through every light. */
    fun made(hoopX: Float, swish: Boolean, streak: Int) {
        val cx = hoopX * S
        val ry = HoopsGeo.RIM_Y * S
        val cz = -HoopsGeo.HOOP_Z * S
        val color = stateColor(streak)
        flash = calmK()
        hype = clamp01(hype + 0.25f + 0.1f * streak)
        val rim = HoopsGeo.RIM_R * S
        burst(K_RING_FLAT, cx, ry - 3f, cz, rim * 2f, rim * 8f, 0.6f, 0.85f, color)
        if (streak >= 2) burst(K_RING_FLAT, cx, ry - 3f, cz, rim * 1.6f, rim * 6f, 0.5f, 0.6f, Pal.WHITE, delay = 0.08f)
        burst(K_FLARE, cx, ry - 10f, cz + 8f, 30f, if (swish) 190f else 130f, 0.4f, 1f, Pal.mix(color, Pal.WHITE, 0.5f))
        burst(K_GLOW, cx, ry - 8f, cz, 100f, 240f, 0.5f, 0.6f, color)
    }

    /** A ball clipped the rim at scene position ([x], [y], [z]). */
    fun rimHit(x: Float, y: Float, z: Float) {
        rimFlash = calmK()
        burst(K_FLARE, x, y, z + 6f, 14f, 60f, 0.22f, 0.9f, Pal.mix(Pal.ORANGE, Pal.WHITE, 0.5f))
    }

    /** A ball struck the backboard at [x] (scene units from the board's centre) and height [y]. */
    fun boardHit(hoopX: Float, x: Float, y: Float) {
        boardFlash = calmK()
        boardHitX = x
        boardHitY = y
        burst(K_RING_UP, hoopX * S + x, y, -HoopsGeo.BOARD_Z * S + 2f, 10f, 78f, 0.32f, 0.8f, Pal.mix(Pal.SKY, Pal.WHITE, 0.4f))
    }

    /** A ball landed on the court at ([x], [z]) with [strength] 0..1. */
    fun floorHit(x: Float, z: Float, strength: Float) {
        burst(K_RING_FLAT, x, 0.9f, z, 10f, 14f + strength * 48f, 0.3f, 0.3f * strength + 0.05f, 0xFFFFD6A0.toInt())
    }

    // ---------------------------------------------------------------- lights

    private val gym = PointLight(0f, HoopsLook.GYM_Y, HoopsLook.GYM_Z, 1f, 0.9f, 0.75f, HoopsLook.GYM_RADIUS, HoopsLook.GYM_INTENSITY)
    private val spot = PointLight(0f, HoopsLook.SPOT_Y, HoopsLook.SPOT_Z, 0.65f, 0.78f, 1f, HoopsLook.SPOT_RADIUS, HoopsLook.SPOT_INTENSITY)
    private val ready = PointLight(0f, HoopsLook.READY_Y, HoopsLook.READY_Z, 1f, 0.85f, 0.65f, HoopsLook.READY_RADIUS, HoopsLook.READY_INTENSITY)
    private val accentLight = PointLight(0f, HoopsGeo.RIM_Y * S + 5f, -HoopsGeo.HOOP_Z * S + 10f, 1f, 0.55f, 0.2f, HoopsLook.ACCENT_RADIUS, HoopsLook.ACCENT_REST)

    /** Sets the room's lighting and the renderer's look for this frame. */
    fun light(r: Renderer3D, hoopX: Float, streak: Int, t: Float) {
        val l: Lighting = r.lighting
        l.ambR = HoopsLook.AMB_R; l.ambG = HoopsLook.AMB_G; l.ambB = HoopsLook.AMB_B
        l.setDirection(0.15f, 1f, 0.55f)
        l.dirR = HoopsLook.DIR_R; l.dirG = HoopsLook.DIR_G; l.dirB = HoopsLook.DIR_B
        val fire = streak >= HoopsTuning.MAX_MULTIPLIER
        accentLight.x = hoopX * S
        var k = HoopsLook.ACCENT_REST + flash * HoopsLook.ACCENT_MAKE + rimFlash * HoopsLook.ACCENT_RIM
        if (fire) k += 0.9f + 0.35f * sin(t * 14f)
        accentLight.intensity = k
        when {
            fire -> { accentLight.r = 1f; accentLight.g = 0.5f; accentLight.b = 0.12f }
            streak >= 2 -> { accentLight.r = 1f; accentLight.g = 0.3f; accentLight.b = 0.7f }
            else -> { accentLight.r = 1f; accentLight.g = 0.55f; accentLight.b = 0.2f }
        }
        spot.x = hoopX * S * 0.5f
        l.points.clear()
        l.points += gym
        l.points += spot
        l.points += accentLight
        l.points += ready
        r.vignette = HoopsLook.VIGNETTE
        r.bloom = HoopsLook.BLOOM
        r.floorReflect = HoopsLook.FLOOR_REFLECT
    }

    // ---------------------------------------------------------------- models

    private val room: Model by lazy { buildRoom() }
    private val boardModel: Model by lazy { buildBoard() }
    private val ledModel: Model by lazy { buildLed() }

    /** The rim with its mounting plate, centred on the hoop (place it each frame). */
    val rimModel: Model by lazy { buildRim() }

    private fun steelFaces(gloss: Float = HoopsLook.FRAME_GLOSS): BoxFaces {
        val s = HoopsArt.steel.full
        return BoxFaces(front = s, left = s, right = s, top = s, gloss = gloss)
    }

    private fun buildRoom(): Model {
        val b = ModelBuilder()
        val w = HoopsGeo.CAGE_HALF_W * S
        val back = HoopsGeo.WALL_Z
        val hw = HoopsLook.WALL_HALF_W
        val steel = HoopsArt.steel.full
        // The arena wall, rising into the dark.
        b.quad(-hw, HoopsLook.WALL_TOP, back, hw, HoopsLook.WALL_TOP, back, hw, 0f, back, -hw, 0f, back, HoopsArt.crowd.full, 0f, 0f, 1f)
        // Court and the carpet beyond the cage (darker towards the outside).
        b.quad(-w, 0f, back, w, 0f, back, w, 0f, 60f, -w, 0f, 60f, HoopsArt.floor.full, 0f, 1f, 0f, gloss = HoopsLook.FLOOR_GLOSS)
        val apron = HoopsArt.apron.full
        b.quad(-hw, 0f, back, -w, 0f, back, -w, 0f, 60f, -hw, 0f, 60f, apron, 0f, 1f, 0f, shade = floatArrayOf(0.45f, 1f, 1f, 0.45f))
        b.quad(w, 0f, back, hw, 0f, back, hw, 0f, 60f, w, 0f, 60f, apron, 0f, 1f, 0f, shade = floatArrayOf(1f, 0.45f, 0.45f, 1f))
        // LED ad boards along the foot of the wall, under a capping rail.
        val ad = HoopsLook.AD_HW
        b.quad(-ad, HoopsLook.AD_H, HoopsLook.AD_Z, ad, HoopsLook.AD_H, HoopsLook.AD_Z, ad, 0f, HoopsLook.AD_Z, -ad, 0f, HoopsLook.AD_Z, HoopsArt.adBoard.full, 0f, 0f, 1f, emissive = HoopsLook.AD_EMISSIVE)
        b.beveledBox(-ad - 1f, HoopsLook.AD_H, HoopsLook.AD_Z - 1.5f, ad + 1f, HoopsLook.AD_H + 3.5f, HoopsLook.AD_Z + 2.5f, steelFaces(), floorAo = 0f, bevel = 0.6f)
        // The cage's back posts and top rail.
        for (sx in floatArrayOf(-w, w)) {
            b.beveledBox(sx - 4f, 0f, back + 3f, sx + 4f, 424f, back + 11f, steelFaces(), floorAo = 0.4f)
        }
        b.beveledBox(-w, 414f, back + 3f, w, 424f, back + 11f, steelFaces(), floorAo = 0f)
        // Floodlight housings in the top corners.
        for (sx in floatArrayOf(-HoopsLook.LAMP_X, HoopsLook.LAMP_X)) {
            b.beveledBox(sx - 22f, HoopsLook.LAMP_Y - 9f, HoopsLook.LAMP_Z - 6f, sx + 22f, HoopsLook.LAMP_Y + 7f, HoopsLook.LAMP_Z + 4f, steelFaces(0.4f), floorAo = 0f, bevel = 0.7f)
        }
        // The backboard's rail, high on the wall, and its brackets.
        b.beveledBox(-HoopsLook.WALL_HALF_W * 0.6f, HoopsLook.RAIL_Y - 6f, HoopsLook.RAIL_Z - 3f, HoopsLook.WALL_HALF_W * 0.6f, HoopsLook.RAIL_Y + 6f, HoopsLook.RAIL_Z + 3f, steelFaces(), floorAo = 0f, bevel = 0.7f)
        for (bx in floatArrayOf(-140f, -70f, 70f, 140f)) {
            b.box(bx - 3f, HoopsLook.RAIL_Y - 5f, back + 1f, bx + 3f, HoopsLook.RAIL_Y + 5f, HoopsLook.RAIL_Z - 3f, BoxFaces(front = steel, left = steel, right = steel, top = steel))
        }
        // Dark plates behind the sign and the two readouts.
        b.beveledBox(-HoopsLook.SIGN_HW - 6f, HoopsLook.SIGN_Y - HoopsLook.SIGN_HH - 5f, back + 0.5f, HoopsLook.SIGN_HW + 6f, HoopsLook.SIGN_Y + HoopsLook.SIGN_HH + 5f, HoopsLook.SIGN_Z - 1.5f, steelFaces(0.3f), tint = 0xFF444458.toInt(), floorAo = 0f, bevel = 0.8f)
        for (sx in floatArrayOf(-HoopsLook.PANEL_X, HoopsLook.PANEL_X)) {
            b.beveledBox(sx - HoopsLook.PANEL_HW - 3f, HoopsLook.SIGN_Y - HoopsLook.PANEL_HH - 3f, back + 0.5f, sx + HoopsLook.PANEL_HW + 3f, HoopsLook.SIGN_Y + HoopsLook.PANEL_HH + 3f, HoopsLook.PANEL_Z - 0.4f, steelFaces(0.4f), floorAo = 0f, bevel = 0.8f)
        }
        return b.build()
    }

    /** The backboard: face, frame and the carriage that hangs it from the rail (moves with the hoop). */
    private fun buildBoard(): Model {
        val b = ModelBuilder()
        val hw = HoopsGeo.BOARD_HALF_W * S
        val bz = -HoopsGeo.BOARD_Z * S
        val top = HoopsGeo.BOARD_TOP * S
        val bottom = HoopsGeo.BOARD_BOTTOM * S
        val t = HoopsLook.FRAME_W
        val steel = HoopsArt.steel.full
        b.quad(-hw, top, bz, hw, top, bz, hw, bottom, bz, -hw, bottom, bz, HoopsArt.boardFace.full, 0f, 0f, 1f, gloss = HoopsLook.BOARD_GLOSS)
        val f = steelFaces()
        b.beveledBox(-hw - t, top, bz - 5f, hw + t, top + t, bz + 3f, f, floorAo = 0f, bevel = 0.7f)
        b.beveledBox(-hw - t, bottom - t, bz - 5f, hw + t, bottom, bz + 3f, f, floorAo = 0f, bevel = 0.7f)
        b.beveledBox(-hw - t, bottom, bz - 5f, -hw, top, bz + 3f, f, floorAo = 0f, bevel = 0.7f)
        b.beveledBox(hw, bottom, bz - 5f, hw + t, top, bz + 3f, f, floorAo = 0f, bevel = 0.7f)
        b.box(-hw, bottom, bz - 5f, hw, top, bz - 0.5f, BoxFaces(top = steel, left = steel, right = steel))
        // The carriage: from the back of the board to the rail.
        b.beveledBox(-22f, HoopsLook.RAIL_Y - 12f, HoopsLook.RAIL_Z + 3f, 22f, HoopsLook.RAIL_Y + 12f, bz - 5f, f, floorAo = 0f, bevel = 0.7f)
        return b.build()
    }

    /** Emissive strips on the face of the backboard's frame, tinted and pulsed by the game. */
    private fun buildLed(): Model {
        val b = ModelBuilder()
        val hw = HoopsGeo.BOARD_HALF_W * S
        val bz = -HoopsGeo.BOARD_Z * S + 3.25f
        val top = HoopsGeo.BOARD_TOP * S
        val bottom = HoopsGeo.BOARD_BOTTOM * S
        val t = HoopsLook.FRAME_W
        val w = TexKit.white.full
        val o = t / 2f - 0.7f
        val ext = hw + t - 0.5f
        fun strip(x0: Float, y0: Float, x1: Float, y1: Float) {
            b.quad(x0, y1, bz, x1, y1, bz, x1, y0, bz, x0, y0, bz, w, 0f, 0f, 1f, emissive = 1f)
        }
        strip(-ext, top + o, ext, top + o + 1.4f)
        strip(-ext, bottom - o - 1.4f, ext, bottom - o)
        strip(-hw - o - 1.4f, bottom, -hw - o, top)
        strip(hw + o, bottom, hw + o + 1.4f, top)
        return b.build()
    }

    private fun buildRim(): Model {
        val b = ModelBuilder()
        val r = HoopsGeo.RIM_R * S
        val steel = HoopsArt.steel.full
        b.torus(0f, 0f, 0f, r, 2f, HoopsArt.rim.full, segments = 20, sides = 6, gloss = 0.8f)
        // Mounting plate on the glass and the arm out to the rim.
        val gap = (HoopsGeo.BOARD_Z - HoopsGeo.HOOP_Z) * S
        val faces = BoxFaces(front = steel, left = steel, right = steel, top = steel, gloss = 0.6f)
        b.beveledBox(-11f, -9f, -gap - 0.5f, 11f, 7f, -gap + 2.5f, faces, floorAo = 0f, bevel = 0.6f)
        b.beveledBox(-3f, -2.5f, -gap + 2f, 3f, 1.5f, -r + 2f, faces, floorAo = 0f, bevel = 0.5f)
        return b.build()
    }

    // ---------------------------------------------------------------- drawing

    private val boardXf = Xform()

    /** Scratch placement for the game to position [rimModel] with each frame. */
    val rimXf = Xform()

    private val makesPanel = HoopsArt.Readout("MAKES", Pal.ORANGE)
    private val multPanel = HoopsArt.Readout("MULT", Pal.PINK)

    /** Repaints the wall readouts when what they show has changed. */
    fun updateReadouts(makes: Int, shots: Int, streak: Int) {
        val mk = makes * 1000 + shots
        if (makesPanel.stale(mk)) makesPanel.paint(mk, "$makes/$shots", Pal.YELLOW)
        val mult = streak.coerceIn(1, HoopsTuning.MAX_MULTIPLIER)
        val tier = when {
            streak >= HoopsTuning.MAX_MULTIPLIER -> 2
            streak >= 2 -> 1
            else -> 0
        }
        val key = mult * 10 + tier
        if (multPanel.stale(key)) multPanel.paint(key, "x$mult", if (tier == 2) Pal.ORANGE else if (tier == 1) Pal.PINK else Pal.GRAY)
    }

    /**
     * The arena: wall, ad boards, posts, sign, readouts, floodlights with their shafts of light,
     * dust in the beams and camera flashes in the crowd. Draws first: everything else is in front.
     */
    fun drawBackdrop(r: Renderer3D, t: Float, streak: Int, multPop: Float) {
        room.draw(r)
        val accent = accent(streak, t)
        val fire = streak >= HoopsTuning.MAX_MULTIPLIER
        val calm = ScreenShake.intensity <= 0f
        val glow = TexKit.glow.full
        val white = TexKit.white.full
        val ledLevel = HoopsLook.LED_BASE + HoopsLook.LED_PULSE * (0.5f + 0.5f * sin(t * 4f)) + flash * HoopsLook.LED_FLASH

        // Light strips: the ad boards' cap and the cage's back posts.
        val ad = HoopsLook.AD_HW
        val capZ = HoopsLook.AD_Z + 2.6f
        r.quad(-ad, HoopsLook.AD_H + 2.6f, capZ, ad, HoopsLook.AD_H + 2.6f, capZ, ad, HoopsLook.AD_H + 1.2f, capZ, -ad, HoopsLook.AD_H + 1.2f, capZ, white, 0f, 0f, 1f, emissive = ledLevel, tint = accent)
        val w = HoopsGeo.CAGE_HALF_W * S
        val postZ = HoopsGeo.WALL_Z + 11.2f
        for (sx in floatArrayOf(-w, w)) {
            r.quad(sx - 0.8f, 408f, postZ, sx + 0.8f, 408f, postZ, sx + 0.8f, 24f, postZ, sx - 0.8f, 24f, postZ, white, 0f, 0f, 1f, emissive = ledLevel, tint = accent)
        }

        // The wall readouts: solid glowing faces, the multiplier popping with the game's spring.
        drawPanel(r, makesPanel.tex.full, -HoopsLook.PANEL_X, 1f)
        drawPanel(r, multPanel.tex.full, HoopsLook.PANEL_X, multPop.coerceIn(0.7f, 1.5f))

        // Neon sign: halo added on top of the wall, crisp tube over it.
        val flick = if (fire) 0.8f + 0.2f * sin(t * 21f) * sin(t * 5.3f) else 1f
        val k = (0.85f + 0.15f * sin(t * 2.4f)) * flick * (1f + flash * 0.35f)
        val sz = HoopsLook.SIGN_Z
        val sy = HoopsLook.SIGN_Y
        val shw = HoopsLook.SIGN_HW
        val shh = HoopsLook.SIGN_HH
        r.quad(-shw, sy + shh, sz, shw, sy + shh, sz, shw, sy - shh, sz, -shw, sy - shh, sz, HoopsArt.logoHalo.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = HoopsLook.SIGN_HALO_ALPHA * k, tint = accent)
        r.quad(-shw, sy + shh, sz, shw, sy + shh, sz, shw, sy - shh, sz, -shw, sy - shh, sz, HoopsArt.logoCore.full, 0f, 0f, 1f, blend = Blend.ALPHA, emissive = 1.15f * k, tint = mixArgb(accent, Pal.WHITE, 0.6f))

        // Floodlights and their shafts.
        val shaft = HoopsArt.shaft.full
        val shaftA = HoopsLook.SHAFT_ALPHA + hype * HoopsLook.SHAFT_HYPE
        for (side in 0..1) {
            val sx = if (side == 0) -HoopsLook.LAMP_X else HoopsLook.LAMP_X
            for (n in -1..1) r.sprite(sx + n * 13f, HoopsLook.LAMP_Y - 1f, HoopsLook.LAMP_Z + 4.5f, 9f, 9f, TexKit.dot.full, emissive = 1.5f, tint = 0xFFFFF0D8.toInt())
            val breathe = 0.9f + 0.1f * sin(t * 0.7f + side * 2f)
            r.sprite(sx, HoopsLook.LAMP_Y, HoopsLook.LAMP_Z + 6f, 80f, 80f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.3f * breathe, tint = 0xFFFFE0B0.toInt())
            val tx = sx * 0.4f
            r.beam(sx, HoopsLook.LAMP_Y - 4f, HoopsLook.LAMP_Z + 5f, tx, 0f, -196f, 64f, shaft, blend = Blend.ADD, emissive = 1f, alpha = shaftA * breathe, tint = 0xFFFFE0B8.toInt())
        }
        // Light spilling down the back posts.
        for (sx in floatArrayOf(-w, w)) {
            r.beam(sx, 24f, postZ + 1f, sx, 408f, postZ + 1f, 22f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.14f * (0.85f + 0.3f * flash), tint = accent)
        }
        // A sweep along the ad boards, brighter on every make.
        val sweep = -ad - 60f + (t * 70f) % (ad * 2f + 120f)
        r.quad(sweep, HoopsLook.AD_H, HoopsLook.AD_Z + 0.4f, sweep + 60f, HoopsLook.AD_H, HoopsLook.AD_Z + 0.4f, sweep + 40f, 0f, HoopsLook.AD_Z + 0.4f, sweep - 20f, 0f, HoopsLook.AD_Z + 0.4f, HoopsArt.glint.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.35f, tint = 0xFFB8C8FF.toInt())
        if (flash > 0.02f) {
            r.quad(-ad, HoopsLook.AD_H, HoopsLook.AD_Z + 0.5f, ad, HoopsLook.AD_H, HoopsLook.AD_Z + 0.5f, ad, 0f, HoopsLook.AD_Z + 0.5f, -ad, 0f, HoopsLook.AD_Z + 0.5f, white, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = flash * 0.3f, tint = accent)
        }

        // Dust in the beams: slow drifting motes.
        val dot = TexKit.dot.full
        for (i in 0 until MOTES) {
            val x = (hash01(i, 91) - 0.5f) * 300f + sin(t * 0.3f + i) * 8f
            val y = 30f + (hash01(i, 92) * 330f + t * (4f + hash01(i, 93) * 5f)) % 330f
            val z = -300f + hash01(i, 94) * 150f
            r.sprite(x, y, z, 2.6f, 2.6f, dot, blend = Blend.ADD, emissive = 1f, alpha = 0.3f * (0.6f + 0.4f * sin(t * 1.3f + i * 2.1f)), tint = 0xFFFFE6C0.toInt())
        }

        // Camera flashes in the crowd (none with reduce motion: they are flashing lights).
        if (!calm) {
            val flare = HoopsArt.flare.full
            val active = 5 + (hype * 7f).toInt()
            for (i in 0 until active.coerceAtMost(FLASHES)) {
                val period = 1.7f + hash01(i, 93) * 2.6f
                val u = t + hash01(i, 94) * period
                val cycle = (u / period).toInt()
                val phase = u - cycle * period
                if (phase > 0.1f) continue
                val x = -180f + hash01(i, cycle, 95) * 360f
                val y = 60f + hash01(i, cycle, 96) * 190f
                val tint = when ((i + cycle) % 3) {
                    0 -> 0xFFDDEEFF.toInt()
                    1 -> 0xFFFFE9A8.toInt()
                    else -> 0xFFFFB8E0.toInt()
                }
                r.sprite(x, y, HoopsGeo.WALL_Z + 2f, 16f, 16f, flare, blend = Blend.ADD, emissive = 1f, alpha = 1f - phase / 0.1f, tint = tint)
            }
        }
    }

    private fun drawPanel(r: Renderer3D, region: Region, cx: Float, pop: Float) {
        val hw = HoopsLook.PANEL_HW * pop
        val hh = HoopsLook.PANEL_HH * pop
        val y = HoopsLook.SIGN_Y
        val z = HoopsLook.PANEL_Z
        r.quad(cx - hw, y + hh, z, cx + hw, y + hh, z, cx + hw, y - hh, z, cx - hw, y - hh, z, region, 0f, 0f, 1f, emissive = HoopsLook.PANEL_EMISSIVE)
    }

    /** Pools of lamp light on the court: under the hoop and where the shooter stands. */
    fun drawPools(r: Renderer3D, hoopX: Float, streak: Int) {
        val glow = TexKit.glow.full
        val a = HoopsLook.POOL_ALPHA + flash * HoopsLook.POOL_FLASH
        r.flat(hoopX * S, -190f, 0.7f, 250f, 330f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = mixArgb(0xFFFFD9A0.toInt(), stateColor(streak), 0.35f))
        r.flat(0f, -25f, 0.7f, 190f, 150f, glow, blend = Blend.ADD, emissive = 1f, alpha = HoopsLook.POOL_ALPHA * 0.8f, tint = 0xFFFFD9A0.toInt())
    }

    /**
     * The backboard on its rail, with its LED frame, lit markings, a slow glint across the glass
     * and the flash where a ball struck it. [hoopX] is the board's offset in metres.
     */
    fun drawBoard(r: Renderer3D, hoopX: Float, t: Float, streak: Int) {
        val hx = hoopX * S
        boardXf.set(hx, 0f, 0f)
        boardModel.draw(r, xf = boardXf)
        val accent = accent(streak, t)
        val level = HoopsLook.LED_BASE + HoopsLook.LED_PULSE * (0.5f + 0.5f * sin(t * 4f)) + (flash + boardFlash * 0.6f) * HoopsLook.LED_FLASH
        ledModel.draw(r, emissiveBoost = level, xf = boardXf, tint = accent)

        val hw = HoopsGeo.BOARD_HALF_W * S
        val top = HoopsGeo.BOARD_TOP * S
        val bottom = HoopsGeo.BOARD_BOTTOM * S
        val bz = -HoopsGeo.BOARD_Z * S
        val fire = streak >= HoopsTuning.MAX_MULTIPLIER
        val flick = if (fire) 0.85f + 0.15f * sin(t * 19f) else 1f
        val marks = (HoopsLook.BOARD_GLOW_ALPHA + 0.2f * sin(t * 3f) + flash * 0.4f + boardFlash * 0.3f) * flick
        // The lit markings on the glass, and a wash of the state colour behind the board.
        r.quad(hx - hw, top, bz + 0.6f, hx + hw, top, bz + 0.6f, hx + hw, bottom, bz + 0.6f, hx - hw, bottom, bz + 0.6f, HoopsArt.boardMarks.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = marks.coerceIn(0f, 1f), tint = accent)
        r.sprite(hx, (top + bottom) / 2f, bz - 4f, 240f, 190f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.12f + flash * 0.16f + (if (fire) 0.08f else 0f), tint = accent)
        // A slow glint across the glass every few seconds.
        val gp = (t % 9f) / 3.2f
        if (gp < 1f) {
            val gx = hx + (gp * 2f - 1f) * (hw + 10f)
            val fade = sin(gp * Math.PI.toFloat())
            r.quad(gx - 12f, top, bz + 0.9f, gx + 12f, top, bz + 0.9f, gx - 6f, bottom, bz + 0.9f, gx - 30f, bottom, bz + 0.9f, HoopsArt.glint.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.3f * fade, tint = 0xFFC0D0FF.toInt())
        }
        // A light chasing round the frame.
        val half = hw + HoopsLook.FRAME_W / 2f
        val topY = top + HoopsLook.FRAME_W / 2f
        val botY = bottom - HoopsLook.FRAME_W / 2f
        val per = 4f * half + 2f * (topY - botY)
        for (i in 0 until 3) {
            val u = ((t * 0.32f - i * 0.02f) % 1f + 1f) % 1f
            chase(r, hx, u * per, half, topY, botY, bz + 3.6f, 22f - i * 6f, 0.55f - i * 0.15f)
        }
        // The flash where a ball hit the glass.
        if (boardFlash > 0.02f) {
            r.sprite(hx + boardHitX, boardHitY, bz + 2f, 60f, 60f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = boardFlash * 0.8f, tint = 0xFFB8D8FF.toInt())
        }
    }

    /** A bead of light [d] centimetres round the frame's perimeter (clockwise from the top left). */
    private fun chase(r: Renderer3D, hx: Float, d: Float, half: Float, top: Float, bottom: Float, z: Float, size: Float, alpha: Float) {
        val wTop = 2f * half
        val hgt = top - bottom
        var p = d
        val x: Float
        val y: Float
        when {
            p < wTop -> { x = -half + p; y = top }
            p < wTop + hgt -> { p -= wTop; x = half; y = top - p }
            p < 2f * wTop + hgt -> { p -= wTop + hgt; x = half - p; y = bottom }
            else -> { p -= 2f * wTop + hgt; x = -half; y = bottom + p }
        }
        r.sprite(hx + x, y, z, size, size, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = 0xFFFFE8C8.toInt())
    }

    // ---------------------------------------------------------------- net

    private val ringY = FloatArray(HoopsLook.NET_RINGS + 1)
    private val ringR = FloatArray(HoopsLook.NET_RINGS + 1)
    private val ringX = FloatArray(HoopsLook.NET_RINGS + 1)

    /**
     * The net: two families of strands weaving diamonds down a tapering cone. It hangs from the
     * rim at [rimY] (scene units, with the rim's wobble), stretches and kicks after a make
     * ([swish] 1 to 0) and bulges round a ball passing through at height [ballY] (negative for
     * none).
     */
    fun drawNet(r: Renderer3D, hoopX: Float, rimY: Float, t: Float, swish: Float, ballY: Float) {
        val tex = TexKit.white.full
        val rings = HoopsLook.NET_RINGS
        val rimR = HoopsGeo.RIM_R * S
        val drop = HoopsLook.NET_DROP + swish * 8f
        val sway = sin(t * 20f) * swish * 3f
        for (j in 0..rings) {
            val f = j / rings.toFloat()
            ringY[j] = rimY - 1f - drop * f
            var rad = rimR * lerp(1f, HoopsLook.NET_FOOT, f)
            if (ballY >= 0f) {
                val d = (ringY[j] - ballY) / HoopsLook.NET_BULGE_H
                rad += HoopsLook.NET_BULGE * exp(-d * d)
            }
            ringR[j] = rad * (1f + swish * 0.05f * sin(t * 26f + f * 3f))
            ringX[j] = sway * f
        }
        val cx = hoopX * S
        val cz = -HoopsGeo.HOOP_Z * S
        val n = HoopsLook.NET_STRANDS
        val netTint = 0xFFE4E0F0.toInt()
        for (i in 0 until n) {
            val a = i / n.toFloat() * TAU
            for (family in 0..1) {
                val dir = if (family == 0) -1f else 1f
                for (j in 0 until rings) {
                    val f0 = j / rings.toFloat()
                    val f1 = (j + 1) / rings.toFloat()
                    val a0 = a + dir * HoopsLook.NET_TWIST * f0
                    val a1 = a + dir * HoopsLook.NET_TWIST * f1
                    r.beam(
                        cx + cos(a0) * ringR[j] + ringX[j], ringY[j], cz + sin(a0) * ringR[j],
                        cx + cos(a1) * ringR[j + 1] + ringX[j + 1], ringY[j + 1], cz + sin(a1) * ringR[j + 1],
                        1.5f, tex, blend = Blend.ALPHA, alpha = 0.85f, tint = netTint,
                    )
                }
            }
        }
    }

    /** The two side nets of the cage and its roof, a fine cool mesh. */
    fun drawCage(r: Renderer3D) {
        val w = HoopsGeo.CAGE_HALF_W * S
        val back = HoopsGeo.WALL_Z
        val net = HoopsArt.cageNetRegion
        val cell = 8f
        val tw = net.w.toFloat()
        val lenU = (-100f - back) / cell * tw
        val hU = 420f / cell * tw
        for (x in floatArrayOf(-w, w)) {
            r.quad(x, 420f, back, x, 420f, -100f, x, 0f, -100f, x, 0f, back, net, if (x < 0f) 1f else -1f, 0f, 0f, u1 = lenU, v1 = hU, blend = Blend.ALPHA, alpha = 0.7f, cull = false)
        }
        r.quad(-w, 420f, -100f, w, 420f, -100f, w, 420f, back, -w, 420f, back, net, 0f, -1f, 0f, u1 = w * 2f / cell * tw, v1 = lenU, blend = Blend.ALPHA, alpha = 0.7f, cull = false)
    }

    // ---------------------------------------------------------------- effects

    /**
     * Glowing trails behind balls in flight, the shockwaves and glints of makes and hits, and
     * (on fire) a halo round the ball and the hoop. Draws last among the additive layers.
     */
    fun drawEffects(r: Renderer3D, hoopX: Float, rimY: Float, t: Float, streak: Int, ballD: Float) {
        val glow = TexKit.glow.full
        val fire = streak >= HoopsTuning.MAX_MULTIPLIER
        val color = if (fire) 0xFFFFA030.toInt() else 0xFFFF8A38.toInt()
        val ring = HoopsArt.ring.full
        val flare = HoopsArt.flare.full
        for (i in 0 until BALLS) {
            for (s in 0 until TRAIL_N) {
                val at = i * TRAIL_N + s
                val age = trailAge[at]
                if (age >= TRAIL_LIFE) continue
                val u = age / TRAIL_LIFE
                val size = ballD * (1f - 0.55f * u)
                val a = HoopsLook.TRAIL_ALPHA * (1f - u) * (if (fire) 1.35f else 1f)
                r.sprite(trailPos[at * 3], trailPos[at * 3 + 1], trailPos[at * 3 + 2], size, size, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = color)
                if (fire && age < TRAIL_DT * 1.5f) {
                    r.sprite(trailPos[at * 3], trailPos[at * 3 + 1], trailPos[at * 3 + 2], ballD * 2.4f, ballD * 2.4f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.3f, tint = Pal.ORANGE)
                }
            }
        }
        if (fire) {
            // The hoop is on fire: a flickering heat glow.
            val flick = 0.75f + 0.25f * sin(t * 17f) * sin(t * 6.1f)
            r.sprite(hoopX * S, rimY + 4f, -HoopsGeo.HOOP_Z * S + 6f, 120f, 100f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.32f * flick, tint = Pal.ORANGE)
        }
        for (b in bursts) {
            if (b.age < 0f || b.age >= b.life) continue
            val p = b.age / b.life
            val e = 1f - (1f - p) * (1f - p)
            val size = lerp(b.s0, b.s1, e)
            val a = b.alpha * (1f - p) * (1f - p * 0.3f)
            when (b.kind) {
                K_RING_FLAT -> r.flat(b.x, b.z, b.y, size, size, ring, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                K_RING_UP -> r.sprite(b.x, b.y, b.z, size, size, ring, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                K_FLARE -> r.sprite(b.x, b.y, b.z, size, size, flare, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                else -> r.sprite(b.x, b.y, b.z, size, size, glow, blend = Blend.ADD, emissive = 1f, alpha = a * 0.7f, tint = b.tint)
            }
        }
    }
}
