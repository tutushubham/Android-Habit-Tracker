package com.habitsheet.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Month lengths, leap years and the workbook's day-1/8/15/22/29 week blocks, over 150 years of months. */
class MonthEnginePropertyTest {
    private val years = 1970..2120

    private fun referenceDays(year: Int, month: Int): Int {
        val first = LocalDate(year, month, 1)
        return first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).day
    }

    @Test
    fun monthLengthsMatchTheCalendarForEveryMonthAndOnlyTheFourExpectedLengthsExist() {
        val seen = mutableSetOf<Int>()
        for (year in years) {
            for (month in 1..12) {
                val days = MonthEngine.daysInMonth(year, month)
                assertEquals(referenceDays(year, month), days, "$year-$month")
                seen += days
            }
        }
        assertEquals(setOf(28, 29, 30, 31), seen)
    }

    @Test
    fun leapYearRuleIsDivisibleBy4ExceptCenturiesNotDivisibleBy400() {
        for (year in years) {
            val reference = runCatching { LocalDate(year, 2, 29) }.isSuccess
            assertEquals(reference, MonthEngine.isLeapYear(year), "year $year")
            assertEquals(if (reference) 29 else 28, MonthEngine.daysInMonth(year, 2))
        }
        assertFalse(MonthEngine.isLeapYear(1900))
        assertTrue(MonthEngine.isLeapYear(2000))
        assertFalse(MonthEngine.isLeapYear(2100))
        assertTrue(MonthEngine.isLeapYear(2024))
    }

    @Test
    fun aYearHas365Or366DaysAndMonthsOutsideOneToTwelveAreRejected() {
        for (year in years) {
            val total = (1..12).sumOf { MonthEngine.daysInMonth(year, it) }
            assertEquals(if (MonthEngine.isLeapYear(year)) 366 else 365, total, "year $year")
        }
        assertFailsWith<IllegalStateException> { MonthEngine.daysInMonth(2026, 0) }
        assertFailsWith<IllegalStateException> { MonthEngine.daysInMonth(2026, 13) }
        assertFailsWith<IllegalArgumentException> { MonthKey(2026, 0) }
        assertFailsWith<IllegalArgumentException> { MonthKey(2026, 13) }
    }

    @Test
    fun datesCoverTheMonthOnceInOrderAndPreviousAndNextAreInverses() {
        for (year in years) {
            for (month in 1..12) {
                val key = MonthKey(year, month)
                val dates = key.dates()
                assertEquals(key.daysInMonth, dates.size, "$key")
                assertEquals(key.firstDay, dates.first())
                assertEquals(key.lastDay, dates.last())
                assertTrue(dates.zipWithNext().all { (a, b) -> a.plus(1, DateTimeUnit.DAY) == b }, "$key is consecutive")
                assertTrue(dates.all { MonthKey.from(it) == key }, "$key")

                assertEquals(key, key.next().previous())
                assertEquals(key, key.previous().next())
                assertTrue(key < key.next() && key.previous() < key)
                assertEquals(key.lastDay.plus(1, DateTimeUnit.DAY), key.next().firstDay, "$key to next")
            }
        }
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).previous())
    }

    @Test
    fun weekBlocksAreSevenDaysFromTheFirstAndTheFifthHoldsOnlyTheRealDays29To31() {
        for (year in years) {
            for (month in 1..12) {
                val key = MonthKey(year, month)
                val days = key.daysInMonth
                val blocks = (0..4).map { key.datesForWeek(it) }

                assertEquals(key.dates(), blocks.flatten(), "$key: the blocks partition the month")
                assertEquals(listOf(7, 7, 7, 7), blocks.take(4).map { it.size }, "$key")
                assertEquals(days - 28, blocks[4].size, "$key: fifth block")
                assertEquals(
                    listOf(1, 8, 15, 22) + if (days >= 29) listOf(29) else emptyList(),
                    key.weekStarts().map { it.day },
                    "$key",
                )
                key.weekStarts().forEachIndexed { index, start -> assertEquals(blocks[index].first(), start) }
            }
        }
    }

    @Test
    fun everyDateMapsToTheStartOfItsOwnBlockAndOtherMonthsMapToNull() {
        val random = Random(7)
        repeat(200) {
            val key = MonthKey(random.nextInt(1990, 2110), random.nextInt(1, 13))
            val blocks = (0..4).map { key.datesForWeek(it) }
            for (date in key.dates()) {
                val block = blocks.first { date in it }
                assertEquals(block.first(), key.weekStartFor(date), "$key $date")
            }
            assertNull(key.weekStartFor(key.previous().lastDay))
            assertNull(key.weekStartFor(key.next().firstDay))
        }
    }

    @Test
    fun weekIndexOutsideZeroToFourIsRejected() {
        assertFailsWith<IllegalArgumentException> { MonthKey(2026, 2).datesForWeek(-1) }
        assertFailsWith<IllegalArgumentException> { MonthKey(2026, 2).datesForWeek(5) }
        assertTrue(MonthKey(2026, 2).datesForWeek(4).isEmpty(), "a 28-day February has no fifth block")
        assertEquals(1, MonthKey(2028, 2).datesForWeek(4).size, "a leap February has one day in the fifth block")
    }
}
