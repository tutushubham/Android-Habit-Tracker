package com.tutushubham.pokidex.core.engine

import java.time.LocalDate

data class PredictionInsight(
    val predictedDate: LocalDate?,
    val confidence: Double,
    val confidenceLabel: String
)
