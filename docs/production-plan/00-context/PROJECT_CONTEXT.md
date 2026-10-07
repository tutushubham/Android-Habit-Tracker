# Project context

Read this at the start of every plan session. It is the shared memory for all plans.

## Product vision (owner's words, condensed)

- An app for people — especially ADHD users — to track their life from their **phone, iPad or any iOS device**.
- **Google Sheets is the backend.** A user creates a blank sheet in their own Google account, copies the link, pastes it into the app. The app creates, maintains and syncs a `Plan` tab. They can open the same sheet on a laptop at month-end to see progress, remaining work, and edit things.
- **No server of ours**, minimal reliance on internet: it must work on-device/offline and sync when online. No other integrations.
- Habits are not the same every day: today a specific run, tomorrow a specific workout, a study plan later; festivals, parties and surprises appear randomly. The model already supports this (dated sessions override weekly plans; skips; multiple sessions per day).
- Two audiences:
  1. **The owner** — uses it with his OND 2026 plan and own data on his own phone + iPad.
  2. **Everyone else** — a neutral app with none of the owner's data, plan, names or credentials.

## Status (updated 2026-10-05, after P1-2)

- **P0-A done**: no personal data/seed in shared code; neutral first run; personal files in gitignored `personal/`. Audit finding P0-1 below is **resolved**.
- **P0-B done**: sync split into `SheetsApi` / `PlanTable` / `PlanReconciler` / `SheetSync` + `SyncScheduler` + `SerializingTokenProvider`; atomic apply, explicit pending flag (migration `4.sqm`), typed errors, retries, offline auto-retry, Disconnect. Audit finding P0-5 is **resolved**.
- **P0-C done in code** (device smoke test and `[mac]` verification owed): one version source in `gradle.properties`, signing/IDs externalised, R8 release build, backup exclusion, iOS privacy manifest, `docs/RELEASING.md`. Audit findings P0-2 and P0-3 are **resolved** (the iOS archive itself is unverified without a Mac).
- **P1-1 done in code** (device and `[mac]` verification owed): `Logger`, cancellation-safe error handling, backup v4 (checksum, settings, optional sheet link), robust Android backup I/O, consistent reset, named confirmations for every destructive action, `refreshToday()` + injectable clock, monotonic `updated_at`, verified migrations (schema v5, snapshots `1.db`–`5.db`), explicit delete cascades, startup failure screen. Audit items under "P1 — robustness" below are **resolved** except where marked.
- **P1-2 done** (device/iPad checks owed, see `PROGRESS.md`): narrow stores and a split repository with a fast snapshot, hierarchical back stack with system back, screens split into leaf composables with previews, typed UI state, crash-safe widget, Spotless/ktlint + detekt + warnings as errors, `./gradlew check` green, ADRs in `docs/adr/`.
- Still open: P0-4 OAuth readiness, P0-6 CI, P0-7 privacy/store assets, P1-3, P2.
- Verification state (2026-10-05, on a Mac): **`./gradlew check` green** = 359 JVM tests, migration verification, Android lint, Spotless, detekt, and **281 common tests on the iOS simulator**; `assembleDebug` and the iOS klib compiles OK; P1-2 refactors compared on an Android emulator (40 screenshots pixel-identical, back navigation and process death checked, widget toggles and a damaged database checked). The iOS app itself builds with Xcode for the simulator and runs (navigation and edge swipe checked). `assembleRelease`/`bundleRelease` last verified in P0-C. Device checks owed (see `PROGRESS.md`).
- Branching: **single-branch policy — everything lives on `master`, no branches are created.** P0 and P1-1 are on `master`; P1-2 is on `master` too (pushed 2026-10-05, audit 2026-10-07); `main`/`origin/main` were deleted. P1-3 and later commit directly to `master`, one commit per step, with the full test run before each commit.

## Tech snapshot (verified in repo)

