package com.habitsheet.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitSheetTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

internal sealed interface HabitEditor {
    data class Daily(val habit: DailyHabit?) : HabitEditor
    data class Weekly(val habit: WeeklyHabit?) : HabitEditor
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitEditorDialog(
    editor: HabitEditor,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveDaily: (DailyHabit?, String, String?, Int, HabitKind, Boolean) -> Unit,
    onSaveWeekly: (WeeklyHabit?, String, String?) -> Unit,
    onArchiveDaily: (DailyHabit) -> Unit,
    onArchiveWeekly: (WeeklyHabit) -> Unit,
    onRestoreDaily: (DailyHabit) -> Unit,
    onRestoreWeekly: (WeeklyHabit) -> Unit,
    onDeleteDaily: (DailyHabit) -> Unit,
    onDeleteWeekly: (WeeklyHabit) -> Unit,
) {
    val daily = (editor as? HabitEditor.Daily)?.habit
    val weekly = (editor as? HabitEditor.Weekly)?.habit
    val isDaily = editor is HabitEditor.Daily
    val isArchived = daily?.active == false || weekly?.active == false
    var name by remember(editor) { mutableStateOf(daily?.name ?: weekly?.name.orEmpty()) }
    var goalText by remember(editor) { mutableStateOf(daily?.monthlyGoal?.toString() ?: "") }
    var categoryId by remember(editor) { mutableStateOf(daily?.categoryId ?: weekly?.categoryId) }
    var kind by remember(editor) { mutableStateOf(daily?.kind ?: HabitKind.ACTION) }
    var datedOnly by remember(editor) { mutableStateOf(daily?.datedOnly ?: false) }

    val goal = goalText.toIntOrNull()
    val isNameValid = name.isNotBlank()
    val isGoalValid = !isDaily || (goal != null && goal >= 0)
    val canSave = isNameValid && isGoalValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        daily != null || weekly != null -> Res.string.editor_title_edit
                        isDaily -> Res.string.editor_title_add_daily
                        else -> Res.string.editor_title_add_weekly
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.editor_name)) },
                    singleLine = true,
                    enabled = !isArchived,
                    isError = !isNameValid && name.isNotEmpty(),
                    supportingText = if (!isNameValid && name.isNotEmpty()) {
                        { Text(stringResource(Res.string.editor_name_empty)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = if (isDaily) ImeAction.Next else ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDaily) {
                    Text(
                        stringResource(Res.string.editor_track_as),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == HabitKind.ACTION,
                            onClick = { kind = HabitKind.ACTION },
                            label = { Text(stringResource(Res.string.editor_kind_do)) },
                        )
                        FilterChip(
                            selected = kind == HabitKind.AVOIDANCE,
                            onClick = { kind = HabitKind.AVOIDANCE },
                            label = { Text(stringResource(Res.string.editor_kind_avoid)) },
                        )
                    }
                    if (kind == HabitKind.AVOIDANCE) {
                        Text(
                            stringResource(Res.string.editor_avoid_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.editor_dated_only), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.Switch(checked = datedOnly, onCheckedChange = { datedOnly = it })
                    }
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it.filter(Char::isDigit) },
                        label = { Text(stringResource(Res.string.editor_goal)) },
                        singleLine = true,
                        enabled = !isArchived,
                        isError = !isGoalValid && goalText.isNotEmpty(),
                        supportingText = if (!isGoalValid && goalText.isNotEmpty()) {
                            { Text(stringResource(Res.string.editor_goal_invalid)) }
                        } else {
                            { Text(stringResource(Res.string.editor_goal_hint)) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }

                Text(
                    text = stringResource(Res.string.editor_category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        enabled = !isArchived,
                        label = { Text(stringResource(Res.string.editor_category_none)) },
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            enabled = !isArchived,
                            label = { Text(category.name, maxLines = 1) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isArchived) {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        if (isDaily) {
                            onSaveDaily(daily, name.trim(), categoryId, goal ?: 0, kind, datedOnly)
                        } else {
                            onSaveWeekly(weekly, name.trim(), categoryId)
                        }
                    },
                ) { Text(stringResource(Res.string.action_save)) }
            } else {
                Row {
                    TextButton(
                        onClick = {
                            if (daily != null) {
                                onDeleteDaily(daily)
                            } else if (weekly != null) {
                                onDeleteWeekly(weekly)
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            if (daily != null) {
                                onRestoreDaily(daily)
                            } else if (weekly != null) {
                                onRestoreWeekly(weekly)
                            }
                        },
                    ) { Text(stringResource(Res.string.action_restore)) }
                }
            }
        },
        dismissButton = {
            Row {
                if (!isArchived) {
                    if (daily != null) {
                        TextButton(onClick = { onArchiveDaily(daily) }) {
                            Text(stringResource(Res.string.action_archive), color = MaterialTheme.colorScheme.error)
                        }
                    } else if (weekly != null) {
                        TextButton(onClick = { onArchiveWeekly(weekly) }) {
                            Text(stringResource(Res.string.action_archive), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Preview
@Composable
private fun HabitEditorDialogPreview() {
    HabitSheetTheme(darkTheme = false) {
        HabitEditorDialog(
            editor = HabitEditor.Daily(DailyHabit("read", "Read 20 pages", "study", 25, 0, true, LocalDate(2026, 9, 1), null, 1, 1)),
            categories = listOf(Category("study", "Study 📚", 0, true, 1), Category("health", "Health ❤️", 1, true, 1)),
            onDismiss = {}, onSaveDaily = { _, _, _, _, _, _ -> }, onSaveWeekly = { _, _, _ -> },
            onArchiveDaily = {}, onArchiveWeekly = {}, onRestoreDaily = {}, onRestoreWeekly = {}, onDeleteDaily = {}, onDeleteWeekly = {},
        )
    }
}
