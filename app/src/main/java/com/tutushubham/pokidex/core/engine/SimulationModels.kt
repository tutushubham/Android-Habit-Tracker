package com.tutushubham.pokidex.core.engine

import java.time.LocalDate

data class SimulationInput(
    val deadlineAdjustments: Map<String, LocalDate> = emptyMap(),
    val dailyCapacityMinutes: Int? = null,
    val sessionDurationOverrides: Map<String, Int> = emptyMap()
)

data class SimulationResult(
    val predictedCompletionDates: Map<String, LocalDate>,
    val overloadChanges: List<OverloadChange>,
    val requiredDailyEffort: Map<String, Double>,
    val feasibilityScore: Double
)

data class OverloadChange(
    val intentId: String,
    val wasBefore: Boolean,
    val isAfter: Boolean,
    val detail: OverloadDetail?
)
