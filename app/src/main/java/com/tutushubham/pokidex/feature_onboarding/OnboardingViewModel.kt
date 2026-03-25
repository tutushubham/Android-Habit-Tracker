package com.tutushubham.pokidex.feature_onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.temporal.ChronoUnit
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.OnboardingGoalInput
import com.tutushubham.pokidex.core.domain.repository.OnboardingPersistState
import com.tutushubham.pokidex.core.domain.repository.OnboardingRepository
import com.tutushubham.pokidex.core.engine.TodayEngine
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

class OnboardingViewModel(
    private val todayEngine: TodayEngine,
    private val onboardingRepository: OnboardingRepository,
    private val anchorRepository: AnchorRepository,
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val intentRepository: IntentRepository,
    private val appStateRepository: AppStateRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _state =
        MutableStateFlow(OnboardingContract.State())

    val state: StateFlow<OnboardingContract.State> =
        _state.asStateFlow()

    private val _effect =
        Channel<OnboardingContract.Effect>(Channel.BUFFERED)

    val effect = _effect.receiveAsFlow()

    fun onEvent(event: OnboardingContract.Event) {
        when (event) {
            OnboardingContract.Event.ScreenOpened -> initializeDefaults()

            OnboardingContract.Event.GeneratePreview -> generatePreview()

            OnboardingContract.Event.AddGoalRequested -> addGoal()

            is OnboardingContract.Event.GoalAdded -> addGoal(event.goal)

            is OnboardingContract.Event.GoalRemoved -> removeGoal(event.id)

            is OnboardingContract.Event.GoalUpdated -> updateGoal(event.goal)

            is OnboardingContract.Event.BlockToggled ->
                toggleBlock(event.block, event.enabled)

            is OnboardingContract.Event.BlockMinutesChanged ->
                updateBlockMinutes(event.block, event.minutes)

            is OnboardingContract.Event.BlockDomainAssigned ->
                assignBlockDomain(event.block, event.domain)

            is OnboardingContract.Event.FocusAdded ->
                addFocus(event.domain, event.name)

            is OnboardingContract.Event.FocusRemoved ->
                removeFocus(event.domain, event.name)

            is OnboardingContract.Event.FocusRenamed ->
                renameFocus(event.domain, event.oldName, event.newName)

            is OnboardingContract.Event.StrategySelected ->
                updateStrategy(event.domain, event.strategy)

            OnboardingContract.Event.FinishClicked ->
                persistAll()
        }
    }

    private fun initializeDefaults() {
        _state.update {
            it.copy(
                goals = emptyList(),
                dayBlocks = emptyMap(),
                blockToDomain = emptyMap(),
                focuses = emptyMap(),
                strategies = emptyMap(),
                previewLines = emptyList()
            )
        }
    }

    private fun addGoal() {
        val today = LocalDate.now(clock)
        val newGoal = OnboardingGoal(
            id = UUID.randomUUID().toString(),
            title = "",
            domain = Domain.STUDIES,
            deadline = today.plusDays(30),
            targetCount = null
        )
        addGoal(newGoal)
    }

    private fun addGoal(goal: OnboardingGoal) {
        _state.update { current ->
            val updatedGoals = current.goals + goal
            val updatedFocuses = if (goal.domain !in current.focuses) {
                val suggestions = defaultFocusSuggestions(goal.domain)
                current.focuses.toMutableMap().apply { put(goal.domain, suggestions) }
            } else current.focuses
            current.copy(goals = updatedGoals, focuses = updatedFocuses)
        }
    }

    private fun removeGoal(id: String) {
        _state.update { current ->
            val updated = current.goals.filter { it.id != id }
            val removedDomain = current.goals.firstOrNull { it.id == id }?.domain
            val stillHasDomain = updated.any { it.domain == removedDomain }
            val updatedBlockToDomain = if (stillHasDomain) current.blockToDomain
            else current.blockToDomain.filterValues { it != removedDomain }
            val updatedFocuses = if (stillHasDomain) current.focuses
            else current.focuses.filterKeys { it != removedDomain }
            val updatedStrategies = if (stillHasDomain) current.strategies
            else current.strategies.filterKeys { it != removedDomain }
            current.copy(
                goals = updated,
                blockToDomain = updatedBlockToDomain,
                focuses = updatedFocuses,
                strategies = updatedStrategies
            )
        }
    }

    private fun updateGoal(goal: OnboardingGoal) {
        _state.update { current ->
            val updated = current.goals.map { if (it.id == goal.id) goal else it }
            current.copy(goals = updated)
        }
    }

    private fun generatePreview() {
        val dayBlocks = _state.value.dayBlocks
        val blockToDomain = _state.value.blockToDomain
        val focuses = _state.value.focuses
        val validGoals = _state.value.goals.filter { it.isValid }

        if (dayBlocks.isEmpty() || blockToDomain.isEmpty() || validGoals.isEmpty()) {
            _state.update { it.copy(previewLines = emptyList()) }
            return
        }

        val tomorrow = LocalDate.now(clock).plusDays(1)
        val today = LocalDate.now(clock)

        val anchors = dayBlocks.mapNotNull { (block, minutes) ->
            blockToDomain[block]?.let { domain ->
                Anchor(
                    id = "preview-$block-$domain",
                    block = block,
                    domain = domain,
                    defaultMinutes = minutes
                )
            }
        }

        val strategies = _state.value.strategies
        val focusByDomain = validGoals.map { it.domain }.toSet().associateWith { domain ->
            resolveFocusForPreview(
                domain = domain,
                strategy = strategies[domain],
                focusNames = focuses[domain].orEmpty(),
                date = tomorrow,
                createdAt = today
            )
        }

        val intents = validGoals.mapIndexed { index, goal ->
            val chosenFocus = focusByDomain[goal.domain]
            GoalIntent(
                id = "preview-${goal.id}",
                domain = goal.domain,
                title = goal.title,
                targetCount = goal.targetCount,
                startDate = today,
                endDate = goal.deadline,
                priority = index + 1,
                estimatedMinutesPerUnit = goal.estimatedMinutesPerUnit,
                focusId = chosenFocus?.id
            )
        }

        val resolveFocus: (Domain) -> Focus? = { domain -> focusByDomain[domain] }

        val plan = todayEngine.generate(
            date = tomorrow,
            intents = intents,
            anchors = anchors,
            resolveFocus = resolveFocus
        )

        val previewLines = plan.sessions.map { session ->
            val focusName = focusByDomain[session.domain]?.name ?: session.domain.name
            "${session.block.displayName()} → ${session.domain.displayName()} → $focusName → ${session.plannedMinutes} min"
        }

        _state.update { it.copy(previewLines = previewLines) }
    }

    private fun toggleBlock(block: DayBlock, enabled: Boolean) {
        _state.update { current ->
            val updated = current.dayBlocks.toMutableMap()
            val updatedBlockToDomain = current.blockToDomain.toMutableMap()
            if (enabled) {
                updated[block] = updated[block] ?: 60
            } else {
                updated.remove(block)
                updatedBlockToDomain.remove(block)
            }
            current.copy(dayBlocks = updated, blockToDomain = updatedBlockToDomain)
        }
    }

    private fun updateBlockMinutes(block: DayBlock, minutes: Int) {
        _state.update { current ->
            val updated = current.dayBlocks.toMutableMap()
            if (minutes > 0) updated[block] = minutes else updated.remove(block)
            current.copy(dayBlocks = updated)
        }
    }

    private fun assignBlockDomain(block: DayBlock, domain: Domain) {
        _state.update { current ->
            val domainsFromGoals = current.goals.map { it.domain }.toSet()
            if (block !in current.dayBlocks || domain !in domainsFromGoals) return@update current
            val updated = current.blockToDomain.toMutableMap()
            updated[block] = domain
            current.copy(blockToDomain = updated)
        }
    }

    /** Strategy-aware focus for preview: uses Rotation/Weighted/Manual/Deadline logic with synthetic Focus(id=name). */
    private fun resolveFocusForPreview(
        domain: Domain,
        strategy: FocusStrategy?,
        focusNames: List<String>,
        date: LocalDate,
        createdAt: LocalDate
    ): Focus? {
        if (focusNames.isEmpty()) return null
        val focuses = focusNames.map { name -> Focus(id = name, domain = domain, name = name) }
        return when (strategy) {
            is FocusStrategy.Rotation -> {
                val order = strategy.order.filter { it in focusNames }
                if (order.isEmpty()) focuses.first()
                else {
                    val daysSinceStart = ChronoUnit.DAYS.between(createdAt, date).toInt()
                    val index = daysSinceStart % order.size
                    val focusId = order[index]
                    focuses.firstOrNull { it.id == focusId } ?: focuses.first()
                }
            }
            is FocusStrategy.Weighted -> {
                val expanded = strategy.weights.flatMap { (id, w) -> List(w.coerceAtLeast(0)) { id } }
                if (expanded.isEmpty()) focuses.first()
                else {
                    val daysSinceStart = ChronoUnit.DAYS.between(createdAt, date).toInt()
                    val index = daysSinceStart % expanded.size
                    val focusId = expanded[index]
                    focuses.firstOrNull { it.id == focusId } ?: focuses.first()
                }
            }
            is FocusStrategy.Manual, is FocusStrategy.DeadlineDriven, null -> focuses.first()
        }
    }

    private fun defaultFocusSuggestions(domain: Domain): List<String> = when (domain) {
        Domain.STUDIES -> listOf("DSA", "Android", "System Design")
        Domain.FITNESS -> listOf("Running", "Gym", "Weight Cut")
        Domain.WORK -> listOf("Deep Work", "Meetings", "Admin")
        Domain.HOBBY -> listOf("Guitar", "Reading", "Creative")
    }

    private fun addFocus(domain: Domain, name: String) {
        _state.update { current ->
            val updated = current.focuses.toMutableMap()
            val list = updated[domain]?.toMutableList() ?: mutableListOf()

            if (!list.contains(name)) {
                list.add(name)
            }

            updated[domain] = list

            current.copy(focuses = updated)
        }
    }

    private fun removeFocus(domain: Domain, name: String) {
        _state.update { current ->
            val updated = current.focuses.toMutableMap()
            val list = updated[domain]?.toMutableList() ?: mutableListOf()

            list.remove(name)
            updated[domain] = list

            current.copy(focuses = updated)
        }
    }

    private fun renameFocus(domain: Domain, oldName: String, newName: String) {
        if (newName.isBlank()) return
        _state.update { current ->
            val updated = current.focuses.toMutableMap()
            val list = updated.getOrDefault(domain, emptyList()).toMutableList()
            val idx = list.indexOf(oldName)
            if (idx >= 0) list[idx] = newName.trim()
            updated[domain] = list
            current.copy(focuses = updated)
        }
    }

    private fun updateStrategy(domain: Domain, strategy: FocusStrategy) {
        _state.update { current ->
            val updated = current.strategies.toMutableMap()
            updated[domain] = strategy
            current.copy(strategies = updated)
        }
    }

    private fun persistAll() = viewModelScope.launch {
        val state = _state.value
        val goalInputs = state.goals.filter { it.isValid }.map { g ->
            OnboardingGoalInput(
                id = g.id,
                title = g.title,
                domain = g.domain,
                deadline = g.deadline,
                targetCount = g.targetCount,
                estimatedMinutesPerUnit = g.estimatedMinutesPerUnit
            )
        }
        val persistState = OnboardingPersistState(
            dayBlocks = state.dayBlocks,
            blockToDomain = state.blockToDomain,
            focuses = state.focuses,
            strategies = state.strategies,
            goals = goalInputs
        )

        onboardingRepository.persist(persistState)
            .fold(
                onSuccess = { _effect.send(OnboardingContract.Effect.ExitOnboarding) },
                onFailure = { t ->
                    _effect.send(
                        OnboardingContract.Effect.ShowMessage(
                            t.message ?: "Error saving setup"
                        )
                    )
                }
            )
    }
}
