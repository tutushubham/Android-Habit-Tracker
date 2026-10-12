package com.habitsheet.presentation.onboarding

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MAX_DAILY_HABITS
import com.habitsheet.domain.plan.PlanApplication
import com.habitsheet.domain.plan.PlanLevel
import com.habitsheet.domain.plan.PlanTemplateId
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.platform.ReminderPlatform
import com.habitsheet.presentation.DailyReminders
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import com.habitsheet.sync.SheetLinker
import com.habitsheet.sync.SheetSetupResult
import com.habitsheet.sync.SyncError
import com.habitsheet.testing.English
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.FakeNotificationPermission
import com.habitsheet.testing.FakeReminderScheduler
import com.habitsheet.testing.buildSnapshot
import com.habitsheet.testing.plusDays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val today = LocalDate(2026, 10, 12)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterTest
    fun close() = scope.cancel()

    private class FakeLinker(var result: SheetSetupResult) : SheetLinker {
        val created = mutableListOf<String>()
        val linked = mutableListOf<String>()

        override suspend fun createAndLink(title: String): SheetSetupResult = result.also { created += title }

        override suspend fun linkExisting(input: String): SheetSetupResult = result.also { linked += input }
    }

    private val url = "https://docs.google.com/spreadsheets/d/abc/edit"
    private val linker = FakeLinker(SheetSetupResult.Linked(url, syncError = null))
    private val scheduler = FakeReminderScheduler()
    private val permission = FakeNotificationPermission()

    private fun world(snapshot: HabitSnapshot = HabitSnapshot(), store: (InMemoryHabitRepository) -> HabitRepository = { it }): Pair<InMemoryHabitRepository, OnboardingViewModel> {
        val repo = InMemoryHabitRepository(snapshot)
        val reminders = DailyReminders(repo, ReminderPlatform(scheduler, permission), resolveText = English::render)
        val vm = OnboardingViewModel(
            store(repo),
            OnboardingServices(sheetLinker = linker, reminders = reminders, idGenerator = DefaultIdGenerator()),
            FakeDateProvider(today = today, nowMillis = 1_000L, tickMillis = 1),
            scope,
            resolveText = English::render,
        )
        return repo to vm
    }

    private fun OnboardingViewModel.throughPreview(template: PlanTemplateId = PlanTemplateId.MARATHON) {
        start()
        next()
        plan.chooseTemplate(template)
        plan.buildPlan()
    }

    @Test
    fun aFreshInstallStartsTheWizardAndAnExistingUserDoesNot() = runTest {
        assertTrue(world().second.autoStart.value)
        assertFalse(world(buildSnapshot { dailyHabit("read") }).second.autoStart.value, "someone with habits")
        val done = InMemoryHabitRepository().apply { setOnboardingCompleted(true) }
        assertFalse(OnboardingViewModel(done, OnboardingServices(idGenerator = DefaultIdGenerator()), scope = scope).autoStart.value, "completed before")
        val (_, vm) = world()
        vm.start()
        assertFalse(vm.autoStart.value, "started: not again")
    }

    @Test
    fun theMarathonPathAddsThePlanSetsAReminderCreatesASheetAndCompletesOnboarding() = runTest {
        val (repo, vm) = world()
        vm.start()
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        assertEquals(7, vm.state.value.steps.size)
        vm.next()
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)

        vm.plan.chooseTemplate(PlanTemplateId.MARATHON)
        assertEquals(OnboardingStep.QUESTIONS, vm.state.value.step)
        assertEquals(today.plusDays(112), vm.state.value.questions?.eventDate, "a race date is suggested")
        vm.plan.updateQuestions { it.copy(daysPerWeek = 3, level = PlanLevel.NEW) }
        vm.plan.buildPlan()
        assertEquals(OnboardingStep.PREVIEW, vm.state.value.step)
        assertEquals(4, vm.state.value.stepNumber, "preview is step 4 of 7")

        vm.plan.setHabitEnabled("stretch", false)
        vm.plan.createPlan()
        assertEquals(OnboardingStep.REMINDER, vm.state.value.step)
        val habits = repo.snapshot.value.dailyHabits.map { it.name }
        assertEquals(listOf("Running", "Sleep 8 hours"), habits)
        assertTrue(repo.snapshot.value.dayPlans.any { it.detail == "Race day: marathon" })
        val summary = vm.state.value.summary!!
        assertEquals(2, summary.addedHabits)
        assertEquals(repo.snapshot.value.dayPlans.size, summary.sessions)

        vm.reminder.setMinute(6 * 60 + 45)
        vm.reminder.turnOn()
        assertEquals(DailyReminder(405), scheduler.scheduled?.reminder)
        assertEquals(OnboardingStep.SHEET, vm.state.value.step)

        vm.sheet.create()
        assertEquals(listOf("Habit Sheet: Marathon"), linker.created)
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertEquals(url, vm.state.value.sheetUrl)

        vm.finish()
        assertTrue(vm.state.value.finished)
        assertTrue(repo.isOnboardingCompleted())
        assertFalse(vm.autoStart.value)
        vm.onLeft()
        assertEquals(OnboardingUiState(), vm.state.value)
    }

    @Test
    fun backWalksTheStepsAndOnTheFirstStepLeavesItToTheScreen() = runTest {
        val (_, vm) = world()
        vm.throughPreview()
        assertTrue(vm.back())
        assertEquals(OnboardingStep.QUESTIONS, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        assertFalse(vm.back())

        vm.throughPreview()
        vm.plan.createPlan()
        assertTrue(vm.back())
        assertEquals(OnboardingStep.REMINDER, vm.state.value.step, "the plan is added: no way back to the preview")
        vm.reminder.skip()
        assertTrue(vm.back())
        assertEquals(OnboardingStep.REMINDER, vm.state.value.step)
    }

    @Test
    fun invalidAnswersAreExplainedAndKeepTheQuestionsOpen() = runTest {
        val (_, vm) = world()
        vm.start()
        vm.plan.chooseTemplate(PlanTemplateId.HYROX)
        vm.plan.updateQuestions { it.copy(eventDate = today.plusDays(10)) }
        vm.plan.buildPlan()
        assertEquals(OnboardingStep.QUESTIONS, vm.state.value.step)
        assertEquals(UiText.of(Res.string.onboarding_problem_event_too_soon), vm.state.value.message)
        vm.plan.updateQuestions { it.copy(eventDate = null) }
        assertNull(vm.state.value.message, "an edit clears the message")
        vm.plan.buildPlan()
        assertEquals(UiText.of(Res.string.onboarding_problem_event_missing), vm.state.value.message)
    }

    @Test
    fun startingFromScratchFinishesAndOpensHabits() = runTest {
        val (repo, vm) = world()
        vm.start()
        vm.plan.chooseTemplate(PlanTemplateId.SCRATCH)
        assertTrue(vm.state.value.finished)
        assertTrue(vm.state.value.openHabits)
        assertTrue(repo.isOnboardingCompleted())
        assertTrue(repo.snapshot.value.dailyHabits.isEmpty())
    }

    @Test
    fun skippingCompletesOnboardingWithoutAddingAnything() = runTest {
        val (repo, vm) = world()
        vm.start()
        vm.finish()
        assertTrue(repo.isOnboardingCompleted())
        assertTrue(repo.snapshot.value.dailyHabits.isEmpty())
    }

    @Test
    fun aRerunAddsToExistingHabitsAndSkipsTheSheetStepWhenOneIsLinked() = runTest {
        val (repo, vm) = world(buildSnapshot { dailyHabit("mine", name = "Running") })
        repo.setSheetUrl(url)
        vm.throughPreview()
        assertEquals(1, vm.state.value.existingHabits)
        assertEquals(6, vm.state.value.steps.size)
        vm.plan.createPlan()
        assertEquals(1, vm.state.value.summary?.reusedHabits)
        vm.reminder.skip()
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
    }

    @Test
    fun aPlanPastTheHabitLimitIsRefusedWithTheLimit() = runTest {
        val (repo, vm) = world(buildSnapshot { repeat(MAX_DAILY_HABITS - 1) { dailyHabit("h$it", name = "Habit $it") } })
        vm.throughPreview()
        vm.plan.createPlan()
        assertEquals(OnboardingStep.PREVIEW, vm.state.value.step)
        assertEquals(UiText.of(Res.string.onboarding_err_too_many, MAX_DAILY_HABITS), vm.state.value.message)
        assertEquals(MAX_DAILY_HABITS - 1, repo.snapshot.value.dailyHabits.size)
    }

    @Test
    fun aFailedSaveSaysNothingChanged() = runTest {
        val (repo, vm) = world(store = { FailingPlanRepository(it) })
        vm.throughPreview()
        vm.plan.createPlan()
        assertEquals(OnboardingStep.PREVIEW, vm.state.value.step)
        assertEquals(UiText.of(Res.string.onboarding_err_create), vm.state.value.message)
        assertFalse(vm.state.value.busy)
        assertTrue(repo.snapshot.value.dailyHabits.isEmpty())
    }

    @Test
    fun aRefusedNotificationPermissionExplainsAndLetsThePersonContinue() = runTest {
        permission.answer = false
        val (repo, vm) = world()
        vm.throughPreview()
        vm.plan.createPlan()
        vm.reminder.turnOn()
        assertEquals(OnboardingStep.REMINDER, vm.state.value.step)
        assertTrue(vm.state.value.reminderDenied)
        assertEquals(UiText.of(Res.string.onboarding_reminder_denied), vm.state.value.message)
        assertNull(repo.getDailyReminder())
        vm.reminder.openSettings()
        assertEquals(1, permission.settingsOpened)
        vm.reminder.skip()
        assertEquals(OnboardingStep.SHEET, vm.state.value.step)
    }

    @Test
    fun sheetOutcomesKeepThePersonOnTheStepWithAReasonOrFinishWithAWarning() = runTest {
        val (_, vm) = world()
        vm.throughPreview()
        vm.plan.createPlan()
        vm.reminder.skip()

        linker.result = SheetSetupResult.Cancelled
        vm.sheet.create()
        assertEquals(OnboardingStep.SHEET, vm.state.value.step)
        assertEquals(UiText.of(Res.string.onboarding_sheet_cancelled), vm.state.value.message)

        linker.result = SheetSetupResult.Failed(SyncError.CreateUnconfirmed("Habit Sheet: Marathon"))
        vm.sheet.create()
        assertEquals("Google did not confirm that the sheet was created. Check Google Drive for “Habit Sheet: Marathon” before trying again, so you do not end up with two.", English.render(vm.state.value.message!!))

        linker.result = SheetSetupResult.InvalidLink
        vm.sheet.linkExisting("not a link")
        assertEquals(UiText.of(Res.string.sheet_msg_invalid_link), vm.state.value.message)
        assertEquals(listOf("not a link"), linker.linked)

        linker.result = SheetSetupResult.Linked(url, SyncError.Offline())
        vm.sheet.linkExisting(url)
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertEquals(UiText.of(Res.string.sync_offline), vm.state.value.message, "linked, first sync pending: said on the last step")
    }

    @Test
    fun withoutSheetOrReminderServicesThoseStepsAreSkipped() = runTest {
        val repo = InMemoryHabitRepository()
        val vm = OnboardingViewModel(repo, OnboardingServices(idGenerator = DefaultIdGenerator()), FakeDateProvider(today = today, nowMillis = 1L), scope, resolveText = English::render)
        vm.throughPreview(PlanTemplateId.GENERAL_FITNESS)
        vm.plan.createPlan()
        vm.reminder.turnOn()
        assertEquals(OnboardingStep.SHEET, vm.state.value.step)
        vm.sheet.create()
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
    }
}

/** A repository whose plan write fails, for the error message test. */
private class FailingPlanRepository(inner: InMemoryHabitRepository) : HabitRepository by inner {
    override suspend fun applyPlan(application: PlanApplication) = error("injected failure in applyPlan")
}
