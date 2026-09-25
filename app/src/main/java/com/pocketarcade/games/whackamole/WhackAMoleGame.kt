package com.pocketarcade.games.whackamole

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.Spring
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.chance
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
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
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.range
import com.pocketarcade.games.BaseMiniGame
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Difficulty and payout knobs for whack-a-mole. */
object WhackTuning {
    const val ROUND_SECONDS = 45f
    /** Seconds between pop-ups at the start and at the end of the round (linear ramp). */
    const val SPAWN_INTERVAL_START = 0.95f
    const val SPAWN_INTERVAL_END = 0.38f
    /** How long a mole stays up at the start and at the end of the round. */
    const val UP_TIME_START = 1.15f
    const val UP_TIME_END = 0.55f
    const val GOLD_CHANCE = 0.08f
    const val BOMB_CHANCE_START = 0.10f
    const val BOMB_CHANCE_END = 0.22f
    const val POINTS_NORMAL = 10
    const val POINTS_GOLD = 20
    const val BOMB_PENALTY = 30
    /** Every this many hits in a row adds [COMBO_BONUS] points per hit. */
    const val COMBO_STEP = 5
    const val COMBO_BONUS = 2
    const val BOMB_STUN_SECONDS = 0.5f
    const val POINTS_PER_TICKET = 30
    const val BASE_TICKETS = 1
}

class WhackAMoleGame : BaseMiniGame() {
    override val id = "whack"
    override val title = "WHACK-A-MOLE"
    override val marquee = "WHACK"
    override val instructions = listOf(
        "TAP MOLES TO BONK THEM",
        "GOLD MOLES = DOUBLE POINTS",
        "DON'T HIT THE BOMBS!",
        "HITS IN A ROW BUILD A COMBO",
        "IT GETS FASTER...",
    )
    override val look = CabinetLook(body = Pal.GREEN, trim = Pal.BROWN, glow = Pal.LIME, shape = CabinetShape.WHACK)
    override val roundSeconds = WhackTuning.ROUND_SECONDS

    private enum class Kind { NORMAL, GOLD, BOMB }
    private enum class Phase { HIDDEN, RISING, UP, FALLING, BONKED }

    private class Mole {
        var kind = Kind.NORMAL
        var phase = Phase.HIDDEN
        var t = 0f
        var upTime = 1f
        var rise = 0f
        val squash = Spring(stiffness = 520f, damping = 14f)
    }

    private companion object {
        const val RISE_TIME = 0.12f
        const val FALL_TIME = 0.14f

        // 3D table layout, in world units.
        val HOLE_XS = floatArrayOf(65f, 180f, 295f)
        val HOLE_ZS = floatArrayOf(0f, 150f, 300f)
        const val HOLE_R = 40f
        const val RIM_W = 9f
        const val RIM_H = 5f
        const val WELL_DEPTH = 110f
        /** Height of the mole that shows above the rim when fully up. */
        const val MOLE_H = 92f
        /** Extra body below the rim, so the bottom of the sprite never shows down the hole. */
        const val MOLE_BELOW = 50f
        const val TABLE_L = -30f
        const val TABLE_R = 390f
        const val TABLE_BACK = -90f
        const val TABLE_FRONT = 390f
        const val BACK_H = 230f
        const val PANEL_L = 100f
        const val PANEL_R = 260f
        const val PANEL_TOP = 184f
        const val MALLET_LEN = 90f
        const val MALLET_R = 20f
        const val MALLET_HALF = 30f

        /** Hit box around a hole, in world units: half width, reach above the rim, below it. */
        const val HIT_HALF_W = 50f
        const val HIT_UP = 92f
        const val HIT_DOWN = 26f
    }

    private val moles = Array(9) { Mole() }
    private var spawnT = 0.6f
    private var combo = 0
    private var bestCombo = 0
    private var stunT = 0f
    private var malletT = 99f
    private var hits = 0

    override fun reset() {
        moles.forEach {
            it.phase = Phase.HIDDEN; it.rise = 0f; it.t = 0f; it.squash.snap(1f)
        }
        spawnT = 0.6f
        combo = 0
        bestCombo = 0
        stunT = 0f
        malletT = 99f
        hits = 0
    }

    override fun ticketsFor(score: Int): Int = WhackTuning.BASE_TICKETS + score / WhackTuning.POINTS_PER_TICKET

    override fun isSettled(): Boolean = moles.all { it.phase == Phase.HIDDEN }

