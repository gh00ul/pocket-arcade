package com.pocketarcade.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.r3d.GameViewport
import com.pocketarcade.engine.easeOutBack
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.games.GameFx
import com.pocketarcade.games.MiniGame
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

private enum class HostPhase { INTRO, COUNTDOWN, PLAYING, ENDING, RESULTS, PAUSED }

private const val COUNT_STEP = 0.65f

/** Round state owned by the host; plain fields are read by the per-frame draw, not by composition. */
private class HostState(val game: MiniGame) {
    var phase by mutableStateOf(HostPhase.INTRO)
    var resumePhase = HostPhase.PLAYING
    var phaseT = 0f
    var timeLeft = game.roundSeconds
    var lastCountdown = -1
    var lastTick = -1
    var timeUpPlayed = false
    var endedEarly = false
    var resultScore = 0
    var resultTickets by mutableIntStateOf(0)
    var printed = 0
    var printAcc = 0f
    var printingDone by mutableStateOf(false)
    var newHigh = false
    var best = 0
    var goT = 99f
    var hostTime = 0f
    var celebrateT = 0f
    val particles = Particles(500)
    val shake = ScreenShake(maxOffset = 10f)
    var gx = 0f
    var gy = 0f
    var gs = 1f

    fun resetRound() {
        phaseT = 0f
        timeLeft = game.roundSeconds
        lastCountdown = -1
        lastTick = -1
        timeUpPlayed = false
        endedEarly = false
        printed = 0
        printAcc = 0f
        printingDone = false
        newHigh = false
        goT = 99f
        celebrateT = 0f
        particles.clear()
        shake.reset()
    }
}

/**
 * Hosts one mini-game: intro card, countdown, round clock, pause/quit, and the results screen
 * where tickets print out of the machine with a counter ticking up.
 */
