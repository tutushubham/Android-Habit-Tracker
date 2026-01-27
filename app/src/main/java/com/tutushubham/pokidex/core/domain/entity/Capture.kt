package com.tutushubham.pokidex.core.domain.entity

import java.time.Instant

data class Capture(
    val id: String,
    val content: String,
    val createdAt: Instant,
    val resolved: Boolean,
    val resolvedSessionId: String?
)
