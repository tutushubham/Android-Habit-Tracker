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
