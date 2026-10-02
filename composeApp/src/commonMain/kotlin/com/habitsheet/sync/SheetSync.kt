package com.habitsheet.sync

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.presentation.DateProvider
import com.habitsheet.presentation.SystemDateProvider
import com.habitsheet.domain.model.SheetLink
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock

/** Platform Google Sign-In owns tokens. The shared layer never persists credentials. */
interface SheetTokenProvider {
    fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit)
}

data class SheetSyncState(val busy: Boolean = false, val message: String = "Not synced yet", val lastSync: Long = 0)

/**
 * Orchestrates one sync: token, [SheetsApi] calls, [PlanReconciler] decisions, repository writes.
 * A deliberately small, single-table Sheets client. Food/Workout/Marathon Plan are untouched.
 */
class SheetSync(
    private val repository: HabitRepository,
    private val tokenProvider: SheetTokenProvider,
    private val client: HttpClient = HttpClient(),
    private val dateProvider: DateProvider = SystemDateProvider,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SheetSyncState())
    val state: StateFlow<SheetSyncState> = mutableState.asStateFlow()

    suspend fun sync(interactive: Boolean = false) {
        mutex.lock()
        try {
            val id = SheetLink.spreadsheetId(repository.getSheetUrl())
            if (id == null) {
                if (interactive) mutableState.value = SheetSyncState(message = "Add a spreadsheet link first.")
                return
            }
            mutableState.value = mutableState.value.copy(busy = true, message = "Syncing…")
            val token = token(interactive)
            if (token == null) {
                mutableState.value = mutableState.value.copy(busy = false, message = if (interactive) "Google sign-in was cancelled or failed. If you did choose an account, the app's Google OAuth client may not match this build's signing key (see SHEET_SYNC.md)." else "Sign in to sync")
                return
            }
            val api = SheetsApi(client, id, token)
            removeLegacyWeeklyPlaceholders()
            val snapshot = repository.snapshot.value
            val window = SheetSyncWindow.rolling(dateProvider.today())
            val hasPlanTab = api.hasPlanTab()
            val createdTabId = if (!hasPlanTab) api.createPlanTab() else null
            val existingRows = if (hasPlanTab) api.readTable() else null
            if (existingRows == null) {
                uploadNewTab(api, createdTabId, snapshot, window)
                return
            }
            var rows = existingRows
            val additionalRows = PlanReconciler.localOnlyRows(snapshot, rows, window)
            if (additionalRows.isNotEmpty()) {
                api.appendRows(additionalRows)
                rows = api.readTable() ?: error("Could not read appended Plan rows.")
            }
            val result = PlanReconciler.reconcile(
                snapshot = snapshot,
                remoteRows = rows,
                oldKeys = repository.getSheetSyncedKeys(),
                lastSync = repository.getSheetLastSync(),
                nowMillis = Clock.System.now().toEpochMilliseconds(),
                newId = { DefaultIdGenerator().newId() },
            )
            applyLocally(result)
            if (result.doneUploads.isNotEmpty()) api.writeDone(result.doneUploads)
            val now = Clock.System.now().toEpochMilliseconds()
            repository.setSheetManagedHabitIds(result.managedHabitIds)
            repository.setSheetSyncedKeys(result.syncedKeys)
            repository.setSheetLastSync(now)
            val uploaded = result.doneUploads.size
            mutableState.value = SheetSyncState(message = "Synced ${result.sessionCount} sessions${if (uploaded > 0) " · $uploaded checks uploaded" else ""}", lastSync = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(busy = false, message = e.message?.take(180) ?: "Sync failed. Try again.")
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun uploadNewTab(api: SheetsApi, createdTabId: Int?, snapshot: com.habitsheet.domain.model.HabitSnapshot, window: SheetSyncWindow) {
        val upload = PlanReconciler.initialUpload(snapshot, window)
        api.putTable(upload.rows)
        if (createdTabId != null) {
            try { api.formatPlanTab(createdTabId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Formatting is optional; the data is already safe. */ }
        }
        repository.setSheetSyncedKeys(upload.syncedKeys)
        val now = Clock.System.now().toEpochMilliseconds()
        repository.setSheetLastSync(now)
        repository.setSheetManagedHabitIds(upload.managedHabitIds)
        mutableState.value = SheetSyncState(message = "Plan tab created · ${upload.rows.size} sessions uploaded", lastSync = now)
    }

    private suspend fun applyLocally(result: ReconcileResult) {
        result.planIdsToDelete.forEach { repository.deleteDayPlanById(it) }
        result.habitsToCreate.forEach { repository.saveDailyHabit(it) }
        result.plansToSave.forEach { repository.saveDayPlan(it) }
        result.completionsToSave.forEach { repository.setDailyCompletion(it) }
    }

    private suspend fun token(interactive: Boolean): String? = suspendCancellableCoroutine { continuation ->
        tokenProvider.requestToken(interactive) { value, error ->
            if (continuation.isActive) {
                if (error != null) continuation.resumeWithException(IllegalStateException(error)) else continuation.resume(value)
            }
        }
    }

    fun close() = client.close()

    /** Older builds stored a placeholder weekly detail that is not a real session; drop it if present. */
    private suspend fun removeLegacyWeeklyPlaceholders() {
        repository.snapshot.value.weeklyPlans.filter { it.detail == LEGACY_WEEKLY_PLACEHOLDER }.forEach {
            repository.deleteWeeklyPlan(it.habitId, it.weekday)
        }
    }
}

private const val LEGACY_WEEKLY_PLACEHOLDER = "Plan in OND sheet"
