package com.habitsheet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitsheet.platform.describeForLog
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.UiText
import com.habitsheet.presentation.asString
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

/** What the platform can do about a database that will not open. Each call reports through [BackupResult]. */
interface StartupRecovery {
    /** Lets the person save a copy of the raw database file(s), e.g. to send to a developer or to recover from. */
    fun exportRawDatabase(onResult: (BackupResult) -> Unit)

    /**
     * Moves the unreadable database aside (it is kept on the device, not deleted) so the next start creates an
     * empty one. Never touches the Google Sheet.
     */
    fun setAsideDatabase(): BackupResult
}

/**
 * Shown instead of the app when the local database cannot be opened or migrated. Deliberately plain: the
 * existing theme, three actions, no navigation. [onRetry] starts the app again.
 */
@Composable
fun StartupFailureScreen(cause: Throwable, recovery: StartupRecovery, onRetry: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    fun show(result: BackupResult) {
        when (result) {
            is BackupResult.Success -> {
                message = result.message
                messageIsError = false
            }

            is BackupResult.Failure -> {
                message = result.message
                messageIsError = true
            }

            BackupResult.Cancelled -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.startup_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.startup_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.startup_detail, cause.describeForLog()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.startup_retry)) }
            OutlinedButton(onClick = { recovery.exportRawDatabase(::show) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.startup_save_copy))
            }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.startup_empty), color = MaterialTheme.colorScheme.error)
            }
            message?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (confirmReset) {
        DestructiveConfirmDialog(
            action = DestructiveAction.SetAsideDamagedData,
            onConfirm = {
                confirmReset = false
                val result = recovery.setAsideDatabase()
                show(result)
                if (result is BackupResult.Success) onRetry()
            },
            onDismiss = { confirmReset = false },
        )
    }
}
