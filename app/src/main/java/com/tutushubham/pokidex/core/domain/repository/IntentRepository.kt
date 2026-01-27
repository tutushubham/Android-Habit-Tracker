package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import java.time.LocalDate

interface IntentRepository {
    suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate): List<GoalIntent>
    suspend fun insertIntent(intent: GoalIntent)
    suspend fun updateIntent(intent: GoalIntent)
    suspend fun getIntentById(id: String): GoalIntent?
}
