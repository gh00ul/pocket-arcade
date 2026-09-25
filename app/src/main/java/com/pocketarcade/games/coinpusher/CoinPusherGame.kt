package com.pocketarcade.games.coinpusher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Body
import com.pocketarcade.engine.CircleWorld
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Segment
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Stage3D
import com.pocketarcade.engine.r3d.TexKit
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

        // 3D cabinet layout (world units = deck units).
        const val BACK_Z = DECK_TOP - 40f
        const val BACK_H = 240f
        const val GUTTER_W = 18f
        const val GUTTER_DEPTH = 40f
        const val TRAY_FRONT = 620f
        const val TRAY_Y = -40f
        const val SIDE_H = 40f
        const val SHELF_H = 30f
        const val GLASS_H = 50f
        const val COIN_THICK = 3f
        const val DROP_Z = BACK_Z + 6f
        const val SLOT_Y = 70f

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
        if (y > frontScreenY + 10f) return
        if (coinsLeft <= 0) {
            play(Sfx.ERROR, 0.4f)
            popups.add("NO COINS", GAME_W / 2f, 300f, Color(Pal.GRAY), size = 3f)
            return
        }
        if (cooldown > 0f) return
        cooldown = PusherTuning.DROP_COOLDOWN
        coinsLeft--
        sinceLastDrop = 0f
        // Drop above the spot on the deck under the finger.
        val deckX = if (stage.touchToPlane(x, y, 0f, pt)) pt[0] else x
        val dx = deckX.coerceIn(PF_L + COIN_R + 4f, PF_R - COIN_R - 4f)
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
                stage.toField(d.x, 0f, landY, pt)
                particles.burst(pt[0], pt[1], 5, 20f, 80f, intArrayOf(Pal.YELLOW, Pal.WHITE), 0.3f, 3f)
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
                stage.toField(b.x, 0f, GUTTER_TOP + 20f, pt)
                popups.add("LOST", pt[0].coerceIn(40f, 320f), pt[1], Color(Pal.GRAY), size = 2f)
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

    private fun collect(kind: Int, deckX: Float) {
        won++
        trayFlash = 1f
        recentSpills[spillCount % recentSpills.size] = PusherTuning.AVALANCHE_WINDOW
        spillCount++
        // Effects happen where the lip is on screen.
        stage.toField(deckX, 0f, FRONT_EDGE, pt)
        val x = pt[0]
        val y = pt[1] + 20f
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

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The machine in 3D, seen from where you'd stand. The deck simulation is top-down, so its
     * x and y become the world's x and depth; items lie on the deck at height 0.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt(), "pusher").apply {
        look(180f, 520f, 900f, 180f, 0f, 330f, fovDeg = 50f)
    }
    private val pt = FloatArray(3)
    private val frontScreenY: Float = run {
        stage.toField(GAME_W / 2f, 0f, FRONT_EDGE, pt)
        pt[1]
    }
    private val deckTex by lazy { PusherArt.deck((PF_R - PF_L).toInt(), (FRONT_EDGE - DECK_TOP).toInt()) }
    private val coinsPanel = PusherArt.Panel(96, 22)
    private val wonPanel = PusherArt.Panel(96, 22)

    private val cabinet: Model by lazy {
        val b = ModelBuilder()
        val cab = PusherArt.cabinet.full
        val dark = PusherArt.dark.full
        val gold = PusherArt.gold.full
        b.quad(PF_L, 0f, DECK_TOP, PF_R, 0f, DECK_TOP, PF_R, 0f, FRONT_EDGE, PF_L, 0f, FRONT_EDGE, deckTex.full, 0f, 1f, 0f)
        // Under the shelf, behind the deck.
        b.quad(PF_L, 0f, BACK_Z, PF_R, 0f, BACK_Z, PF_R, 0f, DECK_TOP, PF_L, 0f, DECK_TOP, dark, 0f, 1f, 0f)
        b.quad(0f, BACK_H, BACK_Z, GAME_W, BACK_H, BACK_Z, GAME_W, 0f, BACK_Z, 0f, 0f, BACK_Z, PusherArt.backWall.full, 0f, 0f, 1f)
        // Side gutters beside the front of the deck, and their floors.
        for (s in 0..1) {
            val x0 = if (s == 0) PF_L - GUTTER_W else PF_R
            val x1 = x0 + GUTTER_W
            b.quad(x0, -GUTTER_DEPTH, GUTTER_TOP, x1, -GUTTER_DEPTH, GUTTER_TOP, x1, -GUTTER_DEPTH, TRAY_FRONT, x0, -GUTTER_DEPTH, TRAY_FRONT, dark, 0f, 1f, 0f)
            b.quad(x0, 0f, GUTTER_TOP, x1, 0f, GUTTER_TOP, x1, -GUTTER_DEPTH, GUTTER_TOP, x0, -GUTTER_DEPTH, GUTTER_TOP, dark, 0f, 0f, 1f)
        }
        // Cabinet walls outside the gutters, full length.
        b.box(0f, -140f, BACK_Z, PF_L - GUTTER_W, SIDE_H, TRAY_FRONT, BoxFaces(top = cab, right = cab, front = cab))
        b.box(PF_R + GUTTER_W, -140f, BACK_Z, GAME_W, SIDE_H, TRAY_FRONT, BoxFaces(top = cab, left = cab, front = cab))
        // Solid side ledges along the back of the deck (where the sim's side walls are).
        b.box(PF_L - GUTTER_W, 0f, BACK_Z, PF_L, 10f, GUTTER_TOP, BoxFaces(top = gold, right = gold, front = gold))
        b.box(PF_R, 0f, BACK_Z, PF_R + GUTTER_W, 10f, GUTTER_TOP, BoxFaces(top = gold, left = gold, front = gold))
        // Front lip, the drop to the tray, and the tray.
        b.box(PF_L - GUTTER_W, -4f, FRONT_EDGE, PF_R + GUTTER_W, 3f, FRONT_EDGE + 6f, BoxFaces(top = gold, front = gold))
        b.quad(PF_L - GUTTER_W, -4f, FRONT_EDGE + 6f, PF_R + GUTTER_W, -4f, FRONT_EDGE + 6f, PF_R + GUTTER_W, TRAY_Y, FRONT_EDGE + 6f, PF_L - GUTTER_W, TRAY_Y, FRONT_EDGE + 6f, PusherArt.lipFace.full, 0f, 0f, 1f)
        b.quad(PF_L - GUTTER_W, TRAY_Y, FRONT_EDGE + 6f, PF_R + GUTTER_W, TRAY_Y, FRONT_EDGE + 6f, PF_R + GUTTER_W, TRAY_Y, TRAY_FRONT, PF_L - GUTTER_W, TRAY_Y, TRAY_FRONT, PusherArt.tray.full, 0f, 1f, 0f)
        b.box(PF_L - GUTTER_W, TRAY_Y - 60f, TRAY_FRONT, PF_R + GUTTER_W, TRAY_Y + 14f, TRAY_FRONT + 8f, BoxFaces(top = gold, front = cab))
        b.build()
    }

    private val warm = PointLight(180f, 380f, 320f, 1f, 0.9f, 0.75f, 720f, 1f)
    private val backGlow = PointLight(180f, 150f, 80f, 1f, 0.45f, 0.6f, 320f, 0.7f)
    private val trayLight = PointLight(180f, TRAY_Y + 40f, FRONT_EDGE + 40f, 1f, 0.85f, 0.3f, 300f, 0f)

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        val l = r.lighting
        l.ambR = 0.55f; l.ambG = 0.5f; l.ambB = 0.6f
        l.setDirection(0.2f, 1f, 0.6f)
        l.dirR = 0.35f; l.dirG = 0.32f; l.dirB = 0.28f
        l.points.clear()
        l.points += warm
        l.points += backGlow
        trayLight.intensity = trayFlash * 1.6f
        if (trayFlash > 0f) l.points += trayLight
        r.gradient(0xFF0C0610.toInt(), Pal.shade(Pal.ORANGE, 0.2f))

        cabinet.draw(r)
        drawShelf(r)
        drawTrayPile(r)
        drawPanels(r)
        for (b in world.bodies) drawItem(r, b.kind, b.x, b.y, 0f, b.angle, 1f)
        for (f in fallers) {
            if (!f.active) continue
            // Tipping over the lip and dropping into the tray (or a gutter).
            val d = (f.y - (if (f.lost) GUTTER_TOP else FRONT_EDGE)).coerceAtLeast(0f)
            val z = if (f.lost) f.y else FRONT_EDGE + minOf(d * 0.6f, 24f)
            val y = -minOf(d * 1.4f, if (f.lost) GUTTER_DEPTH - 4f else -TRAY_Y - 4f)
            val tip = minOf(d / 20f, 1.3f)
            drawTumbling(r, f.kind, f.x, z, y, tip, f.angle)
        }
        for (d in drops) {
            if (!d.active) continue
            val t = clamp01(d.t / 0.32f)
            val z = lerp(DROP_Z, pusherFront + 20f, t)
            val y = SLOT_Y * (1f - t * t) + 4f
            drawTumbling(r, d.kind, d.x, z, y, time * 9f, time * 12f)
        }
        drawBulbs(r)
        // Glass side panels over the back of the deck.
        val glass = PusherArt.glass.full
        for (x in floatArrayOf(PF_L - GUTTER_W, PF_R + GUTTER_W)) {
            r.quad(x, 10f + GLASS_H, BACK_Z, x, 10f + GLASS_H, GUTTER_TOP, x, 10f, GUTTER_TOP, x, 10f, BACK_Z, glass, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false)
        }
        stage.present(scope)

        val cl = coinsLeft.coerceAtLeast(0)
        if (cl > 0 && !timeUp) {
            val a = 0.4f + 0.4f * sin(time * 6f)
            PixelFont.drawCentered(scope, "TAP TO DROP ${PixelFont.DOWN}", GAME_W / 2f, 150f, 2f, Color.White, a)
        }
    }

    /** Winnings pile up in the tray. */
    private fun drawTrayPile(r: Renderer3D) {
        val n = won.coerceAtMost(60)
        for (i in 0 until n) {
            val x = PF_L + 10f + hash01(i, 41) * (PF_R - PF_L - 20f)
            val z = FRONT_EDGE + 16f + hash01(i, 42) * (TRAY_FRONT - FRONT_EDGE - 26f)
            val layer = i / 20
            r.flat(x, z, TRAY_Y + 1f + layer * 3f, COIN_R * 2f, COIN_R * 2f, PusherArt.coin.full, hash01(i, 43) * TAU)
        }
    }

    private fun drawShelf(r: Renderer3D) {
        val front = pusherFront
        r.quad(PF_L, SHELF_H, front, PF_R, SHELF_H, front, PF_R, 0f, front, PF_L, 0f, front, PusherArt.shelfFront.full, 0f, 0f, 1f)
        r.quad(PF_L, SHELF_H, BACK_Z, PF_R, SHELF_H, BACK_Z, PF_R, SHELF_H, front, PF_L, SHELF_H, front, PusherArt.shelfTop.full, 0f, 1f, 0f)
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        for (i in 0 until 6) {
            val on = ((time * 4f).toInt() + i) % 2 == 0
            val x = PF_L + 25f + i * (PF_R - PF_L - 50f) / 5f
            r.sprite(x, SHELF_H + 3f, front - 8f, 7f, 7f, white, emissive = 1.2f, tint = if (on) Pal.CYAN else Pal.TEAL)
            if (on) r.sprite(x, SHELF_H + 3f, front - 7f, 24f, 24f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.CYAN)
        }
    }

    private fun drawPanels(r: Renderer3D) {
        val cl = coinsLeft.coerceAtLeast(0)
        coinsPanel.paint("COINS $cl", if (cl <= 5) Pal.ORANGE else Pal.YELLOW)
        r.quad(120f, 118f, BACK_Z + 1f, 240f, 118f, BACK_Z + 1f, 240f, 90f, BACK_Z + 1f, 120f, 90f, BACK_Z + 1f, coinsPanel.tex.full, 0f, 0f, 1f, emissive = 1f)
        // The coin slot the drops come out of.
        r.quad(150f, SLOT_Y + 8f, BACK_Z + 1f, 210f, SLOT_Y + 8f, BACK_Z + 1f, 210f, SLOT_Y - 2f, BACK_Z + 1f, 150f, SLOT_Y - 2f, BACK_Z + 1f, PusherArt.dark.full, 0f, 0f, 1f)
        wonPanel.paint("WON $won", Pal.YELLOW)
        r.quad(130f, TRAY_Y - 8f, TRAY_FRONT + 8.5f, 230f, TRAY_Y - 8f, TRAY_FRONT + 8.5f, 230f, TRAY_Y - 32f, TRAY_FRONT + 8.5f, 130f, TRAY_Y - 32f, TRAY_FRONT + 8.5f, wonPanel.tex.full, 0f, 0f, 1f, emissive = 1f)
    }

    private fun drawBulbs(r: Renderer3D) {
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        for (s in 0..1) {
            val x = if (s == 0) (PF_L - GUTTER_W) / 2f else (PF_R + GUTTER_W + GAME_W) / 2f
            for (i in 0 until 12) {
                val z = BACK_Z + 20f + i * (TRAY_FRONT - BACK_Z - 40f) / 11f
                val on = ((time * 8f).toInt() - i) % 4 == 0
                r.sprite(x, SIDE_H + 4f, z, 7f, 7f, white, emissive = 1.2f, tint = if (on) Pal.YELLOW else Pal.shade(Pal.GOLD, 0.4f))
                if (on) r.sprite(x, SIDE_H + 5f, z, 26f, 26f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = Pal.GOLD)
            }
        }
    }

    /** An item lying flat on the deck at ([x], [z]). */
    private fun drawItem(r: Renderer3D, kind: Int, x: Float, z: Float, y: Float, angle: Float, scale: Float) {
        val rad = radiusFor(kind) * scale
        when (kind) {
            COIN, BIG -> {
                // A darker disc under the face reads as the coin's edge.
                r.flat(x, z + 1.2f, y + 0.4f, rad * 2f, rad * 2f, PusherArt.coinEdge.full, angle)
                r.flat(x, z, y + COIN_THICK, rad * 2f, rad * 2f, (if (kind == BIG) PusherArt.bigCoin else PusherArt.coin).full, angle)
            }
            TICKETS -> r.flat(x, z, y + 3f, rad * 2.2f, rad * 1.6f, PusherArt.tickets.full, angle)
            GEM -> r.billboard(x, y, z, rad * 2f, rad * 2f, PusherArt.gem.full, lean = 0.4f)
            else -> r.billboard(x, y, z, rad * 2.2f, rad * 2.2f, PusherArt.star.full, lean = 0.4f)
        }
    }

    /** An item in the air: coins show their face turning edge-on as they tumble. */
    private fun drawTumbling(r: Renderer3D, kind: Int, x: Float, z: Float, y: Float, tumble: Float, spin: Float) {
        val rad = radiusFor(kind)
        when (kind) {
            COIN, BIG -> {
                val h = rad * 2f * (0.25f + 0.75f * abs(cos(tumble)))
                r.sprite(x, y + rad, z, rad * 2f, h, (if (kind == BIG) PusherArt.bigCoin else PusherArt.coin).full, roll = spin * 0.1f)
            }
            TICKETS -> r.sprite(x, y + rad, z, rad * 2.2f, rad * 1.6f, PusherArt.tickets.full, roll = spin * 0.2f)
            GEM -> r.sprite(x, y + rad, z, rad * 2f, rad * 2f, PusherArt.gem.full)
            else -> r.sprite(x, y + rad, z, rad * 2.2f, rad * 2.2f, PusherArt.star.full, roll = spin * 0.3f)
        }
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
