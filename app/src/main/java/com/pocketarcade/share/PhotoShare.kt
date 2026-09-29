package com.pocketarcade.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a saved photo strip to the Android share sheet through a content URI from the app's
 * FileProvider (declared in the manifest, sharing only the photos folder), so no storage
 * permission is needed and the other app can read just that one picture.
 */
object PhotoShare {
    /** The manifest declares the provider as "${applicationId}.photos". */
    const val AUTHORITY_SUFFIX = ".photos"

    const val MIME = "image/png"

    fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

    /** The share sheet for [file] (which must be in the photos folder). */
    fun chooser(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context.packageName), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            // The clip is what the grant follows through the chooser to whichever app is picked.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "SHARE YOUR PHOTO STRIP").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Opens the share sheet; false if it couldn't be (no app to share to, the file is gone). */
    fun share(context: Context, file: File): Boolean = try {
        context.startActivity(chooser(context, file))
        true
    } catch (_: Exception) {
        false
    }
}