- Kotlin Multiplatform: Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.3.3, Gradle 9.5.0, SQLDelight 2.3.2, Ktor 3.3.3, kotlinx-datetime 0.8.0, kotlinx-serialization 1.8.0, lifecycle (KMP) 2.9.6. Android min 23 / target+compile 35; iOS deployment target 15; JVM 17. Code quality: Spotless 8.10.3 + ktlint 1.8.0, detekt 1.23.8. Upgrade recommendations: `notes/p1-2-dependency-review.md`.
- One module `:composeApp` (+ `iosApp` SwiftUI host, GoogleSignIn via SPM). **359 JVM tests** (83 at audit time), 4 SQLDelight migrations (`1.sqm`–`4.sqm`, schema version **5**, committed snapshots in `composeApp/src/commonMain/sqldelight/databases/`, `verifyMigrations` part of `check`).
- Layers: `domain` (models, `HabitCalculations`, `PlanResolver`, `backup/` v1–v4 + `Sha256`, `monotonicUpdatedAt`; `repository/` = `HabitStore`, `SettingsStore`, `BackupStore`, union `HabitRepository`) → `data` (`LocalHabitRepository` = `LocalDatabase` + `RowMappers` + `SettingsStoreImpl` + `BackupStoreImpl`; `InMemoryHabitRepository`, `DatabaseFileNames`) → `presentation` (lifecycle ViewModels; `DestructiveAction`, `DateProvider`/`ClockDateProvider`) → `ui` (shared Compose; `navigation/AppBackStack`, `month/*`, `manage/*`, `Components.kt`, `DestructiveConfirmDialog`, `StartupFailureScreen`, `BackupService`/`StartupRecovery` interfaces) ; `platform/` (`Logger`, `runCatchingCancellable`) ; `sync/` (`SheetsApi`, `PlanTable`, `PlanReconciler`, `SheetSync`, `SyncScheduler`, `SyncError`, `SerializingTokenProvider`); `androidMain` (driver, token provider, `AndroidBackupService`/`BackupStreams`, `AndroidStartupRecovery`/`DatabaseFiles`, `AndroidLogger`, share, widget) ; `iosMain` (`IosBackupService`, `IosStartupRecovery`, `IosLogger`).
- Wiring: `AppGraph(driverFactory, tokenProvider, logger)` (hand-rolled DI), created through `AppStartup.create(...)` which returns `Ready(graph)` or `Failed(cause)`; `MainActivity` keeps a nullable graph + startup state, iOS `MainViewController` renders `ReadyApp` or the failure screen. `BackupViewModel` is built by `AppGraph.viewModelProviderFactory(backupService)`. Navigation: `ui/navigation/AppBackStack` (hierarchical back stack, `rememberSaveable`, `BackHandler`), one `AppDestination` host for phone and tablet.
- Docs already in repo: `README.md`, `ARCHITECTURE.md`, `SHEET_SYNC.md`, `WORKBOOK_MAPPING.md`.
- A knowledge graph exists in `graphify-out/` (if present) — use `GRAPH_REPORT.md` / `graph.json` to navigate; refresh with `graphify update .`.

## Key concepts

- `HabitSnapshot.plannedHabitsOn(date)` (domain/model/PlanResolver.kt) is the single resolver: dated `DayPlan` rows → weekly `WeeklyPlan` → every-day only for habits with no schedule (`datedOnly`/sheet-managed habits never fall through).
- Completions: daily keyed `(planId, date)`; weekly `(weeklyHabitId, weekStart)`. Month is a derived window.
- Sheet `Plan` tab columns: `ID, Date, Habit, Session, Done, Skip` (or 8-column OND layout `ID, Date, Area, Habit, Session, Done, Skip, Source`). Sheet wins on conflicts, except local check-offs flagged *pending upload* (explicit per-completion flag, no clock comparison), which are uploaded first.

## Facts from P1-1 that later plans must respect

