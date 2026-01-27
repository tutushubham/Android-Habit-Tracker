package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "signals")
data class SignalEntity(
    @PrimaryKey val id: String,
    val artifactId: String,
    val source: String,        // "GitHub", "GoogleFit"
    val externalId: String?,
    val syncedAt: Instant
)
