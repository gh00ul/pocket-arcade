package com.pocketarcade.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hall's camera choice survives a save and a reload, and a fresh save starts overhead. */
class FirstPersonPrefTest {
    @Test
    fun theViewChoiceRoundTrips() {
        val p = mutablePreferencesOf()
        assertFalse(ArcadeRepository.read(p).firstPerson)
        ArcadeRepository.writeFirstPerson(p, true)
        val loaded = ArcadeRepository.read(p)
        assertTrue(loaded.firstPerson)
        assertTrue(loaded.loaded)
        // Other settings are untouched.
        assertFalse(loaded.muted)
        ArcadeRepository.writeFirstPerson(p, false)
        assertFalse(ArcadeRepository.read(p).firstPerson)
    }
}
