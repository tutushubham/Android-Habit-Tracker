package com.habitsheet.sync

import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Makes any platform [SheetTokenProvider] safe to call concurrently:
 *  - requests run strictly one at a time, in arrival order (a second sign-in sheet can never be launched while
 *    the first is open, and no result is delivered to the wrong caller);
 *  - every caller's completion is invoked exactly once, even if the platform never answers (the Activity was
 *    destroyed, the sign-in UI was dismissed without a callback) or throws.
 */
class SerializingTokenProvider(
    private val delegate: SheetTokenProvider,
    private val interactiveTimeoutMillis: Long = 3 * 60_000L,
    private val backgroundTimeoutMillis: Long = 30_000L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val logger: Logger = NoOpLogger,
) : SheetTokenProvider,
    AutoCloseable {
    private class Request(val interactive: Boolean, val completion: (String?, String?) -> Unit)

    private companion object {
        const val TAG = "TokenProvider"
    }

    private val requests = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in requests) {
                val timeout = if (request.interactive) interactiveTimeoutMillis else backgroundTimeoutMillis
                val outcome = withTimeoutOrNull(timeout) {
                    suspendCancellableCoroutine<Pair<String?, String?>> { continuation ->
                        try {
                            delegate.requestToken(request.interactive) { token, error ->
                                if (continuation.isActive) continuation.resume(token to error)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.e(TAG, "Platform token provider threw", e)
                            if (continuation.isActive) continuation.resume(null to (e.message ?: "Google authorization failed."))
                        }
                    }
                } ?: (null to "Google authorization timed out.").also {
                    logger.w(TAG, "Token request timed out (interactive=${request.interactive})")
                }
                try {
                    request.completion(outcome.first, outcome.second)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A misbehaving caller must not stop the queue.
                    logger.e(TAG, "Token completion callback threw", e)
                }
            }
        }
    }

    override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
        if (requests.trySend(Request(interactive, completion)).isFailure) completion(null, "Google authorization is shut down.")
    }

    override fun close() {
        requests.close()
        scope.cancel()
    }
}
