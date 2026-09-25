package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.Plush
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.claw.Plush3D
import com.pocketarcade.hub.CharacterLook
import com.pocketarcade.hub.Figure
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.HubLayout
import com.pocketarcade.hub.Pose
import com.pocketarcade.hub.Prop
import com.pocketarcade.hub.PropKind
import com.pocketarcade.hub.Props
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * A little photo studio for menu pictures: a backdrop, a turntable and soft lights. Scenes are
 * recorded here and photographed off screen by the GPU (see [Thumbs]).
 */
class Studio(val r: Renderer3D, val w: Int, val h: Int) {
    companion object {
        private val turntable: Model by lazy {
            ModelBuilder()
                .cylinder(0f, 0f, -2f, 0f, 1f, 40, HallArt.darkMetal.full, top = HallArt.solid(0xFF2B2140.toInt()).full, gloss = 0.7f)
                .build()
        }
        private val figures = HashMap<CharacterLook, Figure>()
        private val decor = HashMap<DecorStyle, Pair<Model, List<PointLight>>>()
    }

    private val xf = Xform()

    init {
        r.clear(0xFF120C22.toInt())
        r.gradient(0xFF2E2150.toInt(), 0xFF120C22.toInt())
        val l = r.lighting
        l.ambR = 0.42f; l.ambG = 0.4f; l.ambB = 0.5f
        l.setDirection(-0.45f, 0.8f, 0.7f)
        l.dirR = 0.75f; l.dirG = 0.72f; l.dirB = 0.68f
        l.points.clear()
        r.fogNear = 5000f
        r.fogFar = 9000f
        r.exposure = 1.15f
        r.bloom = 0.35f
    }

    /** Aims the camera so a ball of [radius] round ([cx], [cy], [cz]) fills the picture. */
    fun frame(cx: Float, cy: Float, cz: Float, radius: Float, pitchDeg: Float = 14f) {
        val fovY = Math.toRadians(30.0).toFloat()
        val aspect = w.toFloat() / h
        val halfMin = minOf(tan(fovY / 2f), tan(fovY / 2f) * aspect)
        val dist = radius / halfMin * 1.08f
        val p = Math.toRadians(pitchDeg.toDouble()).toFloat()
        r.camera.lookAt(cx, cy + sin(p) * dist, cz + cos(p) * dist, cx, cy, cz, fovY, w, h)
        // A warm key light, a cool rim from behind and the turntable under it all.
        r.lighting.points += PointLight(cx - radius * 1.5f, cy + radius * 1.8f, cz + radius * 2f, 1f, 0.9f, 0.8f, radius * 6f, 0.7f)
        r.lighting.points += PointLight(cx + radius * 1.2f, cy + radius, cz - radius * 1.8f, 0.5f, 0.7f, 1f, radius * 5f, 0.9f)
        xf.set(cx, 0f, cz, scale = radius * 0.95f)
        turntable.draw(r, xf = xf)
    }

    fun figure(look: CharacterLook, yaw: Float) {
        frame(0f, 23f, 0f, 28f)
        figures.getOrPut(look) { Figure(look) }.draw(r, 0f, 0f, 0f, yaw, Pose.STAND, 0f, 0f)
        r.flat(0f, 0f, 0.15f, 20f, 14f, HallArt.shadow.full, blend = Blend.ALPHA, alpha = 0.5f)
    }

    fun plush(p: Plush, silhouette: Boolean) {
        frame(0f, 9.5f, 0f, 14f, pitchDeg = 10f)
        xf.set(0f, 0f, 0f, yaw = -0.35f)
        Plush3D.model(p).draw(r, xf = xf, tint = if (silhouette) 0xFF0C0914.toInt() else -1)
        r.flat(0f, 0f, 0.15f, 16f, 12f, HallArt.shadow.full, blend = Blend.ALPHA, alpha = 0.5f)
    }

    fun decor(style: DecorStyle) {
        val (dw, dd, dh) = HubLayout.decorSize(style)
        val height = if (style == DecorStyle.DISCO_BALL) 22f else dh
        val (model, lights) = decor.getOrPut(style) {
            val ls = ArrayList<PointLight>()
            Props.build(Prop(PropKind.DECOR, -dw / 2f, -dd / 2f, dw / 2f, dd / 2f, height, decor = style), ls) to ls
        }
        val top = if (style == DecorStyle.DISCO_BALL) height + 8f else height
        val radius = maxOf(dw, dd, top) * 0.62f
        frame(0f, top / 2f, 0f, radius)
        r.lighting.points += lights
        model.draw(r)
    }
}

/** GPU-rendered menu pictures, cached by key. */
object Thumbs {
    /** Kept pictures, least recently used first, within a memory budget. */
    private val cache = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private var bytes = 0L
    private const val BUDGET = 40L shl 20

    private fun keep(key: String, image: ImageBitmap) {
        cache.put(key, image)?.let { bytes -= it.width * it.height * 4L }
        bytes += image.width * image.height * 4L
        val it = cache.entries.iterator()
        while (bytes > BUDGET && it.hasNext()) {
            val e = it.next()
            if (e.key == key) continue
            bytes -= e.value.width * e.value.height * 4L
            it.remove()
        }
    }

    private val waiting = HashMap<String, MutableList<(ImageBitmap) -> Unit>>()
    private val r = Renderer3D(1, 1)

    fun get(key: String): ImageBitmap? = cache[key]

    /** Photographs [scene] at [w] × [h] pixels unless [key] is cached or already on its way. */
    fun request(key: String, w: Int, h: Int, scene: Studio.() -> Unit, onReady: (ImageBitmap) -> Unit) {
        cache[key]?.let { onReady(it); return }
        waiting[key]?.let { it += onReady; return }
        waiting[key] = mutableListOf(onReady)
        r.startFrame()
        r.resize(w, h)
        Studio(r, w, h).scene()
        Gfx.snapshot(r.finishFrame(0, 0, w, h)) { bitmap ->
            val image = bitmap.asImageBitmap()
            keep(key, image)
            waiting.remove(key)?.forEach { it(image) }
        }
    }
}

/**
 * Shows the studio picture [key], photographed with [scene] at this composable's own size (at
 * most [maxPx] across) the first time it's needed. While a new picture develops the old one stays.
 */
@Composable
fun Thumb(key: String, modifier: Modifier = Modifier, maxPx: Int = 720, scene: Studio.() -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val s = minOf(1f, maxPx.toFloat() / maxOf(size.width, size.height))
        val w = (size.width * s).toInt().coerceAtLeast(8)
        val h = (size.height * s).toInt().coerceAtLeast(8)
        Thumbs.request("$key@${w}x$h", w, h, scene) { image = it }
    }
    Canvas(modifier.onSizeChanged { size = it }) {
        val img = image ?: return@Canvas
        drawImage(
            img,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
