package com.pocketarcade.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** How long a day is: the daily bonus ring fills over it. */
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** A duration as the countdowns show it: "5H 32M" from an hour up, "12:04" below. */
internal fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dH %02dM".format(h, m) else "%d:%02d".format(m, s)
}

/** How far through the day the clock is, 0..1, with [untilRefillMs] left until the next refill at midnight. */
internal fun dailyProgress(untilRefillMs: Long): Float = (1f - untilRefillMs.toFloat() / DAY_MILLIS).coerceIn(0f, 1f)

/** How far the wait for a spare token has got, 0..1, with [remainingMs] left of [cooldownMs]. */
internal fun spareProgress(remainingMs: Long, cooldownMs: Long = ArcadeRepository.SPARE_TOKEN_COOLDOWN_MS): Float =
    (1f - remainingMs.toFloat() / cooldownMs).coerceIn(0f, 1f)

/** What the trade card tells you about the exchange with [tickets] in hand: how many tokens they buy, or how far off the first is. */
internal fun tradeHint(tickets: Int, perToken: Int = ArcadeRepository.TICKETS_PER_TOKEN): String {
    val can = tickets / perToken
    return when {
        can >= 2 -> "ENOUGH FOR $can TOKENS"
        can == 1 -> "ENOUGH FOR 1 TOKEN"
        else -> "${perToken - tickets} MORE TICKETS FOR A TOKEN"
    }
}

/**
 * The token machine: a lit kiosk showing your tokens, the daily refill on a countdown ring,
 * a ticket-for-token trade and, when the player is completely out, a spare token every few
 * minutes so play never stalls. The cards scroll if the phone is short.
 */
