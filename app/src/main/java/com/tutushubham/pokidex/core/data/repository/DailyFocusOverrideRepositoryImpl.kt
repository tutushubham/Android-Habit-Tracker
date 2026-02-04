package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.DailyFocusOverrideDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import java.time.LocalDate

class DailyFocusOverrideRepositoryImpl(
    private val dao: DailyFocusOverrideDao
) : DailyFocusOverrideRepository {

    override suspend fun getOverride(domain: Domain, date: LocalDate): DailyFocusOverride? =
        dao.getOverride(domain, date)?.toDomain()

    override suspend fun setOverride(override: DailyFocusOverride) {
        dao.setOverride(override.toEntity())
    }

    override suspend fun clearOverride(domain: Domain, date: LocalDate) {
        dao.clearOverride(domain, date)
    }
}
