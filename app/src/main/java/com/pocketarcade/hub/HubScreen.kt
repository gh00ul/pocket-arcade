package com.pocketarcade.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.rememberGameLoop

/**
 * The walkable hall. Runs the hub simulation on the fixed-step loop and renders it every frame.
 * [zoom] > 1 zooms toward [zoomFocus] (screen pixels) for the enter-a-machine transition.
 */
@Composable
fun HubScreen(
    world: HubWorld,
    save: SaveState,
    zoom: Float,
    zoomFocus: Offset,
    inputEnabled: Boolean,
    onSpotTapped: (Spot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapped by rememberUpdatedState(onSpotTapped)
    val enabled by rememberUpdatedState(inputEnabled)
    val currentSave by rememberUpdatedState(save)
    val frame = rememberGameLoop(world) { dt -> world.update(dt) }

    DisposableEffect(world) {
        onDispose { world.cancelInput() }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { world.setViewport(it.width.toFloat(), it.height.toFloat()) }
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
        if (zoom > 1.001f) {
            withTransform({ scale(zoom, zoom, zoomFocus) }) {
                HubRenderer.draw(this, world, currentSave)
            }
        } else {
            HubRenderer.draw(this, world, currentSave)
        }
    }
}
