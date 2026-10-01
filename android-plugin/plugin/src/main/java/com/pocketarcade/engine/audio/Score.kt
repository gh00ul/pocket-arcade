package com.pocketarcade.engine.audio

/**
 * The notes of one instrument in a [Track]: parallel arrays sorted by step (a step is a sixteenth
 * note), so the player only ever needs a cursor.
 *
 * @property layer 0 always plays; 1 and 2 fade in as the music's intensity rises (see [Track.layerStart]).
 * @property pumpTrigger every note of this lane sets off the sidechain "pump" that ducks patches with [Patch.pump].
 */
internal class Lane(
    val patch: Patch,
    val layer: Int,
    val steps: IntArray,
    val notes: IntArray,
    val lengths: IntArray,
    val velocities: FloatArray,
    val pumpTrigger: Boolean,
) {
    val count: Int get() = steps.size
}

/**
 * A piece of music as data: a tempo, a length in bars of sixteen steps, and the lanes that play
 * over it. [layerStart] is the intensity at which layers 1 and 2 begin to fade in (layer 0 always
 * plays). [level] trims the whole track's loudness so themes sit at about the same volume.
 * [swing] delays every second sixteenth by that fraction of a step. A [loop] track repeats; a
 * one-shot (a stinger) plays once and ends after its last note has rung out.
 */
internal class Track(
    val name: String,
    val bpm: Float,
    val bars: Int,
    val swing: Float,
    val lanes: Array<Lane>,
    val layerStart: FloatArray,
    val level: Float,
    val loop: Boolean,
) {
    val steps: Int get() = bars * STEPS_PER_BAR

    companion object {
        const val STEPS_PER_BAR = 16
        const val LAYERS = 3
    }
}

/** Scales: semitones above the tonic. */
internal object Scales {
    val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    val DORIAN = intArrayOf(0, 2, 3, 5, 7, 9, 10)
    val PHRYGIAN = intArrayOf(0, 1, 3, 5, 7, 8, 10)
    val PENTATONIC_MAJOR = intArrayOf(0, 2, 4, 7, 9)
    val PENTATONIC_MINOR = intArrayOf(0, 3, 5, 7, 10)
}

/** One chord in a progression: its root's pitch class, its intervals and the bars it lasts. */
internal class ChordSpan(val root: Int, val intervals: IntArray, val startBar: Int, val bars: Int)

/**
 * Builds a [Track] from a compact, readable description: a chord progression, then lanes made of
 * pads, basslines, arpeggios, stabs, drum patterns and melodies that follow it. Patterns are
 * strings of sixteen characters a bar (spaces are ignored; `|` separates bars, and a pattern with
 * several bars cycles), so a drum groove is just `"x...x...x...x..."`.
 *
 * Bad input throws [IllegalArgumentException]; the tests build every track, so a typo shows up there.
 */
internal class TrackBuilder(private val name: String, private val bpm: Float, private val bars: Int, private val swing: Float) {
    private val lanes = ArrayList<Lane>()
    private var chords: List<ChordSpan> = emptyList()
    private var layerStarts = floatArrayOf(-1f, 0.3f, 0.65f)
    private var level = 1f
    private var loop = true
    private val totalSteps = bars * Track.STEPS_PER_BAR

    /** The intensity at which layers 1 and 2 start to fade in. */
    fun layers(one: Float, two: Float) {
        layerStarts = floatArrayOf(-1f, one, two)
    }

    /** Trims the track's loudness. */
    fun level(v: Float) {
        level = v
    }

    /** Makes this a one-shot (a stinger). */
    fun once() {
        loop = false
    }

