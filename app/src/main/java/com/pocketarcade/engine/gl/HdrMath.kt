package com.pocketarcade.engine.gl

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Every look-affecting number of the HDR pipeline, named and commented, in one place. The shaders
 * ([GlShaders]) are built from these, so the GLSL and the Kotlin mirrors in [HdrMath] (which the
 * unit tests pin) can never drift apart. All values are deliberately conservative: nothing here
 * has been seen on a device yet, so every effect errs on the quiet side.
 *
 * Units: "exposed linear" is scene light after the pass's exposure and before the tone map, where
 * about 1.0 is a well-lit white surface (the ACES fit maps it to 0.80).
 */
internal object HdrLook {
    // ---------------------------------------------------------------- the scene image's encoding
    /**
     * The scene target is RGBA16F holding `c / (1 + max(c))` (a reversible Reinhard squeeze of
     * the linear colour into 0..1 by its brightest channel), not raw linear light. Multisample
     * resolve, alpha blending and additive glows then behave like the LDR pipeline's (they happen
     * in a bounded, display-like space), so bright edges anti-alias instead of staying jagged; the
     * composite and the bloom decode it back to linear. [ENC_MAX] is the largest encoded value
     * ever decoded: it caps the light at `ENC_MAX / (1 - ENC_MAX)` = 24, so overlapping additive
     * glows can't run away into fireflies.
     */
    const val ENC_MAX = 0.96f

    /** The largest display value the inverse tone map is asked about (its curve climbs steeply near 1). */
    const val ACES_INV_MAX = 0.985f

    // ---------------------------------------------------------------- scene shading
    /**
     * Glowing surfaces (neon, screens, marquees) are lifted this much in HDR so they sit clearly
     * above anything merely lit; the ACES shoulder keeps their look close to the LDR one.
     */
    const val EMISSIVE_GAIN = 1.2f

    /**
     * Lit paint (diffuse, rim light and blacklight glow, not glints or reflections) is eased into
     * this ceiling, in exposed linear light, so a pale tile under many lamps can never reach the
     * bloom threshold and glow white. Below [LIT_START] it is untouched.
     */
    const val LIT_CEILING = 1.05f
    const val LIT_START = 0.6f

    // ---------------------------------------------------------------- bloom
    /**
     * Width of the soft knee around the bloom threshold (exposed linear): glows fade in over
     * `threshold - knee .. threshold + knee` instead of popping on at one brightness.
     */
    const val BLOOM_KNEE = 0.4f

    /**
     * Karis-style average on the first downsample: each of its four taps is weighted
     * `1 / (1 + brightness * KARIS_STRENGTH)`, so one hot texel can't dominate its block and
     * sparkle as a firefly. Kept mild (a full Karis weight would dim thin neon tubes to a third).
     */
    const val KARIS_STRENGTH = 0.25f

    /** The energy one texel may feed into the bloom chain: identity to [BLOOM_INPUT_START], then eased into the cap. */
    const val BLOOM_INPUT_CAP = 6f
    const val BLOOM_INPUT_START = 3f

    /**
     * Bloom's overall strength against the LDR picture's. The HDR halo is added before the tone
     * map, whose toe lifts a faint halo about twice as much as the LDR picture's plain addition
     * would show, so it is scaled back to land near the LDR halo's brightness (measured on
     * uniform patches through the real shaders in a WebGL2 harness, not yet on a phone).
     */
    const val BLOOM_GAIN = 0.6f

    /** The most light the finished bloom may add to a pixel: identity to [BLOOM_ADD_START], then eased into the cap. */
    const val BLOOM_ADD_CAP = 2.5f
    const val BLOOM_ADD_START = 1.2f

    /**
     * Energy conservation: bloom is added as `bloom / (1 + scene * BLOOM_SELF_SHADOW)`, so it
     * lands fully on dark surroundings (the halo) but is held back on pixels that are already
     * bright, which is what keeps lettering readable and pale surfaces from washing to white.
     */
    const val BLOOM_SELF_SHADOW = 0.5f

    // ---------------------------------------------------------------- tone map
    /**
     * Highlights above [HUE_KEEP_FROM] blend up to [HUE_KEEP] of the way from the per-channel ACES
     * curve (which pales a saturated neon toward white) to a hue-preserving one (which keeps its
     * colour). Below the range it is exactly the per-channel curve, the LDR pipeline's look.
     */
    const val HUE_KEEP = 0.4f
    const val HUE_KEEP_FROM = 0.9f
    const val HUE_KEEP_TO = 3.0f

    // ---------------------------------------------------------------- colour grade (after the tone map)
    /** The LDR grade's saturation and contrast, unchanged. */
    const val GRADE_SATURATION = 1.08f
    const val GRADE_CONTRAST = 0.12f

    /** Split toning: shadows lean teal, highlights lean warm, by this much of the tints below. */
    const val SPLIT_STRENGTH = 0.55f
    const val SHADOW_TINT_R = 0.93f
    const val SHADOW_TINT_G = 1.0f
    const val SHADOW_TINT_B = 1.03f
    const val HIGHLIGHT_TINT_R = 1.03f
    const val HIGHLIGHT_TINT_G = 1.0f
    const val HIGHLIGHT_TINT_B = 0.95f

    /** Lifted blacks: pure black comes out as this (a faint blue-teal), fading to nothing at white. */
    const val BLACK_LIFT_R = 0.010f
    const val BLACK_LIFT_G = 0.013f
    const val BLACK_LIFT_B = 0.018f

