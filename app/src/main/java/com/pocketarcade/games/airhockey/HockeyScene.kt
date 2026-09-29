package com.pocketarcade.games.airhockey

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.len
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb
import com.pocketarcade.hub.beveledBox
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rink's measurements, in world units (x across, y up, z from the CPU's end to the
 * player's; the simulation's y is the world's z). Shared by the simulation and the 3D scene.
 */
internal object HockeyGeo {
    const val RL = 40f
    const val RR = 320f
    const val RT = 60f
    const val RB = 600f
    const val CX = 180f
    const val CY = (RT + RB) / 2f
    const val GOAL_HALF = 62f
    const val PUCK_R = 13f
    const val MALLET_R = 22f
    const val RAIL_H = 12f
    /** Radius of the table's rounded corners, which steer pucks back into play. */
    const val CORNER_R = 46f
    /** Height of the mallet's grip plane, where touches land. */
    const val MALLET_H = 8f
    /** Height of the table's top above the arena floor, and so the floor's height. */
    const val TABLE_H = 160f
    const val FLOOR_Y = -TABLE_H
}

/** Every number that shapes how the air hockey scene looks, in one place. */
internal object HockeyLook {
    // ---- Lights: a dim cool ambient, a lamp over the table, a cyan and a pink light at the two
    // ends, a little red-orange light that travels with the puck, and a flash for goals.
    const val AMB_R = 0.3f
    const val AMB_G = 0.32f
    const val AMB_B = 0.46f
    const val DIR_R = 0.22f
    const val DIR_G = 0.23f
    const val DIR_B = 0.3f
    const val LAMP_Y = 380f
    const val LAMP_RADIUS = 700f
    const val LAMP_INTENSITY = 0.95f
    const val END_Y = 90f
    const val END_OFFSET = 60f
    const val END_RADIUS = 420f
    const val END_INTENSITY = 0.8f
    const val PUCK_LIGHT_Y = 26f
    const val PUCK_LIGHT_RADIUS = 190f
    const val PUCK_LIGHT_LIVE = 0.8f
    const val PUCK_LIGHT_SERVE = 0.3f
    const val GOAL_LIGHT_RADIUS = 380f
    const val GOAL_LIGHT_PEAK = 2.4f

    // ---- Materials and post effects.
    const val SURFACE_GLOSS = 0.55f
    const val RAIL_GLOSS = 0.6f
    const val MALLET_GLOSS = 0.75f
    const val FLOOR_GLOSS = 0.45f
    const val VIGNETTE = 0.28f
    const val BLOOM = 0.9f

    // ---- Emissive strengths and additive glows.
    const val WALL_EMISSIVE = 0.9f
    const val LED_LEVEL = 1.15f
    const val UNDERGLOW_ALPHA = 0.22f
    const val NEON_GLOW_ALPHA = 0.32f
    const val TRAIL_ALPHA = 0.55f

    /** How much of each flash and pulse survives the reduce-motion setting. */
    const val CALM_K = 0.4f

    // ---- The wall behind the far end and the scoreboard in front of it (scene units).
    const val WALL_Z = -300f
    const val WALL_HALF_W = 580f
    const val WALL_TOP = 420f
    const val BOARD_HW = 94f
    const val BOARD_Y0 = 156f
    const val BOARD_Y1 = 214f
    const val FLOOR_HALF_X = 700f
}

/**
 * The air hockey table as a 3D scene: a dark glossy playfield under neon, on an arena floor
 * with an LED wall behind it and a lit scoreboard, with everything that answers the game (hits,
 * goals, the serve, the puck's trail and light). It keeps all the *visual* state, so the
 * simulation never reads it and rendering never touches the game's random generator.
 *
 * No texture is built until something is drawn, so headless tests can step a game freely.
 */
