package com.pocketarcade.ui

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The design system's pure pieces: the type and spacing scales, shrink-to-fit and the token twinkle. */
class UiThemeTest {
    @Test
    fun theTypeScaleDescendsFromDisplayToCaption() {
        val sizes = listOf(UiText.DISPLAY, UiText.TITLE, UiText.HEADING, UiText.BODY).map { it.unit.value }
        assertEquals(sizes.sortedDescending(), sizes)
        assertEquals(sizes.distinct(), sizes)
        // The small condensed styles are the spaced-out ones; big words are set tight.
        assertTrue(UiText.LABEL.tiny && UiText.CAPTION.tiny)
        assertTrue(UiText.LABEL.tracking > UiText.BODY.tracking)
        assertTrue(UiText.CAPTION.unit.value < UiText.LABEL.unit.value)
        assertEquals(0f, UiText.DISPLAY.tracking, 0f)
    }

    @Test
    fun theSpacingAndRadiusScalesClimb() {
        val space = listOf(UiSpace.xs, UiSpace.sm, UiSpace.md, UiSpace.lg, UiSpace.xl).map { it.value }
        assertEquals(space.sorted(), space)
        assertEquals(space.distinct(), space)
        val edges = listOf(UiEdge.hair, UiEdge.line, UiEdge.strong).map { it.value }
        assertEquals(edges.sorted(), edges)
        // Boxes sit inside panels, cards inside boxes, chips inside cards.
        assertTrue(UiRadius.panel > UiRadius.box && UiRadius.box > UiRadius.card && UiRadius.card > UiRadius.chip)
    }

    @Test
    fun shrinkToFitOnlyEverShrinks() {
        assertEquals(1f, shrinkScale(100, 200), 0f)
        assertEquals(1f, shrinkScale(200, 200), 0f)
        assertEquals(0.5f, shrinkScale(200, 100), 1e-6f)
        assertEquals(1f, shrinkScale(500, Constraints.Infinity), 0f)
        assertEquals(1f, shrinkScale(0, 10), 0f)
        // Squeezed to nothing it goes to nothing rather than dividing by zero.
        assertEquals(0f, shrinkScale(50, 0), 0f)
    }

    @Test
    fun theTokenGlintRestsThenSwellsAndFades() {
        assertEquals(0.5f, twinkle(0f), 1e-6f)
        assertEquals(0.5f, twinkle(0.5f), 1e-6f)
        assertEquals(0.5f, twinkle(1f), 1e-6f)
        var peak = 0f
        for (i in 0..200) {
            val v = twinkle(i / 200f)
            assertTrue("glint $v out of range", v in 0.5f..1.0001f)
            peak = maxOf(peak, v)
        }
        assertTrue("the glint should reach nearly full size (peak $peak)", peak > 0.98f)
    }
}
