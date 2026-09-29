package com.pocketarcade.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.approach
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.hub.HubWorld
import com.pocketarcade.hub.SpotType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How fast a card arrives and leaves (per second): a quarter of a second either way. */
private const val CARD_FADE_RATE = 4f

/** A card slides this far (dp) as it arrives. */
private const val CARD_SLIDE_DP = 14f

/** Demo touch: the ring's radius (dp) and how far the ghost finger swings (dp) in each lesson. */
private const val DEMO_RING_DP = 34f
private const val DEMO_SWING_DP = 34f

/** Where the demos sit, as shares of the screen (the walk demo overhead is in the middle, where a thumb starts). */
private const val DEMO_Y = 0.6f
private const val DEMO_WALK_SIDE_X = 0.24f

/** What the card shows, rebuilt only when it changes (not every step). */
private data class CoachUi(val text: CoachText, val done: Int, val count: Int, val step: TutorialStep, val active: Boolean)

/** The overlay's own bookkeeping: plain fields, updated in the frame loop and read while drawing. */
private class OverlayState {
    var time = 0f
    var shown = 0f
    var enterT = 0f
    var lastStep: TutorialStep? = null
    var textKey = -1
    var reported = false

    /** A pointer along +x from the origin, one unit long: scaled, turned and moved into place to draw. */
    val arrow = Path().apply {
        moveTo(1f, 0f)
        lineTo(-0.7f, 0.85f)
        lineTo(-0.3f, 0f)
        lineTo(-0.7f, -0.85f)
        close()
    }
    val camera = Camera3D()
    val marker = FloatArray(3)
}

private fun accentOf(step: TutorialStep): Color = Color(
    when (step) {
        TutorialStep.WALK -> Pal.CYAN
        TutorialStep.LOOK -> Pal.SKY
        TutorialStep.PLAY -> Pal.GOLD
        TutorialStep.PRIZES -> Pal.HOTPINK
    },
)

/**
 * The tutorial's coach marks over the hall: a card at the foot of the screen saying what to do
 * next (with a tick when it's done), a ghost of the touch that does it, and an arrow towards the
 * machine or the prize counter. It only ever looks: the card is the one thing that takes touches
 * (its two small buttons), and everything else passes straight through to the hall. It watches
 * [world] and drives [tutorial]; while [paused] (a panel open, a transition) the card steps aside.
 * [onFinished] fires once, after the last card has faded, with whether the player skipped it.
 * With [reduceMotion] nothing slides, swings or bobs: cards fade and the demos hold still.
 */