internal class HockeyScene {
    private companion object {
        const val TRAIL_N = 10
        const val BURSTS = 14
        const val K_RING = 0
        const val K_FLARE = 1
        const val K_GLOW = 2
        const val CYAN = 0xFF4FE8FF.toInt()
        const val PINK = 0xFFFF4FA8.toInt()
        const val SURFACE = 1.3f
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
    private val trailX = FloatArray(TRAIL_N)
    private val trailY = FloatArray(TRAIL_N)
    private var trailN = 0
    private var trailT = 0f
    private var meGlow = 0f
    private var cpuGlow = 0f

    /** Clears every glow and the trail, for a fresh round. */
    fun reset() {
        for (b in bursts) b.age = b.life
        trailN = 0
        trailT = 0f
        meGlow = 0f
        cpuGlow = 0f
    }

    private fun calmK(): Float = if (ScreenShake.intensity <= 0f) HockeyLook.CALM_K else 1f

    /** Ages the glows (called from the game's fixed step). */
    fun step(dt: Float) {
        for (b in bursts) if (b.age < b.life) b.age += dt
        meGlow = (meGlow - dt * 5f).coerceAtLeast(0f)
        cpuGlow = (cpuGlow - dt * 5f).coerceAtLeast(0f)
    }

    /** Adds the puck's position to its trail (a sample every 0.02 s). */
    fun trail(x: Float, y: Float, dt: Float) {
        trailT -= dt
        if (trailT > 0f) return
        trailT = 0.02f
        for (i in TRAIL_N - 1 downTo 1) {
            trailX[i] = trailX[i - 1]; trailY[i] = trailY[i - 1]
        }
        trailX[0] = x; trailY[0] = y
        trailN = (trailN + 1).coerceAtMost(TRAIL_N)
    }

    fun clearTrail() {
        trailN = 0
    }

    private fun burst(kind: Int, x: Float, y: Float, z: Float, s0: Float, s1: Float, life: Float, alpha: Float, tint: Int) {
        val b = bursts[burstNext]
        burstNext = (burstNext + 1) % BURSTS
        b.kind = kind; b.x = x; b.y = y; b.z = z
        b.s0 = s0; b.s1 = s1; b.life = life; b.age = 0f
        b.alpha = alpha * calmK(); b.tint = tint
    }

    // ---------------------------------------------------------------- events

    /** A mallet struck the puck at ([x], [y]) on the table with [power] 0..1. */
    fun malletHit(x: Float, y: Float, power: Float, byPlayer: Boolean) {
        val color = if (byPlayer) CYAN else PINK
        burst(K_RING, x, SURFACE, y, 30f, 44f + power * 100f, 0.32f, 0.5f + power * 0.4f, color)
        burst(K_FLARE, x, 10f, y, 18f, 34f + power * 60f, 0.2f, 0.9f, Pal.mix(color, Pal.WHITE, 0.6f))
        if (byPlayer) meGlow = 1f else cpuGlow = 1f
    }

    /** The puck struck a rail at ([x], [y]) at [strength] 0..1. */
    fun wallHit(x: Float, y: Float, strength: Float) {
        burst(K_FLARE, x, HockeyGeo.RAIL_H, y, 10f, 22f + strength * 26f, 0.18f, 0.55f + strength * 0.4f, 0xFFB8ECFF.toInt())
    }

    /** A goal at the far end ([byPlayer]) or the near one: a shockwave across the table and a flare in the slot. */
    fun goal(byPlayer: Boolean) {
        val z = if (byPlayer) HockeyGeo.RT else HockeyGeo.RB
        val color = if (byPlayer) CYAN else PINK
        burst(K_RING, HockeyGeo.CX, SURFACE, z, 60f, 700f, 0.95f, 0.75f, color)
        burst(K_FLARE, HockeyGeo.CX, 16f, z, 40f, 300f, 0.5f, 1f, Pal.mix(color, Pal.WHITE, 0.5f))
        burst(K_GLOW, HockeyGeo.CX, 14f, z, 140f, 460f, 0.8f, 0.6f, color)
    }

    // ---------------------------------------------------------------- lights

    private val lamp = PointLight(HockeyGeo.CX, HockeyLook.LAMP_Y, HockeyGeo.CY, 1f, 0.96f, 0.9f, HockeyLook.LAMP_RADIUS, HockeyLook.LAMP_INTENSITY)
    private val nearLight = PointLight(HockeyGeo.CX, HockeyLook.END_Y, HockeyGeo.RB + HockeyLook.END_OFFSET, 0.25f, 0.9f, 1f, HockeyLook.END_RADIUS, HockeyLook.END_INTENSITY)
    private val farLight = PointLight(HockeyGeo.CX, HockeyLook.END_Y, HockeyGeo.RT - HockeyLook.END_OFFSET, 1f, 0.3f, 0.66f, HockeyLook.END_RADIUS, HockeyLook.END_INTENSITY)
    private val puckLight = PointLight(0f, HockeyLook.PUCK_LIGHT_Y, 0f, 1f, 0.42f, 0.25f, HockeyLook.PUCK_LIGHT_RADIUS, 0f)
    private val goalLight = PointLight(HockeyGeo.CX, 40f, HockeyGeo.RT, 0.4f, 1f, 1f, HockeyLook.GOAL_LIGHT_RADIUS, 0f)

    /** Sets the lighting and the renderer's look for this frame. */
    fun light(r: Renderer3D, puckX: Float, puckY: Float, puckLive: Boolean, goalFlash: Float, goalByPlayer: Boolean) {
        val l = r.lighting
        l.ambR = HockeyLook.AMB_R; l.ambG = HockeyLook.AMB_G; l.ambB = HockeyLook.AMB_B
        l.setDirection(0f, 1f, 0.5f)
        l.dirR = HockeyLook.DIR_R; l.dirG = HockeyLook.DIR_G; l.dirB = HockeyLook.DIR_B
        puckLight.x = puckX
        puckLight.z = puckY
        puckLight.intensity = if (puckLive) HockeyLook.PUCK_LIGHT_LIVE else HockeyLook.PUCK_LIGHT_SERVE
        l.points.clear()
        l.points += lamp
        l.points += nearLight
        l.points += farLight
        l.points += puckLight
        if (goalFlash > 0f) {
            goalLight.z = if (goalByPlayer) HockeyGeo.RT else HockeyGeo.RB
            if (goalByPlayer) {
                goalLight.r = 0.4f; goalLight.g = 1f; goalLight.b = 1f
            } else {
                goalLight.r = 1f; goalLight.g = 0.3f; goalLight.b = 0.5f
            }
            goalLight.intensity = clamp01(goalFlash) * HockeyLook.GOAL_LIGHT_PEAK * calmK()
            l.points += goalLight
        }
        r.vignette = HockeyLook.VIGNETTE
        r.bloom = HockeyLook.BLOOM
    }

    // ---------------------------------------------------------------- models

    private val room: Model by lazy { buildRoom() }
    private val table: Model by lazy { buildTable() }
    private val puckModel: Model by lazy { buildPuck() }
    private val myMallet: Model by lazy { buildMallet(0xFF29C8E8.toInt()) }
    private val cpuMallet: Model by lazy { buildMallet(0xFFE8408C.toInt()) }
    private val xf = Xform()

    private fun buildRoom(): Model {
        val b = ModelBuilder()
        val fx = HockeyLook.FLOOR_HALF_X
        val fy = HockeyGeo.FLOOR_Y
        val wz = HockeyLook.WALL_Z
        val tile = 60f
        val tw = HockeyArt.floorRegion.w.toFloat()
        // The arena floor, tiled, and the LED wall behind the far end.
        b.quad(-fx, fy, wz, fx, fy, wz, fx, fy, 1000f, -fx, fy, 1000f, HockeyArt.floorRegion, 0f, 1f, 0f, u1 = 2f * fx / tile * tw, v1 = (1000f - wz) / tile * tw, gloss = HockeyLook.FLOOR_GLOSS)
        val hw = HockeyLook.WALL_HALF_W
        b.quad(HockeyGeo.CX - hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, fy, wz, HockeyGeo.CX - hw, fy, wz, HockeyArt.wall.full, 0f, 0f, 1f, emissive = HockeyLook.WALL_EMISSIVE)
        return b.build()
    }

    private fun buildTable(): Model {
        val b = ModelBuilder()
        val rl = HockeyGeo.RL
        val rr = HockeyGeo.RR
        val rt = HockeyGeo.RT
        val rb = HockeyGeo.RB
        val cx = HockeyGeo.CX
        val gh = HockeyGeo.GOAL_HALF
        val rh = HockeyGeo.RAIL_H
        val rail = HockeyArt.rail.full
        val body = HockeyArt.body.full
        val post = HockeyArt.post.full
        b.quad(rl, 0f, rt, rr, 0f, rt, rr, 0f, rb, rl, 0f, rb, HockeyArt.surface((rr - rl).toInt(), (rb - rt).toInt(), gh).full, 0f, 1f, 0f, gloss = HockeyLook.SURFACE_GLOSS)
        val g = HockeyLook.RAIL_GLOSS
        // Side rails and the end rails either side of each goal slot, chamfered so the edges catch the lamp.
        b.beveledBox(rl - 16f, 0f, rt - 16f, rl, rh, rb + 16f, BoxFaces(top = rail, right = rail, front = rail, gloss = g), floorAo = 0f, bevel = 1.4f)
        b.beveledBox(rr, 0f, rt - 16f, rr + 16f, rh, rb + 16f, BoxFaces(top = rail, left = rail, front = rail, gloss = g), floorAo = 0f, bevel = 1.4f)
        for (end in 0..1) {
            val z0 = if (end == 0) rt - 16f else rb
            val z1 = z0 + 16f
            b.beveledBox(rl, 0f, z0, cx - gh, rh, z1, BoxFaces(top = rail, front = rail, right = rail, gloss = g), floorAo = 0f, bevel = 1.2f)
            b.beveledBox(cx + gh, 0f, z0, rr, rh, z1, BoxFaces(top = rail, front = rail, left = rail, gloss = g), floorAo = 0f, bevel = 1.2f)
            // The goal slot: a dark pocket below the rail, and a plate over its mouth.
            b.quad(cx - gh, -30f, z0, cx + gh, -30f, z0, cx + gh, -30f, z1, cx - gh, -30f, z1, HockeyArt.slot.full, 0f, 1f, 0f)
            b.box(cx - gh, rh - 4f, z0, cx + gh, rh, z1, BoxFaces(top = rail, front = rail, gloss = g))
        }
        // Rounded corners: a curved rail filling each corner of the rink.
        val halfW = (rr - rl) / 2f
        val halfH = (rb - rt) / 2f
        val cr = HockeyGeo.CORNER_R
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            val ccx = cx + sx * (halfW - cr)
            val ccz = HockeyGeo.CY + sz * (halfH - cr)
            val kx = cx + sx * halfW
            val kz = HockeyGeo.CY + sz * halfH
            val n = 6
            for (i in 0 until n) {
                val t0 = i / n.toFloat() * (Math.PI.toFloat() / 2f)
                val t1 = (i + 1) / n.toFloat() * (Math.PI.toFloat() / 2f)
                val ax = ccx + sx * cos(t0) * cr
                val az = ccz + sz * sin(t0) * cr
                val bx = ccx + sx * cos(t1) * cr
                val bz = ccz + sz * sin(t1) * cr
                b.quad(kx, rh, kz, kx, rh, kz, ax, rh, az, bx, rh, bz, rail, 0f, 1f, 0f, cull = false, gloss = g)
                val tm = (t0 + t1) / 2f
                b.quad(ax, rh, az, bx, rh, bz, bx, 0f, bz, ax, 0f, az, rail, -sx * cos(tm), 0f, -sz * sin(tm), cull = false, gloss = g)
            }
        }
        // Table body under the rink.
        b.beveledBox(rl - 16f, HockeyGeo.FLOOR_Y, rt - 16f, rr + 16f, 0f, rb + 16f, BoxFaces(front = body, left = body, right = body), bevel = 1.2f)
        // The scoreboard on two posts behind the far goal, in a housing.
        for (px in floatArrayOf(cx - 80f, cx + 80f)) {
            b.beveledBox(px - 5f, HockeyGeo.FLOOR_Y, rt - 42f, px + 5f, 150f, rt - 32f, BoxFaces(front = post, left = post, right = post, gloss = 0.4f), bevel = 1f)
        }
        b.beveledBox(cx - 104f, 148f, rt - 46f, cx + 104f, 222f, rt - 32f, BoxFaces(front = post, top = post, left = post, right = post, gloss = 0.5f), floorAo = 0f, bevel = 2.5f)
        return b.build()
    }

