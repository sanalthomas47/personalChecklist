package com.santhomach.personalchecklist.data

import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.util.LogicalDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Dates that can be picked to update a past copy: from the start of the period the checklist was
 * created in (or its oldest copy, if older, e.g. after importing a backup) up to the end of the
 * last finished period. Null when no period has finished yet.
 */
fun pastDateRange(
    type: ChecklistType,
    createdAt: Long,
    oldestCopy: LocalDate?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): ClosedRange<LocalDate>? {
    val created = type.periodStart(LogicalDate.today(LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), zone)))
    val first = if (oldestCopy != null && oldestCopy < created) oldestCopy else created
    val last = type.periodStart(today).minusDays(1)
    return if (first > last) null else first..last
}
