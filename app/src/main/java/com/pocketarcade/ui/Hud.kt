package com.pocketarcade.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Token and ticket counters (ticking up when they change) plus collection and sound buttons. */
@Composable
fun Hud(save: SaveState, onProfile: () -> Unit, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    val tokens by animateIntAsState(save.tokens, tween(500), label = "tokens")
    val tickets by animateIntAsState(save.tickets, tween(900), label = "tickets")
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
                .padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        ) {
            CurrencyRow(tokens, tickets, unit = 2.6.dp)
        }
        Spacer(Modifier.weight(1f))
        RoundButton(UiIcon.TROPHY, onProfile, Color(Pal.PURPLE))
        Spacer(Modifier.width(8.dp))
        RoundButton(if (save.muted) UiIcon.MUTED else UiIcon.SOUND, onToggleMute, Color(Pal.TEAL))
    }
}
