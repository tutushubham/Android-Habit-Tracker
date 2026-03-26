package com.tutushubham.pokidex.feature_focus

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed class FocusRoute(val route: String) {
    data object Overview : FocusRoute("focus/overview")
    data object List : FocusRoute("focus/list")
    data object Strategy : FocusRoute("focus/strategy")
    data object Confirm : FocusRoute("focus/confirm")
}

@Composable
fun FocusHost(
    domain: Domain,
    focusViewModel: FocusViewModel,
    onExit: () -> Unit
) {
    val navController = rememberNavController()
    val state by focusViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(true) {
        focusViewModel.onEvent(FocusEvent.ScreenOpened)
        focusViewModel.effect.collect { effect ->
            when (effect) {
                FocusEffect.NavigateToFocusList ->
                    navController.navigate(FocusRoute.List.route)

                FocusEffect.NavigateToStrategy ->
                    navController.navigate(FocusRoute.Strategy.route)

                FocusEffect.NavigateToConfirm ->
                    navController.navigate(FocusRoute.Confirm.route)

                FocusEffect.Exit ->
                    onExit()

                is FocusEffect.ShowMessage ->
                    Toast.makeText(context, effect.msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = FocusRoute.Overview.route
        ) {

        composable(FocusRoute.Overview.route) {
            FocusOverviewScreen(
                state = state,
                onEdit = {
                    focusViewModel.onEvent(FocusEvent.EditFocusClicked)
                }
            )
        }

        composable(FocusRoute.List.route) {
            FocusListScreen(
                focuses = state.focuses,
                onAddFocus = { name, deadline ->
                    focusViewModel.onEvent(FocusEvent.AddFocus(name, deadline))
                },
                onDeleteFocus = { id ->
                    focusViewModel.onEvent(FocusEvent.DeleteFocus(id))
                },
                onUpdateFocusName = { id, newName ->
                    focusViewModel.onEvent(FocusEvent.UpdateFocusName(id, newName))
                },
                onNext = {
                    focusViewModel.onEvent(FocusEvent.ListNextClicked)
                }
            )
        }

        composable(FocusRoute.Strategy.route) {
            FocusStrategyScreen(
                focuses = state.focuses,
                selected = state.strategy,
                onStrategySelected = {
                    focusViewModel.onEvent(FocusEvent.StrategySelected(it))
                },
                onWeightsUpdated = { weights ->
                    focusViewModel.onEvent(FocusEvent.WeightsUpdated(weights))
                },
                onNext = {
                    focusViewModel.onEvent(FocusEvent.StrategyNextClicked)
                }
            )
        }

        composable(FocusRoute.Confirm.route) {
            FocusConfirmScreen(
                domain = state.domain,
                strategy = state.strategy,
                preview = state.preview,
                currentFocusTitle = state.currentFocusTitle,
                onConfirm = {
                    focusViewModel.onEvent(FocusEvent.ConfirmClicked)
                },
                onBack = { navController.popBackStack() }
            )
        }
        }
    }
}

@Composable
fun FocusHostWithViewModel(
    domain: Domain,
    focusRepository: FocusRepository,
    configRepository: DomainFocusConfigRepository,
    focusResolver: FocusResolver,
    onExit: () -> Unit
) {
    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModelFactory(
            domain = domain,
            focusRepository = focusRepository,
            configRepository = configRepository,
            focusResolver = focusResolver
        )
    )
    FocusHost(
        domain = domain,
        focusViewModel = focusViewModel,
        onExit = onExit
    )
}
