package com.pocketarcade.ui

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The title-to-hall handoff: its look as a function of progress, and its sequencing. */
class HandoffTest {
    /** Frames come as fast as the coroutines ask for them, 16.7 ms of animation time apiece. */
    private class Frames : MonotonicFrameClock {
        var now = 0L
        var count = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            now += 16_666_667L
            count++
            return onFrame(now)
        }
    }

    /** "Remove animations": every animateTo lands on its target on the first frame. */
    private class NoAnimations : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    // ---------------------------------------------------------------- the look

    @Test
    fun theTitleDipsToDarkAndTheHallComesUpOutOfIt() {
        // At rest nothing covers the title; pushed all the way in, everything is dark.
        assertEquals(0f, HandoffPlan.dark(0f, 0f, false), 0f)
        assertEquals(0f, HandoffPlan.glow(0f, 0f, false), 0f)
        assertEquals(1f, HandoffPlan.dark(1f, 0f, false), 0f)
        // The hall has just appeared: still dark, and once settled: clear.
        assertEquals(1f, HandoffPlan.dark(0f, 1f, false), 0f)
        assertEquals(0f, HandoffPlan.dark(0f, 0f, false), 0f)
    }

    @Test
    fun bothSidesOfTheHandoffAreTheSamePictureSoALoadingStepCanSitBetween() {
        // End of the exit (exit 1, entrance 0) and start of the entrance (entrance 1; exit is reset to 0).
        assertEquals(HandoffPlan.dark(1f, 0f, false), HandoffPlan.dark(0f, 1f, false), 0f)
        assertEquals(HandoffPlan.glow(1f, 0f, false), HandoffPlan.glow(0f, 1f, false), 1e-6f)
        assertEquals(HandoffPlan.GLOW_PEAK, HandoffPlan.glow(1f, 0f, false), 1e-6f)
        // The calm crossfade too.
        assertEquals(HandoffPlan.dark(1f, 0f, true), HandoffPlan.dark(0f, 1f, true), 0f)
    }

    @Test
    fun theExitOnlyDarkensAndTheEntranceOnlyLightens() {
        var lastDark = 0f
        var lastGlow = 0f
        for (i in 0..200) {
            val e = i / 200f
            val d = HandoffPlan.dark(e, 0f, false)
            val g = HandoffPlan.glow(e, 0f, false)
            assertTrue(d >= lastDark - 1e-6f && d in 0f..1f)
            assertTrue(g >= lastGlow - 1e-6f && g in 0f..HandoffPlan.GLOW_PEAK + 1e-6f)
            lastDark = d
            lastGlow = g
        }
        lastDark = 1f
        lastGlow = HandoffPlan.GLOW_PEAK
        for (i in 200 downTo 1) {
            val en = i / 200f
            val d = HandoffPlan.dark(0f, en, false)
            val g = HandoffPlan.glow(0f, en, false)
            assertTrue("dark $d after $lastDark", d <= lastDark + 1e-6f)
            assertTrue("glow $g after $lastGlow", g <= lastGlow + 1e-6f)
            lastDark = d
            lastGlow = g
        }
    }

    @Test
    fun theLightIsUpBeforeTheDarknessCloses() {
        // Half way through the exit the doorway is glowing but the picture isn't yet covered.
        assertTrue(HandoffPlan.glow(0.5f, 0f, false) > 0.2f)
        assertEquals(0f, HandoffPlan.dark(HandoffPlan.DARK_FROM, 0f, false), 0f)
        // And coming in, the dark lifts before the light has finished draining.
        val p = HandoffPlan.ENTRANCE_GLOW_FADE
        assertTrue(HandoffPlan.dark(0f, 1f - p, false) < HandoffPlan.dark(0f, 1f, false))
    }

    @Test
    fun aCalmHandoffIsAPlainCrossfadeThroughDark() {
        for (i in 0..20) {
            assertEquals(0f, HandoffPlan.glow(i / 20f, 0f, true), 0f)
            assertEquals(0f, HandoffPlan.glow(0f, i / 20f, true), 0f)
        }
        // It darkens from the first moment (no light first) and is quicker than the full version.
        assertTrue(HandoffPlan.dark(0.3f, 0f, true) > 0f)
        assertTrue(HandoffPlan.CALM_EXIT_MS < HandoffPlan.EXIT_MS)
        assertTrue(HandoffPlan.CALM_ENTRANCE_MS < HandoffPlan.ENTRANCE_MS)
    }

    // ---------------------------------------------------------------- the sequence

    private class Log {
        val events = ArrayList<String>()
        val camera = ArrayList<Float>()
    }

    private fun play(calm: Boolean, animationsOff: Boolean, frames: Frames = Frames()): Triple<TitleHandoff, Log, Frames> {
        val handoff = TitleHandoff()
        val log = Log()
        val context = if (animationsOff) frames + NoAnimations() else frames
        runBlocking {
            launch(context) {
                handoff.run(
                    calm = calm,
                    gate = {
                        log.events += "gate exit=${handoff.exit.value}"
                        yield()
                    },
                    enterHall = { log.events += "enter hud=${handoff.hud.value} exit=${handoff.exit.value}" },
                    onCamera = { log.camera += it },
                )
                log.events += "done"
            }.join()
        }
        return Triple(handoff, log, frames)
    }

    @Test
    fun theTitleExitsFullyThenTheGateRunsThenTheHallIsEntered() {
        val (handoff, log, _) = play(calm = false, animationsOff = false)
        // The gate sees the title pushed all the way in; the hall is entered with the HUD hidden.
        assertEquals(listOf("gate exit=1.0", "enter hud=0.0 exit=1.0", "done"), log.events)
        assertEquals(0f, handoff.entrance.value, 0f)
        assertEquals(0f, handoff.exit.value, 0f)
    }

    @Test
    fun theHallCameraStartsPulledBackAndSettlesToExactlyRest() {
        val (_, log, _) = play(calm = false, animationsOff = false)
        assertEquals(1f, log.camera.first(), 0f)
        assertEquals(0f, log.camera.last(), 0f)
        assertTrue("many frames of easing", log.camera.size > 20)
        // Never goes backwards once it starts settling.
        val settling = log.camera.drop(1)
        for (i in 1 until settling.size) assertTrue(settling[i] <= settling[i - 1] + 1e-6f)
        assertTrue(log.camera.all { it in 0f..1f })
    }

    @Test
    fun theWholeHandoffTakesAboutTwoSeconds() {
        val (_, _, frames) = play(calm = false, animationsOff = false)
        val seconds = frames.now / 1e9f
        val expected = (HandoffPlan.EXIT_MS + HandoffPlan.ENTRANCE_MS) / 1000f
        assertTrue("$seconds s vs $expected s", seconds in expected..expected + 0.4f)
    }

    @Test
    fun theEntranceWaitsForTheHallsFirstFramesBeforeItsClockStarts() {
        val frames = Frames()
        val handoff = TitleHandoff()
        var framesAtEnter = -1
        var entranceWhenEntered = -1f
        runBlocking {
            launch(frames) {
                handoff.run(calm = false, enterHall = {
                    framesAtEnter = frames.count
                    entranceWhenEntered = handoff.entrance.value
                })
            }.join()
        }
        // Entering the hall doesn't start the entrance: it is still 0 (cover held by the exit), and
        // frames keep coming (the settle wait) before it begins.
        assertEquals(0f, entranceWhenEntered, 0f)
        assertTrue(frames.count >= framesAtEnter + HandoffPlan.SETTLE_FRAMES)
    }

    @Test
    fun withAnimationsSwitchedOffTheHallStillEndsUpShowing() {
        val (handoff, log, _) = play(calm = false, animationsOff = true)
        assertEquals("done", log.events.last())
        assertEquals(0f, handoff.entrance.value, 0f)
        assertEquals(0f, handoff.exit.value, 0f)
        assertEquals(0f, log.camera.last(), 0f)
    }

    @Test
    fun aCalmHandoffNeverMovesTheCamera() {
        val (handoff, log, frames) = play(calm = true, animationsOff = false)
        assertTrue("no camera pushes: ${log.camera}", log.camera.isEmpty())
        assertEquals(0f, handoff.entrance.value, 0f)
        val seconds = frames.now / 1e9f
        assertTrue("$seconds s", seconds < (HandoffPlan.CALM_EXIT_MS + HandoffPlan.CALM_ENTRANCE_MS) / 1000f + 0.3f)
    }

    @Test
    fun theInterfaceFadesInAfterwards() {
        val frames = Frames()
        val handoff = TitleHandoff()
        runBlocking {
            launch(frames) {
                handoff.run(calm = false, enterHall = {})
                assertEquals(0f, handoff.hud.value, 0f)
                handoff.fadeInHud()
            }.join()
        }
        assertEquals(1f, handoff.hud.value, 0f)
    }
}