    private fun buildPuck(): Model {
        val r = HockeyGeo.PUCK_R
        val profile = floatArrayOf(r - 0.4f, 0f, r, 0.7f, r, 4.2f, r - 0.7f, 5f)
        return ModelBuilder()
            .lathe(0f, 0f, 0f, profile, 14, HockeyArt.puckSide.full, gloss = 0.5f)
            .disc(0f, 0f, 5.02f, r - 0.8f, 14, HockeyArt.puckTop.full, ny = 1f, emissive = 0.9f, gloss = 0.3f)
            .build()
    }

    private fun buildMallet(color: Int): Model {
        val r = HockeyGeo.MALLET_R
        // Foot, base with a chamfered shoulder, neck, knob: matches HockeyArt.malletSkin's bands.
        val profile = floatArrayOf(
            r - 0.5f, 0f, r, 1.4f, r, 5.2f, 16f, 8.2f, 9f, 9.4f, 8.4f, 17f, 10.4f, 19.6f, 0f, 21.6f,
        )
        return ModelBuilder().lathe(0f, 0f, 0f, profile, 16, HockeyArt.malletSkin(color).full, gloss = HockeyLook.MALLET_GLOSS).build()
    }

    // ---------------------------------------------------------------- drawing

    private val board = HockeyArt.Scoreboard(HockeyTuning.GOALS_TO_WIN)

