package com.habitsheet.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.presentation.asString
import com.habitsheet.presentation.onboarding.OnboardingStep
import com.habitsheet.presentation.onboarding.OnboardingUiState
import com.habitsheet.presentation.onboarding.OnboardingViewModel
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The first-run wizard. Back goes to the previous step; on the first step it offers to skip setup. [onFinished] is
 * called once the wizard is over (Done, Skip, or "start from scratch", which asks for the Habits screen).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: (openHabits: Boolean, planAdded: Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSkip by rememberSaveable { mutableStateOf(false) }

    @Suppress("DEPRECATION") // NavigationEventHandler needs navigationevent-compose, which this app does not ship.
    BackHandler(enabled = true) { if (!viewModel.back()) confirmSkip = true }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished(state.openHabits, state.summary != null)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(state, onStart = viewModel::next, onSkip = { confirmSkip = true })

            OnboardingStep.GOAL -> GoalStep(state, onChoose = viewModel.plan::chooseTemplate, onBack = { viewModel.back() })

            OnboardingStep.QUESTIONS -> QuestionsStep(state, viewModel.plan::updateQuestions, viewModel.plan::buildPlan, onBack = { viewModel.back() })

            OnboardingStep.PREVIEW -> PreviewStep(state, viewModel.plan::setHabitEnabled, viewModel.plan::createPlan, onBack = { viewModel.back() })

            OnboardingStep.REMINDER -> ReminderStep(
                state,
                onTime = viewModel.reminder::setMinute,
                onTurnOn = viewModel.reminder::turnOn,
                onSkip = viewModel.reminder::skip,
                onOpenSettings = viewModel.reminder::openSettings,
            )

            OnboardingStep.SHEET -> SheetStep(state, viewModel.sheet::create, viewModel.sheet::linkExisting, viewModel.sheet::skip)

            OnboardingStep.DONE -> DoneStep(state, onDone = { viewModel.finish() })
        }
    }

    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text(stringResource(Res.string.onboarding_skip_title)) },
            text = { Text(stringResource(Res.string.onboarding_skip_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSkip = false
                    viewModel.finish()
                }) { Text(stringResource(Res.string.onboarding_skip_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text(stringResource(Res.string.onboarding_skip_cancel)) } },
        )
    }
}

/**
 * One step: progress ("Step 3 of 7"), a heading, scrollable content (200 % text and the keyboard never hide it), the
 * message of the step, then its actions. Centred and at most 600 dp wide on tablets.
 */
@Composable
internal fun OnboardingStepScaffold(
    state: OnboardingUiState,
    title: String,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val total = state.steps.size
            val progress = stringResource(Res.string.onboarding_step_of, state.stepNumber, total)
            Text(
                progress,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            LinearProgressIndicator(progress = { state.stepNumber.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            content()
            state.message?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(8.dp))
            actions()
        }
    }
}

/** The main action of a step: full width, at least 48 dp high. */
@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

/** A secondary action: full width, at least 48 dp high. */
@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun WelcomeStep(state: OnboardingUiState, onStart: () -> Unit, onSkip: () -> Unit) {
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_welcome_title),
        actions = {
            PrimaryAction(stringResource(Res.string.onboarding_start), onStart)
            SecondaryAction(stringResource(Res.string.onboarding_skip), onSkip)
        },
    ) {
        Text(stringResource(Res.string.onboarding_welcome_text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DoneStep(state: OnboardingUiState, onDone: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OnboardingStepScaffold(
        state,
        stringResource(Res.string.onboarding_done_title),
        actions = {
            PrimaryAction(stringResource(Res.string.onboarding_done), onDone)
            state.sheetUrl?.let { url -> SecondaryAction(stringResource(Res.string.onboarding_open_sheet), { uriHandler.openUri(url) }) }
        },
    ) {
        val body = MaterialTheme.typography.bodyLarge
        state.summary?.let { summary ->
            Text(stringResource(Res.string.onboarding_done_added, summary.addedHabits, summary.sessions), style = body)
            if (summary.reusedHabits > 0) Text(stringResource(Res.string.onboarding_done_reused, summary.reusedHabits), style = body)
        }
        if (state.reminderOn) Text(stringResource(Res.string.onboarding_done_reminder, clockLabel(state.reminderMinute)), style = body)
        val where = if (state.sheetUrl != null || state.sheetAlreadyLinked) Res.string.onboarding_done_sheet else Res.string.onboarding_done_local
        Text(stringResource(where), style = body)
    }
}

/** "07:30" for a minute of the day. Digits only, like the rest of the app's numbers. */
internal fun clockLabel(minuteOfDay: Int): String {
    val reminder = DailyReminder(minuteOfDay)
    return "${reminder.hour.toString().padStart(2, '0')}:${reminder.minute.toString().padStart(2, '0')}"
}
