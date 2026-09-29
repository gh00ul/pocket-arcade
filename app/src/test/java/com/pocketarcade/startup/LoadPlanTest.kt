package com.pocketarcade.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The staged loader: budgets, monotone progress, every step exactly once, waits, failures and cancel. */
class LoadPlanTest {
    /** A clock the steps move themselves: each takes [msPerWeight] ms of it per unit of weight. */
    private class FakeClock(var ns: Long = 0L) {
        fun read() = ns
        fun spendMs(ms: Double) {
            ns += (ms * 1_000_000.0).toLong()
        }
    }

    private val ms = 1_000_000L

    /** [n] steps of the given weight that each cost [msPerWeight] ms per weight and count their runs. */
    private fun plan(clock: FakeClock, n: Int, weight: Float = 1f, msPerWeight: Double = 1.0, runs: IntArray = IntArray(n)): LoadPlan =
        LoadPlan(List(n) { i -> LoadStep.Work("step $i", weight) { runs[i]++; clock.spendMs(weight * msPerWeight) } })

    @Test
    fun everyStepRunsExactlyOnce() {
        val clock = FakeClock()
        val runs = IntArray(25)
        val driver = LoadDriver(plan(clock, 25, runs = runs), clock::read)
        var slices = 0
        while (!driver.advance(4 * ms)) slices++
        assertTrue(driver.done)
        assertEquals(25, driver.stepsDone)
        assertTrue("every step ran once", runs.all { it == 1 })
        // Asking again after the end runs nothing and stays done.
        assertTrue(driver.advance(4 * ms))
        assertTrue(runs.all { it == 1 })
        assertTrue("it took several slices, not one", slices > 3)
    }

    @Test
    fun aSliceStaysWithinItsBudgetWhenTheStepsAreEvenlySized() {
        val clock = FakeClock()
        val driver = LoadDriver(plan(clock, 60), clock::read)
        val budget = 5 * ms
        var worst = 0L
        var slices = 0
        while (!driver.done) {
            val t0 = clock.ns
            driver.advance(budget)
            worst = maxOf(worst, clock.ns - t0)
            slices++
        }
        // The first slice has only the initial guess to go on: it may run one step more than it should.
        assertTrue("no slice ran longer than the budget plus one step (worst ${worst / ms} ms)", worst <= budget + 1 * ms)
        assertTrue(slices >= 60 / 6)
    }

    @Test
    fun aHeavyStepIsNotStartedBehindLightOnesThatHaveUsedTheBudget() {
        val clock = FakeClock()
        val order = ArrayList<String>()
        val steps = ArrayList<LoadStep>()
        repeat(4) { i -> steps += LoadStep.Work("light $i", 1f) { order += "light $i"; clock.spendMs(1.0) } }
        steps += LoadStep.Work("heavy", 8f) { order += "heavy"; clock.spendMs(8.0) }
        val driver = LoadDriver(LoadPlan(steps), clock::read, initialNsPerWeight = 1 * ms)
        // 6 ms: the four light steps use 4, and 4 + the 8 the heavy one is expected to take is too much.
        driver.advance(6 * ms)
        assertEquals(listOf("light 0", "light 1", "light 2", "light 3"), order)
        assertFalse(driver.done)
        // A slice of its own runs it: the first step of a slice always goes, whatever it costs.
        driver.advance(6 * ms)
        assertEquals("heavy", order.last())
        assertTrue(driver.done)
    }

    @Test
    fun anyBudgetMakesProgress() {
        val clock = FakeClock()
        val driver = LoadDriver(plan(clock, 5, weight = 3f, msPerWeight = 10.0), clock::read)
        var guard = 0
        while (!driver.advance(0L)) assertTrue("stuck", guard++ < 10)
        assertEquals(5, driver.stepsDone)
    }

