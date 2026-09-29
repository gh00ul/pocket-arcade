package com.pocketarcade.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The room reverb: stable, decaying, bounded, and never clicking when the room changes. */
class ReverbTest {
    private val rate = 48000
    private val block = 480

    /** Runs [input] (mono) through a reverb in [room] and returns the left and right tails. */
    private fun run(room: Room, input: FloatArray, reverb: Reverb = Reverb(rate, room)): Pair<FloatArray, FloatArray> {
        val l = FloatArray(input.size)
        val r = FloatArray(input.size)
        val send = FloatArray(block)
        val outL = FloatArray(block)
        val outR = FloatArray(block)
        var at = 0
        while (at + block <= input.size) {
            System.arraycopy(input, at, send, 0, block)
            java.util.Arrays.fill(outL, 0f)
            java.util.Arrays.fill(outR, 0f)
            reverb.process(send, block, outL, outR)
            System.arraycopy(outL, 0, l, at, block)
            System.arraycopy(outR, 0, r, at, block)
            at += block
        }
        return l to r
    }

    private fun impulse(seconds: Float) = FloatArray((seconds * rate).toInt() / block * block).also { it[0] = 1f }

    private fun rms(a: FloatArray, from: Int, to: Int): Float {
        var s = 0.0
        for (i in from until to) s += a[i] * a[i]
        return sqrt(s / (to - from)).toFloat()
    }

    @Test
    fun impulseResponseDecaysAndStaysBoundedInEveryRoom() {
        for (room in Room.entries) {
            val (l, r) = run(room, impulse(12f))
            for (ch in listOf(l, r)) {
                var peak = 0f
                for (v in ch) {
                    assertTrue("$room has a bad sample", v.isFinite())
                    peak = maxOf(peak, abs(v))
                }
                assertTrue("$room impulse response peaks at $peak", peak < 1.5f && peak > 0.001f)
                val start = rms(ch, 0, rate / 2)
                val atRt60 = (room.rt60 * rate).toInt()
                val later = rms(ch, atRt60, atRt60 + rate / 2)
                assertTrue("$room: $later at its rt60 should be far below $start", later < start * 0.05f)
                assertTrue("$room: dead by the end", rms(ch, ch.size - rate, ch.size) < start * 1e-3f)
            }
        }
    }

    @Test
    fun theTailIsWideAndTheEarsDiffer() {
        for (room in Room.entries) {
            val (l, r) = run(room, impulse(3f))
            var diff = 0f
            var sum = 0f
            for (i in l.indices) {
                diff += abs(l[i] - r[i])
                sum += abs(l[i]) + abs(r[i])
            }
            assertTrue("$room ears identical (${diff / sum})", diff / sum > 0.4f)
        }
    }

    /** Reverb time from the energy decay curve (Schroeder's backward integration), T30 scaled to 60 dB. */
    private fun measuredRt60(ir: FloatArray): Float {
        val edc = DoubleArray(ir.size)
        var acc = 0.0
        for (i in ir.indices.reversed()) {
            acc += ir[i].toDouble() * ir[i]
            edc[i] = acc
        }
        val total = edc[0]
        fun timeAt(db: Double): Float {
            for (i in edc.indices) if (10 * log10(edc[i] / total) <= db) return i / rate.toFloat()
            return ir.size / rate.toFloat()
        }
        return (timeAt(-35.0) - timeAt(-5.0)) * 2f
    }

    @Test
    fun theReverbTimeIsAboutWhatTheRoomSaysAndBiggerRoomsRingLonger() {
        val measured = HashMap<Room, Float>()
        for (room in Room.entries) {
            val (l, _) = run(room, impulse(14f))
            val rt = measuredRt60(l)
            measured[room] = rt
            assertTrue("$room measured $rt vs nominal ${room.rt60}", rt > room.rt60 * 0.35f && rt < room.rt60 * 1.3f)
        }
        assertTrue(measured[Room.GAME]!! < measured[Room.HALL]!!)
        assertTrue(measured[Room.HALL]!! < measured[Room.TITLE]!!)
    }

    @Test
    fun everyRoomStaysFiniteAndBoundedUnderLoudNoiseAndSquareWaves() {
        val rng = Random(5)
        for (room in Room.entries) {
            val noise = FloatArray(rate * 5 / block * block) { rng.nextFloat() * 2f - 1f }
            val square = FloatArray(noise.size) { if ((it * 100 / rate) % 2 == 0) 1f else -1f }
            val dc = FloatArray(noise.size) { 1f }
            for ((name, signal) in listOf("noise" to noise, "square" to square, "dc" to dc)) {
                val (l, r) = run(room, signal)
                var peak = 0f
                for (v in l) peak = maxOf(peak, abs(v))
                for (v in r) peak = maxOf(peak, abs(v))
                assertTrue("$room/$name is not finite", l.all { it.isFinite() } && r.all { it.isFinite() })
                assertTrue("$room/$name peaks at $peak", peak < 40f)
            }
        }
    }

