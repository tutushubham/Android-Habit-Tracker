package com.tutushubham.pokidex.feature_today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.feature_settings.SettingsRepository
import java.time.Clock

class TodayViewModelFactory(
    private val todayPlannerUseCase: TodayPlannerUseCase,
    private val sessionRepository: SessionRepository,
    private val appStateRepository: AppStateRepository,
    private val focusRepository: FocusRepository,
    private val dailyOverrideRepository: DailyFocusOverrideRepository,
    private val focusResolver: FocusResolver,
    private val settingsRepository: SettingsRepository,
    private val behaviorProfileUseCase: BehaviorProfileUseCase? = null,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TodayViewModel(
            todayPlannerUseCase = todayPlannerUseCase,
            sessionRepository = sessionRepository,
            appStateRepository = appStateRepository,
            focusRepository = focusRepository,
            dailyFocusOverrideRepository = dailyOverrideRepository,
            focusResolver = focusResolver,
            settingsRepository = settingsRepository,
            clock = clock,
            behaviorProfileUseCase = behaviorProfileUseCase
        ) as T
    }
}
