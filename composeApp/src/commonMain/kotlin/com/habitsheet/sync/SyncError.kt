package com.habitsheet.sync

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * Why a sync did not complete. Technical [message]s are for logs; show users [userMessage].
 * Only [Offline] is "not really an error": the work is kept and retried automatically.
 */
sealed class SyncError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No usable network (no connection, DNS failure, timeouts after retries). */
    class Offline(cause: Throwable? = null) : SyncError("Offline", cause)

    /** Google rejected the token (401) or sign-in failed/was cancelled. */
    class AuthExpired(val detail: String? = null) : SyncError(detail ?: "Google session expired")

    /** 403: signed-in account cannot edit this sheet, or the Sheets API / test-user setup is missing. */
    class AccessDenied : SyncError("Sheet access denied")

    /** 404: link is wrong or the account cannot see the spreadsheet. */
    class NotFound : SyncError("Spreadsheet not found")

    /** 429 (or a 403 quota reply) that outlasted the retries; [retryAfterSeconds] if Google said how long. */
    class RateLimited(val retryAfterSeconds: Long? = null) : SyncError("Rate limited")

    /** The `Plan` tab exists but is not a valid table. [row] is the 1-based sheet row, or null for the header. */
    class MalformedPlanTab(val row: Int?, val reason: String) :
        SyncError(if (row != null) "Plan row $row: $reason" else "Plan tab $reason")

    /** Anything else (HTTP [status] after retries, unexpected failures). */
    class Unknown(val status: Int? = null, cause: Throwable? = null) :
        SyncError(if (status != null) "Google Sheets error $status" else "Sync failed: ${cause?.message}", cause)
}

/** Short, actionable text for the person using the app. */
fun SyncError.userMessage(): String = when (this) {
    is SyncError.Offline -> "Will sync when online."
    is SyncError.AuthExpired -> "Google sign-in needed. Tap Connect & sync to sign in again."
    is SyncError.AccessDenied -> "No access to this sheet. Use a Google account that can edit it."
    is SyncError.NotFound -> "Spreadsheet not found. Check the link and the Google account."
    is SyncError.RateLimited -> "Google is limiting requests. Sync will retry shortly."
    is SyncError.MalformedPlanTab ->
        if (row != null) "Plan tab, row $row: $reason. Fix the sheet, then sync again."
        else "Plan tab $reason. Fix the header row, then sync again."
    is SyncError.Unknown -> "Sync failed. Try again in a moment."
}

/** Classifies any failure from a sync attempt. Never call with a [CancellationException]. */
internal fun Throwable.toSyncError(): SyncError {
    if (this is SyncError) return this
    return if (isConnectivityFailure()) SyncError.Offline(this) else SyncError.Unknown(cause = this)
}

private fun Throwable.isConnectivityFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth++ < 6) {
        if (current is CancellationException) return false
        if (current is IOException ||
            current is HttpRequestTimeoutException ||
            current is ConnectTimeoutException ||
            current is SocketTimeoutException
        ) return true
        // Platform engines that do not surface IOException (Darwin) or wrap it by name.
        val name = current::class.simpleName.orEmpty()
        if (name.contains("UnknownHost") || name.contains("UnresolvedAddress") ||
            name.contains("ConnectException") || name.contains("DarwinHttpRequestException")
        ) return true
        current = current.cause
    }
    return false
}
