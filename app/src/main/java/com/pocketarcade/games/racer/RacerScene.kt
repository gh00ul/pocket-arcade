package com.pocketarcade.games.racer

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.mixArgb
import com.pocketarcade.hub.beveledBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Every number that shapes how the racer's scene looks, in one place so it can be tuned blind. */
internal object RacerLook {
    // ---- Cars.
    const val BODY_GLOSS = 0.6f
    const val GLASS_GLOSS = 0.8f
    /** Tail-light strength (emissive; above 1 so the bloom lifts it). */
    const val TAIL_EMISSIVE = 1.3f
    const val UNDERGLOW_ALPHA = 0.32f
    const val SHADOW_ALPHA = 0.5f
    /** Rivals farther ahead than this are drawn as the cheap model. */
    const val LOD_DISTANCE = 900f

    // ---- Sky.
    const val STARS = 30
    const val STAR_ALPHA = 0.7f
    const val HORIZON_ALPHA = 0.5f
    const val SEARCHLIGHT_ALPHA = 0.09f

    // ---- Road.
    const val ROAD_GLOSS = 0.4f
    const val EDGE_LINE_EMISSIVE = 1.2f
    /** Segments ahead that still get lane dashes and edge lines (further out they'd only shimmer). */
    const val DETAIL_SEGMENTS = 40

    // ---- Effects.
    const val SPEED_LINES = 12
    /** Speed (share of top speed) above which speed lines appear, and the alpha of one at full speed. */
    const val SPEED_LINE_FROM = 0.6f
    const val SPEED_LINE_ALPHA = 0.28f
    const val SMOKE_ALPHA = 0.4f
    const val SKID_ALPHA = 0.5f
    /** How much of each flash and pulse survives the reduce-motion setting. */
    const val CALM_K = 0.4f
}

/**
 * Cars and sky for the racer: car models in two levels of detail (a lit body with a cabin, wing,
 * tail-light bar and wheels; and a plain one for cars far down the road), the starry synthwave
 * sky with its searchlights, and the flames and streaks that go with speed. Nothing here reads
 * the simulation, and nothing builds a texture until it is drawn.
 */
internal class RacerScene {
    private companion object {
        const val HALF_W = 22f
        const val TAIL = 0xFFFF2A2A.toInt()
    }

    private val xf = Xform()
    /** For placing parts while a model is built, so building one mid-frame never disturbs [xf]. */
    private val buildXf = Xform()
    private val hiModels = arrayOfNulls<Model>(RacerTuning.CARS)
    private val loModels = arrayOfNulls<Model>(RacerTuning.CARS)

    private fun calmK(): Float = if (ScreenShake.intensity <= 0f) RacerLook.CALM_K else 1f

    // ---------------------------------------------------------------- cars

    /** A car's paint, painted once and shared by its detailed and plain models. */
    private class CarPaint(val side: Region, val top: Region, val rear: Region)

    private val paints = arrayOfNulls<CarPaint>(RacerTuning.CARS)

    private fun paint(i: Int, color: Int): CarPaint =
        paints[i] ?: CarPaint(RacerArt.carSide(color).full, RacerArt.carTop(color).full, RacerArt.carRear(color).full).also { paints[i] = it }

    /** The car of [color] for grid slot [i], detailed or plain. */
    private fun model(i: Int, color: Int, hi: Boolean): Model {
        val cache = if (hi) hiModels else loModels
        return cache[i] ?: (if (hi) buildCar(paint(i, color)) else buildFarCar(paint(i, color))).also { cache[i] = it }
    }

