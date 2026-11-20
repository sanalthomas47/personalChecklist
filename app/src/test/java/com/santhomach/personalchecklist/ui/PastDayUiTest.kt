package com.santhomach.personalchecklist.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.ui.day.DayDetailScreen
import com.santhomach.personalchecklist.ui.day.DayDetailViewModel
import com.santhomach.personalchecklist.ui.history.HistoryScreen
import com.santhomach.personalchecklist.ui.history.HistoryViewModel
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PastDayUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val today = LogicalDate.today()
    private var checklistId = 0L
    private var yesterdayId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ChecklistRepository(db)
        runBlocking {
            checklistId = db.checklistDao().insert(
                ChecklistEntity(title = "Morning", type = ChecklistType.DAILY, createdAt = System.currentTimeMillis() - 5 * 86_400_000L)
            )
            yesterdayId = db.dayDao().insert(ChecklistDayEntity(checklistId = checklistId, date = today.minusDays(1)))
            db.itemDao().insertAll(listOf(ItemEntity(dayId = yesterdayId, text = "Run", position = 0)))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun isChecked(dayId: Long) = runBlocking { repository.observeItems(dayId).first().single().isChecked }

    private fun toggles() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size

    private fun showDay(edit: Boolean = false) = compose.setContent {
        DayDetailScreen(onBack = {}, viewModel = DayDetailViewModel(repository, yesterdayId, startEditing = edit))
    }

    @Test
    fun pastDayOpensReadOnly() {
        showDay()
        compose.waitUntilAtLeastOneExists(hasText("Run"), timeoutMillis = 5_000)
        assertEquals(0, toggles())
        compose.onNodeWithContentDescription("Edit").assertIsDisplayed()
    }

    @Test
    fun editModeLetsYouTickAndDoneLocksAgain() {
        showDay()
        compose.waitUntilAtLeastOneExists(hasText("Run"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onNodeWithText("Editing a past day: tick what you did.").assertIsDisplayed()

        compose.onNode(isToggleable()).assertIsEnabled().performClick()
        compose.waitUntil(5_000) { isChecked(yesterdayId) }

        compose.onNodeWithContentDescription("Done editing").performClick()
        compose.waitForIdle()
        assertEquals(0, toggles())
        assertEquals(0, compose.onAllNodes(hasText("Editing a past day", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun canOpenStraightIntoEditMode() {
        showDay(edit = true)
        compose.waitUntilAtLeastOneExists(hasText("Editing a past day: tick what you did."), timeoutMillis = 5_000)
        compose.onNode(isToggleable()).assertIsEnabled()
    }

    @Test
    fun historyOffersUpdateAPastDateAndOpensThePicker() {
        compose.setContent { HistoryScreen(onBack = {}, onOpenDay = {}, viewModel = HistoryViewModel(repository, checklistId)) }
        compose.waitUntilAtLeastOneExists(hasText("Update a past date"), timeoutMillis = 5_000)
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Update a past date").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText("Update a past date").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Open"), timeoutMillis = 5_000)
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun updateAPastDateIsDisabledForABrandNewChecklist() {
        val fresh = runBlocking { repository.createChecklist("Fresh", ChecklistType.DAILY) }
        compose.setContent { HistoryScreen(onBack = {}, onOpenDay = {}, viewModel = HistoryViewModel(repository, fresh)) }
        compose.waitUntilAtLeastOneExists(hasText("Update a past date"), timeoutMillis = 5_000)
        compose.onNodeWithText("Update a past date").assertIsNotEnabled()
    }

    @Test
    fun pickingASkippedDateCreatesItAndOpensItForEditing() {
        val vm = HistoryViewModel(repository, checklistId)
        var opened: Long? = null
        vm.openPastDate(today.minusDays(3)) { opened = it }
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle() // no UI on screen, so run the main thread by hand
            opened != null
        }

        val history = runBlocking { repository.observeHistory(checklistId).first() }
        assertEquals(listOf(today.minusDays(1), today.minusDays(3)), history.map { it.date })
        assertEquals(opened, history.last().dayId)
        assertEquals(listOf("Run"), runBlocking { repository.observeItems(opened!!).first().map { it.text } })
    }
}
