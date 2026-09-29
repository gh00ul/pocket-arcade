package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pocketarcade.ArcadeServices
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.hub.CharacterLook
import com.pocketarcade.hub.Figure
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.Pose
import com.pocketarcade.share.PhotoShare
import com.pocketarcade.share.PhotoStore
import com.pocketarcade.share.PhotoStrip
import com.pocketarcade.share.PhotoStripArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

private enum class BoothPhase { READY, SHOOTING, DONE }

/** The kid in the booth's four poses, photographed by the GPU once each and kept for the shots. */
private fun shotKey(look: CharacterLook, i: Int) = "booth:$look:$i"

/**
 * The photo booth: a live-looking preview of your kid in their current hat and outfit, a 3-2-1
 * countdown, four shots in four poses (idle, cheer, sit, cheers) and a photo strip composed from
 * them under your arcade's name and the date. The strip is saved (the last four are kept) and can
 * be shared through the Android share sheet.
 */
@Composable
fun PhotoBoothScreen(save: SaveState, services: ArcadeServices, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = remember(save.hat, save.outfit) { save.playerLook() }
    val arcadeName by rememberUpdatedState(save.arcadeName)
    // The four poses, developed up front so a shot is instant when its flash comes.
    val shots = remember(look) { mutableStateListOf<ImageBitmap?>(null, null, null, null) }
    LaunchedEffect(look) {
        for ((i, pose) in PhotoBoothPlan.poses.withIndex()) {
            Thumbs.request(shotKey(look, i), PhotoStrip.FRAME, PhotoStrip.FRAME, { boothShot(look, pose) }) { shots[i] = it }
        }
    }

    var phase by remember { mutableStateOf(BoothPhase.READY) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var go by remember { mutableIntStateOf(0) }
    var strip by remember { mutableStateOf<ImageBitmap?>(null) }
    var saved by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    val step by remember { derivedStateOf { PhotoBoothPlan.at(elapsed) } }

    // One go: the countdown and the shots run on the frame clock, then the strip is composed and saved.
    LaunchedEffect(go) {
        if (go == 0) return@LaunchedEffect
        phase = BoothPhase.SHOOTING
        strip = null
        saved = null
        message = ""
        elapsed = 0f
        var start = -1L
        var lastCount = PhotoBoothPlan.COUNT + 1
        var lastCaptured = 0
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0L) start = now
            val t = (now - start) / 1_000_000_000f
            elapsed = t
            val s = PhotoBoothPlan.at(t)
            if (s.count in 1 until lastCount) services.audio.play(Sfx.COUNTDOWN, 0.6f, 0.9f + 0.1f * (PhotoBoothPlan.COUNT - s.count))
            if (s.captured > lastCaptured) {
                services.audio.play(Sfx.POP, 0.8f, 1.5f)
                services.haptics.tick()
            }
            lastCount = if (s.count > 0) s.count else lastCount
            lastCaptured = s.captured
            if (s.done) break
        }
        // Painted on this (the UI) thread, like all the arcade's type; the file is written elsewhere.
        val now = System.currentTimeMillis()
        val art = PhotoStripArt.compose(shots.map { it?.asAndroidBitmap() }, arcadeName, PhotoStrip.dateLabel(now))
        strip = art.asImageBitmap()
        phase = BoothPhase.DONE
        services.audio.play(Sfx.PRINT, 0.6f)
        val app = context.applicationContext
        services.persist {
            val file = withContext(Dispatchers.IO) { runCatching { PhotoStore.save(app, art, now) }.getOrNull() }
            saved = file
            if (file == null) message = "COULDN'T SAVE THE STRIP"
            services.repo.addStat("photos")
        }
    }

    ArcadePanel("PHOTO BOOTH", Color(Pal.SKY), onClose = onClose, fillHeight = true) {
        if (phase == BoothPhase.DONE) {
            StripResult(strip, saved != null, message, Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcadeButton(
                    "SHARE",
                    {
                        val file = saved
                        if (file != null && !PhotoShare.share(context, file)) message = "NO APP TO SHARE WITH"
                    },
                    Modifier.weight(1f), color = Color(Pal.GREEN), enabled = saved != null, unit = 2.2.dp,
                )
                ArcadeButton("RETAKE", { go++ }, Modifier.weight(1f), color = Color(Pal.ORANGE), unit = 2.2.dp)
                ArcadeButton("CLOSE", onClose, Modifier.weight(1f), color = Color(Pal.RED), unit = 2.2.dp)
            }
        } else {
            Booth(shots, phase == BoothPhase.SHOOTING, step, { elapsed }, Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until PhotoStrip.SHOTS) MiniShot(shots[i], i, i < step.captured, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            GlassBox(Modifier.fillMaxWidth()) {
                val running = phase == BoothPhase.SHOOTING
                val words = when {
                    !running -> "STRIKE A POSE!"
                    step.count > 0 -> "GET READY..."
                    else -> PhotoBoothPlan.poses[step.pose].cry
                }
                ArcadeText(words, unit = 2.8.dp, color = Color(Pal.YELLOW), centered = true)
                Spacer(Modifier.height(4.dp))
                ArcadeText(
                    if (running) "SHOT ${minOf(step.captured + 1, PhotoStrip.SHOTS)} OF ${PhotoStrip.SHOTS}" else "FOUR SHOTS MAKE A STRIP",
                    unit = 2.dp, tiny = true, color = Color(Pal.LAVENDER), centered = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcadeButton(
                    if (phase == BoothPhase.SHOOTING) "HOLD IT..." else "SNAP!",
                    { go++ }, Modifier.weight(2f), color = Color(Pal.PINK), enabled = phase == BoothPhase.READY, unit = 2.6.dp,
                )
                ArcadeButton("CLOSE", onClose, Modifier.weight(1f), color = Color(Pal.RED), unit = 2.2.dp)
            }
        }
    }
}

/** The big preview: the pose on show, the countdown number over it, the flash. */
@Composable
private fun Booth(shots: List<ImageBitmap?>, running: Boolean, step: PhotoBoothPlan.Step, elapsed: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .clip(shape)
                .border(2.dp, Color(Pal.SKY), shape),
        ) {
            drawRect(Color(0xFF1E0E3A))
            val image = shots[step.pose]
            if (image != null) drawFitted(image) else {
                ArcadeFont.drawCentered(this, "DEVELOPING...", size.width / 2f, size.height * 0.46f, size.width * 0.008f, Color(Pal.LAVENDER), tiny = true)
            }
            if (running && step.count > 0) {
                // Each number swells in and settles over its second.
                val settle = 1f - (elapsed() % PhotoBoothPlan.TICK) / PhotoBoothPlan.TICK
                val unit = size.height * 0.5f / ArcadeFont.CAP * (1f + 0.45f * settle * settle)
                ArcadeFont.drawCentered(this, step.count.toString(), size.width / 2f, size.height / 2f - ArcadeFont.height(unit) / 2f, unit, Color(Pal.YELLOW), alpha = 0.95f)
            }
            if (step.flash > 0f) drawRect(Color.White, alpha = step.flash * 0.9f)
        }
    }
}