    private fun buildCar(paint: CarPaint): Model {
        val side = paint.side
        val top = paint.top
        val rear = paint.rear
        val glass = RacerArt.glass.full
        val white = TexKit.white.full
        val g = RacerLook.BODY_GLOSS
        val b = ModelBuilder()
        // The tub, chamfered so its edges catch the light; the trunk deck on it; the cabin with its
        // sloping rear glass and sides.
        b.beveledBox(-HALF_W, 4f, -40f, HALF_W, 15f, 40f, BoxFaces(front = rear, left = side, right = side, top = top, gloss = g), floorAo = 0f, bevel = 1.6f)
        b.beveledBox(-19f, 15f, 16f, 19f, 18f, 39f, BoxFaces(front = side, left = side, right = side, top = top, gloss = g), floorAo = 0f, bevel = 0.8f)
        val gg = RacerLook.GLASS_GLOSS
        b.quad(-11f, 27f, 6f, 11f, 27f, 6f, 14f, 15f, 12f, -14f, 15f, 12f, glass, 0f, 0.447f, 0.894f, gloss = gg)
        b.quad(-11f, 27f, -8f, 11f, 27f, -8f, 11f, 27f, 6f, -11f, 27f, 6f, top, 0f, 1f, 0f, gloss = g)
        b.poly(
            floatArrayOf(-11f, -11f, -14f, -14f), floatArrayOf(27f, 27f, 15f, 15f), floatArrayOf(-8f, 6f, 12f, -12f),
            floatArrayOf(0f, 16f, 16f, 0f), floatArrayOf(0f, 0f, 8f, 8f), glass, -0.968f, 0.242f, 0f, gloss = gg,
        )
        b.poly(
            floatArrayOf(11f, 11f, 14f, 14f), floatArrayOf(27f, 27f, 15f, 15f), floatArrayOf(6f, -8f, -12f, 12f),
            floatArrayOf(0f, 16f, 16f, 0f), floatArrayOf(0f, 0f, 8f, 8f), glass, 0.968f, 0.242f, 0f, gloss = gg,
        )
        // Rear wing on two pillars, with end plates.
        b.beveledBox(-24f, 24f, 31f, 24f, 26.2f, 41f, BoxFaces(front = side, left = side, right = side, top = top, gloss = g), floorAo = 0f, bevel = 0.5f)
        b.box(-9f, 15f, 33f, -6f, 24f, 37f, BoxFaces(front = side, left = side, right = side))
        b.box(6f, 15f, 33f, 9f, 24f, 37f, BoxFaces(front = side, left = side, right = side))
        b.box(-24.6f, 22f, 31f, -24f, 28f, 41f, BoxFaces(left = side, right = side, front = side, top = side))
        b.box(24f, 22f, 31f, 24.6f, 28f, 41f, BoxFaces(left = side, right = side, front = side, top = side))
        // Tail lights: a bar across the tail and brighter lenses at each end.
        val z = 40.2f
        b.quad(-19f, 12.4f, z, 19f, 12.4f, z, 19f, 10.2f, z, -19f, 10.2f, z, white, 0f, 0f, 1f, emissive = RacerLook.TAIL_EMISSIVE, tint = TAIL)
        b.quad(-19f, 13.2f, z + 0.05f, -11f, 13.2f, z + 0.05f, -11f, 9.6f, z + 0.05f, -19f, 9.6f, z + 0.05f, white, 0f, 0f, 1f, emissive = RacerLook.TAIL_EMISSIVE * 1.4f, tint = 0xFFFF6A5A.toInt())
        b.quad(11f, 13.2f, z + 0.05f, 19f, 13.2f, z + 0.05f, 19f, 9.6f, z + 0.05f, 11f, 9.6f, z + 0.05f, white, 0f, 0f, 1f, emissive = RacerLook.TAIL_EMISSIVE * 1.4f, tint = 0xFFFF6A5A.toInt())
        // Wheels.
        val wheel = wheelModel()
        for (sx in 0..1) for (sz in 0..1) {
            val x = if (sx == 0) -HALF_W - 2f + 4.5f else HALF_W + 2f - 4.5f
            val zc = if (sz == 0) -26f else 26f
            b.add(wheel, buildXf.set(x, 8f, zc, roll = (PI / 2.0).toFloat()))
        }
        return b.build()
    }

