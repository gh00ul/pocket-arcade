package com.pocketarcade.startup

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loading gate's two plans on a frame clock that ticks as fast as the coroutine asks for frames. */
class StartupGateTest {
    private class Frames : MonotonicFrameClock {
        var now = 0L
        var count = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            now += FRAME_NS
            count++
            return onFrame(now)
        }
    }

    private companion object {
        const val FRAME_NS = 16_666_667L
    }

    private fun steps(prefix: String, n: Int, weight: Float = 1f, log: MutableList<String>) =
        LoadPlan(List(n) { i -> LoadStep.Work("$prefix $i", weight) { log += "$prefix $i" } })

    private fun gate(boot: LoadPlan, hall: () -> LoadPlan) = StartupGate(boot, hall, vsyncNs = { FRAME_NS })

    /** Runs [g] to the end on a fake frame clock; [urgent] may look at the frame count. */
    private fun runGate(g: StartupGate, frames: Frames = Frames(), urgent: (Frames) -> Boolean = { true }) {
        runBlocking {
            launch(frames) { g.run { urgent(frames) } }.join()
        }
    }

    @Test
    fun theTitleComesFirstThenTheHall() {
        val log = ArrayList<String>()
        lateinit var g: StartupGate
        val bootSeen = ArrayList<Boolean>()
        val boot = LoadPlan(List(3) { i -> LoadStep.Work("boot $i", 1f) { bootSeen += g.bootDone; log += "boot $i" } })
        val hallSeen = ArrayList<Boolean>()
        val hall = LoadPlan(List(4) { i -> LoadStep.Work("hall $i", 1f) { hallSeen += g.bootDone; log += "hall $i" } })
        g = gate(boot) { hall }
        assertFalse(g.bootDone)
        assertFalse(g.hallReady)
        runGate(g)
        assertTrue(g.bootDone)
        assertTrue(g.hallReady)
        assertEquals(listOf("boot 0", "boot 1", "boot 2", "hall 0", "hall 1", "hall 2", "hall 3"), log)
        assertTrue("the title isn't shown while its own plan still runs", bootSeen.none { it })
        assertTrue("the hall plan only starts once the title can show", hallSeen.all { it })
        assertEquals(1f, g.progress, 0f)
    }

    @Test
    fun progressOfEachPlanStartsOverAndOnlyRises() {
        val log = ArrayList<String>()
        lateinit var g: StartupGate
        val seen = ArrayList<Pair<Boolean, Float>>()
        val boot = LoadPlan(List(8) { i -> LoadStep.Work("boot $i", 1f) { seen += g.bootDone to g.progress } })
        val hall = LoadPlan(List(8) { i -> LoadStep.Work("hall $i", 1f) { seen += g.bootDone to g.progress } })
        g = gate(boot) { hall }
        runGate(g)
        for (plan in listOf(false, true)) {
            val values = seen.filter { it.first == plan }.map { it.second }
            for (i in 1 until values.size) assertTrue("progress fell in the ${if (plan) "hall" else "boot"} plan", values[i] >= values[i - 1])
        }
        assertTrue("the hall plan's bar starts again from the start", seen.first { it.first }.second <= 0.3f)
    }

    @Test
    fun theHallWaitsQuietlyBehindTheTitleUntilItsFirstMomentsHavePlayed() {
        val log = ArrayList<String>()
        val frames = Frames()
        var hallStartedAt = -1
        val boot = steps("boot", 1, log = log)
        val hall = LoadPlan(List(3) { i -> LoadStep.Work("hall $i", 1f) { if (hallStartedAt < 0) hallStartedAt = frames.count; log += "hall $i" } })
        val g = gate(boot) { hall }
        runGate(g, frames, urgent = { false })
        assertTrue(g.hallReady)
        val quietFrames = (StartupGate.TITLE_QUIET_NS / FRAME_NS).toInt()
        assertTrue("the hall began after ${hallStartedAt} frames, the title's quiet time is $quietFrames", hallStartedAt >= quietFrames)
    }

    @Test
    fun aTapDuringTheQuietTimeStartsTheHallAtOnce() {
        val log = ArrayList<String>()
        val frames = Frames()
        var hallStartedAt = -1
        val boot = steps("boot", 1, log = log)
        val hall = LoadPlan(List(3) { i -> LoadStep.Work("hall $i", 1f) { if (hallStartedAt < 0) hallStartedAt = frames.count; log += "hall $i" } })
        val g = gate(boot) { hall }
        // Tapped on frame 10: the quiet time (54 frames) is cut short.
        runGate(g, frames, urgent = { it.count >= 10 })
        assertTrue(g.hallReady)
        assertTrue("started on frame $hallStartedAt", hallStartedAt in 10..14)
    }

    @Test
    fun aStepTooBigForATitleFrameStillGetsItsTurn() {
        val log = ArrayList<String>()
        val frames = Frames()
        var ranAt = -1
        val boot = steps("boot", 1, log = log)
        // Expected to take 150 ms: far more than the slack of a title frame, so only a forced slice runs it.
        val hall = LoadPlan(listOf(LoadStep.Work("heavy", 100f) { ranAt = frames.count }))
        val g = gate(boot) { hall }
        runGate(g, frames, urgent = { false })
        assertTrue(g.hallReady)
        val quietFrames = (StartupGate.TITLE_QUIET_NS / FRAME_NS).toInt()
        assertTrue("forced after ${LoadBudget.FORCE_AFTER_FRAMES} idle frames (ran on $ranAt)", ranAt >= quietFrames + LoadBudget.FORCE_AFTER_FRAMES)
        assertTrue("but not much later (ran on $ranAt)", ranAt <= quietFrames + LoadBudget.FORCE_AFTER_FRAMES + 8)
    }

    @Test
    fun aHeavyStepRunsAtOnceOnceTheHallIsWanted() {
        val log = ArrayList<String>()
        val frames = Frames()
        var ranAt = -1
        val boot = steps("boot", 1, log = log)
        val hall = LoadPlan(listOf(LoadStep.Work("heavy", 100f) { ranAt = frames.count }))
        val g = gate(boot) { hall }
        runGate(g, frames, urgent = { true })
        assertTrue("ran on frame $ranAt", ranAt in 1..8)
    }

    @Test
    fun aPlanThatCannotBeMadeLetsTheScreensThrough() {
        val log = ArrayList<String>()
        val g = gate(steps("boot", 2, log = log)) { error("no hall today") }
        runGate(g)
        assertTrue(g.bootDone)
        assertTrue("the hall builds itself where it is drawn, rather than the loading screen staying up for ever", g.hallReady)
    }

    @Test
    fun aStepThatThrowsDoesNotStopTheStartup() {
        val log = ArrayList<String>()
        val boot = LoadPlan(listOf(LoadStep.Work("bad", 1f) { throw IllegalStateException("font missing") }, LoadStep.Work("good", 1f) { log += "good" }))
        val g = gate(boot) { steps("hall", 1, log = log) }
        runGate(g)
        assertTrue(g.hallReady)
        assertEquals(listOf("good", "hall 0"), log)
    }

    @Test
    fun theLabelNamesWhatIsBeingMade() {
        val labels = ArrayList<String>()
        lateinit var g: StartupGate
        val boot = LoadPlan(listOf(LoadStep.Work("PAINTING", 1f) { labels += g.label }, LoadStep.Work("BUILDING", 1f) { labels += g.label }))
        g = gate(boot) { LoadPlan.EMPTY }
        runGate(g)
        assertEquals("the label is set before the first step runs, then follows the plan a slice at a time", "PAINTING", labels.first())
    }
}
