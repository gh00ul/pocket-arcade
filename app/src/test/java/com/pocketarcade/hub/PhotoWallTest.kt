package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.share.PhotoStore
import com.pocketarcade.share.PhotoStrip
import com.pocketarcade.share.StripLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo wall by the booth: four tall posters (the last four strips kept) that fit the wall
 * beside the booth, clear of the neon strip, the other posters and the signs, and that keep the
 * shape of a strip, in one shared texture.
 */
class PhotoWallTest {
    private val maps = listOf(
        HubLayout.build(GameRegistry.createAll(), emptySet()),
        HubLayout.build(GameRegistry.createAll(), DecorStyle.entries.toSet()),
    )
    private val wallX = (HubLayout.WIDTH - HubLayout.WALL)

    private fun booth(map: HubMap) = map.props.single { it.kind == PropKind.PHOTO_BOOTH }

    @Test
    fun thereIsOnePosterForEveryStripKept() {
        assertEquals(PhotoStore.KEEP, PhotoWallLayout.SLOTS)
        assertEquals(4, PhotoWallLayout.SLOTS)
        assertEquals(PhotoWallLayout.SLOTS * PhotoWallLayout.POSTER_W, PhotoWallLayout.TEX_W)
        assertEquals(PhotoWallLayout.POSTER_H, PhotoWallLayout.TEX_H)
    }

    @Test
    fun aPosterKeepsTheShapeOfAStrip() {
        val l = StripLayout(PhotoStrip.FRAME)
        val strip = l.width.toDouble() / l.height
        val texture = PhotoWallLayout.POSTER_W.toDouble() / PhotoWallLayout.POSTER_H
        val world = PhotoWallLayout.WORLD_W.toDouble() / (PhotoWallLayout.Y1 - PhotoWallLayout.Y0)
        assertEquals("strip $strip, texture $texture", strip, texture, 0.01 * strip)
        assertEquals("strip $strip, on the wall $world", strip, world, 0.03 * strip)
    }

    @Test
    fun theTexturesPostersAreSideBySideWithoutOverlap() {
        for (slot in 0 until PhotoWallLayout.SLOTS) {
            val x = PhotoWallLayout.texX(slot)
            assertTrue(x >= 0 && x + PhotoWallLayout.POSTER_W <= PhotoWallLayout.TEX_W)
            if (slot > 0) assertEquals(PhotoWallLayout.texX(slot - 1) + PhotoWallLayout.POSTER_W, x)
        }
    }

    @Test
    fun theBlankFramesAreTheOnesWithoutAStrip() {
        assertEquals(listOf(false, false, false, false), PhotoWallLayout.filled(0).toList())
        assertEquals(listOf(true, false, false, false), PhotoWallLayout.filled(1).toList())
        assertEquals(listOf(true, true, true, false), PhotoWallLayout.filled(3).toList())
        assertEquals(listOf(true, true, true, true), PhotoWallLayout.filled(4).toList())
        // More strips than posters (they're never kept) doesn't overflow.
        assertEquals(listOf(true, true, true, true), PhotoWallLayout.filled(9).toList())
    }

    @Test
    fun theBoothStandsByTheWallItHangsOn() {
        for (map in maps) {
            val b = booth(map)
            assertTrue("the booth is ${wallX - b.x1} from the wall", wallX - b.x1 in 0f..PhotoWallLayout.REACH)
        }
    }

    @Test
    fun theWallBandHangsOverTheBoothAndClearOfTheNeonStripAndSigns() {
        for (map in maps) {
            val b = booth(map)
            val z0 = PhotoWallLayout.startZ(b)
            val z1 = z0 + PhotoWallLayout.WORLD_LENGTH
            // Over the booth's own length, above its top, below the neon strip along the wall's top.
            assertTrue("the wall band $z0..$z1 sticks out past the booth ${b.z0}..${b.z1}", z0 >= b.z0 - 1f && z1 <= b.z1 + 1f)
            assertTrue("the posters are behind the booth's top (${b.height})", PhotoWallLayout.Y0 > b.height + 4f)
            assertTrue(PhotoWallLayout.Y1 < PhotoWallLayout.SIGN_Y0)
            assertTrue("the sign runs into the neon strip", PhotoWallLayout.SIGN_Y1 <= HubLayout.WALL_HEIGHT - 10f)
            // Each poster is inside the band, in order and apart.
            for (slot in 0 until PhotoWallLayout.SLOTS) {
                val a = PhotoWallLayout.posterZ(b, slot)
                assertTrue(a >= z0 - 0.01f && a + PhotoWallLayout.WORLD_W <= z1 + 0.01f)
                if (slot > 0) assertEquals(PhotoWallLayout.WORLD_GAP, a - (PhotoWallLayout.posterZ(b, slot - 1) + PhotoWallLayout.WORLD_W), 1e-3f)
            }
            // The other posters and signs on the right wall are elsewhere along it.
            for (pz in HubLayout.rightPosters) {
                assertFalse("the poster at z $pz runs into the photo wall", pz + 12f > z0 && pz - 12f < z1)
            }
            for (sign in HubLayout.wallSigns) {
                if (!sign.right) continue
                assertFalse("the ${sign.text} sign runs into the photo wall", sign.z1 > z0 && sign.z0 < z1)
            }
        }
    }
}
