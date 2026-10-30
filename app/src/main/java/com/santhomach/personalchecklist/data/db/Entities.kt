package com.santhomach.personalchecklist.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ChecklistType {
    DAILY,
    WEEKLY,
    ONE_TIME;

    /** Whether the checklist gets a fresh unticked copy each period (day or week). */
    val repeats: Boolean get() = this != ONE_TIME

    /** Date the copy current on [today] is stored under: that week's Monday for weekly, else [today]. */
    fun periodStart(today: LocalDate): LocalDate = when (this) {
        WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DAILY, ONE_TIME -> today
    }
}

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: ChecklistType,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One dated copy of a checklist. Daily checklists get one per day, weekly ones one per week (dated
 * by its Monday), and one-time checklists have exactly one.
 */
@Entity(
    tableName = "checklist_days",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["checklistId", "date"], unique = true)],
)
data class ChecklistDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val date: LocalDate,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val text: String,
    val isChecked: Boolean = false,
    val position: Int,
)

/** A checklist card on the main page, with the progress of its current day. */
data class ChecklistWithProgress(
    val id: Long,
    val title: String,
    val type: ChecklistType,
    val done: Int,
    val total: Int,
)

data class DaySummary(
    val dayId: Long,
    val date: LocalDate,
    val done: Int,
    val total: Int,
)

/** How often one item (matched by its text) was left unticked across a range of past days. */
data class MissedItemStat(
    val text: String,
    /** Days on which the item was left unticked. */
    val missed: Int,
    /** Days on which the item was on the list. */
    val total: Int,
)
