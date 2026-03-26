package com.tutushubham.pokidex.feature_today

import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.engine.IntentProgress
import java.time.LocalDate

/**
 * MVI Contract for Today feature
 *
 * Defines:
 * - TodayEvent: All possible events (user actions, lifecycle, system)
 * - TodayState: UI state representation
 * - TodayEffect: Side effects (navigation, system interactions, UI feedback)
 */
object TodayContract {

    sealed interface TodayEmptyState {
        data object None : TodayEmptyState
        data object OnboardingRequired : TodayEmptyState
        data object NoStructure : TodayEmptyState
        data object NoIntent : TodayEmptyState
        data object NoSessionsToday : TodayEmptyState
        data object AllCompleted : TodayEmptyState
    }

    /**
     * Sealed interface representing all possible events in the Today feature
     */
    sealed interface TodayEvent {

        /** Screen lifecycle events */
        data object ScreenOpened : TodayEvent
        data object Refresh : TodayEvent

        /** User action events */
        data class StartSession(val sessionId: String) : TodayEvent
        data class SkipSession(
            val sessionId: String,
            val reason: SkipReason
        ) : TodayEvent
        data class CompleteSession(
            val sessionId: String,
            val actualMinutes: Int
        ) : TodayEvent

        /** System / time driven events */
        data class SessionTick(val elapsedMinutes: Int) : TodayEvent

        /** Active session adaptation */
        data class PauseSession(val sessionId: String) : TodayEvent
        data class ResumeSession(val sessionId: String) : TodayEvent
        data class ExtendSession(val sessionId: String, val additionalMinutes: Int) : TodayEvent
        data class ShortenSession(val sessionId: String, val reduceMinutes: Int) : TodayEvent

        /** Restart a completed or skipped session */
        data class RestartSession(val sessionId: String) : TodayEvent

        /** Focus override for today */
        data class OverrideFocusForToday(
            val domain: Domain,
            val focusId: String
        ) : TodayEvent
        data class RequestFocusOverride(val domain: Domain) : TodayEvent
        data object CancelFocusOverride : TodayEvent
        data class ClearOverrideForToday(val domain: Domain) : TodayEvent
    }

    /**
     * UI state for the Today screen
     */
    data class TodayState(
        val isLoading: Boolean = false,
        val date: LocalDate = LocalDate.now(),

        /** Planned sessions for today */
        val sessions: List<Session> = emptyList(),

        /** Currently active session (only one allowed) */
        val activeSessionId: String? = null,

        /** Derived UI state */
        val elapsedMinutes: Int = 0,
        val isPaused: Boolean = false,

        /** Resolved focus per domain for today (derived, not persisted) */
        val activeFocusByDomain: Map<Domain, Focus> = emptyMap(),

        /** Error handling */
        val error: String? = null,

        /** Pending focus override (domain + available focuses for selection) */
        val pendingOverrideDomain: Domain? = null,
        val availableOverrideFocuses: List<Focus> = emptyList(),

        /** Derived: whether setup has anchors / intents (from last plan load) */
        val hasAnchors: Boolean = false,
        val hasIntents: Boolean = false,

        /** Intent IDs whose required units today exceed daily capacity (under-allocated). */
        val overloadedIntentIds: List<String> = emptyList(),

        /** Max overload severity (needed / capacity) across overloaded intents; null if none. */
        val maxOverloadSeverity: Double? = null,

        /** Per-goal progress with execution-based pace, sorted by criticality. */
        val progressList: List<IntentProgress> = emptyList(),

        /** Transient settings impact banner */
        val settingsChangeBanner: String? = null,

        /** Derived empty state for empty-state UI */
        val emptyState: TodayEmptyState = TodayEmptyState.None
    ) {
        /**
         * Computed property: Get the active session if any
         */
        val activeSession: Session?
            get() = activeSessionId?.let { id ->
                sessions.firstOrNull { it.id == id }
            }

        /** Derived: whether there are any sessions for today */
        val hasSessions: Boolean
            get() = sessions.isNotEmpty()

        val hasSevereOverload: Boolean
            get() = (maxOverloadSeverity ?: 0.0) > 1.5

        val hasCriticalGoals: Boolean
            get() = progressList.any { it.isCritical }

    }

    /**
     * Sealed interface representing side effects (one-time events)
     */
    sealed interface TodayEffect {

        /** Navigation effects */
        data class NavigateToSession(val sessionId: String) : TodayEffect

        /** System interaction effects */
        data class StartSessionTimer(val sessionId: String) : TodayEffect
        data class ResumeSessionTimer(val sessionId: String, val elapsedMinutes: Int) : TodayEffect
        data object StopSessionTimer : TodayEffect

        /** UI feedback effects */
        data class ShowMessage(val message: String) : TodayEffect
    }
}
