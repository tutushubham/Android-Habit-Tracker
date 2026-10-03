package com.habitsheet

import com.habitsheet.data.DriverFactory
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.sync.SheetTokenProvider

/** Outcome of starting the app: either the object graph, or the reason the local database could not be opened. */
sealed interface StartupState {
    class Ready(val graph: AppGraph) : StartupState

    /** [cause] is kept for logging by class name only; never show its message (it may contain paths or data). */
    class Failed(val cause: Throwable) : StartupState
}

object AppStartup {
    /**
     * Builds the [AppGraph]. Opening the database and running migrations happen here; if they throw, the app shows
     * the recovery screen instead of crashing. Nothing is deleted or reset on failure: the database files stay as
     * they are until the person chooses an action there.
     */
    fun create(
        driverFactory: DriverFactory,
        tokenProvider: SheetTokenProvider,
        logger: Logger = NoOpLogger,
    ): StartupState = runCatchingCancellable { AppGraph(driverFactory, tokenProvider, logger) }
        .fold(
            onSuccess = { StartupState.Ready(it) },
            onFailure = {
                logger.e("Startup", "Opening the local database failed", it)
                StartupState.Failed(it)
            },
        )
}
