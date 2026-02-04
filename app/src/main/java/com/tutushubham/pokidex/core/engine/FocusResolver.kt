package com.tutushubham.pokidex.core.engine

import android.util.Log
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val TAG = "FocusResolver"

open class FocusResolver(
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val overrideRepository: DailyFocusOverrideRepository
) {
    open suspend fun resolve(domain: Domain, date: LocalDate): Focus? {
        overrideRepository.getOverride(domain, date)?.let { override ->
            focusRepository.getFocusById(override.focusId)?.let { return it }
            // Override exists but focus was deleted — fall through to normal strategy
        }

        val focuses = focusRepository.getFocusesByDomain(domain)
        if (focuses.isEmpty()) return null

        val config = configRepository.getConfig(domain)
            ?: return focuses.first() // safe default

        return when (config.strategy) {
            is FocusStrategy.Manual ->
                resolveManual(config, focuses)

            is FocusStrategy.Rotation ->
                resolveRotation(config, focuses, date)

            is FocusStrategy.Weighted ->
                resolveWeighted(config, focuses, date)

            is FocusStrategy.DeadlineDriven ->
                resolveDeadline(focuses, date)
        }
    }

    /**
     * Resolves focus for a given config and focuses list without reading from repositories.
     * Used for preview generation in the Focus flow.
     */
    open fun resolveWithConfig(
        config: DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? {
        if (focuses.isEmpty()) return null
        return when (config.strategy) {
            is FocusStrategy.Manual -> resolveManual(config, focuses)
            is FocusStrategy.Rotation -> resolveRotation(config, focuses, date)
            is FocusStrategy.Weighted -> resolveWeighted(config, focuses, date)
            is FocusStrategy.DeadlineDriven -> resolveDeadline(focuses, date)
        }
    }

    private fun resolveManual(
        config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig,
        focuses: List<Focus>
    ): Focus? {
        val manual = config.manualOverrideFocusId
            ?.let { id -> focuses.firstOrNull { it.id == id } }
        
        if (config.manualOverrideFocusId != null && manual == null) {
            try {
                Log.w(
                    TAG,
                    "Manual override focus ID '${config.manualOverrideFocusId}' not found in domain ${config.domain}. " +
                            "Available focus IDs: ${focuses.map { it.id }}"
                )
            } catch (e: RuntimeException) {
                // Log not available in unit tests - ignore
            }
        }
        
        return manual ?: focuses.firstOrNull()
    }

    private fun resolveRotation(
        config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? {
        val strategy = config.strategy as FocusStrategy.Rotation
        if (strategy.order.isEmpty()) return focuses.firstOrNull()

        val daysSinceStart = ChronoUnit.DAYS.between(
            config.createdAt,
            date
        ).toInt()

        val index = daysSinceStart % strategy.order.size
        val focusId = strategy.order[index]

        return focuses.firstOrNull { it.id == focusId }
            ?: focuses.firstOrNull()
    }

    private fun resolveWeighted(
        config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? {
        val strategy = config.strategy as FocusStrategy.Weighted
        if (strategy.weights.isEmpty()) return focuses.firstOrNull()

        val daysSinceStart = ChronoUnit.DAYS.between(
            config.createdAt,
            date
        ).toInt()

        // Expand weights into a list (e.g., {A: 2, B: 1} -> [A, A, B])
        val expanded = strategy.weights.flatMap { (id, weight) ->
            List(weight) { id }
        }

        if (expanded.isEmpty()) return focuses.firstOrNull()

        val index = daysSinceStart % expanded.size
        val focusId = expanded[index]

        return focuses.firstOrNull { it.id == focusId }
            ?: focuses.firstOrNull()
    }

    private fun resolveDeadline(
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? {
        // Filter for upcoming deadlines only (explicitly prefer earliest upcoming, not past)
        val deadlineFocuses = focuses.filter { focus ->
            focus.deadline != null && focus.deadline >= date
        }
        
        if (deadlineFocuses.isEmpty()) {
            // No active deadlines, return first focus
            return focuses.firstOrNull()
        }

        // Return focus with closest deadline using ChronoUnit for clarity
        return deadlineFocuses.minByOrNull { focus ->
            focus.deadline?.let { 
                ChronoUnit.DAYS.between(date, it)
            } ?: Long.MAX_VALUE
        }
    }
}
