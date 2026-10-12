package com.habitsheet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.resources.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * Names and date shapes come from the string catalog, so the platform language picks them and a
 * translator can reorder the parts (`date_weekday_day_month`). Digits stay ASCII on purpose: the sheet,
 * the CSV and the backups use them too, and no screen shows a decimal separator.
 */

@Composable
internal fun MonthKey.monthName(): String = stringResource(monthNameResources[month - 1])

@Composable
internal fun LocalDate.weekdayLabel(): String = stringResource(weekdayInitialResources.getValue(dayOfWeek))

@Composable
internal fun LocalDate.weekdayName(): String = stringResource(weekdayFullResources.getValue(dayOfWeek))

/** "Monday, 12 October" */
@Composable
internal fun LocalDate.fullDayLabel(): String =
    stringResource(Res.string.date_weekday_day_month, weekdayName(), day, MonthKey.from(this).monthName())

/** "Monday, 12 October 2026" */
@Composable
internal fun LocalDate.fullDateLabel(): String =
    stringResource(Res.string.date_weekday_day_month_year, weekdayName(), day, MonthKey.from(this).monthName(), year)

/** "12 October" */
@Composable
internal fun LocalDate.dayMonthLabel(): String =
    stringResource(Res.string.date_day_month, day, MonthKey.from(this).monthName())

@Composable
internal fun Double.percentLabel(): String = stringResource(Res.string.percent_value, (this * 100).roundToInt())

/**
 * Weekday numbers (1 = Monday … 7 = Sunday, as stored in `WeeklyPlan`) in the order a week should be listed,
 * starting at [first]. Only labels use this; stored weekdays and the five-block month are unchanged.
 */
internal fun weekdayNumbersFrom(first: DayOfWeek): List<Int> = (0 until DAYS_IN_WEEK).map { (first.ordinal + it) % DAYS_IN_WEEK + 1 }

/** Theme tokens, so each colour keeps its contrast in light and dark. */
@Composable
internal fun categoryColor(category: Category?, all: List<Category>): Color {
    val index = all.indexOfFirst { it.id == category?.id }.coerceAtLeast(0)
    val scheme = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> scheme.secondary
        1 -> scheme.primary
        else -> scheme.tertiary
    }
}

/** Fixed row heights grow with the text size (up to 200%) so enlarged text is not clipped. */
@Composable
internal fun scaledForText(base: Dp): Dp = base * LocalDensity.current.fontScale.coerceIn(1f, 2f)

private const val DAYS_IN_WEEK = 7

private val monthNameResources: List<StringResource> = listOf(
    Res.string.month_january, Res.string.month_february, Res.string.month_march,
    Res.string.month_april, Res.string.month_may, Res.string.month_june,
    Res.string.month_july, Res.string.month_august, Res.string.month_september,
    Res.string.month_october, Res.string.month_november, Res.string.month_december,
)

private val weekdayFullResources: Map<DayOfWeek, StringResource> = mapOf(
    DayOfWeek.MONDAY to Res.string.weekday_full_monday,
    DayOfWeek.TUESDAY to Res.string.weekday_full_tuesday,
    DayOfWeek.WEDNESDAY to Res.string.weekday_full_wednesday,
    DayOfWeek.THURSDAY to Res.string.weekday_full_thursday,
    DayOfWeek.FRIDAY to Res.string.weekday_full_friday,
    DayOfWeek.SATURDAY to Res.string.weekday_full_saturday,
    DayOfWeek.SUNDAY to Res.string.weekday_full_sunday,
)

private val weekdayInitialResources: Map<DayOfWeek, StringResource> = mapOf(
    DayOfWeek.MONDAY to Res.string.weekday_initial_monday,
    DayOfWeek.TUESDAY to Res.string.weekday_initial_tuesday,
    DayOfWeek.WEDNESDAY to Res.string.weekday_initial_wednesday,
    DayOfWeek.THURSDAY to Res.string.weekday_initial_thursday,
    DayOfWeek.FRIDAY to Res.string.weekday_initial_friday,
    DayOfWeek.SATURDAY to Res.string.weekday_initial_saturday,
    DayOfWeek.SUNDAY to Res.string.weekday_initial_sunday,
)
