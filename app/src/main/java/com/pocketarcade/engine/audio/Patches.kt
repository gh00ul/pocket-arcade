package com.pocketarcade.engine.audio

/**
 * The instruments the themes are played on. Each is a [Patch]: read its fields as a synthesizer
 * panel (oscillators, envelope, filter, place in the mix). The gains are set so that a whole
 * theme sits at about the same loudness whichever instruments it uses.
 */
internal object Patches {
    // ------------------------------------------------------------------ pads

    /** Two slightly detuned saws through a soft low-pass, wide and slow: the warm bed under most themes. */
    val PAD_WARM = Patch(
        wave = Wave.SAW, wave2 = Wave.SAW, ratio2 = 1.006f, mix2 = 0.9f, spread = 0.55f,
        attack = 0.6f, decay = 1.5f, sustain = 0.8f, release = 2.0f, cutoffHz = 1300f,
        gain = 0.075f, send = 0.5f,
    )

    /** A softer, rounder pad: triangles with a bright octave shimmer. */
    val PAD_SOFT = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SINE, ratio2 = 2f, mix2 = 0.35f, spread = 0.4f,
        attack = 0.7f, decay = 1.5f, sustain = 0.85f, release = 2.2f, cutoffHz = 2200f,
        gain = 0.1f, send = 0.55f,
    )

    /** An airy, glassy pad for the top of a chord. */
    val PAD_GLASS = Patch(
        wave = Wave.SINE, wave2 = Wave.TRIANGLE, ratio2 = 1.003f, mix2 = 0.5f, spread = 0.65f,
        attack = 0.9f, decay = 1.5f, sustain = 0.85f, release = 2.5f, cutoffHz = 3200f,
        gain = 0.085f, send = 0.6f,
    )

    /** A dark, low drone for tense themes. */
    val PAD_DARK = Patch(
        wave = Wave.SAW, wave2 = Wave.SQUARE, ratio2 = 0.5f, mix2 = 0.5f, spread = 0.3f,
        attack = 0.8f, decay = 1.5f, sustain = 0.8f, release = 1.6f, cutoffHz = 700f,
        gain = 0.09f, send = 0.3f,
    )

    /** A pad that gates on and off in step with the kick (synthwave): saws with a strong sidechain pump. */
    val PAD_PUMP = Patch(
        wave = Wave.SAW, wave2 = Wave.SAW, ratio2 = 1.008f, mix2 = 0.9f, spread = 0.6f,
        attack = 0.15f, decay = 1.2f, sustain = 0.85f, release = 0.9f, cutoffHz = 1800f,
        gain = 0.07f, send = 0.4f, pump = 0.65f,
    )

    // ------------------------------------------------------------------ bass

    /** A round bass: a triangle and a saw for the harmonics small speakers need, with a soft pluck at the start. */
    val BASS_SUB = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SAW, ratio2 = 1f, mix2 = 0.3f, sub = 0.35f,
        attack = 0.006f, decay = 0.9f, sustain = 0.6f, release = 0.25f,
        cutoffHz = 600f, cutEnvHz = 700f, cutDecay = 0.1f, gain = 0.26f, send = 0.06f,
    )

    /** A punchy synth bass for driving themes, ducked by the kick. */
    val BASS_SYNTH = Patch(
        wave = Wave.SAW, wave2 = Wave.SQUARE, ratio2 = 1f, mix2 = 0.4f, sub = 0.4f, pulse = 0.4f,
        attack = 0.004f, decay = 0.5f, sustain = 0.5f, release = 0.15f,
        cutoffHz = 500f, cutEnvHz = 1400f, cutDecay = 0.12f, gain = 0.22f, send = 0.04f, pump = 0.6f,
    )

    /** A short, hollow square bass for tense minimal themes. */
    val BASS_SQUARE = Patch(
        wave = Wave.SQUARE, pulse = 0.35f, sub = 0.5f,
        attack = 0.003f, decay = 0.2f, sustain = 0.1f, release = 0.1f,
        cutoffHz = 450f, cutEnvHz = 900f, cutDecay = 0.06f, gain = 0.24f, send = 0.03f,
    )

    /** A plucked, woody bass (a pizzicato), for playful themes. */
    val BASS_PLUCK = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SQUARE, ratio2 = 2f, mix2 = 0.15f, sub = 0.2f,
        attack = 0.003f, decay = 0.35f, sustain = 0f, release = 0.1f,
        cutoffHz = 700f, cutEnvHz = 900f, cutDecay = 0.05f, gain = 0.3f, send = 0.06f,
    )

    // ------------------------------------------------------------------ plucks, bells and keys

    /** A bright pulse pluck for arpeggios: opens and closes quickly. */
    val PLUCK = Patch(
        wave = Wave.SQUARE, pulse = 0.3f, wave2 = Wave.SAW, ratio2 = 1.004f, mix2 = 0.3f, spread = 0.35f,
        attack = 0.002f, decay = 0.32f, sustain = 0f, release = 0.2f,
        cutoffHz = 1500f, cutEnvHz = 3500f, cutDecay = 0.09f, gain = 0.075f, send = 0.4f,
    )

    /** A saw pluck, sharper, for synthwave arps. */
    val PLUCK_SAW = Patch(
        wave = Wave.SAW, wave2 = Wave.SAW, ratio2 = 1.005f, mix2 = 0.5f, spread = 0.4f,
        attack = 0.002f, decay = 0.26f, sustain = 0f, release = 0.16f,
        cutoffHz = 1200f, cutEnvHz = 4200f, cutDecay = 0.08f, gain = 0.07f, send = 0.35f,
    )

    /** A glassy bell: a sine and a fourth-harmonic overtone that ring out. */
    val BELL = Patch(
        wave = Wave.SINE, wave2 = Wave.SINE, ratio2 = 3.99f, mix2 = 0.2f, spread = 0.25f,
        attack = 0.002f, decay = 1.7f, sustain = 0f, release = 1.2f,
        cutoffHz = 6500f, gain = 0.11f, send = 0.5f,
    )

    /** A music box tine: high, pure, quick to fade. */
    val MUSICBOX = Patch(
        wave = Wave.SINE, wave2 = Wave.SINE, ratio2 = 3f, mix2 = 0.12f, spread = 0.3f,
        attack = 0.002f, decay = 1.0f, sustain = 0f, release = 0.7f,
        cutoffHz = 7000f, gain = 0.1f, send = 0.5f,
    )

    /** A xylophone: a wooden knock with a bright overtone. */
    val XYLO = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SINE, ratio2 = 3.9f, mix2 = 0.3f,
        attack = 0.001f, decay = 0.3f, sustain = 0f, release = 0.15f,
        cutoffHz = 6000f, gain = 0.13f, send = 0.3f,
    )

    /** An electric piano: a triangle and an octave that brighten and mellow with each note. */
    val EP = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SINE, ratio2 = 2f, mix2 = 0.4f, spread = 0.25f,
        attack = 0.004f, decay = 1.3f, sustain = 0.25f, release = 0.5f,
        cutoffHz = 1500f, cutEnvHz = 2500f, cutDecay = 0.2f, gain = 0.1f, send = 0.3f,
    )

    /** Brassy chord stabs: detuned saws whose filter snaps open. */
    val STAB = Patch(
        wave = Wave.SAW, wave2 = Wave.SAW, ratio2 = 1.005f, mix2 = 1f, spread = 0.4f,
        attack = 0.008f, decay = 0.5f, sustain = 0.25f, release = 0.25f,
        cutoffHz = 900f, cutEnvHz = 3200f, cutDecay = 0.1f, gain = 0.06f, send = 0.25f, pump = 0.3f,
    )

    // ------------------------------------------------------------------ leads

    /** A soft, singing lead: a triangle with a hint of octave and a late vibrato. */
    val LEAD_SOFT = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SINE, ratio2 = 2f, mix2 = 0.18f, spread = 0.2f,
        attack = 0.03f, decay = 0.8f, sustain = 0.7f, release = 0.7f,
        cutoffHz = 3500f, vibHz = 5.4f, vibDepth = 0.006f, vibDelay = 0.3f, gain = 0.15f, send = 0.45f,
    )

    /** A chip-tune style square lead. */
    val LEAD_SQUARE = Patch(
        wave = Wave.SQUARE, pulse = 0.4f, wave2 = Wave.SQUARE, ratio2 = 1.005f, mix2 = 0.4f, spread = 0.25f,
        attack = 0.008f, decay = 0.5f, sustain = 0.6f, release = 0.25f,
        cutoffHz = 2800f, vibHz = 5.8f, vibDepth = 0.005f, vibDelay = 0.2f, gain = 0.085f, send = 0.3f,
    )

    /** A wide saw lead, for synthwave. */
    val LEAD_SAW = Patch(
        wave = Wave.SAW, wave2 = Wave.SAW, ratio2 = 1.01f, mix2 = 0.8f, spread = 0.5f,
        attack = 0.01f, decay = 0.7f, sustain = 0.7f, release = 0.5f,
        cutoffHz = 2400f, cutEnvHz = 1800f, cutDecay = 0.2f, vibHz = 5.5f, vibDepth = 0.005f, vibDelay = 0.25f,
        gain = 0.075f, send = 0.4f, pump = 0.3f,
    )

    // ------------------------------------------------------------------ drums

    /** A kick drum: a sine that falls from a thump to a low note, with a click of noise. */
    val KICK = Patch(
        wave = Wave.SINE, wave2 = Wave.NOISE, mix2 = 0.12f,
        attack = 0.001f, decay = 0.32f, sustain = 0f, release = 0.12f,
        pitchEnv = 4f, pitchDecay = 0.028f, cutoffHz = 1500f, cutEnvHz = 3000f, cutDecay = 0.012f,
        gain = 0.5f, send = 0.04f,
    )

    /** A gentle kick for calm themes. */
    val KICK_SOFT = Patch(
        wave = Wave.SINE, wave2 = Wave.NOISE, mix2 = 0.05f,
        attack = 0.002f, decay = 0.36f, sustain = 0f, release = 0.12f,
        pitchEnv = 2.4f, pitchDecay = 0.035f, cutoffHz = 900f, cutEnvHz = 1500f, cutDecay = 0.015f,
        gain = 0.34f, send = 0.05f,
    )

    /** A snare: a tone and a burst of noise. */
    val SNARE = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.NOISE, mix2 = 1.1f, spread = 0.15f,
        attack = 0.001f, decay = 0.24f, sustain = 0f, release = 0.1f,
        pitchEnv = 0.6f, pitchDecay = 0.02f, cutoffHz = 6500f,
        gain = 0.17f, send = 0.25f,
    )

    /** A clap: noise through a high-pass, quick. */
    val CLAP = Patch(
        wave = Wave.NOISE, highpass = true,
        attack = 0.002f, decay = 0.2f, sustain = 0f, release = 0.1f,
        cutoffHz = 1100f, gain = 0.16f, send = 0.35f,
    )

    /** A closed hi-hat. */
    val HAT = Patch(
        wave = Wave.NOISE, highpass = true,
        attack = 0.001f, decay = 0.07f, sustain = 0f, release = 0.04f,
        cutoffHz = 7500f, gain = 0.08f, pan = 0.2f, send = 0.15f,
    )

    /** An open hi-hat. */
    val HAT_OPEN = Patch(
        wave = Wave.NOISE, highpass = true,
        attack = 0.002f, decay = 0.32f, sustain = 0f, release = 0.12f,
        cutoffHz = 7000f, gain = 0.07f, pan = -0.2f, send = 0.2f,
    )

    /** A shaker: soft noise with a tiny swell. */
    val SHAKER = Patch(
        wave = Wave.NOISE, highpass = true,
        attack = 0.012f, decay = 0.1f, sustain = 0f, release = 0.05f,
        cutoffHz = 5500f, gain = 0.06f, pan = -0.25f, send = 0.15f,
    )

    /** A rim shot: a short knock. */
    val RIM = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.NOISE, ratio2 = 1f, mix2 = 0.3f,
        attack = 0.001f, decay = 0.06f, sustain = 0f, release = 0.04f,
        cutoffHz = 5000f, gain = 0.15f, pan = 0.15f, send = 0.2f,
    )

    /** A soft tom: a sine that dips in pitch. */
    val TOM = Patch(
        wave = Wave.SINE, wave2 = Wave.NOISE, mix2 = 0.05f,
        attack = 0.002f, decay = 0.4f, sustain = 0f, release = 0.12f,
        pitchEnv = 1.4f, pitchDecay = 0.05f, cutoffHz = 1200f, gain = 0.3f, send = 0.2f,
    )

    /** A crash cymbal: a long wash of high noise. */
    val CRASH = Patch(
        wave = Wave.NOISE, highpass = true,
        attack = 0.004f, decay = 1.7f, sustain = 0f, release = 0.9f,
        cutoffHz = 4500f, gain = 0.09f, send = 0.4f,
    )

    // ------------------------------------------------------------------ effects for stingers

    /** A deep boom: a sine that falls a long way. */
    val BOOM = Patch(
        wave = Wave.SINE, wave2 = Wave.NOISE, mix2 = 0.06f,
        attack = 0.002f, decay = 1.0f, sustain = 0f, release = 0.5f,
        pitchEnv = 1.6f, pitchDecay = 0.25f, cutoffHz = 800f, gain = 0.5f, send = 0.3f,
    )

    /** An open-fifth power chord: bright saws. */
    val POWER = Patch(
        wave = Wave.SAW, wave2 = Wave.SQUARE, ratio2 = 1.004f, mix2 = 0.6f, spread = 0.4f,
        attack = 0.006f, decay = 1.1f, sustain = 0.3f, release = 1.0f,
        cutoffHz = 1400f, cutEnvHz = 3500f, cutDecay = 0.25f, gain = 0.07f, send = 0.4f,
    )

    /** A bright sparkle: a short, high, shimmering ping. */
    val SPARKLE = Patch(
        wave = Wave.SINE, wave2 = Wave.TRIANGLE, ratio2 = 2.01f, mix2 = 0.35f, spread = 0.5f,
        attack = 0.002f, decay = 0.9f, sustain = 0f, release = 0.6f,
        cutoffHz = 9000f, gain = 0.09f, send = 0.55f,
    )

    /** A falling tone for the clock running out: a soft triangle that sinks in pitch as it plays. */
    val SINK = Patch(
        wave = Wave.TRIANGLE, wave2 = Wave.SAW, ratio2 = 1.005f, mix2 = 0.3f,
        attack = 0.01f, decay = 0.9f, sustain = 0.2f, release = 0.9f,
        pitchEnv = 0.12f, pitchDecay = 0.5f, cutoffHz = 900f, gain = 0.18f, send = 0.35f,
    )

    /** A tick: a tiny bright click. */
    val TICK = Patch(
        wave = Wave.SINE, wave2 = Wave.NOISE, mix2 = 0.2f,
        attack = 0.001f, decay = 0.03f, sustain = 0f, release = 0.02f,
        cutoffHz = 9000f, gain = 0.12f, pan = 0.3f, send = 0.2f,
    )
}
