package com.pocketarcade.games.coinpusher

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Body
import com.pocketarcade.engine.CircleWorld
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Segment
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.drawPixelImage
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Difficulty and payout knobs for the coin pusher. */
object PusherTuning {
    const val ROUND_SECONDS = 50f
    const val COINS_PER_ROUND = 25
    const val DROP_COOLDOWN = 0.18f
    /** Fraction of the hex-packed starting deck left empty (looser deck = fewer spills). */
    const val PACK_GAP_CHANCE = 0.06f
    /** How far (in coin radii) the front row sits back from the lip. */
    const val FRONT_ROW_OVERHANG = 0.6f
    const val START_ITEMS = 4
    /** Chance that a dropped coin brings a bonus item along with it. */
    const val ITEM_DROP_CHANCE = 0.14f
    const val PUSHER_PERIOD = 3.2f
    const val PUSHER_BACK = 150f
    const val PUSHER_FORWARD = 232f
    /** Fraction of velocity lost per second as coins slide on the deck. */
    const val SLIDE_DAMPING = 7f
    const val COIN_POINTS = 10
    const val GEM_POINTS = 50
    const val BIG_COIN_POINTS = 50
    const val TICKET_BUNDLE = 10
    const val SHOWER_COINS = 6
    /** Coins falling off within [AVALANCHE_WINDOW] seconds that trigger the avalanche bonus. */
    const val AVALANCHE_COUNT = 5
    const val AVALANCHE_WINDOW = 0.9f
    const val AVALANCHE_BONUS = 30
    const val POINTS_PER_TICKET = 15
    const val BASE_TICKETS = 1
    /** Seconds after the last coin before an out-of-coins round ends early. */
    const val SETTLE_AFTER_LAST_COIN = 3.5f
}

class CoinPusherGame : BaseMiniGame() {
    override val id = "pusher"
    override val title = "COIN PUSHER"
    override val marquee = "PUSHER"
    override val instructions = listOf(
        "TAP TO DROP A COIN",
        "THE SHELF PUSHES THE PILE",
        "COINS OVER THE FRONT EDGE",
        "SCORE! SIDE GUTTERS DON'T.",
        "GRAB GEMS, STARS & TICKETS",
    )
    override val look = CabinetLook(body = Pal.ORANGE, trim = Pal.GOLD, glow = Pal.YELLOW, shape = CabinetShape.WIDE)
    override val roundSeconds = PusherTuning.ROUND_SECONDS

    private companion object {
        const val PF_L = 30f
        const val PF_R = 330f
        const val DECK_TOP = 92f
        const val FRONT_EDGE = 560f
        const val GUTTER_TOP = 492f
        const val COIN_R = 13f

        const val COIN = 0
        const val GEM = 1
        const val TICKETS = 2
        const val STAR = 3
        const val BIG = 4
    }

    private class Drop {
        var x = 0f
        var t = 0f
        var kind = COIN
        var active = false
    }

    private class Faller {
        var x = 0f
        var y = 0f
        var vy = 0f
        var t = 0f
        var kind = COIN
        var lost = false
        var active = false
        var angle = 0f
    }

    private val world = CircleWorld().apply { iterations = 5; restingSpeed = 1000f }
    private val drops = Array(16) { Drop() }
    private val fallers = Array(40) { Faller() }
    private var coinsLeft = 0
    private var cooldown = 0f
    private var pusherFront = PusherTuning.PUSHER_BACK
    private var pusherV = 0f
    private var pusherPhase = 0f
    private var sinceLastDrop = 0f
    private var won = 0
    private var lost = 0
    private val recentSpills = FloatArray(12)
    private var spillCount = 0
    private var trayFlash = 0f
    private var lowCoinWarned = false

    private val sprites: Array<ImageBitmap> by lazy { buildSprites() }

    init {
        world.constraint = { b -> pushBody(b) }
    }

    private fun radiusFor(kind: Int) = when (kind) {
        BIG -> 20f
        TICKETS -> 15f
        GEM -> 12f
        else -> COIN_R
    }

