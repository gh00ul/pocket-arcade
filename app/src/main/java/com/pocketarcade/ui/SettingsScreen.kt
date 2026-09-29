package com.pocketarcade.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.GameSettings
import com.pocketarcade.data.StepRange
import com.pocketarcade.engine.Pal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The options: how first person feels, comfort, haptics and volume. Every change goes straight
 * to [onChange] (which applies and saves it); the caller sanitises, so the steppers only ever
 * ask for the next step. With [onReplayTutorial] a HELP row offers to run the tutorial again.
 */
@Composable
fun SettingsScreen(
    settings: GameSettings,
    onChange: (GameSettings) -> Unit,
    onReplayTutorial: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val s = settings
    ArcadePanel("SETTINGS", Color(Pal.CYAN), onClose, fillHeight = true) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Section("CONTROLS", "THE HALL IN FIRST PERSON") {
                Stepper("LOOK SPEED", "${s.lookPercent}%", GameSettings.LOOK, s.lookPercent) { onChange(s.copy(lookPercent = it)) }
                Toggle("INVERT LOOK", s.invertY, "DRAG UP TO LOOK DOWN") { onChange(s.copy(invertY = it)) }
                Toggle("LEFT-HANDED", s.leftHanded, "RIGHT THUMB WALKS,\nLEFT THUMB LOOKS") { onChange(s.copy(leftHanded = it)) }
                Stepper("FIELD OF VIEW", "${s.fovDeg}°", GameSettings.FOV, s.fovDeg) { onChange(s.copy(fovDeg = it)) }
                ChoiceRow(
                    "RUN MODE",
                    if (s.runLatch) "PUSH TO THE RIM TO\nLOCK A RUN, EASE OFF" else "RUN ONLY WHILE YOUR\nTHUMB IS AT THE RIM",
                    if (s.runLatch) "LATCH" else "RIM",
                    Color(Pal.PURPLE),
                ) { onChange(s.copy(runLatch = !s.runLatch)) }
            }
            Spacer(Modifier.height(12.dp))
            Section("RACER", "") {
                Toggle("TILT STEERING", s.tiltSteering, "LEAN THE PHONE TO STEER,\nDRAGGING STILL WORKS") { onChange(s.copy(tiltSteering = it)) }
            }
            Spacer(Modifier.height(12.dp))
            Section("COMFORT", "") {
                Toggle("REDUCE MOTION", s.reduceMotion, "NO HEAD BOB, RUN ZOOM\nOR SCREEN SHAKE") { onChange(s.copy(reduceMotion = it)) }
                Toggle("HAPTICS", s.haptics, "VIBRATION ON HITS\nAND WINS") { onChange(s.copy(haptics = it)) }
                if (s.haptics) {
                    Stepper("HAPTIC STRENGTH", "${s.hapticsPercent}%", GameSettings.HAPTIC_STRENGTH, s.hapticsPercent) { onChange(s.copy(hapticsPercent = it)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Section("SOUND", "") {
                Stepper("EFFECTS VOLUME", "${s.sfxPercent}%", GameSettings.VOLUME, s.sfxPercent) { onChange(s.copy(sfxPercent = it)) }
                Stepper("AMBIENCE VOLUME", "${s.ambiencePercent}%", GameSettings.VOLUME, s.ambiencePercent) { onChange(s.copy(ambiencePercent = it)) }
            }
            Spacer(Modifier.height(12.dp))
            Section("GRAPHICS", "CHANGES APPLY AT ONCE") {
                ChoiceRow(
                    "QUALITY",
                    when (s.quality) {
                        GameSettings.QUALITY_BATTERY -> "COOLER AND LIGHTER,\nGIVES UP EFFECTS EARLY"
                        GameSettings.QUALITY_BEST -> "KEEPS EVERY EFFECT,\nLOWERS SHARPNESS FIRST"
                        else -> "ADAPTS TO YOUR PHONE\nAS YOU PLAY"
                    },
                    when (s.quality) {
                        GameSettings.QUALITY_BATTERY -> "BATTERY"
                        GameSettings.QUALITY_BEST -> "BEST"
                        else -> "AUTO"
                    },
                    Color(Pal.PURPLE),
                ) { onChange(s.copy(quality = (s.quality + 1) % 3)) }
                ChoiceRow(
                    "FRAME RATE",
                    "30 SAVES BATTERY,\n60 IS SMOOTHER",
                    when (s.frameCap) {
                        30 -> "30 FPS"
                        60 -> "60 FPS"
                        else -> "AUTO"
                    },
                    Color(Pal.PURPLE),
                ) {
                    val caps = GameSettings.CAPS
                    onChange(s.copy(frameCap = caps[(caps.indexOf(s.frameCap).coerceAtLeast(0) + 1) % caps.size]))
                }
            }
            if (onReplayTutorial != null) {
                Spacer(Modifier.height(12.dp))
                Section("HELP", "") {
                    ChoiceRow("TUTORIAL", "THE GUIDED TOUR OF THE HALL,\nFROM THE START", "REPLAY", Color(Pal.PURPLE)) { onReplayTutorial() }
                }
            }
            Spacer(Modifier.height(16.dp))
            ArcadeButton("RESET ALL", { onChange(GameSettings()) }, color = Color(Pal.RED), unit = 2.4.dp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A frosted box holding one group of rows, headed by [title] (and a small [note] under it). */
@Composable
private fun Section(title: String, note: String, rows: @Composable () -> Unit) {
    GlassBox(Modifier.fillMaxWidth()) {
        ArcadeText(title, unit = 2.8.dp, color = Color(Pal.YELLOW))
        if (note.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ArcadeText(note, unit = 1.6.dp, tiny = true, color = Color(Pal.LAVENDER), maxWidth = 230.dp)
        }
        Spacer(Modifier.height(8.dp))
        rows()
    }
}

/** "− value +": the [label] over the current [value], stepping through [range] a step at a time. */
@Composable
private fun Stepper(label: String, value: String, range: StepRange, current: Int, onSet: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcadeButton("-", { onSet(range.nudge(current, -1)) }, Modifier.width(60.dp), color = Color(Pal.PURPLE), enabled = current > range.min, unit = 3.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ArcadeText(label, unit = 1.8.dp, tiny = true, color = Color(Pal.LAVENDER), maxWidth = 150.dp)
            Spacer(Modifier.height(4.dp))
            ArcadeText(value, unit = 3.dp, color = Color.White)
        }
        ArcadeButton("+", { onSet(range.nudge(current, 1)) }, Modifier.width(60.dp), color = Color(Pal.PURPLE), enabled = current < range.max, unit = 3.dp)
    }
}

/** A row with an ON / OFF button (green when on): the [label] and a small [note] on the left. */
@Composable
private fun Toggle(label: String, on: Boolean, note: String, onSet: (Boolean) -> Unit) {
    ChoiceRow(label, note, if (on) "ON" else "OFF", if (on) Color(Pal.GREEN) else Color(0xFF5A5478)) { onSet(!on) }
}

/** A row whose button flips a choice: the [label] and a small [note] on the left, the current [choice] on the button. */
@Composable
private fun ChoiceRow(label: String, note: String, choice: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ArcadeText(label, unit = 2.3.dp, tiny = true, color = Color.White, maxWidth = 165.dp)
            Spacer(Modifier.height(4.dp))
            ArcadeText(note, unit = 1.5.dp, tiny = true, color = Color(Pal.LAVENDER), maxWidth = 165.dp)
        }
        Spacer(Modifier.width(8.dp))
        ArcadeButton(choice, onClick, Modifier.width(104.dp), color = color, unit = 2.dp)
    }
}

/** A cog: a ring with eight teeth, [s] across, centred on [c]. */
fun DrawScope.drawGearIcon(c: Offset, s: Float, color: Color) {
    drawCircle(color, s * 0.2f, c, style = Stroke(s * 0.13f))
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4f
        val dx = cos(a)
        val dy = sin(a)
        drawLine(color, c + Offset(dx * s * 0.27f, dy * s * 0.27f), c + Offset(dx * s * 0.41f, dy * s * 0.41f), s * 0.13f, StrokeCap.Butt)
    }
}
