# P1-2 — Architecture & code quality

**Goal:** a maintainable, lifecycle-correct, lint-clean codebase using idiomatic KMP patterns — **without changing behaviour or adding features**. Refactors are guarded by tests written first.

**Depends on:** P0-A/B/C, P1-1.
**Branch:** `prod/p1-2-architecture`
**Out of scope:** new screens, redesign, new libraries beyond those named here, moving to multi-module unless you explicitly choose to.

## 1. Code analysis

- **ViewModels:** `MonthViewModel`, `ManageHabitsViewModel`, `SettingsViewModel`, `BackupViewModel` are plain classes, each with `CoroutineScope(SupervisorJob()+Dispatchers.Default)` and a manual `close()`. They are created in `AppGraph` (Activity/`remember` scope) — no `viewModelScope`, no `SavedStateHandle`, no lifecycle-aware collection (`collectAsState()` instead of `collectAsStateWithLifecycle`). `AppGraph` is created in `MainActivity.onCreate` and closed in `onDestroy` (config changes are swallowed by manifest `configChanges`, which hides lifecycle problems).
- **Navigation:** `ui/HabitSheetApp.kt` — `private enum class Destination { Tracker, Manage, Plan, Settings, Backup, About }` held in `remember`; two layout branches (phone/tablet) duplicate the `AppDestination(...)` call with 14 parameters; no back stack/system back.
- **God files:** `ui/MonthScreen.kt` (1,396 lines), `ui/ManageHabitsScreen.kt` (763), `data/LocalHabitRepository.kt` (685; CRUD + restore + settings + (formerly) seeding), `sync/SheetSync.kt` (split in P0-B), `androidMain/.../widget/HabitCompletionWidgetProvider.kt` (409).
- **DI:** hand-rolled `AppGraph` constructing `LocalHabitRepository`, `SheetSync`, 3 ViewModels; `HabitSheetApp` receives `repository` directly and builds `BackupViewModel` inside a composable.
- **Repository contract:** `HabitRepository` (≈35 suspend methods) mixes domain CRUD, sheet-sync state, theme/onboarding settings, restore. Every write → `loadSnapshot()`.
- **Calculations:** `HabitCalculations` is pure and good. Potential logic issue: `dailySummaries` / `habitSummaries` key by `habitId`, while completions are keyed by `planId` → multi-session days may collapse. Resolve with a test + fix.
- **State:** `MonthUiState` has ~28 fields; `combine` uses an `Array<Any?>` with unchecked casts (MonthViewModel l.~100–120).
- **Quality tooling:** none (no detekt/ktlint/Spotless, no `-Werror`/`allWarningsAsErrors`, no Renovate/Dependabot).
- **Resources:** UI strings hard-coded (82 `Text("…")` in `ui/`; handled in P1-3).

## 2. Steps (order matters: tests → structure → tooling)

