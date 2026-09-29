package com.pocketarcade.games.pinball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The backbox's dot-matrix display: its font, how text fits, and the dots it bakes. No graphics needed. */
class PinballDisplayTest {
    private val lettersAndSymbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!.,-+:?*<> "

    private fun lit(m: DotMatrix): Int = m.lit.count { it > 0f }

    @Test
    fun theFontCoversEveryCharacterTheDisplayCanShow() {
        for (c in lettersAndSymbols) assertTrue("no glyph for '$c'", DotMatrix.hasGlyph(c))
        // Lower case is set in capitals.
        assertTrue(DotMatrix.hasGlyph('q'))
        val game = PinballGame()
        val texts = game.botDmdStrings() + listOf("TILT", "NO BONUS", "MULTIBALL", "BALL SAVE", "12,345,678")
        for (t in texts) for (c in t) assertTrue("'$c' in \"$t\" has no glyph", DotMatrix.hasGlyph(c))
    }

    @Test
    fun glyphsAreTrimmedToTheirInk() {
        val m = DotMatrix()
        assertEquals(5, m.textWidth("A"))
        assertEquals(3, m.textWidth("I"))
        assertEquals(1, m.textWidth("!"))
        assertEquals(1, m.textWidth("."))
        assertEquals(3, m.textWidth(" "))
        // One dot between letters, and everything doubles at scale 2.
        assertEquals(5 + 1 + 5, m.textWidth("AB"))
        assertEquals(2 * (5 + 1 + 5), m.textWidth("AB", 2))
        assertEquals(0, m.textWidth(""))
    }

    @Test
    fun aLetterLightsExactlyItsDots() {
        val m = DotMatrix()
        m.text("A", 10, 10, 1, 1f)
        assertEquals(18, lit(m))
        m.clear()
        m.text("A", 10, 10, 2, 1f)
        assertEquals(18 * 4, lit(m))
        m.clear()
        // Text hanging off the grid is clipped, never an error.
        m.text("MULTIBALL", DotMatrix.COLS - 6, DotMatrix.ROWS - 3, 3, 1f)
        m.text("X", -20, -20, 4, 1f)
        assertTrue(lit(m) > 0)
    }

    @Test
    fun everyMessageFitsTheDisplayAtSomeSize() {
        val m = DotMatrix()
        val game = PinballGame()
        for (t in game.botDmdStrings()) {
            val scale = m.fitScale(t, DotMatrix.COLS - 8, 3)
            assertTrue("\"$t\" is ${m.textWidth(t, 1)} dots wide at the smallest size", m.textWidth(t, scale) <= DotMatrix.COLS - 8)
            assertTrue(scale >= 1)
        }
        // The big ones really do get the big size.
        assertEquals(3, m.fitScale("SHOOT!", DotMatrix.COLS - 8, 3))
    }

    @Test
    fun bakingMakesRoundBrightDotsOnADarkGrid() {
        val m = DotMatrix()
        m.dot(5, 5, 1f)
        m.bake()
        val px = m.texture.pixels
        val stride = DotMatrix.COLS * DotMatrix.CELL
        fun luma(gx: Int, gy: Int): Int {
            val c = px[(gy * DotMatrix.CELL + DotMatrix.CELL / 2) * stride + gx * DotMatrix.CELL + DotMatrix.CELL / 2]
            return (c shr 16 and 255) + (c shr 8 and 255) + (c and 255)
        }
        assertTrue("a lit dot should outshine an unlit one", luma(5, 5) > luma(6, 5) * 4)
        for (c in px) assertEquals("every pixel is opaque", 255, c ushr 24)
        // The gap between dots is darker than the dot itself.
        val corner = px[(5 * DotMatrix.CELL) * stride + 5 * DotMatrix.CELL]
        assertTrue(((corner shr 16 and 255) + (corner shr 8 and 255)) < luma(5, 5))
        assertTrue("baking marks the texture changed", m.texture.version > 0)
    }

    @Test
    fun theDisplayPaintsEveryScreenAndOnlyRepaintsWhenSomethingChanges() {
        val d = PinballDmd()
        val m = d.matrix
        // Playing: the score, ball and multiplier.
        d.update(1f, "12,340", "BALL 2", "3X", null, 0f, 1.6f, 0, multiball = false, saving = false, tilted = false)
        assertTrue(lit(m) > 60)
        val v = m.texture.version
        d.update(1f, "12,340", "BALL 2", "3X", null, 0f, 1.6f, 0, multiball = false, saving = false, tilted = false)
        assertEquals("nothing changed, so it is not repainted", v, m.texture.version)
        d.update(1f, "12,350", "BALL 2", "3X", null, 0f, 1.6f, 0, multiball = false, saving = false, tilted = false)
        assertNotEquals("a new score repaints", v, m.texture.version)
        // A message, a big moment, a tilt, ball save and multiball all put something on the grid.
        val game = PinballGame()
        for (t in game.botDmdStrings()) {
            for (hot in 0..2) {
                d.update(2f + hot, "0", "BALL 1", "1X", t, 0.5f, 1.6f, hot, multiball = false, saving = false, tilted = false)
                assertTrue("\"$t\" (hot $hot) drew nothing", lit(m) > 0)
            }
        }
        d.update(9f, "0", "BALL 1", "1X", null, 0f, 1.6f, 0, multiball = false, saving = false, tilted = true)
        assertTrue(lit(m) > 0)
        d.update(10f, "0", "BALL 1", "1X", null, 0f, 1.6f, 0, multiball = true, saving = false, tilted = false)
        assertTrue(lit(m) > 0)
        d.update(11f, "0", "BALL 1", "1X", null, 0f, 1.6f, 0, multiball = false, saving = true, tilted = false)
        assertTrue(lit(m) > 0)
        assertFalse(m.lit.any { it.isNaN() })
    }
}
