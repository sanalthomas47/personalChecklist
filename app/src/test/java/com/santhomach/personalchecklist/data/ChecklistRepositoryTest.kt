package com.santhomach.personalchecklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ChecklistRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private var today = LocalDate.of(2026, 10, 17)

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
    private suspend fun history(checklistId: Long) = repository.observeHistory(checklistId).first()

    private suspend fun checklistWithItems(
        vararg texts: String,
        type: ChecklistType = ChecklistType.DAILY
    ): Long {
        val id = repository.createChecklist("Routine", type)
        val dayId = currentDay(id).id
        texts.forEach { repository.addItem(dayId, it) }
        return id
    }

    private fun nextDay() {
        today = today.plusDays(1)
    }

    // --- Checklists ---

    @Test
    fun createChecklistTrimsTitleAndStoresType() = runTest {
        val id = repository.createChecklist("  Morning  ", ChecklistType.ONE_TIME)
        val checklist = repository.observeChecklist(id).first()!!
        assertEquals("Morning", checklist.title)
        assertEquals(ChecklistType.ONE_TIME, checklist.type)
    }

    @Test
    fun createChecklistAddsAnEmptyDayForToday() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        val day = currentDay(id)
        assertEquals(today, day.date)
        assertTrue(items(day.id).isEmpty())
    }

    @Test
    fun renameChecklistTrimsTitle() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        repository.renameChecklist(id, "  Evening ")
        assertEquals("Evening", repository.observeChecklist(id).first()!!.title)
    }

    @Test
    fun renameKeepsTypeAndItems() = runTest {
        val id = checklistWithItems("Run")
        repository.renameChecklist(id, "Evening")
        assertEquals(ChecklistType.DAILY, repository.observeChecklist(id).first()!!.type)
        assertEquals(listOf("Run"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun deleteChecklistRemovesAllDaysAndItems() = runTest {
        val id = checklistWithItems("Run", "Read")
        val firstDayId = currentDay(id).id
        nextDay()
        repository.ensureToday()
        val secondDayId = currentDay(id).id

        repository.deleteChecklist(id)

        assertNull(repository.observeChecklist(id).first())
        assertTrue(history(id).isEmpty())
        assertTrue(items(firstDayId).isEmpty())
        assertTrue(items(secondDayId).isEmpty())
        assertTrue(repository.observeChecklists().first().isEmpty())
    }

    @Test
    fun deleteChecklistLeavesOtherChecklistsAlone() = runTest {
        val keep = checklistWithItems("Keep me")
        val remove = checklistWithItems("Remove me")
        repository.deleteChecklist(remove)
        assertEquals(listOf(keep), repository.observeChecklists().first().map { it.id })
        assertEquals(listOf("Keep me"), items(currentDay(keep).id).map { it.text })
    }

    // --- Items ---

    @Test
    fun addItemTrimsTextAndKeepsInsertionOrder() = runTest {
        val id = checklistWithItems("  Run ", "Read", "Stretch")
        val added = items(currentDay(id).id)
        assertEquals(listOf("Run", "Read", "Stretch"), added.map { it.text })
        assertEquals(listOf(0, 1, 2), added.map { it.position })
        assertTrue(added.none { it.isChecked })
    }

    @Test
    fun addItemAfterDeleteStillAppendsAtEnd() = runTest {
        val id = checklistWithItems("A", "B", "C")
        val dayId = currentDay(id).id
        repository.deleteItem(items(dayId).first().id)
        repository.addItem(dayId, "D")
        assertEquals(listOf("B", "C", "D"), items(dayId).map { it.text })
    }

    @Test
    fun setCheckedTicksAndUnticks() = runTest {
        val id = checklistWithItems("Run")
        val dayId = currentDay(id).id
        val itemId = items(dayId).single().id

        repository.setChecked(itemId, true)
        assertTrue(items(dayId).single().isChecked)

        repository.setChecked(itemId, false)
        assertTrue(!items(dayId).single().isChecked)
    }

    @Test
    fun deleteItemRemovesOnlyThatItem() = runTest {
        val id = checklistWithItems("Run", "Read", "Stretch")
        val dayId = currentDay(id).id
        repository.deleteItem(items(dayId)[1].id)
        assertEquals(listOf("Run", "Stretch"), items(dayId).map { it.text })
    }

    // --- Daily copy ---

    @Test
    fun ensureTodayCopiesDailyItemsUncheckedInSameOrder() = runTest {
        val id = checklistWithItems("Run", "Read", "Stretch")
        items(currentDay(id).id).forEach { repository.setChecked(it.id, true) }

        nextDay()
        repository.ensureToday()

        val newDay = currentDay(id)
        assertEquals(today, newDay.date)
        val copied = items(newDay.id)
        assertEquals(listOf("Run", "Read", "Stretch"), copied.map { it.text })
        assertTrue(copied.none { it.isChecked })
    }

    @Test
    fun ensureTodayLeavesPreviousDayUntouched() = runTest {
        val id = checklistWithItems("Run", "Read")
        val yesterdayId = currentDay(id).id
        repository.setChecked(items(yesterdayId).first().id, true)

        nextDay()
        repository.ensureToday()
        // Changes to today's copy must not leak into yesterday.
        val todayItems = items(currentDay(id).id)
        repository.setChecked(todayItems[1].id, true)
        repository.deleteItem(todayItems[0].id)

        val yesterdayItems = items(yesterdayId)
        assertEquals(listOf("Run", "Read"), yesterdayItems.map { it.text })
        assertEquals(listOf(true, false), yesterdayItems.map { it.isChecked })
    }

    @Test
    fun ensureTodayIsIdempotent() = runTest {
        val id = checklistWithItems("Run", "Read")
        nextDay()
        repository.ensureToday()
        repository.ensureToday()
        repository.ensureToday()

        assertEquals(2, history(id).size)
        assertEquals(2, items(currentDay(id).id).size)
    }

    @Test
    fun ensureTodayOnCreationDayDoesNothing() = runTest {
        val id = checklistWithItems("Run")
        val dayId = currentDay(id).id
        repository.ensureToday()
        assertEquals(1, history(id).size)
        assertEquals(dayId, currentDay(id).id)
    }

    @Test
    fun ensureTodayDoesNotTickOrCopyIntoExistingToday() = runTest {
        val id = checklistWithItems("Run")
        val dayId = currentDay(id).id
        repository.setChecked(items(dayId).single().id, true)
        repository.ensureToday()
        assertTrue(items(dayId).single().isChecked)
    }

    @Test
    fun ensureTodaySkipsOneTimeChecklists() = runTest {
        val id = checklistWithItems("Pack", type = ChecklistType.ONE_TIME)
        val dayId = currentDay(id).id
        repository.setChecked(items(dayId).single().id, true)

        repeat(3) { nextDay() }
        repository.ensureToday()

        assertEquals(1, history(id).size)
        assertEquals(dayId, currentDay(id).id)
        assertTrue(items(dayId).single().isChecked)
    }

    @Test
    fun ensureTodayAfterGapCopiesFromLatestDayWithoutBackfilling() = runTest {
        val id = checklistWithItems("Run")
        nextDay()
        repository.ensureToday()
        repository.addItem(currentDay(id).id, "Added on day 2")

        repeat(4) { nextDay() } // app not opened for a few days
        repository.ensureToday()

        assertEquals(
            listOf(today, today.minusDays(4), today.minusDays(5)),
            history(id).map { it.date },
        )
        assertEquals(listOf("Run", "Added on day 2"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun ensureTodayCopiesEmptyDailyChecklist() = runTest {
        val id = repository.createChecklist("Empty", ChecklistType.DAILY)
        nextDay()
        repository.ensureToday()
        assertEquals(today, currentDay(id).date)
        assertTrue(items(currentDay(id).id).isEmpty())
    }

    @Test
    fun ensureTodayHandlesSeveralChecklistsIndependently() = runTest {
        val morning = checklistWithItems("Run")
        val evening = checklistWithItems("Read", "Journal")
        val trip = checklistWithItems("Pack", type = ChecklistType.ONE_TIME)

        nextDay()
        repository.ensureToday()

        assertEquals(listOf("Run"), items(currentDay(morning).id).map { it.text })
        assertEquals(listOf("Read", "Journal"), items(currentDay(evening).id).map { it.text })
        assertEquals(1, history(trip).size)
    }

    @Test
    fun ensureTodayWithNoChecklistsDoesNothing() = runTest {
        repository.ensureToday()
        assertTrue(repository.observeChecklists().first().isEmpty())
    }

    // --- Progress and history ---

    @Test
    fun mainListProgressComesFromLatestDay() = runTest {
        val id = checklistWithItems("Run", "Read")
        items(currentDay(id).id).forEach { repository.setChecked(it.id, true) }
        val before = repository.observeChecklists().first().single()
        assertEquals(2 to 2, before.done to before.total)

        nextDay()
        repository.ensureToday()

        val after = repository.observeChecklists().first().single()
        assertEquals(0 to 2, after.done to after.total)
    }

    @Test
    fun mainListShowsEveryChecklistInCreationOrderWithType() = runTest {
        checklistWithItems("Run")
        repository.createChecklist("Trip", ChecklistType.ONE_TIME)
        val list = repository.observeChecklists().first()
        assertEquals(listOf("Routine", "Trip"), list.map { it.title })
        assertEquals(listOf(ChecklistType.DAILY, ChecklistType.ONE_TIME), list.map { it.type })
        assertEquals(0 to 0, list[1].done to list[1].total)
    }

    @Test
    fun historyIsNewestFirstWithEachDaysCompletion() = runTest {
        val id = checklistWithItems("A", "B", "C", "D")
        // Day 1: 1 of 4 done
        repository.setChecked(items(currentDay(id).id)[0].id, true)
        // Day 2: 4 of 4 done
        nextDay()
        repository.ensureToday()
        items(currentDay(id).id).forEach { repository.setChecked(it.id, true) }
        // Day 3: nothing done yet
        nextDay()
        repository.ensureToday()

        val days = history(id)
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), days.map { it.date })
        assertEquals(listOf(0, 4, 1), days.map { it.done })
        assertEquals(listOf(4, 4, 4), days.map { it.total })
    }

    @Test
    fun historyReflectsItemsAddedOrRemovedOnThatDay() = runTest {
        val id = checklistWithItems("A", "B")
        nextDay()
        repository.ensureToday()
        val dayId = currentDay(id).id
        repository.addItem(dayId, "C")
        repository.deleteItem(items(dayId).first().id)
        repository.setChecked(items(dayId).last().id, true)

        val (todaySummary, yesterdaySummary) = history(id)
        assertEquals(1 to 2, todaySummary.done to todaySummary.total)
        assertEquals(0 to 2, yesterdaySummary.done to yesterdaySummary.total)
    }

    @Test
    fun observeDayReturnsThatDay() = runTest {
        val id = checklistWithItems("Run")
        val day = currentDay(id)
        val observed = repository.observeDay(day.id).first()
        assertNotNull(observed)
        assertEquals(day, observed)
    }
}
