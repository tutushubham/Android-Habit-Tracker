package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.IntentEntity
import com.tutushubham.pokidex.core.domain.entity.GoalIntent

fun IntentEntity.toDomain(): GoalIntent {
    return GoalIntent(
        id = id,
        domain = domain,
        title = title,
        targetCount = targetCount,
        startDate = startDate,
        endDate = endDate,
        priority = priority
    )
}

fun GoalIntent.toEntity(): IntentEntity {
    return IntentEntity(
        id = id,
        domain = domain,
        title = title,
        targetCount = targetCount,
        startDate = startDate,
        endDate = endDate,
        priority = priority
    )
}