    /**
     * One wheel, eight-sided, turned to lie with its axle along y (the caller rolls it into
     * place): a tyre with a rim face on each side. The faces are two-sided, since a wheel is
     * seen from whichever side the camera is on.
     */
    private fun wheelModel(): Model {
        val rim = RacerArt.wheel.full
        val b = ModelBuilder().cylinder(0f, 0f, -4.5f, 4.5f, 8f, 8, RacerArt.tyre.full)
        val n = 8
        for (k in 0..1) {
            val y = if (k == 0) 4.5f else -4.5f
            val xs = FloatArray(n)
            val ys = FloatArray(n) { y }
            val zs = FloatArray(n)
            val us = FloatArray(n)
            val vs = FloatArray(n)
            for (i in 0 until n) {
                val a = i * (2f * PI.toFloat() / n)
                xs[i] = cos(a) * 8f
                zs[i] = sin(a) * 8f
                us[i] = 8f + cos(a) * 8f
                vs[i] = 8f + sin(a) * 8f
            }
            b.poly(xs, ys, zs, us, vs, rim, 0f, if (k == 0) 1f else -1f, 0f, cull = false)
        }
        return b.build()
    }

    /** The cheap car for the distance: a body, a cabin, a wing and a tail-light bar. */
    private fun buildFarCar(paint: CarPaint): Model {
        val side = paint.side
        val top = paint.top
        val rear = paint.rear
        val glass = RacerArt.glass.full
        val b = ModelBuilder()
        b.box(-HALF_W, 4f, -40f, HALF_W, 17f, 40f, BoxFaces(front = rear, left = side, right = side, top = top))
        b.box(-14f, 17f, -10f, 14f, 27f, 12f, BoxFaces(front = glass, left = glass, right = glass, top = top))
        b.box(-24f, 24f, 31f, 24f, 26f, 41f, BoxFaces(front = side, top = top, left = side, right = side))
        b.quad(-19f, 12.4f, 40.2f, 19f, 12.4f, 40.2f, 19f, 10.2f, 40.2f, -19f, 10.2f, 40.2f, TexKit.white.full, 0f, 0f, 1f, emissive = RacerLook.TAIL_EMISSIVE, tint = TAIL)
        return b.build()
    }

    /**
     * Draws car [i] (paint [color]) at ([x], [y], [z]) turned by [yaw] and leaning by [roll],
     * pitched to the road's [grade] (height gained per unit of distance) with a contact shadow
     * and a glow of its colour on the road under it, both laid along the slope.
     */
    fun drawCar(r: Renderer3D, i: Int, color: Int, hi: Boolean, x: Float, y: Float, z: Float, yaw: Float, roll: Float, grade: Float) {
        decal(r, TexKit.shadow.full, Blend.ALPHA, 0f, RacerLook.SHADOW_ALPHA, -1, x, y + 0.35f, z, 58f, 100f, grade)
        decal(r, TexKit.glow.full, Blend.ADD, 1f, RacerLook.UNDERGLOW_ALPHA, color, x, y + 0.6f, z, 84f, 138f, grade)
        xf.set(x, y, z, yaw = yaw, pitch = grade, roll = roll)
        model(i, color, hi).draw(r, xf = xf)
    }

    /** A [w] × [len] patch of road centred on ([x], [y], [z]) that rises by [grade] per unit towards the horizon. */
    private fun decal(r: Renderer3D, region: Region, blend: Blend, emissive: Float, alpha: Float, tint: Int, x: Float, y: Float, z: Float, w: Float, len: Float, grade: Float) {
        val hw = w / 2f
        val hl = len / 2f
        val yFar = y + grade * hl
        val yNear = y - grade * hl
        r.quad(x - hw, yFar, z - hl, x + hw, yFar, z - hl, x + hw, yNear, z + hl, x - hw, yNear, z + hl, region, 0f, 1f, 0f, blend = blend, emissive = emissive, alpha = alpha, cull = false, tint = tint)
    }

