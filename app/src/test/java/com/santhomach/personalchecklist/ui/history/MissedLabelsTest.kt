package com.santhomach.personalchecklist.ui.history

import com.santhomach.personalchecklist.data.MissedPeriod
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
    }

    @Test
    fun periodLabels() {
        assertEquals(listOf("7 days", "30 days", "All time"), MissedPeriod.entries.map { it.label() })
    }

    @Test
    fun periodStartDates() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(LocalDate.of(2026, 10, 14), MissedPeriod.WEEK.from(today))
        assertEquals(LocalDate.of(2026, 9, 21), MissedPeriod.MONTH.from(today))
        assertEquals(LocalDate.MIN, MissedPeriod.ALL.from(today))
    }
}
