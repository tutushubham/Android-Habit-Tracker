package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

@Entity(tableName = "domain_focus_config")
data class DomainFocusConfigEntity(
    @PrimaryKey val domain: Domain,
    val strategyType: String, // MANUAL, ROTATION, WEIGHTED, DEADLINE
    val strategyData: String?, // JSON (order / weights)
    val manualOverrideFocusId: String?,
    val createdAt: LocalDate
)
