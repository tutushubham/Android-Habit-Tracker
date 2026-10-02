package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.BackupViewModel

@Composable
fun DataBackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    var showImportConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            SettingsTopBar(onBack, "Data & Backup", showBack = true, insetTop = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionLabel("Data & Backup", Modifier.align(Alignment.Start).padding(bottom = 16.dp))
            
            Text(
                if (viewModel.usesClipboard) "Copy a JSON backup and save it in Files, Notes, or another safe place. Copy it again before importing." else "Keep your habit tracking data safe. You can export all your history to a JSON file and restore it later or on another device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            BackupActionCard(
                title = "Export Backup",
                description = if (viewModel.usesClipboard) "Copy all habits, plans, and history as JSON." else "Save your categories, habits, plans, and entire completion history to a local file.",
                buttonText = if (viewModel.usesClipboard) "Copy JSON backup" else "Export JSON",
                onClick = {
                    viewModel.exportBackup()
                    if (viewModel.usesClipboard) successMessage = "Backup copied. Paste and save it somewhere safe before copying anything else."
                }
            )

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = "Export CSV",
                description = if (viewModel.usesClipboard) "Copy your habit history as CSV." else "Export your daily and weekly habit history for use in Excel or Google Sheets.",
                buttonText = if (viewModel.usesClipboard) "Copy CSV" else "Export CSV",
                onClick = {
                    viewModel.exportCsv()
                    if (viewModel.usesClipboard) successMessage = "CSV copied to clipboard."
                }
            )

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = "Import Backup",
                description = if (viewModel.usesClipboard) "Restore JSON currently copied to the clipboard." else "Restore your data from a previously exported JSON backup file.",
                buttonText = if (viewModel.usesClipboard) "Import copied JSON" else "Import JSON",
                onClick = { showImportConfirmation = true },
                isDestructive = true
            )

            Spacer(Modifier.height(48.dp))

            var showResetConfirmation by remember { mutableStateOf(false) }

            BackupActionCard(
                title = "Reset all data",
                description = "Permanently remove all habits, history, and categories from this device. This cannot be undone.",
                buttonText = "Reset everything",
                onClick = { showResetConfirmation = true },
                isDestructive = true
            )

            if (showResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text("Reset all data?") },
                    text = { Text("This will permanently delete all your habits, completion history, categories, and local data. This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetConfirmation = false
                                viewModel.clearAllData()
                                successMessage = "All data has been reset"
                                errorMessage = null
                            }
                        ) {
                            Text("Reset all data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            successMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("Replace existing data?") },
            text = { Text(if (viewModel.usesClipboard) "Importing the copied JSON will permanently replace your current habits, plans, and history. This cannot be undone." else "Importing a backup will permanently replace all your current habits, plans, and history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        viewModel.importBackup(
                            onSuccess = { 
                                successMessage = "Data restored successfully"
                                errorMessage = null
                            },
                            onError = { 
                                errorMessage = it 
                                successMessage = null
                            }
                        )
                    }
                ) {
                    Text("Import & Replace", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error)
                      else ButtonDefaults.buttonColors(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(buttonText)
        }
    }
}
