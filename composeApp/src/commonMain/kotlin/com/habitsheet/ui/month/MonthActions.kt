package com.habitsheet.ui.month

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * Everything the month and day views can ask for. Built from the ViewModel once in [MonthScreen]; leaves receive these
 * lambdas (or single ones from here), never the ViewModel, so they can be previewed and tested with plain values.
 */
@Immutable
internal class MonthActions(
    val previousDay: () -> Unit = {},
    val nextDay: () -> Unit = {},
    val currentDay: () -> Unit = {},
    val previousMonth: () -> Unit = {},
    val nextMonth: () -> Unit = {},
    val currentMonth: () -> Unit = {},
    val setTodayMode: (Boolean) -> Unit = {},
    val openDay: (LocalDate) -> Unit = {},
    val togglePlanned: (planId: String, date: LocalDate) -> Unit = { _, _ -> },
    val toggleWeekly: (habitId: String, weekStart: LocalDate) -> Unit = { _, _ -> },
)