    // ---------------------------------------------------------------- vignette
    /** The vignette's radius range (of the aspect-corrected distance from the centre) and its faint cool cast at the corners. */
    const val VIGNETTE_FROM = 0.28f
    const val VIGNETTE_TO = 0.98f
    const val VIGNETTE_TINT_R = 0.96f
    const val VIGNETTE_TINT_G = 0.97f
    const val VIGNETTE_TINT_B = 1.0f

    // ---------------------------------------------------------------- film finish (see GfxQuality levers)
    /** Film grain amplitude (fraction of full scale, peak): 0.028 is about 3.5 of 255 levels at the peak. */
    const val GRAIN = 0.028f

    /**
     * Chromatic aberration: the red and blue images are pushed apart by `d * |d|² * amount`
     * (uv, d = offset from the centre), so it is zero mid-screen and a pixel or so at the corners.
     * [ABERRATION_MOTION] is the extra multiple at full camera motion (a dive or a fast turn).
     */
    const val ABERRATION = 0.0022f
    const val ABERRATION_MOTION = 1.5f

    /** Anamorphic glare on the very brightest neon: how much is added, and the streak's shape. */
    const val GLARE_AMOUNT = 0.55f
    const val GLARE_THRESHOLD = 0.9f
    const val GLARE_TAPS = 8

    /** Tap spacing of the glare blur, in texels of the bloom's second octave. */
    const val GLARE_SPACING = 2f
    const val GLARE_FALLOFF = 0.28f
    const val GLARE_GAIN = 2.2f
    const val GLARE_TINT_R = 0.85f
    const val GLARE_TINT_G = 0.95f
    const val GLARE_TINT_B = 1.1f

    // ---------------------------------------------------------------- camera-motion signal for the aberration
    /** Camera motion (radians turned plus travel over [MOTION_TRAVEL] world units, per frame) that counts as "full". */
    const val MOTION_FULL = 0.06f
    const val MOTION_TRAVEL = 60f

    /** Share of the new reading taken each frame, so the aberration eases rather than flickers. */
    const val MOTION_SMOOTH = 0.18f
}

/**
 * Kotlin mirrors of the HDR shaders' maths, so the unit tests can pin what the GLSL does. Each
 * function has a twin of the same name in [GlShaders.HDR_GLSL] (or the bloom shader).
 */
internal object HdrMath {
    /** The ACES filmic fit used by both pipelines (Narkowicz), for one channel, clamped to 0..1. */
    fun aces(c: Float): Float = ((c * (2.51f * c + 0.03f)) / (c * (2.43f * c + 0.59f) + 0.14f)).coerceIn(0f, 1f)

    /** The display value [y] (0..1) back to the linear light [aces] maps to it; clamped near 1, where the curve is flat. */
    fun inverseAces(y: Float): Float {
        val v = y.coerceIn(0f, HdrLook.ACES_INV_MAX)
        val a = 2.43f * v - 2.51f
        val b = 0.59f * v - 0.03f
        val c = 0.14f * v
        return (b + sqrt(max(b * b - 4f * a * c, 0f))) / (2f * (2.51f - 2.43f * v))
    }

    /** The scene encoding of a brightest-channel value [m] (linear light): 0..1. */
    fun encode(m: Float): Float = max(m, 0f) / (1f + max(m, 0f))

    /** Linear light back from an encoded brightest-channel value, capped at what [HdrLook.ENC_MAX] allows. */
    fun decode(x: Float): Float = min(x, HdrLook.ENC_MAX) / (1f - min(x, HdrLook.ENC_MAX))

    /**
     * Identity up to [start], then an exponential ease into [cap] that never exceeds it and has
     * no kink at [start] (slope 1 on both sides). Used for the bloom's input, its added light and
     * the lit-paint ceiling.
     */
    fun softCap(x: Float, cap: Float, start: Float): Float {
        if (x <= start) return x
        val r = max(cap - start, 1e-4f)
        return start + r * (1f - exp(-(x - start) / r))
    }

    /**
     * The bloom threshold with a soft knee: how much of a pixel of brightness [br] (exposed
     * linear) goes into the glow. Zero well below `threshold - knee`, a smooth quadratic ramp
     * through the knee, then `br - threshold`; finally eased into [cap] so no single texel
     * feeds the chain more than that.
     */
    fun bloomEnergy(
        br: Float,
        threshold: Float,
        knee: Float = HdrLook.BLOOM_KNEE,
        cap: Float = HdrLook.BLOOM_INPUT_CAP,
        capStart: Float = HdrLook.BLOOM_INPUT_START,
    ): Float {
        val s = (br - threshold + knee).coerceIn(0f, 2f * knee)
        val soft = s * s / (4f * knee)
        return softCap(max(soft, br - threshold), cap, capStart)
    }

    /** The Karis weight of a first-downsample tap of brightness [br]. */
    fun karisWeight(br: Float, strength: Float = HdrLook.KARIS_STRENGTH): Float = 1f / (1f + br * strength)

    /** The bloom light a pixel of scene brightness [scene] actually receives when [bloom] is added (energy-conserving, capped). */
    fun bloomAdded(bloom: Float, scene: Float): Float =
        softCap(bloom, HdrLook.BLOOM_ADD_CAP, HdrLook.BLOOM_ADD_START) / (1f + scene * HdrLook.BLOOM_SELF_SHADOW)

    /** The lit-paint ceiling: [m] is the exposed brightest channel of the paint's light. */
    fun litLimit(m: Float): Float = softCap(m, HdrLook.LIT_CEILING, HdrLook.LIT_START)
}
