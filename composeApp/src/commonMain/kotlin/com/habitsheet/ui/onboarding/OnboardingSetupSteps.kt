package com.habitsheet.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.model.SheetLink
import com.habitsheet.domain.plan.PLAN_HORIZON_DAYS
import com.habitsheet.presentation.onboarding.OnboardingUiState
import com.habitsheet.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReminderStep(
    state: OnboardingUiState,
    onTime: (Int) -> Unit,
    onTurnOn: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pickTime by rememberSaveable { mutableStateOf(false) }
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_reminder_title),
        actions = {
            if (state.reminderDenied) {
                PrimaryAction(stringResource(Res.string.onboarding_open_settings), onOpenSettings)
            } else {
                PrimaryAction(stringResource(Res.string.onboarding_reminder_on), onTurnOn, enabled = !state.busy)
            }
            SecondaryAction(stringResource(Res.string.onboarding_reminder_skip), onSkip, enabled = !state.busy)
        },
    ) {
        Text(stringResource(Res.string.onboarding_reminder_text), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.onboarding_reminder_time, clockLabel(state.reminderMinute)), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.onboarding_reminder_change))
        }
    }
    if (pickTime) {
        ReminderTimePicker(state.reminderMinute, onPicked = onTime, onDismiss = { pickTime = false })
    }
}

@Composable
internal fun SheetStep(state: OnboardingUiState, onCreate: () -> Unit, onLink: (String) -> Unit, onSkip: () -> Unit) {
    var ownSheet by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_sheet_title),
        actions = {
            if (ownSheet) {
                val valid = SheetLink.canonicalize(link) != null
                PrimaryAction(stringResource(Res.string.onboarding_sheet_connect), { onLink(link) }, enabled = valid && !state.busy)
            } else {
                PrimaryAction(stringResource(Res.string.onboarding_sheet_create), onCreate, enabled = !state.busy)
                SecondaryAction(stringResource(Res.string.onboarding_sheet_existing), { ownSheet = true }, enabled = !state.busy)
            }
            SecondaryAction(stringResource(Res.string.onboarding_sheet_skip), onSkip, enabled = !state.busy)
        },
    ) {
        Text(stringResource(Res.string.onboarding_sheet_text), style = MaterialTheme.typography.bodyLarge)
        if (ownSheet) {
            val invalid = link.isNotBlank() && SheetLink.canonicalize(link) == null
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(Res.string.settings_sheet_link)) },
                placeholder = { Text(stringResource(Res.string.settings_sheet_placeholder)) },
                isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(Res.string.settings_link_invalid)) }) else null,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
            Text(stringResource(Res.string.onboarding_sheet_existing_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimePicker(minuteOfDay: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val time = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.onboarding_time_title)) },
        text = { TimeInput(time) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(time.hour * 60 + time.minute)
                onDismiss()
            }) { Text(stringResource(Res.string.onboarding_date_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.onboarding_date_cancel)) } },
    )
}

/** Picks the event day: from [from] (the start) up to the plan horizon. Dates are whole days in UTC in the picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDatePicker(initial: LocalDate?, from: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val first = from.toEpochDays()
    val last = first + PLAN_HORIZON_DAYS
    val selectable = remember(first) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis / MILLIS_PER_DAY in first..last
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDays() * MILLIS_PER_DAY }, selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(LocalDate.fromEpochDays(it / MILLIS_PER_DAY)) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.onboarding_date_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.onboarding_date_cancel)) } },
    ) { DatePicker(state) }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
