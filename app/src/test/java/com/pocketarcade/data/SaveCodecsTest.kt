package com.pocketarcade.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure encodings and cleaners behind the save: they must never throw and must keep what is good. */
class SaveCodecsTest {
    // ---- stats

    @Test
    fun statsRoundTripAndKeysMayHoldColons() {
        val map = mapOf("plays:racer" to 12L, "tickets:earned" to 5_000_000_000L)
        val raw = ArcadeRepository.encodeStats(map)
        assertEquals("plays:racer:12;tickets:earned:5000000000", raw)
        assertEquals(map, ArcadeRepository.decodeStats(raw))
    }

    @Test
    fun encodeStatsDropsZeroAndNegativeCounts() {
        assertEquals("a:1", ArcadeRepository.encodeStats(mapOf("a" to 1L, "b" to 0L, "c" to -4L)))
        assertEquals("", ArcadeRepository.encodeStats(emptyMap()))
    }

    @Test
    fun decodeStatsIgnoresBadParts() {
        assertEquals(emptyMap<String, Long>(), ArcadeRepository.decodeStats(null))
        assertEquals(emptyMap<String, Long>(), ArcadeRepository.decodeStats(""))
        assertEquals(emptyMap<String, Long>(), ArcadeRepository.decodeStats(" \n "))
        assertEquals(emptyMap<String, Long>(), ArcadeRepository.decodeStats(";;;::;:"))
        val decoded = ArcadeRepository.decodeStats(
            ";;ok:3;noColon;bad:text;:8;neg:-1;zero:0;huge:99999999999999999999;  spaced  : 7 ;last:1",
        )
        assertEquals(mapOf("ok" to 3L, "spaced" to 7L, "last" to 1L), decoded)
    }

    @Test
    fun decodeStatsLetsALaterDuplicateWin() {
        assertEquals(mapOf("a" to 9L), ArcadeRepository.decodeStats("a:2;a:9"))
    }

    // ---- id sets

    @Test
    fun idSetsRoundTripInOrder() {
        val ids = linkedSetOf("first_win", "racer:gold", "a")
        assertEquals(listOf("first_win", "racer:gold", "a"), ArcadeRepository.decodeIds(ArcadeRepository.encodeIds(ids)).toList())
        assertEquals("", ArcadeRepository.encodeIds(emptySet()))
    }

    @Test
    fun decodeIdsSkipsBlanksTrimsAndDropsRepeats() {
        assertEquals(emptySet<String>(), ArcadeRepository.decodeIds(null))
        assertEquals(emptySet<String>(), ArcadeRepository.decodeIds(""))
        assertEquals(emptySet<String>(), ArcadeRepository.decodeIds(";;; ;"))
        assertEquals(listOf("a", "b"), ArcadeRepository.decodeIds(" a;;b ;a;").toList())
    }

    // ---- the arcade name

    @Test
    fun sanitizeArcadeNameUppercasesAndKeepsOnlyLettersDigitsAndSpaces() {
        assertEquals("HELLO WORLD9", ArcadeRepository.sanitizeArcadeName("hello, world_9!"))
        assertEquals("ABC", ArcadeRepository.sanitizeArcadeName("a\tb\nc"))
        assertEquals("CAF", ArcadeRepository.sanitizeArcadeName("café"))
    }

