# Progress

Tick when the plan's *Definition of done* is fully met and merged.
Legend: **[Done]** = code, automated tests and docs complete and on `master`. Items that can only be checked on a real device are listed separately under *Manual checks still owed*.

## Next session: P1-2
- Branch policy: **only `master`; no new branches.** P1-1 was fast-forwarded into `master` (`b0224f8`). P1-2 commits directly to `master`, one commit per step.
- Read first: `00-context/PROJECT_CONTEXT.md` ("Status" and "Facts from P1-1"), then `part-1-local/P1-2-architecture-and-code-quality.md` — its new section **"Handoff from P1-1"** lists what changed under it and what to adjust.
- Baseline to protect: 291 JVM tests green, `verifyCommonMainHabitsDatabaseMigration` green, `compileKotlinIosSimulatorArm64` + `compileTestKotlinIosSimulatorArm64` green, `assembleDebug` OK.
- Untracked on purpose (not part of any plan): `.claude/` and `gradle/gradle-daemon-jvm.properties`.

## Part 1 — Local
- [Done] P0-A Product split & seed removal (on `master` via PR #1; manual device check, step 9, still owed)
- [Done] P0-B Sync correctness (on `master`; manual two-device checks still owed)
- [Done*] P0-C Release build config (steps 1–11 on `master`; *code, automated checks and docs complete; owed: release-APK device smoke test and the `[mac]` iOS verification, see below)
- [Done*] P1-1 Robustness & data safety (steps 1–11 on `master`; *code, automated checks and docs complete; owed: the device checks and `[mac]` iOS checks listed under "Manual checks still owed")
- [ ] P1-2 Architecture & code quality (in progress: steps 1–4 done on `master`)
- [ ] P1-3 Gaps in existing features
- [ ] P2 Testing
- [ ] **Part 1 exit gate** (see README)

## Part 2 — Beyond the code
- [ ] B1 CI/CD
- [ ] B2 Google OAuth & Sheets setup
- [ ] B3 Legal, privacy, store listing
- [ ] B4 Build, sign, distribute
- [ ] B5 Docs, support, launch

## Decisions (see 00-context/DECISIONS.md)
- [Done] D1 Personal data strategy — option A applied: no seed in the app, the plan lives in the owner's Google Sheet (P0-A)
- [ ] D2 Repo visibility / history (decide before the end of Part 1; history rewrite happens last)
- [ ] D3 Sheets OAuth scope & verification path
- [ ] D4 Reminders (default: out of scope)
- [ ] D5 App identity (name, bundle IDs, publisher)

## What is done so far

### P0-A — Product split & seed removal
- [x] Characterization tests pin start-up behaviour (fresh DB: default categories only, zero habits; already-seeded DB: data untouched). `SeedRemovalCharacterizationTest`
- [x] Seeding removed from `LocalHabitRepository` (`seedOndPlan` parameter, the three seed/ensure functions); `OndSeedData.kt` deleted. Existing installs keep all their data; legacy settings rows are simply no longer read.
- [x] `SheetSync` freed of personal rules: no habit-name allow-lists, no fixed 2026 window, no first-sync seed keys; generic rolling window (`SheetSyncWindow.rolling`: 31 days before the month start to 180 days ahead); `Area` column mapped to an existing category by name; legacy `"Plan in OND sheet"` weekly placeholders cleaned up once if present.
- [x] Fresh install is neutral: zero habits, first-run tutorial restored and shown once (not for installs that already have habits).
- [x] Personal files moved to the gitignored `personal/` folder (`Habits.xlsx`, `outputs/`, `tools/`) with a README on uploading the plan to your own Drive.
- [x] `README.md`, `SHEET_SYNC.md`, `ARCHITECTURE.md` rewritten for a generic product.
- [ ] Step 9 manual: install over the existing build on your device; confirm habits/plans/completions intact and your sheet still connects.

### P0-B — Sync correctness
- [x] Step 1 `FakeSheetsServer` (Ktor MockEngine: metadata, `Plan` A:H, addSheet, format, PUT, append, values:batchUpdate; injectable 401/403/404/429/5xx/timeout/connection failure/failure on call N; records writes outside `Plan`) + characterization tests. `5dcc123`
- [x] Step 2 `SheetSync` split into `SheetsApi` (HTTP), `PlanTable` (pure parse/serialize), `PlanReconciler` (pure), thin orchestrator; reconciler tested with no IO. `58e6139`
- [x] Steps 3–4 Explicit habit identity (known plan ID wins; rename detection; duplicate names never throw; empty-tab guard) and atomic `HabitRepository.applySheetSync` (one transaction, one snapshot reload, rollback test on real SQLite). Design note: `notes/p0-b-identity.md`. `81a7a7c`
- [x] Steps 5–6 `SyncScheduler` (2.5 s debounce, one follow-up run, immediate on foreground / Sync now) and explicit per-completion `pending_upload` flag with non-destructive `4.sqm` migration + migration tests. Conflict rule documented. `5351d99`
- [x] Steps 7–8 Ktor timeouts, bounded exponential backoff with jitter and `Retry-After`, sealed `SyncError` (Offline, AuthExpired, AccessDenied, NotFound, RateLimited, MalformedPlanTab, Unknown), friendly messages; offline reads "Will sync when online." and is not styled as an error. A fake-server test per error. `266b776`
- [x] Steps 9–11 Plan-tab safety tests (foreign/malformed `Plan` tab and other tabs never written), `SerializingTokenProvider` + hardened `AndroidSheetTokenProvider`, explicit **Disconnect** button, `SHEET_SYNC.md` (schema, identity, conflicts, errors, offline). `56e97a8`
- [x] Completion review (this session): offline auto-retry, iOS compile fix, extra coverage, docs refresh — see below.

## Completion review of P0-A + P0-B (2026-10-03)

Checked every step and every Definition-of-done item of both plans against the code, tests and docs. Result: all deliverables present. Problems found and fixed during the review:

| # | Problem | Fix |
|---|---------|-----|
| 1 | **iOS compile was broken**: `PlanReconciler` used `MutableMap.merge`, a JVM-only API, in `commonMain` (Android and JVM tests were green, so it went unnoticed). | Replaced with a plain map update. `./gradlew :composeApp:compileKotlinIosSimulatorArm64` and `compileTestKotlinIosSimulatorArm64` now pass (they run on Windows; linking/running still needs a Mac). |
| 2 | "Will sync when online." was a promise nothing kept: after an offline failure the next sync only happened on the next tap / foreground. | `SyncScheduler` now retries by itself (30 s, doubling to 5 min) while the last result was Offline **and** check-offs are still pending; stops as soon as either stops being true. 3 new scheduler tests. |
| 3 | Coverage gaps listed after step 1 (concurrent `sync()`, window bounds, failure on the Nth call). | `SheetSyncGapsTest` (4 tests). |
| 4 | `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `DECISIONS.md`, the P0-B plan checklist and `README.md` still described the pre-P0-B sync or were unticked. | Updated. |
| 5 | `graphify` was not installed / not on PATH, so `CLAUDE.md` instructions failed. | Installed (`pip install graphifyy`); `CLAUDE.md` now uses `python -m graphify …` (works without PATH changes); graph rebuilt (1.4k nodes). |
| 6 | Branch layout: `master` lacked P0-B and local `master` lagged `origin/master`. | `master` fast-forwarded to `origin/master` (PR #1), P0-B rebased on it and fast-forwarded in. All further work happens on `master`. |

State after the review: **196 JVM tests, 0 failures**; `assembleDebug` OK; iOS klib compile OK.

### P0-C — Release build configuration
- [x] Step 1 One version source: `appVersionName`/`appVersionCode` in `gradle.properties`; `syncIosVersion` generates `iosApp/Config/Version.xcconfig`; pbxproj/`Info.plist` use `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION`. `0591134`
- [x] Step 2 Signing from gitignored `keystore.properties` or `HABITSHEET_*` env vars (release builds fail with a clear message without them); iOS team ID and Google client IDs moved to gitignored `iosApp/Config/Local.xcconfig` (+ committed `.example`). `cd90327`
- [x] Steps 3–4 Release build type with R8 minify + shrinkResources and `proguard-rules.pro`; `assembleRelease` and `bundleRelease` verified with a throwaway keystore. Debug `.debug` suffix deliberately **not** added (would need its own OAuth client and start with an empty DB); documented. `64ee17f`
- [x] Steps 5–6 DB and shared prefs excluded from cloud backup and device transfer; `Theme.HabitSheet` day/night pair; predictive back opt-in; exported flags reviewed. `9fab092`
- [x] Steps 7–8 `PrivacyInfo.xcprivacy`, `ITSAppUsesNonExemptEncryption=NO`, opaque full-bleed 1024 app icon. `ae51ca9`
- [x] Steps 9–11 `jvmToolchain(17)` (foojay resolver bumped to 1.0.0), `.gitignore`, redundant `stitch` zip removed, `docs/RELEASING.md`. `9edcc91`
- [ ] Owed on a device: release-APK smoke test (checklist in `docs/RELEASING.md`).
- [ ] Owed on a Mac `[mac]`: open the project in Xcode (hand-edited pbxproj), create `Local.xcconfig`, `xcodebuild archive`, Validate in Organizer, compare the privacy report with `PrivacyInfo.xcprivacy`, check the adjusted icon.

### P1-1 — Robustness & data safety (on `master`)
- [x] Steps 1–2 `Logger` (Android logcat, iOS `NSLog`, no-op), injected through `AppGraph`; throwables logged by class name only (no URLs, tokens or user text); `runCatchingCancellable`; every `catch` in `commonMain` logs or rethrows cancellation (`ManageHabitsViewModel` used to swallow it); Android uncaught-exception logger. `8e18a67`
- [x] Step 3 Backup format **v4** (`settings`: theme, onboarding, optional sheet link; SHA-256 over settings+data; no sync keys/last-sync/pending flags); v1–v3 still restore (frozen fixtures `BackupFixtures`); restore always clears sheet sync state; tamper, missing checksum and unknown-version rejection. `66a4c97`
- [x] Steps 4–5 `BackupService` reports `BackupResult` / failures; `AndroidBackupService` reads whole streams (`BackupStreams`: 32 MB cap, BOM, null/IO errors), exports through a private cache file (survives process death, toast if the screen is gone), deletes half-written files; iOS clipboard import refuses empty text, export is v4. `3f8e95c`
- [x] Steps 6–7 Reset clears sheet link + sync state (Google Sheet untouched); every destructive action has a named confirmation (`DestructiveAction` + `DestructiveConfirmDialog`), including the two previously unguarded session removals; audit table in `notes/p1-1-destructive-actions-audit.md`; `DestructiveCallSitesTest` guards the screens. `1b729ba`
- [x] Step 8 `refreshToday()` on foreground, Android TIMEZONE/DATE/TIME broadcasts and iOS time-zone/day-change notifications (1-min poll kept); `ClockDateProvider` (injectable clock+zone); rollover, DST and travel tests; `updated_at` never goes backwards (completions strictly increase so upload acknowledgements stay correct). `bff8353`
- [x] Steps 9–10 `verifyMigrations` on, snapshots `1.db`–`5.db` (README in `sqldelight/databases/`), `SchemaMigrationTest` (v1–v4 → v5 with rows), `3.sqm` got a no-op `DROP INDEX IF EXISTS`; explicit delete cascades (the JVM driver ignores `PRAGMA foreign_keys`); `loadSnapshot()` measured 209 ms median for 36k completions, proposal in `notes/p1-1-load-snapshot-timings.md` (**not implemented**, input to P1-2 step 5). `0158050`
- [x] Step 11 Startup failure screen (`AppStartup`, `StartupFailureScreen`, `AndroidStartupRecovery`, `IosStartupRecovery`): Try again / Save a copy of the data file / Start with empty data (confirmed; moves the files aside, keeps the newest 3); Android no longer lets the framework delete a corrupt database; a failed open closes its driver. `cc029b7`
- [ ] Owed: device and `[mac]` checks, see the next section (P1-1 entries).

## Manual checks still owed (need a device / your Google account)
- P0-A step 9: install over the existing build on your phone; habits, plans and completions intact; sheet still connects.
- P0-A: fresh emulator install is empty and shows the tutorial once.
- P0-B (from the plan, section 4): (1) 20 quick check-offs produce one coalesced sync; (2) airplane mode, toggle, reconnect → uploaded (now also automatic); (3) edit a session in the sheet → appears after sync; (4) rename a habit in the sheet on all its rows → local habit renamed, no duplicate; (5) revoke app access in the Google Account → clear message, local data intact; also try **Disconnect**.
- P1-1 steps 4–5 (device): (a) Android: export a backup, kill the app (`adb shell am kill com.habitsheet...` or swipe away) while the save picker is open, then save: a "Backup saved." toast appears and the file holds the full v4 JSON; (b) export to a full storage / revoked location shows an error and leaves no half-written file; (c) import a large multi-line v3 backup from an old build; (d) import a non-backup file, a 0-byte file and cancel the picker: data unchanged, clear message or nothing.
- P1-1 steps 4–5 `[mac]`: iOS compile of `IosBackupService` passes on Windows (klib) but the clipboard flow was never run: export, copy something else, import (expect the "no backup text" or "isn't a valid backup" message and unchanged data); export then import on a second device; a v3 backup text from the old build imports.
- P1-1 step 8 (device): with the app open, change the time zone (Android Settings → Date & time; iOS Settings → General → Date & Time) and cross midnight: the selected day, month grid and widget move at once. Change the device clock backwards, toggle a habit, sync: the check-off still uploads. `[mac]` iOS: `NSSystemTimeZoneDidChange` / `NSCalendarDayChanged` observers compile but were never run.
- P1-1 steps 9–10 (device): install the new build over one that still has a v1–v4 database (an old APK, if you have one) and confirm habits, plans, check-offs, settings and the sheet link are intact; delete a habit with check-offs, export a backup and import it again (orphan check, see the cascade note in the log).
- P1-1 step 11 (device): force a failure and look at the screen. Android: close the app, `adb shell run-as <package> sh -c 'head -c 2000 /dev/urandom > databases/habit-sheet.db'` (or overwrite the file with garbage another way), start the app: the recovery screen appears (not an empty app: Android's default would silently delete a corrupt database, `onCorruption` is now a no-op). Try each button: Try again (still fails, nothing changes), Save a copy (zip of the file(s) lands where you choose; also kill the app while the picker is open), Start with empty data (confirm; app starts empty; `ls databases/` shows `habit-sheet.db.damaged-<time>`). Then restore a normal install and check the app still opens. `[mac]`: same on iOS; `IosStartupRecovery` was only compiled (file locations, share sheet, move-aside are unverified).
- iOS: build and run on a Mac (`[mac]`): the Swift token provider is wrapped by `SerializingTokenProvider` but not run on a device.

## Known limitations carried forward (not bugs in P0-A/P0-B/P0-C scope)
- ~~`restoreFromSnapshot` does not reset sync keys / last-sync~~ — fixed in P1-1 step 3 (restore now clears all sync state; backup v4 carries theme/onboarding and an optional sheet link, never sync state or pending marks).
- After a restore, check-offs not yet uploaded are dropped from the pending set (sheet wins); the checked state itself comes from the backup. (A restored theme and a cleared link now show immediately: `BackupViewModel.onDataReplaced` → `SettingsViewModel.reloadFromStorage`.) The UI has no prompt yet for restoring a backup's sheet link or including it on export (`exportBackup(includeSheetLink)` / `importBackup(applySheetLink)` exist, default false) → P1-3 copy.
- P1-1 follow-ups left for later plans: no "export a backup first" prompt or automatic safety copy before Reset/Import (**P1-3**); Import confirms before the file is chosen so it cannot preview the file's contents (**P1-3**); iOS backup is still clipboard-based (**P1-3**); a sync running at the moment of a Reset can re-apply sheet rows once; deleting a sheet-managed habit locally is undone by the next sync and the dialog does not say so; the widget (`HabitCompletionWidgetProvider`) still opens its own `LocalHabitRepository` per action (**P1-2 step 9**).
- Whether the Android/iOS SQLite drivers keep `PRAGMA foreign_keys = ON` is unverified (the JVM driver does not); deletes no longer depend on it, but inserts are not FK-checked on the JVM test driver.
- `loadSnapshot()` is 2x over the 100 ms target for a heavy user (209 ms, 36k completions); cause (the completions query runs twice) and a 4-point proposal are in `notes/p1-1-load-snapshot-timings.md` → **P1-2 step 5**.
- Widget check-offs set the pending flag but do not themselves start a sync (uploaded on the next foreground / Sync now).
- Debounce has no maximum wait (continuous tapping with gaps under 2.5 s delays the sync until a pause).
- Android consent screen results after Activity recreation are not resumed (the queue times out after 3 min and the user taps Connect & sync again) → revisit in **P1-2**.
- `stitch/` design exports are still tracked (the redundant zip was removed in P0-C); decide with D2 at the end of Part 1.
- Apple team / Google client IDs are out of tracked files (P0-C) but remain in git history → **D2**.
- Default category names (emoji list) are the original workbook's → **P1-3** neutral first run.
- Build warning left on purpose: the Kotlin Gradle plugin reports that `com.android.application` inside the KMP module is deprecated (AGP 9). It works today through the `android.builtInKotlin=false` / `android.newDsl=false` opt-outs in `gradle.properties`; the fix is moving the Android app into a separate `androidApp` module → **P1-2**.
- Knowledge-graph parser (tree-sitter) reports a syntax warning at the `class SheetSync(` header; the code compiles, it is a tool limitation.
- The legacy `"Plan in OND sheet"` constant in `SheetSync.kt` and old-install simulations in tests intentionally still mention OND.

## Session log
<!-- One line per session: date · plan · outcome · commit. All work happens on master. -->
2026-10-02 · P0-A · steps 2–8 done (then on branch `prod/p0-a-product-split`) (seed removed, SheetSync de-OND'd with rolling window, personal files moved, docs rewritten, tutorial restored); 87 JVM tests green; step 9 manual device checks pending · merged as PR #1 (`cc4660d`)
2026-10-02/03 · P0-B · steps 1–11 done (`5dcc123` … `56e97a8`); 190 JVM tests green · rebased onto master
2026-10-03 · P0-A/B completion review · iOS compile fix, offline auto-retry, coverage gaps, docs refresh, graphify installed; 196 JVM tests green; work continues directly on `master`
2026-10-03 · P0-C · steps 1–11 done directly on `master` (`0591134` … `9edcc91`); assembleDebug, assembleRelease/bundleRelease (test keystore) and unit tests OK; device + Mac verification pending
2026-10-03 · P0-C audit · full rebuild green (`--rerun-tasks`: debug + release + unit tests + iOS klib compile); dead check in `SyncError.kt` removed, AGP deprecation noise silenced via `gradle.properties` (that file is part of the still-uncommitted Gradle/AGP upgrade), docs refreshed
2026-10-03 · P1-1 steps 1–2 · `Logger` (Android logcat / iOS NSLog / no-op), `runCatchingCancellable`, every `catch` in commonMain logs or rethrows cancellation, Android uncaught-exception logger; 201 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 step 3 · backup v4 (settings, optional sheet link, SHA-256 over settings+data), v1–v3 still restore (frozen fixtures), restore clears sync state, tamper/unknown-version rejection; 222 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 steps 4–5 · `BackupService` now reports results/failures; `AndroidBackupService` reads whole streams (`BackupStreams`, 32 MB cap, BOM, null/IO errors), exports via a private cache file so a save survives process death (toast if the screen is gone), deletes half-written files; iOS clipboard import refuses empty text, export is v4; 234 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 steps 6–7 · reset now clears sheet link + sync state (Google Sheet untouched), named confirmations via `DestructiveAction`/`DestructiveConfirmDialog` incl. the two previously unguarded session removals, audit in `docs/production-plan/notes/p1-1-destructive-actions-audit.md`, `DestructiveCallSitesTest` guard; 247 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 step 8 · `MonthViewModel.refreshToday()` (foreground, Android TIMEZONE/DATE/TIME broadcasts, iOS foreground + time-zone + day-change observers, 1-min poll kept as safety net), injectable `ClockDateProvider`, rollover/DST/travel tests, `updated_at` never moves backwards (SQL `MAX`, completions strictly increasing so upload acks stay correct); 266 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 steps 9–10 · `verifyMigrations` on (snapshots `1.db`–`5.db` committed, wired into `check`, negative-tested), `SchemaMigrationTest` migrates v1–v4 fixtures to v5, `3.sqm` got a no-op `DROP INDEX IF EXISTS` so SQLDelight can compile it against `1.db`; **found** the JVM driver ignores `PRAGMA foreign_keys` so deletes now cascade explicitly (`DeleteCascadeSqliteTest`); `loadSnapshot()` measured at 209 ms median for 36k completions (target 100) — cause and proposal in `notes/p1-1-load-snapshot-timings.md`; 274 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 step 11 · `AppStartup.create` returns Ready/Failed instead of throwing; `StartupFailureScreen` (Try again / Save a copy of the data file / Start with empty data, confirmed, moves the file aside and keeps the newest 3 copies) on Android (`AndroidStartupRecovery`, zip via picker) and iOS (`IosStartupRecovery`, share sheet, unverified); Android no longer deletes a corrupt database (`onCorruption` no-op); failed open closes its driver; 291 JVM tests green · master (then on `prod/p1-1-robustness`)
2026-10-03 · P1-1 review · progress and docs checked against the code (291 JVM tests, migration verification, iOS compile green); `PROJECT_CONTEXT.md`, `PROGRESS.md`, `ARCHITECTURE.md`, `README.md`, `SHEET_SYNC.md` and the P1-2/P1-3/P2 plans updated for the next session · master
2026-10-03 · branch policy · `prod/p1-1-robustness` fast-forwarded into `master` (`b0224f8`); docs updated: only `master` is used and no new branches are created
2026-10-03 · P1-2 steps 1–2 · `MonthUiStateGoldenTest` (11 hand-derived golden tests for `toUiState`, which is now `internal`); `MultiSessionCountingTest` (12). Finding: the app's month/day/habit/category/Today numbers were already per-session (`planId`) and stayed unchanged; the real mismatch was the Android widget (rows and toggles keyed by habit id, so with two sessions both rows shared one state and a toggle wrote a completion the app did not show). Fixed through shared `todaySessions` / `sessionToToggle`; old widget buttons without a plan id fall back to the habit's first session. Habit-keyed `HabitCalculations` functions are unused by the app and documented as such. Rule in `WORKBOOK_MAPPING.md`. 314 JVM tests green, assembleDebug and both iOS klib compiles OK · master
2026-10-03 · P1-2 step 3 · the four ViewModels are `androidx.lifecycle.ViewModel`s (lifecycle 2.9.6, JetBrains KMP artifacts): no `close()`, `viewModelScope` Job on `Dispatchers.Default`, created by `AppGraph.viewModelProviderFactory(...)` (AppGraph is the `ViewModelStoreOwner`, store cleared in `close()`), screens use `collectAsStateWithLifecycle()`; scope injection kept as a test override; new `ViewModelLifecycleTest` (setMain, default scope, clearing) and `AppGraphViewModelsTest`; 324 JVM tests green, migrations verified, assembleDebug and both iOS klib compiles OK · master
2026-10-05 · P1-2 step 4 · `HabitRepository` is now `HabitStore` (habits/plans/check-offs + `snapshot`) + `SettingsStore` (theme, onboarding, sheet link, sync state, `applySheetSync`) + `BackupStore` (`snapshot`, `backupSettings`, `clearAllData`, `restoreFromSnapshot`); `ManageHabitsViewModel` → `HabitStore`, `SettingsViewModel` → `SettingsStore`, `BackupViewModel` → `BackupStore`, `MonthViewModel` and `SheetSync` → `HabitStore` + `SettingsStore`; `NarrowStoresTest` builds each from a one-interface wrapper; 329 JVM tests green, assembleDebug and both iOS klib compiles OK · master
