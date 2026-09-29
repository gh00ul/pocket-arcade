package com.pocketarcade.hub

import com.pocketarcade.games.GameRegistry
import com.pocketarcade.hub.RigChecks.DT
import com.pocketarcade.hub.RigChecks.assertSane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

/** Idle life: breathing, fidgets, blinking and where the head points. */
class FigureLifeTest {
    /** The head's turn from the body's facing (the neck plus what the shoulders took). */
    private fun gazeYaw(a: FigureAnim) = a.headYaw + a.twist

    /** Runs [a] for [secs] (seated by default, so no idle fidget can wander in and turn the head) calling [look] each step. */
    private fun settle(a: FigureAnim, secs: Float, look: (() -> Unit)? = null, pose: Pose = Pose.SIT) {
        repeat((secs / DT).toInt()) {
            look?.invoke()
            a.update(DT, 0f, 0f, 0f, pose)
        }
    }

    @Test
    fun theHeadTurnsToWhatItLooksAtAndNeverFurtherThanANeckCan() {
        // A target 45 degrees to one side is looked at directly (less the shoulders' share, which is added back).
        val a = FigureAnim()
        settle(a, 1.5f, { a.look(70f, 70f) })
        assertEquals("looked ${gazeYaw(a)} at a target at 45 degrees", 0.785f, gazeYaw(a), 0.06f)
        // Further round than a neck allows: as far as it goes, and no further.
        val b = FigureAnim()
        settle(b, 1.5f, { b.look(-100f, -10f) })
        val g = gazeYaw(b)
        assertTrue("turned $g for a target at 96 degrees", g < -0.9f && g > -1.1f)
        // A target directly behind is ignored altogether.
        val c = FigureAnim()
        settle(c, 1.5f, { c.look(0f, -100f) })
        assertTrue("turned ${gazeYaw(c)} toward something behind", abs(gazeYaw(c)) < 0.12f)
    }

    @Test
    fun theHeadTipsUpAndDownToTheHeightOfWhatItSees() {
        val up = FigureAnim()
        settle(up, 1.5f, { up.look(0f, 40f, 90f) })
        val down = FigureAnim()
        settle(down, 1.5f, { down.look(0f, 40f, 5f) })
        val level = FigureAnim()
        settle(level, 1.5f, { level.look(0f, 40f, Figure.HEAD_Y) })
        assertTrue("looking up ${up.headPitch}", up.headPitch < level.headPitch - 0.2f)
        assertTrue("looking down ${down.headPitch}", down.headPitch > level.headPitch + 0.2f)
        assertTrue("nodded ${up.headPitch}/${down.headPitch} past what a neck can", up.headPitch > -0.6f && down.headPitch < 0.7f)
    }

    @Test
    fun theGazeEasesInAndOutAndTheHeadNeverSnaps() {
        val a = FigureAnim()
        settle(a, 0.5f)
        var prev = gazeYaw(a)
        var biggest = 0f
        var side = 1f
        repeat(120 * 8) { n ->
            if (n % 120 == 0) side = -side
            // Look left, then right, then away altogether.
            if (n < 120 * 6) a.look(side * 60f, 30f)
            a.update(DT, 0f, 0f, 0f, Pose.SIT)
            val g = gazeYaw(a)
            biggest = maxOf(biggest, abs(g - prev))
            prev = g
            assertSane(a, "gaze $n")
        }
        assertTrue("head moved $biggest in one step", biggest < 0.1f)
        assertTrue("head didn't come back to rest (${gazeYaw(a)})", abs(gazeYaw(a)) < 0.12f)
    }

    @Test
    fun theHeadLeadsTheBodyIntoATurn() {
        val a = FigureAnim()
        a.update(DT, 0f, 0f, 0f, Pose.STAND)
        var lead = 0f
        // The body is asked to turn a quarter of the way round; its head goes first.
        repeat(30) {
            a.update(DT, 0f, 0f, 0f, Pose.STAND, yawGoal = 1.5f)
            lead = maxOf(lead, gazeYaw(a))
        }
        assertTrue("head led by only $lead", lead > 0.2f)
        assertTrue("head led by $lead", lead <= 0.55f + 0.05f)
    }

    @Test
    fun standingAboutIsFullOfFidgetsThatAreTheSameEveryRun() {
        fun timeline(seed: Int): List<Int> {
            val a = FigureAnim(seed)
            val marks = ArrayList<Int>()
            var last = 0
            repeat(120 * 60) { n ->
                a.update(DT, 0f, 0f, 0f, Pose.STAND)
                if (a.fidgetCount != last) {
                    marks += n
                    last = a.fidgetCount
                }
                assertSane(a, "idle $n")
            }
            return marks
        }
        val one = timeline(3)
        assertTrue("only ${one.size} fidgets in a minute", one.size in 5..18)
        assertEquals("fidgets aren't repeatable", one, timeline(3))
        assertNotEquals("two kids fidget in step", one, timeline(4))
    }

    @Test
    fun aWalkingKidDoesNotFidgetAndASittingOneDoesNotEither() {
        val a = FigureAnim(seed = 6)
        var z = 0f
        repeat(120 * 20) {
            z += 40f * DT
            a.update(DT, 0f, z, 0f, Pose.WALK)
        }
        assertEquals(0, a.fidgetCount)
        val b = FigureAnim(seed = 6)
        repeat(120 * 20) { b.update(DT, 0f, 0f, 0f, Pose.SIT) }
        assertEquals(0, b.fidgetCount)
    }

