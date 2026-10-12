@file:OptIn(ExperimentalTestApi::class)

package com.habitsheet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.DataSummary
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.ThemeMode
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import com.habitsheet.testing.English
import com.habitsheet.ui.manage.ManageHabitsScreen
import com.habitsheet.ui.month.CompletionCell
import com.habitsheet.ui.month.MonthScreen
import org.jetbrains.compose.resources.StringResource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The English copy for [resource], from the same catalog the app renders (so a copy edit never breaks a test). */
internal fun text(resource: StringResource, vararg args: Any): String =
    English.render(UiText.of(resource, *args)).replace("&amp;", "&")

/** A tap the way a person makes it: bring the element into view first (these screens scroll), then press it. */
private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

private fun hasState(description: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/**
 * Compose UI scenarios shared by the Android (Robolectric, `androidUnitTest`) and iOS-simulator (`iosTest`, [mac])
 * runners. Each is an extension on [ComposeUiTest] so a runner only wraps it in `runComposeUiTest { }`. They assume
 * the English catalog (the default locale on both runners).
 */
object UiScenarios {
    private fun ComposeUiTest.showMonthScreen(world: UiWorld, onManage: () -> Unit = {}, onSettings: () -> Unit = {}) {
        // A phone-sized box: the iOS test window is 1024 dp wide, which would select the tablet layout (no bottom tabs).
        setContent {
            HabitSheetTheme(darkTheme = false) {
                Box(Modifier.size(412.dp, 892.dp)) { MonthScreen(world.month, onManage = onManage, onSettings = onSettings) }
            }
        }
    }

    // ---- empty state and first run ----

    fun ComposeUiTest.emptyStateOffersTheTwoWaysToStartInBothModes() {
        val world = UiWorld()
        var managed = 0
        var linked = 0
        showMonthScreen(world, onManage = { managed++ }, onSettings = { linked++ })

        onNodeWithText(text(Res.string.empty_daily_title)).assertExists()
        onNodeWithText(text(Res.string.empty_daily_button)).tap()
        assertEquals(1, managed)
        onNodeWithText(text(Res.string.empty_link_sheet_button)).tap()
        assertEquals(1, linked)

        onNodeWithText(text(Res.string.mode_month)).performClick()
        onNodeWithText(text(Res.string.empty_daily_title)).assertExists()
        world.close()
    }

    fun ComposeUiTest.theCoachMarksShowWhenAskedAndSkippingThemIsRemembered() {
        val world = UiWorld(onboardingCompleted = false)
        showMonthScreen(world)
        onNodeWithText(text(Res.string.tutorial_manage_title)).assertDoesNotExist()
        world.month.showTutorial()

        onNodeWithText(text(Res.string.tutorial_manage_title)).assertExists()
        onNodeWithText(text(Res.string.tutorial_step_of, 1, 3)).assertExists()
        onNodeWithContentDescription(text(Res.string.tutorial_skip_description)).performClick()

        onNodeWithText(text(Res.string.tutorial_manage_title)).assertDoesNotExist()
        var completed = false
        world.now { completed = world.repository.isOnboardingCompleted() }
        assertTrue(completed, "skipping stores that the tutorial was seen")
        world.close()
    }

    // ---- toggling ----

    fun ComposeUiTest.aSessionInTheDayListTogglesIndependentlyOfItsSiblings() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        val read = hasContentDescription("Read, Study")
        val longRun = hasContentDescription("Run, Long run")
        val strides = hasContentDescription("Run, Strides")

        onNode(read).assert(hasRole(Role.Checkbox)).assert(hasState(text(Res.string.status_incomplete)))
        onNodeWithText(text(Res.string.today_completed, 0, 3, "0%")).assertExists()

        onNode(longRun).tap()

        onNode(longRun).assert(hasState(text(Res.string.status_completed)))
        onNode(strides).assert(hasState(text(Res.string.status_incomplete)))
        onNode(read).assert(hasState(text(Res.string.status_incomplete)))
        onNodeWithText(text(Res.string.today_completed, 1, 3, "33%")).assertExists()
        assertEquals(setOf("run-am"), world.repository.snapshot.value.dailyCompletions.filter { it.completed }.map { it.planId }.toSet())

        onNode(longRun).tap()
        onNode(longRun).assert(hasState(text(Res.string.status_incomplete)))
        onNodeWithText(text(Res.string.today_completed, 0, 3, "0%")).assertExists()
        world.close()
    }

    fun ComposeUiTest.aCellInTheMonthGridTogglesAndLabelsItselfWithHabitAndDate() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        onNodeWithText(text(Res.string.mode_month)).performClick()

        val monday = hasContentDescription("Read, Monday, 12 October 2026")
        val tuesday = hasContentDescription("Read, Tuesday, 13 October 2026")
        onNode(monday).assert(hasRole(Role.Checkbox)).assertIsOff().assert(hasState(text(Res.string.status_incomplete)))

        onNode(monday).performClick()

        onNode(monday).assertIsOn().assert(hasState(text(Res.string.status_completed)))
        onNode(tuesday).assertIsOff()
        assertTrue(world.repository.snapshot.value.dailyCompletions.any { it.planId == "read|2026-10-12" && it.completed })

        onNode(monday).performClick()
        onNode(monday).assertIsOff()
        world.close()
    }

    // ---- month and day navigation ----

    fun ComposeUiTest.monthNavigationMovesTheTitleAndThePickerJumpsToAnyMonthAndYear() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        onNodeWithText(text(Res.string.mode_month)).performClick()
        fun picker(month: String, year: Int) = onNodeWithContentDescription(text(Res.string.month_picker_description, month, year))

        picker("October", 2026).assertExists().assert(hasRole(Role.Button))
        onNodeWithContentDescription(text(Res.string.date_next_month)).performClick()
        picker("November", 2026).assertExists()
        repeat(2) { onNodeWithContentDescription(text(Res.string.date_prev_month)).performClick() }
        picker("September", 2026).assertExists()
        assertEquals(MonthKey(2026, 9), world.month.state.value.selectedMonth)

        picker("September", 2026).performClick()
        onNodeWithContentDescription(text(Res.string.picker_prev_year)).performClick()
        onNode(hasText("2025")).assertExists()
        onNode(hasText(text(Res.string.month_march)) and hasAnyAncestor(isDialog())).performClick()

        picker("March", 2025).assertExists()
        assertEquals(MonthKey(2025, 3), world.month.state.value.selectedMonth)
        world.close()
    }

    fun ComposeUiTest.dayNavigationShowsABackToTodayButtonOnlyAwayFromToday() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        onNodeWithText(text(Res.string.today_back)).assertDoesNotExist()
        onNodeWithText(text(Res.string.today_label)).assertExists()

        onNodeWithContentDescription(text(Res.string.date_next_day)).performClick()

        onNodeWithText(text(Res.string.today_back)).assertExists()
        onNodeWithText(text(Res.string.day_plan_label)).assertExists()
        assertEquals(world.dates.today.toEpochDays() + 1, world.month.state.value.selectedDay.toEpochDays())

        onNodeWithText(text(Res.string.today_back)).performClick()
        onNodeWithText(text(Res.string.today_back)).assertDoesNotExist()
        onNodeWithText(text(Res.string.today_label)).assertExists()
        world.close()
    }

    // ---- adding a habit ----

    fun ComposeUiTest.addingADailyHabitNeedsANameAndAValidGoalAndShowsItInTheList() {
        val world = UiWorld()
        setContent { HabitSheetTheme(darkTheme = false) { ManageHabitsScreen(world.manage, onTracker = {}) } }

        onNodeWithText(text(Res.string.manage_daily_none)).assertExists()
        onNode(hasText(text(Res.string.manage_add_daily), substring = true)).performClick()
        onNodeWithText(text(Res.string.editor_title_add_daily)).assertExists()
        val save = hasText(text(Res.string.action_save)) and hasAnyAncestor(isDialog())
        onNode(save).assertIsNotEnabled()

        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Stretch")
        fields[1].performTextInput("abc") // letters are dropped as they are typed, so the goal stays empty
        onNodeWithText(text(Res.string.editor_goal_hint)).assertExists()
        onNode(save).assertIsNotEnabled()

        fields[1].performTextInput("99999999999") // too large for a goal
        onNodeWithText(text(Res.string.editor_goal_invalid)).assertExists()
        onNode(save).assertIsNotEnabled()

        fields[1].performTextReplacement("15")
        onNodeWithText(text(Res.string.editor_goal_invalid)).assertDoesNotExist()
        onNode(save).assertIsEnabled()
        onNode(save).performClick()

        onNodeWithText("Stretch").assertExists()
        val habit = world.repository.snapshot.value.dailyHabits.single()
        assertEquals("Stretch", habit.name)
        assertEquals(15, habit.monthlyGoal)
        assertEquals(world.dates.today, habit.createdOn)
        world.close()
    }

    // ---- sheet link and theme ----

    private fun ComposeUiTest.showSettings(world: UiWorld) {
        setContent {
            HabitSheetTheme(darkTheme = false) {
                SettingsScreen(world.settings, onBack = {}, onManageCategories = {}, onBackup = {}, onAbout = {})
            }
        }
    }

    fun ComposeUiTest.theSheetLinkIsValidatedWhileTypingAndConnectNeedsAValidLink() {
        val world = UiWorld()
        showSettings(world)
        val connect = hasText(text(Res.string.settings_connect_sync))
        val invalid = text(Res.string.settings_link_invalid)

        onNode(connect).assertIsNotEnabled()
        onNodeWithText(invalid).assertDoesNotExist()

        onNode(hasSetTextAction()).performTextInput("https://example.com/spreadsheets/d/abc")
        onNodeWithText(invalid).assertExists()
        onNode(connect).assertIsNotEnabled()

        onNode(hasSetTextAction()).performTextReplacement("https://docs.google.com/spreadsheets/d/abc_123/edit#gid=0")
        onNodeWithText(invalid).assertDoesNotExist()
        onNode(connect).assertIsEnabled()
        onNodeWithText(text(Res.string.plan_open_sheet)).assertDoesNotExist() // nothing saved yet

        onNode(connect).tap()

        assertEquals("https://docs.google.com/spreadsheets/d/abc_123/edit", world.settings.sheetUrl.value)
        onNodeWithText(text(Res.string.plan_open_sheet)).assertExists()
        onNodeWithText(text(Res.string.settings_disconnect)).assertExists().tap()
        assertEquals("", world.settings.sheetUrl.value)
        onNodeWithText(text(Res.string.sheet_msg_disconnected)).assertExists()
        onNodeWithText(text(Res.string.plan_open_sheet)).assertDoesNotExist()
        world.close()
    }

    fun ComposeUiTest.chosingAThemeUpdatesTheSelectionAndIsStored() {
        val world = UiWorld()
        showSettings(world)
        fun option(label: String) = onNode(hasText(label))

        assertEquals(ThemeMode.System, world.settings.themeMode.value)
        option(text(Res.string.theme_dark)).tap()

        assertEquals(ThemeMode.Dark, world.settings.themeMode.value)
        var stored = -1
        world.now { stored = world.repository.getThemeMode() }
        assertEquals(ThemeMode.Dark.ordinal, stored)
        assertNull(world.settings.sheetMessage.value)
        world.close()
    }

    fun ComposeUiTest.theThemeOptionsExposeWhichOneIsSelected() {
        val world = UiWorld()
        showSettings(world)
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)

        onNode(hasText(text(Res.string.theme_system))).assert(selected).assert(hasRole(Role.RadioButton))
        onNode(hasText(text(Res.string.theme_dark))).assert(notSelected)

        onNode(hasText(text(Res.string.theme_dark))).tap()

        onNode(hasText(text(Res.string.theme_dark))).assert(selected)
        onNode(hasText(text(Res.string.theme_system))).assert(notSelected)
    }

    fun ComposeUiTest.theDayAndMonthSwitchExposesWhichModeIsSelected() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)

        onNode(hasText(text(Res.string.mode_day))).assert(selected)
        onNode(hasText(text(Res.string.mode_month))).assert(notSelected)

        onNode(hasText(text(Res.string.mode_month))).performClick()

        onNode(hasText(text(Res.string.mode_month))).assert(selected)
        onNode(hasText(text(Res.string.mode_day))).assert(notSelected)
        world.close()
    }

    // ---- backup confirmations ----

    private fun ComposeUiTest.showBackup(world: UiWorld) {
        setContent { HabitSheetTheme(darkTheme = false) { DataBackupScreen(world.backup, onBack = {}) } }
    }

    private fun inDialog(label: String) = hasText(label) and hasAnyAncestor(isDialog())

    fun ComposeUiTest.resetNeedsAConfirmationThatNamesWhatIsLostAndCancelChangesNothing() {
        val world = UiWorld(UiWorld.sampleData())
        showBackup(world)
        val action = DestructiveAction.ResetAllData(world.backup.currentDataSummary())
        val confirmation = action.confirmation

        onNodeWithText(text(Res.string.backup_reset_button)).tap()

        onNode(inDialog(English.render(confirmation.title))).assertExists()
        onNode(inDialog(English.render(confirmation.message))).assertExists()
        onNode(inDialog(text(Res.string.backup_export_first))).assertExists()
        onNode(inDialog(text(Res.string.action_cancel))).performClick()
        onNode(isDialog()).assertDoesNotExist()
        assertEquals(2, world.repository.snapshot.value.dailyHabits.size, "cancel keeps the data")

        onNodeWithText(text(Res.string.backup_reset_button)).tap()
        onNode(inDialog(English.render(confirmation.confirmLabel))).performClick()

        assertTrue(world.repository.snapshot.value.dailyHabits.isEmpty())
        onNodeWithText(text(Res.string.backup_reset_done)).assertExists()
        world.close()
    }

    fun ComposeUiTest.importShowsWhatWillBeReplacedAndOnlyOffersTheLinkWhenTheFileHasOne() {
        val incoming = UiWorld.sampleData()
        val world = UiWorld(
            initial = HabitSnapshot(),
            backupService = FakeBackupService(BackupSerializer.serialize(incoming, timestamp = 5)),
        )
        showBackup(world)
        val action = DestructiveAction.ReplaceWithBackup(DataSummary.of(world.repository.snapshot.value), DataSummary.of(incoming))

        onNodeWithText(text(Res.string.backup_import_button_file)).tap()

        onNode(inDialog(English.render(action.confirmation.title))).assertExists()
        onNode(inDialog(English.render(action.confirmation.message))).assertExists()
        onNode(inDialog(text(Res.string.backup_use_link))).assertDoesNotExist() // this file carries no sheet link
        assertTrue(world.repository.snapshot.value.dailyHabits.isEmpty(), "choosing a file changes nothing yet")

        onNode(inDialog(English.render(action.confirmation.confirmLabel))).performClick()

        assertEquals(setOf("read", "run"), world.repository.snapshot.value.dailyHabits.map { it.id }.toSet())
        onNodeWithText(text(Res.string.backup_restored)).assertExists()
        world.close()
    }

    fun ComposeUiTest.exportDoesNotOfferTheSheetLinkWhenNoneIsSaved() {
        val world = UiWorld(UiWorld.sampleData())
        showBackup(world)
        onNodeWithText(text(Res.string.backup_export_title)).assertExists()
        onNodeWithText(text(Res.string.backup_include_link)).assertDoesNotExist()
        world.close()
    }

    fun ComposeUiTest.exportOffersToIncludeTheSheetLinkWhenOneIsSaved() {
        val linked = UiWorld(UiWorld.sampleData())
        linked.now { linked.repository.setSheetUrl("https://docs.google.com/spreadsheets/d/abc/edit") }
        showBackup(linked)
        onNodeWithText(text(Res.string.backup_include_link)).assertExists()
        linked.close()
    }

    // ---- semantics of the month cell itself ----

    fun ComposeUiTest.aCompletionCellIsACheckboxWithStateLabelAndATouchTargetOfAtLeast48dp() {
        setContent {
            HabitSheetTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    CompletionCell(completed = true, currentDay = false, description = "Done cell", width = 48.dp, height = 56.dp, onClick = {})
                    CompletionCell(completed = false, currentDay = true, description = "Open cell", width = 48.dp, height = 56.dp, onClick = {})
                    CompletionCell(completed = false, currentDay = false, enabled = false, description = "Off cell", width = 48.dp, height = 56.dp, onClick = {})
                }
            }
        }

        onNodeWithContentDescription("Done cell").assertIsOn().assert(hasRole(Role.Checkbox)).assert(hasState(text(Res.string.status_completed)))
        onNodeWithContentDescription("Open cell").assertIsOff().assertIsEnabled().assert(hasState(text(Res.string.status_incomplete)))
        onNodeWithContentDescription("Off cell").assertIsNotEnabled().assert(hasState(text(Res.string.status_not_planned)))
        onNodeWithContentDescription("Done cell").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        onAllNodes(hasText("✓")).assertCountEquals(0) // the glyph is decorative and must not be read out
    }

    fun ComposeUiTest.everyDayOfTheMonthHasOneLabelledCellPerHabitAndTheBottomTabsSayWhichIsSelected() {
        val world = UiWorld(UiWorld.sampleData())
        showMonthScreen(world)
        onNodeWithText(text(Res.string.mode_month)).performClick()

        onAllNodes(hasContentDescription("Read, ", substring = true)).assertCountEquals(31)
        onNode(hasText(text(Res.string.nav_tracker)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(hasRole(Role.Tab))
        onNode(hasText(text(Res.string.nav_habits)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        world.close()
    }
}
