package com.habitsheet.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.habitsheet.presentation.DestructiveAction

/**
 * The one confirmation dialog for everything in [DestructiveAction]. Screens must show it before calling a
 * view-model function that deletes, resets or replaces data.
 */
@Composable
fun DestructiveConfirmDialog(action: DestructiveAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val text = action.confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.title) },
        text = { Text(text.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text.confirmLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
