package com.santhomach.personalchecklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.backup.Backup
import com.santhomach.personalchecklist.data.backup.BackupChecklist
import com.santhomach.personalchecklist.data.backup.BackupDay
import com.santhomach.personalchecklist.data.backup.BackupItem
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.backup.InvalidBackupException
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private var today = LocalDate.of(2026, 10, 18)
    private val exportTime = LocalDateTime.of(2026, 10, 18, 9, 30, 15, 123_000_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = ChecklistRepository(db) { today }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun currentDay(checklistId: Long) =
        repository.observeCurrentDay(checklistId).first()!!

    private suspend fun items(dayId: Long) = repository.observeItems(dayId).first()

    /** Two days of a daily checklist (with ticks) plus a one-time checklist. */
    private suspend fun seed() {
        today = LocalDate.of(2026, 10, 17)
        val morning = repository.createChecklist("Morning", ChecklistType.DAILY)
        val day1 = currentDay(morning).id
        repository.addItem(day1, "Run")
        repository.addItem(day1, "Read")
        repository.setChecked(items(day1).first().id, true)

        today = LocalDate.of(2026, 10, 18)
        repository.ensureToday()
        repository.addItem(currentDay(morning).id, "Stretch")

        val trip = repository.createChecklist("Trip", ChecklistType.ONE_TIME)
        repository.addItem(currentDay(trip).id, "Pack")
        repository.setChecked(items(currentDay(trip).id).single().id, true)
    }

    @Test
    fun exportIncludesEveryChecklistDayAndItem() = runTest {
        seed()
        val backup = repository.exportBackup(exportTime)

        assertEquals("2026-10-18T09:30:15", backup.exportedAt)
        assertEquals(listOf("Morning", "Trip"), backup.checklists.map { it.title })
        assertEquals(
            listOf(ChecklistType.DAILY, ChecklistType.ONE_TIME),
            backup.checklists.map { it.type })

        val morning = backup.checklists[0]
        assertEquals(listOf("2026-10-17", "2026-10-18"), morning.days.map { it.date })
        assertEquals(
            listOf(BackupItem("Run", true), BackupItem("Read", false)),
            morning.days[0].items
        )
        assertEquals(
            listOf(
                BackupItem("Run", false),
                BackupItem("Read", false),
                BackupItem("Stretch", false)
            ),
            morning.days[1].items,
        )
        assertEquals(listOf(BackupItem("Pack", true)), backup.checklists[1].days.single().items)
    }

    @Test
    fun exportOfEmptyAppHasNoChecklists() = runTest {
        assertTrue(repository.exportBackup(exportTime).checklists.isEmpty())
    }

    @Test
    fun exportThenImportRestoresIdenticalData() = runTest {
        seed()
        val json = BackupJson.encode(repository.exportBackup(exportTime))

        // Wipe and change things, then restore.
        repository.observeChecklists().first().forEach { repository.deleteChecklist(it.id) }
        repository.createChecklist("Should disappear", ChecklistType.DAILY)
        repository.importBackup(BackupJson.decode(json))

        assertEquals(json, BackupJson.encode(repository.exportBackup(exportTime)))
    }

    @Test
    fun importReplacesExistingData() = runTest {
        val old = repository.createChecklist("Old", ChecklistType.DAILY)
        repository.addItem(currentDay(old).id, "Old item")

        repository.importBackup(
            Backup(
                exportedAt = "2026-10-18T09:30:00",
                checklists = listOf(
                    BackupChecklist(
                        "New",
                        ChecklistType.ONE_TIME,
                        1L,
                        listOf(BackupDay("2026-10-10", listOf(BackupItem("A", true))))
                    ),
                ),
            )
        )

        val list = repository.observeChecklists().first()
        assertEquals(listOf("New"), list.map { it.title })
        assertEquals(1 to 1, list.single().done to list.single().total)
        assertTrue(repository.observeHistory(old).first().isEmpty())
    }

    @Test
    fun importRestoresHistoryWithCompletion() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "2026-10-18T09:30:00",
                checklists = listOf(
                    BackupChecklist(
                        "Morning", ChecklistType.DAILY, 1L,
                        listOf(
                            BackupDay(
                                "2026-10-16",
                                listOf(BackupItem("A", true), BackupItem("B", true))
                            ),
                            BackupDay(
                                "2026-10-17",
                                listOf(BackupItem("A", true), BackupItem("B", false))
                            ),
                            BackupDay(
                                "2026-10-18",
                                listOf(BackupItem("A", false), BackupItem("B", false))
                            ),
                        ),
                    ),
                ),
            )
        )
        val id = repository.observeChecklists().first().single().id
        val history = repository.observeHistory(id).first()
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), history.map { it.date })
        assertEquals(listOf(0, 1, 2), history.map { it.done })
        assertEquals(listOf("A", "B"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun importOfOldBackupAddsTodaysCopyForDailyChecklistsOnly() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "2026-10-01T09:30:00",
                checklists = listOf(
                    BackupChecklist(
                        "Daily",
                        ChecklistType.DAILY,
                        1L,
                        listOf(BackupDay("2026-10-01", listOf(BackupItem("A", true))))
                    ),
                    BackupChecklist(
                        "Once",
                        ChecklistType.ONE_TIME,
                        2L,
                        listOf(BackupDay("2026-10-01", listOf(BackupItem("B", true))))
                    ),
                ),
            )
        )
        val (daily, once) = repository.observeChecklists().first()

        val dailyDay = currentDay(daily.id)
        assertEquals(today, dailyDay.date)
        assertEquals(listOf("A" to false), items(dailyDay.id).map { it.text to it.isChecked })
        assertEquals(2, repository.observeHistory(daily.id).first().size)

        assertEquals(LocalDate.of(2026, 10, 1), currentDay(once.id).date)
        assertEquals(1 to 1, once.done to once.total)
    }

    @Test
    fun importGivesChecklistWithoutDaysAnEmptyToday() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "x",
                checklists = listOf(
                    BackupChecklist(
                        "Bare",
                        ChecklistType.ONE_TIME,
                        1L,
                        emptyList()
                    )
                )
            )
        )
        val id = repository.observeChecklists().first().single().id
        assertEquals(today, currentDay(id).date)
    }

    @Test
    fun importOfEmptyBackupClearsEverything() = runTest {
        seed()
        repository.importBackup(Backup(exportedAt = "x", checklists = emptyList()))
        assertTrue(repository.observeChecklists().first().isEmpty())
    }

    @Test
    fun importedChecklistsWorkNormallyAfterwards() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "x",
                checklists = listOf(
                    BackupChecklist(
                        "Morning",
                        ChecklistType.DAILY,
                        1L,
                        listOf(BackupDay(today.toString(), listOf(BackupItem("A", false))))
                    )
                ),
            )
        )
        val id = repository.observeChecklists().first().single().id
        val dayId = currentDay(id).id
        repository.addItem(dayId, "B")
        assertEquals(listOf("A", "B"), items(dayId).map { it.text })
        assertEquals(listOf(0, 1), items(dayId).map { it.position })

        today = today.plusDays(1)
        repository.ensureToday()
        assertEquals(listOf("A", "B"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun failedImportLeavesExistingDataUntouched() = runTest {
        seed()
        val before = BackupJson.encode(repository.exportBackup(exportTime))
        // The second checklist is broken, so the import fails part-way through.
        val broken = Backup(
            exportedAt = "x",
            checklists = listOf(
                BackupChecklist(
                    "Fine",
                    ChecklistType.DAILY,
                    1L,
                    listOf(BackupDay("2026-10-18", listOf(BackupItem("A", false))))
                ),
                BackupChecklist(
                    "Broken",
                    ChecklistType.DAILY,
                    2L,
                    listOf(BackupDay("not-a-date", emptyList()))
                ),
            ),
        )

        val error = runCatching { repository.importBackup(broken) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertEquals(before, BackupJson.encode(repository.exportBackup(exportTime)))
    }

    @Test
    fun specialCharactersSurviveRoundTrip() = runTest {
        val title = "Morgen ☀️ \"routine\" – café & 日本語"
        val itemText = "Line with \\ backslash, {braces}, [brackets] and émojis 🏃‍♂️"
        val id = repository.createChecklist(title, ChecklistType.DAILY)
        repository.addItem(currentDay(id).id, itemText)

        val json = BackupJson.encode(repository.exportBackup(exportTime))
        repository.importBackup(BackupJson.decode(json))

        val restored = repository.observeChecklists().first().single()
        assertEquals(title, restored.title)
        assertEquals(listOf(itemText), items(currentDay(restored.id).id).map { it.text })
    }

    @Test
    fun yearOfHistorySurvivesRoundTrip() = runTest {
        today = LocalDate.of(2025, 10, 18)
        val id = repository.createChecklist("Year", ChecklistType.DAILY)
        listOf("A", "B", "C", "D").forEach { repository.addItem(currentDay(id).id, it) }
        repeat(365) { dayIndex ->
            // Tick a different number of items each day: 0..4.
            items(currentDay(id).id).take(dayIndex % 5)
                .forEach { repository.setChecked(it.id, true) }
            today = today.plusDays(1)
            repository.ensureToday()
        }
        val historyBefore =
            repository.observeHistory(id).first().map { Triple(it.date, it.done, it.total) }
        val json = BackupJson.encode(repository.exportBackup(exportTime))

        repository.importBackup(BackupJson.decode(json))

        val newId = repository.observeChecklists().first().single().id
        val historyAfter =
            repository.observeHistory(newId).first().map { Triple(it.date, it.done, it.total) }
        assertEquals(366, historyAfter.size)
        assertEquals(historyBefore, historyAfter)
    }

    @Test
    fun importTrimsWhitespaceInTitlesAndItems() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "x",
                checklists = listOf(
                    BackupChecklist(
                        "  Spaced  ",
                        ChecklistType.DAILY,
                        1L,
                        listOf(BackupDay(today.toString(), listOf(BackupItem("  item  ", false))))
                    )
                ),
            )
        )
        val checklist = repository.observeChecklists().first().single()
        assertEquals("Spaced", checklist.title)
        assertEquals(listOf("item"), items(currentDay(checklist.id).id).map { it.text })
    }
}
