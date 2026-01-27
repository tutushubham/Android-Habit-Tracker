package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.Domain

interface DomainFocusConfigRepository {
    suspend fun getConfig(domain: Domain): DomainFocusConfig?
    suspend fun upsertConfig(config: DomainFocusConfig)
}
