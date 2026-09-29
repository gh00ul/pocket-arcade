package com.pocketarcade.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** The photo strip's geometry, its file names, its date and its cropping: all plain numbers and text. */
class PhotoStripTest {
    private fun inside(r: PxRect, w: Int, h: Int) = r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h

    @Test
    fun theStripHoldsFourSquareShotsWithRoomForTheHeaderAndDate() {
        for (frame in listOf(PhotoStrip.FRAME, 100, 240, 361, 512)) {
            val l = StripLayout(frame)
            val label = "frame $frame"
            assertEquals(label, PhotoStrip.SHOTS, l.frames.size)
            assertEquals(label, frame + 2 * l.margin, l.width)
            // Everything is inside the strip and inside the margins.
            for (r in l.frames + l.header + l.footer) {
                assertTrue("$label: $r is outside ${l.width} x ${l.height}", inside(r, l.width, l.height))
                assertTrue("$label: $r is in the margin", r.left >= l.margin && r.right <= l.width - l.margin && r.top >= l.margin && r.bottom <= l.height - l.margin)
            }
            for (r in l.frames) {
                assertEquals("$label: shots are square", frame, r.width)
                assertEquals("$label: shots are square", frame, r.height)
                assertEquals("$label: shots line up", l.margin, r.left)
            }
            // Header, the four shots and the footer run down the strip in order, a gap between the shots.
            assertEquals("$label: the header starts at the margin", l.margin, l.header.top)
            assertEquals("$label: the first shot follows the header", l.header.bottom, l.frames.first().top)
            for (i in 1 until l.frames.size) {
                assertEquals("$label: gap $i", l.gap, l.frames[i].top - l.frames[i - 1].bottom)
                assertTrue("$label: shots $i overlap", l.frames[i].top >= l.frames[i - 1].bottom)
            }
            assertEquals("$label: the footer follows the last shot", l.frames.last().bottom, l.footer.top)
            assertEquals("$label: the strip ends after the footer and a margin", l.footer.bottom + l.margin, l.height)
            assertTrue("$label: room for the arcade's name", l.header.height >= frame / 4 && l.header.width == frame)
            assertTrue("$label: room for the date", l.footer.height >= frame / 6 && l.footer.width == frame)
        }
    }

    @Test
    fun theStandardStripIsTallAndNarrow() {
        val l = StripLayout(PhotoStrip.FRAME)
        assertTrue("${l.width} x ${l.height}", l.height > 3 * l.width && l.height < 6 * l.width)
        // Small enough to hold in memory and share.
        assertTrue(l.width.toLong() * l.height * 4 < 8L * 1024 * 1024)
    }

    @Test
    fun fileNamesAreTheUtcTimeSoTheySortInTheOrderMade() {
        assertEquals("strip-19700101-000000-000.png", PhotoStrip.fileName(0L))
        // 2026-09-28 21:30:45.123 UTC
        val millis = Instant.parse("2026-09-28T21:30:45.123Z").toEpochMilli()
        assertEquals("strip-20260928-213045-123.png", PhotoStrip.fileName(millis))

        val rng = Random(7)
        val times = List(200) { rng.nextLong(0L, 4_102_444_800_000L) }.distinct().sorted()
        val names = times.map { PhotoStrip.fileName(it) }
        assertEquals("names sort in the order the strips were made", names, names.sorted())
        assertEquals("every strip gets its own name", names.size, names.toSet().size)
        // A second apart, and a millisecond apart, across midnight and new year.
        for ((a, b) in listOf("2026-12-31T23:59:59.999Z" to "2027-01-01T00:00:00.000Z", "2026-09-28T09:59:59.999Z" to "2026-09-28T10:00:00.000Z")) {
            assertTrue(PhotoStrip.fileName(Instant.parse(a).toEpochMilli()) < PhotoStrip.fileName(Instant.parse(b).toEpochMilli()))
        }
    }

    @Test
    fun onlyOurOwnNamesCountAsStrips() {
        assertTrue(PhotoStrip.isStripName(PhotoStrip.fileName(123456789L)))
        assertTrue(PhotoStrip.isStripName("strip-20260928-213045-123.png"))
        for (other in listOf("", "strip.png", "strip-1.png", "strip-20260928-213045-123.png.tmp", "notes.txt", "xstrip-20260928-213045-123.png", "strip-20260928-213045-123.jpg", "../strip-20260928-213045-123.png")) {
            assertFalse("'$other'", PhotoStrip.isStripName(other))
        }
    }

