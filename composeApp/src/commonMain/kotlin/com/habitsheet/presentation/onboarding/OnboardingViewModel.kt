package com.habitsheet.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.domain.plan.PlanDraft
import com.habitsheet.domain.plan.PlanLabel
import com.habitsheet.domain.plan.PlanMergeResult
import com.habitsheet.domain.plan.PlanMerger
import com.habitsheet.domain.plan.PlanProblem
import com.habitsheet.domain.plan.PlanQuestions
import com.habitsheet.domain.plan.PlanResult
import com.habitsheet.domain.plan.PlanSource
import com.habitsheet.domain.plan.PlanTemplateId
import com.habitsheet.domain.plan.PlanTime
import com.habitsheet.domain.plan.PlanWording
import com.habitsheet.domain.plan.TemplatePlanSource
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.repository.IdGenerator
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.presentation.DailyReminders
import com.habitsheet.presentation.DateProvider
import com.habitsheet.presentation.ReminderOutcome
import com.habitsheet.presentation.SystemDateProvider
import com.habitsheet.presentation.UiText
import com.habitsheet.presentation.backgroundScope
import com.habitsheet.presentation.resolve
import com.habitsheet.resources.*
import com.habitsheet.sync.SheetLinker
import com.habitsheet.sync.SheetSetupResult
import com.habitsheet.sync.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

enum class OnboardingStep { WELCOME, GOAL, QUESTIONS, PREVIEW, REMINDER, SHEET, DONE }

/** What the plan added: shown on the last step. */
data class PlanSummary(val addedHabits: Int, val reusedHabits: Int, val sessions: Int)

/**
 * The wizard's state. [message] is an error or a hint for the current step. [existingHabits] above zero means the
 * wizard was opened again from Settings: the plan is added to those habits. [sheetAlreadyLinked] skips the sheet step.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val template: PlanTemplateId? = null,
    val questions: PlanQuestions? = null,
    val draft: PlanDraft? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
    val existingHabits: Int = 0,
    val summary: PlanSummary? = null,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    val reminderOn: Boolean = false,
    val reminderDenied: Boolean = false,
    val sheetAlreadyLinked: Boolean = false,
    val sheetUrl: String? = null,
    /** Set when the wizard is over; [openHabits] when the person chose to start from scratch. */
    val finished: Boolean = false,
    val openHabits: Boolean = false,
) {
    /** The steps this run shows, for "Step 3 of 7". */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { step ->
            when (step) {
                OnboardingStep.QUESTIONS -> template != PlanTemplateId.SCRATCH
                OnboardingStep.SHEET -> !sheetAlreadyLinked
                else -> true
            }
        }

    val stepNumber: Int get() = steps.indexOf(step) + 1

    companion object {
        const val DEFAULT_REMINDER_MINUTE = 7 * 60
    }
}

/** The services the wizard uses besides the repository. */
class OnboardingServices(
    val planSource: PlanSource = TemplatePlanSource(),
    val sheetLinker: SheetLinker? = null,
    val reminders: DailyReminders? = null,
    val idGenerator: IdGenerator,
)

/**
 * The first-run wizard: pick a goal, answer a few questions, preview the plan, add it, optionally a daily reminder
 * and a Google Sheet, done. Shown automatically on a fresh install ([autoStart]) and again from Settings ([start]).
 * Finishing or skipping marks onboarding complete.
 */
