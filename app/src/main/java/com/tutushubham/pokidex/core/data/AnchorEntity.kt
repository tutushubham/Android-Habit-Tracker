package com.tutushubham.pokidex.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain

@Entity(tableName = "anchors")
data class AnchorEntity(
    @PrimaryKey val id: String,
    val block: DayBlock,
    val domain: Domain,
    val defaultMinutes: Int
)
