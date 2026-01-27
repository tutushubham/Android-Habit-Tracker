package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.IntentDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import java.time.LocalDate

class IntentRepositoryImpl(
    private val dao: IntentDao
) : IntentRepository {

    override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate): List<GoalIntent> {
        return dao.getIntentsForDateRange(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun insertIntent(intent: GoalIntent) {
        dao.insert(intent.toEntity())
    }

    override suspend fun updateIntent(intent: GoalIntent) {
        dao.update(intent.toEntity())
    }

    override suspend fun getIntentById(id: String): GoalIntent? {
        return dao.getIntentById(id)?.toDomain()
    }
}
