package com.pocketarcade.engine.audio

import com.pocketarcade.engine.Sfx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The music: every theme builds, is deterministic, loops without a seam, never clips, and crossfades. */
class MusicTest {
    private val rate = 48000
    private val block = 480

    /** The scene that plays [t]: the three fixed themes, otherwise the machine named for it. */
    private fun sceneFor(t: Track): MusicScene = when (t.name) {
        "title" -> MusicScene.Title
        "hall" -> MusicScene.Hall
        "results" -> MusicScene.Results
        else -> MusicScene.Game(t.name)
    }

    private fun samplesPerStep(t: Track) = rate * 60.0 / (t.bpm * 4.0)
    private fun loopSamples(t: Track) = t.steps * samplesPerStep(t)

    /** Renders [samples] frames of [player] into a mono-summed array (left and right stacked: [2*n]). */
    private fun renderPlayer(player: ScorePlayer, samples: Int, intensity: Float): Pair<FloatArray, FloatArray> {
        val l = FloatArray(samples)
        val r = FloatArray(samples)
        val bl = FloatArray(block)
        val br = FloatArray(block)
        val bs = FloatArray(block)
        var done = 0
        while (done < samples) {
            val n = minOf(block, samples - done)
            java.util.Arrays.fill(bl, 0f); java.util.Arrays.fill(br, 0f); java.util.Arrays.fill(bs, 0f)
            player.render(bl, br, bs, n, intensity, 1f, 1f)
            System.arraycopy(bl, 0, l, done, n)
            System.arraycopy(br, 0, r, done, n)
            done += n
        }
        return l to r
    }

