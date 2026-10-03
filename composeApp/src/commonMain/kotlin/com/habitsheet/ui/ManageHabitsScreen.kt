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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.ManageHabitsViewModel

private sealed interface HabitEditor {
    data class Daily(val habit: DailyHabit?) : HabitEditor
    data class Weekly(val habit: WeeklyHabit?) : HabitEditor
}

@Composable
fun ManageHabitsScreen(
    viewModel: ManageHabitsViewModel,
    onTracker: () -> Unit,
    tabletLayout: Boolean = false,
    openCategories: Boolean = false,
    onCategoriesOpened: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<HabitEditor?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(openCategories) {
        if (openCategories) {
            showCategories = true
            onCategoriesOpened()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = tabletLayout
            if (wide) {
                Column(Modifier.fillMaxSize()) {
                        ManageTopBar(wide = true)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                Modifier
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
                                    ManageDailySection(state, archived = false, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) }, onMove = viewModel::moveDailyHabit)
                                    ManageWeeklySection(state, archived = false, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) }, onMove = viewModel::moveWeeklyHabit)
                                    ManageCategorySection(state.categories, onManage = { showCategories = true })
                                } else {
                                    ManageDailySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Daily(it) })
                                    ManageWeeklySection(state, archived = true, onAdd = { }, onEdit = { editor = HabitEditor.Weekly(it) })
                                }
                            }
                        }
                }
            } else {
                Scaffold(
                    topBar = { ManageTopBar(wide = false) },
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
                                ManageDailySection(state, archived = false, onAdd = { editor = HabitEditor.Daily(null) }, onEdit = { editor = HabitEditor.Daily(it) }, onMove = viewModel::moveDailyHabit)
                                ManageWeeklySection(state, archived = false, onAdd = { editor = HabitEditor.Weekly(null) }, onEdit = { editor = HabitEditor.Weekly(it) }, onMove = viewModel::moveWeeklyHabit)
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
            onSaveDaily = { existing, name, categoryId, goal, kind, datedOnly ->
                if (existing == null) viewModel.addDailyHabit(name, categoryId, goal, kind, datedOnly)
                else viewModel.updateDailyHabit(existing.copy(name = name, categoryId = categoryId, monthlyGoal = goal, kind = kind, datedOnly = datedOnly))
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
        val action = when (item) {
            is DailyHabit -> DestructiveAction.delete(item, state)
            is WeeklyHabit -> DestructiveAction.delete(item, state)
            is Category -> DestructiveAction.delete(item, state)
            else -> null
        }
        if (action == null) {
            itemToDelete = null
        } else {
            DestructiveConfirmDialog(
                action = action,
                onConfirm = {
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
                },
                onDismiss = { itemToDelete = null },
            )
        }
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
private fun ManageTopBar(wide: Boolean) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (wide) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars))
            .height(if (wide) 72.dp else 64.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Habits", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ManageDailySection(state: HabitSnapshot, archived: Boolean, onAdd: () -> Unit, onEdit: (DailyHabit) -> Unit, onMove: (Int, Int) -> Unit = { _, _ -> }) {
    SectionLabel(if (archived) "Archived daily habits" else "Daily habits", Modifier.padding(bottom = 16.dp))
    val habits = state.dailyHabits.filter { it.active == !archived }.sortedBy { it.displayOrder }
    if (habits.isEmpty()) {
        Text(
            if (archived) "No archived daily habits." else "No daily habits yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    } else {
        Column(Modifier.fillMaxWidth()) {
            habits.forEachIndexed { index, habit ->
                DefinitionRow(
                    name = habit.name,
                    category = state.categories.firstOrNull { it.id == habit.categoryId },
                    categories = state.categories,
                    goal = "${habit.monthlyGoal}/month",
                    onEdit = { onEdit(habit) },
                    onMoveUp = if (!archived && index > 0) { { onMove(index, index - 1) } } else null,
                    onMoveDown = if (!archived && index < habits.size - 1) { { onMove(index, index + 1) } } else null
                )
            }
        }
    }
    if (!archived) AddButton("Add Daily Habit", onAdd)
    else Spacer(Modifier.height(16.dp))
}

@Composable
private fun ManageWeeklySection(state: HabitSnapshot, archived: Boolean, onAdd: () -> Unit, onEdit: (WeeklyHabit) -> Unit, onMove: (Int, Int) -> Unit = { _, _ -> }) {
    SectionLabel(if (archived) "Archived weekly habits" else "Weekly habits", Modifier.padding(top = 32.dp, bottom = 16.dp))
    val habits = state.weeklyHabits.filter { it.active == !archived }.sortedBy { it.displayOrder }
    if (habits.isEmpty()) {
        Text(
            if (archived) "No archived weekly habits." else "No weekly habits yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    } else {
        Column(Modifier.fillMaxWidth()) {
            habits.forEachIndexed { index, habit ->
                DefinitionRow(
                    name = habit.name,
                    category = state.categories.firstOrNull { it.id == habit.categoryId },
                    categories = state.categories,
                    goal = "1/week",
                    onEdit = { onEdit(habit) },
                    onMoveUp = if (!archived && index > 0) { { onMove(index, index - 1) } } else null,
                    onMoveDown = if (!archived && index < habits.size - 1) { { onMove(index, index + 1) } } else null
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
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 420.dp
        val rowModifier = Modifier.fillMaxWidth().drawBehind {
            drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
        }.padding(horizontal = 8.dp)
        if (narrow) {
            Column(rowModifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HabitRowIdentity(name, category, categories, Modifier.weight(1f))
                    TextButton(onClick = onEdit, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Edit") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Goal $goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (onMoveUp != null || onMoveDown != null) {
                        HabitMoveButton(name, true, onMoveUp)
                        HabitMoveButton(name, false, onMoveDown)
                    }
                }
            }
        } else {
            Row(rowModifier.height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onMoveUp != null || onMoveDown != null) {
                    Column {
                        HabitMoveButton(name, true, onMoveUp)
                        HabitMoveButton(name, false, onMoveDown)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                HabitRowIdentity(name, category, categories, Modifier.weight(1f))
                Text("Goal $goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1)
                TextButton(onClick = onEdit, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun HabitRowIdentity(name: String, category: Category?, categories: List<Category>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(8.dp).background(categoryColor(category, categories), CircleShape))
            Text(
                category?.name?.uppercase() ?: "GENERAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HabitMoveButton(name: String, up: Boolean, onClick: (() -> Unit)?) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.size(44.dp).semantics { contentDescription = "Move $name ${if (up) "up" else "down"}" },
    ) {
        HabitIcon(if (up) HabitIconGlyph.Up else HabitIconGlyph.Down, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Text("＋  $label", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Text("Edit categories", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorDialog(
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
                    Text("TRACK AS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == HabitKind.ACTION, onClick = { kind = HabitKind.ACTION }, label = { Text("Do") })
                        FilterChip(selected = kind == HabitKind.AVOIDANCE, onClick = { kind = HabitKind.AVOIDANCE }, label = { Text("Avoid") })
                    }
                    if (kind == HabitKind.AVOIDANCE) Text("Name it as a positive check-off, e.g. No Sugar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Only on planned dates", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.Switch(checked = datedOnly, onCheckedChange = { datedOnly = it })
                    }
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
                        if (isDaily) onSaveDaily(daily, name.trim(), categoryId, goal ?: 0, kind, datedOnly)
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
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
