package com.pocketarcade.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The results screen's grade, count-up and timeline. */
class ResultsPlanTest {
    private fun grade(score: Int, best: Int, tickets: Int) = ResultsPlan.grade(score, best, tickets)

    @Test
    fun aZeroScoreIsAlwaysACWhateverElseHappened() {
        assertEquals(Grade.C, grade(0, 0, 50))
        assertEquals(Grade.C, grade(0, 100, 50))
    }

    @Test
    fun aFirstRoundIsJudgedByTheHaulAlone() {
        // No best yet: par tickets is a solid A, a fat haul is an S, a tiny one a C.
        assertEquals(Grade.A, grade(300, 0, ResultsPlan.PAR_TICKETS))
        assertEquals(Grade.S, grade(900, 0, (ResultsPlan.PAR_TICKETS * 1.4f).toInt()))
        assertEquals(Grade.C, grade(20, 0, 3))
        assertEquals(Grade.B, grade(120, 0, ResultsPlan.PAR_TICKETS * 6 / 10))
    }

    @Test
    fun aNewBestOnAPoorHaulIsNotAnS() {
        val g = grade(160, 100, 8)
        assertTrue("got $g", g == Grade.B || g == Grade.A)
        assertTrue(g != Grade.S)
    }

    @Test
    fun anSNeedsBothABestBeatingScoreAndAHealthyHaul() {
        assertEquals(Grade.S, grade(150, 100, 30))
        // Only tying the best on a par haul is an A, and a great score with a tiny haul is no S either.
        assertEquals(Grade.A, grade(100, 100, ResultsPlan.PAR_TICKETS))
        assertTrue(grade(150, 100, 2).ordinal > Grade.S.ordinal)
    }

    @Test
    fun aWeakRoundAgainstAHighBestIsACAndAStrongOneAgainstALowBestIsBetter() {
        assertEquals(Grade.C, grade(20, 400, 3))
        assertTrue(grade(400, 400, 24).ordinal <= Grade.A.ordinal)
    }

    @Test
    fun moreScoreOrMoreTicketsNeverLowersTheGrade() {
        // Grade is ordered S(0) < A < B < C, so "never worse" means the ordinal never rises.
        for (best in intArrayOf(0, 50, 400)) {
            var prev = Grade.C.ordinal
            for (score in 1..800 step 7) prev = check(prev, grade(score, best, 20), "score $score best $best")
            prev = Grade.C.ordinal
            for (tickets in 0..80) prev = check(prev, grade(200, best, tickets), "tickets $tickets best $best")
        }
        // A better personal best (higher bar) never makes the same round grade higher.
        var last = -1
        for (best in 10..600 step 10) {
            val o = grade(200, best, 20).ordinal
            assertTrue("best $best", o >= last)
            last = o
        }
    }

    private fun check(prev: Int, g: Grade, what: String): Int {
        assertTrue("$what: $g after ordinal $prev", g.ordinal <= prev)
        return g.ordinal
    }

    @Test
    fun theSkillIsClampedSoAFluke1000xBestCannotRunAway() {
        assertTrue(ResultsPlan.skill(1_000_000, 10, 100_000) <= 1.4f)
        assertEquals(0f, ResultsPlan.skill(0, 50, 0), 0f)
    }

    @Test
    fun theCountUpStartsAtZeroClimbsAndLandsOnTheExactScore() {
        assertEquals(0, ResultsPlan.countedScore(1234, 0f))
        assertEquals(0, ResultsPlan.countedScore(1234, ResultsPlan.SCORE_AT))
        var prev = 0
        var t = 0f
        while (t < ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS + 0.5f) {
            val shown = ResultsPlan.countedScore(1234, t)
            assertTrue(shown >= prev && shown <= 1234)
            prev = shown
            t += 0.01f
        }
        assertEquals(1234, ResultsPlan.countedScore(1234, ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS))
        assertEquals(1234, ResultsPlan.countedScore(1234, 99f))
        assertEquals(0, ResultsPlan.countedScore(0, 5f))
        // Eased out: most of the number is on screen well before the end.
        assertTrue(ResultsPlan.countedScore(1000, ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS * 0.5f) > 800)
    }

    @Test
    fun theRevealRunsInOrderAndSkipJumpsToPrinting() {
        val times = floatArrayOf(ResultsPlan.SCORE_AT, ResultsPlan.BEST_AT, ResultsPlan.GRADE_AT, ResultsPlan.PRINT_AT)
        for (i in 1 until times.size) assertTrue(times[i] > times[i - 1])
        assertEquals(0, ResultsPlan.stageAt(0f))
        assertEquals(1, ResultsPlan.stageAt(ResultsPlan.SCORE_AT))
        assertEquals(2, ResultsPlan.stageAt(ResultsPlan.BEST_AT + 0.01f))
        assertEquals(3, ResultsPlan.stageAt(ResultsPlan.GRADE_AT))
        assertEquals(4, ResultsPlan.stageAt(ResultsPlan.PRINT_AT))
        assertEquals(4, ResultsPlan.stageAt(ResultsPlan.PRINT_AT + 100f))
        // The count-up finishes before the best line lands, and the stamp finishes before printing starts.
        assertTrue(ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS <= ResultsPlan.BEST_AT)
        assertTrue(ResultsPlan.GRADE_AT + ResultsPlan.STAMP_SECONDS <= ResultsPlan.PRINT_AT + 0.05f)
        // And the whole reveal stays short enough not to nag on repeat rounds.
        assertTrue(ResultsPlan.PRINT_AT <= 2.2f)
    }

    @Test
    fun ticketsFlyInAtMostTheMaxNumberOfSpritesAndAllOfThemArrive() {
        for (total in intArrayOf(0, 1, 5, 18, 19, 40, 71, 500)) {
            val chunk = ResultsPlan.flightChunk(total)
            assertTrue(chunk >= 1)
            var left = total
            var flights = 0
            while (left > 0) {
                left -= minOf(chunk, left); flights++
            }
            assertTrue("$total tickets in $flights flights", flights <= ResultsPlan.MAX_FLIGHTS)
        }
        assertEquals(1, ResultsPlan.flightChunk(0))
        assertEquals(1, ResultsPlan.flightChunk(12))
        assertEquals(2, ResultsPlan.flightChunk(19))
    }
}
