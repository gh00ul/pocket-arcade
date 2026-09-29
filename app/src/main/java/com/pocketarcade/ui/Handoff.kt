package com.pocketarcade.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.pocketarcade.hub.TitleShowcase

/**
 * The timing and look of the title-to-hall handoff, as pure functions of two progress values:
 * `exit` (0 = the title at rest, 1 = pushed all the way in) and `entrance` (1 = the hall has just
 * appeared, 0 = settled). The screen dips into a warm doorway of light, then to dark, and the hall
 * comes up out of the dark as that light drains away; both ends of the dip are the same picture
 * (fully dark, a warm core), so a loading step can sit between them without a seam.
 */
object HandoffPlan {
    /** Milliseconds for the title's exit and the hall's entrance; the calm versions are quick crossfades. */
    const val EXIT_MS = 820
    const val ENTRANCE_MS = 1150
    const val CALM_EXIT_MS = 320
    const val CALM_ENTRANCE_MS = 450

    /** How long the interface takes to fade in once the hall has settled. */
    const val HUD_MS = 500

    /**
     * Frames to wait after the hall first appears before its entrance starts. Its first frame
     * builds the hall's models and can take a long time; the entrance clock starts after that, so
     * the hitch can't eat the animation and make the hall pop.
     */
    const val SETTLE_FRAMES = 2

    /** The doorway light's strongest, as an opacity of its warm core. */
    const val GLOW_PEAK = 0.55f

    /** The exit's light is at full by this much of the way; the darkness closes in from [DARK_FROM]. */
    const val GLOW_RISE_END = 0.85f
    const val DARK_FROM = 0.55f

    /** The entrance's darkness has lifted by this much of the way, and its light has drained by [ENTRANCE_GLOW_FADE]. */
    const val ENTRANCE_DARK_FADE = 0.62f
    const val ENTRANCE_GLOW_FADE = 0.45f

    /** How opaque the dark cover is. Whichever of the two is running counts: the entrance once it has begun. */
    fun dark(exit: Float, entrance: Float, calm: Boolean): Float {
        if (entrance > 0f) return 1f - smoothstep(0f, ENTRANCE_DARK_FADE, 1f - entrance)
        return if (calm) smoothstep(0f, 1f, exit) else smoothstep(DARK_FROM, 1f, exit)
    }

    /** How strong the warm doorway light at the middle of the screen is (0 to [GLOW_PEAK]); none when calm. */
    fun glow(exit: Float, entrance: Float, calm: Boolean): Float {
        if (calm) return 0f
        if (entrance > 0f) return GLOW_PEAK * (1f - smoothstep(0f, ENTRANCE_GLOW_FADE, 1f - entrance))
        return GLOW_PEAK * smoothstep(0f, GLOW_RISE_END, exit)
    }
}

/**
 * Drives the title-to-hall handoff: [exit] carries the title away, [entrance] eases the hall in,
 * and [hud] brings the interface up afterwards. Compose it with [HandoffWash] for the dip.
 */
class TitleHandoff {
    /** 0 = the title at rest, 1 = pushed all the way in. */
    val exit = Animatable(0f)

    /** 1 = the hall has just appeared, 0 = settled. */
    val entrance = Animatable(0f)

    /** The interface's opacity: held at 0 through the handoff, then eased up by [fadeInHud]. */
    val hud = Animatable(1f)

    /**
     * Plays the whole handoff. The title exits ([exit] 0 to 1), then [gate] runs while the screen is
     * fully dark (a place for a loading step), then [enterHall] switches to the hall, which eases in
     * ([entrance] 1 to 0, and [onCamera] gets the same value each frame so the hall camera can settle
     * with it). With [calm] both ends are short crossfades and the camera is never asked to move.
     * Returns when the hall has settled.
     *
     * The steps run one after another in this one coroutine: nothing races, so animations switched
     * off in the system settings still end with the hall showing.
     */
    suspend fun run(
        calm: Boolean,
        gate: suspend () -> Unit = {},
        enterHall: () -> Unit,
        onCamera: (Float) -> Unit = {},
    ) {
        exit.animateTo(1f, tween(if (calm) HandoffPlan.CALM_EXIT_MS else HandoffPlan.EXIT_MS, easing = LinearEasing))
        gate()
        hud.snapTo(0f)
        if (!calm) onCamera(1f)
        enterHall()
        repeat(HandoffPlan.SETTLE_FRAMES) { withFrameNanos { } }
        // The cover is the same picture either side of this: dark with the doorway's warm core.
        entrance.snapTo(1f)
        exit.snapTo(0f)
        entrance.animateTo(0f, tween(if (calm) HandoffPlan.CALM_ENTRANCE_MS else HandoffPlan.ENTRANCE_MS, easing = FastOutSlowInEasing)) {
            if (!calm) onCamera(value)
        }
        if (!calm) onCamera(0f)
    }

    /** Fades the interface in over [HandoffPlan.HUD_MS]. */
    suspend fun fadeInHud() {
        hud.animateTo(1f, tween(HandoffPlan.HUD_MS))
    }
}

private val DarkCover = Color(0xFF0B0616)
private val DoorwayLight = Color(0xFFFFDDB0)

/** The dip in the handoff: a dark cover, and over it the warm light of a doorway at the middle of the picture. */
@Composable
fun HandoffWash(handoff: TitleHandoff, calm: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val exit = handoff.exit.value
        val entrance = handoff.entrance.value
        val dark = HandoffPlan.dark(exit, entrance, calm)
        val glow = HandoffPlan.glow(exit, entrance, calm)
        if (dark > 0.002f) drawRect(DarkCover, alpha = dark.coerceIn(0f, 1f))
        if (glow > 0.002f) {
            // The lens centre of the title's camera is where the push is heading: put the light there.
            val centre = Offset(size.width / 2f, size.height * TitleShowcase.CENTER_Y)
            val radius = maxOf(size.width, size.height) * (0.25f + 0.75f * glow / HandoffPlan.GLOW_PEAK)
            drawRect(
                Brush.radialGradient(
                    listOf(DoorwayLight.copy(alpha = glow * 0.9f), DoorwayLight.copy(alpha = glow * 0.35f), Color.Transparent),
                    center = centre,
                    radius = radius,
                ),
            )
        }
    }
}
