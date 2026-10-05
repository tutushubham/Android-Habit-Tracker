# ADR 0002: Multiplatform lifecycle ViewModels owned by `AppGraph`

- Status: accepted (P1-2 step 3, 2026-10-03)

## Context

The four presentation classes were plain objects with their own `CoroutineScope` and a manual `close()`, created in
`remember`. Nothing tied their work to a lifecycle and screens collected state with `collectAsState()`.

## Decision

- `MonthViewModel`, `ManageHabitsViewModel`, `SettingsViewModel`, `BackupViewModel` extend
  `androidx.lifecycle.ViewModel` (JetBrains KMP artifacts, lifecycle 2.9.x). Work runs in `viewModelScope`'s job on
  `Dispatchers.Default` (`ViewModel.backgroundScope()`), because the repository does blocking SQLite calls.
- `AppGraph` is the `ViewModelStoreOwner` and builds them through `viewModelProviderFactory(backupService)`; the store is
  cleared in `AppGraph.close()`. Screens collect with `collectAsStateWithLifecycle()`.
- Each ViewModel depends only on the store its role needs (`HabitStore`, `SettingsStore`, `BackupStore`; P1-2 step 4).
- A `scope` constructor parameter remains as a test override.

## Consequences

- Cancellation and clearing follow the platform; tests use `Dispatchers.setMain` or pass `backgroundScope`.
- The ViewModels live as long as the graph, which on Android is Activity-scoped (the Google token provider needs the
  Activity), so they are not retained across an Activity re-creation; the manifest handles configuration changes.
  If that changes, scope the graph to the Application and the token provider to the Activity.
