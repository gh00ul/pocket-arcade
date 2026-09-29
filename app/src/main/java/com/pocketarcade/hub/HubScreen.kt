package com.pocketarcade.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.rememberGameLoop
import com.pocketarcade.engine.thumbZoneGestureExclusion

/**
 * The walkable 3D hall. Runs the hub simulation on the fixed-step loop and renders it every
 * frame. [dive] (0..1) flies the camera into [diveSpot]'s screen for the enter/exit transition.
 */
@Composable
fun HubScreen(
    world: HubWorld,
    save: SaveState,
    dive: Float,
    diveSpot: Spot?,
    inputEnabled: Boolean,
    onSpotTapped: (Spot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapped by rememberUpdatedState(onSpotTapped)
    val enabled by rememberUpdatedState(inputEnabled)
    val currentSave by rememberUpdatedState(save)
    val renderer = remember(world) { world.stage.renderer }
    val frame = rememberGameLoop(world) { dt -> world.update(dt) }

    DisposableEffect(world) {
        onDispose {
            world.cancelInput()
            Gfx.remove(HubRenderer.SLOT)
        }
    }
    // A dialog, a machine starting or a pause takes the hall's focus: let go of every finger, so
    // nobody comes back to a stick still pushed or a view still turning.
    LaunchedEffect(inputEnabled) {
        if (!inputEnabled) world.cancelInput()
    }
    val density = LocalDensity.current
    world.density = density.density
    // The HUD row: the safe-area inset, its padding and the buttons with their lip.
    world.hudBottom = WindowInsets.safeDrawing.getTop(density) + with(density) { 76.dp.toPx() }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { world.setViewport(it.width.toFloat(), it.height.toFloat()) }
            // The stick and the look drag start low on either side: keep Back swipes off them.
            .thumbZoneGestureExclusion(density)
            .pointerInput(world) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val id = change.id.value
                            val pos = change.position
                            when {
                                change.changedToDownIgnoreConsumed() -> {
                                    if (enabled) world.pointerDown(id, pos.x, pos.y)
                                }
                                change.changedToUpIgnoreConsumed() -> {
                                    val spot = world.pointerUp(id, pos.x, pos.y)
                                    if (spot != null && enabled) tapped(spot)
                                }
                                change.positionChanged() -> world.pointerMove(id, pos.x, pos.y)
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        frame.value
        world.setDive(diveSpot, dive)
        renderer.draw(this, world, currentSave)
    }
}
