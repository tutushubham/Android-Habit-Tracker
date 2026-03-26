package com.tutushubham.pokidex.feature_today

import androidx.compose.ui.graphics.Color
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.engine.IntentProgress
import java.time.Instant
import java.time.LocalDate

internal object PreviewData {

    val date: LocalDate = LocalDate.of(2026, 3, 26)

    val focuses = linkedMapOf(
        Domain.STUDIES to Focus("f1", Domain.STUDIES, "DSA", 1, null),
        Domain.FITNESS to Focus("f2", Domain.FITNESS, "Cardio", 1, null),
        Domain.WORK to Focus("f3", Domain.WORK, "Android", 1, null)
    )

    val progressCritical = IntentProgress(
        intentId = "i1", title = "DSA Mastery", domain = Domain.STUDIES,
        targetCount = 120, completedUnits = 45, remainingUnits = 75,
        daysRemaining = 12, requiredUnitsPerDay = 6.3, currentPace = 3.8,
        isBehind = true, isOverloaded = true, overloadSeverity = 2.1
    )

    val progressBehind = IntentProgress(
        intentId = "i2", title = "Compose UI Pro", domain = Domain.WORK,
        targetCount = 25, completedUnits = 10, remainingUnits = 15,
        daysRemaining = 28, requiredUnitsPerDay = 0.5, currentPace = 0.3,
        isBehind = true
    )

    val progressOnTrack = IntentProgress(
        intentId = "i3", title = "Marathon Prep", domain = Domain.FITNESS,
        targetCount = 60, completedUnits = 42, remainingUnits = 18,
        daysRemaining = 20, requiredUnitsPerDay = 0.9, currentPace = 1.2,
        isBehind = false
    )

    val allProgress = listOf(progressCritical, progressBehind, progressOnTrack)

    val sessionPlanned = Session(
        id = "s1", intentId = "i1", domain = Domain.STUDIES,
        date = date, block = DayBlock.MORNING, plannedMinutes = 90,
        actualMinutes = null, status = SessionStatus.PLANNED,
        skipReason = null, startedAt = null, endedAt = null
    )

    val sessionInProgress = Session(
        id = "s2", intentId = "i3", domain = Domain.FITNESS,
        date = date, block = DayBlock.DAY, plannedMinutes = 45,
        actualMinutes = null, status = SessionStatus.IN_PROGRESS,
        skipReason = null, startedAt = Instant.now(), endedAt = null
    )

    val sessionCompleted = Session(
        id = "s3", intentId = "i2", domain = Domain.WORK,
        date = date, block = DayBlock.EVENING, plannedMinutes = 60,
        actualMinutes = 55, status = SessionStatus.COMPLETED,
        skipReason = null,
        startedAt = Instant.now().minusSeconds(3600),
        endedAt = Instant.now()
    )

    val allSessions = listOf(sessionPlanned, sessionInProgress, sessionCompleted)

    fun todayState(
        isLoading: Boolean = false,
        activeSessionId: String? = null,
        elapsedMinutes: Int = 0,
        emptyState: TodayContract.TodayEmptyState = TodayContract.TodayEmptyState.None
    ) = TodayContract.TodayState(
        isLoading = isLoading,
        date = date,
        sessions = allSessions,
        activeSessionId = activeSessionId,
        elapsedMinutes = elapsedMinutes,
        activeFocusByDomain = focuses,
        overloadedIntentIds = listOf("i1"),
        maxOverloadSeverity = 2.1,
        progressList = allProgress,
        emptyState = emptyState
    )

