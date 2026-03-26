package com.tutushubham.pokidex.feature_focus

import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.LocalDate

data class FocusState(
    val domain: Domain,
    val focuses: List<Focus> = emptyList(),
    val strategy: FocusStrategy? = null,
    val manualOverrideFocusId: String? = null,
    val preview: List<String> = emptyList(),
    val weeklyMomentum: List<Float> = emptyList(),
    val currentFocusTitle: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface FocusEffect {
    data object NavigateToFocusList : FocusEffect
    data object NavigateToStrategy : FocusEffect
    data object NavigateToConfirm : FocusEffect
    data object Exit : FocusEffect
    data class ShowMessage(val msg: String) : FocusEffect
}

sealed interface FocusEvent {
    data object ScreenOpened : FocusEvent

    data object EditFocusClicked : FocusEvent
    data object ListNextClicked : FocusEvent
    data object StrategyNextClicked : FocusEvent
    data object ConfirmClicked : FocusEvent

    data class StrategySelected(val strategy: FocusStrategy) : FocusEvent
    data class ManualFocusSelected(val focusId: String) : FocusEvent
    data class RotationOrderUpdated(val order: List<String>) : FocusEvent
    data class WeightsUpdated(val weights: Map<String, Int>) : FocusEvent

    data class AddFocus(val name: String, val deadline: LocalDate?) : FocusEvent
    data class DeleteFocus(val focusId: String) : FocusEvent
    data class UpdateFocusName(val focusId: String, val newName: String) : FocusEvent
}
