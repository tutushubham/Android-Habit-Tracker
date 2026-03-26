package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.Capture

interface CaptureRepository {
    suspend fun getAllCaptures(): List<Capture>
    suspend fun getUnresolvedCaptures(): List<Capture>
    suspend fun insertCapture(capture: Capture)
    suspend fun updateCapture(capture: Capture)
    suspend fun deleteCapture(id: String)
    suspend fun getCaptureById(id: String): Capture?
}
