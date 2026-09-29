package com.pocketarcade.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
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

private const val TAG = "ArcadeRepository"

private val Context.arcadeStore: DataStore<Preferences> by preferencesDataStore(
    name = "pocket_arcade",
    corruptionHandler = ArcadeRepository.corruptionHandler,
)

/**
 * The single source of truth for progress: tokens, tickets, the prize collection, cosmetics,
 * per-machine high scores and score tables, the daily token refill, and the stats, unlocks and
 * collectibles later features build on. Every mutation is one atomic DataStore edit.
 *
 * A write that fails on disk never crashes the app: the mutators catch the [IOException], log it
 * and report it as "couldn't do it" (false, or nothing done), so callers need no error handling.
 *
 * The store is injected so tests can run against a temp file; `ArcadeRepository(context)` uses
 * the app's own.
 */
class ArcadeRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.arcadeStore)

    companion object {
        const val STARTING_TOKENS = 20
        const val DAILY_TOKENS = 10
        /** Tickets needed to buy one token at the token machine. */
        const val TICKETS_PER_TOKEN = 40
        /** While broke, a spare token can be claimed this often. */
        const val SPARE_TOKEN_COOLDOWN_MS = 3 * 60 * 1000L

        /** The name over the door until the player picks one. */
        const val DEFAULT_ARCADE_NAME = "POCKET ARCADE"
        const val MAX_ARCADE_NAME_LENGTH = 14

        /**
         * A save file that can't be parsed is replaced by an empty one instead of failing every read
         * and write for good: the player starts afresh, but the app runs.
         */
        val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

        // These names are the saved file's format: never rename one.
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
        private val FIRST_PERSON = booleanPreferencesKey("first_person")
        private val STATS = stringPreferencesKey("stats")
        private val UNLOCKED = stringPreferencesKey("unlocked")
        private val COLLECTIBLES = stringPreferencesKey("collectibles")
        private val ARCADE_NAME = stringPreferencesKey("arcade_name")
        private const val HIGH_SCORE_PREFIX = "hs_"
        private const val SCORES_PREFIX = "scores_"

        private fun highScoreKey(gameId: String) = intPreferencesKey(HIGH_SCORE_PREFIX + gameId)
        private fun scoresKey(gameId: String) = stringPreferencesKey(SCORES_PREFIX + gameId)

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

        /** `plays:racer:12;tickets:earned:340`: each key (which may hold colons) then its count. */
        fun encodeStats(map: Map<String, Long>): String =
            map.entries.filter { it.value > 0 }.joinToString(";") { "${it.key}:${it.value}" }

        /** Reads what [encodeStats] wrote, skipping parts with no key or a count that isn't above zero. */
        fun decodeStats(raw: String?): Map<String, Long> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(';').mapNotNull { part ->
                val idx = part.lastIndexOf(':')
                if (idx <= 0) return@mapNotNull null
                val key = part.substring(0, idx).trim()
                val count = part.substring(idx + 1).trim().toLongOrNull()?.takeIf { it > 0 }
                if (key.isEmpty() || count == null) null else key to count
            }.toMap()
        }

        /** `a;b;c`: ids in the order they were added. */
        fun encodeIds(ids: Set<String>): String = ids.joinToString(";")

        /** Reads what [encodeIds] wrote, skipping blank parts. */
        fun decodeIds(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw.split(';').mapNotNullTo(LinkedHashSet()) { it.trim().ifEmpty { null } }
        }

        /**
         * A free-form id or key, trimmed; null if it is blank or holds `;`, which separates the
         * saved entries.
         */
        private fun cleanId(id: String): String? = id.trim().takeIf { it.isNotEmpty() && ';' !in it }

        /**
         * Cleans a typed arcade name: upper-cased, only A-Z, 0-9 and spaces kept, trimmed, cut to
         * [MAX_ARCADE_NAME_LENGTH]; blank becomes [DEFAULT_ARCADE_NAME].
         */
        fun sanitizeArcadeName(raw: String?): String =
            raw.orEmpty().uppercase()
                .filter { it in 'A'..'Z' || it in '0'..'9' || it == ' ' }
                .trim()
                .take(MAX_ARCADE_NAME_LENGTH)
                .trimEnd()
                .ifEmpty { DEFAULT_ARCADE_NAME }

        /** Stores the hall's camera choice: first person (true) or overhead. */
        fun writeFirstPerson(p: MutablePreferences, on: Boolean) {
            p[FIRST_PERSON] = on
        }

        /** Everything saved, as the app sees it. */
        fun read(p: Preferences): SaveState {
            val highScores = p.asMap().entries
                .filter { it.key.name.startsWith(HIGH_SCORE_PREFIX) }
                .associate { it.key.name.removePrefix(HIGH_SCORE_PREFIX) to ((it.value as? Int) ?: 0) }
            val scoreTables = HashMap<String, List<ScoreEntry>>()
            for ((key, value) in p.asMap()) {
                if (!key.name.startsWith(SCORES_PREFIX)) continue
                val entries = ScoreTables.decode(value as? String)
                if (entries.isNotEmpty()) scoreTables[key.name.removePrefix(SCORES_PREFIX)] = entries
            }
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
                firstPerson = p[FIRST_PERSON] ?: false,
                stats = decodeStats(p[STATS]),
                unlocked = decodeIds(p[UNLOCKED]),
                collectibles = decodeCollection(p[COLLECTIBLES]),
                arcadeName = sanitizeArcadeName(p[ARCADE_NAME]),
                scoreTables = scoreTables,
            )
        }
    }

    val state: Flow<SaveState> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> read(p) }

    private fun MutablePreferences.tokens() = this[TOKENS] ?: STARTING_TOKENS
    private fun MutablePreferences.tickets() = this[TICKETS] ?: 0

    /**
     * Applies [transform] as one atomic edit. A disk failure is logged and reported as false; a
     * caller that recorded a result inside [transform] must not trust it then, since nothing landed.
     */
    private suspend fun tryEdit(transform: (MutablePreferences) -> Unit): Boolean =
        try {
            store.edit { p -> transform(p) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save progress", e)
            false
        }

    /**
     * Grants the daily tokens if the calendar day changed since the last refill.
     * The very first launch just starts the clock (the player begins with [STARTING_TOKENS]).
     * Returns the number of tokens granted.
     */
    suspend fun applyDailyRefill(today: Long = LocalDate.now().toEpochDay()): Int {
        var granted = 0
        val saved = tryEdit { p ->
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
        return if (saved) granted else 0
    }

    /** Spends one token; returns false if the player has none (or the save failed). */
    suspend fun spendToken(): Boolean {
        var ok = false
        val saved = tryEdit { p ->
            val t = p.tokens()
            if (t > 0) {
                p[TOKENS] = t - 1
                p[TOTAL_PLAYS] = (p[TOTAL_PLAYS] ?: 0) + 1
                ok = true
            }
        }
        return saved && ok
    }

    suspend fun refundToken() {
        tryEdit { p ->
            p[TOKENS] = p.tokens() + 1
            p[TOTAL_PLAYS] = ((p[TOTAL_PLAYS] ?: 1) - 1).coerceAtLeast(0)
        }
    }

    suspend fun addTickets(n: Int) {
        if (n <= 0) return
        tryEdit { p -> p[TICKETS] = p.tickets() + n }
    }

    /** Spends [n] tickets atomically; returns false, changing nothing, if the player has fewer. */
    suspend fun spendTickets(n: Int): Boolean {
        if (n < 0) return false
        if (n == 0) return true
        var ok = false
        val saved = tryEdit { p ->
            val tickets = p.tickets()
            if (tickets >= n) {
                p[TICKETS] = tickets - n
                ok = true
            }
        }
        return saved && ok
    }

    /** Stores [score] if it beats the machine's record. Returns true on a new high score. */
    suspend fun recordScore(gameId: String, score: Int): Boolean {
        var beaten = false
        val saved = tryEdit { p ->
            val key = highScoreKey(gameId)
            val old = p[key] ?: 0
            if (score > old) {
                p[key] = score
                beaten = true
            }
        }
        return saved && beaten
    }

    /**
     * Enters [score] with [initials] (cleaned to three characters) in the machine's top-5 table if
     * it makes it. Returns its place (0 is first) or -1 if it didn't make the table. This is
     * separate from [recordScore], which keeps the single high score.
     */
    suspend fun recordScoreEntry(gameId: String, score: Int, initials: String): Int {
        if (gameId.isBlank() || score <= 0) return -1
        var rank = -1
        val saved = tryEdit { p ->
            val key = scoresKey(gameId)
            val entries = ScoreTables.decode(p[key])
            val place = ScoreTables.rankFor(entries, score)
            if (place >= 0) {
                val entry = ScoreEntry(ScoreTables.sanitizeInitials(initials), score)
                p[key] = ScoreTables.encode(ScoreTables.insert(entries, entry))
                rank = place
            }
        }
        return if (saved) rank else -1
    }

    suspend fun addPrize(plushId: String) {
        tryEdit { p ->
            val map = decodeCollection(p[COLLECTION]).toMutableMap()
            map[plushId] = (map[plushId] ?: 0) + 1
            p[COLLECTION] = encodeCollection(map)
        }
    }

    /**
     * Adds [delta] to the lifetime counter [key], such as "plays:racer" or "tickets:earned". A
     * counter never goes below zero. A blank key, or one holding `;`, is ignored.
     */
    suspend fun addStat(key: String, delta: Long = 1) {
        val clean = cleanId(key) ?: return
        if (delta == 0L) return
        tryEdit { p ->
            val map = decodeStats(p[STATS]).toMutableMap()
            val old = map[clean] ?: 0L
            // Saturate rather than wrap around if a counter ever reaches the top.
            val sum = if (delta > 0 && old > Long.MAX_VALUE - delta) Long.MAX_VALUE else old + delta
            map[clean] = sum.coerceAtLeast(0L)
            p[STATS] = encodeStats(map)
        }
    }

    /** Unlocks [id]. Returns true only when it was newly unlocked (not before, and saved). */
    suspend fun unlock(id: String): Boolean {
        val clean = cleanId(id) ?: return false
        var fresh = false
        val saved = tryEdit { p ->
            val ids = decodeIds(p[UNLOCKED])
            if (clean !in ids) {
                p[UNLOCKED] = encodeIds(ids + clean)
                fresh = true
            }
        }
        return saved && fresh
    }

    /**
     * Counts one more of the collectible [id], namespaced like "fish:trout". This is separate from
     * the plush collection ([addPrize]). A blank id, or one holding `;`, is ignored.
     */
    suspend fun addCollectible(id: String) {
        val clean = cleanId(id) ?: return
        tryEdit { p ->
            val map = decodeCollection(p[COLLECTIBLES]).toMutableMap()
            val old = map[clean] ?: 0
            map[clean] = if (old < Int.MAX_VALUE) old + 1 else old
            p[COLLECTIBLES] = encodeCollection(map)
        }
    }

    /** Renames the arcade; [raw] is cleaned by [sanitizeArcadeName], and a blank name resets it. */
    suspend fun setArcadeName(raw: String) {
        tryEdit { p -> p[ARCADE_NAME] = sanitizeArcadeName(raw) }
    }

    /** Buys [item] with tickets and equips it (hats/outfits). Returns false if unaffordable or owned. */
    suspend fun buy(item: ShopItem): Boolean {
        var ok = false
        val saved = tryEdit { p ->
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
        return saved && ok
    }

    /** Equips an owned hat or outfit; equipping the worn hat again takes it off. */
    suspend fun equip(item: ShopItem) {
        tryEdit { p ->
            val owned = (p[OWNED] ?: emptySet()) + Catalog.DEFAULT_OUTFIT
            if (item.id !in owned) return@tryEdit
            when (item.kind) {
                ItemKind.HAT -> p[HAT] = if (p[HAT] == item.id) "" else item.id
                ItemKind.OUTFIT -> p[OUTFIT] = item.id
                ItemKind.DECOR -> Unit
            }
        }
    }

    suspend fun exchangeTicketsForToken(): Boolean {
        var ok = false
        val saved = tryEdit { p ->
            val tickets = p.tickets()
            if (tickets >= TICKETS_PER_TOKEN) {
                p[TICKETS] = tickets - TICKETS_PER_TOKEN
                p[TOKENS] = p.tokens() + 1
                ok = true
            }
        }
        return saved && ok
    }

    /** Gives a free token when the player is completely out and the cooldown has passed. */
    suspend fun claimSpareToken(now: Long = System.currentTimeMillis()): Boolean {
        var ok = false
        val saved = tryEdit { p ->
            val at = p[SPARE_AT] ?: 0L
            if (p.tokens() == 0 && now >= at) {
                p[TOKENS] = 1
                p[SPARE_AT] = now + SPARE_TOKEN_COOLDOWN_MS
                ok = true
            }
        }
        return saved && ok
    }

    suspend fun setMuted(muted: Boolean) {
        tryEdit { p -> p[MUTED] = muted }
    }

    /** Remembers whether the hall is walked in first person. */
    suspend fun setFirstPerson(on: Boolean) {
        tryEdit { p -> writeFirstPerson(p, on) }
    }
}
