package com.pocketarcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.pocketarcade.hub.PhotoWall
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
            // Hang it on the photo wall by the booth.
            if (file != null) PhotoWall.refresh(app.filesDir)
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
                for (i in 0 until PhotoStrip.SHOTS) MiniShot(shots[i], i, i < step.captured, phase == BoothPhase.SHOOTING && i == step.captured, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            val running = phase == BoothPhase.SHOOTING
            GlassBox(Modifier.fillMaxWidth(), highlight = if (running) Color(Pal.YELLOW) else null) {
                val words = when {
                    !running -> "STRIKE A POSE!"
                    step.count > 0 -> "GET READY..."
                    else -> PhotoBoothPlan.poses[step.pose].cry
                }
                ArcadeText(words, UiText.HEADING, color = Color(Pal.YELLOW), centered = true)
                Spacer(Modifier.height(4.dp))
                ArcadeText(
                    if (running) "SHOT ${minOf(step.captured + 1, PhotoStrip.SHOTS)} OF ${PhotoStrip.SHOTS}" else "FOUR SHOTS MAKE A STRIP",
                    UiText.CAPTION, color = UiColors.textMid, centered = true,
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

/** The countdown's numeral swells by this much as it lands (a fraction of its size) and settles over its second. */
private const val NUMERAL_SWELL = 0.45f

/** A focus ring closes in on each numeral: it starts this much bigger than the numeral's ring and ends on it. */
private const val FOCUS_RING_SPREAD = 0.9f

/** The viewfinder's corner brackets: how far in from the edge and how long each arm is (fractions of the picture's width). */
private const val BRACKET_INSET = 0.05f
private const val BRACKET_ARM = 0.07f

/**
 * The big preview, framed like a camera monitor: the pose on show, viewfinder brackets, a status
 * light (READY, or a red REC while it shoots), the countdown numeral with a focus ring closing
 * in on it, and the flash.
 */
@Composable
private fun Booth(shots: List<ImageBitmap?>, running: Boolean, step: PhotoBoothPlan.Step, elapsed: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(UiRadius.box)
    val accent = Color(Pal.SKY)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .uiGlow(if (running) Color(Pal.RED) else accent, UiRadius.box, 8.dp, 0.24f)
                .clip(shape)
                .background(Color(0xFF1E0E3A))
                .border(UiEdge.strong, Brush.verticalGradient(listOf(accent, accent.shade(0.45f))), shape),
        ) {
            val image = shots[step.pose]
            if (image != null) drawFitted(image) else {
                ArcadeFont.drawCentered(this, "DEVELOPING...", size.width / 2f, size.height * 0.46f, size.width * 0.008f, Color(Pal.LAVENDER), tiny = true)
            }
            // A soft vignette, so the edges fall away toward the frame.
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x80070510)), center, size.maxDimension * 0.72f))
            // Viewfinder brackets in the corners.
            val inset = size.width * BRACKET_INSET
            val arm = size.width * BRACKET_ARM
            val bw = 2.dp.toPx()
            val bc = Color.White.copy(alpha = 0.7f)
            for (sx in intArrayOf(0, 1)) for (sy in intArrayOf(0, 1)) {
                val x = if (sx == 0) inset else size.width - inset
                val y = if (sy == 0) inset else size.height - inset
                val dx = if (sx == 0) arm else -arm
                val dy = if (sy == 0) arm else -arm
                drawLine(bc, Offset(x, y), Offset(x + dx, y), bw, StrokeCap.Round)
                drawLine(bc, Offset(x, y), Offset(x, y + dy), bw, StrokeCap.Round)
            }
            // The status light: green READY, or a blinking red REC.
            val lu = size.width * 0.0055f
            val lightC = Offset(inset + arm * 0.35f, inset + arm + lu * 5f)
            val recOn = running && (!UiMotion.enabled || (elapsed() * 2f).toInt() % 2 == 0)
            val lightColor = if (running) Color(Pal.RED) else Color(Pal.GREEN)
            if (!running || recOn) {
                glowCircle(lightColor, lightC, lu * 2.2f, lu * 3f, 0.4f)
                drawCircle(lightColor, lu * 2.2f, lightC)
            }
            ArcadeFont.draw(this, if (running) "REC" else "READY", lightC.x + lu * 5f, lightC.y - ArcadeFont.height(lu * 1.3f, true) / 2f, lu * 1.3f, Color.White, alpha = 0.85f, tiny = true)
            if (running && step.count > 0) {
                // Each number swells in and settles over its second, with a focus ring closing in on it.
                val settle = 1f - (elapsed() % PhotoBoothPlan.TICK) / PhotoBoothPlan.TICK
                // The focus ring's closing is decoration: with reduce motion it stays on the numeral.
                val ringSettle = if (UiMotion.enabled) settle else 0f
                val unit = size.height * 0.5f / ArcadeFont.CAP * (1f + NUMERAL_SWELL * settle * settle)
                val ringR = size.height * 0.27f
                drawCircle(Color.Black, ringR * 1.05f, center, alpha = 0.32f)
                drawCircle(Color(Pal.YELLOW), ringR * (1f + FOCUS_RING_SPREAD * ringSettle), center, alpha = 0.75f * (1f - 0.6f * ringSettle), style = Stroke(3.dp.toPx()))
                drawCircle(Color(Pal.YELLOW), ringR, center, alpha = 0.4f, style = Stroke(1.5.dp.toPx()))
                ArcadeFont.drawCentered(this, step.count.toString(), size.width / 2f, size.height / 2f - ArcadeFont.height(unit) / 2f, unit, Color(Pal.YELLOW), alpha = 0.95f)
            }
            if (step.flash > 0f) drawRect(Color.White, alpha = step.flash * 0.9f)
        }
    }
}

