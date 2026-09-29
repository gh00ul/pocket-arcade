package com.pocketarcade.engine.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.SystemClock
import android.util.Log
import com.pocketarcade.engine.r3d.RenderPass

/**
 * Owns the OpenGL ES 3 context and draws submitted passes into the window's surface. The
 * context survives the surface coming and going (the app pausing). If the context is lost or
 * the thread fails, everything is torn down and rebuilt with a fresh context (textures and
 * models upload again), within the limits of a [RestartPolicy]; past those the thread gives up
 * and reports it through [Gfx.failure].
 */
internal class GlThread : Thread("ArcadeGL") {
    companion object {
        private const val TAG = "PocketArcadeGL"
        /** How long a UI-thread [shutdown] waits for the GL thread to wind down. */
        private const val JOIN_MS = 250L
        /** Pause before starting over after a failure, so a broken driver can't spin the CPU. */
        private const val RESTART_DELAY_MS = 150L
    }

    /** Guards the surface hand-off with the UI thread: [surfaceTexture] through [finished]. */
    private val lock = Object()
    private var surfaceTexture: SurfaceTexture? = null
    private var width = 0
    private var height = 0
    private var surfaceChanged = false
    private var releaseRequested = false
    private var surfaceReleased = true
    private var finished = false
    @Volatile private var quit = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer = GlRenderer()
    private val stats = FrameStats()
    private val current = LinkedHashMap<String, RenderPass>()

    /** The surface size for the frame being drawn, copied under [lock] (GL thread only). */
    private var drawW = 0
    private var drawH = 0

    // Frame pacing for the adaptive quality ladder, and the frame-rate cap.
    private val pacer = ScalePacer(start = GlRenderer.START_SCALE, ladder = GfxQuality.LADDER)
    private val gate = FrameGate()
    /** A frame is wanted but the cap has held it back for now. */
    private var wantFrame = false
    private var appliedTier: GfxQuality.Tier? = null
    private var rungChosen = false

    private class ContextLost : RuntimeException("GL context lost")

    fun setSurface(st: SurfaceTexture, w: Int, h: Int) {
        synchronized(lock) {
            surfaceTexture = st
            width = w
            height = h
            surfaceChanged = true
            surfaceReleased = false
        }
        Gfx.poke()
    }

    fun resize(w: Int, h: Int) {
        synchronized(lock) {
            width = w
            height = h
            surfaceChanged = true
        }
        Gfx.poke()
    }

    /** Called from the UI thread when the surface is destroyed; blocks until GL lets go of it. */
    fun releaseSurface() {
        synchronized(lock) {
            if (surfaceTexture == null) return
            // A thread that has already exited let go of it on its way out: nothing to wait for.
            if (!finished) {
                releaseRequested = true
                Gfx.poke()
                val deadline = SystemClock.uptimeMillis() + 1000
                while (!surfaceReleased && !finished && SystemClock.uptimeMillis() < deadline) {
                    lock.wait(50)
                }
            }
            surfaceTexture = null
        }
    }

    /** Asks the thread to stop and waits a moment for it, so a new one doesn't overlap its context. */
    fun shutdown() {
        quit = true
        Gfx.poke()
        if (currentThread() !== this && isAlive) {
            try {
                join(JOIN_MS)
            } catch (_: InterruptedException) {
                currentThread().interrupt()
            }
        }
    }

    override fun run() {
        val policy = RestartPolicy()
        try {
            while (!quit) {
                var failure: Throwable? = null
                try {
                    loop()
                } catch (t: Throwable) {
                    failure = t
                }
                // The context belongs to this thread, so it is always torn down here.
                teardown()
                if (failure == null || quit) break
                val fatal = failure is GlUnsupportedException
                Log.e(TAG, if (fatal) "This device can't make an OpenGL ES 3 context" else "GL thread failed", failure)
                if (!policy.shouldRestart(SystemClock.elapsedRealtime(), fatal)) {
                    Log.e(TAG, "Giving up on graphics")
                    Gfx.reportFailure(if (fatal) GfxFailure.NEEDS_ES3 else GfxFailure.STOPPED)
                    break
                }
                Log.w(TAG, "Restarting the GL thread with a fresh context")
                renderer = GlRenderer()
                renderer.renderScale = pacer.scale
                synchronized(lock) { surfaceChanged = true }
                try {
                    sleep(RESTART_DELAY_MS)
                } catch (_: InterruptedException) {
                    // Woken early: carry on restarting.
                }
            }
        } finally {
            teardown()
            synchronized(lock) {
                finished = true
                surfaceReleased = true
                lock.notifyAll()
            }
        }
    }

