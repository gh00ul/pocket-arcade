package com.pocketarcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.easeOutCubic
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

// The menus' design system. Tokens (colours, spacing, radii, type, glow) live in UiTheme.kt, the
// vector icons and currency art in UiIcons.kt, chips / bars / toggles in UiParts.kt; this file
// holds the components every screen is assembled from: text, buttons, panels, glass boxes, the
// currency counter and the banner, plus the entrance helpers that animate them in.

fun Color.shade(f: Float): Color = Color(Pal.shade(toArgb(), f))

/** Mixes towards white by [f]. */
fun Color.lift(f: Float): Color = Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha)

/**
 * What a screen reader should say for arcade text: the symbols the font draws inline (play, star,
 * token...) named or dropped, line breaks as spaces and the capitals lowered, so TalkBack reads
 * words rather than spelling out shouted letters.
 */
fun spokenText(text: String): String {
    val out = StringBuilder(text.length + 8)
    for (ch in text) {
        when (ch) {
            // The play triangle only points at the word beside it, and a note says nothing.
            ArcadeFont.PLAY, ArcadeFont.NOTE -> out.append(' ')
            ArcadeFont.STAR -> out.append(" star ")
            ArcadeFont.HEART -> out.append(" heart ")
            ArcadeFont.TOKEN -> out.append(" token ")
            ArcadeFont.TICKET -> out.append(" tickets ")
            ArcadeFont.LEFT -> out.append(" left ")
            ArcadeFont.RIGHT -> out.append(" right ")
            ArcadeFont.UP -> out.append(" up ")
            ArcadeFont.DOWN -> out.append(" down ")
            '\n' -> out.append(' ')
            else -> out.append(ch)
        }
    }
    return out.toString().trim().replace(Regex(" {2,}"), " ").lowercase()
}

/** The scale that makes something [natural] pixels wide fit in [limit]: 1 when it already fits or there is no limit. */
internal fun shrinkScale(natural: Int, limit: Int): Float =
    if (limit == Constraints.Infinity || natural <= 0 || natural <= limit) 1f else limit.toFloat() / natural

/**
 * Lets this composable be as wide as it likes, then scales it down (never up) to fit the width it
 * was offered. A label that would run past its box shrinks instead: nothing in the menus may
 * overflow on a narrow phone. It reports the scaled size, so neighbours pack against what is seen.
 */
