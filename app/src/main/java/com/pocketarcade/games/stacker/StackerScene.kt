package com.pocketarcade.games.stacker

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb
import com.pocketarcade.hub.beveledBox
import kotlin.math.sin

/** Every number that shapes how the stacker's scene looks, in one place so it can be tuned blind. */
internal object StackerLook {
    // ---- Lights.
    const val AMB_R = 0.38f
    const val AMB_G = 0.38f
    const val AMB_B = 0.5f
    const val DIR_R = 0.6f
    const val DIR_G = 0.58f
    const val DIR_B = 0.56f
    /** The top glow light: the top slab's own colour, lighting the courses just below it. */
    const val TOP_RADIUS = 420f
    const val TOP_INTENSITY = 0.85f
    const val TOP_FLASH = 1.4f
    const val RIM_INTENSITY = 0.55f
    const val KEY_INTENSITY = 0.45f

    // ---- Slab geometry and material.
    /** The chamfer round a slab's top edge, which the neon outline runs along. */
    const val BEVEL = 1.5f
    const val TOP_GLOSS = 0.6f
    const val SIDE_GLOSS = 0.35f
    /** Emissive strength of a slab's outline at full glow, and the least an old slab keeps. */
    const val EDGE_EMISSIVE = 1f
    const val EDGE_MIN = 0.15f
    /** How many courses below the top the outline takes to fade to its minimum. */
    const val GLOW_FADE_LEVELS = 9f

    // ---- The world around the tower.
    const val ROOF_Y = -60f
    const val ROOF_HALF = 380f
    const val CITY_Y = -1100f
    const val CITY_HALF = 3500f
    const val CITY_EMISSIVE = 0.9f
    const val CLOUD_Y_NEAR = -380f
    const val CLOUD_Y_FAR = -760f
    const val CLOUD_ALPHA = 0.5f
    const val COLUMN_ALPHA = 0.08f
    const val MOTES = 16
    /** Height markers stand behind the tower's left, at this place (see the projection test). */
    const val MARKER_X = -225f
    const val MARKER_Z = -120f
    const val MARKER_RANGE = 520f

    /** How much of each flash and pulse survives the reduce-motion setting. */
    const val CALM_K = 0.4f
}

/**
 * The stacker as a 3D scene: a rooftop above a night city with cloud decks between, a tower of
 * dark glass slabs with neon edges that fade from the top down, and every effect that answers
 * the game (perfect drops, cuts, landings, misses, height milestones). It keeps all the
 * *visual* state, so the simulation never reads it and rendering never touches the game's
 * random generator.
 *
 * No texture is built until something is drawn, so headless tests can step a game freely.
 */
