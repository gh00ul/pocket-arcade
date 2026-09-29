package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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

/** How long the turntable holds each angle, and the angle it rests at when the interface may not move (a three-quarter view). */
private const val TURN_MILLIS = 140L
private const val REST_STEP = 2

/** The preview stage is this share of the screen's height, kept within [PREVIEW_MIN] and [PREVIEW_MAX] dp, so the grid keeps room on a short phone. */
private const val PREVIEW_SCREEN_SHARE = 0.2f
private const val PREVIEW_MIN = 104f
private const val PREVIEW_MAX = 170f

/** How rare a prize is, from what it costs: the edge colour of its card. */
internal enum class Rarity(val label: String, val accent: Color) {
    COMMON("COMMON", Color(Pal.LAVENDER)),
    RARE("RARE", Color(Pal.SKY)),
    EPIC("EPIC", Color(0xFFB57CFF)),
    LEGENDARY("LEGENDARY", Color(Pal.GOLD)),
}

/** The tier for a prize that costs [price] tickets: under 120 is common, then 250 and 450 are the steps up. */
internal fun rarityOf(price: Int): Rarity = when {
    price >= 450 -> Rarity.LEGENDARY
    price >= 250 -> Rarity.EPIC
    price >= 120 -> Rarity.RARE
    else -> Rarity.COMMON
}

/** Where a prize stands for this player: what its card and its button say. */
internal enum class PrizeState { WORN, PLACED, OWNED, FREE, AFFORDABLE, LOCKED }

/**
 * The state of an item of [kind] costing [price] for a player with [tickets], who [owned] it or
 * not and is wearing it ([worn]). A decoration you own is placed in the hall for good; an item you
 * can't afford yet is locked.
 */
internal fun prizeState(kind: ItemKind, price: Int, owned: Boolean, worn: Boolean, tickets: Int): PrizeState = when {
    owned && kind == ItemKind.DECOR -> PrizeState.PLACED
    owned && worn -> PrizeState.WORN
    owned -> PrizeState.OWNED
    price == 0 -> PrizeState.FREE
    tickets >= price -> PrizeState.AFFORDABLE
    else -> PrizeState.LOCKED
}

/** What a screen reader says for [state]. */
private fun PrizeState.spoken(price: Int): String = when (this) {
    PrizeState.WORN -> "wearing"
    PrizeState.PLACED -> "in the hall"
    PrizeState.OWNED -> "owned"
    PrizeState.FREE -> "free"
    PrizeState.AFFORDABLE -> "$price tickets, you can afford it"
    PrizeState.LOCKED -> "$price tickets, not enough tickets yet"
}

/** How many of the [items] the player has (the count under each tab). */
private fun ownedCount(save: SaveState, items: List<ShopItem>) = items.count { save.owns(it.id) }

