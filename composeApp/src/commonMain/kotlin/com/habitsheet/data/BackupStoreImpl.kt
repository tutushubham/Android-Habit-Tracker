package com.habitsheet.data

import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.backup.BackupValidator
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.repository.BackupStore
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.domain.repository.readBackupSettings
import kotlinx.coroutines.flow.StateFlow

/** SQLite [BackupStore]: reset and restore, each a single transaction. */
internal class BackupStoreImpl(
    private val db: LocalDatabase,
    private val settingsStore: SettingsStore,
) : BackupStore {
    override val snapshot: StateFlow<HabitSnapshot> get() = db.snapshot

    override suspend fun backupSettings(includeSheetLink: Boolean): BackupSettings = settingsStore.readBackupSettings(includeSheetLink)

    override suspend fun clearAllData() {
        db.write {
            deleteAllHabitData()
            setSetting(SettingKeys.ONBOARDING_COMPLETED, 0L)
            // The sheet link and everything learned from it belongs to the data that was just removed.
            // (Pending-upload flags went with the completions.) The Google Sheet itself is never touched.
            setTextSetting(SettingKeys.SHEET_URL, "")
            clearSheetSyncState()
        }
    }

    override suspend fun restoreFromSnapshot(snapshot: HabitSnapshot, settings: BackupSettings?, restoreSheetLink: Boolean) {
        // Validate before opening the replacement transaction so corrupt or inconsistent
        // backups can never clear the user's current data.
        BackupValidator.validate(snapshot)
        settings?.let(BackupValidator::validate)
        db.write {
            deleteAllHabitData()
            // The restored data no longer matches what the sheet last saw: forget all sync state.
            clearSheetSyncState()
            settings?.themeMode?.let { setSetting(SettingKeys.THEME_MODE, it.toLong()) }
            settings?.onboardingCompleted?.let { setSetting(SettingKeys.ONBOARDING_COMPLETED, if (it) 1L else 0L) }
            if (restoreSheetLink && !settings?.sheetUrl.isNullOrBlank()) {
                setTextSetting(SettingKeys.SHEET_URL, settings!!.sheetUrl!!)
            }

            snapshot.categories.forEach { insert(it) }
            snapshot.dailyHabits.forEach { insert(it) }
            snapshot.dailyCompletions.forEach { completion ->
                upsertDailyCompletion(
                    plan_id = completion.planId,
                    habit_id = completion.habitId,
                    date = completion.date.toString(),
                    completed = completion.completed.toDbLong(),
                    updated_at = completion.updatedAtEpochMillis,
                    pending_upload = 0L,
                )
            }
            snapshot.weeklyPlans.forEach { plan ->
                upsertWeeklyPlan(plan.habitId, plan.weekday.toLong(), plan.detail, plan.updatedAtEpochMillis)
            }
            snapshot.dayPlans.forEach { plan ->
                upsertDayPlan(plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(), plan.updatedAtEpochMillis)
            }
            snapshot.weeklyHabits.forEach { insert(it) }
            snapshot.weeklyCompletions.forEach { completion ->
                upsertWeeklyCompletion(
                    weekly_habit_id = completion.weeklyHabitId,
                    week_start_date = completion.weekStartDate.toString(),
                    completed = completion.completed.toDbLong(),
                    updated_at = completion.updatedAtEpochMillis,
                )
            }
        }
    }
}
