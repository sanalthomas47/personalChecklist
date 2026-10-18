package com.santhomach.personalchecklist.data.backup

import android.net.Uri
import java.io.IOException

/** In-memory stand-in for the Downloads folder. A file's URI is `file:///<name>`. */
class FakeBackupStorage : BackupStorage {
    val files = linkedMapOf<String, String>()
    var failWrites = false

    override suspend fun saveToDownloads(fileName: String, text: String): String {
        if (failWrites) throw IOException("Disk full")
        files[fileName] = text
        return fileName
    }

    override suspend fun read(uri: Uri): String =
        files[uri.lastPathSegment] ?: throw IOException("File not found")

    fun uriFor(name: String): Uri = Uri.parse("file:///$name")
}
