package com.pocketarcade.hub

import com.pocketarcade.engine.gl.Warmup
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.games.MiniGame
import com.pocketarcade.startup.LoadPlan
import com.pocketarcade.startup.LoadStep
import com.pocketarcade.startup.WarmRun

/**
 * The cabinets of the title screen's showroom, one per game. They are built here a game at a time
 * by the boot plan (behind the loading screen) so that the title's own `TitleShowcase` finds them
 * ready; built on demand ([completeAll]) if it gets there first. Every cabinet uses its game's
 * shared [MachineArts], which the hall then reuses instead of painting a second copy.
 */
internal class TitleUnits private constructor(private val games: List<MiniGame>) {
    val units = ArrayList<MachineUnit>()

    /** Builds the next cabinet and paints its first screen; false once every game has one. */
    fun buildNext(): Boolean {
        val i = units.size
        if (i >= games.size) return false
        val u = TitleShowcase.buildUnit(games, i)
        u.screen?.warm(0)
        units += u
        return true
    }

    /** Builds whatever is left and returns every cabinet. */
    fun completeAll(): List<MachineUnit> {
        while (buildNext()) Unit
        return units
    }

    /** Warm-up pictures that hand the GPU every cabinet and its screen, four cabinets a picture. */
    fun warmJobs(): List<(Renderer3D) -> Unit> {
        val jobs = ArrayList<(Renderer3D) -> Unit>()
        for (chunk in units.chunked(4)) {
            jobs += { r ->
                for (u in chunk) {
                    u.drawOpaque(r, 0f)
                    u.drawTransparent(r)
                }
                Warmup.touch(r, HallArt.solid(-1).full)
                Warmup.touch(r, HallArt.glow.full)
            }
        }
        jobs += { r -> Warmup.touch(r, HallArt.tiles.full) }
        return jobs
    }

    companion object {
        /** The showroom's cabinets for [games]: one set for the app's game list, kept while it is in use. */
        private var current: TitleUnits? = null

        fun of(games: List<MiniGame>): TitleUnits = current?.takeIf { it.games === games } ?: TitleUnits(games).also { current = it }

        /** The steps that paint each game's cabinet art and build its showroom cabinet ([HallStages] names them for the screen). */
        fun plan(games: List<MiniGame>): LoadPlan {
            val s = ArrayList<LoadStep>()
            for (g in games) {
                s += LoadStep.Work("PAINTING THE SHOWROOM", 3f) { MachineArts.paint(g, 0) }
                s += LoadStep.Work("PAINTING THE SHOWROOM", 3f) { MachineArts.paint(g, 1) }
            }
            repeat(games.size) { s += LoadStep.Work("WHEELING THEM IN", 2f) { of(games).buildNext() } }
            return LoadPlan(s)
        }

        /** The wait that has the GPU take the showroom (its shaders too: the title is the first thing drawn). */
        fun gpuStep(games: List<MiniGame>): LoadStep {
            var run: WarmRun? = null
            return LoadStep.Wait(
                "WARMING UP THE GPU", weight = 6f, timeoutMs = GPU_TIMEOUT_MS,
                start = {
                    val jobs = of(games).warmJobs()
                    run = WarmRun(jobs.size, { Warmup.submit(jobs[it]) })
                },
                onTimeout = { Warmup.giveUp() },
                fraction = { run?.fraction ?: 0f },
                ready = { run?.poll() ?: true },
            )
        }

        /** How long the title waits for the GPU's first answer (the driver may still be starting up) before carrying on without it. */
        private const val GPU_TIMEOUT_MS = 8_000L
    }
}
