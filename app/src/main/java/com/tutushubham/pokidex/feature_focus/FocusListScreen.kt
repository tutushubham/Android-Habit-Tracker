package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable
fun FocusListScreen(
    focuses: List<Focus>,
    onAddFocus: (String, LocalDate?) -> Unit,
    onDeleteFocus: (String) -> Unit,
    onUpdateFocusName: (String, String) -> Unit,
    onNext: (() -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Focus?>(null) }

    if (showAddDialog) {
        AddFocusDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, deadline ->
                onAddFocus(name, deadline)
                showAddDialog = false
            }
        )
    }

    renameTarget?.let { focus ->
        RenameFocusDialog(
            initialName = focus.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onUpdateFocusName(focus.id, newName)
                renameTarget = null
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = AppShapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add focus")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AppSpacing.xl,
                    end = AppSpacing.xl,
                    top = AppSpacing.lg,
                    bottom = AppSpacing.xxxxxl + AppSpacing.xxxxl
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                item {
                    Text(
                        text = "Active Targets",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Priorities in this domain. Use edit or delete on each row.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.md)
                    )
                }

                items(focuses, key = { it.id }) { focus ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = focus.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            focus.deadline?.let {
                                Text(
                                    text = "Deadline: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renameTarget = focus }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename"
                                    )
                                }
                                IconButton(onClick = { onDeleteFocus(focus.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                    )
                }

                item {
                    if (focuses.isEmpty()) {
                        Text(
                            text = "No targets yet. Tap + to add your first focus.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AppSpacing.xxl)
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(AppSpacing.sm))
                    if (onNext != null) {
                        PrimaryButton(
                            text = "Next: Set strategy",
                            onClick = onNext,
                            enabled = focuses.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFocusDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var deadlineText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New focus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it },
                    label = { Text("Deadline (optional, YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val deadline = parseDeadline(deadlineText)
                    onConfirm(name, deadline)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameFocusDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename focus") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseDeadline(raw: String): LocalDate? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return try {
        LocalDate.parse(t)
    } catch (_: DateTimeParseException) {
        null
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusListScreenPreview() {
    PokidexTheme {
        FocusListScreen(
            focuses = listOf(
                Focus("f1", Domain.FITNESS, "Running", 1, null),
                Focus("f2", Domain.FITNESS, "Yoga", 1, LocalDate.of(2025, 3, 1)),
                Focus("f3", Domain.FITNESS, "Swimming", 2, null)
            ),
            onAddFocus = { _, _ -> },
            onDeleteFocus = {},
            onUpdateFocusName = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusListScreenEmptyPreview() {
    PokidexTheme {
        FocusListScreen(
            focuses = emptyList(),
            onAddFocus = { _, _ -> },
            onDeleteFocus = {},
            onUpdateFocusName = { _, _ -> }
        )
    }
}
