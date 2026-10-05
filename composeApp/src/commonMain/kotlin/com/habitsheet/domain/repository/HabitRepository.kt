package com.habitsheet.domain.repository

/**
 * Everything the app stores, as one object. Only the composition root (`AppGraph`), the platform shells and tests use
 * this type; ViewModels and sync depend on the narrow store they actually use ([HabitStore], [SettingsStore],
 * [BackupStore]). `LocalHabitRepository` (SQLite) and `InMemoryHabitRepository` (tests) implement all three.
 */
interface HabitRepository : HabitStore, SettingsStore, BackupStore

fun interface IdGenerator {
    fun newId(): String
}
