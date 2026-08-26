package com.habitsheet.domain.backup

import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupContainer(
    val version: Int,
    val timestamp: Long,
    val data: HabitSnapshot
)

object BackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(snapshot: HabitSnapshot, timestamp: Long): String {
        val container = BackupContainer(
            version = 1,
            timestamp = timestamp,
            data = snapshot
        )
        return json.encodeToString(BackupContainer.serializer(), container)
    }

    fun deserialize(jsonString: String): HabitSnapshot {
        val container = json.decodeFromString(BackupContainer.serializer(), jsonString)
        if (container.version > 1) {
            throw IllegalArgumentException("Unsupported backup version: ${container.version}")
        }
        return container.data
    }
}
