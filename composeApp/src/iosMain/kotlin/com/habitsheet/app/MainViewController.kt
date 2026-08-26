package com.habitsheet.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.habitsheet.AppGraph
import com.habitsheet.ui.HabitSheetApp

fun MainViewController() = ComposeUIViewController {
    val graph = remember { AppGraph(IosDriverFactory()) }
    DisposableEffect(graph) {
        onDispose(graph::close)
    }
    HabitSheetApp(graph.monthViewModel, graph.manageHabitsViewModel)
}
