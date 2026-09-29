package com.pocketarcade.engine.gl

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D

/**
 * Hands the GPU the models, textures and shaders a screen will need before the first frame that
 * draws them. Left alone, each mesh and texture reaches the GPU the first time a frame draws it
 * (the mesh is packed and every texture converted and uploaded on the GL thread), so the first
 * seconds in the hall stutter as the camera sees each cabinet for the first time.
 *
 * A warm-up is an ordinary off-screen picture ([Gfx.snapshot]) of a tiny window with the things to
 * upload drawn in it, so the GL thread does the work itself when it has a context and a surface, in
 * its own time, and answers on the main thread. Nothing here waits: [submit] returns a [Ticket]
 * to poll (a loading plan's `Wait` step does), and a GPU that never answers (no surface yet, the
 * app in the background, a context that keeps being lost) leaves the ticket open, so callers give
 * up after a timeout and call [giveUp], after which every later request is answered at once and
 * the hall simply uploads as it draws, as before.
 */
object Warmup {
    /** Side of the throwaway picture. The cost is in the uploads and the first draw, not in the pixels. */
    const val SIZE = 32

    /** A request in flight: [done] once the GL thread has drawn it (or it was never going to). */
    class Ticket internal constructor(done: Boolean = false) {
        @Volatile
        var done: Boolean = done
            internal set
    }

    /** True once a warm-up has been given up on (see [giveUp]) or the GL thread failed for good. */
    @Volatile
    var disabled = false
        private set

    /** Stops warming: the GPU isn't answering, so later requests complete immediately. */
    fun giveUp() {
        disabled = true
    }

    /** Only the main thread records, so one renderer serves every request. */
    private val renderer by lazy { Renderer3D(SIZE, SIZE) }

    /**
     * Records what [record] draws into a [SIZE]-square picture and queues it for the GL thread.
     * The camera looks down at the origin from close by, models are never culled, and there are no
     * lights: everything drawn is uploaded whether or not it would be seen. Call from the main thread.
     */
    fun submit(record: (Renderer3D) -> Unit): Ticket {
        if (disabled || Gfx.failure != null) return Ticket(done = true)
        val ticket = Ticket()
        val r = renderer
        r.startFrame()
        r.resize(SIZE, SIZE)
        r.camera.lookAt(0f, 200f, 120f, 0f, 0f, 0f, 0.9f, SIZE, SIZE)
        r.cullModels = false
        r.drawDistance = 0f
        r.lighting.points.clear()
        r.lighting.ambR = 0.3f; r.lighting.ambG = 0.3f; r.lighting.ambB = 0.3f
        r.clear(0xFF000000.toInt())
        record(r)
        val pass = r.finishFrame(0, 0, SIZE, SIZE)
        Gfx.snapshot(pass) { bitmap ->
            bitmap.recycle()
            ticket.done = true
        }
        return ticket
    }

    /** Draws [region]'s texture as a little decal in view, so drawing code with no model to hand still uploads it. */
    fun touch(r: Renderer3D, region: Region) {
        r.decal(-1f, -1f, 1f, 1f, 0.05f, region, Blend.ALPHA)
    }
}
