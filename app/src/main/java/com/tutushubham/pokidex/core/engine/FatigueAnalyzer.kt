package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.SessionStatus

/**
 * Stateless helper that detects fatigue from recent session history.
 *
 * Examines skip streaks (consecutive most-recent skips) and recent skip rate
 * over a sliding window to classify fatigue as LOW / MEDIUM / HIGH.
 */
object FatigueAnalyzer {

    private const val RECENT_WINDOW = 10

    fun analyze(recentSessions: List<Session>): FatigueSignal {
        if (recentSessions.isEmpty()) {
            return FatigueSignal(FatigueLevel.LOW, skipStreak = 0, recentSkipRate = 0.0)
        }

        val sortedByDate = recentSessions.sortedByDescending { it.date }

        val skipStreak = sortedByDate.takeWhile { it.status == SessionStatus.SKIPPED }.size

        val window = sortedByDate.take(RECENT_WINDOW)
        val skipped = window.count { it.status == SessionStatus.SKIPPED }
        val recentSkipRate = skipped.toDouble() / window.size

        val level = when {
            skipStreak >= 3 || recentSkipRate > 0.6 -> FatigueLevel.HIGH
            skipStreak >= 2 || recentSkipRate > 0.3 -> FatigueLevel.MEDIUM
            else -> FatigueLevel.LOW
        }

        return FatigueSignal(level, skipStreak, recentSkipRate)
    }
}