class OnboardingViewModel(
    private val repository: HabitRepository,
    private val services: OnboardingServices,
    private val dateProvider: DateProvider = SystemDateProvider,
    scope: CoroutineScope? = null,
    private val logger: Logger = NoOpLogger,
    private val resolveText: suspend (UiText) -> String = { it.resolve() },
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: backgroundScope()
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val mutableAutoStart = MutableStateFlow(false)

    /** True on a fresh install: onboarding not completed and no habits yet. Existing users never see the wizard. */
    val autoStart: StateFlow<Boolean> = mutableAutoStart.asStateFlow()

    init {
        this.scope.launch {
            runCatchingCancellable {
                mutableAutoStart.value = !repository.isOnboardingCompleted() && repository.snapshot.value.dailyHabits.isEmpty()
            }.onFailure { logger.e(TAG, "loading the onboarding state failed", it) }
        }
    }

    /** Opens the wizard at its first step (on first run, or again from Settings). */
    fun start() {
        mutableAutoStart.value = false
        val habits = repository.snapshot.value.dailyHabits.count { it.active && it.archivedOn == null }
        mutableState.value = OnboardingUiState(existingHabits = habits)
        scope.launch {
            runCatchingCancellable { repository.getSheetUrl().isNotBlank() }
                .onSuccess { linked -> mutableState.update { it.copy(sheetAlreadyLinked = linked) } }
                .onFailure { logger.e(TAG, "reading the sheet link failed", it) }
        }
    }

    fun next() {
        if (state.value.step == OnboardingStep.WELCOME) go(OnboardingStep.GOAL)
    }

    /** Goes back one step; false on the first step (the screen then offers to skip). The plan, once added, stays. */
    fun back(): Boolean {
        val current = state.value
        val previous = when (current.step) {
            OnboardingStep.WELCOME -> return false
            OnboardingStep.GOAL -> OnboardingStep.WELCOME
            OnboardingStep.QUESTIONS -> OnboardingStep.GOAL
            OnboardingStep.PREVIEW -> OnboardingStep.QUESTIONS
            OnboardingStep.REMINDER -> OnboardingStep.REMINDER
            OnboardingStep.SHEET -> OnboardingStep.REMINDER
            OnboardingStep.DONE -> OnboardingStep.DONE
        }
        go(previous)
        return true
    }

    /** Goal, questions, preview and adding the plan. */
    val plan = PlanActions()

    /** The daily reminder step. */
    val reminder = ReminderActions()

    /** The Google Sheet step. */
    val sheet = SheetActions()

    inner class PlanActions {
        fun chooseTemplate(template: PlanTemplateId) {
            if (template == PlanTemplateId.SCRATCH) {
                finish(openHabits = true)
                return
            }
            val today = dateProvider.today()
            val suggestedEvent = when (TemplatePlanSource.eventDate(template)) {
                TemplatePlanSource.EventDate.REQUIRED -> today.plus(DatePeriod(days = SUGGESTED_RACE_DAYS))
                else -> null
            }
            mutableState.update {
                it.copy(
                    template = template,
                    questions = PlanQuestions(startDate = today, eventDate = suggestedEvent),
                    draft = null,
                    step = OnboardingStep.QUESTIONS,
                    message = null,
                )
            }
        }

        fun updateQuestions(change: (PlanQuestions) -> PlanQuestions) {
            mutableState.update { state -> state.questions?.let { state.copy(questions = change(it), message = null) } ?: state }
        }

        /** Builds the plan from the answers and shows the preview, or says what is wrong with the answers. */
        fun buildPlan() {
            val current = state.value
            val template = current.template ?: return
            val questions = current.questions ?: return
            scope.launch {
                runCatchingCancellable { services.planSource.draft(template, questions, dateProvider.today()) }
                    .onSuccess { result ->
                        when (result) {
                            is PlanResult.Ok -> mutableState.update { it.copy(draft = result.draft, step = OnboardingStep.PREVIEW, message = null) }
                            is PlanResult.Invalid -> mutableState.update { it.copy(message = result.problem.text()) }
                        }
                    }
                    .onFailure {
                        logger.e(TAG, "building the plan failed", it)
                        mutableState.update { state -> state.copy(message = UiText.of(Res.string.onboarding_err_build)) }
                    }
            }
        }

        fun setHabitEnabled(key: String, enabled: Boolean) {
            mutableState.update { state -> state.copy(draft = state.draft?.withHabitEnabled(key, enabled)) }
        }

        /** Adds the previewed plan to the person's data (one transaction) and moves on to the reminder. */
        fun createPlan() {
            val draft = state.value.draft ?: return
            mutableState.update { it.copy(busy = true, message = null) }
            scope.launch {
                runCatchingCancellable { apply(draft) }
                    .onSuccess { summary ->
                        mutableState.update {
                            if (summary == null) it.copy(busy = false) else it.copy(busy = false, summary = summary, step = OnboardingStep.REMINDER)
                        }
                    }
                    .onFailure {
                        logger.e(TAG, "adding the plan failed", it)
                        mutableState.update { state -> state.copy(busy = false, message = UiText.of(Res.string.onboarding_err_create)) }
                    }
            }
        }

        /** Null (with a message set) when the plan does not fit; the rows are written in one transaction otherwise. */
        private suspend fun apply(draft: PlanDraft): PlanSummary? {
            val words = PlanMerger.labelsNeeded(draft).associateWith { resolveText(it.toUiText()) }
            val copyFormat = resolveText(UiText.of(Res.string.onboarding_habit_copy_name, COPY_PLACEHOLDER))
            val wording = object : PlanWording {
                override fun text(label: PlanLabel): String = words.getValue(label)

                override fun copyName(name: String): String = copyFormat.replace(COPY_PLACEHOLDER, name)
            }
            val time = PlanTime(dateProvider.today(), dateProvider.nowEpochMillis())
            return when (val merged = PlanMerger.merge(repository.snapshot.value, draft, wording, { services.idGenerator.newId() }, time)) {
                is PlanMergeResult.TooManyHabits -> {
                    mutableState.update { it.copy(message = UiText.of(Res.string.onboarding_err_too_many, merged.limit)) }
                    null
                }

                is PlanMergeResult.Ok -> {
                    repository.applyPlan(merged.application)
                    PlanSummary(merged.application.habits.size, merged.reusedHabits, merged.application.dayPlans.size)
                }
            }
        }
    }

    inner class ReminderActions {
        fun setMinute(minuteOfDay: Int) {
            mutableState.update { it.copy(reminderMinute = minuteOfDay, reminderDenied = false, message = null) }
        }

        /** Asks for the notification permission and schedules the reminder; on a refusal the step says how to allow it. */
        fun turnOn() {
            val reminders = services.reminders ?: return skip()
            val minute = state.value.reminderMinute
            mutableState.update { it.copy(busy = true, message = null) }
            scope.launch {
                when (reminders.set(DailyReminder(minute))) {
                    ReminderOutcome.ON -> {
                        mutableState.update { it.copy(busy = false, reminderOn = true) }
                        go(afterReminder())
                    }

                    ReminderOutcome.PERMISSION_DENIED -> mutableState.update {
                        it.copy(busy = false, reminderDenied = true, message = UiText.of(Res.string.onboarding_reminder_denied))
                    }

                    else -> mutableState.update { it.copy(busy = false, message = UiText.of(Res.string.onboarding_err_reminder)) }
                }
            }
        }

        fun openSettings() {
            services.reminders?.openSystemSettings()
        }

        fun skip() = go(afterReminder())

        private fun afterReminder(): OnboardingStep = if (state.value.sheetAlreadyLinked) OnboardingStep.DONE else OnboardingStep.SHEET
    }

    inner class SheetActions {
        /** Creates a Google Sheet named after the plan, links it and fills it with the plan. */
        fun create() {
            val linker = services.sheetLinker ?: return skip()
            val template = state.value.template ?: PlanTemplateId.GENERAL_FITNESS
            linkWith { linker.createAndLink(resolveText(UiText.of(Res.string.sheet_new_title, template.title()))) }
        }

        /** Links the person's own sheet (pasted link) and syncs it. */
        fun linkExisting(input: String) {
            val linker = services.sheetLinker ?: return skip()
            linkWith { linker.linkExisting(input) }
        }

        private fun linkWith(link: suspend () -> SheetSetupResult) {
            mutableState.update { it.copy(busy = true, message = null) }
            scope.launch {
                val result = runCatchingCancellable { link() }.getOrElse {
                    logger.e(TAG, "linking a sheet failed", it)
                    mutableState.update { state -> state.copy(busy = false, message = UiText.of(Res.string.onboarding_err_sheet)) }
                    return@launch
                }
                when (result) {
                    is SheetSetupResult.Linked -> {
                        mutableState.update { it.copy(busy = false, sheetUrl = result.url, message = result.syncError?.userMessage()) }
                        go(OnboardingStep.DONE)
                    }

                    SheetSetupResult.Cancelled -> mutableState.update { it.copy(busy = false, message = UiText.of(Res.string.onboarding_sheet_cancelled)) }

                    SheetSetupResult.InvalidLink -> mutableState.update { it.copy(busy = false, message = UiText.of(Res.string.sheet_msg_invalid_link)) }

                    is SheetSetupResult.Failed -> mutableState.update { it.copy(busy = false, message = result.error.userMessage()) }
                }
            }
        }

        fun skip() = go(OnboardingStep.DONE)
    }

    /** Ends the wizard (Done, or Skip on any step): onboarding is complete either way. */
    fun finish(openHabits: Boolean = false) {
        mutableState.update { it.copy(finished = true, openHabits = openHabits) }
        mutableAutoStart.value = false
        scope.launch {
            runCatchingCancellable { repository.setOnboardingCompleted(true) }
                .onFailure { logger.e(TAG, "saving onboarding completion failed", it) }
        }
    }

    /** The screen has left the wizard: the next [start] begins fresh. */
    fun onLeft() {
        mutableState.value = OnboardingUiState()
    }

    private fun go(step: OnboardingStep) {
        mutableState.update { it.copy(step = step, message = if (step == OnboardingStep.DONE) it.message else null) }
    }

    private companion object {
        const val TAG = "Onboarding"
        const val SUGGESTED_RACE_DAYS = 16 * 7
        const val COPY_PLACEHOLDER = "\u0000"
    }
}

