package com.habitsheet.ui.month

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SectionLabel
import com.habitsheet.ui.monthName
import com.habitsheet.ui.percentLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WeeklySection(
    state: MonthUiState,
    onToggleWeekly: (habitId: String, weekStart: LocalDate) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    Column(modifier) {
        SectionLabel(stringResource(Res.string.weekly_title), Modifier.padding(bottom = 16.dp))
        if (state.weeklyHabits.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.weekly_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(Res.string.weekly_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        val nameWidth = 160.dp
        val weekWidth = 64.dp
        val dividerColor = MaterialTheme.colorScheme.outlineVariant

        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .width(nameWidth)
                    .drawBehind {
                        drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    },
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(Res.string.grid_habit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.weeklyHabits.forEach { habit ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .drawBehind {
                                drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SummaryLabel(stringResource(Res.string.grid_done), 36.dp)
                SummaryLabel(stringResource(Res.string.grid_left), 36.dp)
                SummaryLabel(stringResource(Res.string.grid_goal), 36.dp)
                SummaryLabel("%", 36.dp)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                state.weeklyBlocks.forEach { block ->
                    Column(Modifier.width(weekWidth)) {
                        Row(
                            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DonutProgress(block.percentage, Modifier.size(20.dp))
                            Text(
                                stringResource(Res.string.week_short, block.index + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.weeklyHabits.forEach { habit ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .drawBehind {
                                        drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                CompletionCell(
                                    completed = habit.id to block.weekStartDate in state.weeklyCompletionKeys,
                                    currentDay = state.today in state.selectedMonth.datesForWeek(block.index),
                                    enabled = state.selectedMonth.datesForWeek(block.index).any(habit::isActiveOn),
                                    description = stringResource(
                                        Res.string.week_cell_description,
                                        habit.name,
                                        block.index + 1,
                                        state.selectedMonth.monthName(),
                                    ),
                                    width = weekWidth,
                                    height = 46.dp,
                                    onClick = { onToggleWeekly(habit.id, block.weekStartDate) },
                                )
                            }
                        }
                        SummaryValue(block.completed.toString(), 36.dp)
                        SummaryValue(block.notCompleted.toString(), 36.dp)
                        SummaryValue(block.goal.toString(), 36.dp)
                        SummaryValue(block.percentage.percentLabel(), 36.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(value: String, height: Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(widthDp = 411)
@Composable
private fun WeeklySectionPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface { WeeklySection(MonthPreviewData.state(), onToggleWeekly = { _, _ -> }) }
    }
}

@Preview(widthDp = 411)
@Composable
private fun WeeklySectionEmptyPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface { WeeklySection(MonthPreviewData.empty, onToggleWeekly = { _, _ -> }) }
    }
}
