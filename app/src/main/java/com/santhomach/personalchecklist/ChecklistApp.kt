package com.santhomach.personalchecklist

import android.app.Application
import androidx.work.Configuration
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.work.DailyCopyWorker

class ChecklistApp : Application(), Configuration.Provider {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ChecklistRepository by lazy { ChecklistRepository(database) }

    // Lets WorkManager initialise on demand even where the startup provider hasn't run (e.g. JVM tests).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        DailyCopyWorker.schedule(this)
    }
}
