package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.FilterQuality
import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.games.MiniGame
import com.pocketarcade.hub.emblem

/** The lifetime totals shown at the top of the profile. */
internal class ProfileTotals(val played: Int, val prizes: Int, val plushFound: Int, val plushTotal: Int, val photos: Long)

/** Tallies [save] into the profile's totals (the starting outfit doesn't count as a prize). */
internal fun profileTotals(save: SaveState): ProfileTotals = ProfileTotals(
    played = save.totalPlays,
    prizes = save.owned.count { it != Catalog.DEFAULT_OUTFIT },
    plushFound = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 },
    plushTotal = Catalog.plushies.size,
    photos = save.stat("photos"),
)

/** How many of [games] have a score on the board. */
internal fun machinesPlayed(save: SaveState, games: List<MiniGame>): Int = games.count { save.highScore(it.id) > 0 }

/**
 * Your arcade at a glance: the name on the door, lifetime totals, a row for every machine (its
 * emblem in its glow, your best score and plays) and the plush collection. It scrolls as one, so
 * a short phone never squeezes the collection.
 */
@Composable
fun ProfileScreen(save: SaveState, games: List<MiniGame>, onClose: () -> Unit) {
    val purple = Color(Pal.PURPLE)
    val totals = profileTotals(save)
    ArcadePanel("MY ARCADE", purple, onClose, fillHeight = true) {
        CurrencyRow(save.tokens, save.tickets, unit = 2.5.dp)
        Spacer(Modifier.height(UiSpace.sm + 2.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpace.sm),
            verticalArrangement = Arrangement.spacedBy(UiSpace.sm),
        ) {
            item(key = "plaque", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().staggerIn(0), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArcadeText(save.arcadeName, UiText.TITLE, color = purple.lift(0.45f), centered = true)
                    Spacer(Modifier.height(UiSpace.xs))
                    ArcadeText("YOUR ARCADE", UiText.CAPTION, color = UiColors.textLow, centered = true)
                }
            }
            item(key = "totals", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().staggerIn(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatTile("${totals.played}", "PLAYED", Color(Pal.SKY), Modifier.weight(1f))
                    StatTile("${totals.prizes}", "PRIZES", Color(Pal.PINK), Modifier.weight(1f))
                    StatTile("${totals.plushFound}/${totals.plushTotal}", "PLUSH", Color(Pal.LIME), Modifier.weight(1f))
                    StatTile("${totals.photos}", "PHOTOS", Color(Pal.ORANGE), Modifier.weight(1f))
                }
            }
            item(key = "scores", span = { GridItemSpan(maxLineSpan) }) { ScoreBoard(save, games) }
            item(key = "collection", span = { GridItemSpan(maxLineSpan) }) {
                CollectionHeader(save, Color(Pal.PINK), "WIN THEM AT THE CLAW MACHINE", Modifier.padding(top = UiSpace.xs))
            }
            plushItems(save)
        }
    }
}

/** One lifetime total: a big value over a small label, in a glass tile edged in [color]. */
@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .cardFrame(color.copy(alpha = 0.55f), tint = color)
            .clearAndSetSemantics { contentDescription = "$value ${label.lowercase()}" }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArcadeText(value, UiText.HEADING, color = Color.White, centered = true)
        Spacer(Modifier.height(3.dp))
        ArcadeText(label, UiText.CAPTION, color = color.lift(0.3f), centered = true)
    }
}

/** Every machine with your best score: emblem, name, plays and the best. */
@Composable
private fun ScoreBoard(save: SaveState, games: List<MiniGame>) {
    GlassBox(Modifier.fillMaxWidth()) {
        SectionHeader("HIGH SCORES", Color(Pal.YELLOW), trailing = "${machinesPlayed(save, games)}/${games.size} PLAYED")
        Spacer(Modifier.height(UiSpace.sm))
        games.forEachIndexed { i, g ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(UiEdge.hair).background(UiColors.glassEdge.copy(alpha = 0.12f)))
            ScoreRow(g, save.highScore(g.id), save.stat("plays:${g.id}"))
        }
    }
}

/**
 * One machine's row: its emblem in its glow, its name in that glow, how often you've played it
 * (once the game counts that) and your best, dimmed when you haven't scored yet. At least 44 dp
 * tall.
 */
@Composable
private fun ScoreRow(game: MiniGame, best: Int, plays: Long) {
    val glow = Color(game.look.glow)
    val played = best > 0
    val note = when {
        plays > 0L -> "$plays ${if (plays == 1L) "PLAY" else "PLAYS"}"
        played -> "PLAYED"
        else -> "NOT PLAYED YET"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = "${game.title.lowercase()}, ${if (played) "best $best" else "not played yet"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmblemChip(game, 38.dp)
        Spacer(Modifier.width(UiSpace.md))
        Column(Modifier.weight(1f)) {
            ArcadeText(game.title, UiText.BODY, color = if (played) glow.lift(0.2f) else glow.copy(alpha = 0.7f))
            Spacer(Modifier.height(2.dp))
            ArcadeText(note, UiText.CAPTION, color = if (plays > 0L) UiColors.textMid else UiColors.textOff)
        }
        Spacer(Modifier.width(UiSpace.sm))
        Column(horizontalAlignment = Alignment.End) {
            ArcadeText("BEST", UiText.CAPTION, color = UiColors.textLow)
            Spacer(Modifier.height(2.dp))
            ArcadeText(if (played) "$best" else "-", UiText.HEADING, color = if (played) Color.White else UiColors.textOff)
        }
    }
}

/** The emblems painted so far, by machine and size: they are painted once and kept. */
private val emblemCache = HashMap<String, ImageBitmap>()

/** [game]'s cabinet emblem (the picture on its marquee) painted at [px] pixels square. */
private fun emblemFor(game: MiniGame, px: Int): ImageBitmap = emblemCache.getOrPut("${game.id}@$px") {
    val tp = TexPaint(px, px)
    val l = game.look
    tp.emblem(l.shape, px / 2f, px / 2f, px / 2f - 1f, l.body, l.trim, l.glow)
    tp.bitmap.asImageBitmap()
}

/**
 * A machine's emblem in a chip: the same painted badge that is on its cabinet in the hall (a
 * claw over a plush, skee rings, a chequered flag...), inside a ring of the machine's glow colour.
 */
@Composable
fun EmblemChip(game: MiniGame, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(24, 256)
    val image = remember(game.id, px) { emblemFor(game, px) }
    val glow = Color(game.look.glow)
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val r = this.size.minDimension / 2f
        glowCircle(glow, center, r, r * 0.35f, 0.35f)
        drawImage(
            image, dstOffset = IntOffset.Zero, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
