# Architecture

## Model

Habit definitions are reusable activity templates with a category and an action/avoidance kind. A dated plan row has a stable `ID`; multiple rows may use the same habit and date. A daily completion is keyed by `(planId, date)` and a weekly completion by `(weeklyHabitId, weekStartDate)`. `MonthKey` is a derived calendar window, never a database container. Removing or moving a planned row does not delete its completion history.

`HabitSnapshot.plannedHabitsOn(date)` is the single plan resolver. Dated sessions win; otherwise a weekly weekday prescription applies; otherwise a habit with no schedule stays due every day (legacy simple trackers). Habits with `datedOnly`, any weekly plan, or sheet management never fall through to that every-day default. Sheet-imported habits are plan-driven (`datedOnly`). Month progress uses planned, non-skipped sessions.

## Persistence

SQLDelight stores categories, daily habits, weekly habits, and completion records in SQLite. Stable text IDs and update timestamps make rows individually addressable. Unique completion keys make writes idempotent. The repository also stores the selected Sheet link, last successful sync, keys previously imported from that Sheet, and a per-completion `pending_upload` flag (schema migration `4.sqm`); it never stores Google tokens. The repository exposes domain models only; generated SQL rows do not cross the data boundary.

The schema is at version 5 (`1.sqm`–`4.sqm`); `composeApp/src/commonMain/sqldelight/databases/N.db` are committed snapshots of every released version and `verifyCommonMainHabitsDatabaseMigration` (part of `check`) migrates each one to the current schema and compares. `SchemaMigrationTest` migrates databases filled with rows as each version stored them. Deletes state their cascades explicitly (one transaction) instead of relying on `PRAGMA foreign_keys`, and `updated_at` never moves backwards (SQL `MAX`; completions strictly increase).

The data contract is split by role (`domain/repository`): `HabitStore` (habits, categories, plans, check-offs and the `snapshot` they publish), `SettingsStore` (theme, tutorial, sheet link, sync state and `applySheetSync`) and `BackupStore` (`snapshot`, `backupSettings`, `clearAllData`, `restoreFromSnapshot`). `HabitRepository` is their union, used only by `AppGraph`, the platform shells and tests; every ViewModel and `SheetSync` depends on the narrow store(s) it uses (`NarrowStoresTest`).

`LocalHabitRepository` is the production implementation, assembled from `LocalDatabase` (driver, the single lock, the transaction helper, open/seed, snapshot reload), `RowMappers.kt` (SQL rows <-> domain, list order), `SettingsStoreImpl` and `BackupStoreImpl` (delegated). Every write is one transaction and reloads the snapshot, except a single check-off (`setDailyCompletion`/`setWeeklyCompletion`), which patches the in-memory snapshot with the same comparators the reload sorts by; `SnapshotParityTest` checks that every kind of write leaves the snapshot equal to a fresh read. Another instance writing the same file (the Android widget) becomes visible on `refresh()` (called on resume) or the next reloading write. A whole sync is applied through `applySheetSync`: one transaction, one reload. `SheetSync` uses the local cache and the Google Sheets API without changing the presentation data model.

## Robustness and data safety

