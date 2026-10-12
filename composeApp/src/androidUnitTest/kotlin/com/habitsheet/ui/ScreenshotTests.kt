package com.habitsheet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.ui.manage.ManageHabitsScreen
import com.habitsheet.ui.month.MonthScreen
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val TABLET = "w834dp-h1194dp-xhdpi"

/**
 * Screenshot tests of the key screens: light and dark, phone and tablet, and a phone at 200% text size.
 *
 *     ./gradlew :composeApp:testDebugUnitTest --tests "*ScreenshotTests" -Proborazzi.test.record=true   # write/update goldens
 *     ./gradlew :composeApp:testDebugUnitTest --tests "*ScreenshotTests" -Proborazzi.test.verify=true   # fail on a difference
 *     ./gradlew :composeApp:testDebugUnitTest --tests "*ScreenshotTests" -Proborazzi.test.compare=true  # diffs in build/outputs/roborazzi
 *
 * Goldens are in src/androidUnitTest/screenshots. Plain `testDebugUnitTest` (and so `check`) runs these tests but
 * compares nothing. The goldens were recorded on
 * Windows; fonts differ slightly per OS, so record and verify on the same OS or re-record when CI is added (B1).
 * The date is fixed (Monday 12 October 2026), so the images do not change from day to day.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ScreenshotTests {
    private enum class Screen { MonthDay, MonthGrid, Empty, Settings, Backup, Manage }

    private fun capture(screen: Screen, name: String, tablet: Boolean, dark: Boolean, fontScale: Float) = runComposeUiTest {
        val world = if (screen == Screen.Empty) UiWorld() else UiWorld(UiWorld.sampleData())
        if (screen != Screen.Empty) {
            world.now {
                world.repository.setDailyCompletion(DailyHabitCompletion("read", LocalDate(2026, 10, 12), true, 10))
                world.repository.setDailyCompletion(DailyHabitCompletion("read", LocalDate(2026, 10, 11), true, 10))
                world.repository.setDailyCompletion(DailyHabitCompletion("run", LocalDate(2026, 10, 12), true, 10, "run-am"))
            }
        }
        if (screen == Screen.MonthGrid) world.month.setTodayMode(false)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                HabitSheetTheme(darkTheme = dark) { Content(world, screen, tablet) }
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("src/androidUnitTest/screenshots/$name.png")
        world.close()
    }

    @Composable
    private fun Content(world: UiWorld, screen: Screen, tablet: Boolean) {
        when (screen) {
            Screen.MonthDay, Screen.MonthGrid, Screen.Empty -> MonthScreen(world.month, onManage = {}, tabletLayout = tablet)
            Screen.Settings -> SettingsScreen(world.settings, onBack = {}, onManageCategories = {}, onBackup = {}, onAbout = {})
            Screen.Backup -> DataBackupScreen(world.backup, onBack = {})
            Screen.Manage -> ManageHabitsScreen(world.manage, onTracker = {}, tabletLayout = tablet)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun monthDayPhoneLight() = capture(Screen.MonthDay, "monthDay_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun monthDayPhoneDark() = capture(Screen.MonthDay, "monthDay_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun monthDayTabletLight() = capture(Screen.MonthDay, "monthDay_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun monthDayTabletDark() = capture(Screen.MonthDay, "monthDay_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun monthDayPhoneFont200() = capture(Screen.MonthDay, "monthDay_phoneFont200", tablet = false, dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = PHONE)
    fun monthGridPhoneLight() = capture(Screen.MonthGrid, "monthGrid_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun monthGridPhoneDark() = capture(Screen.MonthGrid, "monthGrid_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun monthGridTabletLight() = capture(Screen.MonthGrid, "monthGrid_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun monthGridTabletDark() = capture(Screen.MonthGrid, "monthGrid_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun monthGridPhoneFont200() = capture(Screen.MonthGrid, "monthGrid_phoneFont200", tablet = false, dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = PHONE)
    fun emptyPhoneLight() = capture(Screen.Empty, "empty_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun emptyPhoneDark() = capture(Screen.Empty, "empty_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun emptyTabletLight() = capture(Screen.Empty, "empty_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun emptyTabletDark() = capture(Screen.Empty, "empty_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun emptyPhoneFont200() = capture(Screen.Empty, "empty_phoneFont200", tablet = false, dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = PHONE)
    fun settingsPhoneLight() = capture(Screen.Settings, "settings_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun settingsPhoneDark() = capture(Screen.Settings, "settings_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun settingsTabletLight() = capture(Screen.Settings, "settings_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun settingsTabletDark() = capture(Screen.Settings, "settings_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun settingsPhoneFont200() = capture(Screen.Settings, "settings_phoneFont200", tablet = false, dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = PHONE)
    fun backupPhoneLight() = capture(Screen.Backup, "backup_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun backupPhoneDark() = capture(Screen.Backup, "backup_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun backupTabletLight() = capture(Screen.Backup, "backup_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun backupTabletDark() = capture(Screen.Backup, "backup_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun backupPhoneFont200() = capture(Screen.Backup, "backup_phoneFont200", tablet = false, dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = PHONE)
    fun managePhoneLight() = capture(Screen.Manage, "manage_phoneLight", tablet = false, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun managePhoneDark() = capture(Screen.Manage, "manage_phoneDark", tablet = false, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun manageTabletLight() = capture(Screen.Manage, "manage_tabletLight", tablet = true, dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = TABLET)
    fun manageTabletDark() = capture(Screen.Manage, "manage_tabletDark", tablet = true, dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = PHONE)
    fun managePhoneFont200() = capture(Screen.Manage, "manage_phoneFont200", tablet = false, dark = false, fontScale = 2f)
}
