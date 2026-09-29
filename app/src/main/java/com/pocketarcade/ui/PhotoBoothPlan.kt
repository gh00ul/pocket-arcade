package com.pocketarcade.ui

import com.pocketarcade.hub.Pose
import com.pocketarcade.share.PhotoStrip

/** How one shot of the strip is posed: what the kid does, which way they turn, and what the booth calls out. */
class PhotoPose(
    val pose: Pose,
    /** Radians the kid is turned from facing the camera. */
    val yaw: Float,
    /** The time fed to the pose's animation (the idle sway, the cheering arms), so the frame is always the same one. */
    val time: Float,
    val cry: String,
)

/**
 * What the photo booth does and when: the four poses, and a timeline of one go: a 3-2-1
 * countdown, then a shot every [GAP] seconds, each with a flash, then a short hold before the
 * strip prints. Pure functions of the time since SNAP, so the screen only has to draw them and the
 * JVM tests can check the timing.
 */
object PhotoBoothPlan {
    /** One pose per shot: idle, cheer, sit down, cheers with a drink. */
    val poses: List<PhotoPose> = listOf(
        PhotoPose(Pose.STAND, 0f, 0f, "SMILE!"),
        PhotoPose(Pose.CHEER, 0.2f, 0.42f, "HANDS UP!"),
        PhotoPose(Pose.SIT, -0.28f, 0f, "TAKE A SEAT!"),
        PhotoPose(Pose.HOLD, 0.3f, 0f, "CHEERS!"),
    )

    /** The countdown starts at this number and takes [TICK] seconds a number. */
    const val COUNT = 3
    const val TICK = 0.9f

    /** Seconds from one shot to the next. */
    const val GAP = 1.5f

    /** How long the flash takes to fade. */
    const val FLASH = 0.3f

    /** Seconds after the last flash before the strip is done. */
    const val HOLD = 0.7f

    /** When shot [i] is taken (seconds after SNAP): straight after the countdown, then every [GAP]. */
    fun shotTime(i: Int): Float = COUNT * TICK + i * GAP

    /** When the strip is done. */
    val duration: Float = shotTime(PhotoStrip.SHOTS - 1) + FLASH + HOLD

    /**
     * Where the go has got to. [count] is the countdown number on show (0 once it is over),
     * [captured] how many shots have been taken, [pose] the pose on show (the next one to be shot
     * once a flash has passed, the last one after the final flash), [flash] the white of a flash
     * still fading (1 at the shot, 0 once gone) and [done] whether it's all over.
     */
    data class Step(val count: Int, val captured: Int, val pose: Int, val flash: Float, val done: Boolean)

    fun at(t: Float): Step {
        val count = if (t < COUNT * TICK) COUNT - (t.coerceAtLeast(0f) / TICK).toInt() else 0
        var captured = 0
        var flash = 0f
        for (i in 0 until PhotoStrip.SHOTS) {
            val at = shotTime(i)
            if (t < at) break
            captured = i + 1
            if (t < at + FLASH) flash = 1f - (t - at) / FLASH
        }
        return Step(count, captured, minOf(captured, PhotoStrip.SHOTS - 1), flash, t >= duration)
    }
}
