package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain

interface FocusRepository {
    suspend fun getFocusById(id: String): Focus?
    suspend fun getFocusesByDomain(domain: Domain): List<Focus>
    suspend fun insertFocus(focus: Focus)
    suspend fun updateFocus(focus: Focus)
    suspend fun deleteFocus(id: String)
}
