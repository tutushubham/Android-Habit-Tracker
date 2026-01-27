package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val content: String,
    val createdAt: Instant,
    val resolved: Boolean,
    val resolvedSessionId: String?
)
