package com.tutushubham.pokidex.feature_focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import java.time.Clock

class FocusViewModelFactory(
    private val domain: Domain,
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val focusResolver: FocusResolver,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FocusViewModel(
            domain = domain,
            focusRepository = focusRepository,
            configRepository = configRepository,
            focusResolver = focusResolver,
            clock = clock
        ) as T
    }
}
