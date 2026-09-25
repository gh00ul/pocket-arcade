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
 * context survives the surface coming and going (the app pausing); if the context itself is
 * lost, everything is rebuilt and textures and models upload again.
 */
internal class GlThread : Thread("ArcadeGL") {
    private val lock = Object()
    private var surfaceTexture: SurfaceTexture? = null
    private var width = 0
    private var height = 0
    private var surfaceChanged = false
    private var releaseRequested = false
    private var surfaceReleased = true
    @Volatile private var quit = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val renderer = GlRenderer()
    private val current = LinkedHashMap<String, RenderPass>()

    // Frame pacing for the adaptive resolution.
    private var slowFrames = 0
    private var fastFrames = 0
    private var lastSwap = 0L

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
            releaseRequested = true
            Gfx.poke()
            val deadline = SystemClock.uptimeMillis() + 1000
            while (!surfaceReleased && SystemClock.uptimeMillis() < deadline) {
                lock.wait(50)
            }
            surfaceTexture = null
        }
    }

    fun shutdown() {
        quit = true
        Gfx.poke()
    }

    override fun run() {
        try {
            loop()
        } catch (t: Throwable) {
            Log.e("PocketArcadeGL", "GL thread crashed", t)
        } finally {
            destroySurface()
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
        }
    }

    private fun loop() {
        while (!quit) {
            var needDraw = Gfx.take(current, 250)
            synchronized(lock) {
                if (releaseRequested) {
                    destroySurface()
                    releaseRequested = false
                    surfaceReleased = true
                    lock.notifyAll()
                }
                if (surfaceChanged && surfaceTexture != null) {
                    surfaceChanged = false
                    ensureContext()
                    if (surface == EGL14.EGL_NO_SURFACE) createSurface(surfaceTexture!!)
                    needDraw = true
                }
            }
            if (!needDraw || surface == EGL14.EGL_NO_SURFACE) continue
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                if (EGL14.eglGetError() == EGL14.EGL_CONTEXT_LOST) recreateContext()
                continue
            }
            while (true) {
                val (pass, onReady) = Gfx.nextSnapshot() ?: break
                val bitmap = renderer.snapshot(pass)
                pass.recycle()
                Gfx.deliver(onReady, bitmap)
            }
            renderer.drawFrame(current.values, width, height)
            if (!EGL14.eglSwapBuffers(display, surface)) {
                val err = EGL14.eglGetError()
                if (err == EGL14.EGL_CONTEXT_LOST) recreateContext() else if (err == EGL14.EGL_BAD_SURFACE) destroySurface()
            }
            pace()
        }
    }

    /** Lowers the render resolution when frames run long and raises it again when there's headroom. */
    private fun pace() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (lastSwap != 0L) {
            val ms = (now - lastSwap) / 1_000_000f
            if (ms > 24f && ms < 200f) {
                slowFrames++; fastFrames = 0
            } else if (ms < 17.5f) {
                fastFrames++; slowFrames = 0
            }
            if (slowFrames > 30 && renderer.renderScale > 0.5f) {
                renderer.renderScale = (renderer.renderScale - 0.1f).coerceAtLeast(0.5f)
                slowFrames = 0
            } else if (fastFrames > 300 && renderer.renderScale < 0.8f) {
                renderer.renderScale = (renderer.renderScale + 0.1f).coerceAtMost(0.8f)
                fastFrames = 0
            }
        }
        lastSwap = now
    }

    private fun ensureContext() {
        if (context != EGL14.EGL_NO_CONTEXT) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
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
        config = configs[0] ?: throw IllegalStateException("No suitable EGL config")
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("Could not create a GLES 3 context")
        // A tiny pbuffer lets us initialise GL state before a window surface exists.
        val pb = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, pb, pb, context)
        renderer.init()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, pb)
    }

    private fun createSurface(st: SurfaceTexture) {
        surface = EGL14.eglCreateWindowSurface(display, config, st, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            Log.w("PocketArcadeGL", "eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
        }
    }

    private fun destroySurface() {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE
        }
    }

    private fun recreateContext() {
        Log.w("PocketArcadeGL", "GL context lost; rebuilding")
        destroySurface()
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        context = EGL14.EGL_NO_CONTEXT
        ensureContext()
        surfaceTexture?.let { createSurface(it) }
    }
}
