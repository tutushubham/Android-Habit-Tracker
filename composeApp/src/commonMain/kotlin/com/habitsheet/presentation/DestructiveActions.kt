package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import kotlinx.datetime.LocalDate

/** What a confirmation dialog shows. */
data class ConfirmationText(val title: String, val message: String, val confirmLabel: String)

/** Counts shown before data is replaced or reset, so the person sees what is at stake. */
data class DataSummary(val dailyHabits: Int, val weeklyHabits: Int, val checkOffs: Int) {
    fun describe(): String {
        if (dailyHabits == 0 && weeklyHabits == 0 && checkOffs == 0) return "no habits or history"
        return "${plural(dailyHabits, "daily habit")}, ${plural(weeklyHabits, "weekly habit")} and ${plural(checkOffs, "check-off")}"
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
            "Delete habit?",
            "Delete '$name' from this device? Its ${plural(checkOffs, "check-off")} and ${plural(sessions, "planned session")} " +
                "are deleted with it. This cannot be undone. Archive it instead to keep the history.",
            "Delete",
        )
    }

    data class DeleteWeeklyHabit(val name: String, val checkOffs: Int) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Delete habit?",
            "Delete '$name' from this device? Its ${plural(checkOffs, "check-off")} are deleted with it. " +
                "This cannot be undone. Archive it instead to keep the history.",
            "Delete",
        )
    }

    data class DeleteCategory(val name: String, val habits: Int) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Delete category?",
            "Delete the category '$name'? " +
                if (habits == 0) "No habits use it." else "${plural(habits, "habit")} in it will stay, without a category.",
            "Delete",
        )
    }

    data class RemoveDaySession(val habitName: String, val date: LocalDate, val detail: String) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Remove session?",
            "Remove '$detail' for $habitName on $date? Check-offs already recorded for it are kept.",
            "Remove",
        )
    }

    data class RemoveWeeklySession(val habitName: String, val weekday: String, val detail: String) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Remove weekly session?",
            "Remove '$detail' from $habitName on every $weekday? Sessions already planned for specific dates are kept.",
            "Remove",
        )
    }

    /** Reset all data. Clears the sheet link and all sync state too; the Google Sheet itself is never touched. */
    data class ResetAllData(val summary: DataSummary) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Reset all data on this device?",
            "This deletes ${summary.describe()} on this device and disconnects the sheet link. " +
                "Your Google Sheet is untouched. This cannot be undone.",
            "Reset all data",
        )
    }

    data class ReplaceWithBackup(val summary: DataSummary) : DestructiveAction {
        override val confirmation = ConfirmationText(
            "Replace existing data?",
            "Importing a backup replaces ${summary.describe()} on this device with the backup's content. " +
                "Syncing with your Google Sheet restarts afterwards (your Google Sheet is untouched). This cannot be undone.",
            "Import & Replace",
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

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
