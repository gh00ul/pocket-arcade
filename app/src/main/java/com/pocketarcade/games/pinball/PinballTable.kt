package com.pocketarcade.games.pinball

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pinball table's layout in table units: x across (0 at the left wall, 300 at the right
 * wall; the shooter lane is 272..300), y down the table from the top arch (0) to the drain
 * (640). Gravity pulls towards +y. The 3D view uses the same numbers as world x and z.
 *
 * Every wall, rubber post and bumper the ball can touch lives here as plain arrays, so the
 * physics, the painted playfield and the rail models all read one source. Pure numbers:
 * safe for headless tests.
 */
internal object PinballTable {
    const val W = 300f
    /** Right edge of the playfield proper: the shooter lane's inner wall. */
    const val PLAY_W = 272f
    /** Middle of the playfield (not of the whole table). */
    const val CX = 136f
    const val BALL_R = 9f
    /** A ball whose centre passes this line has drained. */
    const val DRAIN_Y = 640f
    /** Where the apron (the plastic over the drain) starts. */
    const val APRON_Y = 586f

    // Shooter lane and plunger.
    const val LANE_X = 286f
    const val LANE_REST_Y = 575f
    const val LANE_TOP_Y = 160f
    /** The one-way gate at the top of the lane runs from the outer wall down to the lane wall. */
    const val GATE_Y0 = 132f

    // Flippers: pivots, length, radii at the pivot and the tip, and their swing.
    const val FLIP_L_X = 74f
    const val FLIP_R_X = 198f
    const val FLIP_Y = 520f
    const val FLIP_LEN = 52f
    const val FLIP_R0 = 9f
    const val FLIP_R1 = 5f
    /** Left flipper angles (radians from +x towards +y, i.e. down the table); the right mirrors them. */
    const val FLIP_REST = 0.52f
    const val FLIP_UP = -0.45f

    // Pop bumpers.
    val BUMPER_X = floatArrayOf(100f, 172f, 136f)
    val BUMPER_Y = floatArrayOf(150f, 150f, 206f)
    const val BUMPER_R = 17f

    // Top rollover lanes between four guide posts.
    val LANE_GUIDE_X = floatArrayOf(76f, 116f, 156f, 196f)
    const val LANE_GUIDE_Y0 = 44f
    const val LANE_GUIDE_Y1 = 84f
    val ROLLOVER_X = floatArrayOf(96f, 136f, 176f)
    const val ROLLOVER_Y = 64f
    const val ROLLOVER_HALF = 16f

    // Drop targets: a vertical bank of three on the right, facing left.
    const val DROP_X = 258f
    val DROP_Y0 = floatArrayOf(236f, 266f, 296f)
    const val DROP_LEN = 24f

    // Left orbit: the lane between the left wall and this inner wall, with the spinner across it.
    const val ORBIT_X = 40f
    const val ORBIT_Y0 = 120f
    const val ORBIT_Y1 = 330f
    const val SPINNER_Y = 232f

    // Slingshots (left one; the right mirrors it): top, bottom-left and bottom-right corners.
    const val SLING_AX = 50f
    const val SLING_AY = 410f
    const val SLING_BX = 50f
    const val SLING_BY = 464f
    const val SLING_CX = 84f
    const val SLING_CY = 488f
    /** Where the inlane guide turns into the sloped floor that feeds the flipper. */
    const val INLANE_BEND_Y = 476f

    // Collider kinds.
    const val WALL = 0
    /** A slingshot's kicking face; the tag is the sling (0 left, 1 right). */
    const val SLING = 1
    /** The shooter lane's one-way gate: solid only from above. */
    const val GATE = 2
    /** Lively rubber (sling sides, post rubbers). */
    const val RUBBER = 3

    /** How a wall looks in 3D: a tall outer rail, a metal guide or a rubber band. */
    const val STYLE_RAIL = 0
    const val STYLE_GUIDE = 1
    const val STYLE_RUBBER = 2

    private const val MAX_SEGS = 64
    private const val MAX_CIRCLES = 24

    val ax = FloatArray(MAX_SEGS)
    val ay = FloatArray(MAX_SEGS)
    val bx = FloatArray(MAX_SEGS)
    val by = FloatArray(MAX_SEGS)
    val bounce = FloatArray(MAX_SEGS)
    val kind = IntArray(MAX_SEGS)
    val tag = IntArray(MAX_SEGS)
    val style = IntArray(MAX_SEGS)
    var segCount = 0
        private set

    /** Round posts (rubber-ringed pins, guide tips). Bumpers are separate. */
    val px = FloatArray(MAX_CIRCLES)
    val py = FloatArray(MAX_CIRCLES)
    val pr = FloatArray(MAX_CIRCLES)
    var postCount = 0
        private set

