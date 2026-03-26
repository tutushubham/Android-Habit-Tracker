package com.tutushubham.pokidex.feature_today

import androidx.compose.ui.graphics.Color
import com.tutushubham.pokidex.core.engine.IntentProgress

/**
 * Pure mapping layer: derives UI-ready models from [TodayContract.TodayState].
 *
 * No repository calls, no business logic — just presentation transforms.
 * Keeps composables free of derivation logic and enables easy unit testing.
 */
object TodayUiMapper {

    private data class InsightColorPair(val container: Color, val content: Color)

    private fun behindColors(dark: Boolean) = if (dark)
        InsightColorPair(Color(0xFFB41340), Color(0xFFFFEFEF))
    else
        InsightColorPair(Color(0xFFFCE4EC), Color(0xFFB71C1C))

    private fun fatigueColors(dark: Boolean) = if (dark)
        InsightColorPair(Color(0xFFFFD18B), Color(0xFF455367))
    else
        InsightColorPair(Color(0xFFFFF3E0), Color(0xFFE65100))

    private fun momentumColors(dark: Boolean) = if (dark)
        InsightColorPair(Color(0xFF006947), Color(0xFFC8FFE0))
    else
        InsightColorPair(Color(0xFFE8F5E9), Color(0xFF1B5E20))

    private fun recommendationColors(dark: Boolean) = if (dark)
        InsightColorPair(Color(0xFF4E5C71), Color(0xFFEDF3FF))
    else
        InsightColorPair(Color(0xFFE3F2FD), Color(0xFF1565C0))

    fun mapInsights(state: TodayContract.TodayState, isDarkTheme: Boolean = true): List<InsightUiModel> {
        val insights = mutableListOf<InsightUiModel>()
        val behind = behindColors(isDarkTheme)
        val fatigue = fatigueColors(isDarkTheme)
        val momentum = momentumColors(isDarkTheme)
        val recommendation = recommendationColors(isDarkTheme)

        val progressMap = state.progressList.associateBy { it.intentId }

        state.progressList.forEach { progress ->
            when {
                progress.isCritical -> insights.add(
                    InsightUiModel(
                        type = InsightType.BEHIND,
                        title = "You are behind on ${progress.title} (+%.1f/day)".format(progress.deficit),
                        subtitle = "Recovery plan: Add extra sessions to catch up.",
                        domain = progress.domain,
                        containerColor = behind.container,
                        contentColor = behind.content,
                        icon = "warning",
                        relatedIntentId = progress.intentId
                    )
                )
                progress.isBehind -> insights.add(
                    InsightUiModel(
                        type = InsightType.BEHIND,
                        title = "${progress.title} needs attention",
                        subtitle = "Deficit: %.1f/day. Current pace won't meet the deadline.".format(progress.deficit),
                        domain = progress.domain,
                        containerColor = fatigue.container,
                        contentColor = fatigue.content,
                        icon = "trending_down",
                        relatedIntentId = progress.intentId
                    )
                )
            }
        }

        state.progressList
            .filter { !it.isBehind && it.currentPace >= it.requiredUnitsPerDay && it.completedUnits > 0 }
            .forEach { progress ->
                insights.add(
                    InsightUiModel(
                        type = InsightType.MOMENTUM,
                        title = "Great momentum on ${progress.title}",
                        subtitle = "Pace: %.1f/day vs %.1f/day required. Keep it up!".format(
                            progress.currentPace, progress.requiredUnitsPerDay
                        ),
                        domain = progress.domain,
                        containerColor = momentum.container,
                        contentColor = momentum.content,
                        icon = "local_fire_department"
                    )
                )
            }

        if (state.overloadedIntentIds.isNotEmpty()) {
            val severity = state.maxOverloadSeverity ?: 1.0
            insights.add(
                InsightUiModel(
                    type = InsightType.RECOMMENDATION,
                    title = "Cognitive overload detected (%.1fx)".format(severity),
                    subtitle = "Consider adjusting deadlines or reducing scope.",
                    containerColor = recommendation.container,
                    contentColor = recommendation.content,
                    icon = "psychology",
                    relatedIntentId = state.overloadedIntentIds.firstOrNull()
                )
            )
        }

        return insights
    }

    fun mapSessions(state: TodayContract.TodayState): List<SessionUiModel> {
        val progressMap = state.progressList.associateBy { it.intentId }

        return state.sessions.map { session ->
            val progress = progressMap[session.intentId]
            val tag = deriveTag(progress)

            SessionUiModel(
                id = session.id,
                intentId = session.intentId,
                domain = session.domain,
                goalTitle = progress?.title,
                plannedMinutes = session.plannedMinutes,
                actualMinutes = session.actualMinutes,
                status = session.status,
                reason = deriveReason(progress, tag),
                learnedMinutes = null,
                tag = tag,
                isHighlighted = tag == SessionTag.HIGH_PRIORITY
            )
        }
    }

    private fun deriveTag(progress: IntentProgress?): SessionTag? = when {
        progress == null -> null
        progress.isCritical -> SessionTag.HIGH_PRIORITY
        progress.isBehind -> SessionTag.RECOVERY
        else -> null
    }