fun Modifier.shrinkToFit(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints())
    val s = shrinkScale(p.width, constraints.maxWidth)
    val w = constraints.constrainWidth((p.width * s).roundToInt())
    val h = constraints.constrainHeight((p.height * s).roundToInt())
    layout(w, h) {
        if (s >= 1f) {
            p.place(0, 0)
        } else {
            p.placeWithLayer(0, 0) {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** The width of [glyphs] set one by one with [tracking] units of extra space between them, at unit size [u]. */
private fun trackedWidth(glyphs: Array<String>, u: Float, tiny: Boolean, tracking: Float): Float {
    var w = 0f
    for (g in glyphs) w += ArcadeFont.width(g, u, tiny)
    return w + tracking * u * (glyphs.size - 1).coerceAtLeast(0)
}

/**
 * Text in the game's type, sized in grid units (capitals are 7 units tall, tiny ones 5), with a
 * soft shadow. Multi-line text splits on '\n'. [tracking] adds that many units of space between
 * letters (small capitals look better spaced). [fit] shrinks the text to the width it is offered
 * instead of letting it run past its box; [maxWidth] does the same to an explicit width.
 *
 * Prefer the [UiText] overload, which picks size, face and tracking together.
 */
@Composable
fun ArcadeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    unit: Dp = 3.dp,
    shadow: Boolean = true,
    tiny: Boolean = false,
    centered: Boolean = false,
    alpha: Float = 1f,
    /** If set, the text shrinks (never grows) to fit this width. */
    maxWidth: Dp = Dp.Unspecified,
    tracking: Float = 0f,
    fit: Boolean = false,
) {
    val density = LocalDensity.current
    val lines = remember(text) { text.split('\n') }
    // Tracked text is set glyph by glyph (kerning is traded for the spacing); plain text as runs.
    val glyphs = remember(text, tracking) {
        if (tracking > 0f) lines.map { l -> Array(l.length) { l[it].toString() } } else null
    }
    // The letters are painted, so give a screen reader the words.
    val spoken = remember(text) { spokenText(text) }
    fun lineWidth(i: Int, u: Float): Float =
        if (glyphs != null) trackedWidth(glyphs[i], u, tiny, tracking) else ArcadeFont.width(lines[i], u, tiny)

    var u = with(density) { unit.toPx() }
    if (maxWidth != Dp.Unspecified) {
        // Width is linear in the unit, shadow pad included, so one scale fits it exactly.
        var widest = 0f
        for (i in lines.indices) widest = maxOf(widest, lineWidth(i, u))
        val natural = widest + (if (shadow) u * 1.2f else 0f)
        val limit = with(density) { maxWidth.toPx() }
        if (natural > limit && natural > 0f) u *= limit / natural
    }
    val gap = u * 3.2f
    val lineH = ArcadeFont.height(u, tiny) + gap
    val pad = if (shadow) u * 1.2f else 0f
    val widths = FloatArray(lines.size) { lineWidth(it, u) }
    val w = (widths.maxOrNull() ?: 0f) + pad
    val h = lines.size * lineH - gap + pad
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }
    Canvas(
        modifier
            .then(if (fit) Modifier.shrinkToFit() else Modifier)
            .size(wDp, hDp)
            .semantics { contentDescription = spoken },
    ) {
        lines.forEachIndexed { i, line ->
            val x0 = if (centered) (w - pad - widths[i]) / 2f else 0f
            val y = i * lineH
            if (glyphs == null) {
                if (shadow) ArcadeFont.drawShadowed(this, line, x0, y, u, color, alpha = alpha, tiny = tiny)
                else ArcadeFont.draw(this, line, x0, y, u, color, alpha, tiny)
            } else {
                var x = x0
                for (g in glyphs[i]) {
                    if (shadow) ArcadeFont.drawShadowed(this, g, x, y, u, color, alpha = alpha, tiny = tiny)
                    else ArcadeFont.draw(this, g, x, y, u, color, alpha, tiny)
                    x += ArcadeFont.width(g, u, tiny) + tracking * u
                }
            }
        }
    }
}

/**
 * Text in one of the menus' [style]s (size, face and tracking together), shrinking to fit the
 * width it is offered unless [fit] is off.
 */
@Composable
fun ArcadeText(
    text: String,
    style: UiText,
    modifier: Modifier = Modifier,
    color: Color = UiColors.textHi,
    centered: Boolean = false,
    alpha: Float = 1f,
    shadow: Boolean = true,
    fit: Boolean = true,
) {
    ArcadeText(text, modifier, color, style.unit, shadow, style.tiny, centered, alpha, Dp.Unspecified, style.tracking, fit)
}

/**
 * Press feedback shared by every button: 0 at rest, 1 fully pressed. It springs down fast and
 * back up with a little overshoot (below 0: the cap pops up past rest), so a release feels like
 * a real button. With reduce motion it jumps between the two with no overshoot.
 */
@Composable
internal fun rememberPress(pressed: Boolean): State<Float> {
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (!UiMotion.enabled) {
            press.snapTo(if (pressed) 1f else 0f)
        } else if (pressed) {
            press.animateTo(1f, spring(dampingRatio = 1f, stiffness = PRESS_STIFFNESS))
        } else {
            press.animateTo(0f, spring(dampingRatio = RELEASE_DAMPING, stiffness = RELEASE_STIFFNESS))
        }
    }
    return press.asState()
}

/** How hard the button shrinks when fully pressed (a fraction of its size), and how much it brightens. */
internal const val PRESS_SHRINK = 0.06f
private const val PRESS_BRIGHTEN = 0.16f
private const val PRESS_STIFFNESS = 1600f
private const val RELEASE_STIFFNESS = 700f
private const val RELEASE_DAMPING = 0.42f

/** A resting button's glow (alpha at its edge) and how far it reaches; pressing dims it. */
private const val BUTTON_GLOW = 0.28f
private val BUTTON_GLOW_REACH = 9.dp

