package com.pocketarcade.hub

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.ui.UiColors
import com.pocketarcade.ui.UiIcon
import com.pocketarcade.ui.drawTicket
import com.pocketarcade.ui.drawToken
import com.pocketarcade.ui.drawUiIcon
import com.pocketarcade.ui.glowRoundRect
import com.pocketarcade.ui.lift
import com.pocketarcade.ui.shade
import kotlin.math.sin

/** The small picture in a prompt bubble's badge. */
internal enum class PromptGlyph(val icon: UiIcon?) {
    STAR(UiIcon.STAR), TROPHY(UiIcon.TROPHY), CAMERA(UiIcon.CAMERA), PLAY(UiIcon.PLAY),
    SOUND(UiIcon.SOUND), SPARKLE(UiIcon.SPARKLE),

    /** Drawn as the token itself rather than an icon. */
    TOKEN(null),
}

/** The currency drawn in front of a prompt's info line, if any. */
internal enum class PromptCost { NONE, TOKEN, TICKET }

/**
 * The prompt bubble that floats over the machine (or prop) you stand at: a glass card edged and
 * glowing in the place's accent, with a badge and the name on top, a solid accent PLAY pill you
 * tap in the middle and a cost or status line, with the currency's icon, underneath. It pulses
 * gently (the glow breathes and the pill brightens) so it reads as tappable.
 *
 * It is measured first ([measure]) so the caller can keep it on screen, then drawn ([draw]) at
 * the place it settled. State lives here (the hall draws on one thread) so a frame allocates
 * nothing.
 */
internal object PromptBubble {
    /** The grid unit in dp: everything in the bubble is a multiple of it. */
    private const val UNIT_DP = 2.3f

    /** Text sizes as multiples of the unit: title and info are tiny caps, the action is big caps. */
    private const val SMALL_TEXT = 1.05f
    private const val ACTION_TEXT = 1.3f

    /** The card's body, top to bottom; a little lighter while it is pressed. */
    private val BODY_TOP = Color(0xF0281E48)
    private val BODY_TOP_PRESSED = Color(0xF03E2E68)
    private val BODY_BOTTOM = Color(0xF0120C22)

    /** How bright the accent's glow round the card is at rest, and how far it swings with the pulse. */
    private const val GLOW_REST = 0.3f
    private const val GLOW_SWING = 0.12f

    /** The pulse's speed in radians per second (glow), and the pill's (faster, so it flickers a little). */
    private const val GLOW_RATE = 3f
    private const val PILL_RATE = 6f

    /** Accents brighter than this (0..1 luminance) get dark ink on the PLAY pill; the rest get white. */
    private const val BRIGHT_ACCENT = 0.58f

    /** Dark ink for text on a bright accent. */
    private const val INK = 0xFF1B1030.toInt()

