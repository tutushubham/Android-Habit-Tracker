package com.habitsheet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.calculation.CategorySummary
import com.habitsheet.domain.calculation.ProgressSummary
import com.habitsheet.domain.model.Category
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.presentation.MonthViewModel
import kotlinx.datetime.LocalDate

@Composable
fun MonthScreen(viewModel: MonthViewModel, onManage: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                TabletMonthScreen(state, viewModel, onManage)
            } else {
                PhoneMonthScreen(state, viewModel, onManage)
            }
        }
    }
}

@Composable
private fun PhoneMonthScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onManage: () -> Unit,
) {
    Scaffold(
        topBar = { MonthTopBar(state, viewModel, compact = true) },
        bottomBar = { BottomNavigation(selectedTracker = true, onTracker = {}, onManage = onManage) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("Daily habits", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (state.habits.isEmpty()) {
                EmptyHabits(onManage)
            } else {
                DailyHabitGrid(
                    state = state,
                    viewModel = viewModel,
                    leftWidth = 140.dp,
                    cellWidth = 36.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            WeeklySection(state, viewModel)
            OverviewStrip(state)
            CategorySection(state.categorySummaries)
        }
    }
}

@Composable
private fun TabletMonthScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onManage: () -> Unit,
) {
    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        MonthSidebar(state, onManage)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MonthTopBar(state, viewModel, compact = false)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                if (state.habits.isEmpty()) {
                    EmptyHabits(onManage)
                } else {
                    DailyHabitGrid(
                        state = state,
                        viewModel = viewModel,
                        leftWidth = 290.dp,
                        cellWidth = 34.dp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                WeeklySection(state, viewModel, Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}

@Composable
private fun MonthTopBar(state: MonthUiState, viewModel: MonthViewModel, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(if (compact) 56.dp else 80.dp)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .padding(horizontal = if (compact) 8.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = viewModel::previousMonth) {
            Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = state.selectedMonth.label(),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            textAlign = if (compact) TextAlign.Center else TextAlign.Start,
        )
        TextButton(onClick = viewModel::nextMonth) {
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = viewModel::currentMonth) {
            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyHabits(onManage: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No daily habits yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add the habits and monthly goals you want to track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onManage, modifier = Modifier.padding(top = 8.dp)) {
            Text("＋ Add daily habit", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DailyHabitGrid(
    state: MonthUiState,
    viewModel: MonthViewModel,
    leftWidth: Dp,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val dates = state.selectedMonth.dates().reversed()
    LaunchedEffect(state.selectedMonth, state.today, scrollState.maxValue) {
        if (state.today in dates) {
            val index = dates.indexOf(state.today)
            val target = ((index - 1).coerceAtLeast(0) * cellWidth.value).toInt()
            scrollState.scrollTo(target.coerceAtMost(scrollState.maxValue))
        }
    }
    val rowHeight = 52.dp
    val summaryHeight = 32.dp
    Row(
        modifier
            .fillMaxWidth()
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ),
    ) {
        Column(
            Modifier
                .width(leftWidth)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HABIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.habits.forEach { summary ->
                val category = state.categories.firstOrNull { it.id == summary.habit.categoryId }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        summary.habit.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category?.name ?: "Uncategorized",
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor(category, state.categories),
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
                    SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 3.dp))
                }
            }
            SummaryLabel("Completed", summaryHeight)
            SummaryLabel("Not completed", summaryHeight)
            SummaryLabel("Completion %", summaryHeight)
        }
        Column(Modifier.weight(1f).horizontalScroll(scrollState)) {
            Row(Modifier.height(40.dp)) {
                dates.forEach { date -> DateHeader(date, date == state.today, cellWidth) }
            }
            state.habits.forEach { summary ->
                Row(Modifier.height(rowHeight)) {
                    dates.forEach { date ->
                        CompletionCell(
                            completed = summary.habit.id to date in state.dailyCompletionKeys,
                            currentDay = date == state.today,
                            enabled = summary.habit.isActiveOn(date),
                            width = cellWidth,
                            height = rowHeight,
                            onClick = { viewModel.toggleDaily(summary.habit.id, date) },
                        )
                    }
                }
            }
            SummaryRow(state.daily.map { it.completed.toString() }.reversed(), cellWidth, summaryHeight)
            SummaryRow(state.daily.map { it.notCompleted.toString() }.reversed(), cellWidth, summaryHeight)
            SummaryRow(state.daily.map { it.percentage.percentLabel() }.reversed(), cellWidth, summaryHeight)
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, current: Boolean, width: Dp) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (current) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.weekdayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            date.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CompletionCell(
    completed: Boolean,
    currentDay: Boolean,
    enabled: Boolean = true,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    currentDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
            )
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (completed && enabled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        when {
                            completed && enabled -> MaterialTheme.colorScheme.primaryContainer
                            enabled -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    RoundedCornerShape(2.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed && enabled) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryLabel(text: String, height: Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryRow(values: List<String>, width: Dp, height: Dp) {
    Row(Modifier.height(height)) {
        values.forEach { value ->
            Box(
                Modifier.width(width).fillMaxHeight().border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
                contentAlignment = Alignment.Center,
            ) {
                Text(value, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun OverviewStrip(state: MonthUiState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
        SectionLabel("Monthly overview")
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricPanel("Daily progress", state.monthlyProgress, Modifier.weight(1f))
            MetricPanel("Weekly progress", state.weeklyProgress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricPanel(label: String, progress: ProgressSummary, modifier: Modifier = Modifier) {
    Column(
        modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)).padding(12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (progress.hasGoal) progress.percentage.percentLabel() else "—",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text("${progress.completed} / ${progress.goal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SubtleProgress(progress.percentage, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun CategorySection(categories: List<CategorySummary>) {
    if (categories.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel("Categories")
        categories.forEach { summary ->
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.category.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${summary.completed}/${summary.goal}  ·  ${summary.remaining} left  ·  ${summary.percentage.percentLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun WeeklySection(
    state: MonthUiState,
    viewModel: MonthViewModel,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    Column(modifier.padding(top = 28.dp, bottom = 16.dp)) {
        SectionLabel("Weekly habits")
        if (state.weeklyHabits.isEmpty()) {
            Text(
                "No weekly habits configured.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
            return@Column
        }
        val nameWidth = 168.dp
        val weekWidth = 72.dp
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
        ) {
            Column(Modifier.width(nameWidth)) {
                Box(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text("HABIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.weeklyHabits.forEach { habit ->
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(habit.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SummaryLabel("Completed", 34.dp)
                SummaryLabel("Goal", 34.dp)
                SummaryLabel("Completion %", 34.dp)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                state.weeklyBlocks.reversed().forEach { block ->
                    Column(Modifier.width(weekWidth)) {
                        Row(
                            Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DonutProgress(block.percentage, Modifier.size(20.dp))
                            Text("W${block.index + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                        state.weeklyHabits.forEach { habit ->
                            CompletionCell(
                                completed = habit.id to block.weekStartDate in state.weeklyCompletionKeys,
                                currentDay = state.today in state.selectedMonth.datesForWeek(block.index),
                                enabled = habit.isActiveOn(block.weekStartDate),
                                width = weekWidth,
                                height = 46.dp,
                                onClick = { viewModel.toggleWeekly(habit.id, block.weekStartDate) },
                            )
                        }
                        SummaryValue(block.completed.toString(), 34.dp)
                        SummaryValue(block.goal.toString(), 34.dp)
                        SummaryValue(block.percentage.percentLabel(), 34.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutProgress(progress: Double, modifier: Modifier = Modifier) {
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

@Composable
private fun SummaryValue(value: String, height: Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MonthSidebar(state: MonthUiState, onManage: () -> Unit) {
    Column(
        Modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .padding(16.dp),
    ) {
        Text("◉ Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(36.dp))
        SidebarNavItem("▣  Tracker", selected = true, onClick = {})
        SidebarNavItem("⚙  Manage", selected = false, onClick = onManage)
        Spacer(Modifier.height(36.dp))
        SectionLabel("Monthly completion")
        Text(
            if (state.monthlyProgress.hasGoal) state.monthlyProgress.percentage.percentLabel() else "—",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "${state.monthlyProgress.completed} / ${state.monthlyProgress.goal}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SubtleProgress(state.monthlyProgress.percentage, Modifier.fillMaxWidth().padding(top = 10.dp))
        val sidebarCategories = state.categorySummaries.filter { it.goal > 0 }.take(6)
        if (sidebarCategories.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Categories")
            sidebarCategories.forEach { summary ->
                Column(Modifier.padding(top = 12.dp)) {
                    Row {
                        Text(summary.category.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(summary.percentage.percentLabel(), style = MaterialTheme.typography.labelSmall)
                    }
                    SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("Weekly progress")
        Text(
            "${state.weeklyProgress.completed} / ${state.weeklyProgress.goal}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        SubtleProgress(state.weeklyProgress.percentage, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
private fun SidebarNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun SubtleProgress(progress: Double, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
    }
}

@Composable
internal fun BottomNavigation(
    selectedTracker: Boolean,
    onTracker: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
    ) {
        BottomNavItem("▣", "Tracker", selectedTracker, Modifier.weight(1f), onTracker)
        BottomNavItem("⚙", "Manage", !selectedTracker, Modifier.weight(1f), onManage)
    }
}

@Composable
private fun BottomNavItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 19.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
