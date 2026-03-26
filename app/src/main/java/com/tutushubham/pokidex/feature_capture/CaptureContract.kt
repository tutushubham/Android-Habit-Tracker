package com.tutushubham.pokidex.feature_capture

import com.tutushubham.pokidex.core.domain.entity.Capture

object CaptureContract {

    sealed interface CaptureEvent {
        data object ScreenOpened : CaptureEvent
        data class AddCapture(val text: String) : CaptureEvent
        data class ConvertToGoal(val captureId: String) : CaptureEvent
        data class DeleteCapture(val captureId: String) : CaptureEvent
    }

    data class CaptureState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val captures: List<Capture> = emptyList(),
        val weeklyMomentum: Float = 0f,
        val processedCount: Int = 0
    )

    sealed interface CaptureEffect {
        data class ShowMessage(val message: String) : CaptureEffect
        data class NavigateToGoalEdit(val intentId: String) : CaptureEffect
    }
}
