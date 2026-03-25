package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tutushubham.pokidex.core.data.DomainBehaviorProfileEntity
import com.tutushubham.pokidex.core.domain.model.Domain

@Dao
interface DomainBehaviorProfileDao {
    @Query("SELECT * FROM domain_behavior_profiles WHERE domain = :domain")
    suspend fun getProfile(domain: Domain): DomainBehaviorProfileEntity?

    @Query("SELECT * FROM domain_behavior_profiles")
    suspend fun getAll(): List<DomainBehaviorProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: DomainBehaviorProfileEntity)
}
