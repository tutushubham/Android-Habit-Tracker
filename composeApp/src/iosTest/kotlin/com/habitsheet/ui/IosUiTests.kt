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
import kotlin.test.Test

/**
 * The shared Compose UI scenarios on the iOS simulator [mac]:
 * `./gradlew :composeApp:iosSimulatorArm64Test --tests "com.habitsheet.ui.IosUiTests"`.
 * They are not runnable on Windows or Linux (they compile there). The scenarios assume the English catalog, so run
 * the simulator with English as its language and a phone-sized device.
 */
@OptIn(ExperimentalTestApi::class)
class IosUiTests {
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
}
