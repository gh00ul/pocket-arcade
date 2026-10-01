package com.pocketarcade.engine.audio

import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * Plays one [Track]: a sample-accurate step sequencer driving a pool of [MusicVoice]s. Time is
 * counted in whole samples, and each step's start is computed from the step number (not by adding
 * up rounded lengths), so tempo never drifts and a loop is exactly periodic whenever a step is a
 * whole number of samples. Rendering happens in chunks that end at step boundaries, so notes start
 * on the exact sample, and it allocates nothing.
 *
 * Layers 1 and 2 fade in with the intensity passed to [render]; a layer that is silent and not
 * rising is not played at all (its notes are simply skipped), which saves the work.
 */
internal class ScorePlayer(private val sampleRate: Int, poolSize: Int, private val seed: Int) {
    companion object {
        /** Intensity range over which a layer fades from silent to full after its start point. */
        const val LAYER_FADE_WIDTH = 0.25f

        /** Time constant (seconds) of a layer's fade: about 0.7 s, so layers glide in and out. */
        const val LAYER_TAU = 0.7f

        /** A layer at or below this level, and heading down, is skipped entirely. */
        const val LAYER_SKIP = 0.003f

        /** The longest chunk rendered at once (the size of the pump buffer); a block is split at steps anyway. */
        const val MAX_CHUNK = 4096

        /** The kick's sidechain duck recovers with this time constant (seconds). */
        const val PUMP_TAU = 0.16f

        /** A note is released this many steps' worth of a step early, so it never runs into the next one. */
        private const val GATE_TRIM = 0.06f

        /** The most lanes a track may have (the cursors are allocated once, not per track). */
        const val MAX_LANES = 32
    }

    private val sr = sampleRate.toFloat()
    private val voices = Array(poolSize) { MusicVoice() }

    var track: Track? = null
        private set

    /** Whether this player has anything to play (a track that hasn't ended). */
    var active = false
        private set

    /** For a one-shot: it has played its last note and everything has rung out. */
    var finished = false
        private set

    /** How many times a note had to take over a sounding voice (should stay 0: the pool is sized to avoid it). */
    var steals = 0
        private set

    /** The highest number of voices sounding at once so far (for sizing the pool in tests). */
    var peakVoices = 0
        private set

    private var position = 0L
    private var nextEdge = 0L
    private var stepNumber = 0L
    private var samplesPerStep = 0.0
    private val cursor = IntArray(MAX_LANES)
    private val layerNow = FloatArray(Track.LAYERS)
    private val layerTarget = FloatArray(Track.LAYERS)
    private val layerStart = FloatArray(Track.LAYERS)
    private val layerSlope = FloatArray(Track.LAYERS)
    private var pump = 0f
    private val pumpBus = FloatArray(MAX_CHUNK)
    private val pumpDecay = exp(-1f / (PUMP_TAU * sr))

    /** Starts [t] from its first step. [intensity] sets the layers straight away (no fade-in) so a new track begins as it means to go on. */
    fun start(t: Track, intensity: Float) {
        require(t.lanes.size <= MAX_LANES) { "${t.name} has ${t.lanes.size} lanes" }
        for (v in voices) v.kill()
        track = t
        active = true
        finished = false
        position = 0L
        stepNumber = 0L
        samplesPerStep = sampleRate * 60.0 / (t.bpm * 4.0)
        nextEdge = edge(0)
        java.util.Arrays.fill(cursor, 0)
        pump = 0f
        for (l in 0 until Track.LAYERS) {
            layerTarget[l] = layerGoal(t, l, intensity)
            layerNow[l] = layerTarget[l]
        }
    }

    /** Stops at once. */
    fun stop() {
        for (v in voices) v.kill()
        active = false
        track = null
    }

    /** Sample at which step [k] begins: swung steps (odd ones) start a fraction of a step late. */
    private fun edge(k: Long): Long {
        val t = track ?: return 0L
        val late = if (k % 2L == 1L) t.swing * samplesPerStep else 0.0
        return (k * samplesPerStep + late).roundToLong()
    }

    private fun layerGoal(t: Track, layer: Int, intensity: Float): Float =
        if (layer == 0) 1f else Dsp.smooth((intensity - t.layerStart[layer]) / LAYER_FADE_WIDTH)