    /** Repaints the scoreboard when a score changes. */
    fun updateBoard(you: Int, cpu: Int) = board.paint(you, cpu)

    /**
     * The arena: floor and LED wall, the glow of the table's underlight on the floor, and the
     * wall flaring in the scorer's colour for a goal. Draws first.
     */
    fun drawRoom(r: Renderer3D, t: Float, goalFlash: Float, goalByPlayer: Boolean) {
        room.draw(r)
        val glow = TexKit.glow.full
        val fy = HockeyGeo.FLOOR_Y + 0.6f
        val pulse = 1f + 0.12f * sin(t * 2.4f)
        val g = clamp01(goalFlash) * calmK()
        val near = if (goalByPlayer) 0f else g
        val far = if (goalByPlayer) g else 0f
        // Underglow: cyan under the player's end, pink under the CPU's, brightening for a goal.
        r.flat(HockeyGeo.CX, 470f, fy, 520f, 520f, glow, blend = Blend.ADD, emissive = 1f, alpha = (HockeyLook.UNDERGLOW_ALPHA + near * 0.5f) * pulse, tint = CYAN)
        r.flat(HockeyGeo.CX, 190f, fy, 520f, 520f, glow, blend = Blend.ADD, emissive = 1f, alpha = (HockeyLook.UNDERGLOW_ALPHA + far * 0.5f) * pulse, tint = PINK)
        if (goalFlash > 0f) {
            val hw = HockeyLook.WALL_HALF_W
            val wz = HockeyLook.WALL_Z + 1f
            r.quad(HockeyGeo.CX - hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyGeo.FLOOR_Y, wz, HockeyGeo.CX - hw, HockeyGeo.FLOOR_Y, wz, TexKit.white.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = g * 0.3f, tint = if (goalByPlayer) CYAN else PINK)
        }
    }

