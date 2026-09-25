package com.pocketarcade.hub

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.FrameImage
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.RasterPainter
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.MiniGame
import kotlin.math.cos
import kotlin.math.sin

/**
 * The title screen's showroom: every machine in the arcade lined up on a neon floor, with a
 * slow camera dolly gliding past them while their attract screens play.
 */
class TitleShowcase(private val games: List<MiniGame>) {
    private val r = Renderer3D(1, 1)
    private val frame = FrameImage()
    private val painter = RasterPainter()
    private val spacing = 52f
    private val rowWidth = spacing * (games.size - 1)
    private val entries: List<Triple<MiniGame, CabinetSkin, Model>> = games.mapIndexed { i, g ->
        val skin = HubScene3D.skinFor(g)
        val (w, d, _) = HubLayout.cabinetSize(g.look.shape)
        val cx = i * spacing - rowWidth / 2f
        val deep = g.look.shape == com.pocketarcade.games.CabinetShape.LANE || g.look.shape == com.pocketarcade.games.CabinetShape.TABLE
        val front = if (deep) 30f else 0f
        Triple(g, skin, HubModels.cabinet(skin, cx - w / 2f, front - d, cx + w / 2f, front))
    }
    private val lights = games.mapIndexed { i, g ->
        val c = g.look.glow
        PointLight(i * spacing - rowWidth / 2f, 26f, 24f, (c shr 16 and 255) / 255f, (c shr 8 and 255) / 255f, (c and 255) / 255f, 90f, 1.1f)
    }
    private val floor = Texture.of(PixelCanvas(32, 32).apply {
        for (y in 0 until 32) for (x in 0 until 32) {
            set(x, y, if ((x / 16 + y / 16) % 2 == 0) 0xFF151027.toInt() else 0xFF5E5688.toInt())
        }
    }).region(wrap = true)
    private val glow = HubTextures.glow.full

    fun draw(scope: DrawScope, x: Float, y: Float, w: Float, h: Float, t: Float, highScore: (String) -> Int) {
        val scale = (w / 300f).toInt().coerceIn(2, 5)
        val fbw = (w / scale).toInt().coerceAtLeast(16)
        val fbh = (h / scale).toInt().coerceAtLeast(16)
        r.resize(fbw, fbh)
        val sweep = if (games.size > 1) sin(t * 0.22f) * (rowWidth / 2f - 20f) else 0f
        val orbit = sin(t * 0.37f) * 0.35f
        val eyeX = sweep + sin(orbit) * 170f
        val eyeZ = cos(orbit) * 170f + 20f
        r.camera.lookAt(eyeX, 64f, eyeZ, sweep, 26f, -8f, Math.toRadians(42.0).toFloat(), fbw, fbh)
        r.lighting.ambR = 0.3f; r.lighting.ambG = 0.26f; r.lighting.ambB = 0.4f
        r.lighting.setDirection(0.2f, 1f, 0.8f)
        r.lighting.dirR = 0.25f; r.lighting.dirG = 0.22f; r.lighting.dirB = 0.3f
        r.lighting.points.clear()
        for ((i, l) in lights.withIndex()) {
            l.intensity = 1f + 0.15f * sin(t * 2.2f + i)
            r.lighting.points += l
        }
        r.fogNear = 200f
        r.fogFar = 520f
        r.fogFloor = 0.1f
        r.clear(Pal.NIGHT)
        r.gradient(0xFF0B0718.toInt(), 0xFF2A1450.toInt(), 0, fbh / 2)
        val half = rowWidth / 2f + 80f
        r.quad(-half, 0f, -80f, half, 0f, -80f, half, 0f, 120f, -half, 0f, 120f, floor, 0f, 1f, 0f, u0 = -half * 2f, v0 = -160f, u1 = half * 2f, v1 = 240f)
        for ((i, e) in entries.withIndex()) {
            val (g, skin, model) = e
            skin.paint(g, highScore(g.id), t, i, painter)
            model.draw(r, Blend.OPAQUE)
        }
        for ((i, e) in entries.withIndex()) {
            val cx = i * spacing - rowWidth / 2f
            r.decal(cx - 34f, -6f, cx + 34f, 40f, 0.2f, glow, Blend.ADD, emissive = 1f, alpha = 0.35f, tint = e.first.look.glow)
            e.third.draw(r, Blend.ALPHA)
        }
        frame.draw(scope, r, x, y, w, h)
    }
}
