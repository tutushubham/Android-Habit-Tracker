package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.EstimateLearner
import com.tutushubham.pokidex.core.engine.FatigueAnalyzer
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.MomentumAnalyzer
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import com.tutushubham.pokidex.feature_goal_detail.GoalInsightsMapper
import com.tutushubham.pokidex.feature_goal_detail.GoalInsightsUiModel
import java.time.LocalDate

class GoalInsightsUseCase(
    private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository,
    private val behaviorRepository: BehaviorRepository
) {
    suspend fun load(
        intentId: String,
        progressList: List<IntentProgress>,
        today: LocalDate = LocalDate.now(),
        profile: UserBehaviorProfile? = null
    ): GoalInsightsUiModel {
        val intent = intentRepository.getIntentById(intentId)
            ?: throw IllegalArgumentException("Intent $intentId not found")

        val cutoff30d = today.minusDays(30)
        val sessions30d = sessionRepository.getRecentSessions(cutoff30d)
            .filter { it.intentId == intentId }

        val progress = progressList.firstOrNull { it.intentId == intentId }

        val fatigue = profile?.fatigue ?: FatigueAnalyzer.analyze(sessions30d)
        val momentum = profile?.momentum ?: MomentumAnalyzer.analyze(sessions30d, today)

        val learnedEstimate = profile?.learnedEstimate ?: run {
            val persistedStats = behaviorRepository.getIntentStats(intentId)
            if (persistedStats != null && intent.estimatedMinutesPerUnit != null) {
                val totalMinutes = sessionRepository.getTotalActualMinutesForIntent(intentId)
                val completedUnits = sessionRepository.getCompletedUnitsForIntent(intentId)
                EstimateLearner.computeEstimate(intent.estimatedMinutesPerUnit, totalMinutes, completedUnits)
            } else {
                EstimateLearner.computeRecentEstimate(intent.estimatedMinutesPerUnit, sessions30d)
            }
        }

        return GoalInsightsMapper.map(
            intent = intent,
            progress = progress,
            sessions30d = sessions30d,
            fatigue = fatigue,
            momentum = momentum,
            learnedEstimate = learnedEstimate,
            today = today,
            profile = profile
        )
    }
}
