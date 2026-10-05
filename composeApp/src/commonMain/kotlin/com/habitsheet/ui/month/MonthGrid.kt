package com.habitsheet.ui.month

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SubtleProgress
import com.habitsheet.ui.categoryColor
import com.habitsheet.ui.percentLabel
import com.habitsheet.ui.weekdayLabel
import kotlinx.datetime.LocalDate

@Composable
internal fun EmptyHabits(onManage: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No daily habits yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Add your first daily habit from Manage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onManage,
            modifier = Modifier.padding(top = 24.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Manage Habits")
        }
    }
}

@Composable
internal fun DailyHabitGrid(
    state: MonthUiState,
    onOpenDay: (LocalDate) -> Unit,
    onTogglePlanned: (planId: String, date: LocalDate) -> Unit,
    leftWidth: Dp,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val dates = state.selectedMonth.dates()
    val density = LocalDensity.current
    // Only rows that have at least one planned session this month — hide never-planned clutter.
    val gridHabits = state.habits.filter { summary ->
        state.monthPlan.values.any { day -> day.any { it.habit.id == summary.habit.id } }
    }.ifEmpty { state.habits }

    LaunchedEffect(state.selectedMonth, state.today, scrollState.maxValue, state.scrollToTodayTrigger, cellWidth) {
        if (state.today in dates) {
            val index = dates.indexOf(state.today)
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val target = ((index - 1).coerceAtLeast(0) * cellWidthPx).toInt()
            val currentScroll = scrollState.value
            val isVisible = currentScroll <= target && currentScroll >= target - (cellWidthPx * 3)
            if (state.scrollToTodayTrigger > 0 || (scrollState.value == 0 && !isVisible)) {
                scrollState.animateScrollTo(target.coerceAtMost(scrollState.maxValue))
            }
        }
    }
    val rowHeight = 56.dp
    val headerHeight = 44.dp
    val summaryHeight = 32.dp
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Row(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .width(leftWidth)
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                },
        ) {
            Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text("ACTIVITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            gridHabits.forEach { summary ->
                val category = state.categories.firstOrNull { it.id == summary.habit.categoryId }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .drawBehind {
                            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        summary.habit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            category?.name?.uppercase() ?: "GENERAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor(category, state.categories).copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${summary.completed}/${summary.goal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            SummaryLabel("DONE", summaryHeight)
            SummaryLabel("MISS", summaryHeight)
            SummaryLabel("%", summaryHeight)
        }
        Column(Modifier.weight(1f).horizontalScroll(scrollState)) {
            Row(Modifier.height(headerHeight)) {
                dates.forEach { date -> DateHeader(date, date == state.today, cellWidth) { onOpenDay(date) } }
            }
            gridHabits.forEach { summary ->
                Row(
                    Modifier
                        .height(rowHeight)
                        .drawBehind {
                            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        },
                ) {
                    dates.forEach { date ->
                        val planned = state.monthPlan[date].orEmpty().filter { it.habit.id == summary.habit.id && !it.skipped }
                        val completedCount = planned.count { it.id to date in state.dailyCompletionKeys }
                        when (planned.size) {
                            0 -> NotPlannedCell(
                                name = summary.habit.name,
                                date = date,
                                width = cellWidth,
                                height = rowHeight,
                            )

                            1 -> CompletionCell(
                                completed = completedCount == 1,
                                currentDay = date == state.today,
                                description = "${summary.habit.name}, $date: planned, ${if (completedCount == 1) "complete" else "incomplete"}",
                                width = cellWidth,
                                height = rowHeight,
                                onClick = { onTogglePlanned(planned.single().id, date) },
                            )

                            else -> Box(
                                Modifier.width(cellWidth).height(rowHeight)
                                    .clickable(onClickLabel = "Open ${planned.size} planned sessions") { onOpenDay(date) }
                                    .semantics { contentDescription = "${summary.habit.name}, $date: $completedCount of ${planned.size} complete" },
                                contentAlignment = Alignment.Center,
                            ) { Text("$completedCount/${planned.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            SummaryRow(state.daily.map { it.completed.toString() }, cellWidth, summaryHeight)
            SummaryRow(state.daily.map { it.notCompleted.toString() }, cellWidth, summaryHeight)
            SummaryRow(state.daily.map { it.percentage.percentLabel() }, cellWidth, summaryHeight)
        }
    }
}

internal data class MonthGridLayout(val leftWidth: Dp, val availableForDays: Dp) {
    fun cellWidth(dayCount: Int): Dp {
        if (dayCount <= 0) return 36.dp
        val fitted = availableForDays / dayCount
        return fitted.coerceIn(28.dp, 44.dp)
    }
}

internal fun monthGridLayout(maxWidth: Dp): MonthGridLayout {
    val left = when {
        maxWidth < 420.dp -> 108.dp
        maxWidth < 700.dp -> 132.dp
        maxWidth < 1000.dp -> 168.dp
        else -> 200.dp
    }
    return MonthGridLayout(leftWidth = left, availableForDays = (maxWidth - left).coerceAtLeast(160.dp))
}

@Composable
private fun NotPlannedCell(name: String, date: LocalDate, width: Dp, height: Dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .semantics { contentDescription = "$name, $date: not planned" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "·",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, current: Boolean, width: Dp, onClick: () -> Unit) {
    val indicatorColor = MaterialTheme.colorScheme.primary
    val dateDescription = if (current) "Today, ${date.dayOfWeek.name} ${date.day}" else "${date.dayOfWeek.name} ${date.day}"

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClickLabel = "Open plan for $date", onClick = onClick)
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
            .drawBehind {
                if (current) {
                    val indicatorHeight = 3.dp.toPx()
                    drawRect(indicatorColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, indicatorHeight))
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = dateDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.weekdayLabel().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = if (current) {
                Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier.padding(top = 2.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MonthGridLegend(modifier: Modifier = Modifier) {
    Text(
        "□ planned incomplete · ✓ complete · · not planned · 1/2 multiple (tap day)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun CompletionCell(
    completed: Boolean,
    currentDay: Boolean,
    enabled: Boolean = true,
    description: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val statusDescription = when {
        !enabled -> "Not planned"
        completed -> "Completed"
        else -> "Incomplete"
    }

    Box(
        Modifier
            .width(width)
            .height(height)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    currentDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
            )
            .semantics {
                role = Role.Checkbox
                contentDescription = description
                stateDescription = statusDescription
            }
            .clickable(
                enabled = enabled,
                onClickLabel = if (completed) "Mark incomplete" else "Mark complete",
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (completed && enabled) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    BorderStroke(
                        if (completed && enabled) 0.dp else 1.dp,
                        if (completed && enabled) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed && enabled) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SummaryLabel(text: String, height: Dp) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryRow(values: List<String>, width: Dp, height: Dp) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.height(height)) {
        values.forEach { value ->
            Box(
                Modifier
                    .width(width)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Preview(widthDp = 411, heightDp = 480)
@Composable
private fun DailyHabitGridPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            val state = MonthPreviewData.state()
            Column {
                MonthGridLegend()
                DailyHabitGrid(state, onOpenDay = {}, onTogglePlanned = { _, _ -> }, leftWidth = 108.dp, cellWidth = 36.dp)
            }
        }
    }
}

@Preview
@Composable
private fun EmptyHabitsPreview() {
    HabitSheetTheme(darkTheme = false) { Surface { EmptyHabits(onManage = {}) } }
}

@Preview
@Composable
private fun GridCellsPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            Row {
                CompletionCell(completed = true, currentDay = false, description = "done", width = 44.dp, height = 56.dp, onClick = {})
                CompletionCell(completed = false, currentDay = true, description = "open", width = 44.dp, height = 56.dp, onClick = {})
                NotPlannedCell("Read", LocalDate(2026, 10, 5), width = 44.dp, height = 56.dp)
                DateHeader(LocalDate(2026, 10, 5), current = true, width = 44.dp, onClick = {})
            }
        }
    }
}