    private fun deriveReason(progress: IntentProgress?, tag: SessionTag?): String? = when (tag) {
        SessionTag.HIGH_PRIORITY -> "Critical: %.1fx overload, %d days left".format(
            progress?.overloadSeverity ?: 0.0, progress?.daysRemaining ?: 0
        )
        SessionTag.RECOVERY -> "Behind by %.1f/day — recovery session".format(
            progress?.deficit ?: 0.0
        )
        else -> progress?.let { "${it.completedUnits}/${it.targetCount} done • ${it.daysRemaining} days left" }
    }

    // --- Active Session mapping ---

    fun mapActiveSession(state: TodayContract.TodayState): ActiveSessionUiModel? {
        val session = state.activeSession ?: return null
        val progressMap = state.progressList.associateBy { it.intentId }
        val progress = progressMap[session.intentId]

        val planned = session.plannedMinutes
        val elapsed = state.elapsedMinutes
        val remaining = (planned - elapsed).coerceAtLeast(0)
        val fraction = if (planned > 0) (elapsed.toFloat() / planned).coerceIn(0f, 1f) else 0f

        val performance = derivePerformance(elapsed, planned)
        val tag = deriveTag(progress)
        val confidence = deriveConfidence(progress)

        return ActiveSessionUiModel(
            sessionId = session.id,
            domain = session.domain,
            goalTitle = progress?.title ?: session.domain.name,
            tag = tag,
            reason = deriveSessionContext(progress, tag),
            plannedMinutes = planned,
            elapsedMinutes = elapsed,
            remainingMinutes = remaining,
            progressFraction = fraction,
            performance = performance,
            performanceLabel = performanceLabel(performance),
            confidence = confidence,
            confidenceLabel = confidenceLabel(confidence),
            suggestion = deriveSuggestion(elapsed, planned, performance, progress, tag)
        )
    }

    private fun derivePerformance(elapsed: Int, planned: Int): PerformanceStatus = when {
        elapsed > planned -> PerformanceStatus.OVERTIME
        planned == 0 -> PerformanceStatus.ON_TRACK
        elapsed.toFloat() / planned < 0.6f -> PerformanceStatus.FASTER
        elapsed.toFloat() / planned <= 1.0f -> PerformanceStatus.ON_TRACK
        else -> PerformanceStatus.OVERTIME
    }

    private fun performanceLabel(status: PerformanceStatus): String = when (status) {
        PerformanceStatus.FASTER -> "Ahead of plan"
        PerformanceStatus.ON_TRACK -> "On track"
        PerformanceStatus.SLOWER -> "Falling behind"
        PerformanceStatus.OVERTIME -> "Over time"
    }

    private fun deriveConfidence(progress: IntentProgress?): ConfidenceLevel {
        if (progress == null) return ConfidenceLevel.LOW
        return when {
            progress.completedUnits >= 15 -> ConfidenceLevel.HIGH
            progress.completedUnits >= 5 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private fun confidenceLabel(level: ConfidenceLevel): String = when (level) {
        ConfidenceLevel.HIGH -> "AI Optimizer Active"
        ConfidenceLevel.MEDIUM -> "AI Calibrating"
        ConfidenceLevel.LOW -> "AI Learning"
    }

    private fun deriveSessionContext(progress: IntentProgress?, tag: SessionTag?): String? = when {
        tag == SessionTag.HIGH_PRIORITY -> "Critical deadline — this session is essential for recovery."
        tag == SessionTag.RECOVERY -> "Recovery session to close your pace deficit."
        progress != null && !progress.isBehind ->
            "Maintaining momentum: ${progress.completedUnits}/${progress.targetCount} done."
        else -> null
    }

    private fun deriveSuggestion(
        elapsed: Int,
        planned: Int,
        performance: PerformanceStatus,
        progress: IntentProgress?,
        tag: SessionTag?
    ): SessionSuggestion? {
        val halfwayPassed = elapsed > 0 && elapsed >= planned / 2

        return when {
            performance == PerformanceStatus.OVERTIME ->
                SessionSuggestion(
                    title = "Session Overtime",
                    message = "You've exceeded the planned ${planned}m. Wrap up or adjust your estimate.",
                    icon = "⏱️"
                )

            halfwayPassed && performance == PerformanceStatus.ON_TRACK && tag == null ->
                SessionSuggestion(
                    title = "Flow State",
                    message = "You're on pace — stay focused for the remaining ${planned - elapsed}m.",
                    icon = "🧠"
                )

            halfwayPassed && tag == SessionTag.HIGH_PRIORITY ->
                SessionSuggestion(
                    title = "Critical Session",
                    message = "You're making progress on a critical goal. Every minute counts.",
                    icon = "🎯"
                )

            elapsed == 0 && tag == SessionTag.RECOVERY ->
                SessionSuggestion(
                    title = "Recovery Mode",
                    message = "This session helps close your daily deficit of %.1f units.".format(
                        progress?.deficit ?: 0.0
                    ),
                    icon = "🔄"
                )

            else -> null
        }
    }
}
