package com.pocketarcade.ui

import androidx.compose.runtime.mutableFloatStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the UI's entrances and button presses. */
class EntranceTest {
    @Test
    fun aSlotOfAnEntranceClampsBeforeAndAfterItself() {
        assertEquals(0f, stageOf(0f, 0.2f, 0.6f), 0f)
        assertEquals(0f, stageOf(0.2f, 0.2f, 0.6f), 0f)
        assertEquals(0.5f, stageOf(0.4f, 0.2f, 0.6f), 1e-6f)
        assertEquals(1f, stageOf(0.6f, 0.2f, 0.6f), 0f)
        assertEquals(1f, stageOf(5f, 0.2f, 0.6f), 0f)
        assertEquals(0f, stageOf(-1f, 0.2f, 0.6f), 0f)
    }

    @Test
    fun anEmptySlotDoesNotDivideByZero() {
        val v = stageOf(0.5f, 0.5f, 0.5f)
        assertTrue(v == 0f || v == 1f)
        assertTrue(stageOf(0.9f, 0.5f, 0.5f).isFinite())
    }

    @Test
    fun panelPiecesQueueUpInOrderAndAllFinishInside() {
        val e = PanelEntrance(mutableFloatStateOf(0f))
        assertEquals(0, e.claim())
        assertEquals(1, e.claim())
        assertEquals(2, e.claim())
        var prev = -1f
        for (i in 0..40) {
            val start = e.slotStart(i)
            assertTrue("slot $i", start >= prev && start <= 0.7f)
            // Each piece takes 0.3 of the entrance, so even the last is done by the end.
            assertTrue(start + 0.3f <= 1.0001f)
            prev = start
        }
        // Early pieces are distinct steps apart, so the stagger is visible.
        assertTrue(e.slotStart(1) - e.slotStart(0) > 0.03f)
    }

    @Test
    fun aButtonSinksWhenPressedAndPopsUpPastRestOnRelease() {
        assertEquals(0f, pressOffset(10f, 0f), 0f)
        assertEquals(8f, pressOffset(10f, 1f), 1e-5f)
        // The release overshoot (press below 0) lifts the cap above its rest, a little.
        val lifted = pressOffset(10f, -0.25f)
        assertTrue(lifted < 0f && lifted > -4f)
        // Monotonic: deeper press, deeper cap.
        assertTrue(pressOffset(10f, 0.6f) > pressOffset(10f, 0.3f))
    }
}