    val insights = listOf(
        InsightUiModel(
            type = InsightType.BEHIND,
            title = "You are behind on DSA (+2.5/day)",
            subtitle = "Recovery plan: Add extra sessions to catch up.",
            domain = Domain.STUDIES,
            containerColor = Color(0xFFFCE4EC),
            contentColor = Color(0xFFB71C1C),
            icon = "warning"
        ),
        InsightUiModel(
            type = InsightType.MOMENTUM,
            title = "Great momentum on Marathon Prep",
            subtitle = "Pace: 1.2/day vs 0.9/day required. Keep it up!",
            domain = Domain.FITNESS,
            containerColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF1B5E20),
            icon = "local_fire_department"
        ),
        InsightUiModel(
            type = InsightType.RECOMMENDATION,
            title = "Cognitive overload detected (2.1x)",
            subtitle = "Consider adjusting deadlines or reducing scope.",
            containerColor = Color(0xFFE3F2FD),
            contentColor = Color(0xFF1565C0),
            icon = "psychology"
        )
    )

    val sessionUiModels = listOf(
        SessionUiModel(
            id = "s1", intentId = "i1", domain = Domain.STUDIES,
            goalTitle = "DSA Mastery", plannedMinutes = 90, actualMinutes = null,
            status = SessionStatus.PLANNED,
            reason = "Critical: 2.1x overload, 12 days left",
            learnedMinutes = null, tag = SessionTag.HIGH_PRIORITY, isHighlighted = true
        ),
        SessionUiModel(
            id = "s2", intentId = "i3", domain = Domain.FITNESS,
            goalTitle = "Marathon Prep", plannedMinutes = 45, actualMinutes = null,
            status = SessionStatus.IN_PROGRESS,
            reason = "42/60 done • 20 days left",
            learnedMinutes = null, tag = null, isHighlighted = false
        ),
        SessionUiModel(
            id = "s3", intentId = "i2", domain = Domain.WORK,
            goalTitle = "Compose UI Pro", plannedMinutes = 60, actualMinutes = 55,
            status = SessionStatus.COMPLETED,
            reason = "10/25 done • 28 days left",
            learnedMinutes = null, tag = SessionTag.RECOVERY, isHighlighted = false
        )
    )

    val activeSessionOnTrack = ActiveSessionUiModel(
        sessionId = "s2", domain = Domain.FITNESS,
        goalTitle = "Marathon Prep", tag = null,
        reason = "Maintaining momentum: 42/60 done.",
        plannedMinutes = 45, elapsedMinutes = 20, remainingMinutes = 25,
        progressFraction = 0.44f,
        performance = PerformanceStatus.ON_TRACK,
        performanceLabel = "On track",
        confidence = ConfidenceLevel.HIGH,
        confidenceLabel = "Based on your history",
        suggestion = SessionSuggestion(
            title = "Flow State",
            message = "You're on pace — stay focused for the remaining 25m.",
            icon = "🧠"
        )
    )

    val activeSessionCritical = ActiveSessionUiModel(
        sessionId = "s1", domain = Domain.STUDIES,
        goalTitle = "Advanced Tree Traversal", tag = SessionTag.HIGH_PRIORITY,
        reason = "Critical deadline — this session is essential for recovery.",
        plannedMinutes = 90, elapsedMinutes = 55, remainingMinutes = 35,
        progressFraction = 0.61f,
        performance = PerformanceStatus.ON_TRACK,
        performanceLabel = "On track",
        confidence = ConfidenceLevel.MEDIUM,
        confidenceLabel = "Calibrating your pace",
        suggestion = SessionSuggestion(
            title = "Critical Session",
            message = "You're making progress on a critical goal. Every minute counts.",
            icon = "🎯"
        )
    )

    val activeSessionOvertime = ActiveSessionUiModel(
        sessionId = "s1", domain = Domain.STUDIES,
        goalTitle = "DSA Mastery", tag = SessionTag.RECOVERY,
        reason = "Recovery session to close your pace deficit.",
        plannedMinutes = 45, elapsedMinutes = 52, remainingMinutes = 0,
        progressFraction = 1f,
        performance = PerformanceStatus.OVERTIME,
        performanceLabel = "Over time",
        confidence = ConfidenceLevel.LOW,
        confidenceLabel = "Learning your pace",
        suggestion = SessionSuggestion(
            title = "Session Overtime",
            message = "You've exceeded the planned 45m. Wrap up or adjust your estimate.",
            icon = "⏱️"
        )
    )

    val suggestion = SessionSuggestion(
        title = "Concentration Peak Detected",
        message = "Your cognitive baseline is 12% higher than average. You're in a Flow State.",
        icon = "🧠"
    )
}