    /**
     * The chord progression, as names separated by spaces: `Am7 F C G`. `Am7*2` lasts two bars.
     * It repeats to fill the track. Names are a root (`C`, `F#`, `Bb`) and a quality: nothing (major),
     * `m`, `7`, `m7`, `maj7`, `6`, `m6`, `sus4`, `sus2`, `add9`, `9`, `m9`, `maj9`, `dim`, `5`, `7sus4`, `m7b5`.
     */
    fun chords(spec: String) {
        val list = ArrayList<ChordSpan>()
        var bar = 0
        val names = spec.trim().split(Regex("\\s+"))
        var i = 0
        while (bar < bars) {
            val token = names[i % names.size]
            i++
            val star = token.indexOf('*')
            val name = if (star >= 0) token.substring(0, star) else token
            val length = if (star >= 0) token.substring(star + 1).toInt() else 1
            val (root, intervals) = parseChord(name)
            val span = minOf(length, bars - bar)
            list += ChordSpan(root, intervals, bar, span)
            bar += span
        }
        chords = list
    }

    private fun chordAtBar(bar: Int): ChordSpan {
        check(chords.isNotEmpty()) { "$name: chords() first" }
        return chords.first { bar >= it.startBar && bar < it.startBar + it.bars }
    }

    /**
     * A pad: each chord's notes held for the whole chord, voiced within the octave starting at MIDI
     * note [low] so they sit close together.
     */
    fun pad(patch: Patch, layer: Int = 0, low: Int = 52, vel: Float = 0.9f) {
        val e = Events()
        for (c in chords) {
            for (iv in c.intervals) {
                var n = 12 * 5 + c.root + iv
                while (n < low) n += 12
                while (n > low + 11) n -= 12
                e.add(c.startBar * Track.STEPS_PER_BAR, n, c.bars * Track.STEPS_PER_BAR, vel)
            }
        }
        add(patch, layer, e, false)
    }

    /**
     * A bass line on the chord roots. [rhythm] characters: `r` the root, `R` the root an octave up,
     * `5` the fifth, `b` the flat seventh, `3` the third, `.` a rest or a held note (a note lasts until
     * the next one or [maxLen] steps). [octave] is the root's octave (2 puts C at MIDI 36).
     */
    fun bass(patch: Patch, rhythm: String, layer: Int = 0, octave: Int = 2, vel: Float = 0.9f, maxLen: Int = 4, pump: Boolean = false) {
        val pats = patterns(rhythm)
        val e = Events()
        for (bar in 0 until bars) {
            val chord = chordAtBar(bar)
            val pat = pats[bar % pats.size]
            val base = 12 * (octave + 1) + chord.root
            for (s in 0 until 16) {
                val ch = pat[s]
                val note = when (ch) {
                    'r' -> base
                    'R' -> base + 12
                    '5' -> base + 7
                    'b' -> base + 10
                    '3' -> base + (chord.intervals.getOrElse(1) { 4 })
                    '.' -> continue
                    else -> throw IllegalArgumentException("$name: bass pattern '$ch'")
                }
                var len = 1
                while (s + len < 16 && pat[s + len] == '.' && len < maxLen) len++
                e.add(bar * 16 + s, note, len, vel)
            }
        }
        add(patch, layer, e, pump)
    }

    /**
     * An arpeggio over the chords. [pattern] characters: a digit picks a chord tone (0 the root, then up
     * the chord, wrapping into the next octave), `.` is a rest. [octave] is where the root sits.
     */
    fun arp(patch: Patch, pattern: String, layer: Int = 1, octave: Int = 4, vel: Float = 0.8f, len: Int = 1) {
        val pats = patterns(pattern)
        val e = Events()
        for (bar in 0 until bars) {
            val chord = chordAtBar(bar)
            val pat = pats[bar % pats.size]
            val base = 12 * (octave + 1) + chord.root
            for (s in 0 until 16) {
                val ch = pat[s]
                if (ch == '.') continue
                require(ch in '0'..'9') { "$name: arp pattern '$ch'" }
                val idx = ch - '0'
                val n = chord.intervals.size
                e.add(bar * 16 + s, base + chord.intervals[idx % n] + 12 * (idx / n), len, vel)
            }
        }
        add(patch, layer, e, false)
    }

