package com.santhomach.personalchecklist.data

import com.santhomach.personalchecklist.data.db.MissedItemStat
import java.time.LocalDate

/** How far back the "Most missed items" section looks. Today is never included; it isn't over yet. */
enum class MissedPeriod(val days: Long?) {
    WEEK(7),
    MONTH(30),
    ALL(null);

    /** First day in the period, counting back from (and excluding) [today]. */
    fun from(today: LocalDate): LocalDate = days?.let { today.minusDays(it) } ?: LocalDate.MIN
}

data class MissedItemsReport(
    /** Past days in the period that have a copy of the checklist. */
    val pastDays: Int,
    /** Items missed at least once, most missed first. */
    val missed: List<MissedItemStat>,
    /** Items that were ticked every day they appeared. */
    val neverMissed: List<String>,
)
