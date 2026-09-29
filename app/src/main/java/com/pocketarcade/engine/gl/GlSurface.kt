package com.pocketarcade.engine.gl

import android.app.ActivityManager
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The GPU drawing surface under the whole app. Screens don't draw into it directly: they
 * submit [com.pocketarcade.engine.r3d.RenderPass]es through [Gfx], placed in window pixels,
 * and Compose draws the interface on top.
 */
@Composable
fun GlSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Only made here: starting the thread is a side effect, so it waits for the effect below
    // (a surface that arrives first just waits in the thread's hand-off).
    val thread = remember { GlThread() }
    DisposableEffect(thread) {
        // A fresh thread has a fresh restart budget, so it may draw again after an earlier failure.
        Gfx.failure = null
        GfxQuality.lowRamDevice = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        thread.start()
        onDispose { thread.shutdown() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { view ->
                view.isOpaque = false
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        noteDisplay(view)
                        thread.setSurface(st, w, h)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        noteDisplay(view)
                        thread.resize(w, h)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        thread.releaseSurface()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** Tells [GfxQuality] how fast the display refreshes, which decides whether a frame cap bites. */
private fun noteDisplay(view: TextureView) {
    val hz = view.display?.refreshRate ?: 60f
    GfxQuality.displayHz = if (hz >= 30f) hz else 60f
}
