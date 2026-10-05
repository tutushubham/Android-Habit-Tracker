package com.habitsheet.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SectionLabel
import com.habitsheet.ui.SubtleProgress
import com.habitsheet.ui.monthName
import com.habitsheet.ui.percentLabel
import kotlinx.datetime.LocalDate

@Composable
internal fun TodayModeContent(
    state: MonthUiState,
    onPlan: () -> Unit,
    onBackToToday: () -> Unit,
    onTogglePlanned: (planId: String, date: LocalDate) -> Unit,
    onToggleWeekly: (habitId: String, weekStart: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.selectedDay == state.today) "TODAY" else "DAY PLAN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${state.selectedDay.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${state.selectedDay.day} ${state.selectedMonth.monthName()}",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = onPlan, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Plan") }
            }
            if (state.selectedDay != state.today) {
                TextButton(onClick = onBackToToday, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                    Text("Back to today")
                }
            }

            val summary = state.todaySummary
            Row(
                Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DonutProgress(summary.percentage, Modifier.size(52.dp))
                Text(
                    "${summary.completedCount} / ${summary.totalCount} completed • ${summary.percentage.percentLabel()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 16.dp))

            if (summary.totalCount == 0) {
                Text(
                    "Nothing planned for this day. Open Plan to add sessions, or enjoy the rest day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }

            val details = state.dayPlan.associateBy { it.id }
            val toggle = { id: String -> onTogglePlanned(id, state.selectedDay) }
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f)) {
                        PlannedDayGroups(summary.leftHabits, completed = false, details = details, onToggle = toggle)
                    }
                    Column(Modifier.weight(1f)) {
                        PlannedDayGroups(summary.doneHabits, completed = true, details = details, onToggle = toggle)
                    }
                }
            } else {
                PlannedDayGroups(summary.leftHabits, completed = false, details = details, onToggle = toggle)
                PlannedDayGroups(summary.doneHabits, completed = true, details = details, onToggle = toggle)
            }

            val skipped = state.dayPlan.filter { it.skipped }
            if (skipped.isNotEmpty()) {
                SectionLabel("REST / SKIPPED", Modifier.padding(top = 28.dp, bottom = 8.dp))
                skipped.forEach { item ->
                    Text(
                        "${item.habit.name} · ${item.detail.orEmpty()}",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val nextDate = LocalDate.fromEpochDays(state.selectedDay.toEpochDays() + 1)
            val nextSessions = state.tomorrowPlan.filterNot { it.skipped }
            SectionLabel(
                "${if (state.selectedDay == state.today) "TOMORROW" else "NEXT DAY"} · ${nextDate.day} ${MonthKey.from(nextDate).monthName()}",
                Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            if (nextSessions.isEmpty()) {
                Text("Nothing scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                nextSessions.forEach { item ->
                    Text(
                        "${item.habit.name}${item.detail?.let { " · $it" } ?: ""}",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }

            val currentWeekStart = state.selectedMonth.weekStartFor(state.selectedDay)
            val weeklyHabits = currentWeekStart?.let {
                state.weeklyHabits.filter { habit -> habit.isActiveOn(state.selectedDay) }
            }.orEmpty()
            if (currentWeekStart != null && weeklyHabits.isNotEmpty()) {
                SectionLabel("THIS WEEK", Modifier.align(Alignment.Start).padding(top = 40.dp, bottom = 8.dp))
                weeklyHabits.sortedBy { it.displayOrder }.forEach { habit ->
                    val completed = habit.id to currentWeekStart in state.weeklyCompletionKeys
                    TodayHabitRow(
                        name = habit.name,
                        completed = completed,
                        onClick = { onToggleWeekly(habit.id, currentWeekStart) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal enum class DaySection(val openLabel: String, val doneLabel: String) {
    Workout("WORKOUTS", "DONE · WORKOUTS"),
    Study("STUDY", "DONE · STUDY"),
    Habit("HABITS", "DONE · HABITS"),
    Avoid("AVOID TODAY · CHECK AT NIGHT", "KEPT TODAY"),
}

internal fun daySection(habit: com.habitsheet.domain.calculation.DailyShareHabit): DaySection {
    if (habit.kind == HabitKind.AVOIDANCE) return DaySection.Avoid
    val category = habit.categoryName.orEmpty().lowercase()
    return when {
        category.contains("fitness") || category.contains("physical") ||
            habit.name.equals("Run", true) || habit.name.equals("Workout", true) ||
            habit.name.equals("Mobility", true) -> DaySection.Workout
        category.contains("study") || category.contains("mental") ||
            habit.name.equals("Study", true) || habit.name.equals("DSA", true) ||
            habit.name.equals("Android", true) || habit.name.equals("SDE", true) -> DaySection.Study
        else -> DaySection.Habit
    }
}

@Composable
private fun PlannedDayGroups(
    habits: List<com.habitsheet.domain.calculation.DailyShareHabit>,
    completed: Boolean,
    details: Map<String, com.habitsheet.domain.model.PlannedHabit>,
    onToggle: (String) -> Unit,
) {
    DaySection.entries.forEach { section ->
        val items = habits.filter { daySection(it) == section }
        if (items.isEmpty()) return@forEach
        SectionLabel(
            if (completed) section.doneLabel else section.openLabel,
            Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        items.forEach { habit ->
            val subtitle = when {
                section == DaySection.Avoid && !completed -> "Mark kept if you avoided it all day"
                section == DaySection.Avoid && completed -> "Avoidance commitment kept"
                else -> listOfNotNull(habit.categoryName, details[habit.id]?.detail).joinToString(" · ")
            }
            TodayHabitRow(habit.name, completed = completed, onClick = { onToggle(habit.id) }, subtitle = subtitle)
        }
    }
}

@Composable
private fun TodayHabitRow(name: String, completed: Boolean, onClick: () -> Unit, subtitle: String = "") {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                role = Role.Checkbox
                contentDescription = listOf(name, subtitle).filter { it.isNotBlank() }.joinToString(", ")
                stateDescription = if (completed) "Completed" else "Incomplete"
            }
            .clickable(onClickLabel = if (completed) "Mark incomplete" else "Mark complete") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (completed) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(1.dp, if (completed) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (completed) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(widthDp = 411, heightDp = 900)
@Composable
private fun TodayModeContentPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            TodayModeContent(
                state = MonthPreviewData.state(todayMode = true),
                onPlan = {},
                onBackToToday = {},
                onTogglePlanned = { _, _ -> },
                onToggleWeekly = { _, _ -> },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Preview
@Composable
private fun TodayHabitRowPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            Column {
                TodayHabitRow("Read 20 pages", completed = false, onClick = {}, subtitle = "Study 📚 · Chapter review")
                TodayHabitRow("Drink 2L water", completed = true, onClick = {})
            }
        }
    }
}
