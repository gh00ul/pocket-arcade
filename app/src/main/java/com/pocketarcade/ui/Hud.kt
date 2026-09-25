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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.hub.CharacterArt
import com.pocketarcade.hub.CharacterLook

/** The player's look for the current save: outfit colours plus the equipped hat. */
fun SaveState.playerLook(): CharacterLook {
    val outfit = Catalog.outfit(outfit)
    return CharacterArt.player(outfit.shirt, outfit.pants, Catalog.hat(hat)?.hat)
}

/** Token and ticket counters (ticking up when they change) plus collection and sound buttons. */
@Composable
fun Hud(save: SaveState, onProfile: () -> Unit, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    val tokens by animateIntAsState(save.tokens, tween(500), label = "tokens")
    val tickets by animateIntAsState(save.tickets, tween(900), label = "tickets")
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(Color(Pal.NIGHT).copy(alpha = 0.88f))
                .border(2.dp, Color(Pal.PURPLE))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            CurrencyRow(tokens, tickets)
        }
        Spacer(Modifier.weight(1f))
        ArcadeButton("${PixelFont.STAR}", onProfile, color = Color(Pal.PURPLE))
        Spacer(Modifier.width(8.dp))
        ArcadeButton(if (save.muted) "×" else "${PixelFont.NOTE}", onToggleMute, color = Color(Pal.TEAL))
    }
}