/**
 * A glossy arcade push-button: a candy-coloured cap on a darker skirt, lit by a soft glow of its
 * own colour. It sinks, shrinks a touch and brightens under your finger, then springs back up
 * past rest and settles. Disabled, it is a dull slate cap that doesn't glow. Its label shrinks
 * to fit if the button is narrower than the words.
 */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(Pal.PINK),
    textColor: Color = Color.White,
    enabled: Boolean = true,
    unit: Dp = 3.dp,
    tiny: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && enabled)
    val lip = 5.dp
    val spoken = remember(text) { spokenText(text) }
    Box(
        modifier
            // The touch area is the layout box; the spring below only moves what is drawn.
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .graphicsLayer {
                val s = 1f - PRESS_SHRINK * press.value
                scaleX = s
                scaleY = s
            }
            .drawBehind { buttonCap(color, enabled, lip.toPx(), press.value, round = false) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // The label rides the cap: up at rest, down when pressed.
                .graphicsLayer {
                    val p = press.value
                    translationY = (-0.5f + 0.8f * p) * lip.toPx()
                }
                // The button says its text once (above); its painted label stays out of the way.
                .clearAndSetSemantics {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ArcadeText(
                text, Modifier.shrinkToFit(), color = if (enabled) textColor else UiColors.textOff, unit = unit,
                shadow = enabled, tiny = tiny, centered = true, tracking = if (tiny) 0.3f else 0f,
            )
        }
    }
}

/** What a screen reader calls a round button, by its icon (icons not listed here go by their name). */
private fun iconLabel(icon: UiIcon): String = when (icon) {
    UiIcon.CLOSE -> "Close"
    UiIcon.TROPHY -> "Profile"
    UiIcon.SOUND -> "Sound on"
    UiIcon.MUTED -> "Sound off"
    UiIcon.EYE -> "Camera view, first person"
    UiIcon.CAMERA -> "Camera view, overhead"
    else -> icon.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
}

/**
 * A round glossy button carrying a vector [icon], ringed by a soft glow of its [color]. Screen
 * readers say [label] (by default what the icon means: "Close", "Profile", "Sound on"...) and
 * call it a button.
 */
@Composable
fun RoundButton(
    icon: UiIcon,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    label: String = iconLabel(icon),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    val lip = 4.dp
    Canvas(
        modifier
            .size(size, size + lip)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer {
                val sc = 1f - PRESS_SHRINK * press.value
                scaleX = sc
                scaleY = sc
            },
    ) {
        val l = lip.toPx()
        val p = press.value
        buttonCap(color, true, l, p, round = true)
        val faceY = pressOffset(l, p)
        val c = Offset(this.size.width / 2f, faceY + (this.size.height - l) / 2f)
        drawUiIcon(icon, c + Offset(0f, this.size.width * 0.03f), this.size.width * 0.5f, Color.Black.copy(alpha = 0.3f))
        drawUiIcon(icon, c, this.size.width * 0.5f, Color.White)
    }
}

/** How far the cap has sunk into the skirt at [press] (1 = fully down); the release overshoot lifts it a little above rest. */
internal fun pressOffset(lip: Float, press: Float): Float = if (press >= 0f) lip * 0.8f * press else lip * 0.5f * press

/**
 * The shared look of every button: a glow of its colour, a drop shadow, the skirt, a gradient cap
 * with a darker lower bevel, gloss and a lit rim, at press depth [press] (0 rest, 1 down). A
 * button that isn't [enabled] is slate, flat and dark.
 */
private fun DrawScope.buttonCap(color: Color, enabled: Boolean, lip: Float, press: Float, round: Boolean) {
    val w = size.width
    val h = size.height - lip
    val r = if (round) h / 2f else min(UiRadius.button.toPx(), h / 2f)
    val cr = CornerRadius(r, r)
    val faceY = pressOffset(lip, press)
    val pr = press.coerceIn(0f, 1f)
    // The cap brightens as it is pressed, as if the light behind it came up.
    val base = if (enabled && press > 0f) color.lift(PRESS_BRIGHTEN * pr) else color
    if (enabled) glowRoundRect(color, Offset(0f, faceY + lip * 0.4f), Size(w, h), r, BUTTON_GLOW_REACH.toPx(), BUTTON_GLOW * (1f - 0.6f * pr))
    drawRoundRect(Color.Black.copy(alpha = 0.38f), Offset(0f, lip + 2.dp.toPx()), Size(w, h), cr)
    drawRoundRect(if (enabled) base.shade(0.42f) else UiColors.offSkirt, Offset(0f, lip), Size(w, h), cr)
    val top = if (enabled) base.lift(0.3f) else UiColors.offCapTop
    val mid = if (enabled) base else UiColors.offCapTop.shade(0.9f)
    val bottom = if (enabled) base.shade(0.74f) else UiColors.offCapBottom
    drawRoundRect(Brush.verticalGradient(listOf(top, mid, bottom), startY = faceY, endY = faceY + h), Offset(0f, faceY), Size(w, h), cr)
    if (enabled) {
        // The cap's lower bevel: its bottom edge turns away from the light.
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Transparent, base.shade(0.5f).copy(alpha = 0.6f)), startY = faceY + h * 0.68f, endY = faceY + h),
            Offset(0f, faceY), Size(w, h), cr, style = Stroke(2.dp.toPx()),
        )
    }
    val inset = 3.dp.toPx()
    val gh = h * 0.46f
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (enabled) 0.42f else 0.1f), Color.White.copy(alpha = 0.03f)),
            startY = faceY + inset, endY = faceY + inset + gh,
        ),
        Offset(inset, faceY + inset * 0.7f), Size(w - inset * 2f, gh), CornerRadius(r * 0.85f, r * 0.85f),
    )
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (enabled) 0.55f else 0.14f), Color.White.copy(alpha = 0.05f)),
            startY = faceY, endY = faceY + h,
        ),
        Offset(0f, faceY), Size(w, h), cr, style = Stroke(1.dp.toPx()),
    )
}