    private fun pushBody(b: Body) {
        val front = pusherFront + b.r
        if (b.y < front) {
            b.y = front
            if (b.vy < pusherV) b.vy = pusherV
        }
    }

    override fun reset() {
        world.bodies.clear()
        world.segments.clear()
        world.segments.add(Segment(PF_L, DECK_TOP - 40f, PF_L, GUTTER_TOP, 0.2f))
        world.segments.add(Segment(PF_R, DECK_TOP - 40f, PF_R, GUTTER_TOP, 0.2f))
        drops.forEach { it.active = false }
        fallers.forEach { it.active = false }
        coinsLeft = PusherTuning.COINS_PER_ROUND
        cooldown = 0f
        pusherPhase = 0f
        pusherFront = PusherTuning.PUSHER_BACK
        pusherV = 0f
        sinceLastDrop = 0f
        won = 0
        lost = 0
        spillCount = 0
        trayFlash = 0f
        lowCoinWarned = false
        // A real pusher deck is packed edge to edge: a jittered hex pack from the front lip back
        // to just ahead of the shelf, so every push travels through the pile.
        val spacingX = COIN_R * 2f + 0.6f
        val spacingY = spacingX * 0.866f
        var y = FRONT_EDGE - COIN_R * PusherTuning.FRONT_ROW_OVERHANG
        var row = 0
        val backLimit = PusherTuning.PUSHER_FORWARD + COIN_R + 4f
        while (y > backLimit) {
            var x = PF_L + COIN_R + 1f + if (row % 2 == 0) 0f else spacingX / 2f
            while (x < PF_R - COIN_R - 1f) {
                if (rng.nextFloat() > PusherTuning.PACK_GAP_CHANCE) {
                    addBody(COIN, x + rng.range(-1.5f, 1.5f), y + rng.range(-1.5f, 1.5f))
                }
                x += spacingX
            }
            y -= spacingY
            row++
        }
        // Bonus items hide among the coins.
        repeat(PusherTuning.START_ITEMS) { i ->
            val b = world.bodies[rng.nextInt(world.bodies.size)]
            b.kind = 1 + i % 4
            b.r = radiusFor(b.kind)
            b.mass = if (b.kind == BIG) 2.5f else 1f
        }
        repeat(90) { world.step(FIXED_DT) }
        // Anything the settle nudged off the deck is removed without scoring.
        world.bodies.removeAll { it.y > FRONT_EDGE || (it.y > GUTTER_TOP && (it.x < PF_L || it.x > PF_R)) }
        world.bodies.forEach { it.vx = 0f; it.vy = 0f }
    }

    private fun addBody(kind: Int, x: Float, y: Float): Body {
        val b = Body(x, y, radiusFor(kind)).apply {
            this.kind = kind
            damping = PusherTuning.SLIDE_DAMPING
            friction = 0.5f
            restitution = 0.05f
            mass = if (kind == BIG) 2.5f else 1f
            angle = rng.range(0f, TAU)
        }
        world.bodies.add(b)
        return b
    }

