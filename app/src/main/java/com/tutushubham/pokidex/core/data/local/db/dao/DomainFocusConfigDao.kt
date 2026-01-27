package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.tutushubham.pokidex.core.data.DomainFocusConfigEntity
import com.tutushubham.pokidex.core.domain.model.Domain

@Dao
interface DomainFocusConfigDao {
    @Query("SELECT * FROM domain_focus_config WHERE domain = :domain")
    suspend fun getConfigByDomain(domain: Domain): DomainFocusConfigEntity?

    @Query("SELECT * FROM domain_focus_config")
    suspend fun getAllConfigs(): List<DomainFocusConfigEntity>

    @Upsert
    suspend fun upsertConfig(config: DomainFocusConfigEntity)
}
