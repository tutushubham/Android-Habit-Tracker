package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

data class Focus(
    val id: String,
    val domain: Domain,
    val name: String, // "DSA", "Android", "Guitar"
    val weight: Int = 1, // for weighted rotation
    val deadline: LocalDate? = null
) {
    fun isDeadlineActive(today: LocalDate): Boolean =
        deadline != null && deadline >= today
}
