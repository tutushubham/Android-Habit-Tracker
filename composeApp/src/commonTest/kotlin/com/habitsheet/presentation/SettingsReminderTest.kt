package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.platform.ReminderPlatform
import com.habitsheet.resources.*
import com.habitsheet.testing.English
import com.habitsheet.testing.FakeNotificationPermission
import com.habitsheet.testing.FakeReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The daily reminder row in Settings. */
class SettingsReminderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = InMemoryHabitRepository()
    private val scheduler = FakeReminderScheduler()
    private val permission = FakeNotificationPermission()
    private val reminders = DailyReminders(repository, ReminderPlatform(scheduler, permission), resolveText = English::render)

    @AfterTest
    fun close() = scope.cancel()

    private fun viewModel(withReminders: Boolean = true) =
        SettingsViewModel(repository, scope, Dispatchers.Unconfined, reminders = reminders.takeIf { withReminders })

    @Test
    fun theReminderCanBeTurnedOnChangedAndOff() = runTest {
        val vm = viewModel()
        assertTrue(vm.remindersAvailable)
        assertNull(vm.reminder.value)

        vm.setReminder(8 * 60)
        assertEquals(DailyReminder(480), vm.reminder.value)
        assertEquals(DailyReminder(480), scheduler.scheduled?.reminder)
        assertNull(vm.reminderMessage.value)

        vm.setReminder(21 * 60 + 15)
        assertEquals(DailyReminder(1275), vm.reminder.value)

        vm.setReminder(null)
        assertNull(vm.reminder.value)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun aRefusedPermissionIsExplainedAndTheSettingsCanBeOpened() = runTest {
        permission.answer = false
        val vm = viewModel()
        vm.setReminder(480)
        assertNull(vm.reminder.value)
        assertEquals(UiText.of(Res.string.settings_reminder_denied), vm.reminderMessage.value)
        vm.openNotificationSettings()
        assertEquals(1, permission.settingsOpened)
    }

    @Test
    fun aReminderSetElsewhereShowsAfterAReload() = runTest {
        val vm = viewModel()
        repository.setDailyReminder(DailyReminder(420))
        assertNull(vm.reminder.value)
        vm.reloadFromStorage()
        assertEquals(DailyReminder(420), vm.reminder.value)
    }

    @Test
    fun withoutAReminderPlatformTheRowIsNotOffered() = runTest {
        val vm = viewModel(withReminders = false)
        assertFalse(vm.remindersAvailable)
        vm.setReminder(480)
        vm.openNotificationSettings()
        assertNull(repository.getDailyReminder())
    }
}
