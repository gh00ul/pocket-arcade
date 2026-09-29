package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.PointLight
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The café in the hall's front-left corner, left of the doors and open to the main aisle, with
 * the fishing tubs behind it: a back bar with the machines and the lit
 * menu, the service counter in front of it (the barista works the lane between them), a queue
 * along the counter front, diner booths against the wall and round tables with chairs.
 * Everything faces the entrance, so it reads from the hall camera, and the queue runs out
 * towards the aisle. Every depth is measured from [FLOOR_Z0], so the whole café moves with it.
 * Pure floor-plan data: no drawing here, so the JVM tests can build it.
 */
object CafeLayout {
    /** The tiled café floor. */
    const val FLOOR_X0 = 16f
    const val FLOOR_X1 = 216f
    const val FLOOR_Z0 = 796f
    const val FLOOR_Z1 = FLOOR_Z0 + 280f

    /** The back bar: machines on top, the menu board along its back edge. */
    const val BAR_X0 = 20f
    const val BAR_X1 = 152f
    const val BAR_Z0 = FLOOR_Z0 + 16f
    const val BAR_Z1 = FLOOR_Z0 + 34f
    const val BAR_H = 30f

    /** The service counter. Its footprint takes in the barista's lane behind it. */
    const val COUNTER_Z0 = FLOOR_Z0 + 34f
    const val COUNTER_BACK = FLOOR_Z0 + 54f
    const val COUNTER_Z1 = FLOOR_Z0 + 72f
    const val COUNTER_H = 28f

    /** Where the barista walks, and how far along the lane they go. */
    const val LANE_Z = FLOOR_Z0 + 44f
    const val LANE_X0 = 30f
    const val LANE_X1 = 142f

    /** Stations along the lane (x): the till, the pastry case, the slushie tanks, the espresso machine and the soft-serve. */
    const val TILL_X = 134f
    const val PASTRY_X = 44f
    const val SLUSH_X = 94f
    const val ESPRESSO_X = 36f
    const val SOFTSERVE_X = 138f

    /** The slushie tanks' centres on the counter (x), their centre z and the tank radius and height. */
    val SLUSH_TANKS = floatArrayOf(84f, 94f, 104f)
    const val SLUSH_Z = FLOOR_Z0 + 62f
    const val SLUSH_R = 4.2f
    const val SLUSH_Y0 = COUNTER_H + 7f
    const val SLUSH_Y1 = COUNTER_H + 22f

    /** Where the espresso machine's steam rises from. */
    const val STEAM_X = 32f
    const val STEAM_Y = BAR_H + 20f
    const val STEAM_Z = FLOOR_Z0 + 24f

    /** Round tables (centre x, z) and the directions their chairs sit (radians, 0 = south of the table). */
    val TABLES = floatArrayOf(
        108f, FLOOR_Z0 + 132f, 172f, FLOOR_Z0 + 132f, 108f, FLOOR_Z0 + 210f, 172f, FLOOR_Z0 + 210f, 140f, FLOOR_Z0 + 264f,
    )
    private val TABLE_CHAIRS = arrayOf(
        floatArrayOf(0f, PI.toFloat() / 2f, -PI.toFloat() / 2f),
        floatArrayOf(0f, PI.toFloat() / 2f, -PI.toFloat() / 2f),
        floatArrayOf(PI.toFloat(), PI.toFloat() / 2f, -PI.toFloat() / 2f),
        floatArrayOf(PI.toFloat(), PI.toFloat() / 2f, -PI.toFloat() / 2f),
        floatArrayOf(PI.toFloat(), PI.toFloat() / 2f, -PI.toFloat() / 2f),
    )
    const val TABLE_R = 11f
    const val CHAIR_D = 17f

    /** Diner booths against the left wall, back to back: their near edge z and depth. */
    val BOOTHS = floatArrayOf(FLOOR_Z0 + 100f, FLOOR_Z0 + 146f)
    const val BOOTH_X0 = 20f
    const val BOOTH_X1 = 60f
    const val BOOTH_D = 46f

    /** Queue spots from the till backwards along the counter front. */
    private val QUEUE = floatArrayOf(TILL_X, FLOOR_Z0 + 86f, 158f, FLOOR_Z0 + 89f, 182f, FLOOR_Z0 + 95f, 204f, FLOOR_Z0 + 106f)

