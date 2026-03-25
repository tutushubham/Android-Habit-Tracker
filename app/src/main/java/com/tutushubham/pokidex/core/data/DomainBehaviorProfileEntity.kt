package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

@Entity(tableName = "domain_behavior_profiles")
data class DomainBehaviorProfileEntity(
    @PrimaryKey val domain: Domain,
    val preferredSessionDuration: Int,
    val lastUpdated: LocalDate
)
