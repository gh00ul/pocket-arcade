package com.pocketarcade

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.hub.HubScreen
import com.pocketarcade.hub.HubWorld
import com.pocketarcade.hub.Spot
import com.pocketarcade.hub.SpotType
import com.pocketarcade.ui.GameHostScreen
import com.pocketarcade.ui.Hud
import com.pocketarcade.ui.PixelText
import com.pocketarcade.ui.PrizeCounterScreen
import com.pocketarcade.ui.ProfileScreen
import com.pocketarcade.ui.TitleScreen
import com.pocketarcade.ui.TokenMachineScreen
import com.pocketarcade.ui.playerLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Long-lived services shared by every screen. */
class ArcadeServices(val repo: ArcadeRepository, val audio: AudioSynth, val haptics: Haptics)

/** Signals from the Activity lifecycle that screens react to. */
class AppSignals {
    var paused by mutableStateOf(false)

    /** A machine id to walk straight into (from the launch intent's "play" extra). */
    var launchGame by mutableStateOf<String?>(null)
}

private enum class Screen { TITLE, HUB, GAME }
private enum class Overlay { NONE, PRIZES, TOKENS, PROFILE }

/**
 * Top-level flow: title → hall ↔ machines, with the camera diving into a cabinet's screen and
 * back out to the exact spot the player was standing.
 */
@Composable
fun ArcadeApp(services: ArcadeServices, signals: AppSignals) {
    val save by services.repo.state.collectAsState(initial = SaveState())
    val games = remember { GameRegistry.createAll() }
    val world = remember { HubWorld(games, services.audio) }
    var screen by remember { mutableStateOf(Screen.TITLE) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var activeGame by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var diveSpot by remember { mutableStateOf<Spot?>(null) }
    val dive = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val audio = services.audio

    LaunchedEffect(save.hat, save.outfit) { world.setPlayerLook(save.playerLook()) }
    LaunchedEffect(save.owned) { world.setDecor(save.ownedDecor) }
    LaunchedEffect(save.muted) { audio.muted = save.muted }
    LaunchedEffect(screen) {
        audio.ambientTarget = when (screen) {
            Screen.HUB -> 1f
            Screen.GAME -> 0.2f
            Screen.TITLE -> 0.5f
        }
    }
    // Daily refill: checked on launch and whenever the app comes back to the foreground.
    LaunchedEffect(save.loaded, signals.paused) {
        if (save.loaded && !signals.paused) {
            val granted = services.repo.applyDailyRefill()
            if (granted > 0) {
                banner = "DAILY BONUS: +$granted TOKENS!"
                audio.play(Sfx.JACKPOT)
                services.haptics.win()
            }
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(3200)
            banner = null
        }
    }

    fun enterMachine(index: Int) {
        if (busy) return
        scope.launch {
            if (!services.repo.spendToken()) {
                audio.play(Sfx.ERROR)
                services.haptics.tick()
                banner = "OUT OF TOKENS! TRY THE TOKEN MACHINE"
                return@launch
            }
            busy = true
            audio.play(Sfx.TOKEN)
            audio.play(Sfx.WHOOSH, 0.8f)
            services.haptics.hit()
            val spot = world.map.spots.first { it.type == SpotType.MACHINE && it.machine == index }
            diveSpot = spot
            world.cancelInput()
            launch {
                delay(200)
                fade.animateTo(1f, tween(560, easing = FastOutLinearInEasing))
            }
            dive.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
            activeGame = index
            screen = Screen.GAME
            fade.animateTo(0f, tween(350))
            busy = false
        }
    }

    fun exitGame(refund: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            if (refund) services.repo.refundToken()
            audio.play(Sfx.WHOOSH, 0.6f, 0.8f)
            fade.animateTo(1f, tween(250))
            screen = Screen.HUB
            activeGame = -1
            dive.snapTo(1f)
            launch { dive.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
            fade.animateTo(0f, tween(420))
            busy = false
        }
    }

    // Shortcut launch: `adb shell am start -n com.pocketarcade/.MainActivity --es play <id>`.
    LaunchedEffect(signals.launchGame, save.loaded, busy) {
        val id = signals.launchGame ?: return@LaunchedEffect
        if (!save.loaded || busy) return@LaunchedEffect
        signals.launchGame = null
        val index = games.indexOfFirst { it.id == id }
        if (index < 0 || screen == Screen.GAME) return@LaunchedEffect
        overlay = Overlay.NONE
        screen = Screen.HUB
        enterMachine(index)
    }

    fun onSpot(spot: Spot) {
        when (spot.type) {
            SpotType.MACHINE -> enterMachine(spot.machine)
            SpotType.TOKENS -> {
                world.cancelInput()
                overlay = Overlay.TOKENS
                audio.play(Sfx.SELECT)
            }
            SpotType.PRIZES -> {
                world.cancelInput()
                overlay = Overlay.PRIZES
                audio.play(Sfx.SELECT)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(Pal.NIGHT))) {
        when (screen) {
            Screen.TITLE -> TitleScreen(save, games) {
                if (!busy) {
                    scope.launch {
                        busy = true
                        audio.play(Sfx.COIN)
                        audio.play(Sfx.WHOOSH, 0.5f)
                        fade.animateTo(1f, tween(300))
                        screen = Screen.HUB
                        fade.animateTo(0f, tween(500))
                        busy = false
                    }
                }
            }
            Screen.HUB -> {
                HubScreen(
                    world = world,
                    save = save,
                    dive = dive.value,
                    diveSpot = diveSpot,
                    inputEnabled = overlay == Overlay.NONE && !busy,
                    onSpotTapped = ::onSpot,
                )
                if (!busy && dive.value <= 0.01f) {
                    Hud(
                        save = save,
                        onProfile = {
                            world.cancelInput()
                            overlay = Overlay.PROFILE
                            audio.play(Sfx.SELECT)
                        },
                        onToggleMute = {
                            scope.launch { services.repo.setMuted(!save.muted) }
                        },
                    )
                }
                when (overlay) {
                    Overlay.PRIZES -> PrizeCounterScreen(save, services) { overlay = Overlay.NONE }
                    Overlay.TOKENS -> TokenMachineScreen(save, services) { overlay = Overlay.NONE }
                    Overlay.PROFILE -> ProfileScreen(save, games) { overlay = Overlay.NONE }
                    Overlay.NONE -> Unit
                }
                BackHandler(enabled = overlay != Overlay.NONE) {
                    overlay = Overlay.NONE
                    audio.play(Sfx.BLIP, 0.5f)
                }
            }
            Screen.GAME -> if (activeGame in games.indices) {
                GameHostScreen(
                    game = games[activeGame],
                    save = save,
                    services = services,
                    appPaused = signals.paused,
                    onExit = ::exitGame,
                )
            }
        }

        banner?.let { text ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 84.dp, start = 12.dp, end = 12.dp)
                    .background(Color(Pal.NIGHT))
                    .border(3.dp, Color(Pal.YELLOW))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                PixelText(text, pixel = 2.dp, color = Color(Pal.YELLOW), centered = true)
            }
        }

        if (fade.value > 0.001f) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black, alpha = fade.value.coerceIn(0f, 1f))
            }
        }
    }
}
