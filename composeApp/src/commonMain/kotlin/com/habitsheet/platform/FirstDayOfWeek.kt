package com.habitsheet.platform

import kotlinx.datetime.DayOfWeek

/** The first day of the week for the device's region (Monday in most of Europe, Sunday in the US). */
expect fun localeFirstDayOfWeek(): DayOfWeek

private const val DAYS_IN_WEEK = 7
private const val SUNDAY_BASED_TO_MONDAY_BASED = DAYS_IN_WEEK - 2

/** Platform calendars number the week 1 = Sunday … 7 = Saturday; out-of-range values fall back to Monday. */
fun dayOfWeekFromSundayBased(value: Int): DayOfWeek =
    if (value in 1..DAYS_IN_WEEK) DayOfWeek.entries[(value + SUNDAY_BASED_TO_MONDAY_BASED) % DAYS_IN_WEEK] else DayOfWeek.MONDAY