- **Startup:** `AppStartup.create` builds `AppGraph` and returns `Ready` or `Failed`. A database that cannot be opened or migrated shows `StartupFailureScreen` (Try again / Save a copy of the data file / Start with empty data). The last action is confirmed and *moves* the files aside (`<file>.damaged-<time>`, newest 3 kept; naming in `DatabaseFileNames`), it never deletes. Android's SQLite helper would delete a corrupt database on its own; `AndroidDriverFactory.onCorruption` is a no-op so this screen decides. A failed open closes its driver.
- **Logging:** `platform/Logger` (logcat, `NSLog`, no-op in tests), injected through `AppGraph`. Throwables are rendered as class names only, messages are fixed descriptions; nothing leaves the device. `runCatchingCancellable` never swallows `CancellationException`. Android also logs uncaught exceptions and chains the previous handler.
- **Backups:** `BackupSerializer` writes v4 (`version`, `timestamp`, `checksum` = SHA-256 over compact `settings` + `data` as written, `settings` = theme/onboarding/optional sheet link, `data`) and reads v1–v4 (`BackupContainer` for v1–v3). No sync state is ever written. `restoreFromSnapshot` validates first, replaces everything in one transaction and always clears sync state (synced keys, last sync, managed habits, pending flags); the sheet link is replaced only when asked. `BackupService` reports `BackupResult`; Android reads whole streams (`BackupStreams`) and exports via a private cache file so it survives process death.
- **Destructive actions:** each has a `DestructiveAction` text naming what is affected, shown by `DestructiveConfirmDialog`. `clearAllData` also clears the sheet link and sync state (local only; the Google Sheet is never touched). `DestructiveCallSitesTest` fails when a screen gains an unreviewed destructive call. Audit: `docs/production-plan/notes/p1-1-destructive-actions-audit.md`.
- **Dates:** `DateProvider.today()` is derived from a clock and the zone at the moment of the call (`ClockDateProvider`); `MonthViewModel.refreshToday()` is triggered by the one-minute poll, app foreground, and Android `ACTION_TIMEZONE_CHANGED`/`DATE_CHANGED`/`TIME_CHANGED` or iOS `NSSystemTimeZoneDidChange`/`NSCalendarDayChanged`. Recorded dates are plain `LocalDate`s and never move when the device changes zone.
- **Cost:** a full reload of 36k completions takes about 37 ms on an Apple-silicon Mac (was 82 ms on the same machine, 209 ms on the original Windows desktop); a check-off about 1 ms, independent of history size (`docs/production-plan/notes/p1-1-load-snapshot-timings.md`).

## Calculations

`HabitCalculations` contains pure summary functions. Month, Today, and Sheet export all resolve due work through `plannedHabitsOn`. `MonthEngine` handles leap years and the workbook's five day blocks.

## UI and presentation

Compose Multiplatform UI is shared across Android and iOS.