1. **Lock behaviour with characterization tests.** For `MonthViewModel.toUiState` (golden tests with a fixed snapshot), `PlanResolver`, `HabitCalculations`, repository CRUD/restore. These are the safety net for everything below.
2. **Fix the multi-session counting question.** Write failing tests: one habit with two sessions on the same date (different `planId`), one completed. Month/day/category summaries must count sessions consistently with `plannedHabitsOn` and with the Today screen. Fix in `HabitCalculations`/`MonthViewModel` if the tests fail; document the rule in `WORKBOOK_MAPPING.md`.
3. **Adopt multiplatform lifecycle ViewModels.** Add `androidx.lifecycle:lifecycle-viewmodel` + `lifecycle-runtime-compose` (KMP artifacts) and `lifecycle-viewmodel-compose`. Convert the four ViewModels to `androidx.lifecycle.ViewModel` using `viewModelScope`; remove manual `close()`/`CoroutineScope`; collect state with `collectAsStateWithLifecycle()`. Provide them through a small factory using `AppGraph`'s repository (no DI library required).
4. **Narrow the repository interfaces** (Interface Segregation) without moving code yet: `HabitRepository` → `HabitStore` (habits/plans/completions), `SettingsStore` (theme/onboarding/sheet link & sync state), `BackupStore` (clear/restore). `LocalHabitRepository` can implement all three; ViewModels depend only on what they use.
5. **Split `LocalHabitRepository`.** Extract `RowMappers.kt` (SQL row → domain), `SettingsStoreImpl`, `BackupStoreImpl`; keep one transaction helper. Leave `loadSnapshot()` unless P1-1's measurement says otherwise; if so, switch to SQLDelight `asFlow()` or incremental updates behind the same `StateFlow<HabitSnapshot>`.
6. **Navigation.** Replace the enum+`remember` with Compose Multiplatform Navigation (`org.jetbrains.androidx.navigation:navigation-compose`) **or** a minimal back-stack holder, whichever you prefer; requirements: system back on Android (predictive back enabled in P0-C), iOS swipe-back feel, state survival across config/process changes for the *current destination*. Collapse the duplicated phone/tablet `AppDestination` calls into one with a layout parameter.
7. **Decompose the big composables.** `MonthScreen.kt` → `MonthScreen` (scaffold/state), `MonthGrid`, `DayPlanPanel`, `TodayList`, `WeeklyBlock`, `MonthHeader`, etc. in separate files under `ui/month/`; `ManageHabitsScreen.kt` → editors/dialogs under `ui/manage/`. Hoist state; pass lambdas, not ViewModels, to leaf composables; add `@Preview` for each leaf (CMP `@Preview` in commonMain is supported). No visual change — verify with screenshot diffs (manual or Paparazzi/roborazzi on Android, optional).
8. **UI state modelling.** Replace `Array<Any?>` `combine` with typed `combine` overloads or a small intermediate data class; split `MonthUiState` into focused sub-states (`CalendarState`, `PlanState`, `ProgressState`) if it reduces recomposition; mark state classes `@Immutable`/`@Stable` where appropriate.
9. **Android widget.** Extract the data access of `HabitCompletionWidgetProvider` (409 lines) behind an interface so it can be unit-tested; ensure it uses the same repository/queries as the app and that DB access is on `Dispatchers.IO` with a bounded scope (`goAsync()` usage reviewed for ANR safety).
10. **Static analysis & formatting.** Add Spotless (ktlint) + detekt with a baseline file; enable `allWarningsAsErrors` for `commonMain` once warnings are cleaned; add `./gradlew check` aggregating tests+lint. Add Renovate or Dependabot config (`.github/` is created in B1 — only add the config files now if you like).
11. **Dependency review.** Verify versions; confirm Ktor features (timeouts/retry from P0-B), consider `kotlinx-collections-immutable` for state lists only if profiling shows recomposition issues. Do **not** add DI/ORM/etc. libraries unless there is a concrete pain.
12. **Docs.** Update `ARCHITECTURE.md` (layers, interfaces, navigation, ViewModel lifecycle, sync components) and add an ADR folder `docs/adr/` with short records for: Sheets as backend; lifecycle ViewModels; navigation choice; no DI framework.

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md and part-1-local/P1-2-architecture-and-code-quality.md. Confirm P0-A/B/C and P1-1 are merged. Branch prod/p1-2-architecture. Hard rules: zero behaviour/visual change except the multi-session counting fix; every refactor step is preceded by tests that fail if behaviour changes; commit after each step; use graphify-out if present. Summarise the plan in 8 lines and wait.
```

### Step prompts
```
Step 1-2: Add golden/characterization tests for MonthViewModel.toUiState with a fixed HabitSnapshot, PlanResolver, HabitCalculations and repository CRUD/restore. Then write failing tests for a habit with two sessions on one date (different planId, one done) across day, habit, category and month summaries and the Today list; make all views agree with plannedHabitsOn; fix whatever fails and document the rule in WORKBOOK_MAPPING.md.
```
```
Step 3: Convert MonthViewModel, ManageHabitsViewModel, SettingsViewModel and BackupViewModel to androidx.lifecycle.ViewModel (KMP artifacts) using viewModelScope; remove manual close()/CoroutineScope; collect state with collectAsStateWithLifecycle; provide them via a small factory fed by AppGraph. Keep public behaviour identical; update tests (Dispatchers.setMain with a test dispatcher).
```
```
Steps 4-5: Split the HabitRepository interface into HabitStore, SettingsStore and BackupStore (ViewModels depend only on what they use) and split LocalHabitRepository into RowMappers, a settings implementation and a backup implementation sharing one transaction helper. If P1-1's loadSnapshot timing showed >100ms, also move to SQLDelight asFlow() behind the same StateFlow<HabitSnapshot>. No behaviour change; all tests green.
```
```
Step 6: Replace the Destination enum + remember in ui/HabitSheetApp.kt with Compose Multiplatform navigation (or a minimal back-stack holder if you can justify it), supporting Android system back (and predictive back) and iOS swipe-back, and collapse the duplicated phone/tablet AppDestination calls into one with a layout parameter. Document the choice in docs/adr/.
```
```
Steps 7-8: Decompose ui/MonthScreen.kt into ui/month/* (header, grid, day plan panel, today list, weekly block, …) and ManageHabitsScreen.kt into ui/manage/*, hoisting state and passing lambdas to leaves, with @Preview for each leaf. Replace the Array<Any?> combine in MonthViewModel with typed combines or an intermediate data class and split MonthUiState into focused sub-states if it reduces recomposition. No visual change; list how you verified that.
```
```
Step 9: Put the Android widget's data access behind an interface so it is unit-testable, ensure DB work runs on Dispatchers.IO with a bounded scope and goAsync() is used safely for ANR protection, and add tests for toggle and progress computation.
```
```
Steps 10-12: Add Spotless(ktlint) and detekt with a baseline, a ./gradlew check that runs tests + lint, allWarningsAsErrors for commonMain once clean, and Renovate/Dependabot config files. Then update ARCHITECTURE.md and add docs/adr/ with short records: Sheets as backend, lifecycle ViewModels, navigation choice, no DI framework. Report any dependency upgrades you recommend but did not apply.
```
```
Run the whole plan: execute P1-2 steps 1-12 in order with commit per step; after each structural step run all tests; stop if any golden test changes unexpectedly; finish with the Definition-of-done checklist.
```

## 4. Verification
```
./gradlew check            # tests + spotless + detekt
./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64   # [mac] for iOS
```
Manual: rotate/resize/split-screen on tablet; background the app and kill the process → relaunch returns sensibly; Android back button from every screen; iPad sidebar layout unchanged; widget toggles still work.

## 5. Definition of done
- [ ] All ViewModels lifecycle-aware; no manual scopes; state collected lifecycle-aware.
- [ ] Repository split behind narrow interfaces; no file > ~500 lines in `ui/` or `data/`.
- [ ] Navigation with back stack; single destination host.
- [ ] Multi-session counting consistent and tested.
- [ ] Spotless + detekt + `check` green; warnings clean.
- [ ] `ARCHITECTURE.md` + ADRs updated; `PROGRESS.md` ticked.

## 6. Handoff
P1-3 edits UI strings and semantics — easier now that screens are decomposed. P2 builds UI tests on the new leaf composables.
