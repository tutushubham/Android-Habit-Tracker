package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.AnchorEntity
import com.tutushubham.pokidex.core.domain.entity.Anchor

fun AnchorEntity.toDomain(): Anchor {
    return Anchor(
        id = id,
        block = block,
        domain = domain,
        defaultMinutes = defaultMinutes
    )
}

fun Anchor.toEntity(): AnchorEntity {
    return AnchorEntity(
        id = id,
        block = block,
        domain = domain,
        defaultMinutes = defaultMinutes
    )
}
