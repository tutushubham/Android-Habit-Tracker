package com.habitsheet.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.asString
import com.habitsheet.resources.*
import com.habitsheet.ui.BottomNavigation
import com.habitsheet.ui.SharePreviewScreen
import com.habitsheet.ui.ShareService
import com.habitsheet.ui.TutorialOverlay
import com.habitsheet.ui.TutorialStep
import com.habitsheet.ui.tutorialTarget
import org.jetbrains.compose.resources.stringResource

@Composable
fun MonthScreen(
    viewModel: MonthViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit = {},
    onPlan: () -> Unit = {},
    shareService: ShareService? = null,
    tabletLayout: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { viewModel.actions() }
    var showSharePreview by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val targetPositions = remember { mutableStateMapOf<String, Rect>() }

    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // iPad portrait is 834dp wide. Treat it (and similarly sized Android
            // tablets) as an expanded layout instead of stretching phone chrome.
            if (tabletLayout || maxWidth >= 720.dp) {
                TabletMonthScreen(state, actions, onManage, onSettings, onPlan, onShare = { showSharePreview = true }, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            } else {
                PhoneMonthScreen(state, actions, onManage, onSettings, onPlan, onShare = { showSharePreview = true }, onPosition = { tag, rect -> targetPositions[tag] = rect }, onShowPicker = { showMonthPicker = true })
            }

            if (showSharePreview && shareService != null) {
                SharePreviewScreen(state.todaySummary, onShare = {
                    shareService.shareDailySummary(state.todaySummary)
                    showSharePreview = false
                }, onClose = { showSharePreview = false })
            }

            if (state.onboardingVisible) {
                TutorialOverlay(
                    steps = listOf(
                        TutorialStep(stringResource(Res.string.tutorial_manage_title), stringResource(Res.string.tutorial_manage_text), "manage"),
                        TutorialStep(stringResource(Res.string.tutorial_plan_title), stringResource(Res.string.tutorial_plan_text), "today"),
                        TutorialStep(
                            stringResource(Res.string.tutorial_share_title),
                            stringResource(Res.string.tutorial_share_text),
                            "share",
                            stringResource(Res.string.tutorial_get_started),
                        ),
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
                    },
                )
            }

            state.error?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(Res.string.action_ok), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                ) {
                    Text(msg.asString())
                }
            }
        }
    }
}

@Composable
private fun PhoneMonthScreen(
    state: MonthUiState,
    actions: MonthActions,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onShare: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit,
) {
    Scaffold(
        topBar = { MonthTopBar(state, actions, compact = true, onShare = onShare, onSettings = onSettings, onPosition = onPosition, onShowPicker = onShowPicker) },
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
                    onPlan = onPlan,
                    onBackToToday = actions.currentDay,
                    onTogglePlanned = actions.togglePlanned,
                    onToggleWeekly = actions.toggleWeekly,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                MonthSummaryStrip(state, onCurrentMonth = actions.currentMonth)
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
                            onOpenDay = actions.openDay,
                            onTogglePlanned = actions.togglePlanned,
                            leftWidth = layout.leftWidth,
                            cellWidth = layout.cellWidth(state.selectedMonth.dates().size),
                        )
                    }
                }
                DailyWeeksSection(state.dailyWeeks)
                WeeklySection(
                    state = state,
                    onToggleWeekly = actions.toggleWeekly,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .tutorialTarget("weekly", onPosition),
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
    actions: MonthActions,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onShare: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MonthTopBar(state, actions, compact = false, onShare = onShare, onSettings = onSettings, onPosition = onPosition, onShowPicker = onShowPicker)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            if (state.todayMode) {
                TodayModeContent(
                    state = state,
                    onPlan = onPlan,
                    onBackToToday = actions.currentDay,
                    onTogglePlanned = actions.togglePlanned,
                    onToggleWeekly = actions.toggleWeekly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            } else {
                MonthSummaryStrip(state, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), actions.currentMonth)
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
                            onOpenDay = actions.openDay,
                            onTogglePlanned = actions.togglePlanned,
                            leftWidth = layout.leftWidth,
                            cellWidth = layout.cellWidth(state.selectedMonth.dates().size),
                        )
                    }
                }
                DailyWeeksSection(state.dailyWeeks, Modifier.padding(horizontal = 24.dp))
                WeeklySection(
                    state = state,
                    onToggleWeekly = actions.toggleWeekly,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .tutorialTarget("weekly", onPosition),
                )
                OverviewStrip(state, Modifier.tutorialTarget("summary", onPosition))
            }
        }
    }
}

private fun MonthViewModel.actions() = MonthActions(
    previousDay = ::previousDay,
    nextDay = ::nextDay,
    currentDay = ::currentDay,
    previousMonth = ::previousMonth,
    nextMonth = ::nextMonth,
    currentMonth = ::currentMonth,
    setTodayMode = ::setTodayMode,
    openDay = ::openDay,
    togglePlanned = ::togglePlanned,
    toggleWeekly = ::toggleWeekly,
)