    @Test
    fun fidgetsFadeOutWhenTheKidSetsOff() {
        val a = FigureAnim(seed = 8)
        // Stand until a fidget is in full swing (a weight shift shows in the sway)...
        var n = 0
        while (a.fidgetCount == 0 && n < 120 * 30) {
            a.update(DT, 0f, 0f, 0f, Pose.STAND)
            n++
        }
        assertTrue(a.fidgetCount > 0)
        repeat(30) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
        // ...then set off: nothing jumps.
        val prev = FloatArray(RigChecks.JOINTS)
        val now = FloatArray(RigChecks.JOINTS)
        var z = 0f
        var biggest = 0f
        repeat(120) {
            z += 40f * DT
            RigChecks.joints(a, prev)
            a.update(DT, 0f, z, 0f, Pose.WALK)
            RigChecks.joints(a, now)
            for (i in now.indices) biggest = maxOf(biggest, abs(now[i] - prev[i]))
            assertSane(a, "setting off")
        }
        assertTrue("a joint jumped $biggest setting off", biggest < 0.2f)
    }

    @Test
    fun kidsBlinkEveryFewSecondsSmoothly() {
        val a = FigureAnim(seed = 2)
        var blinks = 0
        var shut = false
        var prev = 0f
        var biggest = 0f
        var longest = 0
        var run = 0
        repeat(120 * 60) {
            a.update(DT, 0f, 0f, 0f, Pose.STAND)
            assertTrue(a.blink in 0f..1f)
            if (a.blink > 0.9f && !shut) {
                blinks++
                shut = true
            }
            if (a.blink < 0.3f) shut = false
            run = if (a.blink > 0.02f) run + 1 else 0
            longest = maxOf(longest, run)
            biggest = maxOf(biggest, abs(a.blink - prev))
            prev = a.blink
        }
        assertTrue("$blinks blinks in a minute", blinks in 9..30)
        assertTrue("an eyelid moved $biggest in a step", biggest < 0.3f)
        // A blink is over in a fraction of a second.
        assertTrue("a blink lasted $longest steps", longest <= (0.2f / DT).toInt())
    }

    @Test
    fun breathingRisesAndFallsOnceEveryFewSeconds() {
        val a = FigureAnim()
        var hi = -1f
        var lo = 1f
        var crossings = 0
        var was = 0f
        repeat(120 * 30) {
            a.update(DT, 0f, 0f, 0f, Pose.STAND)
            hi = maxOf(hi, a.breath)
            lo = minOf(lo, a.breath)
            if (was < 0f && a.breath >= 0f) crossings++
            was = a.breath
        }
        assertTrue("breath range $lo..$hi", hi > 0.01f && lo < -0.01f && hi < 0.03f && lo > -0.03f)
        // 30 s at one breath every 3.8 s.
        assertTrue("$crossings breaths in 30 s", crossings in 7..8)
    }

    @Test
    fun identicalFiguresAnimateIdentically() {
        val a = FigureAnim(seed = 5)
        val b = FigureAnim(seed = 5)
        val va = FloatArray(RigChecks.JOINTS)
        val vb = FloatArray(RigChecks.JOINTS)
        var z = 0f
        repeat(120 * 20) { n ->
            if (n > 400) z += 30f * DT
            val pose = if (n > 400) Pose.WALK else Pose.STAND
            a.look(50f, 50f)
            b.look(50f, 50f)
            a.update(DT, 0f, z, 0f, pose)
            b.update(DT, 0f, z, 0f, pose)
        }
        RigChecks.joints(a, va)
        RigChecks.joints(b, vb)
        assertTrue(va.contentEquals(vb))
    }

    // ------------------------------------------------------------------ in the hall

    @Test
    fun kidsInTheHallKeepEveryJointInRangeAndTurnToLookAtAPlayerNearby() {
        val world = HubWorld(GameRegistry.createAll(), null)
        val dt = 1f / 60f
        // Run the crowd for a few minutes, checking every figure as it goes.
        repeat((180f / dt).toInt()) { n ->
            world.update(dt)
            if (n % 10 == 0) {
                for ((i, k) in world.npcs.withIndex()) assertSane(k.anim, "kid $i at step $n")
                assertSane(world.player.anim, "player at step $n")
                assertSane(world.cafe.barista.anim, "barista at step $n")
                assertSane(world.clerk, "clerk at step $n")
            }
        }
        // Find a kid sitting or standing about and put the player off to one side of them.
        val kid = world.npcs.firstOrNull { it.state == Npc.State.SIT || it.state == Npc.State.IDLE }
        assumeTrue("no kid was sitting or standing about", kid != null)
        kid!!
        val side = kid.yaw + 1.0f
        world.player.place(kid.x + kotlin.math.sin(side) * 40f, kid.y + kotlin.math.cos(side) * 40f)
        var looked = 0f
        repeat((1.2f / dt).toInt()) {
            world.update(dt)
            if (kid.state == Npc.State.SIT || kid.state == Npc.State.IDLE) {
                val toPlayer = atan2(world.player.x - kid.x, world.player.y - kid.y) - kid.anim.yaw
                if (abs(AnimMath.wrap(toPlayer)) < 1.5f) looked = maxOf(looked, abs(kid.anim.headYaw + kid.anim.twist))
            }
        }
        assertTrue("the kid never turned to look at the player ($looked)", looked > 0.3f)
    }
}
