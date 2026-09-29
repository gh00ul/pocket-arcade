package com.pocketarcade.ui

import com.pocketarcade.hub.HubCamera
import com.pocketarcade.hub.Spot
import com.pocketarcade.hub.SpotType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** The unlock id (in the save, through `ArcadeRepository.unlock`) that says the tutorial has been seen. */
const val TUTORIAL_DONE = "tutorial_done"

/** When the first-run tutorial starts by itself. */
object TutorialPolicy {
    /**
     * A new player who hasn't seen it: never seen ([seen]) and never played a round ([totalPlays]
     * is 0). Anyone who has played already knows their way round and is left alone; Settings can
     * still replay it for them.
     */
    fun shouldAutoStart(seen: Boolean, totalPlays: Int): Boolean = !seen && totalPlays <= 0
}

/** The lesson steps, in order. */
enum class TutorialStep { WALK, LOOK, PLAY, PRIZES }

/**
 * What the tutorial reads from the hall each frame: a plain snapshot, so tests can drive the
 * step machine without a hall. (That a round was started or the prize counter opened come as
 * events instead, [Tutorial.onPlayed] and [Tutorial.onPrizesOpened], since neither is on screen
 * when it happens.)
 */
class TutorialInput {
    /** Where the player's feet are (world units). */
    var x = 0f
    var y = 0f

    /** Whether the hall is in first person, and the view's heading (radians) and whether a finger is turning it. */
    var firstPerson = false
    var yaw = 0f
    var lookDragging = false

    /** Whether the player is standing at a machine's play spot, or the prize counter's. */
    var atMachine = false
    var atPrizes = false

    /** Whether the player is left-handed (the walk and look halves swap). */
    var leftHanded = false

    /** A panel or a transition has the screen: nothing counts and the card waits. */
    var blocked = false
}

/** What a coach card says: a title and up to two lines under it. */
data class CoachText(val title: String, val body: String, val check: Boolean = false)

/**
 * The first-run tutorial as a step machine: WALK, LOOK, PLAY, PRIZES. Each step advances when the
 * player actually does the thing (never on a timer, never on a button), shows a brief tick, then
 * the next begins; the last is followed by a short thank-you. It never blocks anything: it only
 * watches. The walk and the look are soft steps that count only what was done; playing a round
 * and opening the prize counter are hard ones: if the player gets there first, everything before
 * it is done too. [skipStep] and [skipAll] end things early.
 *
 * Time is fed in by [update] with the fixed step, so the machine is deterministic.
 */
class Tutorial {
    companion object {
        /** Distance (world units) walked, in total, that counts as learning to walk. The hall is 608 across. */
        const val WALK_DISTANCE = 90f

        /** Radians of view turned by a finger, in total, that counts as learning to look (about 34 degrees). */
        const val LOOK_ANGLE = 0.6f

        /** A position jump bigger than this in one update is a nudge or a teleport, not walking. */
        const val TELEPORT = 40f

        /** How long the tick after a step stays, and how long the thank-you after the last stays. */
        const val CHECK_SECONDS = 1.1f
        const val OUTRO_SECONDS = 2.8f

        /** How long a card takes to arrive. */
        const val ENTER_SECONDS = 0.4f

        private val STEPS = TutorialStep.entries
    }

    /** Where the machine is: waiting for the step's action, showing its tick, saying goodbye, or over. */
    enum class Phase { ACTIVE, CHECK, OUTRO, FINISHED }

    var step: TutorialStep = STEPS.first()
        private set
    var phase = Phase.ACTIVE
        private set

    /** Seconds since the phase began (a step's card arrives over [ENTER_SECONDS] from zero). */
    var phaseTime = 0f
        private set

    /** Whether the player ended it (or skipped its last step) rather than finishing it. */
    var skipped = false
        private set

    val finished: Boolean get() = phase == Phase.FINISHED

    /** Steps done so far, 0 to [stepCount]: for the progress dots. */
    val stepsDone: Int get() = if (phase == Phase.ACTIVE) step.ordinal else step.ordinal + 1

    val stepCount: Int get() = STEPS.size

    /** Total distance walked and total turn made by a finger, since the tutorial began. */
    var walked = 0f
        private set
    var looked = 0f
        private set
    private var played = false
    private var prizesSeen = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastYaw = 0f
    private var hasBaseline = false

    /** A round of a machine has begun. */
    fun onPlayed() {
        played = true
    }

