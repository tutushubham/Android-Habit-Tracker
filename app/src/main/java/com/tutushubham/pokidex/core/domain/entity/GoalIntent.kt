package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

data class GoalIntent(
    val id: String,
    val domain: Domain,
    val title: String,
    val targetCount: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val priority: Int // 1 = highest
)
