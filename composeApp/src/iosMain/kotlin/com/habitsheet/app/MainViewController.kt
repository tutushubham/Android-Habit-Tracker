@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.habitsheet.AppGraph
import com.habitsheet.AppStartup
import com.habitsheet.StartupState
import com.habitsheet.domain.calculation.DailyShareSummary
import com.habitsheet.platform.IosLogger
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.ui.HabitSheetApp
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.ShareService
import com.habitsheet.ui.StartupFailureScreen
import platform.Foundation.NSCalendarDayChangedNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSTimeZone
import platform.Foundation.resetSystemTimeZone
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

// Called from Swift as MainViewControllerKt.MainViewController(tokenProvider:), so the name stays PascalCase.
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(tokenProvider: SheetTokenProvider) = ComposeUIViewController {
    val logger = remember { IosLogger() }
    var attempt by remember { mutableIntStateOf(0) }
    // Opening the database (and migrating it) can fail; then the recovery screen replaces the app.
    val startup = remember(attempt) { AppStartup.create(IosDriverFactory(), tokenProvider, logger) }
    val hostController = LocalUIViewController.current
    when (startup) {
        is StartupState.Failed -> {
            val recovery = remember(hostController) { IosStartupRecovery({ topViewController(hostController) }, logger) }
            HabitSheetTheme { StartupFailureScreen(startup.cause, recovery, onRetry = { attempt++ }) }
        }

        is StartupState.Ready -> ReadyApp(startup.graph, hostController)
    }
}

@Composable
private fun ReadyApp(graph: AppGraph, hostController: UIViewController) {
    LaunchedEffect(graph) { graph.syncOnForeground() }
    val shareService = remember(hostController) {
        object : ShareService {
            override fun shareDailySummary(summary: DailyShareSummary) {
                val text = buildString {
                    append("Habit Sheet — ${summary.date}\n")
                    append("${summary.completedCount}/${summary.totalCount} completed (${(summary.percentage * 100).toInt()}%)\n")
                    summary.doneHabits.forEach { append("✓ ${it.name}\n") }
                    summary.leftHabits.forEach { append("○ ${it.name}\n") }
                }
                val presenter = topViewController(hostController)
                if (presenter != null) {
                    val activity = UIActivityViewController(listOf(text), null)
                    activity.popoverPresentationController?.apply {
                        sourceView = presenter.view
                        sourceRect = presenter.view.bounds
                        permittedArrowDirections = 0uL
                    }
                    presenter.presentViewController(activity, true, null)
                }
            }
        }
    }
    DisposableEffect(graph) {
        val center = NSNotificationCenter.defaultCenter
        val foreground = center.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            NSOperationQueue.mainQueue,
        ) {
            // The system zone is cached by NSTimeZone until reset; a trip may have changed it while we were away.
            NSTimeZone.resetSystemTimeZone()
            graph.refreshToday()
            graph.syncOnForeground()
        }
        val zoneChanged = center.addObserverForName(
            NSSystemTimeZoneDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) {
            NSTimeZone.resetSystemTimeZone()
            graph.refreshToday()
        }
        val dayChanged = center.addObserverForName(
            NSCalendarDayChangedNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { graph.refreshToday() }
        onDispose {
            center.removeObserver(foreground)
            center.removeObserver(zoneChanged)
            center.removeObserver(dayChanged)
            graph.close()
        }
    }
    HabitSheetApp(
        graph,
        shareService,
        IosBackupService(),
        IosVersionProvider(),
    )
}

private fun topViewController(controller: UIViewController?): UIViewController? {
    var current = controller ?: return null
    while (true) {
        current = current.presentedViewController ?: break
    }
    return current
}