    /** The prize counter's screen has been opened. */
    fun onPrizesOpened() {
        prizesSeen = true
    }

    /** Ends the whole tutorial now. */
    fun skipAll() {
        if (phase == Phase.FINISHED) return
        skipped = true
        setPhase(Phase.FINISHED)
    }

    /** Gives up on the current step: on to the next, or over if it was the last. */
    fun skipStep() {
        if (phase != Phase.ACTIVE) return
        if (step == STEPS.last()) {
            skipAll()
        } else {
            step = STEPS[step.ordinal + 1]
            setPhase(Phase.ACTIVE)
        }
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
    }

    private fun triggered(s: TutorialStep): Boolean = when (s) {
        TutorialStep.WALK -> walked >= WALK_DISTANCE
        TutorialStep.LOOK -> looked >= LOOK_ANGLE
        TutorialStep.PLAY -> played
        TutorialStep.PRIZES -> prizesSeen
    }

    /** Whether reaching [s] proves everything before it (playing, the prize counter), rather than being one thing done. */
    private fun isHard(s: TutorialStep) = s == TutorialStep.PLAY || s == TutorialStep.PRIZES

    /** Advances by [dt] seconds with the hall as [input] shows it. */
    fun update(dt: Float, input: TutorialInput) {
        if (phase == Phase.FINISHED || input.blocked) return
        track(input)
        phaseTime += dt
        when (phase) {
            Phase.ACTIVE -> {
                // The furthest hard step already done wins: it settles everything before it.
                var reached = -1
                for (i in step.ordinal until STEPS.size) if (isHard(STEPS[i]) && triggered(STEPS[i])) reached = i
                if (reached >= 0) {
                    step = STEPS[reached]
                    setPhase(Phase.CHECK)
                } else if (triggered(step)) {
                    setPhase(Phase.CHECK)
                }
            }
            Phase.CHECK -> if (phaseTime >= CHECK_SECONDS) {
                if (step == STEPS.last()) {
                    setPhase(Phase.OUTRO)
                } else {
                    step = STEPS[step.ordinal + 1]
                    setPhase(Phase.ACTIVE)
                }
            }
            Phase.OUTRO -> if (phaseTime >= OUTRO_SECONDS) setPhase(Phase.FINISHED)
            Phase.FINISHED -> Unit
        }
    }

    /** Adds this update's walking and looking to the totals. */
    private fun track(input: TutorialInput) {
        if (!hasBaseline) {
            hasBaseline = true
            lastX = input.x
            lastY = input.y
            lastYaw = input.yaw
        }
        val dx = input.x - lastX
        val dy = input.y - lastY
        val moved = sqrt(dx * dx + dy * dy)
        if (moved < TELEPORT) walked += moved
        lastX = input.x
        lastY = input.y
        val turn = abs(HubCamera.wrap(input.yaw - lastYaw))
        // Only a finger turning the view counts, not the view turning itself to face a machine.
        if (input.firstPerson && input.lookDragging) looked += turn
        lastYaw = input.yaw
    }

    /**
     * What the card says now for [input]: the step's lesson (in words that fit the current view and
     * the player's hand), the tick after it, or the thank-you at the end.
     */
    fun text(input: TutorialInput): CoachText {
        if (phase == Phase.OUTRO || phase == Phase.FINISHED) {
            return CoachText("YOU'RE ALL SET!", "HAVE FUN. COME BACK EVERY DAY\nFOR FREE TOKENS.", check = true)
        }
        if (phase == Phase.CHECK) {
            return CoachText(
                "NICE!",
                when (step) {
                    TutorialStep.WALK -> "THAT'S HOW YOU GET AROUND."
                    TutorialStep.LOOK -> "NOW YOU CAN TAKE IT ALL IN."
                    TutorialStep.PLAY -> "TICKETS PRINT OUT OF EVERY MACHINE."
                    TutorialStep.PRIZES -> "TICKETS BUY HATS, OUTFITS\nAND DECORATIONS."
                },
                check = true,
            )
        }
        val walkSide = if (input.leftHanded) "RIGHT" else "LEFT"
        val lookSide = if (input.leftHanded) "LEFT" else "RIGHT"
        return when (step) {
            TutorialStep.WALK -> CoachText(
                "WALK AROUND",
                if (input.firstPerson) "DRAG ON THE $walkSide OF THE SCREEN\nTO WALK. TAP A MACHINE TO GO THERE." else "DRAG ANYWHERE TO WALK,\nOR TAP THE FLOOR.",
            )
            TutorialStep.LOOK -> CoachText(
                "LOOK AROUND",
                if (input.firstPerson) "DRAG ON THE $lookSide OF THE SCREEN\nTO LOOK AROUND." else "TAP THE EYE BUTTON (TOP RIGHT)\nTO SEE THROUGH YOUR KID'S EYES.",
            )
            TutorialStep.PLAY -> CoachText(
                "PLAY A MACHINE",
                if (input.atMachine) "TAP THE PLAY BUBBLE.\nIT COSTS ONE TOKEN." else "WALK UP TO ANY MACHINE.\nFOLLOW THE ARROW.",
            )
            TutorialStep.PRIZES -> CoachText(
                "VISIT THE PRIZE COUNTER",
                if (input.atPrizes) "TAP THE SHOP BUBBLE." else "IT'S AT THE BACK OF THE HALL.\nTAP IT TO WALK THERE.",
            )
        }
    }