    // ---------------------------------------------------------------- flames and speed

    /**
     * The player's exhaust: two plumes of stacked glows that lengthen and flicker with [boostK]
     * (0 to 1, ramping in and out), cyan for a turbo and gold for a super turbo, and a
     * dimmer pair of cool flickers at full speed. [x], [y] are the car's; the tail is at z = 41.
     */
    fun drawFlames(r: Renderer3D, x: Float, y: Float, boostK: Float, superK: Float, speedK: Float, t: Float) {
        val glow = TexKit.glow.full
        if (boostK > 0.01f) {
            val hot = mixArgb(Pal.CYAN, Pal.ORANGE, superK)
            val core = mixArgb(Pal.WHITE, Pal.YELLOW, superK)
            val flick = 0.85f + 0.15f * sin(t * 70f)
            for (side in 0..1) {
                val sx = x + if (side == 0) -9f else 9f
                for (k in 0 until 4) {
                    val zz = 42f + k * 9f * boostK
                    val size = (18f - k * 2.5f) * (0.6f + 0.4f * boostK) * flick
                    r.sprite(sx, y + 8f, zz, size * 1.3f, size, glow, blend = Blend.ADD, emissive = 1.2f, alpha = boostK * (0.8f - k * 0.17f), tint = if (k < 2) core else hot)
                }
            }
            r.sprite(x, y + 9f, 62f, 46f * boostK + 12f, 30f * boostK + 8f, glow, blend = Blend.ADD, emissive = 1.1f, alpha = 0.45f * boostK * flick, tint = hot)
        } else if (speedK > 0.8f) {
            val a = (0.35f + 0.25f * sin(t * 50f)) * ((speedK - 0.8f) / 0.2f).coerceIn(0f, 1f)
            r.sprite(x, y + 8f, 46f, 30f, 18f, glow, blend = Blend.ADD, emissive = 1f, alpha = a, tint = Pal.CYAN)
        }
    }

    /**
     * Streaks of light rushing past at speed and in a turbo: thin beams, toward the camera, beside
     * and above the road, placed by hash so nothing is stored. [speedK] is speed as a share of
     * top speed, [boostK] the turbo ramp; [x], [y] are the player's, [t] the clock.
     */
    fun drawSpeedLines(r: Renderer3D, x: Float, y: Float, speedK: Float, boostK: Float, t: Float) {
        val k = ((speedK - RacerLook.SPEED_LINE_FROM) / (1f - RacerLook.SPEED_LINE_FROM)).coerceIn(0f, 1f) + boostK * 0.6f
        if (k < 0.02f) return
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        val alpha = RacerLook.SPEED_LINE_ALPHA * k.coerceAtMost(1.4f) * calmK()
        val tint = mixArgb(0xFFCFE8FF.toInt(), Pal.CYAN, boostK)
        val span = 900f
        for (i in 0 until RacerLook.SPEED_LINES) {
            val side = if (hash01(i, 61) > 0.5f) 1f else -1f
            val lx = x + side * (70f + hash01(i, 62) * 260f)
            val ly = y + 6f + hash01(i, 63) * 120f
            val zPos = 300f - ((hash01(i, 64) * span + t * (2200f + 1600f * boostK)) % span)
            val len = 40f + 130f * k
            // Streaks fade in and out along their run so they never pop.
            val life = ((300f - zPos) / span).coerceIn(0f, 1f)
            val fade = sin(life * PI.toFloat())
            r.beam(lx, ly, zPos, lx, ly, zPos + len, 2.2f, white, blend = Blend.ADD, emissive = 1f, alpha = alpha * fade, tint = tint)
            if (k > 0.6f && i % 4 == 0) r.sprite(lx, ly, zPos, 10f, 10f, glow, blend = Blend.ADD, emissive = 1f, alpha = alpha * fade * 0.6f, tint = tint)
        }
    }

