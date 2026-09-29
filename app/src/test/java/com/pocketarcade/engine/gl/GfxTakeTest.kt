package com.pocketarcade.engine.gl

import com.pocketarcade.engine.r3d.RenderPass
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The GL thread waits for news in [Gfx.take]. A queued snapshot is only news when there is a
 * surface to draw it with: without one it used to make `take` return at once, every time, and
 * the thread spun flat out while the app sat in the background.
 */
class GfxTakeTest {
    private fun pass() = RenderPass(ConcurrentLinkedQueue())

    @After
    fun leaveNothingBehind() {
        while (Gfx.nextSnapshot() != null) Unit
        Gfx.slotsChanged = false
    }

    @Test
    fun aQueuedSnapshotIsNotNewsWithoutASurface() {
        Gfx.snapshot(pass()) { }
        val started = System.nanoTime()
        assertFalse(Gfx.take(LinkedHashMap(), 40L, canSnapshot = false))
        // It rested for its timeout instead of coming straight back.
        assertTrue("returned at once: the GL thread would spin", System.nanoTime() - started >= 25_000_000L)
    }

    @Test
    fun aQueuedSnapshotIsNewsOnceThereIsASurface() {
        Gfx.snapshot(pass()) { }
        val started = System.nanoTime()
        assertTrue(Gfx.take(LinkedHashMap(), 200L, canSnapshot = true))
        assertTrue("should not have waited", System.nanoTime() - started < 150_000_000L)
    }

    @Test
    fun aNewPictureIsStillNewsWithoutASurface() {
        val current = LinkedHashMap<String, RenderPass>()
        Gfx.submit("gfx-take-test", pass())
        assertTrue(Gfx.take(current, 40L, canSnapshot = false))
        assertTrue(current.containsKey("gfx-take-test"))
        Gfx.remove("gfx-take-test")
        assertTrue(Gfx.take(current, 40L, canSnapshot = false))
        assertFalse(current.containsKey("gfx-take-test"))
    }
}
