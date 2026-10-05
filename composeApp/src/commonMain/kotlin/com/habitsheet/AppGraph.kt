package com.habitsheet

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.sync.SerializingTokenProvider
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.sync.SyncError
import com.habitsheet.sync.SyncScheduler
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AppGraph(
    driverFactory: DriverFactory,
    tokenProvider: SheetTokenProvider,
    val logger: Logger = NoOpLogger,
) : ViewModelStoreOwner {
    /**
     * The ViewModels live exactly as long as this graph: they are created on first use (see [viewModelProviderFactory]) and
     * cleared in [close]. They are not retained across an Android Activity re-creation because the graph itself is
     * Activity-scoped (the token provider is bound to the Activity).
     */
    override val viewModelStore = ViewModelStore()

    private val serializedTokens = SerializingTokenProvider(tokenProvider, logger = logger)
    val repository = LocalHabitRepository(driverFactory)
    val sheetSync = SheetSync(repository, repository, serializedTokens, logger = logger)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // "Will sync when online": while offline with check-offs still waiting, retry on its own (30 s, doubling to 5 min).
    private val syncScheduler = SyncScheduler(
        syncScope,
        logger = logger,
        shouldRetry = { sheetSync.state.value.error is SyncError.Offline && repository.snapshot.value.pendingCompletions.isNotEmpty() },
    ) { interactive -> sheetSync.sync(interactive) }

    /**
     * Creates the ViewModels. [BackupViewModel] needs the platform's [BackupService], so it is only offered when
     * one is passed. Use with `viewModel(viewModelStoreOwner = graph, factory = ...)`.
     */
    fun viewModelProviderFactory(backupService: BackupService? = null): ViewModelProvider.Factory = viewModelFactory {
        initializer { MonthViewModel(repository, repository, onLocalChange = syncScheduler::markDirty, logger = logger) }
        initializer { ManageHabitsViewModel(repository, DefaultIdGenerator(), logger = logger) }
        initializer { SettingsViewModel(repository, sheetSync = sheetSync, logger = logger) }
        if (backupService != null) {
            initializer {
                BackupViewModel(
                    repository = repository,
                    backupService = backupService,
                    logger = logger,
                    onDataReplaced = { settingsViewModel.reloadFromStorage() },
                )
            }
        }
    }

    private val defaultFactory = viewModelProviderFactory()

    val monthViewModel: MonthViewModel get() = ViewModelProvider.create(this, defaultFactory)[MonthViewModel::class]
    val settingsViewModel: SettingsViewModel get() = ViewModelProvider.create(this, defaultFactory)[SettingsViewModel::class]

    fun syncOnForeground() = syncScheduler.syncNow()

    /** The app came to the foreground, or the system date / time zone changed: re-evaluate "today". */
    fun refreshToday() = monthViewModel.refreshToday()

    fun close() {
        viewModelStore.clear()
        syncScope.cancel()
        sheetSync.close()
        serializedTokens.close()
        repository.close()
    }
}
