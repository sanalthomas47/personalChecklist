package com.santhomach.personalchecklist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ChecklistEntity::class, ChecklistDayEntity::class, ItemEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checklistDao(): ChecklistDao
    abstract fun dayDao(): DayDao
    abstract fun itemDao(): ItemDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "checklist.db").build()
    }
}
