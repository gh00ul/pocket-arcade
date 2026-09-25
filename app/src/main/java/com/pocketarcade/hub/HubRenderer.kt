package com.pocketarcade.hub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.PixelPainter
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.r3d.FrameBudget
import com.pocketarcade.engine.r3d.FrameImage
import com.pocketarcade.engine.r3d.Renderer3D
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders the hall: the 3D scene into a low-resolution framebuffer (scaled up pixel-sharp),
 * then the prompt bubble, walking hint and joystick on top at screen resolution.
 */
class HubRenderer {
    private val r = Renderer3D(1, 1)
    private val frame = FrameImage()
    private val budget = FrameBudget(budgetMs = 8f, name = "hub")
    private var scene: HubScene3D? = null
    private val painter = PixelPainter()
    private val proj = FloatArray(3)

    /** Screen pixels per framebuffer pixel; grows if the phone can't keep up. */
    var pixelScale = 0
        private set

    fun draw(scope: DrawScope, world: HubWorld, save: SaveState) {
        val sw = scope.size.width
        val sh = scope.size.height
        if (sw < 2f || sh < 2f) return
        if (pixelScale == 0) pixelScale = (sw / 360f).roundToInt().coerceIn(2, 4)
        val fbw = (sw / pixelScale).toInt().coerceAtLeast(16)
        val fbh = (sh / pixelScale).toInt().coerceAtLeast(16)
        r.resize(fbw, fbh)
        val sc = scene?.takeIf { it.map === world.map } ?: HubScene3D(world.map, world.games).also { scene = it }

        budget.begin()
        world.camera.apply(r.camera, fbw, fbh)
        sc.render(r, world, save)
        if (budget.end() && pixelScale < 5) pixelScale++
        frame.draw(scope, r, 0f, 0f, sw, sh)

        val px = sw / fbw
        drawPrompt(scope, world, save, px)
        if (!world.hasWalked) {
            val a = 0.55f + 0.45f * sin(world.time * 4f)
            PixelFont.drawCentered(scope, "DRAG ANYWHERE TO WALK", sw / 2f, sh * 0.8f, floor(px).coerceAtLeast(2f), Color.White, a)
        }
        val js = world.joystick
        if (js.active) {
            val rad = js.radius
            scope.drawCircle(Color.White, rad, Offset(js.baseX, js.baseY), alpha = 0.1f)
            scope.drawCircle(Color.White, rad, Offset(js.baseX, js.baseY), alpha = 0.35f, style = Stroke(width = px * 1.5f))
            scope.drawCircle(Color.White, rad * 0.42f, Offset(js.knobX, js.knobY), alpha = 0.5f)
            scope.drawCircle(Color(Pal.PINK), rad * 0.42f, Offset(js.knobX, js.knobY), alpha = 0.85f, style = Stroke(width = px * 1.2f))
        }
    }

    private fun drawPrompt(scope: DrawScope, world: HubWorld, save: SaveState, px: Float) {
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
                action = "${PixelFont.PLAY} PLAY"
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
                action = "${PixelFont.PLAY} OPEN"
                info = "YOU HAVE ${save.tokens}"
                accent = Pal.GOLD
            }
            SpotType.PRIZES -> {
                title = "PRIZE COUNTER"
                action = "${PixelFont.PLAY} SHOP"
                info = "${save.tickets} TICKETS"
                accent = Pal.PINK
            }
        }
        // Bubble in "bubble pixels" (1 per framebuffer pixel, doubled for legibility).
        val s = floor(px * 2f)
        val tw = maxOf(PixelFont.width(title, 1f, true), PixelFont.width(action, 1f), PixelFont.width(info, 1f, true))
        val bw = tw + 10f
        val bh = 5f + 2f + 7f + 2f + 5f + 8f
        val bob = sin(t * 4f) * 1f
        val ax = proj[0] * px
        val ay = proj[1] * px + bob * s
        val left = ax / s - bw / 2f
        val top = ay / s - bh - 3f
        val pop = easeOutBack(clamp01(world.promptT / 0.22f))
        world.bubbleLeft = left * s
        world.bubbleTop = top * s
        world.bubbleRight = (left + bw) * s
        world.bubbleBottom = (top + bh + 3f) * s
        val pressed = world.bubblePressed >= 0
        scope.withTransform({ scale(pop, pop, Offset(ax, ay)) }) {
            val p = painter.begin(this, 0f, 0f, s)
            p.fill(left + 1f, top + 1f, bw, bh, Color.Black, 0.5f)
            p.fill(left, top, bw, bh, Color(if (pressed) Pal.PLUM else Pal.NIGHT), 0.94f)
            p.frame(left, top, bw, bh, Color(accent))
            val cx = left + bw / 2f
            p.fill(floor(cx) - 2f, top + bh, 5f, 1f, Color(accent))
            p.fill(floor(cx) - 1f, top + bh + 1f, 3f, 1f, Color(accent))
            p.fill(floor(cx), top + bh + 2f, 1f, 1f, Color(accent))
            p.textCentered(title, cx, top + 3f, Color(accent), tiny = true)
            val blink = 0.75f + 0.25f * sin(t * 8f)
            p.textCentered(action, cx, top + 10f, Color.White, alpha = blink)
            val infoAlpha = if (infoColor == Pal.RED) (0.5f + 0.5f * sin(t * 10f)) else 1f
            p.textCentered(info, cx, top + 19f, Color(infoColor), tiny = true, alpha = infoAlpha)
        }
    }
}
