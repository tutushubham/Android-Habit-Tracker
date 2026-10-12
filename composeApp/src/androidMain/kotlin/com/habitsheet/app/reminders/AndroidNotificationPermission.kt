package com.habitsheet.app.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.habitsheet.platform.NotificationPermission
import com.habitsheet.platform.NotificationPermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The notification permission: a runtime permission from Android 13, an app setting before. Must be created in
 * `onCreate` (it registers an activity-result launcher), like the sign-in provider it lives next to.
 */
class AndroidNotificationPermission(private val activity: ComponentActivity) : NotificationPermission {
    private val prefs = activity.applicationContext.getSharedPreferences("notification_permission", android.content.Context.MODE_PRIVATE)
    private var pending: CancellableContinuation<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit { putBoolean(KEY_ASKED, true) }
        pending?.takeIf { it.isActive }?.resume(granted)
        pending = null
    }

    override suspend fun status(): NotificationPermissionStatus = when {
        NotificationManagerCompat.from(activity).areNotificationsEnabled() -> NotificationPermissionStatus.GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.getBoolean(KEY_ASKED, false) -> NotificationPermissionStatus.NOT_ASKED
        else -> NotificationPermissionStatus.DENIED
    }

    override suspend fun request(): Boolean {
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) return true
        // Before Android 13 there is no dialog: notifications were switched off in Settings.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pending = continuation
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun openSystemSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        }
        activity.startActivity(intent)
    }

    private companion object {
        const val KEY_ASKED = "asked"
    }
}
