package com.pocketarcade.engine.gl

/**
 * What the GL driver offers that the HDR pipeline cares about, read once per context from its
 * version and extension strings. Pure (no GL calls), so the decision below is unit-tested.
 */
internal class GlCaps(version: String, extensions: String, val maxSamples: Int) {
    private val tokens = extensions.split(' ').filter { it.isNotEmpty() }.toHashSet()

    /** OpenGL ES 3.2 folds EXT_color_buffer_float into the core. */
    val es32: Boolean = ES_VERSION.find(version)?.destructured?.let { (major, minor) ->
        major.toInt() > 3 || (major.toInt() == 3 && minor.toInt() >= 2)
    } ?: false

    /** Half and full float colour buffers, including multisampled ones and R11G11B10F. */
    val colorBufferFloat: Boolean = es32 || "GL_EXT_color_buffer_float" in tokens

    /** RGBA16F colour buffers (single-sampled only, as the extension promises). */
    val colorBufferHalfFloat: Boolean = "GL_EXT_color_buffer_half_float" in tokens

    /** Whether an RGBA16F target can be rendered to at all. */
    val floatTargets: Boolean get() = colorBufferFloat || colorBufferHalfFloat

    /** Whether a multisampled RGBA16F renderbuffer may be offered (still verified with `glCheckFramebufferStatus`). */
    val floatMsaa: Boolean get() = colorBufferFloat && maxSamples > 1

    private companion object {
        val ES_VERSION = Regex("OpenGL ES (\\d+)\\.(\\d+)")
    }
}

/** Which picture the renderer draws with. */
internal enum class Pipeline(val label: String) {
    /** RGBA8 scene target with a per-fragment tone map: the picture before HDR existed. */
    LDR("LDR"),

    /** RGBA16F scene target, no multisampling. */
    HDR("HDR16F"),

    /** RGBA16F multisampled scene target resolved into an RGBA16F texture. */
    HDR_MSAA("HDR16F+MSAA"),
    ;

    val isHdr: Boolean get() = this != LDR
}

/**
 * Chooses the pipeline from the capability set. Anything doubtful means [Pipeline.LDR]:
 * - never for [GfxQuality.Tier.BATTERY], and only where the current ladder rung allows it;
 * - never once [HdrGuard] has blocked it (a shader, a framebuffer or a GL error failed before);
 * - a device that can't render to float targets stays LDR;
 * - where multisampling is wanted, HDR needs *multisampled* float renderbuffers; a driver that
 *   can only do single-sampled float keeps the LDR picture with its anti-aliasing rather than
 *   trade jagged edges for HDR. Where no multisampling is wanted, single-sampled HDR will do.
 */
internal object HdrPlan {
    fun choose(
        caps: GlCaps?,
        tier: GfxQuality.Tier,
        rungHdr: Boolean,
        samplesWanted: Int,
        blocked: Boolean,
        msaaBlocked: Boolean,
    ): Pipeline {
        if (caps == null || blocked || !rungHdr || tier == GfxQuality.Tier.BATTERY || !caps.floatTargets) return Pipeline.LDR
        if (samplesWanted > 1) return if (caps.floatMsaa && !msaaBlocked) Pipeline.HDR_MSAA else Pipeline.LDR
        return Pipeline.HDR
    }
}

/**
 * The record of what went wrong with the HDR pipeline, shared by the whole process so that a GL
 * thread restarted with a fresh context doesn't walk into the same failure again. The renderer
 * disables the pipeline at the first sign of trouble (a shader that won't link, an incomplete
 * framebuffer, a GL error on the first frames, an exception while it is on) and draws the frame
 * again with the LDR picture, which is unchanged and has always worked.
 */
internal object HdrGuard {
    /** HDR is off for the rest of the run. */
    @Volatile var blocked = false
        private set

    /** Multisampled float targets failed: HDR is only possible without multisampling (in practice, off). */
    @Volatile var msaaBlocked = false
        private set

    /** Why, for the log. */
    @Volatile var reason = ""
        private set

    fun block(why: String) {
        if (!blocked) reason = why
        blocked = true
    }

    fun blockMsaa(why: String) {
        if (!msaaBlocked && !blocked) reason = why
        msaaBlocked = true
    }

    /** Clears everything (tests). */
    fun reset() {
        blocked = false
        msaaBlocked = false
        reason = ""
    }
}
