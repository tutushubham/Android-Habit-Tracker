package com.tutushubham.pokidex.feature_insights

import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import java.time.LocalDate

class InsightsUseCase(
    private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository
) {
    suspend fun loadInsights(
        today: LocalDate = LocalDate.now(),
        profiles: Map<String, UserBehaviorProfile> = emptyMap()
    ): InsightsUiModel {
        val cutoff30d = today.minusDays(30)
        val cutoff7d = today.minusDays(7)

        val sessions30d = sessionRepository.getRecentSessions(cutoff30d)
        val sessions7d = sessions30d.filter { it.date >= cutoff7d }

        val farPast = today.minusDays(365)
        val intents = intentRepository.getIntentsForDateRange(farPast, today)

        return InsightsMapper.map(sessions7d, sessions30d, intents, today, profiles)
    }
}
