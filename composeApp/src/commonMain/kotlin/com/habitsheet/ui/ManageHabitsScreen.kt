package com.habitsheet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.presentation.ManageHabitsViewModel

private sealed interface HabitEditor {
    data class Daily(val habit: DailyHabit?) : HabitEditor
    data class Weekly(val habit: WeeklyHabit?) : HabitEditor
}

@Composable
fun ManageHabitsScreen(viewModel: ManageHabitsViewModel, onTracker: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var editor by remember { mutableStateOf<HabitEditor?>(null) }
    var showCategories by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    ManageSidebar(onTracker)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        ManageTopBar(onTracker, wide = true)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                Modifier
                                    .widthIn(max = 720.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                            ) {
                                ManageDailySection(state, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) })
                                ManageWeeklySection(state, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) })
                                ManageCategorySection(state.categories, onManage = { showCategories = true })
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    topBar = { ManageTopBar(onTracker, wide = false) },
                    bottomBar = { BottomNavigation(selectedTracker = false, onTracker = onTracker, onManage = {}) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                        ) {
                            ManageDailySection(state, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) })
                            ManageWeeklySection(state, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) })
                            ManageCategorySection(state.categories, onManage = { showCategories = true })
                        }
                    }
                }
            }
        }
    }

    editor?.let { value ->
        HabitEditorDialog(
            editor = value,
            categories = state.categories.filter { it.active },
            onDismiss = { editor = null },
            onSaveDaily = { existing, name, categoryId, goal ->
                if (existing == null) viewModel.addDailyHabit(name, categoryId, goal)
                else viewModel.updateDailyHabit(existing.copy(name = name, categoryId = categoryId, monthlyGoal = goal))
                editor = null
            },
            onSaveWeekly = { existing, name, categoryId ->
                if (existing == null) viewModel.addWeeklyHabit(name, categoryId)
                else viewModel.updateWeeklyHabit(existing.copy(name = name, categoryId = categoryId))
                editor = null
            },
            onArchiveDaily = { viewModel.archiveDailyHabit(it.id); editor = null },
            onArchiveWeekly = { viewModel.archiveWeeklyHabit(it.id); editor = null },
        )
    }
    if (showCategories) {
        CategoryEditorDialog(
            categories = state.categories.filter { it.active },
            onDismiss = { showCategories = false },
            onSave = { category, name -> viewModel.saveCategory(category.id, name, category.displayOrder, category.active) },
            onArchive = { viewModel.archiveCategory(it.id) },
            onAdd = { viewModel.addCategory(it) },
        )
    }
}

@Composable
private fun ManageTopBar(onBack: () -> Unit, wide: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(if (wide) 72.dp else 56.dp)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .padding(horizontal = if (wide) 24.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!wide) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary) }
        }
        Text("Manage Habits", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ManageSidebar(onTracker: () -> Unit) {
    Column(
        Modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .padding(16.dp),
    ) {
        Text("◉ Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(36.dp))
        ManageSidebarItem("▣  Tracker", selected = false, onClick = onTracker)
        ManageSidebarItem("⚙  Manage", selected = true, onClick = {})
    }
}

@Composable
private fun ManageSidebarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ManageDailySection(state: HabitSnapshot, onAdd: () -> Unit, onEdit: (DailyHabit) -> Unit) {
    SectionLabel("Daily habits", Modifier.padding(bottom = 14.dp))
    Column(Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))) {
        state.dailyHabits.filter { it.active }.sortedBy { it.displayOrder }.forEach { habit ->
            DefinitionRow(
                name = habit.name,
                category = state.categories.firstOrNull { it.id == habit.categoryId },
                categories = state.categories,
                goal = "${habit.monthlyGoal}/month",
                onEdit = { onEdit(habit) },
            )
        }
    }
    AddButton("Add Daily Habit", onAdd)
}

@Composable
private fun ManageWeeklySection(state: HabitSnapshot, onAdd: () -> Unit, onEdit: (WeeklyHabit) -> Unit) {
    SectionLabel("Weekly habits", Modifier.padding(top = 28.dp, bottom = 14.dp))
    Column(Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))) {
        state.weeklyHabits.filter { it.active }.sortedBy { it.displayOrder }.forEach { habit ->
            DefinitionRow(
                name = habit.name,
                category = state.categories.firstOrNull { it.id == habit.categoryId },
                categories = state.categories,
                goal = "1/week",
                onEdit = { onEdit(habit) },
            )
        }
    }
    AddButton("Add Weekly Habit", onAdd)
}

@Composable
private fun DefinitionRow(
    name: String,
    category: Category?,
    categories: List<Category>,
    goal: String,
    onEdit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
            .clickable(onClick = onEdit)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⠿", color = MaterialTheme.colorScheme.outline, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Box(Modifier.size(8.dp).background(categoryColor(category, categories), CircleShape))
                Text(
                    category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(goal.substringBefore('/'), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(goal.substringAfter('/').uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("✎", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Text("＋  $label", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ManageCategorySection(categories: List<Category>, onManage: () -> Unit) {
    SectionLabel("Categories", Modifier.padding(top = 28.dp, bottom = 14.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.filter { it.active }.forEach { category ->
            Row(
                Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(categoryColor(category, categories), CircleShape))
                Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
    TextButton(onClick = onManage, modifier = Modifier.padding(top = 10.dp)) {
        Text("⌘  Manage Categories", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HabitEditorDialog(
    editor: HabitEditor,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveDaily: (DailyHabit?, String, String?, Int) -> Unit,
    onSaveWeekly: (WeeklyHabit?, String, String?) -> Unit,
    onArchiveDaily: (DailyHabit) -> Unit,
    onArchiveWeekly: (WeeklyHabit) -> Unit,
) {
    val daily = (editor as? HabitEditor.Daily)?.habit
    val weekly = (editor as? HabitEditor.Weekly)?.habit
    val isDaily = editor is HabitEditor.Daily
    var name by remember(editor) { mutableStateOf(daily?.name ?: weekly?.name.orEmpty()) }
    var goalText by remember(editor) { mutableStateOf(daily?.monthlyGoal?.toString() ?: "") }
    var categoryId by remember(editor) { mutableStateOf(daily?.categoryId ?: weekly?.categoryId) }
    val goal = goalText.toIntOrNull()
    val valid = name.isNotBlank() && (!isDaily || goal != null && goal >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (daily != null || weekly != null) "Edit habit" else if (isDaily) "Add daily habit" else "Add weekly habit") },
        text = {
            Column(Modifier.imePadding()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDaily) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it.filter(Char::isDigit) },
                        label = { Text("Monthly goal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name, maxLines = 1) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (isDaily) onSaveDaily(daily, name.trim(), categoryId, goal ?: 0)
                    else onSaveWeekly(weekly, name.trim(), categoryId)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                when {
                    daily != null -> TextButton(onClick = { onArchiveDaily(daily) }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
                    weekly != null -> TextButton(onClick = { onArchiveWeekly(weekly) }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CategoryEditorDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category, String) -> Unit,
    onArchive: (Category) -> Unit,
    onAdd: (String) -> Unit,
) {
    var newCategoryName by remember { mutableStateOf("") }
    val ordered = categories.sortedBy { it.displayOrder }
    val modifiedNames = remember(categories) { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage categories") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                ordered.forEach { category ->
                    val currentName = modifiedNames[category.id] ?: category.name
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = currentName,
                            onValueChange = { modifiedNames[category.id] = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onArchive(category) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 20.sp)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New category") },
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
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Add")
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
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
