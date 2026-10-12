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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.onboarding.OnboardingStep
import com.habitsheet.resources.*
import com.habitsheet.ui.month.MonthScreen
import com.habitsheet.ui.onboarding.OnboardingScreen
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

/** The first-run wizard on screen, shared by the Android and iOS runners like [UiScenarios]. */
object UiScenariosOnboarding {
    private class Finished(var openHabits: Boolean? = null, var planAdded: Boolean? = null)

    private fun ComposeUiTest.showWizard(world: UiWorld): Finished {
        val finished = Finished()
        world.onboarding.start()
        setContent {
            HabitSheetTheme(darkTheme = false) {
                // Phone-sized, like the other scenarios (the iOS test window is 1024 dp wide).
                Box(Modifier.size(412.dp, 892.dp)) {
                    OnboardingScreen(world.onboarding) { openHabits, planAdded ->
                        finished.openHabits = openHabits
                        finished.planAdded = planAdded
                    }
                }
            }
        }
        return finished
    }

    fun ComposeUiTest.aFreshInstallGoesFromWelcomeToAMarathonPlanWithASheetInSevenSteps() {
        val world = UiWorld(onboardingCompleted = false)
        val finished = showWizard(world)

        onNodeWithText(text(Res.string.onboarding_step_of, 1, 7)).assertExists()
        onNodeWithText(text(Res.string.onboarding_start)).tap()
        onNodeWithText(text(Res.string.onboarding_goal_marathon)).tap()
        onNodeWithText(text(Res.string.onboarding_questions_title)).assertExists()
        onNodeWithText(text(Res.string.onboarding_build)).tap()

        onNodeWithText(text(Res.string.onboarding_step_of, 4, 7)).assertExists()
        onNode(hasText("Running")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        onNodeWithText(text(Res.string.onboarding_create)).tap()

        onNodeWithText(text(Res.string.onboarding_reminder_title)).assertExists()
        onNodeWithText(text(Res.string.onboarding_reminder_on)).tap()
        assertEquals(7 * 60, world.reminderScheduler.scheduled?.reminder?.minuteOfDay)

        onNodeWithText(text(Res.string.onboarding_sheet_create)).tap()
        assertEquals(listOf("Habit Sheet: Marathon"), world.sheetLinker.created)

        onNodeWithText(text(Res.string.onboarding_done_title)).assertExists()
        onNodeWithText(text(Res.string.onboarding_done_sheet)).assertExists()
        onNodeWithText(text(Res.string.onboarding_open_sheet)).assertExists()
        onNodeWithText(text(Res.string.onboarding_done)).tap()

        waitForIdle()
        assertEquals(false, finished.openHabits)
        assertEquals(true, finished.planAdded)
        assertTrue(world.repository.snapshot.value.dailyHabits.any { it.name == "Running" })
        world.close()
    }

    fun ComposeUiTest.skippingOnTheFirstStepAsksFirstAndMarksSetupDone() {
        val world = UiWorld(onboardingCompleted = false)
        val finished = showWizard(world)

        onNodeWithText(text(Res.string.onboarding_skip)).tap()
        onNodeWithText(text(Res.string.onboarding_skip_title)).assertExists()
        onNode(hasText(text(Res.string.onboarding_skip_cancel)) and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(text(Res.string.onboarding_skip_title)).assertDoesNotExist()

        onNodeWithText(text(Res.string.onboarding_skip)).tap()
        onNode(hasText(text(Res.string.onboarding_skip_confirm)) and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(false, finished.planAdded)
        var completed = false
        world.now { completed = world.repository.isOnboardingCompleted() }
        assertTrue(completed)
        assertTrue(world.repository.snapshot.value.dailyHabits.isEmpty())
        world.close()
    }

    fun ComposeUiTest.theBackButtonOfAStepGoesToThePreviousStep() {
        val world = UiWorld(onboardingCompleted = false)
        showWizard(world)
        onNodeWithText(text(Res.string.onboarding_start)).tap()
        onNodeWithText(text(Res.string.onboarding_goal_interview)).tap()
        onNodeWithText(text(Res.string.onboarding_event_interview)).assertExists()
        onNodeWithText(text(Res.string.onboarding_back)).tap()
        onNodeWithText(text(Res.string.onboarding_goal_title)).assertExists()
        onNodeWithText(text(Res.string.onboarding_back)).tap()
        onNodeWithText(text(Res.string.onboarding_welcome_title)).assertExists()
        world.close()
    }

    fun ComposeUiTest.aRefusedNotificationPermissionOffersTheSystemSettingsAndContinuing() {
        val world = UiWorld(onboardingCompleted = false)
        world.notificationPermission.answer = false
        showWizard(world)
        onNodeWithText(text(Res.string.onboarding_start)).tap()
        onNodeWithText(text(Res.string.onboarding_goal_fitness)).tap()
        onNodeWithText(text(Res.string.onboarding_build)).tap()
        onNodeWithText(text(Res.string.onboarding_create)).tap()
        onNodeWithText(text(Res.string.onboarding_reminder_on)).tap()

        onNodeWithText(text(Res.string.onboarding_reminder_denied)).assertExists()
        onNodeWithText(text(Res.string.onboarding_open_settings)).tap()
        assertEquals(1, world.notificationPermission.settingsOpened)
        onNodeWithText(text(Res.string.onboarding_reminder_skip)).tap()
        assertEquals(OnboardingStep.SHEET, world.onboarding.state.value.step)
        world.close()
    }

    fun ComposeUiTest.ownSheetLinksAreCheckedBeforeConnectIsOffered() {
        val world = UiWorld(onboardingCompleted = false)
        showWizard(world)
        onNodeWithText(text(Res.string.onboarding_start)).tap()
        onNodeWithText(text(Res.string.onboarding_goal_fitness)).tap()
        onNodeWithText(text(Res.string.onboarding_build)).tap()
        onNodeWithText(text(Res.string.onboarding_create)).tap()
        onNodeWithText(text(Res.string.onboarding_reminder_skip)).tap()

        onNodeWithText(text(Res.string.onboarding_sheet_existing)).tap()
        onNode(hasSetTextAction()).performTextInput("https://example.com/sheet")
        onNodeWithText(text(Res.string.settings_link_invalid)).assertExists()
        onNodeWithText(text(Res.string.onboarding_sheet_connect)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.onboarding_sheet_existing_note)).assertExists()
        world.close()
    }

    fun ComposeUiTest.theEmptyTrackerOffersToSetUpAPlan() {
        val world = UiWorld()
        var setUp = 0
        setContent {
            HabitSheetTheme(darkTheme = false) {
                Box(Modifier.size(412.dp, 892.dp)) { MonthScreen(world.month, onManage = {}, onSetUpPlan = { setUp++ }) }
            }
        }
        onNodeWithText(text(Res.string.empty_setup_plan_button)).tap()
        assertEquals(1, setUp)
        onNodeWithText(text(Res.string.empty_daily_button)).assertExists()
        world.close()
    }
}