    @Test
    fun theDateIsPrintedInTheLocalZoneInWords() {
        assertEquals("JAN 1 1970", PhotoStrip.dateLabel(0L, ZoneOffset.UTC))
        val late = Instant.parse("2026-09-28T23:30:00Z").toEpochMilli()
        assertEquals("SEP 28 2026", PhotoStrip.dateLabel(late, ZoneOffset.UTC))
        // The same instant is already tomorrow two hours east, and still today in New York.
        assertEquals("SEP 29 2026", PhotoStrip.dateLabel(late, ZoneId.of("Europe/Helsinki")))
        assertEquals("SEP 28 2026", PhotoStrip.dateLabel(late, ZoneId.of("America/New_York")))
        assertEquals("DEC 5 2031", PhotoStrip.dateLabel(Instant.parse("2031-12-05T12:00:00Z").toEpochMilli(), ZoneOffset.UTC))
        // Every month has a three-letter name.
        for (m in 1..12) {
            val s = PhotoStrip.dateLabel(Instant.parse("2026-%02d-15T12:00:00Z".format(m)).toEpochMilli(), ZoneOffset.UTC)
            assertTrue(s, Regex("[A-Z]{3} 15 2026").matches(s))
        }
    }

    @Test
    fun aLongNameShrinksToFitAndAShortOneKeepsItsSize() {
        // 100 pixels wide at one unit, 360 to put it in.
        assertEquals(3.6f, PhotoStrip.fitUnit(100f, 360f, maxUnit = 6f), 1e-4f)
        assertEquals(6f, PhotoStrip.fitUnit(40f, 360f, maxUnit = 6f), 0f)
        assertEquals(6f, PhotoStrip.fitUnit(0f, 360f, maxUnit = 6f), 0f)
        assertEquals(6f, PhotoStrip.fitUnit(100f, 0f, maxUnit = 6f), 0f)
        // Whatever it comes to, the text ends up no wider than the room.
        for (natural in listOf(10f, 55f, 99f, 140f, 400f)) {
            val unit = PhotoStrip.fitUnit(natural, 360f, maxUnit = 6f)
            assertTrue(unit * natural <= 360f + 1e-3f)
            assertTrue(unit <= 6f)
        }
    }

    @Test
    fun cropsKeepTheShapeOfTheFrameAndTheMiddleOfThePicture() {
        // Already the right shape: everything.
        var c = PhotoStrip.cropToAspect(360, 360, 300, 300)
        assertEquals("(0, 0)-(360, 360)", c.toString())
        // Too wide: trimmed evenly at the sides.
        c = PhotoStrip.cropToAspect(200, 100, 50, 50)
        assertEquals("(50, 0)-(150, 100)", c.toString())
        // Too tall: trimmed evenly top and bottom.
        c = PhotoStrip.cropToAspect(100, 200, 50, 50)
        assertEquals("(0, 50)-(100, 150)", c.toString())
        // Into a non-square frame.
        c = PhotoStrip.cropToAspect(400, 300, 200, 100)
        assertEquals("(0, 50)-(400, 250)", c.toString())
        // Nonsense sizes don't crash.
        assertEquals(0, PhotoStrip.cropToAspect(0, 0, 10, 10).width)
        assertEquals(10, PhotoStrip.cropToAspect(10, 10, 0, 0).width)
        // Whatever the shapes, the crop is inside the picture and has the frame's shape (to a pixel).
        val rng = Random(3)
        repeat(300) {
            val sw = rng.nextInt(20, 900)
            val sh = rng.nextInt(20, 900)
            val dw = rng.nextInt(20, 900)
            val dh = rng.nextInt(20, 900)
            val r = PhotoStrip.cropToAspect(sw, sh, dw, dh)
            assertTrue("$sw x $sh -> $r", r.left >= 0 && r.top >= 0 && r.right <= sw && r.bottom <= sh && r.width > 0 && r.height > 0)
            // The same shape as the frame, to the pixel the trimmed side is rounded by.
            val off = kotlin.math.abs(r.width.toLong() * dh - r.height.toLong() * dw)
            assertTrue("$sw x $sh into $dw x $dh: $r is off by $off", off <= maxOf(dw, dh))
        }
    }
}