    /** The table, its lit rail inlays and neon skirts, and the glow at each goal mouth. */
    fun drawTable(r: Renderer3D, t: Float, goalFlash: Float, goalByPlayer: Boolean) {
        table.draw(r)
        val rl = HockeyGeo.RL
        val rr = HockeyGeo.RR
        val rt = HockeyGeo.RT
        val rb = HockeyGeo.RB
        val cx = HockeyGeo.CX
        val cy = HockeyGeo.CY
        val gh = HockeyGeo.GOAL_HALF
        val y = HockeyGeo.RAIL_H + 0.2f
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        val level = HockeyLook.LED_LEVEL * (0.9f + 0.1f * sin(t * 3f))
        // Light inlays along the rails' inner edges: pink at the CPU's end, cyan at the player's.
        strip(r, rl - 3.4f, rt - 16f, rl - 2f, cy, y, PINK, level)
        strip(r, rl - 3.4f, cy, rl - 2f, rb + 16f, y, CYAN, level)
        strip(r, rr + 2f, rt - 16f, rr + 3.4f, cy, y, PINK, level)
        strip(r, rr + 2f, cy, rr + 3.4f, rb + 16f, y, CYAN, level)
        strip(r, rl, rt - 3.4f, cx - gh, rt - 2f, y, PINK, level)
        strip(r, cx + gh, rt - 3.4f, rr, rt - 2f, y, PINK, level)
        strip(r, rl, rb + 2f, cx - gh, rb + 3.4f, y, CYAN, level)
        strip(r, cx + gh, rb + 2f, rr, rb + 3.4f, y, CYAN, level)
        // Neon skirts under the side rails, split by team colour, with a soft halo.
        val pulse = HockeyLook.NEON_GLOW_ALPHA + 0.08f * sin(t * 3f)
        for (x in floatArrayOf(rl - 17f, rr + 17f)) {
            r.beam(x, -2f, rt - 16f, x, -2f, cy, 3f, white, emissive = 1.3f, tint = PINK)
            r.beam(x, -2f, cy, x, -2f, rb + 16f, 3f, white, emissive = 1.3f, tint = CYAN)
            r.beam(x, -2f, rt - 16f, x, -2f, cy, 20f, glow, blend = Blend.ADD, emissive = 1f, alpha = pulse, tint = PINK)
            r.beam(x, -2f, cy, x, -2f, rb + 16f, 20f, glow, blend = Blend.ADD, emissive = 1f, alpha = pulse, tint = CYAN)
        }
        // Each goal mouth glows in its team's colour, fiercely for a goal.
        val g = clamp01(goalFlash) * calmK()
        val farA = 0.28f + (if (goalByPlayer) g * 0.7f else 0f)
        val nearA = 0.28f + (if (goalByPlayer) 0f else g * 0.7f)
        r.flat(cx, rt - 8f, y + 0.4f, gh * 2.6f, 56f, glow, blend = Blend.ADD, emissive = 1f, alpha = farA, tint = PINK)
        r.flat(cx, rb + 8f, y + 0.4f, gh * 2.6f, 56f, glow, blend = Blend.ADD, emissive = 1f, alpha = nearA, tint = CYAN)
    }

