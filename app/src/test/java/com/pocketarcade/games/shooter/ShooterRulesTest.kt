package com.pocketarcade.games.shooter

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.RoundDriver
import com.pocketarcade.games.playRound
import com.pocketarcade.games.simFx
import com.pocketarcade.games.tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rule-level tests for the shootout: scoring, the gun, enemies that shoot back, the boss, input and round hygiene. */
class ShooterRulesTest {
    private companion object {
        const val GOON = 0
        const val CIVILIAN = 1
        const val DRONE = 2
        const val GOLD = 3
        /** Far-row crate spots on the left, middle and right, and a near sandbag spot. */
        const val FAR_LEFT = 3
        const val FAR_MID = 4
        const val FAR_RIGHT = 5
        const val NEAR = 8
        /** Open sky: nothing to hit up there. */
        const val SKY_X = 180f
        const val SKY_Y = 40f
    }

    private var ms = 0L

    /** A fresh round with natural spawns held back, so a test places exactly what it needs. */
    private fun quietGame(seed: Long = 1L): ShooterGame = ShooterGame().also {
        it.seed = seed
        it.start(simFx)
        it.botHoldSpawns(999f)
    }

    private fun ShooterGame.steps(seconds: Float, timeLeft: Float = 30f) {
        repeat((seconds / FIXED_DT).toInt()) {
            update(FIXED_DT, timeLeft)
            ms += 8
        }
    }

    private fun ShooterGame.shoot(slot: Int, id: Long) {
        val a = FloatArray(2)
        botAim(slot, a)
        tap(this, id, a[0], a[1], ms)
    }

    private fun ShooterGame.reload(id: Long) {
        tap(this, id, botReloadX, botReloadY, ms)
        steps(ShooterTuning.RELOAD_SECONDS + 0.05f)
    }

    @Test
    fun shootingABanditScoresAndTheCoverInFrontOfItStopsBullets() {
        val g = quietGame()
        val slot = g.botSpawn(GOON, FAR_MID)
        g.steps(0.05f)
        assertEquals(GOON, g.botKind(slot))
        // The crate in front of it: a miss that leaves a bullet hole.
        val a = FloatArray(2)
        g.botAim(slot, a)
        tap(g, 1L, a[0], a[1] + 60f, ms)
        assertEquals(0, g.score)
        assertEquals(1, g.botShots)
        assertEquals(0, g.botHits)
        assertEquals(1, g.botDecals)
        assertEquals(GOON, g.botKind(slot))
        // Right on it.
        g.shoot(slot, 2L)
        assertEquals(ShooterTuning.GOON_POINTS, g.score)
        assertEquals(1, g.botHits)
        assertEquals(-1, g.botKind(slot))
        // Just wide of where it was is open street: nothing.
        tap(g, 3L, a[0] + 70f, a[1] - 30f, ms)
        assertEquals(ShooterTuning.GOON_POINTS, g.score)
    }

    @Test
    fun shootingACivilianCostsAndBreaksTheCombo() {
        val g = quietGame()
        for (spot in intArrayOf(FAR_LEFT, FAR_MID, FAR_RIGHT)) g.shoot(g.botSpawn(GOON, spot), spot.toLong())
        assertEquals(3 * ShooterTuning.GOON_POINTS, g.score)
        assertEquals(3, g.botCombo)
        g.steps(0.6f)
        val civ = g.botSpawn(CIVILIAN, FAR_MID)
        g.steps(0.05f)
        assertEquals(CIVILIAN, g.botKind(civ))
        g.shoot(civ, 10L)
        assertEquals(3 * ShooterTuning.GOON_POINTS - ShooterTuning.CIVILIAN_PENALTY, g.score)
        assertEquals(1, g.botCiviliansShot)
        assertEquals(0, g.botCombo)
        // Civilians aren't hits: they count against accuracy.
        assertEquals(4, g.botShots)
        assertEquals(3, g.botHits)
    }

    @Test
    fun theGoldDroneIsWorthFarMoreThanAPlainOne() {
        val g = quietGame()
        val drone = g.botSpawn(DRONE, -1)
        g.steps(0.05f)
        assertEquals(DRONE, g.botKind(drone))
        g.shoot(drone, 1L)
        val plain = g.score
        assertEquals(ShooterTuning.DRONE_POINTS, plain)
        g.steps(0.6f)
        val gold = g.botSpawn(GOLD, -1)
        g.steps(0.05f)
        assertEquals(GOLD, g.botKind(gold))
        g.shoot(gold, 2L)
        val bonus = g.score - plain
        assertEquals(ShooterTuning.GOLD_POINTS, bonus)
        assertTrue(bonus >= plain * 3)
    }

