package com.habitsheet.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.asString
import com.habitsheet.resources.*
import com.habitsheet.ui.BottomNavigation
import com.habitsheet.ui.DestructiveConfirmDialog
import org.jetbrains.compose.resources.stringResource

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

    // One list for both layouts; only the gap under the Active/Archived tabs differs.
    val lists = @Composable { tabsBottomPadding: Dp ->
        ManageHabitLists(
            state = state,
            showArchived = showArchived,
            onShowArchivedChange = { showArchived = it },
            tabsBottomPadding = tabsBottomPadding,
            onAddDaily = { editor = HabitEditor.Daily(null) },
            onEditDaily = { editor = HabitEditor.Daily(it) },
            onMoveDaily = viewModel::moveDailyHabit,
            onAddWeekly = { editor = HabitEditor.Weekly(null) },
            onEditWeekly = { editor = HabitEditor.Weekly(it) },
            onMoveWeekly = viewModel::moveWeeklyHabit,
            onManageCategories = { showCategories = true },
        )
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
                        lists(24.dp)
                    }
                }
            } else {
                Scaffold(
                    topBar = { ManageTopBar(wide = false) },
                    bottomBar = { BottomNavigation(selectedTracker = false, onTracker = onTracker, onManage = {}) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        lists(16.dp)
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
                if (existing == null) {
                    viewModel.addDailyHabit(name, categoryId, goal, kind, datedOnly)
                } else {
                    viewModel.updateDailyHabit(existing.copy(name = name, categoryId = categoryId, monthlyGoal = goal, kind = kind, datedOnly = datedOnly))
                }
                editor = null
            },
            onSaveWeekly = { existing, name, categoryId ->
                if (existing == null) {
                    viewModel.addWeeklyHabit(name, categoryId)
                } else {
                    viewModel.updateWeeklyHabit(existing.copy(name = name, categoryId = categoryId))
                }
                editor = null
            },
            onArchiveDaily = {
                viewModel.archiveDailyHabit(it.id)
                editor = null
            },
            onArchiveWeekly = {
                viewModel.archiveWeeklyHabit(it.id)
                editor = null
            },
            onRestoreDaily = {
                viewModel.restoreDailyHabit(it.id)
                editor = null
            },
            onRestoreWeekly = {
                viewModel.restoreWeeklyHabit(it.id)
                editor = null
            },
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
                    Text(stringResource(Res.string.action_ok), color = MaterialTheme.colorScheme.primary)
                }
            },
        ) {
            Text(msg.asString())
        }
    }
}
