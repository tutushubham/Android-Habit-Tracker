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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    val error by viewModel.error.collectAsState()
    var editor by remember { mutableStateOf<HabitEditor?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Any?>(null) }

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
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    TextButton(
                                        onClick = { showArchived = false },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (!showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.semantics {
                                            role = Role.Tab
                                            selected = !showArchived
                                        }
                                    ) {
                                        Text("Active", fontWeight = if (!showArchived) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    TextButton(
                                        onClick = { showArchived = true },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.semantics {
                                            role = Role.Tab
                                            selected = showArchived
                                        }
                                    ) {
                                        Text("Archived", fontWeight = if (showArchived) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }

                                if (!showArchived) {
                                    ManageDailySection(state, archived = false, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) })
                                    ManageWeeklySection(state, archived = false, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) })
                                    ManageCategorySection(state.categories, onManage = { showCategories = true })
                                } else {
                                    ManageDailySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Daily(it) })
                                    ManageWeeklySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Weekly(it) })
                                }
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
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    TextButton(
                                        onClick = { showArchived = false },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (!showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.semantics {
                                            role = Role.Tab
                                            selected = !showArchived
                                        }
                                    ) {
                                        Text("Active", fontWeight = if (!showArchived) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    TextButton(
                                        onClick = { showArchived = true },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.semantics {
                                            role = Role.Tab
                                            selected = showArchived
                                        }
                                    ) {
                                        Text("Archived", fontWeight = if (showArchived) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }

                            if (!showArchived) {
                                ManageDailySection(state, archived = false, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) })
                                ManageWeeklySection(state, archived = false, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) })
                                ManageCategorySection(state.categories, onManage = { showCategories = true })
                            } else {
                                ManageDailySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Daily(it) })
                                ManageWeeklySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Weekly(it) })
                            }
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
            onRestoreDaily = { viewModel.restoreDailyHabit(it.id); editor = null },
            onRestoreWeekly = { viewModel.restoreWeeklyHabit(it.id); editor = null },
            onDeleteDaily = { itemToDelete = it },
            onDeleteWeekly = { itemToDelete = it },
        )
    }
    if (showCategories) {
        CategoryEditorDialog(
            categories = state.categories.filter { it.active },
            onDismiss = { showCategories = false },
            onSave = { category, name -> viewModel.saveCategory(category.id, name, category.displayOrder, category.active) },
            onDelete = { itemToDelete = it },
            onAdd = { viewModel.addCategory(it) },
        )
    }

    itemToDelete?.let { item ->
        val title = when (item) {
            is DailyHabit -> "Delete habit?"
            is WeeklyHabit -> "Delete habit?"
            is Category -> "Delete category?"
            else -> "Delete?"
        }
        val name = when (item) {
            is DailyHabit -> item.name
            is WeeklyHabit -> item.name
            is Category -> item.name
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(title) },
            text = { Text("Delete '$name'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (item) {
                            is DailyHabit -> {
                                viewModel.deleteDailyHabit(item.id)
                                editor = null
                            }
                            is WeeklyHabit -> {
                                viewModel.deleteWeeklyHabit(item.id)
                                editor = null
                            }
                            is Category -> viewModel.deleteCategory(item.id)
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    error?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = viewModel::clearError) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            }
        ) {
            Text(msg)
        }
    }
}

@Composable
private fun ManageTopBar(onBack: () -> Unit, wide: Boolean) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(if (wide) 88.dp else 64.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (wide) 24.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!wide) {
            IconButton(onClick = onBack) { 
                Text(
                    "‹", 
                    fontSize = 32.sp, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "Back" }
                ) 
            }
        }
        Text("Manage Habits", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ManageSidebar(onTracker: () -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(24.dp),
    ) {
        Text("◉ Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(48.dp))
        ManageSidebarItem("▣  Tracker", selected = false, onClick = onTracker)
        ManageSidebarItem("⚙  Manage", selected = true, onClick = {})
    }
}

@Composable
private fun ManageSidebarItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ManageDailySection(state: HabitSnapshot, archived: Boolean, onAdd: () -> Unit, onEdit: (DailyHabit) -> Unit) {
    SectionLabel(if (archived) "Archived daily habits" else "Daily habits", Modifier.padding(bottom = 16.dp))
    val habits = state.dailyHabits.filter { it.active == !archived }.sortedBy { it.displayOrder }
    if (habits.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (archived) "No archived habits" else "No active habits",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (archived) "Your archived habits will appear here." else "Add your first daily habit to start tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            habits.forEach { habit ->
                DefinitionRow(
                    name = habit.name,
                    category = state.categories.firstOrNull { it.id == habit.categoryId },
                    categories = state.categories,
                    goal = "${habit.monthlyGoal}/month",
                    onEdit = { onEdit(habit) },
                )
            }
        }
    }
    if (!archived) AddButton("Add Daily Habit", onAdd)
    else Spacer(Modifier.height(16.dp))
}

