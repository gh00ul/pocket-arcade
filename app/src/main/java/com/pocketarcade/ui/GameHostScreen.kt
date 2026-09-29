package com.pocketarcade.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.SaveState
import com.pocketarcade.data.TokenGate
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Flash
import com.pocketarcade.engine.easeOutCubic
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.SimClock
import com.pocketarcade.engine.TimeScale
import com.pocketarcade.engine.TiltControlled
import com.pocketarcade.engine.TiltSteer
import com.pocketarcade.engine.TouchType
import com.pocketarcade.engine.audio.RoundMusic
import com.pocketarcade.engine.audio.Stinger
import com.pocketarcade.engine.thumbZoneGestureExclusion
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

/** Countdown numerals: 3, 2, 1 warm up from pink to yellow, and GO! is lime. */
private val COUNT_COLORS = intArrayOf(Pal.PINK, Pal.ORANGE, Pal.YELLOW)

/** Light camera kicks on each countdown number and a firmer one on GO!. */
private const val COUNT_PUNCH = 0.12f
private const val GO_PUNCH = 0.35f

/** The colours the NEW HIGH SCORE banner cycles through. */
private val RAINBOW = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE)

/** A tap on the results this long after they begin skips the reveal (not sooner: a last fast tap of the round must not). */
private const val SKIP_AFTER = 0.6f

/** The NEW HIGH SCORE beat: a freeze on the slam, then slow motion while the confetti hangs in the air. */
private const val NEW_HIGH_STOP = 0.07f
private const val NEW_HIGH_SLOW = 0.35f
private const val NEW_HIGH_SLOW_SECONDS = 0.5f
private const val NEW_HIGH_PUNCH = 0.9f
private const val NEW_HIGH_FLASH = 0.6f

/** The results card, in field units. */
private const val CARD_X = 24f
private const val CARD_W = 312f
private const val CARD_TOP = 28f
private const val CARD_H = 276f

/** Where the grade badge sits on the card, from the card's top and the field's middle. */
private const val BADGE_DX = -78f
private const val BADGE_DY = 204f
private const val BADGE_R = 30f

