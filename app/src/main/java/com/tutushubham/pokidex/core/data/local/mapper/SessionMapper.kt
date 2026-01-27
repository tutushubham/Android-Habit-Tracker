package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.SessionEntity
import com.tutushubham.pokidex.core.domain.entity.Session

fun SessionEntity.toDomain(): Session {
    return Session(
        id = id,
        intentId = intentId,
        domain = domain,
        date = date,
        block = block,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        status = status,
        skipReason = skipReason,
        startedAt = startedAt,
        endedAt = endedAt
    )
}

fun Session.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        intentId = intentId,
        domain = domain,
        date = date,
        block = block,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        status = status,
        skipReason = skipReason,
        startedAt = startedAt,
        endedAt = endedAt
    )
}
