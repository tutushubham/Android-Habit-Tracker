package com.tutushubham.pokidex.feature_today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.feature_today.components.EmptyCard
import com.tutushubham.pokidex.feature_today.components.PerformanceIndicator
import com.tutushubham.pokidex.feature_today.components.SessionContext
import com.tutushubham.pokidex.feature_today.components.SessionControls
import com.tutushubham.pokidex.feature_today.components.SessionTimer
import com.tutushubham.pokidex.feature_today.components.SuggestionCard
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun ActiveSessionScreen(
    state: TodayContract.TodayState,
    onEvent: (TodayContract.TodayEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeModel = remember(state) { TodayUiMapper.mapActiveSession(state) }

    if (activeModel == null) {
        Box(
            modifier = modifier.fillMaxSize().systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            EmptyCard(
                title = "No active session",
                message = "Start a session from the Today screen to begin tracking.",
                buttonText = "Go back",
                onClick = onBack
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = greetingText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        SessionContext(model = activeModel)

        Spacer(Modifier.height(AppSpacing.xxxl))

        SessionTimer(
            remainingMinutes = activeModel.remainingMinutes,
            progressFraction = activeModel.progressFraction,
            modifier = Modifier.padding(horizontal = AppSpacing.xxxl + AppSpacing.lg)
        )

        Spacer(Modifier.height(AppSpacing.xxxl + AppSpacing.sm))

        PerformanceIndicator(model = activeModel)

        Spacer(Modifier.height(AppSpacing.xxxl + AppSpacing.sm))

        SessionControls(
            onSkip = {
                onEvent(
                    TodayContract.TodayEvent.SkipSession(
                        activeModel.sessionId,
                        SkipReason.LOW_ENERGY
                    )
                )
                onBack()
            },
            onPauseResume = {
                if (state.isPaused) {
                    onEvent(TodayContract.TodayEvent.ResumeSession(activeModel.sessionId))
                } else {
                    onEvent(TodayContract.TodayEvent.PauseSession(activeModel.sessionId))
                }
            },
            onFinish = {
                onEvent(
                    TodayContract.TodayEvent.CompleteSession(
                        activeModel.sessionId,
                        activeModel.elapsedMinutes
                    )
                )
                onBack()
            },
            isPaused = state.isPaused,
            modifier = Modifier.padding(horizontal = AppSpacing.xxl)
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        activeModel.suggestion?.let { suggestion ->
            SuggestionCard(suggestion = suggestion)
        }

        Spacer(Modifier.height(AppSpacing.xxxl))
    }
}

private fun greetingText(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveSessionOnTrackPreview() {
    PokidexTheme {
        ActiveSessionScreen(
            state = PreviewData.todayState(activeSessionId = "s2", elapsedMinutes = 20),
            onEvent = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveSessionCriticalPreview() {
    PokidexTheme {
        ActiveSessionScreen(
            state = PreviewData.todayState(activeSessionId = "s1", elapsedMinutes = 55),
            onEvent = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveSessionEmptyPreview() {
    PokidexTheme {
        ActiveSessionScreen(
            state = PreviewData.todayState(),
            onEvent = {},
            onBack = {}
        )
    }
}
