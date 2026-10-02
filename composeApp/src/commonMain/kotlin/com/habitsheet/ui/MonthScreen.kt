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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.presentation.MonthViewModel
import kotlinx.datetime.LocalDate

@Composable
fun MonthScreen(
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit = {},
    onPlan: () -> Unit = {},
    shareService: ShareService? = null,
    tabletLayout: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    var showSharePreview by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val targetPositions = remember { mutableStateMapOf<String, Rect>() }

    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // iPad portrait is 834dp wide. Treat it (and similarly sized Android
            // tablets) as an expanded layout instead of stretching phone chrome.
            if (tabletLayout || maxWidth >= 720.dp) {
                TabletMonthScreen(state, viewModel, onManage, onSettings, onPlan, onShare = { showSharePreview = true }, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            } else {
                PhoneMonthScreen(state, viewModel, onManage, onSettings, onPlan, onShare = { showSharePreview = true }, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            }

            if (showSharePreview && shareService != null) {
                SharePreviewScreen(state.todaySummary, onShare = { shareService.shareDailySummary(state.todaySummary); showSharePreview = false }, onClose = { showSharePreview = false })
            }

            if (state.onboardingVisible) {
                TutorialOverlay(
                    steps = listOf(
                        TutorialStep("Add your habits", "Open Habits to add daily and weekly habits and choose a category.", "manage"),
                        TutorialStep("Plan and track your day", "Open Plan to schedule sessions, then tap a habit to check it off. To plan from a laptop, link a Google Sheet in Settings.", "today"),
                        TutorialStep("Share your day", "Create a clean summary of what you completed and what is left today.", "share", "Get Started"),
                    ),
                    targetPositions = targetPositions,
                    onComplete = viewModel::completeOnboarding,
                    onSkip = viewModel::completeOnboarding,
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
    onPlan: () -> Unit,
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
            if (state.todayMode) {
                TodayModeContent(
                    state = state,
                    viewModel = viewModel,
                    onPlan = onPlan,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                MonthSummaryStrip(state, onCurrentMonth = viewModel::currentMonth)
                MonthGridLegend()
                if (state.habits.isEmpty()) {
                    EmptyHabits(onManage)
                } else {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .tutorialTarget("daily", onPosition),
                    ) {
                        val layout = monthGridLayout(maxWidth)
                        DailyHabitGrid(
                            state = state,
                            viewModel = viewModel,
                            leftWidth = layout.leftWidth,
                            cellWidth = layout.cellWidth(state.selectedMonth.dates().size),
                        )
                    }
                }
                DailyWeeksSection(state.dailyWeeks)
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
}

@Composable
private fun TabletMonthScreen(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onShare: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
            MonthTopBar(state, viewModel, compact = false, onShare = onShare, onSettings = onSettings, onPosition = onPosition, onShowPicker = onShowPicker)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                if (state.todayMode) {
                    TodayModeContent(
                        state = state,
                        viewModel = viewModel,
                        onPlan = onPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                } else {
                    MonthSummaryStrip(state, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), viewModel::currentMonth)
                    MonthGridLegend(Modifier.padding(horizontal = 24.dp))
                    if (state.habits.isEmpty()) {
                    EmptyHabits(onManage)
                    } else {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .tutorialTarget("daily", onPosition),
                    ) {
                        val layout = monthGridLayout(maxWidth)
                        DailyHabitGrid(
                            state = state,
                            viewModel = viewModel,
                            leftWidth = layout.leftWidth,
                            cellWidth = layout.cellWidth(state.selectedMonth.dates().size),
                        )
                    }
                    }
                    DailyWeeksSection(state.dailyWeeks, Modifier.padding(horizontal = 24.dp))
                    WeeklySection(
                    state = state, 
                    viewModel = viewModel, 
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
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
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .height(if (compact) 126.dp else 112.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (compact) 12.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Settings" },
                ) {
                    HabitIcon(
                        HabitIconGlyph.Settings,
                        Modifier.size(22.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (state.todayMode) Modifier
                        else Modifier
                            .clickable(onClick = onShowPicker)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "Select month and year. Currently ${state.selectedMonth.monthName()} ${state.selectedMonth.year}"
                            }
                    ),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = if (state.todayMode) "Day plan" else "${state.selectedMonth.monthName()} ${state.selectedMonth.year} ▾",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.todayMode) {
                TextButton(
                    onClick = onShare,
                    modifier = Modifier
                        .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                        .tutorialTarget("share", onPosition),
                ) {
                    Text("Share day", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            DateNavigationButton(
                previous = true,
                dayMode = state.todayMode,
                compact = compact,
                onClick = if (state.todayMode) viewModel::previousDay else viewModel::previousMonth,
            )
            ModeSwitcher(
                todayMode = state.todayMode,
                viewModel = viewModel,
                compact = compact,
            )
            DateNavigationButton(
                previous = false,
                dayMode = state.todayMode,
                compact = compact,
                onClick = if (state.todayMode) viewModel::nextDay else viewModel::nextMonth,
            )
        }
    }
}

@Composable
private fun DateNavigationButton(previous: Boolean, dayMode: Boolean, compact: Boolean, onClick: () -> Unit) {
    val direction = if (previous) "Previous" else "Next"
    val unit = if (dayMode) "day" else "month"
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .semantics { contentDescription = "$direction $unit" },
    ) {
        Text(if (compact) {
            if (previous) "‹ $unit" else "$unit ›"
        } else {
            if (previous) "‹ $direction $unit" else "$direction $unit ›"
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModeSwitcher(
    todayMode: Boolean,
    viewModel: MonthViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(40.dp)
            .width(if (compact) 144.dp else 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(3.dp),
    ) {
        ModeSwitcherItem(
            label = "Day",
            selected = todayMode,
            onClick = { viewModel.setTodayMode(true) },
            modifier = Modifier.weight(1f),
        )
        ModeSwitcherItem(
            label = "Month",
            selected = !todayMode,
            onClick = { viewModel.setTodayMode(false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSwitcherItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    verticalArrangement = Arrangement.Center
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
                dates.forEach { date -> DateHeader(date, date == state.today, cellWidth) { viewModel.openDay(date) } }
            }
            gridHabits.forEach { summary ->
                Row(
                    Modifier
                        .height(rowHeight)
                        .drawBehind {
                            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                        }
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
                                onClick = { viewModel.togglePlanned(planned.single().id, date) },
                            )
                            else -> Box(
                                Modifier.width(cellWidth).height(rowHeight)
                                    .clickable(onClickLabel = "Open ${planned.size} planned sessions") { viewModel.openDay(date) }
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

private data class MonthGridLayout(val leftWidth: Dp, val availableForDays: Dp) {
    fun cellWidth(dayCount: Int): Dp {
        if (dayCount <= 0) return 36.dp
        val fitted = availableForDays / dayCount
        return fitted.coerceIn(28.dp, 44.dp)
    }
}

private fun monthGridLayout(maxWidth: Dp): MonthGridLayout {
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
private fun MonthGridLegend(modifier: Modifier = Modifier) {
    Text(
        "□ planned incomplete · ✓ complete · · not planned · 1/2 multiple (tap day)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun CompletionCell(
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
            MetricPanel("Monthly daily", state.monthlyProgress, Modifier.weight(1f))
            MetricPanel("Weekly habits", state.weeklyProgress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DailyWeeksSection(
    weeks: List<com.habitsheet.domain.calculation.DailyWeekSummary>,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
) {
    if (weeks.isEmpty()) return
    Column(modifier.padding(top = 8.dp)) {
        SectionLabel("Daily week totals", Modifier.padding(bottom = 12.dp))
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
                        Text("W${week.index + 1}", style = MaterialTheme.typography.labelSmall)
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
                        "${summary.completed}/${summary.goal} · ${summary.remaining} left · ${summary.percentage.percentLabel()}",
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
                SummaryLabel("LEFT", 36.dp)
                SummaryLabel("GOAL", 36.dp)
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
                                    enabled = state.selectedMonth.datesForWeek(block.index).any(habit::isActiveOn),
                                    description = "${habit.name}, week ${block.index + 1} of ${state.selectedMonth.monthName()}",
                                    width = weekWidth,
                                    height = 46.dp,
                                    onClick = { viewModel.toggleWeekly(habit.id, block.weekStartDate) },
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
private fun MonthSummaryStrip(
    state: MonthUiState,
    modifier: Modifier = Modifier,
    onCurrentMonth: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SummaryItem("Daily", state.monthlyProgress.percentage.percentLabel())
        SummaryItem("Weekly", state.weeklyProgress.percentage.percentLabel())
        Spacer(Modifier.weight(1f))
        if (onCurrentMonth != null && state.selectedMonth != MonthKey.from(state.today)) {
            TextButton(onClick = onCurrentMonth) {
                Text("Current month", color = MaterialTheme.colorScheme.primary)
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
private fun TodayModeContent(
    state: MonthUiState,
    viewModel: MonthViewModel,
    onPlan: () -> Unit,
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
                TextButton(onClick = viewModel::currentDay, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
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
            val toggle = { id: String -> viewModel.togglePlanned(id, state.selectedDay) }
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
                        onClick = { viewModel.toggleWeekly(habit.id, currentWeekStart) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class DaySection(val openLabel: String, val doneLabel: String) {
    Workout("WORKOUTS", "DONE · WORKOUTS"),
    Study("STUDY", "DONE · STUDY"),
    Habit("HABITS", "DONE · HABITS"),
    Avoid("AVOID TODAY · CHECK AT NIGHT", "KEPT TODAY"),
}

private fun daySection(habit: com.habitsheet.domain.calculation.DailyShareHabit): DaySection {
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
                IconButton(onClick = { year-- }, modifier = Modifier.semantics { contentDescription = "Previous year" }) {
                    HabitIcon(HabitIconGlyph.Back, Modifier.size(20.dp))
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { year++ }, modifier = Modifier.semantics { contentDescription = "Next year" }) {
                    HabitIcon(HabitIconGlyph.Forward, Modifier.size(20.dp))
                }
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
                .fillMaxWidth(progressValue)
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
        BottomNavItem(HabitIconGlyph.Tracker, "Tracker", selectedTracker, Modifier.weight(1f), onTracker)
        BottomNavItem(
            HabitIconGlyph.Manage,
            "Habits",
            !selectedTracker, 
            Modifier.weight(1f).tutorialTarget("manage", onPosition), 
            onManage
        )
    }
}

@Composable
private fun BottomNavItem(icon: HabitIconGlyph, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HabitIcon(
            icon,
            Modifier.size(21.dp),
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