@Composable
fun GameHostScreen(
    game: MiniGame,
    save: SaveState,
    services: ArcadeServices,
    appPaused: Boolean,
    onExit: (refundToken: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val exit by rememberUpdatedState(onExit)
    val audio = services.audio
    val haptics = services.haptics
    val fx = remember(game) {
        GameFx(audio, haptics) { id -> scope.launch { services.repo.addPrize(id) } }
    }
    val state = remember(game) {
        HostState(game).also {
            game.start(fx)
            it.best = save.highScore(game.id)
        }
    }

    fun startResults() {
        val score = game.score
        val total = game.ticketsFor(score) + game.bonusTickets
        state.resultScore = score
        state.resultTickets = total
        state.newHigh = score > state.best && score > 0
        state.printed = 0
        state.printAcc = 0f
        state.printingDone = total == 0
        state.phase = HostPhase.RESULTS
        state.phaseT = 0f
        scope.launch {
            services.repo.addTickets(total)
            services.repo.recordScore(game.id, score)
        }
        if (state.newHigh) {
            audio.play(Sfx.HIGHSCORE)
            audio.play(Sfx.CHEER, 0.7f)
            haptics.jackpot()
            state.particles.confetti(0f, 0f, GAME_W, 140, 6f)
            state.shake.add(0.5f)
        } else {
            audio.play(Sfx.WIN, 0.7f)
        }
    }

    fun pause() {
        if (state.phase == HostPhase.PLAYING || state.phase == HostPhase.COUNTDOWN || state.phase == HostPhase.ENDING) {
            state.resumePhase = state.phase
            state.phase = HostPhase.PAUSED
            audio.play(Sfx.SELECT, 0.6f, 0.8f)
        }
    }

    fun onExitPressed() {
        when (state.phase) {
            HostPhase.INTRO -> exit(true)
            HostPhase.RESULTS -> exit(false)
            HostPhase.PAUSED -> {
                state.phase = state.resumePhase
            }
            else -> pause()
        }
    }

    BackHandler { onExitPressed() }

    DisposableEffect(game) {
        onDispose { Gfx.remove(GameViewport.SLOT) }
    }

    LaunchedEffect(appPaused) {
        if (appPaused) pause()
    }

    val frame = rememberGameLoop(game) { dt ->
        state.hostTime += dt
        state.particles.update(dt)
        state.shake.update(dt)
        when (state.phase) {
            HostPhase.COUNTDOWN -> {
                state.phaseT += dt
                val step = (state.phaseT / COUNT_STEP).toInt()
                if (step != state.lastCountdown) {
                    state.lastCountdown = step
                    if (step < 3) audio.play(Sfx.COUNTDOWN) else {
                        audio.play(Sfx.GO)
                        haptics.tick()
                    }
                }
                if (state.phaseT >= COUNT_STEP * 3f) {
                    state.phase = HostPhase.PLAYING
                    state.goT = 0f
                }
            }
            HostPhase.PLAYING -> {
                state.goT += dt
                state.timeLeft = (state.timeLeft - dt).coerceAtLeast(0f)
                game.update(dt, state.timeLeft)
                val sec = ceil(state.timeLeft).toInt()
                if (state.timeLeft > 0f && sec <= 5 && sec != state.lastTick) {
                    state.lastTick = sec
                    audio.play(Sfx.COUNTDOWN, 0.5f, 1.5f)
                }
                if (state.timeLeft <= 0f && !state.timeUpPlayed) {
                    state.timeUpPlayed = true
                    audio.play(Sfx.BUZZER)
                    haptics.hit()
                }
                if (game.finished) {
                    state.endedEarly = state.timeLeft > 0f
                    if (state.endedEarly) audio.play(Sfx.BUZZER, 0.7f, 1.2f)
                    state.phase = HostPhase.ENDING
                    state.phaseT = 0f
                }
            }
            HostPhase.ENDING -> {
                state.phaseT += dt
                game.update(dt, 0f)
                if (state.phaseT > 1.2f) startResults()
            }
            HostPhase.RESULTS -> {
                state.phaseT += dt
                val total = state.resultTickets
                if (!state.printingDone && state.phaseT > 0.9f) {
                    val rate = maxOf(12f, total / 2.2f)
                    state.printAcc += dt * rate
                    while (state.printAcc >= 1f && state.printed < total) {
                        state.printAcc -= 1f
                        state.printed++
                        audio.play(Sfx.TICKET, 0.7f, 0.9f + (state.printed % 6) * 0.05f)
                        if (state.printed % 3 == 0) audio.play(Sfx.PRINT, 0.5f)
                        if (state.printed % 4 == 0) haptics.tick()
                    }
                    if (state.printed >= total) {
                        state.printingDone = true
                        state.printAcc = 0f
                        audio.play(Sfx.COIN, 0.8f, 0.8f)
                        haptics.hit()
                    }
                }
                if (state.newHigh) {
                    state.celebrateT += dt
                    if (state.celebrateT > 0.7f && state.phaseT < 5f) {
                        state.celebrateT = 0f
                        state.particles.confetti(0f, 0f, GAME_W, 30, 6f)
                    }
                }
            }
            else -> Unit
        }
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
    val bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat()

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(game) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val type = when {
                                    c.changedToDownIgnoreConsumed() -> TouchType.DOWN
                                    c.changedToUpIgnoreConsumed() -> TouchType.UP
                                    c.positionChanged() -> TouchType.MOVE
                                    else -> null
                                }
                                if (type != null) {
                                    val lx = (c.position.x - state.gx) / state.gs
                                    val ly = (c.position.y - state.gy) / state.gs
                                    when (state.phase) {
                                        HostPhase.PLAYING -> game.onTouch(type, c.id.value, lx, ly, c.uptimeMillis)
                                        HostPhase.RESULTS -> if (type == TouchType.DOWN && !state.printingDone && state.phaseT > 0.9f) {
                                            state.printed = state.resultTickets
                                        }
                                        else -> Unit
                                    }
                                }
                                c.consume()
                            }
                        }
                    }
                },
        ) {
            frame.value
            drawHost(this, state, game, topInset, bottomInset)
        }

        // Exit button.
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
        ) {
            RoundButton(UiIcon.CLOSE, { onExitPressed() }, Color(Pal.RED), size = 44.dp)
        }

        when (state.phase) {
            HostPhase.INTRO -> IntroCard(game, state.best) {
                audio.play(Sfx.SELECT)
                state.resetRound()
                state.phase = HostPhase.COUNTDOWN
            }
            HostPhase.PAUSED -> PauseCard(
                onResume = { state.phase = state.resumePhase; audio.play(Sfx.SELECT) },
                onQuit = { exit(false) },
            )
            HostPhase.RESULTS -> if (state.printingDone) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassBox { CurrencyRow(save.tokens, save.tickets) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ArcadeButton(
                            if (save.tokens > 0) "PLAY AGAIN\n1 TOKEN" else "NO TOKENS\nLEFT",
                            {
                                scope.launch {
                                    if (services.repo.spendToken()) {
                                        audio.play(Sfx.TOKEN)
                                        haptics.tick()
                                        state.best = maxOf(state.best, state.resultScore)
                                        game.start(fx)
                                        state.resetRound()
                                        state.phase = HostPhase.COUNTDOWN
                                    } else {
                                        audio.play(Sfx.ERROR)
                                    }
                                }
                            },
                            color = Color(Pal.GREEN),
                            enabled = save.tokens > 0,
                        )
                        ArcadeButton("EXIT", { exit(false) }, color = Color(Pal.PURPLE))
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun IntroCard(game: MiniGame, best: Int, onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xAA07050E))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        val glow = Color(game.look.glow)
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .shadow(24.dp, shape, ambientColor = glow, spotColor = glow)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(UiColors.cardTop, UiColors.cardBottom)))
                .border(2.dp, Brush.verticalGradient(listOf(glow, glow.shade(0.45f))), shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArcadeText(game.title, unit = 4.dp, color = Color(game.look.glow))
            Spacer(Modifier.height(16.dp))
            for (line in game.instructions) {
                ArcadeText(line, unit = 2.dp, color = Color.White, centered = true)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            ArcadeText("${game.roundSeconds.toInt()} SECOND ROUND", unit = 2.dp, color = Color(Pal.LAVENDER))
            Spacer(Modifier.height(6.dp))
            ArcadeText("BEST: $best", unit = 3.dp, color = Color(Pal.YELLOW))
            Spacer(Modifier.height(18.dp))
            ArcadeButton("${ArcadeFont.PLAY} START", onStart, color = Color(Pal.GREEN), unit = 4.dp)
        }
    }
}

