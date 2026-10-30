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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MonthlyChecklistTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val firstOfMonth = LocalDate.of(2026, 10, 1)
    private val firstOfNextMonth = LocalDate.of(2026, 11, 1)
    private var today = LocalDate.of(2026, 10, 14)

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

    /** A monthly checklist created today with "Pay rent" ticked and "Backup photos" not. */
    private suspend fun monthlyBills(): Long {
        val id = repository.createChecklist("Bills", ChecklistType.MONTHLY)
        val dayId = currentDay(id).id
        repository.addItem(dayId, "Pay rent")
        repository.addItem(dayId, "Backup photos")
        repository.setChecked(items(dayId).first().id, true)
        return id
    }

    @Test
    fun monthlyPeriodStartsOnTheFirst() {
        assertEquals(firstOfMonth, ChecklistType.MONTHLY.periodStart(firstOfMonth))
        assertEquals(firstOfMonth, ChecklistType.MONTHLY.periodStart(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2028, 2, 1), ChecklistType.MONTHLY.periodStart(LocalDate.of(2028, 2, 29)))
        assertTrue(ChecklistType.MONTHLY.repeats)
    }

    @Test
    fun newMonthlyChecklistIsDatedByTheFirst() = runTest {
        val id = repository.createChecklist("Bills", ChecklistType.MONTHLY)
        assertEquals(ChecklistType.MONTHLY, repository.observeChecklist(id).first()!!.type)
        assertEquals(firstOfMonth, currentDay(id).date)
    }

    @Test
    fun noNewCopyDuringTheMonth() = runTest {
        val id = monthlyBills()
        while (today < LocalDate.of(2026, 10, 31)) {
            today = today.plusDays(1)
            repository.ensureToday()
        }
        assertEquals(1, history(id).size)
        assertEquals(listOf(true, false), items(currentDay(id).id).map { it.isChecked })
    }

    @Test
    fun freshUntickedCopyOnTheFirst() = runTest {
        val id = monthlyBills()
        val lastMonth = currentDay(id)
        today = firstOfNextMonth
        repository.ensureToday()
        repository.ensureToday()

        val thisMonth = currentDay(id)
        assertEquals(firstOfNextMonth, thisMonth.date)
        assertEquals(listOf("Pay rent", "Backup photos"), items(thisMonth.id).map { it.text })
        assertTrue(items(thisMonth.id).none { it.isChecked })
        assertEquals(listOf(true, false), items(lastMonth.id).map { it.isChecked })
        assertEquals(listOf(firstOfNextMonth, firstOfMonth), history(id).map { it.date })
    }

    @Test
    fun otherTypesUnaffectedOverAMonthBoundary() = runTest {
        val monthly = monthlyBills()
        val weekly = repository.createChecklist("Chores", ChecklistType.WEEKLY)
        val daily = repository.createChecklist("Morning", ChecklistType.DAILY)
        today = LocalDate.of(2026, 10, 30) // Friday
        repository.ensureToday()
        today = firstOfNextMonth // Sunday
        repository.ensureToday()

        assertEquals(2, history(monthly).size)
        assertEquals(2, history(weekly).size) // weeks of 12 Oct and 26 Oct; 1 Nov is still in the latter
        assertEquals(3, history(daily).size) // 14 Oct, 30 Oct, 1 Nov
    }

    @Test
    fun missedItemsCountMonthsAndSkipTheCurrentMonth() = runTest {
        val id = db.checklistDao().insert(ChecklistEntity(title = "Bills", type = ChecklistType.MONTHLY))
        suspend fun month(monthsAgo: Long, vararg items: Pair<String, Boolean>) {
            val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = id, date = firstOfMonth.minusMonths(monthsAgo)))
            db.itemDao().insertAll(items.mapIndexed { i, (text, checked) -> ItemEntity(dayId = dayId, text = text, isChecked = checked, position = i) })
        }
        month(5, "Rent" to false)
        month(2, "Rent" to false, "Photos" to true)
        month(1, "Rent" to true, "Photos" to false)
        month(0, "Rent" to false, "Photos" to false) // this month: not over yet

        val recent = repository.observeMissedItems(id, MissedPeriod.SHORT).first() // last 3 months
        assertEquals(2, recent.pastPeriods)
        assertEquals(listOf(MissedItemStat("Photos", 1, 2), MissedItemStat("Rent", 1, 2)), recent.missed)

        val all = repository.observeMissedItems(id, MissedPeriod.ALL).first()
        assertEquals(3, all.pastPeriods)
        assertEquals(listOf(MissedItemStat("Rent", 2, 3), MissedItemStat("Photos", 1, 2)), all.missed)
    }

    @Test
    fun backupRoundTripKeepsMonthlyType() = runTest {
        monthlyBills()
        repository.importBackup(BackupJson.decode(BackupJson.encode(repository.exportBackup())))

        val restored = repository.observeChecklists().first().single()
        assertEquals(ChecklistType.MONTHLY, restored.type)
        assertEquals(listOf(firstOfMonth), history(restored.id).map { it.date })
    }

    @Test
    fun importedMonthlyChecklistWithoutDaysGetsThisMonthsCopy() = runTest {
        repository.importBackup(
            Backup(
                exportedAt = "2026-10-14T10:00:00",
                checklists = listOf(BackupChecklist("Bills", ChecklistType.MONTHLY, 1L, emptyList())),
            )
        )
        val id = repository.observeChecklists().first().single().id
        assertEquals(listOf(firstOfMonth), history(id).map { it.date })
    }
}
