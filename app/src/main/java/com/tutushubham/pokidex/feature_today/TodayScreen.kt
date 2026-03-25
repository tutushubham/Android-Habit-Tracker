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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.service.SessionTimerHelper
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToSession: (String) -> Unit,
    onOpenFocusSettings: ((Domain) -> Unit)? = null,
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToStructureSettings: () -> Unit = {},
    onNavigateToAddGoal: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onEvent(TodayContract.TodayEvent.ScreenOpened)
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        onOpenFocusSettings?.let { openFocus ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = { openFocus(Domain.STUDIES) }) {
                    Text("Focus (STUDIES)")
                }
            }
        }
        if (!state.isLoading && state.overloadedIntentIds.isNotEmpty()) {
            val severity = state.maxOverloadSeverity ?: 1.0
            val (message, containerColor, contentColor) = when {
                severity >= 3.0 -> Triple(
                    "Critical: your capacity is far below what's needed to meet deadlines.",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                severity >= 1.5 -> Triple(
                    "Your current capacity cannot meet some deadlines.",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                else -> Triple(
                    "Your current capacity may not meet some deadlines.",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = containerColor,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> LoadingView()
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

                    TodayContract.TodayEmptyState.None ->
                        TodayContent(
                            state = state,
                            onEvent = viewModel::onEvent
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
fun TodayContent(
    state: TodayContract.TodayState,
    onEvent: (TodayContract.TodayEvent) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TodayFocusHeader(
            focusMap = state.activeFocusByDomain,
            onChangeFocus = { domain ->
                onEvent(TodayContract.TodayEvent.RequestFocusOverride(domain))
            }
        )
        if (state.progressList.isNotEmpty()) {
            ProgressSection(progressList = state.progressList)
        }
        Box(Modifier.weight(1f)) {
            SessionsList(
                state = state,
                onEvent = onEvent
            )
        }
    }
}

@Composable
fun ProgressSection(progressList: List<IntentProgress>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Goal progress", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        progressList.forEach { progress ->
            ProgressCard(progress)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ProgressCard(progress: IntentProgress) {
    val statusColor = when {
        progress.isCritical -> MaterialTheme.colorScheme.error
        progress.isBehind -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val statusText = when {
                    progress.isCritical -> {
                        val sev = progress.overloadSeverity
                        if (sev != null) "Critical (%.1fx overload)".format(sev) else "Critical"
                    }
                    progress.isBehind -> "Behind by %.1f/day".format(progress.deficit)
                    else -> "On track"
                }
                val chipColor = when {
                    progress.isCritical -> MaterialTheme.colorScheme.errorContainer
                    progress.isBehind -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = chipColor
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            progress.isCritical || progress.isBehind -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.2f),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${progress.completedUnits} / ${progress.targetCount} done",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${progress.daysRemaining} days left",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (progress.isBehind || progress.isCritical) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Need: %.1f/day  |  Doing: %.1f/day  |  Deficit: %.1f/day".format(
                        progress.requiredUnitsPerDay,
                        progress.currentPace,
                        progress.deficit
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TodayFocusHeader(
    focusMap: Map<Domain, Focus>,
    onChangeFocus: (Domain) -> Unit
) {
    if (focusMap.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Today's focus",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        focusMap.forEach { (domain, focus) ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${domain.name}: ${focus.name}")
                TextButton(onClick = { onChangeFocus(domain) }) {
                    Text("Change")
                }
            }
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
                },
                    onChangeFocusForToday = {
                        onEvent(TodayContract.TodayEvent.RequestFocusOverride(session.domain))
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
    onComplete: () -> Unit,
    onChangeFocusForToday: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.domain.name,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onChangeFocusForToday) {
                    Text("Change today")
                }
            }

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
fun FocusOverrideSheet(
    focuses: List<Focus>,
    onSelect: (Focus) -> Unit,
    onDismiss: () -> Unit,
    onClearOverride: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = "Switch focus just for today",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))
        focuses.forEach { focus ->
            ListItem(
                headlineContent = { Text(focus.name) },
                modifier = Modifier.clickable {
                    onSelect(focus)
                    onDismiss()
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            onClearOverride()
            onDismiss()
        }) {
            Text("Use automatic focus")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusOverrideSheetPreview() {
    PokidexTheme {
        val sampleFocuses = listOf(
            Focus("f1", Domain.STUDIES, "DSA", 1, null),
            Focus("f2", Domain.STUDIES, "Android", 1, null),
            Focus("f3", Domain.STUDIES, "Guitar", 1, LocalDate.of(2030, 2, 1))
        )
        FocusOverrideSheet(
            focuses = sampleFocuses,
            onSelect = { },
            onDismiss = { },
            onClearOverride = { }
        )
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
fun EmptyCard(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
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
            Text("Your day is clear. Take a rest or adjust your setup.")
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
