package com.pocketarcade.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.Pal
import kotlin.random.Random

/**
 * The menus' colour tokens. Every screen picks from here instead of inventing a hex, so the
 * whole product shares one night-arcade look: violet-black surfaces, frosted glass, one accent
 * per screen and warm gold for currency.
 */
object UiColors {
    // ---- Surfaces. The first five predate the design system and are used by the game host's cards.
    val cardTop = Color(0xFF221A3C)
    val cardBottom = Color(0xFF120C22)
    val glass = Color(0x16FFFFFF)
    val glassEdge = Color(0x24FFFFFF)
    val scrim = Color(0xCC07050E)

    /** A modal panel's body, top to bottom: a touch lighter than a card, sinking into near-black. */
    val panelTop = Color(0xFF281F4A)
    val panelBottom = Color(0xFF0F0A1E)

    /** Frosted glass inside a panel, brightest at its top edge. */
    val glassHi = Color(0x22FFFFFF)
    val glassLo = Color(0x08FFFFFF)

    /** The bright hairline that runs just inside a surface's top edge, like light catching a bevel. */
    val bevelLight = Color(0x4DFFFFFF)

    /** A sunken well (progress tracks, the turntable stage, empty slots). */
    val well = Color(0x59000000)
    val wellEdge = Color(0x1FFFFFFF)

    /** A deep backdrop for stages that show a 3D picture. */
    val stage = Color(0xFF120C22)

    // ---- Text, brightest to dimmest.
    val textHi = Color.White
    val textMid = Color(Pal.LAVENDER)
    val textLow = Color(0xFF9790B6)
    val textOff = Color(0xFF6F698A)

    // ---- Currency and status.
    val token = Color(0xFFFFD35A)
    val ticket = Color(0xFFFFA24A)
    val good = Color(Pal.LIME)
    val info = Color(Pal.CYAN)
    val warn = Color(Pal.ORANGE)
    val bad = Color(0xFFFF7A66)
    val gold = Color(Pal.GOLD)

    // ---- Buttons that cannot be pressed: a dull slate cap on a darker skirt.
    val offCapTop = Color(0xFF4C4568)
    val offCapBottom = Color(0xFF302A47)
    val offSkirt = Color(0xFF211C34)
}

/** Spacing scale: pick one of these instead of a fresh number. */
object UiSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Corner radii: panels hold boxes, boxes hold cards, cards hold chips. */
object UiRadius {
    val panel = 24.dp
    val box = 16.dp
    val card = 14.dp
    val button = 16.dp
    val chip = 8.dp
}

/** Edge widths: a hairline for quiet edges, a line for cards, a strong edge for what is chosen. */
object UiEdge {
    val hair = 1.dp
    val line = 1.5.dp
    val strong = 2.dp
}

/**
 * The type scale. Text is set in the game's own face, sized in grid units ([ArcadeText]), so the
 * hierarchy is size, weight (the display face for big words, the condensed face for small ones)
 * and tracking (extra space between letters, in units: small capitals read better spaced out).
 */
enum class UiText(val unit: Dp, val tiny: Boolean, val tracking: Float) {
    /** Hero numbers and one-word banners. */
    DISPLAY(5.dp, false, 0f),

    /** A panel's title. */
    TITLE(3.3.dp, false, 0.2f),

    /** A section or box heading, an item's name. */
    HEADING(2.6.dp, false, 0.1f),

    /** Running words and values. */
    BODY(2.1.dp, false, 0f),

    /** A label above or beside a value; condensed and spaced. */
    LABEL(2.1.dp, true, 0.5f),

    /** The smallest legible note: hints, counts, tags. */
    CAPTION(1.75.dp, true, 0.45f),
}

/** One glow language for every screen: soft rings of the accent colour round a surface. */
object UiGlow {
    /** How far a glow reaches beyond its surface. */
    val REACH = 10.dp

