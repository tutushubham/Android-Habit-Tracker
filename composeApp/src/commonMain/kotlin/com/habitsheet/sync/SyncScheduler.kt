package com.habitsheet.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coalesces sync triggers so a burst of check-offs costs one sync instead of one per tap.
 *
 *  - [markDirty] (a local change): waits for [debounceMillis] of quiet, then syncs once.
 *  - [syncNow] (foreground, "Sync now"): syncs immediately, absorbing any pending debounce.
 *  - A trigger that arrives while a sync is running is queued and produces exactly one follow-up run
 *    (after its own debounce for [markDirty]); any number of triggers collapse into that one run.
 *  - If a run ends with [shouldRetry] true (offline with check-offs still waiting to upload), the scheduler
 *    tries again by itself after [retryDelayMillis], doubling up to [maxRetryDelayMillis]; it stops as soon as
 *    [shouldRetry] turns false. Any new trigger is handled normally in the meantime.
 *
 * Runs are strictly sequential: a single consumer coroutine owns the loop, so no extra locking is needed.
 */
class SyncScheduler(
    scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_MILLIS,
    private val maxRetryDelayMillis: Long = DEFAULT_MAX_RETRY_MILLIS,
    private val shouldRetry: () -> Boolean = { false },
    private val runSync: suspend (interactive: Boolean) -> Unit,
) {
    private sealed interface Trigger {
        data object Dirty : Trigger
        data object Retry : Trigger
        data class Now(val interactive: Boolean) : Trigger
    }

    private val triggers = Channel<Trigger>(Channel.UNLIMITED)

    init {
        scope.launch {
            var retryDelay: Long? = null
            while (true) {
                var current: Trigger = nextTrigger(retryDelay) ?: break
                // Trailing debounce: every further trigger within the quiet window restarts the wait.
                while (current is Trigger.Dirty) {
                    val more = withTimeoutOrNull(debounceMillis) { triggers.receiveCatching() } ?: break
                    current = more.getOrNull() ?: break
                }
                val interactive = (current as? Trigger.Now)?.interactive == true
                try {
                    runSync(interactive)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // SheetSync reports its own failures; the scheduler must never die.
                }
                retryDelay = if (shouldRetry()) {
                    ((retryDelay ?: (retryDelayMillis / 2)) * 2).coerceAtMost(maxRetryDelayMillis)
                } else null
            }
        }
    }

    /** The next trigger; [Trigger.Retry] if none arrives within [retryDelay]; null once the scheduler is closed. */
    private suspend fun nextTrigger(retryDelay: Long?): Trigger? {
        if (retryDelay == null) return triggers.receiveCatching().getOrNull()
        val result = withTimeoutOrNull(retryDelay) { triggers.receiveCatching() } ?: return Trigger.Retry
        return result.getOrNull()
    }

    /** A local change that should reach the sheet soon. Cheap; safe to call per tap from any thread. */
    fun markDirty() {
        triggers.trySend(Trigger.Dirty)
    }

    /** Sync as soon as any running sync finishes. */
    fun syncNow(interactive: Boolean = false) {
        triggers.trySend(Trigger.Now(interactive))
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 2_500L
        const val DEFAULT_RETRY_MILLIS = 30_000L
        const val DEFAULT_MAX_RETRY_MILLIS = 300_000L
    }
}
