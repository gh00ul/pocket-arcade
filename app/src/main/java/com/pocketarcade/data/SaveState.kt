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
) {
    fun highScore(gameId: String): Int = highScores[gameId] ?: 0
    fun owns(itemId: String): Boolean = itemId in owned
    val ownedDecor: Set<DecorStyle>
        get() = Catalog.decor.filter { it.id in owned }.mapNotNull { it.decor }.toSet()
}
