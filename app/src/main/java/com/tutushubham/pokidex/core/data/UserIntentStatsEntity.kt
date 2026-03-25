package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "user_intent_stats")
data class UserIntentStatsEntity(
    @PrimaryKey val intentId: String,
    val learnedMinutesPerUnit: Double?,
    val confidence: Double,
    val lastUpdated: LocalDate
)