    /** Solid side of the gate (unit normal). */
    val gateNX: Float
    val gateNY: Float

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, e: Float, k: Int = WALL, t: Int = 0, s: Int = STYLE_GUIDE) {
        val i = segCount++
        ax[i] = x0; ay[i] = y0; bx[i] = x1; by[i] = y1
        bounce[i] = e; kind[i] = k; tag[i] = t; style[i] = s
    }

    /** The same segment on both sides of the playfield. */
    private fun pair(x0: Float, y0: Float, x1: Float, y1: Float, e: Float, k: Int = WALL, s: Int = STYLE_GUIDE) {
        seg(x0, y0, x1, y1, e, k, 0, s)
        seg(PLAY_W - x0, y0, PLAY_W - x1, y1, e, k, 1, s)
    }

    private fun post(x: Float, y: Float, r: Float) {
        val i = postCount++
        px[i] = x; py[i] = y; pr[i] = r
    }

    private fun postPair(x: Float, y: Float, r: Float) {
        post(x, y, r)
        post(PLAY_W - x, y, r)
    }

    /** An arc of [n] segments around ([cx], [cy]) from angle [a0] to [a1] (degrees, y down). */
    private fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, n: Int) {
        for (k in 0 until n) {
            val t0 = ((a0 + (a1 - a0) * k / n) * PI / 180.0).toFloat()
            val t1 = ((a0 + (a1 - a0) * (k + 1) / n) * PI / 180.0).toFloat()
            seg(cx + cos(t0) * r, cy + sin(t0) * r, cx + cos(t1) * r, cy + sin(t1) * r, 0.45f, s = STYLE_RAIL)
        }
    }

    init {
        // The cabinet: side walls, the top arch and the shooter lane.
        seg(0f, 70f, 0f, DRAIN_Y + 20f, 0.45f, s = STYLE_RAIL)
        seg(W, 70f, W, DRAIN_Y + 20f, 0.45f, s = STYLE_RAIL)
        seg(70f, 0f, 230f, 0f, 0.45f, s = STYLE_RAIL)
        arc(70f, 70f, 70f, 180f, 270f, 8)
        arc(230f, 70f, 70f, 270f, 360f, 8)
        seg(PLAY_W, LANE_TOP_Y, PLAY_W, DRAIN_Y + 20f, 0.4f)
        seg(PLAY_W, LANE_REST_Y + 14f, W, LANE_REST_Y + 14f, 0.1f)
        seg(W, GATE_Y0, PLAY_W, LANE_TOP_Y, 0.3f, GATE)
        val gx = PLAY_W - W
        val gy = LANE_TOP_Y - GATE_Y0
        val gl = sqrt(gx * gx + gy * gy)
        // Perpendicular to the gate, pointing up the table (towards smaller y).
        gateNX = gy / gl * -1f
        gateNY = gx / gl
        // Left orbit's inner wall, with rounded ends.
        seg(ORBIT_X, ORBIT_Y0, ORBIT_X, ORBIT_Y1, 0.4f)
        post(ORBIT_X, ORBIT_Y0, 4f)
        post(ORBIT_X, ORBIT_Y1, 5f)
        // Deflectors that turn a ball running down a side wall into the playfield.
        pair(0f, 350f, 20f, 378f, 0.35f)
        // Inlane guides and the inlane floors that feed the flippers; outlanes run outside them.
        // The floor bends low enough to leave a ball (18) about 4 units to spare under the
        // sling's bottom post (50, 464, r 4); bending at 466 left 16.8 and trapped it there.
        pair(22f, 398f, 22f, INLANE_BEND_Y, 0.35f)
        pair(22f, INLANE_BEND_Y, 64f, 506f, 0.25f)
        postPair(22f, 398f, 4f)
        // Slingshots: two plain rubber sides and a kicking face.
        pair(SLING_AX, SLING_AY, SLING_BX, SLING_BY, 0.5f, RUBBER, STYLE_RUBBER)
        pair(SLING_BX, SLING_BY, SLING_CX, SLING_CY, 0.5f, RUBBER, STYLE_RUBBER)
        pair(SLING_CX, SLING_CY, SLING_AX, SLING_AY, 0.6f, SLING, STYLE_RUBBER)
        postPair(SLING_AX, SLING_AY, 4f)
        postPair(SLING_BX, SLING_BY, 4f)
        postPair(SLING_CX, SLING_CY, 4f)
        // Top lane guides.
        for (x in LANE_GUIDE_X) {
            seg(x, LANE_GUIDE_Y0, x, LANE_GUIDE_Y1, 0.4f)
            post(x, LANE_GUIDE_Y0, 3.5f)
            post(x, LANE_GUIDE_Y1, 3.5f)
        }
        // Guards above and below the drop target bank.
        seg(PLAY_W, 196f, DROP_X, 230f, 0.35f)
        post(DROP_X, 231f, 3f)
        post(DROP_X, DROP_Y0[2] + DROP_LEN + 2f, 3f)
    }

    /** Pivot x of flipper [side] (0 left, 1 right). */
    fun flipX(side: Int): Float = if (side == 0) FLIP_L_X else FLIP_R_X

    /** Resting angle of flipper [side]. */
    fun restAngle(side: Int): Float = if (side == 0) FLIP_REST else PI.toFloat() - FLIP_REST

    /** Raised angle of flipper [side]. */
    fun upAngle(side: Int): Float = if (side == 0) FLIP_UP else PI.toFloat() - FLIP_UP
}
