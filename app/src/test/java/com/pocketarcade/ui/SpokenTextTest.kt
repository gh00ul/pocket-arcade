package com.pocketarcade.ui

import com.pocketarcade.engine.ArcadeFont
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a screen reader is told for the game's painted text. */
class SpokenTextTest {
    @Test
    fun wordsAreLoweredSoTheyAreReadNotSpelled() {
        assertEquals("quit round", spokenText("QUIT ROUND"))
        assertEquals("paused", spokenText("PAUSED"))
    }

    @Test
    fun lineBreaksBecomeSpaces() {
        assertEquals("play again 1 token", spokenText("PLAY AGAIN\n1 TOKEN"))
        assertEquals("no tokens left", spokenText("NO TOKENS\nLEFT"))
    }

    @Test
    fun thePlayTriangleIsDroppedAndOtherSymbolsAreNamed() {
        assertEquals("start", spokenText("${ArcadeFont.PLAY} START"))
        assertEquals("star new high score! star", spokenText("${ArcadeFont.STAR} NEW HIGH SCORE! ${ArcadeFont.STAR}"))
        assertEquals("you have tickets 12", spokenText("YOU HAVE ${ArcadeFont.TICKET}12"))
        assertEquals("buy tickets 40", spokenText("BUY ${ArcadeFont.TICKET}40"))
        assertEquals("token 5 tokens tickets 9 tickets", spokenText("${ArcadeFont.TOKEN} 5 TOKENS ${ArcadeFont.TICKET} 9 TICKETS"))
        assertEquals("hold left right to move the claw", spokenText("HOLD ${ArcadeFont.LEFT} ${ArcadeFont.RIGHT} TO MOVE THE CLAW"))
        assertEquals("flick up", spokenText("FLICK ${ArcadeFont.UP}"))
        assertEquals("tap to drop down", spokenText("TAP TO DROP ${ArcadeFont.DOWN}"))
        assertEquals("best with sound on", spokenText("BEST WITH SOUND ON ${ArcadeFont.NOTE}"))
    }

    @Test
    fun aBareSymbolIsStillSaid() {
        assertEquals("heart", spokenText("${ArcadeFont.HEART}"))
        assertEquals("", spokenText(""))
        assertEquals("", spokenText("${ArcadeFont.PLAY}"))
    }
}
