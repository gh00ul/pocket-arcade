package com.pocketarcade.engine.gl

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.pocketarcade.engine.r3d.RenderPass

/**
 * Frame-time logging for the GL thread, enabled with
 * `adb shell setprop log.tag.PocketArcade3D DEBUG` (then restart the app). Once a second it logs
 * the UI thread's recording time per pass (startFrame → finishFrame), the GL thread's time to
 * issue a frame, the interval between buffer swaps, the GPU time when the driver has
 * `GL_EXT_disjoint_timer_query`, and the render scale. Nothing is measured or allocated per
 * frame while logging is off.
 */
internal class FrameStats {
    companion object {
        const val TAG = "PocketArcade3D"
        /** `adb shell setprop log.tag.PocketArcade3DPin DEBUG` holds the render scale steady. */
        const val PIN_TAG = "PocketArcade3DPin"
        private const val GL_TIME_ELAPSED_EXT = 0x88BF
        private const val GL_GPU_DISJOINT_EXT = 0x8FBB
        private const val RING = 4
    }

    /** Whether logging is on; re-read from the log property every few seconds. */
    var enabled = false
        private set

    /** Whether the adaptive resolution is held at its ceiling, so runs compare like for like. */
    var pinScale = false
        private set
    private var checkedAt = 0L

    private var windowStart = 0L
    private var frames = 0
    private var recordNs = 0L
    private var recordPasses = 0
    private var drawNs = 0L
    private var drawStart = 0L
    private var swapSum = 0L
    private var swapMax = 0L
    private var swapCount = 0
    private var lastSwap = 0L
    private var gpuNs = 0L
    private var gpuCount = 0
    private var finishNs = 0L

    // Timer queries, in a ring so results are read a few frames late without stalling.
    private var timerExt = false
    private val queries = IntArray(RING)
    private val queryUsed = BooleanArray(RING)
    private var queryAt = 0
    private val tmp = IntArray(1)
    private var gen = -1

    /** Call once per frame before drawing; [generation] notices a new GL context. */
    fun beginFrame(generation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - checkedAt > 3000L || checkedAt == 0L) {
            checkedAt = now
            val on = Log.isLoggable(TAG, Log.DEBUG)
            if (on && !enabled) reset()
            enabled = on
            pinScale = Log.isLoggable(PIN_TAG, Log.DEBUG)
        }
        if (!enabled) return
        if (gen != generation) {
            gen = generation
            val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
            timerExt = ext.contains("GL_EXT_disjoint_timer_query")
            if (timerExt) GLES30.glGenQueries(RING, queries, 0)
            queryUsed.fill(false)
            Log.d(TAG, "timer queries ${if (timerExt) "available" else "not available"}")
        }
        drawStart = System.nanoTime()
        if (timerExt) {
            // Collect the oldest query in the ring if it has finished, then reuse it.
            val q = queryAt
            if (queryUsed[q]) {
                GLES30.glGetQueryObjectuiv(queries[q], GLES30.GL_QUERY_RESULT_AVAILABLE, tmp, 0)
                if (tmp[0] != 0) {
                    // A disjoint event (frequency change, preemption) invalidates the timing.
                    GLES30.glGetIntegerv(GL_GPU_DISJOINT_EXT, tmp, 0)
                    val disjoint = tmp[0] != 0
                    GLES30.glGetQueryObjectuiv(queries[q], GLES30.GL_QUERY_RESULT, tmp, 0)
                    if (!disjoint) {
                        gpuNs += tmp[0].toLong() and 0xFFFFFFFFL
                        gpuCount++
                    }
                    queryUsed[q] = false
                }
            }
            if (!queryUsed[q]) GLES30.glBeginQuery(GL_TIME_ELAPSED_EXT, queries[q])
        }
    }

    /** Counts a pass's UI-thread recording time the first time it is drawn. */
    fun notePass(p: RenderPass) {
        if (!enabled || p.statsTaken) return
        p.statsTaken = true
        recordNs += p.recordNs
        recordPasses++
    }

    /** Call after the frame's GL calls are issued (before swapping). */
    fun endFrame() {
        if (!enabled) return
        drawNs += System.nanoTime() - drawStart
        if (timerExt) {
            if (!queryUsed[queryAt]) {
                GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
                queryUsed[queryAt] = true
                queryAt = (queryAt + 1) % RING
            }
        } else {
            // Without timer queries, wait for the GPU so the frame's full cost can be seen.
            GLES30.glFinish()
            finishNs += System.nanoTime() - drawStart
        }
    }

    /** Call after the swap; logs once a second. */
    fun afterSwap(renderScale: Float) {
        if (!enabled) return
        val now = System.nanoTime()
        if (lastSwap != 0L) {
            val d = now - lastSwap
            if (d < 250_000_000L) {
                swapSum += d
                swapCount++
                if (d > swapMax) swapMax = d
            }
        }
        lastSwap = now
        frames++
        if (windowStart == 0L) windowStart = now
        if (now - windowStart >= 1_000_000_000L) {
            val f = frames.coerceAtLeast(1)
            val swapAvg = if (swapCount > 0) swapSum / 1e6 / swapCount else 0.0
            val rec = if (recordPasses > 0) recordNs / 1e6 / recordPasses else 0.0
            val gpu = if (gpuCount > 0) {
                String.format("%.2f ms", gpuNs / 1e6 / gpuCount)
            } else {
                String.format("n/a (draw+glFinish %.2f ms)", finishNs / 1e6 / f)
            }
            Log.d(
                TAG,
                String.format(
                    "record %.2f ms/pass (%d passes) | gl draw %.2f ms | swap %.2f ms avg, %.2f max (%.1f fps) | gpu %s | scale %.2f",
                    rec, recordPasses, drawNs / 1e6 / f, swapAvg, swapMax / 1e6,
                    if (swapAvg > 0) 1000.0 / swapAvg else 0.0, gpu, renderScale,
                ),
            )
            reset()
            windowStart = now
        }
    }

    private fun reset() {
        windowStart = 0L
        frames = 0
        recordNs = 0L
        recordPasses = 0
        drawNs = 0L
        swapSum = 0L
        swapMax = 0L
        swapCount = 0
        gpuNs = 0L
        gpuCount = 0
        finishNs = 0L
    }
}
