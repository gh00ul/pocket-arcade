package com.pocketarcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.ItemKind
import com.pocketarcade.data.SaveState
import com.pocketarcade.data.ShopItem
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelFont
import com.pocketarcade.engine.Sfx
import com.pocketarcade.games.claw.PlushArt
import com.pocketarcade.hub.CharacterArt
import com.pocketarcade.hub.PropArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TABS = listOf("HATS", "STYLE", "DECOR", "PLUSH")

/** Trade tickets for hats, outfit colours and hall decorations; browse won plushies. */
@Composable
fun PrizeCounterScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val images = remember(save.hat, save.outfit) { HashMap<String, ImageBitmap>() }

    val items: List<ShopItem> = when (tab) {
        0 -> Catalog.hats
        1 -> Catalog.outfits
        2 -> Catalog.decor
        else -> emptyList()
    }
    val selected = selectedId?.let { Catalog.byId(it) }?.takeIf { it in items }

    fun imageFor(item: ShopItem): ImageBitmap = images.getOrPut(item.id) {
        val outfit = Catalog.outfit(save.outfit)
        when (item.kind) {
            ItemKind.HAT -> CharacterArt.build(CharacterArt.player(outfit.shirt, outfit.pants, item.hat), CharacterArt.DOWN, 0).toImageBitmap()
            ItemKind.OUTFIT -> CharacterArt.build(CharacterArt.player(item.shirt, item.pants, Catalog.hat(save.hat)?.hat), CharacterArt.DOWN, 0).toImageBitmap()
            ItemKind.DECOR -> PropArt.decor(item.decor!!).image
        }
    }

    ArcadePanel("PRIZE COUNTER", Color(Pal.PINK), onClose, fillHeight = true) {
        CurrencyRow(save.tokens, save.tickets)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, label ->
                ArcadeButton(
                    label, { tab = i; selectedId = null; message = ""; services.audio.play(Sfx.BLIP, 0.6f) },
                    Modifier.weight(1f),
                    color = if (tab == i) Color(Pal.PINK) else Color(Pal.PLUM),
                    pixel = 2.dp, tiny = false,
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCell(
                        item = item,
                        image = imageFor(item),
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

@Composable
private fun PreviewCharacter(save: SaveState, selected: ShopItem?) {
    var dir by remember { mutableIntStateOf(CharacterArt.DOWN) }
    LaunchedEffect(Unit) {
        val order = intArrayOf(CharacterArt.DOWN, CharacterArt.LEFT, CharacterArt.UP, CharacterArt.RIGHT)
        var i = 0
        while (true) {
            delay(900)
            i = (i + 1) % order.size
            dir = order[i]
        }
    }
    val outfit = if (selected?.kind == ItemKind.OUTFIT) selected else Catalog.outfit(save.outfit)
    val hat = if (selected?.kind == ItemKind.HAT) selected.hat else Catalog.hat(save.hat)?.hat
    val look = CharacterArt.player(outfit.shirt, outfit.pants, hat)
    val image = remember(look, dir) { CharacterArt.build(look, dir, 0).toImageBitmap() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(Pal.DEEP))
            .border(2.dp, Color(Pal.PLUM))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelImage(image, 4.dp)
    }
}

@Composable
private fun ItemCell(
    item: ShopItem,
    image: ImageBitmap,
    owned: Boolean,
    worn: Boolean,
    affordable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        selected -> Color(Pal.YELLOW)
        worn -> Color(Pal.LIME)
        else -> Color(Pal.PLUM)
    }
    Column(
        Modifier
            .background(Color(if (selected) Pal.PLUM else Pal.DEEP))
            .border(2.dp, border)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val px = (56f / maxOf(image.width, image.height)).coerceIn(1f, 3f)
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            PixelImage(image, px.dp)
        }
        Spacer(Modifier.height(4.dp))
        PixelText(item.name, pixel = 2.dp, tiny = true, centered = true, color = Color.White)
        Spacer(Modifier.height(4.dp))
        val (label, color) = when {
            worn -> "WORN" to Pal.LIME
            owned && item.kind == ItemKind.DECOR -> "PLACED" to Pal.LIME
            owned -> "OWNED" to Pal.CYAN
            item.price == 0 -> "FREE" to Pal.CYAN
            else -> "${PixelFont.TICKET}${item.price}" to (if (affordable) Pal.GOLD else Pal.GRAY)
        }
        PixelText(label, pixel = 2.dp, tiny = true, color = Color(color))
    }
}

@Composable
private fun ActionBar(save: SaveState, selected: ShopItem?, message: String, onAction: (ShopItem) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(Pal.DEEP))
            .border(2.dp, Color(Pal.PLUM))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (selected == null) {
            PixelText("PICK A PRIZE!", pixel = 3.dp, color = Color(Pal.LAVENDER))
            Spacer(Modifier.height(6.dp))
            PixelText("WIN TICKETS AT THE MACHINES", pixel = 2.dp, tiny = true, color = Color(Pal.GRAY))
            return
        }
        PixelText(selected.name, pixel = 3.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(6.dp))
        PixelText(selected.blurb, pixel = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
        Spacer(Modifier.height(10.dp))
        val owned = save.owns(selected.id)
        val worn = selected.id == save.hat || selected.id == save.outfit
        val label = when {
            !owned -> "BUY ${PixelFont.TICKET}${selected.price}"
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
        ArcadeButton(label, { onAction(selected) }, color = Color(if (!owned) Pal.GREEN else Pal.SKY), enabled = enabled || !owned)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PixelText(message, pixel = 2.dp, tiny = true, color = Color(Pal.CYAN))
        }
    }
}

@Composable
private fun PlushGrid(save: SaveState, modifier: Modifier) {
    val found = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText("$found/${Catalog.plushies.size} FOUND", pixel = 3.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(4.dp))
        PixelText("WIN THEM AT THE CLAW MACHINE", pixel = 2.dp, tiny = true, color = Color(Pal.GRAY))
        Spacer(Modifier.height(8.dp))
        PlushCollectionGrid(save, Modifier.weight(1f))
    }
}

/** Every plush with how many you've won; unknown ones are silhouettes. */
@Composable
fun PlushCollectionGrid(save: SaveState, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(Catalog.plushies, key = { it.id }) { p ->
            val count = save.collection[p.id] ?: 0
            Column(
                Modifier
                    .background(Color(Pal.DEEP))
                    .border(2.dp, Color(if (p.rare && count > 0) Pal.GOLD else Pal.PLUM))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    PixelImage(PlushArt.image(p), 3.dp, tint = if (count == 0) Color(Pal.PLUM) else null)
                }
                PixelText(if (count > 0) p.name else "???", pixel = 2.dp, tiny = true, centered = true)
                Spacer(Modifier.height(3.dp))
                PixelText(if (count > 0) "×$count" else "-", pixel = 2.dp, tiny = true, color = Color(Pal.GOLD))
            }
        }
    }
}
