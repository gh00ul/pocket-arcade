package com.pocketarcade.share

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.OutputStream

/**
 * The photo strips kept on the phone: PNGs in the app's own files folder (so no storage
 * permission is needed), only the newest [KEEP] of them. The rotation and the writing work on
 * plain files, so the JVM tests run them on a temp folder.
 */
object PhotoStore {
    /** The folder under the app's files directory; res/xml/file_paths.xml shares exactly this one. */
    const val DIR_NAME = "photos"

    /** How many strips are kept; each new one pushes the oldest out. */
    const val KEEP = 4

    fun dir(filesDir: File): File = File(filesDir, DIR_NAME)

    /**
     * Which of the [names] in the folder to delete so that only the newest [keep] strips are left.
     * Names sort in the order the strips were made ([PhotoStrip.fileName]); anything that isn't a
     * strip's name is never touched.
     */
    fun stale(names: Collection<String>, keep: Int = KEEP): List<String> {
        val strips = names.filter(PhotoStrip::isStripName).sorted()
        return strips.take((strips.size - keep.coerceAtLeast(0)).coerceAtLeast(0))
    }

    /** Deletes every strip in [dir] but the newest [keep]. */
    fun prune(dir: File, keep: Int = KEEP) {
        for (name in stale(dir.list()?.toList().orEmpty(), keep)) File(dir, name).delete()
    }

    /** The strips in [dir], newest first. */
    fun list(dir: File): List<File> =
        dir.list()?.filter(PhotoStrip::isStripName)?.sortedDescending()?.map { File(dir, it) }.orEmpty()

    /**
     * Writes a strip called [name] into [dir] with [writer], then trims the folder to the newest
     * [keep]. The strip is written to a temporary file first, so a failure part way leaves neither
     * a broken strip nor a trimmed folder; the error is passed on.
     */
    fun write(dir: File, name: String, keep: Int = KEEP, writer: (OutputStream) -> Unit): File {
        check(dir.isDirectory || dir.mkdirs()) { "can't make the folder $dir" }
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        try {
            temp.outputStream().use(writer)
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
        } finally {
            temp.delete()
        }
        prune(dir, keep)
        return target
    }

    /** Saves [strip] as a PNG named for [nowMillis] under the app's files and trims to the newest [KEEP]. */
    fun save(context: Context, strip: Bitmap, nowMillis: Long = System.currentTimeMillis()): File =
        write(dir(context.filesDir), PhotoStrip.fileName(nowMillis)) { out ->
            check(strip.compress(Bitmap.CompressFormat.PNG, 100, out)) { "couldn't write the photo strip" }
        }
}
