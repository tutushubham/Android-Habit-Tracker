package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.monotonicUpdatedAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class DateHandlingTest {
    private val newYork = TimeZone.of("America/New_York")
    private val auckland = TimeZone.of("Pacific/Auckland")
    private val honolulu = TimeZone.of("Pacific/Honolulu")
    private val kolkata = TimeZone.of("Asia/Kolkata")

    private val everyDayHabit = DailyHabit("run", "Run", null, 20, 0, true, LocalDate(2026, 1, 1), null, 1, 1)

    // ---- the provider -------------------------------------------------------------------------------

    @Test
    fun todayRollsOverAtLocalMidnightAndNotBefore() {
        val clock = FakeClock(Instant.parse("2026-08-05T23:59:30+05:30"))
        val provider = ClockDateProvider(clock) { kolkata }
        assertEquals(LocalDate(2026, 8, 5), provider.today())
        clock.instant += 29.seconds
        assertEquals(LocalDate(2026, 8, 5), provider.today())
        clock.instant += 1.seconds
        assertEquals(LocalDate(2026, 8, 6), provider.today())
    }

    @Test
    fun monthAndYearEndsRollOver() {
        val clock = FakeClock(Instant.parse("2026-12-31T23:59:59+05:30"))
        val provider = ClockDateProvider(clock) { kolkata }
        assertEquals(LocalDate(2026, 12, 31), provider.today())
        clock.instant += 1.seconds
        assertEquals(LocalDate(2027, 1, 1), provider.today())
    }

    @Test
    fun springForwardDayHas23HoursAndNoDateIsSkippedOrRepeated() {
        // New York, 2026-03-08: 02:00 EST jumps to 03:00 EDT.
        val hours = hourlyDates(Instant.parse("2026-03-07T12:00:00-05:00"), 72)
        assertEquals(listOf(LocalDate(2026, 3, 7), LocalDate(2026, 3, 8), LocalDate(2026, 3, 9), LocalDate(2026, 3, 10)), hours.keys.toList())
        assertEquals(23, hours.getValue(LocalDate(2026, 3, 8)))
    }

    @Test
    fun fallBackDayHas25HoursAndNoDateIsSkippedOrRepeated() {
        // New York, 2026-11-01: 02:00 EDT falls back to 01:00 EST.
        val hours = hourlyDates(Instant.parse("2026-10-31T12:00:00-04:00"), 72)
        assertEquals(listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1), LocalDate(2026, 11, 2), LocalDate(2026, 11, 3)), hours.keys.toList())
        assertEquals(25, hours.getValue(LocalDate(2026, 11, 1)))
    }

    /** Hours spent on each local date, in order, walking [count] hours from [start] in New York. */
    private fun hourlyDates(start: Instant, count: Int): Map<LocalDate, Int> {
        val clock = FakeClock(start)
        val provider = ClockDateProvider(clock) { newYork }
        val perDate = linkedMapOf<LocalDate, Int>()
        repeat(count) {
            provider.today().let { date -> perDate[date] = (perDate[date] ?: 0) + 1 }
            clock.instant += 1.hours
        }
        return perDate
    }

    @Test
    fun theZoneIsReadOnEveryCallSoATravelChangeIsSeenImmediately() {
        val clock = FakeClock(Instant.parse("2026-08-05T23:30:00-04:00"))
        var zone = newYork
        val provider = ClockDateProvider(clock) { zone }
        assertEquals(LocalDate(2026, 8, 5), provider.today())
        zone = auckland
        assertEquals(LocalDate(2026, 8, 6), provider.today())
        zone = honolulu
        assertEquals(LocalDate(2026, 8, 5), provider.today())
        // The instant (and therefore updated_at) is the same wherever the phone is.
        assertEquals(clock.instant.toEpochMilliseconds(), provider.nowEpochMillis())
    }

    // ---- the view model -----------------------------------------------------------------------------

    private fun TestScopeViewModel(
        provider: DateProvider,
        scope: kotlinx.coroutines.CoroutineScope,
        repository: InMemoryHabitRepository = InMemoryHabitRepository(HabitSnapshot(dailyHabits = listOf(everyDayHabit))),
    ) = MonthViewModel(repository, provider, scope) to repository

    @Test
    fun refreshTodayMovesTheSelectedDayAtMidnightWithoutWaitingForThePoll() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T23:59:30+05:30"))
        val (viewModel, _) = TestScopeViewModel(ClockDateProvider(clock) { kolkata }, backgroundScope)
        runCurrent()
        assertEquals(LocalDate(2026, 8, 5), viewModel.state.value.selectedDay)

        clock.instant += 1.minutes
        viewModel.refreshToday()
        runCurrent()

        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.today)
        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.selectedDay)
    }

    @Test
    fun thePollStillCatchesARolloverNobodyAnnounced() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T23:59:30+05:30"))
        val (viewModel, _) = TestScopeViewModel(ClockDateProvider(clock) { kolkata }, backgroundScope)
        runCurrent()

        clock.instant += 1.minutes
        advanceTimeBy(61_000)
        runCurrent()

        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.today)
    }

    @Test
    fun monthFollowsTodayAcrossAMonthEndOnlyIfTheCurrentMonthWasShown() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-31T23:59:30+05:30"))
        val provider = ClockDateProvider(clock) { kolkata }
        val (viewing, _) = TestScopeViewModel(provider, backgroundScope)
        val (browsing, _) = TestScopeViewModel(provider, backgroundScope)
        browsing.previousMonth() // looking at July
        runCurrent()

        clock.instant += 1.minutes
        viewing.refreshToday()
        browsing.refreshToday()
        runCurrent()

        assertEquals(MonthKey(2026, 9), viewing.state.value.selectedMonth)
        assertEquals(MonthKey(2026, 7), browsing.state.value.selectedMonth)
        assertEquals(LocalDate(2026, 9, 1), browsing.state.value.today)
    }

    @Test
    fun aDayThePersonOpenedYesterdayStaysSelectedAfterMidnight() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T23:59:30+05:30"))
        val (viewModel, _) = TestScopeViewModel(ClockDateProvider(clock) { kolkata }, backgroundScope)
        viewModel.openDay(LocalDate(2026, 8, 1)) // not following today any more
        runCurrent()

        clock.instant += 1.minutes
        viewModel.refreshToday()
        runCurrent()

        assertEquals(LocalDate(2026, 8, 1), viewModel.state.value.selectedDay)
        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.today)
    }

    @Test
    fun aCheckOffMadeJustBeforeMidnightStaysOnTheDayItWasMadeFor() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T23:59:50+05:30"))
        val provider = ClockDateProvider(clock) { kolkata }
        val (viewModel, repository) = TestScopeViewModel(provider, backgroundScope)
        runCurrent()

        viewModel.toggleDaily("run", viewModel.state.value.selectedDay)
        runCurrent()
        clock.instant += 1.minutes
        viewModel.refreshToday()
        runCurrent()

        val completion = repository.snapshot.value.dailyCompletions.single()
        assertEquals(LocalDate(2026, 8, 5), completion.date)
        assertTrue(completion.completed)
        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.selectedDay)
    }

    @Test
    fun travellingAcrossTimeZonesNeverMovesOrLosesARecordedCheckOff() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T23:30:00-04:00"))
        var zone = newYork
        val (viewModel, repository) = TestScopeViewModel(ClockDateProvider(clock) { zone }, backgroundScope)
        runCurrent()

        // Evening in New York: check off Aug 5.
        viewModel.toggleDaily("run", viewModel.state.value.today)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 8, 5)), repository.snapshot.value.dailyCompletions.map { it.date })

        // Land in Auckland: it is already Aug 6 there. Aug 5 stays Aug 5; Aug 6 is a new, empty day.
        zone = auckland
        viewModel.refreshToday()
        runCurrent()
        assertEquals(LocalDate(2026, 8, 6), viewModel.state.value.today)
        assertEquals(listOf(LocalDate(2026, 8, 5)), repository.snapshot.value.dailyCompletions.map { it.date })
        viewModel.toggleDaily("run", viewModel.state.value.today)
        runCurrent()
        assertEquals(setOf(LocalDate(2026, 8, 5), LocalDate(2026, 8, 6)), repository.snapshot.value.dailyCompletions.map { it.date }.toSet())

        // Fly on to Honolulu: it is Aug 5 again. Nothing is deleted; "today" is simply the earlier date again.
        zone = honolulu
        viewModel.refreshToday()
        runCurrent()
        assertEquals(LocalDate(2026, 8, 5), viewModel.state.value.today)
        assertEquals(LocalDate(2026, 8, 5), viewModel.state.value.selectedDay)
        assertEquals(2, repository.snapshot.value.dailyCompletions.size)
        assertTrue(repository.snapshot.value.dailyCompletions.all { it.completed })
    }

    @Test
    fun aTimeZoneChangeThatKeepsTheDateChangesNothing() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-05T12:00:00Z"))
        var zone: TimeZone = TimeZone.UTC
        val (viewModel, _) = TestScopeViewModel(ClockDateProvider(clock) { zone }, backgroundScope)
        runCurrent()
        val before = viewModel.state.value

        zone = TimeZone.of("Europe/Paris")
        viewModel.refreshToday()
        runCurrent()

        assertEquals(before.today, viewModel.state.value.today)
        assertEquals(before.selectedDay, viewModel.state.value.selectedDay)
        assertEquals(before.selectedMonth, viewModel.state.value.selectedMonth)
    }

    // ---- updated_at ---------------------------------------------------------------------------------

    @Test
    fun monotonicRuleNeverGoesBackwardsAndStrictModeAlwaysAdvances() {
        assertEquals(100, monotonicUpdatedAt(100, null))
        assertEquals(100, monotonicUpdatedAt(50, 100))
        assertEquals(100, monotonicUpdatedAt(100, 100))
        assertEquals(150, monotonicUpdatedAt(150, 100))
        assertEquals(101, monotonicUpdatedAt(50, 100, strict = true))
        assertEquals(101, monotonicUpdatedAt(100, 100, strict = true))
        assertEquals(150, monotonicUpdatedAt(150, 100, strict = true))
    }

    @Test
    fun aClockSetBackwardsNeverMakesARowOlder() = runTest {
        val day = LocalDate(2026, 8, 5)
        val stamps = mutableListOf<Long>()
        val repository = InMemoryHabitRepository(HabitSnapshot(dailyHabits = listOf(everyDayHabit)))
        listOf(1_000L, 400L, 400L, 2_000L, 10L).forEach { clock ->
            repository.setDailyCompletion(com.habitsheet.domain.model.DailyHabitCompletion("run", day, true, clock))
            stamps += repository.snapshot.value.dailyCompletions.single().updatedAtEpochMillis
        }
        assertEquals(listOf(1_000L, 1_001L, 1_002L, 2_000L, 2_001L), stamps)
    }
}
