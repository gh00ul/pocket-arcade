package com.pocketarcade.engine.gl

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The GPU drawing surface under the whole app. Screens don't draw into it directly: they
 * submit [com.pocketarcade.engine.r3d.RenderPass]es through [Gfx], placed in window pixels,
 * and Compose draws the interface on top.
 */
@Composable
fun GlSurface(modifier: Modifier = Modifier) {
    val thread = remember { GlThread().also { it.start() } }
    DisposableEffect(thread) {
        onDispose { thread.shutdown() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = thread.setSurface(st, w, h)
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = thread.resize(w, h)
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
