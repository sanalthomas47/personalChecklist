package com.santhomach.personalchecklist.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.ui.checklist.ChecklistViewModel
import com.santhomach.personalchecklist.ui.day.DayDetailViewModel
import com.santhomach.personalchecklist.ui.history.HistoryViewModel
import com.santhomach.personalchecklist.ui.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ViewModelsTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private var today = LocalDate.of(2026, 10, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChecklistRepository(db) { today }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // --- MainViewModel ---

    @Test
    fun mainCreateAddsChecklistWithChosenType() = runTest {
        val vm = MainViewModel(repository)
        vm.create("Morning", ChecklistType.DAILY).join()
        vm.create("Trip", ChecklistType.ONE_TIME).join()

        val list = vm.checklists.first { it?.size == 2 }!!
        assertEquals(listOf("Morning", "Trip"), list.map { it.title })
        assertEquals(listOf(ChecklistType.DAILY, ChecklistType.ONE_TIME), list.map { it.type })
    }

    @Test
    fun mainRenameUpdatesCard() = runTest {
        val vm = MainViewModel(repository)
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        vm.rename(id, "Evening").join()
        assertEquals("Evening", vm.checklists.first { it?.singleOrNull()?.title == "Evening" }!!.single().title)
    }

    @Test
    fun mainDeleteRemovesCard() = runTest {
        val vm = MainViewModel(repository)
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        vm.delete(id).join()
        assertTrue(vm.checklists.first { it != null }!!.isEmpty())
    }

    // --- ChecklistViewModel ---

    @Test
    fun checklistAddToggleAndDeleteItems() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        val vm = ChecklistViewModel(repository, id)
        backgroundScope.launch { vm.day.collect {} }
        vm.day.first { it != null }

        vm.addItem("Run").join()
        vm.addItem("Read").join()
        val added = vm.items.first { it.size == 2 }
        assertEquals(listOf("Run", "Read"), added.map { it.text })

        vm.setChecked(added[0].id, true).join()
        assertTrue(vm.items.first { it.firstOrNull()?.isChecked == true }[0].isChecked)

        vm.deleteItem(added[1].id).join()
        assertEquals(listOf("Run"), vm.items.first { it.size == 1 }.map { it.text })
    }

    @Test
    fun checklistFollowsNewDayAfterRollover() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        val vm = ChecklistViewModel(repository, id)
        backgroundScope.launch { vm.day.collect {} }
        vm.day.first { it != null }
        vm.addItem("Run").join()
        vm.setChecked(vm.items.first { it.isNotEmpty() }.single().id, true).join()

        today = today.plusDays(1)
        repository.ensureToday()

        assertEquals(today, vm.day.first { it?.date == today }!!.date)
        val items = vm.items.first { it.singleOrNull()?.isChecked == false }
        assertEquals(listOf("Run"), items.map { it.text })
    }

    @Test
    fun checklistRenameAndDelete() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.ONE_TIME)
        val vm = ChecklistViewModel(repository, id)
        backgroundScope.launch { vm.checklist.collect {} }

        vm.rename("Trip").join()
        val renamed = vm.checklist.first { it?.title == "Trip" }!!
        assertEquals(ChecklistType.ONE_TIME, renamed.type)

        vm.delete().join()
        assertNull(vm.checklist.first { it == null })
    }

    // --- HistoryViewModel / DayDetailViewModel ---

    @Test
    fun historyListsDaysNewestFirstAndDayDetailShowsThatDaysItems() = runTest {
        val id = repository.createChecklist("Morning", ChecklistType.DAILY)
        val firstDayId = repository.observeCurrentDay(id).first()!!.id
        repository.addItem(firstDayId, "Run")
        repository.setChecked(repository.observeItems(firstDayId).first().single().id, true)
        today = today.plusDays(1)
        repository.ensureToday()

        val history = HistoryViewModel(repository, id)
        val days = history.days.first { it.size == 2 }
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(listOf(0, 1), days.map { it.done })

        val detail = DayDetailViewModel(repository, firstDayId)
        assertEquals(today.minusDays(1), detail.day.first { it != null }!!.date)
        val items = detail.items.first { it.isNotEmpty() }
        assertEquals(listOf("Run"), items.map { it.text })
        assertTrue(items.single().isChecked)
    }
}
