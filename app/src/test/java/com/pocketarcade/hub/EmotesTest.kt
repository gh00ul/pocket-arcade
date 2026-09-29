package com.pocketarcade.hub

import com.pocketarcade.games.GameRegistry
import com.pocketarcade.hub.RigChecks.DT
import com.pocketarcade.hub.RigChecks.assertSane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Waves and cheers: rare, seeded, repeatable, and they only ever change the pose a kid shows. */
class EmotesTest {
    /** Drives [e] through [encounters] meetings with the player (each [meet] seconds long, [gap] seconds apart); returns the poses seen. */
    private fun meetings(e: Emotes, encounters: Int, meet: Float = 3f, gap: Float = 25f, standing: Boolean = true): List<Pose?> {
        val seen = ArrayList<Pose?>()
        repeat(encounters) {
            var t = 0f
            var waved: Pose? = null
            while (t < meet) {
                e.update(0.05f, true, standing, false, false)?.let { waved = it }
                t += 0.05f
            }
            seen += waved
            t = 0f
            while (t < gap) {
                e.update(0.05f, false, standing, false, false)
                t += 0.05f
            }
        }
        return seen
    }

    /** A kid who has been about a while (long enough that their first-wave cooldown, which staggers a crowd, is over). */
    private fun settled(seed: Int): Emotes {
        val e = Emotes(seed)
        repeat(240) { e.update(0.05f, false, true, false, false) }
        return e
    }

    @Test
    fun aKidWavesAtSomeOfThePlayersPassingsButNotAll() {
        var waves = 0
        var total = 0
        for (seed in 1..40) {
            val seen = meetings(Emotes(seed), 10)
            waves += seen.count { it == Pose.WAVE }
            total += seen.size
            assertTrue(seen.all { it == null || it == Pose.WAVE })
        }
        val rate = waves.toFloat() / total
        assertTrue("waved at $rate of encounters", rate in 0.2f..0.5f)
    }

    @Test
    fun aWaveLastsAboutAsLongAsSetAndStartsAfterAMomentsHesitation() {
        // Find a kid who waves at the first meeting.
        for (seed in 1..200) {
            val e = settled(seed)
            var t = 0f
            var start = -1f
            var end = -1f
            while (t < 6f) {
                val p = e.update(0.02f, true, true, false, false)
                if (p == Pose.WAVE && start < 0f) start = t
                if (p == Pose.WAVE) end = t
                t += 0.02f
            }
            if (start >= 0f) {
                assertTrue("waved at once ($start)", start >= Emotes.WAVE_DELAY - 0.03f)
                assertTrue("hesitated too long ($start)", start <= Emotes.WAVE_DELAY + Emotes.WAVE_DELAY_SPREAD + 0.05f)
                assertEquals(Emotes.WAVE_TIME, end - start, 0.1f)
                return
            }
        }
        throw AssertionError("no kid waved at a first meeting in 200 tries")
    }

    @Test
    fun aKidDoesNotWaveTwiceInARowAndNeverWhileBusy() {
        for (seed in 1..60) {
            // Never waves while walking or holding something (standing = false), however often we meet.
            val busy = meetings(Emotes(seed), 20, standing = false)
            assertTrue(busy.all { it == null })
            // Meeting again straight after a wave is met with a straight face (the cooldown).
            val e = settled(seed)
            val first = meetings(e, 1, meet = 6f, gap = 0.5f)
            if (first[0] == Pose.WAVE) {
                val again = meetings(e, 4, meet = 3f, gap = 0.5f)
                assertTrue("waved again inside the cooldown (seed $seed)", again.all { it == null })
            }
        }
    }

    @Test
    fun aWaveStopsIfTheKidSetsOffOrThePlayerLeaves() {
        for (seed in 1..200) {
            val e = settled(seed)
            var t = 0f
            var waving = false
            while (t < 2f && !waving) {
                waving = e.update(0.02f, true, true, false, false) == Pose.WAVE
                t += 0.02f
            }
            if (!waving) continue
            // Walking off ends it at once.
            assertNull(e.update(0.02f, true, false, false, true))
            assertNull(e.update(0.02f, false, false, false, true))
            return
        }
        throw AssertionError("nobody waved")
    }

    @Test
    fun theSameKidWavesTheSameWayEveryRunAndDifferentKidsDifferently() {
        assertEquals(meetings(Emotes(7), 30), meetings(Emotes(7), 30))
        val a = meetings(Emotes(7), 30)
        val b = meetings(Emotes(8), 30)
        val c = meetings(Emotes(9), 30)
        assertTrue("three kids waved in perfect step", a != b || b != c)
    }

    @Test
    fun celebratingIsACheerOrAClapAfterAShortDelayAndThenOver() {
        val e = Emotes(4)
        e.celebrate(0.5f, seated = false)
        var first = -1f
        var last = -1f
        var t = 0f
        var pose: Pose? = null
        while (t < 6f) {
            val p = e.update(0.02f, false, true, false, false)
            if (p != null) {
                if (first < 0f) first = t
                last = t
                pose = p
                assertTrue(p == Pose.CHEER || p == Pose.CLAP)
            }
            t += 0.02f
        }
        assertTrue("cheered too soon ($first)", first >= 0.5f - 0.03f)
        assertTrue("cheered too late ($first)", first <= 0.5f + Emotes.CELEBRATE_SPREAD + 0.05f)
        assertEquals(Emotes.CELEBRATE_TIME, last - first, 0.1f)
        assertTrue(pose != null)
        assertTrue(!e.active)
        // A seated kid can only clap; a walking one is only passing.
        val s = Emotes(4)
        s.celebrate(0f, seated = true)
        var seen: Pose? = null
        repeat(200) { s.update(0.02f, false, false, true, false)?.let { seen = it } }
        assertEquals(Pose.CLAP, seen)
        val w = Emotes(4)
        w.celebrate(0f, seated = false)
        repeat(200) { assertNull(w.update(0.02f, false, false, false, true)) }
    }

