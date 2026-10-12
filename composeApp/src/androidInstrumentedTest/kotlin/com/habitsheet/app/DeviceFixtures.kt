package com.habitsheet.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Shared helpers for the on-device tests. They assume nothing about the device language. */
internal val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** The string as the app shows it on this device (the same resources, the same locale). */
internal fun s(resource: StringResource, vararg args: Any): String = runBlocking { getString(resource, *args) }

/** Removes the app's database (and its journal files) so a test starts from a first run. */
internal fun deleteAppDatabase() {
    targetContext.deleteDatabase(AndroidDriverFactory.DATABASE_NAME)
}

/**
 * Waits until [condition] holds, for work the system does on its own schedule without a callback to wait on
 * (a posted notification appears in the system server shortly after `notify`). Polls up to [timeoutMillis]; this
 * waits for a condition, not a fixed time. Returns whether it held.
 */
internal fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (!condition()) {
        if (System.nanoTime() > deadline) return false
        java.util.concurrent.locks.LockSupport.parkNanos(10_000_000)
    }
    return true
}