/**
 * One of the four small frames under the preview, like a frame of film: dark until its shot is
 * taken, then lit yellow with a tick. While a round is running the next one to be taken glows cyan.
 */
@Composable
private fun MiniShot(image: ImageBitmap?, index: Int, taken: Boolean, next: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(UiRadius.chip + 2.dp)
    val edge = when {
        taken -> Color(Pal.YELLOW)
        next -> Color(Pal.CYAN)
        else -> UiColors.glassEdge
    }
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (taken || next) Modifier.uiGlow(edge, UiRadius.chip + 2.dp, 6.dp, 0.3f) else Modifier)
            .clip(shape)
            .border(if (taken || next) UiEdge.strong else UiEdge.hair, edge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF120C22))
            if (taken && image != null) drawFitted(image)
            else {
                val unit = size.height * 0.03f
                ArcadeFont.drawCentered(
                    this, (index + 1).toString(), size.width / 2f, (size.height - ArcadeFont.height(unit)) / 2f, unit,
                    if (next) Color(Pal.CYAN) else Color(Pal.GRAY), alpha = if (next) 0.9f else 0.6f,
                )
            }
        }
        if (taken) IconBadge(UiIcon.CHECK, UiColors.good, Modifier.align(Alignment.TopEnd).padding(2.dp), size = 16.dp)
    }
}

/** How far the printed strip rests off the vertical, in degrees: a fresh print laid on the counter. */
private const val STRIP_TILT = -1.6f

/** The finished strip, as tall as fits, laid at a slight angle with a soft shadow and a glint, and whether it's safely saved. */
@Composable
private fun StripResult(strip: ImageBitmap?, saved: Boolean, message: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = UiSpace.sm), contentAlignment = Alignment.Center) {
            if (strip != null) {
                val shape = RoundedCornerShape(4.dp)
                val glint = Color(Pal.SKY)
                Canvas(
                    Modifier
                        .aspectRatio(strip.width.toFloat() / strip.height, matchHeightConstraintsFirst = true)
                        .graphicsLayer { rotationZ = if (UiMotion.enabled) STRIP_TILT else 0f }
                        .shadow(14.dp, shape, ambientColor = glint, spotColor = glint)
                        .clip(shape)
                        .border(UiEdge.hair, UiColors.glassEdge, shape),
                ) {
                    drawFitted(strip)
                    drawSparkle(Offset(size.width * 0.84f, size.height * 0.05f), size.width * 0.13f, Color.White.copy(alpha = 0.9f))
                }
            } else {
                ArcadeText("PRINTING...", UiText.TITLE, color = UiColors.textMid)
            }
        }
        val note = message.ifEmpty { if (saved) "SAVED! SHARE IT WITH FRIENDS" else "SAVING..." }
        ArcadeText(note, UiText.LABEL, color = if (message.isEmpty()) UiColors.info else UiColors.warn, centered = true)
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
