package com.habitsheet.ui.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.calculation.CategorySummary
import com.habitsheet.domain.calculation.ProgressSummary
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SectionLabel
import com.habitsheet.ui.SubtleProgress
import com.habitsheet.ui.percentLabel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MonthSummaryStrip(
    state: MonthUiState,
    modifier: Modifier = Modifier,
    onCurrentMonth: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SummaryItem(stringResource(Res.string.summary_daily), state.monthlyProgress.percentage.percentLabel())
        SummaryItem(stringResource(Res.string.summary_weekly), state.weeklyProgress.percentage.percentLabel())
        Spacer(Modifier.weight(1f))
        if (onCurrentMonth != null && state.selectedMonth != MonthKey.from(state.today)) {
            TextButton(onClick = onCurrentMonth) {
                Text(stringResource(Res.string.summary_current_month), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun OverviewStrip(state: MonthUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 32.dp)) {
        SectionLabel(stringResource(Res.string.summary_title), Modifier.padding(bottom = 16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricPanel(stringResource(Res.string.summary_monthly_daily), state.monthlyProgress, Modifier.weight(1f))
            MetricPanel(stringResource(Res.string.summary_weekly_habits), state.weeklyProgress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricPanel(label: String, progress: ProgressSummary, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (progress.hasGoal) progress.percentage.percentLabel() else "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (progress.hasGoal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${progress.completed}/${progress.goal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        SubtleProgress(progress.percentage, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
internal fun DailyWeeksSection(
    weeks: List<com.habitsheet.domain.calculation.DailyWeekSummary>,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    if (weeks.isEmpty()) return
    Column(modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(Res.string.summary_daily_week_totals), Modifier.padding(bottom = 12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            weeks.forEach { week ->
                Row(
                    Modifier
                        .width(116.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DonutProgress(week.percentage, Modifier.size(24.dp))
                    Column {
                        Text(stringResource(Res.string.week_short, week.index + 1), style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${week.completed}/${week.goal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategorySection(categories: List<CategorySummary>) {
    if (categories.isEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            SectionLabel(stringResource(Res.string.summary_categories), Modifier.padding(bottom = 12.dp))
            Text(
                stringResource(Res.string.summary_categories_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel(stringResource(Res.string.summary_categories), Modifier.padding(bottom = 12.dp))
        categories.forEach { summary ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(Res.string.category_progress, summary.completed, summary.goal, summary.remaining, summary.percentage.percentLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun DonutProgress(progress: Double, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val value = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
        drawArc(
            value,
            startAngle = -90f,
            sweepAngle = 360f * progress.toFloat().coerceIn(0f, 1f),
            useCenter = false,
            style = stroke,
        )
    }
}

@Preview(widthDp = 411)
@Composable
private fun MonthSummaryPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            val state = MonthPreviewData.state()
            Column {
                MonthSummaryStrip(state, onCurrentMonth = {})
                DailyWeeksSection(state.dailyWeeks)
                OverviewStrip(state)
                CategorySection(state.categorySummaries)
            }
        }
    }
}
