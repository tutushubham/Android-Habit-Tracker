# P1-1 — Robustness & data safety

**Goal:** the app never silently loses or corrupts a user's history; failures are logged and visible to the developer (locally), understandable to the user; dates/time zones, migrations, backups and destructive actions behave predictably.

**Depends on:** P0-A, P0-B (typed sync errors), P0-C (backup rules).
**Branch:** `prod/p1-1-robustness`
**Out of scope:** remote crash reporting services/analytics (no backend; decide in Part 2 whether to use platform-provided crash reports only: Play Console vitals, Xcode Organizer).

## 1. Code analysis

- **Logging:** zero log calls in `composeApp/src`. 31 `catch` blocks in `commonMain` (`ManageHabitsViewModel` ×13, `BackupViewModel`, `SheetSync`, etc.) turn exceptions into UI error strings and discard the cause.
- **Backup:** `domain/backup/BackupModels.kt` — `BackupContainer(version, timestamp, data: HabitSnapshot)`, `CURRENT_VERSION = 3`, `ignoreUnknownKeys = true`. `HabitSnapshot.sheetManagedHabitIds` is `@Transient`. Sheet link, theme, onboarding flag, last sync and synced keys are not included. `restoreFromSnapshot` (LocalHabitRepository ~l.370) clears everything and `sheet_managed_habits` but leaves `sheet_url`, `sheet_last_sync`, `sheet_synced_keys` → after restore, sync state refers to data that no longer exists.
- **Android import:** `androidMain/.../AndroidBackupService.kt` reads lines and appends without `\n`; `exportBackup` sets `pendingDataToExport` in a field (lost on process death while the picker is open); output stream `openOutputStream(...)?.use` swallows null; no error callback.
- **iOS import/export:** clipboard-based (`iosMain/.../IosBackupService.kt`, 21 lines).
- **Reset/clear:** `clearAllData()` (LocalHabitRepository l.~354–365) wipes tables and sets `onboarding_completed=0` but not sheet link/sync state.
- **Destructive deletes:** `deleteDailyHabit`/`deleteWeeklyHabit`/`deleteCategory` cascade via FKs; `DestructiveActionsTest` exists — verify every screen path goes through a confirmation.
- **Dates:** `SystemDateProvider` uses `TimeZone.currentSystemDefault()`; `MonthViewModel.today` flow polls each minute; habits store `created_on` as `LocalDate` text; `updated_at` uses device clock.
- **Migrations:** `1.sqm–3.sqm`; `PlanMigrationTest` covers one step; no test opens a DB at schema v1/v2 and migrates to latest.
- **Snapshot loading:** every repository write calls `loadSnapshot()` (full reload).

## 2. Steps

1. **Logging abstraction.** Add `Logger` interface in `commonMain` (levels, tag, throwable) with expect/actual or injected implementations: Android → `android.util.Log`, iOS → `NSLog`/`os_log`; no-op in tests. Do not add third-party analytics. (Using Kermit is acceptable if you prefer a library.) Redact: never log sheet URLs, tokens, habit names, or session text at `INFO+`.
2. **Replace swallowed exceptions.** For each `catch`, log with context and keep the user message; never catch `CancellationException`; replace `catch (e: Exception)` blocks with a small helper (`runCatchingCancellable`) used consistently. Add a global uncaught-exception hook on Android (`Thread.setDefaultUncaughtExceptionHandler` chain, logging only) — no network.
3. **Backup completeness and integrity.**
   - Introduce `BackupContainer` v4: add `settings` (theme, onboarding) and *optionally* sheet link; **exclude** sync keys/last-sync/pending flags.
   - On restore: clear sheet sync state (`sheet_synced_keys`, `sheet_last_sync`, managed IDs, pending flags) so the next sync does a clean "sheet wins" reconcile; keep the sheet link only if the backup contains it and the user confirms.
   - Keep reading v1–v3 (unit tests with fixture files for each version).
   - Add a checksum field (SHA-256 over the data JSON) and validate it for v4.
