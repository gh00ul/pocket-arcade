package com.pocketarcade.games.shooter

import com.pocketarcade.games.Stats
import com.pocketarcade.games.assertPayoutBands
import com.pocketarcade.games.gaussian
import com.pocketarcade.games.playRound
import com.pocketarcade.games.tap
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Plays the shootout headlessly with a sharp-shooting bot and a casual one, through the real touch path. */
class ShooterSimulationTest {
    /** Seeds successive rounds so every run replays the same games. */
    private var roundSeed = 1L

    private class Skill(
        val name: String,
        /** Seconds a target must have been out before the bot reacts to it. */
        val reaction: Float,
        /** Aim error in field units (one sigma). */
        val noise: Float,
        /** Shortest gap between the bot's taps. */
        val interval: Float,
        /** Shoots whatever it sees first rather than the bandits taking aim. */
        val careless: Boolean,
        /** Chance of blasting a civilian it sees. */
        val civilianMistake: Float,
        /** Reloads with shots still in the gun when nothing is out; otherwise only after a click. */
        val smartReload: Boolean,
    )

    private val good = Skill("shooter sharp", reaction = 0.2f, noise = 5f, interval = 0.14f, careless = false, civilianMistake = 0f, smartReload = true)
    private val casual = Skill("shooter casual", reaction = 0.65f, noise = 18f, interval = 0.42f, careless = true, civilianMistake = 0.12f, smartReload = false)

    /** Totals across a batch, for the stats line. */
    private class Tally {
        var shots = 0
        var hits = 0
        var hurts = 0
        var civilians = 0
        var bosses = 0
        var rounds = 0
    }

    private fun shooter(rounds: Int, skill: Skill, seed: Int, tally: Tally): Stats {
        val stats = Stats(skill.name)
        val rng = Random(seed)
        val game = ShooterGame()
        val aim = FloatArray(2)
        // Whether the bot means to shoot the civilian in each slot (decided once per appearance).
        val blast = BooleanArray(game.botSlots)
        val lastSeen = FloatArray(game.botSlots)
        repeat(rounds) {
            var next = 0f
            var id = 1L
            var clickedAt = -1f
            playRound(game, roundSeed++, stats) { t, ms ->
                for (i in 0 until game.botSlots) {
                    val seen = game.botSeen(i)
                    if (seen < lastSeen[i]) lastSeen[i] = 0f
                    if (seen > 0f && lastSeen[i] == 0f) blast[i] = rng.nextBoolean(skill.civilianMistake)
                    if (seen > 0f) lastSeen[i] = seen
                }
                if (t < next) return@playRound
                // Out of rounds: a sharp shooter reloads at once, a casual one only after a click and a beat.
                if (game.botAmmo == 0 && !game.botReloading) {
                    if (skill.smartReload || (clickedAt >= 0f && t - clickedAt > 0.35f)) {
                        tap(game, id++, game.botReloadX, game.botReloadY, ms)
                        clickedAt = -1f
                        next = t + skill.interval
                        return@playRound
                    }
                }
                if (game.botReloading || game.botDown) return@playRound
                var pick = -1
                var best = -1f
                for (i in 0 until game.botSlots) {
                    val kind = game.botKind(i)
                    if (kind < 0 || game.botSeen(i) < skill.reaction) continue
                    if (kind == 1 && !blast[i]) continue
                    val priority = if (skill.careless) rng.nextFloat() else when {
                        game.botTelling(i) -> 10f + game.botSeen(i)
                        kind == 3 -> 8f
                        kind == 0 -> 5f + game.botSeen(i)
                        else -> 4f
                    }
                    if (priority > best) {
                        best = priority
                        pick = i
                    }
                }
                var x = 0f
                var y = 0f
                var have = false
                if (pick >= 0) {
                    game.botAim(pick, aim)
                    x = aim[0]; y = aim[1]; have = true
                } else if (game.botBossFighting) {
                    val part = when {
                        skill.careless -> 3
                        game.botBossCharging && game.botBossWeak(2) -> 2
                        game.botBossWeak(0) -> 0
                        game.botBossWeak(1) -> 1
                        else -> 3
                    }
                    game.botBossAim(part, aim)
                    x = aim[0]; y = aim[1]; have = true
                }
                if (!have) {
                    if (skill.smartReload && game.botAmmo < 3 && !game.botReloading) {
                        tap(game, id++, game.botReloadX, game.botReloadY, ms)
                        next = t + skill.interval
                    }
                    return@playRound
                }
                x += gaussian(rng) * skill.noise
                y += gaussian(rng) * skill.noise
                // A sharp shooter holds fire when a civilian is in the way.
                if (!skill.careless && game.botWouldHit(x, y) == 1) return@playRound
                if (game.botAmmo == 0 && clickedAt < 0f) clickedAt = t
                tap(game, id++, x, y, ms)
                next = t + skill.interval * (0.8f + rng.nextFloat() * 0.4f)
            }
            tally.shots += game.botShots
            tally.hits += game.botHits
            tally.hurts += game.botHurts
            tally.civilians += game.botCiviliansShot
            if (game.botCleared) tally.bosses++
            tally.rounds++
        }
        return stats
    }

    private fun Random.nextBoolean(p: Float) = nextFloat() < p

    private fun line(stats: Stats, t: Tally) = "%s  acc %3d%%  hurt %.1f  civ %.1f  boss %d/%d".format(
        stats, if (t.shots == 0) 0 else t.hits * 100 / t.shots, t.hurts / t.rounds.toFloat(), t.civilians / t.rounds.toFloat(), t.bosses, t.rounds,
    )

    @Test
    fun shooterPaysOutAndRewardsSkill() {
        val rounds = 12
        val gt = Tally()
        val ct = Tally()
        val g = shooter(rounds, good, seed = 31, tally = gt)
        val c = shooter(rounds, casual, seed = 32, tally = ct)
        println(line(g, gt))
        println(line(c, ct))
        assertPayoutBands(g, c)
        assertTrue("a sharp shooter should beat the boss most rounds (${gt.bosses}/$rounds)", gt.bosses * 2 >= rounds)
        assertTrue("a sharp shooter shouldn't shoot civilians (${gt.civilians})", gt.civilians <= rounds / 4)
        assertTrue("casual play should hit a civilian now and then", ct.civilians > 0)
    }
}
