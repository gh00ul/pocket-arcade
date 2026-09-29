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
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/** The live screens' glow: just above 1 so the picture reads as lit but stays under the bloom threshold. */
private const val SCREEN_GLOW = 1.15f
/** Radius of the cap of a control-panel button on an upright. */
private const val BUTTON_R = 0.9f
/** Depth of a claw machine's control deck, in front of its glass. */
private const val CLAW_DECK_D = 7.2f
/** Plush prizes in a claw machine's pile (each is a few hundred polygons). */
private const val CLAW_PRIZES = 8

// Attract-mode props, looked up once rather than per frame.
private val skeeBall: Model by lazy { MachineKit.ball(0xFFB0213A.toInt()) }
private val blueMallet: Model by lazy { MachineKit.hockeyMallet(0xFF2F5BE0.toInt()) }
private val redMallet: Model by lazy { MachineKit.hockeyMallet(0xFFE8323C.toInt()) }

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
    /** How lit-up the "you're standing here" highlight is, 0..1 ([Highlight] eases and shapes it). */
    var highlight = 0f
        private set
    /** The emissive multiplier for this frame's draws: 1 unless highlighted. */
    private var boost = 1f

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
    /** The game's own cabinet, if it has one; otherwise the built-in one for its shape. */
    private val design = game.cabinet
    private val box: CabinetBuild

    init {
        val b = ModelBuilder()
        val c = CabinetBuild(b, this, x0, x1, z0, z1, h, prop.variant, seed, art)
        box = c
        val d = design
        if (d != null) {
            d.build(c)
        } else {
            when (prop.shape) {
                CabinetShape.UPRIGHT -> upright(c, tall = false)
                CabinetShape.TOWER -> upright(c, tall = true)
                CabinetShape.CLAW -> claw(c, withPrizes = true)
                CabinetShape.WIDE -> claw(c, withPrizes = false)
                CabinetShape.WHACK -> whack(c)
                CabinetShape.SKEEBALL -> lane(c, skee = true)
                CabinetShape.LANE -> lane(c, skee = false)
                CabinetShape.HOOPS -> hoops(c)
                CabinetShape.PUSHER -> pusher(c)
                CabinetShape.AIR_HOCKEY -> table(c, hockey = true)
                CabinetShape.TABLE -> table(c, hockey = false)
                // Machines that bring their own design; a stand-in if one ever doesn't.
                CabinetShape.RACER, CabinetShape.GUN -> upright(c, tall = false)
                CabinetShape.PINBALL, CabinetShape.FISHING -> table(c, hockey = false)
            }
        }
        model = b.build()
        // Every machine glows onto the carpet in front of it.
        c.light(cx, 20f, z1 + 10f, art.glow, 70f, 0.85f)
    }

    internal fun addBulb(x: Float, y: Float, z: Float) {
        bulbs += x
        bulbs += y
        bulbs += z
    }

    internal fun attachScreen(s: LiveScreen) {
        screen = s
    }

    // ------------------------------------------------------------------ cabinets

    /**
     * A classic upright: a kick plate with the coin door and a ticket dispenser, a sloped control
     * deck with a joystick and lit buttons on its printing, a screen under glass in a raised
     * bezel, and a framed, lit marquee. The tower is taller, with one big button.
     */
    private fun upright(c: CabinetBuild, tall: Boolean) {
        val b = c.b
        val st = 1.8f
        val ix0 = x0 + st
        val ix1 = x1 - st
        val pw = ix1 - ix0
        val panelY = if (tall) 28f else 26f
        val cpBack = z1 - 9f
        val zf = z1 - 1f
        val screenTop = h - (if (tall) 14f else 11f)
        c.sidePanels(h)
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        b.beveledBox(ix0, 0f, z0, ix1, panelY, zf, BoxFaces(front = art.kick.full, top = dark, back = dark), bevel = 0f)
        b.quad(ix0, panelY + 4f, cpBack, ix1, panelY + 4f, cpBack, ix1, panelY, zf, ix0, panelY, zf, (if (tall) art.panelBig else art.panel).full, 0f, 0.91f, 0.41f, gloss = 0.55f)
        // A raised lip along the front of the deck, chrome on top.
        b.box(ix0, panelY - 0.7f, zf - 0.6f, ix1, panelY + 0.5f, zf + 0.7f, BoxFaces(front = metal, top = HallArt.chrome.full, left = metal, right = metal, gloss = 0.8f))
        // The screen: housing, a bezel round it, the live picture, and a pane of glass over it.
        b.box(ix0, panelY + 4f, z0, ix1, screenTop, cpBack, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        val live = c.liveScreen()
        val sxa = ix0 + 1.6f
        val sxb = ix1 - 1.6f
        val sya = panelY + 6.5f
        val syb = screenTop - 1.6f
        val sz = cpBack + 0.15f
        b.quad(sxa, syb, sz, sxb, syb, sz, sxb, sya, sz, sxa, sya, sz, live.texture.full, 0f, 0f, 1f, emissive = SCREEN_GLOW)
        c.screenBezel(sxa, sxb, sya, syb, sz)
        c.screenGlass(sxa, sxb, sya, syb, sz + 0.09f)
        c.marqueeBox(ix0, ix1, screenTop, h - 1f, z0, cpBack + 3f)
        val te = MachineKit.glowFor(art.trim, 0.7f)
        b.box(x0, h - 1f, z0, x1, h, cpBack + 4f, BoxFaces(top = art.topper.full, front = art.trimTex.full, back = dark, left = dark, right = dark, frontEmissive = te, topEmissive = CabinetBuild.TOPPER_GLOW, gloss = 0.5f))
        c.rearPanel(ix0, ix1, 1f, screenTop)
        // The kick plate's hardware: the coin door, and the ticket dispenser under it.
        c.coinDoor(cx, 8f, zf, 8f)
        c.ticketDispenser(cx, 2.4f, zf + 0.5f, 7f)
        // Controls sit on the sloping deck, on the rings and well printed there.
        val tilt = atan2(4f, zf - cpBack)
        fun deckZ(v: Float) = cpBack + v * (zf - cpBack)
        fun deckY(z: Float) = panelY + 4f * (zf - z) / (zf - cpBack)
        if (tall) {
            val bz = deckZ(0.45f)
            b.add(MachineKit.button(art.glow, 2.3f), xf.set(cx, deckY(bz), bz, pitch = tilt))
            val sz2 = deckZ(0.48f)
            for (s in intArrayOf(-1, 1)) b.add(MachineKit.button(art.trim, 0.8f), xf.set(cx + s * 0.305f * pw, deckY(sz2), sz2, pitch = tilt))
        } else {
            val jz = deckZ(0.45f)
            b.add(MachineKit.joystick(art.glow), xf.set(ix0 + MachineKit.PANEL_STICK_U * pw, deckY(jz), jz, pitch = tilt))
            val ringColors = intArrayOf(art.glow, art.trim, lift(art.glow, 0.4f))
            for (row in 0 until 2) for (k in 0 until 3) {
                val bx = ix0 + (MachineKit.PANEL_BUTTON_U + k * MachineKit.PANEL_BUTTON_STEP_U + row * MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * pw
                val bz = deckZ(if (row == 0) MachineKit.PANEL_ROW_V else MachineKit.PANEL_ROW2_V)
                b.add(MachineKit.button(ringColors[k], BUTTON_R), xf.set(bx, deckY(bz), bz, pitch = tilt))
            }
        }
        c.light(cx, panelY + 16f, z1 + 4f, art.glow, 50f, 0.7f)
    }

    /** T-moulding down the two front corners of a body box ([xa]..[xb] wide, [top] high, its front face at depth [z]). */
    private fun frontCorners(c: CabinetBuild, xa: Float, xb: Float, top: Float, z: Float, thick: Float = 1.6f) {
        c.tMoulding(xa - CabinetBuild.T_MOLD_SIDE, xa + thick, 0f, top, z)
        c.tMoulding(xb - thick, xb + CabinetBuild.T_MOLD_SIDE, 0f, top, z)
    }

    /**
     * A glass merchandiser: prize pile (or a screen) under a gantry and claw with LED strips, and a
     * control deck sloping up in front of the glass with a joystick and a lit drop button. The
     * base has the coin door, a ticket dispenser and a prize door with a smoked flap; the marquee
     * on top is framed and lit.
     */
    private fun claw(c: CabinetBuild, withPrizes: Boolean) {
        val b = c.b
        val baseTop = 28f
        val glassTop = h - 8f
        val glassFront = z1 - CLAW_DECK_D
        val side = art.sideArtSquare.full
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        val chrome = HallArt.chrome.full
        val trim = art.trimTex.full
        b.beveledBox(x0, 0f, z0, x1, baseTop, z1, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark, gloss = 0.3f))
        frontCorners(c, x0, x1, baseTop, z1)
        val bandE = MachineKit.glowFor(art.trim, 0.7f)
        b.box(x0 - 0.3f, baseTop - 1f, z0 - 0.3f, x1 + 0.3f, baseTop, z1 + 0.3f, BoxFaces(front = trim, left = trim, right = trim, frontEmissive = bandE))
        c.posts(x0, x1, z0, glassFront, baseTop, glassTop)
        // A solid back behind the prizes, so the case isn't see-through from behind the bank.
        c.rearPanel(x0, x1, 1f, glassTop)
        // The front of the base: a prize door on the left, the coin door, and a ticket dispenser.
        val fx = x0 + 7.5f
        b.box(fx - 5.4f, 3.4f, z1, fx + 5.4f, 13.4f, z1 + 0.3f, BoxFaces(front = chrome, top = chrome, left = chrome, right = chrome, gloss = 0.9f))
        b.quad(fx - 4.8f, 12.8f, z1 + 0.32f, fx + 4.8f, 12.8f, z1 + 0.32f, fx + 4.8f, 4f, z1 + 0.32f, fx - 4.8f, 4f, z1 + 0.32f, MachineKit.prizeChute.full, 0f, 0f, 1f, gloss = 0.5f)
        c.coinDoor(cx + 2.5f, 7f, z1, 8f)
        c.ticketDispenser(x1 - 5.2f, 5f, z1 + 0.5f, 5f)
        // The control deck: a wedge sloping up to the glass, a raised lip along its front.
        val dy0 = baseTop + 0.5f
        val dy1 = baseTop + 4.5f
        val dLen = sqrt(CLAW_DECK_D * CLAW_DECK_D + 16f)
        b.quad(x0 + 1f, dy1, glassFront, x1 - 1f, dy1, glassFront, x1 - 1f, dy0, z1, x0 + 1f, dy0, z1, metal, 0f, CLAW_DECK_D / dLen, 4f / dLen, gloss = 0.5f)
        val uv = floatArrayOf(0f, 0f, 0f)
        b.poly(floatArrayOf(x0 + 1f, x0 + 1f, x0 + 1f), floatArrayOf(baseTop, dy1, dy0), floatArrayOf(glassFront, glassFront, z1), uv, uv, dark, -1f, 0f, 0f)
        b.poly(floatArrayOf(x1 - 1f, x1 - 1f, x1 - 1f), floatArrayOf(dy0, dy1, baseTop), floatArrayOf(z1, glassFront, glassFront), uv, uv, dark, 1f, 0f, 0f)
        b.box(x0 + 1f, baseTop, z1 - 0.7f, x1 - 1f, dy0 + 0.3f, z1, BoxFaces(front = metal, top = chrome, gloss = 0.8f))
        val tilt = atan2(4f, CLAW_DECK_D)
        fun deckY(z: Float) = dy0 + 4f * (z1 - z) / CLAW_DECK_D
        b.add(MachineKit.joystick(art.trim), xf.set(cx - 4.5f, deckY(z1 - 3.8f), z1 - 3.8f, pitch = tilt))
        b.add(MachineKit.button(art.glow, 2.1f), xf.set(cx + 5f, deckY(z1 - 3.6f), z1 - 3.6f, pitch = tilt))
        // Inside: velvet floor and a printed back wall, lit by strips down the back corners and along the roof.
        b.quad(x0 + 1f, baseTop + 0.3f, z0 + 1f, x1 - 1f, baseTop + 0.3f, z0 + 1f, x1 - 1f, baseTop + 0.3f, glassFront - 1f, x0 + 1f, baseTop + 0.3f, glassFront - 1f, MachineKit.velvet.full, 0f, 1f, 0f)
        b.quad(x0 + 1f, glassTop, z0 + 1.2f, x1 - 1f, glassTop, z0 + 1.2f, x1 - 1f, baseTop, z0 + 1.2f, x0 + 1f, baseTop, z0 + 1.2f, art.sideArtSquare.full, 0f, 0f, 1f, emissive = 0.55f)
        c.ledStrip(x0 + 1.4f, baseTop, x0 + 2.2f, glassTop, z0 + 1.3f)
        c.ledStrip(x1 - 2.2f, baseTop, x1 - 1.4f, glassTop, z0 + 1.3f)
        c.ledStripDown(x0 + 1.4f, x1 - 1.4f, glassTop - 0.05f, glassFront - 2.4f, glassFront - 1.2f)
        if (withPrizes) {
            val plushies = Catalog.plushies
            val zc = (z0 + glassFront) / 2f
            val zr = (glassFront - z0) / 2f - 5.5f
            for (k in 0 until CLAW_PRIZES) {
                val p = plushies[(seed + k * 3) % plushies.size]
                val px = cx + MachineKit.jitter(seed + k, 1, (x1 - x0) / 2f - 5f)
                val pz = zc + MachineKit.jitter(seed + k, 2, zr)
                val py = baseTop + 0.3f + (k / 4) * 2.2f
                b.add(Plush3D.model(p), xf.set(px, py, pz, yaw = MachineKit.jitter(seed + k, 3, 0.9f), roll = MachineKit.jitter(seed + k, 4, 0.2f), scale = 0.42f))
            }
            // Prize chute in the front-left corner: a chrome-rimmed hole in the floor.
            b.box(x0 + 1.4f, baseTop, glassFront - 9f, x0 + 9f, baseTop + 0.8f, glassFront - 1.6f, BoxFaces(top = HallArt.solid(0xFF08060A.toInt()).full, front = chrome, left = chrome, right = chrome, back = chrome, gloss = 0.8f))
        } else {
            val live = c.liveScreen()
            val sxa = x0 + 3f
            val sxb = x1 - 3f
            val sya = baseTop + 6f
            val syb = glassTop - 3f
            val sz = z0 + 1.6f
            b.quad(sxa, syb, sz, sxb, syb, sz, sxb, sya, sz, sxa, sya, sz, live.texture.full, 0f, 0f, 1f, emissive = SCREEN_GLOW)
            c.screenBezel(sxa, sxb, sya, syb, sz, frame = 1f, depth = 0.5f)
        }
        // Gantry rails: a frame of chrome under the roof.
        val rail = BoxFaces(front = chrome, top = chrome, back = chrome, left = chrome, right = chrome, gloss = 0.9f)
        b.box(x0 + 1f, glassTop - 2.5f, z0 + 5f, x1 - 1f, glassTop - 1.5f, z0 + 6f, rail)
        b.box(x0 + 1f, glassTop - 2.5f, glassFront - 6f, x1 - 1f, glassTop - 1.5f, glassFront - 5f, rail)
        b.box(x0 + 1.2f, glassTop - 2.5f, z0 + 5f, x0 + 2.4f, glassTop - 1.5f, glassFront - 5f, rail)
        b.box(x1 - 2.4f, glassTop - 2.5f, z0 + 5f, x1 - 1.2f, glassTop - 1.5f, glassFront - 5f, rail)
        c.glassBox(x0 + 0.4f, baseTop, z0 + 0.4f, x1 - 0.4f, glassTop, glassFront - 0.4f)
        c.marqueeBox(x0, x1, glassTop, h, z0, z1)
        c.light(cx, glassTop - 6f, (z0 + glassFront) / 2f, lift(art.glow, 0.3f), 44f, 1.1f)
    }

    /**
     * Whack-a-mole: a padded table with five lit holes, a painted backboard with a framed score
     * display and a lit marquee, the coin door and a ticket dispenser on the front, two mallets
     * resting on the corners.
     */
    private fun whack(c: CabinetBuild) {
        val b = c.b
        val tableY = 26f
        val boardZ = z0 + 4f
        val side = art.sideArtSquare.full
        val dark = art.darkPaint.full
        val pad = art.trimTex.full
        b.beveledBox(x0, 0f, boardZ, x1, tableY, z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, gloss = 0.3f))
        frontCorners(c, x0, x1, tableY, z1)
        b.quad(x0, tableY, boardZ, x1, tableY, boardZ, x1, tableY, z1, x0, tableY, z1, MachineKit.whackTop(art.body).full, 0f, 1f, 0f, gloss = 0.35f)
        c.coinDoor(cx - 5f, 6f, z1, 8f)
        c.ticketDispenser(cx + 6.5f, 7f, z1 + 0.5f, 6.5f)
        // Each hole has a lit ring round its collar.
        val w = x1 - x0
        val d = z1 - boardZ
        val ringE = MachineKit.glowFor(art.glow, 0.9f)
        for ((fx, fz) in MachineKit.whackHoles) b.annulus(x0 + fx * w, boardZ + fz * d, tableY + 0.06f, 3.55f, 4.2f, 12, art.glowTex.full, emissive = ringE)
        // Padded rim round three sides of the table, with a strip of light along its front.
        val padFaces = BoxFaces(front = pad, top = pad, left = pad, right = pad, gloss = 0.4f)
        b.beveledBox(x0 - 0.5f, tableY, z1 - 1.5f, x1 + 0.5f, tableY + 1.2f, z1 + 0.5f, padFaces, bevel = 0.45f, floorAo = 0f)
        b.beveledBox(x0 - 0.5f, tableY, boardZ, x0 + 1f, tableY + 1.2f, z1 - 1.4f, padFaces, bevel = 0.45f, floorAo = 0f)
        b.beveledBox(x1 - 1f, tableY, boardZ, x1 + 0.5f, tableY + 1.2f, z1 - 1.4f, padFaces, bevel = 0.45f, floorAo = 0f)
        c.ledStrip(x0 + 1f, tableY + 0.2f, x1 - 1f, tableY + 0.9f, z1 + 0.52f)
        // Backboard with the marquee on top and the score display in a bezel.
        b.beveledBox(x0, 0f, z0, x1, h - 10f, boardZ, BoxFaces(front = art.sideArt.full, top = dark, left = dark, right = dark, back = dark))
        c.tMoulding(x0 - CabinetBuild.T_MOLD_SIDE, x0 + 1.6f, tableY, h - 10f, boardZ)
        c.tMoulding(x1 - 1.6f, x1 + CabinetBuild.T_MOLD_SIDE, tableY, h - 10f, boardZ)
        c.rearPanel(x0, x1, 1f, h - 10f)
        c.display(cx - 9f, cx + 9f, tableY + 16f, tableY + 21f, boardZ + 0.1f)
        c.screenBezel(cx - 9f, cx + 9f, tableY + 16f, tableY + 21f, boardZ + 0.1f, frame = 0.9f, depth = 0.5f)
        c.marqueeBox(x0 - 1f, x1 + 1f, h - 10f, h, z0, boardZ + 1f)
        // Mallets resting on the front corners.
        b.add(MachineKit.mallet, xf.set(x0 + 5f, tableY + 2f, z1 - 4f, yaw = 2.4f))
        b.add(MachineKit.mallet, xf.set(x1 - 5f, tableY + 2f, z1 - 4f, yaw = -2.4f))
        c.light(cx, tableY + 20f, (boardZ + z1) / 2f, lift(art.glow, 0.3f), 50f, 0.8f)
    }

    /**
     * Skee-ball alley (or a generic lane with a screen at the end): rails with printed sides and
     * chrome caps, a rising wood lane with gutters, a ball tray, the tilted target board (or a
     * screen), a lit score display over the coin door, netting and a framed marquee.
     */
    private fun lane(c: CabinetBuild, skee: Boolean) {
        val b = c.b
        val side = art.sideArtLong.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        val chrome = HallArt.chrome.full
        val black = HallArt.solid(0xFF14121A.toInt()).full
        val railH = 22f
        val boardZ = z0 + 8f
        // The rails: printed side, chrome cap, lit T-moulding at the front.
        for (s in intArrayOf(-1, 1)) {
            val xa = if (s < 0) x0 else x1 - 2f
            val faces = if (s < 0) BoxFaces(left = side, right = inner, top = dark, front = dark, gloss = 0.4f) else BoxFaces(right = side, left = inner, top = dark, front = dark, gloss = 0.4f)
            b.beveledBox(xa, 0f, boardZ, xa + 2f, railH, z1, faces)
            b.box(xa - 0.15f, railH, boardZ, xa + 2.15f, railH + 0.6f, z1, BoxFaces(top = chrome, front = chrome, left = chrome, right = chrome, gloss = 0.9f))
            c.tMoulding(xa - 0.15f, xa + 2.15f, 0f, railH, z1)
        }
        // Front console with the coin door, a score display, a ticket dispenser and the ball tray.
        b.beveledBox(x0 + 2f, 0f, z1 - 10f, x1 - 2f, 18f, z1, BoxFaces(front = art.kick.full, top = black, gloss = 0.3f))
        c.coinDoor(cx - 4f, 3f, z1, 7f)
        c.ticketDispenser(cx + 6f, 4f, z1 + 0.5f, 6.5f)
        c.display(cx - 6f, cx + 6f, 13.4f, 16.8f, z1 + 0.05f)
        c.screenBezel(cx - 6f, cx + 6f, 13.4f, 16.8f, z1 + 0.05f, frame = 0.6f, depth = 0.4f)
        val tray = BoxFaces(top = chrome, front = chrome, left = chrome, right = chrome, gloss = 0.85f)
        b.box(x0 + 3f, 18f, z1 - 1.5f, x1 - 3f, 19.4f, z1 - 0.8f, tray)
        b.box(x0 + 3f, 18f, z1 - 8f, x0 + 3.8f, 19.4f, z1 - 1.5f, tray)
        b.box(x1 - 3.8f, 18f, z1 - 8f, x1 - 3f, 19.4f, z1 - 1.5f, tray)
        // The lane rises towards the jump; a gutter with a chrome divider runs down each side.
        val laneFront = z1 - 10f
        val laneBack = boardZ + 30f
        val wood = MachineKit.laneWood.region(wrap = true)
        b.quad(x0 + 2f, 26f, laneBack, x1 - 2f, 26f, laneBack, x1 - 2f, 18f, laneFront, x0 + 2f, 18f, laneFront, wood, 0f, 0.99f, 0.12f, u1 = 128f, v1 = 512f, gloss = 0.5f)
        fun laneY(z: Float) = 18f + 8f * (laneFront - z) / (laneFront - laneBack) + 0.08f
        val gw = 2.2f
        for (s in intArrayOf(-1, 1)) {
            val xa = if (s < 0) x0 + 2f else x1 - 2f - gw
            b.quad(xa, laneY(laneBack), laneBack, xa + gw, laneY(laneBack), laneBack, xa + gw, laneY(laneFront), laneFront, xa, laneY(laneFront), laneFront, metal, 0f, 0.99f, 0.12f, gloss = 0.6f)
            val xl = if (s < 0) xa + gw else xa - 0.4f
            b.quad(xl, laneY(laneBack) + 0.03f, laneBack, xl + 0.4f, laneY(laneBack) + 0.03f, laneBack, xl + 0.4f, laneY(laneFront) + 0.03f, laneFront, xl, laneY(laneFront) + 0.03f, laneFront, chrome, 0f, 0.99f, 0.12f, gloss = 0.9f)
        }
        // The jump hump.
        b.quad(x0 + 2f, 30f, laneBack - 4f, x1 - 2f, 30f, laneBack - 4f, x1 - 2f, 26f, laneBack, x0 + 2f, 26f, laneBack, wood, 0f, 0.7f, 0.7f, u1 = 128f, v1 = 40f, gloss = 0.5f)
        if (skee) {
            // Tilted target board with the scoring rings.
            b.quad(x0 + 2f, 50f, boardZ, x1 - 2f, 50f, boardZ, x1 - 2f, 27f, laneBack - 6f, x0 + 2f, 27f, laneBack - 6f, MachineKit.skeeRings.full, 0f, 0.66f, 0.75f, gloss = 0.4f)
        } else {
            val live = c.liveScreen()
            b.quad(x0 + 3f, 50f, boardZ + 0.2f, x1 - 3f, 50f, boardZ + 0.2f, x1 - 3f, 30f, boardZ + 0.2f, x0 + 3f, 30f, boardZ + 0.2f, live.texture.full, 0f, 0f, 1f, emissive = SCREEN_GLOW)
            c.screenBezel(x0 + 3f, x1 - 3f, 30f, 50f, boardZ + 0.2f, frame = 1f, depth = 0.5f)
            b.quad(x0 + 2f, 28f, boardZ, x1 - 2f, 28f, boardZ, x1 - 2f, 28f, laneBack - 4f, x0 + 2f, 28f, laneBack - 4f, dark, 0f, 1f, 0f)
        }
        // Backboard and marquee.
        b.beveledBox(x0, 0f, z0, x1, h - 12f, boardZ, BoxFaces(front = dark, left = dark, right = dark, top = dark, back = dark))
        c.rearPanel(x0, x1, 1f, h - 12f)
        c.marqueeBox(x0 - 1f, x1 + 1f, h - 12f, h, z0, boardZ + 1f)
        // Netting over the target end.
        val net = MachineKit.net.region(wrap = true)
        val netTop = h - 14f
        val netEnd = boardZ + 44f
        b.quad(x0, netTop, boardZ, x1, netTop, boardZ, x1, netTop, netEnd, x0, netTop, netEnd, net, 0f, -1f, 0f, u1 = 64f, v1 = 160f, blend = Blend.ALPHA, cull = false)
        b.quad(x0 + 0.3f, netTop, boardZ, x0 + 0.3f, netTop, netEnd, x0 + 0.3f, railH, netEnd, x0 + 0.3f, railH, boardZ, net, 1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        b.quad(x1 - 0.3f, netTop, netEnd, x1 - 0.3f, netTop, boardZ, x1 - 0.3f, railH, boardZ, x1 - 0.3f, railH, netEnd, net, -1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        // Balls waiting in the tray.
        for (k in 0 until 3) b.add(MachineKit.ball(0xFFB0213A.toInt()), xf.set(cx - 5f + k * 5f, 20f, z1 - 5f, scale = 2.1f))
        c.light(cx, 44f, boardZ + 20f, 0xFFFFE8C8.toInt(), 60f, 0.9f)
    }

    /**
     * Basketball alley: printed side walls with chrome caps, a court ramp, a net cage with a
     * chrome frame, and at the end a backboard with an LED border, a rim and net, and a framed
     * score display; the coin door, a credits display and a ticket dispenser on the console.
     */
    private fun hoops(c: CabinetBuild) {
        val b = c.b
        val side = art.sideArtLong.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val chrome = HallArt.chrome.full
        val black = HallArt.solid(0xFF14121A.toInt()).full
        val wallH = 30f
        for (s in intArrayOf(-1, 1)) {
            val xa = if (s < 0) x0 else x1 - 2f
            val faces = if (s < 0) BoxFaces(left = side, right = inner, top = dark, front = dark, gloss = 0.4f) else BoxFaces(right = side, left = inner, top = dark, front = dark, gloss = 0.4f)
            b.beveledBox(xa, 0f, z0 + 4f, xa + 2f, wallH, z1, faces)
            b.box(xa - 0.15f, wallH, z0 + 4f, xa + 2.15f, wallH + 0.6f, z1, BoxFaces(top = chrome, front = chrome, left = chrome, right = chrome, gloss = 0.9f))
            c.tMoulding(xa - 0.15f, xa + 2.15f, 0f, wallH, z1)
        }
        b.beveledBox(x0 + 2f, 0f, z1 - 12f, x1 - 2f, 20f, z1, BoxFaces(front = art.kick.full, top = black, gloss = 0.3f))
        c.coinDoor(cx - 4f, 4f, z1, 7f)
        c.ticketDispenser(cx + 6f, 5f, z1 + 0.5f, 6.5f)
        c.display(cx - 6f, cx + 6f, 14.8f, 18.2f, z1 + 0.05f)
        c.screenBezel(cx - 6f, cx + 6f, 14.8f, 18.2f, z1 + 0.05f, frame = 0.6f, depth = 0.4f)
        // A chrome-lipped rack for the balls on the console.
        val tray = BoxFaces(top = chrome, front = chrome, left = chrome, right = chrome, gloss = 0.85f)
        b.box(x0 + 3f, 20f, z1 - 1.5f, x1 - 3f, 21.4f, z1 - 0.8f, tray)
        b.box(x0 + 3f, 20f, z1 - 10f, x0 + 3.8f, 21.4f, z1 - 1.5f, tray)
        b.box(x1 - 3.8f, 20f, z1 - 10f, x1 - 3f, 21.4f, z1 - 1.5f, tray)
        b.quad(x0 + 2f, 28f, z0 + 4f, x1 - 2f, 28f, z0 + 4f, x1 - 2f, 20f, z1 - 12f, x0 + 2f, 20f, z1 - 12f, MachineKit.court.full, 0f, 0.99f, 0.1f, gloss = 0.45f)
        // Backboard frame, board, an LED border round it, rim and net.
        b.beveledBox(x0, 0f, z0, x1, h - 10f, z0 + 4f, BoxFaces(front = dark, left = dark, right = dark, top = dark, back = dark))
        c.rearPanel(x0, x1, 1f, h - 10f)
        b.quad(cx - 13f, h - 13f, z0 + 4.2f, cx + 13f, h - 13f, z0 + 4.2f, cx + 13f, h - 31f, z0 + 4.2f, cx - 13f, h - 31f, z0 + 4.2f, MachineKit.hoopBoard.full, 0f, 0f, 1f, gloss = 0.9f, emissive = 0.4f)
        val lz = z0 + 4.25f
        c.ledStrip(cx - 13.7f, h - 13f, cx + 13.7f, h - 12.3f, lz)
        c.ledStrip(cx - 13.7f, h - 31.7f, cx + 13.7f, h - 31f, lz)
        c.ledStrip(cx - 13.7f, h - 31f, cx - 13f, h - 13f, lz)
        c.ledStrip(cx + 13f, h - 31f, cx + 13.7f, h - 13f, lz)
        val rimY = h - 29f
        val rimZ = z0 + 10f
        val orange = HallArt.paint(0xFFFF7A1A.toInt(), 0.3f, 0.8f).full
        b.torus(cx, rimY, rimZ, 4.6f, 0.45f, orange, segments = 16, sides = 6, gloss = 0.7f)
        b.box(cx - 0.6f, rimY - 0.5f, z0 + 4f, cx + 0.6f, rimY + 0.3f, rimZ - 4.4f, BoxFaces(top = orange, left = orange, right = orange, front = orange))
        val netRegion = MachineKit.net.region(wrap = true)
        b.cylinder(cx, rimZ, rimY - 7f, rimY, 3f, 12, netRegion, topRadius = 4.5f)
        b.cylinder(cx, rimZ, rimY - 7f, rimY, 3f, 12, netRegion, topRadius = 4.5f, inward = true)
        c.display(cx - 8f, cx + 8f, h - 38f, h - 33f, z0 + 4.1f)
        c.screenBezel(cx - 8f, cx + 8f, h - 38f, h - 33f, z0 + 4.1f, frame = 0.8f, depth = 0.45f)
        c.marqueeBox(x0 - 1f, x1 + 1f, h - 10f, h, z0, z0 + 6f)
        // Net cage over the alley, on a chrome frame.
        val net = MachineKit.net.region(wrap = true)
        val cageEnd = z1 - 14f
        val top = h - 12f
        b.quad(x0, top, z0 + 4f, x1, top, z0 + 4f, x1, top, cageEnd, x0, top, cageEnd, net, 0f, -1f, 0f, u1 = 80f, v1 = 160f, blend = Blend.ALPHA, cull = false)
        b.quad(x0 + 0.3f, top, z0 + 4f, x0 + 0.3f, top, cageEnd, x0 + 0.3f, wallH, cageEnd, x0 + 0.3f, wallH, z0 + 4f, net, 1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        b.quad(x1 - 0.3f, top, cageEnd, x1 - 0.3f, top, z0 + 4f, x1 - 0.3f, wallH, z0 + 4f, x1 - 0.3f, wallH, cageEnd, net, -1f, 0f, 0f, u1 = 160f, v1 = 90f, blend = Blend.ALPHA, cull = false)
        val frame = BoxFaces(front = chrome, top = chrome, left = chrome, right = chrome, back = chrome, gloss = 0.9f)
        b.box(x0, top - 0.5f, z0 + 4f, x0 + 1f, top + 0.5f, cageEnd, frame)
        b.box(x1 - 1f, top - 0.5f, z0 + 4f, x1, top + 0.5f, cageEnd, frame)
        b.box(x0, top - 0.5f, cageEnd - 1f, x1, top + 0.5f, cageEnd, frame)
        b.box(x0, wallH, cageEnd - 1f, x0 + 1f, top, cageEnd, frame)
        b.box(x1 - 1f, wallH, cageEnd - 1f, x1, top, cageEnd, frame)
        for (k in 0 until 3) b.add(MachineKit.basketball, xf.set(cx - 7f + k * 7f, 23.2f, z1 - 6f, yaw = k * 1.3f, scale = 3.2f))
        c.light(cx, h - 16f, z0 + 20f, 0xFFFFE8C8.toInt(), 70f, 0.9f)
    }

    /**
     * Coin pusher: a glass case over a coin-covered deck with side walls lit along their tops
     * and a sliding shelf, LED strips in the roof and back corners, and a base with the coin
     * door, a framed credits display and a ticket dispenser.
     */
    private fun pusher(c: CabinetBuild) {
        val b = c.b
        val baseTop = 30f
        val glassTop = h - 8f
        val side = art.sideArtSquare.full
        val dark = art.darkPaint.full
        val chrome = HallArt.chrome.full
        val trim = art.trimTex.full
        b.beveledBox(x0, 0f, z0, x1, baseTop, z1, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark, gloss = 0.3f))
        frontCorners(c, x0, x1, baseTop, z1)
        c.posts(x0, x1, z0, z1, baseTop, glassTop)
        c.rearPanel(x0, x1, 1f, glassTop)
        c.coinDoor(cx - 6f, 8f, z1, 9f)
        c.ticketDispenser(cx + 8.5f, 8.5f, z1 + 0.5f, 7f)
        c.display(cx - 9f, cx + 9f, 23f, 27.5f, z1 + 0.05f)
        c.screenBezel(cx - 9f, cx + 9f, 23f, 27.5f, z1 + 0.05f, frame = 0.8f, depth = 0.45f)
        b.quad(x0 + 1f, glassTop, z0 + 1.2f, x1 - 1f, glassTop, z0 + 1.2f, x1 - 1f, baseTop, z0 + 1.2f, x0 + 1f, baseTop, z0 + 1.2f, art.sideArtSquare.full, 0f, 0f, 1f, emissive = 0.5f)
        c.ledStrip(x0 + 1.4f, baseTop, x0 + 2.2f, glassTop, z0 + 1.3f)
        c.ledStrip(x1 - 2.2f, baseTop, x1 - 1.4f, glassTop, z0 + 1.3f)
        c.ledStripDown(x0 + 1.4f, x1 - 1.4f, glassTop - 0.05f, z1 - 2.4f, z1 - 1.2f)
        val deckY = baseTop + 3f
        b.box(x0 + 1f, baseTop, z0 + 1f, x1 - 1f, deckY, z1 - 1f, BoxFaces(top = MachineKit.deck.full, front = MachineKit.gold.full, gloss = 0.6f))
        // Side walls round the playfield, their tops lit.
        val wallE = MachineKit.glowFor(art.trim, 0.8f)
        val wall = BoxFaces(top = trim, right = dark, left = dark, front = dark, topEmissive = wallE, gloss = 0.5f)
        b.box(x0 + 1f, deckY, z0 + 1f, x0 + 2.2f, deckY + 4.6f, z1 - 1f, wall)
        b.box(x1 - 2.2f, deckY, z0 + 1f, x1 - 1f, deckY + 4.6f, z1 - 1f, wall)
        for (k in 0 until 26) {
            val px = cx + MachineKit.jitter(seed + k, 5, (x1 - x0) / 2f - 4f)
            val pz = z0 + 12f + hash01(seed + k, 6) * (z1 - z0 - 16f)
            val layer = if (k % 4 == 0) 1 else 0
            b.add(MachineKit.coin, xf.set(px, deckY + 0.4f + layer * 0.8f, pz, yaw = hash01(k, 7) * 6f))
        }
        c.glassBox(x0 + 0.4f, baseTop, z0 + 0.4f, x1 - 0.4f, glassTop, z1 - 0.4f)
        c.marqueeBox(x0, x1, glassTop, h, z0, z1)
        c.light(cx, glassTop - 5f, (z0 + z1) / 2f, 0xFFFFD27A.toInt(), 44f, 1.1f)
    }

    /**
     * Air hockey (or a generic table with a screen): a table on legs with levelling feet, a glowing
     * air-hole surface, padded rails lit along their inner edge with glowing goal mouths, the coin
     * door and a ticket dispenser on the front, and an overhead scoreboard on a post.
     */
    private fun table(c: CabinetBuild, hockey: Boolean) {
        val b = c.b
        val top = 22f
        val side = art.sideArtLong.full
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        val chrome = HallArt.chrome.full
        val leg = BoxFaces(front = metal, left = metal, right = metal, back = metal)
        for ((lx, lz) in listOf(x0 + 1f to z0 + 7f, x1 - 3f to z0 + 7f, x0 + 1f to z1 - 3f, x1 - 3f to z1 - 3f)) {
            b.box(lx, 0.5f, lz, lx + 2f, 9f, lz + 2f, leg)
            b.box(lx - 0.3f, 0f, lz - 0.3f, lx + 2.3f, 0.5f, lz + 2.3f, BoxFaces.all(dark))
        }
        b.beveledBox(x0 + 0.5f, 9f, z0 + 6f, x1 - 0.5f, top, z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, gloss = 0.3f))
        c.tMoulding(x0 + 0.35f, x0 + 2.1f, 9f, top, z1)
        c.tMoulding(x1 - 2.1f, x1 - 0.35f, 9f, top, z1)
        c.rearPanel(x0 + 0.5f, x1 - 0.5f, 9.5f, top, z0 + 6f)
        c.coinDoor(cx - 6f, 10.5f, z1, 7.5f)
        c.ticketDispenser(cx + 8f, 11f, z1 + 0.5f, 6f)
        val surface = if (hockey) MachineKit.hockeySurface.full else dark
        b.quad(x0 + 2f, top + 0.3f, z0 + 7.5f, x1 - 2f, top + 0.3f, z0 + 7.5f, x1 - 2f, top + 0.3f, z1 - 1.5f, x0 + 2f, top + 0.3f, z1 - 1.5f, surface, 0f, 1f, 0f, emissive = 0.55f, gloss = 0.8f)
        // Padded rails, their tops shaded from the trim colour.
        val rail = HallArt.paint(art.trim, 0f, 0.55f).full
        val rf = BoxFaces(front = rail, top = rail, left = rail, right = rail, back = rail, gloss = 0.6f)
        b.box(x0, top, z0 + 6f, x0 + 2f, top + 3f, z1, rf)
        b.box(x1 - 2f, top, z0 + 6f, x1, top + 3f, z1, rf)
        b.box(x0, top, z1 - 1.5f, cx - 6f, top + 3f, z1, rf)
        b.box(cx + 6f, top, z1 - 1.5f, x1, top + 3f, z1, rf)
        b.box(x0, top, z0 + 6f, cx - 6f, top + 3f, z0 + 7.5f, rf)
        b.box(cx + 6f, top, z0 + 6f, x1, top + 3f, z0 + 7.5f, rf)
        // The rails' inner faces carry a strip of light (the table's rim light) and the goal mouths glow.
        val glowTex = art.glowTex.full
        val rimE = MachineKit.glowFor(art.glow, 1.1f)
        val ry0 = top + 0.9f
        val ry1 = top + 1.7f
        b.quad(x0 + 2.02f, ry1, z0 + 7.5f, x0 + 2.02f, ry1, z1 - 1.5f, x0 + 2.02f, ry0, z1 - 1.5f, x0 + 2.02f, ry0, z0 + 7.5f, glowTex, 1f, 0f, 0f, emissive = rimE)
        b.quad(x1 - 2.02f, ry1, z1 - 1.5f, x1 - 2.02f, ry1, z0 + 7.5f, x1 - 2.02f, ry0, z0 + 7.5f, x1 - 2.02f, ry0, z1 - 1.5f, glowTex, -1f, 0f, 0f, emissive = rimE)
        for ((xa, xb) in listOf((x0 + 2f) to (cx - 6f), (cx + 6f) to (x1 - 2f))) {
            b.quad(xa, ry1, z0 + 7.52f, xb, ry1, z0 + 7.52f, xb, ry0, z0 + 7.52f, xa, ry0, z0 + 7.52f, glowTex, 0f, 0f, 1f, emissive = rimE)
            b.quad(xb, ry1, z1 - 1.52f, xa, ry1, z1 - 1.52f, xa, ry0, z1 - 1.52f, xb, ry0, z1 - 1.52f, glowTex, 0f, 0f, -1f, emissive = rimE)
        }
        if (hockey) {
            val redGoal = HallArt.solid(0xFFE8323C.toInt()).full
            val blueGoal = HallArt.solid(0xFF2F5BE0.toInt()).full
            b.quad(cx - 6f, top + 2.6f, z0 + 7.52f, cx + 6f, top + 2.6f, z0 + 7.52f, cx + 6f, top + 0.4f, z0 + 7.52f, cx - 6f, top + 0.4f, z0 + 7.52f, redGoal, 0f, 0f, 1f, emissive = 1f)
            b.quad(cx + 6f, top + 2.6f, z1 - 1.52f, cx - 6f, top + 2.6f, z1 - 1.52f, cx - 6f, top + 0.4f, z1 - 1.52f, cx + 6f, top + 0.4f, z1 - 1.52f, blueGoal, 0f, 0f, -1f, emissive = 1f)
        }
        // Overhead scoreboard on a post, framed in dark metal.
        b.box(cx - 1.5f, top, z0 + 1f, cx + 1.5f, h - 12f, z0 + 4f, leg)
        val frame = BoxFaces(front = metal, top = metal, left = metal, right = metal, back = metal, gloss = 0.7f)
        if (hockey) {
            b.box(x0 - 3f, h - 14f, z0, x1 + 3f, h, z0 + 4f, BoxFaces(front = art.marquee.full, top = dark, left = dark, right = dark, back = dark, frontEmissive = CabinetBuild.MARQUEE_GLOW))
            b.box(x0 - 3f, h - 14.6f, z0, x1 + 3f, h - 13.8f, z0 + 4.2f, frame)
            b.box(x0 - 3f, h - 0.1f, z0, x1 + 3f, h + 0.5f, z0 + 4.2f, frame)
            c.underside(x0 - 3f, x1 + 3f, z0, z0 + 4f, h - 14f)
            c.display(cx - 8f, cx + 8f, h - 20f, h - 15f, z0 + 4.2f)
            c.screenBezel(cx - 8f, cx + 8f, h - 20f, h - 15f, z0 + 4.2f, frame = 0.8f, depth = 0.45f)
        } else {
            val live = c.liveScreen()
            b.box(x0 - 3f, h - 22f, z0, x1 + 3f, h, z0 + 4f, BoxFaces(front = art.bezel.full, top = dark, left = dark, right = dark, back = dark))
            b.box(x0 - 3f, h - 22.6f, z0, x1 + 3f, h - 21.8f, z0 + 4.2f, frame)
            b.box(x0 - 3f, h - 0.1f, z0, x1 + 3f, h + 0.5f, z0 + 4.2f, frame)
            c.underside(x0 - 3f, x1 + 3f, z0, z0 + 4f, h - 22f)
            b.quad(x0 - 1f, h - 2f, z0 + 4.15f, x1 + 1f, h - 2f, z0 + 4.15f, x1 + 1f, h - 20f, z0 + 4.15f, x0 - 1f, h - 20f, z0 + 4.15f, live.texture.full, 0f, 0f, 1f, emissive = SCREEN_GLOW)
            c.screenBezel(x0 - 1f, x1 + 1f, h - 20f, h - 2f, z0 + 4.15f, frame = 1f, depth = 0.5f)
        }
        c.bulbRow(x0 - 2f, x1 + 2f, h + 0.6f, z0 + 3.4f, 10)
        c.light(cx, top + 26f, (z0 + z1) / 2f, lift(art.glow, 0.4f), 60f, 0.9f)
    }

    // ------------------------------------------------------------------ per frame

    /** Repaints the live screen and display (only while the machine is on screen). */
    fun refresh(best: Int, t: Float) {
        screen?.paint(best, t)
        art.updateDisplay(best, t)
    }

    /**
     * Fades the highlight in while [active] is this cabinet's own play spot and out otherwise,
     * and works out this frame's emissive boost. Cheap enough to run for every cabinet, on screen
     * or not, so one that was lit as the kid walked away fades out rather than freezing.
     */
    fun stepHighlight(active: Spot?, dt: Float, t: Float) {
        highlight = Highlight.step(highlight, Highlight.isSpotOf(active, prop), dt)
        boost = Highlight.boost(highlight, t)
    }

    /** Draws the solid parts and whatever moves in attract mode. */
    fun drawOpaque(r: Renderer3D, t: Float) {
        model.draw(r, Blend.OPAQUE, emissiveBoost = boost)
        val d = design
        if (d != null) {
            d.animate(r, box, t)
            return
        }
        val phase = t + seed * 0.37f
        when (prop.shape) {
            CabinetShape.CLAW, CabinetShape.WIDE -> {
                // The claw patrols over the prizes, dips now and then.
                val glassTop = h - 8f
                val glassFront = z1 - CLAW_DECK_D
                val sweep = sin(phase * 0.55f)
                val px = cx + sweep * ((x1 - x0) / 2f - 6f)
                val pz = (z0 + glassFront) / 2f + sin(phase * 0.31f) * ((glassFront - z0) / 2f - 7f)
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
                val holes = MachineKit.whackHoles
                for (k in holes.indices) {
                    val hole = holes[k]
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
                blueMallet.draw(r, Blend.OPAQUE, xf = xf.set(cx + (px - cx) * 0.6f, top, z1 - 7f))
                redMallet.draw(r, Blend.OPAQUE, xf = xf.set(cx + (px - cx) * 0.7f, top, z0 + 13f))
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
                    skeeBall.draw(r, Blend.OPAQUE, xf = xf.set(cx + sin(phase * 3f) * 2f, yy + 2f, zz, pitch = -k * 20f, scale = 2.1f))
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

    /**
     * Glass and netting, drawn after every solid thing, then the additive glows (halos, light
     * pools under the cabinet, neon edges) over them.
     */
    fun drawTransparent(r: Renderer3D) {
        if (model.hasAlpha) model.draw(r, Blend.ALPHA, emissiveBoost = boost)
        if (model.hasAdd) model.draw(r, Blend.ADD, emissiveBoost = boost)
    }

    /** Chasing marquee bulbs, with a soft halo each; the highlight brightens the idle bulbs and the flashing halos. */
    fun drawBulbs(r: Renderer3D, t: Float, bulb: Region, halo: Region) {
        val n = bulbs.size / 3
        if (n == 0) return
        val chase = ((t + seed * 0.19f) * 9f).toInt()
        val hl = Highlight.ease(highlight)
        val idle = 0.7f + Highlight.BULB_BOOST * hl
        val haloAlpha = 0.55f + Highlight.BULB_HALO * hl
        for (i in 0 until n) {
            val x = bulbs[i * 3]
            val y = bulbs[i * 3 + 1]
            val z = bulbs[i * 3 + 2]
            val on = (chase + i) % 3 == 0
            val c = if (on) 0xFFFFF4C0.toInt() else dim(art.trim, 0.45f)
            r.sprite(x, y, z, 1.4f, 1.4f, bulb, emissive = if (on) 1.8f else idle, tint = c)
            if (on) r.sprite(x, y, z + 0.3f, 6f, 6f, halo, blend = Blend.ADD, emissive = 1f, alpha = haloAlpha, tint = 0xFFFFE08A.toInt())
        }
    }

    private fun tri(x: Float): Float {
        val f = x - kotlin.math.floor(x)
        return abs(f * 4f - 2f) - 1f
    }
}
