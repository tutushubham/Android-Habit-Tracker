package com.tutushubham.pokidex.feature_onboarding

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class OnboardingRoute(val route: String) {
    data object Welcome : OnboardingRoute("onboarding/welcome")
    data object Goals : OnboardingRoute("onboarding/goals")
    data object DayStructure : OnboardingRoute("onboarding/day_structure")
    data object BlockAssignment : OnboardingRoute("onboarding/block_assignment")
    data object Focuses : OnboardingRoute("onboarding/focuses")
    data object Strategy : OnboardingRoute("onboarding/strategy")
    data object Preview : OnboardingRoute("onboarding/preview")
}

@Composable
fun OnboardingHost(
    onboardingViewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val navController = rememberNavController()
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onboardingViewModel.onEvent(OnboardingContract.Event.ScreenOpened)
    }

    LaunchedEffect(Unit) {
        onboardingViewModel.effect.collect { effect ->
            when (effect) {
                OnboardingContract.Effect.ExitOnboarding ->
                    onFinished()

                is OnboardingContract.Effect.ShowMessage ->
                    Toast.makeText(context, effect.msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = OnboardingRoute.Welcome.route
    ) {

        composable(OnboardingRoute.Welcome.route) {
            WelcomeScreen(
                onNext = { navController.navigate(OnboardingRoute.Goals.route) }
            )
        }

        composable(OnboardingRoute.Goals.route) {
            GoalsScreen(
                state = state,
                onEvent = onboardingViewModel::onEvent,
                onNext = { navController.navigate(OnboardingRoute.DayStructure.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(OnboardingRoute.DayStructure.route) {
            DayStructureScreen(
                state = state,
                onEvent = onboardingViewModel::onEvent,
                onNext = { navController.navigate(OnboardingRoute.BlockAssignment.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(OnboardingRoute.BlockAssignment.route) {
            BlockAssignmentScreen(
                state = state,
                onEvent = onboardingViewModel::onEvent,
                onNext = { navController.navigate(OnboardingRoute.Focuses.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(OnboardingRoute.Focuses.route) {
            FocusSetupScreen(
                state = state,
                onEvent = onboardingViewModel::onEvent,
                onNext = { navController.navigate(OnboardingRoute.Strategy.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(OnboardingRoute.Strategy.route) {
            StrategySetupScreen(
                state = state,
                onEvent = onboardingViewModel::onEvent,
                onNext = {
                    onboardingViewModel.onEvent(OnboardingContract.Event.GeneratePreview)
                    navController.navigate(OnboardingRoute.Preview.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(OnboardingRoute.Preview.route) {
            PreviewScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onFinish = {
                    onboardingViewModel.onEvent(OnboardingContract.Event.FinishClicked)
                }
            )
        }
    }
}
