package com.pocketarcade.startup

import com.pocketarcade.engine.gl.Warmup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The GPU warm-up queue: one request at a time, progress, and giving up on a GPU that stops answering. */
class WarmRunTest {
    private var clock = 0L
    private val sent = ArrayList<Int>()
    private val tickets = ArrayList<Warmup.Ticket>()

    private fun run(count: Int, stallMs: Long = 1000, onStall: () -> Unit = {}) = WarmRun(
        count,
        { i ->
            sent += i
            Warmup.Ticket().also { tickets += it }
        },
        stallMs, { clock }, onStall, depth = { 1 },
    )

    @After
    fun resetWarmup() {
        // Warmup is process-wide: leave it as the next test found it.
        Warmup::class.java.getDeclaredField("disabled").apply { isAccessible = true }.setBoolean(Warmup, false)
    }

    @Test
    fun sendsOneJobAtATimeAndFinishesWhenTheGpuHasAnsweredAll() {
        val r = run(3)
        assertEquals(0f, r.fraction, 0f)
        assertFalse(r.poll())
        assertEquals("only the first job is out", listOf(0), sent)
        assertFalse("still waiting for it", r.poll())
        assertEquals(listOf(0), sent)
        tickets[0].done = true
        assertFalse(r.poll())
        assertEquals(listOf(0, 1), sent)
        assertEquals(1 / 3f, r.fraction, 1e-6f)
        tickets[1].done = true
        assertFalse(r.poll())
        tickets[2].done = true
        assertTrue(r.poll())
        assertEquals(1f, r.fraction, 0f)
        assertEquals(listOf(0, 1, 2), sent)
    }

    @Test
    fun keepsSeveralInFlightWhenLoadingHasTheScreenToItself() {
        val r = WarmRun(
            5,
            { i -> sent += i; Warmup.Ticket().also { tickets += it } },
            1000, { clock }, {}, depth = { 3 },
        )
        assertFalse(r.poll())
        assertEquals("three out at once", listOf(0, 1, 2), sent)
        assertFalse(r.poll())
        assertEquals(listOf(0, 1, 2), sent)
        tickets[0].done = true
        assertFalse(r.poll())
        assertEquals("one came back, one more goes", listOf(0, 1, 2, 3), sent)
        assertEquals(1 / 5f, r.fraction, 1e-6f)
        // Answers arrive in the order sent; a later one alone doesn't count until the ones before it are in.
        tickets[3].done = true
        assertFalse(r.poll())
        assertEquals(1 / 5f, r.fraction, 1e-6f)
        tickets[1].done = true
        tickets[2].done = true
        assertFalse(r.poll())
        assertEquals(listOf(0, 1, 2, 3, 4), sent)
        tickets[4].done = true
        assertTrue(r.poll())
        assertEquals(1f, r.fraction, 0f)
    }

    @Test
    fun oneAtATimeWhileTheTitleIsBeingWatched() {
        var loading = false
        val r = WarmRun(
            4,
            { i -> sent += i; Warmup.Ticket().also { tickets += it } },
            1000, { clock }, {}, depth = { if (loading) 3 else 1 },
        )
        assertFalse(r.poll())
        assertEquals(listOf(0), sent)
        loading = true
        assertFalse(r.poll())
        assertEquals("the loading screen went up: three in flight", listOf(0, 1, 2), sent)
    }

    @Test
    fun theStallClockRunsFromTheLastAnswerEvenWithSeveralOut() {
        var stalled = 0
        val r = WarmRun(6, { i -> sent += i; Warmup.Ticket().also { tickets += it } }, 500, { clock }, { stalled++ }, depth = { 3 })
        r.poll()
        clock += 400
        tickets[0].done = true
        assertFalse(r.poll())
        clock += 400
        assertFalse("an answer 400 ms ago is progress", r.poll())
        clock += 200
        assertTrue(r.poll())
        assertEquals(1, stalled)
    }

    @Test
    fun anEmptyQueueIsDoneAtOnce() {
        val r = run(0)
        assertEquals(1f, r.fraction, 0f)
        assertTrue(r.poll())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aGpuThatStopsAnsweringIsGivenUpOn() {
        var stalled = 0
        val r = run(4, stallMs = 500, onStall = { stalled++ })
        assertFalse(r.poll())
        clock += 400
        assertFalse(r.poll())
        clock += 200
        assertTrue("nothing back for longer than the stall time: done, whatever is left", r.poll())
        assertEquals(1, stalled)
        assertTrue("later warm-ups are answered at once", Warmup.disabled)
        assertEquals(listOf(0), sent)
    }

    @Test
    fun progressResetsTheStallClockEachTimeAJobLands() {
        val r = run(3, stallMs = 500)
        r.poll()
        clock += 400
        tickets[0].done = true
        assertFalse(r.poll())
        clock += 400
        assertFalse("the second job has only been out 400 ms", r.poll())
        assertFalse(Warmup.disabled)
    }

    @Test
    fun jobsAnsweredAtOnceRunStraightThrough() {
        // A GPU that is switched off answers every request with a finished ticket.
        val done = ArrayList<Int>()
        val r = WarmRun(3, { i -> done += i; Warmup.Ticket(done = true) }, 1000, { clock }, depth = { 1 })
        assertTrue(r.poll())
        assertEquals(listOf(0, 1, 2), done)
    }
}
