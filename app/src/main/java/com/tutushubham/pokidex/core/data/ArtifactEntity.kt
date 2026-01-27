package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,          // "video", "commit", "run"
    val reference: String?,    // url, path, text
    val createdAt: Instant
)
