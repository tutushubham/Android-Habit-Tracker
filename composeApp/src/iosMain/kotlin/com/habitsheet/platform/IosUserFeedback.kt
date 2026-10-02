@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

private class IosUserFeedback : UserFeedback {
    private val selection = UISelectionFeedbackGenerator()
    private val completion = UINotificationFeedbackGenerator()
    private val removal = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)

    override fun completionChanged(completed: Boolean) {
        safely {
            if (completed) {
                completion.prepare()
                completion.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
                )
                // Public AudioToolbox system-sound playback; it respects Silent Mode.
                AudioServicesPlaySystemSound(COMPLETION_SOUND_ID)
            } else {
                removal.prepare()
                removal.impactOccurred()
            }
        }
    }

    override fun selectionChanged() {
        safely {
            selection.prepare()
            selection.selectionChanged()
        }
    }

    override fun error() {
        safely {
            completion.prepare()
            completion.notificationOccurred(
                UINotificationFeedbackType.UINotificationFeedbackTypeError,
            )
        }
    }

    private inline fun safely(action: () -> Unit) {
        // Feedback is decorative and can be unavailable on some hardware.
        runCatching(action)
    }

    private companion object {
        // 1104 is the short, non-looping system "Tock" used for UI confirmation.
        const val COMPLETION_SOUND_ID: UInt = 1104u
    }
}

@Composable
actual fun rememberUserFeedback(): UserFeedback = remember { IosUserFeedback() }
