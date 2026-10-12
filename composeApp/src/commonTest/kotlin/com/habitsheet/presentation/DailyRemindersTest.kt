package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.platform.NotificationPermissionStatus
import com.habitsheet.platform.ReminderPlatform
import com.habitsheet.testing.English
import com.habitsheet.testing.FakeLogger
import com.habitsheet.testing.FakeNotificationPermission
import com.habitsheet.testing.FakeReminderScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyRemindersTest {
    private val repo = InMemoryHabitRepository(HabitSnapshot())
    private val scheduler = FakeReminderScheduler()
    private val permission = FakeNotificationPermission()
    private val reminders = DailyReminders(repo, ReminderPlatform(scheduler, permission), resolveText = English::render)
    private val sevenThirty = DailyReminder(7 * 60 + 30)

    @Test
    fun turningItOnAsksOnceStoresTheTimeAndSchedulesTheCatalogText() = runTest {
        assertEquals(ReminderOutcome.ON, reminders.set(sevenThirty))
        assertEquals(1, permission.requests)
        assertEquals(sevenThirty, repo.getDailyReminder())
        assertEquals(FakeReminderScheduler.Scheduled(sevenThirty, "Habit Sheet", "Time to check off today’s habits."), scheduler.scheduled)

        assertEquals(ReminderOutcome.ON, reminders.set(DailyReminder(60)))
        assertEquals(1, permission.requests, "an allowed app is not asked again")
        assertEquals(DailyReminder(60), scheduler.scheduled?.reminder)
    }

    @Test
    fun aRefusedPermissionStoresAndSchedulesNothing() = runTest {
        permission.answer = false
        assertEquals(ReminderOutcome.PERMISSION_DENIED, reminders.set(sevenThirty))
        assertNull(repo.getDailyReminder())
        assertNull(scheduler.scheduled)
        assertEquals(NotificationPermissionStatus.DENIED, reminders.permission())
        reminders.openSystemSettings()
        assertEquals(1, permission.settingsOpened)
    }

    @Test
    fun turningItOffCancelsAndForgetsIt() = runTest {
        reminders.set(sevenThirty)
        assertEquals(ReminderOutcome.OFF, reminders.set(null))
        assertNull(scheduler.scheduled)
        assertNull(reminders.current())
    }

    @Test
    fun atStartTheStoredReminderIsScheduledAgainOnlyWhenAllowed() = runTest {
        repo.setDailyReminder(sevenThirty)
        reminders.restore()
        assertNull(scheduler.scheduled, "not allowed (yet): nothing scheduled, nobody asked")
        assertEquals(0, permission.requests)

        permission.current = NotificationPermissionStatus.GRANTED
        reminders.restore()
        assertEquals(sevenThirty, scheduler.scheduled?.reminder)

        repo.setDailyReminder(null)
        val before = scheduler.calls
        reminders.restore()
        assertEquals(before, scheduler.calls, "off: nothing to do")
    }

    @Test
    fun aStorageFailureIsReportedAndLogged() = runTest {
        val logger = FakeLogger()
        val broken = object : SettingsStore by repo {
            override suspend fun setDailyReminder(reminder: DailyReminder?) = error("disk full")
            override suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long) = Unit
        }
        val failing = DailyReminders(broken, ReminderPlatform(scheduler, permission), logger, English::render)
        assertEquals(ReminderOutcome.FAILED, failing.set(sevenThirty))
        assertNull(scheduler.scheduled)
        assertTrue(logger.entries.isNotEmpty())
    }

    @Test
    fun withoutAPlatformTheReminderCannotBeTurnedOn() = runTest {
        val none = DailyReminders(repo, ReminderPlatform.NONE, resolveText = English::render)
        assertEquals(ReminderOutcome.PERMISSION_DENIED, none.set(sevenThirty))
        assertEquals(ReminderOutcome.OFF, none.set(null))
        none.restore()
        none.openSystemSettings()
    }
}
