package com.tutushubham.pokidex.core.engine

enum class FatigueLevel { LOW, MEDIUM, HIGH }

data class FatigueSignal(
    val level: FatigueLevel,
    val skipStreak: Int,
    val recentSkipRate: Double
)

data class MomentumSignal(
    val streakDays: Int,
    val recentCompletionRate: Double,
    val isConsistent: Boolean
)

data class IntentBehaviorProfile(
    val fatigue: FatigueSignal,
    val momentum: MomentumSignal,
    val learnedEstimate: LearnedEstimate?
)
