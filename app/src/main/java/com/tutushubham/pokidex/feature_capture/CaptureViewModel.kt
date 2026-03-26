package com.tutushubham.pokidex.feature_capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.entity.Capture
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.CaptureRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CaptureViewModel(
    private val captureRepository: CaptureRepository,
    private val intentRepository: IntentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureContract.CaptureState())
    val state: StateFlow<CaptureContract.CaptureState> = _state.asStateFlow()

    private val _effect = Channel<CaptureContract.CaptureEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: CaptureContract.CaptureEvent) {
        when (event) {
            CaptureContract.CaptureEvent.ScreenOpened -> loadCaptures()
            is CaptureContract.CaptureEvent.AddCapture -> addCapture(event.text)
            is CaptureContract.CaptureEvent.ConvertToGoal -> convertToGoal(event.captureId)
            is CaptureContract.CaptureEvent.DeleteCapture -> deleteCapture(event.captureId)
        }
    }

    private fun loadCaptures() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val all = captureRepository.getAllCaptures()
            val processed = all.count { it.resolved }
            _state.update {
                it.copy(
                    isLoading = false,
                    captures = all.filter { capture -> !capture.resolved },
                    processedCount = processed
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isLoading = false, error = t.message) }
        }
    }

    private fun addCapture(text: String) = viewModelScope.launch {
        val capture = Capture(
            id = UUID.randomUUID().toString(),
            content = text,
            createdAt = Instant.now(),
            resolved = false,
            resolvedSessionId = null
        )
        captureRepository.insertCapture(capture)
        loadCaptures()
    }

    private fun convertToGoal(captureId: String) = viewModelScope.launch {
        val capture = _state.value.captures.firstOrNull { it.id == captureId } ?: return@launch
        val intentId = UUID.randomUUID().toString()
        val intent = GoalIntent(
            id = intentId,
            domain = Domain.STUDIES,
            title = capture.content,
            targetCount = 10,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(30),
            priority = 1,
            estimatedMinutesPerUnit = 30,
            focusId = null
        )
        intentRepository.insertIntent(intent)
        captureRepository.updateCapture(
            capture.copy(resolved = true, resolvedSessionId = intentId)
        )
        _effect.send(CaptureContract.CaptureEffect.ShowMessage("Converted to goal"))
        _effect.send(CaptureContract.CaptureEffect.NavigateToGoalEdit(intentId))
        loadCaptures()
    }

    private fun deleteCapture(captureId: String) = viewModelScope.launch {
        captureRepository.deleteCapture(captureId)
        loadCaptures()
        _effect.send(CaptureContract.CaptureEffect.ShowMessage("Thought deleted"))
    }
}
