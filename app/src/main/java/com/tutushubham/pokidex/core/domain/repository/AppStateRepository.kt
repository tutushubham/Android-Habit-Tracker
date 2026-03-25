package com.tutushubham.pokidex.core.domain.repository

interface AppStateRepository {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
