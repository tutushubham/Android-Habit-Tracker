package com.tutushubham.pokidex.feature_today

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.SkipReason
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

        /** Error handling */
        val error: String? = null
    ) {
        /**
         * Computed property: Get the active session if any
         */
        val activeSession: Session?
            get() = activeSessionId?.let { id ->
                sessions.firstOrNull { it.id == id }
            }

    }

    /**
     * Sealed interface representing side effects (one-time events)
     */
    sealed interface TodayEffect {

        /** Navigation effects */
        data class NavigateToSession(val sessionId: String) : TodayEffect

        /** System interaction effects */
        data class StartSessionTimer(val sessionId: String) : TodayEffect
        data object StopSessionTimer : TodayEffect

        /** UI feedback effects */
        data class ShowMessage(val message: String) : TodayEffect
    }
}
