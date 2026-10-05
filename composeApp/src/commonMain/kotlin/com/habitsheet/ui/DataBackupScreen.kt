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
import com.habitsheet.presentation.DestructiveAction

@Composable
fun DataBackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    var showImportConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    fun showResult(result: BackupResult) {
        when (result) {
            is BackupResult.Success -> {
                successMessage = result.message
                errorMessage = null
            }

            is BackupResult.Failure -> {
                errorMessage = result.message
                successMessage = null
            }

            BackupResult.Cancelled -> Unit
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(onBack, "Data & Backup", showBack = true, insetTop = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel("Data & Backup", Modifier.align(Alignment.Start).padding(bottom = 16.dp))

            Text(
                if (viewModel.usesClipboard) "Copy a JSON backup and save it in Files, Notes, or another safe place. Copy it again before importing." else "Keep your habit tracking data safe. You can export all your history to a JSON file and restore it later or on another device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            BackupActionCard(
                title = "Export Backup",
                description = if (viewModel.usesClipboard) "Copy all habits, plans, and history as JSON." else "Save your categories, habits, plans, and entire completion history to a local file.",
                buttonText = if (viewModel.usesClipboard) "Copy JSON backup" else "Export JSON",
                onClick = { viewModel.exportBackup(onResult = ::showResult) },
            )

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = "Export CSV",
                description = if (viewModel.usesClipboard) "Copy your habit history as CSV." else "Export your daily and weekly habit history for use in Excel or Google Sheets.",
                buttonText = if (viewModel.usesClipboard) "Copy CSV" else "Export CSV",
                onClick = { viewModel.exportCsv(::showResult) },
            )

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = "Import Backup",
                description = if (viewModel.usesClipboard) "Restore JSON currently copied to the clipboard." else "Restore your data from a previously exported JSON backup file.",
                buttonText = if (viewModel.usesClipboard) "Import copied JSON" else "Import JSON",
                onClick = { showImportConfirmation = true },
                isDestructive = true,
            )

            Spacer(Modifier.height(48.dp))

            var showResetConfirmation by remember { mutableStateOf(false) }

            BackupActionCard(
                title = "Reset all data",
                description = "Permanently remove all habits, history, and categories from this device and disconnect the sheet link. Your Google Sheet is untouched. This cannot be undone.",
                buttonText = "Reset everything",
                onClick = { showResetConfirmation = true },
                isDestructive = true,
            )

            if (showResetConfirmation) {
                DestructiveConfirmDialog(
                    action = DestructiveAction.ResetAllData(viewModel.currentDataSummary()),
                    onConfirm = {
                        showResetConfirmation = false
                        viewModel.clearAllData()
                        successMessage = "All data on this device has been reset"
                        errorMessage = null
                    },
                    onDismiss = { showResetConfirmation = false },
                )
            }

            errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            successMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }

    if (showImportConfirmation) {
        DestructiveConfirmDialog(
            action = DestructiveAction.ReplaceWithBackup(viewModel.currentDataSummary()),
            onConfirm = {
                showImportConfirmation = false
                viewModel.importBackup(
                    onSuccess = {
                        successMessage = "Data restored successfully"
                        errorMessage = null
                    },
                    onError = {
                        errorMessage = it
                        successMessage = null
                    },
                )
            },
            onDismiss = { showImportConfirmation = false },
        )
    }
}

@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isDestructive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(buttonText)
        }
    }
}