@Composable
fun TutorialOverlay(
    tutorial: Tutorial,
    world: HubWorld,
    paused: Boolean,
    reduceMotion: Boolean,
    onFinished: (skipped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = remember(tutorial) { OverlayState() }
    val input = remember(tutorial) { TutorialInput() }
    val finish by rememberUpdatedState(onFinished)
    var coach by remember(tutorial) { mutableStateOf<CoachUi?>(null) }
    val frame = rememberGameLoop(tutorial) { dt ->
        // What the hall looks like now.
        val spot = world.activeSpot
        input.x = world.player.x
        input.y = world.player.y
        input.firstPerson = world.camera.firstPerson
        input.yaw = world.camera.yaw
        input.lookDragging = world.lookDragging
        input.atMachine = spot != null && spot.type == SpotType.MACHINE
        input.atPrizes = spot != null && spot.type == SpotType.PRIZES
        input.leftHanded = world.settings.leftHanded
        input.blocked = paused
        tutorial.update(dt, input)

        ui.time += dt
        if (tutorial.step != ui.lastStep) {
            ui.lastStep = tutorial.step
            ui.enterT = 0f
        } else {
            ui.enterT += dt
        }
        // Rebuild the words only when what they depend on changes (not 120 times a second).
        val key = tutorial.step.ordinal or (tutorial.phase.ordinal shl 3) or
            ((if (input.firstPerson) 1 else 0) shl 6) or ((if (input.leftHanded) 1 else 0) shl 7) or
            ((if (input.atMachine) 1 else 0) shl 8) or ((if (input.atPrizes) 1 else 0) shl 9)
        if (key != ui.textKey) {
            ui.textKey = key
            coach = CoachUi(
                tutorial.text(input), tutorial.stepsDone, tutorial.stepCount, tutorial.step,
                active = tutorial.phase == Tutorial.Phase.ACTIVE,
            )
        }
        ui.shown = approach(ui.shown, if (paused || tutorial.finished) 0f else 1f, dt * CARD_FADE_RATE)
        if (tutorial.finished && ui.shown <= 0.01f && !ui.reported) {
            ui.reported = true
            finish(tutorial.skipped)
        }
    }

    Box(modifier.fillMaxSize()) {
        // The ghost touches and the arrow. No touch handling here, so every finger reaches the hall.
        Canvas(Modifier.fillMaxSize()) {
            frame.value
            if (ui.shown > 0.01f) drawCoachMarks(tutorial, world, input, ui, reduceMotion)
        }
        val c = coach
        if (c != null) {
            val slide = with(LocalDensity.current) { CARD_SLIDE_DP.dp.toPx() }
            CoachCard(
                ui = c,
                checkProgress = {
                    // Read the frame counter, so the tick redraws as it draws itself in.
                    frame.value
                    smoothstep(0.05f, 0.45f, tutorial.phaseTime)
                },
                onSkipStep = { tutorial.skipStep() },
                onEnd = { tutorial.skipAll() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 12.dp, end = 12.dp, bottom = 64.dp)
                    .graphicsLayer {
                        frame.value
                        val arrive = smoothstep(0f, Tutorial.ENTER_SECONDS, ui.enterT)
                        val a = ui.shown * arrive
                        alpha = a
                        translationY = if (reduceMotion) 0f else (1f - a) * slide
                    },
            )
        }
    }
}

/** The card: step dots and title, what to do, and (while a step is running) the two ways out. */
@Composable
private fun CoachCard(
    ui: CoachUi,
    checkProgress: () -> Float,
    onSkipStep: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentOf(ui.step)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF0241A40), Color(0xF0120C22))))
            .border(2.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ui.text.check) {
                Canvas(Modifier.size(22.dp)) { drawCheck(checkProgress(), accent) }
                Spacer(Modifier.width(8.dp))
            }
            ArcadeText(ui.text.title, unit = 2.2.dp, color = accent.lift(0.2f), maxWidth = 210.dp)
            Spacer(Modifier.weight(1f))
            for (i in 0 until ui.count) {
                if (i > 0) Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i < ui.done) accent else Color(0x44FFFFFF)),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        ArcadeText(ui.text.body, unit = 1.7.dp, tiny = true, color = Color.White, maxWidth = 300.dp)
        if (ui.active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallAction("SKIP STEP", "Skip this step", onSkipStep)
                SmallAction("END TUTORIAL", "End the tutorial", onEnd)
            }
        } else {
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** A quiet text button with a comfortable touch target. */
@Composable
private fun SmallAction(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ArcadeText(label, unit = 1.5.dp, tiny = true, color = Color(0xFF9A90B8))
    }
}

/** A tick drawing itself in: the short stroke first, then the long one. */
private fun DrawScope.drawCheck(progress: Float, color: Color) {
    val s = size.minDimension
    drawCircle(color, s / 2f, alpha = 0.22f)
    val w = s * 0.13f
    val a = Offset(s * 0.24f, s * 0.54f)
    val b = Offset(s * 0.43f, s * 0.72f)
    val c = Offset(s * 0.78f, s * 0.30f)
    val first = (progress / 0.4f).coerceIn(0f, 1f)
    val second = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
    if (first > 0f) drawLine(color, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first), w, StrokeCap.Round)
    if (second > 0f) drawLine(color, b, Offset(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second), w, StrokeCap.Round)
}

