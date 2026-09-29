package com.pocketarcade.ui

import com.pocketarcade.data.Catalog
import com.pocketarcade.data.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the prize counter classifies its prizes: rarity by price, and the state each card shows. */
class PrizeCounterTest {
    @Test
    fun rarityClimbsWithThePriceAndHasEveryTierInTheCatalog() {
        var last = Rarity.COMMON
        for (price in 0..800 step 10) {
            val r = rarityOf(price)
            assertTrue("$price went from $last down to $r", r >= last)
            last = r
        }
        assertEquals(Rarity.COMMON, rarityOf(0))
        assertEquals(Rarity.LEGENDARY, rarityOf(600))
        val seen = Catalog.all.map { rarityOf(it.price) }.toSet()
        assertEquals("every tier is worth having in the shop", Rarity.entries.toSet(), seen)
    }

    @Test
    fun theStartingOutfitIsCommonAndTheBiggestPrizesAreLegendary() {
        assertEquals(Rarity.COMMON, rarityOf(Catalog.outfits.first().price))
        for (top in Catalog.all.filter { it.price >= 450 }) assertEquals(Rarity.LEGENDARY, rarityOf(top.price))
    }

    @Test
    fun aDecorationYouOwnIsPlacedAndOthersAreWornOrOwned() {
        assertEquals(PrizeState.PLACED, prizeState(ItemKind.DECOR, 200, owned = true, worn = false, tickets = 0))
        assertEquals(PrizeState.WORN, prizeState(ItemKind.HAT, 200, owned = true, worn = true, tickets = 0))
        assertEquals(PrizeState.OWNED, prizeState(ItemKind.HAT, 200, owned = true, worn = false, tickets = 0))
        assertEquals(PrizeState.WORN, prizeState(ItemKind.OUTFIT, 0, owned = true, worn = true, tickets = 0))
    }

    @Test
    fun anUnownedPrizeIsAffordableLockedOrFree() {
        assertEquals(PrizeState.AFFORDABLE, prizeState(ItemKind.HAT, 100, owned = false, worn = false, tickets = 100))
        assertEquals(PrizeState.LOCKED, prizeState(ItemKind.HAT, 100, owned = false, worn = false, tickets = 99))
        assertEquals(PrizeState.FREE, prizeState(ItemKind.OUTFIT, 0, owned = false, worn = false, tickets = 0))
    }
}
