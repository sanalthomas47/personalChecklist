package com.santhomach.personalchecklist.ui.history

import com.santhomach.personalchecklist.data.MissedPeriod
import com.santhomach.personalchecklist.data.db.ChecklistType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MissedLabelsTest {
    @Test
    fun missedLabelShowsCountAndPercentRoundedDown() {
        assertEquals("12/30 days · 40%", missedLabel(12, 30))
        assertEquals("2/3 days · 66%", missedLabel(2, 3))
        assertEquals("3/3 days · 100%", missedLabel(3, 3))
        assertEquals("0/0 days · 0%", missedLabel(0, 0))
        assertEquals("3/4 weeks · 75%", missedLabel(3, 4, "week"))
    }

    @Test
    fun periodLabels() {
        assertEquals(listOf("7 days", "30 days", "All time"), MissedPeriod.entries.map { it.label(ChecklistType.DAILY) })
        assertEquals(listOf("4 weeks", "12 weeks", "All time"), MissedPeriod.entries.map { it.label(ChecklistType.WEEKLY) })
    }

    @Test
    fun periodStartDates() {
        val today = LocalDate.of(2026, 10, 21)
        val daily = ChecklistType.DAILY
        assertEquals(LocalDate.of(2026, 10, 14), MissedPeriod.SHORT.from(today, daily))
        assertEquals(LocalDate.of(2026, 9, 21), MissedPeriod.LONG.from(today, daily))
        assertEquals(LocalDate.MIN, MissedPeriod.ALL.from(today, daily))
    }

    @Test
    fun weeklyPeriodStartDatesCountWholeWeeks() {
        val monday = LocalDate.of(2026, 10, 26)
        val weekly = ChecklistType.WEEKLY
        assertEquals(LocalDate.of(2026, 9, 28), MissedPeriod.SHORT.from(monday, weekly))
        assertEquals(LocalDate.of(2026, 8, 3), MissedPeriod.LONG.from(monday, weekly))
        assertEquals(LocalDate.MIN, MissedPeriod.ALL.from(monday, weekly))
    }
}
