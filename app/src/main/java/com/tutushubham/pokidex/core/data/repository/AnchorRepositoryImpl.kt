package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.AnchorDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository

class AnchorRepositoryImpl(
    private val dao: AnchorDao
) : AnchorRepository {

    override suspend fun getAllAnchors(): List<Anchor> {
        return dao.getAllAnchors().map { it.toDomain() }
    }

    override suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain): Anchor? {
        return dao.getAnchorByBlockAndDomain(block, domain)?.toDomain()
    }

    override suspend fun insertAnchor(anchor: Anchor) {
        dao.insert(anchor.toEntity())
    }

    override suspend fun updateAnchor(anchor: Anchor) {
        dao.update(anchor.toEntity())
    }
}