    private fun loop() {
        while (!quit) {
            val cap = GfxQuality.effectiveCap()
            val hz = GfxQuality.displayHz
            // A frame the cap is holding back waits only as long as the cap needs; otherwise
            // rest until there is news.
            val restMs = if (wantFrame) (gate.waitNs(SystemClock.elapsedRealtimeNanos(), cap, hz) + 999_999L) / 1_000_000L else 250L
            if (Gfx.take(current, maxOf(1L, restMs), canSnapshot = surface != EGL14.EGL_NO_SURFACE)) wantFrame = true
            // The context is built out here, not under the lock: compiling the shaders takes a
            // while, and the UI thread must stay free to resize, attach or release the surface.
            if (context == EGL14.EGL_NO_CONTEXT && hasSurfaceWaiting()) ensureContext()
            synchronized(lock) {
                if (releaseRequested) {
                    destroySurface()
                    releaseRequested = false
                    surfaceReleased = true
                    lock.notifyAll()
                }
                val st = surfaceTexture
                if (surfaceChanged && st != null && context != EGL14.EGL_NO_CONTEXT) {
                    surfaceChanged = false
                    if (surface == EGL14.EGL_NO_SURFACE) createSurface(st)
                    wantFrame = true
                    // Uploads and shader compiles make the first frames slow: don't judge them.
                    pacer.reset(SystemClock.elapsedRealtimeNanos(), keepScale = true)
                }
                drawW = width
                drawH = height
            }
            if (Gfx.slotsChanged) {
                // A different screen: start again from the default scale.
                Gfx.slotsChanged = false
                pacer.reset(SystemClock.elapsedRealtimeNanos())
                renderer.renderScale = pacer.scale
            }
            if (!wantFrame || surface == EGL14.EGL_NO_SURFACE) {
                wantFrame = false
                continue
            }
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                if (EGL14.eglGetError() == EGL14.EGL_CONTEXT_LOST) throw ContextLost()
                continue
            }
            while (true) {
                val (pass, onReady) = Gfx.nextSnapshot() ?: break
                val bitmap = renderer.snapshot(pass)
                pass.recycle()
                Gfx.deliver(onReady, bitmap)
            }
            val now = SystemClock.elapsedRealtimeNanos()
            if (gate.waitNs(now, cap, hz) > 0L) continue
            gate.take(now)
            wantFrame = false
            syncQuality(cap)
            stats.beginFrame(renderer.generation)
            if (stats.enabled) for (p in current.values) stats.notePass(p)
            renderer.floorReflectOff = stats.noFloorReflect
            renderer.drawFrame(current.values, drawW, drawH)
            stats.noteDraws(renderer.drawCalls, renderer.vertsDrawn)
            stats.endFrame()
            if (!EGL14.eglSwapBuffers(display, surface)) {
                val err = EGL14.eglGetError()
                if (err == EGL14.EGL_CONTEXT_LOST) throw ContextLost() else if (err == EGL14.EGL_BAD_SURFACE) destroySurface()
            }
            pace()
            stats.afterSwap(renderer.renderScale, pacer.rung)
        }
    }

    /** Whether the UI has handed over a surface that isn't being taken away. */
    private fun hasSurfaceWaiting(): Boolean = synchronized(lock) { surfaceTexture != null && !releaseRequested }

    /**
     * Follows the quality settings before a frame: a new tier confines the ladder, the first
     * frame on a context picks the starting rung from the device, and the cap sets the pacer's
     * idea of a good frame time. Then hands the rung's levers to the renderer and the recorder.
     */
    private fun syncQuality(cap: Int) {
        val tier = GfxQuality.tier
        if (tier != appliedTier) {
            appliedTier = tier
            pacer.setLimits(GfxQuality.topRung(tier), GfxQuality.bottomRung(tier))
        }
        if (!rungChosen) {
            rungChosen = true
            val device = GfxQuality.deviceRung(renderer.glRendererName, GfxQuality.lowRamDevice)
            Log.i(TAG, "Quality tier $tier, device rung $device")
            pacer.jumpTo(GfxQuality.startRung(tier, device))
        }
        pacer.targetFps = cap
        // The pinned scale of frame-time comparisons ([FrameStats.pinScale]) pins the picture too.
        val r = GfxQuality.LADDER[if (stats.pinScale) 0 else pacer.rung]
        renderer.applyRung(r)
        if (GfxQuality.lightBudget != r.lights) GfxQuality.lightBudget = r.lights
    }

    /** Lowers the render resolution (then effects) when frames run long and raises them again when there's headroom. */
    private fun pace() {
        val gpu = stats.takeGpuMs()
        if (stats.pinScale) {
            renderer.renderScale = GlRenderer.START_SCALE
            return
        }
        renderer.renderScale = pacer.onFrame(SystemClock.elapsedRealtimeNanos(), gpu)
    }

    private fun ensureContext() {
        if (context != EGL14.EGL_NO_CONTEXT) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("No EGL display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed: ${EGL14.eglGetError()}")
        }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        // No ES 3 config at all: a device that old will never draw the arcade, so don't retry.
        config = configs[0] ?: throw GlUnsupportedException("No OpenGL ES 3 EGL config")
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            val err = EGL14.eglGetError()
            // Running out of memory may pass; anything else means ES 3 is not on offer.
            if (err == EGL14.EGL_BAD_ALLOC) throw IllegalStateException("Out of memory creating the GL context")
            throw GlUnsupportedException("Could not create a GLES 3 context (EGL error $err)")
        }
        // A tiny pbuffer lets us initialise GL state before a window surface exists.
        val pb = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, pb, pb, context)
        renderer.init()
        GfxQuality.glParticles = renderer.particlesOk
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, pb)
    }

    private fun createSurface(st: SurfaceTexture) {
        surface = EGL14.eglCreateWindowSurface(display, config, st, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            Log.w(TAG, "eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
        }
    }

    private fun destroySurface() {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE
        }
    }

    /** Lets go of the window surface, the context and the display; safe to call more than once. */
    private fun teardown() {
        destroySurface()
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
        config = null
    }
}