/** Simulation steps of ENDING every round gets (1.2 s at 120 Hz), however slow the last beat was. */
private const val ENDING_STEPS = 144

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
    var grade = Grade.C
    /** The ticket count when the round ended, so the counter can climb as each ticket lands. */
    var ticketsBefore = 0
    /** Tickets that have flown into the counter so far. */
    var landed by mutableIntStateOf(0)
    /** Tickets sent flying so far. */
    var launched = 0
    var chunk = 1
    /** How far the staged reveal has got: see [ResultsPlan.stageAt]. */
    var revealStage by mutableIntStateOf(0)
    var countTicks = 0
    /** The player tapped through the reveal: its time control and shakes stay quiet. */
    var skipped = false
    /** A white flash over the field for the biggest moments (off with reduce motion). */
    val flash = Flash(decayPerSec = 2.4f)
    /** Tickets arcing from the printer into the counter. */
    val fly = CurrencyFx()
    /** Hit-stops and slow-mo beats the game asks for, and the steps they leave it. */
    val time = TimeScale()
    val sim = SimClock()
    /** Game steps taken in ENDING, so a slow last beat can't shorten the settle the payout depends on. */
    var endSteps = 0
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
        endSteps = 0
        grade = Grade.C
        landed = 0
        launched = 0
        chunk = 1
        revealStage = 0
        countTicks = 0
        skipped = false
        flash.reset()
        fly.clear()
        time.reset()
        sim.reset()
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
    // A prize won at the very end of a round is saved even if the player leaves at once.
    val fx = remember(game) {
        GameFx(audio, haptics) { id -> services.persist { services.repo.addPrize(id) } }
    }
    // Held from the PLAY AGAIN tap until its token is spent (or refused), so a quick double tap
    // can't spend two tokens for one restart.
    val playAgainGate = remember(game) { TokenGate() }
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
        state.grade = ResultsPlan.grade(score, state.best, total)
        state.ticketsBefore = save.tickets
        state.landed = 0
        state.launched = 0
        state.chunk = ResultsPlan.flightChunk(total)
        state.revealStage = 0
        state.countTicks = 0
        state.skipped = false
        state.printed = 0
        state.printAcc = 0f
        state.printingDone = total == 0
        state.phase = HostPhase.RESULTS
        state.phaseT = 0f
        audio.music.stinger(if (state.newHigh) Stinger.HIGH_SCORE else Stinger.RESULTS)
        // The payout is saved even if the player leaves before the write returns.
        services.persist {
            services.repo.addTickets(total)
            services.repo.recordScore(game.id, score)
            // The profile's per-machine "played" counts, and lifetime tickets for later goals.
            services.repo.addStat("plays:${game.id}")
            services.repo.addStat("tickets:earned", total.toLong())
        }
        // The card sweeping in; the fanfares wait for their place in the reveal.
        audio.play(Sfx.WHOOSH, 0.45f, 1.25f)
    }

    /** The best line lands: a plain BEST, or the NEW HIGH SCORE slam with its freeze, slow motion, confetti and camera kick. */
    fun revealBest() {
        if (state.newHigh) {
            audio.play(Sfx.HIGHSCORE)
            audio.play(Sfx.CHEER, 0.7f)
            haptics.jackpot()
            state.particles.confetti(0f, 0f, GAME_W, 140, 6f)
            state.shake.add(0.5f)
            if (!state.skipped) {
                state.time.hitStop(NEW_HIGH_STOP)
                state.time.slowMo(NEW_HIGH_SLOW, NEW_HIGH_SLOW_SECONDS)
                GameViewport.requestPunch(NEW_HIGH_PUNCH)
                state.flash.trigger(NEW_HIGH_FLASH)
            }
        } else {
            audio.play(Sfx.WIN, 0.7f)
        }
    }

    /** The grade is stamped: a thud that grows with the grade, and a spray of sparks for an S. */
    fun stampGrade() {
        val g = state.grade
        audio.play(Sfx.THUD, 0.9f, if (g == Grade.S) 1.25f else if (g == Grade.A) 1.1f else 0.95f)
        if (g == Grade.S || g == Grade.A) haptics.hit() else haptics.tick()
        state.shake.add(if (g == Grade.S) 0.35f else if (g == Grade.A) 0.25f else 0.12f)
        if (g == Grade.S) {
            audio.play(Sfx.COIN, 0.7f, 1.4f)
            state.particles.burst(
                GAME_W / 2f + BADGE_DX, CARD_TOP + BADGE_DY, 30, 50f, 190f,
                intArrayOf(Pal.GOLD, Pal.YELLOW, Pal.WHITE), lifetime = 0.8f, sz = 3f, dragPerSec = 2.4f, kind = Particles.SPARKLE,
            )
        }
        if (!state.skipped && g != Grade.C) {
            state.time.hitStop(if (g == Grade.S) 0.07f else 0.04f)
            GameViewport.requestPunch(if (g == Grade.S) 0.5f else 0.25f)
        }
    }

    /** Sends [n] tickets flying from the printer's slot to the counter, [delay] seconds from now; the counter climbs as they land. */
    fun flyTickets(n: Int, delay: Float) {
        val from = Offset(state.gx + GAME_W / 2f * state.gs, state.gy + (ResultsPlan.SLOT_Y - 4f) * state.gs)
        state.fly.launch(CurrencyFx.Kind.TICKET, from, state.fly.ticketAnchor, delay = delay, duration = 0.62f) {
            state.landed += n
            audio.play(Sfx.CLINK, 0.22f, 1.2f + 0.02f * (state.landed % 10))
        }
    }

    fun pause() {
        if (state.phase == HostPhase.PLAYING || state.phase == HostPhase.COUNTDOWN || state.phase == HostPhase.ENDING) {
            state.resumePhase = state.phase
            state.phase = HostPhase.PAUSED
            // Touches stop reaching the game here, so a finger lifted while paused never sends its UP.
            game.cancelInput()
            audio.play(Sfx.SELECT, 0.6f, 0.8f)
        }
    }

    /** Leaves from the results screen, unless PLAY AGAIN is still spending its token (it would be lost). */
    fun leaveResults() {
        if (!playAgainGate.claimed) exit(false)
    }

    fun onExitPressed() {
        when (state.phase) {
            HostPhase.INTRO -> exit(true)
            HostPhase.RESULTS -> leaveResults()
            HostPhase.PAUSED -> {
                state.phase = state.resumePhase
            }
            else -> pause()
        }
    }

    BackHandler { onExitPressed() }

    // Tilt steering (the racer, and only while its option is on): the sensor is listened to only
    // while this round is on screen and the app is in front.
    val tiltGame = game as? TiltControlled
    val tiltOn = tiltGame?.tiltSteering == true
    val context = LocalContext.current
    DisposableEffect(game, tiltOn, appPaused) {
        val tilt = if (tiltGame != null && tiltOn && !appPaused) TiltSteer(context).also { it.start(tiltGame) } else null
        onDispose { tilt?.stop() }
    }

    DisposableEffect(game) {
        onDispose {
            Gfx.remove(GameViewport.SLOT)
            GameViewport.takePunch()
        }
    }

    LaunchedEffect(appPaused) {
        if (appPaused) pause()
    }

    // The soundtrack follows the round: this machine's theme, sat back under the intro card and the
    // pause menu, then the results' resolving loop (see RoundMusic for how it heats up).
    LaunchedEffect(state.phase) {
        when (state.phase) {
            HostPhase.INTRO -> RoundMusic.intro(audio, game.id)
            HostPhase.COUNTDOWN -> RoundMusic.countdown(audio, game.id)
            HostPhase.RESULTS -> RoundMusic.results(audio)
            HostPhase.PAUSED -> RoundMusic.pause(audio)
            else -> RoundMusic.play(audio)
        }
    }
    DisposableEffect(game) { onDispose { RoundMusic.play(audio) } }

    val frame = rememberGameLoop(game) { dt ->
        state.hostTime += dt
        // The results run on the time-scaled clock too, so the high-score beat that stretches the
        // reveal also stretches its confetti; everything else here is real time.
        val vdt = if (state.phase == HostPhase.RESULTS) state.time.update(dt) else dt
        state.particles.update(vdt)
        state.shake.update(vdt)
        state.flash.update(dt)
        // Time control lives here and only here: the game asks (GameFx), the clock decides how many
        // of these real steps it gets, and each one it gets is still exactly FIXED_DT. Only the round
        // itself is ever slowed: intro, countdown, pause and results run in real time.
        val playing = state.phase == HostPhase.PLAYING
        val punch = fx.flush(if (playing) state.time else null)
        // The camera kick isn't time control: it plays in real time, even through a freeze.
        if (punch > 0f) GameViewport.requestPunch(punch)
        val stepGame = (playing || state.phase == HostPhase.ENDING) && state.sim.advance(state.time.update(dt))
        when (state.phase) {
            HostPhase.COUNTDOWN -> {
                state.phaseT += dt
                val step = (state.phaseT / COUNT_STEP).toInt()
                if (step != state.lastCountdown) {
                    state.lastCountdown = step
                    audio.music.stinger(if (step < 3) Stinger.COUNTDOWN else Stinger.GO)
                    if (step < 3) {
                        audio.play(Sfx.COUNTDOWN)
                        GameViewport.requestPunch(COUNT_PUNCH)
                    } else {
                        audio.play(Sfx.GO)
                        haptics.tick()
                        GameViewport.requestPunch(GO_PUNCH)
                    }
                }
                if (state.phaseT >= COUNT_STEP * 3f) {
                    state.phase = HostPhase.PLAYING
                    state.goT = 0f
                }
            }
            HostPhase.PLAYING -> {
                state.goT += dt
                if (stepGame) {
                    // The round clock is game time: it slows and freezes with the game.
                    state.timeLeft = (state.timeLeft - FIXED_DT).coerceAtLeast(0f)
                    game.update(FIXED_DT, state.timeLeft)
                }
                audio.music.setIntensity(RoundMusic.intensity(state.timeLeft, game.roundSeconds))
                val sec = ceil(state.timeLeft).toInt()
                if (state.timeLeft > 0f && sec <= 5 && sec != state.lastTick) {
                    state.lastTick = sec
                    audio.play(Sfx.COUNTDOWN, 0.5f, 1.5f)
                }
                if (state.timeLeft <= 0f && !state.timeUpPlayed) {
                    state.timeUpPlayed = true
                    audio.music.stinger(Stinger.TIME_UP)
                    audio.play(Sfx.BUZZER)
                    haptics.hit()
                }
                if (game.finished) {
                    state.endedEarly = state.timeLeft > 0f
                    if (state.endedEarly) audio.play(Sfx.BUZZER, 0.7f, 1.2f)
                    game.cancelInput()
                    state.phase = HostPhase.ENDING
                    state.phaseT = 0f
                    state.endSteps = 0
                }
            }
            HostPhase.ENDING -> {
                state.phaseT += dt
                if (stepGame) {
                    game.update(FIXED_DT, 0f)
                    state.endSteps++
                }
                if (state.phaseT > 1.2f && state.endSteps >= ENDING_STEPS) startResults()
            }
            HostPhase.RESULTS -> {
                state.phaseT += vdt
                val t = state.phaseT
                val total = state.resultTickets
                // The staged reveal: score counts up, the best line lands, the grade is stamped, tickets print.
                if (state.revealStage < 1 && t >= ResultsPlan.SCORE_AT) state.revealStage = 1
                if (state.revealStage == 1 && t < ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS) {
                    val ticks = ((t - ResultsPlan.SCORE_AT) / ResultsPlan.COUNT_TICK_SECONDS).toInt()
                    if (ticks > state.countTicks) {
                        state.countTicks = ticks
                        val climb = clamp01((t - ResultsPlan.SCORE_AT) / ResultsPlan.SCORE_SECONDS)
                        audio.play(Sfx.BLIP, 0.2f, 0.9f + 0.9f * climb)
                    }
                }
                if (state.revealStage < 2 && t >= ResultsPlan.BEST_AT) {
                    state.revealStage = 2
                    revealBest()
                }
                if (state.revealStage < 3 && t >= ResultsPlan.GRADE_AT) {
                    state.revealStage = 3
                    stampGrade()
                }
                if (state.revealStage < 4 && t >= ResultsPlan.PRINT_AT) state.revealStage = 4

                if (!state.printingDone && state.revealStage >= 4) {
                    val rate = maxOf(12f, total / 2.2f)
                    state.printAcc += vdt * rate
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
                // Printed tickets fly into the counter, one sprite per chunk and the remainder once
                // printing is done; after a skip they go up in a quick stagger.
                if (state.revealStage >= 4) {
                    var k = 0
                    while (state.printed - state.launched >= state.chunk) {
                        state.launched += state.chunk
                        flyTickets(state.chunk, 0.035f * k++)
                    }
                    if (state.printingDone && state.launched < total) {
                        val rest = total - state.launched
                        state.launched = total
                        flyTickets(rest, 0.035f * k)
                    }
                }
                if (state.newHigh) {
                    state.celebrateT += vdt
                    if (state.celebrateT > 0.7f && state.phaseT < 5f && state.revealStage >= 2) {
                        state.celebrateT = 0f
                        state.particles.confetti(0f, 0f, GAME_W, 30, 6f)
                    }
                }
            }
            else -> Unit
        }
    }

    val density = LocalDensity.current
    val view = LocalView.current
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
    val bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat()

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Thumbs rest low on both sides (pinball flippers, racer steering): no Back swipes there.
                .thumbZoneGestureExclusion(density)
                .pointerInput(game) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            for (i in 0 until changes.size) {
                                val c = changes[i]
                                val type = when {
                                    c.changedToDownIgnoreConsumed() -> TouchType.DOWN
                                    c.changedToUpIgnoreConsumed() -> TouchType.UP
                                    c.positionChanged() -> TouchType.MOVE
                                    else -> null
                                }
                                if (type != null) {
                                    when (state.phase) {
                                        HostPhase.PLAYING -> {
                                            if (type == TouchType.DOWN) {
                                                // Deliver this touch stream as it arrives, not batched to the
                                                // frame (a no-op unless the event is the first finger's DOWN).
                                                val raw = event.motionEvent
                                                if (raw != null) view.requestUnbufferedDispatch(raw)
                                            } else if (type == TouchType.MOVE) {
                                                // Compose batches the samples between two frames: hand the
                                                // game every one, oldest first, each at its own time, so a
                                                // flick's speed is measured on all of them, not one per frame.
                                                val past = c.historical
                                                for (h in 0 until past.size) {
                                                    val s = past[h]
                                                    game.onTouch(
                                                        TouchType.MOVE, c.id.value,
                                                        (s.position.x - state.gx) / state.gs,
                                                        (s.position.y - state.gy) / state.gs,
                                                        s.uptimeMillis,
                                                    )
                                                }
                                            }
                                            game.onTouch(
                                                type, c.id.value,
                                                (c.position.x - state.gx) / state.gs,
                                                (c.position.y - state.gy) / state.gs,
                                                c.uptimeMillis,
                                            )
                                        }
                                        HostPhase.RESULTS -> if (type == TouchType.DOWN && state.phaseT > SKIP_AFTER) {
                                            if (state.revealStage < 4) {
                                                // A tap jumps the reveal to the printing.
                                                state.skipped = true
                                                state.phaseT = maxOf(state.phaseT, ResultsPlan.PRINT_AT)
                                            } else if (!state.printingDone) {
                                                state.printed = state.resultTickets
                                            }
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
            HostPhase.RESULTS -> if (state.revealStage >= 4) {
                ResultsControls(
                    state = state,
                    save = save,
                    playAgainLabel = if (save.tokens > 0) "PLAY AGAIN\n1 TOKEN" else "NO TOKENS\nLEFT",
                    canPlayAgain = save.tokens > 0,
                    onPlayAgain = {
                        // Claimed on the tap, before the suspending spend (like enterMachine).
                        if (playAgainGate.tryClaim()) {
                            scope.launch {
                                try {
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
                                } finally {
                                    playAgainGate.release()
                                }
                            }
                        }
                    },
                    onExit = { leaveResults() },
                    onTick = { audio.play(Sfx.BLIP, 0.14f, 1.7f) },
                )
            }
            else -> Unit
        }

        // Tickets in the air between the printer and the counter.
        CurrencyFxLayer(state.fly)

        // Exit button, last so it sits above the cards: their backdrops swallow every other tap.
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
        ) {
            RoundButton(UiIcon.CLOSE, { onExitPressed() }, Color(Pal.RED), size = 48.dp, label = "Close")
        }
    }
}

/** The buttons under the results card: the currency counter (climbing as tickets land), then PLAY AGAIN and EXIT popping in when printing is done. */
@Composable
private fun BoxScope.ResultsControls(
    state: HostState,
    save: SaveState,
    playAgainLabel: String,
    canPlayAgain: Boolean,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    onTick: () -> Unit,
) {
    val entrance = rememberEntrance(520)
    val ready = state.printingDone
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassBox(Modifier.enterStage(entrance, 0f, 0.6f, rise = 18.dp)) {
            CurrencyRow(
                save.tokens, state.ticketsBefore + state.landed,
                onTick = onTick,
                ticketIconModifier = Modifier.onGloballyPositioned {
                    state.fly.ticketAnchor = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        // Always laid out, so the counter above never jumps up when the buttons arrive.
        Row(Modifier.reserveSpace(ready), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArcadeButton(
                playAgainLabel, onPlayAgain,
                modifier = Modifier.popIn(0, ready),
                color = Color(Pal.GREEN),
                enabled = canPlayAgain,
            )
            ArcadeButton("EXIT", onExit, modifier = Modifier.popIn(90, ready), color = Color(Pal.PURPLE))
        }
    }
}

@Composable
private fun IntroCard(game: MiniGame, best: Int, onStart: () -> Unit) {
    // One linear progress, and every piece of the card takes its own slice of it.
    val entrance = rememberEntrance(820)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0xAA07050E), alpha = clamp01(entrance.value * 4f)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        val glow = Color(game.look.glow)
        // The card's inside: the screen less its margin and padding (20 dp each, both sides).
        val fit = (LocalConfiguration.current.screenWidthDp - 80).coerceAtLeast(120).dp
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val k = easeOutBack(stageOf(entrance.value, 0f, 0.55f))
                    alpha = clamp01(entrance.value * 5f)
                    if (UiMotion.enabled) {
                        val s = 0.86f + 0.14f * k
                        scaleX = s
                        scaleY = s
                        translationY = (1f - k) * 40.dp.toPx()
                    }
                }
                .shadow(24.dp, shape, ambientColor = glow, spotColor = glow)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(UiColors.cardTop, UiColors.cardBottom)))
                .border(2.dp, Brush.verticalGradient(listOf(glow, glow.shade(0.45f))), shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArcadeText(game.title, Modifier.enterStage(entrance, 0.12f, 0.5f, pop = true), unit = 4.dp, color = Color(game.look.glow), maxWidth = fit)
            Spacer(Modifier.height(16.dp))
            game.instructions.forEachIndexed { i, line ->
                ArcadeText(
                    line, Modifier.enterStage(entrance, 0.24f + 0.07f * i, 0.58f + 0.07f * i, rise = 12.dp),
                    unit = 2.dp, color = Color.White, centered = true, maxWidth = fit,
                )
                Spacer(Modifier.height(8.dp))
            }
            val tail = 0.32f + 0.07f * game.instructions.size
            Spacer(Modifier.height(8.dp))
            ArcadeText("${game.roundSeconds.toInt()} SECOND ROUND", Modifier.enterStage(entrance, tail, tail + 0.3f, rise = 10.dp), unit = 2.dp, color = Color(Pal.LAVENDER))
            Spacer(Modifier.height(6.dp))
            ArcadeText("BEST: $best", Modifier.enterStage(entrance, tail + 0.05f, tail + 0.35f, rise = 10.dp), unit = 3.dp, color = Color(Pal.YELLOW))
            Spacer(Modifier.height(18.dp))
            ArcadeButton(
                "${ArcadeFont.PLAY} START", onStart,
                modifier = Modifier.enterStage(entrance, tail + 0.12f, tail + 0.45f, rise = 16.dp, pop = true),
                color = Color(Pal.GREEN), unit = 4.dp,
            )
        }
    }
}

