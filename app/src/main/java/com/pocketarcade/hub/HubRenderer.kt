package com.pocketarcade.hub

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.r3d.Renderer3D
import kotlin.math.sin

/**
 * Renders the hall: the 3D scene on the GPU, then the prompt bubble, walking hint and joystick
 * on top.
 */
class HubRenderer {
    companion object {
        const val SLOT = "hub"
    }

    private val r = Renderer3D(1, 1)
    private var scene: HallScene? = null
    private val proj = FloatArray(3)

    fun draw(scope: DrawScope, world: HubWorld, save: SaveState) {
        val sw = scope.size.width
        val sh = scope.size.height
        if (sw < 2f || sh < 2f) return
        val w = sw.toInt()
        val h = sh.toInt()
        val sc = scene?.takeIf { it.map === world.map } ?: HallScene(world.map, world.games).also { scene = it }

        r.startFrame()
        r.resize(w, h)
        world.camera.apply(r.camera, w, h)
        sc.render(r, world, save)
        Gfx.submit(SLOT, r.finishFrame(0, 0, w, h))

        val px = 2f * scope.density
        drawPrompt(scope, world, save)
        if (!world.hasWalked) {
            val a = 0.55f + 0.45f * sin(world.time * 4f)
            ArcadeFont.drawCentered(scope, "DRAG ANYWHERE TO WALK", sw / 2f, sh * 0.8f, px * 1.2f, Color.White, a, tiny = true)
        }
        val js = world.joystick
        if (js.active) {
            val rad = js.radius
            scope.drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.14f)), Offset(js.baseX, js.baseY), rad), rad, Offset(js.baseX, js.baseY))
            scope.drawCircle(Color.White, rad, Offset(js.baseX, js.baseY), alpha = 0.35f, style = Stroke(width = px * 1.2f))
            val knob = Offset(js.knobX, js.knobY)
            scope.drawCircle(Color.Black, rad * 0.44f, knob + Offset(0f, px * 2f), alpha = 0.3f)
            scope.drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB8DD), Color(Pal.PINK), Color(0xFFB02070)), knob - Offset(rad * 0.12f, rad * 0.15f), rad * 0.6f), rad * 0.42f, knob)
            scope.drawCircle(Color.White, rad * 0.42f, knob, alpha = 0.35f, style = Stroke(width = px))
        }
    }

    private fun drawPrompt(scope: DrawScope, world: HubWorld, save: SaveState) {
        val spot = world.activeSpot
        if (spot == null || world.camera.dive > 0.01f || !r.camera.project(spot.anchorX, spot.anchorHeight, spot.anchorZ, proj)) {
            world.bubbleLeft = 0f
            world.bubbleRight = 0f
            return
        }
        val t = world.time
        val title: String
        val action: String
        val info: String
        var infoColor = Pal.GOLD
        val accent: Int
        when (spot.type) {
            SpotType.MACHINE -> {
                val g = world.games[spot.machine]
                title = g.title
                action = "${ArcadeFont.PLAY} PLAY"
                accent = g.look.glow
                if (save.tokens > 0) {
                    info = "COSTS 1 TOKEN"
                } else {
                    info = "NO TOKENS!"
                    infoColor = Pal.RED
                }
            }
            SpotType.TOKENS -> {
                title = "TOKEN MACHINE"
                action = "${ArcadeFont.PLAY} OPEN"
                info = "YOU HAVE ${save.tokens}"
                accent = Pal.GOLD
            }
            SpotType.PRIZES -> {
                title = "PRIZE COUNTER"
                action = "${ArcadeFont.PLAY} SHOP"
                info = "${save.tickets} TICKETS"
                accent = Pal.PINK
            }
        }
        // A dark glass card with an accent edge and a pointer down to the machine.
        val u = 2.3f * scope.density
        val tu = u * 1.05f
        val tw = maxOf(ArcadeFont.width(title, tu, true), ArcadeFont.width(action, u * 1.3f), ArcadeFont.width(info, tu, true))
        val pad = u * 5f
        val bw = tw + pad * 2f
        val bh = pad * 2f + ArcadeFont.height(tu, true) + u * 4f + ArcadeFont.height(u * 1.3f) + u * 4f + ArcadeFont.height(tu, true)
        val tip = u * 5f
        val bob = sin(t * 4f) * u * 0.8f
        val ax = proj[0]
        val ay = proj[1] + bob
        val left = ax - bw / 2f
        val top = ay - bh - tip
        val pop = easeOutBack(clamp01(world.promptT / 0.22f))
        world.bubbleLeft = left
        world.bubbleTop = top
        world.bubbleRight = left + bw
        world.bubbleBottom = ay
        val pressed = world.bubblePressed >= 0
        val accentC = Color(accent)
        scope.withTransform({ scale(pop, pop, Offset(ax, ay)) }) {
            val r = CornerRadius(u * 5f)
            drawRoundRect(Color.Black, Offset(left, top + u * 1.5f), Size(bw, bh), r, alpha = 0.35f)
            val pointer = Path().apply {
                moveTo(ax - tip, top + bh - 1f)
                lineTo(ax + tip, top + bh - 1f)
                lineTo(ax, ay)
                close()
            }
            drawPath(pointer, accentC)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(if (pressed) 0xF03A2A5E else 0xF0241A40), Color(0xF0120C22)), startY = top, endY = top + bh),
                Offset(left, top), Size(bw, bh), r,
            )
            drawRoundRect(accentC, Offset(left, top), Size(bw, bh), r, style = Stroke(u * 0.9f))
            val cx = left + bw / 2f
            var y = top + pad
            ArcadeFont.drawCentered(this, title, cx, y, tu, accentC, tiny = true)
            y += ArcadeFont.height(tu, true) + u * 4f
            val blink = 0.75f + 0.25f * sin(t * 8f)
            ArcadeFont.drawCentered(this, action, cx, y, u * 1.3f, Color.White, alpha = blink)
            y += ArcadeFont.height(u * 1.3f) + u * 4f
            val infoAlpha = if (infoColor == Pal.RED) (0.5f + 0.5f * sin(t * 10f)) else 1f
            ArcadeFont.drawCentered(this, info, cx, y, tu, Color(infoColor), alpha = infoAlpha, tiny = true)
        }
    }
}
