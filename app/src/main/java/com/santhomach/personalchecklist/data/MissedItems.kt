package com.santhomach.personalchecklist.data

import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.MissedItemStat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How far back the "Most missed items" section looks, counted in the checklist's own periods: days
 * for a daily checklist, weeks for weekly, months for monthly. The current period is never
 * included; it isn't over yet.
 */
enum class MissedPeriod(private val days: Long?, private val weeks: Long?, private val months: Long?) {
    SHORT(days = 7, weeks = 4, months = 3),
    LONG(days = 30, weeks = 12, months = 12),
    ALL(days = null, weeks = null, months = null);

    /** Number of periods covered, or null for all time. */
    fun length(type: ChecklistType): Long? = when (type) {
        ChecklistType.WEEKLY -> weeks
        ChecklistType.MONTHLY -> months
        ChecklistType.DAILY, ChecklistType.ONE_TIME -> days
    }

    /** First date in the range, counting back from (and excluding) the period starting at [currentStart]. */
    fun from(currentStart: LocalDate, type: ChecklistType): LocalDate {
        val length = length(type) ?: return LocalDate.MIN
        val unit = when (type) {
            ChecklistType.WEEKLY -> ChronoUnit.WEEKS
            ChecklistType.MONTHLY -> ChronoUnit.MONTHS
            ChecklistType.DAILY, ChecklistType.ONE_TIME -> ChronoUnit.DAYS
        }
        return currentStart.minus(length, unit)
    }
}

data class MissedItemsReport(
    /** Past days (or weeks, or months) in the range that have a copy of the checklist. */
    val pastPeriods: Int,
    /** Items missed at least once, most missed first. */
    val missed: List<MissedItemStat>,
    /** Items that were ticked every day (or week, or month) they appeared. */
    val neverMissed: List<String>,
)
