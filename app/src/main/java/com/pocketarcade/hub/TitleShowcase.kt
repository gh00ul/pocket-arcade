package com.pocketarcade.hub

import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.games.MiniGame
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.tan

/**
 * The title screen's showroom: one of every machine lined up on a polished floor in front of a
 * dark stage wall, lit by hanging spotlights through the haze, filling the whole screen. A scripted
 * camera ([ShowroomPath]) glides along the row on a spline, low and oblique so the cabinets pass
 * in depth, and on the player's tap pushes in toward them ([TitlePush]) as the title hands over
 * to the hall. With reduce motion it holds one composed shot instead.
 */
class TitleShowcase(private val games: List<MiniGame>) {
    companion object {
        const val SLOT = "title"

        /** Horizontal field of view the framing aims for, degrees; the vertical follows from the screen's shape. */
        const val H_FOV_DEG = 42f
        const val MIN_FOV_DEG = 50f
        const val MAX_FOV_DEG = 86f

        /**
         * The lens is shifted so the look-at point lands this far down the picture (0.5 is the middle),
         * leaving the upper third of a tall screen for the sign over the stage's dark sky.
         */
        const val CENTER_Y = 0.585f

        /** The stage wall behind the row, its height, and the floor's extent (the machines' fronts are at z = 20). */
        const val WALL_Z = -150f
        const val WALL_HEIGHT = 135f
        const val FLOOR_FRONT = 520f

        /** Floor and wall run this far past the ends of the row, so no edge is ever in view. */
        const val MARGIN = 700f

        /** Where each machine's spotlight hangs (height, and how far in front of the row). */
        const val SPOT_Y = 215f
        const val SPOT_Z = 64f

        /** Exit brightening: how much exposure and bloom the push adds at its end. */
        const val EXIT_EXPOSURE = 0.5f
        const val EXIT_BLOOM = 0.45f
    }

    private val r = Renderer3D(1, 1)
    private val spacing = 58f
    private val rowWidth = spacing * (games.size - 1).coerceAtLeast(0)
    private val rowHalf = (rowWidth / 2f).coerceAtLeast(60f)
    private val units: List<MachineUnit> = games.mapIndexed { i, g ->
        val (w, d, h) = HubLayout.cabinetSize(g)
        val cx = i * spacing - rowWidth / 2f
        // Long machines sit further back so every front lines up.
        val front = 20f
        val prop = Prop(PropKind.MACHINE, cx - w / 2f, front - d, cx + w / 2f, front, h, machine = i, shape = g.look.shape, variant = i)
        MachineUnit(prop, g, MachineArt(g))
    }
    private val spots = units.map { u ->
        PointLight(u.prop.centerX, 110f, 40f, 1f, 0.95f, 0.88f, 150f, 0.9f)
    }
    private val bulb = HallArt.solid(-1).full
    private val halo = HallArt.glow.full
    private val shaft = HallArt.shaft.full
    private val floor = HallArt.tiles.region(wrap = true)
    private val wall = HallArt.solid(0xFF2A1858.toInt()).full
    private val neonPink = HallArt.solid(0xFFFF4FA8.toInt()).full
    private val neonCyan = HallArt.solid(0xFF39E6F2.toInt()).full

    private val path = ShowroomPath.forRow(rowHalf)
    private val pose = CameraPose()

    /** The eye's x and the heading (radians, 0 down the row's depth, positive to the right) of the last frame drawn. */
    var eyeX = 0f
        private set
    var yaw = 0f
        private set

    /** The screen row (pixels) where the stage wall's top edge falls in the last frame: the sky is above it. */
    var wallTopY = 0f
        private set
    private val scratch = FloatArray(3)

