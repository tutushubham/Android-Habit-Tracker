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

## Status (updated 2026-10-03)

- **P0-A done**: no personal data/seed in shared code; neutral first run; personal files in gitignored `personal/`. Audit finding P0-1 below is **resolved**.
- **P0-B done**: sync split into `SheetsApi` / `PlanTable` / `PlanReconciler` / `SheetSync` + `SyncScheduler` + `SerializingTokenProvider`; atomic apply, explicit pending flag (migration `4.sqm`), typed errors, retries, offline auto-retry, Disconnect. Audit finding P0-5 is **resolved**.
- **P0-C done in code** (device smoke test and `[mac]` verification owed): one version source in `gradle.properties`, signing/IDs externalised, R8 release build, backup exclusion, iOS privacy manifest, `docs/RELEASING.md`. Audit findings P0-2 and P0-3 are **resolved** (the iOS archive itself is unverified without a Mac).
- Still open: P0-4 OAuth readiness, P0-6 CI, P0-7 privacy/store assets, and every P1/P2 item.
- Verification state: 196 JVM tests green, `assembleDebug`, `assembleRelease`/`bundleRelease` (test keystore) and iOS klib compile OK on Windows; device and Mac checks owed (see `PROGRESS.md`).
- Branching: work happens directly on `master` from here.

## Tech snapshot (verified in repo)

- Kotlin Multiplatform: Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 8.9.1, Gradle 8.14.4, SQLDelight 2.3.2, Ktor 3.3.3, kotlinx-datetime 0.8.0, kotlinx-serialization 1.8.0. Android min 23 / target+compile 35; iOS deployment target 15; JVM 17.
- One module `:composeApp` (+ `iosApp` SwiftUI host, GoogleSignIn via SPM). ~13k lines Kotlin, 196 JVM tests (was 83 at audit time), 4 SQLDelight migrations (`1.sqm`–`4.sqm`).
- Layers: `domain` (models, `HabitCalculations`, `PlanResolver`, backup) → `data` (`LocalHabitRepository`, `InMemoryHabitRepository`, `OndSeedData`) → `presentation` (plain-class ViewModels with own scopes) → `ui` (shared Compose) ; `sync/` (`SheetsApi`, `PlanTable`, `PlanReconciler`, `SheetSync`, `SyncScheduler`, `SyncError`, `SerializingTokenProvider`); `androidMain` (driver, token provider, backup, share, widget) ; `iosMain`.
- Wiring: `AppGraph` (hand-rolled DI). Navigation: a single `enum Destination` in `ui/HabitSheetApp.kt`.
- Docs already in repo: `README.md`, `ARCHITECTURE.md`, `SHEET_SYNC.md`, `WORKBOOK_MAPPING.md`.
- A knowledge graph exists in `graphify-out/` (if present) — use `GRAPH_REPORT.md` / `graph.json` to navigate; refresh with `graphify update .`.

## Key concepts

- `HabitSnapshot.plannedHabitsOn(date)` (domain/model/PlanResolver.kt) is the single resolver: dated `DayPlan` rows → weekly `WeeklyPlan` → every-day only for habits with no schedule (`datedOnly`/sheet-managed habits never fall through).
- Completions: daily keyed `(planId, date)`; weekly `(weeklyHabitId, weekStart)`. Month is a derived window.
- Sheet `Plan` tab columns: `ID, Date, Habit, Session, Done, Skip` (or 8-column OND layout `ID, Date, Area, Habit, Session, Done, Skip, Source`). Sheet wins on conflicts, except local check-offs flagged *pending upload* (explicit per-completion flag, no clock comparison), which are uploaded first.

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

### P1 — robustness
- No logging or crash reporting; 31 `catch` blocks in `commonMain` (V).
- Backup (`domain/backup/BackupModels.kt`) serializes only `HabitSnapshot`; sheet link/theme/onboarding and `sheetManagedHabitIds` (`@Transient`) are excluded; `restoreFromSnapshot` wipes `sheet_managed_habits` (V).
- `AndroidBackupService` import reads lines and concatenates without newlines (V). iOS backup is clipboard-based (README).
- `allowBackup="true"` and no `dataExtractionRules` → the whole DB (with sync state) goes to Google backup (V).
- `deleteDailyHabit` cascades plans and completions (V schema); confirm UI guards (DestructiveActionsTest exists).
- `SystemDateProvider` uses device TZ; midnight rollover/travel untested (I).
- 3 SQLDelight migrations (`1.sqm`–`3.sqm`); only `PlanMigrationTest` covers a step (V).

### P1 — architecture & code quality
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
