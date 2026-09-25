package com.pocketarcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.ItemKind
import com.pocketarcade.data.SaveState
import com.pocketarcade.data.ShopItem
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import com.pocketarcade.hub.CharacterLook
import com.pocketarcade.hub.Looks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TABS = listOf("HATS", "STYLE", "DECOR", "PLUSH")

/** How many angles the turning preview figure is photographed from. */
private const val TURN_STEPS = 12

/** Trade tickets for hats, outfit colours and hall decorations; browse won plushies. */
@Composable
fun PrizeCounterScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val items: List<ShopItem> = when (tab) {
        0 -> Catalog.hats
        1 -> Catalog.outfits
        2 -> Catalog.decor
        else -> emptyList()
    }
    val selected = selectedId?.let { Catalog.byId(it) }?.takeIf { it in items }

    ArcadePanel("PRIZE COUNTER", Color(Pal.PINK), onClose, fillHeight = true) {
        CurrencyRow(save.tokens, save.tickets)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, label ->
                ArcadeButton(
                    label, { tab = i; selectedId = null; message = ""; services.audio.play(Sfx.BLIP, 0.6f) },
                    Modifier.weight(1f),
                    color = if (tab == i) Color(Pal.PINK) else Color(0xFF4A3470),
                    unit = 1.9.dp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 3) {
            PlushGrid(save, Modifier.weight(1f))
        } else {
            if (tab != 2) {
                PreviewCharacter(save, selected)
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCell(
                        item = item,
                        save = save,
                        owned = save.owns(item.id),
                        worn = item.id == save.hat || item.id == save.outfit,
                        affordable = save.tickets >= item.price,
                        selected = item.id == selected?.id,
                        onClick = {
                            selectedId = item.id
                            message = ""
                            services.audio.play(Sfx.BLIP, 0.6f, 1.2f)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ActionBar(save, selected, message) { item ->
                scope.launch {
                    val owned = save.owns(item.id)
                    if (!owned) {
                        if (services.repo.buy(item)) {
                            services.audio.play(Sfx.WIN)
                            services.audio.play(Sfx.CHEER, 0.5f)
                            services.haptics.win()
                            message = if (item.kind == ItemKind.DECOR) "IT'S IN THE HALL NOW!" else "NEW ITEM! LOOKING GOOD!"
                        } else {
                            services.audio.play(Sfx.ERROR)
                            message = "NEED ${item.price - save.tickets} MORE TICKETS"
                        }
                    } else if (item.kind != ItemKind.DECOR) {
                        services.repo.equip(item)
                        services.audio.play(Sfx.SELECT)
                        services.haptics.tick()
                        message = ""
                    }
                }
            }
        }
    }
}

/** How [item] looks on the player (or on its own, for decorations). */
private fun lookFor(save: SaveState, item: ShopItem?): CharacterLook {
    val outfit = if (item?.kind == ItemKind.OUTFIT) item else Catalog.outfit(save.outfit)
    val hat = if (item?.kind == ItemKind.HAT) item.hat else Catalog.hat(save.hat)?.hat
    return Looks.player(outfit.shirt, outfit.pants, hat)
}

@Composable
private fun FigureThumb(look: CharacterLook, step: Int, modifier: Modifier) {
    val yaw = step * (2f * Math.PI.toFloat() / TURN_STEPS)
    Thumb("fig:$look:$step", modifier) { figure(look, yaw) }
}

/** The player on a turntable, wearing whatever is selected. */
@Composable
private fun PreviewCharacter(save: SaveState, selected: ShopItem?) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(140)
            step = (step + 1) % TURN_STEPS
        }
    }
    val look = lookFor(save, selected)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .border(1.dp, UiColors.glassEdge, shape),
        contentAlignment = Alignment.Center,
    ) {
        FigureThumb(look, step, Modifier.fillMaxWidth().height(150.dp))
    }
}