    private fun strip(r: Renderer3D, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, tint: Int, level: Float) {
        r.quad(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, TexKit.white.full, 0f, 1f, 0f, emissive = level, cull = false, tint = tint)
    }

    /** The lit scoreboard face, glowing, and flashing in the scorer's colour on a goal. */
    fun drawScoreboard(r: Renderer3D, t: Float, goalFlash: Float, goalByPlayer: Boolean) {
        val z = HockeyGeo.RT - 31.5f
        val hw = HockeyLook.BOARD_HW
        val y0 = HockeyLook.BOARD_Y0
        val y1 = HockeyLook.BOARD_Y1
        val cx = HockeyGeo.CX
        r.quad(cx - hw, y1, z, cx + hw, y1, z, cx + hw, y0, z, cx - hw, y0, z, board.tex.full, 0f, 0f, 1f, emissive = 1f)
        // A lamp bar along the top of the housing and a soft halo behind the face.
        r.quad(cx - 100f, 221f, z + 0.2f, cx + 100f, 221f, z + 0.2f, cx + 100f, 219.4f, z + 0.2f, cx - 100f, 219.4f, z + 0.2f, TexKit.white.full, 0f, 0f, 1f, emissive = 1.1f, tint = 0xFFB8ECFF.toInt())
        r.sprite(cx, (y0 + y1) / 2f, z - 10f, 300f, 120f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.14f, tint = 0xFF7AB8FF.toInt())
        if (goalFlash > 0f && (t * 8f).toInt() % 2 == 0) {
            r.quad(cx - hw, y1, z + 0.4f, cx + hw, y1, z + 0.4f, cx + hw, y0, z + 0.4f, cx - hw, y0, z + 0.4f, TexKit.white.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = clamp01(goalFlash) * 0.4f * calmK(), tint = if (goalByPlayer) CYAN else PINK)
        }
    }

