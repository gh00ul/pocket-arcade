package com.pocketarcade.ui

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutCubic
import kotlin.math.ceil

/** The letter a round is stamped with on the results card, best first. */
enum class Grade(val letter: String, val argb: Int) {
    S("S", Pal.GOLD),
    A("A", Pal.LIME),
    B("B", Pal.CYAN),
    C("C", Pal.LAVENDER),
}

/**
 * The results screen's pure parts: the timeline of the staged reveal, the score's count-up, the
 * performance grade and how many tickets each flying sprite stands for. No drawing and no
 * Android, so they are unit-tested; `GameHostScreen` plays them.
 *
 * The reveal runs title, score count-up, best (or NEW HIGH SCORE), grade stamp, ticket printing
 * and finally the buttons. A tap during it jumps to [PRINT_AT].
 */
object ResultsPlan {
    /** The card slides in over this long. */
    const val CARD_IN = 0.45f

    /** The score starts counting up here and takes [SCORE_SECONDS]. */
    const val SCORE_AT = 0.35f
    const val SCORE_SECONDS = 0.75f

    /** The best line (or the NEW HIGH SCORE slam) lands here. */
    const val BEST_AT = 1.15f

    /** The grade is stamped here; the stamp takes [STAMP_SECONDS] to slam down. */
    const val GRADE_AT = 1.5f
    const val STAMP_SECONDS = 0.2f

    /** Tickets start printing here, and the buttons follow once they are done. */
    const val PRINT_AT = 1.85f

    /** Where the ticket printer's slot sits in the game field (the strip feeds out below it). */
    const val SLOT_Y = 372f

    /** A soft tick sounds this often while the score counts up. */
    const val COUNT_TICK_SECONDS = 0.055f

    /** However many tickets are paid, they fly into the counter in at most this many sprites. */
    const val MAX_FLIGHTS = 18

    /**
     * Tickets a good round pays: about the middle of the band every machine is tuned into (a good
     * player averages 8 to 70 a round). The grade reads a round against it and against the
     * player's own best, so it needs no per-machine table and changes nothing about the economy.
     */
    const val PAR_TICKETS = 24

    /** Skill needed for each grade (see [skill]). */
    const val S_AT = 1.05f
    const val A_AT = 0.8f
    const val B_AT = 0.5f

    /** The score shown [t] seconds into the reveal: 0 before [SCORE_AT], the final score once counted, eased out. */
    fun countedScore(score: Int, t: Float): Int {
        val p = clamp01((t - SCORE_AT) / SCORE_SECONDS)
        return if (p >= 1f) score else (score * easeOutCubic(p)).toInt()
    }

    /**
     * How well a round went, about 0 to 1.4: half how the score stands against the player's [best]
     * before this round (0 to 1.25, so a new best counts for a bit more than a tie) and half the
     * ticket haul against [PAR_TICKETS] (0 to 1.5). With no best yet (a first round on a machine)
     * the haul alone decides, so a first round can earn a good grade but can't be judged against
     * nothing.
     */
    fun skill(score: Int, best: Int, tickets: Int): Float {
        val pay = (tickets / PAR_TICKETS.toFloat()).coerceIn(0f, 1.5f)
        if (best <= 0) return pay
        val vsBest = (score / best.toFloat()).coerceIn(0f, 1.25f)
        return 0.5f * vsBest + 0.5f * pay
    }

    /** The grade for a round: [score] against the previous [best], and the [tickets] it paid in all. A zero score is always a C. */
    fun grade(score: Int, best: Int, tickets: Int): Grade {
        if (score <= 0) return Grade.C
        val s = skill(score, best, tickets)
        return when {
            s >= S_AT -> Grade.S
            s >= A_AT -> Grade.A
            s >= B_AT -> Grade.B
            else -> Grade.C
        }
    }

    /** How many tickets one flying sprite stands for, so [total] tickets fly in at most [MAX_FLIGHTS] sprites. */
    fun flightChunk(total: Int): Int = ceil(total.coerceAtLeast(0) / MAX_FLIGHTS.toFloat()).toInt().coerceAtLeast(1)

    /** The reveal stage at [t] seconds in: 0 card, 1 score counting, 2 best, 3 grade, 4 printing. */
    fun stageAt(t: Float): Int = when {
        t >= PRINT_AT -> 4
        t >= GRADE_AT -> 3
        t >= BEST_AT -> 2
        t >= SCORE_AT -> 1
        else -> 0
    }
}
