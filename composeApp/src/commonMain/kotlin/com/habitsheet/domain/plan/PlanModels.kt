package com.habitsheet.domain.plan

import com.habitsheet.domain.model.HabitKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The goals the first-run wizard offers. [SCRATCH] produces an empty plan: the person adds habits themselves. */
enum class PlanTemplateId { MARATHON, HYROX, INTERVIEW, GENERAL_FITNESS, SCRATCH }

enum class PlanLevel { NEW, REGULAR, EXPERIENCED }

/**
 * What the wizard asks. [eventDate] is the race or interview day (required for [PlanTemplateId.MARATHON] and
 * [PlanTemplateId.HYROX], optional for [PlanTemplateId.INTERVIEW], unused otherwise). [restDay] never gets a session
 * except the event itself.
 */
data class PlanQuestions(
    val startDate: LocalDate,
    val eventDate: LocalDate? = null,
    val level: PlanLevel = PlanLevel.REGULAR,
    val daysPerWeek: Int = 4,
    val restDay: DayOfWeek? = null,
) {
    companion object {
        const val MIN_DAYS_PER_WEEK = 2
        const val MAX_DAYS_PER_WEEK = 6
    }
}

/**
 * Wording without words: every name and session text is a [PlanText] key plus integer arguments, resolved from the
 * string catalog in the person's language when the plan is applied (ADR 0006). The domain never holds copy.
 */
data class PlanLabel(val text: PlanText, val args: List<Int> = emptyList()) {
    constructor(text: PlanText, vararg args: Int) : this(text, args.toList())
}

/** The areas a plan files its habits under; mapped to the default categories when the person still has them. */
enum class PlanCategory { FITNESS, HEALTH, SLEEP, CAREER, STUDY }

data class DraftHabit(
    val key: String,
    val label: PlanLabel,
    val category: PlanCategory,
    val kind: HabitKind = HabitKind.ACTION,
    /** Session habits only appear on days with a session; daily habits are due every day. */
    val datedOnly: Boolean,
    val monthlyGoal: Int,
    val enabled: Boolean = true,
)

data class DraftSession(val habitKey: String, val date: LocalDate, val label: PlanLabel)

/** A generated plan before it touches the database; the person can switch habits off in the preview. */
data class PlanDraft(
    val template: PlanTemplateId,
    val habits: List<DraftHabit> = emptyList(),
    val sessions: List<DraftSession> = emptyList(),
) {
    val enabledHabits: List<DraftHabit> get() = habits.filter { it.enabled }

    fun sessionsOf(habitKey: String): List<DraftSession> = sessions.filter { it.habitKey == habitKey }

    fun withHabitEnabled(key: String, enabled: Boolean): PlanDraft =
        copy(habits = habits.map { if (it.key == key) it.copy(enabled = enabled) else it })
}

/** Why a template could not build a plan from the answers. */
enum class PlanProblem {
    /** The template needs an event date and none was given. */
    EVENT_DATE_MISSING,

    /** The event is before the start or too close to train for. */
    EVENT_TOO_SOON,

    /** The event is beyond [PLAN_HORIZON_DAYS]: the plan could not be shown or synced in full. */
    EVENT_TOO_FAR,

    /** Days per week outside [PlanQuestions.MIN_DAYS_PER_WEEK]..[PlanQuestions.MAX_DAYS_PER_WEEK]. */
    DAYS_PER_WEEK_OUT_OF_RANGE,

    /** The start date is in the past. */
    START_IN_THE_PAST,
}

sealed interface PlanResult {
    data class Ok(val draft: PlanDraft) : PlanResult

    data class Invalid(val problem: PlanProblem) : PlanResult
}

/** Longest plan, counted from today: the sheet sync uploads 180 days ahead (`SheetSyncWindow.rolling`). */
const val PLAN_HORIZON_DAYS = 180

/**
 * Where plans come from. Templates are bundled today ([TemplatePlanSource]); a generated source (for example an AI
 * service) can implement this later. It is `suspend` so such a source may use the network; every source's output must
 * satisfy the same invariants (`PlanInvariants`).
 */
interface PlanSource {
    fun templates(): List<PlanTemplateId>

    suspend fun draft(template: PlanTemplateId, questions: PlanQuestions, today: LocalDate): PlanResult
}
