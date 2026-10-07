package com.habitsheet.app

import androidx.lifecycle.ViewModelProvider
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.AppGraph
import com.habitsheet.data.DriverFactory
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.UiText
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** The graph is the ViewModelStoreOwner and the factory: one instance per ViewModel, Backup only with a service. */
class AppGraphViewModelsTest {
    private val tokens = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(null, null)
    }
    private val backup = object : BackupService {
        override val usesClipboard = false
        override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) = Unit
        override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) = Unit
        override fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit) = Unit
    }
    private val graph = AppGraph(
        DriverFactory { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { HabitsDatabase.Schema.create(it) } },
        tokens,
    )

    @AfterTest
    fun close() = graph.close()

    @Test
    fun theGraphAndTheComposablesShareTheSameInstances() {
        val provider = ViewModelProvider.create(graph, graph.viewModelProviderFactory(backup))
        assertSame(graph.monthViewModel, provider[MonthViewModel::class])
        assertSame(graph.settingsViewModel, provider[SettingsViewModel::class])
        assertSame(provider[ManageHabitsViewModel::class], ViewModelProvider.create(graph, graph.viewModelProviderFactory())[ManageHabitsViewModel::class])
    }

    @Test
    fun backupViewModelExistsOnlyWhenThePlatformOffersABackupService() {
        assertFailsWith<IllegalArgumentException> {
            ViewModelProvider.create(graph, graph.viewModelProviderFactory())[BackupViewModel::class]
        }
        ViewModelProvider.create(graph, graph.viewModelProviderFactory(backup))[BackupViewModel::class]
    }
}
