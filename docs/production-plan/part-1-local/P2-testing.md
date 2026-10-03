# P2 — Testing

**Goal:** enough automated coverage that you can ship updates confidently: logic, persistence, sync failures, UI flows, accessibility semantics, Android + iOS smoke.

**Depends on:** P1-2/P1-3 (stable structure).
**Branch:** `prod/p2-testing`
**Out of scope:** load/perf testing beyond what P1-1 added; third-party device farms (Part 2 may add Firebase Test Lab / Xcode Cloud optionally).

## 1. Code analysis

Existing (83 `@Test`):
- `commonTest`: `HabitCalculationsTest`, `MonthEngineTest`, `PlanResolverTest`, `SheetLinkTest`, `SheetPlanTableTest`, `SheetSyncTest`, `BackupRestoreTest`, `CsvGeneratorTest`, `ArchivingTest`, `BackupViewModelTest`, `MonthViewModelTest`, `ManageHabitsViewModelTest`, `SettingsViewModelTest`, `DestructiveActionsTest`, `ResetDataTest`, `HabitUsabilityTest`, `ProductionReadinessTest`, `InMemoryHabitRepositoryTest`.
- `androidUnitTest`: `LocalHabitRepositoryPersistenceTest` (real SQLite), `PlanMigrationTest`.
Gaps (verified by listing): no Compose UI tests, no Android instrumented/widget tests, no `iosTest`, sync tested only for happy-path parsing (extended in P0-B), no per-version migration tests (added in P1-1), no coverage reporting, no screenshot tests, no accessibility assertions, no test for time-zone/rollover UI.

## 2. Steps

