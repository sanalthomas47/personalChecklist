package com.santhomach.personalchecklist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.santhomach.personalchecklist.ChecklistApp
import com.santhomach.personalchecklist.util.LogicalDate
import java.util.concurrent.TimeUnit

/** Makes the daily copies at 2am, then queues itself for the next 2am. */
class DailyCopyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as ChecklistApp).repository.ensureToday()
        schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        internal const val WORK_NAME = "daily-checklist-copy"

        /** Queues a run for the upcoming 2am. KEEP leaves an already pending run alone. */
        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val request = OneTimeWorkRequestBuilder<DailyCopyWorker>()
                .setInitialDelay(LogicalDate.millisUntilNextRollover(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
