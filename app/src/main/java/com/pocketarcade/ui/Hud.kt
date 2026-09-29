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

/**
 * Token and ticket counters (rolling like an odometer when they change, with [onTick] for a soft
 * sound) plus collection and sound buttons, and (given [onToggleView]) the camera button switching
 * the hall between overhead and first person. Given a [fx], the token icon tells it where it is so
 * a coin can fly out of it.
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
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .shadow(10.dp, pill)
                .clip(pill)
                .background(Brush.verticalGradient(listOf(Color(0xE6261C44), Color(0xE6120C22))))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), pill)
                .padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp)
                // The counters are numbers beside icons: say what they are, once, with the real totals.
                .clearAndSetSemantics { contentDescription = "${save.tokens} tokens, ${save.tickets} tickets" },
        ) {
            CurrencyRow(
                save.tokens, save.tickets, unit = 2.6.dp, onTick = onTick,
                tokenIconModifier = Modifier.onGloballyPositioned {
                    fx?.tokenAnchor = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                },
            )
        }
        Spacer(Modifier.weight(1f))
        if (onToggleView != null) {
            RoundButton(if (firstPerson) UiIcon.EYE else UiIcon.CAMERA, onToggleView, Color(Pal.BLUE))
            Spacer(Modifier.width(8.dp))
        }
        RoundButton(UiIcon.TROPHY, onProfile, Color(Pal.PURPLE))
        Spacer(Modifier.width(8.dp))
        RoundButton(if (save.muted) UiIcon.MUTED else UiIcon.SOUND, onToggleMute, Color(Pal.TEAL))
    }
}

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
            .padding(start = 10.dp, end = 10.dp, top = 70.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        RoundButton(UiIcon.MAP, onMap, Color(Pal.SKY))
        Spacer(Modifier.width(8.dp))
        RoundButton(UiIcon.GEAR, onSettings, Color(Pal.ORANGE))
    }
}
