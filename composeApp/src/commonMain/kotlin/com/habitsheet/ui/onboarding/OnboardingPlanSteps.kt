package com.habitsheet.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.plan.PlanLevel
import com.habitsheet.domain.plan.PlanQuestions
import com.habitsheet.domain.plan.PlanTemplateId
import com.habitsheet.domain.plan.TemplatePlanSource
import com.habitsheet.presentation.asString
import com.habitsheet.presentation.onboarding.OnboardingUiState
import com.habitsheet.presentation.onboarding.description
import com.habitsheet.presentation.onboarding.title
import com.habitsheet.presentation.onboarding.toUiText
import com.habitsheet.resources.*
import com.habitsheet.ui.fullDateLabel
import com.habitsheet.ui.weekdayName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GoalStep(state: OnboardingUiState, onChoose: (PlanTemplateId) -> Unit, onBack: () -> Unit) {
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_goal_title),
        actions = { SecondaryAction(stringResource(Res.string.onboarding_back), onBack) },
    ) {
        PlanTemplateId.entries.forEach { template ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onChoose(template) }
                    .padding(16.dp),
            ) {
                Text(template.title().asString(), style = MaterialTheme.typography.titleMedium)
                Text(template.description().asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionsStep(state: OnboardingUiState, onChange: ((PlanQuestions) -> PlanQuestions) -> Unit, onBuild: () -> Unit, onBack: () -> Unit) {
    val questions = state.questions ?: return
    val template = state.template ?: return
    var pickDate by rememberSaveable { mutableStateOf(false) }
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_questions_title),
        actions = {
            PrimaryAction(stringResource(Res.string.onboarding_build), onBuild, enabled = !state.busy)
            SecondaryAction(stringResource(Res.string.onboarding_back), onBack)
        },
    ) {
        val eventRule = TemplatePlanSource.eventDate(template)
        if (eventRule != TemplatePlanSource.EventDate.NOT_ASKED) {
            val eventLabel = if (eventRule == TemplatePlanSource.EventDate.REQUIRED) Res.string.onboarding_event_race else Res.string.onboarding_event_interview
            QuestionLabel(stringResource(eventLabel))
            Text(questions.eventDate?.fullDateLabel() ?: stringResource(Res.string.onboarding_event_none), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.onboarding_event_pick))
                }
                if (eventRule == TemplatePlanSource.EventDate.OPTIONAL && questions.eventDate != null) {
                    OutlinedButton(onClick = { onChange { it.copy(eventDate = null) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.onboarding_event_clear))
                    }
                }
            }
        }

        QuestionLabel(stringResource(Res.string.onboarding_level))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanLevel.entries.forEach { level ->
                FilterChip(
                    selected = questions.level == level,
                    onClick = { onChange { it.copy(level = level) } },
                    label = { Text(stringResource(level.label())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        QuestionLabel(stringResource(Res.string.onboarding_days_per_week))
        DaysPerWeek(questions.daysPerWeek) { days -> onChange { it.copy(daysPerWeek = days) } }

        QuestionLabel(stringResource(Res.string.onboarding_rest_day))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = questions.restDay == null,
                onClick = { onChange { it.copy(restDay = null) } },
                label = { Text(stringResource(Res.string.onboarding_rest_none)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = questions.restDay == day,
                    onClick = { onChange { it.copy(restDay = day) } },
                    label = { Text(day.sampleDate().weekdayName()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    if (pickDate) {
        EventDatePicker(
            initial = questions.eventDate,
            from = questions.startDate,
            onPicked = { date -> onChange { it.copy(eventDate = date) } },
            onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun QuestionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DaysPerWeek(days: Int, onChange: (Int) -> Unit) {
    val fewer = stringResource(Res.string.onboarding_days_fewer)
    val more = stringResource(Res.string.onboarding_days_more)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = { onChange(days - 1) },
            enabled = days > PlanQuestions.MIN_DAYS_PER_WEEK,
            modifier = Modifier.semantics { contentDescription = fewer },
        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        Text(pluralStringResource(Res.plurals.onboarding_days_value, days, days), style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = { onChange(days + 1) },
            enabled = days < PlanQuestions.MAX_DAYS_PER_WEEK,
            modifier = Modifier.semantics { contentDescription = more },
        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
internal fun PreviewStep(state: OnboardingUiState, onToggle: (String, Boolean) -> Unit, onCreate: () -> Unit, onBack: () -> Unit) {
    val draft = state.draft ?: return
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_preview_title),
        actions = {
            PrimaryAction(stringResource(Res.string.onboarding_create), onCreate, enabled = !state.busy && draft.enabledHabits.isNotEmpty())
            SecondaryAction(stringResource(Res.string.onboarding_back), onBack, enabled = !state.busy)
        },
    ) {
        Text(stringResource(Res.string.onboarding_preview_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.existingHabits > 0) {
            Text(stringResource(Res.string.onboarding_preview_adds, state.existingHabits), style = MaterialTheme.typography.bodyMedium)
        }
        draft.habits.forEach { habit ->
            val sessions = draft.sessionsOf(habit.key)
            val name = habit.label.toUiText().asString()
            val detail = if (habit.datedOnly) {
                val first = sessions.firstOrNull()?.label?.toUiText()?.asString().orEmpty()
                pluralStringResource(Res.plurals.onboarding_preview_sessions, sessions.size, sessions.size, first)
            } else {
                stringResource(Res.string.onboarding_preview_every_day)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = habit.enabled, role = Role.Checkbox, onValueChange = { onToggle(habit.key, it) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = habit.enabled, onCheckedChange = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun PlanLevel.label() = when (this) {
    PlanLevel.NEW -> Res.string.onboarding_level_new
    PlanLevel.REGULAR -> Res.string.onboarding_level_regular
    PlanLevel.EXPERIENCED -> Res.string.onboarding_level_experienced
}

/** A Sunday: the day before a Monday, to turn a weekday into a date for the catalog's weekday names. */
private val sundayBeforeAMonday = LocalDate(2026, 10, 4)

/** Any date with this weekday. */
private fun DayOfWeek.sampleDate(): LocalDate = sundayBeforeAMonday.plus(DatePeriod(days = isoDayNumber))