@Composable
private fun ItemCell(
    item: ShopItem,
    save: SaveState,
    owned: Boolean,
    worn: Boolean,
    affordable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        selected -> Color(Pal.YELLOW)
        worn -> Color(Pal.LIME)
        else -> UiColors.glassEdge
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0x33FFD84D) else UiColors.glass)
            .border(if (selected || worn) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pic = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
        when (item.kind) {
            ItemKind.DECOR -> Thumb("decor:${item.decor}", pic) { decor(item.decor!!) }
            else -> FigureThumb(lookFor(save, item), 0, pic)
        }
        Spacer(Modifier.height(5.dp))
        ArcadeText(item.name, unit = 1.9.dp, tiny = true, centered = true, color = Color.White)
        Spacer(Modifier.height(4.dp))
        val (label, color) = when {
            worn -> "WORN" to Pal.LIME
            owned && item.kind == ItemKind.DECOR -> "PLACED" to Pal.LIME
            owned -> "OWNED" to Pal.CYAN
            item.price == 0 -> "FREE" to Pal.CYAN
            else -> "${ArcadeFont.TICKET}${item.price}" to (if (affordable) Pal.GOLD else Pal.GRAY)
        }
        ArcadeText(label, unit = 2.dp, tiny = true, color = Color(color))
    }
}

@Composable
private fun ActionBar(save: SaveState, selected: ShopItem?, message: String, onAction: (ShopItem) -> Unit) {
    GlassBox(Modifier.fillMaxWidth()) {
        if (selected == null) {
            ArcadeText("PICK A PRIZE!", unit = 2.8.dp, color = Color(Pal.LAVENDER))
            Spacer(Modifier.height(6.dp))
            ArcadeText("WIN TICKETS AT THE MACHINES", unit = 2.dp, tiny = true, color = Color(Pal.GRAY))
            return@GlassBox
        }
        ArcadeText(selected.name, unit = 2.8.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(6.dp))
        ArcadeText(selected.blurb, unit = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
        Spacer(Modifier.height(10.dp))
        val owned = save.owns(selected.id)
        val worn = selected.id == save.hat || selected.id == save.outfit
        val label = when {
            !owned -> "BUY ${ArcadeFont.TICKET}${selected.price}"
            selected.kind == ItemKind.DECOR -> "IN THE HALL"
            selected.kind == ItemKind.HAT && worn -> "TAKE OFF"
            worn -> "WEARING"
            else -> "WEAR IT"
        }
        val enabled = when {
            !owned -> save.tickets >= selected.price
            selected.kind == ItemKind.DECOR -> false
            selected.kind == ItemKind.OUTFIT && worn -> false
            else -> true
        }
        ArcadeButton(label, { onAction(selected) }, color = Color(if (!owned) Pal.GREEN else Pal.SKY), enabled = enabled || !owned, unit = 2.6.dp)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ArcadeText(message, unit = 2.dp, tiny = true, color = Color(Pal.CYAN))
        }
    }
}

@Composable
private fun PlushGrid(save: SaveState, modifier: Modifier) {
    val found = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcadeText("$found/${Catalog.plushies.size} FOUND", unit = 2.8.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(4.dp))
        ArcadeText("WIN THEM AT THE CLAW MACHINE", unit = 2.dp, tiny = true, color = Color(Pal.GRAY))
        Spacer(Modifier.height(8.dp))
        PlushCollectionGrid(save, Modifier.weight(1f))
    }
}

/** Every plush with how many you've won; ones not found yet are dark silhouettes. */
@Composable
fun PlushCollectionGrid(save: SaveState, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Catalog.plushies, key = { it.id }) { p ->
            val count = save.collection[p.id] ?: 0
            val shape = RoundedCornerShape(14.dp)
            val edge = if (p.rare && count > 0) Color(Pal.GOLD) else UiColors.glassEdge
            Column(
                Modifier
                    .clip(shape)
                    .background(UiColors.glass)
                    .border(if (p.rare && count > 0) 2.dp else 1.dp, edge, shape)
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val known = count > 0
                Thumb("plush:${p.id}:$known", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                    plush(p, silhouette = !known)
                }
                Spacer(Modifier.size(4.dp))
                ArcadeText(if (known) p.name else "???", unit = 1.9.dp, tiny = true, centered = true)
                Spacer(Modifier.height(3.dp))
                ArcadeText(if (known) "×$count" else "-", unit = 2.dp, tiny = true, color = Color(Pal.GOLD))
            }
        }
    }
}
