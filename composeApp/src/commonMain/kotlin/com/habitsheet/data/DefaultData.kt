package com.habitsheet.data

import com.habitsheet.domain.model.Category

object DefaultData {
    private val names = listOf(
        "Career 💼",
        "Diet 🍎",
        "Family ❣️",
        "Fitness 💪",
        "Health ❤️‍🩹",
        "Money 💰",
        "Productivity 🕺",
        "Sleep 💤",
        "Social 👫",
        "Study 📚",
    )

    fun categories(nowEpochMillis: Long): List<Category> = names.mapIndexed { index, name ->
        Category(
            id = "category-${index + 1}",
            name = name,
            displayOrder = index,
            active = true,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}
