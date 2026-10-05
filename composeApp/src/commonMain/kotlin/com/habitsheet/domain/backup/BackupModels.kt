package com.habitsheet.domain.backup

import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetLink
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Version 1-3 container. Kept so every older backup still restores; version 4 is read by [BackupSerializer.parse]. */
@Serializable
data class BackupContainer(
    val version: Int,
    val timestamp: Long,
    val data: HabitSnapshot,
)

/**
 * Device settings carried by a version 4 backup. Every field is optional. Sync state (synced keys, last sync,
 * managed habit ids, pending-upload flags) is deliberately NOT part of a backup: after a restore the next sync
 * starts from a clean "sheet wins" reconcile.
 */
@Serializable
data class BackupSettings(
    val themeMode: Int? = null,
    val onboardingCompleted: Boolean? = null,
    /** Only present if the person chose to include it when exporting; applied only if they choose to restore it. */
    val sheetUrl: String? = null,
)

/** A successfully read backup of any supported version. [settings] is null for versions 1-3. */
data class ParsedBackup(
    val version: Int,
    val timestamp: Long,
    val snapshot: HabitSnapshot,
    val settings: BackupSettings?,
)

object BackupSerializer {
    const val CURRENT_VERSION = 4
    private const val CHECKSUM_PREFIX = "sha256:"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Always writes the current version (4). */
    fun serialize(snapshot: HabitSnapshot, timestamp: Long, settings: BackupSettings? = null): String {
        val data = json.encodeToJsonElement(HabitSnapshot.serializer(), snapshot)
        val settingsElement = json.encodeToJsonElement(BackupSettings.serializer(), settings ?: BackupSettings())
        val root = buildJsonObject {
            put("version", CURRENT_VERSION)
            put("timestamp", timestamp)
            put("checksum", CHECKSUM_PREFIX + checksum(settingsElement, data))
            put("settings", settingsElement)
            put("data", data)
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun deserialize(jsonString: String): HabitSnapshot = parse(jsonString).snapshot

    fun parse(jsonString: String): ParsedBackup {
        if (jsonString.isBlank()) {
            throw BackupValidationException("The backup file is empty.")
        }
        val root = json.parseToJsonElement(jsonString) as? JsonObject
            ?: throw SerializationException("The backup is not a JSON object.")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: throw BackupValidationException("The backup has no version.")
        if (version !in 1..CURRENT_VERSION) {
            throw BackupValidationException("Unsupported backup version: $version.")
        }
        val parsed = if (version < 4) {
            val container = json.decodeFromJsonElement(BackupContainer.serializer(), root)
            ParsedBackup(version, container.timestamp, container.data, settings = null)
        } else {
            parseV4(root)
        }
        if (parsed.timestamp < 0) {
            throw BackupValidationException("The backup has an invalid timestamp.")
        }
        BackupValidator.validate(parsed.snapshot)
        parsed.settings?.let(BackupValidator::validate)
        return parsed
    }

    private fun parseV4(root: JsonObject): ParsedBackup {
        val data = root["data"] ?: throw BackupValidationException("The backup has no data.")
        val settingsElement = root["settings"] ?: JsonObject(emptyMap())
        val expected = (root["checksum"] as? JsonPrimitive)?.contentOrNull
            ?: throw BackupValidationException("The backup has no checksum.")
        if (expected != CHECKSUM_PREFIX + checksum(settingsElement, data)) {
            throw BackupValidationException("This backup file was changed or damaged and can't be restored.")
        }
        return ParsedBackup(
            version = 4,
            timestamp = (root["timestamp"] as? JsonPrimitive)?.longOrNull
                ?: throw BackupValidationException("The backup has an invalid timestamp."),
            snapshot = json.decodeFromJsonElement(HabitSnapshot.serializer(), data),
            settings = json.decodeFromJsonElement(BackupSettings.serializer(), settingsElement),
        )
    }

    /**
     * SHA-256 over the compact JSON of `settings` and `data` as they appear in the file (not over a re-encoding
     * of the decoded model), so a later app version that adds model fields still verifies old files.
     */
    private fun checksum(settings: JsonElement, data: JsonElement): String = Sha256.hex("$settings\n$data")
}

class BackupValidationException(message: String) : IllegalArgumentException(message)

/**
 * Checks the relationships that the database relies on before an import can replace local data.
 * Keeping this separate from JSON decoding also lets repositories protect direct restore calls.
 */
object BackupValidator {
    fun validate(settings: BackupSettings) {
        requireBackup(settings.themeMode == null || settings.themeMode in 0..2, "The backup has an invalid theme setting.")
        requireBackup(
            settings.sheetUrl.isNullOrBlank() || SheetLink.canonicalize(settings.sheetUrl) != null,
            "The backup contains an invalid sheet link.",
        )
    }

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
        val weeklyPlanKeys = mutableSetOf<Pair<String, Int>>()
        snapshot.weeklyPlans.forEach { plan ->
            requireBackup(plan.habitId in dailyHabitIds, "A weekly plan refers to a missing habit.")
            requireBackup(plan.weekday in 1..7, "A weekly plan has an invalid weekday.")
            requireBackup(plan.detail.isNotBlank(), "A weekly plan has an empty detail.")
            requireBackup(plan.updatedAtEpochMillis >= 0, "A weekly plan has an invalid timestamp.")
            requireBackup(weeklyPlanKeys.add(plan.habitId to plan.weekday), "The backup contains duplicate weekly plans.")
        }
        val dayPlanKeys = mutableSetOf<String>()
        snapshot.dayPlans.forEach { plan ->
            requireBackup(plan.habitId in dailyHabitIds, "A day plan refers to a missing habit.")
            requireBackup(plan.detail.isNotBlank(), "A day plan has an empty detail.")
            requireBackup(plan.updatedAtEpochMillis >= 0, "A day plan has an invalid timestamp.")
            requireBackup(plan.id.isNotBlank() && dayPlanKeys.add(plan.id), "The backup contains duplicate day plan identifiers.")
        }

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
                completion.planId.isNotBlank() && dailyCompletionKeys.add(completion.planId to completion.date),
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
