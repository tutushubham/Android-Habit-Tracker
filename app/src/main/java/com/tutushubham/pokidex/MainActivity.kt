package com.tutushubham.pokidex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tutushubham.pokidex.core.data.repository.AnchorRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.DailyFocusOverrideRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.DomainFocusConfigRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.FocusRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.IntentRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.SessionRepositoryImpl
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.feature_focus.FocusHostWithViewModel
import com.tutushubham.pokidex.feature_today.TodayScreen
import com.tutushubham.pokidex.feature_today.TodayViewModel
import com.tutushubham.pokidex.feature_today.TodayViewModelFactory
import com.tutushubham.pokidex.ui.theme.PokidexTheme

sealed class RootRoute(val route: String) {
    data object Today : RootRoute("today")
    data object Focus : RootRoute("focus/{domain}") {
        const val ARG_DOMAIN = "domain"
        fun create(domain: Domain) = "focus/${domain.name}"
    }
}

class MainActivity : ComponentActivity() {

    private val app by lazy { application as PokidexApp }
    private val database get() = app.database

    private val sessionRepository by lazy { SessionRepositoryImpl(database.sessionDao()) }
    private val intentRepository by lazy { IntentRepositoryImpl(database.intentDao()) }
    private val anchorRepository by lazy { AnchorRepositoryImpl(database.anchorDao()) }
    private val focusRepository by lazy { FocusRepositoryImpl(database.focusDao()) }
    private val domainFocusConfigRepository by lazy {
        DomainFocusConfigRepositoryImpl(database.domainFocusConfigDao())
    }
    private val dailyOverrideRepository by lazy {
        DailyFocusOverrideRepositoryImpl(database.dailyFocusOverrideDao())
    }
    private val focusResolver by lazy {
        FocusResolver(
            focusRepository,
            domainFocusConfigRepository,
            dailyOverrideRepository
        )
    }
    private val todayEngine by lazy {
        TodayEngine(intentRepository, sessionRepository, anchorRepository, focusResolver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PokidexTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = RootRoute.Today.route
                ) {
                    composable(RootRoute.Today.route) {
                        val todayViewModel: TodayViewModel = viewModel(
                            factory = TodayViewModelFactory(
                                todayEngine,
                                sessionRepository,
                                focusRepository,
                                dailyOverrideRepository,
                                focusResolver
                            )
                        )
                        TodayScreen(
                            viewModel = todayViewModel,
                            onNavigateToSession = { /* later */ },
                            onOpenFocusSettings = { domain ->
                                navController.navigate(RootRoute.Focus.create(domain))
                            }
                        )
                    }

                    composable(
                        route = RootRoute.Focus.route,
                        arguments = listOf(
                            navArgument(RootRoute.Focus.ARG_DOMAIN) {
                                type = NavType.StringType
                            }
                        )
                    ) { backStackEntry ->
                        val domain = Domain.valueOf(
                            backStackEntry.arguments!!
                                .getString(RootRoute.Focus.ARG_DOMAIN)!!
                        )
                        FocusHostWithViewModel(
                            domain = domain,
                            focusRepository = focusRepository,
                            configRepository = domainFocusConfigRepository,
                            focusResolver = focusResolver,
                            onExit = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
