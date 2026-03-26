package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain

interface BehaviorRepository {
    suspend fun getIntentStats(intentId: String): UserIntentStats?
    suspend fun getAllIntentStats(): List<UserIntentStats>
    suspend fun saveIntentStats(stats: UserIntentStats)
    suspend fun getDomainProfile(domain: Domain): DomainBehaviorProfile?
    suspend fun getAllDomainProfiles(): List<DomainBehaviorProfile>
    suspend fun saveDomainProfile(profile: DomainBehaviorProfile)
    suspend fun clearAll()
}
