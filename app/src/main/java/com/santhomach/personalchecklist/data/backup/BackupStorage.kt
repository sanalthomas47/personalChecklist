package com.santhomach.personalchecklist.data.backup

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Reads and writes backup files. An interface so tests can swap in an in-memory version. */
interface BackupStorage {
    /** Saves [text] to the Downloads folder and returns the name it was saved under. */
    suspend fun saveToDownloads(fileName: String, text: String): String

    suspend fun read(uri: Uri): String

    companion object {
        private val fileTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        fun fileName(now: LocalDateTime = LocalDateTime.now()) = "checklist-backup-${now.format(fileTimestamp)}.json"
    }
}

class AndroidBackupStorage(private val context: Context) : BackupStorage {
    override suspend fun saveToDownloads(fileName: String, text: String): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(fileName, text) else saveToFile(fileName, text)
    }

    /** Android 10+: no permission needed for files the app creates itself. */
    private fun saveWithMediaStore(fileName: String, text: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file in Downloads.")
        try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: throw IOException("Couldn't write the file in Downloads.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        // MediaStore renames the file if the name is already taken, so report the name it actually used.
        return resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: fileName
    }

    /** Android 8–9: needs WRITE_EXTERNAL_STORAGE, which the UI requests before exporting. */
    @Suppress("DEPRECATION")
    private fun saveToFile(fileName: String, text: String): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads.mkdirs()
        val file = File(downloads, fileName)
        file.writeText(text)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("application/json"), null)
        return file.name
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Couldn't open the selected file.")
    }
}
