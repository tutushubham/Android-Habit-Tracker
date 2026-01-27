package com.tutushubham.pokidex.core.domain.entity

import java.time.Instant

data class Signal(
    val id: String,
    val artifactId: String,
    val source: String, // "GitHub", "GoogleFit"
    val externalId: String?,
    val syncedAt: Instant
)
