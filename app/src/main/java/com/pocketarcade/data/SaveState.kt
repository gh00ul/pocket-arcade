package com.pocketarcade.data

/** Everything the player has earned, as last read from DataStore. */
data class SaveState(
    val loaded: Boolean = false,
    val tokens: Int = ArcadeRepository.STARTING_TOKENS,
    val tickets: Int = 0,
    val lastRefillDay: Long = 0L,
    val owned: Set<String> = setOf(Catalog.DEFAULT_OUTFIT),
    val hat: String = "",
    val outfit: String = Catalog.DEFAULT_OUTFIT,
    val collection: Map<String, Int> = emptyMap(),
    val highScores: Map<String, Int> = emptyMap(),
    val muted: Boolean = false,
    /** Wall-clock millis after which a free spare token can be claimed while broke. */
    val spareTokenAt: Long = 0L,
    val totalPlays: Int = 0,
    /** Whether the hall is walked in first person rather than seen from above. */
    val firstPerson: Boolean = false,
    /** Free-form lifetime counters such as "plays:racer" or "tickets:earned". */
    val stats: Map<String, Long> = emptyMap(),
    /** Ids of things the player has unlocked (achievements and the like), in the order earned. */
    val unlocked: Set<String> = emptySet(),
    /** Namespaced collectibles and how many of each were found, such as "fish:trout". */
    val collectibles: Map<String, Int> = emptyMap(),
    /** The name over the arcade's door: A-Z, 0-9 and spaces, at most 14 characters. */
    val arcadeName: String = ArcadeRepository.DEFAULT_ARCADE_NAME,
    /** Each machine's top-5 table, best first. Machines nobody has scored on are absent. */
    val scoreTables: Map<String, List<ScoreEntry>> = emptyMap(),
) {
    fun highScore(gameId: String): Int = highScores[gameId] ?: 0
    fun owns(itemId: String): Boolean = itemId in owned
    fun stat(key: String): Long = stats[key] ?: 0L
    fun isUnlocked(id: String): Boolean = id in unlocked
    fun collectibleCount(id: String): Int = collectibles[id] ?: 0
    fun scoreTable(gameId: String): List<ScoreEntry> = scoreTables[gameId] ?: emptyList()
    val ownedDecor: Set<DecorStyle>
        get() = Catalog.decor.filter { it.id in owned }.mapNotNull { it.decor }.toSet()
}
