package com.tutushubham.pokidex.core.engine

/**
 * Pure engine that generates human-readable explanations for intelligence outputs.
 *
 * Every function accepts only the data it needs and returns an [InsightExplanation].
 * No side effects, no repository access, fully testable.
 */
object ExplainabilityEngine {

    fun explainSessionPriority(
        progress: IntentProgress,
        profile: UserBehaviorProfile
    ): InsightExplanation {
        val factors = mutableListOf<String>()

        if (progress.deficit > 0) {
            factors += "Behind by %.1f units/day".format(progress.deficit)
        }
        if (profile.fatigue.level != FatigueLevel.LOW) {
            factors += "Fatigue: ${profile.fatigue.level.name}"
        }
        if (profile.momentum.streakDays > 0) {
            factors += "${profile.momentum.streakDays}-day streak"
        }
        if (profile.consistencyScore > 0.7) {
            factors += "High consistency (${(profile.consistencyScore * 100).toInt()}%)"
        }

        val title = when {
            progress.deficit > 1.0 -> "Critical Priority"
            progress.deficit > 0 -> "High Priority"
            profile.momentum.isConsistent -> "Maintain Momentum"
            else -> "Scheduled"
        }

        val description = when {
            progress.deficit > 1.0 ->
                "This session is critical because you are behind schedule and need to catch up."
            progress.deficit > 0 ->
                "You're slightly behind. This session will help you stay on track."
            profile.momentum.isConsistent ->
                "You're in a good flow. Keep the momentum going."
            else ->
                "This session is part of your regular plan."
        }

        return InsightExplanation(title, description, factors)
    }

    fun explainFatigue(profile: UserBehaviorProfile): InsightExplanation {
        val factors = mutableListOf<String>()
        factors += "Skip rate: ${(profile.skipRate * 100).toInt()}%"
        if (profile.fatigue.skipStreak > 0) {
            factors += "${profile.fatigue.skipStreak}-day skip streak"
        }
        factors += "Fatigue level: ${profile.fatigue.level.name}"
        factors += "Completion rate: ${(profile.completionRate * 100).toInt()}%"

        val (title, description) = when (profile.fatigue.level) {
            FatigueLevel.HIGH -> "High Fatigue" to
                "Your recent skip rate suggests burnout risk. Consider lighter sessions or a recovery day."
            FatigueLevel.MEDIUM -> "Moderate Fatigue" to
                "Some signs of fatigue detected. Monitor your energy and consider adjusting pace."
            FatigueLevel.LOW -> "Low Fatigue" to
                "You're showing good energy levels. Keep it up!"
        }

        return InsightExplanation(title, description, factors)
    }

    fun explainPrediction(
        prediction: PredictionInsight,
        profile: UserBehaviorProfile
    ): InsightExplanation {
        val factors = mutableListOf<String>()
        factors += "Consistency: ${(profile.consistencyScore * 100).toInt()}%"
        factors += "Velocity trend: ${profile.velocityTrend.name}"
        factors += "Confidence: ${(prediction.confidence * 100).toInt()}%"

        val title = when {
            prediction.confidence >= 0.8 -> "High Confidence Prediction"
            prediction.confidence >= 0.5 -> "Moderate Prediction"
            else -> "Low Confidence Estimate"
        }

        val description = if (prediction.predictedDate != null) {
            "Based on your ${profile.velocityTrend.name.lowercase()} velocity trend and " +
                "${(profile.consistencyScore * 100).toInt()}% consistency, " +
                "completion is predicted by ${prediction.predictedDate}."
        } else {
            "Insufficient data to predict completion date reliably."
        }

        return InsightExplanation(title, description, factors)
    }

    fun explainMomentum(profile: UserBehaviorProfile): InsightExplanation {
        val factors = mutableListOf<String>()
        factors += "Streak: ${profile.momentum.streakDays} days"
        factors += "Completion rate: ${(profile.momentum.recentCompletionRate * 100).toInt()}%"
        factors += "Consistent: ${if (profile.momentum.isConsistent) "Yes" else "No"}"

        val title = when {
            profile.momentum.streakDays >= 7 -> "Strong Momentum"
            profile.momentum.streakDays >= 3 -> "Building Momentum"
            profile.momentum.isConsistent -> "Steady Pace"
            else -> "Getting Started"
        }

        val description = when {
            profile.momentum.streakDays >= 7 ->
                "You're on a ${profile.momentum.streakDays}-day streak with " +
                    "${(profile.momentum.recentCompletionRate * 100).toInt()}% completion. Outstanding!"
            profile.momentum.streakDays >= 3 ->
                "Your ${profile.momentum.streakDays}-day streak shows building momentum. Keep going!"
            profile.momentum.isConsistent ->
                "You're maintaining a steady pace. Consistency is key."
            else ->
                "Build your streak by completing sessions consistently."
        }

        return InsightExplanation(title, description, factors)
    }
}
