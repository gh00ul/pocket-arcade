package com.pocketarcade.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The primitives later features build on: ticket spending, stats, unlocks, collectibles, the
 * arcade's name and the score tables. Each is checked through the repository and a relaunch.
 */
class SaveExtrasTest : RepositoryTestBase() {
    // ---- spendTickets

    @Test
    fun spendTicketsTakesExactlyThatManyAndFailsWhenShort() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 50) })
        assertTrue(repo.spendTickets(20))
        assertEquals(30, repo.state.first().tickets)
        assertFalse(repo.spendTickets(31))
        assertEquals(30, repo.state.first().tickets)
        assertTrue(repo.spendTickets(30))
        assertEquals(0, repo.state.first().tickets)
        assertFalse(repo.spendTickets(1))
        assertEquals(0, repo.state.first().tickets)
    }

    @Test
    fun spendTicketsNeverPaysOutForANegativeAmount() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 5) })
        assertFalse(repo.spendTickets(-10))
        assertEquals(5, repo.state.first().tickets)
        assertTrue(repo.spendTickets(0))
        assertEquals(5, repo.state.first().tickets)
    }

    // ---- stats

    @Test
    fun addStatCountsUpEachKeyAndSurvivesARelaunch() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addStat("plays:racer")
        repo.addStat("plays:racer")
        repo.addStat("tickets:earned", 340)
        repo.addStat("plays:claw")
        assertEquals(mapOf("plays:racer" to 2L, "tickets:earned" to 340L, "plays:claw" to 1L), repo.state.first().stats)
        relaunch()
        val s = ArcadeRepository(newStore()).state.first()
        assertEquals(2L, s.stat("plays:racer"))
        assertEquals(340L, s.stat("tickets:earned"))
        assertEquals(0L, s.stat("plays:pinball"))
    }

    @Test
    fun addStatHoldsBigNumbersAndNeverGoesNegative() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addStat("big", 5_000_000_000L)
        repo.addStat("big", 5_000_000_000L)
        assertEquals(10_000_000_000L, repo.state.first().stat("big"))
        repo.addStat("big", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, repo.state.first().stat("big"))
        repo.addStat("small", 3)
        repo.addStat("small", -10)
        assertEquals(0L, repo.state.first().stat("small"))
        assertFalse("small" in repo.state.first().stats)
    }

    @Test
    fun addStatIgnoresBlankKeysKeysWithTheSeparatorAndZeroDeltas() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addStat("")
        repo.addStat("   ")
        repo.addStat("a;b")
        repo.addStat("zero", 0)
        repo.addStat("  padded  ")
        assertEquals(mapOf("padded" to 1L), repo.state.first().stats)
    }

    @Test
    fun addStatKeepsTheGoodPartsOfADamagedStatsString() = blocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("stats")] = "plays:racer:4;;junk;x:notANumber;:9;neg:-3" }
        val repo = ArcadeRepository(store)
        assertEquals(mapOf("plays:racer" to 4L), repo.state.first().stats)
        repo.addStat("plays:racer")
        assertEquals(mapOf("plays:racer" to 5L), repo.state.first().stats)
    }

    // ---- unlocks

    @Test
    fun unlockIsTrueOnlyTheFirstTime() = blocking {
        val repo = ArcadeRepository(newStore())
        assertTrue(repo.unlock("first_win"))
        assertFalse(repo.unlock("first_win"))
        assertTrue(repo.unlock("racer:gold"))
        val s = repo.state.first()
        assertEquals(setOf("first_win", "racer:gold"), s.unlocked)
        assertTrue(s.isUnlocked("first_win"))
        assertFalse(s.isUnlocked("nope"))
        relaunch()
        assertEquals(setOf("first_win", "racer:gold"), ArcadeRepository(newStore()).state.first().unlocked)
    }

    @Test
    fun unlockKeepsTheOrderTheyWereEarnedIn() = blocking {
        val repo = ArcadeRepository(newStore())
        listOf("zeta", "alpha", "mid").forEach { repo.unlock(it) }
        assertEquals(listOf("zeta", "alpha", "mid"), repo.state.first().unlocked.toList())
    }

    @Test
    fun unlockRefusesBlankIdsAndIdsWithTheSeparator() = blocking {
        val repo = ArcadeRepository(newStore())
        assertFalse(repo.unlock(""))
        assertFalse(repo.unlock("  "))
        assertFalse(repo.unlock("a;b"))
        assertTrue(repo.state.first().unlocked.isEmpty())
        assertTrue(repo.unlock("  trimmed "))
        assertFalse(repo.unlock("trimmed"))
        assertEquals(setOf("trimmed"), repo.state.first().unlocked)
    }

    @Test
    fun unlockedReadsThroughADamagedString() = blocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("unlocked")] = ";;a; ;b;;a;" }
        val repo = ArcadeRepository(store)
        assertEquals(setOf("a", "b"), repo.state.first().unlocked)
        assertFalse(repo.unlock("a"))
        assertTrue(repo.unlock("c"))
        assertEquals(setOf("a", "b", "c"), repo.state.first().unlocked)
    }

    // ---- collectibles

    @Test
    fun addCollectibleCountsNamespacedIdsApartFromThePlushCollection() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addCollectible("fish:trout")
        repo.addCollectible("fish:trout")
        repo.addCollectible("fish:golden")
        repo.addPrize("bear")
        val s = repo.state.first()
        assertEquals(mapOf("fish:trout" to 2, "fish:golden" to 1), s.collectibles)
        assertEquals(mapOf("bear" to 1), s.collection)
        assertEquals(2, s.collectibleCount("fish:trout"))
        assertEquals(0, s.collectibleCount("fish:whale"))
        relaunch()
        assertEquals(mapOf("fish:trout" to 2, "fish:golden" to 1), ArcadeRepository(newStore()).state.first().collectibles)
    }

    @Test
    fun addCollectibleIgnoresBlankAndSeparatorIds() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addCollectible("")
        repo.addCollectible("x;y")
        repo.addCollectible(" fish:carp ")
        assertEquals(mapOf("fish:carp" to 1), repo.state.first().collectibles)
    }

    // ---- the arcade's name

    @Test
    fun theArcadeNameDefaultsThenTakesACleanedName() = blocking {
        val repo = ArcadeRepository(newStore())
        assertEquals("POCKET ARCADE", repo.state.first().arcadeName)
        repo.setArcadeName("  fun house 99! ")
        assertEquals("FUN HOUSE 99", repo.state.first().arcadeName)
        relaunch()
        assertEquals("FUN HOUSE 99", ArcadeRepository(newStore()).state.first().arcadeName)
    }

    @Test
    fun aBlankNameResetsToTheDefault() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.setArcadeName("Neon Nook")
        assertEquals("NEON NOOK", repo.state.first().arcadeName)
        repo.setArcadeName("   ")
        assertEquals("POCKET ARCADE", repo.state.first().arcadeName)
        repo.setArcadeName("Neon Nook")
        repo.setArcadeName("!!!")
        assertEquals("POCKET ARCADE", repo.state.first().arcadeName)
    }

    @Test
    fun aTamperedStoredNameIsCleanedOnRead() = blocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("arcade_name")] = "hello <b>world</b> and then some more" }
        val name = ArcadeRepository(store).state.first().arcadeName
        assertTrue(name.length <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)
        assertEquals(name, ArcadeRepository.sanitizeArcadeName(name))
        assertTrue(name.all { it in 'A'..'Z' || it in '0'..'9' || it == ' ' })
    }

    // ---- score tables

    @Test
    fun recordScoreEntryRanksAndKeepsTheTopFiveBestFirst() = blocking {
        val repo = ArcadeRepository(newStore())
        assertEquals(0, repo.recordScoreEntry("racer", 500, "ann"))
        assertEquals(1, repo.recordScoreEntry("racer", 300, "bob"))
        assertEquals(0, repo.recordScoreEntry("racer", 900, "cy"))
        assertEquals(3, repo.recordScoreEntry("racer", 100, "dee"))
        assertEquals(4, repo.recordScoreEntry("racer", 50, "eve"))
        assertEquals(
            listOf(
                ScoreEntry("CYA", 900), ScoreEntry("ANN", 500), ScoreEntry("BOB", 300),
                ScoreEntry("DEE", 100), ScoreEntry("EVE", 50),
            ),
            repo.state.first().scoreTable("racer"),
        )
        // Full: a low score is turned away and changes nothing, a good one pushes the last off.
        assertEquals(-1, repo.recordScoreEntry("racer", 40, "zed"))
        assertEquals(2, repo.recordScoreEntry("racer", 400, "fay"))
        val table = repo.state.first().scoreTable("racer")
        assertEquals(5, table.size)
        assertEquals(listOf(900, 500, 400, 300, 100), table.map { it.score })
        assertEquals("FAY", table[2].initials)
        relaunch()
        assertEquals(table, ArcadeRepository(newStore()).state.first().scoreTable("racer"))
    }

    @Test
    fun aTiedScoreGoesBelowTheEarlierOne() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.recordScoreEntry("claw", 200, "one")
        assertEquals(1, repo.recordScoreEntry("claw", 200, "two"))
        assertEquals(listOf("ONE", "TWO"), repo.state.first().scoreTable("claw").map { it.initials })
    }

    @Test
    fun aTiedScoreIsTurnedAwayFromAFullTableOfEqualScores() = blocking {
        val repo = ArcadeRepository(newStore())
        repeat(5) { assertEquals(it, repo.recordScoreEntry("claw", 100, "aaa")) }
        assertEquals(-1, repo.recordScoreEntry("claw", 100, "bbb"))
        assertTrue(repo.state.first().scoreTable("claw").all { it.initials == "AAA" })
    }

    @Test
    fun recordScoreEntryTurnsAwayZeroNegativeAndNamelessScores() = blocking {
        val repo = ArcadeRepository(newStore())
        assertEquals(-1, repo.recordScoreEntry("racer", 0, "abc"))
        assertEquals(-1, repo.recordScoreEntry("racer", -5, "abc"))
        assertEquals(-1, repo.recordScoreEntry("  ", 50, "abc"))
        assertTrue(repo.state.first().scoreTables.isEmpty())
    }

    @Test
    fun scoreTablesKeepMachinesApartAndLeaveTheHighScoreAlone() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.recordScoreEntry("racer", 500, "abc")
        repo.recordScoreEntry("claw", 20, "xyz")
        var s = repo.state.first()
        assertEquals(setOf("racer", "claw"), s.scoreTables.keys)
        assertEquals(500, s.scoreTable("racer").single().score)
        assertEquals(emptyList<ScoreEntry>(), s.scoreTable("pinball"))
        // hs_<id> is a separate record, written by recordScore.
        assertEquals(0, s.highScore("racer"))
        repo.recordScore("racer", 500)
        s = repo.state.first()
        assertEquals(500, s.highScore("racer"))
        assertEquals(1, s.scoreTable("racer").size)
        assertEquals(mapOf("racer" to 500), s.highScores)
    }

    @Test
    fun initialsAreCleanedToThreeCharacters() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.recordScoreEntry("g", 90, "a-b!c9z")
        repo.recordScoreEntry("g", 80, "")
        repo.recordScoreEntry("g", 70, "q")
        repo.recordScoreEntry("g", 60, "  ")
        repo.recordScoreEntry("g", 50, "  9x ")
        assertEquals(listOf("ABC", "AAA", "QAA", "AAA", "9XA"), repo.state.first().scoreTable("g").map { it.initials })
    }

    @Test
    fun aDamagedScoreTableKeepsItsGoodEntries() = blocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("scores_racer")] = "ABC:100,junk,DEF:x,GH:5,IJK:-3,LMN:40,,OPQ:0" }
        val repo = ArcadeRepository(store)
        assertEquals(listOf(ScoreEntry("ABC", 100), ScoreEntry("LMN", 40)), repo.state.first().scoreTable("racer"))
        assertEquals(1, repo.recordScoreEntry("racer", 60, "new"))
        assertEquals(listOf(100, 60, 40), repo.state.first().scoreTable("racer").map { it.score })
    }
}