4. **Android backup service.** Read the full stream with `readBytes().decodeToString()`; handle null streams/IO exceptions with a callback; persist `pendingDataToExport` via `rememberSaveable`-equivalent (e.g. keep in the Activity's `savedInstanceState`) or write to cache file first and copy on result; show success/failure to the user.
5. **iOS file-based backup `[mac]`.** (Overlaps P1-3 UX; do the *data-safety* part here.) Ensure import validates before wiping; export includes the same v4 container.
6. **Reset flow.** `clearAllData()` should also clear sheet link, sync state and pending flags, **or** keep link by explicit user choice — pick and document; confirm dialogs have a clear "this deletes only data on this device, your Google Sheet is untouched" statement (copy text lives in P1-3 strings).
7. **Destructive actions audit.** Walk every path that can call `delete*`/`clearAllData`/`restoreFromSnapshot`; ensure confirmation with named item; ensure archive is the default affordance where it exists. Add tests where missing.
8. **Date and time-zone safety.**
   - Make midnight rollover and TZ change observable in `MonthViewModel` (re-evaluate `today` on foreground and on `ACTION_TIMEZONE_CHANGED`/iOS `NSSystemTimeZoneDidChange`); do not rely only on the 1-minute poll.
   - Add tests with a fake `DateProvider`: 23:59→00:00 rollover, DST change days, travel across zones (completions stay on the local date they were recorded).
   - Decide `updated_at` source: keep device clock but monotonic-guard (never move backwards for the same row).
9. **Migration safety.** Add `MigrationTest` that creates an empty DB at each schema version (use SQLDelight `verifyMigrations = true` plus `Schema.migrate` on a JVM SQLite driver with fixture data at v1, v2, v3) and asserts data survives. Make `verifyMigrations` part of the build (`sqldelight { databases { ... verifyMigrations.set(true) } }`) and commit `.db` schema snapshots (`./gradlew generateSchema`/`generateDebugHabitsDatabaseSchema`).
10. **Repository performance guard.** Measure `loadSnapshot()` with 5 years × 20 habits of completions in a test; if > ~100 ms on JVM, change writes to update the in-memory snapshot incrementally or use SQLDelight `asFlow()`. (Do the measurement now; the refactor itself, if needed, is covered in P1-2.)
11. **Startup failure handling.** If DB open/migration fails, show a recovery screen (retry / export raw DB / reset) instead of crashing in `AppGraph` construction. Minimal UI only.

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md and part-1-local/P1-1-robustness-and-data-safety.md. Confirm P0-A/B/C are merged. Branch prod/p1-1-robustness. Rule: never reduce what existing backups (v1-v3) can restore; never drop user data. Summarise the plan in 8 lines and wait.
```

### Step prompts
```
Steps 1-2: Add a Logger interface (commonMain) with Android (android.util.Log) and iOS (NSLog) implementations and a no-op for tests, injected through AppGraph. Never log sheet URLs, tokens, habit names or session text at INFO or above. Replace every swallowed catch in commonMain (ManageHabitsViewModel, BackupViewModel, SheetSync, repository) with logging plus the existing user message; add a runCatchingCancellable helper so CancellationException is never swallowed. Add a logging-only uncaught exception handler on Android. Keep tests green.
```
```
Step 3: Create backup format v4 (settings: theme+onboarding; optional sheet link; NO sync keys/last-sync/pending flags; SHA-256 checksum over the data) while still restoring v1-v3. On restore clear all sheet sync state so the next sync is a clean sheet-wins reconcile. Add fixture files for v1, v2, v3, v4 and tests for restore, tamper detection and unknown-version rejection.
```
```
Steps 4-5: Fix AndroidBackupService: read the full stream (no line concatenation), handle null/IO errors with callbacks and user-visible success/failure, and survive process death while the picker is open (cache file or saved state). For iOS ensure the import validates before wiping and export uses the v4 container; list anything that needs a Mac to verify.
```
```
Steps 6-7: Make clearAllData consistent about sheet link and sync state (clear them; keep local-only wording in the confirm dialog: "deletes data on this device; your Google Sheet is untouched"). Audit every path to delete*/clearAllData/restoreFromSnapshot for a named confirmation and add tests for any unguarded path. Report the audit as a table.
```
```
Step 8: Make date handling robust: re-evaluate "today" on app foreground and on timezone-change events (Android ACTION_TIMEZONE_CHANGED, iOS NSSystemTimeZoneDidChange) instead of only the 1-minute poll; add fake-DateProvider tests for midnight rollover, DST days and cross-zone travel (completions stay on their recorded local date); make updated_at monotonic per row.
```
```
Step 9-10: Enable SQLDelight verifyMigrations, commit generated schema snapshots, and add a JVM migration test that builds DBs at v1, v2 and v3 with fixture rows, migrates to latest and asserts data survives. Add a performance test for loadSnapshot() with 5 years x 20 habits of completions; report timings and propose (not yet implement) a change if > 100 ms.
```
```
Step 11: Add a minimal startup failure screen (Retry / Export raw database file / Reset) shown when DB open or migration throws, instead of crashing AppGraph construction, on both platforms. No new styling beyond existing theme.
```
```
Run the whole plan: execute P1-1 steps 1-11 in order with a commit per step; stop for confirmation before changing any destructive behaviour; finish with the Definition-of-done checklist.
```

## 4. Verification
```
./gradlew :composeApp:testDebugUnitTest :composeApp:verifySqlDelightMigration   # task name per SQLDelight 2.x: verifyDebugHabitsDatabaseMigration
```
Manual: export v3 backup from an old build → import into the new build; kill the app while the Android save picker is open; change device time zone while the app is open; force a corrupted DB (rename a file) and confirm the recovery screen.

## 5. Definition of done
- [x] Logger in place; no silent catches; CancellationException never swallowed; no PII in logs.
- [x] Backup v4 + v1–v3 restore tests; checksum; restore clears sync state.
- [x] Android backup IO robust; iOS path validated `[mac]` (iOS code written and compiled; Mac verification owed).
- [x] Reset and delete flows audited, all confirmed and tested (`notes/p1-1-destructive-actions-audit.md`).
- [x] Rollover/TZ/DST tests pass; foreground refresh works (device check owed).
- [x] Migrations verified from v1/v2/v3 (and v4); `verifyMigrations` on in the build.
- [x] Startup failure screen exists (Android and iOS; device/Mac verification owed).
- [x] `PROGRESS.md` ticked (as Done*, with the manual checks listed).

## 6. Handoff
P1-2 gets a `Logger` to inject into new components and the measured `loadSnapshot()` numbers to decide whether incremental updates are needed.
