package com.tutushubham.pokidex.feature_focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.entity.Focus
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
import java.util.UUID

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

            is FocusEvent.AddFocus ->
                addFocus(event.name, event.deadline)

            is FocusEvent.DeleteFocus ->
                deleteFocus(event.focusId)

            is FocusEvent.UpdateFocusName ->
                updateFocusName(event.focusId, event.newName)
        }
    }

    private fun load() = viewModelScope.launch {
        reloadFocusData(initialOpen = true)
    }

    private suspend fun reloadFocusData(initialOpen: Boolean = false) {
        try {
            val focuses = focusRepository.getFocusesByDomain(domain)
            val config = configRepository.getConfig(domain)

            _state.update {
                it.copy(
                    focuses = focuses,
                    strategy = config?.strategy,
                    manualOverrideFocusId = config?.manualOverrideFocusId,
                    isLoading = if (initialOpen) false else it.isLoading,
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

    private fun addFocus(name: String, deadline: LocalDate?) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        try {
            val newFocus = Focus(
                id = UUID.randomUUID().toString(),
                domain = domain,
                name = trimmed,
                weight = 1,
                deadline = deadline
            )
            focusRepository.insertFocus(newFocus)
            reloadFocusData()
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
        }
    }

    private fun deleteFocus(focusId: String) = viewModelScope.launch {
        try {
            sanitizeStrategyAfterDelete(focusId)
            focusRepository.deleteFocus(focusId)
            _state.update {
                val clearedManual =
                    if (it.manualOverrideFocusId == focusId) null else it.manualOverrideFocusId
                it.copy(manualOverrideFocusId = clearedManual)
            }
            configRepository.getConfig(domain)?.let { cfg ->
                configRepository.upsertConfig(
                    cfg.copy(
                        strategy = _state.value.strategy ?: cfg.strategy,
                        manualOverrideFocusId = _state.value.manualOverrideFocusId
                    )
                )
            }
            reloadFocusData()
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
        }
    }

    private fun sanitizeStrategyAfterDelete(deletedId: String) {
        val s = _state.value.strategy ?: return
        val updated = when (s) {
            is FocusStrategy.Rotation ->
                s.copy(order = s.order.filter { it != deletedId })

            is FocusStrategy.Weighted ->
                s.copy(weights = s.weights.filterKeys { it != deletedId })

            else -> s
        }
        if (updated != s) {
            _state.update { it.copy(strategy = updated) }
        }
    }

    private fun updateFocusName(focusId: String, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@launch
        try {
            val focus = focusRepository.getFocusById(focusId) ?: return@launch
            focusRepository.updateFocus(focus.copy(name = trimmed))
            reloadFocusData()
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
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
            val strategy = _state.value.strategy
            val focuses = _state.value.focuses
            val today = LocalDate.now(clock)

            if (focuses.isEmpty()) {
                _state.update {
                    it.copy(
                        preview = emptyList(),
                        weeklyMomentum = List(7) { 0f },
                        currentFocusTitle = null
                    )
                }
                return@launch
            }

            if (strategy == null) {
                _state.update {
                    it.copy(
                        preview = emptyList(),
                        weeklyMomentum = List(7) { 0.2f },
                        currentFocusTitle = focuses.first().name
                    )
                }
                return@launch
            }

            val draftConfig = DomainFocusConfig(
                domain = domain,
                strategy = strategy,
                manualOverrideFocusId = _state.value.manualOverrideFocusId,
                createdAt = today
            )

            val dailyResolved = (0 until days).map { offset ->
                val date = today.plusDays(offset.toLong())
                focusResolver.resolveWithConfig(draftConfig, focuses, date)
            }

            val preview = dailyResolved.mapNotNull { it?.name }

            val weeklyMomentum = dailyResolved.map { resolved ->
                when {
                    resolved == null -> 0.12f
                    else -> {
                        val w = resolved.weight.coerceIn(1, 10)
                        (0.4f + 0.6f * (w / 10f)).coerceIn(0.05f, 1f)
                    }
                }
            }

            val todayResolved = dailyResolved.firstOrNull()

            _state.update {
                it.copy(
                    preview = preview,
                    weeklyMomentum = weeklyMomentum,
                    currentFocusTitle = todayResolved?.name
                )
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