    @Test
    fun combosMultiplyPoints() {
        val g = quietGame()
        var id = 1L
        repeat(ShooterTuning.COMBO_STEP) {
            g.shoot(g.botSpawn(GOON, FAR_MID), id++)
            g.steps(0.6f)
            if (g.botAmmo == 0) g.reload(id++)
        }
        assertEquals(ShooterTuning.COMBO_STEP * ShooterTuning.GOON_POINTS, g.score)
        val before = g.score
        g.shoot(g.botSpawn(GOON, FAR_MID), id++)
        assertEquals(2 * ShooterTuning.GOON_POINTS, g.score - before)
        // A miss resets it.
        g.reload(id++)
        tap(g, id++, SKY_X, SKY_Y, ms)
        assertEquals(0, g.botCombo)
    }

    @Test
    fun aBanditThatShootsBackCostsAHeartUnlessYouGetItFirst() {
        val g = quietGame()
        // Bank some points so the penalty shows.
        g.shoot(g.botSpawn(GOON, FAR_LEFT), 1L)
        g.shoot(g.botSpawn(GOON, FAR_RIGHT), 2L)
        g.steps(0.6f)
        val before = g.score
        val gunner = g.botSpawn(GOON, FAR_MID, gunner = true, fireDelay = 0.1f)
        var t = 0f
        var told = false
        while (g.botHurts == 0 && t < 3f) {
            g.steps(FIXED_DT)
            t += FIXED_DT
            if (g.botTelling(gunner)) told = true
        }
        assertTrue("it warned first", told)
        assertEquals(1, g.botHurts)
        assertEquals(ShooterTuning.HEARTS - 1, g.botHearts)
        assertEquals(before - ShooterTuning.HURT_PENALTY, g.score)
        assertEquals(0, g.botCombo)
        g.steps(1.5f)

        // This time, drop it during its warning: no shot, and a quick-draw bonus.
        val quick = g.botSpawn(GOON, FAR_MID, gunner = true, fireDelay = 0.1f)
        while (!g.botTelling(quick)) g.steps(FIXED_DT)
        val s0 = g.score
        g.shoot(quick, 5L)
        assertEquals(ShooterTuning.GOON_POINTS + ShooterTuning.QUICK_DRAW_BONUS, g.score - s0)
        g.steps(2f)
        assertEquals(1, g.botHurts)
    }

    @Test
    fun losingTheLastHeartKnocksYouDownThenYouGetUp() {
        val g = quietGame()
        repeat(ShooterTuning.HEARTS) {
            val slot = g.botSpawn(GOON, FAR_MID, gunner = true, fireDelay = 0f)
            var t = 0f
            val hurts = g.botHurts
            while (g.botHurts == hurts && t < 3f) {
                g.steps(FIXED_DT)
                t += FIXED_DT
            }
            g.steps(0.8f)
            assertEquals(-1, g.botKind(slot))
        }
        assertTrue(g.botDown)
        // Down: taps don't fire.
        val shots = g.botShots
        tap(g, 9L, SKY_X, SKY_Y, ms)
        assertEquals(shots, g.botShots)
        g.steps(ShooterTuning.DOWN_SECONDS)
        assertFalse(g.botDown)
        assertEquals(ShooterTuning.HEARTS, g.botHearts)
    }

    @Test
    fun anEmptyGunDryFiresAndNeverScores() {
        val g = quietGame()
        var id = 1L
        repeat(ShooterTuning.CLIP) { tap(g, id++, SKY_X, SKY_Y, ms) }
        assertEquals(0, g.botAmmo)
        assertEquals(ShooterTuning.CLIP, g.botShots)
        val slot = g.botSpawn(GOON, FAR_MID)
        g.steps(0.05f)
        repeat(3) { g.shoot(slot, id++) }
        assertEquals(0, g.score)
        assertEquals(ShooterTuning.CLIP, g.botShots)
        assertEquals(GOON, g.botKind(slot))
    }