/**
 * The body of a modal panel or card, painted over its gradient: a bloom of the [accent] from the
 * top, the scan texture, and a bright hairline just inside the top edge that fades down the sides
 * (light catching a bevel).
 */
internal fun DrawScope.paintSurface(accent: Color, corner: Float, bloom: Float = 0.2f) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(listOf(accent.copy(alpha = bloom), Color.Transparent), Offset(w * 0.28f, 0f), w * 0.95f),
    )
    drawRect(UiTexture.scan, alpha = UiTexture.PANEL_ALPHA)
    val inset = 1.5.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(UiColors.bevelLight, Color.White.copy(alpha = 0.02f)), startY = 0f, endY = h * 0.4f),
        Offset(inset, inset), Size(w - inset * 2f, h - inset * 2f), CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(1.dp.toPx()),
    )
}

/**
 * A full-screen modal: dims the hall, blocks touches behind it and shows a layered glass card: a
 * violet gradient with a bloom of the [accent] from the top, a fine scan texture, a lit inner
 * hairline, a glowing accent edge and outer glow, and a title row (an accent bar, the title, a
 * close button) over a fading rule. It arrives: the scrim fades in, the card rises and settles
 * with a small overshoot, and the title row lands just after it. Every [GlassBox] inside then
 * slides up in turn, in the order it is composed. With reduce motion it simply fades in.
 */
@Composable
fun ArcadePanel(
    title: String,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(UiRadius.panel)
    val progress = rememberEntrance(PANEL_ENTRANCE_MILLIS)
    val entrance = remember { PanelEntrance(progress) }
    CompositionLocalProvider(LocalEntrance provides entrance) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(UiColors.scrim, alpha = clamp01(progress.value * 3.2f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(UiSpace.md),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
                    .graphicsLayer {
                        val e = stageOf(progress.value, 0f, 0.55f)
                        alpha = clamp01(e * 2.4f)
                        if (UiMotion.enabled) {
                            val k = easeOutBack(e)
                            val sc = 0.95f + 0.05f * k
                            scaleX = sc
                            scaleY = sc
                            translationY = (1f - k) * 44.dp.toPx()
                        }
                    }
                    .shadow(24.dp, shape, ambientColor = accent, spotColor = accent)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(UiColors.panelTop, UiColors.panelBottom)))
                    .drawBehind { paintSurface(accent, UiRadius.panel.toPx()) }
                    .border(UiEdge.strong, Brush.verticalGradient(listOf(accent, accent.shade(0.45f))), shape)
                    .padding(UiSpace.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().enterStage(progress, 0.1f, 0.5f, rise = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // An accent bar leads the title, like the tab of a file folder.
                    Box(
                        Modifier
                            .size(5.dp, 26.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(accent.lift(0.35f), accent.shade(0.7f)))),
                    )
                    Spacer(Modifier.width(UiSpace.sm + 2.dp))
                    ArcadeText(title, UiText.TITLE, Modifier.weight(1f), color = accent.lift(0.2f))
                    Spacer(Modifier.width(UiSpace.sm))
                    RoundButton(UiIcon.CLOSE, onClose, Color(Pal.RED), size = 48.dp, label = "Close")
                }
                ArcadeDivider(accent, Modifier.padding(top = UiSpace.sm, bottom = UiSpace.md))
                content()
            }
        }
    }
}

/** How long a panel takes to arrive, all its staggered pieces included. */
private const val PANEL_ENTRANCE_MILLIS = 620

/**
 * A frosted inset box inside a panel: glass lit from the top with a hairline bevel. Given a
 * [highlight] it takes that colour (tinted glass, a solid edge and a soft outer glow), for the
 * one box on a screen that matters most. Inside an [ArcadePanel] it arrives with it: each box
 * composed while the panel is still coming in slides up a beat after the one before it.
 */
