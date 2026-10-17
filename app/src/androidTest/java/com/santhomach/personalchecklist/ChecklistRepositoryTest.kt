package com.santhomach.personalchecklist

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ChecklistRepository(db) { today }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun dailyChecklistIsCopiedUncheckedAndHistoryKeepsCompletion() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        val day1 = repository.observeCurrentDay(id).first()!!
        repository.addItem(day1.id, "Run")
        repository.addItem(day1.id, "Read")
        repository.addItem(day1.id, "Stretch")
        repository.observeItems(day1.id).first().take(2).forEach { repository.setChecked(it.id, true) }

        today = today.plusDays(1)
        repository.ensureToday()
        repository.ensureToday() // second call must be a no-op

        val day2 = repository.observeCurrentDay(id).first()!!
        assertEquals(today, day2.date)
        val day2Items = repository.observeItems(day2.id).first()
        assertEquals(listOf("Run", "Read", "Stretch"), day2Items.map { it.text })
        assertEquals(0, day2Items.count { it.isChecked })

        val history = repository.observeHistory(id).first()
        assertEquals(listOf(today, today.minusDays(1)), history.map { it.date })
        assertEquals(0 to 3, history[0].done to history[0].total)
        assertEquals(2 to 3, history[1].done to history[1].total)
    }

    @Test
    fun oneTimeChecklistIsNotCopied() = runTest {
        val id = repository.createChecklist("Move house", ChecklistType.ONE_TIME)
        val day = repository.observeCurrentDay(id).first()!!
        repository.addItem(day.id, "Pack")
        repository.setChecked(repository.observeItems(day.id).first().single().id, true)

        today = today.plusDays(3)
        repository.ensureToday()

        assertEquals(1, repository.observeHistory(id).first().size)
        val progress = repository.observeChecklists().first().single()
        assertEquals(1 to 1, progress.done to progress.total)
    }

    @Test
    fun deletingChecklistRemovesItsHistory() = runTest {
        val id = repository.createChecklist("Temp", ChecklistType.DAILY)
        today = today.plusDays(1)
        repository.ensureToday()
        repository.deleteChecklist(id)

        assertEquals(0, repository.observeHistory(id).first().size)
        assertEquals(0, repository.observeChecklists().first().size)
    }
}
