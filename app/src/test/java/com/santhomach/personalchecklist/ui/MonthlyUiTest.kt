package com.santhomach.personalchecklist.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
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
import com.santhomach.personalchecklist.ui.common.displayMonth
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
class MonthlyUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private val thisMonth = ChecklistType.MONTHLY.periodStart(LogicalDate.today())

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
    fun createDialogOffersAllFourTypes() {
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
        listOf("Weekly", "Monthly", "One-time").forEach { compose.onNodeWithText(it).assertIsNotSelected() }

        compose.onNodeWithText("Monthly").performClick()
        compose.onNodeWithText("Monthly").assertIsSelected()
        compose.onNodeWithText("Daily").assertIsNotSelected()
        compose.onNodeWithText("A fresh unticked copy is made on the 1st of every month at 2am.").assertIsDisplayed()

        compose.onNodeWithText("Name").performTextInput("Bills")
        compose.onNodeWithText("Create").performClick()
        assertEquals("Bills" to ChecklistType.MONTHLY, created)
    }

    @Test
    fun checklistPageShowsTheMonth() {
        val id = runBlocking { repository.createChecklist("Bills", ChecklistType.MONTHLY) }
        compose.setContent { ChecklistScreen(onBack = {}, onOpenHistory = {}, viewModel = ChecklistViewModel(repository, id)) }

        waitForText(thisMonth.displayMonth())
        compose.onNodeWithText("Monthly checklist · resets on the 1st of every month").assertIsDisplayed()
        compose.onNodeWithContentDescription("History").assertIsDisplayed()
    }

    @Test
    fun historyCountsMonths() {
        val id = runBlocking {
            val id = db.checklistDao().insert(ChecklistEntity(title = "Bills", type = ChecklistType.MONTHLY))
            for ((monthsAgo, paid) in listOf(2L to false, 1L to true, 0L to false)) {
                val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = id, date = thisMonth.minusMonths(monthsAgo)))
                db.itemDao().insert(ItemEntity(dayId = dayId, text = "Rent", isChecked = paid, position = 0))
            }
            id
        }
        compose.setContent { HistoryScreen(onBack = {}, onOpenDay = {}, viewModel = HistoryViewModel(repository, id)) }

        compose.waitUntilAtLeastOneExists(hasContentDescription("Rent, missed 1 of 2 months, 50 percent"), timeoutMillis = 5_000)
        compose.onNodeWithText("12 months").assertIsSelected()
        compose.onNodeWithText("3 months").assertIsDisplayed()
        compose.onNodeWithText("Based on 2 months before this month").assertIsDisplayed()
        waitForText("Monthly history")
        waitForText(thisMonth.displayMonth() + " (this month)")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(thisMonth.minusMonths(1).displayMonth()))
        compose.onNodeWithText(thisMonth.minusMonths(1).displayMonth()).assertIsDisplayed()
    }
}
