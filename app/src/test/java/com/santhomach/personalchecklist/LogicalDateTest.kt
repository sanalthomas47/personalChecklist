package com.santhomach.personalchecklist

import com.santhomach.personalchecklist.util.LogicalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class LogicalDateTest {
    @Test
    fun beforeTwoAmIsStillPreviousDay() {
        assertEquals(LocalDate.of(2026, 10, 16), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 0, 0)))
        assertEquals(LocalDate.of(2026, 10, 16), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 1, 59, 59)))
    }

    @Test
    fun fromTwoAmIsNewDay() {
        assertEquals(LocalDate.of(2026, 10, 17), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 2, 0)))
        assertEquals(LocalDate.of(2026, 10, 17), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 23, 59)))
    }

    @Test
    fun rolloverCrossesMonthAndYearBoundaries() {
        assertEquals(LocalDate.of(2026, 10, 31), LogicalDate.today(LocalDateTime.of(2026, 11, 1, 1, 0)))
        assertEquals(LocalDate.of(2026, 12, 31), LogicalDate.today(LocalDateTime.of(2027, 1, 1, 1, 30)))
        assertEquals(LocalDate.of(2028, 2, 29), LogicalDate.today(LocalDateTime.of(2028, 3, 1, 0, 30)))
    }

    @Test
    fun delayUntilNextRollover() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(1),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 1, 59)),
        )
        assertEquals(
            TimeUnit.HOURS.toMillis(4),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 22, 0)),
        )
    }

    @Test
    fun exactlyAtRolloverWaitsForTomorrow() {
        assertEquals(
            TimeUnit.HOURS.toMillis(24),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 2, 0)),
        )
    }

    @Test
    fun delayCrossesYearBoundary() {
        assertEquals(
            TimeUnit.HOURS.toMillis(3),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 12, 31, 23, 0)),
        )
    }
}
