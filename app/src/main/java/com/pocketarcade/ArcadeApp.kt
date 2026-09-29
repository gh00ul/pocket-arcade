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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.data.GameSettings
import com.pocketarcade.data.SaveState
import com.pocketarcade.data.SettingsStore
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.gl.GfxFailureNotice
import com.pocketarcade.engine.gl.GfxQuality
import com.pocketarcade.engine.gl.GlSurface
import com.pocketarcade.engine.Sfx
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.games.racer.RacerGame
import com.pocketarcade.hub.HubScreen
import com.pocketarcade.hub.HubWorld
import com.pocketarcade.hub.Spot
import com.pocketarcade.hub.SpotType
import com.pocketarcade.ui.GameHostScreen
import com.pocketarcade.ui.Hud
import com.pocketarcade.ui.HudExtras
import com.pocketarcade.ui.HudExtrasReach
import com.pocketarcade.ui.ArcadeText
import com.pocketarcade.ui.GlassBox
import com.pocketarcade.ui.MapScreen
import com.pocketarcade.ui.PrizeCounterScreen
import com.pocketarcade.ui.ProfileScreen
import com.pocketarcade.ui.SettingsScreen
import com.pocketarcade.ui.TitleScreen
import com.pocketarcade.ui.TokenMachineScreen
import com.pocketarcade.ui.playerLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Long-lived services shared by every screen. [appScope] outlives every screen (it is never
 * cancelled), for saves that must land even when the player leaves the screen that asked.
 */
class ArcadeServices(
    val repo: ArcadeRepository,
    val audio: AudioSynth,
    val haptics: Haptics,
    val appScope: CoroutineScope,
) {
    /**
     * Runs a save that must not be lost: it is launched in [appScope], so leaving the screen
     * (which cancels that screen's own scope) can't drop it, and it can't be cancelled midway.
     */
    fun persist(write: suspend () -> Unit) {
        appScope.launch { withContext(NonCancellable) { write() } }
    }
}

/** Signals from the Activity lifecycle that screens react to. */
class AppSignals {
    var paused by mutableStateOf(false)

    /** A machine id to walk straight into (from the launch intent's "play" extra). */
    var launchGame by mutableStateOf<String?>(null)
}

private enum class Screen { TITLE, HUB, GAME }
private enum class Overlay { NONE, PRIZES, TOKENS, PROFILE, MAP, SETTINGS }

/**
 * Top-level flow: title → hall ↔ machines, with the camera diving into a cabinet's screen and
 * back out to the exact spot the player was standing.
 */
