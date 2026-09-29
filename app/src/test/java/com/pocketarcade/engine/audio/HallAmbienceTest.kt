package com.pocketarcade.engine.audio

import com.pocketarcade.engine.Sfx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/** Where the hall's background sounds come from, with the mixer replaced by a recorder. */
class HallAmbienceTest {
    private class Started(val sfx: Sfx, val left: Float, val right: Float, val send: Float, val priority: Int)

    private class Recorder : VoiceSink {
        val started = ArrayList<Started>()
        var ambient = 0
        override fun startVoice(sfx: Sfx, gainL: Float, gainR: Float, send: Float, pitch: Float, priority: Int) {
            started += Started(sfx, gainL, gainR, send, priority)
        }

        override fun ambientVoices(): Int = ambient
    }

    private val block = 480
    private val l = FloatArray(block)
    private val r = FloatArray(block)
    private val north = PI.toFloat()

    private fun ambience(): HallAmbience = HallAmbience(48000).also { it.target = 1f }

    /** Runs [seconds] of ambience as heard from ([lx], [lz]) facing north, returning the mixed left channel's RMS. */
    private fun run(a: HallAmbience, sink: VoiceSink, seconds: Float, lx: Float = 300f, lz: Float = 500f): Float {
        var sum = 0.0
        var count = 0
        val blocks = (seconds * 48000 / block).toInt()
        for (b in 0 until blocks) {
            java.util.Arrays.fill(l, 0f)
            java.util.Arrays.fill(r, 0f)
            a.render(l, r, block, sink, 1f, lx, lz, north)
            if (b > blocks / 2) for (v in l) {
                sum += v * v
                count++
            }
        }
        return sqrt(sum / count).toFloat()
    }

    @Test
    fun bleepsComeFromCabinetsOnTheirOwnSide() {
        val a = ambience()
        // Two cabinets: one due east (right, facing north), one due west (left).
        a.setSources(floatArrayOf(360f, 240f), floatArrayOf(500f, 500f), intArrayOf(Attract.GENERIC, Attract.GENERIC), 2)
        val rec = Recorder()
        run(a, rec, 30f)
        val bleeps = rec.started.filter { it.priority == Priority.AMBIENT }
        assertTrue("expected plenty of bleeps, got ${bleeps.size}", bleeps.size > 20)
        var right = 0
        var left = 0
        for (b in bleeps) if (b.right > b.left) right++ else left++
        assertTrue("both cabinets should be heard ($left left, $right right)", left > 3 && right > 3)
        for (b in bleeps) assertTrue("each bleep is hard to one side", minOf(b.left, b.right) < maxOf(b.left, b.right) * 0.2f)
    }

    @Test
    fun onlyCabinetsWithinEarshotBleep() {
        val a = ambience()
        a.setSources(floatArrayOf(300f), floatArrayOf(500f + Spatial.MAX_DISTANCE + 100f), intArrayOf(Attract.GENERIC), 1)
        val rec = Recorder()
        run(a, rec, 20f)
        assertTrue("a far cabinet stays silent", rec.started.none { it.priority == Priority.AMBIENT && it.sfx != Sfx.STEAM && it.sfx != Sfx.CLINK })
        // Walk up to it and it speaks.
        run(a, rec, 20f, lz = 500f + Spatial.MAX_DISTANCE + 60f)
        assertTrue(rec.started.isNotEmpty())
    }

    @Test
    fun eachMachineBleepsInItsOwnVoice() {
        val a = ambience()
        val kinds = intArrayOf(Attract.kindFor("racer"), Attract.kindFor("claw"))
        a.setSources(floatArrayOf(330f, 270f), floatArrayOf(480f, 480f), kinds, 2)
        val rec = Recorder()
        run(a, rec, 60f)
        val palettes = kinds.map { Attract.palette(it).sfx.toSet() }
        val heard = rec.started.filter { it.priority == Priority.AMBIENT }.map { it.sfx }.toSet()
        assertTrue("racer sounds heard", heard.any { it in palettes[0] })
        assertTrue("claw sounds heard", heard.any { it in palettes[1] })
        assertFalse("nothing outside the two palettes", heard.any { it !in palettes[0] && it !in palettes[1] })
    }

    @Test
    fun theCafeHissesAndClinksFromItsCounterOnlyWhenYouAreNearIt() {
        val a = ambience()
        a.setCafe(100f, 900f)
        // One cabinet miles away, so the only sounds nearby are the café's.
        a.setSources(floatArrayOf(5000f), floatArrayOf(5000f), IntArray(1), 1)
        val near = Recorder()
        // Standing close by, with the café to the west (left, facing north).
        run(a, near, 60f, lx = 160f, lz = 900f)
        val cafe = near.started.filter { it.sfx == Sfx.STEAM || it.sfx == Sfx.CLINK }
        assertTrue("steam and cups near the café", cafe.any { it.sfx == Sfx.STEAM } && cafe.any { it.sfx == Sfx.CLINK })
        assertTrue("from the left", cafe.all { it.left > it.right })
        val far = Recorder()
        val b = ambience()
        b.setCafe(100f, 900f)
        b.setSources(floatArrayOf(5000f), floatArrayOf(5000f), IntArray(1), 1)
        run(b, far, 60f, lx = 500f, lz = 100f)
        assertTrue("silent from across the hall", far.started.none { it.sfx == Sfx.STEAM || it.sfx == Sfx.CLINK })
    }

    @Test
    fun aBusyHallMurmursLouderThanAnEmptyOne() {
        val quiet = ambience()
        quiet.crowd = 0f
        val busy = ambience()
        busy.crowd = 1f
        val q = run(quiet, Recorder(), 12f)
        val b = run(busy, Recorder(), 12f)
        assertTrue("busy $b should beat quiet $q by a good margin", b > q * 1.15f)
    }

    @Test
    fun silentWhenTheTargetOrTheVolumeIsZero() {
        val a = HallAmbience(48000)
        val rec = Recorder()
        java.util.Arrays.fill(l, 0f)
        repeat(50) { a.render(l, r, block, rec, 1f, 0f, 0f, 0f) }
        assertEquals(0f, l.max(), 0f)
        assertTrue(rec.started.isEmpty())
        a.target = 1f
        repeat(200) { a.render(l, r, block, rec, 0f, 0f, 0f, 0f) }
        assertEquals(0f, l.max(), 0f)
        assertTrue(rec.started.isEmpty())
    }

    @Test
    fun theAmbienceVoicesAreCapped() {
        val a = ambience()
        a.setCafe(300f, 500f)
        a.setSources(FloatArray(20) { 300f + it }, FloatArray(20) { 500f }, IntArray(20), 20)
        val rec = Recorder()
        rec.ambient = 5 // the mixer already has five going
        run(a, rec, 20f)
        assertTrue("no more while at the cap", rec.started.isEmpty())
    }

    @Test
    fun beforeTheHallReportsBleepsStillHappenFromSomewhere() {
        val a = ambience()
        val rec = Recorder()
        run(a, rec, 20f)
        val bleeps = rec.started.filter { it.priority == Priority.AMBIENT }
        assertTrue(bleeps.size > 10)
        assertTrue("both ears get some", bleeps.any { it.left > it.right } && bleeps.any { it.right > it.left })
    }
}
