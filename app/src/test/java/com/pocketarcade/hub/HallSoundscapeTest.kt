package com.pocketarcade.hub

import com.pocketarcade.engine.Sfx
import com.pocketarcade.engine.audio.Attract
import com.pocketarcade.engine.audio.Spatial
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** The hall as heard by the player: ears, cabinets, café, kids' footsteps and the crowd. */
class HallSoundscapeTest {
    private val games = GameRegistry.createAll()

    private class Played(val sfx: Sfx, val x: Float, val z: Float, val volume: Float)

    private class Recorder : HallSoundSink {
        var listenerX = 0f
        var listenerZ = 0f
        var listenerYaw = 0f
        var sourceCount = 0
        var sources = FloatArray(0)
        var sourcesZ = FloatArray(0)
        var kinds = IntArray(0)
        var cafeX = Float.NaN
        var cafeZ = Float.NaN
        var lastCrowd = -1f
        val played = ArrayList<Played>()
        var playedThisFrame = 0

        override fun setListener(x: Float, z: Float, yawRad: Float) {
            listenerX = x
            listenerZ = z
            listenerYaw = yawRad
        }

        override fun setHallSources(xs: FloatArray, zs: FloatArray, kinds: IntArray, count: Int) {
            sourceCount = count
            sources = xs.copyOf(count)
            sourcesZ = zs.copyOf(count)
            this.kinds = kinds.copyOf(count)
        }

        override fun setCafe(x: Float, z: Float) {
            cafeX = x
            cafeZ = z
        }

        override fun setCrowd(level: Float) {
            lastCrowd = level
        }

        override fun playAt(sfx: Sfx, x: Float, z: Float, volume: Float, pitch: Float) {
            played += Played(sfx, x, z, volume)
            playedThisFrame++
        }
    }

