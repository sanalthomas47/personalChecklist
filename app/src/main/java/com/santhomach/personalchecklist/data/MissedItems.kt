package com.santhomach.personalchecklist.data

import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.MissedItemStat
import java.time.LocalDate

/**
 * How far back the "Most missed items" section looks, counted in the checklist's own periods: days
 * for a daily checklist, weeks for a weekly one. The current period is never included; it isn't over yet.
 */
enum class MissedPeriod(private val days: Long?, private val weeks: Long?) {
    SHORT(days = 7, weeks = 4),
    LONG(days = 30, weeks = 12),
    ALL(days = null, weeks = null);

    /** Number of periods covered, or null for all time. */
    fun length(type: ChecklistType): Long? = if (type == ChecklistType.WEEKLY) weeks else days

    /** First date in the range, counting back from (and excluding) the period starting at [currentStart]. */
    fun from(currentStart: LocalDate, type: ChecklistType): LocalDate {
        val length = length(type) ?: return LocalDate.MIN
        return if (type == ChecklistType.WEEKLY) currentStart.minusWeeks(length) else currentStart.minusDays(length)
    }
}

data class MissedItemsReport(
    /** Past days (or weeks) in the range that have a copy of the checklist. */
    val pastPeriods: Int,
    /** Items missed at least once, most missed first. */
    val missed: List<MissedItemStat>,
    /** Items that were ticked every day (or week) they appeared. */
    val neverMissed: List<String>,
)
