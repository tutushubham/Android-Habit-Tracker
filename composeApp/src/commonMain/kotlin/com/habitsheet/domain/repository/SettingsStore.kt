package com.habitsheet.domain.repository

import com.habitsheet.domain.model.SheetSyncChanges

/** App settings (theme, first-run tutorial), the sheet link and everything sync remembers about the sheet. */
interface SettingsStore {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)
    suspend fun getSheetUrl(): String
    /** Linking a different sheet (or none) forgets the previous sheet's sync state and pending-upload flags. */
    suspend fun setSheetUrl(url: String)
    suspend fun getSheetLastSync(): Long
    suspend fun setSheetLastSync(epochMillis: Long)
    suspend fun getSheetSyncedKeys(): Set<String>
    suspend fun setSheetSyncedKeys(keys: Set<String>)
    suspend fun setSheetManagedHabitIds(ids: Set<String>)
    /**
     * Applies a whole sheet sync atomically: [changes], the new synced [newKeys] and [lastSync].
     * Either everything is written (and the snapshot reloaded once) or nothing is.
     */
    suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long)
}