    @Test
    fun sanitizeArcadeNameTrimsAndCutsToFourteen() {
        assertEquals("NEON NOOK", ArcadeRepository.sanitizeArcadeName("   neon nook   "))
        assertEquals("ABCDEFGHIJKLMN", ArcadeRepository.sanitizeArcadeName("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("ABCDEFGHIJKLMN", ArcadeRepository.sanitizeArcadeName("ABCDEFGHIJKLMN"))
        // A cut that lands after a space leaves no trailing space.
        val cut = ArcadeRepository.sanitizeArcadeName("abcdefghijklm nopq")
        assertEquals("ABCDEFGHIJKLM", cut)
        assertTrue(ArcadeRepository.sanitizeArcadeName("x".repeat(500)).length <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)
    }

    @Test
    fun sanitizeArcadeNameFallsBackToTheDefaultWhenNothingIsLeft() {
        assertEquals("POCKET ARCADE", ArcadeRepository.sanitizeArcadeName(null))
        assertEquals("POCKET ARCADE", ArcadeRepository.sanitizeArcadeName(""))
        assertEquals("POCKET ARCADE", ArcadeRepository.sanitizeArcadeName("    "))
        assertEquals("POCKET ARCADE", ArcadeRepository.sanitizeArcadeName("!@#$%"))
        assertEquals("POCKET ARCADE", ArcadeRepository.DEFAULT_ARCADE_NAME)
        assertTrue(ArcadeRepository.DEFAULT_ARCADE_NAME.length <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)
    }

    @Test
    fun sanitizeArcadeNameIsIdempotent() {
        for (raw in listOf("hello world", "  x  ", "A B C D E F G H I J K", "%%%", "POCKET ARCADE")) {
            val once = ArcadeRepository.sanitizeArcadeName(raw)
            assertEquals(once, ArcadeRepository.sanitizeArcadeName(once))
        }
    }

    // ---- score tables

    private fun table(vararg scores: Int) = scores.mapIndexed { i, s -> ScoreEntry("P${i}A", s) }

    @Test
    fun rankForFindsThePlaceInASortedTable() {
        val entries = table(900, 500, 300)
        assertEquals(0, ScoreTables.rankFor(entries, 1_000))
        assertEquals(1, ScoreTables.rankFor(entries, 600))
        assertEquals(2, ScoreTables.rankFor(entries, 400))
        assertEquals(3, ScoreTables.rankFor(entries, 100))
        assertEquals(0, ScoreTables.rankFor(emptyList(), 1))
    }

    @Test
    fun rankForPutsATieBelowTheEarlierScore() {
        val entries = table(900, 500, 300)
        assertEquals(1, ScoreTables.rankFor(entries, 900))
        assertEquals(2, ScoreTables.rankFor(entries, 500))
        assertEquals(3, ScoreTables.rankFor(entries, 300))
    }

    @Test
    fun rankForTurnsAwayScoresThatMissAFullTable() {
        val full = table(500, 400, 300, 200, 100)
        assertEquals(-1, ScoreTables.rankFor(full, 99))
        assertEquals(-1, ScoreTables.rankFor(full, 100))
        assertEquals(4, ScoreTables.rankFor(full, 101))
        assertEquals(0, ScoreTables.rankFor(full, 501))
    }

    @Test
    fun rankForTurnsAwayScoresAtOrBelowZero() {
        assertEquals(-1, ScoreTables.rankFor(emptyList(), 0))
        assertEquals(-1, ScoreTables.rankFor(emptyList(), -3))
        assertEquals(-1, ScoreTables.rankFor(table(5), Int.MIN_VALUE))
    }

    @Test
    fun rankForCopesWithATableThatIsNotSorted() {
        assertEquals(1, ScoreTables.rankFor(table(100, 900, 300), 600))
    }

    @Test
    fun insertPlacesTheEntryAndCutsToFive() {
        val full = table(500, 400, 300, 200, 100)
        val inserted = ScoreTables.insert(full, ScoreEntry("NEW", 350))
        assertEquals(listOf(500, 400, 350, 300, 200), inserted.map { it.score })
        assertEquals("NEW", inserted[2].initials)
        // A miss leaves the table as it was.
        assertEquals(full, ScoreTables.insert(full, ScoreEntry("LOW", 50)))
        assertEquals(listOf(ScoreEntry("ONE", 7)), ScoreTables.insert(emptyList(), ScoreEntry("ONE", 7)))
    }

    @Test
    fun scoreTablesRoundTrip() {
        val entries = listOf(ScoreEntry("ABC", 900), ScoreEntry("X9Z", 50))
        assertEquals("ABC:900,X9Z:50", ScoreTables.encode(entries))
        assertEquals(entries, ScoreTables.decode(ScoreTables.encode(entries)))
        assertEquals("", ScoreTables.encode(emptyList()))
    }

    @Test
    fun decodeSkipsBadPartsSortsAndCutsToFive() {
        assertEquals(emptyList<ScoreEntry>(), ScoreTables.decode(null))
        assertEquals(emptyList<ScoreEntry>(), ScoreTables.decode(""))
        assertEquals(emptyList<ScoreEntry>(), ScoreTables.decode("   "))
        assertEquals(emptyList<ScoreEntry>(), ScoreTables.decode(",,,:::,:,"))
        val decoded = ScoreTables.decode(
            "AAA:10,junk,BB:20,CCCC:30,dd1:40,EEE:x,FFF:0,GGG:-5,HHH:99999999999,,III:50,J K:60,LLL:70,MMM:5,NNN:80,OOO:90",
        )
        // The lower-case "dd1", the two-letter and four-letter initials and the bad numbers all go.
        assertEquals(listOf("OOO", "NNN", "LLL", "III", "AAA"), decoded.map { it.initials })
        assertEquals(listOf(90, 80, 70, 50, 10), decoded.map { it.score })
    }

    @Test
    fun decodeKeepsTiesInTheOrderTheyWereSaved() {
        assertEquals(listOf("AAA", "BBB", "CCC"), ScoreTables.decode("AAA:5,BBB:5,CCC:5").map { it.initials })
    }

    // ---- initials

    @Test
    fun sanitizeInitialsGivesExactlyThreeCharactersOfLettersAndDigits() {
        assertEquals("ABC", ScoreTables.sanitizeInitials("abc"))
        assertEquals("ABC", ScoreTables.sanitizeInitials("a-b c!"))
        assertEquals("ABC", ScoreTables.sanitizeInitials("abcdef"))
        assertEquals("A9Z", ScoreTables.sanitizeInitials("a9z"))
        assertEquals("BAA", ScoreTables.sanitizeInitials("b"))
        assertEquals("XYA", ScoreTables.sanitizeInitials(" x y "))
        assertEquals("AAA", ScoreTables.sanitizeInitials(""))
        assertEquals("AAA", ScoreTables.sanitizeInitials(null))
        assertEquals("AAA", ScoreTables.sanitizeInitials("!?-"))
        assertEquals(ScoreTables.DEFAULT_INITIALS, ScoreTables.sanitizeInitials("   "))
        for (raw in listOf("", "q", "qwerty", "  9 ", "ééé", "\n\t")) {
            val out = ScoreTables.sanitizeInitials(raw)
            assertEquals(3, out.length)
            assertTrue(out.all { it in 'A'..'Z' || it in '0'..'9' })
        }
    }
}
