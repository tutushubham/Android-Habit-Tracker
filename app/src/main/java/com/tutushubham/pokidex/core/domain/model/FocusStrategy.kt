package com.tutushubham.pokidex.core.domain.model

sealed interface FocusStrategy {
    data object Manual : FocusStrategy

    data class Rotation(
        val order: List<String> // focus IDs
    ) : FocusStrategy

    data class Weighted(
        val weights: Map<String, Int>
    ) : FocusStrategy

    data object DeadlineDriven : FocusStrategy
}