    @Test
    fun progressOnlyMovesUpAndReachesOne() {
        val clock = FakeClock()
        val steps = List(12) { i -> LoadStep.Work("s$i", (1 + i % 4).toFloat()) { clock.spendMs(1.0 + i % 3) } }
        val driver = LoadDriver(LoadPlan(steps), clock::read)
        var last = driver.progress
        assertEquals(0f, last, 0f)
        while (!driver.done) {
            driver.advance(3 * ms)
            assertTrue("progress fell from $last to ${driver.progress}", driver.progress >= last)
            last = driver.progress
        }
        assertEquals(1f, driver.progress, 1e-6f)
    }

    @Test
    fun progressFollowsTheWeights() {
        val clock = FakeClock()
        val steps = listOf(
            LoadStep.Work("small", 1f) { clock.spendMs(1.0) },
            LoadStep.Work("big", 3f) { clock.spendMs(3.0) },
        )
        val driver = LoadDriver(LoadPlan(steps), clock::read)
        driver.advance(1 * ms)
        assertEquals(0.25f, driver.progress, 1e-6f)
        assertEquals("big", driver.label)
        driver.advance(10 * ms)
        assertEquals(1f, driver.progress, 1e-6f)
        assertEquals("the last stage stays named once done", "big", driver.label)
    }

    @Test
    fun anEmptyPlanIsDoneAtOnce() {
        val driver = LoadDriver(LoadPlan.EMPTY, { 0L })
        assertTrue(driver.done)
        assertEquals(1f, driver.progress, 0f)
        assertTrue(driver.advance(1L))
    }

    @Test
    fun cancelStopsWhatHasNotStarted() {
        val clock = FakeClock()
        val runs = IntArray(10)
        val driver = LoadDriver(plan(clock, 10, runs = runs), clock::read)
        driver.advance(3 * ms)
        val before = runs.sum()
        assertTrue(before in 1..9)
        driver.cancel()
        assertFalse(driver.advance(100 * ms))
        assertEquals("nothing runs after a cancel", before, runs.sum())
        assertTrue(driver.cancelled)
        assertTrue(driver.finished)
        assertFalse("a cancelled plan is not a finished load", driver.done)
    }

    @Test
    fun aStepCanCancelTheRestOfThePlan() {
        val clock = FakeClock()
        lateinit var driver: LoadDriver
        var later = 0
        val steps = listOf(
            LoadStep.Work("first", 1f) { clock.spendMs(1.0) },
            LoadStep.Work("stop", 1f) { driver.cancel() },
            LoadStep.Work("never", 1f) { later++ },
        )
        driver = LoadDriver(LoadPlan(steps), clock::read)
        driver.advance(100 * ms)
        assertEquals(0, later)
        assertTrue(driver.cancelled)
    }

    @Test
    fun aFailingStepIsRecordedAndTheRestStillRun() {
        val clock = FakeClock()
        var after = 0
        val steps = listOf(
            LoadStep.Work("bad", 1f) { throw IllegalStateException("no such font") },
            LoadStep.Work("good", 1f) { after++ },
        )
        val seen = ArrayList<Exception?>()
        val listener = object : LoadListener {
            override fun onStepDone(step: LoadStep, index: Int, ns: Long, failure: Exception?) {
                seen += failure
            }
        }
        val driver = LoadDriver(LoadPlan(steps), clock::read, listener)
        driver.runToEnd()
        assertTrue(driver.done)
        assertEquals(1, after)
        assertEquals(1, driver.failures.size)
        assertEquals(2, seen.size)
        assertTrue(seen[0] is IllegalStateException)
        assertEquals(null, seen[1])
        assertEquals(1f, driver.progress, 1e-6f)
    }

    @Test
    fun aWaitHoldsTheSliceUntilItIsReady() {
        val clock = FakeClock()
        var started = 0
        var ready = false
        var after = 0
        val steps = listOf(
            LoadStep.Wait("gpu", 2f, timeoutMs = 1000, start = { started++ }, ready = { ready }),
            LoadStep.Work("after", 1f) { after++ },
        )
        val driver = LoadDriver(LoadPlan(steps), clock::read)
        driver.advance(5 * ms)
        driver.advance(5 * ms)
        driver.advance(5 * ms)
        assertEquals("started once however often it is polled", 1, started)
        assertEquals(0, after)
        assertEquals(0f, driver.progress, 0f)
        ready = true
        driver.advance(5 * ms)
        assertEquals(1, after)
        assertTrue(driver.done)
        assertEquals(0, driver.timeouts)
    }

