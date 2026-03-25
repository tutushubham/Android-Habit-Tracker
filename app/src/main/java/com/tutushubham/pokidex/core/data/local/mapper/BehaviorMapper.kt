package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.DomainBehaviorProfileEntity
import com.tutushubham.pokidex.core.data.UserIntentStatsEntity
import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats

fun UserIntentStatsEntity.toDomain() = UserIntentStats(
    intentId = intentId,
    learnedMinutesPerUnit = learnedMinutesPerUnit,
    confidence = confidence,
    lastUpdated = lastUpdated
)

fun UserIntentStats.toEntity() = UserIntentStatsEntity(
    intentId = intentId,
    learnedMinutesPerUnit = learnedMinutesPerUnit,
    confidence = confidence,
    lastUpdated = lastUpdated
)

fun DomainBehaviorProfileEntity.toDomain() = DomainBehaviorProfile(
    domain = domain,
    preferredSessionDuration = preferredSessionDuration,
    lastUpdated = lastUpdated
)

fun DomainBehaviorProfile.toEntity() = DomainBehaviorProfileEntity(
    domain = domain,
    preferredSessionDuration = preferredSessionDuration,
    lastUpdated = lastUpdated
)
