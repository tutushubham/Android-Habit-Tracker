package com.habitsheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.presentation.MonthViewModel

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class PlanEditor(val habit: DailyHabit, val weekday: Int? = null, val planId: String? = null)

@Composable
fun PlanScreen(viewModel: MonthViewModel, onBack: () -> Unit, showBack: Boolean = true, sheetUrl: String = "") {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    var weeklyMode by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<PlanEditor?>(null) }
    val selectedDate = state.selectedDay
    val activeHabits = state.allDailyHabits.filter { it.isActiveOn(selectedDate) }.sortedBy { it.displayOrder }

    Scaffold(topBar = { SettingsTopBar(onBack, "Plan", showBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(if (sheetUrl.isBlank()) "Set your sessions" else "Your sessions", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (sheetUrl.isBlank()) "Edit one day or set a weekly repeat. Skipped sessions do not count as due."
                    else "Your Google Sheet controls this plan. Edit Session, Date or Skip there; sync when you return.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                if (sheetUrl.isNotBlank()) Button(onClick = { uriHandler.openUri(sheetUrl) }) { Text("Open Google Sheet") }
                if (sheetUrl.isBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !weeklyMode, onClick = { weeklyMode = false }, label = { Text("Day") })
                    FilterChip(selected = weeklyMode, onClick = { weeklyMode = true }, label = { Text("Repeat weekly") })
                }
                if (!weeklyMode || sheetUrl.isNotBlank()) {
                    Text(
                        "${selectedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${selectedDate.day} ${MonthKey.from(selectedDate).monthName()} ${selectedDate.year}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = viewModel::previousDay) { Text("‹ Previous day") }
                        TextButton(onClick = viewModel::nextDay) { Text("Next day ›") }
                    }
                    val shownHabits = if (sheetUrl.isBlank()) activeHabits else activeHabits.filter { habit ->
                        state.dayPlans.any { it.habitId == habit.id && it.date == selectedDate }
                    }
                    if (shownHabits.isEmpty()) Text(if (sheetUrl.isBlank()) "Create a daily habit first." else "No sessions planned for this day.")
                    shownHabits.forEach { habit ->
                        val entries = state.dayPlans.filter { it.habitId == habit.id && it.date == selectedDate }
                        val weekly = state.weeklyPlans.firstOrNull { it.habitId == habit.id && it.weekday == selectedDate.dayOfWeek.ordinal + 1 }
                        val scheduledElsewhere = habit.datedOnly || state.weeklyPlans.any { it.habitId == habit.id }
                        if (entries.isNotEmpty()) {
                            entries.forEach { entry ->
                                PlanRow(habit.name, entry.detail + if (entry.skipped) " · rest / skipped" else "",
                                    if (sheetUrl.isBlank()) ({ editor = PlanEditor(habit, planId = entry.id) }) else null)
                            }
                        } else {
                            val label = when {
                                weekly != null -> weekly.detail + " · repeats weekly"
                                scheduledElsewhere -> "Not planned today"
                                else -> "Every day until you add a weekly or dated plan"
                            }
                            if (sheetUrl.isBlank()) PlanRow(habit.name, label) { editor = PlanEditor(habit, planId = "${habit.id}|$selectedDate") }
                        }
                        if (sheetUrl.isBlank() && entries.isNotEmpty()) {
                            TextButton(onClick = { editor = PlanEditor(habit) }) { Text("+ Add another ${habit.name} session") }
                        }
                    }
                } else {
                    Text("Tap a weekday to set the session for that habit. Habits with a weekly plan appear only on their planned days.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                    if (activeHabits.isEmpty()) Text("Create a daily habit first.")
                    activeHabits.forEach { habit ->
                        Text(habit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                        (1..7).forEach { weekday ->
                            val plan = state.weeklyPlans.firstOrNull { it.habitId == habit.id && it.weekday == weekday }
                            PlanRow(weekdays[weekday - 1], plan?.detail ?: "Rest / no session") { editor = PlanEditor(habit, weekday) }
                        }
                    }
                }
                Spacer(Modifier.padding(bottom = 32.dp))
            }
        }
    }

    if (sheetUrl.isBlank()) editor?.let { selected ->
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
                        label = { Text("Session or focus") },
                        placeholder = { Text("Easy run · 5 km / Upper · 45 min / DSA graphs") },
                        singleLine = false,
                        minLines = 2,
                    )
                    if (!isWeekly && detail.isNotBlank()) {
                        TextButton(onClick = {
                            viewModel.saveDayPlan(selected.habit.id, selectedDate, detail, skipped = true,
                                id = dayPlan?.id ?: selected.planId ?: DefaultIdGenerator().newId())
                            editor = null
                        }) { Text("Skip this session") }
                    }
                    val canRemove = if (isWeekly) recurring != null else dayPlan != null
                    if (canRemove) TextButton(onClick = {
                        if (isWeekly) viewModel.deleteWeeklyPlan(selected.habit.id, selected.weekday)
                        else dayPlan?.let { viewModel.deleteDayPlanById(it.id) }
                        editor = null
                    }) { Text(if (isWeekly) "Remove weekly session" else "Remove plan for this day") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (isWeekly) viewModel.saveWeeklyPlan(selected.habit.id, selected.weekday, detail)
                    else viewModel.saveDayPlan(selected.habit.id, selectedDate, detail,
                        id = dayPlan?.id ?: selected.planId ?: DefaultIdGenerator().newId())
                    editor = null
                }, enabled = detail.isNotBlank()) { Text("Save session") }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text("Cancel") } },
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
            if (onClick != null) Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