    /** How bright a resting glow is (0..1 alpha at the surface's edge), and an active or chosen one. */
    const val IDLE = 0.3f
    const val ACTIVE = 0.55f
}

/** A glow is this many stroked rings, brightest at the surface and fading quadratically outward. */
private const val GLOW_RINGS = 5

/**
 * Paints a soft glow of [color] round the rounded rectangle at [topLeft] of [size]: [GLOW_RINGS]
 * strokes stepping out to [reach] pixels, faint at the far end. It is cheap (five strokes, no
 * blur, no allocation) and stays outside the shape, so translucent glass above it isn't tinted.
 * [strength] is the alpha at the edge.
 */
fun DrawScope.glowRoundRect(color: Color, topLeft: Offset, size: Size, corner: Float, reach: Float, strength: Float) {
    if (strength <= 0.003f || reach <= 0.5f) return
    val step = reach / GLOW_RINGS
    for (i in 0 until GLOW_RINGS) {
        val t = i / GLOW_RINGS.toFloat()
        val grow = step * (i + 0.5f)
        drawRoundRect(
            color.copy(alpha = strength * (1f - t) * (1f - t)),
            Offset(topLeft.x - grow, topLeft.y - grow),
            Size(size.width + grow * 2f, size.height + grow * 2f),
            CornerRadius(corner + grow),
            style = Stroke(step + 0.75f),
        )
    }
}

/** The same glow round a circle of [radius] at [center]. */
fun DrawScope.glowCircle(color: Color, center: Offset, radius: Float, reach: Float, strength: Float) {
    if (strength <= 0.003f || reach <= 0.5f) return
    val step = reach / GLOW_RINGS
    for (i in 0 until GLOW_RINGS) {
        val t = i / GLOW_RINGS.toFloat()
        drawCircle(color.copy(alpha = strength * (1f - t) * (1f - t)), radius + step * (i + 0.5f), center, style = Stroke(step + 0.75f))
    }
}

/** A glow of [color] round this composable's rounded-rectangle bounds, [strength] read while drawing (so it can pulse without recomposing). */
fun Modifier.uiGlow(color: Color, corner: Dp, reach: Dp = UiGlow.REACH, strength: () -> Float): Modifier = drawBehind {
    glowRoundRect(color, Offset.Zero, size, corner.toPx(), reach.toPx(), strength())
}

/** A steady glow of [color] round this composable's rounded-rectangle bounds. */
fun Modifier.uiGlow(color: Color, corner: Dp, reach: Dp = UiGlow.REACH, strength: Float = UiGlow.IDLE): Modifier = drawBehind {
    glowRoundRect(color, Offset.Zero, size, corner.toPx(), reach.toPx(), strength)
}

/**
 * The surface texture: faint scanlines and film-grain specks in one small tile, painted the
 * first time a panel needs it and then only tiled. Laid over a panel's gradient it keeps big
 * dark areas from looking like flat plastic.
 */
internal object UiTexture {
    /** The tile's side in pixels, its scanline period and the grain's seed. */
    private const val TILE = 64
    private const val SCAN_PERIOD = 4
    private const val SEED = 0x5EED

    /** How strongly a panel shows the texture. */
    const val PANEL_ALPHA = 0.9f

    /** The tiled texture as a brush; drawn with [DrawScope.drawRect]. */
    val scan: Brush by lazy {
        val px = IntArray(TILE * TILE)
        val rnd = Random(SEED)
        for (y in 0 until TILE) {
            if (y % SCAN_PERIOD == 0) for (x in 0 until TILE) px[y * TILE + x] = 0x0EFFFFFF
        }
        // Light and dark specks, unevenly strong.
        repeat(110) { px[rnd.nextInt(TILE * TILE)] = ((6 + rnd.nextInt(18)) shl 24) or 0xFFFFFF }
        repeat(70) { px[rnd.nextInt(TILE * TILE)] = ((16 + rnd.nextInt(30)) shl 24) }
        val bmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, TILE, 0, 0, TILE, TILE)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}
