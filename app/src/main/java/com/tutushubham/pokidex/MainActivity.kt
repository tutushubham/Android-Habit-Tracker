package com.tutushubham.pokidex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tutushubham.pokidex.core.data.repository.AnchorRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.DomainFocusConfigRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.FocusRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.IntentRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.SessionRepositoryImpl
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.feature_today.TodayScreen
import com.tutushubham.pokidex.feature_today.TodayViewModel
import com.tutushubham.pokidex.feature_today.TodayViewModelFactory
import com.tutushubham.pokidex.ui.theme.PokidexTheme

class MainActivity : ComponentActivity() {
    
    private val app by lazy { application as PokidexApp }
    private val database get() = app.database
    
    private val sessionRepository by lazy {
        SessionRepositoryImpl(database.sessionDao())
    }
    
    private val intentRepository by lazy {
        IntentRepositoryImpl(database.intentDao())
    }
    
    private val anchorRepository by lazy {
        AnchorRepositoryImpl(database.anchorDao())
    }
    
    private val focusRepository by lazy {
        FocusRepositoryImpl(database.focusDao())
    }
    
    private val domainFocusConfigRepository by lazy {
        DomainFocusConfigRepositoryImpl(database.domainFocusConfigDao())
    }
    
    private val focusResolver by lazy {
        FocusResolver(focusRepository, domainFocusConfigRepository)
    }
    
    private val todayEngine by lazy {
        TodayEngine(intentRepository, sessionRepository, anchorRepository, focusResolver)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            PokidexTheme {
                val viewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(todayEngine, sessionRepository)
                )
                
                TodayScreen(
                    viewModel = viewModel,
                    onNavigateToSession = { sessionId ->
                        // TODO: Implement navigation to session details
                    }
                )
            }
        }
    }
}