/** The demo touch and the guiding arrow for the step that is running. */
private fun DrawScope.drawCoachMarks(tutorial: Tutorial, world: HubWorld, input: TutorialInput, ui: OverlayState, calm: Boolean) {
    if (tutorial.phase != Tutorial.Phase.ACTIVE) return
    val accent = accentOf(tutorial.step)
    val fade = ui.shown * smoothstep(0f, Tutorial.ENTER_SECONDS, ui.enterT)
    val touching = world.joystick.active || world.lookDragging
    val w = size.width
    val h = size.height
    val ring = DEMO_RING_DP.dp.toPx()
    val swing = if (calm) 0f else DEMO_SWING_DP.dp.toPx() * sin(ui.time * 2.6f)
    when (tutorial.step) {
        TutorialStep.WALK -> if (!touching) {
            // A thumb resting where the stick will appear, pushing forward and back.
            val x = if (input.firstPerson) w * (if (input.leftHanded) 1f - DEMO_WALK_SIDE_X else DEMO_WALK_SIDE_X) else w * 0.5f
            drawTouchDemo(x, h * DEMO_Y, 0f, -swing, ring, fade, accent)
        }
        TutorialStep.LOOK -> if (input.firstPerson && !touching) {
            val x = w * (if (input.leftHanded) DEMO_WALK_SIDE_X else 1f - DEMO_WALK_SIDE_X)
            drawTouchDemo(x, h * DEMO_Y, swing * 1.4f, 0f, ring, fade, accent)
        }
        TutorialStep.PLAY, TutorialStep.PRIZES -> Unit
    }
    val target = tutorial.guideTarget(world.map.spots, input) ?: return
    val cam = ui.camera
    world.camera.apply(cam, w.toInt(), h.toInt())
    val ax = target.anchorX
    val ay = target.anchorHeight + 6f
    val az = target.anchorZ
    val onIt = GuideMarker.place(
        cam.viewX(ax, ay, az), cam.viewY(ax, ay, az), cam.viewZ(ax, ay, az),
        cam.focal, cam.cx, cam.cy, cam.near, w, h,
        margin = 40.dp.toPx(), top = world.hudBottom + 28.dp.toPx(), bottom = h - 190.dp.toPx(), out = ui.marker,
    )
    val bob = if (calm) 0f else sin(ui.time * 4.2f) * 6.dp.toPx()
    val size = 15.dp.toPx()
    val x = ui.marker[0]
    val y = ui.marker[1]
    if (onIt) {
        // Hovering over the thing, pointing down at it, with a ring breathing out from its foot.
        val pulse = if (calm) 0.5f else (ui.time * 0.9f) % 1f
        drawCircle(accent, size * (0.8f + 1.6f * pulse), Offset(x, y + size * 0.4f), alpha = 0.5f * (1f - pulse) * fade, style = Stroke(2.dp.toPx()))
        drawArrow(ui, x, y - size * 1.6f - bob, (PI / 2.0).toFloat(), size, accent, fade)
    } else {
        // Pointing off the screen's edge: it nudges outward along the way it points.
        val a = ui.marker[2]
        drawArrow(ui, x + cos(a) * bob, y + sin(a) * bob, a, size, accent, fade * 0.95f)
    }
}

/** A pointer at ([x], [y]) turned [angle] radians clockwise from pointing right, [size] long, in [color]. */
private fun DrawScope.drawArrow(ui: OverlayState, x: Float, y: Float, angle: Float, size: Float, color: Color, alpha: Float) {
    val degrees = angle * 180f / PI.toFloat()
    withTransform({
        translate(x, y)
        rotate(degrees, Offset.Zero)
        scale(size * 1.22f, size * 1.22f, Offset.Zero)
    }) { drawPath(ui.arrow, Color.Black, alpha = 0.45f * alpha) }
    withTransform({
        translate(x, y)
        rotate(degrees, Offset.Zero)
        scale(size, size, Offset.Zero)
    }) { drawPath(ui.arrow, color, alpha = alpha) }
}

/** A ghost touch: a ring where it lands and a fingertip [dx], [dy] away from its centre. */
private fun DrawScope.drawTouchDemo(cx: Float, cy: Float, dx: Float, dy: Float, ring: Float, alpha: Float, accent: Color) {
    drawCircle(Color.Black, ring, Offset(cx, cy), alpha = 0.18f * alpha)
    drawCircle(Color.White, ring, Offset(cx, cy), alpha = 0.32f * alpha, style = Stroke(2.dp.toPx()))
    val tip = Offset(cx + dx, cy + dy)
    drawCircle(accent, ring * 0.5f, tip, alpha = 0.28f * alpha)
    drawCircle(Color.White, ring * 0.3f, tip, alpha = 0.7f * alpha)
}