@Composable
private fun PauseCard(onResume: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC08060F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcadeText("PAUSED", unit = 6.dp, color = Color(Pal.YELLOW))
            Spacer(Modifier.height(24.dp))
            ArcadeButton("${ArcadeFont.PLAY} RESUME", onResume, color = Color(Pal.GREEN), unit = 4.dp)
            Spacer(Modifier.height(16.dp))
            ArcadeButton("QUIT ROUND", onQuit, color = Color(Pal.RED), unit = 3.dp)
            Spacer(Modifier.height(10.dp))
            ArcadeText("QUITTING FORFEITS THIS ROUND", unit = 2.dp, tiny = true, color = Color(Pal.GRAY))
            Spacer(Modifier.width(1.dp))
        }
    }
}

// ---------------------------------------------------------------- per-frame drawing

private fun drawHost(scope: DrawScope, state: HostState, game: MiniGame, topInset: Float, bottomInset: Float) {
    with(scope) {
        val w = size.width
        val h = size.height
        val unit = floor(2.dp.toPx()).coerceAtLeast(2f)
        val barH = topInset + 42f * unit
        val availH = h - barH - bottomInset - 6f * unit
        val gs = minOf(w / GAME_W, availH / GAME_H)
        val gw = GAME_W * gs
        val gh = GAME_H * gs
        val gx = (w - gw) / 2f
        val gy = barH + ((availH - gh) / 2f).coerceAtLeast(0f)
        state.gx = gx
        state.gy = gy
        state.gs = gs
        val look = game.look
        val t = state.hostTime

        // Cabinet bezel around the field (the field itself shows the GPU picture underneath).
        val bezel = Color(Pal.shade(look.body, 0.28f))
        drawRect(bezel, Offset.Zero, Size(w, gy))
        drawRect(bezel, Offset(0f, gy + gh), Size(w, h - gy - gh))
        drawRect(bezel, Offset(0f, gy), Size(gx, gh))
        drawRect(bezel, Offset(gx + gw, gy), Size(w - gx - gw, gh))
        GameViewport.x = gx + state.shake.offsetX * gs
        GameViewport.y = gy + state.shake.offsetY * gs
        GameViewport.scale = gs
        GameViewport.clipX0 = gx.toInt()
        GameViewport.clipY0 = gy.toInt()
        GameViewport.clipX1 = (gx + gw).toInt()
        GameViewport.clipY1 = (gy + gh).toInt()
        for (i in 0 until 20) {
            val y = barH + i * (h - barH) / 20f
            val on = ((t * 5f).toInt() + i) % 4 == 0
            val c = Color(if (on) Pal.YELLOW else Pal.shade(look.trim, 0.4f))
            if (gx > unit * 6f) {
                drawCircle(c, unit * 2f, Offset(gx / 2f, y + unit * 6f))
                drawCircle(c, unit * 2f, Offset(w - gx / 2f, y + unit * 6f))
            }
        }

        // The game itself, clipped to its screen.
        clipRect(gx, gy, gx + gw, gy + gh) {
            withTransform({
                translate(gx + state.shake.offsetX * gs, gy + state.shake.offsetY * gs)
                scale(gs, gs, Offset.Zero)
            }) {
                game.draw(this)
                drawOverlays(this, state, game)
                state.particles.draw(this)
            }
        }
        drawRect(Color(look.trim), Offset(gx - unit * 2f, gy - unit * 2f), Size(gw + unit * 4f, gh + unit * 4f), style = Stroke(unit * 2f))

        // Top bar: score, time, best.
        drawRect(Color(Pal.NIGHT), Offset.Zero, Size(w, barH))
        drawRect(Color(look.trim), Offset(0f, barH - unit * 2f), Size(w, unit * 2f))
        drawRect(Color(look.glow), Offset(0f, barH), Size(w, unit * 3f), alpha = 0.25f)
        val labelY = topInset + unit * 6f
        val valueY = labelY + unit * 10f
        val scoreX = w * 0.40f
        ArcadeFont.drawCentered(this, "SCORE", scoreX, labelY, unit, Color(Pal.LAVENDER))
        ArcadeFont.drawCentered(this, game.score.toString(), scoreX, valueY, unit * 2.5f, Color.White)
        val timeX = w * 0.66f
        val secs = ceil(state.timeLeft).toInt()
        val low = secs <= 10 && state.phase == HostPhase.PLAYING
        val timeAlpha = if (low && (t * 4f).toInt() % 2 == 0) 0.4f else 1f
        ArcadeFont.drawCentered(this, "TIME", timeX, labelY, unit, Color(Pal.LAVENDER))
        ArcadeFont.drawCentered(this, secs.toString(), timeX, valueY, unit * 2.5f, Color(if (low) Pal.RED else Pal.CYAN), timeAlpha)
        val bestX = w * 0.88f
        val liveBest = maxOf(state.best, if (state.phase == HostPhase.RESULTS) state.resultScore else 0)
        ArcadeFont.drawCentered(this, "BEST", bestX, labelY, unit, Color(Pal.LAVENDER))
        ArcadeFont.drawCentered(this, liveBest.toString(), bestX, valueY + unit * 2f, unit * 1.5f, Color(Pal.YELLOW))
        // Time bar.
        val frac = clamp01(state.timeLeft / game.roundSeconds)
        drawRect(Color(Pal.DEEP), Offset(w * 0.27f, barH - unit * 6f), Size(w * 0.7f, unit * 2f))
        drawRect(Color(if (low) Pal.RED else look.glow), Offset(w * 0.27f, barH - unit * 6f), Size(w * 0.7f * frac, unit * 2f))
    }
}