    @Test
    fun everyMachineCabinetIsPublishedWithItsOwnKindOfBleep() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        HallSoundscape(world, rec).publish()
        val machines = world.map.props.filter { it.kind == PropKind.MACHINE }
        assertTrue(machines.isNotEmpty())
        assertEquals(machines.size, rec.sourceCount)
        for (i in machines.indices) {
            assertEquals(machines[i].centerX, rec.sources[i], 1e-4f)
            assertEquals(machines[i].centerZ, rec.sourcesZ[i], 1e-4f)
            assertEquals(Attract.kindFor(games[machines[i].machine].id), rec.kinds[i])
        }
        // Every game has a palette of its own (not the generic one), and they aren't all the same.
        for (g in games) assertTrue("${g.id} has no attract palette", Attract.kindFor(g.id) != Attract.GENERIC)
        assertTrue(games.map { Attract.kindFor(it.id) }.toSet().size == games.size)
    }

    @Test
    fun theCafeIsPublishedInsideTheCafe() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        HallSoundscape(world, rec).publish()
        assertTrue(rec.cafeX in CafeLayout.FLOOR_X0..CafeLayout.FLOOR_X1)
        assertTrue(rec.cafeZ in CafeLayout.FLOOR_Z0..CafeLayout.FLOOR_Z1)
    }

    @Test
    fun theEarsFollowThePlayerAndTheCamerasHeading() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        val sound = HallSoundscape(world, rec)
        world.player.x = 250f
        world.player.y = 400f
        sound.update(0.016f)
        assertEquals(250f, rec.listenerX, 0f)
        assertEquals(400f, rec.listenerZ, 0f)
        // Overhead the camera looks at the back wall.
        assertEquals(PI.toFloat(), abs(rec.listenerYaw), 1e-4f)
        // First person: the gaze.
        world.camera.setFirstPerson(true, animate = false)
        world.camera.setLook(1.2f, 0f)
        sound.update(0.016f)
        assertEquals(1.2f, rec.listenerYaw, 1e-4f)
    }

    @Test
    fun listenerYawBlendsTheShortWayRound() {
        val north = PI.toFloat()
        assertEquals(north, abs(HallSoundscape.listenerYaw(0f, 0f)), 1e-4f)
        assertEquals(0.7f, HallSoundscape.listenerYaw(1f, 0.7f), 1e-4f)
        // Half way from north (pi) to a gaze of -3 is close to north, through the short arc, not through 0.
        val mid = HallSoundscape.listenerYaw(0.5f, -3f)
        assertTrue("mid $mid", abs(abs(mid) - north) < 0.1f)
        // Never outside (-pi, pi].
        for (a in floatArrayOf(-3.1f, -1f, 0f, 2f, 3.1f)) for (f in floatArrayOf(0f, 0.3f, 0.8f, 1f)) {
            val y = HallSoundscape.listenerYaw(f, a)
            assertTrue(y > -north - 1e-4f && y <= north + 1e-4f)
        }
    }

    @Test
    fun kidsWalkingNearbyPatterQuietlyFromWhereTheyAre() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        val sound = HallSoundscape(world, rec)
        val dt = 1f / 60f
        var worstFrame = 0
        // A couple of minutes of hall, with the player standing in the middle of the floor.
        world.player.x = 304f
        world.player.y = 600f
        repeat(60 * 120) {
            rec.playedThisFrame = 0
            world.update(dt)
            sound.update(dt)
            worstFrame = maxOf(worstFrame, rec.playedThisFrame)
        }
        val steps = rec.played.filter { it.sfx == Sfx.STEP }
        assertTrue("expected kids to be heard walking, got ${steps.size}", steps.size > 20)
        assertTrue("too many at once: $worstFrame", worstFrame <= HallSoundscape.STEPS_PER_FRAME + 1)
        for (s in steps) {
            assertEquals(HallSoundscape.STEP_VOLUME, s.volume, 1e-6f)
            assertTrue("a step from ${hypot(s.x - rec.listenerX, s.z - rec.listenerZ)} away", hypot(s.x - rec.listenerX, s.z - rec.listenerZ) <= HallSoundscape.STEP_RANGE + 40f)
            assertTrue(s.x in 0f..HubLayout.WIDTH.toFloat() && s.z in 0f..HubLayout.DEPTH.toFloat())
        }
        assertTrue("footsteps stay quiet", HallSoundscape.STEP_VOLUME < 0.22f)
    }

    @Test
    fun cheersAreRareAndFaint() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        val sound = HallSoundscape(world, rec)
        val dt = 1f / 60f
        world.player.x = 304f
        world.player.y = 500f
        repeat(60 * 300) {
            world.update(dt)
            sound.update(dt)
        }
        val cheers = rec.played.filter { it.sfx == Sfx.CHEER }
        // At most one every cooldown, over five minutes.
        assertTrue("${cheers.size} cheers", cheers.size <= (300f / HallSoundscape.CHEER_COOLDOWN).toInt() + 1)
        for (c in cheers) assertTrue(c.volume <= 0.12f)
    }

    @Test
    fun theCrowdSwellsNearTheCafeAndAmongKids() {
        val quiet = HallSoundscape.crowdLevel(0f, 900f)
        val kids = HallSoundscape.crowdLevel(HallSoundscape.CROWD_FULL, 900f)
        val cafe = HallSoundscape.crowdLevel(0f, 0f)
        val both = HallSoundscape.crowdLevel(HallSoundscape.CROWD_FULL, 0f)
        assertTrue(quiet in 0f..1f && kids in 0f..1f && cafe in 0f..1f && both in 0f..1f)
        assertTrue("kids $kids over empty $quiet", kids > quiet + 0.2f)
        assertTrue("café $cafe over empty $quiet", cafe > quiet + 0.3f)
        assertTrue(both >= kids && both >= cafe)
        // Monotone in both.
        var last = -1f
        var w = 0f
        while (w <= 8f) {
            val c = HallSoundscape.crowdLevel(w, 500f)
            assertTrue(c >= last)
            last = c
            w += 0.25f
        }
        last = 2f
        var d = 0f
        while (d <= 600f) {
            val c = HallSoundscape.crowdLevel(1f, d)
            assertTrue(c <= last)
            last = c
            d += 20f
        }
    }

    @Test
    fun standingAtTheCafeMakesTheHallBusierThanTheFarCorner() {
        val world = HubWorld(games, null)
        val rec = Recorder()
        val sound = HallSoundscape(world, rec)
        world.player.x = HallSoundscape.CAFE_X + 20f
        world.player.y = HallSoundscape.CAFE_Z + 30f
        sound.update(0.2f)
        val atCafe = rec.lastCrowd
        world.player.x = 560f
        world.player.y = 120f
        sound.update(0.2f)
        val far = rec.lastCrowd
        assertTrue("café $atCafe vs far $far", atCafe > far)
    }

    @Test
    fun theHallCarriesOnWithNoAudioAtAll() {
        // No sink: nothing to tell, nothing to break.
        val world = HubWorld(games, null)
        val sound = HallSoundscape(world, null)
        sound.publish()
        repeat(120) {
            world.update(1f / 60f)
            sound.update(1f / 60f)
        }
        assertTrue(Spatial.MAX_DISTANCE > HallSoundscape.STEP_RANGE)
    }
}
