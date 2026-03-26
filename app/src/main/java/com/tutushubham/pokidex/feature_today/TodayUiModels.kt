package com.tutushubham.pokidex.feature_today

import androidx.compose.ui.graphics.Color
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.engine.InsightExplanation

enum class SessionTag { HIGH_PRIORITY, RECOVERY }

enum class InsightType { BEHIND, FATIGUE, MOMENTUM, RECOMMENDATION }

data class InsightUiModel(
    val type: InsightType,
    val title: String,
    val subtitle: String,
    val domain: Domain? = null,
    val containerColor: Color,
    val contentColor: Color,
    val icon: String,
    val explanation: InsightExplanation? = null,
    /** When set, Today can navigate (e.g. to full recommendation flow). */
    val relatedIntentId: String? = null
)

data class SessionUiModel(
    val id: String,
    val intentId: String,
    val domain: Domain,
    val goalTitle: String?,
    val plannedMinutes: Int,
    val actualMinutes: Int?,
    val status: SessionStatus,
    val reason: String?,
    val learnedMinutes: Int?,
    val tag: SessionTag?,
    val isHighlighted: Boolean,
    val explanation: InsightExplanation? = null
)

// --- Active Session models ---

enum class PerformanceStatus { FASTER, ON_TRACK, SLOWER, OVERTIME }

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

data class SessionSuggestion(
    val title: String,
    val message: String,
    val icon: String
)

data class ActiveSessionUiModel(
    val sessionId: String,
    val domain: Domain,
    val goalTitle: String,
    val tag: SessionTag?,
    val reason: String?,

    val plannedMinutes: Int,
    val elapsedMinutes: Int,
    val remainingMinutes: Int,
    val progressFraction: Float,

    val performance: PerformanceStatus,
    val performanceLabel: String,
    val confidence: ConfidenceLevel,
    val confidenceLabel: String,

    val suggestion: SessionSuggestion?,
    val explanation: InsightExplanation? = null
)