    /** Shadow offset for something at ([x], [z]): away from the lamp above the middle of the table. */
    private fun shadowDx(x: Float, h: Float) = (x - HockeyGeo.CX) * h * 0.01f + 2f
    private fun shadowDz(z: Float, h: Float) = (z - HockeyGeo.CY) * h * 0.01f + 3f

    /**
     * The puck, the mallets and what goes with them: contact shadows, a glow ring under each
     * mallet, the puck's warm glow and hot trail, and the serve marker that closes in on the
     * puck before it is released. [serveT] runs the serve delay down to 0.
     */
    fun drawPieces(
        r: Renderer3D, t: Float,
        puckX: Float, puckY: Float, puckVX: Float, puckVY: Float, puckShown: Boolean, puckLive: Boolean,
        meX: Float, meY: Float, meSpeed: Float, cpuX: Float, cpuY: Float, cpuSpeed: Float,
        serveT: Float, serving: Boolean,
    ) {
        val sh = TexKit.shadow.full
        val glow = TexKit.glow.full
        val ring = HockeyArt.ring.full
        val pr = HockeyGeo.PUCK_R
        val mr = HockeyGeo.MALLET_R
        r.flat(puckX + shadowDx(puckX, 5f), puckY + shadowDz(puckY, 5f), 0.4f, pr * 2.6f, pr * 2.6f, sh, blend = Blend.ALPHA, alpha = 0.6f)
        r.flat(meX + shadowDx(meX, 22f), meY + shadowDz(meY, 22f), 0.4f, mr * 2.7f, mr * 2.7f, sh, blend = Blend.ALPHA, alpha = 0.6f)
        r.flat(cpuX + shadowDx(cpuX, 22f), cpuY + shadowDz(cpuY, 22f), 0.4f, mr * 2.7f, mr * 2.7f, sh, blend = Blend.ALPHA, alpha = 0.6f)
        if (puckShown) {
            xf.set(puckX, 0f, puckY)
            puckModel.draw(r, xf = xf)
        }
        xf.set(cpuX, 0f, cpuY)
        cpuMallet.draw(r, xf = xf)
        xf.set(meX, 0f, meY)
        myMallet.draw(r, xf = xf)

        // A glow ring under each mallet, swelling with its speed and with a hit.
        val meRing = mr * (2.5f + meSpeed / 2500f + meGlow * 0.6f)
        val cpuRing = mr * (2.5f + cpuSpeed / 2500f + cpuGlow * 0.6f)
        r.flat(meX, meY, SURFACE, meRing, meRing, ring, blend = Blend.ADD, emissive = 1f, alpha = 0.5f + meGlow * 0.4f, tint = CYAN)
        r.flat(meX, meY, SURFACE, meRing * 1.6f, meRing * 1.6f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.16f + meGlow * 0.3f, tint = CYAN)
        r.flat(cpuX, cpuY, SURFACE, cpuRing, cpuRing, ring, blend = Blend.ADD, emissive = 1f, alpha = 0.5f + cpuGlow * 0.4f, tint = PINK)
        r.flat(cpuX, cpuY, SURFACE, cpuRing * 1.6f, cpuRing * 1.6f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.16f + cpuGlow * 0.3f, tint = PINK)

        val sp = len(puckVX, puckVY)
        if (puckShown) {
            // The puck's own glow, and a hot trail behind a fast one.
            val a = 0.22f + clamp01(sp / 900f) * 0.3f
            r.flat(puckX, puckY, SURFACE + 0.1f, pr * 3.6f, pr * 3.6f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = 0xFFFF7A38.toInt())
        }
        if (puckLive && sp > 300f) {
            val a = clamp01((sp - 300f) / 700f) * HockeyLook.TRAIL_ALPHA
            for (i in 1 until trailN) {
                val k = 1f - i / trailN.toFloat()
                val hot = mixArgb(0xFFFF6A30.toInt(), 0xFFFFE0A8.toInt(), k)
                r.flat(trailX[i], trailY[i], SURFACE + 0.2f, pr * 3.2f * k + 6f, pr * 3.2f * k + 6f, glow, blend = Blend.ADD, emissive = 1f, alpha = a * k, tint = hot)
                if (i % 2 == 0) r.flat(trailX[i], trailY[i], SURFACE + 0.3f, pr * 1.3f * k + 3f, pr * 1.3f * k + 3f, glow, blend = Blend.ADD, emissive = 1f, alpha = a * k, tint = 0xFFFFF2D0.toInt())
            }
        }
        if (!puckLive && serving && serveT > 0f) {
            // The serve: a ring closes in on the puck, and the puck breathes.
            val blink = 0.5f + 0.5f * sin(t * 12f)
            val close = clamp01(serveT / 1.2f)
            val s = lerp(36f, 110f, close)
            r.flat(puckX, puckY, SURFACE + 0.1f, s, s, ring, blend = Blend.ADD, emissive = 1f, alpha = 0.5f + 0.3f * blink, tint = 0xFFFFD0A0.toInt())
            r.flat(puckX, puckY, SURFACE + 0.2f, 60f, 60f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.4f * blink, tint = Pal.WHITE)
        }
    }

    /** Shockwaves, glints and flares from hits and goals, on top of everything. */
    fun drawEffects(r: Renderer3D) {
        val ring = HockeyArt.ring.full
        val flare = HockeyArt.flare.full
        val glow = TexKit.glow.full
        for (b in bursts) {
            if (b.age >= b.life) continue
            val p = b.age / b.life
            val e = 1f - (1f - p) * (1f - p)
            val size = lerp(b.s0, b.s1, e)
            val a = b.alpha * (1f - p) * (1f - p * 0.3f)
            when (b.kind) {
                K_RING -> r.flat(b.x, b.z, b.y, size, size, ring, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                K_FLARE -> r.sprite(b.x, b.y, b.z, size, size, flare, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                else -> r.sprite(b.x, b.y, b.z, size, size, glow, blend = Blend.ADD, emissive = 1f, alpha = a * 0.7f, tint = b.tint)
            }
        }
    }
}