@Composable
private fun PauseCard(onResume: () -> Unit, onQuit: () -> Unit) {
    val entrance = rememberEntrance(520)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0xCC08060F), alpha = clamp01(entrance.value * 4f)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcadeText("PAUSED", Modifier.enterStage(entrance, 0f, 0.4f, pop = true), unit = 6.dp, color = Color(Pal.YELLOW))
            Spacer(Modifier.height(24.dp))
            ArcadeButton("${ArcadeFont.PLAY} RESUME", onResume, Modifier.enterStage(entrance, 0.15f, 0.55f, rise = 14.dp, pop = true), color = Color(Pal.GREEN), unit = 4.dp)
            Spacer(Modifier.height(16.dp))
            ArcadeButton("QUIT ROUND", onQuit, Modifier.enterStage(entrance, 0.25f, 0.65f, rise = 14.dp, pop = true), color = Color(Pal.RED), unit = 3.dp)
            Spacer(Modifier.height(10.dp))
            ArcadeText("QUITTING FORFEITS THIS ROUND", Modifier.enterStage(entrance, 0.4f, 0.8f, rise = 8.dp), unit = 2.dp, tiny = true, color = Color(Pal.GRAY))
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
                // A punch no 3D stage took (a flat game) has nothing to move: drop it, don't let it linger.
                GameViewport.takePunch()
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
        // The new best stays hidden until the results reveal it.
        val liveBest = maxOf(state.best, if (state.phase == HostPhase.RESULTS && state.revealStage >= 2) state.resultScore else 0)
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
        val motion = UiMotion.enabled
        when (state.phase) {
            HostPhase.COUNTDOWN -> {
                val step = (state.phaseT / COUNT_STEP).toInt().coerceIn(0, 2)
                val frac = (state.phaseT % COUNT_STEP) / COUNT_STEP
                drawRect(Color.Black, Offset(0f, 0f), Size(GAME_W, GAME_H), alpha = 0.35f * clamp01(state.phaseT / 0.3f))
                val label = (3 - step).toString()
                val color = Color(COUNT_COLORS[step])
                val settle = clamp01(frac / 0.28f)
                val leave = clamp01((frac - 0.72f) / 0.28f)
                // Each numeral drops in big, overshoots a touch small, settles, then eases away.
                val scale = (if (motion) lerp(1.9f, 1f, easeOutBack(settle)) else 1f) * (1f - 0.12f * leave)
                val alpha = clamp01(frac / 0.1f) * (1f - leave)
                val size = 16f * scale
                val midY = 262f
                if (motion) {
                    drawCircle(color, 36f + 120f * easeOutCubic(frac), Offset(cx, midY), alpha = (1f - frac) * 0.45f, style = Stroke(3f))
                }
                ArcadeFont.drawCentered(this, label, cx, midY - 3.5f * size, size, color, alpha)
                ArcadeFont.drawCentered(this, "GET READY", cx, 380f, 3f, Color.White, clamp01(state.phaseT / 0.3f) * (0.65f + 0.35f * sin(state.hostTime * 5f)))
            }
            HostPhase.PLAYING -> if (state.goT < 0.75f) {
                val p = state.goT / 0.75f
                val settle = clamp01(state.goT / 0.16f)
                // GO! slams in, swells as it fades, with a ring rolling outward.
                val scale = (if (motion) lerp(2f, 1f, easeOutBack(settle)) else 1f) * (1f + 0.35f * p)
                val a = if (p < 0.55f) 1f else 1f - (p - 0.55f) / 0.45f
                val size = 12f * scale
                val midY = 262f
                if (motion) {
                    drawCircle(Color(Pal.LIME), 30f + 170f * easeOutCubic(p), Offset(cx, midY), alpha = (1f - p) * 0.5f, style = Stroke(4f))
                }
                ArcadeFont.drawCentered(this, "GO!", cx, midY - 3.5f * size, size, Color(Pal.LIME), a)
            }
            HostPhase.ENDING -> {
                val pop = if (motion) easeOutBack(clamp01(state.phaseT / 0.35f)) else clamp01(state.phaseT / 0.2f)
                drawRect(Color.Black, Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.4f * clamp01(state.phaseT / 0.3f))
                ArcadeFont.drawCentered(this, if (state.endedEarly) "ALL DONE!" else "TIME'S UP!", cx, 280f, 6f * pop, Color(Pal.YELLOW), clamp01(state.phaseT / 0.12f))
            }
            HostPhase.RESULTS -> drawResults(this, state, game)
            else -> Unit
        }
        // The white flash of the biggest moments: motion, so reduce motion turns it off.
        val flashAlpha = state.flash.value * 0.5f * ScreenShake.intensity.coerceIn(0f, 1f)
        if (flashAlpha > 0f) drawRect(Color.White, Offset.Zero, Size(GAME_W, GAME_H), alpha = flashAlpha)
    }
}

