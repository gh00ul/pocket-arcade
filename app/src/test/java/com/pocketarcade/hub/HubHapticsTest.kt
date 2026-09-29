package com.pocketarcade.hub

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Haptics
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The hall's haptics: a soft tick when a play prompt appears, and one bump (not a buzz) when the
 * player walks into a wall or a kid, overhead and in first person.
 */
class HubHapticsTest {
    private companion object {
        const val W = 1080f
        const val H = 2400f
        const val DENSITY = 2.75f
        const val DEG = PI.toFloat() / 180f
    }

    /** Counts what the hall asks for instead of vibrating. */
    private class Counting : Haptics(null) {
        var softs = 0
        var bumps = 0
        override fun soft() {
            softs++
        }
        override fun bump() {
            bumps++
        }
    }

    private val games = GameRegistry.createAll()

    private fun world(haptics: Haptics, firstPerson: Boolean, kids: Boolean = false): HubWorld {
        val w = HubWorld(games, null, haptics)
        w.density = DENSITY
        w.setViewport(W, H)
        w.setFirstPerson(firstPerson, animate = false)
        if (!kids) w.npcs.clear()
        return w
    }

    private fun HubWorld.run(seconds: Float) {
        repeat((seconds / FIXED_DT).roundToInt()) { update(FIXED_DT) }
    }

    private fun HubWorld.atTheDoor() {
        player.place(map.spawnX, map.spawnY)
        camera.setLook(PI.toFloat(), HubCamera.REST_PITCH_DEG * DEG)
        update(FIXED_DT)
    }

    /** Pushes the stick straight up the screen (forward, and up the main aisle overhead) at nearly full tilt. */
    private fun HubWorld.pushUp() {
        pointerDown(1, 200f, 1800f)
        pointerMove(1, 200f, 1800f - joystick.radius * Joystick.FULL_AT)
    }

    /** Walks on until the player has been stopped for a while (or [limit] seconds pass); returns the seconds walked. */
    private fun HubWorld.walkUntilStopped(limit: Float): Float {
        var t = 0f
        var still = 0f
        while (t < limit && still < 0.5f) {
            update(FIXED_DT)
            t += FIXED_DT
            still = if (player.speedFrac < 0.05f && t > 0.5f) still + FIXED_DT else 0f
        }
        return t
    }

    @Test
    fun walkingHeadOnIntoTheEndOfTheAisleBumpsOnceInFirstPersonAndOverhead() {
        for (firstPerson in booleanArrayOf(true, false)) {
            val h = Counting()
            val w = world(h, firstPerson)
            w.atTheDoor()
            h.bumps = 0
            w.pushUp()
            val walked = w.walkUntilStopped(40f)
            assertTrue("never came to a stop (first person $firstPerson, $walked s)", walked < 40f)
            assertEquals("one bump on the stop (first person $firstPerson)", 1, h.bumps)
            // Leaning on the wall is not a stream of bumps.
            w.run(2f)
            assertEquals("still one bump after leaning on it (first person $firstPerson)", 1, h.bumps)
        }
    }

    @Test
    fun lettingGoOfTheStickIsNotABump() {
        for (firstPerson in booleanArrayOf(true, false)) {
            val h = Counting()
            val w = world(h, firstPerson)
            w.atTheDoor()
            w.pushUp()
            w.run(0.6f)
            assertTrue(w.player.speedFrac > 0.5f)
            w.pointerUp(1, 0f, 0f)
            w.run(0.5f)
            assertEquals("stopping by choice (first person $firstPerson)", 0, h.bumps)
        }
    }

    @Test
    fun turningInTheAisleAndWalkingOnIsNotABump() {
        val h = Counting()
        val w = world(h, firstPerson = true)
        w.atTheDoor()
        w.pushUp()
        w.run(1f)
        // Swing the stick right round to walk back the way we came: braking is gradual, not a wall.
        w.pointerMove(1, 200f, 1800f + w.joystick.radius * Joystick.FULL_AT)
        w.run(1f)
        assertEquals(0, h.bumps)
    }

    @Test
    fun walkingIntoAKidBumpsOnceAndAKidBumpingYouDoesNot() {
        // A kid dead ahead of a walking player.
        val h = Counting()
        val w = world(h, firstPerson = true, kids = true)
        w.npcs.retainAll(listOf(w.npcs[0]))
        val kid = w.npcs[0]
        w.atTheDoor()
        kid.x = w.player.x
        kid.y = w.player.y - 40f
        h.bumps = 0
        w.pushUp()
        var closest = Float.MAX_VALUE
        repeat((2.5f / FIXED_DT).toInt()) {
            w.update(FIXED_DT)
            closest = minOf(closest, hypot(kid.x - w.player.x, kid.y - w.player.y))
        }
        assertTrue("never reached the kid ($closest)", closest < Body.RADIUS + HubWorld.KID_RADIUS)
        assertTrue("no bump for walking into a kid", h.bumps >= 1)
        assertTrue("a bump per meeting, not per step (${h.bumps})", h.bumps <= 3)

        // A kid on top of a player who is standing still: nothing to feel.
        val g = Counting()
        val v = world(g, firstPerson = true, kids = true)
        v.npcs.retainAll(listOf(v.npcs[0]))
        v.atTheDoor()
        v.npcs[0].x = v.player.x + 4f
        v.npcs[0].y = v.player.y + 4f
        v.run(0.3f)
        assertEquals(0, g.bumps)
    }

    @Test
    fun aPlayPromptAppearingGivesOneSoftTick() {
        for (firstPerson in booleanArrayOf(true, false)) {
            val h = Counting()
            val w = world(h, firstPerson)
            w.atTheDoor()
            assertEquals("nothing at the door", 0, h.softs)
            val spot = w.map.spots.first { it.type == SpotType.MACHINE }
            w.player.place(spot.area.centerX, spot.area.centerY)
            w.run(0.5f)
            assertEquals("the prompt appeared (first person $firstPerson)", 1, h.softs)
            // Staying put in it is not another tick.
            w.run(0.5f)
            assertEquals(1, h.softs)
        }
    }
}