/** Trade tickets for hats, outfit colours and hall decorations; browse won plushies. */
@Composable
fun PrizeCounterScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var messageBad by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val previewHeight = (LocalConfiguration.current.screenHeightDp * PREVIEW_SCREEN_SHARE).coerceIn(PREVIEW_MIN, PREVIEW_MAX).dp

    val items: List<ShopItem> = when (tab) {
        0 -> Catalog.hats
        1 -> Catalog.outfits
        2 -> Catalog.decor
        else -> emptyList()
    }
    val selected = selectedId?.let { Catalog.byId(it) }?.takeIf { it in items }
    val plushFound = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
    val counts = listOf(
        "${ownedCount(save, Catalog.hats)}/${Catalog.hats.size}",
        "${ownedCount(save, Catalog.outfits)}/${Catalog.outfits.size}",
        "${ownedCount(save, Catalog.decor)}/${Catalog.decor.size}",
        "$plushFound/${Catalog.plushies.size}",
    )

    ArcadePanel("PRIZE COUNTER", Color(Pal.PINK), onClose, fillHeight = true) {
        CurrencyRow(save.tokens, save.tickets, unit = 2.5.dp)
        Spacer(Modifier.height(UiSpace.sm + 2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, label ->
                PrizeTab(
                    label, counts[i], tab == i, Color(Pal.PINK),
                    {
                        tab = i
                        selectedId = null
                        message = ""
                        services.audio.play(Sfx.BLIP, 0.6f)
                    },
                    Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(UiSpace.sm + 2.dp))
        if (tab == 3) {
            PlushGrid(save, Modifier.weight(1f))
        } else {
            if (tab != 2) {
                PreviewStage(save, selected, previewHeight)
                Spacer(Modifier.height(UiSpace.sm))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
                verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                    val owned = save.owns(item.id)
                    val worn = item.id == save.hat || item.id == save.outfit
                    ItemCard(
                        item = item,
                        save = save,
                        state = prizeState(item.kind, item.price, owned, worn, save.tickets),
                        selected = item.id == selected?.id,
                        modifier = Modifier.staggerIn(1 + i / 3),
                        onClick = {
                            selectedId = item.id
                            message = ""
                            services.audio.play(Sfx.BLIP, 0.6f, 1.2f)
                        },
                    )
                }
            }
            Spacer(Modifier.height(UiSpace.sm + 2.dp))
            ActionBar(save, selected, message, messageBad) { item ->
                scope.launch {
                    val owned = save.owns(item.id)
                    if (!owned) {
                        if (services.repo.buy(item)) {
                            services.audio.play(Sfx.WIN)
                            services.audio.play(Sfx.CHEER, 0.5f)
                            services.haptics.win()
                            messageBad = false
                            message = if (item.kind == ItemKind.DECOR) "IT'S IN THE HALL NOW!" else "NEW ITEM! LOOKING GOOD!"
                        } else {
                            services.audio.play(Sfx.ERROR)
                            messageBad = true
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

/**
 * One of the four tabs: a lit pill in the accent when chosen, glass when not, with how many of its
 * prizes you have underneath. Tall enough (52 dp) to hit with a thumb; a screen reader hears it as
 * a selectable tab with its count.
 */
@Composable
private fun PrizeTab(label: String, count: String, selected: Boolean, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(UiRadius.card)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    Column(
        modifier
            .height(52.dp)
            .graphicsLayer {
                val s = 1f - PRESS_SHRINK * press.value
                scaleX = s
                scaleY = s
            }
            .then(if (selected) Modifier.uiGlow(accent, UiRadius.card, 8.dp, UiGlow.IDLE) else Modifier)
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(accent.lift(0.22f), accent.shade(0.72f)))
                else Brush.verticalGradient(listOf(UiColors.glassHi, UiColors.glassLo)),
            )
            .drawBehind { paintGlassBevel(UiRadius.card.toPx()) }
            .border(if (selected) UiEdge.line else UiEdge.hair, if (selected) accent.lift(0.4f) else UiColors.glassEdge, shape)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "${label.lowercase()}, $count owned" }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArcadeText(label, UiText.LABEL, color = if (selected) Color.White else UiColors.textMid, centered = true)
        Spacer(Modifier.height(3.dp))
        ArcadeText(count, UiText.CAPTION, color = if (selected) Color.White.copy(alpha = 0.85f) else UiColors.textLow, centered = true)
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

/**
 * The player on a turntable under stage lights, wearing whatever is selected: a framed stage with
 * corner brackets, a vignette and the pick's rarity. The turntable stands still (at a three-quarter
 * view) when the interface may not move.
 */
@Composable
private fun PreviewStage(save: SaveState, selected: ShopItem?, height: Dp) {
    var step by remember { mutableIntStateOf(REST_STEP) }
    LaunchedEffect(UiMotion.enabled) {
        if (!UiMotion.enabled) {
            step = REST_STEP
            return@LaunchedEffect
        }
        while (true) {
            delay(TURN_MILLIS)
            step = (step + 1) % TURN_STEPS
        }
    }
    val look = lookFor(save, selected)
    val rarity = selected?.let { rarityOf(it.price) }
    val accent = rarity?.accent ?: Color(Pal.PINK)
    val shape = RoundedCornerShape(UiRadius.box)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .uiGlow(accent, UiRadius.box, 8.dp, 0.22f)
            .clip(shape)
            .background(UiColors.stage)
            .border(UiEdge.line, Brush.verticalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.22f))), shape),
    ) {
        FigureThumb(look, step, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // A soft vignette pulls the eye to the middle; four brackets frame it like a viewfinder.
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99070510)), center, size.maxDimension * 0.62f))
            val m = 10.dp.toPx()
            val len = 14.dp.toPx()
            val w = 2.dp.toPx()
            val c = accent.copy(alpha = 0.75f)
            for (sx in intArrayOf(0, 1)) for (sy in intArrayOf(0, 1)) {
                val x = if (sx == 0) m else size.width - m
                val y = if (sy == 0) m else size.height - m
                val dx = if (sx == 0) len else -len
                val dy = if (sy == 0) len else -len
                drawLine(c, Offset(x, y), Offset(x + dx, y), w, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy), w, StrokeCap.Round)
            }
        }
        if (rarity != null) {
            ArcadeChip(rarity.label, rarity.accent, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp), filled = true)
        } else {
            ArcadeText("TAP A PRIZE TO TRY IT ON", UiText.CAPTION, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), color = UiColors.textMid, centered = true)
        }
    }
}

