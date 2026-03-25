package com.tutushubham.pokidex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tutushubham.pokidex.core.data.repository.AnchorRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.AppStateRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.OnboardingRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.DailyFocusOverrideRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.DomainFocusConfigRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.FocusRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.IntentRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.BehaviorRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.SessionRepositoryImpl
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.feature_focus.FocusHostWithViewModel
import com.tutushubham.pokidex.feature_onboarding.OnboardingHost
import com.tutushubham.pokidex.feature_onboarding.OnboardingViewModel
import com.tutushubham.pokidex.feature_onboarding.OnboardingViewModelFactory
import com.tutushubham.pokidex.feature_today.TodayScreen
import com.tutushubham.pokidex.feature_today.TodayViewModel
import com.tutushubham.pokidex.feature_today.TodayViewModelFactory
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import kotlinx.coroutines.launch

sealed class RootRoute(val route: String) {
    data object Onboarding : RootRoute("onboarding")
    data object Main : RootRoute("main")
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
    private val appStateRepository by lazy { AppStateRepositoryImpl(this) }
    private val focusResolver by lazy {
        FocusResolver(
            focusRepository,
            domainFocusConfigRepository,
            dailyOverrideRepository
        )
    }
    private val behaviorRepository by lazy {
        BehaviorRepositoryImpl(database.userIntentStatsDao(), database.domainBehaviorProfileDao())
    }
    private val todayEngine by lazy { TodayEngine() }
    private val todayPlannerUseCase by lazy {
        TodayPlannerUseCase(
            intentRepository, sessionRepository, anchorRepository,
            focusResolver, behaviorRepository, todayEngine
        )
    }
    private val onboardingRepository by lazy {
        OnboardingRepositoryImpl(
            database = database,
            anchorRepository = anchorRepository,
            focusRepository = focusRepository,
            configRepository = domainFocusConfigRepository,
            intentRepository = intentRepository,
            appStateRepository = appStateRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PokidexTheme {
                val navController = rememberNavController()

                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val completed = this@MainActivity.appStateRepository.isOnboardingCompleted()
                    startDestination =
                        if (completed) RootRoute.Main.route
                        else RootRoute.Onboarding.route
                }

                if (startDestination != null) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        composable(RootRoute.Onboarding.route) {
                            val onboardingViewModel: OnboardingViewModel = viewModel(
                                factory = OnboardingViewModelFactory(
                                    todayEngine = todayEngine,
                                    onboardingRepository = onboardingRepository,
                                    anchorRepository = anchorRepository,
                                    focusRepository = focusRepository,
                                    configRepository = domainFocusConfigRepository,
                                    intentRepository = intentRepository,
                                    appStateRepository = this@MainActivity.appStateRepository
                                )
                            )
                            OnboardingHost(
                                onboardingViewModel = onboardingViewModel,
                                onFinished = {
                                    navController.navigate(RootRoute.Main.route) {
                                        popUpTo(RootRoute.Onboarding.route) {
                                            inclusive = true
                                        }
                                    }
                                }
                            )
                        }

                        composable(RootRoute.Main.route) {
                            MainAppHost(
                                rootNavController = navController,
                                appStateRepository = this@MainActivity.appStateRepository
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MainAppHost(
        rootNavController: NavHostController,
        appStateRepository: AppStateRepository
    ) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "today"
        ) {
            composable("today") {
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(
                        todayPlannerUseCase = todayPlannerUseCase,
                        sessionRepository = sessionRepository,
                        appStateRepository = appStateRepository,
                        focusRepository = focusRepository,
                        dailyOverrideRepository = dailyOverrideRepository,
                        focusResolver = focusResolver
                    )
                )
                TodayScreen(
                    viewModel = todayViewModel,
                    onNavigateToSession = { /* later */ },
                    onOpenFocusSettings = { domain ->
                        navController.navigate("focus/${domain.name}")
                    },
                    onNavigateToOnboarding = {
                        rootNavController.navigate(RootRoute.Onboarding.route) {
                            popUpTo(RootRoute.Main.route) { inclusive = true }
                        }
                    },
                    onNavigateToStructureSettings = { },
                    onNavigateToAddGoal = { }
                )
            }

            composable(
                route = "focus/{domain}",
                arguments = listOf(
                    navArgument("domain") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val domain = Domain.valueOf(
                    backStackEntry.arguments!!
                        .getString("domain")!!
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
