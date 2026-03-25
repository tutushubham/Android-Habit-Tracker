package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.AppDatabase
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.OnboardingPersistState
import com.tutushubham.pokidex.core.domain.repository.OnboardingRepository
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class OnboardingRepositoryImpl(
    private val database: AppDatabase,
    private val anchorRepository: AnchorRepository,
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val intentRepository: IntentRepository,
    private val appStateRepository: AppStateRepository,
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone()
) : OnboardingRepository {

    override suspend fun persist(state: OnboardingPersistState): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val today = LocalDate.now(clock)

                database.withTransaction {
                    state.dayBlocks.forEach { (block, minutes) ->
                        val domain = state.blockToDomain[block] ?: return@forEach
                        anchorRepository.insertAnchor(
                            Anchor(
                                id = UUID.randomUUID().toString(),
                                block = block,
                                domain = domain,
                                defaultMinutes = minutes
                            )
                        )
                    }

                    val createdFocuses = mutableMapOf<Domain, List<Focus>>()

                    state.focuses.forEach { (domain, names) ->
                        val focusList = names.map { name ->
                            val focus = Focus(
                                id = UUID.randomUUID().toString(),
                                domain = domain,
                                name = name
                            )
                            focusRepository.insertFocus(focus)
                            focus
                        }
                        createdFocuses[domain] = focusList
                    }

                    state.strategies.forEach { (domain, strategy) ->
                        val strategyWithIds = when (val s = strategy) {
                            is FocusStrategy.Rotation -> FocusStrategy.Rotation(
                                order = createdFocuses[domain]?.map { it.id } ?: emptyList()
                            )
                            is FocusStrategy.Weighted -> FocusStrategy.Weighted(
                                weights = createdFocuses[domain]?.associate { it.id to (s.weights[it.name] ?: 1) } ?: emptyMap()
                            )
                            else -> s
                        }
                        configRepository.upsertConfig(
                            DomainFocusConfig(
                                domain = domain,
                                strategy = strategyWithIds,
                                manualOverrideFocusId = null,
                                createdAt = today
                            )
                        )
                    }

                    state.goals.forEachIndexed { index, goal ->
                        val firstFocusId = createdFocuses[goal.domain]?.firstOrNull()?.id
                        intentRepository.insertIntent(
                            GoalIntent(
                                id = goal.id,
                                domain = goal.domain,
                                title = goal.title,
                                targetCount = goal.targetCount,
                                startDate = today,
                                endDate = goal.deadline,
                                priority = index + 1,
                                estimatedMinutesPerUnit = goal.estimatedMinutesPerUnit,
                                focusId = firstFocusId
                            )
                        )
                    }
                }

                appStateRepository.setOnboardingCompleted()
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
}
