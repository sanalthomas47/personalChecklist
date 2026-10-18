package com.santhomach.personalchecklist.work

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.santhomach.personalchecklist.ChecklistApp
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DailyCopyWorkerTest {
    private lateinit var app: ChecklistApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() = app.database.close()

    private fun pendingRuns(): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(DailyCopyWorker.WORK_NAME).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun doWorkMakesTodaysCopyOfDailyChecklistsOnly() = runTest {
        val db = app.database
        val yesterday = LogicalDate.today().minusDays(1)
        val dailyId = db.checklistDao().insert(ChecklistEntity(title = "Morning", type = ChecklistType.DAILY))
        val oneTimeId = db.checklistDao().insert(ChecklistEntity(title = "Trip", type = ChecklistType.ONE_TIME))
        val dailyDay = db.dayDao().insert(ChecklistDayEntity(checklistId = dailyId, date = yesterday))
        db.dayDao().insert(ChecklistDayEntity(checklistId = oneTimeId, date = yesterday))
        db.itemDao().insertAll(
            listOf(
                ItemEntity(dayId = dailyDay, text = "Run", isChecked = true, position = 0),
                ItemEntity(dayId = dailyDay, text = "Read", isChecked = false, position = 1),
            )
        )

        val result = TestListenableWorkerBuilder<DailyCopyWorker>(app).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val todayCopy = db.dayDao().get(dailyId, LogicalDate.today())
        assertNotNull(todayCopy)
        val copied = db.itemDao().getByDay(todayCopy!!.id)
        assertEquals(listOf("Run", "Read"), copied.map { it.text })
        assertTrue(copied.none { it.isChecked })
        assertNull(db.dayDao().get(oneTimeId, LogicalDate.today()))
    }

    @Test
    fun doWorkQueuesTheNextRunForNext2am() = runTest {
        TestListenableWorkerBuilder<DailyCopyWorker>(app).build().doWork()

        val pending = pendingRuns().single()
        assertTrue(pending.initialDelayMillis > 0)
        assertTrue(pending.initialDelayMillis <= TimeUnit.HOURS.toMillis(24))
    }

    @Test
    fun scheduleKeepsAlreadyPendingRun() {
        DailyCopyWorker.schedule(app)
        val first = pendingRuns().single().id
        DailyCopyWorker.schedule(app)
        assertEquals(first, pendingRuns().single().id)
    }
}
