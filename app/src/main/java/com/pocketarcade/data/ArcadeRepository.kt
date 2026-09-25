package com.pocketarcade.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

private val Context.arcadeStore: DataStore<Preferences> by preferencesDataStore(name = "pocket_arcade")

/**
 * The single source of truth for progress: tokens, tickets, the prize collection, cosmetics,
 * per-machine high scores and the daily token refill. Every mutation is one atomic DataStore edit.
 */
class ArcadeRepository(context: Context) {
    companion object {
        const val STARTING_TOKENS = 20
        const val DAILY_TOKENS = 10
        /** Tickets needed to buy one token at the token machine. */
        const val TICKETS_PER_TOKEN = 40
        /** While broke, a spare token can be claimed this often. */
        const val SPARE_TOKEN_COOLDOWN_MS = 3 * 60 * 1000L

        private val TOKENS = intPreferencesKey("tokens")
        private val TICKETS = intPreferencesKey("tickets")
        private val LAST_REFILL_DAY = longPreferencesKey("last_refill_day")
        private val OWNED = stringSetPreferencesKey("owned")
        private val HAT = stringPreferencesKey("hat")
        private val OUTFIT = stringPreferencesKey("outfit")
        private val COLLECTION = stringPreferencesKey("collection")
        private val MUTED = booleanPreferencesKey("muted")
        private val SPARE_AT = longPreferencesKey("spare_token_at")
        private val TOTAL_PLAYS = intPreferencesKey("total_plays")
        private const val HIGH_SCORE_PREFIX = "hs_"

        private fun highScoreKey(gameId: String) = intPreferencesKey(HIGH_SCORE_PREFIX + gameId)

        fun encodeCollection(map: Map<String, Int>): String =
            map.entries.filter { it.value > 0 }.joinToString(";") { "${it.key}:${it.value}" }

        fun decodeCollection(raw: String?): Map<String, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(';').mapNotNull { part ->
                val idx = part.lastIndexOf(':')
                if (idx <= 0) return@mapNotNull null
                val count = part.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
                part.substring(0, idx) to count
            }.toMap()
        }
    }

    private val store = context.applicationContext.arcadeStore

    val state: Flow<SaveState> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> read(p) }

    private fun read(p: Preferences): SaveState {
        val highScores = p.asMap().entries
            .filter { it.key.name.startsWith(HIGH_SCORE_PREFIX) }
            .associate { it.key.name.removePrefix(HIGH_SCORE_PREFIX) to ((it.value as? Int) ?: 0) }
        return SaveState(
            loaded = true,
            tokens = p[TOKENS] ?: STARTING_TOKENS,
            tickets = p[TICKETS] ?: 0,
            lastRefillDay = p[LAST_REFILL_DAY] ?: 0L,
            owned = (p[OWNED] ?: emptySet()) + Catalog.DEFAULT_OUTFIT,
            hat = p[HAT] ?: "",
            outfit = p[OUTFIT] ?: Catalog.DEFAULT_OUTFIT,
            collection = decodeCollection(p[COLLECTION]),
            highScores = highScores,
            muted = p[MUTED] ?: false,
            spareTokenAt = p[SPARE_AT] ?: 0L,
            totalPlays = p[TOTAL_PLAYS] ?: 0,
        )
    }

    private fun MutablePreferences.tokens() = this[TOKENS] ?: STARTING_TOKENS
    private fun MutablePreferences.tickets() = this[TICKETS] ?: 0

    /**
     * Grants the daily tokens if the calendar day changed since the last refill.
     * The very first launch just starts the clock (the player begins with [STARTING_TOKENS]).
     * Returns the number of tokens granted.
     */
    suspend fun applyDailyRefill(today: Long = LocalDate.now().toEpochDay()): Int {
        var granted = 0
        store.edit { p ->
            val last = p[LAST_REFILL_DAY]
            if (last == null) {
                p[LAST_REFILL_DAY] = today
                p[TOKENS] = p.tokens()
            } else if (today > last) {
                granted = DAILY_TOKENS
                p[TOKENS] = p.tokens() + DAILY_TOKENS
                p[LAST_REFILL_DAY] = today
            } else if (today < last) {
                // Clock moved backwards: resync without granting.
                p[LAST_REFILL_DAY] = today
            }
        }
        return granted
    }

    /** Spends one token; returns false if the player has none. */
    suspend fun spendToken(): Boolean {
        var ok = false
        store.edit { p ->
            val t = p.tokens()
            if (t > 0) {
                p[TOKENS] = t - 1
                p[TOTAL_PLAYS] = (p[TOTAL_PLAYS] ?: 0) + 1
                ok = true
            }
        }
        return ok
    }

    suspend fun refundToken() {
        store.edit { p ->
            p[TOKENS] = p.tokens() + 1
            p[TOTAL_PLAYS] = ((p[TOTAL_PLAYS] ?: 1) - 1).coerceAtLeast(0)
        }
    }

    suspend fun addTickets(n: Int) {
        if (n <= 0) return
        store.edit { p -> p[TICKETS] = p.tickets() + n }
    }

    /** Stores [score] if it beats the machine's record. Returns true on a new high score. */
    suspend fun recordScore(gameId: String, score: Int): Boolean {
        var beaten = false
        store.edit { p ->
            val key = highScoreKey(gameId)
            val old = p[key] ?: 0
            if (score > old) {
                p[key] = score
                beaten = true
            }
        }
        return beaten
    }

    suspend fun addPrize(plushId: String) {
        store.edit { p ->
            val map = decodeCollection(p[COLLECTION]).toMutableMap()
            map[plushId] = (map[plushId] ?: 0) + 1
            p[COLLECTION] = encodeCollection(map)
        }
    }

    /** Buys [item] with tickets and equips it (hats/outfits). Returns false if unaffordable or owned. */
    suspend fun buy(item: ShopItem): Boolean {
        var ok = false
        store.edit { p ->
            val owned = p[OWNED] ?: emptySet()
            val tickets = p.tickets()
            if (item.id !in owned && tickets >= item.price) {
                p[TICKETS] = tickets - item.price
                p[OWNED] = owned + item.id
                when (item.kind) {
                    ItemKind.HAT -> p[HAT] = item.id
                    ItemKind.OUTFIT -> p[OUTFIT] = item.id
                    ItemKind.DECOR -> Unit
                }
                ok = true
            }
        }
        return ok
    }

    /** Equips an owned hat or outfit; equipping the worn hat again takes it off. */
    suspend fun equip(item: ShopItem) {
        store.edit { p ->
            val owned = (p[OWNED] ?: emptySet()) + Catalog.DEFAULT_OUTFIT
            if (item.id !in owned) return@edit
            when (item.kind) {
                ItemKind.HAT -> p[HAT] = if (p[HAT] == item.id) "" else item.id
                ItemKind.OUTFIT -> p[OUTFIT] = item.id
                ItemKind.DECOR -> Unit
            }
        }
    }

    suspend fun exchangeTicketsForToken(): Boolean {
        var ok = false
        store.edit { p ->
            val tickets = p.tickets()
            if (tickets >= TICKETS_PER_TOKEN) {
                p[TICKETS] = tickets - TICKETS_PER_TOKEN
                p[TOKENS] = p.tokens() + 1
                ok = true
            }
        }
        return ok
    }

    /** Gives a free token when the player is completely out and the cooldown has passed. */
    suspend fun claimSpareToken(now: Long = System.currentTimeMillis()): Boolean {
        var ok = false
        store.edit { p ->
            val at = p[SPARE_AT] ?: 0L
            if (p.tokens() == 0 && now >= at) {
                p[TOKENS] = 1
                p[SPARE_AT] = now + SPARE_TOKEN_COOLDOWN_MS
                ok = true
            }
        }
        return ok
    }

    suspend fun setMuted(muted: Boolean) {
        store.edit { p -> p[MUTED] = muted }
    }
}
