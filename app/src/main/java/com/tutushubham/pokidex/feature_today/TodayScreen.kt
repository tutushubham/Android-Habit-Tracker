package com.tutushubham.pokidex.feature_today

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.service.SessionTimerHelper
import java.time.Instant
import java.time.LocalDate

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToSession: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Handle one-off effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TodayContract.TodayEffect.StartSessionTimer -> {
                    SessionTimerHelper.startTimer(
                        context,
                        effect.sessionId
                    )
                }

                TodayContract.TodayEffect.StopSessionTimer -> {
                    SessionTimerHelper.stopTimer(context)
                }

                is TodayContract.TodayEffect.NavigateToSession -> {
                    onNavigateToSession(effect.sessionId)
                }

                is TodayContract.TodayEffect.ShowMessage -> {
                    Toast
                        .makeText(context, effect.message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun TodayContent(
    state: TodayContract.TodayState,
    onEvent: (TodayContract.TodayEvent) -> Unit
) {
    when {
        state.isLoading -> {
            LoadingView()
        }

        state.sessions.isEmpty() -> {
            EmptyStateView(
                onRefresh = {
                    onEvent(TodayContract.TodayEvent.Refresh)
                }
            )
        }

        else -> {
            SessionsList(
                state = state,
                onEvent = onEvent
            )
        }
    }
}

@Composable
fun SessionsList(
    state: TodayContract.TodayState,
    onEvent: (TodayContract.TodayEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        items(state.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                isActive = session.id == state.activeSessionId,
                elapsedMinutes = state.elapsedMinutes,
                onStart = {
                    onEvent(
                        TodayContract.TodayEvent.StartSession(session.id)
                    )
                },
                onSkip = { reason ->
                    onEvent(
                        TodayContract.TodayEvent.SkipSession(
                            session.id,
                            reason
                        )
                    )
                },
                onComplete = {
                    onEvent(
                        TodayContract.TodayEvent.CompleteSession(
                            session.id,
                            state.elapsedMinutes
                        )
                    )
                }
            )
        }
    }
}

@Composable
fun SessionCard(
    session: Session,
    isActive: Boolean,
    elapsedMinutes: Int,
    onStart: () -> Unit,
    onSkip: (SkipReason) -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = session.domain.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${session.plannedMinutes} min planned",
                style = MaterialTheme.typography.bodySmall
            )

            if (isActive) {
                Text(
                    text = "$elapsedMinutes min elapsed",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            when (session.status) {
                SessionStatus.PLANNED -> {
                    Button(onClick = onStart) {
                        Text("Start")
                    }
                }

                SessionStatus.IN_PROGRESS -> {
                    Row {
                        Button(onClick = onComplete) {
                            Text("Complete")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onSkip(SkipReason.LOW_ENERGY) }
                        ) {
                            Text("Skip")
                        }
                    }
                }

                SessionStatus.COMPLETED -> {
                    Text("Completed ✅")
                }

                SessionStatus.SKIPPED -> {
                    Text("Skipped ❌")
                }
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyStateView(
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sessions planned for today")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh) {
                Text("Refresh")
            }
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
private fun LoadingViewPreview() {
    LoadingView()
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateViewPreview() {
    EmptyStateView(onRefresh = {})
}

@Preview(showBackground = true)
@Composable
private fun SessionCardPlannedPreview() {
    val sampleSession = Session(
        id = "session-1",
        intentId = "intent-1",
        domain = Domain.FITNESS,
        date = LocalDate.now(),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = null,
        status = SessionStatus.PLANNED,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )
    
    SessionCard(
        session = sampleSession,
        isActive = false,
        elapsedMinutes = 0,
        onStart = {},
        onSkip = {},
        onComplete = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionCardInProgressPreview() {
    val sampleSession = Session(
        id = "session-2",
        intentId = "intent-2",
        domain = Domain.STUDIES,
        date = LocalDate.now(),
        block = DayBlock.DAY,
        plannedMinutes = 45,
        actualMinutes = null,
        status = SessionStatus.IN_PROGRESS,
        skipReason = null,
        startedAt = Instant.now(),
        endedAt = null
    )
    
    SessionCard(
        session = sampleSession,
        isActive = true,
        elapsedMinutes = 15,
        onStart = {},
        onSkip = {},
        onComplete = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionCardCompletedPreview() {
    val sampleSession = Session(
        id = "session-3",
        intentId = "intent-3",
        domain = Domain.WORK,
        date = LocalDate.now(),
        block = DayBlock.EVENING,
        plannedMinutes = 60,
        actualMinutes = 55,
        status = SessionStatus.COMPLETED,
        skipReason = null,
        startedAt = Instant.now().minusSeconds(3600),
        endedAt = Instant.now()
    )
    
    SessionCard(
        session = sampleSession,
        isActive = false,
        elapsedMinutes = 0,
        onStart = {},
        onSkip = {},
        onComplete = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionCardSkippedPreview() {
    val sampleSession = Session(
        id = "session-4",
        intentId = "intent-4",
        domain = Domain.HOBBY,
        date = LocalDate.now(),
        block = DayBlock.NIGHT,
        plannedMinutes = 20,
        actualMinutes = null,
        status = SessionStatus.SKIPPED,
        skipReason = SkipReason.LOW_ENERGY,
        startedAt = null,
        endedAt = null
    )
    
    SessionCard(
        session = sampleSession,
        isActive = false,
        elapsedMinutes = 0,
        onStart = {},
        onSkip = {},
        onComplete = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayContentWithSessionsPreview() {
    val sampleSessions = listOf(
        Session(
            id = "session-1",
            intentId = "intent-1",
            domain = Domain.FITNESS,
            date = LocalDate.now(),
            block = DayBlock.MORNING,
            plannedMinutes = 30,
            actualMinutes = null,
            status = SessionStatus.PLANNED,
            skipReason = null,
            startedAt = null,
            endedAt = null
        ),
        Session(
            id = "session-2",
            intentId = "intent-2",
            domain = Domain.STUDIES,
            date = LocalDate.now(),
            block = DayBlock.DAY,
            plannedMinutes = 45,
            actualMinutes = null,
            status = SessionStatus.IN_PROGRESS,
            skipReason = null,
            startedAt = Instant.now(),
            endedAt = null
        ),
        Session(
            id = "session-3",
            intentId = "intent-3",
            domain = Domain.WORK,
            date = LocalDate.now(),
            block = DayBlock.EVENING,
            plannedMinutes = 60,
            actualMinutes = 55,
            status = SessionStatus.COMPLETED,
            skipReason = null,
            startedAt = Instant.now().minusSeconds(3600),
            endedAt = Instant.now()
        )
    )
    
    val sampleState = TodayContract.TodayState(
        isLoading = false,
        date = LocalDate.now(),
        sessions = sampleSessions,
        activeSessionId = "session-2",
        elapsedMinutes = 15,
        error = null
    )
    
    TodayContent(
        state = sampleState,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayContentLoadingPreview() {
    val sampleState = TodayContract.TodayState(
        isLoading = true,
        date = LocalDate.now(),
        sessions = emptyList(),
        activeSessionId = null,
        elapsedMinutes = 0,
        error = null
    )
    
    TodayContent(
        state = sampleState,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayContentEmptyPreview() {
    val sampleState = TodayContract.TodayState(
        isLoading = false,
        date = LocalDate.now(),
        sessions = emptyList(),
        activeSessionId = null,
        elapsedMinutes = 0,
        error = null
    )
    
    TodayContent(
        state = sampleState,
        onEvent = {}
    )
}
