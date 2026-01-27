package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

@Entity(
    tableName = "focuses",
    indices = [Index(value = ["domain", "name"], unique = true)]
)
data class FocusEntity(
    @PrimaryKey val id: String,
    val domain: Domain,
    val name: String,
    val weight: Int = 1,
    val deadline: LocalDate? = null
)