/** A small round badge carrying an icon, in [color]: the mark on a card that says worn or locked. */
@Composable
internal fun IconBadge(icon: UiIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.4f), r, center + Offset(0f, r * 0.12f))
        drawCircle(Brush.verticalGradient(listOf(color.lift(0.3f), color.shade(0.72f))), r, center)
        drawCircle(Color.White.copy(alpha = 0.5f), r, center, style = Stroke(1.dp.toPx()))
        drawUiIcon(icon, center, r * 1.25f, if (color.luminance() > 0.5f) Color(0xFF1B1030) else Color.White)
    }
}

/** A colour's rough brightness, 0 (black) to 1 (white). */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/**
 * The tag at the foot of a card: a gold price with the ticket when you can afford it, a dull one
 * with a lock when you can't, and a green or cyan word (with a tick) once it is yours.
 */
@Composable
private fun StateTag(state: PrizeState, price: Int) {
    when (state) {
        PrizeState.WORN -> TagRow("WORN", UiColors.good, UiIcon.CHECK, filled = true)
        PrizeState.PLACED -> TagRow("PLACED", UiColors.good, UiIcon.CHECK, filled = true)
        PrizeState.OWNED -> TagRow("OWNED", UiColors.info, UiIcon.CHECK, filled = false)
        PrizeState.FREE -> TagRow("FREE", UiColors.info, null, filled = false)
        PrizeState.AFFORDABLE -> PriceTag(price, true)
        PrizeState.LOCKED -> PriceTag(price, false)
    }
}

@Composable
private fun TagRow(text: String, color: Color, icon: UiIcon?, filled: Boolean) {
    val shape = RoundedCornerShape(UiRadius.chip)
    Row(
        Modifier
            .clip(shape)
            .background(if (filled) Brush.verticalGradient(listOf(color.lift(0.22f), color.shade(0.78f))) else Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.08f))))
            .border(UiEdge.hair, color.copy(alpha = if (filled) 0.9f else 0.6f), shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (filled) Color(0xFF12301F) else color.lift(0.3f)
        if (icon != null) {
            Canvas(Modifier.size(10.dp)) { drawUiIcon(icon, center, this.size.minDimension * 1.15f, ink) }
            Spacer(Modifier.width(3.dp))
        }
        ArcadeText(text, UiText.CAPTION, color = ink, shadow = !filled)
    }
}

