package com.santhomach.personalchecklist

import android.app.Application
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.work.DailyCopyWorker

class ChecklistApp : Application() {
    val repository: ChecklistRepository by lazy { ChecklistRepository(AppDatabase.build(this)) }

    override fun onCreate() {
        super.onCreate()
        DailyCopyWorker.schedule(this)
    }
}
