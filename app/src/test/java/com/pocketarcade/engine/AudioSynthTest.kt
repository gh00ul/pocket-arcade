package com.pocketarcade.engine

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Every sound effect must actually be synthesized: audible, finite and not clipping. */
class AudioSynthTest {
    @Test
    fun everySfxHasASound() {
        val synth = AudioSynth()
        synth.generateAll()
        for (sfx in Sfx.entries) {
            val s = synth.samples(sfx)
            assertNotNull("$sfx has no sound", s)
            s!!
            assertTrue("$sfx is empty", s.size > 100)
            var peak = 0f
            for (v in s) {
                assertTrue("$sfx has a bad sample", v.isFinite())
                peak = maxOf(peak, abs(v))
            }
            assertTrue("$sfx is silent (peak $peak)", peak > 0.02f)
            assertTrue("$sfx clips (peak $peak)", peak <= 0.951f)
        }
    }
}
