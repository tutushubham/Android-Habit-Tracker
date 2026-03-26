package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.engine.FocusStrategyResolver
import java.time.LocalDate

class FocusResolverUseCase(
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val overrideRepository: DailyFocusOverrideRepository
) {
    suspend fun resolve(domain: Domain, date: LocalDate): Focus? {
        overrideRepository.getOverride(domain, date)?.let { override ->
            focusRepository.getFocusById(override.focusId)?.let { return it }
        }

        val focuses = focusRepository.getFocusesByDomain(domain)
        if (focuses.isEmpty()) return null

        val config = configRepository.getConfig(domain)
            ?: return focuses.first()

        return FocusStrategyResolver.resolve(config, focuses, date)
    }

    fun resolveWithConfig(
        config: DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? = FocusStrategyResolver.resolve(config, focuses, date)
}
