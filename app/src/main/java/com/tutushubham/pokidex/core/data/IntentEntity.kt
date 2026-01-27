package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate


@Entity(tableName = "intents")
data class IntentEntity(
    @PrimaryKey val id: String,
    val domain: Domain,
    val title: String,          // "Publish 12 guitar videos"
    val targetCount: Int?,      // optional
    val startDate: LocalDate,
    val endDate: LocalDate,
    val priority: Int           // 1 = highest
)
