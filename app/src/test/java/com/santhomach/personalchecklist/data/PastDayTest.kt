package com.santhomach.personalchecklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PastDayTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val start = LocalDate.of(2026, 11, 10) // a Tuesday
    private var today = start

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

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    /** Advances to [date], making the copies the app would make if opened that day. */
    private suspend fun openAppOn(date: LocalDate) {
        today = date
        repository.ensureToday()
    }

    /** Daily checklist with Run and Read, created on [start]. */
    private suspend fun morning(): Long {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        repository.addItem(currentDay(id).id, "Run")
        repository.addItem(currentDay(id).id, "Read")
        return id
    }

    @Test
    fun existingPastDayIsReturnedUnchanged() = runTest {
        val id = morning()
        val firstDay = currentDay(id)
        repository.setChecked(items(firstDay.id)[0].id, true)
        openAppOn(start.plusDays(1))

        assertEquals(firstDay.id, repository.pastDay(id, start))
        assertEquals(listOf(true, false), items(firstDay.id).map { it.isChecked })
        assertEquals(2, history(id).size)
    }

    @Test
    fun tickingAPastDayChangesOnlyThatDay() = runTest {
        val id = morning()
        openAppOn(start.plusDays(1))
        val pastId = repository.pastDay(id, start)
        repository.setChecked(items(pastId)[1].id, true)

        assertEquals(listOf(false, true), items(pastId).map { it.isChecked })
        assertTrue(items(currentDay(id).id).none { it.isChecked })
        assertEquals(1, history(id).single { it.date == start }.done)
    }

    @Test
    fun skippedDayIsFilledInFromTheNearestEarlierDay() = runTest {
        val id = morning()
        repository.setChecked(items(currentDay(id).id)[0].id, true)
        openAppOn(start.plusDays(3)) // app not opened on the 11th and 12th
        repository.addItem(currentDay(id).id, "Stretch") // added later; not part of the 11th

        val filled = repository.pastDay(id, start.plusDays(1))
        assertEquals(listOf("Run", "Read"), items(filled).map { it.text })
        assertTrue(items(filled).none { it.isChecked })
        assertEquals(listOf(start.plusDays(3), start.plusDays(1), start), history(id).map { it.date })
        // Repeating the request opens the same copy rather than adding another.
        assertEquals(filled, repository.pastDay(id, start.plusDays(1)))
        assertEquals(3, history(id).size)
    }

    @Test
    fun dayBeforeTheOldestCopyTakesItemsFromTheNextOne() = runTest {
        val id = morning()
        openAppOn(start.plusDays(1))
        val filled = repository.pastDay(id, start.minusDays(2))
        assertEquals(listOf("Run", "Read"), items(filled).map { it.text })
    }

    @Test
    fun fillingInThePastDoesNotDisturbTodayOrTheNextCopy() = runTest {
        val id = morning()
        openAppOn(start.plusDays(3))
        val todayCopy = currentDay(id)
        repository.addItem(todayCopy.id, "Stretch")
        repository.pastDay(id, start.plusDays(1))

        assertEquals(todayCopy, currentDay(id)) // still the newest copy
        openAppOn(start.plusDays(4))
        assertEquals(listOf("Run", "Read", "Stretch"), items(currentDay(id).id).map { it.text })
    }

    @Test
    fun weeklyPastDateMapsToItsWeek() = runTest {
        val id = repository.createChecklist("Chores", ChecklistType.WEEKLY)
        repository.addItem(currentDay(id).id, "Laundry")
        openAppOn(start.plusWeeks(2))

        val filled = repository.pastDay(id, start.plusWeeks(1).plusDays(2)) // a Thursday
        assertEquals(LocalDate.of(2026, 11, 16), history(id).single { it.dayId == filled }.date)
        assertEquals(listOf("Laundry"), items(filled).map { it.text })
    }

    @Test
    fun monthlyPastDateMapsToItsMonth() = runTest {
        val id = repository.createChecklist("Bills", ChecklistType.MONTHLY)
        openAppOn(LocalDate.of(2027, 1, 5))
        val filled = repository.pastDay(id, LocalDate.of(2026, 12, 25))
        assertEquals(LocalDate.of(2026, 12, 1), history(id).single { it.dayId == filled }.date)
    }

    @Test
    fun currentOrFutureDatesAndOneTimeChecklistsAreRejected() = runTest {
        val id = morning()
        assertRejected { repository.pastDay(id, start) }
        assertRejected { repository.pastDay(id, start.plusDays(1)) }
        val trip = repository.createChecklist("Trip", ChecklistType.ONE_TIME)
        assertRejected { repository.pastDay(trip, start.minusDays(1)) }
        assertEquals(1, history(id).size)
    }

    @Test
    fun filledInDaysCountInMissedItemsAndSurviveBackup() = runTest {
        val id = morning()
        openAppOn(start.plusDays(2))
        val filled = repository.pastDay(id, start.plusDays(1))
        repository.setChecked(items(filled)[0].id, true) // ran, didn't read

        val report = repository.observeMissedItems(id, MissedPeriod.ALL).first()
        assertEquals(2, report.pastPeriods)
        assertEquals(listOf("Read", "Run"), report.missed.map { it.text })

        repository.importBackup(BackupJson.decode(BackupJson.encode(repository.exportBackup())))
        val restored = repository.observeChecklists().first().single().id
        assertEquals(listOf(start.plusDays(2), start.plusDays(1), start), history(restored).map { it.date })
        assertEquals(1, history(restored).single { it.date == start.plusDays(1) }.done)
    }

    // --- Pickable range ---

    private fun millisAt(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun rangeRunsFromCreationToTheLastFinishedPeriod() {
        val created = millisAt(start, 9)
        assertEquals(
            start..LocalDate.of(2026, 11, 19),
            pastDateRange(ChecklistType.DAILY, created, start, LocalDate.of(2026, 11, 20), ZoneOffset.UTC),
        )
        assertEquals(
            LocalDate.of(2026, 11, 9)..LocalDate.of(2026, 11, 15),
            pastDateRange(ChecklistType.WEEKLY, created, null, LocalDate.of(2026, 11, 20), ZoneOffset.UTC),
        )
        assertEquals(
            LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 12, 31),
            pastDateRange(ChecklistType.MONTHLY, created, null, LocalDate.of(2027, 1, 3), ZoneOffset.UTC),
        )
    }

    @Test
    fun rangeIsEmptyUntilAPeriodHasFinished() {
        assertNull(pastDateRange(ChecklistType.DAILY, millisAt(start, 9), start, start, ZoneOffset.UTC))
        assertNull(pastDateRange(ChecklistType.WEEKLY, millisAt(start, 9), null, start.plusDays(4), ZoneOffset.UTC))
    }

    @Test
    fun rangeUsesTheTwoAmDayBoundaryAndOlderImportedCopies() {
        // Created at 1am on the 10th: that still belongs to the 9th.
        assertEquals(start.minusDays(1), pastDateRange(ChecklistType.DAILY, millisAt(start, 1), null, start.plusDays(1), ZoneOffset.UTC)!!.start)
        // A backup brought in copies older than the checklist's creation time.
        val older = LocalDate.of(2026, 1, 1)
        assertEquals(older, pastDateRange(ChecklistType.DAILY, millisAt(start, 9), older, start.plusDays(1), ZoneOffset.UTC)!!.start)
    }
}
