package com.habitsheet.platform

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.presentation.DateProvider
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.sync.FakeSheetsServer
import com.habitsheet.sync.FaultAction
import com.habitsheet.sync.RetryPolicy
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.testing.English
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecordingLogger : Logger {
    data class Entry(val level: LogLevel, val tag: String, val message: String, val throwable: Throwable?)

    val entries = mutableListOf<Entry>()
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        entries += Entry(level, tag, message, throwable)
    }

    /** Everything a platform logger would print. */
    fun printed(): String = entries.joinToString("\n") { "${it.tag} ${formatLogLine(it.message, it.throwable)}" }
}

class LoggerTest {
    private val dates = object : DateProvider {
        override fun today() = LocalDate(2026, 10, 1)
        override fun nowEpochMillis() = 0L
    }

    @Test
    fun runCatchingCancellableCapturesFailuresButRethrowsCancellation() {
        val failure = runCatchingCancellable { error("boom") }.exceptionOrNull()
        assertIs<IllegalStateException>(failure)
        assertEquals(5, runCatchingCancellable { 5 }.getOrThrow())
        assertFailsWith<CancellationException> { runCatchingCancellable { throw CancellationException("cancelled") } }
    }

    @Test
    fun throwableIsLoggedByClassNameOnlyNeverByMessage() {
        val error = IllegalStateException("Run 5k at https://docs.google.com/spreadsheets/d/SECRETID", RuntimeException("Bearer ya29.TOKEN"))
        val line = formatLogLine("something failed", error)
        assertEquals("something failed [IllegalStateException <- RuntimeException]", line)
    }

    @Test
    fun failedSaveIsLoggedWithoutUserTextAndStillShowsTheUserMessage() = runTest {
        val logger = RecordingLogger()
        val inner = InMemoryHabitRepository()
        val failing = object : HabitRepository by inner {
            override suspend fun saveCategory(category: Category) = error("cannot save ${category.name}")
        }
        val viewModel = ManageHabitsViewModel(failing, DefaultIdGenerator(), dates, backgroundScope, logger)

        viewModel.saveCategory("c1", "Private Category Name", 0)
        yield()
        testScheduler.advanceUntilIdle()

        assertEquals("Couldn't save category.", English.render(assertNotNull(viewModel.error.value)), "entries=${logger.entries}")
        val entry = logger.entries.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertFalse("Private Category Name" in logger.printed())
    }

    @Test
    fun cancelledOperationIsNeitherLoggedNorShownAsAnError() = runTest {
        val logger = RecordingLogger()
        val inner = InMemoryHabitRepository()
        val cancelling = object : HabitRepository by inner {
            override suspend fun saveCategory(category: Category) = throw CancellationException("scope closed")
        }
        val viewModel = ManageHabitsViewModel(cancelling, DefaultIdGenerator(), dates, backgroundScope, logger)

        viewModel.saveCategory("c1", "Name", 0)
        yield()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.error.value)
        assertTrue(logger.entries.isEmpty())
    }

    @Test
    fun syncFailureLogsNeverContainSheetIdTokenOrHabitText() = runTest {
        val day = LocalDate(2026, 10, 1)
        val logger = RecordingLogger()
        val repo = InMemoryHabitRepository(
            HabitSnapshot(
                dailyHabits = listOf(DailyHabit("run", "Secret Habit", null, 12, 0, true, day, null, 1, 1, datedOnly = true)),
                dayPlans = listOf(DayPlan("run", day, "Secret Session", false, 1, "run-1")),
            ),
        ).also { it.setSheetUrl("https://docs.google.com/spreadsheets/d/SECRETSHEETID/edit") }
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Secret Habit", "Secret Session", false, false))
        server.fail(FaultAction.Status(500), times = 100)
        val sync = SheetSync(
            repo,
            repo,
            object : SheetTokenProvider {
                override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("tok-SECRET", null)
            },
            server.client(),
            dates,
            RetryPolicy(sleep = { }),
            logger,
        )

        sync.sync()

        val printed = logger.printed()
        assertTrue(logger.entries.any { it.level == LogLevel.ERROR && it.tag == "SheetSync" })
        assertTrue("HTTP 500" in printed)
        listOf("SECRETSHEETID", "tok-SECRET", "Secret Habit", "Secret Session", "docs.google.com").forEach {
            assertFalse(it in printed, "log leaked: $it")
        }
    }
}
