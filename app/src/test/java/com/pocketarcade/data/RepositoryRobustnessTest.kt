package com.pocketarcade.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** A save that is damaged or can't be written must never crash the app, and never lose a token twice. */
class RepositoryRobustnessTest : RepositoryTestBase() {
    /** Bytes no protobuf reader can parse: a tag that never ends. */
    private fun corruptTheFile() {
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })
    }

    /** A disk that reads fine but never accepts a write (the edit's function still runs). */
    private class WriteFailsStore(private val real: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> get() = real.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            transform(real.data.first())
            throw IOException("disk full")
        }
    }

    /** A disk that fails every read and write. */
    private class BrokenStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disk gone") }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("disk gone")
    }

    // ---- a corrupt save file

    @Test
    fun aCorruptFileIsReplacedSoReadsGiveDefaultsAndWritesSucceed() = blocking {
        corruptTheFile()
        val repo = ArcadeRepository(newStore())
        assertEquals(SaveState(loaded = true), repo.state.first())
        // The very first thing the app does at launch.
        assertEquals(0, repo.applyDailyRefill(today = 20_000))
        assertTrue(repo.spendToken())
        repo.addTickets(12)
        val s = repo.state.first()
        assertEquals(ArcadeRepository.STARTING_TOKENS - 1, s.tokens)
        assertEquals(12, s.tickets)
        assertEquals(20_000L, s.lastRefillDay)
    }

    @Test
    fun aCorruptFileThatIsReplacedStaysFixedAcrossARelaunch() = blocking {
        corruptTheFile()
        ArcadeRepository(newStore()).apply {
            applyDailyRefill(today = 20_000)
            addTickets(5)
        }
        relaunch()
        // A second store over the same file (the next launch) reads what was written after the reset.
        val s = ArcadeRepository(newStore()).state.first()
        assertEquals(5, s.tickets)
        assertEquals(20_000L, s.lastRefillDay)
    }

    @Test
    fun withoutTheHandlerACorruptFileStillReadsDefaultsAndWritesFailQuietly() = blocking {
        corruptTheFile()
        val repo = ArcadeRepository(newStore(handler = null))
        // The read flow falls back to defaults; every write throws a CorruptionException inside
        // DataStore, which the repository turns into "couldn't do it" instead of crashing.
        assertEquals(SaveState(loaded = true), repo.state.first())
        assertEquals(0, repo.applyDailyRefill(today = 20_000))
        assertFalse(repo.spendToken())
        assertFalse(repo.exchangeTicketsForToken())
        assertFalse(repo.recordScore("racer", 10))
        repo.addTickets(3)
        repo.refundToken()
        repo.addPrize("bear")
        repo.setMuted(true)
    }

    // ---- a disk that won't take a write

    @Test
    fun aFailedWriteReportsFailureEvenThoughTheEditsFunctionRan() = blocking {
        val repo = ArcadeRepository(WriteFailsStore(newStore()))
        assertFalse(repo.spendToken())
        assertTrue(repo.spendTickets(0)) // nothing to save
        assertFalse(repo.spendTickets(1))
        assertFalse(repo.exchangeTicketsForToken())
        assertFalse(repo.buy(Catalog.hats.first()))
        assertFalse(repo.recordScore("racer", 10))
        assertFalse(repo.claimSpareToken(now = 1_000))
        assertFalse(repo.unlock("first_win"))
        assertEquals(-1, repo.recordScoreEntry("racer", 10, "ABC"))
        assertEquals(0, repo.applyDailyRefill(today = 20_000))
        // These have no result to report; they only must not throw.
        repo.refundToken()
        repo.addTickets(4)
        repo.addPrize("bear")
        repo.addStat("plays:racer")
        repo.addCollectible("fish:trout")
        repo.setArcadeName("Fun House")
        repo.equip(Catalog.hats.first())
        repo.setMuted(true)
        repo.setFirstPerson(true)
        // Nothing landed.
        assertEquals(SaveState(loaded = true), repo.state.first())
    }

    @Test
    fun aDiskThatFailsEverythingNeverThrowsFromTheRepository() = blocking {
        val repo = ArcadeRepository(BrokenStore())
        assertEquals(SaveState(loaded = true), repo.state.first())
        assertFalse(repo.spendToken())
        assertEquals(0, repo.applyDailyRefill(today = 1))
        repo.addTickets(1)
        repo.setMuted(true)
    }

    // ---- atomic spending

    @Test
    fun twoConcurrentSpendsOfTheOnlyTokenSucceedExactlyOnce() = blocking {
        val store = newStore()
        val repo = ArcadeRepository(store)
        repeat(10) { round ->
            store.edit { it.clear() }
            store.seed(tokens = 1)
            val results = coroutineScope {
                listOf(
                    async(Dispatchers.Default) { repo.spendToken() },
                    async(Dispatchers.Default) { repo.spendToken() },
                ).awaitAll()
            }
            assertEquals("round $round", 1, results.count { it })
            val s = repo.state.first()
            assertEquals(0, s.tokens)
            assertEquals(1, s.totalPlays)
        }
    }

    @Test
    fun manyConcurrentSpendsNeverOverdrawTheTokensOrTickets() = blocking {
        val repo = ArcadeRepository(newStore().also { it.seed(tokens = 5, tickets = 10) })
        val (tokens, tickets) = coroutineScope {
            val t = (1..20).map { async(Dispatchers.Default) { repo.spendToken() } }
            val k = (1..20).map { async(Dispatchers.Default) { repo.spendTickets(3) } }
            t.awaitAll() to k.awaitAll()
        }
        assertEquals(5, tokens.count { it })
        assertEquals(3, tickets.count { it })
        val s = repo.state.first()
        assertEquals(0, s.tokens)
        assertEquals(1, s.tickets)
    }

    @Test
    fun theSavedKeysKeepTheirNamesSoExistingSavesStillLoad() = blocking {
        val store = newStore()
        store.edit { p ->
            p[KEY_TOKENS] = 7
            p[KEY_TICKETS] = 123
            p[KEY_COLLECTION] = "bear:2"
        }
        val s = ArcadeRepository(store).state.first()
        assertEquals(7, s.tokens)
        assertEquals(123, s.tickets)
        assertEquals(mapOf("bear" to 2), s.collection)
    }
}
