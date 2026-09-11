package com.habitsheet.domain.backup

import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupContainer(
    val version: Int,
    val timestamp: Long,
    val data: HabitSnapshot
)

object BackupSerializer {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(snapshot: HabitSnapshot, timestamp: Long): String {
        val container = BackupContainer(
            version = CURRENT_VERSION,
            timestamp = timestamp,
            data = snapshot
        )
        return json.encodeToString(BackupContainer.serializer(), container)
    }

    fun deserialize(jsonString: String): HabitSnapshot {
        if (jsonString.isBlank()) {
            throw BackupValidationException("The backup file is empty.")
        }
        val container = json.decodeFromString(BackupContainer.serializer(), jsonString)
        if (container.version != CURRENT_VERSION) {
            throw BackupValidationException("Unsupported backup version: ${container.version}.")
        }
        if (container.timestamp < 0) {
            throw BackupValidationException("The backup has an invalid timestamp.")
        }
        BackupValidator.validate(container.data)
        return container.data
    }
}

class BackupValidationException(message: String) : IllegalArgumentException(message)

/**
 * Checks the relationships that the database relies on before an import can replace local data.
 * Keeping this separate from JSON decoding also lets repositories protect direct restore calls.
 */
object BackupValidator {
    fun validate(snapshot: HabitSnapshot) {
        val categoryIds = uniqueIds(
            values = snapshot.categories.map { it.id },
            entityName = "category",
        )
        val dailyHabitIds = uniqueIds(
            values = snapshot.dailyHabits.map { it.id },
            entityName = "daily habit",
        )
        val weeklyHabitIds = uniqueIds(
            values = snapshot.weeklyHabits.map { it.id },
            entityName = "weekly habit",
        )

        snapshot.categories.forEach { category ->
            requireBackup(category.id.isNotBlank(), "A category has an empty identifier.")
            requireBackup(category.name.isNotBlank(), "A category has an empty name.")
            requireBackup(category.displayOrder >= 0, "A category has an invalid display order.")
            requireBackup(category.updatedAtEpochMillis >= 0, "A category has an invalid timestamp.")
        }

        snapshot.dailyHabits.forEach { habit ->
            requireBackup(habit.id.isNotBlank(), "A daily habit has an empty identifier.")
            requireBackup(habit.name.isNotBlank(), "A daily habit has an empty name.")
            requireBackup(habit.monthlyGoal >= 0, "A daily habit has an invalid monthly goal.")
            requireBackup(habit.displayOrder >= 0, "A daily habit has an invalid display order.")
            requireBackup(habit.createdAtEpochMillis >= 0, "A daily habit has an invalid creation timestamp.")
            requireBackup(habit.updatedAtEpochMillis >= 0, "A daily habit has an invalid update timestamp.")
            requireBackup(
                habit.archivedOn == null || habit.archivedOn >= habit.createdOn,
                "A daily habit was archived before it was created.",
            )
            requireBackup(
                habit.categoryId == null || habit.categoryId in categoryIds,
                "A daily habit refers to a missing category.",
            )
        }

        snapshot.weeklyHabits.forEach { habit ->
            requireBackup(habit.id.isNotBlank(), "A weekly habit has an empty identifier.")
            requireBackup(habit.name.isNotBlank(), "A weekly habit has an empty name.")
            requireBackup(habit.displayOrder >= 0, "A weekly habit has an invalid display order.")
            requireBackup(habit.createdAtEpochMillis >= 0, "A weekly habit has an invalid creation timestamp.")
            requireBackup(habit.updatedAtEpochMillis >= 0, "A weekly habit has an invalid update timestamp.")
            requireBackup(
                habit.archivedOn == null || habit.archivedOn >= habit.createdOn,
                "A weekly habit was archived before it was created.",
            )
            requireBackup(
                habit.categoryId == null || habit.categoryId in categoryIds,
                "A weekly habit refers to a missing category.",
            )
        }

        val dailyCompletionKeys = mutableSetOf<Pair<String, LocalDate>>()
        snapshot.dailyCompletions.forEach { completion ->
            requireBackup(
                completion.habitId in dailyHabitIds,
                "A daily completion refers to a missing habit.",
            )
            requireBackup(
                dailyCompletionKeys.add(completion.habitId to completion.date),
                "The backup contains duplicate daily completions.",
            )
            requireBackup(completion.updatedAtEpochMillis >= 0, "A daily completion has an invalid timestamp.")
        }

        val weeklyCompletionKeys = mutableSetOf<Pair<String, LocalDate>>()
        snapshot.weeklyCompletions.forEach { completion ->
            requireBackup(
                completion.weeklyHabitId in weeklyHabitIds,
                "A weekly completion refers to a missing habit.",
            )
            requireBackup(
                weeklyCompletionKeys.add(completion.weeklyHabitId to completion.weekStartDate),
                "The backup contains duplicate weekly completions.",
            )
            requireBackup(completion.updatedAtEpochMillis >= 0, "A weekly completion has an invalid timestamp.")
        }
    }

    private fun uniqueIds(values: List<String>, entityName: String): Set<String> {
        val ids = mutableSetOf<String>()
        values.forEach { id ->
            requireBackup(ids.add(id), "The backup contains duplicate $entityName identifiers.")
        }
        return ids
    }

    private fun requireBackup(condition: Boolean, message: String) {
        if (!condition) throw BackupValidationException(message)
    }
}
