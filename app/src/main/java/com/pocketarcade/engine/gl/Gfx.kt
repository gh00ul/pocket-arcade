package com.pocketarcade.engine.gl

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketarcade.engine.r3d.RenderPass

/** Why the GPU picture stopped for good; the app shows a notice for it. */
enum class GfxFailure {
    /** The device cannot make an OpenGL ES 3.0 context. */
    NEEDS_ES3,

    /** The GL thread kept failing and was given up on. */
    STOPPED,
}

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

    /** GL thread: set by [take] when a slot appeared or went away (a different screen). */
    internal var slotsChanged = false

    /**
     * Non-null once the GL thread has given up (see [RestartPolicy]): nothing will be drawn
     * again this run, so the app shows a notice instead of a blank screen. Compose state.
     */
    var failure by mutableStateOf<GfxFailure?>(null)
        internal set

    /** GL thread: reports that it has given up. The state is only ever written on the main thread. */
    internal fun reportFailure(f: GfxFailure) {
        main.post { failure = f }
    }

    /** Queues [pass] as the next picture for [slot], replacing one the GL thread hasn't taken yet. */
    fun submit(slot: String, pass: RenderPass) {
        if (pass.skipped) {
            // A frame the cap skipped: nothing new to draw, so the last picture stays up.
            pass.recycle()
            return
        }
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
            for (s in removed) {
                val old = current.remove(s)
                if (old != null) {
                    old.recycle()
                    slotsChanged = true
                }
            }
            removed.clear()
            for ((s, p) in pending) {
                val old = current.put(s, p)
                if (old != null) old.recycle() else slotsChanged = true
            }
            pending.clear()
            return true
        }
    }

    /** Wakes the GL thread (for surface changes). */
    internal fun poke() {
        synchronized(lock) { lock.notifyAll() }
    }
}
