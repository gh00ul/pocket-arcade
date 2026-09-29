package com.pocketarcade.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The token, ticket, shop, refill and score rules, run on a real DataStore in a temp file. */
class ArcadeRepositoryTest : RepositoryTestBase() {
    private fun hat(id: String) = Catalog.hats.first { it.id == id }
    private fun outfit(id: String) = Catalog.outfits.first { it.id == id }
    private fun decor(id: String) = Catalog.decor.first { it.id == id }

    @Test
    fun aFreshSaveReadsTheStartingDefaults() = blocking {
        val repo = ArcadeRepository(newStore())
        assertEquals(SaveState(loaded = true), repo.state.first())
    }

    // ---- tokens

    @Test
    fun spendTokenTakesOneTokenAndCountsThePlay() = blocking {
        val repo = ArcadeRepository(newStore())
        assertTrue(repo.spendToken())
        val s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS - 1, s.tokens)
        assertEquals(1, s.totalPlays)
    }

    @Test
    fun spendTokenWithNoTokensFailsAndChangesNothing() = blocking {
        val store = newStore().also { it.seed(tokens = 0) }
        val repo = ArcadeRepository(store)
        assertFalse(repo.spendToken())
        val s = repo.state.first()
        assertEquals(0, s.tokens)
        assertEquals(0, s.totalPlays)
    }

    @Test
    fun theLastTokenCanBeSpent() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tokens = 1) })
        assertTrue(repo.spendToken())
        assertFalse(repo.spendToken())
        assertEquals(0, repo.state.first().tokens)
    }

    @Test
    fun refundTokenGivesTheTokenAndThePlayBack() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.spendToken()
        repo.refundToken()
        val s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS, s.tokens)
        assertEquals(0, s.totalPlays)
    }

    @Test
    fun refundTokenNeverTakesTheCountOfPlaysBelowZero() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.refundToken()
        val s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS + 1, s.tokens)
        assertEquals(0, s.totalPlays)
    }

    @Test
    fun exchangeTicketsForTokenNeedsTheFullPrice() = blocking {
        val store = newStore().also { it.seed(tokens = 3, tickets = ArcadeRepository.TICKETS_PER_TOKEN - 1) }
        val repo = ArcadeRepository(store)
        assertFalse(repo.exchangeTicketsForToken())
        var s = repo.state.first()
        assertEquals(3, s.tokens)
        assertEquals(ArcadeRepository.TICKETS_PER_TOKEN - 1, s.tickets)

        repo.addTickets(1)
        assertTrue(repo.exchangeTicketsForToken())
        s = repo.state.first()
        assertEquals(4, s.tokens)
        assertEquals(0, s.tickets)
    }

    @Test
    fun exchangeTicketsKeepsTheChange() = blocking {
        val store = newStore().also { it.seed(tokens = 0, tickets = 100) }
        val repo = ArcadeRepository(store)
        assertTrue(repo.exchangeTicketsForToken())
        val s = repo.state.first()
        assertEquals(1, s.tokens)
        assertEquals(100 - ArcadeRepository.TICKETS_PER_TOKEN, s.tickets)
    }

    @Test
    fun addTicketsIgnoresZeroAndNegativeAmounts() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.addTickets(0)
        repo.addTickets(-5)
        assertEquals(0, repo.state.first().tickets)
        repo.addTickets(7)
        repo.addTickets(3)
        assertEquals(10, repo.state.first().tickets)
    }

    // ---- the shop

    @Test
    fun buyingAHatPaysEquipsAndOwnsIt() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 100) })
        val cap = hat("hat_cap")
        assertTrue(repo.buy(cap))
        val s = repo.state.first()
        assertEquals(100 - cap.price, s.tickets)
        assertTrue(s.owns(cap.id))
        assertEquals(cap.id, s.hat)
    }

    @Test
    fun buyingAnOutfitEquipsIt() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 100) })
        val blue = outfit("outfit_blue")
        assertTrue(repo.buy(blue))
        val s = repo.state.first()
        assertEquals(blue.id, s.outfit)
        assertEquals("", s.hat)
    }

    @Test
    fun buyingDecorOwnsItButEquipsNothing() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 100) })
        val palm = decor("decor_palm")
        assertTrue(repo.buy(palm))
        val s = repo.state.first()
        assertTrue(s.owns(palm.id))
        assertEquals("", s.hat)
        assertEquals(Catalog.DEFAULT_OUTFIT, s.outfit)
        assertEquals(setOf(DecorStyle.PALM), s.ownedDecor)
    }

    @Test
    fun buyingSomethingAlreadyOwnedFailsAndCostsNothing() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 200) })
        val cap = hat("hat_cap")
        assertTrue(repo.buy(cap))
        val ticketsAfter = repo.state.first().tickets
        assertFalse(repo.buy(cap))
        assertEquals(ticketsAfter, repo.state.first().tickets)
    }

    @Test
    fun buyingWhatYouCannotAffordFailsAndChangesNothing() = blocking {
        val cap = hat("hat_cap")
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = cap.price - 1) })
        assertFalse(repo.buy(cap))
        val s = repo.state.first()
        assertEquals(cap.price - 1, s.tickets)
        assertFalse(s.owns(cap.id))
        assertEquals("", s.hat)
    }

    @Test
    fun anItemCanBeBoughtWithExactlyItsPrice() = blocking {
        val cap = hat("hat_cap")
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = cap.price) })
        assertTrue(repo.buy(cap))
        assertEquals(0, repo.state.first().tickets)
    }

    @Test
    fun equipSwitchesHatsTakesTheWornOneOffAndIgnoresUnownedItems() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tickets = 500) })
        val cap = hat("hat_cap")
        val beanie = hat("hat_beanie")
        repo.buy(cap)
        repo.buy(beanie)
        assertEquals(beanie.id, repo.state.first().hat)
        repo.equip(cap)
        assertEquals(cap.id, repo.state.first().hat)
        repo.equip(cap)
        assertEquals("", repo.state.first().hat)
        repo.equip(hat("hat_halo"))
        assertEquals("", repo.state.first().hat)
    }

    // ---- the daily refill

    @Test
    fun theFirstLaunchStartsTheClockWithoutGrantingTokens() = blocking {
        val repo = ArcadeRepository(newStore())
        assertEquals(0, repo.applyDailyRefill(today = 20_000))
        val s = repo.state.first()
        assertEquals(20_000L, s.lastRefillDay)
        assertEquals(ArcadeRepository.STARTING_TOKENS, s.tokens)
    }

    @Test
    fun aNewDayGrantsTheDailyTokensOnce() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.applyDailyRefill(today = 20_000)
        assertEquals(ArcadeRepository.DAILY_TOKENS, repo.applyDailyRefill(today = 20_001))
        var s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS + ArcadeRepository.DAILY_TOKENS, s.tokens)
        assertEquals(20_001L, s.lastRefillDay)
        // The same day again, and the days after a long absence, grant one bonus each visit.
        assertEquals(0, repo.applyDailyRefill(today = 20_001))
        assertEquals(ArcadeRepository.DAILY_TOKENS, repo.applyDailyRefill(today = 20_100))
        s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS + 2 * ArcadeRepository.DAILY_TOKENS, s.tokens)
    }

    @Test
    fun theSameDayGrantsNothing() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.applyDailyRefill(today = 20_000)
        assertEquals(0, repo.applyDailyRefill(today = 20_000))
        assertEquals(ArcadeRepository.STARTING_TOKENS, repo.state.first().tokens)
    }

    @Test
    fun aClockMovedBackwardsResyncsWithoutGranting() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.applyDailyRefill(today = 20_000)
        assertEquals(0, repo.applyDailyRefill(today = 19_990))
        val s = repo.state.first()
        assertEquals(19_990L, s.lastRefillDay)
        assertEquals(ArcadeRepository.STARTING_TOKENS, s.tokens)
    }

    // ---- the spare token

    @Test
    fun aSpareTokenIsOnlyForTheCompletelyBroke() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tokens = 1) })
        assertFalse(repo.claimSpareToken(now = 1_000))
        assertEquals(1, repo.state.first().tokens)
    }

    @Test
    fun aSpareTokenComesBackOnlyAfterTheCooldown() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tokens = 0) })
        val cooldown = ArcadeRepository.SPARE_TOKEN_COOLDOWN_MS
        assertTrue(repo.claimSpareToken(now = 1_000))
        var s = repo.state.first()
        assertEquals(1, s.tokens)
        assertEquals(1_000 + cooldown, s.spareTokenAt)

        repo.spendToken()
        assertFalse(repo.claimSpareToken(now = 1_000 + cooldown - 1))
        assertEquals(0, repo.state.first().tokens)
        assertTrue(repo.claimSpareToken(now = 1_000 + cooldown))
        s = repo.state.first()
        assertEquals(1, s.tokens)
        assertEquals(1_000 + 2 * cooldown, s.spareTokenAt)
    }

    // ---- scores and prizes

    @Test
    fun recordScoreKeepsOnlyTheBestPerMachine() = blocking {
        val repo = ArcadeRepository(newStore())
        assertFalse(repo.recordScore("racer", 0))
        assertTrue(repo.recordScore("racer", 50))
        assertFalse(repo.recordScore("racer", 50))
        assertFalse(repo.recordScore("racer", 40))
        assertTrue(repo.recordScore("racer", 60))
        assertTrue(repo.recordScore("claw", 5))
        val s = repo.state.first()
        assertEquals(60, s.highScore("racer"))
        assertEquals(5, s.highScore("claw"))
        assertEquals(0, s.highScore("pinball"))
    }

    @Test
    fun addPrizeCountsEachPlushAndTheCollectionSurvivesAReload() = blocking {
        val store = newStore()
        val repo = ArcadeRepository(store)
        repo.addPrize("bear")
        repo.addPrize("bear")
        repo.addPrize("cat")
        assertEquals(mapOf("bear" to 2, "cat" to 1), repo.state.first().collection)
        // A relaunch reads the same collection back from the file.
        relaunch()
        assertEquals(mapOf("bear" to 2, "cat" to 1), ArcadeRepository(newStore()).state.first().collection)
    }

    @Test
    fun addPrizeKeepsTheGoodPartsOfADamagedCollection() = blocking {
        val store = newStore()
        store.edit { it[KEY_COLLECTION] = "bear:2;;junk;cat:x;:4" }
        val repo = ArcadeRepository(store)
        repo.addPrize("bear")
        repo.addPrize("dino")
        assertEquals(mapOf("bear" to 3, "dino" to 1), repo.state.first().collection)
    }

    @Test
    fun collectionEncodingRoundTripsAndDropsEmptyCounts() {
        val map = mapOf("bear" to 3, "cat" to 1, "ghost" to 0, "dino" to -2)
        assertEquals("bear:3;cat:1", ArcadeRepository.encodeCollection(map))
        assertEquals(mapOf("bear" to 3, "cat" to 1), ArcadeRepository.decodeCollection(ArcadeRepository.encodeCollection(map)))
        assertEquals("", ArcadeRepository.encodeCollection(emptyMap()))
    }

    @Test
    fun decodeCollectionSkipsGarbageAndKeepsTheRest() {
        assertEquals(emptyMap<String, Int>(), ArcadeRepository.decodeCollection(null))
        assertEquals(emptyMap<String, Int>(), ArcadeRepository.decodeCollection(""))
        assertEquals(emptyMap<String, Int>(), ArcadeRepository.decodeCollection("   "))
        assertEquals(emptyMap<String, Int>(), ArcadeRepository.decodeCollection(";;;:::;"))
        val decoded = ArcadeRepository.decodeCollection(";;bear:2;noColon;cat:notANumber;:9;dino:99999999999;fox:1;a:b:3;")
        assertEquals(2, decoded["bear"])
        assertEquals(1, decoded["fox"])
        // The last colon splits, so an id may hold colons of its own.
        assertEquals(3, decoded["a:b"])
        assertNull(decoded["noColon"])
        assertNull(decoded["cat"])
        assertNull(decoded["dino"])
        assertNull(decoded[""])
    }

    @Test
    fun mutedAndFirstPersonAreRemembered() = blocking {
        val repo = ArcadeRepository(newStore())
        repo.setMuted(true)
        repo.setFirstPerson(true)
        var s = repo.state.first()
        assertTrue(s.muted)
        assertTrue(s.firstPerson)
        repo.setMuted(false)
        s = repo.state.first()
        assertFalse(s.muted)
        assertTrue(s.firstPerson)
    }
}