/**
 * The results card, revealed in stages (see [ResultsPlan]): the card slides in, the score counts up,
 * the best line lands (or NEW HIGH SCORE slams), the grade is stamped, and the ticket count and
 * printer come last. Reduce motion fades instead of sliding and stamping.
 */
private fun drawResults(scope: DrawScope, state: HostState, game: MiniGame) {
    with(scope) {
        val cx = GAME_W / 2f
        val t = state.phaseT
        val look = game.look
        val motion = UiMotion.enabled
        val stage = state.revealStage
        drawRect(Color.Black, Offset.Zero, Size(GAME_W, GAME_H), alpha = 0.7f * clamp01(t / 0.25f))

        // The card drops in with a bounce (a plain fade with reduce motion).
        val slide = if (motion) easeOutBack(clamp01(t / ResultsPlan.CARD_IN)) else 1f
        val cardAlpha = clamp01(t / (if (motion) 0.18f else 0.3f))
        val top = CARD_TOP - (1f - slide) * 300f
        drawRoundRect(Color(Pal.NIGHT), Offset(CARD_X, top), Size(CARD_W, CARD_H), CornerRadius(10f, 10f), alpha = cardAlpha)
        val hot = state.newHigh && stage >= 2
        if (hot) {
            // A gold rim that breathes for a new high score.
            val glow = 0.5f + 0.5f * sin(state.hostTime * 6f)
            drawRoundRect(Color(Pal.GOLD), Offset(CARD_X - 3f, top - 3f), Size(CARD_W + 6f, CARD_H + 6f), CornerRadius(12f, 12f), alpha = 0.18f + 0.22f * glow, style = Stroke(6f))
        }
        drawRoundRect(Color(if (hot) Pal.GOLD else look.trim), Offset(CARD_X, top), Size(CARD_W, CARD_H), CornerRadius(10f, 10f), alpha = cardAlpha, style = Stroke(4f))
        ArcadeFont.drawCentered(this, "ROUND OVER", cx, top + 14f, 3f, Color(look.glow), clamp01((t - 0.15f) / 0.2f))

        // Score: counts up, then gives one small pop as it lands on the total.
        if (stage >= 1) {
            val fadeIn = clamp01((t - ResultsPlan.SCORE_AT) / 0.15f)
            ArcadeFont.drawCentered(this, "SCORE", cx, top + 46f, 2f, Color(Pal.LAVENDER), fadeIn)
            val shown = ResultsPlan.countedScore(state.resultScore, t)
            val settleT = clamp01((t - (ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS)) / 0.25f)
            val pop = if (motion && settleT < 1f && t > ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS) 1f + 0.16f * (1f - easeOutCubic(settleT)) else 1f
            val size = 6f * pop
            ArcadeFont.drawCentered(this, shown.toString(), cx, top + 64f + (42f - 7f * size) / 2f, size, Color.White, fadeIn)
        }

        // Best line, or the NEW HIGH SCORE slam.
        if (stage >= 2) {
            val since = t - ResultsPlan.BEST_AT
            val land = clamp01(since / 0.28f)
            if (state.newHigh) {
                val c = RAINBOW[((state.hostTime * 10f).toInt()) % RAINBOW.size]
                val slam = if (motion) lerp(2.4f, 1f, easeOutBack(land)) else 1f
                val pulse = 1f + 0.06f * sin(state.hostTime * 10f)
                val size = 2.5f * slam * pulse
                ArcadeFont.drawCentered(this, "${ArcadeFont.STAR} NEW HIGH SCORE! ${ArcadeFont.STAR}", cx, top + 118f + (17.5f - 7f * size) / 2f, size, Color(c), clamp01(since / 0.1f))
            } else {
                ArcadeFont.drawCentered(this, "BEST ${state.best}", cx, top + 118f, 2f, Color(Pal.GRAY), clamp01(since / 0.25f))
            }
        }

        // Grade stamp (left) and the ticket count (right).
        if (stage >= 3) {
            val gt = t - ResultsPlan.GRADE_AT
            ArcadeFont.drawCentered(this, "GRADE", cx + BADGE_DX, top + 150f, 2f, Color(Pal.LAVENDER), clamp01(gt / 0.15f))
            drawGradeStamp(this, state.grade, Offset(cx + BADGE_DX, top + BADGE_DY), gt, state.hostTime, motion)
        }
        if (stage >= 4) {
            val pt = t - ResultsPlan.PRINT_AT
            val a = clamp01(pt / 0.2f)
            ArcadeFont.drawCentered(this, "TICKETS", cx - BADGE_DX, top + 150f, 2f, Color(Pal.LAVENDER), a)
            ArcadeFont.drawCentered(this, "${ArcadeFont.TICKET} ${state.printed}", cx - BADGE_DX, top + BADGE_DY - 17.5f, 5f, Color(Pal.ORANGE), a)
            if (game.bonusTickets > 0) {
                ArcadeFont.drawCentered(this, "INCLUDES +${game.bonusTickets} BONUS", cx, top + 246f, 2f, Color(Pal.YELLOW), a)
            }
        }

        // Ticket printer with the strip feeding out of it: it rises into place with the card.
        val slotY = ResultsPlan.SLOT_Y + (if (motion) (1f - clamp01((t - 0.15f) / 0.4f)) * 90f else 0f)
        val segH = 24f
        val segW = 60f
        val sx = cx - segW / 2f
        val printing = !state.printingDone && state.printed < state.resultTickets && stage >= 4
        val frac = if (printing) state.printAcc.coerceIn(0f, 1f) else 0f
        val count = state.printed + if (printing) 1 else 0
        val maxVisible = 12
        clipRect(0f, slotY, GAME_W, GAME_H) {
            for (k in 0 until minOf(count, maxVisible)) {
                val index = if (printing) k else k + 1
                val y = slotY + (frac + index - 1f) * segH
                if (y > GAME_H) break
                val wobble = sin(k * 0.9f + state.hostTime * 2f) * (k * 0.4f)
                ticket(this, sx + wobble, y, segW, segH)
            }
        }
        val housingAlpha = clamp01((t - 0.1f) / 0.3f)
        drawRoundRect(Color(Pal.DARKGRAY), Offset(cx - 80f, slotY - 26f), Size(160f, 30f), CornerRadius(6f, 6f), alpha = housingAlpha)
        drawRect(Color(Pal.BLACK), Offset(cx - 38f, slotY - 4f), Size(76f, 6f), alpha = housingAlpha)
        val lampOn = printing && (state.hostTime * 10f).toInt() % 2 == 0
        drawCircle(Color(if (lampOn) Pal.LIME else Pal.DARKGREEN), 5f, Offset(cx + 64f, slotY - 12f), alpha = housingAlpha)
        ArcadeFont.drawCentered(this, "TICKETS", cx - 10f, slotY - 20f, 2f, Color(Pal.LIGHTGRAY), housingAlpha, shadow = false)
        if ((stage < 4 && t > SKIP_AFTER) || printing) {
            ArcadeFont.drawCentered(this, "TAP TO SKIP", cx, 610f, 2f, Color.White, 0.5f + 0.5f * sin(state.hostTime * 6f))
        }
    }
}

