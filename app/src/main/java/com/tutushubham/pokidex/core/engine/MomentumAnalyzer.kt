package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import java.time.LocalDate

/**
 * Stateless helper that detects momentum / consistency from recent sessions.
 *
 * Streak = consecutive days with at least one completed session, counting backward
 * from yesterday (today is excluded because it hasn't ended yet).
 * Consistency = streak >= 3 days AND completion rate >= 70%.
 */
object MomentumAnalyzer {

    fun analyze(recentSessions: List<Session>, today: LocalDate): MomentumSignal {
        if (recentSessions.isEmpty()) {
            return MomentumSignal(streakDays = 0, recentCompletionRate = 0.0, isConsistent = false)
        }

        val completedDates = recentSessions
            .filter { it.status == SessionStatus.COMPLETED }
            .map { it.date }
            .toSet()

        var streakDays = 0
        var checkDate = today.minusDays(1)
        while (checkDate in completedDates) {
            streakDays++
            checkDate = checkDate.minusDays(1)
        }

        val completed = recentSessions.count { it.status == SessionStatus.COMPLETED }
        val recentCompletionRate = completed.toDouble() / recentSessions.size

        val isConsistent = streakDays >= 3 && recentCompletionRate >= 0.7

        return MomentumSignal(streakDays, recentCompletionRate, isConsistent)
    }
}
