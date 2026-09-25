package com.pocketarcade.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dH %02dM".format(h, m) else "%d:%02d".format(m, s)
}

/**
 * The token machine: shows the daily refill countdown, trades tickets for tokens and, when the
 * player is completely out, coughs up a spare token every few minutes so play never stalls.
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

    ArcadePanel("TOKEN MACHINE", Color(Pal.GOLD), onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TokenIcon(56.dp)
            Spacer(Modifier.width(12.dp))
            ArcadeText("${save.tokens}", unit = 7.dp, color = Color(0xFFFFD35A))
        }
        Spacer(Modifier.height(6.dp))
        ArcadeText("TOKENS", unit = 3.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(16.dp))

        GlassBox(Modifier.fillMaxWidth()) {
            ArcadeText("DAILY BONUS", unit = 3.dp, color = Color(Pal.LIME))
            Spacer(Modifier.height(6.dp))
            ArcadeText("+${ArcadeRepository.DAILY_TOKENS} FREE TOKENS EVERY DAY", unit = 2.dp, tiny = true, color = Color.White)
            Spacer(Modifier.height(6.dp))
            ArcadeText("NEXT REFILL IN ${formatDuration(untilRefill)}", unit = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
        }
        Spacer(Modifier.height(12.dp))

        GlassBox(Modifier.fillMaxWidth()) {
            ArcadeText("TRADE TICKETS", unit = 3.dp, color = Color(Pal.ORANGE))
            Spacer(Modifier.height(6.dp))
            ArcadeText("YOU HAVE ${ArcadeFont.TICKET}${save.tickets}", unit = 2.dp, tiny = true, color = Color.White)
            Spacer(Modifier.height(8.dp))
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
                color = Color(Pal.ORANGE),
                enabled = save.tickets >= ArcadeRepository.TICKETS_PER_TOKEN,
                unit = 2.dp,
            )
        }

        if (save.tokens == 0) {
            Spacer(Modifier.height(12.dp))
            GlassBox(Modifier.fillMaxWidth()) {
                val ready = now >= save.spareTokenAt
                ArcadeText("OUT OF TOKENS?", unit = 3.dp, color = Color(Pal.CYAN))
                Spacer(Modifier.height(6.dp))
                if (ready) {
                    ArcadeText("SOMETHING SHINY UNDER\nTHE MACHINE...", unit = 2.dp, tiny = true, centered = true)
                    Spacer(Modifier.height(8.dp))
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
                        color = Color(Pal.CYAN),
                        textColor = Color(Pal.NAVY),
                        unit = 2.dp,
                    )
                } else {
                    ArcadeText("NEXT SPARE TOKEN IN ${formatDuration(save.spareTokenAt - now)}", unit = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
                }
            }
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ArcadeText(message, unit = 3.dp, color = Color(Pal.YELLOW))
        }
        Spacer(Modifier.height(8.dp))
    }
}
