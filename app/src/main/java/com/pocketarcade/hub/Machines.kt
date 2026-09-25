package com.pocketarcade.hub

import com.pocketarcade.data.Catalog
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.MiniGame
import com.pocketarcade.games.claw.Plush3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * One cabinet on the arcade floor: its model, the parts that move in attract mode (a claw
 * patrolling the prizes, moles popping, a puck gliding...), the chase lights on its marquee and
 * the coloured light it throws on the carpet.
 */
class MachineUnit(val prop: Prop, val game: MiniGame, val art: MachineArt) {
    val model: Model
    val lights = ArrayList<PointLight>()
    var screen: LiveScreen? = null
        private set

    private val x0 = prop.x0
    private val x1 = prop.x1
    private val z0 = prop.z0
    private val z1 = prop.z1
    private val h = prop.height
    private val cx = (x0 + x1) / 2f
    private val seed = prop.variant * 7 + prop.machine * 13
    /** Marquee chase lights: x, y, z per bulb. */
    private val bulbs = ArrayList<Float>()
    private val xf = Xform()
    private val xf2 = Xform()

    init {
        val b = ModelBuilder()
        when (prop.shape) {
            CabinetShape.UPRIGHT -> upright(b, tall = false)
            CabinetShape.TOWER -> upright(b, tall = true)
            CabinetShape.CLAW -> claw(b, withPrizes = true)
            CabinetShape.WIDE -> claw(b, withPrizes = false)
            CabinetShape.WHACK -> whack(b)
            CabinetShape.SKEEBALL -> lane(b, skee = true)
            CabinetShape.LANE -> lane(b, skee = false)
            CabinetShape.HOOPS -> hoops(b)
            CabinetShape.PUSHER -> pusher(b)
            CabinetShape.AIR_HOCKEY -> table(b, hockey = true)
            CabinetShape.TABLE -> table(b, hockey = false)
            CabinetShape.RACER -> racer(b)
        }
        model = b.build()
        // Every machine glows onto the carpet in front of it.
        lights += light(cx, 20f, z1 + 10f, art.glow, 70f, 0.85f)
    }

    private fun light(x: Float, y: Float, z: Float, color: Int, radius: Float, intensity: Float) =
        PointLight(x, y, z, (color shr 16 and 255) / 255f, (color shr 8 and 255) / 255f, (color and 255) / 255f, radius, intensity)

