package com.santhomach.personalchecklist.ui.main

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.backup.FakeBackupStorage
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelBackupTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChecklistRepository
    private lateinit var storage: FakeBackupStorage
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = ChecklistRepository(db) { LocalDate.of(2026, 10, 18) }
        storage = FakeBackupStorage()
        vm = MainViewModel(repository, storage)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun titles() = repository.observeChecklists().first().map { it.title }

    @Test
    fun exportWritesValidBackupToDownloadsAndReportsFileName() = runTest {
        repository.createChecklist("Morning", ChecklistType.DAILY)
        vm.exportBackup().join()

        val (name, json) = storage.files.entries.single()
        assertTrue(name.matches(Regex("""checklist-backup-\d{4}-\d{2}-\d{2}-\d{6}\.json""")))
        assertEquals(listOf("Morning"), BackupJson.decode(json).checklists.map { it.title })
        assertEquals("Backup saved to Downloads/$name", vm.messages.first())
    }

    @Test
    fun exportFailureIsReported() = runTest {
        storage.failWrites = true
        vm.exportBackup().join()
        assertEquals("Export failed: Disk full", vm.messages.first())
    }

    @Test
    fun choosingValidFileAsksForConfirmationWithoutChangingData() = runTest {
        repository.createChecklist("Current", ChecklistType.DAILY)
        storage.files["backup.json"] = BackupJson.encode(
            repository.exportBackup().copy(checklists = emptyList())
        )

        vm.onImportFileChosen(storage.uriFor("backup.json")).join()

        assertNotNull(vm.pendingImport.value)
        assertEquals(listOf("Current"), titles())
    }

    @Test
    fun confirmImportReplacesDataAndReports() = runTest {
        repository.createChecklist("From backup", ChecklistType.ONE_TIME)
        storage.files["backup.json"] = BackupJson.encode(repository.exportBackup())
        repository.observeChecklists().first().forEach { repository.deleteChecklist(it.id) }
        repository.createChecklist("Current", ChecklistType.DAILY)

        vm.onImportFileChosen(storage.uriFor("backup.json")).join()
        vm.confirmImport().join()

        assertEquals(listOf("From backup"), titles())
        assertNull(vm.pendingImport.value)
        assertEquals("Restored 1 checklist from backup", vm.messages.first())
    }

    @Test
    fun cancelImportLeavesDataAlone() = runTest {
        repository.createChecklist("Current", ChecklistType.DAILY)
        storage.files["backup.json"] =
            BackupJson.encode(repository.exportBackup().copy(checklists = emptyList()))

        vm.onImportFileChosen(storage.uriFor("backup.json")).join()
        vm.cancelImport()

        assertNull(vm.pendingImport.value)
        assertEquals(listOf("Current"), titles())
    }

    @Test
    fun invalidFileIsRejectedWithMessage() = runTest {
        repository.createChecklist("Current", ChecklistType.DAILY)
        storage.files["notes.json"] = """{"hello": "world"}"""

        vm.onImportFileChosen(storage.uriFor("notes.json")).join()

        assertNull(vm.pendingImport.value)
        assertEquals("Couldn't import: This isn't a checklist backup file.", vm.messages.first())
        assertEquals(listOf("Current"), titles())
    }

    @Test
    fun unreadableFileIsReported() = runTest {
        vm.onImportFileChosen(storage.uriFor("missing.json")).join()
        assertNull(vm.pendingImport.value)
        assertEquals("Couldn't read the file: File not found", vm.messages.first())
    }

    @Test
    fun checklistCountWording() {
        assertEquals("0 checklists", checklistCount(0))
        assertEquals("1 checklist", checklistCount(1))
        assertEquals("3 checklists", checklistCount(3))
    }
}
