package com.pocketarcade.data

/** One line of a machine's top-5 table: three-character [initials] and the [score] they set. */
data class ScoreEntry(val initials: String, val score: Int)

/**
 * The per-machine top-5 score tables: ranking, initials and the string encoding stored in the
 * save. All pure, so it is unit-tested without a DataStore.
 */
object ScoreTables {
    /** How many entries a machine's table keeps. */
    const val SIZE = 5

    const val DEFAULT_INITIALS = "AAA"
    private const val INITIALS_LENGTH = 3

    private fun isInitial(c: Char) = c in 'A'..'Z' || c in '0'..'9'

    /**
     * Cleans typed initials to exactly three characters of A-Z and 0-9: upper-cased, anything else
     * dropped, and padded with `A` if short (so blank is [DEFAULT_INITIALS]).
     */
    fun sanitizeInitials(raw: String?): String =
        raw.orEmpty().uppercase().filter(::isInitial).take(INITIALS_LENGTH).padEnd(INITIALS_LENGTH, 'A')

    /**
     * The place (0 is first) [score] would take in [entries], or -1 if it wouldn't make the table:
     * it is not above zero, or five entries beat or tie it (a tie keeps the earlier entry ahead).
     */
    fun rankFor(entries: List<ScoreEntry>, score: Int): Int {
        if (score <= 0) return -1
        val rank = entries.count { it.score >= score }
        return if (rank < SIZE) rank else -1
    }

    /** [entries] with [entry] placed where [rankFor] says, best first and cut to [SIZE]. */
    fun insert(entries: List<ScoreEntry>, entry: ScoreEntry): List<ScoreEntry> {
        val sorted = entries.sortedByDescending { it.score }
        val rank = rankFor(sorted, entry.score)
        if (rank < 0) return sorted.take(SIZE)
        return sorted.toMutableList().apply { add(rank, entry) }.take(SIZE)
    }

    /** `AAA:100,BBB:50`: initials and score pairs, best first. */
    fun encode(entries: List<ScoreEntry>): String =
        entries.joinToString(",") { "${it.initials}:${it.score}" }

    /**
     * Reads what [encode] wrote. A bad part (wrong initials, a score that isn't above zero, no
     * colon) is skipped rather than failing the whole table; the rest is sorted and cut to [SIZE].
     */
    fun decode(raw: String?): List<ScoreEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { part ->
            val idx = part.lastIndexOf(':')
            if (idx <= 0) return@mapNotNull null
            val initials = part.substring(0, idx)
            if (initials.length != INITIALS_LENGTH || !initials.all(::isInitial)) return@mapNotNull null
            val score = part.substring(idx + 1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            ScoreEntry(initials, score)
        }.sortedByDescending { it.score }.take(SIZE)
    }
}
