package com.tutushubham.pokidex.feature_onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.OnboardingRepository
import com.tutushubham.pokidex.core.engine.TodayEngine
import java.time.Clock

class OnboardingViewModelFactory(
    private val todayEngine: TodayEngine,
    private val onboardingRepository: OnboardingRepository,
    private val anchorRepository: AnchorRepository,
    private val focusRepository: FocusRepository,
    private val configRepository: DomainFocusConfigRepository,
    private val intentRepository: IntentRepository,
    private val appStateRepository: AppStateRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OnboardingViewModel(
            todayEngine = todayEngine,
            onboardingRepository = onboardingRepository,
            anchorRepository = anchorRepository,
            focusRepository = focusRepository,
            configRepository = configRepository,
            intentRepository = intentRepository,
            appStateRepository = appStateRepository,
            clock = clock
        ) as T
    }
}
