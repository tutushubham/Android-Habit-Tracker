package com.habitsheet.platform

import androidx.compose.runtime.Composable

/**
 * Small, platform-aware feedback surface for deliberate user actions.
 *
 * Keeping feedback here avoids leaking Android/iOS APIs into shared UI. Calls are
 * intentionally best-effort: disabled system haptics, silent mode, and devices
 * without a vibrator must never stop a habit from being updated.
 */
interface UserFeedback {
    /** A completion was checked or unchecked. */
    fun completionChanged(completed: Boolean)

    /** A lightweight navigation or date-selection action occurred. */
    fun selectionChanged()

    /** An action failed and the UI is displaying an error. */
    fun error()
}

/** Returns a lifecycle-safe feedback implementation for the current platform. */
@Composable
expect fun rememberUserFeedback(): UserFeedback