1. **Coverage tooling.** Add Kover (`org.jetbrains.kotlinx.kover`) to produce JVM/Android unit coverage; set an initial threshold (e.g. domain ≥ 90%, data/sync ≥ 80%, presentation ≥ 70%) — start with report-only, then ratchet.
2. **Test fixtures & builders.** Create `testFixtures`-style helpers: `snapshot { habit("Run"); session("2026-10-01") … }` DSL, `FakeDateProvider`, `FakeSheetsServer` (from P0-B), `FakeLogger`. Remove copy-pasted setup in existing tests.
3. **Domain/property tests.** Property-based tests (kotest-property or manual randomised loops with seeds) for `PlanResolver` (dated > weekly > default precedence), `MonthEngine` (28/29/30/31 days incl. leap), `HabitCalculations` invariants (completed ≤ goal rules per WORKBOOK_MAPPING).
4. **Repository & migration tests.** Cover: every `HabitStore/SettingsStore/BackupStore` method on real SQLite; FK cascade behaviour; idempotent upserts; atomic `applySheetSync` (failure injection); migrations from v1/v2/v3/v4 fixtures (P1-1/P0-B).
5. **Sync tests.** Using `FakeSheetsServer`: each `SyncError` path, retry/backoff timing (virtual time), coalescing scheduler (`runTest`), conflict rule, first-sync paths, foreign `Plan` tab untouched, other tabs untouched, concurrent token requests.
6. **ViewModel tests** with a test dispatcher for lifecycle ViewModels (P1-2); cover midnight/TZ rollover, month navigation, today/tomorrow plans, completion toggling, error surfaces.
7. **Compose UI tests (commonTest, `compose.uiTest`).** Using the decomposed leaf composables: empty state; add habit flow; toggle completion; month navigation; sheet-link validation messages; backup confirm dialogs; settings theme switch; semantics assertions (role/state/label on month cells). Run on Android (JVM or instrumented) and iOS simulator `[mac]`.
8. **Android instrumented tests.** Smoke: launch → add habit → toggle → rotate → state retained; widget toggle via `AppWidgetHost`/receiver test; backup export/import via fake `ActivityResultRegistry`; DB migration on-device.
9. **iOS tests `[mac]`.** `iosSimulatorArm64Test` for shared logic (SQLDelight native driver, serialization, datetime); an XCUITest smoke (launch, add habit, toggle, background/foreground).
10. **Screenshot tests (optional but recommended).** Roborazzi/Paparazzi for Android of key screens in light/dark/phone/tablet/font-scale; store goldens in repo.
11. **Test the build artefacts.** A script that installs the release APK on an emulator and runs the smoke suite (R8 safety); an iOS archive-then-run smoke `[mac]`.
12. **Flake control & docs.** No `Thread.sleep`; use virtual time; add `docs/TESTING.md` explaining layers, how to run each, how to add a test; add the commands to CI later (B1).

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md and part-1-local/P2-testing.md. Confirm P1-2/P1-3 are merged. Branch prod/p2-testing. Do not change production code except for testability seams (inject DateProvider/Logger/dispatchers) and genuine bugs you find — list each bug separately. Summarise the plan in 8 lines and wait.
```

### Step prompts
```
Steps 1-2: Add Kover coverage (report-only first) and shared test fixtures: a snapshot-builder DSL, FakeDateProvider, FakeLogger, and reuse the FakeSheetsServer from P0-B. Refactor existing tests to use them. Print the baseline coverage per package.
```
```
Steps 3-4: Add property-style randomised tests (seeded) for PlanResolver precedence, MonthEngine (28/29/30/31 days, leap years) and HabitCalculations invariants per WORKBOOK_MAPPING.md; add full real-SQLite tests for every store method, FK cascades, idempotent upserts, atomic applySheetSync failure injection, and migrations from v1..latest fixtures.
```
```
Steps 5-6: Complete sync tests with FakeSheetsServer for every SyncError, retry/backoff using virtual time, the coalescing scheduler, conflict rule, first-sync paths, foreign Plan tab and other tabs untouched, concurrent token requests. Extend ViewModel tests (lifecycle ViewModels) for midnight/timezone rollover, month navigation, today/tomorrow plans, toggling and error surfaces.
```
```
Step 7: Add Compose Multiplatform UI tests (compose.uiTest) on the decomposed leaf composables for: empty state, add habit, toggle completion, month navigation, sheet-link validation messages, backup confirm dialogs, theme switch, and semantics assertions (role/state/label) on month cells. Make them runnable on Android; mark iOS-simulator runs [mac].
```
```
Steps 8-9: Add Android instrumented smoke tests (launch, add habit, toggle, rotate retains state; widget toggle; backup export/import with fake ActivityResultRegistry; on-device migration) and iOS tests [mac]: shared-logic tests on iosSimulatorArm64 and an XCUITest smoke (launch, add habit, toggle, background/foreground). Give me the exact commands.
```
```
Steps 10-12: Add Roborazzi (or Paparazzi) screenshot tests of key screens in light/dark, phone/tablet and 200% font; a script to install the release APK on an emulator and run the smoke suite; remove all Thread.sleep in tests; write docs/TESTING.md. Then ratchet Kover thresholds to the achieved coverage minus 2%.
```
```
Run the whole plan: execute P2 steps 1-12 in order with a commit per step. Keep a running list of production bugs found; fix each with a failing test first, in separate commits.
```

## 4. Verification
```
./gradlew check koverHtmlReport
./gradlew :composeApp:connectedDebugAndroidTest      # emulator/device
./gradlew :composeApp:iosSimulatorArm64Test          # [mac]
xcodebuild test -scheme iosApp ...                   # [mac], XCUITest smoke
```

## 5. Definition of done
- [ ] Coverage report with agreed thresholds enforced.
- [ ] Domain, data, sync, ViewModel and UI layers each have meaningful tests; failure paths covered.
- [ ] Instrumented + iOS smoke tests exist and pass on your machines.
- [ ] Release-APK smoke test script passes.
- [ ] `docs/TESTING.md` written; bugs found are fixed with regression tests.
- [ ] `PROGRESS.md` ticked → **Part 1 exit gate** review.

## 6. Part 1 exit gate (do this after P2)
1. Clean clone → `./gradlew check assembleRelease` passes; iOS archive builds `[mac]`.
2. Fresh install on a phone and an iPad: neutral app, no personal data.
3. Your own devices: update-over-install keeps your data; your sheet syncs both ways.
4. `git grep` for personal strings/IDs (see P0-A/P0-C) is clean.
5. Handle D2: create the public-facing repo/history strategy now so Part 2 (B1/B4) doesn't ship private history.

## 7. Inputs from P1-1 (what exists, what is still missing)
- **Exists (JVM, 291 tests at P1-1 end):** backup v1–v4 fixtures and restore, tamper detection; real-SQLite migration tests from v1–v4 (`SchemaMigrationTest`) and schema verification in `check`; delete cascades; monotonic `updated_at`; date rollover/DST/travel with a fake clock; startup failure handling and file move-aside; `loadSnapshot` timing test; destructive-call-site guard.
- **Missing, assign here:** Compose UI tests for `DestructiveConfirmDialog` flows (Reset, Import, delete habit/category, remove session), `StartupFailureScreen` (three buttons, reset confirmation, success → retry), `DataBackupScreen` result messages; instrumented tests for `AndroidBackupService` (process death while the picker is open), `AndroidStartupRecovery` and a forced corrupt database (Android's default would delete it; `onCorruption` is a no-op by design); iOS tests for `IosBackupService`, `IosStartupRecovery` (file locations, share sheet) and the `NSSystemTimeZoneDidChange` / `NSCalendarDayChanged` observers `[mac]`; a device check that the Android/iOS SQLite drivers keep `PRAGMA foreign_keys = ON`.
- **Performance:** keep `LoadSnapshotPerformanceTest` (prints timings, loose bound) and tighten it to the 100 ms / 50 ms targets once P1-2 step 5 has implemented the proposal.
- **Coverage tooling gap:** `androidUnitTest` is where all SQLite tests run (JDBC driver); they locate `sqldelight/databases` and `ui/` sources by relative path from the module directory.
