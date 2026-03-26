package com.tutushubham.pokidex.feature_capture

import com.tutushubham.pokidex.core.capture
import com.tutushubham.pokidex.core.domain.entity.Capture
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.repository.CaptureRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCaptureRepository(
        private val captures: MutableList<Capture> = mutableListOf()
    ) : CaptureRepository {
        fun snapshot(): List<Capture> = captures.toList()

        override suspend fun getAllCaptures(): List<Capture> = captures.toList()
        override suspend fun getUnresolvedCaptures(): List<Capture> =
            captures.filter { !it.resolved }
        override suspend fun insertCapture(capture: Capture) {
            captures.add(capture)
        }
        override suspend fun updateCapture(capture: Capture) {
            val i = captures.indexOfFirst { it.id == capture.id }
            if (i >= 0) captures[i] = capture
        }
        override suspend fun deleteCapture(id: String) {
            captures.removeAll { it.id == id }
        }
        override suspend fun getCaptureById(id: String): Capture? =
            captures.firstOrNull { it.id == id }
    }

    private class FakeIntentRepository : IntentRepository {
        val inserted = mutableListOf<GoalIntent>()
        override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate) =
            emptyList<GoalIntent>()
        override suspend fun insertIntent(intent: GoalIntent) {
            inserted.add(intent)
        }
        override suspend fun updateIntent(intent: GoalIntent) {}
        override suspend fun getIntentById(id: String) = null
    }

    @Test
    fun `screenOpened loads captures`() = runTest {
        val open = capture(id = "c-open", content = "Open thought", resolved = false)
        val done = capture(id = "c-done", content = "Done", resolved = true)
        val capRepo = FakeCaptureRepository(mutableListOf(open, done))
        val intentRepo = FakeIntentRepository()
        val vm = CaptureViewModel(capRepo, intentRepo)

        vm.onEvent(CaptureContract.CaptureEvent.ScreenOpened)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.captures.size)
        assertEquals("Open thought", state.captures.single().content)
        assertEquals(1, state.processedCount)
    }

    @Test
    fun `addCapture inserts and reloads`() = runTest {
        val capRepo = FakeCaptureRepository(mutableListOf())
        val intentRepo = FakeIntentRepository()
        val vm = CaptureViewModel(capRepo, intentRepo)

        vm.onEvent(CaptureContract.CaptureEvent.ScreenOpened)
        advanceUntilIdle()
        assertTrue(vm.state.value.captures.isEmpty())

        vm.onEvent(CaptureContract.CaptureEvent.AddCapture("New note"))
        advanceUntilIdle()

        assertEquals(1, capRepo.snapshot().size)
        assertEquals("New note", vm.state.value.captures.single().content)
    }

    @Test
    fun `convertToGoal creates intent and marks capture resolved`() = runTest {
        val cap = capture(id = "cap-1", content = "Become a runner", resolved = false)
        val capRepo = FakeCaptureRepository(mutableListOf(cap))
        val intentRepo = FakeIntentRepository()
        val vm = CaptureViewModel(capRepo, intentRepo)

        vm.onEvent(CaptureContract.CaptureEvent.ScreenOpened)
        advanceUntilIdle()

        vm.onEvent(CaptureContract.CaptureEvent.ConvertToGoal("cap-1"))
        advanceUntilIdle()

        assertEquals(1, intentRepo.inserted.size)
        assertEquals("Become a runner", intentRepo.inserted.single().title)

        val stored = capRepo.getCaptureById("cap-1")
        assertTrue(stored!!.resolved)
        assertEquals(intentRepo.inserted.single().id, stored.resolvedSessionId)

        assertEquals(
            CaptureContract.CaptureEffect.ShowMessage("Converted to goal"),
            vm.effect.first()
        )
        val nav = vm.effect.first() as CaptureContract.CaptureEffect.NavigateToGoalEdit
        assertEquals(intentRepo.inserted.single().id, nav.intentId)

        assertTrue(vm.state.value.captures.none { it.id == "cap-1" })
    }

    @Test
    fun `deleteCapture removes and reloads`() = runTest {
        val cap = capture(id = "del-1", content = "Delete me", resolved = false)
        val capRepo = FakeCaptureRepository(mutableListOf(cap))
        val intentRepo = FakeIntentRepository()
        val vm = CaptureViewModel(capRepo, intentRepo)

        vm.onEvent(CaptureContract.CaptureEvent.ScreenOpened)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.captures.size)

        vm.onEvent(CaptureContract.CaptureEvent.DeleteCapture("del-1"))
        advanceUntilIdle()

        assertTrue(capRepo.snapshot().isEmpty())
        assertTrue(vm.state.value.captures.isEmpty())
        assertEquals(
            CaptureContract.CaptureEffect.ShowMessage("Thought deleted"),
            vm.effect.first()
        )
    }
}