@Composable
fun GlassBox(modifier: Modifier = Modifier, highlight: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(UiRadius.box)
    val entrance = LocalEntrance.current
    val index = remember { entrance?.claim() ?: 0 }
    Column(
        modifier
            .then(if (entrance != null) Modifier.enterStage(entrance.progress, entrance.slotStart(index), entrance.slotStart(index) + 0.3f, rise = 14.dp) else Modifier)
            .then(if (highlight != null) Modifier.uiGlow(highlight, UiRadius.box, 8.dp, 0.26f) else Modifier)
            .clip(shape)
            .background(
                if (highlight != null) Brush.verticalGradient(listOf(highlight.copy(alpha = 0.22f), highlight.copy(alpha = 0.08f)))
                else Brush.verticalGradient(listOf(UiColors.glassHi, UiColors.glassLo)),
            )
            .drawBehind { paintGlassBevel(UiRadius.box.toPx()) }
            .border(if (highlight != null) UiEdge.strong else UiEdge.hair, highlight ?: UiColors.glassEdge, shape)
            .padding(UiSpace.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A lit hairline just inside the top edge of a glass surface, fading toward its sides. */
internal fun DrawScope.paintGlassBevel(corner: Float) {
    val inset = 1.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), startY = 0f, endY = size.height * 0.3f),
        Offset(inset, inset), Size(size.width - inset * 2f, size.height - inset * 2f), CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(1.dp.toPx()),
    )
}

/** At this many tokens or fewer the token counter pulses, nudging the player toward the token machine. */
const val LOW_TOKENS = 2

