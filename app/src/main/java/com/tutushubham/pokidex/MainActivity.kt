package com.tutushubham.pokidex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tutushubham.pokidex.core.data.repository.CaptureRepositoryImpl
import com.tutushubham.pokidex.core.data.repository.SessionRepositoryImpl
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.FatigueLevel
import com.tutushubham.pokidex.core.engine.FatigueSignal
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.MomentumSignal
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import com.tutushubham.pokidex.feature_capture.CaptureScreen
import com.tutushubham.pokidex.feature_capture.CaptureViewModel
import com.tutushubham.pokidex.feature_capture.CaptureViewModelFactory
import com.tutushubham.pokidex.feature_focus.DayFocusPreview
import com.tutushubham.pokidex.feature_focus.DayStructureEditorScreen
import com.tutushubham.pokidex.feature_focus.FocusEvent
import com.tutushubham.pokidex.feature_focus.FocusHostWithViewModel
import com.tutushubham.pokidex.feature_focus.FocusPreviewScreen
import com.tutushubham.pokidex.feature_focus.FocusViewModel
import com.tutushubham.pokidex.feature_focus.FocusViewModelFactory
import com.tutushubham.pokidex.feature_onboarding.OnboardingHost
import com.tutushubham.pokidex.feature_onboarding.OnboardingViewModel
import com.tutushubham.pokidex.feature_onboarding.OnboardingViewModelFactory
import com.tutushubham.pokidex.feature_goal_detail.GoalDetailScreen
import com.tutushubham.pokidex.feature_goal_detail.GoalDetailViewModel
import com.tutushubham.pokidex.feature_goal_detail.GoalDetailViewModelFactory
import com.tutushubham.pokidex.feature_insights.FatigueDetailScreen
import com.tutushubham.pokidex.feature_insights.InsightsScreen
import com.tutushubham.pokidex.feature_insights.InsightsViewModel
import com.tutushubham.pokidex.feature_insights.InsightsViewModelFactory
import com.tutushubham.pokidex.feature_insights.MomentumDetailScreen
import com.tutushubham.pokidex.feature_recommendation.RecommendationScreen
import com.tutushubham.pokidex.feature_recommendation.RecommendationViewModelFactory
import com.tutushubham.pokidex.feature_settings.SettingsRepository
import com.tutushubham.pokidex.feature_settings.ThemeMode
import com.tutushubham.pokidex.feature_settings.SettingsScreen
import com.tutushubham.pokidex.feature_settings.SettingsViewModel
import com.tutushubham.pokidex.feature_settings.SettingsViewModelFactory
import com.tutushubham.pokidex.feature_today.ActiveSessionScreen
import com.tutushubham.pokidex.feature_today.TodayScreen
import com.tutushubham.pokidex.feature_today.TodayViewModel
import com.tutushubham.pokidex.feature_today.TodayViewModelFactory
import com.tutushubham.pokidex.ui.components.AppScaffold
import com.tutushubham.pokidex.ui.components.BottomTab
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.LocalDate
import java.util.UUID
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
    private val captureRepository by lazy { CaptureRepositoryImpl(database.captureDao()) }
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
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val todayEngine by lazy { TodayEngine() }
    private val behaviorProfileUseCase by lazy {
        BehaviorProfileUseCase(sessionRepository, intentRepository, behaviorRepository)
    }
    private val todayPlannerUseCase by lazy {
        TodayPlannerUseCase(
            intentRepository, sessionRepository, anchorRepository,
            focusResolver, behaviorRepository, todayEngine,
            behaviorProfileUseCase
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
            val themeMode by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.tutushubham.pokidex.feature_settings.SystemSettings()
            )
            PokidexTheme(themeMode = themeMode.themeMode) {
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

        val todayViewModel: TodayViewModel = viewModel(
            factory = TodayViewModelFactory(
                todayPlannerUseCase = todayPlannerUseCase,
                sessionRepository = sessionRepository,
                appStateRepository = appStateRepository,
                focusRepository = focusRepository,
                dailyOverrideRepository = dailyOverrideRepository,
                focusResolver = focusResolver,
                settingsRepository = settingsRepository,
                behaviorProfileUseCase = behaviorProfileUseCase
            )
        )

        val currentBackStackEntry by navController.currentBackStackEntryFlow
            .collectAsStateWithLifecycle(initialValue = null)
        val currentRoute = currentBackStackEntry?.destination?.route ?: "today"
        val showBottomBar = currentRoute in BottomTab.entries.map { it.route }

        if (showBottomBar) {
            AppScaffold(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("today") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            ) { innerPadding ->
                MainNavHost(navController, todayViewModel, rootNavController, innerPadding)
            }
        } else {
            MainNavHost(navController, todayViewModel, rootNavController, null)
        }
    }

    @Composable
    private fun MainNavHost(
        navController: NavHostController,
        todayViewModel: TodayViewModel,
        rootNavController: NavHostController,
        innerPadding: androidx.compose.foundation.layout.PaddingValues?
    ) {
        val modifier = if (innerPadding != null) {
            Modifier.padding(innerPadding)
        } else Modifier

        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = modifier
        ) {
            composable("today") {
                TodayScreen(
                    viewModel = todayViewModel,
                    onNavigateToSession = { sessionId ->
                        navController.navigate("session/$sessionId")
                    },
                    onNavigateToInsights = {
                        navController.navigate("insights") {
                            popUpTo("today") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToGoalDetail = { intentId ->
                        navController.navigate("goal/$intentId")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            popUpTo("today") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
                route = "session/{sessionId}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType }
                )
            ) {
                val state by todayViewModel.state.collectAsStateWithLifecycle()
                ActiveSessionScreen(
                    state = state,
                    onEvent = todayViewModel::onEvent,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "goal/{intentId}",
                arguments = listOf(
                    navArgument("intentId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val intentId = backStackEntry.arguments!!.getString("intentId")!!
                val todayState by todayViewModel.state.collectAsStateWithLifecycle()
                val goalDetailViewModel: GoalDetailViewModel = viewModel(
                    key = intentId,
                    factory = GoalDetailViewModelFactory(
                        intentId = intentId,
                        progressList = todayState.progressList,
                        sessionRepository = sessionRepository,
                        intentRepository = intentRepository,
                        behaviorRepository = behaviorRepository
                    )
                )
                GoalDetailScreen(
                    viewModel = goalDetailViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToRecommendation = {
                        navController.navigate("recommendation/$intentId")
                    }
                )
            }

            composable("settings") {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModelFactory(
                        settingsRepository = settingsRepository,
                        behaviorRepository = behaviorRepository
                    )
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("insights") {
                val insightsViewModel: InsightsViewModel = viewModel(
                    factory = InsightsViewModelFactory(
                        sessionRepository = sessionRepository,
                        intentRepository = intentRepository,
                        behaviorProfileUseCase = behaviorProfileUseCase
                    )
                )
                InsightsScreen(
                    viewModel = insightsViewModel,
                    onNavigateToFatigue = { navController.navigate("insights/fatigue") },
                    onNavigateToMomentum = { navController.navigate("insights/momentum") }
                )
            }

            composable("insights/fatigue") {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry("insights")
                }
                val insightsViewModel: InsightsViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = InsightsViewModelFactory(
                        sessionRepository = sessionRepository,
                        intentRepository = intentRepository,
                        behaviorProfileUseCase = behaviorProfileUseCase
                    )
                )
                val state by insightsViewModel.state.collectAsStateWithLifecycle()
                FatigueDetailScreen(
                    profile = aggregateBehaviorProfiles(state.profiles),
                    onBack = { navController.popBackStack() },
                    onAdaptStrategy = {
                        navController.popBackStack()
                        navController.navigate("settings")
                    }
                )
            }

            composable("insights/momentum") {
                val parentEntry = remember(navController) {
                    navController.getBackStackEntry("insights")
                }
                val insightsViewModel: InsightsViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = InsightsViewModelFactory(
                        sessionRepository = sessionRepository,
                        intentRepository = intentRepository,
                        behaviorProfileUseCase = behaviorProfileUseCase
                    )
                )
                val state by insightsViewModel.state.collectAsStateWithLifecycle()
                MomentumDetailScreen(
                    profile = aggregateBehaviorProfiles(state.profiles),
                    onBack = { navController.popBackStack() },
                    onKeepStreak = {
                        navController.popBackStack("today", inclusive = false)
                    }
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

            composable("focus_home") {
                FocusHomeLanding(
                    onDomainSelected = { domain ->
                        navController.navigate("focus/${domain.name}")
                    }
                )
            }

            composable("inbox") {
                val captureViewModel: CaptureViewModel = viewModel(
                    factory = CaptureViewModelFactory(
                        captureRepository = captureRepository,
                        intentRepository = intentRepository
                    )
                )
                CaptureScreen(
                    viewModel = captureViewModel,
                    onNavigateToGoalEdit = { intentId ->
                        navController.navigate("goal/$intentId")
                    }
                )
            }
        }
    }
}

@Composable
private fun FocusHomeLanding(
    onDomainSelected: (Domain) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text(
            text = "Focus Areas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select a domain to manage your focus targets and strategy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Domain.entries.forEach { domain ->
            val emoji = when (domain) {
                Domain.STUDIES -> "📚"
                Domain.FITNESS -> "💪"
                Domain.WORK -> "💼"
                Domain.HOBBY -> "🎯"
            }
            Surface(
                onClick = { onDomainSelected(domain) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    Text(text = emoji, fontSize = 28.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = domain.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = domainSubtitle(domain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun domainSubtitle(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "Algorithms, concepts & deep learning"
    Domain.FITNESS -> "Physical training & wellness habits"
    Domain.WORK -> "Professional workflow & milestones"
    Domain.HOBBY -> "Creative pursuits & exploration"
}

private fun aggregateBehaviorProfiles(profiles: Map<String, UserBehaviorProfile>): UserBehaviorProfile {
    val values = profiles.values.toList()
    if (values.isEmpty()) {
        return UserBehaviorProfile(
            intentId = "aggregate",
            fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
            momentum = MomentumSignal(0, 0.0, false),
            learnedEstimate = null,
            consistencyScore = 0.5,
            skipRate = 0.0,
            completionRate = 0.0,
            peakFocusHours = emptyList(),
            velocityTrend = TrendDirection.FLAT,
            durationTrend = TrendDirection.FLAT,
            weeklyMinutesByDay = emptyMap()
        )
    }
    val avgSkip = values.map { it.skipRate }.average()
    val maxSkipStreak = values.maxOfOrNull { it.fatigue.skipStreak } ?: 0
    val fatigueLevel = when {
        avgSkip > 0.35 -> FatigueLevel.HIGH
        avgSkip > 0.15 -> FatigueLevel.MEDIUM
        else -> FatigueLevel.LOW
    }
    val fatigue = FatigueSignal(fatigueLevel, maxSkipStreak, avgSkip)

    val maxStreak = values.maxOfOrNull { it.momentum.streakDays } ?: 0
    val avgRecentCompletion = values.map { it.momentum.recentCompletionRate }.average()
    val consistentCount = values.count { it.momentum.isConsistent }
    val momentum = MomentumSignal(
        streakDays = maxStreak,
        recentCompletionRate = avgRecentCompletion,
        isConsistent = consistentCount >= (values.size + 1) / 2
    )

    val mergedWeekly = mutableMapOf<java.time.DayOfWeek, Int>()
    for (p in values) {
        for ((d, m) in p.weeklyMinutesByDay) {
            mergedWeekly[d] = (mergedWeekly[d] ?: 0) + m
        }
    }

    val velocityTrend = values.groupingBy { it.velocityTrend }.eachCount().maxByOrNull { it.value }?.key
        ?: TrendDirection.FLAT
    val durationTrend = values.groupingBy { it.durationTrend }.eachCount().maxByOrNull { it.value }?.key
        ?: TrendDirection.FLAT

    return UserBehaviorProfile(
        intentId = "aggregate",
        fatigue = fatigue,
        momentum = momentum,
        learnedEstimate = null,
        consistencyScore = values.map { it.consistencyScore }.average(),
        skipRate = avgSkip,
        completionRate = values.map { it.completionRate }.average(),
        peakFocusHours = values.flatMap { it.peakFocusHours }.distinct().sorted(),
        velocityTrend = velocityTrend,
        durationTrend = durationTrend,
        weeklyMinutesByDay = mergedWeekly
    )
}
