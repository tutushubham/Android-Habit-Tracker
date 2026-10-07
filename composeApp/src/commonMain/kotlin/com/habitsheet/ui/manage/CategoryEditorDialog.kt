package com.habitsheet.ui.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.model.Category
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitSheetTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CategoryEditorDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category, String) -> Unit,
    onDelete: (Category) -> Unit,
    onAdd: (String) -> Unit,
) {
    var newCategoryName by remember { mutableStateOf("") }
    val ordered = categories.sortedBy { it.displayOrder }
    val modifiedNames = remember(categories) { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.catdlg_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(stringResource(Res.string.catdlg_new)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onAdd(newCategoryName.trim())
                                newCategoryName = ""
                            }
                        },
                        enabled = newCategoryName.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(Res.string.action_add))
                    }
                }
                ordered.forEach { category ->
                    val currentName = modifiedNames[category.id] ?: category.name
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = currentName,
                            onValueChange = { modifiedNames[category.id] = it },
                            label = { Text(stringResource(Res.string.catdlg_category)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        val deleteLabel = stringResource(Res.string.catdlg_delete, category.name)
                        IconButton(
                            onClick = { onDelete(category) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "✕",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 20.sp,
                                modifier = Modifier.semantics { contentDescription = deleteLabel },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ordered.forEach { category ->
                    val newName = modifiedNames[category.id]
                    if (newName != null && newName.isNotBlank() && newName != category.name) {
                        onSave(category, newName.trim())
                    }
                }
                onDismiss()
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Preview
@Composable
private fun CategoryEditorDialogPreview() {
    HabitSheetTheme(darkTheme = false) {
        CategoryEditorDialog(
            categories = listOf(Category("study", "Study 📚", 0, true, 1), Category("health", "Health ❤️", 1, true, 1)),
            onDismiss = {},
            onSave = { _, _ -> },
            onDelete = {},
            onAdd = {},
        )
    }
}
