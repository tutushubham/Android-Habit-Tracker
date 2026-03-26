package com.tutushubham.pokidex.core.engine

/**
 * Pure engine for consistent coach-like copy across the app.
 *
 * All user-facing intelligence text follows: observation -> reason -> suggestion.
 * No Android dependencies, no repository access.
 */
object CopyEngine {

    fun sessionPriority(progress: IntentProgress): String {
        val observation = when {
            progress.isCritical -> "You're critically behind on ${progress.title}."
            progress.isBehind -> "${progress.title} needs attention."
            progress.completedUnits > 0 -> "Good progress on ${progress.title}."
            else -> "Time to start on ${progress.title}."
        }

        val reason = when {
            progress.isCritical -> "You need %.1f units/day but are at %.1f.".format(
                progress.requiredUnitsPerDay, progress.currentPace
            )
            progress.isBehind -> "Current pace (%.1f/day) is below required (%.1f/day).".format(
                progress.currentPace, progress.requiredUnitsPerDay
            )
            else -> "${progress.completedUnits}/${progress.targetCount} complete, ${progress.daysRemaining} days left."
        }

        val suggestion = when {
            progress.isCritical -> "Focus on this goal today — every session counts."
            progress.isBehind -> "Try to fit in an extra session to close the gap."
            else -> "Keep your current pace to finish on time."
        }

        return "$observation $reason $suggestion"
    }

    fun fatigueWarning(profile: UserBehaviorProfile): String {
        val observation = when (profile.fatigue.level) {
            FatigueLevel.HIGH -> "You're showing signs of burnout."
            FatigueLevel.MEDIUM -> "Your energy levels are dipping."
            FatigueLevel.LOW -> "You're in good shape."
        }

        val reason = when (profile.fatigue.level) {
            FatigueLevel.HIGH -> "Skip rate: ${(profile.skipRate * 100).toInt()}%, " +
                "${profile.fatigue.skipStreak} sessions skipped in a row."
            FatigueLevel.MEDIUM -> "Some recent skips suggest you may need lighter sessions."
            FatigueLevel.LOW -> "Completion rate at ${(profile.completionRate * 100).toInt()}%."
        }

        val suggestion = when (profile.fatigue.level) {
            FatigueLevel.HIGH -> "Consider a recovery day or shorter sessions."
            FatigueLevel.MEDIUM -> "Reduce session duration or try a different domain."
            FatigueLevel.LOW -> "Keep up the great work!"
        }

        return "$observation $reason $suggestion"
    }

    fun momentumUpdate(profile: UserBehaviorProfile): String {
        val observation = when {
            profile.momentum.streakDays >= 7 -> "Outstanding streak!"
            profile.momentum.streakDays >= 3 -> "Building momentum."
            profile.momentum.isConsistent -> "Steady progress."
            else -> "Getting started."
        }

        val reason = when {
            profile.momentum.streakDays >= 7 ->
                "${profile.momentum.streakDays}-day streak with ${(profile.momentum.recentCompletionRate * 100).toInt()}% completion."
            profile.momentum.streakDays >= 3 ->
                "${profile.momentum.streakDays} days in a row — consistency builds results."
            else ->
                "Completing sessions regularly builds lasting habits."
        }

        val suggestion = when {
            profile.momentum.streakDays >= 7 -> "Challenge yourself with a stretch goal."
            profile.momentum.streakDays >= 3 -> "Keep going — you're close to a strong streak."
            else -> "Try completing at least one session today."
        }

        return "$observation $reason $suggestion"
    }

    fun settingsImpact(changedSettings: List<String>): String {
        if (changedSettings.isEmpty()) return "Settings updated."
        val changes = changedSettings.joinToString(", ")
        return "You changed: $changes. Your plan will adjust accordingly."
    }
}
