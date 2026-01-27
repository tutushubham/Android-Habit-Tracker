package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.DomainFocusConfigDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository

class DomainFocusConfigRepositoryImpl(
    private val dao: DomainFocusConfigDao
) : DomainFocusConfigRepository {

    override suspend fun getConfig(domain: Domain): DomainFocusConfig? {
        return dao.getConfigByDomain(domain)?.toDomain()
    }

    override suspend fun upsertConfig(config: DomainFocusConfig) {
        dao.upsertConfig(config.toEntity())
    }
}
