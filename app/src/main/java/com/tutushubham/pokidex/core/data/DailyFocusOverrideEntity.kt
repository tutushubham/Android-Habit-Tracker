package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

@Entity(
    tableName = "daily_focus_override",
    primaryKeys = ["date", "domain"],
    indices = [Index("domain")]
)
data class DailyFocusOverrideEntity(
    val date: LocalDate,
    val domain: Domain,
    val focusId: String
)
