package com.tutushubham.pokidex.core.domain.entity

import java.time.Instant

enum class SignalType {
    RECOMMENDATION_ACCEPTED,
    RECOMMENDATION_IGNORED,
    FOCUS_OVERRIDE_USED,
    SIMULATION_RUN,
    SESSION_EXTENDED,
    SESSION_SHORTENED,
    SETTINGS_CHANGED
}

data class UserSignal(
    val id: String,
    val type: SignalType,
    val intentId: String? = null,
    val timestamp: Instant = Instant.now(),
    val metadata: String? = null
)
