package com.tutushubham.pokidex.feature_today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.TodayEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class TodayViewModel(
    private val todayEngine: TodayEngine,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TodayContract.TodayState(isLoading = true))
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
        }
    }

    private fun loadToday() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }

        try {
            val today = _state.value.date
            val plan = todayEngine.generate(today)

            _state.update {
                it.copy(
                    isLoading = false,
                    sessions = plan.sessions
                )
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(isLoading = false, error = t.message)
            }
        }
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
