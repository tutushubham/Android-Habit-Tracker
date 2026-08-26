package com.habitsheet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.habitsheet.AppGraph
import com.habitsheet.ui.HabitSheetApp

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = AppGraph(AndroidDriverFactory(applicationContext))
        setContent {
            HabitSheetApp(graph.monthViewModel, graph.manageHabitsViewModel)
        }
    }

    override fun onDestroy() {
        graph.close()
        super.onDestroy()
    }
}
