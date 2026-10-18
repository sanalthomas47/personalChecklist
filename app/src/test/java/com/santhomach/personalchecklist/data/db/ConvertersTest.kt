package com.santhomach.personalchecklist.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun localDateRoundTripsThroughEpochDay() {
        for (date in listOf(LocalDate.of(1970, 1, 1), LocalDate.of(2026, 10, 17), LocalDate.of(2028, 2, 29))) {
            assertEquals(date, converters.toLocalDate(converters.fromLocalDate(date)))
        }
        assertEquals(0L, converters.fromLocalDate(LocalDate.of(1970, 1, 1)))
    }

    @Test
    fun localDatesSortInDateOrder() {
        // History and "latest day" queries sort on the stored value, so it must follow date order.
        val earlier = converters.fromLocalDate(LocalDate.of(2026, 12, 31))
        val later = converters.fromLocalDate(LocalDate.of(2027, 1, 1))
        assert(earlier < later)
    }

    @Test
    fun checklistTypeRoundTripsByName() {
        for (type in ChecklistType.entries) {
            assertEquals(type, converters.toType(converters.fromType(type)))
        }
        assertEquals("ONE_TIME", converters.fromType(ChecklistType.ONE_TIME))
    }
}
