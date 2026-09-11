package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthEngineTest {
    @Test
    fun handlesLeapYears() {
        assertTrue(MonthEngine.isLeapYear(2024))
        assertFalse(MonthEngine.isLeapYear(2100))
        assertTrue(MonthEngine.isLeapYear(2000))
        assertEquals(29, MonthKey(2024, 2).daysInMonth)
        assertEquals(28, MonthKey(2025, 2).daysInMonth)
    }

    @Test
    fun handlesEveryMonthLength() {
        assertEquals(28, MonthKey(2025, 2).dates().size)
        assertEquals(29, MonthKey(2024, 2).dates().size)
        assertEquals(30, MonthKey(2026, 9).dates().size)
        assertEquals(31, MonthKey(2026, 8).dates().size)
    }

    @Test
    fun navigationCrossesYearBoundariesWithoutCopyingState() {
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).previous())
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
    }

    @Test
    fun resolvesWorkbookWeekForAnyDateInMonth() {
        val september = MonthKey(2026, 9)
        assertEquals(LocalDate(2026, 9, 1), september.weekStartFor(LocalDate(2026, 9, 7)))
        assertEquals(LocalDate(2026, 9, 8), september.weekStartFor(LocalDate(2026, 9, 8)))
        assertEquals(LocalDate(2026, 9, 29), september.weekStartFor(LocalDate(2026, 9, 30)))
        assertEquals(null, september.weekStartFor(LocalDate(2026, 10, 1)))
    }
}
