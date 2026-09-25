package com.pocketarcade.hub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.PixelPainter
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TintCache
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.drawPixelImage
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.hash01
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws the hall: floor, glow pools, depth-sorted props and characters, neon, prompt and joystick. */
object HubRenderer {
    private val painter = PixelPainter()

    private class Item {
        var sortY = 0f
        var prop: Prop? = null
        var npc: Npc? = null
        var player = false
        var clerk = false
    }

    private val pool = ArrayList<Item>()
    private val order = ArrayList<Item>()

    private val flamingoNeon: ImageBitmap by lazy {
        val c = PixelCanvas(20, PropArt.FLAMINGO_ROWS.size)
        c.sprite(PropArt.FLAMINGO_ROWS, 0, 0, mapOf('#' to Pal.WHITE))
        c.toImageBitmap()
    }

    fun draw(scope: DrawScope, world: HubWorld, save: SaveState) {
        val s = world.scale
        val cam = world.camera
        val ox = (cam.x * s).roundToInt().toFloat()
        val oy = (cam.y * s).roundToInt().toFloat()
        val map = world.map
        val t = world.time
        val screenW = scope.size.width
        val screenH = scope.size.height

        scope.drawRect(Color(Pal.NIGHT), Offset.Zero, scope.size)

        // Static floor and walls: only the visible slice of the background.
        val bg = map.background
        val sx0 = floor(cam.x).toInt().coerceIn(0, bg.width - 1)
        val sy0 = floor(cam.y).toInt().coerceIn(0, bg.height - 1)
        val sw = (ceil(cam.viewW).toInt() + 2).coerceAtMost(bg.width - sx0)
        val sh = (ceil(cam.viewH).toInt() + 2).coerceAtMost(bg.height - sy0)
        scope.drawImage(
            image = bg,
            srcOffset = IntOffset(sx0, sy0),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset((sx0 * s - ox).roundToInt(), (sy0 * s - oy).roundToInt()),
            dstSize = IntSize((sw * s).roundToInt(), (sh * s).roundToInt()),
            filterQuality = FilterQuality.None,
        )

        val p = painter.begin(scope, -ox, -oy, s)
        val viewTop = cam.y - 100f
        val viewBottom = cam.y + cam.viewH + 100f

        // Neon glow pools under cabinets.
        for (prop in map.props) {
            if (prop.bottom < viewTop || prop.y > viewBottom) continue
            val color = when (prop.kind) {
                PropKind.MACHINE -> world.games[prop.machine].look.glow
                PropKind.TOKENS -> Pal.GOLD
                PropKind.COUNTER -> Pal.PINK
                PropKind.DECOR -> when (prop.decor) {
                    DecorStyle.FLAMINGO -> Pal.PINK
                    DecorStyle.JUKEBOX -> Pal.PURPLE
                    DecorStyle.LAVA_LAMP -> Pal.ORANGE
                    DecorStyle.FISH_TANK -> Pal.SKY
                    else -> continue
                }
                else -> continue
            }
            val pulse = 0.55f + 0.2f * sin(t * 2.2f + prop.x * 0.1f)
            val gw = prop.sprite.w * 2.4f
            glow(scope, prop.x + prop.sprite.w / 2f, prop.bottom - 4f, gw, gw * 0.55f, color, pulse, s, ox, oy)
        }

        // Disco ball light spots sweeping the floor.
        if (DecorStyle.DISCO_BALL in save.ownedDecor) {
            val colors = intArrayOf(Pal.PINK, Pal.CYAN, Pal.YELLOW, Pal.LIME, Pal.WHITE, Pal.PURPLE)
            for (k in 0 until 18) {
                val a = t * 0.5f + k * TAU / 18f
                val r = 24f + 70f * hash01(k, 4)
                val x = map.discoX + cos(a) * r * 1.3f
                val y = map.discoY + 50f + sin(a) * r
                p.fill(x, y, 3f, 2f, Color(colors[k % colors.size]), 0.35f)
            }
        }

        // Depth-sorted props and characters.
        order.clear()
        var used = 0
        fun item(): Item {
            if (used == pool.size) pool.add(Item())
            val it = pool[used++]
            it.prop = null; it.npc = null; it.player = false; it.clerk = false
            order.add(it)
            return it
        }
        for (prop in map.props) {
            if (prop.bottom < viewTop || prop.y > viewBottom) continue
            item().apply { this.prop = prop; sortY = prop.sortY }
        }
        for (n in world.npcs) {
            if (n.y < viewTop || n.y - 30f > viewBottom) continue
            item().apply { npc = n; sortY = n.y }
        }
        item().apply { player = true; sortY = world.player.y }
        if (map.clerkY > viewTop) item().apply { clerk = true; sortY = map.clerkY }
        for (i in 1 until order.size) {
            val cur = order[i]
            var j = i - 1
            while (j >= 0 && order[j].sortY > cur.sortY) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = cur
        }
        for (it in order) {
            val prop = it.prop
            when {
                prop != null -> drawProp(scope, world, save, prop, s, ox, oy)
                it.npc != null -> it.npc?.let { n -> drawCharacter(scope, n.frames, n.dir, n.frame, n.x, n.y, n.hop, s, ox, oy) }
                it.player -> {
                    val pl = world.player
                    if (pl.frames.isNotEmpty()) drawCharacter(scope, pl.frames, pl.dir, pl.frame, pl.x, pl.y, 0f, s, ox, oy)
                }
                it.clerk -> {
                    val bob = if ((t % 3f) < 0.2f) 1f else 0f
                    drawCharacter(scope, world.clerkFrames, CharacterArt.DOWN, 0, map.clerkX, map.clerkY, bob, s, ox, oy)
                }
            }
        }

        // Neon sign over the prize counter, with the odd flickering letter.
        val sign = "POCKET ARCADE"
        val signW = PixelFont.width(sign, 1f)
        val signX = map.signCenterX - signW / 2f
        val buzz = 0.8f + 0.2f * sin(t * 13f) * sin(t * 3.1f)
        p.fill(signX - 3f, map.signY - 2f, signW + 6f, 11f, Color(Pal.PINK), 0.12f * buzz)
        p.text(sign, signX + 1f, map.signY + 1f, Color(Pal.shade(Pal.PINK, 0.4f)))
        p.text(sign, signX, map.signY, Color(Pal.HOTPINK), alpha = buzz)
        val flickIndex = (hash01((t * 3f).toInt(), 9) * 40).toInt()
        if (flickIndex < sign.length && sign[flickIndex] != ' ' && hash01((t * 20f).toInt(), 3) < 0.5f) {
            p.fill(signX + flickIndex * PixelFont.ADV, map.signY, 5f, 7f, Color(Pal.PLUM), 0.75f)
        }

        // Neon strips along the side walls.
        val wallPulse = 0.35f + 0.25f * sin(t * 1.7f)
        p.fill(15f, 48f, 1f, map.heightPx - 64f, Color(Pal.CYAN), wallPulse)
        p.fill(16f, 48f, 2f, map.heightPx - 64f, Color(Pal.CYAN), wallPulse * 0.25f)
        p.fill(map.widthPx - 16f, 48f, 1f, map.heightPx - 64f, Color(Pal.CYAN), wallPulse)
        p.fill(map.widthPx - 18f, 48f, 2f, map.heightPx - 64f, Color(Pal.CYAN), wallPulse * 0.25f)

        world.particles.draw(scope, -ox, -oy, s)

        drawPrompt(scope, world, save, s, ox, oy)

        if (!world.hasWalked) {
            val a = 0.55f + 0.45f * sin(t * 4f)
            PixelFont.drawCentered(scope, "DRAG ANYWHERE TO WALK", screenW / 2f, screenH * 0.82f, (s * 0.75f).coerceAtLeast(2f), Color.White, a)
        }

        val js = world.joystick
        if (js.active) {
            val r = js.radius
            scope.drawCircle(Color.White, r, Offset(js.baseX, js.baseY), alpha = 0.1f)
            scope.drawCircle(Color.White, r, Offset(js.baseX, js.baseY), alpha = 0.35f, style = Stroke(width = s * 0.8f))
            scope.drawCircle(Color.White, r * 0.42f, Offset(js.knobX, js.knobY), alpha = 0.5f)
            scope.drawCircle(Color(Pal.PINK), r * 0.42f, Offset(js.knobX, js.knobY), alpha = 0.85f, style = Stroke(width = s * 0.7f))
        }
    }

