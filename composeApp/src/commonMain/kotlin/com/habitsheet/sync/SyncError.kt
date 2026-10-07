package com.habitsheet.sync

import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
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
    class MalformedPlanTab(val row: Int?, val problem: PlanTabProblem) : SyncError(logMessage(row, problem)) {
        /** English technical wording for logs and tests; users see [userMessage]. */
        val reason: String get() = problem.logText
    }

    /** Anything else (HTTP [status] after retries, unexpected failures). */
    class Unknown(val status: Int? = null, cause: Throwable? = null) : SyncError(if (status != null) "Google Sheets error $status" else "Sync failed: ${cause?.message}", cause)
}

private fun logMessage(row: Int?, problem: PlanTabProblem): String =
    if (row != null) "Plan row $row: ${problem.logText}" else "Plan tab ${problem.logText}"

/** What is wrong with the `Plan` tab. [logText] is English for logs; [text] is what the person reads. */
sealed class PlanTabProblem(val logText: String) {
    data object Empty : PlanTabProblem("is empty")
    data object MissingColumns : PlanTabProblem("needs ID, Date, Habit, Session, Done, Skip (optional Area and Source columns)")
    data object BadDate : PlanTabProblem("use YYYY-MM-DD in Date")
    data object MissingRequired : PlanTabProblem("ID, Habit and Session are required")
    class DuplicateId(val id: String) : PlanTabProblem("duplicate ID $id")
    class BadFlag(val column: String) : PlanTabProblem("$column must be a checkbox or TRUE/FALSE")

    fun text(): UiText = when (this) {
        Empty -> UiText.of(Res.string.plan_problem_empty)
        MissingColumns -> UiText.of(Res.string.plan_problem_missing_columns)
        BadDate -> UiText.of(Res.string.plan_problem_bad_date)
        MissingRequired -> UiText.of(Res.string.plan_problem_missing_required)
        is DuplicateId -> UiText.of(Res.string.plan_problem_duplicate_id, id)
        is BadFlag -> UiText.of(Res.string.plan_problem_bad_flag, column)
    }
}

/** Short, actionable text for the person using the app. */
fun SyncError.userMessage(): UiText = when (this) {
    is SyncError.Offline -> UiText.of(Res.string.sync_offline)

    is SyncError.AuthExpired -> UiText.of(Res.string.sync_auth_expired)

    is SyncError.AccessDenied -> UiText.of(Res.string.sync_access_denied)

    is SyncError.NotFound -> UiText.of(Res.string.sync_not_found)

    is SyncError.RateLimited -> UiText.of(Res.string.sync_rate_limited)

    is SyncError.MalformedPlanTab ->
        if (row != null) {
            UiText.of(Res.string.sync_plan_row_problem, row, problem.text())
        } else {
            UiText.of(Res.string.sync_plan_header_problem, problem.text())
        }

    is SyncError.Unknown -> UiText.of(Res.string.sync_failed)
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
        ) {
            return true
        }
        // Platform engines that do not surface IOException (Darwin) or wrap it by name.
        val name = current::class.simpleName.orEmpty()
        if (name.contains("UnknownHost") || name.contains("UnresolvedAddress") ||
            name.contains("ConnectException") || name.contains("DarwinHttpRequestException")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
