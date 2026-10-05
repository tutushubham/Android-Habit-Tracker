package com.habitsheet.platform

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

private class AndroidUserFeedback(
    private val view: View,
) : UserFeedback {
    override fun completionChanged(completed: Boolean) {
        safely {
            view.performHapticFeedback(
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && completed ->
                        HapticFeedbackConstants.CONFIRM

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        HapticFeedbackConstants.GESTURE_END

                    else -> HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
            // This follows the user's system "touch sounds" preference and volume.
            view.playSoundEffect(
                if (completed) SoundEffectConstants.CLICK else SoundEffectConstants.NAVIGATION_DOWN,
            )
        }
    }

    override fun selectionChanged() {
        safely {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
        }
    }

    override fun error() {
        safely {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                },
            )
        }
    }

    private inline fun safely(action: () -> Unit) {
        // Feedback is decorative. An OEM implementation must not be able to break
        // the action that caused it.
        runCatching(action)
    }
}

@Composable
actual fun rememberUserFeedback(): UserFeedback {
    val view = LocalView.current
    return remember(view) { AndroidUserFeedback(view) }
}
