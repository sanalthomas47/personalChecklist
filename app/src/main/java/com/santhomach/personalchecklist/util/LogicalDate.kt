package com.santhomach.personalchecklist.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The day rolls over at 2am rather than midnight, matching when the daily copy is made. */
object LogicalDate {
    val ROLLOVER: LocalTime = LocalTime.of(2, 0)

    fun today(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        now.minusHours(ROLLOVER.hour.toLong()).toLocalDate()

    fun millisUntilNextRollover(now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.toLocalDate().atTime(ROLLOVER)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
