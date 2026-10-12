package com.habitsheet.ui

import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekdayOrderTest {
    @Test
    fun mondayFirstIsTheStoredOrder() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), weekdayNumbersFrom(DayOfWeek.MONDAY))
    }

    @Test
    fun sundayFirstListsSundayThenMondayToSaturday() {
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), weekdayNumbersFrom(DayOfWeek.SUNDAY))
    }

    @Test
    fun saturdayFirstWrapsAround() {
        assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), weekdayNumbersFrom(DayOfWeek.SATURDAY))
    }

    @Test
    fun everyStartCoversEachWeekdayOnce() {
        DayOfWeek.entries.forEach { first ->
            assertEquals((1..7).toList(), weekdayNumbersFrom(first).sorted())
        }
    }
}

class PlatformFirstWeekdayMappingTest {
    @Test
    fun sundayBasedNumbersMapToIsoDays() {
        val expected = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        )
        assertEquals(expected, (1..7).map { com.habitsheet.platform.dayOfWeekFromSundayBased(it) })
    }

    @Test
    fun unknownNumbersFallBackToMonday() {
        assertEquals(DayOfWeek.MONDAY, com.habitsheet.platform.dayOfWeekFromSundayBased(0))
        assertEquals(DayOfWeek.MONDAY, com.habitsheet.platform.dayOfWeekFromSundayBased(8))
    }
}
