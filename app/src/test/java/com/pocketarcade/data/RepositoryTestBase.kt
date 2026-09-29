package com.pocketarcade.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Okio's file system, except that saving over an existing file replaces it. DataStore's stock
 * storage does that with `File.renameTo`, which refuses on Windows once the file exists, so a
 * second write would fail there (on Android and Linux it replaces atomically).
 */
private object ReplacingFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
    override fun atomicMove(source: Path, target: Path) {
        Files.move(source.toFile().toPath(), target.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Runs repository tests against a real DataStore in a temp file, the same code path as the app
 * (serialiser, atomic edits, corruption recovery), so the encodings and the recovery are exercised.
 */
abstract class RepositoryTestBase {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    /** The save file (DataStore insists on the `preferences_pb` extension). */
    val file: File by lazy { File(tmp.newFolder(), "save.preferences_pb") }

    /** A store over [file], with the app's corruption handler unless [handler] says otherwise. */
    fun newStore(
        handler: ReplaceFileCorruptionHandler<Preferences>? = ArcadeRepository.corruptionHandler,
    ): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        val storage = OkioStorage(ReplacingFileSystem, PreferencesSerializer, producePath = { file.absolutePath.toPath() })
        return PreferenceDataStoreFactory.create(storage = storage, corruptionHandler = handler, scope = scope)
    }

    /**
     * Shuts every store down and waits for it, which frees the file for a fresh store: the next
     * app launch. (DataStore refuses two live stores over one file.)
     */
    suspend fun relaunch() {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun closeStores() {
        runBlocking { relaunch() }
    }

    /** `runBlocking` that returns Unit, so a test can be written as `= blocking { ... }`. */
    fun blocking(body: suspend CoroutineScope.() -> Unit) = runBlocking(block = body)

    /** Writes the raw saved values a test wants to start from, by the on-disk key names. */
    suspend fun DataStore<Preferences>.seed(tokens: Int? = null, tickets: Int? = null) {
        edit { p ->
            if (tokens != null) p[KEY_TOKENS] = tokens
            if (tickets != null) p[KEY_TICKETS] = tickets
        }
    }

    companion object {
        // Spelled out, not taken from the repository: a rename would orphan every player's save.
        val KEY_TOKENS = intPreferencesKey("tokens")
        val KEY_TICKETS = intPreferencesKey("tickets")
        val KEY_COLLECTION = stringPreferencesKey("collection")
    }
}