    @Test
    fun aWavingAndCheeringFigureStaysInRangeAndBlendsIn() {
        for (pose in listOf(Pose.WAVE, Pose.CLAP)) {
            val a = FigureAnim(seed = 3)
            repeat(120) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
            var peak = 0f
            var swing = 0f
            var prev = a.armPitch[1]
            var biggest = 0f
            repeat(240) { n ->
                a.update(DT, 0f, 0f, 0f, pose)
                assertSane(a, "$pose $n")
                peak = minOf(peak, a.armPitch[1])
                biggest = maxOf(biggest, abs(a.armPitch[1] - prev))
                prev = a.armPitch[1]
                if (n > 60) swing = maxOf(swing, abs(a.armRoll[1]))
            }
            assertTrue("$pose: arm never came up ($peak)", if (pose == Pose.WAVE) peak < -2.5f else peak < -1.2f)
            assertTrue("$pose: arm jumped $biggest in a step", biggest < 0.25f)
            assertTrue("$pose: no movement in the hand ($swing)", swing > 0.15f)
        }
        // A clap brings both hands in toward each other; a wave moves the right hand only.
        val c = FigureAnim(seed = 3)
        repeat(240) { c.update(DT, 0f, 0f, 0f, Pose.CLAP) }
        var mirrored = true
        repeat(60) {
            c.update(DT, 0f, 0f, 0f, Pose.CLAP)
            if (abs(c.armRoll[0] + c.armRoll[1]) > 0.05f) mirrored = false
            assertTrue("hands crossed (${c.armRoll[0]}, ${c.armRoll[1]})", c.armRoll[1] < 0.02f && c.armRoll[0] > -0.02f)
        }
        assertTrue("the clap isn't symmetrical", mirrored)
    }

    // ------------------------------------------------------------------ in the hall

    private class Trace(val x: FloatArray, val y: FloatArray, val state: IntArray)

    /** Runs a hall for [secs] and records every kid's position and state at each step; [poke] can do things to the world on the way. */
    private fun run(secs: Float, dt: Float, poke: (HubWorld, Int) -> Unit): Trace {
        val world = HubWorld(GameRegistry.createAll(), null)
        val steps = (secs / dt).toInt()
        val n = world.npcs.size
        val x = FloatArray(steps * n)
        val y = FloatArray(steps * n)
        val st = IntArray(steps * n)
        for (s in 0 until steps) {
            poke(world, s)
            world.update(dt)
            for (i in 0 until n) {
                val k = world.npcs[i]
                x[s * n + i] = k.x
                y[s * n + i] = k.y
                st[s * n + i] = k.state.ordinal * 100 + k.queueSpot + 1
            }
        }
        return Trace(x, y, st)
    }

    @Test
    fun cheeringAndWavingNeverChangeWhereKidsGoOrWhenTheyMove() {
        val dt = 1f / 30f
        val plain = run(240f, dt) { _, _ -> }
        val excited = run(240f, dt) { w, s ->
            if (s % 90 == 0) w.celebratePurchase()
        }
        assertTrue("positions differ", plain.x.contentEquals(excited.x) && plain.y.contentEquals(excited.y))
        assertTrue("states differ", plain.state.contentEquals(excited.state))
    }

    @Test
    fun kidsNearThePrizeCounterCheerWhenSomeoneBuysAndTheClerkClaps() {
        val world = HubWorld(GameRegistry.createAll(), null)
        val dt = 1f / 60f
        repeat(120) { world.update(dt) }
        // Bring some kids that are not on the move (a walking kid is only passing) up to the counter.
        val near = world.npcs.filter { it.state != Npc.State.WALK }.take(4)
        assertTrue("every kid was walking", near.size >= 3)
        for ((i, k) in near.withIndex()) {
            k.x = world.map.clerkX + 30f * (i + 1)
            k.y = world.map.clerkY + 60f
        }
        world.celebratePurchase()
        val cheered = HashSet<Npc>()
        var clerkClapped = false
        repeat((3f / dt).toInt()) {
            world.update(dt)
            for (k in near) if (k.pose == Pose.CHEER || k.pose == Pose.CLAP) cheered += k
            if (world.clerk.blender.target == Pose.CLAP) clerkClapped = true
        }
        // (One that got up to walk somewhere in the meantime may miss it.)
        assertTrue("only ${cheered.size} of ${near.size} kids at the counter joined in", cheered.size >= near.size - 1)
        assertTrue("the clerk didn't clap", clerkClapped)
        // And it ends.
        repeat((4f / dt).toInt()) { world.update(dt) }
        assertNotEquals(Pose.CLAP, world.clerk.blender.target)
    }

    @Test
    fun aPurchaseIsOnlyCheeredAtTheCounterAndOnceTheShopCloses() {
        val world = HubWorld(GameRegistry.createAll(), null)
        val dt = 1f / 60f
        repeat(60) { world.update(dt) }
        world.noteOwned(3)
        world.noteOwned(5)
        world.hallResumed()
        // Nobody was at the prize counter when the count went up (a save loading in, say): no cheer.
        repeat(120) {
            world.update(dt)
            assertTrue(world.clerk.blender.target != Pose.CLAP)
        }
    }
}
