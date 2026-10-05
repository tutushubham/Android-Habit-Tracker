package com.habitsheet.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** The app's screens. Navigation is hierarchical: every screen except [Tracker] has one [parent]. */
internal enum class Destination {
    Tracker, Manage, Plan, Settings, Backup, About;

    /** Where Back leads (on-screen and system back alike); null for the start screen. */
    val parent: Destination?
        get() = when (this) {
            Tracker -> null
            Manage, Plan, Settings -> Tracker
            Backup, About -> Settings
        }

    /** The top-level section a screen belongs to (highlighted in the tablet sidebar). */
    val section: Destination
        get() = parent?.takeIf { it != Tracker }?.section ?: this
}

/**
 * The back stack: the chain of parents leading to the current screen, e.g. Tracker > Settings > Backup.
 * Navigating anywhere replaces it with that screen's chain, so Back always means "up one level", which is what the
 * on-screen back buttons do. Survives configuration changes and process death through [Saver].
 */
@Stable
internal class AppBackStack(initial: List<Destination> = listOf(Destination.Tracker)) {
    var entries: List<Destination> by mutableStateOf(initial)
        private set

    val current: Destination get() = entries.last()

    /** False on the start screen: system back then leaves the app as usual. */
    val canGoBack: Boolean get() = entries.size > 1

    fun navigate(to: Destination) {
        entries = pathTo(to)
    }

    /** Goes up one level; returns false (and does nothing) on the start screen. */
    fun back(): Boolean {
        if (!canGoBack) return false
        entries = entries.dropLast(1)
        return true
    }

    fun save(): List<String> = entries.map { it.name }

    companion object {
        fun pathTo(destination: Destination): List<Destination> =
            generateSequence(destination) { it.parent }.toList().asReversed()

        /** Rebuilt from the last saved screen; anything unreadable falls back to the start screen. */
        fun restore(saved: List<String>): AppBackStack {
            val last = saved.lastOrNull()?.let { name -> Destination.entries.firstOrNull { it.name == name } }
            return AppBackStack(pathTo(last ?: Destination.Tracker))
        }

        val Saver: Saver<AppBackStack, Any> = listSaver(save = { it.save() }, restore = { restore(it) })
    }
}