@Composable
private fun ManageWeeklySection(state: HabitSnapshot, archived: Boolean, onAdd: () -> Unit, onEdit: (WeeklyHabit) -> Unit) {
    SectionLabel(if (archived) "Archived weekly habits" else "Weekly habits", Modifier.padding(top = 32.dp, bottom = 16.dp))
    val habits = state.weeklyHabits.filter { it.active == !archived }.sortedBy { it.displayOrder }
    if (habits.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (archived) "No archived weekly habits" else "No active weekly habits",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (archived) "Your archived weekly habits will appear here." else "Add habits you want to track each week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            habits.forEach { habit ->
                DefinitionRow(
                    name = habit.name,
                    category = state.categories.firstOrNull { it.id == habit.categoryId },
                    categories = state.categories,
                    goal = "1/week",
                    onEdit = { onEdit(habit) },
                )
            }
        }
    }
    if (!archived) AddButton("Add Weekly Habit", onAdd)
    else Spacer(Modifier.height(16.dp))
}

@Composable
private fun DefinitionRow(
    name: String,
    category: Category?,
    categories: List<Category>,
    goal: String,
    onEdit: () -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .drawBehind {
                drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
            }
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⠿", color = MaterialTheme.colorScheme.outline, fontSize = 20.sp, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(8.dp).background(categoryColor(category, categories), CircleShape))
                Text(
                    category?.name?.uppercase() ?: "GENERAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(goal.substringBefore('/'), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(goal.substringAfter('/').uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("✎", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
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
    val activeCategories = categories.filter { it.active }
    if (activeCategories.isEmpty()) {
        Text(
            "No categories created yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            activeCategories.forEach { category ->
                Row(
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(categoryColor(category, categories), CircleShape))
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
    TextButton(onClick = onManage, modifier = Modifier.padding(top = 10.dp)) {
        Text("⌘  Manage Categories", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorDialog(
    editor: HabitEditor,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveDaily: (DailyHabit?, String, String?, Int) -> Unit,
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
    
    val goal = goalText.toIntOrNull()
    val isNameValid = name.isNotBlank()
    val isGoalValid = !isDaily || (goal != null && goal >= 0)
    val canSave = isNameValid && isGoalValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (daily != null || weekly != null) "Edit habit" else if (isDaily) "Add daily habit" else "Add weekly habit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    enabled = !isArchived,
                    isError = !isNameValid && name.isNotEmpty(),
                    supportingText = if (!isNameValid && name.isNotEmpty()) {
                        { Text("Name cannot be empty") }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = if (isDaily) ImeAction.Next else ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDaily) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it.filter(Char::isDigit) },
                        label = { Text("Monthly goal") },
                        singleLine = true,
                        enabled = !isArchived,
                        isError = !isGoalValid && goalText.isNotEmpty(),
                        supportingText = if (!isGoalValid && goalText.isNotEmpty()) {
                            { Text("Enter a valid number") }
                        } else {
                            { Text("Target completions per month") }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        enabled = !isArchived,
                        label = { Text("None") }
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
                        if (isDaily) onSaveDaily(daily, name.trim(), categoryId, goal ?: 0)
                        else onSaveWeekly(weekly, name.trim(), categoryId)
                    },
                ) { Text("Save") }
            } else {
                Row {
                    TextButton(
                        onClick = {
                            if (daily != null) onDeleteDaily(daily)
                            else if (weekly != null) onDeleteWeekly(weekly)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            if (daily != null) onRestoreDaily(daily)
                            else if (weekly != null) onRestoreWeekly(weekly)
                        },
                    ) { Text("Restore") }
                }
            }
        },
        dismissButton = {
            Row {
                if (!isArchived) {
                    if (daily != null) {
                        TextButton(onClick = { onArchiveDaily(daily) }) { 
                            Text("Archive", color = MaterialTheme.colorScheme.error) 
                        }
                    } else if (weekly != null) {
                        TextButton(onClick = { onArchiveWeekly(weekly) }) { 
                            Text("Archive", color = MaterialTheme.colorScheme.error) 
                        }
                    }
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
    onDelete: (Category) -> Unit,
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
                        IconButton(
                            onClick = { onDelete(category) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                "✕", 
                                color = MaterialTheme.colorScheme.error, 
                                fontSize = 20.sp,
                                modifier = Modifier.semantics { contentDescription = "Delete ${category.name}" }
                            )
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
