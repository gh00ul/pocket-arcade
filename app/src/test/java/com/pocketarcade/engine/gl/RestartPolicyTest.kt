package com.pocketarcade.engine.gl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartPolicyTest {
    @Test
    fun threeRestartsAreAllowedThenItGivesUp() {
        val p = RestartPolicy()
        assertTrue(p.shouldRestart(0))
        assertTrue(p.shouldRestart(1_000))
        assertTrue(p.shouldRestart(2_000))
        assertFalse(p.gaveUp)
        assertFalse(p.shouldRestart(3_000))
        assertTrue(p.gaveUp)
        // Once refused it stays refused, even if a long quiet spell has passed.
        assertFalse(p.shouldRestart(1_000_000))
    }

    @Test
    fun failuresSpreadOutOverTimeNeverAddUp() {
        val p = RestartPolicy()
        // One failure every 30 s: never more than two inside a 60 s window.
        for (k in 0 until 50) assertTrue("failure $k", p.shouldRestart(k * 30_000L))
        assertFalse(p.gaveUp)
    }

    @Test
    fun theWindowSlidesRatherThanResets() {
        val p = RestartPolicy()
        assertTrue(p.shouldRestart(0))
        assertTrue(p.shouldRestart(20_000))
        assertTrue(p.shouldRestart(40_000))
        // At 61 s the first restart has left the window, so there is room for one more...
        assertTrue(p.shouldRestart(61_000))
        // ...but not two: 20 s, 40 s and 61 s are all still inside it at 62 s.
        assertFalse(p.shouldRestart(62_000))
    }

    @Test
    fun aFatalFailureNeverRestarts() {
        val p = RestartPolicy()
        assertFalse(p.shouldRestart(0, fatal = true))
        assertTrue(p.gaveUp)
        assertFalse(p.shouldRestart(500_000))
    }

    @Test
    fun zeroRestartsMeansFirstFailureIsFinal() {
        val p = RestartPolicy(maxRestarts = 0)
        assertFalse(p.shouldRestart(0))
    }
}
