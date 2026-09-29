package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Xform
import kotlin.math.sin

/**
 * The café's moving parts, drawn each frame from [HallScene]: the barista behind the counter,
 * the slush turning in its tanks and steam off the espresso machine. The static café is built
 * with the other fixtures (see [CafeArt]). Allocation-free per frame.
 */
class CafeScene {
    private companion object {
        val FLAVOURS = intArrayOf(0xFF2FB8FF.toInt(), 0xFFFF3D6E.toInt(), 0xFF7CF25A.toInt())
    }

    private val barista = Figure(Looks.barista)
    private val slush: Array<Model> = Array(FLAVOURS.size) { slushModel(FLAVOURS[it]) }
    private val xf = Xform()
    private val shadow = FigureShadow(HallArt.shadow.full)
    private val puff = HallArt.glow.full

    /** Slush fills most of a tank: a column wrapped in swirled stripes, turned about its axis. */
    private fun slushModel(color: Int): Model {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, lift(color, 0.1f), dim(color, 0.6f))
        for (k in -64 until 128 step 16) tp.line(k.toFloat(), 64f, k + 40f, 0f, 5f, alpha(lift(color, 0.5f), 0.75f))
        for (k in 0 until 30) tp.circle((k * 37 % 64).toFloat(), (k * 23 % 64).toFloat(), 1.2f, alpha(-1, 0.45f))
        val tex = tp.toTexture().also { tp.recycle() }
        val r = CafeLayout.SLUSH_R - 0.45f
        val top = CafeLayout.SLUSH_Y1 - CafeLayout.SLUSH_Y0 - 2.2f
        return ModelBuilder()
            .cylinder(0f, 0f, 0.2f, top, r, 12, tex.full, top = HallArt.solid(lift(color, 0.3f)).full, emissive = 0.95f, gloss = 0.6f)
            // The auger's blade, turning with the slush.
            .box(-0.3f, 0.5f, -r + 0.6f, 0.3f, top - 0.5f, r - 0.6f, com.pocketarcade.engine.r3d.BoxFaces.all(HallArt.solid(lift(color, 0.75f)).full))
            .build()
    }

    fun draw(r: Renderer3D, world: HubWorld, t: Float, minX: Float, maxX: Float, minZ: Float, maxZ: Float) {
        if (CafeLayout.BAR_X1 < minX || CafeLayout.BAR_X0 > maxX || CafeLayout.COUNTER_Z1 < minZ || CafeLayout.BAR_Z0 > maxZ) return
        val b = world.cafe.barista
        barista.draw(r, b.x, 0f, b.z, b.anim, 1.1f, b.item)
        shadow.draw(r, b.x, b.z, 1.1f)

        val tanks = CafeLayout.SLUSH_TANKS
        for (i in tanks.indices) {
            val dir = if (i % 2 == 0) 1f else -1f
            xf.set(tanks[i], CafeLayout.SLUSH_Y0, CafeLayout.SLUSH_Z, yaw = t * 1.7f * dir + i)
            slush[i].draw(r, Blend.OPAQUE, xf = xf)
        }

        // Steam curling up off the espresso machine.
        for (k in 0 until 4) {
            val c = (t * 0.45f + k * 0.25f) % 1f
            val y = CafeLayout.STEAM_Y + c * 16f
            val x = CafeLayout.STEAM_X + sin(t * 1.3f + k * 1.7f) * (1f + c * 2.5f)
            val size = 3f + c * 7f
            r.sprite(x, y, CafeLayout.STEAM_Z, size, size, puff, blend = Blend.ADD, emissive = 1f, alpha = 0.28f * (1f - c) * (0.3f + c * 2f).coerceAtMost(1f))
        }
    }
}
