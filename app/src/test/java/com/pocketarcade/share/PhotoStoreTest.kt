package com.pocketarcade.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Keeping only the newest four photo strips, on a temp folder and as plain names. */
class PhotoStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun name(second: Int) = PhotoStrip.fileName(1_800_000_000_000L + second * 1000L)

    @Test
    fun theOldestStripsAreTheOnesToDelete() {
        val names = (1..6).map { name(it) }
        // Six strips, keep four: the two oldest go, whatever order the folder lists them in.
        assertEquals(listOf(name(1), name(2)), PhotoStore.stale(names))
        assertEquals(listOf(name(1), name(2)), PhotoStore.stale(names.reversed()))
        assertEquals(listOf(name(1), name(2)), PhotoStore.stale(names.shuffled(java.util.Random(5))))
        // Four or fewer: nothing to do.
        assertEquals(emptyList<String>(), PhotoStore.stale(names.takeLast(4)))
        assertEquals(emptyList<String>(), PhotoStore.stale(names.takeLast(1)))
        assertEquals(emptyList<String>(), PhotoStore.stale(emptyList()))
        // Other numbers to keep.
        assertEquals(names.take(5), PhotoStore.stale(names, keep = 1))
        assertEquals(names, PhotoStore.stale(names, keep = 0))
        assertEquals(names, PhotoStore.stale(names, keep = -3))
        assertEquals(PhotoStore.KEEP, 4)
    }

    @Test
    fun otherFilesAreNeverOnTheList() {
        val strips = (1..5).map { name(it) }
        val others = listOf("notes.txt", "strip-1.png", "${name(1)}.tmp", "readme")
        assertEquals(listOf(name(1)), PhotoStore.stale(strips + others))
        assertEquals(emptyList<String>(), PhotoStore.stale(others))
    }

    @Test
    fun writingAStripKeepsTheNewestFourAndLeavesNothingElseBehind() {
        val dir = File(tmp.root, "photos")
        val kept = File(dir.apply { mkdirs() }, "keep-me.txt").apply { writeText("mine") }
        val files = (1..6).map { i -> PhotoStore.write(dir, name(i)) { it.write(byteArrayOf(i.toByte())) } }
        assertEquals(name(6), files.last().name)
        assertEquals("the last strip holds what was written", listOf<Byte>(6), files.last().readBytes().toList())
        // The newest four remain, newest first...
        assertEquals((6 downTo 3).map { name(it) }, PhotoStore.list(dir).map { it.name })
        // ...the two oldest are gone, no temporary file is left, and an unrelated file is untouched.
        assertFalse(File(dir, name(1)).exists())
        assertFalse(File(dir, name(2)).exists())
        assertEquals(setOf("keep-me.txt") + (3..6).map { name(it) }, dir.list()!!.toSet())
        assertEquals("mine", kept.readText())
    }

    @Test
    fun theFolderIsMadeIfItIsMissing() {
        val dir = File(tmp.root, "deep/photos")
        assertFalse(dir.exists())
        val f = PhotoStore.write(dir, name(1)) { it.write(1) }
        assertTrue(f.isFile)
        assertEquals(listOf(f.name), PhotoStore.list(dir).map { it.name })
        // Listing a folder that isn't there is just empty.
        assertEquals(emptyList<File>(), PhotoStore.list(File(tmp.root, "nothing")))
    }

    @Test
    fun aFailedWriteLeavesNoBrokenStripAndDeletesNothing() {
        val dir = File(tmp.root, "photos")
        for (i in 1..4) PhotoStore.write(dir, name(i)) { it.write(i) }
        try {
            PhotoStore.write(dir, name(5)) {
                it.write(byteArrayOf(1, 2, 3))
                throw IOException("disk full")
            }
            fail("the error should be passed on")
        } catch (e: IOException) {
            assertEquals("disk full", e.message)
        }
        // The four good strips are all still there, and neither the new one nor its temp file.
        assertEquals((1..4).map { name(it) }.toSet(), dir.list()!!.toSet())
    }

    @Test
    fun pruningAnOverfullFolderTrimsItToFour() {
        val dir = File(tmp.root, "photos").apply { mkdirs() }
        for (i in 1..9) File(dir, name(i)).writeText("x")
        PhotoStore.prune(dir)
        assertEquals((6..9).map { name(it) }, PhotoStore.list(dir).map { it.name }.reversed())
        PhotoStore.prune(dir, keep = 2)
        assertEquals(2, PhotoStore.list(dir).size)
        // A folder that isn't there is fine.
        PhotoStore.prune(File(tmp.root, "nothing"))
    }

    @Test
    fun theStoreLivesInTheFolderTheShareSheetReads() {
        assertEquals(File("/data/files", "photos"), PhotoStore.dir(File("/data/files")))
    }
}
