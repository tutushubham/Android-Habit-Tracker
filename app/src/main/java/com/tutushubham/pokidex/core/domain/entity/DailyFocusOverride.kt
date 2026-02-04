package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

data class DailyFocusOverride(
    val date: LocalDate,
    val domain: Domain,
    val focusId: String
)