    /**
     * Records one frame filling a [w] × [h] pixel screen, [t] seconds after the title appeared, with the
     * exit push at [exit] (0..1). [calm] holds a single shot with no camera move and no push.
     */
    fun draw(w: Int, h: Int, t: Float, exit: Float, calm: Boolean, highScore: (String) -> Int) {
        val fbw = w.coerceAtLeast(16)
        val fbh = h.coerceAtLeast(16)
        r.startFrame()
        r.resize(fbw, fbh)

        if (calm) {
            path.pose(ShowroomPath.START_SEGMENTS * path.segmentSeconds, pose)
        } else {
            path.pose(t + ShowroomPath.START_SEGMENTS * path.segmentSeconds, pose)
            TitlePush.apply(pose, exit)
        }
        val aspect = fbw.toFloat() / fbh
        val fovY = (2f * atan(tan(Math.toRadians(H_FOV_DEG / 2.0)).toFloat() / aspect))
            .coerceIn(Math.toRadians(MIN_FOV_DEG.toDouble()).toFloat(), Math.toRadians(MAX_FOV_DEG.toDouble()).toFloat())
        r.camera.lookAt(pose.ex, pose.ey, pose.ez, pose.tx, pose.ty, pose.tz, fovY * pose.fovScale, fbw, fbh, CENTER_Y)
        eyeX = pose.ex
        yaw = atan2(pose.tx - pose.ex, pose.ez - pose.tz)

        val l = r.lighting
        l.ambR = 0.34f; l.ambG = 0.3f; l.ambB = 0.42f
        l.setDirection(0.2f, 1f, 0.8f)
        l.dirR = 0.2f; l.dirG = 0.18f; l.dirB = 0.24f
        l.points.clear()
        for (i in spots.indices) l.points += spots[i]
        for (i in units.indices) l.points += units[i].lights
        r.fogNear = 220f
        r.fogFar = 640f
        r.fogFloor = 0.2f
        val push = TitlePush.ease(if (calm) 0f else exit)
        r.exposure = 1.25f + EXIT_EXPOSURE * push
        r.bloom = 0.9f + EXIT_BLOOM * push
        r.vignette = 0.34f
        // The polished tiles pick up the machines' neon, as the hall's floor does.
        r.floorReflect = 1f
        r.clear(0xFF07050E.toInt())
        // The night sky, brightest where it meets the top of the stage wall (the far background has
        // no parallax of its own; the stars over it are moved by the camera's heading instead).
        wallTopY = if (r.camera.project(pose.ex, WALL_HEIGHT, WALL_Z, scratch)) scratch[1].coerceIn(fbh * 0.15f, fbh * 0.7f) else fbh * 0.32f
        r.gradient(0xFF04030A.toInt(), 0xFF2A1450.toInt(), 0, wallTopY.toInt().coerceAtLeast(1))

        val half = rowHalf + MARGIN
        // Floor: the same tiles the hall's entrance is laid with, 3 texels a unit.
        r.quad(
            -half, 0f, WALL_Z, half, 0f, WALL_Z, half, 0f, FLOOR_FRONT, -half, 0f, FLOOR_FRONT, floor, 0f, 1f, 0f,
            u0 = -half * 3f, v0 = WALL_Z * 3f, u1 = half * 3f, v1 = FLOOR_FRONT * 3f, gloss = 0.7f,
        )
        // Stage wall: dark and self-lit (the spotlights shouldn't wash it out), trimmed in neon.
        r.quad(-half, WALL_HEIGHT, WALL_Z, half, WALL_HEIGHT, WALL_Z, half, 0f, WALL_Z, -half, 0f, WALL_Z, wall, 0f, 0f, 1f, emissive = 0.32f, cull = false)
        r.quad(-half, WALL_HEIGHT, WALL_Z + 0.5f, half, WALL_HEIGHT, WALL_Z + 0.5f, half, WALL_HEIGHT - 3f, WALL_Z + 0.5f, -half, WALL_HEIGHT - 3f, WALL_Z + 0.5f, neonPink, 0f, 0f, 1f, emissive = 2f, cull = false)
        r.quad(-half, 5f, WALL_Z + 0.5f, half, 5f, WALL_Z + 0.5f, half, 2f, WALL_Z + 0.5f, -half, 2f, WALL_Z + 0.5f, neonCyan, 0f, 0f, 1f, emissive = 2f, cull = false)

        for (i in units.indices) {
            val u = units[i]
            u.refresh(highScore(u.game.id), t)
            u.drawOpaque(r, t)
        }
        for (i in units.indices) {
            val u = units[i]
            val p = u.prop
            r.decal(p.x0 - 16f, p.z1 - 6f, p.x1 + 16f, p.z1 + 44f, 0.2f, halo, Blend.ADD, emissive = 1f, alpha = 0.4f, tint = u.art.glow)
        }
        for (i in units.indices) {
            val u = units[i]
            u.drawTransparent(r)
            u.drawBulbs(r, t, bulb, halo)
        }
        // A spotlight over every machine, its shaft hanging in the haze with a slow shimmer.
        for (i in units.indices) {
            val u = units[i]
            val p = u.prop
            val shimmer = if (calm) 1f else 0.85f + 0.15f * sin(t * 1.3f + i * 1.7f)
            r.beam(p.centerX, SPOT_Y, SPOT_Z, p.centerX, 0f, p.z1 + 6f, 40f, shaft, Blend.ADD, emissive = 1f, alpha = 0.24f * shimmer, tint = u.art.glow)
            r.sprite(p.centerX, SPOT_Y, SPOT_Z, 9f, 9f, halo, blend = Blend.ADD, emissive = 1f, alpha = 0.6f, tint = u.art.glow)
        }
        Gfx.submit(SLOT, r.finishFrame(0, 0, fbw, fbh))
    }
}