    /**
     * Whether text on [accent] (ARGB) should be dark: bright neons (gold, cyan, lime) wash out
     * white text, so their pill wears the dark ink instead.
     */
    fun darkInkOn(accent: Int): Boolean {
        val r = (accent shr 16 and 0xFF) / 255f
        val g = (accent shr 8 and 0xFF) / 255f
        val b = (accent and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b > BRIGHT_ACCENT
    }

    // ---- The last measurement (pixels).
    /** The grid unit in pixels, the card's size and the pointer's height. */
    var u = 0f
        private set
    var width = 0f
        private set
    var height = 0f
        private set
    var tip = 0f
        private set
    private var pad = 0f
    private var badge = 0f
    private var pillH = 0f
    private var gap = 0f
    private var titleW = 0f
    private var infoW = 0f
    private var iconW = 0f

    /** Measures a bubble for these words at screen [density]; read the result from [width], [height], [tip] and [u]. */
    fun measure(title: String, action: String, info: String, cost: PromptCost, density: Float) {
        u = UNIT_DP * density
        val small = u * SMALL_TEXT
        val big = u * ACTION_TEXT
        pad = u * 4.5f
        gap = u * 3f
        val smallH = ArcadeFont.height(small, true)
        badge = smallH * 2.3f
        titleW = ArcadeFont.width(title, small, true)
        pillH = ArcadeFont.height(big) + u * 4.6f
        iconW = if (cost == PromptCost.NONE) 0f else smallH * 1.9f + u * 1.6f
        infoW = iconW + ArcadeFont.width(info, small, true)
        val head = badge + u * 2.4f + titleW
        val pill = ArcadeFont.width(action, big) + u * 10f
        width = maxOf(head, pill, infoW) + pad * 2f
        height = pad + badge + gap + pillH + gap + smallH + pad
        tip = u * 5f
    }

    /**
     * Draws the bubble measured last with its left edge at [left] and top at [top], its pointer
     * touching ([ax], [ay]). [t] is the hall's clock (the pulse), [pressed] whether a thumb is on it.
     */
    fun draw(
        scope: DrawScope, title: String, action: String, info: String, infoColor: Int, accent: Int,
        glyph: PromptGlyph, cost: PromptCost, left: Float, top: Float, ax: Float, ay: Float, t: Float, pressed: Boolean,
    ) = with(scope) {
        val small = u * SMALL_TEXT
        val big = u * ACTION_TEXT
        val accentC = Color(accent)
        val r = CornerRadius(u * 5f)
        val cx = left + width / 2f
        val glow = GLOW_REST + GLOW_SWING * sin(t * GLOW_RATE)
        glowRoundRect(accentC, Offset(left, top), Size(width, height), r.x, u * 4.5f, glow)
        drawRoundRect(Color.Black, Offset(left, top + u * 1.5f), Size(width, height), r, alpha = 0.35f)
        val pointer = Path().apply {
            moveTo(ax - tip, top + height - 1f)
            lineTo(ax + tip, top + height - 1f)
            lineTo(ax, ay)
            close()
        }
        drawPath(pointer, accentC)
        drawRoundRect(
            Brush.verticalGradient(listOf(if (pressed) BODY_TOP_PRESSED else BODY_TOP, BODY_BOTTOM), startY = top, endY = top + height),
            Offset(left, top), Size(width, height), r,
        )
        // A lit hairline inside the top edge, then the accent edge itself.
        drawRoundRect(
            Brush.verticalGradient(listOf(UiColors.bevelLight, Color.Transparent), startY = top, endY = top + height * 0.35f),
            Offset(left + u * 0.6f, top + u * 0.6f), Size(width - u * 1.2f, height - u * 1.2f), CornerRadius(r.x - u * 0.6f), style = Stroke(u * 0.3f),
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(accentC.lift(0.25f), accentC.shade(0.8f)), startY = top, endY = top + height),
            Offset(left, top), Size(width, height), r, style = Stroke(u * 0.9f),
        )

        // The badge and the name.
        val smallH = ArcadeFont.height(small, true)
        val headW = badge + u * 2.4f + titleW
        val headX = cx - headW / 2f
        val badgeC = Offset(headX + badge / 2f, top + pad + badge / 2f)
        drawCircle(Color.Black, badge / 2f, badgeC + Offset(0f, u * 0.5f), alpha = 0.35f)
        drawCircle(Brush.verticalGradient(listOf(accentC.lift(0.3f), accentC.shade(0.7f)), startY = badgeC.y - badge / 2f, endY = badgeC.y + badge / 2f), badge / 2f, badgeC)
        drawCircle(Color.White, badge / 2f, badgeC, alpha = 0.4f, style = Stroke(u * 0.3f))
        val ink = if (darkInkOn(accent)) Color(INK) else Color.White
        if (glyph == PromptGlyph.TOKEN) drawToken(badgeC, badge * 0.34f) else glyph.icon?.let { drawUiIcon(it, badgeC, badge * 0.66f, ink) }
        ArcadeFont.draw(this, title, headX + badge + u * 2.4f, badgeC.y - smallH / 2f, small, accentC.lift(0.2f), tiny = true)

        // The PLAY pill: solid accent, brightening a little with the pulse, sinking when pressed.
        val pillTop = top + pad + badge + gap
        val pillX = left + pad
        val pillW = width - pad * 2f
        val pulse = 0.5f + 0.5f * sin(t * PILL_RATE)
        val face = accentC.lift(0.05f + 0.07f * pulse)
        val sink = if (pressed) u * 0.6f else 0f
        val pr = CornerRadius(pillH / 2f)
        drawRoundRect(accentC.shade(0.5f), Offset(pillX, pillTop + u * 0.9f), Size(pillW, pillH), pr)
        drawRoundRect(
            Brush.verticalGradient(listOf(face.lift(0.28f), face, face.shade(0.78f)), startY = pillTop + sink, endY = pillTop + sink + pillH),
            Offset(pillX, pillTop + sink), Size(pillW, pillH), pr,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), startY = pillTop + sink, endY = pillTop + sink + pillH * 0.5f),
            Offset(pillX + u * 1.2f, pillTop + sink + u * 0.5f), Size(pillW - u * 2.4f, pillH * 0.42f), CornerRadius(pillH * 0.2f),
        )
        ArcadeFont.drawCentered(
            this, action, cx, pillTop + sink + (pillH - ArcadeFont.height(big)) / 2f, big, ink, shadow = ink == Color.White,
        )

        // The cost or status line, its currency in front.
        val infoY = pillTop + pillH + gap
        val infoAlpha = if (infoColor == Pal.RED) 0.5f + 0.5f * sin(t * 10f) else 1f
        var x = cx - infoW / 2f
        val iconC = Offset(x + smallH * 0.95f, infoY + smallH / 2f)
        when (cost) {
            PromptCost.TOKEN -> drawToken(iconC, smallH * 0.85f, 0f)
            PromptCost.TICKET -> drawTicket(iconC, smallH * 1.75f)
            PromptCost.NONE -> Unit
        }
        x += iconW
        ArcadeFont.draw(this, info, x, infoY, small, Color(infoColor), alpha = infoAlpha, tiny = true)
    }
}
