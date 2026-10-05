package com.habitsheet.data

import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.repository.SettingsStore

/** SQLite [SettingsStore]: theme, onboarding, the sheet link and sync state, and the atomic apply of a sync. */
internal class SettingsStoreImpl(private val db: LocalDatabase) : SettingsStore {
    override suspend fun isOnboardingCompleted(): Boolean =
        db.locked { getSetting(SettingKeys.ONBOARDING_COMPLETED).executeAsOneOrNull() == 1L }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        db.locked { setSetting(SettingKeys.ONBOARDING_COMPLETED, if (completed) 1L else 0L) }
    }

    override suspend fun getThemeMode(): Int =
        db.locked { getSetting(SettingKeys.THEME_MODE).executeAsOneOrNull()?.toInt() ?: 0 }

    override suspend fun setThemeMode(mode: Int) {
        db.locked { setSetting(SettingKeys.THEME_MODE, mode.toLong()) }
    }

    override suspend fun getSheetUrl(): String = db.locked { getTextSetting(SettingKeys.SHEET_URL).executeAsOneOrNull().orEmpty() }

    override suspend fun setSheetUrl(url: String) {
        db.write {
            if (getTextSetting(SettingKeys.SHEET_URL).executeAsOneOrNull() != url) {
                clearSheetSyncState()
                // Pending checks belonged to the previous sheet; a newly linked sheet is authoritative.
                clearAllPendingUploads()
            }
            setTextSetting(SettingKeys.SHEET_URL, url)
        }
    }

    override suspend fun getSheetLastSync(): Long =
        db.locked { getTextSetting(SettingKeys.SHEET_LAST_SYNC).executeAsOneOrNull()?.toLongOrNull() ?: 0L }

    override suspend fun setSheetLastSync(epochMillis: Long) {
        db.locked { setTextSetting(SettingKeys.SHEET_LAST_SYNC, epochMillis.toString()) }
    }

    override suspend fun getSheetSyncedKeys(): Set<String> = db.locked {
        getTextSetting(SettingKeys.SHEET_SYNCED_KEYS).executeAsOneOrNull()
            ?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    override suspend fun setSheetSyncedKeys(keys: Set<String>) {
        db.locked { setTextSetting(SettingKeys.SHEET_SYNCED_KEYS, keys.sorted().joinToString("\n")) }
    }

    override suspend fun setSheetManagedHabitIds(ids: Set<String>) {
        db.write { setTextSetting(SettingKeys.SHEET_MANAGED_HABITS, ids.sorted().joinToString("\n")) }
    }

    override suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long) {
        // Any exception rolls the whole transaction back; the cached snapshot is only reloaded on success.
        db.write {
            changes.planIdsToDelete.forEach { deleteDayPlanById(it) }
            changes.habitsToSave.forEach { habit ->
                require(habit.monthlyGoal >= 0)
                write(habit)
            }
            changes.plansToSave.forEach { plan ->
                require(plan.detail.isNotBlank())
                upsertDayPlan(plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(), nextDayPlanTime(plan.id, plan.updatedAtEpochMillis))
            }
            changes.completionsToSave.forEach { completion ->
                // The user toggled this check after the sync read its snapshot: their change wins and stays pending.
                val key = completion.date.toString()
                if (isCompletionPending(completion.planId, key).executeAsOneOrNull() == 1L) return@forEach
                upsertDailyCompletion(
                    plan_id = completion.planId,
                    habit_id = completion.habitId,
                    date = key,
                    completed = completion.completed.toDbLong(),
                    updated_at = nextCompletionTime(completion.planId, key, completion.updatedAtEpochMillis),
                    pending_upload = 0L,
                )
            }
            changes.completionsToAcknowledge.forEach { ack ->
                acknowledgeCompletionUpload(ack.planId, ack.date.toString(), ack.updatedAtEpochMillis)
            }
            setTextSetting(SettingKeys.SHEET_MANAGED_HABITS, changes.managedHabitIds.sorted().joinToString("\n"))
            setTextSetting(SettingKeys.SHEET_SYNCED_KEYS, newKeys.sorted().joinToString("\n"))
            setTextSetting(SettingKeys.SHEET_LAST_SYNC, lastSync.toString())
        }
    }
}
