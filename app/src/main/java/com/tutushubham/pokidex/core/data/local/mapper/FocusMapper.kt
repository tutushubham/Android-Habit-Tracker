package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.FocusEntity
import com.tutushubham.pokidex.core.domain.entity.Focus

fun FocusEntity.toDomain(): Focus {
    return Focus(
        id = id,
        domain = domain,
        name = name,
        weight = weight,
        deadline = deadline
    )
}

fun Focus.toEntity(): FocusEntity {
    return FocusEntity(
        id = id,
        domain = domain,
        name = name,
        weight = weight,
        deadline = deadline
    )
}
