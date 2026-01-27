package com.tutushubham.pokidex.core.domain.entity

import java.time.Instant

data class Artifact(
    val id: String,
    val sessionId: String,
    val type: String, // "video", "commit", "run"
    val reference: String?, // url, path, text
    val createdAt: Instant
)