    private fun glow(scope: DrawScope, cx: Float, cy: Float, w: Float, h: Float, color: Int, alpha: Float, s: Float, ox: Float, oy: Float) {
        val img = PropArt.glow
        scope.drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((cx - w / 2f) * s - ox).roundToInt(), ((cy - h / 2f) * s - oy).roundToInt()),
            dstSize = IntSize((w * s).roundToInt(), (h * s).roundToInt()),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = TintCache.get(Color(color)),
            blendMode = BlendMode.Plus,
            filterQuality = FilterQuality.Low,
        )
    }

    private fun drawCharacter(
        scope: DrawScope, frames: Array<Array<ImageBitmap>>, dir: Int, frame: Int,
        x: Float, y: Float, hop: Float, s: Float, ox: Float, oy: Float,
    ) {
        scope.drawOval(Color.Black, Offset((x - 6f) * s - ox, (y - 2f) * s - oy), Size(12f * s, 4f * s), alpha = 0.35f)
        val img = frames[dir][frame]
        scope.drawPixelImage(img, (x - CharacterArt.FEET_X) * s - ox, (y - CharacterArt.FEET_Y - hop) * s - oy, s)
    }

    private fun drawProp(scope: DrawScope, world: HubWorld, save: SaveState, prop: Prop, s: Float, ox: Float, oy: Float) {
        val t = world.time
        val p = painter.begin(scope, -ox, -oy, s)
        if (prop.decor == DecorStyle.DISCO_BALL) {
            p.fill(prop.x + 7f, world.camera.y - 2f, 1f, prop.y - world.camera.y + 2f, Color(Pal.GRAY))
        }
        scope.drawPixelImage(prop.sprite.image, prop.x * s - ox, prop.y * s - oy, s)
        when (prop.kind) {
            PropKind.MACHINE -> drawMachineOverlay(scope, world, save, prop, s, ox, oy)
            PropKind.BROKEN -> {
                val scr = prop.sprite.screen ?: return
                val x0 = prop.x + scr.x
                val y0 = prop.y + scr.y
                val frameNo = (t * 12f).toInt()
                for (k in 0 until 4) {
                    val row = (hash01(frameNo, k, 5) * scr.h).toInt()
                    p.fill(x0, y0 + row, scr.w.toFloat(), 1f, Color(Pal.GRAY), 0.35f)
                }
                if ((t * 1.5f).toInt() % 2 == 0) p.textCentered("SOON", x0 + scr.w / 2f, y0 + 5f, Color(Pal.LIGHTGRAY), tiny = true)
            }
            PropKind.TOKENS -> {
                val scr = prop.sprite.screen ?: return
                val x0 = prop.x + scr.x
                val y0 = prop.y + scr.y
                val spin = abs(cos(t * 4f))
                val cw = 1f + spin * 5f
                p.fill(x0 + 5f - cw / 2f + 3f, y0 + 1f, cw, 6f, Color(Pal.GOLD))
                if (spin > 0.5f) p.fill(x0 + 7.5f, y0 + 2f, 1f, 4f, Color(Pal.ORANGE))
                val on = (t * 2f).toInt() % 2 == 0
                p.fill(prop.x + 15f, prop.y + 25f, 4f, 4f, Color(if (on) Pal.RED else Pal.DARKRED))
                p.textCentered("${PixelFont.TOKEN}", x0 + 13f, y0 + 1.5f, Color(Pal.YELLOW), tiny = true, alpha = if (on) 1f else 0.4f)
            }
            PropKind.DECOR -> drawDecorOverlay(scope, prop, t, s, ox, oy)
            else -> Unit
        }
    }

    private fun drawMachineOverlay(scope: DrawScope, world: HubWorld, save: SaveState, prop: Prop, s: Float, ox: Float, oy: Float) {
        val game = world.games[prop.machine]
        val t = world.time
        val scr = prop.sprite.screen ?: return
        val sx = (prop.x + scr.x) * s - ox
        val sy = (prop.y + scr.y) * s - oy
        val showHigh = (t + prop.machine * 1.7f) % 8f > 6.2f
        scope.clipRect(sx, sy, sx + scr.w * s, sy + scr.h * s) {
            val sp = painter.begin(this, sx, sy, s)
            if (showHigh) {
                sp.fill(0, 0, scr.w, scr.h, Color(Pal.BLACK))
                sp.textCentered("HI", scr.w / 2f, 1f, Color(Pal.YELLOW), tiny = true)
                sp.textCentered(save.highScore(game.id).toString(), scr.w / 2f, 8f, Color.White, tiny = true)
            } else {
                game.drawAttract(sp, scr.w, scr.h, t + prop.machine * 3.1f)
            }
            // CRT scanlines and a glass glint.
            for (row in 0 until scr.h) {
                drawRect(Color.Black, Offset(sx, sy + row * s + s * 0.66f), Size(scr.w * s, s * 0.34f), alpha = 0.22f)
            }
            drawRect(Color.White, Offset(sx + s, sy + s), Size(s * 2f, s), alpha = 0.25f)
        }
        // Chasing marquee bulbs.
        val p = painter.begin(scope, -ox, -oy, s)
        val trim = game.look.trim
        val bulbs = prop.sprite.bulbs
        val phase = (t * 7f).toInt()
        for (i in bulbs.indices) {
            val (bx, by) = bulbs[i]
            val on = (phase + i / 2) % 3 == 0
            val c = if (on) Pal.YELLOW else Pal.shade(trim, 0.55f)
            p.fill(prop.x + bx, prop.y + by, 1f, 1f, Color(c))
            if (on) p.fill(prop.x + bx - 1f, prop.y + by - 1f, 3f, 3f, Color(Pal.YELLOW), 0.25f)
        }
    }

    private fun drawDecorOverlay(scope: DrawScope, prop: Prop, t: Float, s: Float, ox: Float, oy: Float) {
        val p = painter.begin(scope, -ox, -oy, s)
        val scr = prop.sprite.screen
        when (prop.decor) {
            DecorStyle.LAVA_LAMP -> if (scr != null) {
                for (k in 0 until 3) {
                    val yy = prop.y + scr.y + 2f + (sin(t * 0.6f + k * 2.1f) * 0.5f + 0.5f) * (scr.h - 4f)
                    p.disc(prop.x + scr.x + scr.w / 2f, yy, 1.6f + k * 0.3f, Color(if (k == 1) Pal.YELLOW else Pal.ORANGE))
                }
            }
            DecorStyle.FLAMINGO -> if (scr != null) {
                val on = hash01((t * 6f).toInt(), 17) > 0.06f
                if (on) {
                    val gx = (prop.x + scr.x) * s - ox
                    val gy = (prop.y + scr.y) * s - oy
                    scope.drawPixelImage(flamingoNeon, gx + s, gy + s, s, 0.35f, Color(Pal.PINK))
                    scope.drawPixelImage(flamingoNeon, gx, gy, s, 1f, Color(Pal.HOTPINK))
                }
            }
            DecorStyle.FISH_TANK -> if (scr != null) {
                val x0 = prop.x + scr.x
                val y0 = prop.y + scr.y
                val fishColors = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.PINK)
                for (k in 0 until 3) {
                    val speed = 5f + k * 3f
                    val span = scr.w - 4f
                    val raw = (t * speed + k * 11f) % (span * 2f)
                    val right = raw < span
                    val fx = x0 + 1f + if (right) raw else span * 2f - raw
                    val fy = y0 + 2f + k * 3f + sin(t * 2f + k) * 0.8f
                    p.fill(fx, fy, 3f, 2f, Color(fishColors[k]))
                    p.px(if (right) fx - 1f else fx + 3f, fy, Color(fishColors[k]))
                }
                for (k in 0 until 3) {
                    val by = y0 + scr.h - ((t * 6f + k * 4f) % scr.h)
                    p.px(x0 + 6f + k * 9f, by, Color(Pal.WHITE), 0.6f)
                }
            }
            DecorStyle.JUKEBOX -> if (scr != null) {
                val colors = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME)
                for (k in 0 until 8) {
                    val h = 1f + (sin(t * 7f + k * 1.3f) * 0.5f + 0.5f) * (scr.h - 2f)
                    p.fill(prop.x + scr.x + k * 2f, prop.y + scr.y + scr.h - h, 1f, h, Color(colors[(k + (t * 2f).toInt()) % colors.size]))
                }
                val archC = colors[(t * 3f).toInt() % colors.size]
                p.fill(prop.x + 7f, prop.y + 2f, 12f, 1f, Color(archC), 0.8f)
            }
            else -> Unit
        }
    }

    private fun drawPrompt(scope: DrawScope, world: HubWorld, save: SaveState, s: Float, ox: Float, oy: Float) {
        val spot = world.activeSpot
        if (spot == null) {
            world.bubbleRight = 0f
            world.bubbleLeft = 0f
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
        val tw = maxOf(PixelFont.width(title, 1f, true), PixelFont.width(action, 1f), PixelFont.width(info, 1f, true))
        val bw = tw + 10f
        val bh = 5f + 2f + 7f + 2f + 5f + 8f
        val bob = sin(t * 4f) * 1f
        val left = spot.anchorX - bw / 2f
        val below = spot.bubbleBelow
        val top = if (below) spot.anchorY + 3f + bob else spot.anchorY - bh - 3f + bob
        val pop = easeOutBack(clamp01(world.promptT / 0.22f))
        val ax = spot.anchorX * s - ox
        val ay = (spot.anchorY + bob) * s - oy
        world.bubbleLeft = left * s - ox
        world.bubbleTop = (if (below) top - 3f else top) * s - oy
        world.bubbleRight = (left + bw) * s - ox
        world.bubbleBottom = (top + bh + if (below) 0f else 3f) * s - oy
        val pressed = world.bubblePressed >= 0
        scope.withTransform({ scale(pop, pop, Offset(ax, ay)) }) {
            val p = painter.begin(this, -ox, -oy, s)
            p.fill(left + 1f, top + 1f, bw, bh, Color.Black, 0.5f)
            p.fill(left, top, bw, bh, Color(if (pressed) Pal.PLUM else Pal.NIGHT), 0.94f)
            p.frame(left, top, bw, bh, Color(accent))
            // Pointer toward the anchor.
            if (below) {
                p.fill(spot.anchorX - 2f, top - 1f, 5f, 1f, Color(accent))
                p.fill(spot.anchorX - 1f, top - 2f, 3f, 1f, Color(accent))
                p.fill(spot.anchorX, top - 3f, 1f, 1f, Color(accent))
            } else {
                p.fill(spot.anchorX - 2f, top + bh, 5f, 1f, Color(accent))
                p.fill(spot.anchorX - 1f, top + bh + 1f, 3f, 1f, Color(accent))
                p.fill(spot.anchorX, top + bh + 2f, 1f, 1f, Color(accent))
            }
            p.textCentered(title, spot.anchorX, top + 3f, Color(accent), tiny = true)
            val blink = 0.75f + 0.25f * sin(t * 8f)
            p.textCentered(action, spot.anchorX, top + 10f, Color.White, alpha = blink)
            val infoAlpha = if (infoColor == Pal.RED) (0.5f + 0.5f * sin(t * 10f)) else 1f
            p.textCentered(info, spot.anchorX, top + 19f, Color(infoColor), tiny = true, alpha = infoAlpha)
        }
    }
}
