package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.presentation.DateProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetSyncTest {
    private val day = LocalDate(2026, 10, 1)
    private val run = DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1)
    private val url = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val provider = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
            completion("test-token", null)
        }
    }
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 0L
    }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun uploadsOfflineCheckWithoutOverwritingItFromSheet() = runTest {
        val repo = InMemoryHabitRepository(
            HabitSnapshot(
                dailyHabits = listOf(run),
                dayPlans = listOf(DayPlan("run", day, "Easy 6 km", false, 1, "run-1")),
                dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1")),
            ),
        )
        repo.setSheetUrl(url)
        repo.setSheetLastSync(100)
        repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 150, "run-1")) // marks it pending
        var writes = 0
        val client = HttpClient(
            MockEngine { request ->
                when {
                    request.url.toString().contains("values:batchUpdate") -> {
                        writes++
                        respond("{}", headers = jsonHeaders)
                    }

                    request.url.toString().contains("/values/") -> respond(
                        """{"values":[
                    ["ID","Date","Habit","Session","Done","Skip"],
                    ["run-1","2026-10-01","Run","Easy 6 km",false,false]
                ]}""",
                        headers = jsonHeaders,
                    )

                    else -> respond("""{"sheets":[{"properties":{"title":"Plan"}}]}""", headers = jsonHeaders)
                }
            },
        )
        SheetSync(repo, repo, provider, client, dates).sync(interactive = true)
        assertEquals(1, writes)
        assertTrue(repo.snapshot.value.dailyCompletions.single().completed)
        assertEquals(setOf("run"), repo.snapshot.value.sheetManagedHabitIds)
    }

    @Test
    fun missingTabSeedsDatedAndDailyRows() = runTest {
        val protein = DailyHabit("protein", "Protein", null, 30, 1, true, day, null, 1, 1)
        val repo = InMemoryHabitRepository(
            HabitSnapshot(
                dailyHabits = listOf(run, protein),
                dayPlans = listOf(DayPlan("run", day, "Easy 6 km", false, 1)),
                weeklyPlans = listOf(WeeklyPlan("run", 2, "Plan in OND sheet", 1)),
            ),
        )
        repo.setSheetUrl(url)
        var created = false
        var formatted = false
        var uploaded = false
        val client = HttpClient(
            MockEngine { request ->
                when {
                    request.url.toString().contains(":batchUpdate") -> {
                        if (created) {
                            formatted = true
                            respond("{}", headers = jsonHeaders)
                        } else {
                            created = true
                            respond("""{"replies":[{"addSheet":{"properties":{"sheetId":10}}}]}""", headers = jsonHeaders)
                        }
                    }

                    request.url.toString().contains("/values/") -> {
                        uploaded = true
                        respond("{}", headers = jsonHeaders)
                    }

                    else -> respond("""{"sheets":[]}""", headers = jsonHeaders)
                }
            },
        )
        val sync = SheetSync(repo, repo, provider, client, dates)
        sync.sync(interactive = true)
        assertTrue(created)
        assertTrue(formatted)
        assertTrue(uploaded)
        val window = SheetSyncWindow.rolling(day)
        val dailyRows = (window.start.toEpochDays()..window.end.toEpochDays()).count { it >= day.toEpochDays() }
        assertEquals(dailyRows * 2, repo.getSheetSyncedKeys().size)
        assertTrue(repo.snapshot.value.weeklyPlans.isEmpty())
        assertEquals(setOf("run", "protein"), repo.snapshot.value.sheetManagedHabitIds)
    }
}
