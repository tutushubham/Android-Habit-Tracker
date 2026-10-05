package com.habitsheet.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SerializingTokenProviderTest {
    /** A platform provider that answers only when the test says so. */
    private class ManualProvider : SheetTokenProvider {
        val inFlight = mutableListOf<Pair<Boolean, (String?, String?) -> Unit>>()
        var maxConcurrent = 0
        var totalCalls = 0
        var throwOnNext: Exception? = null

        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
            throwOnNext?.let {
                throwOnNext = null
                throw it
            }
            totalCalls++
            inFlight += interactive to completion
            maxConcurrent = maxOf(maxConcurrent, inFlight.size)
        }

        fun answer(token: String?, error: String? = null) {
            inFlight.removeAt(0).second(token, error)
        }
    }

    private val results = mutableListOf<String>()
    private fun record(label: String): (String?, String?) -> Unit = { token, error -> results += "$label:${token ?: "-"}:${error ?: "-"}" }

    @Test
    fun concurrentRequestsReachThePlatformOneAtATimeAndEachGetsItsOwnResult() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, scope = backgroundScope)

        provider.requestToken(true, record("a"))
        provider.requestToken(true, record("b"))
        provider.requestToken(false, record("c"))
        runCurrent()
        assertEquals(1, platform.totalCalls, "only the first request is started")

        platform.answer("token-a")
        runCurrent()
        assertEquals(2, platform.totalCalls)
        platform.answer(null, null)
        runCurrent() // b: user cancelled
        platform.answer("token-c")
        runCurrent()

        assertEquals(listOf("a:token-a:-", "b:-:-", "c:token-c:-"), results)
        assertEquals(1, platform.maxConcurrent)
    }

    @Test
    fun aPlatformThatNeverAnswersTimesOutAndTheQueueKeepsWorking() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, interactiveTimeoutMillis = 1_000, backgroundTimeoutMillis = 100, scope = backgroundScope)

        provider.requestToken(true, record("stuck"))
        provider.requestToken(false, record("next"))
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf("stuck:-:Google authorization timed out."), results)

        platform.inFlight.removeAt(0) // the abandoned dialog never reports back; the next request is now in flight
        platform.answer("late-but-valid")
        runCurrent()
        assertEquals("next:late-but-valid:-", results.last())
    }

    @Test
    fun aLateAnswerAfterTheTimeoutIsIgnoredAndNeverCompletesTwice() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, interactiveTimeoutMillis = 1_000, scope = backgroundScope)
        provider.requestToken(true, record("a"))
        runCurrent()
        val late = platform.inFlight.single().second
        advanceTimeBy(1_001)
        runCurrent()
        late("too-late", null)
        runCurrent()
        assertEquals(listOf("a:-:Google authorization timed out."), results)
    }

    @Test
    fun platformThatCallsBackTwiceCompletesOnce() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, scope = backgroundScope)
        provider.requestToken(true, record("a"))
        runCurrent()
        val callback = platform.inFlight.single().second
        callback("one", null)
        callback("two", null)
        runCurrent()
        assertEquals(listOf("a:one:-"), results)
    }

    @Test
    fun platformThatThrowsBecomesAnErrorCompletionAndDoesNotStopTheQueue() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, scope = backgroundScope)
        platform.throwOnNext = IllegalStateException("Activity destroyed")
        provider.requestToken(true, record("a"))
        provider.requestToken(true, record("b"))
        runCurrent()
        platform.answer("tok")
        runCurrent()
        assertEquals(listOf("a:-:Activity destroyed", "b:tok:-"), results)
    }

    @Test
    fun aThrowingCompletionDoesNotKillTheQueue() = runTest {
        val platform = ManualProvider()
        val provider = SerializingTokenProvider(platform, scope = backgroundScope)
        provider.requestToken(true) { _, _ -> error("caller bug") }
        provider.requestToken(true, record("b"))
        runCurrent()
        platform.answer("x")
        runCurrent()
        platform.answer("y")
        runCurrent()
        assertEquals(listOf("b:y:-"), results)
    }

    @Test
    fun requestsAfterCloseAreAnsweredImmediatelyWithAnError() = runTest {
        val provider = SerializingTokenProvider(ManualProvider(), scope = backgroundScope)
        provider.close()
        provider.requestToken(true, record("late"))
        assertEquals(listOf("late:-:Google authorization is shut down."), results)
    }
}
