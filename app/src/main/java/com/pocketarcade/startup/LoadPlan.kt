package com.pocketarcade.startup

/**
 * One piece of loading work: a [name] the loading screen can show, a [weight] (its share of the
 * progress bar, and a rough guide to how long it takes: a step of weight 4 is expected to take
 * about four times as long as one of weight 1) and either work to run or something to wait for.
 */
sealed class LoadStep(val name: String, val weight: Float) {
    init {
        require(weight > 0f && weight.isFinite()) { "A load step needs a positive weight, not $weight ($name)" }
    }

    /** Work done on the calling thread when the step is reached. */
    class Work(name: String, weight: Float = 1f, val run: () -> Unit) : LoadStep(name, weight)

    /**
     * A wait for something outside the driver, the GPU for one: [start] runs once when the step is
     * reached (to send off the request), then [ready] is polled every slice until it says yes.
     * After [timeoutMs] the step gives up waiting, calls [onTimeout] and counts as finished, so a
     * GPU that never answers can't hold the loading screen up for good.
     */
    class Wait(
        name: String,
        weight: Float = 1f,
        val timeoutMs: Long,
        val start: () -> Unit = {},
        val onTimeout: () -> Unit = {},
        val ready: () -> Boolean,
    ) : LoadStep(name, weight)
}

/** An ordered list of [LoadStep]s: what a loading screen is waiting for. Pure data, testable on the JVM. */
class LoadPlan(val steps: List<LoadStep>) {
    val totalWeight: Float = steps.sumOf { it.weight.toDouble() }.toFloat()

    operator fun plus(other: LoadPlan) = LoadPlan(steps + other.steps)

    companion object {
        val EMPTY = LoadPlan(emptyList())
    }
}

/** Told about each step as it finishes (the startup log, the tests). */
interface LoadListener {
    /** [step] (number [index]) finished after [ns] nanoseconds of the driver's clock; [failure] if its work threw. */
    fun onStepDone(step: LoadStep, index: Int, ns: Long, failure: Exception?) = Unit

    /** [step], a wait, gave up after its timeout. */
    fun onTimeout(step: LoadStep.Wait) = Unit
}

/**
 * Runs a [LoadPlan] a slice at a time, so a loading screen (or a title screen) keeps animating
 * while the work goes on. The caller asks for a slice once a frame, from inside `withFrameNanos`
 * on the thread the work has to run on (the UI thread: the painting code shares mutable state and
 * isn't thread safe), and gives it a time budget.
 *
 * A slice runs whole steps. It starts another only while the time already used plus what the
 * step is expected to take fits the budget, so a slice overruns only by a step that turns out
 * longer than expected, and never starts a heavy step behind a light one that has used the
 * budget up. The first step of a slice always runs, so any budget makes progress. The expected
 * cost of a step is its weight times the time per weight the driver has seen so far.
 *
 * [progress] only moves up. All of this is meant for one thread: the one that calls [advance].
 */
class LoadDriver(
    private val plan: LoadPlan,
    private val clock: () -> Long = System::nanoTime,
    private val listener: LoadListener? = null,
    /** What a step of weight 1 is expected to cost until the first has been seen. */
    initialNsPerWeight: Long = 1_500_000L,
) {
    private val steps = plan.steps
    private var nsPerWeight = initialNsPerWeight.toDouble().coerceAtLeast(1.0)
    private var doneWeight = 0.0
    private var waitStartNs = -1L

    /** Index of the step that runs next (steps.size once the plan is done). */
    var index = 0
        private set

    /** Steps finished so far, whether their work threw or a wait timed out. */
    val stepsDone: Int get() = index

    /** True once every step has finished. */
    val done: Boolean get() = index >= steps.size

    /** True after [cancel]; nothing more will run. */
    var cancelled = false
        private set

    /** Done or cancelled: the driver has nothing more to do. */
    val finished: Boolean get() = done || cancelled

    /** How many waits gave up, and the work that threw (the plan carried on without it). */
    var timeouts = 0
        private set
    val failures = ArrayList<Exception>()

    /** The share of the plan's weight that has finished, 0 to 1. Never goes down. */
    val progress: Float
        get() = if (plan.totalWeight <= 0f) 1f else (doneWeight / plan.totalWeight).toFloat().coerceIn(0f, 1f)

    /** What the loading screen should call the current stage: the step about to run, else the last one. */
    val label: String
        get() = steps.getOrNull(index)?.name ?: steps.lastOrNull()?.name ?: ""

    /** Stops the plan for good (the screen went away). A step already running can't be interrupted; none starts after this. */
    fun cancel() {
        cancelled = true
    }

    /**
     * Runs steps for about [budgetNs] nanoseconds of this driver's clock. Returns whether the plan
     * is done. Safe to call again after it is done or cancelled (it does nothing).
     */
    fun advance(budgetNs: Long): Boolean {
        if (cancelled || done) return done
        val sliceStart = clock()
        var ran = 0
        while (!cancelled && index < steps.size) {
            when (val step = steps[index]) {
                is LoadStep.Work -> {
                    if (ran > 0) {
                        val expected = (step.weight * nsPerWeight).toLong()
                        if (clock() - sliceStart + expected > budgetNs) break
                    }
                    val t0 = clock()
                    var failure: Exception? = null
                    try {
                        step.run()
                    } catch (e: Exception) {
                        // A stage that fails to preload will build itself when first used, as it did
                        // before there was a loading plan: carry on rather than block the whole start.
                        failure = e
                        failures += e
                    }
                    val dt = (clock() - t0).coerceAtLeast(0L)
                    // Weigh recent steps more, so the guess follows the phone it is running on.
                    nsPerWeight = nsPerWeight * 0.6 + (dt / step.weight.toDouble()).coerceAtLeast(1.0) * 0.4
                    finishStep(step, dt, failure)
                    ran++
                }
                is LoadStep.Wait -> {
                    val now = clock()
                    if (waitStartNs < 0L) {
                        waitStartNs = now
                        step.start()
                    }
                    if (step.ready()) {
                        finishStep(step, clock() - waitStartNs, null)
                    } else if (clock() - waitStartNs >= step.timeoutMs * 1_000_000L) {
                        timeouts++
                        listener?.onTimeout(step)
                        step.onTimeout()
                        finishStep(step, clock() - waitStartNs, null)
                    } else {
                        // Still waiting: nothing further can run this slice.
                        break
                    }
                }
            }
            if (clock() - sliceStart >= budgetNs) break
        }
        return done
    }

    /** Runs every remaining step now, however long it takes (waits included, up to their timeouts). */
    fun runToEnd() {
        while (!finished) advance(Long.MAX_VALUE / 4)
    }

    private fun finishStep(step: LoadStep, ns: Long, failure: Exception?) {
        doneWeight += step.weight
        val i = index
        index++
        waitStartNs = -1L
        listener?.onStepDone(step, i, ns, failure)
    }
}
