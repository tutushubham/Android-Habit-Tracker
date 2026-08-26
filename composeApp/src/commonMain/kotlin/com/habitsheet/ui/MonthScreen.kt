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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.calculation.CategorySummary
import com.habitsheet.domain.calculation.ProgressSummary
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.presentation.MonthViewModel
import kotlinx.datetime.LocalDate

@Composable
fun MonthScreen(
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    shareService: ShareService,
) {
    val state by viewModel.state.collectAsState()
    var showSharePreview by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val targetPositions = remember { mutableStateMapOf<String, Rect>() }

    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (state.todayMode) {
                TodayModeScreen(state, viewModel, onSettings, onPosition = { tag, rect -> targetPositions[tag] = rect })
            } else if (maxWidth >= 840.dp) {
                TabletMonthScreen(state, viewModel, onManage, onSettings, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            } else {
                PhoneMonthScreen(state, viewModel, onManage, onSettings, onShare = { showSharePreview = true }, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            }

            if (showSharePreview) {
                SharePreviewScreen(
                    summary = state.todaySummary,
                    onShare = {
                        shareService.shareDailySummary(state.todaySummary)
                        showSharePreview = false
                    },
                    onClose = { showSharePreview = false }
                )
            }
            
            if (showMonthPicker) {
                MonthYearPickerDialog(
                    current = state.selectedMonth,
                    onDismiss = { showMonthPicker = false },
                    onSelect = {
                        viewModel.selectMonth(it)
                        showMonthPicker = false
                    }
                )
            }
            
            if (state.onboardingVisible) {
                TutorialOverlay(
                    steps = listOf(
                        TutorialStep("Set up your habits", "Add your daily and weekly habits, choose a category, and set your goals.", "manage"),
                        TutorialStep("Track your day", "Tap a checkbox when you complete a habit. Your daily progress updates automatically.", "daily"),
                        TutorialStep("Don't forget weekly habits", "Track habits that you want to complete once or more each week.", "weekly"),
                        TutorialStep("See how you're doing", "Your daily, weekly, and category progress is calculated automatically.", "summary"),
                        TutorialStep("Share your day", "Create a clean summary of what you completed and what is still left today.", "share", "Get Started")
                    ),
                    targetPositions = targetPositions,
                    onComplete = viewModel::completeOnboarding,
                    onSkip = viewModel::completeOnboarding
                )
            }

            state.error?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text("OK", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }
}

@Composable
private fun PhoneMonthScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit,
) {
    Scaffold(
        topBar = { MonthTopBar(state, viewModel, compact = true, onShare = onShare, onSettings = onSettings, onPosition = onPosition, onShowPicker = onShowPicker) },
        bottomBar = { BottomNavigation(selectedTracker = true, onTracker = {}, onManage = onManage, onPosition = onPosition) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            MonthSummaryStrip(state)
            DailyHabitGrid(
                state = state,
                viewModel = viewModel,
                leftWidth = 136.dp,
                cellWidth = 40.dp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .tutorialTarget("daily", onPosition)
            )
            WeeklySection(
                state = state, 
                viewModel = viewModel, 
                modifier = Modifier
                    .padding(top = 24.dp)
                    .tutorialTarget("weekly", onPosition)
            )
            OverviewStrip(state, Modifier.tutorialTarget("summary", onPosition))
            CategorySection(state.categorySummaries)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TabletMonthScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit,
) {
    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        MonthSidebar(state, onManage, onSettings, onPosition)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MonthTopBar(state, viewModel, compact = false, onSettings = onSettings, onPosition = onPosition, onShowPicker = onShowPicker)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                MonthSummaryStrip(state, Modifier.padding(horizontal = 32.dp, vertical = 24.dp))
                if (state.habits.isEmpty()) {
                    EmptyHabits(onManage)
                } else {
                    DailyHabitGrid(
                        state = state,
                        viewModel = viewModel,
                        leftWidth = 290.dp,
                        cellWidth = 34.dp,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .tutorialTarget("daily", onPosition)
                    )
                }
                WeeklySection(
                    state = state, 
                    viewModel = viewModel, 
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .tutorialTarget("weekly", onPosition)
                )
                OverviewStrip(state, Modifier.tutorialTarget("summary", onPosition))
            }
        }
    }
}

