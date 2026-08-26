package com.habitsheet.ui

import androidx.compose.ui.graphics.Color
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.MonthKey
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

internal fun MonthKey.label(): String = "${monthNames[month - 1]} $year"
internal fun LocalDate.weekdayLabel(): String = dayOfWeek.name.take(1)
internal fun Double.percentLabel(): String = "${(this * 100).roundToInt()}%"

internal fun categoryColor(category: Category?, all: List<Category>): Color {
    val index = all.indexOfFirst { it.id == category?.id }.coerceAtLeast(0)
    return when (index % 3) {
        0 -> HabitSlate
        1 -> HabitGreen
        else -> HabitTertiary
    }
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
