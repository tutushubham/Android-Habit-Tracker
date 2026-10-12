package com.habitsheet.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.habitsheet.ui.UiScenarios.aCellInTheMonthGridTogglesAndLabelsItselfWithHabitAndDate
import com.habitsheet.ui.UiScenarios.aCompletionCellIsACheckboxWithStateLabelAndATouchTargetOfAtLeast48dp
import com.habitsheet.ui.UiScenarios.aSessionInTheDayListTogglesIndependentlyOfItsSiblings
import com.habitsheet.ui.UiScenarios.addingADailyHabitNeedsANameAndAValidGoalAndShowsItInTheList
import com.habitsheet.ui.UiScenarios.chosingAThemeUpdatesTheSelectionAndIsStored
import com.habitsheet.ui.UiScenarios.dayNavigationShowsABackToTodayButtonOnlyAwayFromToday
import com.habitsheet.ui.UiScenarios.emptyStateOffersTheTwoWaysToStartInBothModes
import com.habitsheet.ui.UiScenarios.everyDayOfTheMonthHasOneLabelledCellPerHabitAndTheBottomTabsSayWhichIsSelected
import com.habitsheet.ui.UiScenarios.exportDoesNotOfferTheSheetLinkWhenNoneIsSaved
import com.habitsheet.ui.UiScenarios.exportOffersToIncludeTheSheetLinkWhenOneIsSaved
import com.habitsheet.ui.UiScenarios.importShowsWhatWillBeReplacedAndOnlyOffersTheLinkWhenTheFileHasOne
import com.habitsheet.ui.UiScenarios.monthNavigationMovesTheTitleAndThePickerJumpsToAnyMonthAndYear
import com.habitsheet.ui.UiScenarios.resetNeedsAConfirmationThatNamesWhatIsLostAndCancelChangesNothing
import com.habitsheet.ui.UiScenarios.theDayAndMonthSwitchExposesWhichModeIsSelected
import com.habitsheet.ui.UiScenarios.theFirstRunTutorialShowsOnceAndSkippingItIsRemembered
import com.habitsheet.ui.UiScenarios.theSheetLinkIsValidatedWhileTypingAndConnectNeedsAValidLink
import com.habitsheet.ui.UiScenarios.theThemeOptionsExposeWhichOneIsSelected
import com.habitsheet.ui.UiScenariosMore.aFailedSetAsideShowsTheReasonAndDoesNotRestartTheApp
import com.habitsheet.ui.UiScenariosMore.aboutRowsAreDisabledWithoutLinksAndWorkOnceTheyAreSet
import com.habitsheet.ui.UiScenariosMore.aboutRowsAreEnabledWithLinksAndLicencesOpen
import com.habitsheet.ui.UiScenariosMore.atTwiceTheTextSizeTargetsKeepTheirMinimumSizeAndStillWork
import com.habitsheet.ui.UiScenariosMore.deletingAnArchivedHabitNeedsConfirmationAndCancelKeepsIt
import com.habitsheet.ui.UiScenariosMore.theDarkThemeRendersTheMonthScreenAndItsCellsAreStillLabelled
import com.habitsheet.ui.UiScenariosMore.theLicencesScreenListsEveryNoticeItIsGiven
import com.habitsheet.ui.UiScenariosMore.theStartupFailureScreenOffersThreeActionsAndResettingNeedsConfirmation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared Compose UI scenarios on the JVM under Robolectric (API 34, the newest level that runs on the
 * project's JDK 17 toolchain). The same scenarios run on the iOS simulator through `IosUiTests` [mac].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class AndroidUiTests {
    @Test
    fun emptyStateOffersTheTwoWaysToStartInBothModes() = runComposeUiTest { emptyStateOffersTheTwoWaysToStartInBothModes() }

    @Test
    fun theFirstRunTutorialShowsOnceAndSkippingItIsRemembered() = runComposeUiTest { theFirstRunTutorialShowsOnceAndSkippingItIsRemembered() }

    @Test
    fun aSessionInTheDayListTogglesIndependentlyOfItsSiblings() = runComposeUiTest { aSessionInTheDayListTogglesIndependentlyOfItsSiblings() }

    @Test
    fun aCellInTheMonthGridTogglesAndLabelsItselfWithHabitAndDate() = runComposeUiTest { aCellInTheMonthGridTogglesAndLabelsItselfWithHabitAndDate() }

    @Test
    fun monthNavigationMovesTheTitleAndThePickerJumpsToAnyMonthAndYear() = runComposeUiTest { monthNavigationMovesTheTitleAndThePickerJumpsToAnyMonthAndYear() }

    @Test
    fun dayNavigationShowsABackToTodayButtonOnlyAwayFromToday() = runComposeUiTest { dayNavigationShowsABackToTodayButtonOnlyAwayFromToday() }

    @Test
    fun addingADailyHabitNeedsANameAndAValidGoalAndShowsItInTheList() = runComposeUiTest { addingADailyHabitNeedsANameAndAValidGoalAndShowsItInTheList() }

    @Test
    fun theSheetLinkIsValidatedWhileTypingAndConnectNeedsAValidLink() = runComposeUiTest { theSheetLinkIsValidatedWhileTypingAndConnectNeedsAValidLink() }

    @Test
    fun chosingAThemeUpdatesTheSelectionAndIsStored() = runComposeUiTest { chosingAThemeUpdatesTheSelectionAndIsStored() }

    @Test
    fun resetNeedsAConfirmationThatNamesWhatIsLostAndCancelChangesNothing() = runComposeUiTest { resetNeedsAConfirmationThatNamesWhatIsLostAndCancelChangesNothing() }

    @Test
    fun importShowsWhatWillBeReplacedAndOnlyOffersTheLinkWhenTheFileHasOne() = runComposeUiTest { importShowsWhatWillBeReplacedAndOnlyOffersTheLinkWhenTheFileHasOne() }

    @Test
    fun exportDoesNotOfferTheSheetLinkWhenNoneIsSaved() = runComposeUiTest { exportDoesNotOfferTheSheetLinkWhenNoneIsSaved() }

    @Test
    fun exportOffersToIncludeTheSheetLinkWhenOneIsSaved() = runComposeUiTest { exportOffersToIncludeTheSheetLinkWhenOneIsSaved() }

    @Test
    fun aCompletionCellIsACheckboxWithStateLabelAndATouchTargetOfAtLeast48dp() = runComposeUiTest { aCompletionCellIsACheckboxWithStateLabelAndATouchTargetOfAtLeast48dp() }

    @Test
    fun everyDayOfTheMonthHasOneLabelledCellPerHabitAndTheBottomTabsSayWhichIsSelected() = runComposeUiTest { everyDayOfTheMonthHasOneLabelledCellPerHabitAndTheBottomTabsSayWhichIsSelected() }

    @Test
    fun theThemeOptionsExposeWhichOneIsSelected() = runComposeUiTest { theThemeOptionsExposeWhichOneIsSelected() }

    @Test
    fun theDayAndMonthSwitchExposesWhichModeIsSelected() = runComposeUiTest { theDayAndMonthSwitchExposesWhichModeIsSelected() }

    @Test
    fun theStartupFailureScreenOffersThreeActionsAndResettingNeedsConfirmation() = runComposeUiTest { theStartupFailureScreenOffersThreeActionsAndResettingNeedsConfirmation() }

    @Test
    fun aFailedSetAsideShowsTheReasonAndDoesNotRestartTheApp() = runComposeUiTest { aFailedSetAsideShowsTheReasonAndDoesNotRestartTheApp() }

    @Test
    fun aboutRowsAreDisabledWithoutLinksAndWorkOnceTheyAreSet() = runComposeUiTest { aboutRowsAreDisabledWithoutLinksAndWorkOnceTheyAreSet() }

    @Test
    fun aboutRowsAreEnabledWithLinksAndLicencesOpen() = runComposeUiTest { aboutRowsAreEnabledWithLinksAndLicencesOpen() }

    @Test
    fun theLicencesScreenListsEveryNoticeItIsGiven() = runComposeUiTest { theLicencesScreenListsEveryNoticeItIsGiven() }

    @Test
    fun deletingAnArchivedHabitNeedsConfirmationAndCancelKeepsIt() = runComposeUiTest { deletingAnArchivedHabitNeedsConfirmationAndCancelKeepsIt() }

    @Test
    fun atTwiceTheTextSizeTargetsKeepTheirMinimumSizeAndStillWork() = runComposeUiTest { atTwiceTheTextSizeTargetsKeepTheirMinimumSizeAndStillWork() }

    @Test
    fun theDarkThemeRendersTheMonthScreenAndItsCellsAreStillLabelled() = runComposeUiTest { theDarkThemeRendersTheMonthScreenAndItsCellsAreStillLabelled() }
}
