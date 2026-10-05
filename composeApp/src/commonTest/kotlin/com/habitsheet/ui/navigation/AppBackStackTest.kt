package com.habitsheet.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app's navigation is hierarchical: Back (on-screen or system) always goes to the screen's parent, exactly as
 * the on-screen back buttons did before there was a back stack. These tests pin that map and the saved state.
 */
class AppBackStackTest {
    @Test
    fun startsOnTheTrackerWithNothingToGoBackTo() {
        val stack = AppBackStack()
        assertEquals(Destination.Tracker, stack.current)
        assertFalse(stack.canGoBack)
        assertFalse(stack.back(), "back on the start screen is left to the system (leave the app)")
        assertEquals(Destination.Tracker, stack.current)
    }

    @Test
    fun backGoesWhereTheOnScreenBackButtonWent() {
        // The previous navigation's on-screen targets: Manage ("Tracker" tab), Plan and Settings -> Tracker;
        // Data & Backup and About -> Settings.
        val expectedParent = mapOf(
            Destination.Manage to Destination.Tracker,
            Destination.Plan to Destination.Tracker,
            Destination.Settings to Destination.Tracker,
            Destination.Backup to Destination.Settings,
            Destination.About to Destination.Settings,
        )
        expectedParent.forEach { (screen, parent) ->
            val stack = AppBackStack()
            stack.navigate(screen)
            assertTrue(stack.back(), "back from $screen")
            assertEquals(parent, stack.current, "back from $screen")
        }
    }

    @Test
    fun deepScreensUnwindThroughTheirParentsToTheTracker() {
        val stack = AppBackStack()
        stack.navigate(Destination.Settings)
        stack.navigate(Destination.Backup)
        assertEquals(listOf(Destination.Tracker, Destination.Settings, Destination.Backup), stack.entries)
        stack.back()
        assertEquals(Destination.Settings, stack.current)
        stack.back()
        assertEquals(Destination.Tracker, stack.current)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun jumpingBetweenSectionsDoesNotPileUpHistory() {
        // Tablet sidebar and the phone bottom bar switch sections; Back then leads to the section's parent only.
        val stack = AppBackStack()
        stack.navigate(Destination.About)
        stack.navigate(Destination.Plan)
        stack.navigate(Destination.Manage)
        assertEquals(listOf(Destination.Tracker, Destination.Manage), stack.entries)
        stack.navigate(Destination.Tracker)
        assertEquals(listOf(Destination.Tracker), stack.entries)
    }

    @Test
    fun theSidebarHighlightsTheSectionAScreenBelongsTo() {
        assertEquals(Destination.Settings, Destination.Backup.section)
        assertEquals(Destination.Settings, Destination.About.section)
        listOf(Destination.Tracker, Destination.Plan, Destination.Manage, Destination.Settings).forEach {
            assertEquals(it, it.section)
        }
    }

    @Test
    fun currentScreenSurvivesSavingAndRestoring() {
        val stack = AppBackStack()
        stack.navigate(Destination.About)
        val restored = AppBackStack.restore(stack.save())
        assertEquals(stack.entries, restored.entries)
        assertEquals(Destination.About, restored.current)
    }

    @Test
    fun unreadableSavedStateFallsBackToTheTracker() {
        assertEquals(listOf(Destination.Tracker), AppBackStack.restore(emptyList()).entries)
        assertEquals(listOf(Destination.Tracker), AppBackStack.restore(listOf("Removed screen")).entries)
        // A saved path is rebuilt from its last screen, so it is always a valid parent chain.
        assertEquals(
            listOf(Destination.Tracker, Destination.Settings, Destination.Backup),
            AppBackStack.restore(listOf("Backup")).entries,
        )
    }
}
