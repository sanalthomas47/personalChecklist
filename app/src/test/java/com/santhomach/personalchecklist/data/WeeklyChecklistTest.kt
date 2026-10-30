package com.santhomach.personalchecklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.backup.Backup
import com.santhomach.personalchecklist.data.backup.BackupChecklist
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.data.db.MissedItemStat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeeklyChecklistTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val monday = LocalDate.of(2026, 10, 26)
    private val nextMonday = LocalDate.of(2026, 11, 2)
    private var today = LocalDate.of(2026, 10, 28) // a Wednesday

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChecklistRepository(db) { today }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun currentDay(checklistId: Long) = repository.observeCurrentDay(checklistId).first()!!
    private suspend fun items(dayId: Long) = repository.observeItems(dayId).first()
    private suspend fun history(checklistId: Long) = repository.observeHistory(checklistId).first()

    /** A weekly checklist created today with "Laundry" ticked and "Groceries" not. */
    private suspend fun weeklyChores(): Long {
        val id = repository.createChecklist("Chores", ChecklistType.WEEKLY)
        val dayId = currentDay(id).id
        repository.addItem(dayId, "Laundry")
        repository.addItem(dayId, "Groceries")
        repository.setChecked(items(dayId).first().id, true)
        return id
    }

    // --- Period dates ---

    @Test
    fun weeklyPeriodStartsOnMonday() {
        for (offset in 0L..6L) {
            assertEquals(monday, ChecklistType.WEEKLY.periodStart(monday.plusDays(offset)))
        }
        assertEquals(nextMonday, ChecklistType.WEEKLY.periodStart(nextMonday))
    }

    @Test
    fun dailyAndOneTimePeriodsStartToday() {
        assertEquals(today, ChecklistType.DAILY.periodStart(today))
        assertEquals(today, ChecklistType.ONE_TIME.periodStart(today))
    }

    @Test
    fun onlyDailyAndWeeklyRepeat() {
        assertTrue(ChecklistType.DAILY.repeats)
        assertTrue(ChecklistType.WEEKLY.repeats)
        assertFalse(ChecklistType.ONE_TIME.repeats)
    }

    // --- Copies ---

    @Test
    fun newWeeklyChecklistIsDatedByThisWeeksMonday() = runTest {
        val id = repository.createChecklist("Chores", ChecklistType.WEEKLY)
        assertEquals(ChecklistType.WEEKLY, repository.observeChecklist(id).first()!!.type)
        assertEquals(monday, currentDay(id).date)
    }

    @Test
    fun noNewCopyDuringTheWeek() = runTest {
        val id = weeklyChores()
        repeat(4) { // Thursday to Sunday
            today = today.plusDays(1)
            repository.ensureToday()
        }
        assertEquals(1, history(id).size)
        assertEquals(listOf(true, false), items(currentDay(id).id).map { it.isChecked })
    }

    @Test
    fun freshUntickedCopyOnMonday() = runTest {
        val id = weeklyChores()
        val lastWeek = currentDay(id)
        today = nextMonday
        repository.ensureToday()

        val thisWeek = currentDay(id)
        assertEquals(nextMonday, thisWeek.date)
        assertEquals(listOf("Laundry", "Groceries"), items(thisWeek.id).map { it.text })
        assertTrue(items(thisWeek.id).none { it.isChecked })
        // Last week keeps its ticks.
        assertEquals(listOf(true, false), items(lastWeek.id).map { it.isChecked })
        assertEquals(listOf(nextMonday, monday), history(id).map { it.date })
    }

    @Test
    fun copyingOnMondayIsIdempotent() = runTest {
        val id = weeklyChores()
        today = nextMonday
        repository.ensureToday()
        repository.ensureToday()
        assertEquals(2, history(id).size)
        assertEquals(2, items(currentDay(id).id).size)
    }

    @Test
    fun openingLateInTheWeekStillCopiesFromTheLastWeek() = runTest {
        val id = weeklyChores()
        today = nextMonday.plusWeeks(1).plusDays(3) // app not opened for over a week
        repository.ensureToday()

        assertEquals(listOf(nextMonday.plusWeeks(1), monday), history(id).map { it.date })
        assertEquals(listOf("Laundry", "Groceries"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun dailyKeepsCopyingDailyAlongsideWeekly() = runTest {
        val weekly = weeklyChores()
        val daily = repository.createChecklist("Morning", ChecklistType.DAILY)
        val oneTime = repository.createChecklist("Trip", ChecklistType.ONE_TIME)
        repeat(5) { // Thursday to next Monday
            today = today.plusDays(1)
            repository.ensureToday()
        }
        assertEquals(6, history(daily).size)
        assertEquals(2, history(weekly).size)
        assertEquals(1, history(oneTime).size)
    }

    // --- Most missed items ---

    @Test
    fun missedItemsCountWeeksAndSkipTheCurrentWeek() = runTest {
        val id = db.checklistDao().insert(ChecklistEntity(title = "Chores", type = ChecklistType.WEEKLY))
        suspend fun week(weeksAgo: Long, vararg items: Pair<String, Boolean>) {
            val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = id, date = monday.minusWeeks(weeksAgo)))
            db.itemDao().insertAll(items.mapIndexed { i, (text, checked) -> ItemEntity(dayId = dayId, text = text, isChecked = checked, position = i) })
        }
        week(6, "Run" to false, "Read" to true)
        week(2, "Run" to false, "Read" to true)
        week(1, "Run" to true, "Read" to false)
        week(0, "Run" to false, "Read" to false) // this week: not over yet

        val recent = repository.observeMissedItems(id, MissedPeriod.SHORT).first() // last 4 weeks
        assertEquals(2, recent.pastPeriods)
        assertEquals(listOf(MissedItemStat("Read", 1, 2), MissedItemStat("Run", 1, 2)), recent.missed)

        val all = repository.observeMissedItems(id, MissedPeriod.ALL).first()
        assertEquals(3, all.pastPeriods)
        assertEquals(listOf(MissedItemStat("Run", 2, 3), MissedItemStat("Read", 1, 3)), all.missed)
    }

    // --- Backup ---

    @Test
    fun backupRoundTripKeepsWeeklyType() = runTest {
        weeklyChores()
        val json = BackupJson.encode(repository.exportBackup())
        repository.importBackup(BackupJson.decode(json))

        val restored = repository.observeChecklists().first().single()
        assertEquals(ChecklistType.WEEKLY, restored.type)
        assertEquals(monday, currentDay(restored.id).date)
        assertEquals(1, history(restored.id).size)
    }

    @Test
    fun importedWeeklyChecklistWithoutDaysGetsThisWeeksCopy() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "2026-10-28T10:00:00",
                checklists = listOf(BackupChecklist("Chores", ChecklistType.WEEKLY, 1L, emptyList())),
            )
        )
        val id = repository.observeChecklists().first().single().id
        assertEquals(listOf(monday), history(id).map { it.date })
    }
}
