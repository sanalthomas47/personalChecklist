package com.santhomach.personalchecklist.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.ui.checklist.ChecklistScreen
import com.santhomach.personalchecklist.ui.checklist.ChecklistViewModel
import com.santhomach.personalchecklist.ui.common.NameDialog
import com.santhomach.personalchecklist.ui.common.displayWeek
import com.santhomach.personalchecklist.ui.history.HistoryScreen
import com.santhomach.personalchecklist.ui.history.HistoryViewModel
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WeeklyUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val thisMonday = ChecklistType.WEEKLY.periodStart(LogicalDate.today())

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ChecklistRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun waitForText(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    @Test
    fun createDialogOffersWeekly() {
        var created: Pair<String, ChecklistType>? = null
        compose.setContent {
            NameDialog(
                title = "New checklist",
                confirmLabel = "Create",
                showTypeSelector = true,
                onConfirm = { name, type -> created = name to type },
                onDismiss = {},
            )
        }
        compose.onNodeWithText("Daily").assertIsSelected()
        compose.onNodeWithText("One-time").assertIsDisplayed()

        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithText("A fresh unticked copy is made every Monday at 2am.").assertIsDisplayed()

        compose.onNodeWithText("Name").performTextInput("Chores")
        compose.onNodeWithText("Create").performClick()
        assertEquals("Chores" to ChecklistType.WEEKLY, created)
    }

    @Test
    fun checklistPageShowsTheWeekAndHistory() {
        val id = runBlocking { repository.createChecklist("Chores", ChecklistType.WEEKLY) }
        compose.setContent { ChecklistScreen(onBack = {}, onOpenHistory = {}, viewModel = ChecklistViewModel(repository, id)) }

        waitForText(thisMonday.displayWeek())
        compose.onNodeWithText("Weekly checklist · resets every Monday").assertIsDisplayed()
        compose.onNodeWithContentDescription("History").assertIsDisplayed()
    }

    @Test
    fun historyCountsWeeks() {
        val id = runBlocking {
            val id = db.checklistDao().insert(ChecklistEntity(title = "Chores", type = ChecklistType.WEEKLY))
            for ((weeksAgo, ranDone) in listOf(2L to false, 1L to true, 0L to false)) {
                val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = id, date = thisMonday.minusWeeks(weeksAgo)))
                db.itemDao().insert(ItemEntity(dayId = dayId, text = "Run", isChecked = ranDone, position = 0))
            }
            id
        }
        compose.setContent { HistoryScreen(onBack = {}, onOpenDay = {}, viewModel = HistoryViewModel(repository, id)) }

        compose.waitUntilAtLeastOneExists(hasContentDescription("Run, missed 1 of 2 weeks, 50 percent"), timeoutMillis = 5_000)
        compose.onNodeWithText("12 weeks").assertIsSelected()
        compose.onNodeWithText("4 weeks").assertIsDisplayed()
        compose.onNodeWithText("Based on 2 weeks before this week").assertIsDisplayed()
        waitForText("Weekly history")
        waitForText(thisMonday.displayWeek() + " (this week)")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(thisMonday.minusWeeks(1).displayWeek()))
        compose.onNodeWithText(thisMonday.minusWeeks(1).displayWeek()).assertIsDisplayed()
    }
}
