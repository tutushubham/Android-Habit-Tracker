package com.tutushubham.pokidex.feature_today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.FocusResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class TodayViewModel(
    private val todayPlannerUseCase: TodayPlannerUseCase,
    private val sessionRepository: SessionRepository,
    private val appStateRepository: AppStateRepository,
    private val focusRepository: FocusRepository,
    private val dailyFocusOverrideRepository: DailyFocusOverrideRepository,
    private val focusResolver: FocusResolver,
    private val clock: Clock = java.time.Clock.systemDefaultZone()
) : ViewModel() {

    private val _state = MutableStateFlow(
        TodayContract.TodayState(isLoading = true, date = LocalDate.now(clock))
    )
    val state: StateFlow<TodayContract.TodayState> = _state.asStateFlow()

    private val _effect = Channel<TodayContract.TodayEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: TodayContract.TodayEvent) {
        when (event) {
            is TodayContract.TodayEvent.ScreenOpened -> loadToday()
            is TodayContract.TodayEvent.Refresh -> loadToday()

            is TodayContract.TodayEvent.StartSession ->
                startSession(event.sessionId)

            is TodayContract.TodayEvent.SkipSession ->
                skipSession(event.sessionId, event.reason)

            is TodayContract.TodayEvent.CompleteSession ->
                completeSession(event.sessionId, event.actualMinutes)

            is TodayContract.TodayEvent.SessionTick ->
                updateElapsed(event.elapsedMinutes)

            is TodayContract.TodayEvent.OverrideFocusForToday ->
                overrideFocus(event.domain, event.focusId)

            is TodayContract.TodayEvent.RequestFocusOverride ->
                requestFocusOverride(event.domain)

            TodayContract.TodayEvent.CancelFocusOverride ->
                _state.update {
                    it.copy(pendingOverrideDomain = null, availableOverrideFocuses = emptyList())
                }

            is TodayContract.TodayEvent.ClearOverrideForToday ->
                clearOverrideForToday(event.domain)
        }
    }

    private fun loadToday() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }

        val today = LocalDate.now(clock)
        _state.update { it.copy(date = today) }

        try {
            val onboardingDone = appStateRepository.isOnboardingCompleted()

            if (!onboardingDone) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        overloadedIntentIds = emptyList(),
                        maxOverloadSeverity = null,
                        progressList = emptyList(),
                        emptyState = TodayContract.TodayEmptyState.OnboardingRequired
                    )
                }
                return@launch
            }

            val plan = todayPlannerUseCase.planToday(today)

            if (!plan.hasAnchors) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        overloadedIntentIds = emptyList(),
                        maxOverloadSeverity = null,
                        progressList = emptyList(),
                        emptyState = TodayContract.TodayEmptyState.NoStructure
                    )
                }
                return@launch
            }

            if (!plan.hasIntents) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        overloadedIntentIds = emptyList(),
                        maxOverloadSeverity = null,
                        progressList = emptyList(),
                        emptyState = TodayContract.TodayEmptyState.NoIntent
                    )
                }
                return@launch
            }

            if (plan.sessions.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        sessions = emptyList(),
                        overloadedIntentIds = emptyList(),
                        maxOverloadSeverity = null,
                        progressList = plan.progressList,
                        emptyState = TodayContract.TodayEmptyState.NoSessionsToday
                    )
                }
                return@launch
            }

            val focusMap = buildMap<Domain, Focus> {
                for (domain in plan.sessions.map { it.domain }.distinct()) {
                    focusResolver.resolve(domain, today)?.let { put(domain, it) }
                }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    sessions = plan.sessions,
                    activeFocusByDomain = focusMap,
                    hasAnchors = plan.hasAnchors,
                    hasIntents = plan.hasIntents,
                    overloadedIntentIds = plan.overloadedIntentIds,
                    maxOverloadSeverity = plan.overloadDetails.maxOfOrNull { it.severity },
                    progressList = plan.progressList,
                    emptyState = TodayContract.TodayEmptyState.None
                )
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(isLoading = false, error = t.message)
            }
        }
    }

    private fun requestFocusOverride(domain: Domain) =
        viewModelScope.launch {
            val focuses = focusRepository.getFocusesByDomain(domain)
            _state.update {
                it.copy(
                    pendingOverrideDomain = domain,
                    availableOverrideFocuses = focuses
                )
            }
        }

    private fun overrideFocus(domain: Domain, focusId: String) = viewModelScope.launch {
        val today = _state.value.date
        dailyFocusOverrideRepository.setOverride(
            DailyFocusOverride(domain = domain, date = today, focusId = focusId)
        )
        _state.update {
            it.copy(pendingOverrideDomain = null, availableOverrideFocuses = emptyList())
        }
        loadToday()
        _effect.send(TodayContract.TodayEffect.ShowMessage("Focus changed for today"))
    }

    private fun clearOverrideForToday(domain: Domain) = viewModelScope.launch {
        val today = _state.value.date
        dailyFocusOverrideRepository.clearOverride(domain, today)
        _state.update {
            it.copy(pendingOverrideDomain = null, availableOverrideFocuses = emptyList())
        }
        loadToday()
        _effect.send(TodayContract.TodayEffect.ShowMessage("Using automatic focus"))
    }

    private fun startSession(sessionId: String) = viewModelScope.launch {
        val session = _state.value.sessions.firstOrNull { it.id == sessionId }
            ?: return@launch

        // Prevent starting a session that's already in progress
        if (session.status == SessionStatus.IN_PROGRESS) return@launch

        val updatedSession = session.copy(
            status = SessionStatus.IN_PROGRESS,
            startedAt = Instant.now()
        )

        sessionRepository.updateSession(updatedSession)

        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) updatedSession else it
        }

        _state.update {
            it.copy(
                activeSessionId = sessionId,
                elapsedMinutes = 0,
                sessions = updatedSessions
            )
        }

        _effect.send(TodayContract.TodayEffect.StartSessionTimer(sessionId))
    }

    private fun skipSession(
        sessionId: String,
        reason: SkipReason
    ) = viewModelScope.launch {
        val wasActiveSession = _state.value.activeSessionId == sessionId

        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) {
                it.copy(
                    status = SessionStatus.SKIPPED,
                    skipReason = reason
                )
            } else it
        }

        updatedSessions.firstOrNull { it.id == sessionId }?.let {
            sessionRepository.updateSession(it)
        }

        _state.update {
            it.copy(
                sessions = updatedSessions,
                activeSessionId = if (wasActiveSession) null else it.activeSessionId
            )
        }

        // Stop timer if this was the active session
        if (wasActiveSession) {
            _effect.send(TodayContract.TodayEffect.StopSessionTimer)
        }

        _effect.send(
            TodayContract.TodayEffect.ShowMessage("Session skipped")
        )
    }

    private fun completeSession(
        sessionId: String,
        actualMinutes: Int
    ) = viewModelScope.launch {
        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) {
                it.copy(
                    status = SessionStatus.COMPLETED,
                    actualMinutes = actualMinutes,
                    endedAt = Instant.now()
                )
            } else it
        }

        updatedSessions.firstOrNull { it.id == sessionId }?.let {
            sessionRepository.updateSession(it)
        }

        _state.update {
            it.copy(
                sessions = updatedSessions,
                activeSessionId = null,
                elapsedMinutes = 0
            )
        }

        _effect.send(TodayContract.TodayEffect.StopSessionTimer)
    }

    private fun updateElapsed(minutes: Int) {
        _state.update { it.copy(elapsedMinutes = minutes) }
    }
}
