package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.usecase.FocusResolverUseCase
import java.time.LocalDate

/**
 * Backward-compatible wrapper around [FocusResolverUseCase].
 * Existing call sites continue to work without changes.
 * New code should prefer [FocusResolverUseCase] directly.
 */
open class FocusResolver(
    focusRepository: FocusRepository,
    configRepository: DomainFocusConfigRepository,
    overrideRepository: DailyFocusOverrideRepository
) {
    private val delegate = FocusResolverUseCase(
        focusRepository, configRepository, overrideRepository
    )

    open suspend fun resolve(domain: Domain, date: LocalDate): Focus? =
        delegate.resolve(domain, date)

    open fun resolveWithConfig(
        config: DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? = delegate.resolveWithConfig(config, focuses, date)
}
