package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.CaptureEntity
import com.tutushubham.pokidex.core.domain.entity.Capture

fun CaptureEntity.toDomain(): Capture {
    return Capture(
        id = id,
        content = content,
        createdAt = createdAt,
        resolved = resolved,
        resolvedSessionId = resolvedSessionId
    )
}

fun Capture.toEntity(): CaptureEntity {
    return CaptureEntity(
        id = id,
        content = content,
        createdAt = createdAt,
        resolved = resolved,
        resolvedSessionId = resolvedSessionId
    )
}
