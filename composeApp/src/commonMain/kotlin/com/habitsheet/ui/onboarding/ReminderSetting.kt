package com.habitsheet.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.asString
import com.habitsheet.presentation.onboarding.OnboardingUiState
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

/** The daily reminder in Settings: its time (or off), turn on / change time / turn off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSetting(viewModel: SettingsViewModel) {
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val message by viewModel.reminderMessage.collectAsStateWithLifecycle()
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        val current = reminder
        val status = if (current == null) {
            stringResource(Res.string.settings_reminder_off)
        } else {
            stringResource(Res.string.settings_reminder_at, clockLabel(current.minuteOfDay))
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current == null) {
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.settings_reminder_turn_on))
                }
            } else {
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.settings_reminder_change))
                }
                OutlinedButton(onClick = { viewModel.setReminder(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.settings_reminder_turn_off))
                }
            }
        }
        message?.let {
            Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = viewModel::openNotificationSettings, modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.onboarding_open_settings))
            }
        }
    }
    if (pickTime) {
        ReminderTimePicker(
            reminder?.minuteOfDay ?: OnboardingUiState.DEFAULT_REMINDER_MINUTE,
            onPicked = { viewModel.setReminder(it) },
            onDismiss = { pickTime = false },
        )
    }
}
