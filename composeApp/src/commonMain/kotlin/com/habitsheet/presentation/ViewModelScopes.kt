package com.habitsheet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The scope the ViewModels launch their work in: it shares the Job of [viewModelScope] (so it is cancelled in
 * `onCleared`) but keeps running on [Dispatchers.Default] as before, because repository calls are plain suspend
 * functions that must not run on the main thread. Tests pass their own scope instead.
 */
internal fun ViewModel.backgroundScope(): CoroutineScope =
    CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Default)