@Composable
fun ArcadeApp(services: ArcadeServices, signals: AppSignals) {
    val context = LocalContext.current
    val save by services.repo.state.collectAsState(initial = SaveState())
    val games = remember { GameRegistry.createAll() }
    val world = remember { HubWorld(games, services.audio, services.haptics) }
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
    // The hall's camera: taken from the save once, then driven by the HUD button (and saved).
    var firstPerson by remember { mutableStateOf(false) }
    var viewRestored by remember { mutableStateOf(false) }
    // The player's options: read once, then this is the truth and every change is saved behind it.
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    var settings by remember { mutableStateOf(GameSettings()) }
    val hudExtra = with(LocalDensity.current) { HudExtrasReach.toPx() }
    world.hudExtra = hudExtra

    /** Puts [s] to work: the hall's controls and view, the volumes, the haptics and the shake. */
    fun applySettings(s: GameSettings) {
        world.applySettings(s)
        audio.sfxVolume = s.sfxGain
        audio.ambienceVolume = s.ambienceGain
        services.haptics.enabled = s.haptics
        services.haptics.strength = s.hapticsStrength
        ScreenShake.intensity = if (s.reduceMotion) 0f else 1f
        GfxQuality.tier = when (s.quality) {
            GameSettings.QUALITY_BATTERY -> GfxQuality.Tier.BATTERY
            GameSettings.QUALITY_BEST -> GfxQuality.Tier.QUALITY
            else -> GfxQuality.Tier.AUTO
        }
        GfxQuality.frameCap = s.frameCap
        for (g in games) if (g is RacerGame) g.tiltSteering = s.tiltSteering
    }

    fun changeSettings(next: GameSettings) {
        val s = next.sanitized()
        if (s == settings) return
        val hapticsTurnedOn = s.haptics && (!settings.haptics || s.hapticsPercent != settings.hapticsPercent)
        settings = s
        applySettings(s)
        audio.play(Sfx.BLIP, 0.5f, 1.2f)
        // So you can feel what you just switched on.
        if (hapticsTurnedOn) services.haptics.hit()
        scope.launch { settingsStore.save(s) }
    }

    LaunchedEffect(Unit) {
        settings = settingsStore.settings.first()
        applySettings(settings)
    }

    LaunchedEffect(save.hat, save.outfit) { world.setPlayerLook(save.playerLook()) }
    LaunchedEffect(save.owned) { world.setDecor(save.ownedDecor) }
    LaunchedEffect(save.muted) { audio.muted = save.muted }
    LaunchedEffect(save.loaded) {
        if (save.loaded && !viewRestored) {
            viewRestored = true
            firstPerson = save.firstPerson
            world.setFirstPerson(save.firstPerson, animate = false)
        }
    }
    // Going to the background lets go of every finger on the hall.
    LaunchedEffect(signals.paused) {
        if (signals.paused) world.cancelInput()
    }
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

    /**
     * Spends a token and dives into machine [index] through [at], the cabinet whose prompt was
     * tapped; without one (a launch shortcut) it dives into the copy nearest the player. The
     * cabinet is picked before the token is spent.
     */
    fun enterMachine(index: Int, at: Spot?) {
        if (busy) return
        val spot = at ?: world.nearestMachineSpot(index) ?: return
        // Claimed before the suspending spend, so a quick second tap can't spend a second token.
        busy = true
        scope.launch {
            if (!services.repo.spendToken()) {
                audio.play(Sfx.ERROR)
                services.haptics.tick()
                banner = "OUT OF TOKENS! TRY THE TOKEN MACHINE"
                busy = false
                return@launch
            }
            audio.play(Sfx.TOKEN)
            audio.play(Sfx.WHOOSH, 0.8f)
            services.haptics.hit()
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
        // Claimed before the suspending refund, like enterMachine: a quick second tap must not
        // get in before the coroutine starts and refund the same token twice.
        busy = true
        scope.launch {
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
        enterMachine(index, null)
    }

    fun onSpot(spot: Spot) {
        when (spot.type) {
            SpotType.MACHINE -> enterMachine(spot.machine, spot)
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
        // Every 3D picture is drawn by the GPU on this surface, under the interface.
        GlSurface(Modifier.fillMaxSize())
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
                        firstPerson = firstPerson,
                        onToggleView = {
                            val on = !firstPerson
                            firstPerson = on
                            world.setFirstPerson(on, animate = true)
                            audio.play(Sfx.WHOOSH, 0.35f, if (on) 1.3f else 0.9f)
                            scope.launch { services.repo.setFirstPerson(on) }
                        },
                    )
                    HudExtras(
                        onMap = {
                            world.cancelInput()
                            overlay = Overlay.MAP
                            audio.play(Sfx.SELECT)
                        },
                        onSettings = {
                            world.cancelInput()
                            overlay = Overlay.SETTINGS
                            audio.play(Sfx.SELECT)
                        },
                    )
                }
                when (overlay) {
                    Overlay.PRIZES -> PrizeCounterScreen(save, services) { overlay = Overlay.NONE }
                    Overlay.TOKENS -> TokenMachineScreen(save, services) { overlay = Overlay.NONE }
                    Overlay.PROFILE -> ProfileScreen(save, games) { overlay = Overlay.NONE }
                    Overlay.MAP -> MapScreen(
                        world,
                        onGo = { pin ->
                            overlay = Overlay.NONE
                            if (pin.go(world)) audio.play(Sfx.WHOOSH, 0.3f, 1.4f) else audio.play(Sfx.ERROR)
                        },
                        onClose = { overlay = Overlay.NONE },
                    )
                    Overlay.SETTINGS -> SettingsScreen(settings, ::changeSettings) { overlay = Overlay.NONE }
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
                    // Under both rows of HUD buttons.
                    .padding(top = 132.dp, start = 12.dp, end = 12.dp),
            ) {
                GlassBox(Modifier.background(Color(0xE6120C22), RoundedCornerShape(16.dp)), highlight = Color(Pal.YELLOW)) {
                    ArcadeText(text, unit = 2.2.dp, color = Color(Pal.YELLOW), centered = true)
                }
            }
        }

        if (fade.value > 0.001f) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black, alpha = fade.value.coerceIn(0f, 1f))
            }
        }

        // The GPU gave up: say so instead of leaving the interface over a blank screen.
        Gfx.failure?.let { GfxFailureNotice(it) }
    }
}