    // ---------------------------------------------------------------- sky

    /**
     * The sky behind the road: a horizon glow, twinkling stars, the striped sun with its halo,
     * slow searchlights sweeping from the city, and two skylines and a mountain range that drift
     * against the bend ahead at different rates ([shift] is the bend's sideways drift).
     */
    fun drawSky(r: Renderer3D, shift: Float, t: Float) {
        val far = -3200f
        val glow = TexKit.glow.full
        val calm = ScreenShake.intensity <= 0f
        // Horizon glow, under everything else.
        r.quad(shift - 3400f, 360f, far - 20f, shift + 3400f, 360f, far - 20f, shift + 3400f, -40f, far - 20f, shift - 3400f, -40f, far - 20f, RacerArt.horizon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = RacerLook.HORIZON_ALPHA, tint = Pal.PINK)
        // Stars in the upper sky.
        val dot = TexKit.dot.full
        for (i in 0 until RacerLook.STARS) {
            val x = shift * 0.2f + (hash01(i, 11) - 0.5f) * 5200f
            val y = 420f + hash01(i, 12) * 1300f
            val tw = if (calm) 0.75f else 0.55f + 0.45f * sin(t * (1.2f + hash01(i, 13) * 1.6f) + i * 3.1f)
            val s = 9f + hash01(i, 14) * 12f
            r.sprite(x, y, far - 40f, s, s, dot, blend = Blend.ADD, emissive = 1f, alpha = RacerLook.STAR_ALPHA * tw, tint = if (i % 5 == 0) Pal.HOTPINK else 0xFFDDE8FF.toInt())
        }
        // The sun, its halo, and a warm bloom round it.
        r.sprite(shift, 260f, far, 1100f, 1100f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.28f, tint = Pal.ORANGE)
        r.sprite(shift, 260f, far, 900f, 900f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = Pal.PINK)
        r.sprite(shift, 250f, far + 10f, 520f, 520f, RacerArt.sun.full, emissive = 1.1f)
        // Searchlights from the city, sweeping slowly.
        val shaft = TexKit.glow.full
        for (i in 0 until 3) {
            val sway = if (calm) 0f else sin(t * 0.35f + i * 2.1f) * 260f
            val bx = shift * 1.5f + (i - 1) * 1300f
            r.beam(bx, 0f, far + 100f, bx + sway, 1500f, far + 100f, 180f, shaft, blend = Blend.ADD, emissive = 1f, alpha = RacerLook.SEARCHLIGHT_ALPHA, tint = if (i == 1) Pal.CYAN else Pal.HOTPINK)
        }
        // Mountains, and the skylines in front of them, far to near.
        val m = RacerArt.mountains.full
        r.quad(shift * 1.3f - 3200f, 330f, far + 60f, shift * 1.3f + 3200f, 330f, far + 60f, shift * 1.3f + 3200f, -40f, far + 60f, shift * 1.3f - 3200f, -40f, far + 60f, m, 0f, 0f, 1f, emissive = 0.9f)
        val sf = RacerArt.skylineFar.full
        r.quad(shift * 1.45f - 2600f, 170f, far + 90f, shift * 1.45f + 2600f, 170f, far + 90f, shift * 1.45f + 2600f, -40f, far + 90f, shift * 1.45f - 2600f, -40f, far + 90f, sf, 0f, 0f, 1f, emissive = 0.7f)
        val s = RacerArt.skyline.full
        r.quad(shift * 1.6f - 2200f, 190f, far + 120f, shift * 1.6f + 2200f, 190f, far + 120f, shift * 1.6f + 2200f, -40f, far + 120f, shift * 1.6f - 2200f, -40f, far + 120f, s, 0f, 0f, 1f, emissive = 0.8f)
    }
}
