package com.santhomach.personalchecklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class MissedItemsTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val today = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChecklistRepository(db) { today }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun checklist(title: String = "Morning") =
        db.checklistDao().insert(ChecklistEntity(title = title, type = ChecklistType.DAILY))

    /** Adds a day [daysAgo] before today with the given items; `true` = ticked. */
    private suspend fun day(checklistId: Long, daysAgo: Long, vararg items: Pair<String, Boolean>) {
        val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = checklistId, date = today.minusDays(daysAgo)))
        db.itemDao().insertAll(items.mapIndexed { i, (text, checked) -> ItemEntity(dayId = dayId, text = text, isChecked = checked, position = i) })
    }

    private suspend fun report(checklistId: Long, period: MissedPeriod = MissedPeriod.ALL) =
        repository.observeMissedItems(checklistId, period).first()

    @Test
    fun countsMissedDaysPerItemMostMissedFirst() = runTest {
        val id = checklist()
        day(id, 3, "Run" to true, "Read" to false, "Stretch" to false)
        day(id, 2, "Run" to true, "Read" to true, "Stretch" to false)
        day(id, 1, "Run" to false, "Read" to false, "Stretch" to false)

        val report = report(id)

        assertEquals(3, report.pastDays)
        assertEquals(
            listOf(MissedItemStat("Stretch", 3, 3), MissedItemStat("Read", 2, 3), MissedItemStat("Run", 1, 3)),
            report.missed,
        )
        assertTrue(report.neverMissed.isEmpty())
    }

    @Test
    fun todayIsNotCounted() = runTest {
        val id = checklist()
        day(id, 1, "Run" to true)
        day(id, 0, "Run" to false) // today, still in progress

        val report = report(id)
        assertEquals(1, report.pastDays)
        assertTrue(report.missed.isEmpty())
        assertEquals(listOf("Run"), report.neverMissed)
    }

    @Test
    fun itemsTickedEveryDayAreListedAsNeverMissed() = runTest {
        val id = checklist()
        day(id, 2, "Water plants" to true, "Read" to false, "Alarm" to true)
        day(id, 1, "Water plants" to true, "Read" to true, "Alarm" to true)

        val report = report(id)
        assertEquals(listOf("Read"), report.missed.map { it.text })
        assertEquals(listOf("Alarm", "Water plants"), report.neverMissed)
    }

    @Test
    fun weekPeriodCoversExactlySevenDaysBeforeToday() = runTest {
        val id = checklist()
        day(id, 8, "Run" to false) // outside the week
        day(id, 7, "Run" to false) // first day of the week
        day(id, 1, "Run" to true)  // last day of the week

        val week = report(id, MissedPeriod.WEEK)
        assertEquals(2, week.pastDays)
        assertEquals(listOf(MissedItemStat("Run", 1, 2)), week.missed)
    }

    @Test
    fun monthAndAllTimeIncludeOlderDays() = runTest {
        val id = checklist()
        day(id, 100, "Run" to false)
        day(id, 30, "Run" to false)
        day(id, 10, "Run" to false)
        day(id, 3, "Run" to true)

        val week = report(id, MissedPeriod.WEEK)
        assertEquals(1, week.pastDays)
        assertTrue(week.missed.isEmpty())
        assertEquals(listOf("Run"), week.neverMissed)
        // 30 days ago is the first day of the month period.
        assertEquals(listOf(MissedItemStat("Run", 2, 3)), report(id, MissedPeriod.MONTH).missed)
        assertEquals(listOf(MissedItemStat("Run", 3, 4)), report(id, MissedPeriod.ALL).missed)
    }

    @Test
    fun itemsAreMatchedByNameAcrossDays() = runTest {
        val id = checklist()
        day(id, 2, "Read" to false)
        day(id, 1, "Read" to false)
        assertEquals(listOf(MissedItemStat("Read", 2, 2)), report(id).missed)
    }

    @Test
    fun itemAddedOrDeletedPartWayOnlyCountsItsOwnDays() = runTest {
        val id = checklist()
        day(id, 4, "Old habit" to false, "Run" to true)
        day(id, 3, "Old habit" to false, "Run" to true)
        day(id, 2, "Run" to true, "New habit" to false)
        day(id, 1, "Run" to true, "New habit" to true)

        val report = report(id)
        assertEquals(4, report.pastDays)
        assertEquals(
            listOf(MissedItemStat("Old habit", 2, 2), MissedItemStat("New habit", 1, 2)),
            report.missed,
        )
        assertEquals(listOf("Run"), report.neverMissed)
    }

    @Test
    fun sameNameTwiceOnOneDayCountsThatDayOnce() = runTest {
        val id = checklist()
        day(id, 1, "Water" to false, "Water" to true)
        assertEquals(listOf(MissedItemStat("Water", 1, 1)), report(id).missed)
    }

    @Test
    fun tiesAreBrokenByMissRateThenName() = runTest {
        val id = checklist()
        day(id, 3, "beta" to false, "Alpha" to true, "Gamma" to false)
        day(id, 2, "beta" to true, "Alpha" to false, "Gamma" to false)
        day(id, 1, "beta" to false, "Alpha" to false) // Gamma removed: 2 of 2 days

        assertEquals(
            listOf(MissedItemStat("Gamma", 2, 2), MissedItemStat("Alpha", 2, 3), MissedItemStat("beta", 2, 3)),
            report(id).missed,
        )
    }

    @Test
    fun otherChecklistsAreIgnored() = runTest {
        val morning = checklist("Morning")
        val evening = checklist("Evening")
        day(morning, 1, "Run" to false)
        day(evening, 1, "Read" to false)

        assertEquals(listOf("Run"), report(morning).missed.map { it.text })
        assertEquals(listOf("Read"), report(evening).missed.map { it.text })
    }

    @Test
    fun brandNewChecklistHasNoPastDays() = runTest {
        val id = repository.createChecklist("Fresh", ChecklistType.DAILY)
        repository.addItem(repository.observeCurrentDay(id).first()!!.id, "Run")

        val report = report(id)
        assertEquals(0, report.pastDays)
        assertTrue(report.missed.isEmpty())
        assertTrue(report.neverMissed.isEmpty())
    }

    @Test
    fun dayWithNoItemsCountsAsPastDay() = runTest {
        val id = checklist()
        day(id, 2)
        day(id, 1, "Run" to false)
        val report = report(id)
        assertEquals(2, report.pastDays)
        assertEquals(listOf(MissedItemStat("Run", 1, 1)), report.missed)
    }

    @Test
    fun worksWithRealDailyRollover() = runTest {
        var date = today.minusDays(2)
        val rolling = ChecklistRepository(db) { date }
        val id = rolling.createChecklist("Morning", ChecklistType.DAILY)
        val firstDay = rolling.observeCurrentDay(id).first()!!.id
        rolling.addItem(firstDay, "Run")
        rolling.addItem(firstDay, "Read")
        rolling.setChecked(rolling.observeItems(firstDay).first()[0].id, true) // Run done, Read missed
        date = date.plusDays(1)
        rolling.ensureToday() // nothing ticked on day 2
        date = date.plusDays(1)
        rolling.ensureToday() // today

        val report = rolling.observeMissedItems(id, MissedPeriod.ALL).first()
        assertEquals(2, report.pastDays)
        assertEquals(listOf(MissedItemStat("Read", 2, 2), MissedItemStat("Run", 1, 2)), report.missed)
    }
}
