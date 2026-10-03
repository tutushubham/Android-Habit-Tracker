@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.habitsheet.AppGraph
import com.habitsheet.platform.IosLogger
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.ui.HabitSheetApp
import com.habitsheet.ui.ShareService
import com.habitsheet.domain.calculation.DailyShareSummary
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

fun MainViewController(tokenProvider: SheetTokenProvider) = ComposeUIViewController {
    val graph = remember { AppGraph(IosDriverFactory(), tokenProvider, IosLogger()) }
    LaunchedEffect(graph) { graph.syncOnForeground() }
    val hostController = LocalUIViewController.current
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
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { graph.syncOnForeground() }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            graph.close()
        }
    }
    HabitSheetApp(
        graph.monthViewModel,
        graph.manageHabitsViewModel,
        shareService,
        graph.settingsViewModel,
        IosBackupService(),
        graph.repository,
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
