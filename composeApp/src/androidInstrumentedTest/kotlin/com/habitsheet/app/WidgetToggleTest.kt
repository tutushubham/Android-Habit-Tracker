package com.habitsheet.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitsheet.app.widget.HabitCompletionWidgetProvider
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The widget's check-off path end to end: a real broadcast to the real provider, which opens the real database.
 * The provider announces that it finished with ACTION_WIDGET_DATA_CHANGED, which is what the test waits for.
 */
@RunWith(AndroidJUnit4::class)
class WidgetToggleTest {
    private val context: Context = targetContext
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    @Before
    fun oneHabitDueToday() {
        deleteAppDatabase()
        withRepository { it.saveDailyHabit(DailyHabit("walk", "Walk", null, 20, 0, true, today, null, 1, 1)) }
    }

    @After
    fun cleanUp() = deleteAppDatabase()

    private fun <T> withRepository(block: suspend (LocalHabitRepository) -> T): T {
        val repository = LocalHabitRepository(AndroidDriverFactory(context))
        try {
            return runBlocking { block(repository) }
        } finally {
            repository.close()
        }
    }

    private fun snapshot(): HabitSnapshot = withRepository { it.snapshot.value }

    /** Sends the widget's toggle broadcast and returns once the provider reports it is done (or fails after 10 s). */
    private fun tapWidgetRow(renderedDate: String) {
        val done = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = done.countDown()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(HabitCompletionWidgetProvider.ACTION_WIDGET_DATA_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(
                Intent(TOGGLE_DAILY)
                    .setComponent(ComponentName(context, HabitCompletionWidgetProvider::class.java))
                    .putExtra("habit_id", "walk")
                    .putExtra("plan_id", "walk|$today")
                    .putExtra("rendered_date", renderedDate),
            )
            assertTrue(done.await(10, TimeUnit.SECONDS), "the widget did not finish within 10 s")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun tappingARowChecksTheHabitOffAndTappingAgainClearsIt() {
        tapWidgetRow(today.toString())
        val checked = snapshot()
        assertEquals(listOf(true), checked.dailyCompletions.map { it.completed })
        assertEquals(1, checked.pendingCompletions.size, "a widget check-off still has to reach the sheet")

        tapWidgetRow(today.toString())
        assertEquals(listOf(false), snapshot().dailyCompletions.map { it.completed })
    }

    @Test
    fun aRowRenderedYesterdayRecordsNothing() {
        tapWidgetRow("2000-01-01")
        assertTrue(snapshot().dailyCompletions.isEmpty())
    }

    private companion object {
        const val TOGGLE_DAILY = "com.habitsheet.app.action.WIDGET_TOGGLE_DAILY"
    }
}
