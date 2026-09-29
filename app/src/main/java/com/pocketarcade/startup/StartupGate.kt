package com.pocketarcade.startup

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.pocketarcade.engine.gl.GfxQuality

/**
 * How much of a frame loading may take. A loading screen draws next to nothing, so most of the
 * frame is the loader's; while a screen someone is watching is up (the title) loading takes only
 * the slack, and steps back for a frame after one that ran long, so the picture never judders
 * for the sake of a bar nobody is looking at.
 */
object LoadBudget {
    /** The share of a frame a loading screen gives to loading, and the title gives to it. */
    const val URGENT_SHARE = 0.6f
    const val GENTLE_SHARE = 0.2f

    /** A gentle slice is skipped when the last frame took longer than this many frames: the picture is struggling. */
    const val STRUGGLING = 1.5f

    /** One frame's length in nanoseconds on a display refreshing at [hz]. */
    fun vsyncNs(hz: Float): Long = (1_000_000_000.0 / hz.coerceIn(24f, 240f)).toLong()

    /**
     * How long this frame may spend loading, in nanoseconds; 0 means not at all. [urgent] is a
     * loading screen (nothing else to keep smooth); [frameNs] is how long the frame before took
     * (0 if there was none).
     */
    fun sliceNs(urgent: Boolean, frameNs: Long, vsyncNs: Long): Long = when {
        urgent -> (vsyncNs * URGENT_SHARE).toLong()
        frameNs in 1L..(vsyncNs * STRUGGLING).toLong() -> (vsyncNs * GENTLE_SHARE).toLong()
        else -> 0L
    }
}

/**
 * The app's loading gate. It runs two plans, one after the other, a slice a frame on the UI
 * thread (see [LoadDriver]): the *boot* plan, which is what the title screen needs (it runs at
 * once behind the loading screen), then the *hall* plan, which builds the hall and hands it to
 * the GPU. The hall plan starts quietly while the title is up, and runs flat out behind the
 * loading screen once the player has tapped to start.
 *
 * The screens read [bootDone] (show the title), [hallReady] (show the hall) and, for the loading
 * screen, [progress] and [label] of whichever plan is running. [progress] restarts at 0 for the
 * hall plan.
 */
@Stable
class StartupGate(
    private val bootPlan: LoadPlan,
    private val hallPlan: () -> LoadPlan,
    private val vsyncNs: () -> Long = { LoadBudget.vsyncNs(GfxQuality.displayHz) },
) {
    companion object {
        /** The title plays on its own for this long (nanoseconds of frame time) before the hall starts loading behind it. */
        const val TITLE_QUIET_NS = 900_000_000L
    }

    /** True once the title screen has what it needs. */
    var bootDone by mutableStateOf(false)
        private set

    /** True once the hall is built and on the GPU. */
    var hallReady by mutableStateOf(false)
        private set

    /** Progress of the plan that is running, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** What the running plan is doing now, for the loading screen ("LAYING THE CARPET"). */
    var label by mutableStateOf("")
        private set

    /**
     * Runs both plans to the end (or until the calling coroutine is cancelled). [urgent] says
     * whether the hall is wanted now (the player has left the title): read every frame.
     */
    suspend fun run(urgent: () -> Boolean) {
        runPlan("boot", bootPlan) { true }
        bootDone = true
        Startup.mark("boot plan done: the title can show")

        // Let the title's first moments play undisturbed.
        var quiet = 0L
        var last = 0L
        while (quiet < TITLE_QUIET_NS && !urgent()) {
            withFrameNanos { now ->
                if (last != 0L) quiet += (now - last).coerceAtMost(100_000_000L)
                last = now
            }
        }
        runPlan("hall", hallPlan(), urgent)
        hallReady = true
        Startup.mark("hall plan done: the hall can show")
    }

    private suspend fun runPlan(name: String, plan: LoadPlan, urgent: () -> Boolean) {
        val driver = LoadDriver(plan, listener = Startup.listener(name))
        progress = 0f
        label = driver.label
        var last = 0L
        try {
            while (!driver.finished) {
                withFrameNanos { now ->
                    val frameNs = if (last == 0L) 0L else now - last
                    last = now
                    val slice = LoadBudget.sliceNs(urgent(), frameNs, vsyncNs())
                    if (slice > 0L) driver.advance(slice)
                    progress = driver.progress
                    label = driver.label
                }
            }
        } finally {
            driver.cancel()
        }
        if (driver.failures.isNotEmpty()) Startup.mark("$name plan had ${driver.failures.size} failing step(s)")
        progress = 1f
    }
}
