package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

data class DomainBehaviorProfile(
    val domain: Domain,
    val preferredSessionDuration: Int,
    val lastUpdated: LocalDate
)
