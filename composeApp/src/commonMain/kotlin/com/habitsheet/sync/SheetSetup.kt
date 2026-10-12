package com.habitsheet.sync

import com.habitsheet.domain.model.SheetLink
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import io.ktor.client.HttpClient

/** How linking a sheet ended. [Linked.syncError] is set when the sheet is linked but its first sync failed. */
sealed interface SheetSetupResult {
    data class Linked(val url: String, val syncError: SyncError?) : SheetSetupResult

    /** The person closed the Google sign-in. Nothing changed. */
    data object Cancelled : SheetSetupResult

    /** The pasted text is not a Google Sheets link. Nothing changed. */
    data object InvalidLink : SheetSetupResult

    /** Nothing was linked; for [SyncError.CreateUnconfirmed] a sheet may exist in Drive. */
    data class Failed(val error: SyncError) : SheetSetupResult
}

/** What the first-run wizard needs to link a sheet; [SheetSetup] in the app, a fake in tests. */
interface SheetLinker {
    /** Signs in, creates a spreadsheet named [title] with an empty `Plan` tab, links it and runs the first sync. */
    suspend fun createAndLink(title: String): SheetSetupResult

    /** Links the sheet at [input] (the person's own) and runs the first sync. */
    suspend fun linkExisting(input: String): SheetSetupResult
}

/**
 * Links a Google Sheet to the app and fills it, for the first-run wizard. The plan is already in the local store, so
 * the first [SheetSync.sync] of a new (blank) sheet uploads it: no separate upload path.
 */
class SheetSetup(
    private val settings: SettingsStore,
    private val sheetSync: SheetSync,
    private val tokenProvider: SheetTokenProvider,
    private val client: HttpClient,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val logger: Logger = NoOpLogger,
) : SheetLinker {
    override suspend fun createAndLink(title: String): SheetSetupResult = attempt {
        val token = tokenProvider.awaitToken(interactive = true) ?: return@attempt SheetSetupResult.Cancelled
        val created = SheetsApi(client, id = "", token, retryPolicy, logger).createSpreadsheet(title)
        val url = checkNotNull(SheetLink.canonicalize(created.url) ?: SheetLink.canonicalize("https://docs.google.com/spreadsheets/d/${created.id}/edit"))
        link(url)
    }

    override suspend fun linkExisting(input: String): SheetSetupResult {
        val url = SheetLink.canonicalize(input) ?: return SheetSetupResult.InvalidLink
        return attempt { link(url) }
    }

    private suspend fun link(url: String): SheetSetupResult {
        if (settings.getSheetUrl() != url) settings.setSheetUrl(url)
        sheetSync.sync(interactive = true)
        return SheetSetupResult.Linked(url, sheetSync.state.value.error)
    }

    private suspend fun attempt(block: suspend () -> SheetSetupResult): SheetSetupResult = try {
        block()
    } catch (e: SyncError) {
        logger.e(TAG, "Linking a sheet failed: ${e::class.simpleName}", e.cause ?: e)
        SheetSetupResult.Failed(e)
    }

    private companion object {
        const val TAG = "SheetSetup"
    }
}