    override fun ticketsFor(score: Int): Int = PusherTuning.BASE_TICKETS + score / PusherTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = drops.none { it.active } && fallers.none { it.active }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp || endedEarly) return
        if (y > FRONT_EDGE + 10f) return
        if (coinsLeft <= 0) {
            play(Sfx.ERROR, 0.4f)
            popups.add("NO COINS", GAME_W / 2f, 300f, Color(Pal.GRAY), size = 3f)
            return
        }
        if (cooldown > 0f) return
        cooldown = PusherTuning.DROP_COOLDOWN
        coinsLeft--
        sinceLastDrop = 0f
        val dx = x.coerceIn(PF_L + COIN_R + 4f, PF_R - COIN_R - 4f)
        launchDrop(dx, COIN)
        if (rng.chance(PusherTuning.ITEM_DROP_CHANCE)) {
            val kind = 1 + rng.nextInt(4)
            launchDrop((dx + rng.range(-40f, 40f)).coerceIn(PF_L + 24f, PF_R - 24f), kind)
        }
        play(Sfx.COIN, 0.5f, rng.range(0.9f, 1.1f))
        fx.haptics.tick()
        if (coinsLeft == 5 && !lowCoinWarned) {
            lowCoinWarned = true
            popups.add("5 COINS LEFT", GAME_W / 2f, 300f, Color(Pal.ORANGE), size = 3f)
        }
    }

    private fun launchDrop(x: Float, kind: Int) {
        val d = drops.firstOrNull { !it.active } ?: return
        d.active = true
        d.x = x
        d.t = 0f
        d.kind = kind
    }

    override fun step(dt: Float) {
        cooldown -= dt
        sinceLastDrop += dt
        trayFlash = (trayFlash - dt * 2f).coerceAtLeast(0f)
        for (i in recentSpills.indices) recentSpills[i] -= dt

        // Pusher shelf: smooth back-and-forth.
        pusherPhase += dt / PusherTuning.PUSHER_PERIOD * TAU
        val mid = (PusherTuning.PUSHER_BACK + PusherTuning.PUSHER_FORWARD) / 2f
        val amp = (PusherTuning.PUSHER_FORWARD - PusherTuning.PUSHER_BACK) / 2f
        val newFront = mid - cos(pusherPhase) * amp
        pusherV = (newFront - pusherFront) / dt
        pusherFront = newFront

        for (d in drops) {
            if (!d.active) continue
            d.t += dt
            if (d.t >= 0.32f) {
                d.active = false
                val landY = pusherFront + radiusFor(d.kind) + rng.range(4f, 26f)
                val b = addBody(d.kind, d.x, landY)
                b.vy = 120f
                b.vx = rng.range(-30f, 30f)
                play(Sfx.CLINK, 0.5f, rng.range(0.8f, 1.2f))
                particles.burst(d.x, landY, 5, 20f, 80f, intArrayOf(Pal.YELLOW, Pal.WHITE), 0.3f, 3f)
            }
        }

        world.step(dt)

        // Coins past the front edge fall into the tray; past the sides into the gutters.
        val iter = world.bodies.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            val overFront = b.y > FRONT_EDGE
            val overSide = b.y > GUTTER_TOP && (b.x < PF_L || b.x > PF_R)
            if (!overFront && !overSide) continue
            iter.remove()
            if (overFront) {
                collect(b.kind, b.x)
            } else {
                lost++
                popups.add("LOST", b.x.coerceIn(40f, 320f), GUTTER_TOP + 20f, Color(Pal.GRAY), size = 2f)
                play(Sfx.GUTTER, 0.35f, 1.6f)
            }
            val f = fallers.firstOrNull { !it.active } ?: continue
            f.active = true
            f.x = b.x
            f.y = b.y
            f.vy = maxOf(b.vy, 60f)
            f.t = 0f
            f.kind = b.kind
            f.lost = !overFront
            f.angle = b.angle
        }

        for (f in fallers) {
            if (!f.active) continue
            f.t += dt
            f.vy += 900f * dt
            f.y += f.vy * dt
            if (f.t > 0.5f) f.active = false
        }

        if (!timeUp && coinsLeft == 0 && drops.none { it.active } && sinceLastDrop > PusherTuning.SETTLE_AFTER_LAST_COIN) {
            endedEarly = true
        }
    }

    private fun collect(kind: Int, x: Float) {
        won++
        trayFlash = 1f
        recentSpills[spillCount % recentSpills.size] = PusherTuning.AVALANCHE_WINDOW
        spillCount++
        val y = FRONT_EDGE + 20f
        when (kind) {
            GEM -> {
                addScore(PusherTuning.GEM_POINTS, x, y - 30f, Color(Pal.CYAN))
                play(Sfx.PRIZE, 0.8f)
                fx.haptics.win()
                particles.burst(x, y, 24, 60f, 240f, intArrayOf(Pal.CYAN, Pal.PINK, Pal.WHITE), 0.7f, 4f, kind = Particles.SPARKLE)
            }
            TICKETS -> {
                bonusTickets += PusherTuning.TICKET_BUNDLE
                popups.add("+${PusherTuning.TICKET_BUNDLE} TICKETS", x.coerceIn(80f, 280f), y - 40f, Color(Pal.ORANGE), size = 3f, life = 1.3f)
                play(Sfx.TICKET, 1f)
                play(Sfx.WIN, 0.6f, 1.2f)
                fx.haptics.win()
                particles.burst(x, y, 20, 60f, 220f, intArrayOf(Pal.ORANGE, Pal.YELLOW), 0.7f, 5f, kind = Particles.CONFETTI)
            }
            STAR -> {
                popups.add("COIN SHOWER!", GAME_W / 2f, 300f, Color(Pal.YELLOW), size = 4f, life = 1.3f)
                addScore(PusherTuning.COIN_POINTS, x, y - 30f, Color(Pal.YELLOW))
                play(Sfx.LUCKY)
                fx.haptics.jackpot()
                shake.add(0.4f)
                for (k in 0 until PusherTuning.SHOWER_COINS) {
                    launchDrop(PF_L + 30f + k * (PF_R - PF_L - 60f) / (PusherTuning.SHOWER_COINS - 1), COIN)
                }
                sinceLastDrop = 0f
            }
            BIG -> {
                addScore(PusherTuning.BIG_COIN_POINTS, x, y - 30f, Color(Pal.GOLD))
                play(Sfx.COIN, 1f, 0.7f)
                fx.haptics.hit()
                shake.add(0.2f)
            }
            else -> {
                addScore(PusherTuning.COIN_POINTS, x, y - 30f, Color(Pal.YELLOW))
                play(Sfx.CLINK, 0.8f, rng.range(0.9f, 1.3f))
                fx.haptics.tick()
            }
        }
        particles.burst(x, y, 6, 40f, 140f, intArrayOf(Pal.GOLD, Pal.YELLOW), 0.4f, 3f, grav = 400f)
        val recent = recentSpills.count { it > 0f }
        if (recent >= PusherTuning.AVALANCHE_COUNT) {
            recentSpills.fill(0f)
            addScore(PusherTuning.AVALANCHE_BONUS, GAME_W / 2f, 250f, Color(Pal.PINK), "AVALANCHE +${PusherTuning.AVALANCHE_BONUS}")
            play(Sfx.SPILL)
            play(Sfx.CHEER, 0.7f)
            fx.haptics.jackpot()
            shake.add(0.5f)
            particles.confetti(0f, 0f, GAME_W, 60)
        } else if (recent >= 3) {
            play(Sfx.SPILL, 0.6f)
            shake.add(0.15f)
        }
    }

    // ---------------------------------------------------------------- drawing

    override fun render(scope: DrawScope) {
        with(scope) {
            drawRect(Color(Pal.shade(Pal.ORANGE, 0.35f)), Offset(-40f, -40f), Size(GAME_W + 80f, GAME_H + 80f))
            // Cabinet side panels with chasing lights.
            for (i in 0 until 16) {
                val on = ((time * 8f).toInt() - i) % 4 == 0
                val c = Color(if (on) Pal.YELLOW else Pal.shade(Pal.GOLD, 0.35f))
                drawCircle(c, 5f, Offset(14f, 110f + i * 28f))
                drawCircle(c, 5f, Offset(GAME_W - 14f, 110f + i * 28f))
            }
            // Deck.
            drawRect(Color(Pal.NAVY), Offset(PF_L, DECK_TOP), Size(PF_R - PF_L, FRONT_EDGE - DECK_TOP))
            for (i in 0 until 12) {
                val y = DECK_TOP + i * 40f
                drawRect(Color(Pal.INDIGO), Offset(PF_L, y), Size(PF_R - PF_L, 2f), alpha = 0.6f)
            }
            // Side gutters.
            drawRect(Color(Pal.BLACK), Offset(PF_L - 18f, GUTTER_TOP), Size(18f, FRONT_EDGE - GUTTER_TOP + 20f))
            drawRect(Color(Pal.BLACK), Offset(PF_R, GUTTER_TOP), Size(18f, FRONT_EDGE - GUTTER_TOP + 20f))
            // Back wall with drop guide.
            drawRect(Color(Pal.PLUM), Offset(PF_L, 30f), Size(PF_R - PF_L, DECK_TOP - 30f))
            val cl = coinsLeft.coerceAtLeast(0)
            PixelFont.drawCentered(this, "COINS $cl", GAME_W / 2f, 40f, 3f, Color(if (cl <= 5) Pal.ORANGE else Pal.YELLOW))
            if (cl > 0 && !timeUp) {
                val a = 0.4f + 0.4f * sin(time * 6f)
                PixelFont.drawCentered(this, "TAP TO DROP ${PixelFont.DOWN}", GAME_W / 2f, 66f, 2f, Color.White, a)
            }

            // Coins resting on the deck.
            for (b in world.bodies) drawItem(this, b.kind, b.x, b.y, b.angle, 1f, 1f)

            // Pusher shelf.
            val top = DECK_TOP - 6f
            drawRect(Color(Pal.GRAY), Offset(PF_L, top), Size(PF_R - PF_L, pusherFront - top))
            drawRect(Color(Pal.LIGHTGRAY), Offset(PF_L, top), Size(PF_R - PF_L, 6f))
            val stripes = 12
            for (i in 0 until stripes) {
                val x = PF_L + i * (PF_R - PF_L) / stripes
                drawRect(Color(if (i % 2 == 0) Pal.YELLOW else Pal.BLACK), Offset(x, pusherFront - 10f), Size((PF_R - PF_L) / stripes, 10f))
            }
            for (i in 0 until 6) {
                val on = ((time * 4f).toInt() + i) % 2 == 0
                drawCircle(Color(if (on) Pal.CYAN else Pal.TEAL), 5f, Offset(PF_L + 30f + i * 48f, pusherFront - 26f))
            }

            // Front edge lip and win tray.
            drawRect(Color(Pal.GOLD), Offset(PF_L, FRONT_EDGE), Size(PF_R - PF_L, 6f))
            drawRoundRect(Color(Pal.BLACK), Offset(PF_L - 10f, FRONT_EDGE + 16f), Size(PF_R - PF_L + 20f, GAME_H - FRONT_EDGE - 20f), CornerRadius(10f, 10f))
            if (trayFlash > 0f) {
                drawRoundRect(Color(Pal.YELLOW), Offset(PF_L - 10f, FRONT_EDGE + 16f), Size(PF_R - PF_L + 20f, GAME_H - FRONT_EDGE - 20f), CornerRadius(10f, 10f), alpha = trayFlash * 0.35f)
            }
            PixelFont.drawCentered(this, "WON $won", GAME_W / 2f, FRONT_EDGE + 36f, 3f, Color(Pal.YELLOW))

            for (f in fallers) {
                if (!f.active) continue
                val s = (1f - f.t * 1.2f).coerceAtLeast(0.2f)
                drawItem(this, f.kind, f.x, f.y, f.angle, s, if (f.lost) 0.5f else 1f)
            }
            for (d in drops) {
                if (!d.active) continue
                val t = clamp01(d.t / 0.32f)
                val z = 1f - t
                val y = lerpY(t)
                drawOval(Color.Black, Offset(d.x - 12f, y - 4f), Size(24f, 10f), alpha = 0.3f * t)
                drawItem(this, d.kind, d.x, y - z * 60f, time * 12f, 1f + z * 0.8f, 1f)
            }
        }
    }

    private fun lerpY(t: Float): Float = 60f + (pusherFront + 20f - 60f) * t

    private fun drawItem(scope: DrawScope, kind: Int, x: Float, y: Float, angle: Float, scale: Float, alpha: Float) {
        val img = sprites[kind.coerceIn(0, sprites.size - 1)]
        val r = radiusFor(kind) * scale
        val px = (r * 2f + 2f) / img.width
        if (kind == COIN || kind == BIG) {
            // Coins show a subtle rim flicker as they spin.
            val squish = 0.85f + 0.15f * abs(cos(angle))
            scope.drawPixelImage(img, x - r - 1f, y - (r + 1f) * squish, px, alpha)
        } else {
            scope.drawPixelImage(img, x - r - 1f, y - r - 1f, px, alpha)
        }
    }

    private fun buildSprites(): Array<ImageBitmap> {
        fun coin(size: Int, face: Int, rim: Int, mark: Boolean): ImageBitmap {
            val c = PixelCanvas(size, size)
            val mid = size / 2f
            c.disc(mid, mid, mid - 0.5f, rim)
            c.disc(mid, mid, mid - 2f, face)
            c.set((mid - 3).toInt(), (mid - 3).toInt(), Pal.WHITE)
            c.set((mid - 2).toInt(), (mid - 3).toInt(), Pal.WHITE)
            c.set((mid - 3).toInt(), (mid - 2).toInt(), Pal.WHITE)
            if (mark) {
                val m = mid.toInt()
                c.vline(m, m - 3, m + 2, rim)
                c.hline(m - 2, m + 2, m - 1, rim)
            } else {
                c.ring(mid, mid, mid - 3f, 1f, Pal.shade(face, 0.85f))
            }
            return c.toImageBitmap()
        }

        val gem = PixelCanvas(12, 12).apply {
            for (y in 0 until 12) {
                val half = if (y < 4) 2 + y else 11 - y
                hline(6 - half, 5 + half, y, if (y < 4) Pal.CYAN else Pal.SKY)
            }
            hline(2, 9, 4, Pal.WHITE)
            set(4, 2, Pal.WHITE)
            outline(Pal.NAVY)
        }
        val tickets = PixelCanvas(16, 16).apply {
            fill(1, 3, 14, 10, Pal.ORANGE)
            fill(2, 1, 14, 10, Pal.GOLD)
            rect(2, 1, 14, 10, Pal.ORANGE)
            text("T", 6, 3, Pal.DARKRED)
            outline(Pal.DARKBROWN)
        }
        val star = PixelCanvas(14, 14).apply {
            val rows = arrayOf(
                "......##......",
                "......##......",
                ".....####.....",
                ".....####.....",
                "##############",
                ".############.",
                "..##########..",
                "...########...",
                "...########...",
                "..####..####..",
                "..###....###..",
                ".###......###.",
                ".##........##.",
                "..............",
            )
            sprite(rows, 0, 0, mapOf('#' to Pal.YELLOW))
            set(6, 5, Pal.WHITE); set(7, 5, Pal.WHITE)
            outline(Pal.ORANGE)
        }
        return arrayOf(
            coin(14, Pal.GOLD, Pal.ORANGE, mark = false),
            gem.toImageBitmap(),
            tickets.toImageBitmap(),
            star.toImageBitmap(),
            coin(20, Pal.YELLOW, Pal.ORANGE, mark = true),
        )
    }

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.NAVY))
        val shelf = 3f + (0.5f - 0.5f * cos(time * 2f)) * 3f
        p.fill(0f, 0f, w.toFloat(), shelf, Color(Pal.GRAY))
        p.fill(0f, shelf - 1f, w.toFloat(), 1f, Color(Pal.YELLOW))
        for (i in 0 until 18) {
            val cx = 2f + (i * 7 % (w - 3)).toFloat()
            val cy = shelf + 3f + (i * 5 % (h - 7)).toFloat()
            p.fill(cx, cy, 2f, 2f, Color(Pal.GOLD))
        }
        val fall = (time * 1.4f) % 1f
        p.fill(w / 2f, h - 3f + fall * 3f, 2f, 2f, Color(Pal.YELLOW))
        p.fill(0f, h - 1f, w.toFloat(), 1f, Color(Pal.GOLD))
    }
}
