package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.FocusDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.FocusRepository

class FocusRepositoryImpl(
    private val dao: FocusDao
) : FocusRepository {

    override suspend fun getFocusById(id: String): Focus? {
        return dao.getFocusById(id)?.toDomain()
    }

    override suspend fun getFocusesByDomain(domain: Domain): List<Focus> {
        return dao.getFocusesByDomain(domain).map { it.toDomain() }
    }

    override suspend fun getAllFocuses(): List<Focus> {
        return dao.getAllFocuses().map { it.toDomain() }
    }

    override suspend fun insertFocus(focus: Focus) {
        dao.insert(focus.toEntity())
    }

    override suspend fun updateFocus(focus: Focus) {
        dao.update(focus.toEntity())
    }

    override suspend fun deleteFocus(id: String) {
        dao.delete(id)
    }
}