    /**
     * Chord stabs: at every `x` in [rhythm] the whole chord sounds for [len] steps, voiced in the octave
     * starting at [low].
     */
    fun stab(patch: Patch, rhythm: String, layer: Int = 0, low: Int = 55, vel: Float = 0.8f, len: Int = 2) {
        val pats = patterns(rhythm)
        val e = Events()
        for (bar in 0 until bars) {
            val chord = chordAtBar(bar)
            val pat = pats[bar % pats.size]
            for (s in 0 until 16) {
                if (pat[s] == '.') continue
                for (iv in chord.intervals) {
                    var n = 12 * 5 + chord.root + iv
                    while (n < low) n += 12
                    while (n > low + 11) n -= 12
                    e.add(bar * 16 + s, n, len, if (pat[s] == 'X') minOf(1f, vel * 1.15f) else vel)
                }
            }
        }
        add(patch, layer, e, false)
    }

    /**
     * A drum lane playing MIDI note [note] (which sets a kick or tom's pitch) on the patterns. `x` is a
     * hit, `X` an accent, `o` a ghost note. With [fill], every [fillEvery]th bar plays that instead.
     * [pump] makes it trigger the sidechain pump.
     */
    fun drums(
        patch: Patch, note: Int, pattern: String, layer: Int = 0, fill: String? = null, fillEvery: Int = 4,
        vel: Float = 0.85f, pump: Boolean = false,
    ) {
        val main = patterns(pattern)
        val fills = fill?.let { patterns(it) }
        val e = Events()
        for (bar in 0 until bars) {
            val pat = if (fills != null && bar % fillEvery == fillEvery - 1) fills[0] else main[bar % main.size]
            for (s in 0 until 16) {
                val v = when (pat[s]) {
                    'x' -> vel
                    'X' -> minOf(1f, vel * 1.2f)
                    'o' -> vel * 0.5f
                    '.' -> continue
                    else -> throw IllegalArgumentException("$name: drum pattern '${pat[s]}'")
                }
                e.add(bar * 16 + s, note, 1, v)
            }
        }
        add(patch, layer, e, pump)
    }

    /**
     * A melody: `degree:length` tokens separated by spaces, in steps. A degree counts scale notes from
     * [tonic] (a note name such as `C5`) up through [scale], with `^` an octave up and `v` down (`-1` is
     * the note below the tonic); `.` is a rest. Starts at bar [startBar]; the tokens need not fill the
     * track, but must not overrun it.
     */
    fun melody(patch: Patch, spec: String, tonic: String, scale: IntArray, layer: Int = 2, vel: Float = 0.85f, startBar: Int = 0) {
        val e = Events()
        var at = startBar * Track.STEPS_PER_BAR
        val tonicMidi = parseNote(tonic)
        for (token in spec.replace("|", " ").trim().split(Regex("\\s+"))) {
            val colon = token.indexOf(':')
            require(colon > 0) { "$name: melody token '$token'" }
            val length = token.substring(colon + 1).toInt()
            var head = token.substring(0, colon)
            if (head != ".") {
                var octave = 0
                while (head.endsWith("^") || head.endsWith("v")) {
                    octave += if (head.endsWith("^")) 1 else -1
                    head = head.dropLast(1)
                }
                val degree = head.toInt()
                val oct = Math.floorDiv(degree, scale.size)
                val idx = Math.floorMod(degree, scale.size)
                e.add(at, tonicMidi + scale[idx] + 12 * (oct + octave), length, vel)
            }
            at += length
        }
        require(at <= totalSteps) { "$name: melody runs to step $at of $totalSteps" }
        add(patch, layer, e, false)
    }

    /** Single notes: `(step, note name, length)` triples, for stingers and one-off lines. */
    fun notes(patch: Patch, layer: Int, vararg hits: Triple<Int, String, Int>, vel: Float = 0.85f) {
        val e = Events()
        for ((step, name, len) in hits) e.add(step, parseNote(name), len, vel)
        add(patch, layer, e, false)
    }

    fun build(): Track {
        require(lanes.isNotEmpty()) { "$name has no lanes" }
        return Track(name, bpm, bars, swing, lanes.toTypedArray(), layerStarts, level, loop)
    }

    // ---------------------------------------------------------------- plumbing

