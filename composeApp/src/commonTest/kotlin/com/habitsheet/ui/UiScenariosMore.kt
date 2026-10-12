@file:OptIn(ExperimentalTestApi::class)

package com.habitsheet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.habitsheet.config.AppLinks
import com.habitsheet.config.OpenSourceNotice
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.UiText
import com.habitsheet.presentation.VersionProvider
import com.habitsheet.resources.*
import com.habitsheet.testing.English
import com.habitsheet.ui.manage.ManageHabitsScreen
import com.habitsheet.ui.month.MonthScreen
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

private fun inDialog(label: String) = hasText(label) and hasAnyAncestor(isDialog())

private class FakeRecovery(var setAsideResult: BackupResult = BackupResult.Success(UiText.Raw("Moved aside."))) : StartupRecovery {
    var exports = 0
    var setAsides = 0

    override fun exportRawDatabase(onResult: (BackupResult) -> Unit) {
        exports++
        onResult(BackupResult.Success(UiText.Raw("Copy saved.")))
    }

    override fun setAsideDatabase(): BackupResult {
        setAsides++
        return setAsideResult
    }
}

/** More shared Compose UI scenarios: recovery, About, destructive deletes, font scale and the dark theme. */
object UiScenariosMore {
    fun ComposeUiTest.theStartupFailureScreenOffersThreeActionsAndResettingNeedsConfirmation() {
        val recovery = FakeRecovery()
        var retries = 0
        setContent {
            HabitSheetTheme(darkTheme = false) {
                StartupFailureScreen(IllegalStateException("disk image is malformed"), recovery, onRetry = { retries++ })
            }
        }
        onNodeWithText(text(Res.string.startup_title)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText(text(Res.string.startup_detail, "IllegalStateException")).assertExists()

        onNodeWithText(text(Res.string.startup_save_copy)).tap()
        assertEquals(1, recovery.exports)
        onNodeWithText("Copy saved.").assertExists()

        onNodeWithText(text(Res.string.startup_empty)).tap()
        val confirmLabel = English.render(DestructiveAction.SetAsideDamagedData.confirmation.confirmLabel)
        onNode(inDialog(English.render(DestructiveAction.SetAsideDamagedData.confirmation.title))).assertExists()
        onNode(inDialog(text(Res.string.action_cancel))).performClick()
        assertEquals(0, recovery.setAsides, "cancelling the reset moves nothing aside")
        assertEquals(0, retries)

        onNodeWithText(text(Res.string.startup_empty)).tap()
        onNode(inDialog(confirmLabel)).performClick()
        assertEquals(1, recovery.setAsides)
        assertEquals(1, retries, "the app starts again once the damaged data is set aside")

        onNodeWithText(text(Res.string.startup_retry)).tap()
        assertEquals(2, retries)
    }

    fun ComposeUiTest.aFailedSetAsideShowsTheReasonAndDoesNotRestartTheApp() {
        val recovery = FakeRecovery(BackupResult.Failure(UiText.Raw("Not enough storage.")))
        var retries = 0
        setContent { HabitSheetTheme(darkTheme = false) { StartupFailureScreen(IllegalStateException(), recovery, onRetry = { retries++ }) } }

        onNodeWithText(text(Res.string.startup_empty)).tap()
        onNode(inDialog(English.render(DestructiveAction.SetAsideDamagedData.confirmation.confirmLabel))).performClick()

        onNodeWithText("Not enough storage.").assertExists()
        assertEquals(0, retries)
    }

    private val version = object : VersionProvider {
        override val versionName = "9.9.9"
        override val versionCode = 99L
    }

    fun ComposeUiTest.aboutRowsAreDisabledWithoutLinksAndWorkOnceTheyAreSet() {
        setContent { HabitSheetTheme(darkTheme = false) { AboutScreen(version, onBack = {}, links = AppLinks()) } }
        onNodeWithText(text(Res.string.about_version, "9.9.9")).assertExists()
        onNode(hasText(text(Res.string.about_privacy_policy))).assertIsNotEnabled()
        onNode(hasText(text(Res.string.about_support))).assertIsNotEnabled()
    }

    fun ComposeUiTest.aboutRowsAreEnabledWithLinksAndLicencesOpen() {
        var licences = 0
        setContent {
            HabitSheetTheme(darkTheme = false) {
                AboutScreen(
                    version,
                    onBack = {},
                    onLicenses = { licences++ },
                    links = AppLinks(privacyPolicyUrl = "https://example.org/privacy", supportEmail = "help@example.org"),
                )
            }
        }
        onNode(hasText(text(Res.string.about_privacy_policy))).assertIsEnabled()
        onNode(hasText(text(Res.string.about_support))).assertIsEnabled()
        onNode(hasText(text(Res.string.about_licenses))).tap()
        assertEquals(1, licences)
    }

    fun ComposeUiTest.theLicencesScreenListsEveryNoticeItIsGiven() {
        val notices = listOf(
            OpenSourceNotice("Alpha Library", "Apache License 2.0", "https://example.org/alpha"),
            OpenSourceNotice("Beta Font", "SIL Open Font License 1.1", "https://example.org/beta"),
        )
        setContent { HabitSheetTheme(darkTheme = false) { LicensesScreen(onBack = {}, notices = notices) } }
        onNodeWithText("Alpha Library").assertExists()
        onNodeWithText("Beta Font").assertExists()
    }

    fun ComposeUiTest.deletingAnArchivedHabitNeedsConfirmationAndCancelKeepsIt() {
        val world = UiWorld(UiWorld.sampleData())
        setContent { HabitSheetTheme(darkTheme = false) { ManageHabitsScreen(world.manage, onTracker = {}) } }

        // Archive "Read" from its editor; deleting is only offered for archived habits.
        onAllNodesWithText(text(Res.string.action_edit))[0].tap()
        onNode(inDialog(text(Res.string.action_archive))).performClick()
        assertTrue(world.repository.snapshot.value.dailyHabits.single { it.id == "read" }.archivedOn != null)

        onNodeWithText(text(Res.string.manage_tab_archived)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        onNodeWithText(text(Res.string.manage_tab_archived)).tap()
        onNodeWithText(text(Res.string.manage_tab_archived)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onAllNodesWithText(text(Res.string.action_edit))[0].tap()
        onNode(inDialog(text(Res.string.action_delete))).performClick()

        val habit: DailyHabit = world.repository.snapshot.value.dailyHabits.single { it.id == "read" }
        val action = DestructiveAction.delete(habit, world.repository.snapshot.value)
        onNode(inDialog(English.render(action.confirmation.title))).assertExists()
        onNode(inDialog(English.render(action.confirmation.message))).assertExists()
        // The editor is still open under the confirmation, so there are two Cancel and two Delete buttons: the
        // confirmation is the one on top, which is the last in the tree.
        onAllNodes(inDialog(text(Res.string.action_cancel))).onLast().performClick()
        assertEquals(2, world.repository.snapshot.value.dailyHabits.size, "cancel deletes nothing")

        onNode(inDialog(English.render(action.confirmation.title))).assertDoesNotExist()
        onNode(inDialog(text(Res.string.action_delete))).performClick()
        onAllNodes(inDialog(English.render(action.confirmation.confirmLabel))).onLast().performClick()
        assertEquals(listOf("run"), world.repository.snapshot.value.dailyHabits.map { it.id })
        world.close()
    }

    fun ComposeUiTest.atTwiceTheTextSizeTargetsKeepTheirMinimumSizeAndStillWork() {
        val world = UiWorld(UiWorld.sampleData())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                HabitSheetTheme(darkTheme = false) { MonthScreen(world.month, onManage = {}) }
            }
        }
        onNodeWithText(text(Res.string.mode_month)).performClick()
        val monday = hasContentDescription("Read, Monday, 12 October 2026")
        onNode(monday).assertHeightIsAtLeast(48.dp).performScrollTo().performClick()
        onNode(monday).assertIsOn()
        world.close()
    }

    fun ComposeUiTest.theDarkThemeRendersTheMonthScreenAndItsCellsAreStillLabelled() {
        val world = UiWorld(UiWorld.sampleData())
        setContent { HabitSheetTheme(darkTheme = true) { MonthScreen(world.month, onManage = {}) } }
        onNodeWithText(text(Res.string.mode_month)).performClick()
        onNodeWithContentDescription("Read, Monday, 12 October 2026").assertExists()
        world.close()
    }
}
