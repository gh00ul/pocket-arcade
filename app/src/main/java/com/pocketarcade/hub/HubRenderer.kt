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
        private const val FLOOR_GLOW = 0.45f
        /** Neon, screens and marquees mirrored in the glossy tiles; a faint haze on the carpet. */
        private const val FLOOR_REFLECT = 1.3f
        private const val FLOOR_REFLECT_MATTE = 0.18f
        private const val WALK_HINT = "DRAG ANYWHERE TO WALK"
        private val FP_HINT = "${ArcadeFont.LEFT} DRAG TO WALK      DRAG TO LOOK ${ArcadeFont.RIGHT}"
        private const val FP_TAP_HINT = "OR TAP A MACHINE TO WALK THERE"
        private val STICK_GLOW = listOf(Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.14f))
        private val KNOB = listOf(Color(0xFFFFB8DD), Color(Pal.PINK), Color(0xFFB02070))
        private val KNOB_RUN = listOf(Color(0xFFFFF2B0), Color(Pal.GOLD), Color(0xFFC07A10))
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
        // The blacklight carpet: its neon print fluoresces a little.
        r.floorGlow = FLOOR_GLOW
        r.floorReflect = FLOOR_REFLECT
        // From a kid's eye the carpet's haze reads as a wet floor, so it all but goes in first person.
        r.floorReflectMatte = FLOOR_REFLECT_MATTE * (1f - 0.85f * world.camera.fpAmount)
        world.camera.apply(r.camera, w, h)
        sc.render(r, world, save)
        Gfx.submit(SLOT, r.finishFrame(0, 0, w, h))

        val px = 2f * scope.density
        drawPrompt(scope, world, save)
        val fp = world.camera.fpAmount
        val a = 0.55f + 0.45f * sin(world.time * 4f)
        if (world.camera.dive > 0.01f) {
            // Diving into (or out of) a machine: no hints over the screen.
        } else if (fp > 0.5f) {
            if (!world.hasWalked || !world.hasLooked) {
                ArcadeFont.drawCentered(scope, FP_HINT, sw / 2f, sh * 0.8f, px * 1.2f, Color.White, a, tiny = true)
                ArcadeFont.drawCentered(scope, FP_TAP_HINT, sw / 2f, sh * 0.8f + px * 12f, px * 1.1f, Color.White, a * 0.8f, tiny = true)
            }
        } else if (!world.hasWalked) {
            ArcadeFont.drawCentered(scope, WALK_HINT, sw / 2f, sh * 0.8f, px * 1.2f, Color.White, a, tiny = true)
        }
        val js = world.joystick
        if (js.active) {
            val rad = js.radius
            val base = Offset(js.baseX, js.baseY)
            // In first person the busy, bright hall is right behind the stick: give it a dark
            // backing and a firmer rim so it reads, and a ring where the walk turns into a run.
            if (fp > 0.01f) {
                scope.drawCircle(Color.Black, rad * 1.04f, base, alpha = 0.3f * fp)
                scope.drawCircle(Color.White, rad * Joystick.RUN_FROM, base, alpha = 0.22f * fp, style = Stroke(width = px * 0.8f))
            }
            scope.drawCircle(Brush.radialGradient(STICK_GLOW, base, rad), rad, base)
            scope.drawCircle(Color.White, rad, base, alpha = 0.35f + 0.3f * fp, style = Stroke(width = px * (1.2f + 0.6f * fp)))
            val knob = Offset(js.knobX, js.knobY)
            val kr = rad * 0.42f
            scope.drawCircle(Color.Black, kr * 1.05f, knob + Offset(0f, px * 2f), alpha = 0.3f)
            val running = fp > 0.5f && js.run > 0.5f
            scope.drawCircle(Brush.radialGradient(if (running) KNOB_RUN else KNOB, knob - Offset(rad * 0.12f, rad * 0.15f), rad * 0.6f), kr, knob)
            scope.drawCircle(Color.White, kr, knob, alpha = 0.35f + 0.25f * fp, style = Stroke(width = px))
        } else if (fp > 0.5f && world.camera.dive <= 0.01f && !world.route.active) {
            // First person with no thumb down: a faint ghost of the stick where a left thumb rests.
            val rad = js.radius
            val base = Offset(sw * 0.22f, sh - rad - 56f * scope.density)
            scope.drawCircle(Color.Black, rad, base, alpha = 0.12f * fp)
            scope.drawCircle(Color.White, rad, base, alpha = 0.16f * fp, style = Stroke(width = px))
            scope.drawCircle(Color.White, rad * 0.42f, base, alpha = 0.12f * fp)
        }
    }

    private fun drawPrompt(scope: DrawScope, world: HubWorld, save: SaveState) {
        val spot = world.activeSpot
        val fp = world.camera.fpAmount
        // In first person the bubble floats at about eye level in front of the machine instead of
        // over its top, which is above the view when you stand right at it.
        val anchorY = spot?.let { it.anchorHeight + (minOf(it.anchorHeight, HubCamera.EYE_HEIGHT + 8f) - it.anchorHeight) * fp } ?: 0f
        val projected = spot != null && r.camera.project(spot.anchorX, anchorY, spot.anchorZ, proj)
        if (spot == null || world.camera.dive > 0.01f || (!projected && fp < 0.5f)) {
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
        // Keep the whole bubble on screen, clear of the HUD along the top: up close in first
        // person the anchor can be off the top or side, or (looking away) behind the eye.
        val sw = scope.size.width
        val sh = scope.size.height
        val edge = u * 4f
        val topLimit = maxOf(world.hudBottom, 84f * scope.density) + u * 3f + bh + tip
        val ax = (if (projected) proj[0] else sw / 2f).coerceIn(minOf(edge + bw / 2f, sw / 2f), maxOf(sw - edge - bw / 2f, sw / 2f))
        val ay = (if (projected) proj[1] else sh * 0.42f).coerceIn(minOf(topLimit, sh * 0.7f), sh * 0.7f) + bob
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
