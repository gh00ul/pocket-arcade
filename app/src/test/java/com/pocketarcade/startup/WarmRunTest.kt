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
        stallMs, { clock }, onStall,
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
        val r = WarmRun(3, { i -> done += i; Warmup.Ticket(done = true) }, 1000, { clock })
        assertTrue(r.poll())
        assertEquals(listOf(0, 1, 2), done)
    }
}