/** One of the four small frames under the preview: filled in as each shot is taken. */
@Composable
private fun MiniShot(image: ImageBitmap?, index: Int, taken: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(if (taken) 2.dp else 1.dp, if (taken) Color(Pal.YELLOW) else UiColors.glassEdge, shape),
    ) {
        drawRect(Color(0xFF120C22))
        if (taken && image != null) drawFitted(image)
        else {
            val unit = size.height * 0.03f
            ArcadeFont.drawCentered(this, (index + 1).toString(), size.width / 2f, (size.height - ArcadeFont.height(unit)) / 2f, unit, Color(Pal.GRAY))
        }
    }
}

/** The finished strip, as tall as fits, and whether it's safely saved. */
@Composable
private fun StripResult(strip: ImageBitmap?, saved: Boolean, message: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (strip != null) {
                val shape = RoundedCornerShape(6.dp)
                Canvas(
                    Modifier
                        .aspectRatio(strip.width.toFloat() / strip.height, matchHeightConstraintsFirst = true)
                        .clip(shape)
                        .border(1.dp, UiColors.glassEdge, shape),
                ) { drawFitted(strip) }
            } else {
                ArcadeText("PRINTING...", unit = 3.dp, color = Color(Pal.LAVENDER))
            }
        }
        Spacer(Modifier.height(6.dp))
        val note = message.ifEmpty { if (saved) "SAVED! SHARE IT WITH FRIENDS" else "SAVING..." }
        ArcadeText(note, unit = 2.dp, tiny = true, color = Color(if (message.isEmpty()) Pal.CYAN else Pal.ORANGE), centered = true)
    }
}

/** Draws [image] over the whole scope (its square or strip shape already matches the canvas). */
private fun DrawScope.drawFitted(image: ImageBitmap) {
    drawImage(
        image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.High,
    )
}

// ---------------------------------------------------------------------- the booth's studio

private val boothFigures = HashMap<CharacterLook, Figure>()

/** A round chrome stool with a red seat, its top at the height a sitting kid's hips are. */
private val boothStool: Model by lazy {
    val chrome = HallArt.chrome.full
    val seat = HallArt.paint(0xFFE8323C.toInt()).full
    ModelBuilder()
        .cylinder(0f, -1f, 0f, 0.8f, 9f, 20, chrome, top = chrome, gloss = 0.8f)
        .cylinder(0f, -1f, 0.8f, 6.5f, 1f, 10, chrome, gloss = 0.9f)
        .cylinder(0f, -1f, 6.5f, 9f, 7.5f, 20, seat, top = seat, gloss = 0.5f)
        .build()
}

/**
 * Photographs [look] doing [shot] in front of the booth's curtain, from the front at about the
 * height of the chest, with a warm key light and a violet light from behind. Recorded here and
 * developed by the GPU through [Thumbs], like every menu picture.
 */
private fun Studio.boothShot(look: CharacterLook, shot: PhotoPose) {
    r.gradient(0xFF7A2A96.toInt(), 0xFF1E0E3A.toInt())
    // A ball of this radius round the middle of the kid fills the picture: hats and raised arms fit.
    val centre = 28f
    val radius = 34f
    val fovY = Math.toRadians(30.0).toFloat()
    val halfMin = minOf(tan(fovY / 2f), tan(fovY / 2f) * w.toFloat() / h)
    val dist = radius / halfMin * 1.08f
    val pitch = Math.toRadians(7.0).toFloat()
    r.camera.lookAt(0f, centre + sin(pitch) * dist, cos(pitch) * dist, 0f, centre, 0f, fovY, w, h)
    r.lighting.points += PointLight(-radius * 1.5f, centre + radius * 1.8f, radius * 2f, 1f, 0.9f, 0.8f, radius * 6f, 0.75f)
    r.lighting.points += PointLight(radius * 1.2f, centre + radius, -radius * 1.8f, 0.7f, 0.45f, 1f, radius * 5f, 0.9f)
    if (shot.pose == Pose.SIT) boothStool.draw(r)
    boothFigures.getOrPut(look) { Figure(look) }.draw(r, 0f, 0f, 0f, shot.yaw, shot.pose, 0f, shot.time)
    r.flat(0f, 0f, 0.15f, 20f, 14f, HallArt.shadow.full, blend = Blend.ALPHA, alpha = 0.5f)
}
