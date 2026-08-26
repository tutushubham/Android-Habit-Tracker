package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate

data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    init {
        require(month in 1..12) { "Month must be in 1..12" }
    }

    val firstDay: LocalDate get() = LocalDate(year, month, 1)
    val daysInMonth: Int get() = MonthEngine.daysInMonth(year, month)
    val lastDay: LocalDate get() = LocalDate(year, month, daysInMonth)

    fun previous(): MonthKey = if (month == 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)
    fun next(): MonthKey = if (month == 12) MonthKey(year + 1, 1) else MonthKey(year, month + 1)
    fun dates(): List<LocalDate> = (1..daysInMonth).map { day -> LocalDate(year, month, day) }

    fun weekStarts(): List<LocalDate> =
        (1..daysInMonth step 7).map { day -> LocalDate(year, month, day) }

    fun datesForWeek(index: Int): List<LocalDate> {
        require(index in 0..4)
        val first = index * 7 + 1
        if (first > daysInMonth) return emptyList()
        val last = minOf(first + 6, daysInMonth)
        return (first..last).map { day -> LocalDate(year, month, day) }
    }

    override fun compareTo(other: MonthKey): Int =
        (year * 12 + month).compareTo(other.year * 12 + other.month)

    companion object {
        fun from(date: LocalDate): MonthKey = MonthKey(date.year, date.month.ordinal + 1)
    }
}

object MonthEngine {
    fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> error("Month must be in 1..12")
    }
}