    override fun onTimeUp() {
        for (m in moles) if (m.phase == Phase.RISING || m.phase == Phase.UP) {
            m.phase = Phase.FALLING; m.t = 0f
        }
    }

    private val progress: Float get() = clamp01(time / roundSeconds)

    override fun step(dt: Float) {
        stunT = (stunT - dt).coerceAtLeast(0f)
        malletT += dt
        if (!timeUp) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawn()
                val interval = lerp(WhackTuning.SPAWN_INTERVAL_START, WhackTuning.SPAWN_INTERVAL_END, progress)
                spawnT = interval * rng.range(0.75f, 1.25f)
            }
        }
        for (m in moles) {
            m.t += dt
            m.squash.update(dt)
            when (m.phase) {
                Phase.HIDDEN -> m.rise = 0f
                Phase.RISING -> {
                    m.rise = easeOutBack(clamp01(m.t / RISE_TIME))
                    if (m.t >= RISE_TIME) {
                        m.phase = Phase.UP; m.t = 0f
                    }
                }
                Phase.UP -> {
                    m.rise = 1f
                    if (m.t >= m.upTime) {
                        m.phase = Phase.FALLING; m.t = 0f
                        if (m.kind != Kind.BOMB && !timeUp) {
                            combo = 0
                        }
                    }
                }
                Phase.FALLING -> {
                    m.rise = 1f - clamp01(m.t / FALL_TIME)
                    if (m.t >= FALL_TIME) {
                        m.phase = Phase.HIDDEN; m.t = 0f
                    }
                }
                Phase.BONKED -> {
                    m.rise = if (m.t < 0.35f) 1f else 1f - clamp01((m.t - 0.35f) / 0.2f)
                    if (m.t >= 0.55f) {
                        m.phase = Phase.HIDDEN; m.t = 0f
                    }
                }
            }
        }
        // Lit fuses sputter.
        for (i in moles.indices) {
            val m = moles[i]
            if (m.kind == Kind.BOMB && m.phase != Phase.HIDDEN && m.phase != Phase.BONKED && m.rise > 0.5f && rng.chance(0.5f)) {
                val fuseY = moleBase(m.rise) + MOLE_H * 0.98f
                stage.toField(worldX(i) + 4f, fuseY, worldZ(i), pt)
                particles.spawn(pt[0], pt[1], rng.range(-40f, 40f), rng.range(-90f, -20f), 0.25f, 3f, if (rng.nextBoolean()) Pal.YELLOW else Pal.ORANGE)
            }
        }
    }

    private fun spawn() {
        val free = moles.indices.filter { moles[it].phase == Phase.HIDDEN }
        if (free.isEmpty()) return
        val m = moles[free[rng.nextInt(free.size)]]
        val bombChance = lerp(WhackTuning.BOMB_CHANCE_START, WhackTuning.BOMB_CHANCE_END, progress)
        val roll = rng.nextFloat()
        m.kind = when {
            roll < bombChance -> Kind.BOMB
            roll < bombChance + WhackTuning.GOLD_CHANCE -> Kind.GOLD
            else -> Kind.NORMAL
        }
        m.phase = Phase.RISING
        m.t = 0f
        m.upTime = lerp(WhackTuning.UP_TIME_START, WhackTuning.UP_TIME_END, progress) * rng.range(0.85f, 1.15f)
        m.squash.snap(0.7f)
        play(Sfx.POP, 0.45f, if (m.kind == Kind.GOLD) 1.5f else rng.range(0.9f, 1.1f))
    }

    override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {
        if (type != TouchType.DOWN || timeUp) return
        malletT = 0f
        if (stage.touchToPlane(x, y, 0f, pt)) aimMallet(pt[0], 0f, pt[1])
        if (stunT > 0f) {
            play(Sfx.ERROR, 0.3f)
            return
        }
        var hitIndex = -1
        var bestDist = Float.MAX_VALUE
        for (i in moles.indices) {
            val hx = holeSX[i]
            val hy = holeSY[i]
            val s = holeScale[i]
            if (abs(x - hx) < HIT_HALF_W * s && y > hy - HIT_UP * s && y < hy + HIT_DOWN * s) {
                val d = abs(x - hx) + abs(y - (hy - 30f * s))
                if (d < bestDist) {
                    bestDist = d; hitIndex = i
                }
            }
        }
        if (hitIndex < 0) {
            play(Sfx.THUD, 0.25f, 1.3f)
            return
        }
        val m = moles[hitIndex]
        val hx = holeSX[hitIndex]
        val hy = holeSY[hitIndex]
        aimMallet(worldX(hitIndex), if (m.phase == Phase.HIDDEN) 0f else MOLE_H * 0.7f * m.rise, worldZ(hitIndex))
        val whackable = (m.phase == Phase.RISING || m.phase == Phase.UP || m.phase == Phase.FALLING) && m.rise > 0.3f
        if (!whackable) {
            combo = 0
            play(Sfx.THUD, 0.4f)
            particles.burst(hx, hy, 6, 30f, 90f, intArrayOf(Pal.DARKGREEN, Pal.BROWN), 0.4f, 4f, grav = 200f)
            return
        }
        m.phase = Phase.BONKED
        m.t = 0f
        m.squash.snap(1f)
        m.squash.kick(-9f)
        val headY = holeScreenY(hitIndex, 70f)
        when (m.kind) {
            Kind.BOMB -> {
                combo = 0
                stunT = WhackTuning.BOMB_STUN_SECONDS
                addScore(-WhackTuning.BOMB_PENALTY, hx, headY - 10f, Color(Pal.RED))
                popups.add("OUCH!", GAME_W / 2f, 130f, Color(Pal.RED), size = 5f, life = 1f)
                play(Sfx.BOMB)
                fx.haptics.heavy()
                shake.add(0.85f)
                flash.trigger(1f)
                particles.burst(hx, hy - 40f, 60, 80f, 420f, intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.RED, Pal.DARKGRAY), 0.9f, 7f, grav = 250f)
            }
            Kind.GOLD, Kind.NORMAL -> {
                combo++
                hits++
                bestCombo = maxOf(bestCombo, combo)
                val base = if (m.kind == Kind.GOLD) WhackTuning.POINTS_GOLD else WhackTuning.POINTS_NORMAL
                val bonus = (combo / WhackTuning.COMBO_STEP) * WhackTuning.COMBO_BONUS
                addScore(base + bonus, hx, headY - 10f, Color(if (m.kind == Kind.GOLD) Pal.GOLD else Pal.WHITE))
                if (combo % WhackTuning.COMBO_STEP == 0) {
                    popups.add("COMBO x$combo!", GAME_W / 2f, 130f, Color(Pal.CYAN), size = 4f, life = 1f)
                    play(Sfx.SELECT, 0.8f, 1.2f)
                }
                if (m.kind == Kind.GOLD) {
                    play(Sfx.COIN, 1f, 1.2f)
                    play(Sfx.WHACK, 0.8f, 1.1f)
                    fx.haptics.win()
                    shake.add(0.3f)
                    particles.burst(hx, headY + 20f, 30, 80f, 280f, intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), 0.7f, 4f, kind = Particles.SPARKLE)
                } else {
                    play(Sfx.WHACK, 1f, rng.range(0.9f, 1.1f))
                    play(Sfx.BONK, 0.5f, 1f + (combo % 8) * 0.06f)
                    fx.haptics.hit()
                    shake.add(0.18f)
                }
                particles.burst(hx, headY + 30f, 12, 60f, 200f, intArrayOf(Pal.WHITE, Pal.YELLOW), 0.4f, 4f, kind = Particles.SPARKLE)
            }
        }
    }

    // ---------------------------------------------------------------- 3D presentation

    /**
     * The table in 3D. Holes sit on a 3×3 grid of world positions; their on-screen positions
     * (for hit tests, particles and popups) come from projecting them through the fixed camera.
     */
    private val stage = Stage3D(GAME_W.toInt(), GAME_H.toInt()).apply {
        look(180f, 560f, 560f, 180f, 0f, 190f, fovDeg = 52f, centerYFrac = 0.625f)
    }
    private val pt = FloatArray(3)
    private val holeSX = FloatArray(9)
    private val holeSY = FloatArray(9)
    private val holeScale = FloatArray(9)

    init {
        for (i in 0 until 9) {
            stage.toField(worldX(i), 0f, worldZ(i), pt)
            holeSX[i] = pt[0]
            holeSY[i] = pt[1]
            holeScale[i] = stage.scaleAt(worldX(i), 0f, worldZ(i))
        }
    }

    /** Height of the rim line on a mole that has risen [rise] of the way. */
    private fun moleBase(rise: Float) = -(1f - rise) * (MOLE_H + 12f)

    private fun worldX(i: Int) = HOLE_XS[i % 3]
    private fun worldZ(i: Int) = HOLE_ZS[i / 3]

    /** Screen position of a point [lift] units above hole [i]'s centre. */
    private fun holeScreenY(i: Int, lift: Float) = holeSY[i] - lift * holeScale[i]

    private val tableModel: Model by lazy {
        val holes = List(9) { i -> floatArrayOf(worldX(i) - TABLE_L, worldZ(i) - TABLE_BACK) }
        val tableTex = WhackArt.table((TABLE_R - TABLE_L).toInt(), (TABLE_FRONT - TABLE_BACK).toInt(), holes, HOLE_R)
        val b = ModelBuilder()
        b.quad(
            TABLE_L, 0f, TABLE_BACK, TABLE_R, 0f, TABLE_BACK, TABLE_R, 0f, TABLE_FRONT, TABLE_L, 0f, TABLE_FRONT,
            tableTex.full, 0f, 1f, 0f,
        )
        b.quad(
            TABLE_L, 0f, TABLE_FRONT, TABLE_R, 0f, TABLE_FRONT, TABLE_R, -220f, TABLE_FRONT, TABLE_L, -220f, TABLE_FRONT,
            WhackArt.front.full, 0f, 0f, 1f,
        )
        val bb = WhackArt.backboard.full
        b.quad(
            TABLE_L, BACK_H, TABLE_BACK, TABLE_R, BACK_H, TABLE_BACK, TABLE_R, 0f, TABLE_BACK, TABLE_L, 0f, TABLE_BACK,
            bb, 0f, 0f, 1f,
        )
        val wood = WhackArt.wood.full
        // Side posts framing the backboard.
        b.box(TABLE_L - 14f, 0f, TABLE_BACK - 10f, TABLE_L, BACK_H + 20f, TABLE_BACK + 12f, BoxFaces(front = wood, right = wood, top = wood))
        b.box(TABLE_R, 0f, TABLE_BACK - 10f, TABLE_R + 14f, BACK_H + 20f, TABLE_BACK + 12f, BoxFaces(front = wood, left = wood, top = wood))
        b.box(TABLE_L - 14f, BACK_H, TABLE_BACK - 10f, TABLE_R + 14f, BACK_H + 20f, TABLE_BACK + 2f, BoxFaces(front = wood, top = wood))
        // Side rails along the table.
        b.box(TABLE_L - 14f, -220f, TABLE_BACK, TABLE_L, 10f, TABLE_FRONT + 4f, BoxFaces(front = wood, right = wood, top = wood))
        b.box(TABLE_R, -220f, TABLE_BACK, TABLE_R + 14f, 10f, TABLE_FRONT + 4f, BoxFaces(front = wood, left = wood, top = wood))
        b.box(TABLE_L - 14f, 0f, TABLE_FRONT, TABLE_R + 14f, 8f, TABLE_FRONT + 10f, BoxFaces(front = wood, top = wood))
        // Each hole: a dark well under the table and a rubber rim around the opening.
        val rim = WhackArt.rim.full
        for (i in 0 until 9) {
            val x = worldX(i)
            val z = worldZ(i)
            b.cylinder(x, z, -WELL_DEPTH, 0f, HOLE_R, 12, WhackArt.well.full, inward = true)
            b.disc(x, z, -WELL_DEPTH, HOLE_R, 12, WhackArt.wellBottom.full)
            b.cylinder(x, z, 0f, RIM_H, HOLE_R + RIM_W, 14, rim)
            b.annulus(x, z, RIM_H, HOLE_R, HOLE_R + RIM_W, 14, rim)
        }
        b.build()
    }

    private val malletHead: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, -MALLET_HALF, MALLET_HALF, MALLET_R, 10, WhackArt.malletHead.full, top = WhackArt.malletCap.full, bottom = WhackArt.malletCap.full).build()
    }
    private val malletHandle: Model by lazy {
        val h = WhackArt.handle.full
        ModelBuilder().box(-4f, -4f, 0f, 4f, 4f, MALLET_LEN, BoxFaces(front = h, back = h, left = h, right = h, top = h)).build()
    }
    private val handXf = Xform()
    private val partXf = Xform()
    private val localXf = Xform()
    private var malletTX = 0f
    private var malletTZ = 0f
    private var malletTY = 0f
    private var panelText = ""

    private val topLight = PointLight(180f, 320f, 160f, 1f, 0.95f, 0.85f, 620f, 0.9f)
    private val backLight = PointLight(180f, 180f, -40f, 0.6f, 1f, 0.5f, 360f, 0.6f)
    private val goldLight = PointLight(0f, 60f, 0f, 1f, 0.8f, 0.3f, 180f, 0f)
    private val boomLight = PointLight(0f, 80f, 0f, 1f, 0.55f, 0.2f, 360f, 0f)

    override fun render(scope: DrawScope) {
        val r = stage.begin()
        lightScene(r)
        r.gradient(Pal.shade(Pal.DARKGREEN, 0.25f), Pal.NIGHT)
        tableModel.draw(r)
        paintPanelIfNeeded()
        r.quad(
            PANEL_L, PANEL_TOP, TABLE_BACK + 1f, PANEL_R, PANEL_TOP, TABLE_BACK + 1f,
            PANEL_R, PANEL_TOP - 30f, TABLE_BACK + 1f, PANEL_L, PANEL_TOP - 30f, TABLE_BACK + 1f,
            WhackArt.panelTex.full, 0f, 0f, 1f, emissive = 1f,
        )
        drawBulbs(r)
        for (i in moles.indices) drawMole(r, i)
        drawMallet(r)
        stage.present()
        if (stunT > 0f) {
            ArcadeFont.drawCentered(scope, "STUNNED!", GAME_W / 2f, 600f, 3f, Color(Pal.RED), 0.5f + 0.5f * abs(sin(time * 20f)))
        }
    }

    private fun lightScene(r: Renderer3D) {
        val l = r.lighting
        l.ambR = 0.55f; l.ambG = 0.55f; l.ambB = 0.62f
        l.setDirection(-0.3f, 1f, 0.5f)
        l.dirR = 0.45f; l.dirG = 0.42f; l.dirB = 0.36f
        l.points.clear()
        l.points += topLight
        l.points += backLight
        var gold = -1
        for (i in moles.indices) if (moles[i].kind == Kind.GOLD && moles[i].phase != Phase.HIDDEN) gold = i
        if (gold >= 0) {
            goldLight.x = worldX(gold); goldLight.z = worldZ(gold) + 30f
            goldLight.intensity = moles[gold].rise * (0.9f + 0.3f * sin(time * 12f))
            l.points += goldLight
        }
        if (stunT > 0f) {
            boomLight.x = malletTX; boomLight.z = malletTZ
            boomLight.intensity = stunT / WhackTuning.BOMB_STUN_SECONDS * 2f
            l.points += boomLight
        }
    }

    private fun paintPanelIfNeeded() {
        val text = if (combo >= 2) "COMBO x$combo" else "BONK 'EM!"
        if (text == panelText) return
        panelText = text
        WhackArt.paintPanel(text, if (combo >= 5) Pal.CYAN else if (combo >= 2) Pal.LIME else Pal.ORANGE)
    }

    private fun drawBulbs(r: Renderer3D) {
        val white = TexKit.white.full
        val glow = TexKit.glow.full
        for (i in 0 until 14) {
            val x = TABLE_L - 7f + i * (TABLE_R - TABLE_L + 14f) / 13f
            val on = ((time * 5f).toInt() + i) % 2 == 0
            r.sprite(x, BACK_H + 10f, TABLE_BACK + 3f, 7f, 7f, TexKit.dot.full, emissive = 1.2f, tint = if (on) Pal.YELLOW else Pal.shade(Pal.YELLOW, 0.35f))
            if (on) r.sprite(x, BACK_H + 10f, TABLE_BACK + 4f, 26f, 26f, glow, blend = Blend.ADD, emissive = 1f, alpha = 0.5f, tint = Pal.GOLD)
        }
    }

    private val moleXf = Xform()

    private fun drawMole(r: Renderer3D, i: Int) {
        val m = moles[i]
        if (m.phase == Phase.HIDDEN || m.rise <= 0.01f) return
        val bonked = m.phase == Phase.BONKED
        val model = when (m.kind) {
            Kind.NORMAL -> if (bonked) Moles.normalBonked else Moles.normal
            Kind.GOLD -> if (bonked) Moles.goldBonked else Moles.gold
            Kind.BOMB -> if (bonked) Moles.bombHit else Moles.bomb
        }
        val sq = m.squash.value
        val h = (MOLE_H + MOLE_BELOW) * sq
        val x = worldX(i)
        val z = worldZ(i)
        // Below the table the mole is only visible through its hole.
        val base = moleBase(m.rise) - MOLE_BELOW * sq
        // Tipped back a touch so the face looks up at the player.
        moleXf.set(x, base, z, pitch = -0.1f).stretch(2f - sq, sq, 2f - sq)
        model.draw(r, xf = moleXf)
        if (m.kind == Kind.GOLD && !bonked) {
            r.sprite(x, base + h * 0.6f, z + 4f, 110f, 110f, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = 0.35f * m.rise, tint = Pal.GOLD)
        }
        if (bonked && m.t < 0.55f) {
            val headY = base + h * 0.9f
            for (k in 0 until 3) {
                val a = time * 8f + k * 2.1f
                r.sprite(x + cos(a) * 30f, headY + sin(a) * 6f, z + sin(a) * 14f, 16f, 16f, WhackArt.star.full, depthBias = 1.05f)
            }
        }
    }

    /** Lines the mallet up over the struck spot; [y] is the height the head comes down to. */
    private fun aimMallet(x: Float, y: Float, z: Float) {
        malletTX = x
        malletTY = y
        malletTZ = z
    }

    private fun drawMallet(r: Renderer3D) {
        if (malletT >= 0.32f) return
        // Swing down fast, rest on the target, then lift away.
        val swing = when {
            malletT < 0.07f -> 1f - malletT / 0.07f
            malletT < 0.18f -> 0f
            else -> (malletT - 0.18f) / 0.14f
        }
        val strike = 0.5f
        val pitch = -strike + swing * 1.25f
        val yaw = 0.45f
        // Place the hand so that at the strike pose the head lands on the target.
        localXf.set(yaw = yaw, pitch = -strike)
        val ox = localXf.x(0f, 0f, -MALLET_LEN)
        val oy = localXf.y(0f, 0f, -MALLET_LEN)
        val oz = localXf.z(0f, 0f, -MALLET_LEN)
        handXf.set(malletTX - ox, malletTY + MALLET_R - oy, malletTZ - oz, yaw = yaw, pitch = pitch)
        // The handle runs from the hand (local origin) along -z.
        localXf.set(0f, 0f, -MALLET_LEN)
        partXf.setProduct(handXf, localXf)
        malletHandle.draw(r, xf = partXf)
        localXf.set(0f, 0f, -MALLET_LEN, roll = PI.toFloat() / 2f)
        partXf.setProduct(handXf, localXf)
        malletHead.draw(r, xf = partXf)
        if (malletT < 0.1f) {
            val a = (0.1f - malletT) * 6f
            r.flat(malletTX, malletTZ, 1f, 90f * (1f + malletT * 6f), 90f * (1f + malletT * 6f), TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = a.coerceAtMost(0.8f))
        }
    }

    // ---------------------------------------------------------------- simulation-test hooks

    /** What a player sees at hole [i]: 0 nothing to hit, 1 mole, 2 gold mole, 3 bomb. */
    internal fun botView(i: Int): Int {
        val m = moles[i]
        val whackable = (m.phase == Phase.RISING || m.phase == Phase.UP || m.phase == Phase.FALLING) && m.rise > 0.3f
        if (!whackable) return 0
        return when (m.kind) {
            Kind.NORMAL -> 1
            Kind.GOLD -> 2
            Kind.BOMB -> 3
        }
    }

    /** Where hole [i] appears on screen, in field units. */
    internal fun holeX(i: Int): Float = holeSX[i]
    internal fun holeY(i: Int): Float = holeSY[i]

    // ---------------------------------------------------------------- attract mode

    override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {
        p.fill(0, 0, w, h, Color(Pal.GREEN))
        val cw = w / 3f
        val ch = h / 3f
        for (i in 0 until 9) {
            val cx = cw * (i % 3) + cw / 2f
            val cy = ch * (i / 3) + ch * 0.75f
            p.fill(cx - 2.5f, cy - 0.5f, 5f, 1.5f, Color(Pal.BLACK))
            val phase = (time * 1.7f + hash01(i, 9) * 7f) % 3f
            if (phase < 0.9f) {
                val up = sin(phase / 0.9f * Math.PI.toFloat())
                val top = cy - 1f - up * 3f
                val gold = i == ((time / 3f).toInt() % 9)
                p.fill(cx - 1.5f, top, 3f, cy - top, Color(if (gold) Pal.GOLD else Pal.BROWN))
                p.px(cx - 1f, top + 0.5f, Color(Pal.BLACK))
                p.px(cx + 1f, top + 0.5f, Color(Pal.BLACK))
            }
        }
    }
}
