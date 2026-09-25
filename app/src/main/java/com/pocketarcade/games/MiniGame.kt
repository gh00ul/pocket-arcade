package com.pocketarcade.games

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.TouchType

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
}

/** How a machine looks in the hall: body and trim colours, the neon glow it casts, and its shape. */
data class CabinetLook(
    val body: Int,
    val trim: Int,
    val glow: Int,
    val shape: CabinetShape = CabinetShape.UPRIGHT,
)

/** Services a game uses while it runs. */
class GameFx(
    val audio: AudioSynth,
    val haptics: Haptics,
    /** Adds a collectible (e.g. a claw-machine plush id) to the player's collection. */
    val onCollectible: (String) -> Unit,
)

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

    val score: Int

    /** True once the round is over (clock expired or the game ended itself) and nothing is still moving. */
    val finished: Boolean

    /** Tickets printed for a final [score]. */
    fun ticketsFor(score: Int): Int

    /** Extra tickets won directly during play (e.g. ticket bundles in the coin pusher). */
    val bonusTickets: Int
}
