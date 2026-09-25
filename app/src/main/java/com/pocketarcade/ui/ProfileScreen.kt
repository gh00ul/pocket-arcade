package com.pocketarcade.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.games.MiniGame

/** High scores for every machine and the plush collection. */
@Composable
fun ProfileScreen(save: SaveState, games: List<MiniGame>, onClose: () -> Unit) {
    ArcadePanel("MY ARCADE", Color(Pal.PURPLE), onClose, fillHeight = true) {
        CurrencyRow(save.tokens, save.tickets)
        Spacer(Modifier.height(12.dp))
        GlassBox(Modifier.fillMaxWidth()) {
            ArcadeText("HIGH SCORES", unit = 2.8.dp, color = Color(Pal.YELLOW))
            Spacer(Modifier.height(10.dp))
            for (g in games) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArcadeText(g.title, unit = 2.dp, color = Color(g.look.glow))
                    Spacer(Modifier.weight(1f))
                    ArcadeText(save.highScore(g.id).toString(), unit = 2.dp, color = Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
            ArcadeText("GAMES PLAYED: ${save.totalPlays}", unit = 2.4.dp, tiny = true, color = Color(Pal.LAVENDER))
        }
        Spacer(Modifier.height(12.dp))
        val found = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
        ArcadeText("PLUSHIES $found/${Catalog.plushies.size}", unit = 2.8.dp, color = Color(Pal.PINK))
        Spacer(Modifier.height(8.dp))
        PlushCollectionGrid(save, Modifier.weight(1f))
    }
}