    /**
     * Adds the café's props, its seats (as hangouts marked [Hangout.cafe]) and its queue spots
     * to the floor plan being built.
     */
    fun add(props: MutableList<Prop>, hangouts: MutableList<Hangout>, queue: MutableList<Hangout>) {
        // The floor, the lighting rig and the signs hang off a prop with no footprint, so they
        // cast no floor shadow.
        props += Prop(PropKind.CAFE_FLOOR, FLOOR_X0, FLOOR_Z0 + 136f, FLOOR_X1, FLOOR_Z0 + 136f, 0f, solid = false)
        props += Prop(PropKind.CAFE_BAR, BAR_X0, BAR_Z0, BAR_X1, BAR_Z1, BAR_H)
        props += Prop(PropKind.CAFE_COUNTER, BAR_X0, COUNTER_Z0, BAR_X1, COUNTER_Z1, COUNTER_H)
        // Vending machines side by side beside the counter, facing the tables; a plant by the
        // booths and one in the front corner by the window.
        props += Prop(PropKind.VENDING, 152f, FLOOR_Z0 + 14f, 182f, FLOOR_Z0 + 36f, 62f, variant = 0)
        props += Prop(PropKind.VENDING, 182f, FLOOR_Z0 + 14f, 212f, FLOOR_Z0 + 36f, 62f, variant = 1)
        props += Prop(PropKind.PLANT, 20f, FLOOR_Z0 + 198f, 38f, FLOOR_Z0 + 216f, 40f)
        props += Prop(PropKind.PLANT, 186f, FLOOR_Z0 + 260f, 204f, FLOOR_Z0 + 278f, 40f)

        for ((i, z0) in BOOTHS.withIndex()) {
            props += Prop(PropKind.BOOTH, BOOTH_X0, z0, BOOTH_X1, z0 + BOOTH_D, 24f, variant = i)
            // Kids sit at the open end of each bench, facing each other, and get in from the aisle.
            val seatX = BOOTH_X1 - 10f
            hangouts += Hangout(seatX, z0 + 7f, 0f, playing = false, cafe = true, approachX = BOOTH_X1 + 14f)
            hangouts += Hangout(seatX, z0 + BOOTH_D - 7f, PI.toFloat(), playing = false, cafe = true, approachX = BOOTH_X1 + 14f)
        }
        for (i in 0 until TABLES.size / 2) {
            val tx = TABLES[i * 2]
            val tz = TABLES[i * 2 + 1]
            props += Prop(PropKind.CAFE_TABLE, tx - TABLE_R, tz - TABLE_R, tx + TABLE_R, tz + TABLE_R, 24f, variant = i)
            for (a in TABLE_CHAIRS[i]) {
                val sx = tx + sin(a) * CHAIR_D
                val sz = tz + cos(a) * CHAIR_D
                val yaw = atan2(tx - sx, tz - sz)
                // The chair's facing rides in its variant, in whole degrees.
                props += Prop(PropKind.CHAIR, sx - 5f, sz - 5f, sx + 5f, sz + 5f, 22f, solid = false, variant = Math.round(Math.toDegrees(yaw.toDouble())).toInt())
                hangouts += Hangout(sx, sz, yaw, playing = false, cafe = true)
            }
        }
        for (k in 0 until QUEUE.size / 2) {
            val qx = QUEUE[k * 2]
            val qz = QUEUE[k * 2 + 1]
            // The front of the queue faces the till; everyone else faces the kid ahead.
            val yaw = if (k == 0) PI.toFloat() else atan2(QUEUE[k * 2 - 2] - qx, QUEUE[k * 2 - 1] - qz)
            queue += Hangout(qx, qz, yaw, playing = false)
        }
    }

    /** The café's own point lights (the vending machines bring one each). */
    const val LIGHTS = 2

    /**
     * Adds the café's lights: a warm wash over the counter and one over the seating. The
     * pendants, neon and menu glow on their own, so the hall's light budget barely notices.
     */
    fun lights(out: MutableList<PointLight>) {
        out += PointLight(86f, 80f, COUNTER_Z1 + 6f, 1f, 0.85f, 0.66f, 130f, 1.05f)
        out += PointLight(130f, 96f, FLOOR_Z0 + 186f, 1f, 0.82f, 0.63f, 150f, 0.9f)
    }

    /** Which way a chair of [variant] faces. */
    fun chairYaw(variant: Int): Float = Math.toRadians(variant.toDouble()).toFloat()
}
