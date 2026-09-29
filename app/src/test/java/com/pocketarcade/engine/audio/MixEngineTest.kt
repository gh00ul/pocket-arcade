package com.pocketarcade.engine.audio

import com.pocketarcade.engine.Sfx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The mixer core, driven a block at a time with no thread and no AudioTrack. */
class MixEngineTest {
    private val rate = 48000
    private val out = ShortArray(MixEngine.BLOCK * 2)
    private lateinit var engine: MixEngine

    @Before
    fun setUp() {
        engine = MixEngine(rate)
        engine.bank.generateAll()
        // Only the sound effects under test: no ambience.
        engine.ambientTarget = 0f
    }

    /** The peak of each channel over [blocks] rendered blocks. */
    private fun peaks(blocks: Int): FloatArray {
        val peak = FloatArray(2)
        repeat(blocks) {
            engine.render(out)
            for (i in 0 until MixEngine.BLOCK) {
                peak[0] = maxOf(peak[0], abs(out[2 * i].toInt()).toFloat())
                peak[1] = maxOf(peak[1], abs(out[2 * i + 1].toInt()).toFloat())
            }
        }
        return peak
    }

    @Test
    fun anUnpannedSoundComesOutEqualInBothChannels() {
        engine.play(Sfx.COIN, 1f, 1f)
        val left = ArrayList<Short>()
        val right = ArrayList<Short>()
        repeat(10) {
            engine.render(out)
            for (i in 0 until MixEngine.BLOCK) {
                left += out[2 * i]
                right += out[2 * i + 1]
            }
        }
        assertEquals(left, right)
        assertTrue("silent", left.any { abs(it.toInt()) > 500 })
    }

    @Test
    fun aSoundFromTheRightIsLouderInTheRightChannel() {
        engine.setListener(300f, 500f, Math.PI.toFloat())
        engine.playAt(Sfx.COIN, 380f, 500f, 1f, 1f)
        val p = peaks(10)
        assertTrue("right ${p[1]} should beat left ${p[0]}", p[1] > p[0] * 4f)
        assertTrue(p[1] > 500f)
    }

    @Test
    fun aSoundOutOfEarshotIsNeverStarted() {
        engine.setListener(0f, 0f, 0f)
        engine.playAt(Sfx.JACKPOT, 0f, Spatial.MAX_DISTANCE + 50f, 1f, 1f)
        engine.render(out)
        assertEquals(0, engine.activeVoices())
    }

    @Test
    fun mutedMakesSilenceAndDropsTheVoicesSoTheyDontBurstOutLater() {
        engine.play(Sfx.JACKPOT, 1f, 1f)
        engine.render(out)
        assertTrue(engine.activeVoices() > 0)
        engine.muted = true
        engine.play(Sfx.JACKPOT, 1f, 1f) // refused
        assertEquals(0f, peaks(2).max(), 0f)
        assertEquals(0, engine.activeVoices())
        engine.muted = false
        assertEquals("nothing left to play", 0f, peaks(2).max(), 0f)
    }

    @Test
    fun volumeSettingsScaleTheSoundEffects() {
        engine.play(Sfx.COIN, 1f, 1f)
        val full = peaks(40)[0]
        engine.sfxVolume = 0.25f
        engine.play(Sfx.COIN, 1f, 1f)
        val quiet = peaks(40)[0]
        assertEquals(full * 0.25f, quiet, full * 0.03f)
        engine.sfxVolume = 0f
        engine.play(Sfx.COIN, 1f, 1f)
        assertEquals(0f, peaks(40).max(), 0f)
    }