@Composable
private fun MonthTopBar(
    state: MonthUiState,
    viewModel: MonthViewModel,
    compact: Boolean,
    onShare: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit = {},
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(if (compact) 64.dp else 88.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (compact) 8.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            IconButton(onClick = onSettings) {
                Text("⚙", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(
            onClick = viewModel::previousMonth,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Text(
                "‹", 
                fontSize = 32.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Previous Month" }
            )
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onShowPicker)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Select month and year. Currently ${state.selectedMonth.monthName()} ${state.selectedMonth.year}"
                }, 
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = state.selectedMonth.yearLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.selectedMonth.monthName(),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(
            onClick = viewModel::nextMonth,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Text(
                "›", 
                fontSize = 32.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Next Month" }
            )
        }
        
        Row(Modifier.padding(start = 8.dp)) {
            val modeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.todayMode) Color.Transparent else modeColor)
                    .clickable { viewModel.setTodayMode(false) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Month", style = MaterialTheme.typography.labelLarge, color = if (state.todayMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.todayMode) modeColor else Color.Transparent)
                    .clickable { viewModel.setTodayMode(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = if (state.todayMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!compact) {
            TextButton(
                onClick = onShare,
                modifier = Modifier
                    .padding(start = 16.dp, end = 4.dp)
                    .tutorialTarget("share", onPosition)
            ) {
                Text("Share", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(
                onClick = viewModel::currentMonth,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyHabits(onManage: () -> Unit) {
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text("Manage Habits")
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
    val density = LocalDensity.current
    
    LaunchedEffect(state.selectedMonth, state.today, scrollState.maxValue, state.scrollToTodayTrigger) {
        if (state.today in dates) {
            val index = dates.indexOf(state.today)
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val target = ((index - 1).coerceAtLeast(0) * cellWidthPx).toInt()
            
            // Do not scroll if already reasonably visible
            val currentScroll = scrollState.value
            val isVisible = currentScroll <= target && currentScroll >= target - (cellWidthPx * 3)
            
            if (state.scrollToTodayTrigger > 0 || (scrollState.value == 0 && !isVisible)) {
                 scrollState.animateScrollTo(target.coerceAtMost(scrollState.maxValue))
            }
        }
    }
    val rowHeight = 60.dp
    val headerHeight = 44.dp
    val summaryHeight = 36.dp
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
            Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("HABIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.habits.forEach { summary ->
                val category = state.categories.firstOrNull { it.id == summary.habit.categoryId }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .drawBehind {
                            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        summary.habit.name,
                        style = MaterialTheme.typography.bodyLarge,
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
                    SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            SummaryLabel("DONE", summaryHeight)
            SummaryLabel("MISS", summaryHeight)
            SummaryLabel("%", summaryHeight)
        }
        Column(Modifier.weight(1f).horizontalScroll(scrollState)) {
            Row(Modifier.height(headerHeight)) {
                dates.forEach { date -> DateHeader(date, date == state.today, cellWidth) }
            }
            state.habits.forEach { summary ->
                Row(
                    Modifier
                        .height(rowHeight)
                        .drawBehind {
                            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        }
                ) {
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
    val indicatorColor = MaterialTheme.colorScheme.primary
    val dateDescription = if (current) "Today, ${date.dayOfWeek.name} ${date.day}" else "${date.dayOfWeek.name} ${date.day}"
    
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
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
            modifier = if (current) Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
            else Modifier.padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    val haptic = LocalHapticFeedback.current
    val statusDescription = when {
        !enabled -> "Locked"
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
                stateDescription = statusDescription
            }
            .clickable(
                enabled = enabled,
                onClickLabel = if (completed) "Mark incomplete" else "Mark complete"
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
                        if (completed && enabled) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
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
private fun SummaryLabel(text: String, height: Dp) {
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

@Composable
private fun OverviewStrip(state: MonthUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 32.dp)) {
        SectionLabel("Summary", Modifier.padding(bottom = 16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricPanel("Daily Avg", state.monthlyProgress, Modifier.weight(1f))
            MetricPanel("Weekly Avg", state.weeklyProgress, Modifier.weight(1f))
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
                if (progress.hasGoal) progress.percentage.percentLabel() else "0%",
                style = MaterialTheme.typography.headlineMedium,
                color = if (progress.hasGoal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${progress.completed}/${progress.goal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        SubtleProgress(progress.percentage, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun CategorySection(categories: List<CategorySummary>) {
    if (categories.isEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            SectionLabel("Categories", Modifier.padding(bottom = 12.dp))
            Text(
                "Assign habits to categories to see progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel("Categories", Modifier.padding(bottom = 12.dp))
        categories.forEach { summary ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        summary.percentage.percentLabel(),
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
private fun WeeklySection(
    state: MonthUiState,
    viewModel: MonthViewModel,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    Column(modifier) {
        SectionLabel("Weekly Habits", Modifier.padding(bottom = 16.dp))
        if (state.weeklyHabits.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No weekly habits yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Add habits you want to track each week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
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
                    }
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text("HABIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                SummaryLabel("DONE", 36.dp)
                SummaryLabel("GOAL", 36.dp)
                SummaryLabel("%", 36.dp)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                state.weeklyBlocks.reversed().forEach { block ->
                    Column(Modifier.width(weekWidth)) {
                        Row(
                            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DonutProgress(block.percentage, Modifier.size(20.dp))
                            Text("W${block.index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.weeklyHabits.forEach { habit ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .drawBehind {
                                        drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CompletionCell(
                                    completed = habit.id to block.weekStartDate in state.weeklyCompletionKeys,
                                    currentDay = state.today in state.selectedMonth.datesForWeek(block.index),
                                    enabled = habit.isActiveOn(block.weekStartDate),
                                    width = weekWidth,
                                    height = 46.dp,
                                    onClick = { viewModel.toggleWeekly(habit.id, block.weekStartDate) },
                                )
                            }
                        }
                        SummaryValue(block.completed.toString(), 36.dp)
                        SummaryValue(block.goal.toString(), 36.dp)
                        SummaryValue(block.percentage.percentLabel(), 36.dp)
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
private fun MonthSummaryStrip(state: MonthUiState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SummaryItem("Daily", state.monthlyProgress.percentage.percentLabel())
        SummaryItem("Weekly", state.weeklyProgress.percentage.percentLabel())
        val onTarget = state.categorySummaries.count { it.percentage >= 0.8 }
        SummaryItem("Categories", "$onTarget/${state.categorySummaries.size} on track")
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
private fun TodayModeScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onSettings: () -> Unit,
    onPosition: (String, Rect) -> Unit
) {
    Scaffold(
        topBar = { MonthTopBar(state, viewModel, compact = true, onSettings = onSettings, onPosition = onPosition) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                state.today.weekdayLabel().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${state.today.day} ${state.selectedMonth.monthName()}",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            val summary = state.todaySummary
            Text(
                "${summary.completedCount} / ${summary.totalCount} completed • ${summary.percentage.percentLabel()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            SubtleProgress(summary.percentage, Modifier.widthIn(max = 200.dp).fillMaxWidth().padding(top = 16.dp))

            if (summary.doneHabits.isNotEmpty()) {
                SectionLabel("DONE", Modifier.align(Alignment.Start).padding(top = 48.dp, bottom = 16.dp))
                summary.doneHabits.forEach { habit ->
                    TodayHabitRow(habit.name, completed = true, onClick = { viewModel.toggleDaily(habit.id, state.today) })
                }
            }

            if (summary.leftHabits.isNotEmpty()) {
                SectionLabel("LEFT", Modifier.align(Alignment.Start).padding(top = 32.dp, bottom = 16.dp))
                summary.leftHabits.forEach { habit ->
                    TodayHabitRow(habit.name, completed = false, onClick = { viewModel.toggleDaily(habit.id, state.today) })
                }
            }
            
            if (state.weeklyBlocks.isNotEmpty()) {
                SectionLabel("WEEKLY PROGRESS", Modifier.align(Alignment.Start).padding(top = 48.dp, bottom = 16.dp))
                MetricPanel("Weekly Avg", state.weeklyProgress, Modifier.fillMaxWidth())
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun TodayHabitRow(name: String, completed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
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
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MonthYearPickerDialog(
    current: MonthKey,
    onDismiss: () -> Unit,
    onSelect: (MonthKey) -> Unit
) {
    var year by remember { mutableStateOf(current.year) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { year-- }) { Text("‹", fontSize = 24.sp) }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { year++ }) { Text("›", fontSize = 24.sp) }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val months = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    months.forEachIndexed { index, name ->
                        val m = index + 1
                        val selected = current.year == year && current.month == m
                        FilterChip(
                            selected = selected,
                            onClick = { onSelect(MonthKey(year, m)) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MonthSidebar(
    state: MonthUiState,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(24.dp),
    ) {
        Text("◉ Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(48.dp))
        SidebarNavItem("▣  Tracker", selected = true, onClick = {})
        SidebarNavItem(
            "⚙  Manage", 
            selected = false, 
            onClick = onManage,
            modifier = Modifier.tutorialTarget("manage", onPosition)
        )
        SidebarNavItem("⛃  Settings", selected = false, onClick = onSettings)
        
        val sidebarCategories = state.categorySummaries.filter { it.goal > 0 }.take(6)
        if (sidebarCategories.isNotEmpty()) {
            Spacer(Modifier.height(48.dp))
            SectionLabel("Categories", Modifier.padding(bottom = 16.dp))
            sidebarCategories.forEach { summary ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Row {
                        Text(summary.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(summary.percentage.percentLabel(), style = MaterialTheme.typography.labelSmall)
                    }
                    SubtleProgress(summary.percentage, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SidebarNavItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
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
    val progressValue = progress.toFloat().coerceIn(0f, 1f)
    Box(
        modifier
            .height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp))
            .semantics {
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progressValue, 0f..1f)
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (progressValue > 0f) progressValue else 0.01f)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
internal fun BottomNavigation(
    selectedTracker: Boolean,
    onTracker: () -> Unit,
    onManage: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(dividerColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            },
    ) {
        BottomNavItem("▣", "Tracker", selectedTracker, Modifier.weight(1f), onTracker)
        BottomNavItem(
            "⚙", 
            "Manage", 
            !selectedTracker, 
            Modifier.weight(1f).tutorialTarget("manage", onPosition), 
            onManage
        )
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
