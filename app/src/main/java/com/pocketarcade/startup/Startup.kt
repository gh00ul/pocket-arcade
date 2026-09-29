package com.pocketarcade.startup

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Stage timings for the log. [mark] says how long since the last mark and since the origin, [begin]
 * and [end] time a span that other code starts and finishes (title tap to the first hall frame).
 * Pure but for its two injected functions, so the tests give it a fake clock.
 */
class StageClock(private val now: () -> Long, private val emit: (String) -> Unit, private var origin: Long = now()) {
    private var last = origin
    private val open = HashMap<String, Long>()

    /** Restarts the "since the origin" count at [ms] on this clock (the process's own start, say). */
    fun setOrigin(ms: Long) {
        origin = ms
    }

    /** Logs [stage] with the time since the previous mark and since the origin. */
    fun mark(stage: String) {
        val t = now()
        emit("$stage: +${t - last} ms (${t - origin} ms in)")
        last = t
    }

    /** Starts timing [span]. Starting it again restarts it. */
    fun begin(span: String) {
        open[span] = now()
    }

    /** Whether [span] has begun and not ended. */
    fun isOpen(span: String) = span in open

    /** Logs how long [span] took and returns that in ms, or -1 if it never began. */
    fun end(span: String): Long {
        val t0 = open.remove(span) ?: return -1L
        val d = now() - t0
        emit("$span: $d ms")
        return d
    }
}

/**
 * The app's startup log. Everything goes to logcat under [TAG] (`adb logcat -s PocketArcadeStartup`),
 * so the numbers can be read off a phone:
 *  - cold start to the first composition, to the title (the boot plan's steps each timed on the way),
 *  - title tap to the first hall frame,
 *  - leaving a machine to the first hall frame,
 *  - buying a decoration to the hall showing it.
 */
object Startup {
    const val TAG = "PocketArcadeStartup"

    private val clock = StageClock(
        now = { SystemClock.elapsedRealtime() },
        emit = { Log.i(TAG, it) },
    ).also {
        // Process.getStartElapsedRealtime is on the same clock as SystemClock.elapsedRealtime.
        val start = Process.getStartElapsedRealtime()
        if (start > 0L) it.setOrigin(start)
    }

    /** The span the next hall frame closes, or null: read every hall frame, so it is one volatile read. */
    @Volatile
    private var hallSpan: String? = null

    /**
     * Time that only runs while frames do, in milliseconds, at most 100 a frame: for timeouts that
     * must not fire because the app sat in the background (no frames come then). Fed by [tickFrame].
     */
    @Volatile
    var frameClockMs = 0L
        private set
    private var lastFrameNs = 0L

    /** Called once a frame by whatever drives loading, with the frame's time in nanoseconds. */
    fun tickFrame(frameTimeNs: Long) {
        if (lastFrameNs != 0L) frameClockMs += ((frameTimeNs - lastFrameNs) / 1_000_000L).coerceIn(0L, 100L)
        lastFrameNs = frameTimeNs
    }

    fun mark(stage: String) = clock.mark(stage)

    fun begin(span: String) = clock.begin(span)

    fun end(span: String) = clock.end(span)

    /** Times [span] from now until the hall draws its next frame ([hallFrameDrawn]). */
    fun awaitHallFrame(span: String) {
        clock.begin(span)
        hallSpan = span
    }

    /** Called by the hall after it hands a frame to the GPU: closes a span waiting for one. */
    fun hallFrameDrawn() {
        val span = hallSpan ?: return
        hallSpan = null
        clock.end(span)
    }

    /** A [LoadListener] that logs each step's time under [plan]'s name (only steps worth a line). */
    fun listener(plan: String): LoadListener = object : LoadListener {
        override fun onStepDone(step: LoadStep, index: Int, ns: Long, failure: Exception?) {
            val ms = ns / 1_000_000L
            if (failure != null) {
                Log.w(TAG, "$plan #$index ${step.name} failed after $ms ms", failure)
            } else if (ms >= LOG_STEP_MS) {
                Log.i(TAG, "$plan #$index ${step.name}: $ms ms (weight ${step.weight})")
            }
        }

        override fun onTimeout(step: LoadStep.Wait) {
            Log.w(TAG, "$plan ${step.name} timed out after ${step.timeoutMs} ms; carrying on")
        }
    }

    /** Steps quicker than this aren't worth a log line each. */
    private const val LOG_STEP_MS = 4L
}