    @Test
    fun theVoiceCountStaysCappedAndStolenVoicesFadeOutInsteadOfClicking() {
        // 28 voices of a steady buffer at a small gain give a steady level.
        val steady = FloatArray(rate * 4) { 1f }
        val gain = 0.02f
        repeat(MixEngine.MAX_VOICES) { engine.startVoiceOf(steady, gain, gain, 0f, 1f, Priority.NORMAL) }
        engine.render(out)
        val before = out[MixEngine.BLOCK * 2 - 2].toInt()
        // One more, silent: it has to steal one, which must fade rather than vanish.
        engine.startVoiceOf(steady, 0f, 0f, 0f, 1f, Priority.NORMAL)
        assertTrue(engine.activeVoices() <= MixEngine.MAX_VOICES + MixEngine.DYING_SLOTS)
        engine.render(out)
        var worstStep = 0
        var prev = before
        for (i in 0 until MixEngine.BLOCK) {
            val s = out[2 * i].toInt()
            worstStep = maxOf(worstStep, abs(s - prev))
            prev = s
        }
        val oneVoice = gain * MixEngine.MASTER * MixEngine.FULL_SCALE
        assertTrue("dropped a voice in one step ($worstStep of ${oneVoice.toInt()})", worstStep < oneVoice / 20f)
        // ...and it is gone by the end of the block.
        val after = out[MixEngine.BLOCK * 2 - 2].toInt()
        assertTrue("level should have dropped by one voice: $before -> $after", before - after > oneVoice * 0.7f)
        assertEquals(MixEngine.MAX_VOICES, engine.activeVoices())
    }

    @Test
    fun aLessImportantSoundNeverStealsAMoreImportantOne() {
        val steady = FloatArray(rate * 4) { 1f }
        repeat(MixEngine.MAX_VOICES) { engine.startVoiceOf(steady, 0.01f, 0.01f, 0f, 1f, Priority.KEY) }
        engine.startVoiceOf(steady, 0.5f, 0.5f, 0f, 1f, Priority.AMBIENT)
        engine.render(out)
        // Level is still the 28 key voices': the ambient one was refused.
        val level = out[MixEngine.BLOCK * 2 - 2].toInt()
        val expected = 0.01f * MixEngine.MAX_VOICES * MixEngine.MASTER * MixEngine.FULL_SCALE
        assertEquals(expected, level.toFloat(), expected * 0.06f)
    }

    @Test
    fun aFloodOfRequestsIsAbsorbedWithoutError() {
        repeat(MixEngine.QUEUE_CAP * 3) { engine.play(Sfx.STEP, 1f, 1f) }
        engine.render(out)
        assertTrue(engine.activeVoices() in 1..MixEngine.MAX_VOICES + MixEngine.DYING_SLOTS)
        // Everything is finished a few blocks later.
        repeat(20) { engine.render(out) }
        assertEquals(0, engine.activeVoices())
    }

    @Test
    fun outputStaysWithinSixteenBitsWhenEverythingPlaysAtOnce() {
        for (sfx in Sfx.entries) engine.play(sfx, 1f, 1f)
        repeat(30) {
            engine.render(out)
            for (s in out) assertTrue(abs(s.toInt()) <= 32000)
        }
    }

    @Test
    fun ambienceIsStereoAndDecorrelatedAndRespectsItsVolume() {
        engine.ambientTarget = 1f
        // Let the level ease up.
        repeat(300) { engine.render(out) }
        val l = FloatArray(MixEngine.BLOCK * 20)
        val r = FloatArray(MixEngine.BLOCK * 20)
        for (b in 0 until 20) {
            engine.render(out)
            for (i in 0 until MixEngine.BLOCK) {
                l[b * MixEngine.BLOCK + i] = out[2 * i].toFloat()
                r[b * MixEngine.BLOCK + i] = out[2 * i + 1].toFloat()
            }
        }
        val rmsL = rms(l)
        assertTrue("ambience should be audible: $rmsL", rmsL > 30f)
        assertTrue("and not deafening: $rmsL", rmsL < 4000f)
        // Two ears, two murmurs: not identical.
        var diff = 0f
        for (i in l.indices) diff += abs(l[i] - r[i])
        assertTrue("channels are identical", diff / l.size > rmsL * 0.05f)
        engine.ambienceVolume = 0f
        repeat(2) { engine.render(out) }
        assertEquals(0f, peaks(3).max(), 0f)
    }

    private fun rms(a: FloatArray): Float {
        var s = 0.0
        for (v in a) s += v * v
        return sqrt(s / a.size).toFloat()
    }
}
