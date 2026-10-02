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
 *
 * Runs are strictly sequential: a single consumer coroutine owns the loop, so no extra locking is needed.
 */
class SyncScheduler(
    scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val runSync: suspend (interactive: Boolean) -> Unit,
) {
    private sealed interface Trigger {
        data object Dirty : Trigger
        data class Now(val interactive: Boolean) : Trigger
    }

    private val triggers = Channel<Trigger>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in triggers) {
                var current: Trigger = first
                // Trailing debounce: every further trigger within the quiet window restarts the wait.
                while (current is Trigger.Dirty) {
                    current = withTimeoutOrNull(debounceMillis) { triggers.receive() } ?: break
                }
                val interactive = (current as? Trigger.Now)?.interactive == true
                try {
                    runSync(interactive)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // SheetSync reports its own failures; the scheduler must never die.
                }
            }
        }
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
    }
}
