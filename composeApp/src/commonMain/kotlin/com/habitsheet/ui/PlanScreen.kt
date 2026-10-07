package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.DestructiveAction
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(Res.string.weekday_mon),
    stringResource(Res.string.weekday_tue),
    stringResource(Res.string.weekday_wed),
    stringResource(Res.string.weekday_thu),
    stringResource(Res.string.weekday_fri),
    stringResource(Res.string.weekday_sat),
    stringResource(Res.string.weekday_sun),
)

private data class PlanEditor(val habit: DailyHabit, val weekday: Int? = null, val planId: String? = null)

@Composable
fun PlanScreen(viewModel: MonthViewModel, onBack: () -> Unit, showBack: Boolean = true, sheetUrl: String = "") {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val weekdays = weekdayLabels()
    var weeklyMode by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<PlanEditor?>(null) }
    var pendingRemoval by remember { mutableStateOf<Pair<DestructiveAction, () -> Unit>?>(null) }
    val selectedDate = state.selectedDay
    val activeHabits = state.allDailyHabits.filter { it.isActiveOn(selectedDate) }.sortedBy { it.displayOrder }

    Scaffold(
        topBar = { SettingsTopBar(onBack, stringResource(Res.string.nav_plan), showBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (sheetUrl.isBlank()) Res.string.plan_title_set else Res.string.plan_title_sheet),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(if (sheetUrl.isBlank()) Res.string.plan_intro_local else Res.string.plan_intro_sheet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                if (sheetUrl.isNotBlank()) Button(onClick = { uriHandler.openUri(sheetUrl) }) { Text(stringResource(Res.string.plan_open_sheet)) }
                if (sheetUrl.isBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !weeklyMode, onClick = { weeklyMode = false }, label = { Text(stringResource(Res.string.mode_day)) })
                        FilterChip(selected = weeklyMode, onClick = { weeklyMode = true }, label = { Text(stringResource(Res.string.plan_repeat_weekly)) })
                    }
                }
                if (!weeklyMode || sheetUrl.isNotBlank()) {
                    Text(
                        "${selectedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${selectedDate.day} ${MonthKey.from(selectedDate).monthName()} ${selectedDate.year}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = viewModel::previousDay) { Text("‹ ${stringResource(Res.string.date_prev_day)}") }
                        TextButton(onClick = viewModel::nextDay) { Text("${stringResource(Res.string.date_next_day)} ›") }
                    }
                    val shownHabits = if (sheetUrl.isBlank()) {
                        activeHabits
                    } else {
                        activeHabits.filter { habit ->
                            state.dayPlans.any { it.habitId == habit.id && it.date == selectedDate }
                        }
                    }
                    if (shownHabits.isEmpty()) Text(stringResource(if (sheetUrl.isBlank()) Res.string.plan_create_habit_first else Res.string.plan_no_sessions))
                    shownHabits.forEach { habit ->
                        val entries = state.dayPlans.filter { it.habitId == habit.id && it.date == selectedDate }
                        val weekly = state.weeklyPlans.firstOrNull { it.habitId == habit.id && it.weekday == selectedDate.dayOfWeek.ordinal + 1 }
                        val scheduledElsewhere = habit.datedOnly || state.weeklyPlans.any { it.habitId == habit.id }
                        if (entries.isNotEmpty()) {
                            entries.forEach { entry ->
                                PlanRow(
                                    habit.name,
                                    if (entry.skipped) stringResource(Res.string.plan_rest_skipped, entry.detail) else entry.detail,
                                    if (sheetUrl.isBlank()) ({ editor = PlanEditor(habit, planId = entry.id) }) else null,
                                )
                            }
                        } else {
                            val label = when {
                                weekly != null -> stringResource(Res.string.plan_repeats_weekly, weekly.detail)
                                scheduledElsewhere -> stringResource(Res.string.plan_not_today)
                                else -> stringResource(Res.string.plan_every_day)
                            }
                            if (sheetUrl.isBlank()) PlanRow(habit.name, label) { editor = PlanEditor(habit, planId = "${habit.id}|$selectedDate") }
                        }
                        if (sheetUrl.isBlank() && entries.isNotEmpty()) {
                            TextButton(onClick = { editor = PlanEditor(habit) }) { Text(stringResource(Res.string.plan_add_another, habit.name)) }
                        }
                    }
                } else {
                    Text(
                        stringResource(Res.string.plan_weekly_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    if (activeHabits.isEmpty()) Text(stringResource(Res.string.plan_create_habit_first))
                    activeHabits.forEach { habit ->
                        Text(habit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                        (1..7).forEach { weekday ->
                            val plan = state.weeklyPlans.firstOrNull { it.habitId == habit.id && it.weekday == weekday }
                            PlanRow(weekdays[weekday - 1], plan?.detail ?: stringResource(Res.string.plan_rest_none)) { editor = PlanEditor(habit, weekday) }
                        }
                    }
                }
                Spacer(Modifier.padding(bottom = 32.dp))
            }
        }
    }

    if (sheetUrl.isBlank()) {
        editor?.let { selected ->
            val dayPlan = state.dayPlans.firstOrNull { it.id == selected.planId && it.date == selectedDate }
            val recurring = state.weeklyPlans.firstOrNull { it.habitId == selected.habit.id && it.weekday == (selected.weekday ?: selectedDate.dayOfWeek.ordinal + 1) }
            var detail by remember(selected, selectedDate) { mutableStateOf(if (selected.weekday == null) dayPlan?.detail ?: recurring?.detail.orEmpty() else recurring?.detail.orEmpty()) }
            val isWeekly = selected.weekday != null
            AlertDialog(
                onDismissRequest = { editor = null },
                title = { Text("${selected.habit.name} · ${if (isWeekly) weekdays[selected.weekday - 1] else selectedDate}") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = detail,
                            onValueChange = { detail = it },
                            label = { Text(stringResource(Res.string.plan_session_label)) },
                            placeholder = { Text(stringResource(Res.string.plan_session_placeholder)) },
                            singleLine = false,
                            minLines = 2,
                        )
                        if (!isWeekly && detail.isNotBlank()) {
                            TextButton(onClick = {
                                viewModel.saveDayPlan(
                                    selected.habit.id,
                                    selectedDate,
                                    detail,
                                    skipped = true,
                                    id = dayPlan?.id ?: selected.planId ?: DefaultIdGenerator().newId(),
                                )
                                editor = null
                            }) { Text(stringResource(Res.string.plan_skip_session)) }
                        }
                        val canRemove = if (isWeekly) recurring != null else dayPlan != null
                        if (canRemove) {
                            TextButton(onClick = {
                                pendingRemoval = if (isWeekly) {
                                    DestructiveAction.RemoveWeeklySession(selected.habit.name, weekdays[selected.weekday - 1], recurring?.detail.orEmpty()) to
                                        { viewModel.deleteWeeklyPlan(selected.habit.id, selected.weekday) }
                                } else {
                                    DestructiveAction.RemoveDaySession(selected.habit.name, selectedDate, dayPlan?.detail.orEmpty()) to
                                        {
                                            dayPlan?.let { viewModel.deleteDayPlanById(it.id) }
                                            Unit
                                        }
                                }
                            }) { Text(stringResource(if (isWeekly) Res.string.plan_remove_weekly else Res.string.plan_remove_day)) }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (isWeekly) {
                            viewModel.saveWeeklyPlan(selected.habit.id, selected.weekday, detail)
                        } else {
                            viewModel.saveDayPlan(
                                selected.habit.id,
                                selectedDate,
                                detail,
                                id = dayPlan?.id ?: selected.planId ?: DefaultIdGenerator().newId(),
                            )
                        }
                        editor = null
                    }, enabled = detail.isNotBlank()) { Text(stringResource(Res.string.plan_save_session)) }
                },
                dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(Res.string.action_cancel)) } },
            )
        }
    }

    pendingRemoval?.let { (action, perform) ->
        DestructiveConfirmDialog(
            action = action,
            onConfirm = {
                perform()
                pendingRemoval = null
                editor = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun PlanRow(title: String, detail: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Text(
                    stringResource(Res.string.action_edit),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