/** Countdown, time-up banner and the results screen, drawn in game units over the field. */
private fun drawOverlays(scope: DrawScope, state: HostState, game: MiniGame) {
    with(scope) {
        val cx = GAME_W / 2f
        when (state.phase) {
            HostPhase.COUNTDOWN -> {
                val step = (state.phaseT / COUNT_STEP).toInt().coerceIn(0, 2)
                val frac = (state.phaseT % COUNT_STEP) / COUNT_STEP
                val pop = easeOutBack(clamp01(frac / 0.35f))
                drawRect(Color.Black, Offset(0f, 0f), Size(GAME_W, GAME_H), alpha = 0.35f)
                val label = (3 - step).toString()
                ArcadeFont.drawCentered(this, label, cx, 250f - 28f * pop, 16f * pop, Color(Pal.YELLOW), 1f - frac * 0.3f)
                ArcadeFont.drawCentered(this, "GET READY", cx, 380f, 3f, Color.White)
            }
            HostPhase.PLAYING -> if (state.goT < 0.7f) {
                val a = 1f - state.goT / 0.7f
                val s = 12f + state.goT * 10f
                ArcadeFont.drawCentered(this, "GO!", cx, 260f - s * 3.5f, s, Color(Pal.LIME), a)
            }
            HostPhase.ENDING -> {
                val pop = easeOutBack(clamp01(state.phaseT / 0.3f))
                drawRect(Color.Black, Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.4f * clamp01(state.phaseT / 0.3f))
                ArcadeFont.drawCentered(this, if (state.endedEarly) "ALL DONE!" else "TIME'S UP!", cx, 280f, 6f * pop, Color(Pal.YELLOW))
            }
            HostPhase.RESULTS -> drawResults(this, state, game)
            else -> Unit
        }
    }
}

