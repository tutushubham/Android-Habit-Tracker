package com.tutushubham.pokidex.feature_today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.TodayEngine

class TodayViewModelFactory(
    private val engine: TodayEngine,
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TodayViewModel(engine, sessionRepository) as T
    }
}
