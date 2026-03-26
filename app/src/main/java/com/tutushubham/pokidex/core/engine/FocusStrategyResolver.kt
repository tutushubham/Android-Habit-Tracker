package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FocusStrategyResolver {

    fun resolve(config: DomainFocusConfig, focuses: List<Focus>, date: LocalDate): Focus? {
        if (focuses.isEmpty()) return null
        return when (config.strategy) {
            is FocusStrategy.Manual -> resolveManual(config, focuses)
            is FocusStrategy.Rotation -> resolveRotation(config, focuses, date)
            is FocusStrategy.Weighted -> resolveWeighted(config, focuses, date)
            is FocusStrategy.DeadlineDriven -> resolveDeadline(focuses, date)
        }
    }

    private fun resolveManual(config: DomainFocusConfig, focuses: List<Focus>): Focus? {
        val manual = config.manualOverrideFocusId
            ?.let { id -> focuses.firstOrNull { it.id == id } }
        return manual ?: focuses.firstOrNull()
    }

    private fun resolveRotation(config: DomainFocusConfig, focuses: List<Focus>, date: LocalDate): Focus? {
        val strategy = config.strategy as FocusStrategy.Rotation
        if (strategy.order.isEmpty()) return focuses.firstOrNull()
        val daysSinceStart = ChronoUnit.DAYS.between(config.createdAt, date).toInt()
        val index = daysSinceStart % strategy.order.size
        val focusId = strategy.order[index]
        return focuses.firstOrNull { it.id == focusId } ?: focuses.firstOrNull()
    }

    private fun resolveWeighted(config: DomainFocusConfig, focuses: List<Focus>, date: LocalDate): Focus? {
        val strategy = config.strategy as FocusStrategy.Weighted
        if (strategy.weights.isEmpty()) return focuses.firstOrNull()
        val daysSinceStart = ChronoUnit.DAYS.between(config.createdAt, date).toInt()
        val expanded = strategy.weights.flatMap { (id, weight) -> List(weight) { id } }
        if (expanded.isEmpty()) return focuses.firstOrNull()
        val index = daysSinceStart % expanded.size
        val focusId = expanded[index]
        return focuses.firstOrNull { it.id == focusId } ?: focuses.firstOrNull()
    }

    private fun resolveDeadline(focuses: List<Focus>, date: LocalDate): Focus? {
        val deadlineFocuses = focuses.filter { focus ->
            focus.deadline != null && focus.deadline >= date
        }
        if (deadlineFocuses.isEmpty()) return focuses.firstOrNull()
        return deadlineFocuses.minByOrNull { focus ->
            focus.deadline?.let { ChronoUnit.DAYS.between(date, it) } ?: Long.MAX_VALUE
        }
    }
}
