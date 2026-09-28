package com.pocketarcade.engine.gl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlGenerationTest {
    @Test
    fun everyContextGetsANewNumberAcrossRenderers() {
        // Two renderers (a surface torn down and recreated, e.g. Back then relaunch) must never
        // share a generation, or process-wide art would keep GL names from the dead context.
        val first = GlRenderer()
        val second = GlRenderer()
        val a = GlGeneration.next()
        val b = GlGeneration.next()
        assertNotEquals(a, b)
        assertTrue(b > a)
        assertTrue(first.generation == 0 && second.generation == 0) // not initialised yet
    }

    @Test
    fun resourcesFromAnOlderContextAreStale() {
        val old = GlGeneration.next()
        val now = GlGeneration.next()
        assertTrue(GlGeneration.isCurrent(now, now))
        assertFalse(GlGeneration.isCurrent(old, now))
        // Never-uploaded resources (-1) and uninitialised renderers (0) are never current.
        assertFalse(GlGeneration.isCurrent(-1, now))
        assertFalse(GlGeneration.isCurrent(0, 0))
    }

    @Test
    fun numbersStayUniqueAcrossThreads() {
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        val threads = List(4) { Thread { repeat(500) { seen += GlGeneration.next() } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(seen.size == 2000)
    }
}
