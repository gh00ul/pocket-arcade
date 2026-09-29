package com.pocketarcade.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal

/** The photo booth (a stand-in until the booth is built). */
@Composable
fun PhotoBoothScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    ArcadePanel("PHOTO BOOTH", Color(Pal.SKY), onClose) {
        Spacer(Modifier.height(24.dp))
        ArcadeText("COMING SOON", unit = 3.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(24.dp))
    }
}