/** A price: the ticket and the number in a dark pill, gold and lit when [affordable], grey with a lock when not. */
@Composable
private fun PriceTag(price: Int, affordable: Boolean) {
    val shape = RoundedCornerShape(UiRadius.chip)
    val tone = if (affordable) UiColors.ticket else UiColors.textLow
    Row(
        Modifier
            .clip(shape)
            .background(if (affordable) Brush.verticalGradient(listOf(Color(0x40FFA24A), Color(0x14FFA24A))) else Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x0AFFFFFF))))
            .border(UiEdge.hair, tone.copy(alpha = if (affordable) 0.85f else 0.4f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (affordable) {
            TicketIcon(9.dp)
        } else {
            Canvas(Modifier.size(10.dp)) { drawUiIcon(UiIcon.LOCK, center, this.size.minDimension * 1.2f, tone) }
        }
        Spacer(Modifier.width(4.dp))
        ArcadeText("$price", UiText.LABEL, color = if (affordable) UiColors.gold else tone)
    }
}

@Composable
private fun ItemCard(
    item: ShopItem,
    save: SaveState,
    state: PrizeState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rarity = rarityOf(item.price)
    val equipped = state == PrizeState.WORN || state == PrizeState.PLACED
    val edge = when {
        selected -> Color(Pal.YELLOW)
        equipped -> UiColors.good
        rarity == Rarity.COMMON -> UiColors.glassEdge
        else -> rarity.accent.copy(alpha = 0.7f)
    }
    val locked = state == PrizeState.LOCKED
    val spoken = remember(item.name, state, item.price) { "${item.name.lowercase()}, ${state.spoken(item.price)}" }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    Column(
        modifier
            .graphicsLayer {
                val s = 1f - PRESS_SHRINK * 0.6f * press.value
                scaleX = s
                scaleY = s
            }
            .cardFrame(
                edge, thick = selected || equipped,
                glow = if (selected) UiGlow.ACTIVE else if (equipped) 0.25f else 0f,
                tint = if (selected) Color(Pal.YELLOW) else if (rarity != Rarity.COMMON && !locked) rarity.accent else Color.Transparent,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                val pic = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).graphicsLayer { alpha = if (locked) 0.55f else 1f }
                when (item.kind) {
                    ItemKind.DECOR -> Thumb("decor:${item.decor}", pic) { decor(item.decor!!) }
                    else -> FigureThumb(lookFor(save, item), 0, pic)
                }
                if (equipped) IconBadge(UiIcon.CHECK, UiColors.good, Modifier.align(Alignment.TopEnd).padding(3.dp))
                if (locked) IconBadge(UiIcon.LOCK, Color(0xFF6A6488), Modifier.align(Alignment.TopEnd).padding(3.dp))
            }
            Spacer(Modifier.height(5.dp))
            ArcadeText(item.name, UiText.CAPTION, color = if (locked) UiColors.textMid else Color.White, centered = true)
            Spacer(Modifier.height(4.dp))
            StateTag(state, item.price)
        }
    }
}

/**
 * The strip under the grid: the pick's name, rarity and blurb, and the one button that does the
 * thing (buy, wear or take off), with what it leaves you. With nothing picked it says so.
 */
@Composable
private fun ActionBar(save: SaveState, selected: ShopItem?, message: String, messageBad: Boolean, onAction: (ShopItem) -> Unit) {
    GlassBox(Modifier.fillMaxWidth(), highlight = if (selected != null) Color(Pal.YELLOW) else null) {
        if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp)) { drawUiIcon(UiIcon.SPARKLE, center, this.size.minDimension * 1.1f, Color(Pal.LAVENDER)) }
                Spacer(Modifier.width(UiSpace.sm))
                ArcadeText("PICK A PRIZE!", UiText.HEADING, color = Color(Pal.LAVENDER))
            }
            Spacer(Modifier.height(5.dp))
            ArcadeText("WIN TICKETS AT THE MACHINES", UiText.CAPTION, color = UiColors.textLow, centered = true)
            return@GlassBox
        }
        val rarity = rarityOf(selected.price)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcadeText(selected.name, UiText.HEADING, Modifier.weight(1f, fill = false), color = Color(Pal.YELLOW))
            Spacer(Modifier.width(UiSpace.sm))
            ArcadeChip(rarity.label, rarity.accent)
        }
        Spacer(Modifier.height(4.dp))
        ArcadeText(selected.blurb, UiText.CAPTION, color = UiColors.textMid, centered = true)
        Spacer(Modifier.height(UiSpace.sm))
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
        ArcadeButton(
            label, { onAction(selected) }, Modifier.fillMaxWidth(),
            color = Color(if (!owned) Pal.GREEN else Pal.SKY), enabled = enabled || !owned, unit = 2.6.dp,
        )
        if (!owned && save.tickets >= selected.price) {
            Spacer(Modifier.height(5.dp))
            ArcadeText("LEAVES YOU ${ArcadeFont.TICKET}${save.tickets - selected.price}", UiText.CAPTION, color = UiColors.textLow, centered = true)
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ArcadeText(message, UiText.LABEL, color = if (messageBad) UiColors.bad else UiColors.info, centered = true)
        }
    }
}

