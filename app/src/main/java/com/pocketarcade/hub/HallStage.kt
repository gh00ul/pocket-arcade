package com.pocketarcade.hub

import android.util.Log
import com.pocketarcade.engine.gl.Warmup
import com.pocketarcade.startup.LoadPlan
import com.pocketarcade.startup.LoadStep
import com.pocketarcade.startup.Startup
import com.pocketarcade.startup.WarmRun

/**
 * Keeps the hall's one [HallScene] (and the renderer that draws it) alive for as long as the
 * [HubWorld] lives. Building a scene takes seconds; it used to be built again every time the
 * hall's screen came back (after each machine) and every time a decoration was bought, which
 * froze the picture for that long. Now it is built once, a slice a frame behind the loading
 * screen ([plan]), and afterwards only *adjusted*: a bought decoration adds its own model and
 * floor patch ([mapChanged]), a new hat or outfit gets its figure made ahead of the frame that
 * draws it ([lookChanged]). The GPU is handed the new models straight away too ([Warmup]), so
 * even their first frame has nothing left to upload.
 *
 * Main thread only, like the world.
 */
class HallStage(private val world: HubWorld) {
    private companion object {
        const val TAG = "PocketArcadeStartup"
        /** The GPU stage gives up altogether after this long. */
        const val GPU_TIMEOUT_MS = 15_000L
    }

    /** Draws the hall. Kept here so coming back to the hall doesn't allocate a renderer (and its buffers) again. */
    val renderer: HubRenderer by lazy { HubRenderer() }

    /** The built scene, or null while the loading plan is still building it. */
    var scene: HallScene? = null
        private set

    /** Whether the scene is built (the GPU may still be taking it). */
    val ready: Boolean get() = scene != null

    /**
     * The steps that build the scene and hand it to the GPU: the kit's, then the scene itself,
     * then the GPU warm-up. The kit reads the floor plan and the crowd when the plan is made.
     */
    fun plan(): LoadPlan {
        val kit = HallKit(world.map, world.games)
        val looks = world.npcs.map { it.look } + listOfNotNull(world.player.look)
        val build = kit.plan(looks)
        var run: WarmRun? = null
        val steps = ArrayList<LoadStep>(build.steps)
        steps += LoadStep.Work(HallStages.PRIZES, 1f) { install(HallScene(kit)) }
        steps += LoadStep.Wait(
            "WARMING UP THE GPU", weight = 14f, timeoutMs = GPU_TIMEOUT_MS,
            start = {
                val jobs = scene?.warmJobs().orEmpty()
                run = WarmRun(jobs.size, { Warmup.submit(jobs[it]) }, onStall = { Log.w(TAG, "The GPU stopped answering the warm-up; the hall uploads as it draws") })
            },
            onTimeout = { Warmup.giveUp() },
            fraction = { run?.fraction ?: 0f },
            ready = { run?.poll() ?: true },
        )
        return LoadPlan(steps)
    }

    private fun install(built: HallScene) {
        scene = built
        // A decoration bought or an outfit changed while the plan was still running.
        sync(built)
    }

    /** The scene to draw now. If the plan hasn't built one (a launch shortcut got here first) it is built on the spot. */
    fun current(): HallScene {
        val s = scene
        if (s == null) {
            Startup.begin("HallScene built inline")
            Log.w(TAG, "The hall was drawn before its loading plan finished: building it in a frame")
            val built = HallScene(world.map, world.games)
            scene = built
            Startup.end("HallScene built inline")
            return built
        }
        if (s.map !== world.map) sync(s)
        return scene ?: s
    }

    /** The world's floor plan changed (a decoration was bought): bring the scene up to date, outside any frame. */
    fun mapChanged() {
        val s = scene ?: return
        sync(s)
    }

    /** The player's look changed: make the figure now and hand it to the GPU. */
    fun lookChanged(look: CharacterLook) {
        val s = scene ?: return
        val figure = s.prepareLook(look)
        Warmup.submit { r -> figure.draw(r, 0f, 0f, 0f, 0f, Pose.STAND, 0f, 0f) }
    }

    private fun sync(s: HallScene) {
        if (s.map === world.map) return
        Startup.begin("decor purchase -> hall updated")
        val built = s.adopt(world.map)
        if (built == null) {
            // Something besides the decorations changed (a machine was added, say): a fresh scene it is.
            Log.w(TAG, "The floor plan changed beyond its decorations: rebuilding the hall scene")
            scene = HallScene(world.map, world.games)
        } else if (built.isNotEmpty()) {
            Warmup.submit { r -> for (m in built) m.draw(r) }
        }
        Startup.end("decor purchase -> hall updated")
    }
}
