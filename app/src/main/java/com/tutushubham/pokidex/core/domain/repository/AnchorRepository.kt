package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain

interface AnchorRepository {
    suspend fun getAllAnchors(): List<Anchor>
    suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain): Anchor?
    suspend fun insertAnchor(anchor: Anchor)
    suspend fun updateAnchor(anchor: Anchor)
    suspend fun deleteAnchor(id: String)
}