    private fun bulbRow(xa: Float, xb: Float, y: Float, z: Float, count: Int) {
        for (k in 0 until count) {
            bulbs += xa + (xb - xa) * (k + 0.5f) / count
            bulbs += y
            bulbs += z
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** Side panels with printed art and lit T-molding along their front edges. */
    private fun sidePanels(b: ModelBuilder, top: Float, depthFront: Float = z1, thick: Float = 1.8f) {
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val trim = art.trimTex.full
        b.box(x0, 0f, z0, x0 + thick, top, depthFront, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x1 - thick, 0f, z0, x1, top, depthFront, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
    }

    private fun marqueeBox(b: ModelBuilder, xa: Float, xb: Float, y0: Float, y1: Float, za: Float, zb: Float) {
        b.box(xa, y0, za, xb, y1, zb, BoxFaces(front = art.marquee.full, top = art.darkPaint.full, left = art.darkPaint.full, right = art.darkPaint.full, back = art.darkPaint.full, frontEmissive = 1.25f))
        bulbRow(xa + 1f, xb - 1f, y1 + 0.6f, zb - 0.6f, ((xb - xa) / 3.2f).toInt())
    }

    private fun display(b: ModelBuilder, xa: Float, xb: Float, ya: Float, yb: Float, z: Float) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, art.display.full, 0f, 0f, 1f, emissive = 1.2f)
    }

    private fun glassBox(b: ModelBuilder, xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, back: Boolean = false) {
        val g = HallArt.glass.full
        b.quad(xa, yb, zb, xb, yb, zb, xb, ya, zb, xa, ya, zb, g, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.quad(xa, yb, za, xa, yb, zb, xa, ya, zb, xa, ya, za, g, -1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.quad(xb, yb, zb, xb, yb, za, xb, ya, za, xb, ya, zb, g, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        if (back) b.quad(xb, yb, za, xa, yb, za, xa, ya, za, xb, ya, za, g, 0f, 0f, -1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
    }

    /** Four chrome corner posts. */
    private fun posts(b: ModelBuilder, xa: Float, xb: Float, za: Float, zb: Float, ya: Float, yb: Float, t: Float = 1.4f) {
        val m = HallArt.chrome.full
        val f = BoxFaces(front = m, back = m, left = m, right = m, top = m, gloss = 0.9f)
        b.box(xa, ya, za, xa + t, yb, za + t, f)
        b.box(xb - t, ya, za, xb, yb, za + t, f)
        b.box(xa, ya, zb - t, xa + t, yb, zb, f)
        b.box(xb - t, ya, zb - t, xb, yb, zb, f)
    }

    // ------------------------------------------------------------------ cabinets

    /** A classic upright: kick panel, control panel, screen, lit marquee. The tower is taller with one big button. */
    private fun upright(b: ModelBuilder, tall: Boolean) {
        val st = 1.8f
        val ix0 = x0 + st
        val ix1 = x1 - st
        val panelY = if (tall) 28f else 26f
        val cpBack = z1 - 9f
        val screenTop = h - (if (tall) 14f else 11f)
        sidePanels(b, h)
        val dark = art.darkPaint.full
        b.box(ix0, 0f, z0, ix1, panelY, z1 - 1f, BoxFaces(front = art.kick.full, top = dark, back = dark))
        b.quad(ix0, panelY + 4f, cpBack, ix1, panelY + 4f, cpBack, ix1, panelY, z1 - 1f, ix0, panelY, z1 - 1f, art.panel.full, 0f, 0.91f, 0.41f, gloss = 0.55f)
        b.box(ix0, panelY + 4f, z0, ix1, screenTop, cpBack, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        val live = LiveScreen(art, seed)
        screen = live
        b.quad(
            ix0 + 1.6f, screenTop - 1.6f, cpBack + 0.15f, ix1 - 1.6f, screenTop - 1.6f, cpBack + 0.15f,
            ix1 - 1.6f, panelY + 6.5f, cpBack + 0.15f, ix0 + 1.6f, panelY + 6.5f, cpBack + 0.15f,
            live.texture.full, 0f, 0f, 1f, emissive = 1.15f,
        )
        marqueeBox(b, ix0, ix1, screenTop, h - 1f, z0, cpBack + 3f)
        b.box(x0, h - 1f, z0, x1, h, cpBack + 4f, BoxFaces(top = dark, front = art.trimTex.full, frontEmissive = 0.9f))
        // Controls sit on the sloping panel.
        fun panelY(z: Float) = panelY + 4f * (z1 - 1f - z) / (z1 - 1f - cpBack)
        if (tall) {
            val bz = z1 - 5f
            b.add(MachineKit.button(art.glow, 3f), xf.set(cx, panelY(bz), bz))
            b.add(MachineKit.button(art.trim, 1.3f), xf.set(cx - 7f, panelY(bz + 1f), bz + 1f))
            b.add(MachineKit.button(art.trim, 1.3f), xf.set(cx + 7f, panelY(bz + 1f), bz + 1f))
        } else {
            val bz = z1 - 5f
            b.add(MachineKit.joystick, xf.set(cx - 6f, panelY(bz), bz))
            for (k in 0 until 3) b.add(MachineKit.button(if (k == 0) art.glow else art.trim, 1.2f), xf.set(cx + 2f + k * 3.4f, panelY(bz), bz))
        }
        lights += light(cx, panelY + 16f, z1 + 4f, art.glow, 50f, 0.7f)
    }

    /** A glass merchandiser: prize pile (or a screen), gantry and claw, lit marquee on top. */
    private fun claw(b: ModelBuilder, withPrizes: Boolean) {
        val baseTop = 28f
        val glassTop = h - 8f
        val side = art.sideArt.full
        val dark = art.darkPaint.full
        b.box(x0, 0f, z0, x1, baseTop, z1, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark, gloss = 0.3f))
        b.box(x0 - 0.3f, baseTop - 1f, z0 - 0.3f, x1 + 0.3f, baseTop, z1 + 0.3f, BoxFaces(front = art.trimTex.full, left = art.trimTex.full, right = art.trimTex.full, frontEmissive = 0.9f))
        posts(b, x0, x1, z0, z1, baseTop, glassTop)
        // Inside: velvet floor and printed back wall.
        b.quad(x0 + 1f, baseTop + 0.3f, z0 + 1f, x1 - 1f, baseTop + 0.3f, z0 + 1f, x1 - 1f, baseTop + 0.3f, z1 - 1f, x0 + 1f, baseTop + 0.3f, z1 - 1f, MachineKit.velvet.full, 0f, 1f, 0f)
        b.quad(x0 + 1f, glassTop, z0 + 1.2f, x1 - 1f, glassTop, z0 + 1.2f, x1 - 1f, baseTop, z0 + 1.2f, x0 + 1f, baseTop, z0 + 1.2f, art.sideArt.full, 0f, 0f, 1f, emissive = 0.55f)
        if (withPrizes) {
            val plushies = Catalog.plushies
            for (k in 0 until 9) {
                val p = plushies[(seed + k * 3) % plushies.size]
                val px = cx + MachineKit.jitter(seed + k, 1, (x1 - x0) / 2f - 5f)
                val pz = (z0 + z1) / 2f - 2f + MachineKit.jitter(seed + k, 2, (z1 - z0) / 2f - 6f)
                val py = baseTop + 0.3f + (k / 4) * 2.2f
                b.add(Plush3D.model(p), xf.set(px, py, pz, yaw = MachineKit.jitter(seed + k, 3, 0.9f), roll = MachineKit.jitter(seed + k, 4, 0.2f), scale = 0.42f))
            }
            // Prize chute in the front-left corner.
            b.box(x0 + 1.4f, baseTop, z1 - 9f, x0 + 9f, baseTop + 9f, z1 - 1.4f, BoxFaces(top = HallArt.solid(0xFF08060A.toInt()).full, front = art.trimTex.full, frontEmissive = 0.6f))
        } else {
            val live = LiveScreen(art, seed)
            screen = live
            b.quad(x0 + 3f, glassTop - 3f, z0 + 1.4f, x1 - 3f, glassTop - 3f, z0 + 1.4f, x1 - 3f, baseTop + 6f, z0 + 1.4f, x0 + 3f, baseTop + 6f, z0 + 1.4f, live.texture.full, 0f, 0f, 1f, emissive = 1.1f)
        }
        // Gantry rails.
        val chrome = HallArt.chrome.full
        val rail = BoxFaces(front = chrome, top = chrome, back = chrome, gloss = 0.9f)
        b.box(x0 + 1f, glassTop - 2.5f, z0 + 5f, x1 - 1f, glassTop - 1.5f, z0 + 6f, rail)
        b.box(x0 + 1f, glassTop - 2.5f, z1 - 6f, x1 - 1f, glassTop - 1.5f, z1 - 5f, rail)
        glassBox(b, x0 + 0.4f, baseTop, z0 + 0.4f, x1 - 0.4f, glassTop, z1 - 0.4f)
        marqueeBox(b, x0, x1, glassTop, h, z0, z1)
        lights += light(cx, glassTop - 6f, (z0 + z1) / 2f, lift(art.glow, 0.3f), 44f, 1.1f)
    }

    /** Whack-a-mole: a table with five holes, a painted backboard and two mallets. */
    private fun whack(b: ModelBuilder) {
        val tableY = 26f
        val boardZ = z0 + 4f
        val side = art.sideArt.full
        val dark = art.darkPaint.full
        b.box(x0, 0f, boardZ, x1, tableY, z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, gloss = 0.3f))
        b.quad(x0, tableY, boardZ, x1, tableY, boardZ, x1, tableY, z1, x0, tableY, z1, MachineKit.whackTop(art.body).full, 0f, 1f, 0f, gloss = 0.35f)
        // Padded rim round the table.
        val pad = art.trimTex.full
        b.box(x0 - 0.5f, tableY, z1 - 1.5f, x1 + 0.5f, tableY + 1.2f, z1 + 0.5f, BoxFaces(front = pad, top = pad, left = pad, right = pad, gloss = 0.4f))
        // Backboard with the marquee on top and the score display.
        b.box(x0, tableY, z0, x1, h - 10f, boardZ, BoxFaces(front = art.sideArt.full, top = dark, left = dark, right = dark, back = dark))
        display(b, cx - 9f, cx + 9f, tableY + 16f, tableY + 21f, boardZ + 0.1f)
        marqueeBox(b, x0 - 1f, x1 + 1f, h - 10f, h, z0, boardZ + 1f)
        // Mallets resting on the front corners.
        b.add(MachineKit.mallet, xf.set(x0 + 5f, tableY + 2f, z1 - 4f, yaw = 2.4f))
        b.add(MachineKit.mallet, xf.set(x1 - 5f, tableY + 2f, z1 - 4f, yaw = -2.4f))
        lights += light(cx, tableY + 20f, (boardZ + z1) / 2f, lift(art.glow, 0.3f), 50f, 0.8f)
    }

    /** Skee-ball alley (or a generic lane with a screen at the end): rails, lane, target, net and backboard. */
    private fun lane(b: ModelBuilder, skee: Boolean) {
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val railH = 22f
        val boardZ = z0 + 8f
        b.box(x0, 0f, boardZ, x0 + 2f, railH, z1, BoxFaces(left = side, right = inner, top = art.trimTex.full, front = art.trimTex.full, frontEmissive = 0.8f, gloss = 0.4f))
        b.box(x1 - 2f, 0f, boardZ, x1, railH, z1, BoxFaces(right = side, left = inner, top = art.trimTex.full, front = art.trimTex.full, frontEmissive = 0.8f, gloss = 0.4f))
        // Front console with the ball tray.
        b.box(x0 + 2f, 0f, z1 - 10f, x1 - 2f, 18f, z1, BoxFaces(front = art.kick.full, top = HallArt.solid(0xFF14121A.toInt()).full, gloss = 0.3f))
        // The lane rises towards the jump.
        val laneFront = z1 - 10f
        val laneBack = boardZ + 30f
        val wood = MachineKit.laneWood.region(wrap = true)
        b.quad(x0 + 2f, 26f, laneBack, x1 - 2f, 26f, laneBack, x1 - 2f, 18f, laneFront, x0 + 2f, 18f, laneFront, wood, 0f, 0.99f, 0.12f, u1 = 128f, v1 = 512f, gloss = 0.5f)
        // The jump hump.
        b.quad(x0 + 2f, 30f, laneBack - 4f, x1 - 2f, 30f, laneBack - 4f, x1 - 2f, 26f, laneBack, x0 + 2f, 26f, laneBack, wood, 0f, 0.7f, 0.7f, u1 = 128f, v1 = 40f, gloss = 0.5f)
        if (skee) {
            // Tilted target board with the scoring rings.
            b.quad(x0 + 2f, 50f, boardZ, x1 - 2f, 50f, boardZ, x1 - 2f, 27f, laneBack - 6f, x0 + 2f, 27f, laneBack - 6f, MachineKit.skeeRings.full, 0f, 0.66f, 0.75f, gloss = 0.4f)
        } else {
            val live = LiveScreen(art, seed)
            screen = live
            b.quad(x0 + 3f, 50f, boardZ + 0.2f, x1 - 3f, 50f, boardZ + 0.2f, x1 - 3f, 30f, boardZ + 0.2f, x0 + 3f, 30f, boardZ + 0.2f, live.texture.full, 0f, 0f, 1f, emissive = 1.1f)
            b.quad(x0 + 2f, 28f, boardZ, x1 - 2f, 28f, boardZ, x1 - 2f, 28f, laneBack - 4f, x0 + 2f, 28f, laneBack - 4f, dark, 0f, 1f, 0f)
        }
        // Backboard, display and marquee.
        b.box(x0, 0f, z0, x1, h - 12f, boardZ, BoxFaces(front = dark, left = side, right = side, top = dark, back = dark))
        display(b, cx - 8f, cx + 8f, h - 20f, h - 14f, boardZ + 0.1f)
        marqueeBox(b, x0 - 1f, x1 + 1f, h - 12f, h, z0, boardZ + 1f)
        // Netting over the target end.
        val net = MachineKit.net.region(wrap = true)
        val netTop = h - 14f
        val netEnd = boardZ + 44f
        b.quad(x0, netTop, boardZ, x1, netTop, boardZ, x1, netTop, netEnd, x0, netTop, netEnd, net, 0f, -1f, 0f, u1 = 64f, v1 = 160f, blend = Blend.ALPHA, cull = false)
        b.quad(x0 + 0.3f, netTop, boardZ, x0 + 0.3f, netTop, netEnd, x0 + 0.3f, railH, netEnd, x0 + 0.3f, railH, boardZ, net, 1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        b.quad(x1 - 0.3f, netTop, netEnd, x1 - 0.3f, netTop, boardZ, x1 - 0.3f, railH, boardZ, x1 - 0.3f, railH, netEnd, net, -1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        // Balls waiting in the tray.
        for (k in 0 until 3) b.add(MachineKit.ball(0xFFB0213A.toInt()), xf.set(cx - 5f + k * 5f, 20f, z1 - 5f, scale = 2.1f))
        lights += light(cx, 44f, boardZ + 20f, 0xFFFFE8C8.toInt(), 60f, 0.9f)
    }

    /** Basketball alley: court ramp, net cage, backboard with rim and net. */
    private fun hoops(b: ModelBuilder) {
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val wallH = 30f
        b.box(x0, 0f, z0 + 4f, x0 + 2f, wallH, z1, BoxFaces(left = side, right = inner, top = art.trimTex.full, front = art.trimTex.full, frontEmissive = 0.8f, gloss = 0.4f))
        b.box(x1 - 2f, 0f, z0 + 4f, x1, wallH, z1, BoxFaces(right = side, left = inner, top = art.trimTex.full, front = art.trimTex.full, frontEmissive = 0.8f, gloss = 0.4f))
        b.box(x0 + 2f, 0f, z1 - 12f, x1 - 2f, 20f, z1, BoxFaces(front = art.kick.full, top = HallArt.solid(0xFF14121A.toInt()).full, gloss = 0.3f))
        b.quad(x0 + 2f, 28f, z0 + 4f, x1 - 2f, 28f, z0 + 4f, x1 - 2f, 20f, z1 - 12f, x0 + 2f, 20f, z1 - 12f, MachineKit.court.full, 0f, 0.99f, 0.1f, gloss = 0.45f)
        // Backboard frame, board, rim and net.
        b.box(x0, 0f, z0, x1, h - 10f, z0 + 4f, BoxFaces(front = dark, left = dark, right = dark, top = dark, back = dark))
        b.quad(cx - 13f, h - 13f, z0 + 4.2f, cx + 13f, h - 13f, z0 + 4.2f, cx + 13f, h - 31f, z0 + 4.2f, cx - 13f, h - 31f, z0 + 4.2f, MachineKit.hoopBoard.full, 0f, 0f, 1f, gloss = 0.9f, emissive = 0.4f)
        val rimY = h - 29f
        val rimZ = z0 + 10f
        val orange = HallArt.paint(0xFFFF7A1A.toInt(), 0.3f, 0.8f).full
        b.torus(cx, rimY, rimZ, 4.6f, 0.45f, orange, segments = 18, sides = 6, gloss = 0.7f)
        b.box(cx - 0.6f, rimY - 0.5f, z0 + 4f, cx + 0.6f, rimY + 0.3f, rimZ - 4.4f, BoxFaces(top = orange, left = orange, right = orange, front = orange))
        val netRegion = MachineKit.net.region(wrap = true)
        b.cylinder(cx, rimZ, rimY - 7f, rimY, 3f, 12, netRegion, topRadius = 4.5f)
        b.cylinder(cx, rimZ, rimY - 7f, rimY, 3f, 12, netRegion, topRadius = 4.5f, inward = true)
        display(b, cx - 8f, cx + 8f, h - 38f, h - 33f, z0 + 4.1f)
        marqueeBox(b, x0 - 1f, x1 + 1f, h - 10f, h, z0, z0 + 6f)
        // Net cage over the alley.
        val net = MachineKit.net.region(wrap = true)
        val cageEnd = z1 - 14f
        val top = h - 12f
        b.quad(x0, top, z0 + 4f, x1, top, z0 + 4f, x1, top, cageEnd, x0, top, cageEnd, net, 0f, -1f, 0f, u1 = 80f, v1 = 160f, blend = Blend.ALPHA, cull = false)
        b.quad(x0 + 0.3f, top, z0 + 4f, x0 + 0.3f, top, cageEnd, x0 + 0.3f, wallH, cageEnd, x0 + 0.3f, wallH, z0 + 4f, net, 1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        b.quad(x1 - 0.3f, top, cageEnd, x1 - 0.3f, top, z0 + 4f, x1 - 0.3f, wallH, z0 + 4f, x1 - 0.3f, wallH, cageEnd, net, -1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        for (k in 0 until 3) b.add(MachineKit.basketball, xf.set(cx - 7f + k * 7f, 23.2f, z1 - 6f, yaw = k * 1.3f, scale = 3.2f))
        lights += light(cx, h - 16f, z0 + 20f, 0xFFFFE8C8.toInt(), 70f, 0.9f)
    }

    /** Coin pusher: glass case over a coin-covered deck with a sliding shelf. */
    private fun pusher(b: ModelBuilder) {
        val baseTop = 30f
        val glassTop = h - 8f
        val side = art.sideArt.full
        val dark = art.darkPaint.full
        b.box(x0, 0f, z0, x1, baseTop, z1, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark, gloss = 0.3f))
        posts(b, x0, x1, z0, z1, baseTop, glassTop)
        b.quad(x0 + 1f, glassTop, z0 + 1.2f, x1 - 1f, glassTop, z0 + 1.2f, x1 - 1f, baseTop, z0 + 1.2f, x0 + 1f, baseTop, z0 + 1.2f, art.sideArt.full, 0f, 0f, 1f, emissive = 0.5f)
        val deckY = baseTop + 3f
        b.box(x0 + 1f, baseTop, z0 + 1f, x1 - 1f, deckY, z1 - 1f, BoxFaces(top = MachineKit.deck.full, front = MachineKit.gold.full, gloss = 0.6f))
        for (k in 0 until 26) {
            val px = cx + MachineKit.jitter(seed + k, 5, (x1 - x0) / 2f - 4f)
            val pz = z0 + 12f + hash01(seed + k, 6) * (z1 - z0 - 16f)
            val layer = if (k % 4 == 0) 1 else 0
            b.add(MachineKit.coin, xf.set(px, deckY + 0.4f + layer * 0.8f, pz, yaw = hash01(k, 7) * 6f))
        }
        glassBox(b, x0 + 0.4f, baseTop, z0 + 0.4f, x1 - 0.4f, glassTop, z1 - 0.4f)
        marqueeBox(b, x0, x1, glassTop, h, z0, z1)
        lights += light(cx, glassTop - 5f, (z0 + z1) / 2f, 0xFFFFD27A.toInt(), 44f, 1.1f)
    }

    /** Air hockey (or a generic table with a screen): table on legs, glowing surface, overhead scoreboard. */
    private fun table(b: ModelBuilder, hockey: Boolean) {
        val top = 22f
        val side = art.sideArt.full
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        val leg = BoxFaces(front = metal, left = metal, right = metal, back = metal)
        for ((lx, lz) in listOf(x0 + 1f to z0 + 7f, x1 - 3f to z0 + 7f, x0 + 1f to z1 - 3f, x1 - 3f to z1 - 3f)) {
            b.box(lx, 0f, lz, lx + 2f, 9f, lz + 2f, leg)
        }
        b.box(x0 + 0.5f, 9f, z0 + 6f, x1 - 0.5f, top, z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, gloss = 0.3f))
        val surface = if (hockey) MachineKit.hockeySurface.full else dark
        b.quad(x0 + 2f, top + 0.3f, z0 + 7.5f, x1 - 2f, top + 0.3f, z0 + 7.5f, x1 - 2f, top + 0.3f, z1 - 1.5f, x0 + 2f, top + 0.3f, z1 - 1.5f, surface, 0f, 1f, 0f, emissive = 0.55f, gloss = 0.8f)
        val rail = art.trimTex.full
        val rf = BoxFaces(front = rail, top = rail, left = rail, right = rail, back = rail, gloss = 0.6f)
        b.box(x0, top, z0 + 6f, x0 + 2f, top + 3f, z1, rf)
        b.box(x1 - 2f, top, z0 + 6f, x1, top + 3f, z1, rf)
        b.box(x0, top, z1 - 1.5f, cx - 6f, top + 3f, z1, rf)
        b.box(cx + 6f, top, z1 - 1.5f, x1, top + 3f, z1, rf)
        b.box(x0, top, z0 + 6f, cx - 6f, top + 3f, z0 + 7.5f, rf)
        b.box(cx + 6f, top, z0 + 6f, x1, top + 3f, z0 + 7.5f, rf)
        // Overhead scoreboard on a post.
        b.box(cx - 1.5f, top, z0 + 1f, cx + 1.5f, h - 12f, z0 + 4f, leg)
        if (hockey) {
            b.box(x0 - 3f, h - 14f, z0, x1 + 3f, h, z0 + 4f, BoxFaces(front = art.marquee.full, top = dark, left = dark, right = dark, back = dark, frontEmissive = 1.25f))
            display(b, cx - 8f, cx + 8f, h - 20f, h - 15f, z0 + 4.2f)
            b.quad(cx - 8f, h - 15f, z0 + 4.1f, cx + 8f, h - 15f, z0 + 4.1f, cx + 8f, h - 20f, z0 + 4.1f, cx - 8f, h - 20f, z0 + 4.1f, dark, 0f, 0f, 1f)
        } else {
            val live = LiveScreen(art, seed)
            screen = live
            b.box(x0 - 3f, h - 22f, z0, x1 + 3f, h, z0 + 4f, BoxFaces(front = art.bezel.full, top = dark, left = dark, right = dark, back = dark))
            b.quad(x0 - 1f, h - 2f, z0 + 4.15f, x1 + 1f, h - 2f, z0 + 4.15f, x1 + 1f, h - 20f, z0 + 4.15f, x0 - 1f, h - 20f, z0 + 4.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.1f)
        }
        bulbRow(x0 - 2f, x1 + 2f, h + 0.6f, z0 + 3.4f, 10)
        lights += light(cx, top + 26f, (z0 + z1) / 2f, lift(art.glow, 0.4f), 60f, 0.9f)
    }

    /** Sit-down racer: a big screen cabinet, a dash with a steering wheel, and a bucket seat. */
    private fun racer(b: ModelBuilder) {
        val cabZ = z0 + 24f
        val st = 1.8f
        val ix0 = x0 + st
        val ix1 = x1 - st
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val trim = art.trimTex.full
        b.box(x0, 0f, z0, x0 + st, h, cabZ + 6f, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x1 - st, 0f, z0, x1, h, cabZ + 6f, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(ix0, 0f, z0, ix1, 22f, cabZ, BoxFaces(front = dark, top = dark, back = dark))
        b.box(ix0, 22f, z0, ix1, h - 10f, cabZ - 2f, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        val live = LiveScreen(art, seed)
        screen = live
        b.quad(ix0 + 1.4f, h - 12f, cabZ - 1.85f, ix1 - 1.4f, h - 12f, cabZ - 1.85f, ix1 - 1.4f, 26f, cabZ - 1.85f, ix0 + 1.4f, 26f, cabZ - 1.85f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        marqueeBox(b, ix0, ix1, h - 10f, h, z0, cabZ)
        // Dash, steering wheel and pedals.
        b.box(ix0, 16f, cabZ, ix1, 26f, cabZ + 6f, BoxFaces(front = dark, top = HallArt.darkMetal.full, gloss = 0.5f))
        val wheel = ModelBuilder()
            .torus(0f, 0f, 0f, 4.8f, 0.8f, MachineKit.rubber.full, segments = 18, sides = 6, gloss = 0.3f)
            .capsule(0f, 0f, 0f, 0f, 0f, -3f, 0.5f, HallArt.darkMetal.full)
            .build()
        b.add(wheel, xf.set(cx, 29f, cabZ + 7f, pitch = -1.05f))
        b.box(cx - 5f, 0.5f, cabZ + 3f, cx - 2f, 2f, cabZ + 7f, BoxFaces(top = HallArt.darkMetal.full, front = HallArt.darkMetal.full))
        b.box(cx + 2f, 0.5f, cabZ + 3f, cx + 5f, 2f, cabZ + 7f, BoxFaces(top = HallArt.darkMetal.full, front = HallArt.darkMetal.full))
        // Bucket seat facing the screen.
        val leather = MachineKit.seatLeather.full
        val seatBody = BoxFaces(front = leather, left = leather, right = leather, top = leather, back = leather, gloss = 0.35f)
        b.box(cx - 8f, 0f, z1 - 18f, cx + 8f, 9f, z1 - 3f, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark))
        b.box(cx - 7f, 9f, z1 - 17f, cx + 7f, 12f, z1 - 4f, seatBody)
        val back = ModelBuilder().box(-7f, 0f, -1.8f, 7f, 20f, 1.8f, seatBody).build()
        b.add(back, xf.set(cx, 11f, z1 - 3.5f, pitch = 0.2f))
        b.add(ModelBuilder().box(-4f, 0f, -1.5f, 4f, 6f, 1.5f, seatBody).build(), xf.set(cx, 31f, z1 + 0.5f, pitch = 0.2f))
        lights += light(cx, 34f, cabZ + 8f, art.glow, 50f, 1f)
    }

    // ------------------------------------------------------------------ per frame

    /** Repaints the live screen and display (only while the machine is on screen). */
    fun refresh(best: Int, t: Float) {
        screen?.paint(best, t)
        art.updateDisplay(best, t)
    }

    /** Draws the solid parts and whatever moves in attract mode. */
    fun drawOpaque(r: Renderer3D, t: Float) {
        model.draw(r, Blend.OPAQUE)
        val phase = t + seed * 0.37f
        when (prop.shape) {
            CabinetShape.CLAW, CabinetShape.WIDE -> {
                // The claw patrols over the prizes, dips now and then.
                val glassTop = h - 8f
                val sweep = sin(phase * 0.55f)
                val px = cx + sweep * ((x1 - x0) / 2f - 6f)
                val pz = (z0 + z1) / 2f + sin(phase * 0.31f) * ((z1 - z0) / 2f - 7f)
                val dip = ((sin(phase * 0.23f) - 0.75f) * 4f).coerceIn(0f, 1f) * 12f
                val cy = glassTop - 6f - dip
                r.beam(px, glassTop - 2f, pz, px, cy, pz, 0.35f, HallArt.chrome.full)
                MachineKit.claw.draw(r, Blend.OPAQUE, xf = xf2.set(px, cy, pz))
                r.quad(
                    px - 2f, glassTop - 1.2f, pz - 3f, px + 2f, glassTop - 1.2f, pz - 3f, px + 2f, glassTop - 1.2f, pz + 3f, px - 2f, glassTop - 1.2f, pz + 3f,
                    HallArt.darkMetal.full, 0f, -1f, 0f, cull = false,
                )
            }
            CabinetShape.WHACK -> {
                val tableY = 26f
                val w = x1 - x0
                val d = z1 - (z0 + 4f)
                for ((k, hole) in MachineKit.whackHoles.withIndex()) {
                    val cycle = (phase * 0.9f + k * 1.37f) % 4f
                    val rise = if (cycle < 1f) sin(cycle * PI.toFloat()) else 0f
                    if (rise <= 0.02f) continue
                    val mx = x0 + hole.first * w
                    val mz = z0 + 4f + hole.second * d
                    MachineKit.mole.draw(r, Blend.OPAQUE, xf = xf.set(mx, tableY - 3f + rise * 6.5f, mz, yaw = 0f))
                }
            }
            CabinetShape.PUSHER -> {
                val deckY = 33f
                val push = (sin(phase * 1.4f) * 0.5f + 0.5f) * 5f
                val shelf = HallArt.brushedMetal.full
                r.quad(x0 + 1.5f, deckY + 5f, z0 + 1.5f, x1 - 1.5f, deckY + 5f, z0 + 1.5f, x1 - 1.5f, deckY + 5f, z0 + 7f + push, x0 + 1.5f, deckY + 5f, z0 + 7f + push, shelf, 0f, 1f, 0f, gloss = 0.8f)
                r.quad(x0 + 1.5f, deckY + 5f, z0 + 7f + push, x1 - 1.5f, deckY + 5f, z0 + 7f + push, x1 - 1.5f, deckY, z0 + 7f + push, x0 + 1.5f, deckY, z0 + 7f + push, art.trimTex.full, 0f, 0f, 1f, emissive = 0.7f)
            }
            CabinetShape.AIR_HOCKEY -> {
                val top = 22.3f
                val w = (x1 - x0) / 2f - 5f
                val d = (z1 - z0 - 8f) / 2f - 5f
                val mz = (z0 + 7.5f + z1 - 1.5f) / 2f
                val px = cx + tri(phase * 0.45f) * w
                val pz = mz + tri(phase * 0.33f + 0.3f) * d
                MachineKit.puck.draw(r, Blend.OPAQUE, xf = xf.set(px, top, pz))
                MachineKit.hockeyMallet(0xFF2F5BE0.toInt()).draw(r, Blend.OPAQUE, xf = xf.set(cx + (px - cx) * 0.6f, top, z1 - 7f))
                MachineKit.hockeyMallet(0xFFE8323C.toInt()).draw(r, Blend.OPAQUE, xf = xf.set(cx + (px - cx) * 0.7f, top, z0 + 13f))
            }
            CabinetShape.SKEEBALL -> {
                // Now and then a ball rolls up the lane and jumps into the rings.
                val cycle = (phase * 0.5f) % 3f
                if (cycle < 1.3f) {
                    val k = cycle / 1.3f
                    val laneFront = z1 - 10f
                    val boardZ = z0 + 8f
                    val zz = laneFront - (laneFront - boardZ - 14f) * k
                    val yy = 19.5f + 8f * k + if (k > 0.75f) sin((k - 0.75f) / 0.25f * PI.toFloat()) * 5f else 0f
                    MachineKit.ball(0xFFB0213A.toInt()).draw(r, Blend.OPAQUE, xf = xf.set(cx + sin(phase * 3f) * 2f, yy + 2f, zz, pitch = -k * 20f, scale = 2.1f))
                }
            }
            CabinetShape.HOOPS -> {
                val cycle = (phase * 0.45f) % 3f
                if (cycle < 1.2f) {
                    val k = cycle / 1.2f
                    val rimY = h - 29f
                    val zz = (z1 - 8f) + ((z0 + 10f) - (z1 - 8f)) * k
                    val yy = 24f + (rimY + 12f - 24f) * (4f * k * (1f - k)) + (rimY - 24f) * k * k
                    MachineKit.basketball.draw(r, Blend.OPAQUE, xf = xf.set(cx, yy, zz, pitch = k * 9f, scale = 3.2f))
                }
            }
            else -> Unit
        }
    }

    /** Glass and netting, drawn after every solid thing. */
    fun drawTransparent(r: Renderer3D) {
        if (model.hasAlpha || model.hasAdd) model.draw(r, Blend.ALPHA)
    }

    /** Chasing marquee bulbs, with a soft halo each. */
    fun drawBulbs(r: Renderer3D, t: Float, bulb: Region, halo: Region) {
        val n = bulbs.size / 3
        if (n == 0) return
        val chase = ((t + seed * 0.19f) * 9f).toInt()
        for (i in 0 until n) {
            val x = bulbs[i * 3]
            val y = bulbs[i * 3 + 1]
            val z = bulbs[i * 3 + 2]
            val on = (chase + i) % 3 == 0
            val c = if (on) 0xFFFFF4C0.toInt() else dim(art.trim, 0.45f)
            r.sprite(x, y, z, 1.4f, 1.4f, bulb, emissive = if (on) 1.8f else 0.7f, tint = c)
            if (on) r.sprite(x, y, z + 0.3f, 6f, 6f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.55f, tint = 0xFFFFE08A.toInt())
        }
    }

    private fun tri(x: Float): Float {
        val f = x - kotlin.math.floor(x)
        return abs(f * 4f - 2f) - 1f
    }
}
