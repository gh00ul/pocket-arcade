package com.pocketarcade.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sharing goes through a FileProvider, and a mismatch between the code and the manifest only
 * shows on a phone, when SHARE crashes. So the manifest and the paths file are read here and held
 * to what [PhotoShare] and [PhotoStore] expect. There is no storage permission to ask for.
 */
class PhotoShareWiringTest {
    /** Unit tests run in the module's folder; the fallback covers running from the project's root. */
    private fun source(path: String): String {
        val f = listOf(File(path), File("app/$path")).firstOrNull { it.isFile } ?: error("can't find $path from ${File(".").absoluteFile}")
        return f.readText()
    }

    @Test
    fun theManifestDeclaresTheProviderTheCodeAsksFor() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(
            "the authority must be \${applicationId}${PhotoShare.AUTHORITY_SUFFIX}",
            manifest.contains("android:authorities=\"\${applicationId}${PhotoShare.AUTHORITY_SUFFIX}\""),
        )
        assertTrue("the provider must not be exported", manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
        assertTrue(manifest.contains("android.support.FILE_PROVIDER_PATHS"))
        assertTrue(manifest.contains("@xml/file_paths"))
        // Sharing needs no storage permission.
        assertTrue(!manifest.contains("STORAGE"))
        assertEquals("com.pocketarcade.photos", PhotoShare.authority("com.pocketarcade"))
    }

    @Test
    fun thePathsFileSharesOnlyThePhotosFolder() {
        val paths = source("src/main/res/xml/file_paths.xml")
        val entries = Regex("<([a-z-]+)\\s+name=\"([^\"]*)\"\\s+path=\"([^\"]*)\"").findAll(paths).toList()
        assertEquals("only one folder is shared", 1, entries.size)
        val (kind, _, path) = entries.single().destructured
        // files-path is the app's own files directory, the one PhotoStore writes under.
        assertEquals("files-path", kind)
        assertEquals("${PhotoStore.DIR_NAME}/", path)
    }
}
