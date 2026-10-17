package com.santhomach.personalchecklist.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun fromType(type: ChecklistType): String = type.name

    @TypeConverter
    fun toType(name: String): ChecklistType = ChecklistType.valueOf(name)
}
