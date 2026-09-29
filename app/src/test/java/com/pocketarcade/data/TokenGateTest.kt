package com.pocketarcade.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guard that stops a quick double tap spending (or refunding) a token twice. */
class TokenGateTest : RepositoryTestBase() {
    @Test
    fun onlyOneClaimGetsThroughUntilItIsReleased() {
        val gate = TokenGate()
        assertFalse(gate.claimed)
        assertTrue(gate.tryClaim())
        assertTrue(gate.claimed)
        assertFalse(gate.tryClaim())
        assertFalse(gate.tryClaim())
        gate.release()
        assertFalse(gate.claimed)
        assertTrue(gate.tryClaim())
    }

    @Test
    fun releasingAnUnclaimedGateIsHarmless() {
        val gate = TokenGate()
        gate.release()
        assertTrue(gate.tryClaim())
        assertFalse(gate.tryClaim())
    }

    /**
     * Two taps land before the first coroutine has run, as on the UI thread. Without the gate both
     * spend (the bug); with it, exactly one does.
     */
    @Test
    fun twoQuickTapsSpendOneTokenThroughTheGateButTwoWithout() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tokens = 5) })
        val gate = TokenGate()
        val launched = mutableListOf<Job>()
        fun tapGated() {
            if (gate.tryClaim()) {
                launched += launch {
                    try {
                        repo.spendToken()
                    } finally {
                        gate.release()
                    }
                }
            }
        }
        // Both taps come in before either coroutine gets to run.
        tapGated()
        tapGated()
        assertEquals(1, launched.size)
        launched.joinAll()
        assertEquals(4, repo.state.first().tokens)
        // A settled spend lets the next tap through.
        assertFalse(gate.claimed)
        assertTrue(gate.tryClaim())
        gate.release()

        launched.clear()
        fun tapUngated() {
            launched += launch { repo.spendToken() }
        }
        tapUngated()
        tapUngated()
        launched.joinAll()
        assertEquals(2, repo.state.first().tokens)
    }
}
