package com.tutushubham.pokidex.core.domain.model

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class PlanningStyle {
    STRICT,
    BALANCED,
    FLEXIBLE;

    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val description: String
        get() = when (this) {
            STRICT -> "Follow deadlines rigidly. No slack time."
            BALANCED -> "Respect deadlines with moderate flexibility."
            FLEXIBLE -> "Prioritize wellbeing. Allow buffer days."
        }
}

enum class FatigueSensitivity {
    LOW, MEDIUM, HIGH;

    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val description: String
        get() = when (this) {
            LOW -> "Push through skip patterns."
            MEDIUM -> "Reduce load after moderate skipping."
            HIGH -> "Quickly reduce load at first signs of fatigue."
        }
}

data class SystemSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val adaptivePlanningEnabled: Boolean = true,
    val planningStyle: PlanningStyle = PlanningStyle.BALANCED,
    val fatigueSensitivity: FatigueSensitivity = FatigueSensitivity.MEDIUM,
    val learningEnabled: Boolean = true,
    val useAiPeakTime: Boolean = true,
    val peakFocusStartHour: Int = 9,
    val peakFocusEndHour: Int = 11,
    val notifyDailySummary: Boolean = true,
    val notifyPeakReminder: Boolean = true,
    val notifyBehindAlert: Boolean = true
)