@Composable
private fun PlushGrid(save: SaveState, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CollectionHeader(save, Color(Pal.PINK), "WIN THEM AT THE CLAW MACHINE")
        Spacer(Modifier.height(UiSpace.sm))
        PlushCollectionGrid(save, Modifier.weight(1f))
    }
}

/**
 * The plush collection's heading: how many you've found out of all, as a big count over a
 * progress bar in [accent], with a [note] under it (where to win more).
 */
@Composable
internal fun CollectionHeader(save: SaveState, accent: Color, note: String, modifier: Modifier = Modifier) {
    val found = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
    val total = Catalog.plushies.size
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            ArcadeText("$found", UiText.DISPLAY, color = Color(Pal.YELLOW))
            Spacer(Modifier.width(6.dp))
            ArcadeText("/ $total FOUND", UiText.HEADING, Modifier.padding(bottom = 3.dp), color = UiColors.textMid)
        }
        Spacer(Modifier.height(6.dp))
        ArcadeProgressBar(if (total == 0) 0f else found.toFloat() / total, accent, description = "$found of $total plushies found")
        Spacer(Modifier.height(5.dp))
        ArcadeText(note, UiText.CAPTION, color = UiColors.textLow, centered = true)
    }
}

/**
 * Every plush with how many you've won. Ones not found yet are dark silhouettes with a question
 * mark; found ones carry their own colour, a count, and the golden cat a gold frame and a RARE tag.
 */
@Composable
fun PlushCollectionGrid(save: SaveState, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
        verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
    ) {
        plushItems(save)
    }
}

/** The plush cards as items of a three-column grid: the collection grid, and the profile's, share them. */
internal fun LazyGridScope.plushItems(save: SaveState) {
    itemsIndexed(Catalog.plushies, key = { _, it -> it.id }) { i, p ->
        val count = save.collection[p.id] ?: 0
        val known = count > 0
        val gold = p.rare && known
        val spoken = if (known) "${p.name.lowercase()}, won $count" else "not found yet"
        Column(
            Modifier
                .staggerIn(1 + i / 3)
                .cardFrame(
                    edge = if (gold) Color(Pal.GOLD) else if (known) Color(p.main).copy(alpha = 0.6f) else UiColors.glassEdge,
                    thick = gold, glow = if (gold) UiGlow.ACTIVE else 0f,
                    tint = if (gold) Color(Pal.GOLD) else if (known) Color(p.main) else Color.Transparent,
                )
                .semantics(mergeDescendants = false) { contentDescription = spoken }
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                    Thumb("plush:${p.id}:$known", Modifier.fillMaxSize()) { plush(p, silhouette = !known) }
                    if (!known) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArcadeText("?", UiText.DISPLAY, color = UiColors.textLow.copy(alpha = 0.8f))
                        }
                    }
                    if (gold) IconBadge(UiIcon.STAR, Color(Pal.GOLD), Modifier.align(Alignment.TopEnd).padding(3.dp))
                }
                Spacer(Modifier.size(4.dp))
                ArcadeText(if (known) p.name else "???", UiText.CAPTION, color = if (known) Color.White else UiColors.textLow, centered = true)
                Spacer(Modifier.height(4.dp))
                if (known) ArcadeChip("×$count", UiColors.gold) else ArcadeText("NOT FOUND", UiText.CAPTION, color = UiColors.textOff, centered = true)
            }
        }
    }
}
