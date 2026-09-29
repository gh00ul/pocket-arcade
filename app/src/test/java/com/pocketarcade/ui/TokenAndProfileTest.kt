package com.pocketarcade.ui

import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The token machine's countdown maths and the profile's tallies. */
class TokenAndProfileTest {
    @Test
    fun durationsReadAsHoursAndMinutesOrMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5_000))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("59:59", formatDuration(3_599_000))
        assertEquals("1H 00M", formatDuration(3_600_000))
        assertEquals("5H 32M", formatDuration((5 * 3600 + 32 * 60 + 10) * 1000L))
    }

    @Test
    fun theDailyRingFillsFromMidnightToMidnight() {
        assertEquals(0f, dailyProgress(24 * 3600 * 1000L), 1e-6f)
        assertEquals(0.5f, dailyProgress(12 * 3600 * 1000L), 1e-6f)
        assertEquals(1f, dailyProgress(0), 1e-6f)
        // Clock oddities (a day of 25 hours, a negative wait) stay inside the ring.
        assertEquals(0f, dailyProgress(30 * 3600 * 1000L), 0f)
        assertEquals(1f, dailyProgress(-1), 0f)
    }

    @Test
    fun theSpareTokenBarFillsAsTheWaitRunsOut() {
        val cool = ArcadeRepository.SPARE_TOKEN_COOLDOWN_MS
        assertEquals(0f, spareProgress(cool), 1e-6f)
        assertEquals(0.5f, spareProgress(cool / 2), 1e-6f)
        assertEquals(1f, spareProgress(0), 1e-6f)
        assertEquals(1f, spareProgress(-10_000), 0f)
    }

    @Test
    fun theTradeHintCountsTokensOrTicketsShort() {
        val per = ArcadeRepository.TICKETS_PER_TOKEN
        assertEquals("$per MORE TICKETS FOR A TOKEN", tradeHint(0))
        assertEquals("1 MORE TICKETS FOR A TOKEN", tradeHint(per - 1))
        assertEquals("ENOUGH FOR 1 TOKEN", tradeHint(per))
        assertEquals("ENOUGH FOR 1 TOKEN", tradeHint(per * 2 - 1))
        assertEquals("ENOUGH FOR 3 TOKENS", tradeHint(per * 3 + 5))
    }

    @Test
    fun theProfileTalliesPrizesPlushiesAndMachines() {
        val fresh = profileTotals(SaveState())
        assertEquals(0, fresh.prizes)
        assertEquals(0, fresh.plushFound)
        assertEquals(Catalog.plushies.size, fresh.plushTotal)
        val save = SaveState(
            owned = setOf(Catalog.DEFAULT_OUTFIT, "hat_cap", "decor_palm"),
            collection = mapOf("plush_bear" to 2, "plush_cat" to 0),
            totalPlays = 7,
            stats = mapOf("photos" to 3L),
            highScores = mapOf("claw" to 120),
        )
        val t = profileTotals(save)
        assertEquals(2, t.prizes)
        assertEquals(1, t.plushFound)
        assertEquals(7, t.played)
        assertEquals(3L, t.photos)
        val games = GameRegistry.createAll()
        assertEquals(1, machinesPlayed(save, games))
        assertTrue(machinesPlayed(SaveState(), games) == 0)
    }
}
