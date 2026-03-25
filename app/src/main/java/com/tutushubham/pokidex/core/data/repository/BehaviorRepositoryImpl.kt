package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.DomainBehaviorProfileDao
import com.tutushubham.pokidex.core.data.local.db.dao.UserIntentStatsDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository

class BehaviorRepositoryImpl(
    private val statsDao: UserIntentStatsDao,
    private val profileDao: DomainBehaviorProfileDao
) : BehaviorRepository {

    override suspend fun getIntentStats(intentId: String): UserIntentStats? {
        return statsDao.getStats(intentId)?.toDomain()
    }

    override suspend fun getAllIntentStats(): List<UserIntentStats> {
        return statsDao.getAll().map { it.toDomain() }
    }

    override suspend fun saveIntentStats(stats: UserIntentStats) {
        statsDao.upsert(stats.toEntity())
    }

    override suspend fun getDomainProfile(domain: Domain): DomainBehaviorProfile? {
        return profileDao.getProfile(domain)?.toDomain()
    }

    override suspend fun getAllDomainProfiles(): List<DomainBehaviorProfile> {
        return profileDao.getAll().map { it.toDomain() }
    }

    override suspend fun saveDomainProfile(profile: DomainBehaviorProfile) {
        profileDao.upsert(profile.toEntity())
    }
}
