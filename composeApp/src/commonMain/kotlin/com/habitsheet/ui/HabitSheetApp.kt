package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.habitsheet.AppGraph
import com.habitsheet.presentation.VersionProvider
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.ThemeMode
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.ui.manage.ManageHabitsScreen
import com.habitsheet.ui.month.MonthScreen
import com.habitsheet.ui.navigation.AppBackStack
import com.habitsheet.ui.navigation.Destination

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HabitSheetApp(
    graph: AppGraph,
    shareService: ShareService,
    backupService: BackupService? = null,
    versionProvider: VersionProvider,
) {
    val backStack = rememberSaveable(saver = AppBackStack.Saver) { AppBackStack() }
    var openCategories by remember { mutableStateOf(false) }
    // The ViewModels belong to the graph (created on first use, cleared with the graph), see AppGraph.
    val factory = remember(graph, backupService) { graph.viewModelProviderFactory(backupService) }
    val monthViewModel = viewModel<MonthViewModel>(viewModelStoreOwner = graph, factory = factory)
    val manageHabitsViewModel = viewModel<ManageHabitsViewModel>(viewModelStoreOwner = graph, factory = factory)
    val settingsViewModel = viewModel<SettingsViewModel>(viewModelStoreOwner = graph, factory = factory)
    val backupViewModel = if (backupService != null) viewModel<BackupViewModel>(viewModelStoreOwner = graph, factory = factory) else null

    // Android system / predictive back and the iOS edge swipe go up one level; on the start screen the platform
    // handles them (leaves the app).
    @Suppress("DEPRECATION") // NavigationEventHandler needs navigationevent-compose, which this app does not ship.
    BackHandler(enabled = backStack.canGoBack) { backStack.back() }

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    HabitSheetTheme(darkTheme = when (themeMode) {
        ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabletLayout = maxWidth >= 720.dp
            AdaptiveFrame(
                tabletLayout = tabletLayout,
                sidebar = { AppSidebar(backStack.current, backStack::navigate) },
            ) {
                AppDestination(
                    destination = backStack.current,
                    tabletLayout = tabletLayout,
                    openCategories = openCategories,
                    onCategoriesOpened = { openCategories = false },
                    requestCategories = { openCategories = true },
                    monthViewModel = monthViewModel,
                    manageHabitsViewModel = manageHabitsViewModel,
                    shareService = shareService,
                    settingsViewModel = settingsViewModel,
                    backupViewModel = backupViewModel,
                    versionProvider = versionProvider,
                    navigate = backStack::navigate,
                    back = { backStack.back() },
                )
            }
        }
    }
}

/** Tablet: sidebar beside the content. Phone: the content alone (each screen brings its own bars). */
@Composable
private fun AdaptiveFrame(tabletLayout: Boolean, sidebar: @Composable () -> Unit, content: @Composable () -> Unit) {
    if (tabletLayout) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            sidebar()
            Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
private fun AppDestination(
    destination: Destination,
    tabletLayout: Boolean,
    openCategories: Boolean,
    onCategoriesOpened: () -> Unit,
    requestCategories: () -> Unit,
    monthViewModel: MonthViewModel,
    manageHabitsViewModel: ManageHabitsViewModel,
    shareService: ShareService,
    settingsViewModel: SettingsViewModel,
    backupViewModel: BackupViewModel?,
    versionProvider: VersionProvider,
    navigate: (Destination) -> Unit,
    back: () -> Unit,
) {
        when (destination) {
            Destination.Tracker -> MonthScreen(
                viewModel = monthViewModel,
                onManage = { navigate(Destination.Manage) },
                onSettings = { navigate(Destination.Settings) },
                onPlan = { navigate(Destination.Plan) },
                shareService = shareService,
                tabletLayout = tabletLayout,
            )
            Destination.Manage -> ManageHabitsScreen(
                viewModel = manageHabitsViewModel,
                onTracker = { navigate(Destination.Tracker) },
                tabletLayout = tabletLayout,
                openCategories = openCategories,
                onCategoriesOpened = onCategoriesOpened,
            )
            Destination.Plan -> {
                val sheetUrl by settingsViewModel.sheetUrl.collectAsStateWithLifecycle()
                PlanScreen(monthViewModel, onBack = back, showBack = !tabletLayout, sheetUrl = sheetUrl)
            }
            Destination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = back,
                onManageCategories = { requestCategories(); navigate(Destination.Manage) },
                onBackup = { if (backupViewModel != null) navigate(Destination.Backup) },
                onAbout = { navigate(Destination.About) },
                showBack = !tabletLayout,
            )
            Destination.Backup -> backupViewModel?.let { DataBackupScreen(it, onBack = back, showBack = !tabletLayout) }
            Destination.About -> AboutScreen(versionProvider, onBack = back, showBack = !tabletLayout)
        }
}

@Composable
private fun AppSidebar(destination: Destination, navigate: (Destination) -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val selected = destination.section
    Column(
        Modifier.width(224.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitIcon(HabitIconGlyph.Tracker, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text("Habit Sheet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(28.dp))
        SidebarDestination("Tracker", HabitIconGlyph.Tracker, selected == Destination.Tracker) { navigate(Destination.Tracker) }
        SidebarDestination("Plan", HabitIconGlyph.Plan, selected == Destination.Plan) { navigate(Destination.Plan) }
        SidebarDestination("Habits", HabitIconGlyph.Manage, selected == Destination.Manage) { navigate(Destination.Manage) }
        SidebarDestination("Settings", HabitIconGlyph.Settings, selected == Destination.Settings) { navigate(Destination.Settings) }
    }
}

@Composable
private fun SidebarDestination(label: String, icon: HabitIconGlyph, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        HabitIcon(icon, Modifier.size(20.dp), color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
