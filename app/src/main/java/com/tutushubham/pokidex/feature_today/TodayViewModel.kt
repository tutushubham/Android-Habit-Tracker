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
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.engine.CopyEngine
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.feature_settings.SettingsRepository
import com.tutushubham.pokidex.feature_settings.SystemSettings
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
    private val settingsRepository: SettingsRepository? = null,
    private val clock: Clock = java.time.Clock.systemDefaultZone(),
    private val behaviorProfileUseCase: BehaviorProfileUseCase? = null
) : ViewModel() {

    private var currentSettings = SystemSettings()
    private var settingsInitialized = false

    init {
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.settings.collect { settings ->
                    val wasInitialized = settingsInitialized
                    val oldSettings = currentSettings
                    currentSettings = settings
                    settingsInitialized = true

                    if (wasInitialized && settings != oldSettings) {
                        behaviorProfileUseCase?.invalidateCache()
                        val changes = describeSettingsChanges(oldSettings, settings)
                        if (changes.isNotEmpty()) {
                            _state.update {
                                it.copy(settingsChangeBanner = CopyEngine.settingsImpact(changes))
                            }
                        }
                        loadToday()
                    }
                }
            }
        }
    }

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

            is TodayContract.TodayEvent.PauseSession ->
                pauseSession(event.sessionId)

            is TodayContract.TodayEvent.ResumeSession ->
                resumeSession(event.sessionId)

            is TodayContract.TodayEvent.ExtendSession ->
                extendSession(event.sessionId, event.additionalMinutes)

            is TodayContract.TodayEvent.ShortenSession ->
                shortenSession(event.sessionId, event.reduceMinutes)

            is TodayContract.TodayEvent.RestartSession ->
                restartSession(event.sessionId)

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

            val plan = todayPlannerUseCase.planToday(today, currentSettings)

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

        val allDone = updatedSessions.all {
            it.status == SessionStatus.COMPLETED || it.status == SessionStatus.SKIPPED
        }
        val emptyWhenAllDone =
            if (allDone && updatedSessions.isNotEmpty()) {
                TodayContract.TodayEmptyState.AllCompleted
            } else null

        _state.update {
            it.copy(
                sessions = updatedSessions,
                activeSessionId = if (wasActiveSession) null else it.activeSessionId,
                emptyState = emptyWhenAllDone ?: it.emptyState
            )
        }

        // Stop timer if this was the active session
        if (wasActiveSession) {
            _effect.send(TodayContract.TodayEffect.StopSessionTimer)
        }

        _effect.send(
            TodayContract.TodayEffect.ShowMessage("Session skipped")
        )
        behaviorProfileUseCase?.invalidateCache()
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

        val allDone = updatedSessions.all {
            it.status == SessionStatus.COMPLETED || it.status == SessionStatus.SKIPPED
        }
        val emptyWhenAllDone =
            if (allDone && updatedSessions.isNotEmpty()) {
                TodayContract.TodayEmptyState.AllCompleted
            } else null

        _state.update {
            it.copy(
                sessions = updatedSessions,
                activeSessionId = null,
                elapsedMinutes = 0,
                emptyState = emptyWhenAllDone ?: it.emptyState
            )
        }

        _effect.send(TodayContract.TodayEffect.StopSessionTimer)
        behaviorProfileUseCase?.invalidateCache()
    }

    private fun pauseSession(sessionId: String) = viewModelScope.launch {
        if (_state.value.activeSessionId != sessionId) return@launch
        _state.update { it.copy(isPaused = true) }
        _effect.send(TodayContract.TodayEffect.StopSessionTimer)
    }

    private fun resumeSession(sessionId: String) = viewModelScope.launch {
        if (_state.value.activeSessionId != sessionId) return@launch
        val elapsed = _state.value.elapsedMinutes
        _state.update { it.copy(isPaused = false) }
        _effect.send(TodayContract.TodayEffect.ResumeSessionTimer(sessionId, elapsed))
    }

    private fun restartSession(sessionId: String) = viewModelScope.launch {
        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) {
                it.copy(
                    status = SessionStatus.PLANNED,
                    actualMinutes = null,
                    endedAt = null,
                    startedAt = null,
                    skipReason = null
                )
            } else it
        }

        updatedSessions.firstOrNull { it.id == sessionId }?.let {
            sessionRepository.updateSession(it)
        }

        _state.update {
            it.copy(
                sessions = updatedSessions,
                emptyState = TodayContract.TodayEmptyState.None
            )
        }
        _effect.send(TodayContract.TodayEffect.ShowMessage("Session restarted"))
    }

    private fun extendSession(sessionId: String, additionalMinutes: Int) = viewModelScope.launch {
        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) {
                it.copy(plannedMinutes = it.plannedMinutes + additionalMinutes)
            } else it
        }
        updatedSessions.firstOrNull { it.id == sessionId }?.let {
            sessionRepository.updateSession(it)
        }
        _state.update { it.copy(sessions = updatedSessions) }
        _effect.send(TodayContract.TodayEffect.ShowMessage("Session extended by ${additionalMinutes}m"))
    }

    private fun shortenSession(sessionId: String, reduceMinutes: Int) = viewModelScope.launch {
        val updatedSessions = _state.value.sessions.map {
            if (it.id == sessionId) {
                it.copy(plannedMinutes = (it.plannedMinutes - reduceMinutes).coerceAtLeast(5))
            } else it
        }
        updatedSessions.firstOrNull { it.id == sessionId }?.let {
            sessionRepository.updateSession(it)
        }
        _state.update { it.copy(sessions = updatedSessions) }
        _effect.send(TodayContract.TodayEffect.ShowMessage("Session shortened by ${reduceMinutes}m"))
    }

    private fun updateElapsed(minutes: Int) {
        _state.update { it.copy(elapsedMinutes = minutes) }
    }

    private fun describeSettingsChanges(old: SystemSettings, new: SystemSettings): List<String> {
        val changes = mutableListOf<String>()
        if (old.adaptivePlanningEnabled != new.adaptivePlanningEnabled) {
            changes += "Adaptive planning ${if (new.adaptivePlanningEnabled) "enabled" else "disabled"}"
        }
        if (old.planningStyle != new.planningStyle) {
            changes += "Planning style → ${new.planningStyle.name.lowercase()}"
        }
        if (old.fatigueSensitivity != new.fatigueSensitivity) {
            changes += "Fatigue sensitivity → ${new.fatigueSensitivity.name.lowercase()}"
        }
        if (old.learningEnabled != new.learningEnabled) {
            changes += "Learning ${if (new.learningEnabled) "enabled" else "disabled"}"
        }
        if (old.themeMode != new.themeMode) {
            changes += "Theme → ${new.themeMode.name.lowercase()}"
        }
        return changes
    }
}
