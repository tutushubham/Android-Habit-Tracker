package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.SignalType
import com.tutushubham.pokidex.core.domain.entity.UserSignal

interface UserSignalRepository {
    suspend fun record(signal: UserSignal)
    suspend fun getSignals(type: SignalType, limit: Int = 50): List<UserSignal>
    suspend fun getRecentSignals(limit: Int = 100): List<UserSignal>
}
