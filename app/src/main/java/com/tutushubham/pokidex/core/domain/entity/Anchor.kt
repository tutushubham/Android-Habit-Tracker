package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain

data class Anchor(
    val id: String,
    val block: DayBlock,
    val domain: Domain,
    val defaultMinutes: Int
)
