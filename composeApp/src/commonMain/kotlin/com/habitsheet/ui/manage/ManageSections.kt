package com.habitsheet.ui.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.ui.HabitIcon
import com.habitsheet.ui.HabitIconGlyph
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SectionLabel
import com.habitsheet.ui.categoryColor
import kotlinx.datetime.LocalDate

@Composable
internal fun ManageTopBar(wide: Boolean) {
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

/** The Active/Archived tabs and the habit lists under them (the same for phone and tablet). */
@Composable
internal fun ManageHabitLists(
    state: HabitSnapshot,
    showArchived: Boolean,
    onShowArchivedChange: (Boolean) -> Unit,
    tabsBottomPadding: Dp,
    onAddDaily: () -> Unit,
    onEditDaily: (DailyHabit) -> Unit,
    onMoveDaily: (Int, Int) -> Unit,
    onAddWeekly: () -> Unit,
    onEditWeekly: (WeeklyHabit) -> Unit,
    onMoveWeekly: (Int, Int) -> Unit,
    onManageCategories: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = tabsBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ArchiveTab("Active", isSelected = !showArchived, onClick = { onShowArchivedChange(false) })
            ArchiveTab("Archived", isSelected = showArchived, onClick = { onShowArchivedChange(true) })
        }

        if (!showArchived) {
            ManageDailySection(state, archived = false, onAdd = onAddDaily, onEdit = onEditDaily, onMove = onMoveDaily)
            ManageWeeklySection(state, archived = false, onAdd = onAddWeekly, onEdit = onEditWeekly, onMove = onMoveWeekly)
            ManageCategorySection(state.categories, onManage = onManageCategories)
        } else {
            ManageDailySection(state, archived = true, onAdd = { }, onEdit = onEditDaily)
            ManageWeeklySection(state, archived = true, onAdd = { }, onEdit = onEditWeekly)
        }
    }
}

@Composable
private fun ArchiveTab(label: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.semantics {
            role = Role.Tab
            selected = isSelected
        }
    ) {
        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

private val previewStart = LocalDate(2026, 9, 1)
private val previewSnapshot = HabitSnapshot(
    categories = listOf(Category("study", "Study 📚", 0, true, 1), Category("health", "Health ❤️", 1, true, 1)),
    dailyHabits = listOf(
        DailyHabit("read", "Read 20 pages", "study", 25, 0, true, previewStart, null, 1, 1),
        DailyHabit("water", "Drink 2L water", "health", 28, 1, true, previewStart, null, 1, 1),
        DailyHabit("old", "Old habit", null, 10, 2, false, previewStart, LocalDate(2026, 9, 20), 1, 1),
    ),
    weeklyHabits = listOf(WeeklyHabit("call", "Call parents", null, 0, true, previewStart, null, 1, 1)),
)

@Preview(widthDp = 411, heightDp = 900)
@Composable
private fun ManageHabitListsPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            Column {
                ManageTopBar(wide = false)
                ManageHabitLists(
                    previewSnapshot, showArchived = false, onShowArchivedChange = {}, tabsBottomPadding = 16.dp,
                    onAddDaily = {}, onEditDaily = {}, onMoveDaily = { _, _ -> },
                    onAddWeekly = {}, onEditWeekly = {}, onMoveWeekly = { _, _ -> }, onManageCategories = {},
                )
            }
        }
    }
}

@Preview(widthDp = 411)
@Composable
private fun ManageArchivedPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            ManageHabitLists(
                previewSnapshot, showArchived = true, onShowArchivedChange = {}, tabsBottomPadding = 16.dp,
                onAddDaily = {}, onEditDaily = {}, onMoveDaily = { _, _ -> },
                onAddWeekly = {}, onEditWeekly = {}, onMoveWeekly = { _, _ -> }, onManageCategories = {},
            )
        }
    }
}
