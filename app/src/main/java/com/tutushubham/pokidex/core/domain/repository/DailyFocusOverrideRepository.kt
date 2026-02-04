package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

interface DailyFocusOverrideRepository {
    suspend fun getOverride(domain: Domain, date: LocalDate): DailyFocusOverride?
    suspend fun setOverride(override: DailyFocusOverride)
    suspend fun clearOverride(domain: Domain, date: LocalDate)
}
