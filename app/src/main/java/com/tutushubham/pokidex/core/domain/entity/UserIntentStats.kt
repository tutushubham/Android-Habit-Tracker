package com.tutushubham.pokidex.core.domain.entity

import java.time.LocalDate

data class UserIntentStats(
    val intentId: String,
    val learnedMinutesPerUnit: Double?,
    val confidence: Double,
    val lastUpdated: LocalDate
)
