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
    val priority: Int, // 1 = highest; used as fallback when urgency not applicable
    /** Minutes per "unit" when targetCount is set (e.g. 25 for DSA, 180 for guitar song). Null = time-based. */
    val estimatedMinutesPerUnit: Int? = null,
    /** Explicit focus for this intent; avoids brittle title matching. */
    val focusId: String? = null
)
