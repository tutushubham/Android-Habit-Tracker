package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.buildSnapshot
import com.habitsheet.testing.plusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Creating and linking a sheet from the first-run wizard ([SheetSetup]), against [FakeSheetsServer]. */
class SheetSetupTest {
    private val today = LocalDate(2026, 10, 12)
    private val url = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val title = "Habit Sheet: Marathon"

    private class World(
        val server: FakeSheetsServer = FakeSheetsServer(),
        token: String? = "token",
        tokenError: String? = null,
        today: LocalDate,
    ) {
        var tokenRequests = 0
        val repo = InMemoryHabitRepository(
            buildSnapshot {
                dailyHabit("run", name = "Running", datedOnly = true)
                dayPlan("run", today, "Easy run 5 km")
                dayPlan("run", today.plusDays(2), "Long run 12 km")
            },
        )
        private val provider = object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
                tokenRequests++
                completion(token, tokenError)
            }
        }
        private val retry = RetryPolicy(sleep = {})
        private val client = server.client()
        val sync = SheetSync(repo, repo, provider, client, FakeDateProvider(today = today, nowMillis = 0L), retry)
        val setup = SheetSetup(repo, sync, provider, client, retry)
    }

    @Test
    fun creatingMakesOneSheetWithOnlyAPlanTabLinksItAndFillsItWithTheLocalPlan() = runTest {
        val world = World(today = today)
        val result = world.setup.createAndLink(title)

        assertEquals(SheetSetupResult.Linked(url, syncError = null), result)
        assertEquals(1, world.server.spreadsheetsCreated)
        assertEquals(listOf(title), world.server.createdTitles)
        assertEquals(url, world.repo.getSheetUrl())
        assertEquals(
            listOf(listOf("Running", "Easy run 5 km"), listOf("Running", "Long run 12 km")),
            world.server.rowsAsText().drop(1).map { listOf(it[2], it[3]) },
        )
        assertTrue(world.server.calls.any { it.isFormat }, "the new Plan tab is formatted")
        assertEquals(emptyList(), world.server.violations)
        assertEquals(setOf("run"), world.repo.snapshot.value.sheetManagedHabitIds, "the plan now follows the sheet")
    }

    @Test
    fun aLostReplyToTheCreateIsReportedAsUnconfirmedAndNeverRetried() = runTest {
        val world = World(today = today)
        world.server.fail(FaultAction.LostResponse) { it.isCreate }

        val result = assertIs<SheetSetupResult.Failed>(world.setup.createAndLink(title))

        val error = assertIs<SyncError.CreateUnconfirmed>(result.error)
        assertEquals(title, error.title)
        assertEquals(1, world.server.calls.count { it.isCreate }, "one request, even though Google made the sheet")
        assertEquals("", world.repo.getSheetUrl(), "nothing is linked")
    }

    @Test
    fun aServerErrorOnCreateIsUnconfirmedButARefusalIsAPlainError() = runTest {
        val unconfirmed = World(today = today).apply { server.fail(FaultAction.Status(500)) { it.isCreate } }
        assertIs<SyncError.CreateUnconfirmed>(assertIs<SheetSetupResult.Failed>(unconfirmed.setup.createAndLink(title)).error)
        assertEquals(1, unconfirmed.server.calls.count { it.isCreate })

        val refused = World(today = today).apply { server.fail(FaultAction.Status(403)) { it.isCreate } }
        assertIs<SyncError.AccessDenied>(assertIs<SheetSetupResult.Failed>(refused.setup.createAndLink(title)).error)
        assertEquals("", refused.repo.getSheetUrl())
    }

    @Test
    fun aCancelledSignInChangesNothing() = runTest {
        val world = World(token = null, today = today)
        val before = world.repo.snapshot.value
        assertEquals(SheetSetupResult.Cancelled, world.setup.createAndLink(title))
        assertEquals(emptyList(), world.server.calls)
        assertEquals("", world.repo.getSheetUrl())
        assertEquals(before, world.repo.snapshot.value)
    }

    @Test
    fun noConnectionAtSignInIsOffline() = runTest {
        val world = World(token = null, tokenError = SheetTokenProvider.NETWORK_ERROR, today = today)
        assertIs<SyncError.Offline>(assertIs<SheetSetupResult.Failed>(world.setup.createAndLink(title)).error)
    }

    @Test
    fun whenTheFirstSyncFailsTheSheetStaysLinkedAndTheErrorIsReported() = runTest {
        val world = World(today = today)
        world.server.fail(FaultAction.Status(500), times = 10) { it.isPut }
        val result = assertIs<SheetSetupResult.Linked>(world.setup.createAndLink(title))
        assertNotNull(result.syncError)
        assertEquals(url, world.repo.getSheetUrl())
        assertEquals(1, world.server.spreadsheetsCreated)

        world.server.clearFaults()
        world.sync.sync(interactive = true)
        assertNull(world.sync.state.value.error, "a later sync fills it")
        assertEquals(3, world.server.planValues.size)
    }

    @Test
    fun aFailureAtAnyRequestNeverMakesTwoSheetsNeverWritesOutsideThePlanTabAndKeepsLocalData() = runTest {
        val total = World(today = today).let {
            it.setup.createAndLink(title)
            it.server.calls.size
        }
        for (n in 1..total) {
            for (action in listOf(FaultAction.Status(500), FaultAction.LostResponse, FaultAction.ConnectionFailure)) {
                val world = World(today = today)
                val before = world.repo.snapshot.value.copy(sheetManagedHabitIds = emptySet())
                world.server.failCallNumber(n, action)
                val result = world.setup.createAndLink(title)
                val context = "fault $action at call $n of $total: $result"
                assertTrue(world.server.calls.count { it.isCreate } == 1, context)
                assertEquals(emptyList(), world.server.violations, context)
                assertEquals(before.dailyHabits, world.repo.snapshot.value.dailyHabits, context)
                assertEquals(before.dayPlans, world.repo.snapshot.value.dayPlans, context)
                assertTrue(result is SheetSetupResult.Linked || result is SheetSetupResult.Failed, context)
            }
        }
    }

    @Test
    fun linkingAnExistingSheetValidatesTheLinkAndUsesTheSheetsOwnRows() = runTest {
        val world = World(server = FakeSheetsServer().withPlanRows(listOf("a", "2026-10-13", "Swim", "Pool 1 km", false, false)), today = today)
        assertEquals(SheetSetupResult.InvalidLink, world.setup.linkExisting("https://example.com/sheet"))
        assertEquals(emptyList(), world.server.calls)

        val result = world.setup.linkExisting("https://docs.google.com/spreadsheets/d/test-sheet/edit#gid=0")
        assertEquals(SheetSetupResult.Linked(url, syncError = null), result)
        assertEquals(0, world.server.spreadsheetsCreated)
        val habits = world.repo.snapshot.value.dailyHabits.map { it.name }.toSet()
        assertEquals(setOf("Running", "Swim"), habits)
        assertTrue(world.server.rowsAsText().any { it[2] == "Running" }, "local-only habits are appended")
    }
}