**Navigation** (`ui/navigation/AppBackStack`, ADR 0003): the back stack is the chain of parents to the current screen (Tracker > Settings > Backup). Back, on-screen or system (Android back and predictive back, the iOS edge swipe, via Compose's `BackHandler`), always goes up one level; on the Tracker the platform handles it. The stack is `rememberSaveable`, so the current screen survives Android process death. Phone and tablet share one destination host (`AppDestination`); `AdaptiveFrame` adds the tablet sidebar. The startup failure screen is outside navigation on purpose.

**Strings** (ADR 0005): all copy is in `composeApp/src/commonMain/composeResources/values/strings.xml` (English, with plurals) and rendered with `stringResource`/`pluralStringResource`. Presentation, sync errors, destructive-action confirmations, `BackupResult` and the Android/iOS backup and recovery services produce `UiText` (resource + arguments, `Raw` for data, `Joined`), which the UI renders with `asString()` and non-UI code with `resolve()`. Android-only system text (the share-sheet title) is in `androidMain/res/values/platform_strings.xml`, like the widget's. `NoHardCodedTextTest` keeps literals out of `ui/` and the catalog consistent; tests render wording with `English.render(uiText)` (`EnglishCatalog` is generated from the XML by `generateEnglishCatalog`). Month names, weekday names and number/date formats are still English in code (P1-3 step 2).

**Screens** are split into an entry composable that collects ViewModel state and leaf composables that take plain values and lambdas: `ui/month/` (`MonthScreen`, `MonthHeader`, `MonthGrid`, `MonthSummary`, `WeeklyBlock`, `TodayList`, `MonthYearPickerDialog`; callbacks bundled in `MonthActions`) and `ui/manage/` (`ManageHabitsScreen`, `ManageSections`, `HabitEditorDialog`, `CategoryEditorDialog`). Every leaf file has `@Preview`s (sample data in `MonthPreviewData`); `UiStructureTest` enforces this and the 500-line limit for `ui/` and `data/`.

**ViewModels** (ADR 0002): the four ViewModels (`MonthViewModel`, `ManageHabitsViewModel`, `SettingsViewModel`, `BackupViewModel`) are `androidx.lifecycle.ViewModel`s (KMP artifacts). `AppGraph` is their `ViewModelStoreOwner` and provides the factory (`viewModelProviderFactory(backupService)`); `HabitSheetApp(graph, …)` obtains them with `viewModel(viewModelStoreOwner = graph, factory = …)` and screens collect state with `collectAsStateWithLifecycle()`. They live as long as the graph (cleared in `AppGraph.close()`), which on Android is the Activity's: they are not retained across an Activity re-creation because the token provider is bound to the Activity. Their work runs in `viewModelScope`'s job on `Dispatchers.Default` (`ViewModel.backgroundScope()`), so repository calls stay off the main thread. `MonthViewModel` and `ManageHabitsViewModel` combine repository state with pure calculations into immutable UI state; `MonthViewModel` combines typed groups (`Selection`, `Chrome`) with the snapshot into an `@Immutable` `MonthUiState` (`MonthStateWiringTest` pins which input feeds which field). Width-based layout primitives size the month grid and day plan; phone vs tablet is a breakpoint on available width, not device-specific business logic. The month grid shows only habits planned in that month and marks unplanned days as non-actionable (not empty checkboxes).

## Module boundaries

The project intentionally uses one shared application module rather than many Gradle modules. Packages maintain the useful boundaries:

- `domain.model`: persistent-history concepts
- `domain.calculation`: workbook formula translation
- `domain.repository`: data contract (`HabitStore`, `SettingsStore`, `BackupStore`, their union `HabitRepository`)
- `domain.backup`: backup format v1–v4, validation, `Sha256`
- `platform`: `Logger`, `runCatchingCancellable`
- `data`: SQLDelight mapping (`RowMappers`), `LocalDatabase`, the local repository and its parts, `DatabaseFileNames`
- `presentation`: UI state and view models
- `ui`: shared Compose screens and design system; `ui.navigation` (back stack), `ui.month`, `ui.manage` (split screens)
- `sync`: the Sheet-as-backend loop, split so the decisions are pure:
  - `SheetsApi`: HTTP only (timeouts, bounded backoff with jitter, `Retry-After`; maps statuses to `SyncError`)
  - `PlanTable`: parse/serialize the `Plan` tab (pure)
  - `PlanReconciler`: pure function from local snapshot + remote rows + pending flags to a `ReconcileResult` (habit identity, renames, deletions, completions to apply or upload)
  - `SheetSync`: orchestration (token, API calls, reconcile, one `applySheetSync`)
  - `SyncScheduler`: debounces triggers (2.5 s), one follow-up run, immediate on foreground / Sync now, automatic retry while offline with pending check-offs
  - `SyncError` / `SerializingTokenProvider`: typed failures with user messages; queues platform sign-in requests
- `androidMain` / `iosMain`: database drivers, platform entry points, backup and startup-recovery implementations, loggers, and the Google authorization bridge
- `androidMain/.../widget`: the home-screen widget. `HabitWidgetLogic` (what it lists, what a tap writes) works on a `HabitStore`; `runWidgetWork` opens the app's own database for one action under the widget lock, always closes it, gives up after 8 s (inside the broadcast window of `goAsync()`) and turns any failure (for example a damaged database) into the widget's "unavailable" state instead of a crash. Widget check-offs set the pending-upload flag like the app's; they do not start a sync themselves.

## Sync limits

The `Plan` tab is authoritative for dated sessions. Check-offs made on the device carry a *pending upload* flag until their value is written to the sheet; pending checks are uploaded, every other check takes the sheet's value (no device clocks are compared). A row's habit is found by its plan `ID` first and by name second; a sheet-side rename of all of a habit's rows renames the local habit. The client tracks previously imported keys to apply Sheet row deletions without clearing unrelated local plans, and keeps local plans if the `Plan` tab is suddenly empty. A `Plan` tab that does not match the documented schema is never modified, and other tabs are never touched.

It does not merge simultaneous edits to one row (the last device to sync wins), sync weekly-habit definitions/categories, or provide a change-history UI. See `SHEET_SYNC.md` for the user-facing rules, error messages and offline behaviour.

## Code quality

`./gradlew check` runs everything a change must pass: the JVM tests, the migration verification,
Android lint, Spotless (ktlint 1.8, IntelliJ IDEA style; `./gradlew spotlessApply` formats), detekt (default rules plus
`config/detekt/detekt.yml`; findings that predate it are in `composeApp/detekt-baseline.xml`) and, on a Mac, the common
tests on the iOS simulator. Production code compiles with `allWarningsAsErrors` (every Android and iOS main
compilation). Formatting-only commits are listed in `.git-blame-ignore-revs`. Dependency updates come as grouped
Dependabot PRs (`.github/dependabot.yml`).

## Decisions

Architecture decision records live in `docs/adr/`: Google Sheet as the backend (0001), lifecycle ViewModels (0002), the navigation back stack (0003), no DI framework (0004).