    @Test
    fun aWhiteNoiseSendComesBackAtAboutTheRoomsReturnLevel() {
        val rng = Random(9)
        for (room in Room.entries) {
            val input = FloatArray(rate * 6 / block * block) { (rng.nextFloat() - 0.5f) }
            val inRms = rms(input, rate, input.size)
            val (l, _) = run(room, input)
            val ratio = rms(l, rate * 2, l.size) / inRms / room.wet
            assertTrue("$room returns ${ratio}x its wet level", ratio in 0.4f..2.2f)
            assertTrue("wet in bounds", room.wet in 0f..1.5f)
        }
    }

    @Test
    fun silenceInSilenceOutAndTheTailStopsCosting() {
        val reverb = Reverb(rate, Room.HALL)
        val input = impulse(25f)
        val (l, r) = run(Room.HALL, input, reverb)
        // After the tail has died and time has passed the reverb adds exactly nothing.
        assertEquals(0f, rms(l, l.size - rate, l.size), 0f)
        assertEquals(0f, rms(r, r.size - rate, r.size), 0f)
        // And it wakes again for the next sound.
        val send = FloatArray(block)
        send[0] = 1f
        val out = FloatArray(block)
        reverb.process(send, block, out, FloatArray(block))
        assertTrue(out.any { it != 0f })
    }

    @Test
    fun changingTheRoomMorphsSmoothlyWithoutClicks() {
        val reverb = Reverb(rate, Room.GAME)
        val rng = Random(21)
        val seconds = 8
        // Steady noise is the fair test: a tone would build up resonantly in a longer room, which is a level
        // change and not a click.
        val noise = FloatArray(rate * seconds / block * block) { (rng.nextFloat() - 0.5f) * 0.4f }
        val send = FloatArray(block)
        val outL = FloatArray(block)
        val outR = FloatArray(block)
        var worstBefore = 0f
        var worstAfter = 0f
        var prev = 0f
        var at = 0
        val switchAt = rate * 3
        var rt1 = 0f
        var rt3 = 0f
        while (at + block <= noise.size) {
            if (at == switchAt) reverb.room = Room.HALL
            System.arraycopy(noise, at, send, 0, block)
            java.util.Arrays.fill(outL, 0f)
            java.util.Arrays.fill(outR, 0f)
            reverb.process(send, block, outL, outR)
            // The biggest single-sample step in this block, against the block's own loudness.
            var step = 0f
            var sq = 0f
            for (i in 0 until block) {
                step = maxOf(step, abs(outL[i] - prev))
                prev = outL[i]
                sq += outL[i] * outL[i]
            }
            val ratio = step / (sqrt(sq / block) + 1e-9f)
            if (at >= rate && at < switchAt) worstBefore = maxOf(worstBefore, ratio)
            if (at >= switchAt && at < switchAt + rate * 3) worstAfter = maxOf(worstAfter, ratio)
            if (at == switchAt + rate) rt1 = reverb.currentRt60
            if (at == switchAt + rate * 3) rt3 = reverb.currentRt60
            at += block
        }
        assertTrue("click on room change: step/rms $worstAfter vs steady $worstBefore", worstAfter < worstBefore * 1.5f + 0.5f)
        assertTrue("rt60 on its way after 1 s: $rt1", rt1 > Room.GAME.rt60 + (Room.HALL.rt60 - Room.GAME.rt60) * 0.8f)
        assertEquals("settled after 3 s", Room.HALL.rt60, rt3, 0.02f)
        assertEquals(Room.HALL.wet, reverb.currentWet, 0.01f)
    }

    @Test
    fun resetEmptiesTheTail() {
        val reverb = Reverb(rate, Room.TITLE)
        val send = FloatArray(block)
        send[0] = 1f
        val outL = FloatArray(block)
        val outR = FloatArray(block)
        reverb.process(send, block, outL, outR)
        reverb.reset()
        java.util.Arrays.fill(send, 0f)
        java.util.Arrays.fill(outL, 0f)
        reverb.process(send, block, outL, outR)
        assertTrue(outL.all { abs(it) < 1e-15f })
    }
}
