# Progress

Tick when the plan's *Definition of done* is fully met and merged.
Legend: **[Done]** = code, automated tests and docs complete and on `master`. Items that can only be checked on a real device are listed separately under *Manual checks still owed*.

## Part 1 — Local
- [Done] P0-A Product split & seed removal (on `master` via PR #1; manual device check, step 9, still owed)
- [Done] P0-B Sync correctness (on `master`; manual two-device checks still owed)
- [Done*] P0-C Release build config (steps 1–11 on `master`; *code, automated checks and docs complete; owed: release-APK device smoke test and the `[mac]` iOS verification, see below)
- [ ] P1-1 Robustness & data safety
- [ ] P1-2 Architecture & code quality
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

## Manual checks still owed (need a device / your Google account)
- P0-A step 9: install over the existing build on your phone; habits, plans and completions intact; sheet still connects.
- P0-A: fresh emulator install is empty and shows the tutorial once.
- P0-B (from the plan, section 4): (1) 20 quick check-offs produce one coalesced sync; (2) airplane mode, toggle, reconnect → uploaded (now also automatic); (3) edit a session in the sheet → appears after sync; (4) rename a habit in the sheet on all its rows → local habit renamed, no duplicate; (5) revoke app access in the Google Account → clear message, local data intact; also try **Disconnect**.
- iOS: build and run on a Mac (`[mac]`): the Swift token provider is wrapped by `SerializingTokenProvider` but not run on a device.

## Known limitations carried forward (not bugs in P0-A/P0-B/P0-C scope)
- `restoreFromSnapshot` does not reset sync keys / last-sync; backup excludes sheet link and pending marks → **P1-1**.
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
<!-- One line per session: date · plan · outcome · branch/commit -->
2026-10-02 · P0-A · steps 2–8 done on `prod/p0-a-product-split` (seed removed, SheetSync de-OND'd with rolling window, personal files moved, docs rewritten, tutorial restored); 87 JVM tests green; step 9 manual device checks pending · merged as PR #1 (`cc4660d`)
2026-10-02/03 · P0-B · steps 1–11 done (`5dcc123` … `56e97a8`); 190 JVM tests green · rebased onto master
2026-10-03 · P0-A/B completion review · iOS compile fix, offline auto-retry, coverage gaps, docs refresh, graphify installed; 196 JVM tests green; work continues directly on `master`
2026-10-03 · P0-C · steps 1–11 done directly on `master` (`0591134` … `9edcc91`); assembleDebug, assembleRelease/bundleRelease (test keystore) and unit tests OK; device + Mac verification pending
2026-10-03 · P0-C audit · full rebuild green (`--rerun-tasks`: debug + release + unit tests + iOS klib compile); dead check in `SyncError.kt` removed, AGP deprecation noise silenced via `gradle.properties` (that file is part of the still-uncommitted Gradle/AGP upgrade), docs refreshed
