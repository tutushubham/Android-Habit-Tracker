package com.habitsheet.domain.repository

import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.coroutines.flow.StateFlow

/** Replaces all data at once: reset and restore. [snapshot] is what a backup would contain right now. */
interface BackupStore {
    val snapshot: StateFlow<HabitSnapshot>

    /** The settings a backup carries: theme and onboarding, plus the sheet link only when [includeSheetLink]. */
    suspend fun backupSettings(includeSheetLink: Boolean): BackupSettings

    /**
     * Removes all habits, plans, categories and history, and also forgets the sheet link and all sync state
     * (the tutorial shows again; theme is kept). Local only: the Google Sheet is never touched.
     */
    suspend fun clearAllData()

    /**
     * Replaces all habit data with [snapshot] in one transaction (validated first, so a bad backup changes nothing).
     * Sheet sync state is always cleared (synced keys, last sync, managed habits, pending flags) so the next sync
     * is a clean "sheet wins" reconcile. The sheet link is left alone unless [restoreSheetLink] is true and
     * [settings] carries one. A non-null [settings] also restores theme and onboarding.
     */
    suspend fun restoreFromSnapshot(snapshot: HabitSnapshot, settings: BackupSettings? = null, restoreSheetLink: Boolean = false)
}
