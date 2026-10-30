package com.santhomach.personalchecklist.data.backup

import com.santhomach.personalchecklist.data.db.ChecklistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupJsonTest {
    private val sample = Backup(
        exportedAt = "2026-10-18T09:30:00",
        checklists = listOf(
            BackupChecklist(
                title = "Morning",
                type = ChecklistType.DAILY,
                createdAt = 1_000L,
                days = listOf(
                    BackupDay(
                        "2026-10-17",
                        listOf(BackupItem("Run", true), BackupItem("Read", false))
                    ),
                    BackupDay(
                        "2026-10-18",
                        listOf(BackupItem("Run", false), BackupItem("Read", false))
                    ),
                ),
            ),
            BackupChecklist(
                "Trip",
                ChecklistType.ONE_TIME,
                2_000L,
                listOf(BackupDay("2026-10-18", emptyList()))
            ),
        ),
    )

    private fun assertRejected(json: String, messagePart: String) {
        val error = assertThrows(InvalidBackupException::class.java) { BackupJson.decode(json) }
        assertTrue(
            "\"${error.message}\" should mention \"$messagePart\"",
            error.message!!.contains(messagePart)
        )
    }

    @Test
    fun encodeThenDecodeGivesSameBackup() {
        assertEquals(sample, BackupJson.decode(BackupJson.encode(sample)))
    }

    @Test
    fun encodedJsonIsReadable() {
        val json = BackupJson.encode(sample)
        assertTrue(json.contains("\"format\": \"personal-checklist-backup\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"type\": \"ONE_TIME\""))
        assertTrue(json.contains("\"date\": \"2026-10-17\""))
        assertTrue(json.contains("\"checked\": true"))
        assertTrue("pretty printed", json.contains("\n"))
    }

    @Test
    fun decodesHandWrittenFileAndIgnoresUnknownFields() {
        val json = """
            {
              "format": "personal-checklist-backup",
              "version": 1,
              "exportedAt": "2026-10-18T09:30:00",
              "someFutureField": 42,
              "checklists": [
                { "title": "Gym", "type": "DAILY", "createdAt": 5, "colour": "red",
                  "days": [ { "date": "2026-10-18", "items": [ { "text": "Squats", "checked": true } ] } ] }
              ]
            }
        """.trimIndent()
        val backup = BackupJson.decode(json)
        assertEquals("Gym", backup.checklists.single().title)
        assertEquals(
            BackupItem("Squats", true),
            backup.checklists.single().days.single().items.single()
        )
    }

    @Test
    fun emptyBackupIsValid() {
        val empty = Backup(
            exportedAt = LocalDateTime.of(2026, 10, 18, 9, 30).toString(),
            checklists = emptyList()
        )
        assertEquals(empty, BackupJson.decode(BackupJson.encode(empty)))
    }

    @Test
    fun rejectsTextThatIsNotJson() =
        assertRejected("hello, this is a shopping list", "isn't a checklist backup")

    @Test
    fun rejectsEmptyFile() = assertRejected("", "isn't a checklist backup")

    @Test
    fun rejectsOtherJson() =
        assertRejected("""{"name": "something else"}""", "isn't a checklist backup")

    @Test
    fun rejectsWrongFormatMarker() =
        assertRejected(
            """{"format": "other-app", "version": 1, "exportedAt": "x", "checklists": []}""",
            "isn't a checklist backup"
        )

    @Test
    fun rejectsNewerVersion() = assertRejected(
        """{"format": "personal-checklist-backup", "version": 2, "exportedAt": "x", "checklists": []}""",
        "newer version",
    )

    @Test
    fun rejectsUnknownChecklistType() = assertRejected(
        BackupJson.encode(sample).replace("\"ONE_TIME\"", "\"MONTHLY\""),
        "isn't a checklist backup",
    )

    @Test
    fun rejectsBlankTitle() = assertRejected(
        BackupJson.encode(sample.copy(checklists = listOf(sample.checklists[0].copy(title = "  ")))),
        "no name",
    )

    @Test
    fun rejectsInvalidDate() = assertRejected(
        BackupJson.encode(sample).replace("2026-10-17", "2026-13-45"),
        "invalid date",
    )

    @Test
    fun rejectsDuplicateDates() = assertRejected(
        BackupJson.encode(sample).replace("2026-10-17", "2026-10-18"),
        "same date more than once",
    )

    @Test
    fun rejectsBlankItemText() = assertRejected(
        BackupJson.encode(sample).replace("\"Read\"", "\"\""),
        "item with no text",
    )

    @Test
    fun fileNameIsTimestamped() {
        assertEquals(
            "checklist-backup-2026-10-18-093005.json",
            BackupStorage.fileName(LocalDateTime.of(2026, 10, 18, 9, 30, 5)),
        )
    }
}
