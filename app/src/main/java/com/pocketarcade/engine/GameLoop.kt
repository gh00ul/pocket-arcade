package com.pocketarcade.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/** Simulation step. 120 Hz divides evenly into 60, 90 and 120 Hz displays and keeps physics stable. */
const val FIXED_DT = 1f / 120f

private const val MAX_FRAME_SECONDS = 0.1f
private const val MAX_STEPS_PER_FRAME = 12
private const val VSYNC_SNAP_SECONDS = 0.0006f

/**
 * Fixed-timestep game loop driven by [withFrameNanos].
 *
 * Every display frame, elapsed time is accumulated and [onStep] runs zero or more times with
 * exactly [FIXED_DT]. Frame deltas that sit within a hair of a whole number of steps are snapped
 * to it, so vsync jitter never produces a 0-step frame followed by a 2-step frame.
 *
 * Returns a frame counter state; read it inside a Canvas draw block to redraw every frame
 * without recomposing anything.
 */
@Composable
fun rememberGameLoop(vararg keys: Any?, onStep: (dt: Float) -> Unit): State<Long> {
    val frame = remember { mutableLongStateOf(0L) }
    val step by rememberUpdatedState(onStep)
    LaunchedEffect(*keys) {
        var last = -1L
        var accumulator = 0f
        while (isActive) {
            withFrameNanos { now ->
                if (last < 0L) last = now
                var elapsed = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                last = now
                val wholeSteps = (elapsed / FIXED_DT).roundToInt()
                if (wholeSteps > 0 && abs(elapsed - wholeSteps * FIXED_DT) < VSYNC_SNAP_SECONDS) {
                    elapsed = wholeSteps * FIXED_DT
                }
                accumulator += elapsed
                var steps = 0
                while (accumulator >= FIXED_DT - 1e-6f && steps < MAX_STEPS_PER_FRAME) {
                    step(FIXED_DT)
                    accumulator -= FIXED_DT
                    steps++
                }
                if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f
                if (accumulator < 0f) accumulator = 0f
                frame.longValue = frame.longValue + 1
            }
        }
    }
    return frame
}
