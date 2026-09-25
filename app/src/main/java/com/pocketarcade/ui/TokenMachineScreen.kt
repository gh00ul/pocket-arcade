package com.pocketarcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.pocketarcade.engine.PixelFont
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
            PixelImage(UiIcons.token, 6.dp)
            Spacer(Modifier.width(12.dp))
            PixelText("${save.tokens}", pixel = 7.dp, color = Color(Pal.GOLD))
        }
        Spacer(Modifier.height(6.dp))
        PixelText("TOKENS", pixel = 3.dp, color = Color(Pal.YELLOW))
        Spacer(Modifier.height(16.dp))

        InfoBox {
            PixelText("DAILY BONUS", pixel = 3.dp, color = Color(Pal.LIME))
            Spacer(Modifier.height(6.dp))
            PixelText("+${ArcadeRepository.DAILY_TOKENS} FREE TOKENS EVERY DAY", pixel = 2.dp, tiny = true, color = Color.White)
            Spacer(Modifier.height(6.dp))
            PixelText("NEXT REFILL IN ${formatDuration(untilRefill)}", pixel = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
        }
        Spacer(Modifier.height(12.dp))

        InfoBox {
            PixelText("TRADE TICKETS", pixel = 3.dp, color = Color(Pal.ORANGE))
            Spacer(Modifier.height(6.dp))
            PixelText("YOU HAVE ${PixelFont.TICKET}${save.tickets}", pixel = 2.dp, tiny = true, color = Color.White)
            Spacer(Modifier.height(8.dp))
            ArcadeButton(
                "1 TOKEN FOR ${PixelFont.TICKET}${ArcadeRepository.TICKETS_PER_TOKEN}",
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
                pixel = 2.dp,
            )
        }

        if (save.tokens == 0) {
            Spacer(Modifier.height(12.dp))
            InfoBox {
                val ready = now >= save.spareTokenAt
                PixelText("OUT OF TOKENS?", pixel = 3.dp, color = Color(Pal.CYAN))
                Spacer(Modifier.height(6.dp))
                if (ready) {
                    PixelText("SOMETHING SHINY UNDER\nTHE MACHINE...", pixel = 2.dp, tiny = true, centered = true)
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
                        pixel = 2.dp,
                    )
                } else {
                    PixelText("NEXT SPARE TOKEN IN ${formatDuration(save.spareTokenAt - now)}", pixel = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
                }
            }
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PixelText(message, pixel = 3.dp, color = Color(Pal.YELLOW))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(Pal.DEEP))
            .border(2.dp, Color(Pal.PLUM))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