/** The name of a goal, as the person reads it. */
fun PlanTemplateId.title(): UiText = UiText.of(
    when (this) {
        PlanTemplateId.MARATHON -> Res.string.onboarding_goal_marathon
        PlanTemplateId.HYROX -> Res.string.onboarding_goal_hyrox
        PlanTemplateId.INTERVIEW -> Res.string.onboarding_goal_interview
        PlanTemplateId.GENERAL_FITNESS -> Res.string.onboarding_goal_fitness
        PlanTemplateId.SCRATCH -> Res.string.onboarding_goal_scratch
    },
)

/** A short description of a goal, under its name. */
fun PlanTemplateId.description(): UiText = UiText.of(
    when (this) {
        PlanTemplateId.MARATHON -> Res.string.onboarding_goal_marathon_text
        PlanTemplateId.HYROX -> Res.string.onboarding_goal_hyrox_text
        PlanTemplateId.INTERVIEW -> Res.string.onboarding_goal_interview_text
        PlanTemplateId.GENERAL_FITNESS -> Res.string.onboarding_goal_fitness_text
        PlanTemplateId.SCRATCH -> Res.string.onboarding_goal_scratch_text
    },
)

/** What is wrong with the answers, in the person's words. */
fun PlanProblem.text(): UiText = UiText.of(
    when (this) {
        PlanProblem.EVENT_DATE_MISSING -> Res.string.onboarding_problem_event_missing
        PlanProblem.EVENT_TOO_SOON -> Res.string.onboarding_problem_event_too_soon
        PlanProblem.EVENT_TOO_FAR -> Res.string.onboarding_problem_event_too_far
        PlanProblem.DAYS_PER_WEEK_OUT_OF_RANGE -> Res.string.onboarding_problem_days
        PlanProblem.START_IN_THE_PAST -> Res.string.onboarding_problem_start
    },
)
