package com.pocketarcade.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.hub.CharacterLook
import com.pocketarcade.hub.Looks

/** The player's look for the current save: outfit colours plus the equipped hat. */
fun SaveState.playerLook(): CharacterLook {
    val outfit = Catalog.outfit(outfit)
    return Looks.player(outfit.shirt, outfit.pants, Catalog.hat(hat)?.hat)
}

/** The HUD's margin from the safe area, and the width of the gap between its round buttons. */
private val HUD_MARGIN = 10.dp
private val HUD_GAP = 8.dp

/** The pill's glow at rest, and how far the low-token warning swings it. */
private const val PILL_GLOW = 0.2f
private const val PILL_GLOW_WARN = 0.5f

/**
 * The currency pill, the collection and sound buttons and (given [onToggleView]) the camera
 * button switching the hall between overhead and first person. The pill is layered glass edged
 * in gold with a soft glow: the token twinkles, the counts roll like an odometer (with [onTick]
 * for a soft sound), and when tokens run low the glow turns to a warning pulse. On a narrow phone
 * the pill's contents shrink rather than push the buttons off screen. Given a [fx], the token
 * icon tells it where it is so a coin can fly out of it. It sits inside the safe area, so a
 * camera cutout never covers it.
 */
@Composable
fun Hud(
    save: SaveState,
    onProfile: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    firstPerson: Boolean = false,
    onToggleView: (() -> Unit)? = null,
    onTick: (() -> Unit)? = null,
    fx: CurrencyFx? = null,
) {
    val pill = RoundedCornerShape(50)
    val low = save.tokens <= LOW_TOKENS
    val pulse = rememberLowPulse(low)
    // Gold at rest; orange while low and red when out, breathing with the same pulse the counter uses.
    val glowColor = when {
        save.tokens == 0 -> UiColors.bad
        low -> UiColors.warn
        else -> UiColors.token
    }
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(HUD_MARGIN),
        // The pill hugs the left, the buttons the right; on a narrow phone the pill takes only
        // what the buttons and the gap leave it, and shrinks its contents to fit.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .uiGlow(glowColor, PILL_HEIGHT, 8.dp) { if (low) PILL_GLOW + (PILL_GLOW_WARN - PILL_GLOW) * pulse.value else PILL_GLOW }
                .shadow(10.dp, pill)
                .clip(pill)
                .background(Brush.verticalGradient(listOf(Color(0xE62C2152), Color(0xE6110B21))))
                .drawBehind { paintGlassBevel(size.height / 2f) }
                .border(UiEdge.line, Brush.verticalGradient(listOf(glowColor.copy(alpha = 0.7f), glowColor.copy(alpha = 0.16f))), pill)
                .padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp)
                // The counters are numbers beside icons: say what they are, once, with the real totals.
                .clearAndSetSemantics { contentDescription = "${save.tokens} tokens, ${save.tickets} tickets" },
        ) {
            CurrencyRow(
                save.tokens, save.tickets, Modifier.shrinkToFit(), unit = 2.6.dp, onTick = onTick,
                tokenIconModifier = Modifier.onGloballyPositioned {
                    fx?.tokenAnchor = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                },
            )
        }
        Spacer(Modifier.width(HUD_GAP))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleView != null) {
                RoundButton(if (firstPerson) UiIcon.EYE else UiIcon.CAMERA, onToggleView, Color(Pal.BLUE))
                Spacer(Modifier.width(HUD_GAP))
            }
            RoundButton(UiIcon.TROPHY, onProfile, Color(Pal.PURPLE))
            Spacer(Modifier.width(HUD_GAP))
            RoundButton(if (save.muted) UiIcon.MUTED else UiIcon.SOUND, onToggleMute, Color(Pal.TEAL))
        }
    }
}

/** The pill's approximate height (a token icon plus its padding), which its glow's corners are sized to. */
private val PILL_HEIGHT = 40.dp

/**
 * Fades the hall's interface in and out as [visible] changes instead of popping, keeping it
 * composed while it fades and swallowing touches once it is on its way out (the buttons must not
 * work under a dive into a machine). Fades in slower than out.
 */
@Composable
fun HudFade(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) 320 else 180, easing = FastOutSlowInEasing),
        label = "hudFade",
    )
    if (alpha > 0.001f) {
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
            content()
            if (!visible) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
                )
            }
        }
    }
}

/**
 * How much further down than [Hud]'s row the [HudExtras] row reaches: that row ends 76 dp below
 * the safe area (which is what [com.pocketarcade.hub.HubWorld.hudBottom] keeps the prompt under)
 * and this one ends at 124.
 */
val HudExtrasReach = 48.dp

/**
 * A second row of round buttons under [Hud]'s, at the right, for the hall's map and the
 * settings. The first row is already full on a narrow phone, so these get their own line.
 */
@Composable
fun HudExtras(onMap: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Hud's padding, its buttons (50 + their 4 lip) and a small gap.
            .padding(start = HUD_MARGIN, end = HUD_MARGIN, top = 70.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        RoundButton(UiIcon.MAP, onMap, Color(Pal.SKY))
        Spacer(Modifier.width(HUD_GAP))
        RoundButton(UiIcon.GEAR, onSettings, Color(Pal.ORANGE))
    }
}
