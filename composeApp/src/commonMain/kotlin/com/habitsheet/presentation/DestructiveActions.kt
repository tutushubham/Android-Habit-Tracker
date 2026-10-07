package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.resources.*
import kotlinx.datetime.LocalDate

/** What a confirmation dialog shows. */
data class ConfirmationText(val title: UiText, val message: UiText, val confirmLabel: UiText)

/** Counts shown before data is replaced or reset, so the person sees what is at stake. */
data class DataSummary(val dailyHabits: Int, val weeklyHabits: Int, val checkOffs: Int) {
    fun describe(): UiText {
        if (dailyHabits == 0 && weeklyHabits == 0 && checkOffs == 0) return UiText.of(Res.string.summary_nothing)
        return UiText.of(
            Res.string.summary_counts,
            UiText.plural(Res.plurals.count_daily_habits, dailyHabits),
            UiText.plural(Res.plurals.count_weekly_habits, weeklyHabits),
            UiText.plural(Res.plurals.count_check_offs, checkOffs),
        )
    }

    companion object {
        fun of(snapshot: HabitSnapshot) = DataSummary(
            dailyHabits = snapshot.dailyHabits.size,
            weeklyHabits = snapshot.weeklyHabits.size,
            checkOffs = snapshot.dailyCompletions.count { it.completed } + snapshot.weeklyCompletions.count { it.completed },
        )
    }
}

/**
 * Every user action that permanently removes or replaces data on this device. The UI shows
 * [confirmation] in a dialog BEFORE calling the matching view-model function; the text always names what is
 * affected. Anything that can destroy data must be added here (see `DestructiveCallSitesTest`).
 */
sealed interface DestructiveAction {
    val confirmation: ConfirmationText

    data class DeleteDailyHabit(val name: String, val checkOffs: Int, val sessions: Int) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_delete_habit_title),
            UiText.of(
                Res.string.confirm_delete_daily_habit,
                name,
                UiText.plural(Res.plurals.count_check_offs, checkOffs),
                UiText.plural(Res.plurals.count_planned_sessions, sessions),
            ),
            UiText.of(Res.string.action_delete),
        )
    }

    data class DeleteWeeklyHabit(val name: String, val checkOffs: Int) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_delete_habit_title),
            UiText.of(Res.string.confirm_delete_weekly_habit, name, UiText.plural(Res.plurals.count_check_offs, checkOffs)),
            UiText.of(Res.string.action_delete),
        )
    }

    data class DeleteCategory(val name: String, val habits: Int) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_delete_category_title),
            if (habits == 0) {
                UiText.of(Res.string.confirm_delete_category_unused, name)
            } else {
                UiText.of(Res.string.confirm_delete_category_used, name, UiText.plural(Res.plurals.count_habits, habits))
            },
            UiText.of(Res.string.action_delete),
        )
    }

    data class RemoveDaySession(val habitName: String, val date: LocalDate, val detail: String) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_remove_session_title),
            UiText.of(Res.string.confirm_remove_day_session, detail, habitName, date.toString()),
            UiText.of(Res.string.action_remove),
        )
    }

    data class RemoveWeeklySession(val habitName: String, val weekday: String, val detail: String) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_remove_weekly_session_title),
            UiText.of(Res.string.confirm_remove_weekly_session, detail, habitName, weekday),
            UiText.of(Res.string.action_remove),
        )
    }

    /** Reset all data. Clears the sheet link and all sync state too; the Google Sheet itself is never touched. */
    data class ResetAllData(val summary: DataSummary) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_reset_title),
            UiText.of(Res.string.confirm_reset_message, summary.describe()),
            UiText.of(Res.string.confirm_reset_action),
        )
    }

    data class ReplaceWithBackup(val summary: DataSummary) : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_replace_title),
            UiText.of(Res.string.confirm_replace_message, summary.describe()),
            UiText.of(Res.string.confirm_replace_action),
        )
    }

    /** Startup recovery: the unreadable database is moved aside (kept on the device) and the app starts empty. */
    data object SetAsideDamagedData : DestructiveAction {
        override val confirmation = ConfirmationText(
            UiText.of(Res.string.confirm_set_aside_title),
            UiText.of(Res.string.confirm_set_aside_message),
            UiText.of(Res.string.confirm_set_aside_title_action),
        )
    }

    companion object {
        fun delete(habit: DailyHabit, snapshot: HabitSnapshot) = DeleteDailyHabit(
            habit.name,
            checkOffs = snapshot.dailyCompletions.count { it.habitId == habit.id && it.completed },
            sessions = snapshot.dayPlans.count { it.habitId == habit.id } + snapshot.weeklyPlans.count { it.habitId == habit.id },
        )

        fun delete(habit: WeeklyHabit, snapshot: HabitSnapshot) = DeleteWeeklyHabit(
            habit.name,
            checkOffs = snapshot.weeklyCompletions.count { it.weeklyHabitId == habit.id && it.completed },
        )

        fun delete(category: Category, snapshot: HabitSnapshot) = DeleteCategory(
            category.name,
            habits = snapshot.dailyHabits.count { it.categoryId == category.id } + snapshot.weeklyHabits.count { it.categoryId == category.id },
        )
    }
}