    @Test
    fun reloadingRefillsTheGunAfterTheReloadTime() {
        val g = quietGame()
        var id = 1L
        repeat(4) { tap(g, id++, SKY_X, SKY_Y, ms) }
        assertEquals(ShooterTuning.CLIP - 4, g.botAmmo)
        tap(g, id++, g.botReloadX, g.botReloadY, ms)
        assertTrue(g.botReloading)
        assertEquals(0, g.botAmmo)
        // Taps while reloading don't fire.
        tap(g, id++, SKY_X, SKY_Y, ms)
        assertEquals(4, g.botShots)
        g.steps(ShooterTuning.RELOAD_SECONDS - 0.05f)
        assertTrue(g.botReloading)
        g.steps(0.1f)
        assertFalse(g.botReloading)
        assertEquals(ShooterTuning.CLIP, g.botAmmo)
        // Off-screen taps reload too (the classic "shoot away from the screen").
        tap(g, id++, SKY_X, SKY_Y, ms)
        tap(g, id++, -20f, 300f, ms)
        assertTrue(g.botReloading)
        g.steps(ShooterTuning.RELOAD_SECONDS + 0.05f)
        assertEquals(ShooterTuning.CLIP, g.botAmmo)
        // A full gun doesn't bother.
        tap(g, id++, g.botReloadX, g.botReloadY, ms)
        assertFalse(g.botReloading)
    }

    @Test
    fun twoFingersLandingTogetherFireOneShotEach() {
        val g = quietGame()
        val a = g.botSpawn(GOON, FAR_LEFT)
        val b = g.botSpawn(GOON, FAR_RIGHT)
        g.steps(0.05f)
        val pa = FloatArray(2)
        val pb = FloatArray(2)
        g.botAim(a, pa)
        g.botAim(b, pb)
        g.onTouch(TouchType.DOWN, 1L, pa[0], pa[1], ms)
        g.onTouch(TouchType.DOWN, 2L, pb[0], pb[1], ms)
        // A repeated DOWN for a finger that's still down changes nothing.
        g.onTouch(TouchType.DOWN, 1L, pa[0], pa[1], ms)
        for (k in 1..5) {
            g.onTouch(TouchType.MOVE, 1L, pa[0] + k, pa[1], ms + k * 8)
            g.onTouch(TouchType.MOVE, 2L, pb[0] - k, pb[1], ms + k * 8)
        }
        assertEquals(2, g.botPointers)
        g.onTouch(TouchType.UP, 2L, pb[0], pb[1], ms + 60)
        g.onTouch(TouchType.UP, 1L, pa[0], pa[1], ms + 70)
        assertEquals(2, g.botShots)
        assertEquals(2, g.botHits)
        assertEquals(2 * ShooterTuning.GOON_POINTS, g.score)
        assertEquals(ShooterTuning.CLIP - 2, g.botAmmo)
        assertEquals(0, g.botPointers)
    }

    @Test
    fun cancelledFingersAreForgottenAndANewOneFires() {
        val g = quietGame()
        val d = RoundDriver(g, 3L)
        g.botHoldSpawns(999f)
        d.play(0.3f)
        g.onTouch(TouchType.DOWN, 1L, SKY_X, SKY_Y, d.ms)
        assertEquals(1, g.botShots)
        d.pause()
        assertEquals(0, g.botPointers)
        // The lost finger's late events do nothing.
        g.onTouch(TouchType.MOVE, 1L, 100f, 300f, d.ms)
        g.onTouch(TouchType.UP, 1L, 100f, 300f, d.ms)
        assertEquals(1, g.botShots)
        val slot = g.botSpawn(GOON, NEAR)
        d.play(0.05f)
        val a = FloatArray(2)
        g.botAim(slot, a)
        g.onTouch(TouchType.DOWN, 2L, a[0], a[1], d.ms)
        assertEquals(2, g.botShots)
        assertEquals(ShooterTuning.GOON_POINTS, g.score)
        // The same id may come back after a cancel, and fires again.
        d.pause()
        g.onTouch(TouchType.DOWN, 2L, SKY_X, SKY_Y, d.ms)
        assertEquals(3, g.botShots)
    }

