package com.habitsheet.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the wiring in `HabitSheetApp.kt` that unit tests of [com.habitsheet.ui.navigation.AppBackStack] cannot see:
 * one destination host for both layouts, system back routed to the back stack, and the stack saved across
 * configuration changes and process death.
 */
class NavigationWiringTest {
    private val app = listOf(
        File("src/commonMain/kotlin/com/habitsheet/ui/HabitSheetApp.kt"),
        File("composeApp/src/commonMain/kotlin/com/habitsheet/ui/HabitSheetApp.kt"),
    ).first { it.isFile }.readText()

    @Test
    fun phoneAndTabletShareOneDestinationHost() {
        val calls = Regex("""(?<!fun )\bAppDestination\(""").findAll(app).count()
        assertEquals(1, calls, "AppDestination must be called once, with the layout as a parameter")
    }

    @Test
    fun systemBackPopsTheBackStackOnlyWhenThereIsSomewhereToGo() {
        assertTrue(Regex("""BackHandler\(\s*enabled\s*=\s*backStack\.canGoBack""").containsMatchIn(app), "BackHandler enabled by canGoBack")
        assertTrue(Regex("""backStack\.back\(\)""").containsMatchIn(app), "BackHandler calls backStack.back()")
    }

    @Test
    fun theBackStackIsSaveable() {
        assertTrue(Regex("""rememberSaveable\(\s*saver\s*=\s*AppBackStack\.Saver""").containsMatchIn(app), "back stack restored after process death")
    }

    @Test
    fun noScreenKeepsItsOwnDestinationState() {
        assertTrue("mutableStateOf(Destination." !in app, "the current screen lives in AppBackStack only")
    }
}