    /** Renders [seconds] of a whole [Music] engine. */
    private fun renderMusic(music: Music, seconds: Float, each: (Int) -> Unit = {}): Pair<FloatArray, FloatArray> {
        val total = (seconds * rate).toInt() / block * block
        val l = FloatArray(total)
        val r = FloatArray(total)
        val bl = FloatArray(block)
        val br = FloatArray(block)
        var done = 0
        while (done < total) {
            each(done)
            java.util.Arrays.fill(bl, 0f); java.util.Arrays.fill(br, 0f)
            music.render(bl, br, block)
            System.arraycopy(bl, 0, l, done, block)
            System.arraycopy(br, 0, r, done, block)
            done += block
        }
        return l to r
    }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
        var s = 0.0
        for (i in from until to) s += a[i] * a[i]
        return sqrt(s / (to - from)).toFloat()
    }

    @Test
    fun everyThemeAndStingerBuildsAndIsWellFormed() {
        assertTrue(Tracks.themes.size >= 3)
        assertEquals(Stinger.entries.size, Tracks.stingers.size)
        for (t in Tracks.themes + Tracks.stingers.map { it.track }) {
            assertTrue("${t.name} has no lanes", t.lanes.isNotEmpty())
            assertTrue("${t.name} tempo", t.bpm in 60f..180f)
            assertTrue("${t.name} swing", t.swing in 0f..0.5f)
            for (lane in t.lanes) {
                assertTrue("${t.name} is empty in a lane", lane.count > 0)
                var last = -1
                for (i in 0 until lane.count) {
                    assertTrue("${t.name} unsorted", lane.steps[i] >= last)
                    last = lane.steps[i]
                    assertTrue("${t.name} step out of range", lane.steps[i] in 0 until t.steps)
                    assertTrue("${t.name} note ${lane.notes[i]}", lane.notes[i] in 12..108)
                    assertTrue("${t.name} length", lane.lengths[i] >= 1)
                    assertTrue("${t.name} velocity", lane.velocities[i] in 0f..1f)
                }
            }
            for (l in 1 until Track.LAYERS) assertTrue("${t.name} layer $l", t.layerStart[l] in 0f..1f)
        }
    }

    @Test
    fun everyThemeLoopsAWholeNumberOfSamplesAtAnyCommonRate() {
        // A step that isn't a whole number of samples makes the loop drift against a rate that isn't a multiple; at 48 kHz all are exact.
        for (t in Tracks.themes) {
            val sps = samplesPerStep(t)
            assertEquals("${t.name} step at 48 kHz", Math.rint(sps), sps, 1e-9)
        }
    }

    @Test
    fun sequencerStartsNotesOnTheExactSample() {
        val patch = Patch(wave = Wave.SINE, attack = 0.002f, decay = 0.05f, sustain = 0f, release = 0.05f, gain = 0.5f)
        val t = track("timing", 100f, 1) {
            notes(patch, 0, Triple(0, "A4", 1), Triple(4, "A4", 1), Triple(9, "A4", 1))
        }
        val sps = samplesPerStep(t).toInt()
        val player = ScorePlayer(rate, 8, 1)
        player.start(t, 1f)
        val (l, _) = renderPlayer(player, sps * 12, 1f)
        // Find each onset: the first sample above a small threshold after silence.
        val onsets = ArrayList<Int>()
        var silent = 0
        for (i in l.indices) {
            if (abs(l[i]) > 1e-5f) {
                if (silent > sps / 2 || onsets.isEmpty()) onsets += i
                silent = 0
            } else silent++
        }
        assertEquals(listOf(0, 4 * sps, 9 * sps), onsets)
    }

    @Test
    fun swingDelaysTheOddStepsOnly() {
        val patch = Patch(wave = Wave.SINE, attack = 0.002f, decay = 0.02f, sustain = 0f, release = 0.02f, gain = 0.5f)
        val t = track("swing", 100f, 1, swing = 0.5f) {
            notes(patch, 0, Triple(2, "A4", 1), Triple(3, "A4", 1), Triple(6, "A4", 1))
        }
        val sps = samplesPerStep(t).toInt()
        val player = ScorePlayer(rate, 8, 1)
        player.start(t, 1f)
        val (l, _) = renderPlayer(player, sps * 8, 1f)
        val onsets = ArrayList<Int>()
        var silent = 0
        for (i in l.indices) {
            if (abs(l[i]) > 1e-5f) {
                if (silent > sps / 3 || onsets.isEmpty()) onsets += i
                silent = 0
            } else silent++
        }
        assertEquals(listOf(2 * sps, 3 * sps + sps / 2, 6 * sps), onsets)
    }

    @Test
    fun theSameSeedRendersTheSameSamplesAndADifferentOneDoesNot() {
        for (name in listOf("hall", "racer")) {
            val t = Tracks.themes.first { it.name == name }
            val a = renderPlayer(ScorePlayer(rate, 34, 7).also { it.start(t, 1f) }, rate * 6, 1f)
            val b = renderPlayer(ScorePlayer(rate, 34, 7).also { it.start(t, 1f) }, rate * 6, 1f)
            val c = renderPlayer(ScorePlayer(rate, 34, 8).also { it.start(t, 1f) }, rate * 6, 1f)
            assertTrue("$name is not deterministic", a.first.contentEquals(b.first) && a.second.contentEquals(b.second))
            assertFalse("$name ignores its seed", a.first.contentEquals(c.first))
        }
        // ...and the whole engine, scene changes and all.
        fun run(seed: Int): FloatArray {
            val m = Music(rate, seed)
            m.setScene(MusicScene.Title)
            m.setIntensity(0.7f)
            return renderMusic(m, 5f) { at -> if (at == rate * 2 / block * block) m.setScene(MusicScene.Hall) }.first
        }
        assertTrue(run(3).contentEquals(run(3)))
        assertFalse(run(3).contentEquals(run(4)))
    }

    @Test
    fun everyThemeStaysUnderFullScaleFiniteAndCentred() {
        for (t in Tracks.themes) {
            for (intensity in floatArrayOf(0f, 1f)) {
                val music = Music(rate)
                music.setScene(sceneFor(t))
                music.setIntensity(intensity)
                val (l, r) = renderMusic(music, 12f)
                var peak = 0f
                var sum = 0.0
                for (i in l.indices) {
                    assertTrue("${t.name} produced a bad sample", l[i].isFinite() && r[i].isFinite())
                    peak = maxOf(peak, abs(l[i]), abs(r[i]))
                    sum += l[i] + r[i]
                }
                assertTrue("${t.name} clips (peak $peak)", peak <= 1f)
                assertTrue("${t.name} is too hot for the bus (peak $peak)", peak < 0.75f)
                assertTrue("${t.name} is nearly silent (rms ${rms(l, rate * 3, l.size)})", rms(l, rate * 3, l.size) > 0.02f)
                assertTrue("${t.name} has a DC offset", abs(sum / (2 * l.size)) < 0.005)
            }
        }
    }

    @Test
    fun stingersAreFiniteQuietEnoughAndEnd() {
        for (st in Tracks.stingers) {
            val player = ScorePlayer(rate, 24, 1)
            player.start(st.track, 1f)
            var frames = 0
            val bl = FloatArray(block)
            val br = FloatArray(block)
            val bs = FloatArray(block)
            var peak = 0f
            while (player.active && frames < rate * 15) {
                java.util.Arrays.fill(bl, 0f); java.util.Arrays.fill(br, 0f); java.util.Arrays.fill(bs, 0f)
                player.render(bl, br, bs, block, 1f, 1f, 1f)
                for (i in 0 until block) {
                    assertTrue(bl[i].isFinite() && br[i].isFinite())
                    peak = maxOf(peak, abs(bl[i]), abs(br[i]))
                }
                frames += block
            }
            assertTrue("${st.track.name} never finished", player.finished)
            assertTrue("${st.track.name} lasts too long (${frames / rate.toFloat()} s)", frames < rate * 10)
            assertTrue("${st.track.name} peak $peak", peak < 0.8f && peak > 0.05f)
            assertTrue("${st.track.name} duck", st.duckDepth in 0f..0.9f && st.duckHold in 0.1f..4f)
            assertEquals("${st.track.name} steals", 0, player.steals)
        }
    }

    @Test
    fun everyThemeLoopsSeamlesslyAndTheLoopIsExactlyPeriodic() {
        for (t in Tracks.themes) {
            val loop = loopSamples(t).toInt()
            val player = ScorePlayer(rate, 34, 1)
            player.start(t, 1f)
            val (l, r) = renderPlayer(player, loop * 3, 1f)
            // Loop 1 has no tails from before; from then on every pass is identical, so the joins are the
            // same as the music: no seam.
            var worst = 0f
            for (i in 0 until loop) {
                worst = maxOf(worst, abs(l[loop + i] - l[2 * loop + i]), abs(r[loop + i] - r[2 * loop + i]))
            }
            assertTrue("${t.name}: loop 2 and loop 3 differ by $worst", worst < 2e-4f)
            // And nothing jumps at the join beyond what the music does elsewhere in that pass.
            var biggestElsewhere = 0f
            for (i in 1 until loop) biggestElsewhere = maxOf(biggestElsewhere, abs(l[loop + i] - l[loop + i - 1]))
            var join = 0f
            for (i in loop - 4..loop + 4) join = maxOf(join, abs(l[i] - l[i - 1]))
            assertTrue("${t.name}: seam $join vs the loop's own steepest step $biggestElsewhere", join <= biggestElsewhere * 1.0001f)
            assertEquals("${t.name}: the pool is big enough", 0, player.steals)
            assertTrue("${t.name}: peak voices ${player.peakVoices} leaves no headroom", player.peakVoices <= 30)
        }
    }

    @Test
    fun higherLayersFadeInWithIntensity() {
        val hall = Tracks.themes.first { it.name == "hall" }
        fun render(intensity: Float): FloatArray {
            val p = ScorePlayer(rate, 34, 1)
            p.start(hall, intensity)
            return renderPlayer(p, (loopSamples(hall) * 0.5).toInt(), intensity).first
        }
        val calm = render(0f)
        val mid = render(0.5f)
        val busy = render(1f)
        // Layer 0 is identical in all three (same seed, same notes), so the difference is what the layers add.
        fun added(a: FloatArray, b: FloatArray): Float {
            val d = FloatArray(a.size) { b[it] - a[it] }
            return rms(d)
        }
        assertTrue("layer 1 should add something audible: ${added(calm, mid)}", added(calm, mid) > 0.015f)
        assertTrue("layer 2 should add something audible: ${added(mid, busy)}", added(mid, busy) > 0.008f)
        assertTrue("and the calm mix is the quietest", rms(calm) <= rms(busy) * 1.01f)
    }

    @Test
    fun intensityRisesFastAndFallsSlowly() {
        val music = Music(rate)
        music.setScene(MusicScene.Hall)
        music.setIntensity(1f)
        renderMusic(music, 1f)
        val rose = music.intensityLevel
        assertTrue("rose to $rose in 1 s", rose > 0.7f)
        music.setIntensity(0f)
        renderMusic(music, 1f)
        assertTrue("fell only to ${music.intensityLevel} in 1 s", music.intensityLevel > 0.4f)
        renderMusic(music, 12f)
        assertTrue(music.intensityLevel < 0.05f)
    }

    @Test
    fun aSceneChangeCrossfadesWithoutAHoleOrAJump() {
        val music = Music(rate)
        music.setScene(MusicScene.Title)
        music.setIntensity(0.5f)
        val switchAt = rate * 4 / block * block
        val (l, _) = renderMusic(music, 9f) { at -> if (at == switchAt) music.setScene(MusicScene.Hall) }
        // Both themes are heard together mid-fade.
        val both = Music(rate)
        both.setScene(MusicScene.Title)
        renderMusic(both, 1f)
        both.setScene(MusicScene.Hall)
        renderMusic(both, 0.5f)
        assertEquals(setOf("title", "hall"), both.playing().toSet())
        renderMusic(both, 2f)
        assertEquals(listOf("hall"), both.playing())
        // No hole in the middle: the loudness over the fade stays near the level either side of it.
        val before = rms(l, switchAt - rate, switchAt)
        val after = rms(l, l.size - rate, l.size)
        val window = rate / 10
        var lowest = Float.MAX_VALUE
        var at = switchAt
        while (at + window < switchAt + rate) {
            lowest = minOf(lowest, rms(l, at, at + window))
            at += window
        }
        assertTrue("dip to $lowest against $before / $after", lowest > minOf(before, after) * 0.45f)
        // No jump: nothing steeper across the switch than the music itself makes.
        var steepestBefore = 0f
        for (i in switchAt - rate * 2 until switchAt) steepestBefore = maxOf(steepestBefore, abs(l[i] - l[i - 1]))
        var steepestFade = 0f
        for (i in switchAt until switchAt + rate) steepestFade = maxOf(steepestFade, abs(l[i] - l[i - 1]))
        assertTrue("jump $steepestFade vs $steepestBefore", steepestFade < steepestBefore * 1.6f + 0.02f)
    }

    @Test
    fun askingForTheSameSceneAgainDoesNotRestartIt() {
        val music = Music(rate)
        music.setScene(MusicScene.Hall)
        renderMusic(music, 1f)
        music.setScene(MusicScene.Hall)
        music.setScene(MusicScene.Game("nope"))
        music.setScene(MusicScene.Hall)
        renderMusic(music, 0.2f)
        assertEquals(listOf("hall"), music.playing())
    }

    @Test
    fun anUnknownMachineStillGetsAThemeAndSilenceFadesOut() {
        val music = Music(rate)
        music.setScene(MusicScene.Game("a-machine-nobody-wrote-yet"))
        val (l, _) = renderMusic(music, 3f)
        assertTrue(rms(l, rate, l.size) > 0.02f)
        music.setScene(MusicScene.Silence)
        // A second to fade, a couple more for the reverb to ring out.
        val (l2, _) = renderMusic(music, 9f)
        assertEquals(0f, rms(l2, rate * 7, l2.size), 1e-6f)
        assertTrue(music.playing().isEmpty())
    }

    @Test
    fun mutedOrZeroVolumeIsSilentAndRestartsTheThemeWhenBack() {
        val music = Music(rate)
        music.setScene(MusicScene.Hall)
        renderMusic(music, 2f)
        music.muted = true
        val (l, _) = renderMusic(music, 1f)
        assertEquals(0f, l.maxOf { abs(it) }, 0f)
        assertTrue("nothing left running", music.playing().isEmpty())
        music.muted = false
        val (back, _) = renderMusic(music, 2f)
        assertTrue(rms(back, rate, back.size) > 0.02f)
        assertEquals(listOf("hall"), music.playing())
        music.volume = 0f
        val (zero, _) = renderMusic(music, 1f)
        assertEquals(0f, zero.maxOf { abs(it) }, 0f)
    }

    @Test
    fun theMusicVolumeScalesTheBus() {
        fun level(volume: Float): Float {
            val m = Music(rate)
            m.volume = volume
            m.setScene(MusicScene.Hall)
            return rms(renderMusic(m, 4f).first, rate * 2, rate * 4)
        }
        val full = level(1f)
        val half = level(0.5f)
        assertEquals(full * 0.5f, half, full * 0.05f)
        assertTrue(full > 0.02f)
    }

    @Test
    fun aDuckDipsTheMusicAndLetsItBackUp() {
        val music = Music(rate)
        music.setScene(MusicScene.Hall)
        renderMusic(music, 2f)
        assertEquals(1f, music.duckLevel, 0.01f)
        music.duck(0.6f, 1f)
        renderMusic(music, 0.5f)
        assertTrue("ducked to ${music.duckLevel}", music.duckLevel < 0.45f)
        renderMusic(music, 4f)
        assertTrue("recovered to ${music.duckLevel}", music.duckLevel > 0.97f)
        // A gentler duck never undoes a deeper one already in force.
        music.duck(0.6f, 1f)
        music.duck(0.1f, 0.2f)
        renderMusic(music, 0.5f)
        assertTrue(music.duckLevel < 0.45f)
    }

    @Test
    fun quietSitsTheMusicBackAndBringsItForwardAgain() {
        val music = Music(rate)
        music.setScene(MusicScene.Hall)
        music.setQuiet(true)
        renderMusic(music, 3f)
        assertEquals(Music.QUIET_LEVEL, music.quietLevel, 0.02f)
        music.setQuiet(false)
        renderMusic(music, 3f)
        assertEquals(1f, music.quietLevel, 0.02f)
    }

    @Test
    fun quietMufflesTheMusicAsWellAsTurningItDown() {
        // Two engines playing the very same music, one sat back from the start: the same moment of the same
        // theme, so any difference is the quiet state's.
        fun play(quiet: Boolean, quietAtSecond: Float = 0f): FloatArray {
            val music = Music(rate)
            music.setScene(MusicScene.Hall)
            music.setIntensity(1f)
            return renderMusic(music, 12f) { at -> if (at >= (quietAtSecond * rate).toInt() / block * block) music.setQuiet(quiet) }.first
        }
        /** How bright the sound is: the size of its sample-to-sample changes against its own size. */
        fun brightness(a: FloatArray, from: Int, to: Int): Float {
            var d = 0.0
            for (i in from + 1 until to) d += (a[i] - a[i - 1]) * (a[i] - a[i - 1])
            return sqrt(d / (to - from)).toFloat() / rms(a, from, to)
        }
        val forward = play(false)
        val back = play(true)
        val from = rate * 6
        val to = rate * 12
        assertTrue("quiet should be quieter: ${rms(back, from, to)} vs ${rms(forward, from, to)}", rms(back, from, to) < rms(forward, from, to) * 0.6f)
        assertTrue(
            "quiet should be duller: ${brightness(back, from, to)} vs ${brightness(forward, from, to)}",
            brightness(back, from, to) < brightness(forward, from, to) * 0.92f,
        )
    }

    @Test
    fun aStingerPlaysOverTheSceneAndDucksIt() {
        val quietMusic = Music(rate)
        quietMusic.setScene(MusicScene.Hall)
        renderMusic(quietMusic, 2f)
        val withStinger = Music(rate)
        withStinger.setScene(MusicScene.Hall)
        renderMusic(withStinger, 2f)
        withStinger.stinger(Stinger.GO)
        renderMusic(withStinger, 0.3f)
        assertTrue("scene should be ducked: ${withStinger.duckLevel}", withStinger.duckLevel < 0.8f)
        // After it has finished the duck has released.
        renderMusic(withStinger, 6f)
        assertTrue(withStinger.duckLevel > 0.97f)
        // Every stinger can be requested (in a burst, too) without trouble.
        for (s in Stinger.entries) withStinger.stinger(s)
        val (l, r) = renderMusic(withStinger, 8f)
        for (i in l.indices) assertTrue(l[i].isFinite() && r[i].isFinite() && abs(l[i]) <= 1f)
        assertEquals(0, withStinger.steals())
    }

    @Test
    fun noThemeEverNeedsToStealAVoiceEvenWithStingersOnTop() {
        val music = Music(rate)
        for (t in Tracks.themes) {
            music.setScene(sceneFor(t))
            music.setIntensity(1f)
            renderMusic(music, 3f)
        }
        for (s in Stinger.entries) music.stinger(s)
        renderMusic(music, 6f)
        assertEquals(0, music.steals())
        assertTrue(music.peakVoices() <= 32)
    }

    @Test
    fun theEngineMixesTheMusicUnderTheSoundEffects() {
        val engine = MixEngine(rate)
        engine.bank.generateAll()
        engine.ambientTarget = 0f
        engine.music.setScene(MusicScene.Hall)
        val out = ShortArray(MixEngine.BLOCK * 2)
        var peak = 0
        var sum = 0.0
        repeat(300) {
            engine.render(out)
            if (it > 100) for (s in out) {
                peak = maxOf(peak, abs(s.toInt()))
                sum += s.toDouble() * s
            }
        }
        assertTrue("music is audible: $peak", peak > 1000)
        assertTrue("and well under full scale: $peak", peak < 20000)
        // A big sound effect ducks it.
        engine.play(Sfx.JACKPOT, 1f, 1f)
        engine.render(out)
        engine.render(out)
        assertTrue("jackpot should duck the music: ${engine.music.duckLevel}", engine.music.duckLevel < 0.9f)
        // Mute silences everything, music included; and the music volume is separate from the sfx one.
        engine.muted = true
        engine.render(out)
        engine.render(out)
        assertTrue(out.all { it.toInt() == 0 })
        engine.muted = false
        engine.music.volume = 0f
        engine.sfxVolume = 0f
        engine.reverb.returnScale = 0f
        repeat(50) { engine.render(out) }
        assertTrue(out.all { abs(it.toInt()) < 3 })
        assertNotEquals(0, peak)
    }
}
