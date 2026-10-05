package com.habitsheet.domain.repository

import com.habitsheet.domain.backup.BackupSettings

/**
 * Everything the app stores, as one object. Only the composition root (`AppGraph`), the platform shells and tests use
 * this type; ViewModels and sync depend on the narrow store they actually use ([HabitStore], [SettingsStore],
 * [BackupStore]). `LocalHabitRepository` (SQLite) and `InMemoryHabitRepository` (tests) implement all three.
 */
interface HabitRepository : HabitStore, SettingsStore, BackupStore {
    override suspend fun backupSettings(includeSheetLink: Boolean): BackupSettings = BackupSettings(
        themeMode = getThemeMode(),
        onboardingCompleted = isOnboardingCompleted(),
        sheetUrl = if (includeSheetLink) getSheetUrl().ifBlank { null } else null,
    )
}

fun interface IdGenerator {
    fun newId(): String
}
