package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.startup.LoadPlan
import com.pocketarcade.startup.LoadStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of building the hall that run on the JVM: what a purchase changes in the floor plan
 * (which decides whether the built scene can be adjusted in place or must be built again), and
 * the plan of steps the loading screen runs. The painting itself needs Android's canvas.
 */
class HallLoadTest {
    private val games = GameRegistry.createAll()

    private fun map(vararg decor: DecorStyle) = HubLayout.build(games, decor.toSet())

    // ------------------------------------------------------------------ what a purchase changes

    @Test
    fun theSameDecorationsChangeNothing() {
        val diff = DecorDiff.between(map(DecorStyle.PALM), map(DecorStyle.PALM))
        assertNotNull(diff)
        assertTrue("two builds of one floor plan are the same, though every prop is a new object", diff!!.isEmpty)
    }

    @Test
    fun buyingADecorationAddsOnlyThatProp() {
        val diff = DecorDiff.between(map(), map(DecorStyle.JUKEBOX))!!
        assertEquals(listOf(DecorStyle.JUKEBOX), diff.added.map { it.decor })
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun buyingSeveralAtOnceAddsEachOne() {
        val diff = DecorDiff.between(map(DecorStyle.PALM), map(DecorStyle.PALM, DecorStyle.FISH_TANK, DecorStyle.DISCO_BALL))!!
        assertEquals(setOf(DecorStyle.FISH_TANK, DecorStyle.DISCO_BALL), diff.added.map { it.decor }.toSet())
        assertTrue("the palm was already there", diff.removed.isEmpty())
    }

    @Test
    fun aDecorationTakenAwayIsRemoved() {
        val diff = DecorDiff.between(map(DecorStyle.PALM, DecorStyle.GUMBALL), map(DecorStyle.GUMBALL))!!
        assertEquals(listOf(DecorStyle.PALM), diff.removed.map { it.decor })
        assertTrue(diff.added.isEmpty())
    }

    @Test
    fun everyDecorationCanBeBoughtWithoutRebuildingTheScene() {
        val bare = map()
        for (style in DecorStyle.entries) {
            val diff = DecorDiff.between(bare, map(style))
            assertNotNull("$style should only add its own prop", diff)
            assertEquals(1, diff!!.added.size)
        }
        val all = DecorDiff.between(bare, map(*DecorStyle.entries.toTypedArray()))!!
        assertEquals(DecorStyle.entries.size, all.added.size)
    }

    @Test
    fun aDifferentSetOfMachinesMeansANewScene() {
        val fewer = HubLayout.build(games.dropLast(1), emptySet())
        assertNull("a machine went missing: the scene can't just be adjusted", DecorDiff.between(map(), fewer))
    }

    @Test
    fun propsAreTheSameOnlyIfTheyStandTheSameWay() {
        val machines = map().props.filter { it.kind == PropKind.MACHINE }
        val again = map().props.filter { it.kind == PropKind.MACHINE }
        assertTrue(DecorDiff.sameProp(machines[0], again[0]))
        assertFalse(DecorDiff.sameProp(machines[0], machines[1]))
    }

    // ------------------------------------------------------------------ the plan of steps

    @Test
    fun theHallPlanHasAStepForEveryMachineAndTheStagesInOrder() {
        val m = map()
        val looks = listOf(Looks.randomKid(3), Looks.randomKid(4))
        val plan = HallKit(m, games).plan(looks)
        val machines = m.props.count { it.kind == PropKind.MACHINE }
        val forMachines = plan.steps.filter { it.name == HallStages.MACHINES }
        // Two art steps per game (none of them painted in a test) and one step per cabinet.
        assertEquals(games.size * 2 + machines, forMachines.size)
        val order = plan.steps.map { it.name }.distinct()
        assertEquals(
            listOf(
                HallStages.FLOORS, HallStages.WALLS, HallStages.NEON, HallStages.BUILD, HallStages.MACHINES,
                HallStages.FURNITURE, HallStages.CROWD, HallStages.PRIZES,
            ),
            order,
        )
        assertTrue("every step has a positive weight", plan.steps.all { it.weight > 0f })
        assertTrue(plan.totalWeight > 50f)
    }

    @Test
    fun theHallPlanMakesAFigureForEachDistinctLookAndTheClerk() {
        val kid = Looks.randomKid(3)
        val few = HallKit(map(), games).plan(listOf(kid, kid, kid))
        val many = HallKit(map(), games).plan(listOf(kid, Looks.randomKid(4), Looks.randomKid(5)))
        val crowd = { p: LoadPlan -> p.steps.count { it.name == HallStages.CROWD } }
        assertEquals("two more looks, two more crowd steps", 2, crowd(many) - crowd(few))
    }

    @Test
    fun theBootPlanPaintsAndBuildsTheShowroomForEveryGame() {
        val plan = TitleUnits.plan(games)
        assertEquals(games.size * 3, plan.steps.size)
        assertEquals(games.size * 2, plan.steps.count { it.name == "PAINTING THE SHOWROOM" })
        assertEquals(games.size, plan.steps.count { it.name == "WHEELING THEM IN" })
        assertTrue(plan.steps.all { it is LoadStep.Work })
    }

    @Test
    fun theLoadingScreenNamesFitOnOneLine() {
        val names = (HallKit(map(), games).plan(emptyList()).steps + TitleUnits.plan(games).steps).map { it.name }.distinct()
        for (n in names) {
            assertEquals("$n is spelt in capitals", n.uppercase(), n)
            assertTrue("$n is short enough for a phone's width", n.length <= 28)
        }
    }

    @Test
    fun aPlanIsOnlyMadeNotRun() {
        // Making the plan touches no texture: it works on the JVM, where painting can't.
        val plan = HallKit(map(DecorStyle.PALM), games).plan(emptyList())
        assertTrue(plan.steps.isNotEmpty())
    }
}
