package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Session(
    val id: String,
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
) {
    companion object {
        fun planned(
            intent: GoalIntent,
            date: LocalDate,
            block: DayBlock,
            minutes: Int
        ): Session = Session(
            id = UUID.randomUUID().toString(),
            intentId = intent.id,
            domain = intent.domain,
            date = date,
            block = block,
            plannedMinutes = minutes,
            actualMinutes = null,
            status = SessionStatus.PLANNED,
            skipReason = null,
            startedAt = null,
            endedAt = null
        )
    }
}
