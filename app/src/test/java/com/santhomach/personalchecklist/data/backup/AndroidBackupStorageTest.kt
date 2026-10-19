package com.santhomach.personalchecklist.data.backup

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AndroidBackupStorageTest {
    private lateinit var context: Context
    private lateinit var storage: AndroidBackupStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = AndroidBackupStorage(context)
    }

    private fun installMediaStore(configure: FakeMediaStore.() -> Unit = {}): FakeMediaStore =
        Robolectric.buildContentProvider(FakeMediaStore::class.java)
            .create(ProviderInfo().apply { authority = MediaStore.AUTHORITY })
            .get()
            .apply(configure)

    // --- Android 10+ (MediaStore) ---

    @Test
    fun savesJsonFileToDownloadsThroughMediaStore() = runTest {
        val media = installMediaStore()

        val savedAs = storage.saveToDownloads("checklist-backup-2026-10-18-093015.json", """{"a":1}""")

        assertEquals("checklist-backup-2026-10-18-093015.json", savedAs)
        val entry = media.entries.values.single()
        assertEquals("checklist-backup-2026-10-18-093015.json", entry.values.getAsString(MediaStore.Downloads.DISPLAY_NAME))
        assertEquals("application/json", entry.values.getAsString(MediaStore.Downloads.MIME_TYPE))
        assertEquals("""{"a":1}""", entry.file.readText())
    }

    @Test
    fun fileIsMarkedPendingWhileWritingAndPublishedAfter() = runTest {
        val media = installMediaStore()
        storage.saveToDownloads("backup.json", "{}")

        val entry = media.entries.values.single()
        assertEquals(1, entry.insertedPending)
        assertEquals(0, entry.values.getAsInteger(MediaStore.Downloads.IS_PENDING))
    }

    @Test
    fun reportsNameChosenByMediaStoreWhenNameIsTaken() = runTest {
        installMediaStore { renameTo = "backup (1).json" }
        assertEquals("backup (1).json", storage.saveToDownloads("backup.json", "{}"))
    }

    @Test
    fun keepsUnicodeTextIntact() = runTest {
        val media = installMediaStore()
        val text = """{"title":"Morgen ☀️ – café, 日本語, \"quoted\""}"""
        storage.saveToDownloads("backup.json", text)
        assertEquals(text, media.entries.values.single().file.readText())
    }

    @Test
    fun failsWhenMediaStoreRefusesToCreateFile() = runTest {
        installMediaStore { refuseInsert = true }
        val error = runCatching { storage.saveToDownloads("backup.json", "{}") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("Couldn't create the file in Downloads.", error!!.message)
    }

    @Test
    fun removesHalfWrittenFileWhenWriteFails() = runTest {
        val media = installMediaStore { failOpen = true }
        val error = runCatching { storage.saveToDownloads("backup.json", "{}") }.exceptionOrNull()
        assertTrue(error != null)
        assertTrue("half-written entry should be deleted", media.entries.isEmpty())
        assertEquals(1, media.deletedCount)
    }

    // --- Android 8–9 (direct file) ---

    @Test
    @Config(sdk = [28])
    fun savesDirectlyToDownloadsFolderOnAndroid9() = runTest {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val savedAs = storage.saveToDownloads("checklist-backup.json", """{"b":2}""")

        assertEquals("checklist-backup.json", savedAs)
        assertEquals("""{"b":2}""", File(downloads, "checklist-backup.json").readText())
    }

    // --- Reading the picked file ---

    @Test
    fun readsSelectedFile() = runTest {
        val file = File(context.cacheDir, "picked.json").apply { writeText("""{"c":3} ✓""") }
        assertEquals("""{"c":3} ✓""", storage.read(Uri.fromFile(file)))
    }

    @Test
    fun readingMissingFileFails() {
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.json"))
        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { storage.read(missing) } }
    }

    @Test
    fun savedFileCanBeReadBack() = runTest {
        val media = installMediaStore()
        storage.saveToDownloads("roundtrip.json", "hello")
        val uri = media.entries.keys.single()
        assertEquals("hello", storage.read(uri))
    }
}

/** Minimal stand-in for the system MediaStore provider, backed by temp files. */
class FakeMediaStore : ContentProvider() {
    class Entry(val values: ContentValues, val file: File, val insertedPending: Int?)

    val entries = linkedMapOf<Uri, Entry>()
    var deletedCount = 0
    var renameTo: String? = null
    var refuseInsert = false
    var failOpen = false
    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (refuseInsert) return null
        val stored = ContentValues(values)
        renameTo?.let { stored.put(MediaStore.Downloads.DISPLAY_NAME, it) }
        val itemUri = ContentUris.withAppendedId(uri, nextId++)
        val file = File.createTempFile("media", ".json")
        entries[itemUri] = Entry(stored, file, stored.getAsInteger(MediaStore.Downloads.IS_PENDING))
        return itemUri
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failOpen) throw IOException("Storage unavailable")
        val file = entries[uri]?.file ?: throw IOException("No such entry")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val entry = entries[uri] ?: return 0
        values?.let { entry.values.putAll(it) }
        return 1
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        return MatrixCursor(columns).apply {
            entries[uri]?.let { entry -> addRow(columns.map { entry.values.get(it) }.toTypedArray()) }
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deletedCount++
        return if (entries.remove(uri) != null) 1 else 0
    }

    override fun getType(uri: Uri): String? = entries[uri]?.values?.getAsString(MediaStore.Downloads.MIME_TYPE)
}
