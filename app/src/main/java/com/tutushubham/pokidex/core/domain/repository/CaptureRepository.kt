package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.Capture

interface CaptureRepository {
    suspend fun getUnresolvedCaptures(): List<Capture>
    suspend fun insertCapture(capture: Capture)
    suspend fun updateCapture(capture: Capture)
    suspend fun getCaptureById(id: String): Capture?
}
