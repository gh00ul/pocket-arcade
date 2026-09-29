package com.pocketarcade.share

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A rectangle in a strip's pixels. */
class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    override fun toString() = "($left, $top)-($right, $bottom)"
}

/**
 * The geometry of a photo strip whose four square shots are [frame] pixels across: a header for
 * the arcade's name, the four shots one under another, a footer for the date, and an even margin
 * all round. Pure numbers, so the JVM tests can check that everything fits and nothing overlaps;
 * [PhotoStripArt] does the painting.
 */
class StripLayout(val frame: Int) {
    val margin: Int = (frame * 0.08f).toInt().coerceAtLeast(2)
    val gap: Int = (frame * 0.045f).toInt().coerceAtLeast(1)
    val headerHeight: Int = (frame * 0.32f).toInt().coerceAtLeast(4)
    val footerHeight: Int = (frame * 0.23f).toInt().coerceAtLeast(4)

    val width: Int = frame + 2 * margin
    val height: Int = margin + headerHeight + PhotoStrip.SHOTS * frame + (PhotoStrip.SHOTS - 1) * gap + footerHeight + margin

    /** Where the arcade's name goes: the full width, above the first shot. */
    val header = PxRect(margin, margin, width - margin, margin + headerHeight)

    /** The shots, first at the top. */
    val frames: List<PxRect> = List(PhotoStrip.SHOTS) { i ->
        val top = margin + headerHeight + i * (frame + gap)
        PxRect(margin, top, margin + frame, top + frame)
    }

    /** Where the date goes: the full width, under the last shot. */
    val footer = PxRect(margin, frames.last().bottom, width - margin, height - margin)
}

/**
 * What goes on a photo strip and what it's called: how many shots, the words, the date, the
 * file's name. Pure, so it is tested without a device.
 */
object PhotoStrip {
    /** Four shots make a strip. */
    const val SHOTS = 4

    /** How many pixels across each shot is photographed. */
    const val FRAME = 360

    private const val PREFIX = "strip-"
    private const val SUFFIX = ".png"
    private val NAME = Regex("^strip-\\d{8}-\\d{6}-\\d{3}\\.png$")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    private val MONTHS = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /**
     * The file a strip made at [millis] is saved as. The name is the UTC time, so names sort in
     * the order strips were made whatever the time zone or daylight saving does.
     */
    fun fileName(millis: Long): String = PREFIX + STAMP.format(Instant.ofEpochMilli(millis)) + SUFFIX

    /** Whether [name] is one of ours (so other files in the folder are never touched). */
    fun isStripName(name: String): Boolean = NAME.matches(name)

    /** The date printed at the foot of the strip: "SEP 28 2026", in the type's capitals. */
    fun dateLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth} ${d.year}"
    }

    /**
     * The part of a [srcW] by [srcH] picture that fills a [dstW] by [dstH] frame without
     * squashing it: the whole picture trimmed evenly at the sides (or top and bottom) to the
     * frame's shape.
     */
    fun cropToAspect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): PxRect {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return PxRect(0, 0, maxOf(srcW, 0), maxOf(srcH, 0))
        // Compare srcW/srcH with dstW/dstH without dividing.
        return if (srcW.toLong() * dstH > srcH.toLong() * dstW) {
            val w = (srcH.toLong() * dstW / dstH).toInt().coerceIn(1, srcW)
            val left = (srcW - w) / 2
            PxRect(left, 0, left + w, srcH)
        } else {
            val h = (srcW.toLong() * dstH / dstW).toInt().coerceIn(1, srcH)
            val top = (srcH - h) / 2
            PxRect(0, top, srcW, top + h)
        }
    }

    /**
     * The text size (in the type's grid units) that fits a line [natural] wide at one unit into
     * [available] pixels, never bigger than [maxUnit]: a long arcade name shrinks to fit.
     */
    fun fitUnit(natural: Float, available: Float, maxUnit: Float): Float {
        if (natural <= 0f || available <= 0f) return maxUnit
        return minOf(maxUnit, available / natural)
    }
}
