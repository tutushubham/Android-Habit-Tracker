package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "sessions",
    indices = [Index("date")]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val intentId: String,
    val domain: Domain,
    val date: LocalDate,

    val block: DayBlock,
    val plannedMinutes: Int,
    val actualMinutes: Int?,

    val status: SessionStatus,
    val skipReason: SkipReason?,

    val startedAt: Instant?,
    val endedAt: Instant?
)
