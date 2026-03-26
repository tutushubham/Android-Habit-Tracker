package com.tutushubham.pokidex.feature_today

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.service.SessionTimerHelper
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import com.tutushubham.pokidex.feature_today.components.EmptyCard
import com.tutushubham.pokidex.feature_today.components.FocusChips
import com.tutushubham.pokidex.feature_today.components.FocusOverrideSheet
import com.tutushubham.pokidex.feature_today.components.InsightSection
import com.tutushubham.pokidex.ui.components.CompletionView
import com.tutushubham.pokidex.ui.components.ShimmerLoadingView
import com.tutushubham.pokidex.ui.components.SyncErrorView
import com.tutushubham.pokidex.feature_today.components.OverloadSection
import com.tutushubham.pokidex.feature_today.components.ProgressSection
import com.tutushubham.pokidex.feature_today.components.SessionSection
import com.tutushubham.pokidex.feature_today.components.TodayHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToSession: (String) -> Unit,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToGoalDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenFocusSettings: ((Domain) -> Unit)? = null,
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToStructureSettings: () -> Unit = {},
    onNavigateToAddGoal: () -> Unit = {},
    onNavigateToRecommendation: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onEvent(TodayContract.TodayEvent.ScreenOpened)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TodayContract.TodayEffect.StartSessionTimer -> {
                    SessionTimerHelper.startTimer(context, effect.sessionId)
                    onNavigateToSession(effect.sessionId)
                }

                is TodayContract.TodayEffect.ResumeSessionTimer ->
                    SessionTimerHelper.resumeTimer(context, effect.sessionId, effect.elapsedMinutes)

                TodayContract.TodayEffect.StopSessionTimer ->
                    SessionTimerHelper.stopTimer(context)

                is TodayContract.TodayEffect.NavigateToSession ->
                    onNavigateToSession(effect.sessionId)

                is TodayContract.TodayEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> ShimmerLoadingView()
                state.error != null ->
                    SyncErrorView(
                        onRetry = { viewModel.onEvent(TodayContract.TodayEvent.Refresh) },
                        onDismiss = null
                    )

                else -> when (state.emptyState) {
                    TodayContract.TodayEmptyState.OnboardingRequired ->
                        EmptyCard(
                            title = "Let's set up your system",
                            message = "Complete onboarding to start planning.",
                            buttonText = "Start setup",
                            onClick = onNavigateToOnboarding
                        )

                    TodayContract.TodayEmptyState.NoStructure ->
                        EmptyCard(
                            title = "Design your day",
                            message = "You haven't defined your daily structure yet.",
                            buttonText = "Configure structure",
                            onClick = onNavigateToStructureSettings
                        )

                    TodayContract.TodayEmptyState.NoIntent ->
                        EmptyCard(
                            title = "What are you working toward?",
                            message = "Add at least one goal to generate sessions.",
                            buttonText = "Add goal",
                            onClick = onNavigateToAddGoal
                        )

                    TodayContract.TodayEmptyState.NoSessionsToday ->
                        EmptyCard(
                            title = "Your day is clear",
                            message = "No sessions scheduled today. Take a rest or adjust your setup.",
                            buttonText = "Refresh",
                            onClick = { viewModel.onEvent(TodayContract.TodayEvent.Refresh) }
                        )

                    TodayContract.TodayEmptyState.AllCompleted ->
                        CompletionView(onReviewInsights = onNavigateToInsights)

                    TodayContract.TodayEmptyState.None ->
                        TodayContent(
                            state = state,
                            onEvent = viewModel::onEvent,
                            onNavigateToInsights = onNavigateToInsights,
                            onNavigateToGoalDetail = onNavigateToGoalDetail,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToRecommendation = onNavigateToRecommendation
                        )
                }
            }
        }

        if (state.pendingOverrideDomain != null) {
            val domain = state.pendingOverrideDomain!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.onEvent(TodayContract.TodayEvent.CancelFocusOverride) },
                sheetState = sheetState
            ) {
                FocusOverrideSheet(
                    focuses = state.availableOverrideFocuses,
                    onSelect = { focus ->
                        viewModel.onEvent(
                            TodayContract.TodayEvent.OverrideFocusForToday(
                                domain = domain,
                                focusId = focus.id
                            )
                        )
                    },
                    onDismiss = { viewModel.onEvent(TodayContract.TodayEvent.CancelFocusOverride) },
                    onClearOverride = {
                        viewModel.onEvent(TodayContract.TodayEvent.ClearOverrideForToday(domain))
                    }
                )
            }
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayContract.TodayState,
    onEvent: (TodayContract.TodayEvent) -> Unit,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToGoalDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRecommendation: (String) -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val insights = remember(state, isDark) { TodayUiMapper.mapInsights(state, isDark) }
    val sessionModels = remember(state) { TodayUiMapper.mapSessions(state) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TodayHeader(
            date = state.date,
            onInsightsClick = onNavigateToInsights,
            onSettingsClick = onNavigateToSettings
        )

        Spacer(Modifier.height(AppSpacing.xs))

        FocusChips(
            focusMap = state.activeFocusByDomain,
            onChangeFocus = { domain ->
                onEvent(TodayContract.TodayEvent.RequestFocusOverride(domain))
            }
        )

        Spacer(Modifier.height(AppSpacing.lg))

        if (state.hasSevereOverload) {
            OverloadSection(
                overloadedIntentIds = state.overloadedIntentIds,
                maxSeverity = state.maxOverloadSeverity,
                progressList = state.progressList,
                onAdjustDeadlines = onNavigateToRecommendation
            )
            Spacer(Modifier.height(AppSpacing.xxl))
        }

        if (state.hasCriticalGoals && !state.hasSevereOverload) {
            SessionSection(
                sessions = sessionModels,
                activeSessionId = state.activeSessionId,
                elapsedMinutes = state.elapsedMinutes,
                onStart = { id -> onEvent(TodayContract.TodayEvent.StartSession(id)) },
                onSkip = { id, reason -> onEvent(TodayContract.TodayEvent.SkipSession(id, reason)) },
                onComplete = { id, minutes -> onEvent(TodayContract.TodayEvent.CompleteSession(id, minutes)) },
                onRestart = { id -> onEvent(TodayContract.TodayEvent.RestartSession(id)) }
            )
            Spacer(Modifier.height(AppSpacing.xxl))
        }

        InsightSection(
            insights = insights,
            onNavigateToRecommendation = onNavigateToRecommendation
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        ProgressSection(
            progressList = state.progressList,
            onGoalClick = onNavigateToGoalDetail
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        if (!state.hasCriticalGoals || state.hasSevereOverload) {
            SessionSection(
                sessions = sessionModels,
                activeSessionId = state.activeSessionId,
                elapsedMinutes = state.elapsedMinutes,
                onStart = { id -> onEvent(TodayContract.TodayEvent.StartSession(id)) },
                onSkip = { id, reason -> onEvent(TodayContract.TodayEvent.SkipSession(id, reason)) },
                onComplete = { id, minutes -> onEvent(TodayContract.TodayEvent.CompleteSession(id, minutes)) },
                onRestart = { id -> onEvent(TodayContract.TodayEvent.RestartSession(id)) }
            )
            Spacer(Modifier.height(AppSpacing.xxl))
        }

        if (!state.hasSevereOverload) {
            OverloadSection(
                overloadedIntentIds = state.overloadedIntentIds,
                maxSeverity = state.maxOverloadSeverity,
                progressList = state.progressList,
                onAdjustDeadlines = onNavigateToRecommendation
            )
        }

        Spacer(Modifier.height(AppSpacing.xxxl))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TodayContentPreview() {
    PokidexTheme {
        TodayContent(
            state = PreviewData.todayState(activeSessionId = "s2", elapsedMinutes = 15),
            onEvent = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TodayContentEmptyPreview() {
    PokidexTheme {
        EmptyCard(
            title = "What are you working toward?",
            message = "Add at least one goal to generate sessions.",
            buttonText = "Add goal",
            onClick = {}
        )
    }
}
