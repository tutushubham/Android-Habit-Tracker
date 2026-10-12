package com.habitsheet.app

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitsheet.resources.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app on a device: first run, add a habit, check it off, rotate. Starts from an empty database and uses the
 * app's own strings, so it follows copy changes. Run with `./gradlew :composeApp:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun freshInstall() {
        deleteAppDatabase()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario.close()
        deleteAppDatabase()
    }

    private val habitName = "Stretch"

    private fun addHabitFromTheFirstRun() {
        // The tutorial shows on a fresh install; skipping it is remembered.
        compose.onNodeWithContentDescription(s(Res.string.tutorial_skip_description)).performClick()
        compose.onNodeWithText(s(Res.string.empty_daily_title)).assertExists()
        compose.onNodeWithText(s(Res.string.empty_daily_button)).performScrollTo().performClick()

        compose.onNode(hasText(s(Res.string.manage_add_daily), substring = true)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(habitName)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("12")
        compose.onNode(hasText(s(Res.string.action_save)) and hasAnyAncestor(isDialog())).performClick()
        // The save goes through the repository on another dispatcher; wait for the list instead of assuming it is there.
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(habitName).fetchSemanticsNodes().isNotEmpty() }

        // Back to the tracker through the bottom bar.
        compose.onNode(hasText(s(Res.string.nav_tracker))).performClick()
    }

    private fun assertChecked(checked: Boolean) {
        val state = s(if (checked) Res.string.status_completed else Res.string.status_incomplete)
        compose.onNodeWithContentDescription(habitName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
    }

    @Test
    fun addAHabitCheckItOffAndKeepItAcrossARotation() {
        addHabitFromTheFirstRun()
        assertChecked(false)

        compose.onNodeWithContentDescription(habitName).performClick()
        assertChecked(true)

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        assertChecked(true)

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitForIdle()
        assertChecked(true)
    }

    @Test
    fun theCheckOffSurvivesRecreatingTheActivityAndRestartingTheApp() {
        addHabitFromTheFirstRun()
        compose.onNodeWithContentDescription(habitName).performClick()
        assertChecked(true)

        scenario.recreate()
        compose.waitForIdle()
        assertChecked(true)

        // A new process is the harshest case; closing and relaunching the activity reads the database again.
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        assertChecked(true)
    }
}
