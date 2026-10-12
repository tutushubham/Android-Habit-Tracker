package com.habitsheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsheet.domain.model.SheetLink
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.ThemeMode
import com.habitsheet.presentation.asString
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onShowTutorial: () -> Unit = {},
    showBack: Boolean = true,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val sheetUrl by viewModel.sheetUrl.collectAsStateWithLifecycle()
    val sheetMessage by viewModel.sheetMessage.collectAsStateWithLifecycle()
    val syncState by viewModel.sheetSyncState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.sheetSyncStatus.collectAsStateWithLifecycle()
    var sheetDraft by remember(sheetUrl) { mutableStateOf(sheetUrl) }

    Scaffold(
        topBar = {
            SettingsTopBar(onBack, showBack = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
            ) {
                SettingsSection(stringResource(Res.string.settings_plan_sync)) {
                    val draftInvalid = sheetDraft.isNotBlank() && SheetLink.canonicalize(sheetDraft) == null
                    val uriHandler = LocalUriHandler.current
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(Res.string.settings_sync_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.settings_step_create), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(Res.string.settings_step_copy), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(Res.string.settings_step_paste), style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedTextField(
                            value = sheetDraft,
                            onValueChange = { sheetDraft = it },
                            label = { Text(stringResource(Res.string.settings_sheet_link)) },
                            placeholder = { Text(stringResource(Res.string.settings_sheet_placeholder)) },
                            isError = draftInvalid,
                            supportingText = if (draftInvalid) ({ Text(stringResource(Res.string.settings_link_invalid)) }) else null,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.connect(sheetDraft) },
                                enabled = sheetDraft.isNotBlank() && !draftInvalid && !syncState.busy,
                            ) {
                                Text(stringResource(if (syncState.busy) Res.string.settings_syncing else Res.string.settings_connect_sync))
                            }
                            if (sheetUrl.isNotBlank()) {
                                OutlinedButton(onClick = { uriHandler.openUri(sheetUrl) }) { Text(stringResource(Res.string.plan_open_sheet)) }
                                OutlinedButton(
                                    onClick = viewModel::disconnect,
                                    enabled = !syncState.busy,
                                ) { Text(stringResource(Res.string.settings_disconnect)) }
                            }
                        }
                        sheetMessage?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                        Text(
                            syncStatus.text.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncStatus.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            stringResource(Res.string.settings_sheet_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsSection(stringResource(Res.string.settings_appearance)) {
                    ThemeOption(stringResource(Res.string.theme_system), ThemeMode.System, themeMode) { viewModel.setThemeMode(it) }
                    ThemeOption(stringResource(Res.string.theme_light), ThemeMode.Light, themeMode) { viewModel.setThemeMode(it) }
                    ThemeOption(stringResource(Res.string.theme_dark), ThemeMode.Dark, themeMode) { viewModel.setThemeMode(it) }
                }

                SettingsSection(stringResource(Res.string.settings_management)) {
                    SettingsActionItem(
                        stringResource(Res.string.settings_manage_categories),
                        stringResource(Res.string.settings_manage_categories_desc),
                        onManageCategories,
                    )
                }

                SettingsSection(stringResource(Res.string.settings_data)) {
                    SettingsActionItem(stringResource(Res.string.settings_data_backup), stringResource(Res.string.settings_data_backup_desc), onBackup)
                }

                SettingsSection(stringResource(Res.string.settings_help)) {
                    SettingsActionItem(
                        stringResource(Res.string.settings_show_tutorial),
                        stringResource(Res.string.settings_show_tutorial_desc),
                        onShowTutorial,
                    )
                }

                SettingsSection(stringResource(Res.string.settings_about)) {
                    SettingsActionItem(stringResource(Res.string.settings_about_app), stringResource(Res.string.settings_about_desc), onAbout)
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionLabel(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val selectLabel = stringResource(Res.string.settings_select_theme, label)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { onSelect(mode) },
                onClickLabel = selectLabel,
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(
            selected = current == mode,
            onClick = null,
            modifier = Modifier.semantics { role = Role.RadioButton },
        )
    }
}

@Composable
internal fun SettingsActionItem(title: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val navigateLabel = stringResource(Res.string.settings_navigate)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "›",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = navigateLabel },
        )
    }
}

@Composable
internal fun SettingsTopBar(
    onBack: () -> Unit,
    title: String = stringResource(Res.string.nav_settings),
    showBack: Boolean = true,
    insetTop: Boolean = showBack,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val backLabel = stringResource(Res.string.action_back)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (insetTop) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .heightIn(min = if (showBack) 64.dp else 72.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (showBack) 8.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Text(
                    "‹",
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = backLabel },
                )
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = if (showBack) 8.dp else 0.dp))
    }
}
