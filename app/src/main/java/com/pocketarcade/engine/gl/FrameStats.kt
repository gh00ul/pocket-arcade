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
 * `GL_EXT_disjoint_timer_query`, the render scale, the quality rung, and the draw calls and vertices per frame (to decide
 * whether hardware instancing is worth it). Nothing is logged or allocated per
 * frame while logging is off; the GPU timer queries (when the driver has them) always run, since
 * the adaptive resolution uses them ([takeGpuMs]).
 */
internal class FrameStats {
    companion object {
        const val TAG = "PocketArcade3D"
        /** `adb shell setprop log.tag.PocketArcade3DPin DEBUG` holds the render scale steady. */
        const val PIN_TAG = "PocketArcade3DPin"
        /** `adb shell setprop log.tag.PocketArcade3DNoRefl DEBUG` skips floor reflections (A/B timing). */
        const val NO_REFL_TAG = "PocketArcade3DNoRefl"
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

    /** Whether floor reflections are switched off, to time them against the same scene. */
    var noFloorReflect = false
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
    private var callsSum = 0L
    private var vertsSum = 0L

    // Timer queries, in a ring so results are read a few frames late without stalling.
    private var timerExt = false
    private val queries = IntArray(RING)
    private val queryUsed = BooleanArray(RING)
    private var queryAt = 0
    private val tmp = IntArray(1)
    private var gen = -1
    private var lastGpuMs = -1f

    /** The newest GPU frame time in ms not yet taken, or -1 (none new, or no timer queries). */
    fun takeGpuMs(): Float {
        val v = lastGpuMs
        lastGpuMs = -1f
        return v
    }

    /** Call once per frame before drawing; [generation] notices a new GL context. */
    fun beginFrame(generation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - checkedAt > 3000L || checkedAt == 0L) {
            checkedAt = now
            val on = Log.isLoggable(TAG, Log.DEBUG)
            if (on && !enabled) reset()
            enabled = on
            pinScale = Log.isLoggable(PIN_TAG, Log.DEBUG)
            noFloorReflect = Log.isLoggable(NO_REFL_TAG, Log.DEBUG)
        }
        if (gen != generation) {
            gen = generation
            val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
            timerExt = ext.contains("GL_EXT_disjoint_timer_query")
            if (timerExt) GLES30.glGenQueries(RING, queries, 0)
            queryUsed.fill(false)
            queryAt = 0
            if (enabled) Log.d(TAG, "timer queries ${if (timerExt) "available" else "not available"}")
        }
        if (enabled) drawStart = System.nanoTime()
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
                        val ns = tmp[0].toLong() and 0xFFFFFFFFL
                        lastGpuMs = ns / 1e6f
                        if (enabled) {
                            gpuNs += ns
                            gpuCount++
                        }
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

    /** Counts the draw calls and vertices the renderer issued for the frame just drawn. */
    fun noteDraws(calls: Int, verts: Long) {
        if (!enabled) return
        callsSum += calls
        vertsSum += verts
    }

    /** Call after the frame's GL calls are issued (before swapping). */
    fun endFrame() {
        if (timerExt && !queryUsed[queryAt]) {
            GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
            queryUsed[queryAt] = true
            queryAt = (queryAt + 1) % RING
        }
        if (!enabled) return
        drawNs += System.nanoTime() - drawStart
        if (!timerExt) {
            // Without timer queries, wait for the GPU so the frame's full cost can be seen.
            GLES30.glFinish()
            finishNs += System.nanoTime() - drawStart
        }
    }

    /** Call after the swap; logs once a second. */
    fun afterSwap(renderScale: Float, rung: Int) {
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
                    "record %.2f ms/pass (%d passes) | gl draw %.2f ms | swap %.2f ms avg, %.2f max (%.1f fps) | gpu %s | scale %.2f | rung %d | %d draws, %.1fk verts per frame",
                    rec, recordPasses, drawNs / 1e6 / f, swapAvg, swapMax / 1e6,
                    if (swapAvg > 0) 1000.0 / swapAvg else 0.0, gpu, renderScale, rung,
                    callsSum / f, vertsSum / 1e3 / f,
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
        callsSum = 0L
        vertsSum = 0L
    }
}