@Composable
fun TokenMachineScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val untilRefill = remember(now) {
        Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).toMillis()
    }
    val gold = Color(Pal.GOLD)

    ArcadePanel("TOKEN MACHINE", gold, onClose) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TokenMachineArt(save.tokens, Modifier.staggerIn(0))
            Spacer(Modifier.height(UiSpace.md))

            GlassBox(Modifier.fillMaxWidth(), highlight = UiColors.good) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(dailyProgress(untilRefill), UiColors.good, RING_SIZE) {
                        ArcadeText(formatDuration(untilRefill), UiText.LABEL, color = Color.White, centered = true, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Spacer(Modifier.width(UiSpace.md))
                    Column(Modifier.weight(1f)) {
                        ArcadeText("DAILY BONUS", UiText.HEADING, color = UiColors.good)
                        Spacer(Modifier.height(UiSpace.xs))
                        ArcadeText("+${ArcadeRepository.DAILY_TOKENS} FREE TOKENS EVERY DAY", UiText.CAPTION, color = Color.White)
                        Spacer(Modifier.height(UiSpace.xs))
                        ArcadeText("NEXT REFILL AT MIDNIGHT", UiText.CAPTION, color = UiColors.textMid)
                    }
                }
            }
            Spacer(Modifier.height(UiSpace.md))

            GlassBox(Modifier.fillMaxWidth(), highlight = UiColors.ticket) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TicketIcon(24.dp)
                    Spacer(Modifier.width(UiSpace.sm + 2.dp))
                    Column(Modifier.weight(1f)) {
                        ArcadeText("TRADE TICKETS", UiText.HEADING, color = UiColors.ticket)
                        Spacer(Modifier.height(UiSpace.xs))
                        ArcadeText("YOU HAVE ${ArcadeFont.TICKET}${save.tickets}", UiText.CAPTION, color = Color.White)
                    }
                }
                Spacer(Modifier.height(UiSpace.sm + 2.dp))
                ArcadeProgressBar(
                    (save.tickets.toFloat() / ArcadeRepository.TICKETS_PER_TOKEN).coerceAtMost(1f), UiColors.ticket,
                    description = "Tickets toward the next token",
                )
                Spacer(Modifier.height(UiSpace.xs + 2.dp))
                ArcadeText(tradeHint(save.tickets), UiText.CAPTION, color = UiColors.textMid, centered = true)
                Spacer(Modifier.height(UiSpace.sm))
                ArcadeButton(
                    "1 TOKEN FOR ${ArcadeFont.TICKET}${ArcadeRepository.TICKETS_PER_TOKEN}",
                    {
                        scope.launch {
                            if (services.repo.exchangeTicketsForToken()) {
                                services.audio.play(Sfx.TOKEN)
                                services.haptics.hit()
                                message = "CLUNK! +1 TOKEN"
                            } else {
                                services.audio.play(Sfx.ERROR)
                                message = "NOT ENOUGH TICKETS"
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    color = Color(Pal.ORANGE),
                    enabled = save.tickets >= ArcadeRepository.TICKETS_PER_TOKEN,
                    unit = 2.4.dp,
                )
            }

            if (save.tokens == 0) {
                Spacer(Modifier.height(UiSpace.md))
                GlassBox(Modifier.fillMaxWidth(), highlight = UiColors.info) {
                    val ready = now >= save.spareTokenAt
                    ArcadeText("OUT OF TOKENS?", UiText.HEADING, color = UiColors.info)
                    Spacer(Modifier.height(UiSpace.sm))
                    if (ready) {
                        ArcadeText("SOMETHING SHINY UNDER\nTHE MACHINE...", UiText.CAPTION, color = Color.White, centered = true)
                        Spacer(Modifier.height(UiSpace.sm))
                        ArcadeButton(
                            "GRAB IT!",
                            {
                                scope.launch {
                                    if (services.repo.claimSpareToken()) {
                                        services.audio.play(Sfx.COIN)
                                        services.audio.play(Sfx.TOKEN, 0.8f)
                                        services.haptics.win()
                                        message = "A SPARE TOKEN! +1"
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                            color = Color(Pal.CYAN),
                            textColor = Color(Pal.NAVY),
                            unit = 2.4.dp,
                        )
                    } else {
                        val left = save.spareTokenAt - now
                        ArcadeProgressBar(spareProgress(left), UiColors.info, description = "Time until a spare token")
                        Spacer(Modifier.height(UiSpace.xs + 2.dp))
                        ArcadeText("NEXT SPARE TOKEN IN ${formatDuration(left)}", UiText.CAPTION, color = UiColors.textMid, centered = true)
                    }
                }
            }

            if (message.isNotEmpty()) {
                Spacer(Modifier.height(UiSpace.md))
                ArcadeText(message, UiText.HEADING, Modifier.popIn(0, message), color = Color(Pal.YELLOW), centered = true)
            }
            Spacer(Modifier.height(UiSpace.sm))
        }
    }
}

/** The daily bonus ring's diameter. */
private val RING_SIZE = 88.dp

// ---------------------------------------------------------------- the machine

/** The kiosk picture's size, and where its display sits (inside it) for the count laid over it. */
private val ART_W = 220.dp
private val ART_H = 150.dp
private val LCD_TOP = 50.dp
private val LCD_W = 108.dp
private val LCD_H = 44.dp

/** How long the marquee's bulbs take to chase once round their pattern. */
private const val CHASE_MILLIS = 1200

/** The cabinet, top to bottom (violet, sinking to near-black), and its gold trim. */
private val CAB_TOP = Color(0xFF41307A)
private val CAB_BOTTOM = Color(0xFF1A1236)
private val TRIM = Color(0xFFE9A821)

/** The display glass: a deep green-black with a cyan edge glow. */
private val LCD = Color(0xFF041614)

/**
 * The token kiosk: a violet cabinet with a gold marquee and chase bulbs, a display (the count
 * is laid over it as a rolling number), a coin slot and a tray with tokens in it. The bulbs chase
 * unless the interface may not move. The count turns red at zero.
 */
@Composable
private fun TokenMachineArt(tokens: Int, modifier: Modifier = Modifier) {
    val chase = if (UiMotion.enabled) {
        rememberInfiniteTransition(label = "tokenChase").animateFloat(
            0f, 1f, infiniteRepeatable(tween(CHASE_MILLIS, easing = LinearEasing)), label = "chase",
        )
    } else {
        null
    }
    Box(
        modifier
            .size(ART_W, ART_H)
            // The count is spoken once, plainly; the picture and its rolling digits stay quiet.
            .clearAndSetSemantics { contentDescription = "$tokens tokens" },
    ) {
        Canvas(Modifier.size(ART_W, ART_H)) { drawKiosk(chase?.value ?: -1f) }
        Box(Modifier.align(Alignment.TopCenter).padding(top = LCD_TOP).size(LCD_W, LCD_H), contentAlignment = Alignment.Center) {
            Row(Modifier.shrinkToFit(), verticalAlignment = Alignment.CenterVertically) {
                TokenIcon(30.dp, twinkle = true)
                Spacer(Modifier.width(6.dp))
                RollingNumber(tokens, if (tokens == 0) UiColors.bad else UiColors.token, 3.6.dp)
            }
        }
    }
}

/** Paints the kiosk in this scope. [chase] is the bulbs' 0..1 phase, or negative for still bulbs. */
private fun DrawScope.drawKiosk(chase: Float) {
    val d = 1.dp.toPx()
    val w = size.width
    val cabL = w * 0.2f
    val cabR = w * 0.8f
    val cabT = 9 * d
    val cabB = 141 * d
    val cab = CornerRadius(14 * d)
    // A pool of gold light on the floor under it.
    drawOval(Color(0xFFFFC83D).copy(alpha = 0.16f), Offset(w * 0.08f, cabB - 6 * d), Size(w * 0.84f, 16 * d))
    glowRoundRect(TRIM, Offset(cabL, cabT), Size(cabR - cabL, cabB - cabT), cab.x, 10 * d, 0.22f)
    drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(cabL, cabT + 3 * d), Size(cabR - cabL, cabB - cabT), cab)
    drawRoundRect(Brush.verticalGradient(listOf(CAB_TOP, CAB_BOTTOM), cabT, cabB), Offset(cabL, cabT), Size(cabR - cabL, cabB - cabT), cab)
    drawRoundRect(
        Brush.verticalGradient(listOf(TRIM, TRIM.shade(0.5f)), cabT, cabB),
        Offset(cabL, cabT), Size(cabR - cabL, cabB - cabT), cab, style = Stroke(2.dp.toPx()),
    )
    // A lit hairline inside the top edge.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), cabT, cabT + 40 * d),
        Offset(cabL + 2 * d, cabT + 2 * d), Size(cabR - cabL - 4 * d, cabB - cabT - 4 * d), CornerRadius(12 * d), style = Stroke(1.dp.toPx()),
    )

    // The marquee: gold, with the name in dark ink and bulbs chasing round its edge.
    val mL = cabL + 8 * d
    val mR = cabR - 8 * d
    val mT = cabT + 8 * d
    val mH = 26 * d
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFE9A821)), mT, mT + mH), Offset(mL, mT), Size(mR - mL, mH), CornerRadius(7 * d))
    ArcadeFont.drawCentered(this, "TOKENS", w / 2f, mT + (mH - ArcadeFont.height(2.1f * d)) / 2f, 2.1f * d, Color(0xFF3A2204), shadow = false)
    val bulbs = 12
    val step = if (chase < 0f) -1 else (chase * 6f).toInt()
    for (i in 0 until bulbs) {
        val x = mL + 7 * d + (mR - mL - 14 * d) * i / (bulbs - 1)
        val lit = step < 0 || (i + step) % 3 != 0
        val c = if (lit) Color(0xFFFFFAE0) else Color(0xFFB37A12)
        drawCircle(c, 1.5f * d, Offset(x, mT + 3.5f * d))
        drawCircle(c, 1.5f * d, Offset(x, mT + mH - 3.5f * d))
    }

    // The display glass, its glow and a faint scan sheen; the count is laid over it.
    val lL = cabL + 12 * d
    val lR = cabR - 12 * d
    val lT = LCD_TOP.toPx()
    val lH = LCD_H.toPx()
    drawRoundRect(LCD, Offset(lL, lT), Size(lR - lL, lH), CornerRadius(8 * d))
    drawRoundRect(Color(0xFF3DF5FF).copy(alpha = 0.55f), Offset(lL, lT), Size(lR - lL, lH), CornerRadius(8 * d), style = Stroke(1.5f * d))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), lT, lT + lH * 0.5f),
        Offset(lL + 2 * d, lT + 2 * d), Size(lR - lL - 4 * d, lH * 0.45f), CornerRadius(6 * d),
    )

    // The coin slot: a lit frame round a dark slit, an arrow pointing into it, and two round buttons.
    val slotY = lT + lH + 10 * d
    val cx = w / 2f
    drawRoundRect(TRIM.shade(0.6f), Offset(cx - 24 * d, slotY - 3 * d), Size(48 * d, 16 * d), CornerRadius(5 * d))
    drawRoundRect(Color(0xFF0A0614), Offset(cx - 18 * d, slotY + 2 * d), Size(36 * d, 5 * d), CornerRadius(2.5f * d))
    drawRoundRect(Color(0xFFFFE08A).copy(alpha = 0.7f), Offset(cx - 18 * d, slotY + 2 * d), Size(36 * d, 5 * d), CornerRadius(2.5f * d), style = Stroke(1.dp.toPx()))
    for (side in floatArrayOf(-1f, 1f)) {
        val bc = Offset(cx + side * 40 * d, slotY + 5 * d)
        drawCircle(Color.Black.copy(alpha = 0.4f), 6 * d, bc + Offset(0f, 1.5f * d))
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFF7AB8), Color(0xFFB0206A)), bc.y - 6 * d, bc.y + 6 * d), 6 * d, bc)
        drawCircle(Color.White.copy(alpha = 0.4f), 6 * d, bc, style = Stroke(1.dp.toPx()))
    }

    // The tray: a dark hollow with three tokens resting in it.
    val tT = cabB - 27 * d
    val tL = cabL + 18 * d
    val tR = cabR - 18 * d
    drawRoundRect(Color(0xFF0A0614), Offset(tL, tT), Size(tR - tL, 20 * d), CornerRadius(8 * d))
    drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(tL, tT), Size(tR - tL, 20 * d), CornerRadius(8 * d), style = Stroke(1.dp.toPx()))
    val ty = tT + 12 * d
    drawToken(Offset(cx - 18 * d, ty + 1 * d), 8 * d, 0f)
    drawToken(Offset(cx + 16 * d, ty + 1 * d), 8 * d, 0f)
    drawToken(Offset(cx - 1 * d, ty - 2 * d), 9 * d, 0.6f)
}
