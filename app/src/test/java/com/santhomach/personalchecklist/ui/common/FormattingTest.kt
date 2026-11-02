package com.santhomach.personalchecklist.ui.common

import com.santhomach.personalchecklist.data.db.ChecklistType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormattingTest {
    @Test
    fun progressLabelShowsCountAndPercent() {
        assertEquals("0/4 · 0%", progressLabel(0, 4))
        assertEquals("3/4 · 75%", progressLabel(3, 4))
        assertEquals("4/4 · 100%", progressLabel(4, 4))
    }

    @Test
    fun progressLabelRoundsPercentDown() {
        assertEquals("2/3 · 66%", progressLabel(2, 3))
    }

    @Test
    fun progressLabelForEmptyChecklist() {
        assertEquals("No items", progressLabel(0, 0))
    }

    @Test
    fun dateIsShownWithWeekday() {
        assertEquals("Saturday, 17 Oct 2026", LocalDate.of(2026, 10, 17).display(Locale.ENGLISH))
    }

    @Test
    fun typeLabels() {
        assertEquals("Daily", ChecklistType.DAILY.label())
        assertEquals("Weekly", ChecklistType.WEEKLY.label())
        assertEquals("Monthly", ChecklistType.MONTHLY.label())
    }

    @Test
    fun periodHeadings() {
        val first = LocalDate.of(2026, 10, 1)
        assertEquals("October 2026", first.displayMonth(Locale.ENGLISH))
        assertEquals("Week of 26 Oct 2026", LocalDate.of(2026, 10, 26).displayWeek(Locale.ENGLISH))
        assertEquals(listOf("today", "this week", "this month", "today"), ChecklistType.entries.map { it.currentPeriodName() })
        assertEquals(
            listOf("every day", "every Monday", "on the 1st of every month", null),
            ChecklistType.entries.map { it.resetSchedule() },
        )
        assertEquals("One-time", ChecklistType.ONE_TIME.label())
    }
}
