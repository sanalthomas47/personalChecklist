package com.santhomach.personalchecklist.data.backup

import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Everything the app stores: each checklist with every dated copy and its items. IDs are not
 * exported; import creates fresh rows and keeps the order of days and items from the file.
 */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val checklists: List<BackupChecklist>,
) {
    companion object {
        const val FORMAT = "personal-checklist-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupChecklist(
    val title: String,
    val type: ChecklistType,
    val createdAt: Long,
    val days: List<BackupDay>,
)

@Serializable
data class BackupDay(
    /** ISO date, e.g. "2026-10-17". */
    val date: String,
    val items: List<BackupItem>,
)

@Serializable
data class BackupItem(
    val text: String,
    @SerialName("checked") val isChecked: Boolean,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupJson {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Parses and validates a backup file, so a bad file is rejected before any data is touched. */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("This isn't a checklist backup file.", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This isn't a checklist backup file.", e)
        }
        validate(backup)
        return backup
    }

    private fun validate(backup: Backup) {
        if (backup.format != Backup.FORMAT) {
            throw InvalidBackupException("This isn't a checklist backup file.")
        }
        if (backup.version > Backup.VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of the app.")
        }
        for (checklist in backup.checklists) {
            if (checklist.title.isBlank()) throw InvalidBackupException("A checklist in the backup has no name.")
            val dates = checklist.days.map { parseDate(it.date, checklist.title) }
            if (dates.toSet().size != dates.size) {
                throw InvalidBackupException("\"${checklist.title}\" has the same date more than once.")
            }
            if (checklist.days.any { day -> day.items.any { it.text.isBlank() } }) {
                throw InvalidBackupException("\"${checklist.title}\" has an item with no text.")
            }
        }
    }

    internal fun parseDate(date: String, title: String): LocalDate = try {
        LocalDate.parse(date)
    } catch (e: DateTimeParseException) {
        throw InvalidBackupException("\"$title\" has an invalid date: $date", e)
    }
}
