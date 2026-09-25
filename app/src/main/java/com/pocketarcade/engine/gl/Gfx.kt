package com.pocketarcade.engine.gl

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.pocketarcade.engine.r3d.RenderPass

/**
 * The hand-off between the UI thread, which records [RenderPass]es, and the GL thread, which
 * draws them. Each screen area submits to its own named slot; the newest pass per slot wins.
 */
object Gfx {
    private val lock = Object()
    private val pending = LinkedHashMap<String, RenderPass>()
    private val removed = HashSet<String>()
    private val snapshots = ArrayDeque<Pair<RenderPass, (Bitmap) -> Unit>>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Queues [pass] as the next picture for [slot], replacing one the GL thread hasn't taken yet. */
    fun submit(slot: String, pass: RenderPass) {
        synchronized(lock) {
            pending.put(slot, pass)?.recycle()
            removed.remove(slot)
            lock.notifyAll()
        }
    }

    /** Stops drawing [slot] (its screen went away). */
    fun remove(slot: String) {
        synchronized(lock) {
            pending.remove(slot)?.recycle()
            removed += slot
            lock.notifyAll()
        }
    }

    /**
     * Renders [pass] off screen at its full size and hands the picture to [onReady] on the main
     * thread (for thumbnails and previews that live inside UI).
     */
    fun snapshot(pass: RenderPass, onReady: (Bitmap) -> Unit) {
        synchronized(lock) {
            snapshots.addLast(pass to onReady)
            lock.notifyAll()
        }
    }

    /** GL thread: the next snapshot to take, if any. */
    internal fun nextSnapshot(): Pair<RenderPass, (Bitmap) -> Unit>? = synchronized(lock) { snapshots.removeFirstOrNull() }

    /** GL thread: delivers a finished snapshot. */
    internal fun deliver(onReady: (Bitmap) -> Unit, bitmap: Bitmap) {
        main.post { onReady(bitmap) }
    }

    /**
     * GL thread: waits up to [timeoutMs] for news, then merges new passes into [current]
     * (recycling the ones they replace). Returns whether anything changed.
     */
    internal fun take(current: LinkedHashMap<String, RenderPass>, timeoutMs: Long): Boolean {
        synchronized(lock) {
            if (pending.isEmpty() && removed.isEmpty() && snapshots.isEmpty()) lock.wait(timeoutMs)
            if (pending.isEmpty() && removed.isEmpty()) return snapshots.isNotEmpty()
            for (s in removed) current.remove(s)?.recycle()
            removed.clear()
            for ((s, p) in pending) current.put(s, p)?.recycle()
            pending.clear()
            return true
        }
    }

    /** Wakes the GL thread (for surface changes). */
    internal fun poke() {
        synchronized(lock) { lock.notifyAll() }
    }
}
