package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.LocalDate

data class DomainFocusConfig(
    val domain: Domain,
    val strategy: FocusStrategy,
    val manualOverrideFocusId: String? = null,
    val createdAt: LocalDate
)
