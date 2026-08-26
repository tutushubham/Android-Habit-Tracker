package com.habitsheet.domain.backup

import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.datetime.LocalDate

object CsvGenerator {
    fun generate(snapshot: HabitSnapshot): String {
        val builder = StringBuilder()
        builder.append("Date,Habit,Category,Completed,Frequency\n")

        // Daily Habits
        snapshot.dailyCompletions.forEach { completion ->
            val habit = snapshot.dailyHabits.find { it.id == completion.habitId } ?: return@forEach
            val category = snapshot.categories.find { it.id == habit.categoryId }
            
            builder.append(escape(completion.date.toString()))
            builder.append(",")
            builder.append(escape(habit.name))
            builder.append(",")
            builder.append(escape(category?.name ?: ""))
            builder.append(",")
            builder.append(completion.completed.toString())
            builder.append(",Daily\n")
        }

        // Weekly Habits
        snapshot.weeklyCompletions.forEach { completion ->
            val habit = snapshot.weeklyHabits.find { it.id == completion.weeklyHabitId } ?: return@forEach
            val category = snapshot.categories.find { it.id == habit.categoryId }
            
            builder.append(escape(completion.weekStartDate.toString()))
            builder.append(",")
            builder.append(escape(habit.name))
            builder.append(",")
            builder.append(escape(category?.name ?: ""))
            builder.append(",")
            builder.append(completion.completed.toString())
            builder.append(",Weekly\n")
        }

        return builder.toString()
    }

    private fun escape(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
