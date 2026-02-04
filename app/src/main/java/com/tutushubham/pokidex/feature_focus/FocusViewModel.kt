package com.tutushubham.pokidex.feature_focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

class FocusViewModel(
    private val domain: com.tutushubham.pokidex.core.domain.model.Domain,
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val focusResolver: FocusResolver,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _state = MutableStateFlow(FocusState(domain = domain, isLoading = true))
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private val _effect = Channel<FocusEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var previewJob: Job? = null

    fun onEvent(event: FocusEvent) {
        when (event) {
            FocusEvent.ScreenOpened -> load()

            FocusEvent.EditFocusClicked ->
                sendEffect(FocusEffect.NavigateToFocusList)

            FocusEvent.ListNextClicked ->
                sendEffect(FocusEffect.NavigateToStrategy)

            FocusEvent.StrategyNextClicked ->
                sendEffect(FocusEffect.NavigateToConfirm)

            FocusEvent.ConfirmClicked ->
                saveConfig()

            is FocusEvent.StrategySelected ->
                updateStrategy(event.strategy)

            is FocusEvent.ManualFocusSelected ->
                updateManual(event.focusId)

            is FocusEvent.RotationOrderUpdated ->
                updateRotation(event.order)

            is FocusEvent.WeightsUpdated ->
                updateWeights(event.weights)
        }
    }

    private fun load() = viewModelScope.launch {
        try {
            val focuses = focusRepository.getFocusesByDomain(domain)
            val config = configRepository.getConfig(domain)

            _state.update {
                it.copy(
                    focuses = focuses,
                    strategy = config?.strategy,
                    manualOverrideFocusId = config?.manualOverrideFocusId,
                    isLoading = false,
                    error = null
                )
            }

            regeneratePreview()
        } catch (t: Throwable) {
            _state.update {
                it.copy(isLoading = false, error = t.message)
            }
        }
    }

    private fun updateStrategy(strategy: FocusStrategy) {
        _state.update {
            it.copy(strategy = strategy)
        }
        regeneratePreview()
    }

    private fun updateManual(focusId: String) {
        _state.update {
            it.copy(manualOverrideFocusId = focusId)
        }
        regeneratePreview()
    }

    private fun updateRotation(order: List<String>) {
        val current = _state.value.strategy as? FocusStrategy.Rotation ?: return
        updateStrategy(current.copy(order = order))
    }

    private fun updateWeights(weights: Map<String, Int>) {
        val current = _state.value.strategy as? FocusStrategy.Weighted ?: return
        updateStrategy(current.copy(weights = weights))
    }

    private fun regeneratePreview(days: Int = 7) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val strategy = _state.value.strategy ?: return@launch
            val focuses = _state.value.focuses
            if (focuses.isEmpty()) return@launch

            val today = LocalDate.now(clock)
            val draftConfig = DomainFocusConfig(
                domain = domain,
                strategy = strategy,
                manualOverrideFocusId = _state.value.manualOverrideFocusId,
                createdAt = today
            )

            val preview = (0 until days).mapNotNull { offset ->
                val date = today.plusDays(offset.toLong())
                focusResolver.resolveWithConfig(draftConfig, focuses, date)?.name
            }

            _state.update {
                it.copy(preview = preview)
            }
        }
    }

    private fun saveConfig() = viewModelScope.launch {
        val strategy = _state.value.strategy ?: return@launch

        val config = DomainFocusConfig(
            domain = domain,
            strategy = strategy,
            manualOverrideFocusId = _state.value.manualOverrideFocusId,
            createdAt = LocalDate.now(clock)
        )

        configRepository.upsertConfig(config)

        sendEffect(FocusEffect.Exit)
    }

    private fun sendEffect(e: FocusEffect) {
        viewModelScope.launch {
            _effect.send(e)
        }
    }
}
