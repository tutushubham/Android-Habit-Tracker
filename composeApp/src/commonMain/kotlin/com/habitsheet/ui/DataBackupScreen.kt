package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.BackupPreview
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.UiText
import com.habitsheet.presentation.asString
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DataBackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var includeSheetLink by remember { mutableStateOf(false) }
    var applyBackupLink by remember { mutableStateOf(false) }
    var hasSheetLink by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { hasSheetLink = viewModel.hasSheetLink() }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
    var successMessage by remember { mutableStateOf<UiText?>(null) }
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
            SettingsTopBar(onBack, stringResource(Res.string.settings_data_backup), showBack = true, insetTop = showBack)
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
            SectionLabel(stringResource(Res.string.settings_data_backup), Modifier.align(Alignment.Start).padding(bottom = 16.dp))

            Text(
                stringResource(if (viewModel.usesClipboard) Res.string.backup_intro_clipboard else Res.string.backup_intro_file),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            BackupActionCard(
                title = stringResource(Res.string.backup_export_title),
                description = stringResource(if (viewModel.usesClipboard) Res.string.backup_export_desc_clipboard else Res.string.backup_export_desc_file),
                buttonText = stringResource(if (viewModel.usesClipboard) Res.string.backup_export_button_clipboard else Res.string.backup_export_button_file),
                onClick = { viewModel.exportBackup(includeSheetLink = includeSheetLink && hasSheetLink, onResult = ::showResult) },
            ) {
                if (hasSheetLink) {
                    LabeledCheckbox(stringResource(Res.string.backup_include_link), includeSheetLink) { includeSheetLink = it }
                    Text(
                        stringResource(Res.string.backup_include_link_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = stringResource(Res.string.backup_csv_title),
                description = stringResource(if (viewModel.usesClipboard) Res.string.backup_csv_desc_clipboard else Res.string.backup_csv_desc_file),
                buttonText = stringResource(if (viewModel.usesClipboard) Res.string.backup_csv_button_clipboard else Res.string.backup_csv_button_file),
                onClick = { viewModel.exportCsv(::showResult) },
            )

            Spacer(Modifier.height(24.dp))

            BackupActionCard(
                title = stringResource(Res.string.backup_import_title),
                description = stringResource(if (viewModel.usesClipboard) Res.string.backup_import_desc_clipboard else Res.string.backup_import_desc_file),
                buttonText = stringResource(if (viewModel.usesClipboard) Res.string.backup_import_button_clipboard else Res.string.backup_import_button_file),
                onClick = {
                    viewModel.pickBackup(
                        onPreview = {
                            applyBackupLink = false
                            errorMessage = null
                            successMessage = null
                            preview = it
                        },
                        onError = {
                            errorMessage = it
                            successMessage = null
                        },
                    )
                },
                isDestructive = true,
            )

            Spacer(Modifier.height(48.dp))

            var showResetConfirmation by remember { mutableStateOf(false) }

            BackupActionCard(
                title = stringResource(Res.string.backup_reset_title),
                description = stringResource(Res.string.backup_reset_desc),
                buttonText = stringResource(Res.string.backup_reset_button),
                onClick = { showResetConfirmation = true },
                isDestructive = true,
            )

            if (showResetConfirmation) {
                DestructiveConfirmDialog(
                    action = DestructiveAction.ResetAllData(viewModel.currentDataSummary()),
                    onConfirm = {
                        showResetConfirmation = false
                        viewModel.clearAllData()
                        successMessage = UiText.of(Res.string.backup_reset_done)
                        errorMessage = null
                    },
                    onDismiss = { showResetConfirmation = false },
                    extraContent = {
                        TextButton(onClick = { viewModel.exportBackup(onResult = ::showResult) }) {
                            Text(stringResource(Res.string.backup_export_first))
                        }
                    },
                )
            }

            errorMessage?.let { msg ->
                Text(
                    msg.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            successMessage?.let { msg ->
                Text(
                    msg.asString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }

    preview?.let { chosen ->
        DestructiveConfirmDialog(
            action = DestructiveAction.ReplaceWithBackup(viewModel.currentDataSummary(), chosen.incoming),
            onConfirm = {
                preview = null
                viewModel.restore(
                    chosen,
                    onSuccess = {
                        successMessage = UiText.of(Res.string.backup_restored)
                        errorMessage = null
                    },
                    onError = {
                        errorMessage = it
                        successMessage = null
                    },
                    applySheetLink = applyBackupLink && chosen.hasSheetLink,
                )
            },
            onDismiss = { preview = null },
            extraContent = {
                if (chosen.hasSheetLink) {
                    LabeledCheckbox(stringResource(Res.string.backup_use_link), applyBackupLink) { applyBackupLink = it }
                }
                TextButton(onClick = { viewModel.exportBackup(onResult = ::showResult) }) {
                    Text(stringResource(Res.string.backup_export_first))
                }
            },
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
    extra: @Composable ColumnScope.() -> Unit = {},
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
        extra()
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

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