    @Test
    fun theBossArrivesOnTimeAndCanBeBeaten() {
        val g = ShooterGame()
        val d = RoundDriver(g, 11L)
        d.play(ShooterTuning.BOSS_AT - 1f)
        assertFalse(g.botBossPresent)
        d.play(1.1f)
        assertTrue("the boss wave starts", g.botBossPresent)
        assertEquals(3, g.botWave)
        d.play(ShooterTuning.BOSS_ENTER_SECONDS)
        assertTrue(g.botBossFighting)
        val a = FloatArray(2)
        var id = 100L
        var shots = 0
        while (!g.botCleared && shots < 400) {
            if (g.botAmmo == 0) {
                if (!g.botReloading) tap(g, id++, g.botReloadX, g.botReloadY, d.ms)
                d.play(0.1f)
                continue
            }
            val part = when {
                g.botBossWeak(2) -> 2
                g.botBossWeak(0) -> 0
                g.botBossWeak(1) -> 1
                else -> 3
            }
            g.botBossAim(part, a)
            tap(g, id++, a[0], a[1], d.ms)
            shots++
            d.play(0.12f)
        }
        assertTrue("the boss went down", g.botCleared)
        assertEquals(0, g.botBossHp)
        assertEquals(ShooterTuning.BOSS_BONUS_TICKETS, g.bonusTickets)
        val score = g.score
        // The round finishes early once the explosions die down, with nothing more to score.
        d.play(5f)
        assertTrue(g.finished)
        d.end()
        assertEquals(score, g.score)
    }

    @Test
    fun theBossHullIsArmouredAndItsWeakPointsHurt() {
        val g = quietGame()
        g.botStartBoss()
        g.steps(0.05f)
        val a = FloatArray(2)
        g.botBossAim(3, a)
        tap(g, 1L, a[0], a[1], ms)
        assertEquals(ShooterTuning.BOSS_HP - ShooterTuning.BOSS_BODY_DAMAGE, g.botBossHp)
        var pod = if (g.botBossWeak(0)) 0 else 1
        assertTrue(g.botBossWeak(pod))
        g.botBossAim(pod, a)
        tap(g, 2L, a[0], a[1], ms)
        assertEquals(ShooterTuning.BOSS_HP - ShooterTuning.BOSS_BODY_DAMAGE - ShooterTuning.BOSS_WEAK_DAMAGE, g.botBossHp)
        assertTrue(g.score > ShooterTuning.BOSS_BODY_POINTS + ShooterTuning.BOSS_WEAK_POINTS - 1)
        pod = 1 - pod
        while (g.botBossWeak(pod)) g.steps(FIXED_DT)
        g.botBossAim(pod, a)
        assertEquals("a closed pod isn't a weak point", 13, g.botWouldHit(a[0], a[1]))
    }

    @Test
    fun theBossCannonHurtsUnlessYouShootItsEye() {
        val g = quietGame()
        g.botStartBoss()
        // Let it charge and fire.
        var t = 0f
        while (g.botHurts == 0 && t < 10f) {
            g.steps(FIXED_DT)
            t += FIXED_DT
        }
        assertEquals(1, g.botHurts)
        // Next charge: shoot the eye while it glows.
        while (!g.botBossCharging) g.steps(FIXED_DT)
        val a = FloatArray(2)
        var id = 10L
        while (g.botBossCharging) {
            if (g.botAmmo == 0) g.reload(id++)
            g.botBossAim(2, a)
            tap(g, id++, a[0], a[1], ms)
            g.steps(0.1f)
        }
        g.steps(0.5f)
        assertEquals("staggered: no shot", 1, g.botHurts)
    }

    @Test
    fun nothingScoresAfterTimeUp() {
        val g = ShooterGame()
        val d = RoundDriver(g, 5L)
        g.botHoldSpawns(999f)
        d.play(1f)
        g.shoot(g.botSpawn(GOON, FAR_MID), 1L)
        assertEquals(ShooterTuning.GOON_POINTS, g.score)
        // Run the clock out.
        d.play(g.roundSeconds)
        assertTrue(g.finished)
        val score = g.score
        val shots = g.botShots
        // A figure somehow still standing, a tap on it, and a tap on the reload strip: no effect.
        val slot = g.botSpawn(GOON, FAR_LEFT)
        val a = FloatArray(2)
        g.botAim(slot, a)
        tap(g, 2L, a[0], a[1], d.ms)
        tap(g, 3L, g.botReloadX, g.botReloadY, d.ms)
        d.end()
        assertEquals(score, g.score)
        assertEquals(shots, g.botShots)
        assertFalse(g.botReloading)
    }