    /**
     * The spot the arrow should point at for [input], or null (no arrow): the nearest machine while
     * playing and not yet at one, the prize counter while visiting it and not yet there.
     */
    fun guideTarget(spots: List<Spot>, input: TutorialInput): Spot? {
        if (phase != Phase.ACTIVE) return null
        return when (step) {
            TutorialStep.PLAY -> if (input.atMachine) null else nearest(spots, SpotType.MACHINE, input.x, input.y)
            TutorialStep.PRIZES -> if (input.atPrizes) null else nearest(spots, SpotType.PRIZES, input.x, input.y)
            else -> null
        }
    }

    private fun nearest(spots: List<Spot>, type: SpotType, x: Float, y: Float): Spot? {
        var best: Spot? = null
        var bestD = Float.MAX_VALUE
        for (i in spots.indices) {
            val s = spots[i]
            if (s.type != type) continue
            val dx = s.area.centerX - x
            val dy = s.area.centerY - y
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = s
            }
        }
        return best
    }
}

/** Where to draw a guiding arrow for a point in the camera's view space. */
object GuideMarker {
    /** How far to the side a point behind the camera has to be (view units) for its arrow to be half way to that edge. */
    private const val BEHIND_SOFTNESS = 60f

    /**
     * Places a marker for a point at view-space ([vx], [vy], [vz]) (x right, y up, z ahead) seen
     * through a camera with focal length [focal] and image centre ([cx], [cy]) on a [w] × [h]
     * screen. On screen (inside [margin] of the sides and between [top] and [bottom]) it sits on the
     * point and points down at it. Off screen, or behind the camera, it sits on the edge of that
     * rectangle in the direction of the point, pointing that way (behind you it sits along the
     * bottom edge, on the side the point is on). Writes x, y and the angle to point at (radians, 0
     * right, clockwise on screen) into [out] and returns whether it is on the point.
     */
    fun place(
        vx: Float, vy: Float, vz: Float, focal: Float, cx: Float, cy: Float, near: Float,
        w: Float, h: Float, margin: Float, top: Float, bottom: Float, out: FloatArray,
    ): Boolean {
        val midX = w / 2f
        val midY = (top + bottom) / 2f
        val halfW = (w / 2f - margin).coerceAtLeast(1f)
        val halfH = ((bottom - top) / 2f).coerceAtLeast(1f)
        var dx: Float
        var dy: Float
        if (vz >= near) {
            val sx = cx + vx / vz * focal
            val sy = cy - vy / vz * focal
            if (sx >= margin && sx <= w - margin && sy >= top && sy <= bottom) {
                out[0] = sx
                out[1] = sy
                out[2] = (PI / 2.0).toFloat()
                return true
            }
            dx = sx - midX
            dy = sy - midY
        } else {
            // Behind: along the bottom edge, pointing down ("turn round"), nearer the side the point is on.
            out[0] = midX + vx / (abs(vx) + BEHIND_SOFTNESS) * halfW
            out[1] = bottom
            out[2] = (PI / 2.0).toFloat()
            return false
        }
        if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) dy = 1f
        val kx = if (abs(dx) < 1e-6f) Float.MAX_VALUE else halfW / abs(dx)
        val ky = if (abs(dy) < 1e-6f) Float.MAX_VALUE else halfH / abs(dy)
        val k = minOf(kx, ky)
        out[0] = midX + dx * k
        out[1] = midY + dy * k
        out[2] = atan2(dy, dx)
        return false
    }
}
