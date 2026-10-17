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
        assertEquals(LocalDate.of(2026, 10, 16), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 1, 59)))
    }

    @Test
    fun fromTwoAmIsNewDay() {
        assertEquals(LocalDate.of(2026, 10, 17), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 2, 0)))
        assertEquals(LocalDate.of(2026, 10, 17), LogicalDate.today(LocalDateTime.of(2026, 10, 17, 23, 59)))
    }

    @Test
    fun delayUntilNextRollover() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(1),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 1, 59)),
        )
        assertEquals(
            TimeUnit.HOURS.toMillis(24),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 2, 0)),
        )
        assertEquals(
            TimeUnit.HOURS.toMillis(4),
            LogicalDate.millisUntilNextRollover(LocalDateTime.of(2026, 10, 17, 22, 0)),
        )
    }
}
