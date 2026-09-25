package com.pocketarcade.hub

import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.games.MiniGame
import kotlin.math.cos
import kotlin.math.sin

/**
 * The title screen's showroom: one of every machine lined up on a polished floor under
 * spotlights, with a slow camera dolly gliding past them while their screens play.
 */
class TitleShowcase(private val games: List<MiniGame>) {
    companion object {
        const val SLOT = "title"
    }

    private val r = Renderer3D(1, 1)
    private val spacing = 58f
    private val rowWidth = spacing * (games.size - 1)
    private val units: List<MachineUnit> = games.mapIndexed { i, g ->
        val (w, d, h) = HubLayout.cabinetSize(g.look.shape)
        val cx = i * spacing - rowWidth / 2f
        // Long machines sit further back so every front lines up.
        val front = 20f
        val prop = Prop(PropKind.MACHINE, cx - w / 2f, front - d, cx + w / 2f, front, h, machine = i, shape = g.look.shape, variant = i)
        MachineUnit(prop, g, MachineArt(g))
    }
    private val spots = units.map { u ->
        PointLight(u.prop.centerX, 110f, 40f, 1f, 0.95f, 0.88f, 150f, 0.9f)
    }
    private val bulb = HallArt.solid(-1).full
    private val halo = HallArt.glow.full
    private val floor = HallArt.tiles.region(wrap = true)

    fun draw(x: Float, y: Float, w: Float, h: Float, t: Float, highScore: (String) -> Int) {
        val fbw = w.toInt().coerceAtLeast(16)
        val fbh = h.toInt().coerceAtLeast(16)
        r.startFrame()
        r.resize(fbw, fbh)
        val sweep = if (games.size > 1) sin(t * 0.2f) * (rowWidth / 2f - 10f) else 0f
        val orbit = sin(t * 0.33f) * 0.4f
        val eyeX = sweep + sin(orbit) * 190f
        val eyeZ = cos(orbit) * 190f + 40f
        r.camera.lookAt(eyeX, 78f, eyeZ, sweep, 34f, -14f, Math.toRadians(44.0).toFloat(), fbw, fbh)
        val l = r.lighting
        l.ambR = 0.34f; l.ambG = 0.3f; l.ambB = 0.42f
        l.setDirection(0.2f, 1f, 0.8f)
        l.dirR = 0.2f; l.dirG = 0.18f; l.dirB = 0.24f
        l.points.clear()
        for (s in spots) l.points += s
        for (u in units) l.points += u.lights
        r.fogNear = 260f
        r.fogFar = 700f
        r.fogFloor = 0.2f
        r.exposure = 1.25f
        r.bloom = 0.9f
        r.clear(0xFF07050E.toInt())
        r.gradient(0xFF0B0718.toInt(), 0xFF2A1450.toInt(), 0, fbh / 2)
        val half = rowWidth / 2f + 140f
        r.quad(-half, 0f, -140f, half, 0f, -140f, half, 0f, 200f, -half, 0f, 200f, floor, 0f, 1f, 0f, u0 = -half * 3f, v0 = -420f, u1 = half * 3f, v1 = 600f, gloss = 0.7f)
        for (u in units) {
            u.refresh(highScore(u.game.id), t)
            u.drawOpaque(r, t)
        }
        for (u in units) {
            val p = u.prop
            r.decal(p.x0 - 16f, p.z1 - 6f, p.x1 + 16f, p.z1 + 44f, 0.2f, halo, Blend.ADD, emissive = 1f, alpha = 0.4f, tint = u.art.glow)
        }
        for (u in units) {
            u.drawTransparent(r)
            u.drawBulbs(r, t, bulb, halo)
        }
        Gfx.submit(SLOT, r.finishFrame(x.toInt(), y.toInt(), fbw, fbh))
    }
}
