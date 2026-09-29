package com.pocketarcade.hub

import com.pocketarcade.engine.Pal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prompt bubble's pure choices (its geometry needs a real Paint, so it is not tested here). */
class PromptBubbleTest {
    @Test
    fun brightNeonsWearDarkInkOnThePlayPill() {
        for (bright in intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.GREEN)) {
            assertTrue("0x${bright.toString(16)} should take dark ink", PromptBubble.darkInkOn(bright))
        }
    }

    @Test
    fun deepAndSaturatedAccentsKeepWhiteInk() {
        for (deep in intArrayOf(Pal.PINK, Pal.RED, Pal.BLUE, Pal.PURPLE, Pal.NAVY, Pal.TEAL, Pal.VIOLET)) {
            assertFalse("0x${deep.toString(16)} should keep white ink", PromptBubble.darkInkOn(deep))
        }
    }

    @Test
    fun everyMachineGlowGetsReadableInk() {
        // Whichever ink is picked, it must not be the accent's own value: the pill would vanish.
        for (g in com.pocketarcade.games.GameRegistry.createAll()) {
            val dark = PromptBubble.darkInkOn(g.look.glow)
            val lum = run {
                val c = g.look.glow
                0.299f * (c shr 16 and 0xFF) / 255f + 0.587f * (c shr 8 and 0xFF) / 255f + 0.114f * (c and 0xFF) / 255f
            }
            assertTrue("${g.id}: dark ink only on bright glows", dark == (lum > 0.58f))
        }
    }
}
