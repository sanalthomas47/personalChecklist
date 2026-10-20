package com.santhomach.personalchecklist.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.backup.Backup
import com.santhomach.personalchecklist.data.backup.BackupChecklist
import com.santhomach.personalchecklist.data.backup.BackupDay
import com.santhomach.personalchecklist.data.backup.BackupItem
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.backup.FakeBackupStorage
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainScreenBackupUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private lateinit var storage: FakeBackupStorage

    private val backupWithTrip = Backup(
        exportedAt = "2026-10-18T09:30:00",
        checklists = listOf(
            BackupChecklist(
                "Trip from backup",
                ChecklistType.ONE_TIME,
                1L,
                listOf(BackupDay("2026-10-18", listOf(BackupItem("Pack", true))))
            ),
        ),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ChecklistRepository(db)
        storage = FakeBackupStorage()
        val vm = MainViewModel(repository, storage)
        compose.setContent { MainScreen(onOpenChecklist = {}, viewModel = vm) }
    }

    @After
    fun tearDown() = db.close()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(
            hasText(text, substring = substring),
            timeoutMillis = 5_000
        )

    private fun openMenuItem(label: String) {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun createChecklist(title: String) {
        runBlocking { repository.createChecklist(title, ChecklistType.DAILY) }
        waitForText(title)
    }

    /** Simulates the user picking [uri] in the system file picker that the app just opened. */
    private fun pickFile(uri: Uri) {
        val shadow = shadowOf(compose.activity)
        val request = shadow.nextStartedActivityForResult
        shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
    }

    private fun titles() = runBlocking { repository.observeChecklists().first().map { it.title } }

    @Test
    fun menuOffersExportAndImport() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Export to JSON").assertIsDisplayed()
        compose.onNodeWithText("Import from JSON").assertIsDisplayed()
    }

    @Test
    fun menuIsAvailableEvenWithNoChecklists() {
        waitForText("No checklists yet", substring = true)
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun exportSavesBackupAndShowsFileName() {
        createChecklist("Morning")
        openMenuItem("Export to JSON")

        waitForText("Backup saved to Downloads/checklist-backup-", substring = true)
        val json = storage.files.values.single()
        assertEquals(listOf("Morning"), BackupJson.decode(json).checklists.map { it.title })
    }

    @Test
    fun importOpensFilePickerForJsonFiles() {
        openMenuItem("Import from JSON")

        val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/json")
        )
    }

    @Test
    fun pickingValidBackupShowsConfirmationWithSummary() {
        createChecklist("Current")
        storage.files["backup.json"] = BackupJson.encode(backupWithTrip)

        openMenuItem("Import from JSON")
        pickFile(storage.uriFor("backup.json"))

        waitForText("Replace all checklists?")
        compose.onNodeWithText("18 Oct 2026, 09:30", substring = true).assertIsDisplayed()
        compose.onNodeWithText("has 1 checklist", substring = true).assertIsDisplayed()
        assertEquals("nothing changes before confirming", listOf("Current"), titles())
    }

    @Test
    fun confirmingReplacesChecklistsAndShowsMessage() {
        createChecklist("Current")
        storage.files["backup.json"] = BackupJson.encode(backupWithTrip)

        openMenuItem("Import from JSON")
        pickFile(storage.uriFor("backup.json"))
        waitForText("Replace all checklists?")
        compose.onNodeWithText("Replace").performClick()

        waitForText("Trip from backup")
        waitForText("Restored 1 checklist from backup")
        compose.onNodeWithText("Current").assertDoesNotExist()
        assertEquals(listOf("Trip from backup"), titles())
    }

    @Test
    fun cancellingKeepsCurrentChecklists() {
        createChecklist("Current")
        storage.files["backup.json"] = BackupJson.encode(backupWithTrip)

        openMenuItem("Import from JSON")
        pickFile(storage.uriFor("backup.json"))
        waitForText("Replace all checklists?")
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Replace all checklists?").assertDoesNotExist()
        compose.onNodeWithText("Current").assertIsDisplayed()
        assertEquals(listOf("Current"), titles())
    }

    @Test
    fun pickingInvalidFileShowsErrorAndNoDialog() {
        createChecklist("Current")
        storage.files["notes.json"] = "just some notes"

        openMenuItem("Import from JSON")
        pickFile(storage.uriFor("notes.json"))

        waitForText("Couldn't import: This isn't a checklist backup file.")
        compose.onNodeWithText("Replace all checklists?").assertDoesNotExist()
        assertEquals(listOf("Current"), titles())
    }

    @Test
    fun cancellingFilePickerDoesNothing() {
        createChecklist("Current")
        openMenuItem("Import from JSON")

        val shadow = shadowOf(compose.activity)
        shadow.receiveResult(
            shadow.nextStartedActivityForResult.intent,
            Activity.RESULT_CANCELED,
            null
        )
        compose.waitForIdle()

        compose.onNodeWithText("Replace all checklists?").assertDoesNotExist()
        assertEquals(listOf("Current"), titles())
    }
}
