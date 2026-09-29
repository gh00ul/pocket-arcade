package com.pocketarcade.games

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.TimeScale
import com.pocketarcade.engine.TouchType
import com.pocketarcade.hub.CabinetDesign

/** Every mini-game plays inside a fixed portrait field of GAME_W x GAME_H units. */
const val GAME_W = 360f
const val GAME_H = 640f

/**
 * The kind of cabinet the hall builds for a machine. The specific shapes get purpose-built
 * models (a glass claw box, a skee-ball alley...) and their own spot on the arcade floor; the
 * generic ones suit any new game.
 */
enum class CabinetShape {
    /** Classic upright video cabinet. */
    UPRIGHT,
    /** Wide glass-fronted merchandiser. */
    WIDE,
    /** Long alley you stand at the end of. */
    LANE,
    /** Low playing table with a scoreboard at the far end. */
    TABLE,
    CLAW,
    WHACK,
    SKEEBALL,
    HOOPS,
    PUSHER,
    AIR_HOCKEY,
    RACER,
    /** Tall upright with a vertical screen and a big button (stacker). */
    TOWER,
    /** Light-gun cabinet: a big screen with two guns mounted on the control shelf (shooter). */
    GUN,
    /** Pinball table: a slanted glass playfield on legs with a lit backbox at the far end. */
    PINBALL,
    /** A big round fishing tub with rods around the rim and a sign on a post (fishing). */
    FISHING,
}

/** How a machine looks in the hall: body and trim colours, the neon glow it casts, and its shape. */
data class CabinetLook(
    val body: Int,
    val trim: Int,
    val glow: Int,
    val shape: CabinetShape = CabinetShape.UPRIGHT,
)

/**
 * Services a game uses while it runs.
 *
 * Besides sound, haptics and collectibles it carries the game's requests for the *feel* of a
 * moment: [hitStop], [slowMo] and [punch]. They are plain fields the game host reads once per step
 * ([flush]) and turns into a freeze, a slow-motion beat and a camera kick; a game just says when
 * something big happens and never touches its own clock, so its simulation always steps by the
 * same fixed time. All three do nothing while reduce motion is on, and the host caps and spaces
 * them (see [com.pocketarcade.engine.TimeScale]), so calling them generously is safe.
 */
class GameFx(
    val audio: AudioSynth,
    val haptics: Haptics,
    /** Adds a collectible (e.g. a claw-machine plush id) to the player's collection. */
    val onCollectible: (String) -> Unit,
) {
    private var stopSeconds = 0f
    private var slowSpeed = 1f
    private var slowSeconds = 0f
    private var punchAmount = 0f

    /** Freezes the game for about [seconds] (0.04-0.1 feels right for a big hit; the host caps it at 0.12). */
    fun hitStop(seconds: Float) {
        if (seconds > stopSeconds) stopSeconds = seconds
    }

    /** Runs the game at [speed] (0.3-0.5) for [seconds] with eased ramps: a jackpot, a new high score, a last-second win. */
    fun slowMo(speed: Float, seconds: Float) {
        if (seconds <= 0f) return
        slowSpeed = minOf(slowSpeed, speed)
        if (seconds > slowSeconds) slowSeconds = seconds
    }

    /** Kicks the 3D camera in and lets it spring back: [amount] 0..1 (0.3 for a solid hit, 1 for the biggest moment). */
    fun punch(amount: Float) {
        if (amount > punchAmount) punchAmount = amount
    }

    /**
     * Host only: hands the pending requests to [time] (or drops them if it is null: outside the
     * playing phase nothing may slow the game), clears them and returns the pending camera punch.
     */
    fun flush(time: TimeScale?): Float {
        if (time != null) {
            if (stopSeconds > 0f) time.hitStop(stopSeconds)
            if (slowSeconds > 0f) time.slowMo(slowSpeed, slowSeconds)
        }
        stopSeconds = 0f
        slowSpeed = 1f
        slowSeconds = 0f
        val p = punchAmount
        punchAmount = 0f
        return p
    }
}

/**
 * The contract every arcade machine implements. The host owns the round flow (intro, countdown,
 * timer, pause, results and ticket printing); a game only simulates, draws and scores.
 *
 * To add a machine: implement this interface (usually by extending [BaseMiniGame]) and add it
 * to [GameRegistry]. The hall gives it a cabinet automatically.
 */
interface MiniGame {
    /** Stable id used for save data (high scores). Never change it after release. */
    val id: String
    /** Full name shown on the intro card and results. */
    val title: String
    /** Up to 6 characters, printed on the hall cabinet's marquee. */
    val marquee: String
    /** Short how-to-play lines for the intro card. */
    val instructions: List<String>
    val look: CabinetLook

    /**
     * The machine's own hall cabinet (size, camera dive point, model, lights, live screen and
     * moving parts), or null for the built-in cabinet of [CabinetLook.shape]. Keep a design's
     * art in the game's own package.
     */
    val cabinet: CabinetDesign? get() = null

    /** Length of a round in seconds. */
    val roundSeconds: Float

    /**
     * Draws the looping attract-mode animation on the cabinet screen in the hall.
     * [p] is set up so one unit is one art pixel; the screen is [w] x [h] art pixels.
     */
    fun drawAttract(p: Painter, w: Int, h: Int, time: Float)

    /** Resets all state for a fresh round. */
    fun start(fx: GameFx)

    /** Advances the simulation by a fixed [dt]. [timeLeft] reaches 0 when the round clock expires. */
    fun update(dt: Float, timeLeft: Float)

    /** Draws the game in field units (0..GAME_W, 0..GAME_H); the host applies scaling. */
    fun draw(scope: DrawScope)

    /** Touch input in field units. Coordinates may fall outside the field (on the bezel). */
    fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long)

    /**
     * The host calls this whenever it stops forwarding touches: on pause (Back, the close
     * button, the app going to the background) and when the round ends. A finger lifted
     * meanwhile never sends its UP, so forget every tracked pointer and let go of every held
     * control; the next touch must start fresh. Keep the simulation state (a ball in flight, a
     * prize in the claw) exactly as it is.
     */
    fun cancelInput()

    val score: Int

    /** True once the round is over (clock expired or the game ended itself) and nothing is still moving. */
    val finished: Boolean

    /** Tickets printed for a final [score]. */
    fun ticketsFor(score: Int): Int

    /** Extra tickets won directly during play (e.g. ticket bundles in the coin pusher). */
    val bonusTickets: Int
}
