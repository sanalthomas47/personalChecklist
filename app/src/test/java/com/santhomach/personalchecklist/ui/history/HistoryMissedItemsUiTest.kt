package com.santhomach.personalchecklist.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.MissedPeriod
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.ui.common.display
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
class HistoryMissedItemsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private var checklistId = 0L
    private val today = LogicalDate.today()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ChecklistRepository(db)
        checklistId = runBlocking { db.checklistDao().insert(ChecklistEntity(title = "Morning", type = ChecklistType.DAILY)) }
    }

    @After
    fun tearDown() = db.close()

    private fun day(daysAgo: Long, vararg items: Pair<String, Boolean>) = runBlocking {
        val dayId = db.dayDao().insert(ChecklistDayEntity(checklistId = checklistId, date = today.minusDays(daysAgo)))
        db.itemDao().insertAll(items.mapIndexed { i, (text, checked) -> ItemEntity(dayId = dayId, text = text, isChecked = checked, position = i) })
    }

    private fun show(): HistoryViewModel {
        val vm = HistoryViewModel(repository, checklistId)
        compose.setContent { HistoryScreen(onBack = {}, onOpenDay = {}, viewModel = vm) }
        return vm
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 5_000)

    private fun waitForBar(description: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(description), timeoutMillis = 5_000)

    @Test
    fun showsRankedBarsForMissedItems() {
        day(3, "Run" to true, "Read" to false, "Stretch" to false)
        day(2, "Run" to true, "Read" to true, "Stretch" to false)
        day(1, "Run" to false, "Read" to false, "Stretch" to false)
        show()

        waitForText("Most missed items")
        waitForBar("Stretch, missed 3 of 3 days, 100 percent")
        compose.onNodeWithContentDescription("Read, missed 2 of 3 days, 66 percent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Run, missed 1 of 3 days, 33 percent").assertIsDisplayed()
        compose.onNodeWithText("Based on 3 days before today").assertIsDisplayed()

        // Rows appear most-missed first.
        val order = compose.onAllNodesWithContentDescription("missed", substring = true)
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.ContentDescription].single().substringBefore(",") }
        assertEquals(listOf("Stretch", "Read", "Run"), order)
    }

    @Test
    fun listsItemsThatWereNeverMissed() {
        day(2, "Read" to false, "Water plants" to true)
        day(1, "Read" to true, "Water plants" to true)
        show()

        waitForText("Never missed: Water plants")
        compose.onNodeWithContentDescription("Read, missed 1 of 2 days, 50 percent").assertIsDisplayed()
    }

    @Test
    fun allTickedShowsNothingMissedMessage() {
        day(1, "Run" to true)
        show()
        waitForText("Nothing missed in this period.")
    }

    @Test
    fun brandNewChecklistExplainsWhenDataAppears() {
        day(0, "Run" to false) // only today exists
        show()
        waitForText("Missed items show up here once a full day has passed.")
    }

    @Test
    fun defaultsToThirtyDaysAndSwitchingPeriodUpdatesBars() {
        day(60, "Run" to false)
        day(20, "Run" to false)
        day(3, "Run" to true)
        val vm = show()

        waitForBar("Run, missed 1 of 2 days, 50 percent")
        compose.onNodeWithText("30 days").assertIsSelected()
        assertEquals(MissedPeriod.MONTH, vm.missedPeriod.value)

        compose.onNodeWithText("All time").performClick()
        waitForBar("Run, missed 2 of 3 days, 66 percent")
        compose.onNodeWithText("All time").assertIsSelected()

        compose.onNodeWithText("7 days").performClick()
        waitForText("Nothing missed in this period.")
        waitForText("Never missed: Run")
    }

    @Test
    fun dailyHistoryListStillShownBelow() {
        day(1, "Run" to true)
        show()
        waitForText("Daily history")
        waitForText(today.minusDays(1).display())
    }
}