private fun drawResults(scope: DrawScope, state: HostState, game: MiniGame) {
    with(scope) {
        val cx = GAME_W / 2f
        val t = state.phaseT
        val look = game.look
        drawRect(Color.Black, Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.7f)
        val slide = easeOutBack(clamp01(t / 0.45f))
        val top = 36f - (1f - slide) * 300f
        drawRoundRect(Color(Pal.NIGHT), Offset(24f, top), Size(312f, 232f), CornerRadius(10f, 10f))
        drawRoundRect(Color(look.trim), Offset(24f, top), Size(312f, 232f), CornerRadius(10f, 10f), style = Stroke(4f))
        ArcadeFont.drawCentered(this, "ROUND OVER", cx, top + 14f, 3f, Color(look.glow))
        ArcadeFont.drawCentered(this, "SCORE", cx, top + 48f, 2f, Color(Pal.LAVENDER))
        val shown = (state.resultScore * clamp01(t / 0.7f)).toInt()
        ArcadeFont.drawCentered(this, shown.toString(), cx, top + 66f, 6f, Color.White)
        if (state.newHigh) {
            val rainbow = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE)
            val c = rainbow[((state.hostTime * 10f).toInt()) % rainbow.size]
            val pulse = 1f + 0.08f * sin(state.hostTime * 10f)
            ArcadeFont.drawCentered(this, "${ArcadeFont.STAR} NEW HIGH SCORE! ${ArcadeFont.STAR}", cx, top + 124f, 2.5f * pulse, Color(c))
        } else {
            ArcadeFont.drawCentered(this, "BEST ${state.best}", cx, top + 124f, 2f, Color(Pal.GRAY))
        }
        ArcadeFont.drawCentered(this, "TICKETS", cx, top + 156f, 2f, Color(Pal.LAVENDER))
        ArcadeFont.drawCentered(this, "${ArcadeFont.TICKET} ${state.printed}", cx, top + 174f, 5f, Color(Pal.ORANGE))
        if (game.bonusTickets > 0) {
            ArcadeFont.drawCentered(this, "INCLUDES +${game.bonusTickets} BONUS", cx, top + 214f, 2f, Color(Pal.YELLOW))
        }

        // Ticket printer with the strip feeding out of it.
        val slotY = 300f
        val segH = 24f
        val segW = 60f
        val sx = cx - segW / 2f
        val printing = !state.printingDone && state.printed < state.resultTickets && t > 0.9f
        val frac = if (printing) state.printAcc.coerceIn(0f, 1f) else 0f
        val count = state.printed + if (printing) 1 else 0
        val maxVisible = 16
        clipRect(0f, slotY, GAME_W, GAME_H) {
            for (k in 0 until minOf(count, maxVisible)) {
                val index = if (printing) k else k + 1
                val y = slotY + (frac + index - 1f) * segH
                if (y > GAME_H) break
                val wobble = sin(k * 0.9f + state.hostTime * 2f) * (k * 0.4f)
                ticket(this, sx + wobble, y, segW, segH)
            }
        }
        drawRoundRect(Color(Pal.DARKGRAY), Offset(cx - 80f, slotY - 26f), Size(160f, 30f), CornerRadius(6f, 6f))
        drawRect(Color(Pal.BLACK), Offset(cx - 38f, slotY - 4f), Size(76f, 6f))
        val lampOn = printing && (state.hostTime * 10f).toInt() % 2 == 0
        drawCircle(Color(if (lampOn) Pal.LIME else Pal.DARKGREEN), 5f, Offset(cx + 64f, slotY - 12f))
        ArcadeFont.drawCentered(this, "TICKETS", cx - 10f, slotY - 20f, 2f, Color(Pal.LIGHTGRAY), shadow = false)
        if (printing) {
            ArcadeFont.drawCentered(this, "TAP TO SKIP", cx, 610f, 2f, Color.White, 0.5f + 0.5f * sin(state.hostTime * 6f))
        }
    }
}

private fun ticket(scope: DrawScope, x: Float, y: Float, w: Float, h: Float) {
    with(scope) {
        drawRect(Color(Pal.ORANGE), Offset(x, y), Size(w, h - 2f))
        drawRect(Color(Pal.GOLD), Offset(x + 4f, y + 3f), Size(w - 8f, h - 8f), style = Stroke(2f))
        drawRect(Color(Pal.NIGHT), Offset(x - 2f, y + h / 2f - 4f), Size(5f, 6f))
        drawRect(Color(Pal.NIGHT), Offset(x + w - 3f, y + h / 2f - 4f), Size(5f, 6f))
        for (i in 0 until 6) drawRect(Color(Pal.shade(Pal.ORANGE, 0.6f)), Offset(x + 3f + i * 10f, y + h - 3f), Size(5f, 2f))
        ArcadeFont.drawCentered(this, "${ArcadeFont.STAR}", x + w / 2f, y + 6f, 1.6f, Color(Pal.DARKRED), shadow = false)
    }
}