    private class Events {
        val step = ArrayList<Int>()
        val note = ArrayList<Int>()
        val len = ArrayList<Int>()
        val vel = ArrayList<Float>()
        fun add(s: Int, n: Int, l: Int, v: Float) {
            step += s; note += n; len += l; vel += v
        }
    }

    private fun add(patch: Patch, layer: Int, e: Events, pump: Boolean) {
        require(layer in 0 until Track.LAYERS) { "$name: layer $layer" }
        // Sorted by step (stable, so a chord's notes keep their order).
        val order = e.step.indices.sortedBy { e.step[it] }
        for (i in order) require(e.step[i] < totalSteps) { "$name: note at step ${e.step[i]} of $totalSteps" }
        lanes += Lane(
            patch, layer,
            IntArray(order.size) { e.step[order[it]] },
            IntArray(order.size) { e.note[order[it]].coerceIn(0, 127) },
            IntArray(order.size) { e.len[order[it]] },
            FloatArray(order.size) { e.vel[order[it]] },
            pump,
        )
    }

    /** Splits a `|`-separated pattern into bars of exactly sixteen characters. */
    private fun patterns(spec: String): List<String> {
        val list = spec.split('|').map { it.replace(" ", "") }
        for (bar in list) require(bar.length == 16) { "$name: pattern bar '$bar' is ${bar.length} steps, not 16" }
        return list
    }

    private companion object {
        val QUALITIES: Map<String, IntArray> = mapOf(
            "" to intArrayOf(0, 4, 7),
            "m" to intArrayOf(0, 3, 7),
            "7" to intArrayOf(0, 4, 7, 10),
            "m7" to intArrayOf(0, 3, 7, 10),
            "maj7" to intArrayOf(0, 4, 7, 11),
            "6" to intArrayOf(0, 4, 7, 9),
            "m6" to intArrayOf(0, 3, 7, 9),
            "sus4" to intArrayOf(0, 5, 7),
            "sus2" to intArrayOf(0, 2, 7),
            "add9" to intArrayOf(0, 4, 7, 14),
            "9" to intArrayOf(0, 4, 7, 10, 14),
            "m9" to intArrayOf(0, 3, 7, 10, 14),
            "maj9" to intArrayOf(0, 4, 7, 11, 14),
            "dim" to intArrayOf(0, 3, 6),
            "5" to intArrayOf(0, 7),
            "7sus4" to intArrayOf(0, 5, 7, 10),
            "m7b5" to intArrayOf(0, 3, 6, 10),
        )
        val PITCH_CLASS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

        fun parseChord(name: String): Pair<Int, IntArray> {
            require(name.isNotEmpty() && name[0] in PITCH_CLASS) { "chord '$name'" }
            var root = PITCH_CLASS.getValue(name[0])
            var i = 1
            if (i < name.length && name[i] == '#') {
                root += 1; i++
            } else if (i < name.length && name[i] == 'b') {
                // A flat root ("Bb", "Ebm7"): no chord quality starts with b, so a b here is always a flat.
                root -= 1; i++
            }
            val quality = QUALITIES[name.substring(i)] ?: throw IllegalArgumentException("chord quality '${name.substring(i)}' in '$name'")
            return Math.floorMod(root, 12) to quality
        }

        /** MIDI note number for a name like `C5`, `F#3`, `Bb4` (C4 is 60). */
        fun parseNote(name: String): Int {
            require(name.length >= 2 && name[0] in PITCH_CLASS) { "note '$name'" }
            var pc = PITCH_CLASS.getValue(name[0])
            var i = 1
            if (name[i] == '#') {
                pc += 1; i++
            } else if (name[i] == 'b') {
                pc -= 1; i++
            }
            return 12 * (name.substring(i).toInt() + 1) + pc
        }
    }
}

/** Builds a track: `track("hall", 96f, 16) { chords("Am7 F"); pad(...) }`. */
internal fun track(name: String, bpm: Float, bars: Int, swing: Float = 0f, build: TrackBuilder.() -> Unit): Track =
    TrackBuilder(name, bpm, bars, swing).apply(build).build()