- **ViewModels (after P1-2 step 3):** all four extend `androidx.lifecycle.ViewModel`; no `close()`. Constructor parameter `scope: CoroutineScope? = null` is a test override (tests pass `backgroundScope`); by default they use `backgroundScope()` (viewModelScope's Job + `Dispatchers.Default`, because the repository does no dispatching of its own). `Logger` stays the last parameter, `BackupViewModel` keeps `callbackDispatcher` (default `Dispatchers.Main`; tests using the default need `Dispatchers.setMain`) and `onDataReplaced`. `AppGraph` is the `ViewModelStoreOwner`; `HabitSheetApp(graph, shareService, backupService, versionProvider)`.
- **Logging rule:** log fixed descriptions plus the throwable (rendered as class names only). Never log sheet URLs/ids, tokens, habit/category names or session text.
- **Destructive actions** go through `DestructiveAction` (text) + `DestructiveConfirmDialog` (UI). `androidUnitTest/.../DestructiveCallSitesTest` scans `commonMain/.../ui/**` recursively (files named by path, e.g. `manage/ManageHabitsScreen.kt`) and keeps the reviewed list of destructive calls; each listed file must show `DestructiveConfirmDialog`. A new screen with a destructive call fails it until reviewed. (Updated in P1-2 step 7.)
- **Dates:** `MonthViewModel.refreshToday()` is called by the platform shells (foreground, time-zone/date change); `AppGraph.refreshToday()` forwards to it. `ClockDateProvider(clock, zone)` is the testable provider. Stored dates are plain `LocalDate`s.
- **`updated_at`:** SQL `UPDATE`s use `MAX(updated_at, :updated_at)`; upserts of plans/completions read the previous stamp (`monotonicUpdatedAt`; completions are strictly increasing because upload acknowledgements compare `updated_at`). Do not "simplify" this away.
- **Deletes cascade explicitly** (`deleteDailyHabit`, `deleteWeeklyHabit`, `deleteCategory` run several statements in one transaction) because the JVM driver ignores `PRAGMA foreign_keys`.
- **Schema changes** need `N.sqm` + a new snapshot (`generateCommonMainHabitsDatabaseSchema`) + a new case in `SchemaMigrationTest`; never edit a shipped `.sqm` or a committed `.db` (one recorded exception in `3.sqm`). See `sqldelight/databases/README.md`.
- **Backups:** writers always produce v4; `BackupSerializer.parse` reads v1–v4. Frozen fixtures `BackupFixtures.V1`–`V4` stand for files people hold: never regenerate them to make a test pass.
- **Android SQLite:** `AndroidDriverFactory` overrides `onCorruption` (no-op) so the framework does not delete a corrupt database; the startup failure screen decides. `DATABASE_NAME` lives there.
- **`loadSnapshot()` cost (resolved in P1-2 step 5):** was 209 ms median for 36k completions; now 37 ms (82 ms before on the same Mac) and a check-off 1 ms, because completions are read once, unsorted by SQL, and single check-offs patch the snapshot. See "Facts from P1-2" and `notes/p1-1-load-snapshot-timings.md`.
- **Test-only trap:** `Map.merge` and other JVM-only APIs compile in `androidUnitTest` and `commonTest` on the JVM but break the iOS compile (`compileTestKotlinIosSimulatorArm64`); run both before declaring done.

## Facts from P1-2 that later plans must respect

- **Stores:** depend on the narrowest of `HabitStore` / `SettingsStore` / `BackupStore`; `HabitRepository` is for the
  composition root and tests (`NarrowStoresTest`). `BackupStore.backupSettings(includeSheetLink)` reads what a backup
  carries.
- **Snapshot:** every write is one transaction and reloads, except single check-offs, which patch the in-memory
  snapshot. Daily completions are ordered by date, habit id, **plan id** (`DAILY_COMPLETION_ORDER`), weekly by week
  start, habit id; reload and patch use the same comparators (`SnapshotParityTest`). A write by another repository
  instance (the widget) shows after `refresh()` (on resume) or the next reloading write.
- **Navigation:** add a screen as a `Destination` with a `parent`; Back always goes to the parent (ADR 0003).
  `BackHandler` from `ui-backhandler` is deprecated in favour of `NavigationEventHandler`; switch when
  `navigationevent-compose` is in the graph (with activity 1.12+ / CMP upgrade).
- **UI structure:** entry composable collects state, leaves take values + lambdas, `@Preview` in every leaf file,
  ≤ 500 lines per file in `ui/` and `data/` (`UiStructureTest`).
- **Widget:** logic in `HabitWidgetLogic`, all database work through `runWidgetWork` (lock covers opening the store,
  8 s bound, never throws).
- **Build:** `./gradlew check` must stay green (tests, lint, Spotless, detekt, iOS simulator tests on a Mac); main code
  has `allWarningsAsErrors`; the detekt baseline is not regenerated to hide new findings; formatting-only commits go in
  `.git-blame-ignore-revs`. The release-signing check only guards the tasks that produce signed artifacts.
- **Visual verification:** `scripts/screenshots/` (emulator, seeded data, 40 screens, pixel comparison).

## Facts from P1-3 step 1 (strings) that later plans must respect

- **Copy is in `composeResources/values/strings.xml`.** Add text there, never as a literal in `ui/` (`NoHardCodedTextTest` fails). Escape `'` as `\'` and `&` as `&amp;`; keep leading/trailing spaces out of values (use format arguments); percent signs and plural handling follow `%1$d` / `%1$s` (pass an already-formatted string for a literal `%`).
- **Presentation returns `UiText`, not `String`:** `MonthUiState.error`, `ManageHabitsViewModel.error`, `SettingsViewModel.sheetMessage`, `SyncStatus.text`, `SheetSyncState.message`, `ConfirmationText.*`, `BackupResult.message`, `BackupService.importBackup(onFailure: (UiText) -> Unit)`. Data stays `Raw`. `SyncError.userMessage()` and `SyncWarning.describe()` return `UiText`; `SyncError.MalformedPlanTab.problem` (`PlanTabProblem`) carries both the user text and the English `logText` (kept in logs and in `reason`).
- **Tests:** compare `UiText` values for behaviour; use `English.render(...)` when the wording matters. `DestructiveCallSitesTest`, `UiStructureTest`, `NoHardCodedTextTest` all scan `ui/**` and will fail on structure or literals.
- **Still English in code (P1-3 step 2):** `UiFormatting.monthNames`, `DailyShare.monthNames`, weekday names from `DayOfWeek.name`, first day of week, date/number formats. **Not translated by design:** logs, the CSV header, `BackupValidator` diagnostics, token-provider technical messages.
- **Wording quirks kept from the old code** (fix in a copy pass): "Synced 1 sessions", "1 checks uploaded", "Plan tab created · 1 sessions uploaded".
- **Platform:** iOS builds the shared text from the catalog (`getString`); Android's share-sheet title is an Android string. The widget keeps `widget_strings.xml`.
- **Copy-change policy:** moving text must not change it. `verbatim` check used in P1-3 step 1: compare each catalog string with the literals of the previous commit (see ADR 0005).

## Audit findings (the source for every plan)

Legend: **V** = verified by reading code/config; **I** = inferred.

### P0 — blockers
1. **Personal data/plan in shared code (V). — RESOLVED in P0-A.**
   - `data/OndSeedData.kt` (859 lines) — the personal Oct–Dec 2026 plan.
   - `LocalHabitRepository.kt` constructor (`seedOndPlan = true`) runs `seedOndPlanIfNeeded` (l.482), `seedWinterArcRoutinesIfNeeded` (l.554), `ensureWinterArcHabitsAreDatedOnly` (l.590) at every startup; settings flags `ond_2026_seeded`, `winter_arc_routines_seeded`, `winter_arc_dated_only`. Hard-coded habit names/category IDs (`category-4`, …).
   - `sync/SheetSync.kt` hard-codes `"run","workout","android","dsa","sde"` (l.115, 164), the `"Plan in OND sheet"` placeholder (l.195, 208), and the window `2026-10-01..2026-12-31` in `ondRowsFor` (l.203–204).
   - README/SHEET_SYNC describe the OND plan as a product feature; `outputs/ond-2026/*`, `tools/build_ond_plan.mjs`, `Habits.xlsx` (2.4 MB) are committed.
2. **No Android release config (V). — RESOLVED in P0-C.** `composeApp/build.gradle.kts` has no `buildTypes`, signing, R8/ProGuard; version hard-coded (`versionCode = 6`, `versionName = "1.1.3"`) and duplicated in `iosApp/iosApp/Info.plist` and `project.pbxproj` (`MARKETING_VERSION = 1.1.3`).
3. **iOS gaps (V). — mostly RESOLVED in P0-C** (privacy manifest; team ID and client IDs externalised; archive validation owed on a Mac). No `PrivacyInfo.xcprivacy`, no entitlements file; `DEVELOPMENT_TEAM = G5VX9GMK76` checked in; deployment target 15.0; GoogleSignIn via SPM only.
4. **OAuth readiness (V per SHEET_SYNC.md).** Consent screen in Testing; Android client registered with the debug SHA-1; scope `spreadsheets` (sensitive).
5. **Sync correctness (V). — RESOLVED in P0-B.** Habits matched by lowercase name; duplicate names abort sync (`require` in SheetSync l.~104); multi-step local writes are not transactional; every local toggle triggers a full `sync()` (`AppGraph.monthViewModel(onLocalChange=…)`) that re-reads `Plan!A:H`; pending detection uses `updatedAt > lastSync` (device clocks); last-upload-wins; `HttpClient()` with no timeouts/retry; parse errors reported by `error()` strings.
6. **No CI (V).** No `.github/`.
7. **No privacy policy / store listing assets (I).**

### P1 — robustness (**resolved in P1-1** except where marked; details in `PROGRESS.md`)
- No logging or crash reporting; 31 `catch` blocks in `commonMain` (V).
- Backup (`domain/backup/BackupModels.kt`) serializes only `HabitSnapshot`; sheet link/theme/onboarding and `sheetManagedHabitIds` (`@Transient`) are excluded; `restoreFromSnapshot` wipes `sheet_managed_habits` (V).
- `AndroidBackupService` import reads lines and concatenates without newlines (V). iOS backup is clipboard-based (README).
- `allowBackup="true"` and no `dataExtractionRules` → the whole DB (with sync state) goes to Google backup (V).
- `deleteDailyHabit` cascades plans and completions (V schema); confirm UI guards (DestructiveActionsTest exists).
- `SystemDateProvider` uses device TZ; midnight rollover/travel untested (I).
- 3 SQLDelight migrations (`1.sqm`–`3.sqm`); only `PlanMigrationTest` covers a step (V).

### P1 — architecture & code quality (**resolved in P1-2**, details in `PROGRESS.md`; the string literals are P1-3)
- ViewModels are plain classes each owning `CoroutineScope(Dispatchers.Default)`; no lifecycle/restoration (V).
- Navigation = `enum Destination` in a `remember`; no back stack, no system-back handling (V).
- God files: `ui/MonthScreen.kt` 1,396 lines; `ManageHabitsScreen.kt` 763; `SheetSync.kt` 370 (API client + reconcile + rules); `LocalHabitRepository.kt` 685 (CRUD + seed + settings + restore) (V).
- Every write calls `loadSnapshot()` (full reload of all tables) (V).
- Possible counting mismatch: `HabitCalculations.dailySummaries/habitSummaries` key by `habitId`, not `planId` (V code; effect I).
- No detekt/ktlint/Spotless, no dependency update bot; `HttpClient()` defaults; `ui/` has 82 `Text("…")` literals (V).

### P1 — gaps in existing features
- 0 uses of `stringResource` — all UI text is hard-coded (V); Android widget uses `strings.xml` (inconsistent).
- 22 `contentDescription`s across UI; month grid is custom-drawn (V).
- Default seed categories are the workbook's ten (`data/DefaultData.kt`) and first-run seeds OND (V).
- iOS: Data & Backup copies to clipboard (V per README); Android: system document picker.
- No iOS widget; no reminders (V) → **deferred, not in scope** (D4).

### P2 — testing
- 83 tests: calculations, MonthEngine, PlanResolver, backup/restore, view models, sheet parsing, SQLite persistence/migration (JVM). Missing: Compose UI tests, instrumented/widget tests, iOS tests, sync-with-fake-server failure cases, per-version migration tests, coverage reporting (V).
- `SheetSync.kt` produced a tree-sitter syntax warning at l.44 in the knowledge graph — likely a parser limitation, confirm it compiles cleanly (I).

## Non-goals (whole project)
New features (reminders, iOS widget, stats, streaks, gamification), a server/backend, analytics, accounts, other cloud integrations.
