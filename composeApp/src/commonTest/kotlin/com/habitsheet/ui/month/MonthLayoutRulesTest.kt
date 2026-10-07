package com.habitsheet.ui.month

import androidx.compose.ui.unit.dp
import com.habitsheet.domain.calculation.DailyShareHabit
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.presentation.UiText
import com.habitsheet.testing.English
import kotlin.test.Test
import kotlin.test.assertEquals

/** Characterization of the pure rules behind the month grid and the day plan grouping (no UI needed). */
class MonthLayoutRulesTest {
    @Test
    fun gridLeftColumnWidensWithTheScreen() {
        assertEquals(108.dp, monthGridLayout(360.dp).leftWidth)
        assertEquals(132.dp, monthGridLayout(420.dp).leftWidth)
        assertEquals(168.dp, monthGridLayout(700.dp).leftWidth)
        assertEquals(200.dp, monthGridLayout(1000.dp).leftWidth)
        assertEquals(252.dp, monthGridLayout(360.dp).availableForDays)
        assertEquals(160.dp, monthGridLayout(200.dp).availableForDays, "never narrower than 160dp for the days")
    }

    @Test
    fun dayCellsFitTheMonthBetween28And44Dp() {
        assertEquals(36.dp, monthGridLayout(1000.dp).cellWidth(0))
        assertEquals(28.dp, monthGridLayout(360.dp).cellWidth(31), "a phone scrolls; cells stay tappable")
        assertEquals(44.dp, monthGridLayout(2000.dp).cellWidth(30), "a wide tablet does not stretch cells")
        assertEquals(30.dp, monthGridLayout(1130.dp).cellWidth(31))
    }

    private fun habit(name: String, category: String?, kind: HabitKind = HabitKind.ACTION) =
        DailyShareHabit(id = name, name = name, categoryName = category, completed = false, kind = kind)

    @Test
    fun dayPlanGroupsHabitsByKindCategoryAndKnownNames() {
        assertEquals(DaySection.Avoid, daySection(habit("No sugar", "Fitness 💪", HabitKind.AVOIDANCE)), "avoidance wins over category")
        assertEquals(DaySection.Workout, daySection(habit("Swim", "Fitness 💪")))
        assertEquals(DaySection.Workout, daySection(habit("Stretch", "Physical")))
        assertEquals(DaySection.Workout, daySection(habit("run", null)))
        assertEquals(DaySection.Study, daySection(habit("Read", "Study 📚")))
        assertEquals(DaySection.Study, daySection(habit("Journal", "Mental")))
        assertEquals(DaySection.Study, daySection(habit("dsa", "Career 💼")))
        assertEquals(DaySection.Habit, daySection(habit("Drink water", "Health ❤️‍🩹")))
        assertEquals(DaySection.Habit, daySection(habit("Floss", null)))
        assertEquals(
            listOf("WORKOUTS", "STUDY", "HABITS", "AVOID TODAY · CHECK AT NIGHT"),
            DaySection.entries.map { English.render(UiText.of(it.openLabel)) },
            "section order on screen",
        )
        assertEquals(listOf("DONE · WORKOUTS", "DONE · STUDY", "DONE · HABITS", "KEPT TODAY"), DaySection.entries.map { English.render(UiText.of(it.doneLabel)) })
    }
}