/**
 * The grade badge, [t] seconds after the stamp began: it slams down from big (a plain fade with
 * reduce motion), a ring rolls outward where it lands, and an S shimmers gold.
 */
private fun drawGradeStamp(scope: DrawScope, grade: Grade, c: Offset, t: Float, hostTime: Float, motion: Boolean) {
    with(scope) {
        val p = clamp01(t / ResultsPlan.STAMP_SECONDS)
        val s = if (motion) lerp(2.8f, 1f, easeOutCubic(p)) else 1f
        val a = clamp01(t / 0.08f)
        val base = if (grade == Grade.S) Color(Pal.mix(Pal.GOLD, Pal.WHITE, 0.5f + 0.5f * sin(hostTime * 7f))) else Color(grade.argb)
        if (motion && t > ResultsPlan.STAMP_SECONDS) {
            val r = (t - ResultsPlan.STAMP_SECONDS) / 0.5f
            if (r < 1f) drawCircle(base, BADGE_R + 50f * easeOutCubic(r), c, alpha = (1f - r) * 0.55f, style = Stroke(3f))
        }
        rotate(if (motion) -10f else 0f, c) {
            drawCircle(Color(Pal.NIGHT), BADGE_R * s, c, alpha = a)
            drawCircle(base, BADGE_R * s, c, alpha = a, style = Stroke(4f * s))
            drawCircle(base, (BADGE_R - 7f) * s, c, alpha = a * 0.45f, style = Stroke(1.5f * s))
            ArcadeFont.drawCentered(this, grade.letter, c.x, c.y - 17.5f * s, 5f * s, base, a)
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
