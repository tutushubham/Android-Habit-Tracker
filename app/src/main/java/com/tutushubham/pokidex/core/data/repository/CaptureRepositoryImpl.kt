package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.CaptureDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.Capture
import com.tutushubham.pokidex.core.domain.repository.CaptureRepository

class CaptureRepositoryImpl(
    private val dao: CaptureDao
) : CaptureRepository {

    override suspend fun getAllCaptures(): List<Capture> {
        return dao.getAllCaptures().map { it.toDomain() }
    }

    override suspend fun getUnresolvedCaptures(): List<Capture> {
        return dao.getUnresolvedCaptures().map { it.toDomain() }
    }

    override suspend fun insertCapture(capture: Capture) {
        dao.insert(capture.toEntity())
    }

    override suspend fun updateCapture(capture: Capture) {
        dao.update(capture.toEntity())
    }

    override suspend fun deleteCapture(id: String) {
        dao.deleteById(id)
    }

    override suspend fun getCaptureById(id: String): Capture? {
        return dao.getCaptureById(id)?.toDomain()
    }
}