    @Test
    fun theAccuracyBonusIsPaidOnceAtTheEnd() {
        val g = ShooterGame()
        val d = RoundDriver(g, 6L)
        g.botHoldSpawns(999f)
        d.play(0.5f)
        g.shoot(g.botSpawn(GOON, FAR_LEFT), 1L)
        g.shoot(g.botSpawn(GOON, FAR_RIGHT), 2L)
        assertEquals(2 * ShooterTuning.GOON_POINTS, g.score)
        d.play(g.roundSeconds + 5f)
        d.end()
        // Two out of two: the full accuracy bonus, once.
        assertEquals(2 * ShooterTuning.GOON_POINTS + ShooterTuning.ACCURACY_BONUS_MAX, g.score)
    }

    @Test
    fun theTargetPoolNeverGrowsAndRoundsAlwaysFinish() {
        val g = ShooterGame()
        val rng = Random(7)
        var id = 1L
        var maxActive = 0
        var maxPointers = 0
        repeat(6) { round ->
            playRound(g, 50L + round) { t, ms ->
                // A button-masher: taps everywhere, sometimes leaves fingers down, sometimes reloads.
                if (rng.nextFloat() < 0.08f) {
                    val x = rng.nextFloat() * 400f - 20f
                    val y = rng.nextFloat() * 680f - 20f
                    g.onTouch(TouchType.DOWN, id, x, y, ms)
                    if (rng.nextFloat() < 0.8f) g.onTouch(TouchType.UP, id, x, y, ms + 30)
                    id++
                }
                maxActive = maxOf(maxActive, g.botActive)
                maxPointers = maxOf(maxPointers, g.botPointers)
                assertTrue(t < g.roundSeconds + 10f)
            }
            assertEquals(12, g.botSlots)
            assertTrue(g.botDecals <= 40)
        }
        assertTrue("pool overflow: $maxActive", maxActive <= g.botSlots)
        assertTrue(maxPointers <= 6)
    }

    @Test
    fun resetBetweenRoundsLeavesNothingBehind() {
        val g = ShooterGame()
        val rng = Random(9)
        var id = 1L
        // A messy first round: shots, holes, a boss fight and a finger left down.
        playRound(g, 77L) { _, ms ->
            if (rng.nextFloat() < 0.05f) {
                tap(g, id++, rng.nextFloat() * 360f, rng.nextFloat() * 640f, ms)
            }
        }
        g.onTouch(TouchType.DOWN, 999L, 10f, 10f, 0L)
        assertTrue(g.botShots > 0)
        // Round two starts clean.
        g.seed = 78L
        g.start(simFx)
        assertEquals(0, g.score)
        assertEquals(0, g.bonusTickets)
        assertEquals(0, g.botActive)
        assertEquals(0, g.botDecals)
        assertEquals(0, g.botPointers)
        assertEquals(0, g.botShots)
        assertEquals(0, g.botHits)
        assertEquals(0, g.botHurts)
        assertEquals(0, g.botCombo)
        assertEquals(0, g.botWave)
        assertEquals(ShooterTuning.CLIP, g.botAmmo)
        assertEquals(ShooterTuning.HEARTS, g.botHearts)
        assertFalse(g.botReloading)
        assertFalse(g.botDown)
        assertFalse(g.botBossPresent)
        assertFalse(g.botCleared)
        assertFalse(g.finished)
        // And plays normally.
        val d = RoundDriver(g, 78L)
        d.play(3f)
        assertTrue(g.botActive > 0)
    }

    @Test
    fun aimPointsAndScoresStayFinite() {
        val g = ShooterGame()
        val a = FloatArray(2)
        playRound(g, 4L) { _, _ ->
            for (i in 0 until g.botSlots) if (g.botKind(i) >= 0) {
                g.botAim(i, a)
                assertTrue(a[0].isFinite() && a[1].isFinite())
            }
            if (g.botBossFighting) for (k in 0..3) {
                g.botBossAim(k, a)
                assertTrue(a[0].isFinite() && a[1].isFinite())
            }
        }
        assertTrue(g.score >= 0)
        assertTrue(g.ticketsFor(g.score) >= ShooterTuning.BASE_TICKETS)
    }
}