    @Test
    fun aWaitThatNeverAnswersGivesUpAtItsTimeout() {
        val clock = FakeClock()
        var timedOut = 0
        var after = 0
        val steps = listOf(
            LoadStep.Wait("gpu", 1f, timeoutMs = 500, onTimeout = { timedOut++ }, ready = { false }),
            LoadStep.Work("after", 1f) { after++ },
        )
        val told = ArrayList<String>()
        val driver = LoadDriver(
            LoadPlan(steps), clock::read,
            object : LoadListener {
                override fun onTimeout(step: LoadStep.Wait) {
                    told += step.name
                }
            },
        )
        driver.advance(5 * ms)
        assertFalse(driver.done)
        clock.spendMs(499.0)
        driver.advance(5 * ms)
        assertFalse("not yet at the timeout", driver.done)
        clock.spendMs(2.0)
        driver.advance(5 * ms)
        assertTrue(driver.done)
        assertEquals(1, timedOut)
        assertEquals(1, after)
        assertEquals(1, driver.timeouts)
        assertEquals(listOf("gpu"), told)
    }

    @Test
    fun theTimeoutRunsFromWhenTheWaitStartedNotFromThePlan() {
        val clock = FakeClock()
        val steps = listOf(
            LoadStep.Work("slow", 1f) { clock.spendMs(2000.0) },
            LoadStep.Wait("gpu", 1f, timeoutMs = 500, ready = { false }),
        )
        val driver = LoadDriver(LoadPlan(steps), clock::read)
        driver.advance(1 * ms)
        // 2 s went on the slow step, which must not count against the wait.
        driver.advance(1 * ms)
        assertFalse(driver.done)
        assertEquals(0, driver.timeouts)
    }

    @Test
    fun theDriverLearnsWhatAStepCostsOnThisPhone() {
        val clock = FakeClock()
        // Steps really take 4 ms each; the first guess says 1 ms. After the first slice the guess is right.
        val driver = LoadDriver(plan(clock, 40, msPerWeight = 4.0), clock::read, initialNsPerWeight = 1 * ms)
        var worst = 0L
        var slices = 0
        while (!driver.done) {
            val t0 = clock.ns
            driver.advance(9 * ms)
            if (slices > 2) worst = maxOf(worst, clock.ns - t0)
            slices++
        }
        assertTrue("once it has learnt, slices stay within the budget (worst ${worst / ms} ms)", worst <= 9 * ms)
    }

    @Test
    fun plansJoinInOrder() {
        val a = LoadPlan(listOf(LoadStep.Work("a", 1f) {}))
        val b = LoadPlan(listOf(LoadStep.Work("b", 2f) {}))
        val both = a + b
        assertEquals(listOf("a", "b"), both.steps.map { it.name })
        assertEquals(3f, both.totalWeight, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStepNeedsAPositiveWeight() {
        LoadStep.Work("free", 0f) {}
    }

    @Test
    fun stageClockReportsGapsAndSpans() {
        var t = 1000L
        val lines = ArrayList<String>()
        val clock = StageClock({ t }, { lines += it }, origin = 400L)
        t = 1250L
        clock.mark("composed")
        t = 1300L
        clock.begin("tap to hall")
        t = 1600L
        assertTrue(clock.isOpen("tap to hall"))
        assertEquals(300L, clock.end("tap to hall"))
        assertFalse(clock.isOpen("tap to hall"))
        assertEquals("never begun", -1L, clock.end("tap to hall"))
        assertEquals(listOf("composed: +850 ms (850 ms in)", "tap to hall: 300 ms"), lines)
    }
}