    /**
     * Renders [n] frames into [outL]/[outR] (and the reverb [sendBus]), scaling the whole player by a
     * gain that moves from [gainFrom] to [gainTo] over the block (a crossfade or a duck) and following
     * [intensity] with its layers.
     */
    fun render(
        outL: FloatArray, outR: FloatArray, sendBus: FloatArray, n: Int,
        intensity: Float, gainFrom: Float, gainTo: Float,
    ) {
        val t = track ?: return
        if (!active) return
        val k = 1f - exp(-(n / sr) / LAYER_TAU)
        for (l in 0 until Track.LAYERS) {
            layerTarget[l] = layerGoal(t, l, intensity)
            layerStart[l] = layerNow[l]
            layerNow[l] += (layerTarget[l] - layerNow[l]) * k
            layerSlope[l] = (layerNow[l] - layerStart[l]) / n
        }
        val gainSlope = (gainTo - gainFrom) / n
        var done = 0
        while (done < n) {
            while (position >= nextEdge && (t.loop || stepNumber < t.steps)) {
                fireStep(t, (stepNumber % t.steps).toInt())
                stepNumber++
                nextEdge = edge(stepNumber)
            }
            val untilStep = if (!t.loop && stepNumber >= t.steps) Long.MAX_VALUE else nextEdge - position
            val chunk = minOf((n - done).toLong(), untilStep, MAX_CHUNK.toLong()).toInt()
            var p = pump
            for (i in 0 until chunk) {
                pumpBus[i] = p
                p *= pumpDecay
            }
            pump = p
            var live = 0
            for (v in voices) {
                if (!v.active) continue
                val l = v.layer
                val g = (layerStart[l] + layerSlope[l] * done)
                val gs = layerSlope[l]
                // The layer's fade and the player's gain multiply; both are ramps, so the product is
                // close enough to a ramp over a chunk that it never steps.
                val pg = gainFrom + gainSlope * done
                v.render(outL, outR, sendBus, done, chunk, g * pg, g * gainSlope + gs * pg, pumpBus)
                if (v.active) live++
            }
            peakVoices = maxOf(peakVoices, live)
            done += chunk
            position += chunk
            if (!t.loop && stepNumber >= t.steps && live == 0) {
                active = false
                finished = true
                return
            }
        }
    }

    private fun fireStep(t: Track, step: Int) {
        for (li in t.lanes.indices) {
            val lane = t.lanes[li]
            if (step == 0) cursor[li] = 0
            var c = cursor[li]
            val skipped = lane.layer > 0 && layerNow[lane.layer] < LAYER_SKIP && layerTarget[lane.layer] < LAYER_SKIP
            while (c < lane.count && lane.steps[c] <= step) {
                if (lane.steps[c] == step && !skipped) noteOn(t, lane, li, c)
                c++
            }
            cursor[li] = c
        }
    }

    private fun noteOn(t: Track, lane: Lane, laneIndex: Int, index: Int) {
        val v = freeVoice()
        val step = lane.steps[index]
        // A little deterministic humanising of the loudness, the same every time round.
        val h = hash(seed, laneIndex, step, lane.notes[index])
        val vel = (lane.velocities[index] * (0.94f + 0.12f * (h and 0xFF) / 255f)).coerceIn(0f, 1f) * t.level
        val gate = ((lane.lengths[index] - GATE_TRIM) * samplesPerStep).toInt()
        v.noteOn(lane.patch, lane.notes[index], vel, gate, sr, h, laneIndex, lane.layer)
        if (lane.pumpTrigger) pump = 1f
    }

    /** A free voice, or (rarely) the quietest one, taken over. */
    private fun freeVoice(): MusicVoice {
        var quietest = voices[0]
        for (v in voices) {
            if (!v.active) return v
            if (v.level < quietest.level) quietest = v
        }
        steals++
        return quietest
    }

    private fun hash(a: Int, b: Int, c: Int, d: Int): Int {
        var h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 1103515245
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** Whether any voice is still sounding (for tests). */
    internal fun voicesSounding(): Int = voices.count { it.active }
}
