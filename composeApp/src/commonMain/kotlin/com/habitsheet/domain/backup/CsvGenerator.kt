package com.habitsheet.domain.backup

import com.habitsheet.domain.model.HabitSnapshot

object CsvGenerator {
    fun generate(snapshot: HabitSnapshot): String {
        val builder = StringBuilder()
        builder.append("Date,Habit,Category,Completed,Frequency\n")
        val categoriesById = snapshot.categories.associateBy { it.id }
        val dailyHabitsById = snapshot.dailyHabits.associateBy { it.id }
        val weeklyHabitsById = snapshot.weeklyHabits.associateBy { it.id }

        snapshot.dailyCompletions
            .sortedWith(compareBy({ it.date }, { it.habitId }))
            .forEach { completion ->
                val habit = dailyHabitsById[completion.habitId] ?: return@forEach
                val category = habit.categoryId?.let(categoriesById::get)

                builder.append(escape(completion.date.toString()))
                builder.append(",")
                builder.append(escapeUserText(habit.name))
                builder.append(",")
                builder.append(escapeUserText(category?.name ?: ""))
                builder.append(",")
                builder.append(completion.completed.toString())
                builder.append(",Daily\n")
            }

        snapshot.weeklyCompletions
            .sortedWith(compareBy({ it.weekStartDate }, { it.weeklyHabitId }))
            .forEach { completion ->
                val habit = weeklyHabitsById[completion.weeklyHabitId] ?: return@forEach
                val category = habit.categoryId?.let(categoriesById::get)

                builder.append(escape(completion.weekStartDate.toString()))
                builder.append(",")
                builder.append(escapeUserText(habit.name))
                builder.append(",")
                builder.append(escapeUserText(category?.name ?: ""))
                builder.append(",")
                builder.append(completion.completed.toString())
                builder.append(",Weekly\n")
            }

        return builder.toString()
    }

    /** Prevents user-entered names from being interpreted as formulas by spreadsheet apps. */
    private fun escapeUserText(value: String): String {
        val startsFormula = value.firstOrNull()?.let { it in FormulaPrefixes } == true
        val spreadsheetSafe = if (startsFormula) "'$value" else value
        return escape(spreadsheetSafe)
    }

    private fun escape(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private val FormulaPrefixes = setOf('=', '+', '-', '@', '\t', '\r')
}