internal class StackerScene {
    private companion object {
        const val BURSTS = 12
        const val K_RING = 0
        const val K_FLARE = 1
        const val K_BEAM = 2
        const val S = 0.70710678f
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
    private var missPulse = 0f
    private var comboGlow = 0f

    fun reset() {
        for (b in bursts) b.age = b.life
        missPulse = 0f
        comboGlow = 0f
    }

    private fun calmK(): Float = if (ScreenShake.intensity <= 0f) StackerLook.CALM_K else 1f

    fun step(dt: Float, combo: Int) {
        for (b in bursts) if (b.age < b.life) b.age += dt
        missPulse = (missPulse - dt * 2f).coerceAtLeast(0f)
        // The column of light behind the tower swells with the combo and settles smoothly.
        val target = (combo / 6f).coerceAtMost(1f)
        comboGlow += (target - comboGlow) * (1f - Math.exp((-3f * dt).toDouble()).toFloat())
    }

    private fun burst(kind: Int, x: Float, y: Float, z: Float, s0: Float, s1: Float, life: Float, alpha: Float, tint: Int) {
        val b = bursts[burstNext]
        burstNext = (burstNext + 1) % BURSTS
        b.kind = kind; b.x = x; b.y = y; b.z = z
        b.s0 = s0; b.s1 = s1; b.life = life; b.age = 0f
        b.alpha = alpha * calmK(); b.tint = tint
    }

    // ---------------------------------------------------------------- events

    /** A perfect drop: a shockwave round the slab's top, a glint and a column of light rising from it. */
    fun perfect(x: Float, y: Float, z: Float, w: Float, d: Float, level: Int, combo: Int) {
        val glow = StackerArt.glowColor(level)
        val size = maxOf(w, d)
        burst(K_RING, x, y + 0.6f, z, size * 1.1f, size * (2.2f + combo * 0.12f), 0.6f, 0.85f, glow)
        burst(K_FLARE, x, y + 6f, z, size * 0.3f, size * (0.9f + combo * 0.05f), 0.4f, 1f, Pal.mix(glow, Pal.WHITE, 0.5f))
        burst(K_BEAM, x, y, z, 44f, 12f, 0.7f, 0.55f, glow)
    }

    /** A landing that was not perfect: a faint ring on the slab's top. */
    fun land(x: Float, y: Float, z: Float, w: Float, d: Float) {
        val size = maxOf(w, d)
        burst(K_RING, x, y + 0.6f, z, size * 1.05f, size * 1.5f, 0.35f, 0.3f, 0xFFDDE4FF.toInt())
    }

    /** A slab was trimmed: a flare where it broke off. */
    fun cut(x: Float, y: Float, z: Float, level: Int) {
        burst(K_FLARE, x, y + 6f, z, 14f, 60f, 0.3f, 0.9f, StackerArt.glowColor(level))
    }

    /** A slab was missed altogether. */
    fun miss(x: Float, y: Float, z: Float) {
        missPulse = calmK()
        burst(K_RING, x, y, z, 90f, 520f, 0.7f, 0.6f, 0xFFFF4D4D.toInt())
    }

    /** The tower passed a height worth a banner: a wide ring at [y]. */
    fun milestone(y: Float) {
        burst(K_RING, 0f, y + 1f, 0f, 260f, 1000f, 1.1f, 0.6f, Pal.CYAN)
    }

    // ---------------------------------------------------------------- lights

    private val key = PointLight(200f, 0f, 260f, 1f, 0.95f, 0.9f, 900f, StackerLook.KEY_INTENSITY)
    private val rim = PointLight(-380f, 0f, -260f, 0.3f, 0.7f, 1f, 900f, StackerLook.RIM_INTENSITY)
    private val top = PointLight(0f, 0f, 140f, 1f, 1f, 1f, StackerLook.TOP_RADIUS, StackerLook.TOP_INTENSITY)
    private val roofLight = PointLight(0f, 40f, 0f, 1f, 0.3f, 0.65f, 520f, 0f)

    /** Sets the lighting for a tower whose top slab (level [topLevel]) is at height [topY], the camera at [camY]. */
    fun light(r: Renderer3D, camY: Float, topY: Float, topLevel: Int, flash: Float, climb: Float) {
        val l = r.lighting
        l.ambR = StackerLook.AMB_R; l.ambG = StackerLook.AMB_G; l.ambB = StackerLook.AMB_B
        l.setDirection(0.55f, 1f, 0.35f)
        l.dirR = StackerLook.DIR_R; l.dirG = StackerLook.DIR_G; l.dirB = StackerLook.DIR_B
        l.points.clear()
        key.y = camY + 200f
        l.points += key
        rim.y = camY + 120f
        l.points += rim
        val c = StackerArt.glowColor(topLevel)
        top.x = 0f
        top.y = topY + 50f
        top.r = (c shr 16 and 255) / 255f
        top.g = (c shr 8 and 255) / 255f
        top.b = (c and 255) / 255f
        // A miss washes the light red; a perfect drop lifts it.
        top.intensity = StackerLook.TOP_INTENSITY + flash * StackerLook.TOP_FLASH * calmK()
        if (missPulse > 0f) {
            top.r = 1f; top.g = 0.25f; top.b = 0.2f
            top.intensity += missPulse * 1.2f
        }
        l.points += top
        // Near the start the rooftop is lit from below by its pad rings.
        roofLight.intensity = 0.6f * (1f - climb)
        if (roofLight.intensity > 0.02f) l.points += roofLight
        r.vignette = 0.3f
    }

    // ---------------------------------------------------------------- models

    private val roofModel: Model by lazy { buildRoof() }

    private fun buildRoof(): Model {
        val b = ModelBuilder()
        val h = StackerLook.ROOF_HALF
        val y = StackerLook.ROOF_Y
        b.quad(-h, y, -h, h, y, -h, h, y, h, -h, y, h, StackerArt.roof.full, 0f, 1f, 0f, gloss = 0.4f)
        // A low parapet round the edge, and a lit lip along it.
        val steel = StackerArt.steel.full
        val f = BoxFaces(front = steel, left = steel, right = steel, top = steel, gloss = 0.4f)
        val t = 26f
        val up = y + 28f
        b.beveledBox(-h - t, y, -h - t, h + t, up, -h, f, floorAo = 0f, bevel = 3f)
        b.beveledBox(-h - t, y, h, h + t, up, h + t, f, floorAo = 0f, bevel = 3f)
        b.beveledBox(-h - t, y, -h, -h, up, h, f, floorAo = 0f, bevel = 3f)
        b.beveledBox(h, y, -h, h + t, up, h, f, floorAo = 0f, bevel = 3f)
        return b.build()
    }

    private val xf = Xform()

    // ---------------------------------------------------------------- drawing

    /**
     * The world around the tower: the rooftop and its parapet, the city far below, cloud decks,
     * motes of light, height markers and a column of light behind the tower, brighter with a
     * combo. [climb] runs 0 to 1 as the tower rises. Draws first.
     */
    fun drawWorld(r: Renderer3D, camY: Float, t: Float, climb: Float, topY: Float, topLevel: Int) {
        val ch = StackerLook.CITY_HALF
        val cy = StackerLook.CITY_Y
        val cityTint = mixArgb(0xFFFFFFFF.toInt(), 0xFF505070.toInt(), climb * 0.8f)
        r.quad(-ch, cy, -ch, ch, cy, -ch, ch, cy, ch, -ch, cy, ch, StackerArt.city.full, 0f, 1f, 0f, emissive = StackerLook.CITY_EMISSIVE, tint = cityTint)
        roofModel.draw(r)
        val glow = TexKit.glow.full
        val color = StackerArt.glowColor(topLevel)
        // Cloud decks between the roof and the city, drifting.
        val cloud = StackerArt.cloud.full
        r.flat(sin(t * 0.05f) * 120f, -60f, StackerLook.CLOUD_Y_NEAR, 2800f, 2800f, cloud, blend = Blend.ALPHA, emissive = 0.75f, alpha = StackerLook.CLOUD_ALPHA)
        r.flat(sin(t * 0.04f + 2f) * 160f, 200f, StackerLook.CLOUD_Y_FAR, 3600f, 3600f, cloud, blend = Blend.ALPHA, emissive = 0.6f, alpha = StackerLook.CLOUD_ALPHA * 0.9f)
        // Light pooling on the rooftop under the tower, in the top slab's colour, fading as we climb.
        val roofA = 0.3f * (1f - climb)
        if (roofA > 0.01f) r.flat(0f, 0f, StackerLook.ROOF_Y + 0.8f, 520f, 520f, glow, blend = Blend.ADD, emissive = 1f, alpha = roofA, tint = color)
        // A column of light behind the tower, swelling with the combo.
        val shaft = StackerArt.shaft.full
        val colA = StackerLook.COLUMN_ALPHA + comboGlow * 0.1f
        r.beam(0f, StackerLook.ROOF_Y, -30f, 0f, topY + 260f, -30f, 240f, glow, blend = Blend.ADD, emissive = 1f, alpha = colA, tint = color)
        // Height markers: a bar and its number, every ten courses, to the tower's left.
        for (n in 1..10) {
            val y = (10 * n + 1) * 18f
            if (kotlin.math.abs(y - camY) > StackerLook.MARKER_RANGE) continue
            drawMarker(r, n, y, t)
        }
        // Motes of light drifting up round the tower.
        val dot = TexKit.dot.full
        for (i in 0 until StackerLook.MOTES) {
            val a = hash01(i, 51) * 6.2832f
            val rad = 90f + hash01(i, 52) * 200f
            val x = kotlin.math.cos(a + t * 0.05f) * rad
            val z = kotlin.math.sin(a + t * 0.05f) * rad - 40f
            val span = 700f
            val y = camY - 300f + (hash01(i, 53) * span + t * (10f + hash01(i, 54) * 14f)) % span
            r.sprite(x, y, z, 3.2f, 3.2f, dot, blend = Blend.ADD, emissive = 1f, alpha = 0.4f * (0.6f + 0.4f * sin(t * 1.6f + i * 2.3f)), tint = color)
        }
    }

    private fun drawMarker(r: Renderer3D, n: Int, y: Float, t: Float) {
        val x = StackerLook.MARKER_X
        val z = StackerLook.MARKER_Z
        val white = TexKit.white.full
        val pulse = 0.85f + 0.15f * sin(t * 2.5f + n)
        r.quad(x - 45f, y + 0.9f, z, x + 45f, y + 0.9f, z, x + 45f, y - 0.9f, z, x - 45f, y - 0.9f, z, white, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.6f * pulse, tint = Pal.CYAN)
        r.sprite(x - 20f, y + 12f, z, 45f, 20f, StackerArt.marker(n), blend = Blend.ALPHA, emissive = 1.1f, alpha = 0.95f * pulse)
    }

    /**
     * A slab [w] × [h] × [d] standing on [y], centred on ([x], [z]) and optionally turned by
     * [spin] about the axis it was sliding along (tumbling pieces). Dark glass with a chamfered
     * top edge whose neon [glowK] (1 at full) tints the edge; [flashK] lights the top white.
     */
    fun slab(r: Renderer3D, x: Float, y: Float, h: Float, z: Float, w: Float, d: Float, level: Int, glowK: Float, spin: Float, axisX: Boolean, flashK: Float) {
        if (axisX) xf.set(x, y + h / 2f, z, roll = -spin) else xf.set(x, y + h / 2f, z, pitch = spin)
        val hw = w / 2f
        val hh = h / 2f
        val hd = d / 2f
        val b = StackerLook.BEVEL.coerceAtMost(minOf(hw, hh, hd) * 0.6f)
        val body = StackerArt.bodyColor(level)
        val glow = StackerArt.glowColor(level)
        val edge = glowK.coerceAtLeast(StackerLook.EDGE_MIN) * StackerLook.EDGE_EMISSIVE
        val flat = TexKit.white.full
        val side = StackerArt.slabSide.full
        val topTint = if (flashK > 0.02f) mixArgb(mixArgb(body, Pal.WHITE, 0.12f), 0xFFCFF8FF.toInt(), flashK) else mixArgb(body, Pal.WHITE, 0.12f)
        val topEm = if (flashK > 0.02f) flashK * 0.85f else 0f
        // Top face, inset by the chamfer.
        face(r, flat, topTint, 0f, 1f, 0f, topEm, StackerLook.TOP_GLOSS, 0f, 0f, 0f, 0f,
            -hw + b, hh, -hd + b, hw - b, hh, -hd + b, hw - b, hh, hd - b, -hw + b, hh, hd - b)
        // The four chamfers, glowing: the slab's neon outline.
        face(r, flat, glow, 0f, S, S, edge, 0f, 0f, 0f, 0f, 0f,
            -hw + b, hh, hd - b, hw - b, hh, hd - b, hw, hh - b, hd, -hw, hh - b, hd)
        face(r, flat, glow, S, S, 0f, edge, 0f, 0f, 0f, 0f, 0f,
            hw - b, hh, hd - b, hw - b, hh, -hd + b, hw, hh - b, -hd, hw, hh - b, hd)
        face(r, flat, glow, -S, S, 0f, edge, 0f, 0f, 0f, 0f, 0f,
            -hw + b, hh, -hd + b, -hw + b, hh, hd - b, -hw, hh - b, hd, -hw, hh - b, -hd)
        face(r, flat, glow, 0f, S, -S, edge, 0f, 0f, 0f, 0f, 0f,
            hw - b, hh, -hd + b, -hw + b, hh, -hd + b, -hw, hh - b, -hd, hw, hh - b, -hd)
        // The four sides, shading down to the foot (the texture's own gradient).
        val tw = side.w.toFloat()
        val th = side.h.toFloat()
        face(r, side, body, 0f, 0f, 1f, 0f, StackerLook.SIDE_GLOSS, tw, th, 0f, 0f,
            -hw, hh - b, hd, hw, hh - b, hd, hw, -hh, hd, -hw, -hh, hd)
        face(r, side, body, 1f, 0f, 0f, 0f, StackerLook.SIDE_GLOSS, tw, th, 0f, 0f,
            hw, hh - b, hd, hw, hh - b, -hd, hw, -hh, -hd, hw, -hh, hd)
        face(r, side, body, -1f, 0f, 0f, 0f, StackerLook.SIDE_GLOSS, tw, th, 0f, 0f,
            -hw, hh - b, -hd, -hw, hh - b, hd, -hw, -hh, hd, -hw, -hh, -hd)
        face(r, side, body, 0f, 0f, -1f, 0f, StackerLook.SIDE_GLOSS, tw, th, 0f, 0f,
            hw, hh - b, -hd, -hw, hh - b, -hd, -hw, -hh, -hd, hw, -hh, -hd)
    }

    /** One quad of a slab in the slab's own frame, placed by [xf]; [tw]/[th] are the texture's extent (0 for a flat colour). */
    private fun face(
        r: Renderer3D, region: Region, tint: Int, nx: Float, ny: Float, nz: Float, emissive: Float, gloss: Float,
        tw: Float, th: Float, u0: Float, v0: Float,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
    ) {
        val uw = if (tw > 0f) tw else 4f
        val vh = if (th > 0f) th else 4f
        r.begin(region, Blend.OPAQUE, emissive, 1f, 1f, true, gloss)
        r.normal(xf.dirX(nx, ny, nz), xf.dirY(nx, ny, nz), xf.dirZ(nx, ny, nz))
        r.tint(tint)
        r.vertex(xf.x(ax, ay, az), xf.y(ax, ay, az), xf.z(ax, ay, az), u0, v0)
        r.vertex(xf.x(bx, by, bz), xf.y(bx, by, bz), xf.z(bx, by, bz), uw, v0)
        r.vertex(xf.x(cx, cy, cz), xf.y(cx, cy, cz), xf.z(cx, cy, cz), uw, vh)
        r.vertex(xf.x(dx, dy, dz), xf.y(dx, dy, dz), xf.z(dx, dy, dz), u0, vh)
        r.end()
    }

    /** Shockwaves, glints and rising light from perfect drops, cuts and misses, on top of the tower. */
    fun drawEffects(r: Renderer3D) {
        val ring = StackerArt.ring.full
        val flare = StackerArt.flare.full
        val shaft = StackerArt.shaft.full
        for (b in bursts) {
            if (b.age >= b.life) continue
            val p = b.age / b.life
            val e = 1f - (1f - p) * (1f - p)
            val size = lerp(b.s0, b.s1, e)
            val a = b.alpha * (1f - p) * (1f - p * 0.3f)
            when (b.kind) {
                K_RING -> r.flat(b.x, b.z, b.y, size, size, ring, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                K_FLARE -> r.sprite(b.x, b.y, b.z, size, size, flare, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
                // The beam rises from the slab and thins as it goes.
                else -> r.beam(b.x, b.y, b.z, b.x, b.y + 60f + 260f * e, b.z, size, shaft, blend = Blend.ADD, emissive = 1f, alpha = a, tint = b.tint)
            }
        }
    }
}
