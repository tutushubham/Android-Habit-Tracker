package com.tutushubham.pokidex.feature_capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.CaptureRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository

class CaptureViewModelFactory(
    private val captureRepository: CaptureRepository,
    private val intentRepository: IntentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CaptureViewModel(captureRepository, intentRepository) as T
    }
}