/** The 0..1 breathing of the low-token pulse: idle (a constant 0, and no frames run) unless [active]. */
@Composable
internal fun rememberLowPulse(active: Boolean): State<Float> {
    if (!active || !UiMotion.enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "lowTokens").animateFloat(
        0f, 1f, infiniteRepeatable(tween(820, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lowTokensPulse",
    )
}

/**
 * Token and ticket counters shown side by side, a hairline between them. The numbers roll like
 * an odometer whenever they change ([RollingNumber]), calling [onTick] as they pass values, and
 * the token count breathes while it is at [LOW_TOKENS] or under and turns red at zero.
 * [tokenIconModifier] and [ticketIconModifier] go on the icons, for a screen that needs to know
 * where they are (a coin flying into a counter).
 */
@Composable
fun CurrencyRow(
    tokens: Int,
    tickets: Int,
    modifier: Modifier = Modifier,
    unit: Dp = 3.dp,
    onTick: (() -> Unit)? = null,
    tokenIconModifier: Modifier = Modifier,
    ticketIconModifier: Modifier = Modifier,
) {
    val pulse = rememberLowPulse(tokens <= LOW_TOKENS)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.graphicsLayer {
                val s = 1f + 0.1f * pulse.value
                scaleX = s
                scaleY = s
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenIcon(unit * 9f, tokenIconModifier, twinkle = true)
            Spacer(Modifier.width(6.dp))
            RollingNumber(tokens, if (tokens == 0) UiColors.bad else UiColors.token, unit, onTick = onTick)
        }
        Spacer(Modifier.width(UiSpace.md))
        Box(Modifier.width(UiEdge.hair).height(unit * 6f).background(UiColors.glassEdge))
        Spacer(Modifier.width(UiSpace.md))
        TicketIcon(unit * 8f, ticketIconModifier)
        Spacer(Modifier.width(6.dp))
        RollingNumber(tickets, UiColors.ticket, unit, onTick = onTick)
    }
}

/**
 * The short notice at the top of the screen ("DAILY BONUS", "COMING SOON"): [text] null hides it.
 * It drops in with a little spring, and fades away rather than vanishing, keeping the words it
 * last showed while it goes. A new text while it is up gives it a small pop. Reduce motion cuts
 * straight between shown and hidden.
 */
@Composable
fun ArcadeBanner(text: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(text ?: "") }
    if (text != null) last = text
    val shown = remember { Animatable(if (text != null) 1f else 0f) }
    LaunchedEffect(text) {
        if (!UiMotion.enabled) {
            shown.snapTo(if (text != null) 1f else 0f)
        } else if (text != null) {
            // Dropping in from above, or a quick re-pop if it was already up with other words.
            if (shown.value > 0.5f) shown.snapTo(0.82f)
            shown.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        } else {
            shown.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    if (text != null || shown.value > 0.002f) {
        Box(
            modifier.graphicsLayer {
                val p = shown.value
                alpha = p.coerceIn(0f, 1f)
                translationY = -(1f - p) * 36.dp.toPx()
                val sc = 0.94f + 0.06f * p
                scaleX = sc
                scaleY = sc
            },
        ) {
            GlassBox(Modifier.background(Color(0xE6120C22), RoundedCornerShape(UiRadius.box)), highlight = Color(Pal.YELLOW)) {
                ArcadeText(last, UiText.HEADING, color = Color(Pal.YELLOW), centered = true)
            }
        }
    }
}

// ---------------------------------------------------------------- entrances

/** How far through the slot [from]..[to] a progress [p] is, 0..1: one step of a staggered sequence. */
fun stageOf(p: Float, from: Float, to: Float): Float = clamp01((p - from) / (to - from).coerceAtLeast(1e-4f))

/**
 * A 0..1 progress that runs once, linearly over [millis], when first composed (at once with reduce
 * motion). Give it to [enterStage] for each piece of a card that should arrive in turn.
 */
@Composable
fun rememberEntrance(millis: Int = 700): State<Float> {
    val progress = remember { Animatable(if (UiMotion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (UiMotion.enabled) progress.animateTo(1f, tween(millis, easing = LinearEasing)) else progress.snapTo(1f)
    }
    return progress.asState()
}

/**
 * Brings a piece of a card in during [from]..[to] of an [entrance]: it fades in and rises [rise]
 * into place, or with [pop] scales up from 70% with a little overshoot. Reduce motion only fades.
 * Reads the progress while drawing, so it costs no recomposition.
 */
fun Modifier.enterStage(entrance: State<Float>, from: Float, to: Float, rise: Dp = 16.dp, pop: Boolean = false): Modifier =
    graphicsLayer {
        val e = stageOf(entrance.value, from, to)
        alpha = clamp01(e * 2.2f)
        if (UiMotion.enabled) {
            val k = if (pop) easeOutBack(e) else easeOutCubic(e)
            translationY = (1f - k) * rise.toPx()
            if (pop) {
                val s = 0.7f + 0.3f * k
                scaleX = s
                scaleY = s
            }
        }
    }

/**
 * The entrance of the panel a composable sits in: its [progress] and a counter that hands the
 * pieces inside their place in the queue ([claim]), so they arrive one after another.
 */
class PanelEntrance(val progress: State<Float>) {
    private var next = 0

    /** The next free place in the queue (0 for the first piece). */
    fun claim(): Int = next++

    /** When (0..1 of the entrance) the piece at [index] starts arriving: a step behind the one before, never later than 0.7. */
    fun slotStart(index: Int): Float = (0.2f + 0.06f * index).coerceAtMost(0.7f)
}

/** The entrance of the panel this is inside, if any; [GlassBox] and [staggerIn] join it. */
val LocalEntrance = compositionLocalOf<PanelEntrance?> { null }

/**
 * Brings a piece of a panel in after the pieces before it: item [index] of a list starts
 * [index] steps after the first, sliding up and fading in. Does nothing outside a panel that
 * provides [LocalEntrance] ([ArcadePanel] does).
 */
@Composable
fun Modifier.staggerIn(index: Int, rise: Dp = 14.dp): Modifier {
    val entrance = LocalEntrance.current ?: return this
    val start = entrance.slotStart(index)
    return enterStage(entrance.progress, start, start + 0.3f, rise)
}

/**
 * Pops the composable in after [delayMillis]: fades up from 70% scale with a springy overshoot.
 * The animation restarts whenever [key] changes (give it `visible` to pop things in as they
 * become available). Reduce motion shows it at once.
 */
fun Modifier.popIn(delayMillis: Int = 0, key: Any? = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(if (UiMotion.enabled) 0f else 1f) }
    LaunchedEffect(key) {
        if (UiMotion.enabled) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        } else {
            progress.snapTo(1f)
        }
    }
    graphicsLayer {
        val v = progress.value
        alpha = clamp01(v * 1.8f)
        if (UiMotion.enabled) {
            val s = 0.7f + 0.3f * v
            scaleX = s
            scaleY = s
            translationY = (1f - v.coerceAtMost(1f)) * 18.dp.toPx()
        }
    }
}

/** Takes up its space in the layout but draws and takes touches only while [show]: buttons that arrive later must not move what is above them. */
fun Modifier.reserveSpace(show: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (show) placeable.place(0, 0) }
}
