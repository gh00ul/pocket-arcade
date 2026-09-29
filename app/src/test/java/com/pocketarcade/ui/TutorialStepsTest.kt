package com.pocketarcade.ui

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.hub.HubLayout
import com.pocketarcade.hub.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** The first-run tutorial's step machine, driven with plain snapshots of the hall. */
class TutorialStepsTest {
    private val input = TutorialInput()

    private fun Tutorial.tick(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            update(FIXED_DT, input)
            t += FIXED_DT
        }
    }

    /** Walks [dist] units along x in unit steps of one update each. */
    private fun Tutorial.walk(dist: Float, step: Float = 1f) {
        var moved = 0f
        while (moved < dist) {
            input.x += step
            moved += step
            update(FIXED_DT, input)
        }
    }

    private fun Tutorial.finishCheck() = tick(Tutorial.CHECK_SECONDS + 0.1f)

    // ---------------------------------------------------------------- policy

    @Test
    fun onlyAPlayerWhoHasNeitherSeenItNorPlayedGetsItByThemselves() {
        assertTrue(TutorialPolicy.shouldAutoStart(seen = false, totalPlays = 0))
        assertFalse(TutorialPolicy.shouldAutoStart(seen = true, totalPlays = 0))
        assertFalse(TutorialPolicy.shouldAutoStart(seen = false, totalPlays = 12))
        assertFalse(TutorialPolicy.shouldAutoStart(seen = true, totalPlays = 12))
    }

    @Test
    fun theSavedFlagIsTheSaveFormatsUnlockId() {
        // Never change it: it is what existing installs have saved.
        assertEquals("tutorial_done", TUTORIAL_DONE)
    }

    // ---------------------------------------------------------------- walking

    @Test
    fun itStartsOnTheWalkWithNothingDone() {
        val t = Tutorial()
        assertEquals(TutorialStep.WALK, t.step)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
        assertEquals(0, t.stepsDone)
        assertEquals(4, t.stepCount)
        assertFalse(t.finished)
    }

    @Test
    fun standingStillDoesNothingHoweverLong() {
        val t = Tutorial()
        t.tick(60f)
        assertEquals(TutorialStep.WALK, t.step)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
    }

    @Test
    fun walkingFarEnoughFinishesTheWalkThenShowsATickThenMovesOn() {
        val t = Tutorial()
        t.walk(Tutorial.WALK_DISTANCE - 5f)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
        t.walk(10f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        assertEquals(TutorialStep.WALK, t.step)
        assertEquals(1, t.stepsDone)
        // The tick stays a moment, then the look begins.
        t.tick(Tutorial.CHECK_SECONDS - 0.2f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        t.tick(0.4f)
        assertEquals(TutorialStep.LOOK, t.step)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
    }

    @Test
    fun aJumpIsNotWalking() {
        val t = Tutorial()
        t.update(FIXED_DT, input)
        // A decoration lands on the player and nudges them 60 across: not a walk.
        input.x += 60f
        t.update(FIXED_DT, input)
        assertEquals(0f, t.walked, 0f)
        t.walk(30f, 0.5f)
        assertEquals(30f, t.walked, 0.5f)
    }

    @Test
    fun nothingCountsWhileAPanelHasTheScreen() {
        val t = Tutorial()
        input.blocked = true
        t.walk(200f)
        assertEquals(0f, t.walked, 0f)
        assertEquals(TutorialStep.WALK, t.step)
    }

    // ---------------------------------------------------------------- looking

    private fun Tutorial.toLook() {
        walk(Tutorial.WALK_DISTANCE + 1f)
        finishCheck()
        assertEquals(TutorialStep.LOOK, step)
    }

    @Test
    fun onlyAFingerTurningTheFirstPersonViewCountsAsLooking() {
        val t = Tutorial()
        t.toLook()
        // Overhead: turning does nothing.
        input.yaw = 0f
        for (i in 0 until 200) {
            input.yaw += 0.02f
            t.update(FIXED_DT, input)
        }
        assertEquals(0f, t.looked, 0f)
        // First person but the view turning by itself (facing a machine): still nothing.
        input.firstPerson = true
        input.lookDragging = false
        for (i in 0 until 200) {
            input.yaw += 0.02f
            t.update(FIXED_DT, input)
        }
        assertEquals(0f, t.looked, 0f)
        // A finger dragging it does, either way round.
        input.lookDragging = true
        for (i in 0 until 20) {
            input.yaw -= 0.02f
            t.update(FIXED_DT, input)
        }
        assertEquals(0.4f, t.looked, 0.02f)
        assertEquals(TutorialStep.LOOK, t.step)
        for (i in 0 until 12) {
            input.yaw += 0.02f
            t.update(FIXED_DT, input)
        }
        assertEquals(Tutorial.Phase.CHECK, t.phase)
    }

    @Test
    fun turningPastTheBackOfTheCompassCountsTheShortWay() {
        val t = Tutorial()
        t.toLook()
        input.yaw = PI.toFloat() - 0.1f
        t.update(FIXED_DT, input)
        input.firstPerson = true
        input.lookDragging = true
        input.yaw = -PI.toFloat() + 0.1f
        t.update(FIXED_DT, input)
        assertEquals(0.2f, t.looked, 0.01f)
    }

    @Test
    fun lookingAroundEarlyCountsWhenTheLookStepArrives() {
        val t = Tutorial()
        input.firstPerson = true
        input.lookDragging = true
        for (i in 0 until 60) {
            input.yaw += 0.02f
            t.update(FIXED_DT, input)
        }
        // Still on the walk (nothing else done), but the look is already in the bank.
        assertEquals(TutorialStep.WALK, t.step)
        t.walk(Tutorial.WALK_DISTANCE + 1f)
        t.finishCheck()
        assertEquals(TutorialStep.LOOK, t.step)
        t.tick(0.1f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
    }

    // ---------------------------------------------------------------- playing and prizes

    @Test
    fun startingARoundSettlesEverythingBeforeIt() {
        val t = Tutorial()
        t.onPlayed()
        t.tick(0.05f)
        assertEquals(TutorialStep.PLAY, t.step)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        t.finishCheck()
        assertEquals(TutorialStep.PRIZES, t.step)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
    }

    @Test
    fun openingThePrizeCounterFinishesTheLessonWhereverYouWere() {
        val t = Tutorial()
        t.onPrizesOpened()
        t.tick(0.05f)
        assertEquals(TutorialStep.PRIZES, t.step)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        t.finishCheck()
        assertEquals(Tutorial.Phase.OUTRO, t.phase)
    }

    @Test
    fun aFullRunEndsWithAThankYouThenIsOverAndNotSkipped() {
        val t = Tutorial()
        t.walk(Tutorial.WALK_DISTANCE + 1f)
        t.finishCheck()
        input.firstPerson = true
        input.lookDragging = true
        for (i in 0 until 40) {
            input.yaw += 0.02f
            t.update(FIXED_DT, input)
        }
        t.finishCheck()
        assertEquals(TutorialStep.PLAY, t.step)
        t.onPlayed()
        t.finishCheck()
        assertEquals(TutorialStep.PRIZES, t.step)
        t.onPrizesOpened()
        t.tick(0.05f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        t.finishCheck()
        assertEquals(Tutorial.Phase.OUTRO, t.phase)
        assertEquals("YOU'RE ALL SET!", t.text(input).title)
        t.tick(Tutorial.OUTRO_SECONDS + 0.1f)
        assertTrue(t.finished)
        assertFalse(t.skipped)
    }

    @Test
    fun aRoundStartedWhileAPanelWasOpenIsNotLost() {
        val t = Tutorial()
        input.blocked = true
        t.onPlayed()
        t.tick(1f)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
        input.blocked = false
        t.tick(0.05f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
    }

    // ---------------------------------------------------------------- skipping

    @Test
    fun skippingAStepMovesToTheNextAndSkippingTheLastEndsIt() {
        val t = Tutorial()
        t.skipStep()
        assertEquals(TutorialStep.LOOK, t.step)
        assertEquals(Tutorial.Phase.ACTIVE, t.phase)
        t.skipStep()
        t.skipStep()
        assertEquals(TutorialStep.PRIZES, t.step)
        assertFalse(t.finished)
        t.skipStep()
        assertTrue(t.finished)
        assertTrue(t.skipped)
    }

    @Test
    fun skippingEverythingEndsItAtOnceFromAnywhere() {
        val t = Tutorial()
        t.walk(Tutorial.WALK_DISTANCE + 1f)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
        t.skipAll()
        assertTrue(t.finished)
        assertTrue(t.skipped)
        // And it stays over.
        t.onPlayed()
        t.tick(5f)
        assertTrue(t.finished)
    }

    @Test
    fun aStepCannotBeSkippedWhileItsTickIsShowing() {
        val t = Tutorial()
        t.walk(Tutorial.WALK_DISTANCE + 1f)
        t.skipStep()
        assertEquals(TutorialStep.WALK, t.step)
        assertEquals(Tutorial.Phase.CHECK, t.phase)
    }

    // ---------------------------------------------------------------- words

    @Test
    fun theWalkLessonFitsTheViewAndTheHand() {
        val t = Tutorial()
        assertTrue(t.text(input).body.contains("DRAG ANYWHERE"))
        input.firstPerson = true
        assertTrue(t.text(input).body.contains("LEFT OF THE SCREEN"))
        input.leftHanded = true
        assertTrue(t.text(input).body.contains("RIGHT OF THE SCREEN"))
    }

    @Test
    fun theLookLessonPointsAtTheEyeButtonThenAtTheOtherThumb() {
        val t = Tutorial()
        t.skipStep()
        assertTrue(t.text(input).body.contains("EYE BUTTON"))
        input.firstPerson = true
        assertTrue(t.text(input).body.contains("RIGHT OF THE SCREEN"))
        input.leftHanded = true
        assertTrue(t.text(input).body.contains("LEFT OF THE SCREEN"))
    }

    @Test
    fun thePlayAndPrizeLessonsChangeOnceYouAreStandingAtTheThing() {
        val t = Tutorial()
        t.skipStep()
        t.skipStep()
        assertEquals(TutorialStep.PLAY, t.step)
        assertTrue(t.text(input).body.contains("WALK UP"))
        input.atMachine = true
        assertTrue(t.text(input).body.contains("PLAY BUBBLE"))
        t.skipStep()
        assertTrue(t.text(input).body.contains("BACK OF THE HALL"))
        input.atPrizes = true
        assertTrue(t.text(input).body.contains("SHOP"))
    }

    @Test
    fun aFinishedStepSaysNiceWithATick() {
        val t = Tutorial()
        t.walk(Tutorial.WALK_DISTANCE + 1f)
        val text = t.text(input)
        assertEquals("NICE!", text.title)
        assertTrue(text.check)
    }

    // ---------------------------------------------------------------- the arrow

    private val map = HubLayout.build(GameRegistry.createAll(), emptySet())

    @Test
    fun theArrowPointsAtTheNearestMachineUntilYouStandAtOne() {
        val t = Tutorial()
        t.skipStep()
        t.skipStep()
        input.x = map.spawnX
        input.y = map.spawnY
        val target = t.guideTarget(map.spots, input)
        assertNotNull(target)
        assertEquals(SpotType.MACHINE, target!!.type)
        val best = map.spots.filter { it.type == SpotType.MACHINE }.minByOrNull {
            (it.area.centerX - input.x) * (it.area.centerX - input.x) + (it.area.centerY - input.y) * (it.area.centerY - input.y)
        }!!
        assertTrue(target === best)
        input.atMachine = true
        assertNull(t.guideTarget(map.spots, input))
    }

    @Test
    fun theArrowPointsAtThePrizeCounterOnTheLastStepOnly() {
        val t = Tutorial()
        assertNull(t.guideTarget(map.spots, input))
        t.skipStep()
        assertNull(t.guideTarget(map.spots, input))
        t.skipStep()
        t.skipStep()
        assertEquals(TutorialStep.PRIZES, t.step)
        val target = t.guideTarget(map.spots, input)
        assertEquals(SpotType.PRIZES, target!!.type)
        input.atPrizes = true
        assertNull(t.guideTarget(map.spots, input))
        input.atPrizes = false
        t.onPrizesOpened()
        t.tick(0.05f)
        assertNull("no arrow while the tick shows", t.guideTarget(map.spots, input))
    }

    // ---------------------------------------------------------------- where the arrow is drawn

    private val out = FloatArray(3)

    private fun place(vx: Float, vy: Float, vz: Float): Boolean =
        GuideMarker.place(vx, vy, vz, focal = 1000f, cx = 540f, cy = 1200f, near = 8f, w = 1080f, h = 2400f, margin = 60f, top = 300f, bottom = 2000f, out = out)

    @Test
    fun aPointInViewGetsTheMarkerOnItPointingDown() {
        assertTrue(place(50f, 20f, 400f))
        assertEquals(540f + 50f / 400f * 1000f, out[0], 0.01f)
        assertEquals(1200f - 20f / 400f * 1000f, out[1], 0.01f)
        assertEquals(PI.toFloat() / 2f, out[2], 1e-5f)
    }

    @Test
    fun aPointOffToTheRightGetsAnArrowOnTheRightEdgePointingRight() {
        assertFalse(place(2000f, 0f, 400f))
        assertEquals(1080f - 60f, out[0], 0.01f)
        assertEquals(0f, out[2], 0.05f)
        assertTrue(out[1] in 300f..2000f)
    }

    @Test
    fun aPointFarAboveGetsAnArrowAlongTheTopPointingUp() {
        assertFalse(place(0f, 5000f, 400f))
        assertEquals(300f, out[1], 0.01f)
        assertEquals(-PI.toFloat() / 2f, out[2], 0.05f)
    }

    @Test
    fun aPointBehindYouGetsAnArrowAlongTheBottomOnItsSide() {
        assertFalse(place(-80f, 0f, -300f))
        assertEquals(2000f, out[1], 0.01f)
        assertTrue("on the left ${out[0]}", out[0] < 540f)
        assertFalse(place(80f, 0f, -300f))
        assertTrue("on the right ${out[0]}", out[0] > 540f)
        // Dead behind: the middle of the bottom edge.
        assertFalse(place(0f, 0f, -300f))
        assertEquals(540f, out[0], 0.5f)
        assertEquals(2000f, out[1], 0.01f)
    }

    @Test
    fun theMarkerNeverLeavesTheScreenWhereverThePointIs() {
        var vx = -3000f
        while (vx <= 3000f) {
            var vy = -3000f
            while (vy <= 3000f) {
                for (vz in floatArrayOf(-500f, 3f, 100f, 900f)) {
                    place(vx, vy, vz)
                    assertTrue("x ${out[0]} for ($vx, $vy, $vz)", out[0] in 59.9f..1020.1f)
                    assertTrue("y ${out[1]} for ($vx, $vy, $vz)", out[1] in 299.9f..2000.1f)
                }
                vy += 500f
            }
            vx += 500f
        }
    }
}
