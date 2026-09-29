package com.pocketarcade.games.airhockey

import android.graphics.RadialGradient
import android.graphics.Shader
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture

/**
 * Painted art for the 3D air hockey table. The look is a dark glossy playfield under neon:
 * the player's half is cyan, the CPU's pink, and every marking is a bright thin line, so the
 * bloom makes the lines glow while the wide surface stays dark and the puck, the one bright
 * warm thing, reads instantly. (A pale surface would sit above the bloom threshold and glow
 * white, so nothing large here is pale.)
 */
internal object HockeyArt {
    /** Transparent white: soft gradients fade to this so their middles don't turn grey. */
    private const val CLEAR_WHITE = 0x00FFFFFF
    private const val LINE_HOT = 0xFFB8F4FF.toInt()

    /**
     * The playing surface: dark navy with a lattice of air holes, a glowing centre line and
     * ring, and the goal creases (the top of the texture is the CPU's end, pink; the bottom the
     * player's, cyan).
     */
    fun surface(w: Int, h: Int, goalHalf: Float): Texture = paintTexture(w, h, 3) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cx = wf / 2f
        val cy = hf / 2f
        vgrad(0f, 0f, wf, hf, 0xFF0A1A38.toInt(), 0xFF0E2A52.toInt(), 0xFF0A1A38.toInt())
        radial(cx, cy, wf * 0.8f, Pal.withAlpha(0xFF2A6AB0.toInt(), 0.2f), 0x002A6AB0)
        // Each half is washed with its team's colour.
        rect(0f, 0f, wf, cy, Pal.withAlpha(Pal.PINK, 0.05f))
        rect(0f, cy, wf, cy, Pal.withAlpha(Pal.CYAN, 0.05f))
        // Air holes: kept dim, so they only wake where the puck's light falls.
        for (y in 6 until h step 12) for (x in 6 until w step 12) circle(x.toFloat(), y.toFloat(), 0.95f, 0xFF244A80.toInt())
        // Quarter lines, dashed and faint, in each team's colour.
        var x = 4f
        while (x < wf - 4f) {
            rect(x, hf / 4f - 0.8f, 7f, 1.6f, Pal.withAlpha(Pal.PINK, 0.4f))
            rect(x, hf * 3f / 4f - 0.8f, 7f, 1.6f, Pal.withAlpha(Pal.CYAN, 0.4f))
            x += 12f
        }
        // Centre line and ring.
        rect(0f, cy - 1.6f, wf, 3.2f, Pal.withAlpha(LINE_HOT, 0.85f))
        ring(cx, cy, 44f, 3f, Pal.withAlpha(LINE_HOT, 0.8f))
        circle(cx, cy, 6f, Pal.WHITE)
        // Goal creases.
        ring(cx, 0f, goalHalf + 14f, 3.2f, Pal.withAlpha(Pal.HOTPINK, 0.9f))
        ring(cx, hf, goalHalf + 14f, 3.2f, Pal.withAlpha(Pal.CYAN, 0.9f))
        // Sides fall away into shadow; a fine border line keeps the edge.
        hgrad(0f, 0f, 12f, hf, 0x80000000.toInt(), 0)
        hgrad(wf - 12f, 0f, 12f, hf, 0, 0x80000000.toInt())
        strokeRound(0.5f, 0.5f, wf - 1f, hf - 1f, 6f, 1.5f, Pal.withAlpha(Pal.SKY, 0.5f))
    }

    /** Rails: brushed dark blue steel, a touch lighter on top. */
    val rail: Texture by lazy {
        paintTexture(16, 16, 4) {
            vgrad(0f, 0f, 16f, 16f, 0xFF35558F.toInt(), 0xFF15264C.toInt())
            rect(0f, 0f, 16f, 0.9f, Pal.withAlpha(Pal.WHITE, 0.25f))
        }
    }

    /** The table's body: dark panels with vents. */
    val body: Texture by lazy {
        paintTexture(64, 32, 4) {
            vgrad(0f, 0f, 64f, 32f, 0xFF14264C.toInt(), 0xFF080F22.toInt())
            for (x in 0 until 64 step 16) round(x + 6f, 6f, 4f, 22f, 2f, 0xFF050A18.toInt())
            rect(0f, 0f, 64f, 1.5f, Pal.shade(Pal.SKY, 0.6f))
        }
    }

    val slot: Texture by lazy { paintTexture(8, 8, 4) { fill(0xFF020308.toInt()) } }

    val post: Texture by lazy {
        paintTexture(16, 16, 4) {
            vgrad(0f, 0f, 16f, 16f, 0xFF3A466A.toInt(), 0xFF12182C.toInt())
            rect(3f, 0f, 1.6f, 16f, Pal.withAlpha(Pal.WHITE, 0.2f))
        }
    }

    /** The arena floor, tiled: near-black with a fine cyan grid. */
    val floorTile: Texture by lazy {
        paintTexture(16, 16, 4) {
            fill(0xFF0A0814.toInt())
            for (i in 0 until 26) circle(hash01(i, 21) * 16f, hash01(i, 22) * 16f, 0.25f, 0xFF120E22.toInt())
            rect(0f, 0f, 16f, 0.45f, Pal.withAlpha(Pal.CYAN, 0.2f))
            rect(0f, 0f, 0.45f, 16f, Pal.withAlpha(Pal.CYAN, 0.2f))
        }
    }
    val floorRegion: Region by lazy { floorTile.region(wrap = true) }

    /**
     * The wall behind the far end: dark panels with a curtain of vertical LED strips shading
     * from cyan to pink, brighter towards a glowing bar at the floor. It draws emissive, so the
     * dark parts stay dark and the strips shine. 232 × 116 units span 1160 × 580 cm.
     */
    val wall: Texture by lazy {
        paintTexture(232, 116, 4) {
            fill(0xFF06040E.toInt())
            for (px in 0 until 232 step 29) rect(px + 1f, 2f, 27f, 104f, 0xFF0B0919.toInt())
            var sx = 2f
            while (sx < 230f) {
                val c = Pal.mix(Pal.CYAN, Pal.PINK, sx / 232f)
                vgrad(sx, 6f, 1.4f, 100f, Pal.withAlpha(c, 0f), Pal.withAlpha(c, 0.85f))
                sx += 5f
            }
            hgrad(0f, 106f, 232f, 2.6f, Pal.CYAN, Pal.HOTPINK, Pal.CYAN)
            hgrad(0f, 108.6f, 232f, 7.4f, 0xFF10182E.toInt(), 0xFF181030.toInt(), 0xFF10182E.toInt())
        }
    }

    /**
     * A mallet's skin, painted top (the knob) to bottom (the base's foot) to match the lathe's
     * profile in [HockeyScene]: a bright knob, a dark neck, a lit shoulder, then the base in
     * the team's colour with a dark foot.
     */
    fun malletSkin(color: Int): Texture = paintTexture(16, 32, 4) {
        vgrad(0f, 0f, 16f, 6.8f, Pal.mix(color, Pal.WHITE, 0.5f), color)
        vgrad(0f, 6.8f, 16f, 11.3f, Pal.shade(color, 0.4f), Pal.shade(color, 0.25f))
        rect(0f, 18.1f, 16f, 1.8f, Pal.mix(color, Pal.WHITE, 0.55f))
        vgrad(0f, 19.9f, 16f, 10f, Pal.shade(color, 0.85f), Pal.shade(color, 0.55f))
        rect(0f, 29.9f, 16f, 2.1f, Pal.shade(color, 0.2f))
    }

    val puckSide: Texture by lazy {
        paintTexture(32, 5, 4) {
            vgrad(0f, 0f, 32f, 5f, 0xFFFF7A44.toInt(), 0xFFB02A14.toInt())
            rect(0f, 0.3f, 32f, 0.9f, Pal.withAlpha(Pal.WHITE, 0.35f))
        }
    }

    /** The puck's top: a hot orange face with a white ring and a bright centre, so it stays vivid. */
    val puckTop: Texture by lazy {
        paintTexture(16, 16, 8) {
            radial(6.5f, 6f, 11f, 0xFFFFB070.toInt(), 0xFFE0421E.toInt())
            ring(8f, 8f, 6f, 0.9f, Pal.withAlpha(Pal.WHITE, 0.85f))
            circle(8f, 8f, 2.1f, Pal.withAlpha(Pal.WHITE, 0.9f))
        }
    }

    /** A thin ring, soft either side, for shockwaves and the serve marker. */
    val ring: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = RadialGradient(
                16f, 16f, 15.5f,
                intArrayOf(CLEAR_WHITE, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE), floatArrayOf(0f, 0.72f, 0.88f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(16f, 16f, 15.5f, paint)
            paint.shader = null
        }
    }

    /** A four-point glint with a hot core. */
    val flare: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            radial(16f, 16f, 14f, Pal.withAlpha(Pal.WHITE, 0.8f), CLEAR_WHITE)
            oval(16f, 16f, 15.5f, 0.8f, Pal.withAlpha(Pal.WHITE, 0.9f))
            oval(16f, 16f, 0.8f, 15.5f, Pal.withAlpha(Pal.WHITE, 0.9f))
            circle(16f, 16f, 2.2f, Pal.WHITE)
        }
    }

    /**
     * LED scoreboard, repainted only when the score changes: a caption and big glowing digits
     * for each side, and seven pips under each showing how far to the win they are.
     */
    class Scoreboard(private val goalsToWin: Int) {
        private val painter by lazy { TexPaint(W * SCALE, H * SCALE).also { it.useUnits(SCALE.toFloat()) } }
        val tex: Texture by lazy { Texture(W, H, IntArray(W * SCALE * H * SCALE), SCALE) }
        private var last = -1

        fun paint(you: Int, cpu: Int) {
            val key = you * 100 + cpu
            if (key == last) return
            last = key
            with(painter) {
                vgrad(0f, 0f, W.toFloat(), H.toFloat(), 0xFF10142A.toInt(), 0xFF05060C.toInt())
                for (y in 0 until H step 2) rect(0f, y.toFloat(), W.toFloat(), 0.55f, Pal.withAlpha(Pal.NIGHT, 0.6f))
                strokeRound(0.6f, 0.6f, W - 1.2f, H - 1.2f, 3f, 1.2f, Pal.shade(Pal.SKY, 0.8f))
                label("YOU", 24f, 2.6f, 5f, Pal.CYAN, tiny = true)
                label("CPU", 72f, 2.6f, 5f, Pal.HOTPINK, tiny = true)
                label("TO $goalsToWin", 48f, 12f, 4.2f, Pal.LAVENDER, tiny = true)
                glow(1.5f, Pal.withAlpha(Pal.YELLOW, 0.7f)) {
                    label(you.toString(), 24f, 9.5f, 13f, -1)
                    label(cpu.toString(), 72f, 9.5f, 13f, -1)
                }
                label(you.toString(), 24f, 9.5f, 13f, Pal.YELLOW)
                label(cpu.toString(), 72f, 9.5f, 13f, Pal.YELLOW)
                round(45f, 18f, 6f, 1.6f, 0.8f, Pal.withAlpha(Pal.WHITE, 0.5f))
                // The race to the win, as pips.
                val gap = 4.7f
                val start = -(goalsToWin - 1) * gap / 2f
                for (i in 0 until goalsToWin) {
                    val on = i < you
                    round(24f + start + i * gap - 1.7f, 26f, 3.4f, 3.4f, 1f, if (on) Pal.CYAN else Pal.shade(Pal.CYAN, 0.22f))
                    val cpuOn = i < cpu
                    round(72f + start + i * gap - 1.7f, 26f, 3.4f, 3.4f, 1f, if (cpuOn) Pal.HOTPINK else Pal.shade(Pal.HOTPINK, 0.22f))
                }
                update(tex)
            }
        }

        companion object {
            const val W = 96
            const val H = 34
            private const val SCALE = 4
        }
    }
}
