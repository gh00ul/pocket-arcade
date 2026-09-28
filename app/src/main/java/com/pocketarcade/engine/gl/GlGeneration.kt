package com.pocketarcade.engine.gl

import java.util.concurrent.atomic.AtomicInteger

/**
 * Numbers every GL context the process creates. Textures and models are cached process-wide
 * (in `by lazy` art objects) and remember which generation their GL names belong to; since a
 * new surface brings a new [GlThread] and [GlRenderer], the count must be process-wide too, or
 * a second context would start at the same number and trust names from a dead one.
 */
internal object GlGeneration {
    private val counter = AtomicInteger(0)

    /** Claims the number for a freshly created context. Never returns the same value twice. */
    fun next(): Int = counter.incrementAndGet()

    /** Whether a resource stamped with [resourceGen] can be used in context [contextGen]. */
    fun isCurrent(resourceGen: Int, contextGen: Int): Boolean = resourceGen == contextGen && contextGen > 0
}
