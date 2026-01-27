package com.tutushubham.pokidex.core.domain

import androidx.room.Embedded
import androidx.room.Relation
import com.tutushubham.pokidex.core.data.ArtifactEntity
import com.tutushubham.pokidex.core.data.SessionEntity

data class SessionWithArtifacts(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val artifacts: List<ArtifactEntity>
)
