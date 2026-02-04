package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.DailyFocusOverrideEntity
import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride

fun DailyFocusOverrideEntity.toDomain(): DailyFocusOverride =
    DailyFocusOverride(date = date, domain = domain, focusId = focusId)

fun DailyFocusOverride.toEntity(): DailyFocusOverrideEntity =
    DailyFocusOverrideEntity(date = date, domain = domain, focusId = focusId)
