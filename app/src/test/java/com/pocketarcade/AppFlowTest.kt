package com.pocketarcade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

/**
 * Three flaws in the app-flow coroutines of ArcadeApp.kt and GameHostScreen.kt, replayed on
 * copies of their sequences (they are local functions of composables, out of a unit test's
 * reach). Each flaw has a test on the code as it is today, ignored because it fails, and a test
 * on the copy with the proposed patch, which passes.
 */
class AppFlowTest {
    /** Frames come as fast as the coroutines ask for them, 16.7 ms of animation time apiece. */
    private class Frames : MonotonicFrameClock {
        var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            now += 16_666_667L
            return onFrame(now)
        }
    }

    /** "Remove animations" (or the developer option): every animateTo lands on its target on the first frame. */
    private class NoAnimations : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    // ------------------------------------------------ 1. the dive in, with animations off

    /** ArcadeApp.enterMachine after the token is spent, as it is: a delayed fade-in child job. */
    private suspend fun CoroutineScope.diveInAsItIs(dive: Animatable<Float, *>, fade: Animatable<Float, *>) {
        launch {
            delay(200)
            fade.animateTo(1f, tween(560, easing = FastOutLinearInEasing))
        }
        dive.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(350))
    }

    /** The same with the patch: the fade-in never outlives the dive. */
    private suspend fun CoroutineScope.diveInPatched(dive: Animatable<Float, *>, fade: Animatable<Float, *>) {
        val fadeIn = launch {
            delay(200)
            fade.animateTo(1f, tween(560, easing = FastOutLinearInEasing))
        }
        dive.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        fadeIn.cancelAndJoin()
        fade.animateTo(0f, tween(350))
    }

    private fun fadeAfterDiveIn(patched: Boolean): Float = runBlocking<Float> {
        val dive = Animatable(0f)
        val fade = Animatable(0f)
        launch(Frames() + NoAnimations()) {
            if (patched) diveInPatched(dive, fade) else diveInAsItIs(dive, fade)
        }.join()
        fade.value
    }

    @Ignore("ArcadeApp.enterMachine: with animations off the delayed fade-in lands after the fade-out and the screen stays black")
    @Test
    fun withAnimationsOffTheGameShowsAfterTheDiveIn() {
        assertEquals(0f, fadeAfterDiveIn(patched = false), 0f)
    }

    @Test
    fun withAnimationsOffThePatchedDiveInLeavesTheGameShowing() {
        assertEquals(0f, fadeAfterDiveIn(patched = true), 0f)
    }

    // ------------------------------------------------ 2. leaving a game twice

    private fun refundsAfterTwoTaps(patched: Boolean): Int = runBlocking<Int> {
        var busy = false
        var refunds = 0
        val jobs = ArrayList<Job>()
        // The DataStore write suspends; scope.launch doesn't start its body until the caller returns.
        suspend fun refundToken() {
            yield()
            refunds++
        }
        fun exitGame(refund: Boolean) {
            if (busy) return
            if (patched) busy = true
            jobs += launch {
                try {
                    busy = true
                    if (refund) refundToken()
                } finally {
                    busy = false
                }
            }
        }
        exitGame(true)
        exitGame(true)
        jobs.joinAll()
        refunds
    }

    @Ignore("ArcadeApp.exitGame claims busy inside the launched coroutine, so two calls in one turn both refund the token")
    @Test
    fun tappingCloseTwiceRefundsTheTokenOnce() {
        assertEquals(1, refundsAfterTwoTaps(patched = false))
    }

    @Test
    fun withTheBusyFlagClaimedFirstTappingCloseTwiceRefundsTheTokenOnce() {
        assertEquals(1, refundsAfterTwoTaps(patched = true))
    }

    // ------------------------------------------------ 3. PLAY AGAIN twice

    private fun tokensSpentByTwoTaps(patched: Boolean): Int = runBlocking<Int> {
        var tokens = 5
        var showingButton = true
        var starting = false
        val jobs = ArrayList<Job>()
        suspend fun spendToken(): Boolean {
            yield()
            return if (tokens > 0) { tokens--; true } else false
        }
        fun playAgain() {
            if (patched) {
                if (starting) return
                starting = true
            }
            jobs += launch {
                try {
                    if (spendToken()) showingButton = false
                } finally {
                    starting = false
                }
            }
        }
        // The button stays on screen until the spend has come back and the phase has changed.
        if (showingButton) playAgain()
        if (showingButton) playAgain()
        jobs.joinAll()
        5 - tokens
    }

    @Ignore("GameHostScreen's PLAY AGAIN spends a token per tap until the first spend has returned")
    @Test
    fun tappingPlayAgainTwiceSpendsOneToken() {
        assertEquals(1, tokensSpentByTwoTaps(patched = false))
    }

    @Test
    fun withAStartingGuardTappingPlayAgainTwiceSpendsOneToken() {
        assertEquals(1, tokensSpentByTwoTaps(patched = true))
    }
}
