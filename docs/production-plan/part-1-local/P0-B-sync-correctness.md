# P0-B — Sync correctness (Google Sheet as the backend)

**Goal:** the Sheet-as-backend loop is correct, safe against partial failure, quiet on the network, and understandable to a non-technical user. Phone and iPad can both write without corrupting each other's data in realistic use.

**Depends on:** P0-A (OND rules removed).
**Branch:** none — work directly on `master` (no branches).
**Out of scope:** real-time collaboration, merge UIs, conflict history, new sync features. Keep "last upload wins" semantics but make them deterministic and honest.

## 1. Code analysis

File: `composeApp/src/commonMain/kotlin/com/habitsheet/sync/SheetSync.kt` (370 lines). Related: `AppGraph.kt`, `MonthViewModel.kt` (`onLocalChange`), `HabitRepository` sheet-state methods, `Habits.sq`, `androidMain/.../AndroidSheetTokenProvider.kt`, `iosApp/iosApp/HabitSheetApp.swift` (token provider).

Findings:
1. **Name-based identity.** Local habit ↔ sheet row link is `row.habit.trim().lowercase()` (`localHabits` map). Rename in sheet or app → silent new habit; duplicate names → `require` aborts sync with a user-facing exception string.
2. **Non-atomic local writes.** The loop calls `repository.saveDailyHabit/saveDayPlan/setDailyCompletion` row by row (each takes the mutex and reloads the whole snapshot). A mid-sync failure leaves partial state; `lastSync` isn't advanced but rows already changed.
3. **Chatty.** `AppGraph` passes `onLocalChange = { syncScope.launch { sheetSync.sync() } }` — every checkbox tap launches a sync; each does `hasPlanTab` + `readTable` (`Plan!A:H`) and possibly `writeDone`. Rapid taps queue behind the `Mutex`.
4. **Clock-based pending detection.** `localDone.updatedAtEpochMillis > lastSync` compares device time with device time, OK on one device but fragile across clock changes; a remote edit between syncs can be overwritten.
5. **Deletion semantics.** A row missing in the sheet deletes the local plan only if its key was in `oldKeys`; seeded-key logic (removed in P0-A) was part of that.
6. **Network hygiene.** `HttpClient()` — no timeouts, retries, backoff, no `Retry-After`/429 handling (`check()` maps only 401/403/404).
7. **Sheet parsing/formatting.** `putTable` writes `RAW` strings; `readTable` expects `UNFORMATTED_VALUE` and handles serial dates; checkbox formatting only on first creation; `Plan!A:H` assumed.
8. **Error UX.** Errors become `e.message?.take(180)`; no distinction between offline, auth, quota, malformed sheet.
9. **Token handling.** Android `AndroidSheetTokenProvider` stores a single `pendingCompletion` callback (concurrent requests overwrite it). iOS provider handled in Swift.
10. **First-sync paths.** "No Plan tab" creates the tab and uploads local rows; "tab exists" makes Sheet authoritative; there is no confirmation for a destructive overwrite of an existing `Plan` tab that belongs to something else.

## 2. Steps

1. **Characterization/fake-server tests first.** Build a `FakeSheetsServer` (Ktor `MockEngine` already in test deps) that models spreadsheet metadata, `Plan` values, `batchUpdate`, `values:batchUpdate`, append; support injected failures (401, 403, 404, 429, 5xx, timeout, mid-sequence failure). Pin today's behaviour for: new tab, existing tab, local-only habits, row deletion, done-sync both directions.
2. **Split `SheetSync`** into: `SheetsApi` (HTTP only), `PlanTable` (parse/serialize; pure), `PlanReconciler` (pure: `(localSnapshot, remoteRows, syncState) -> ReconcileResult` listing habits/plans/completions to apply locally + done-updates to push), and `SheetSync` (orchestration). Reconciler must be unit-testable with zero IO.
3. **Stable identity.** Use the sheet `ID` column as the plan identity (already so) and add a durable habit identity: either a hidden/extra `HabitID` column written by the app and tolerated when absent, or keep name matching but make rename/duplicate handling explicit (surface "two habits share a name" as a resolvable state, never an exception). Pick the lowest-risk option; document in `SHEET_SYNC.md`. Must remain compatible with sheets that only have the six/eight documented columns.
4. **Atomic local apply.** Add a repository method `applySheetSync(result: ReconcileResult, newKeys, lastSync)` that runs in **one** SQLDelight transaction and reloads the snapshot **once**. Remove per-row repository calls from the loop.
5. **Debounce & coalesce.** Replace "sync per tap" with a coalescing trigger: mark dirty, wait ~2–3 s of quiet, then one sync; also sync on foreground and on explicit "Sync now". If a sync is running, set a flag to run exactly one more afterwards. Keep local check-offs instant (they are already local-first).
6. **Deterministic conflicts.** Use a per-completion logical rule that doesn't rely on comparing device clock to server time: store `pending_upload` (dirty flag) per completion locally; push dirty ones; clear on successful write; remote wins otherwise. Document that simultaneous edits of the same checkbox = last writer wins.
7. **HTTP hardening.** Configure Ktor `HttpTimeout`, bounded retry with exponential backoff + jitter for 429/5xx/IOException, respect `Retry-After`; map statuses to typed errors: `Offline`, `AuthExpired`, `AccessDenied`, `NotFound`, `RateLimited`, `MalformedPlanTab(rowN, reason)`, `Unknown`.
8. **User-facing errors.** Map typed errors to short, actionable messages in `SettingsViewModel`/state (no raw exception text). Offline is not an error — show "Will sync when online".
9. **Plan-tab safety.** Before overwriting/creating, validate headers; if a `Plan` tab exists but doesn't match the schema, **don't touch it**; show how to fix. Never modify other tabs (already stated in docs; add a test).
10. **Token provider hardening.** Make Android `requestToken` safe for concurrent calls (queue/serialize); handle `RESULT_CANCELED`, revoked access, and sign-out; add a "Disconnect" path that clears the link and sync state without deleting local data (verify the existing UI offers this; if not, add the minimal action — this is a correctness gap, not a feature).
11. **Docs.** Update `SHEET_SYNC.md`: schema, identity rules, conflict rules, error meanings, what happens offline.

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md, 00-context/DECISIONS.md and part-1-local/P0-B-sync-correctness.md. P0-A must already be merged (verify: no OndSeedData in commonMain). Work directly on master (no branches). Use graphify-out/GRAPH_REPORT.md if present. No new features; keep documented Plan-tab schema compatibility. Summarise the plan in 8 lines and wait for my go.
```

### Step prompts
```
Step 1: Build a FakeSheetsServer for tests using ktor-client-mock (already a test dependency) that models spreadsheet metadata, Plan values (A:H), values:batchUpdate, append and addSheet, with injectable failures (401/403/404/429/5xx/timeouts, failure after N calls). Write characterization tests of CURRENT SheetSync behaviour: creates Plan tab when missing, sheet-wins when present, uploads pending Done, deletes local plans for rows removed from the sheet, local-only habits appended. All must pass on current code. Report coverage gaps you notice.
```
```
Step 2: Refactor SheetSync.kt into SheetsApi (HTTP), PlanTable (parse/serialize, pure), PlanReconciler (pure function from local snapshot + remote rows + sync state to a ReconcileResult) and a thin SheetSync orchestrator. Behaviour must not change; characterization tests from step 1 must stay green. Add focused unit tests for PlanReconciler with no IO.
```
```
Steps 3-4: Make habit identity explicit and make local apply atomic. Propose (as a short design note in docs/production-plan/notes/p0-b-identity.md) the lowest-risk way to stop name-based matching from silently creating or colliding habits while staying compatible with 6/8-column sheets. After I approve, implement it, add HabitRepository.applySheetSync(result, newKeys, lastSync) that applies everything in ONE SQLDelight transaction and reloads the snapshot once, and remove per-row repository calls from the sync loop. Add a test that injects a failure mid-apply and proves nothing is partially written.
```
```
Steps 5-6: Replace sync-per-tap with a coalescing scheduler (debounce ~2-3s, one follow-up run if dirtied during a sync, immediate on foreground and manual Sync now). Replace the updatedAt > lastSync pending detection with an explicit per-completion pending-upload flag (add a SQLDelight migration 4.sqm that is non-destructive and backfills sensibly; add a migration test). Document the conflict rule. Keep tests green.
```
```
Steps 7-8: Harden networking: Ktor HttpTimeout, bounded exponential backoff with jitter for 429/5xx/IO errors honouring Retry-After, and a sealed SyncError hierarchy (Offline, AuthExpired, AccessDenied, NotFound, RateLimited, MalformedPlanTab(row, reason), Unknown). Map them to short actionable user messages in SettingsViewModel; offline must read as "Will sync when online", not an error. Add fake-server tests for each error.
```
```
Steps 9-11: Ensure a non-matching existing Plan tab is never modified, and other tabs are never touched (tests). Make AndroidSheetTokenProvider safe for concurrent requestToken calls and cancelled/revoked authorisation; verify there is a Disconnect action that clears link+sync state without deleting local data (add minimal action only if missing). Update SHEET_SYNC.md (schema, identity, conflicts, errors, offline behaviour).
```
```
Run the whole plan: execute P0-B steps 1-11 in order, one commit per step, run all tests, and finish with the Definition-of-done checklist. Pause for my approval at the identity design note (step 3).
```

## 4. Verification
```
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:compileKotlinIosSimulatorArm64   # [mac] or CI later
```
Manual with your own sheet on two devices: (1) toggle 20 checkboxes quickly → network log shows one coalesced sync; (2) airplane mode, toggle, reconnect → uploaded; (3) edit a session text in the sheet → appears after sync; (4) rename a habit in sheet → no duplicate silent habit (follows the chosen identity rule); (5) revoke app access in Google Account → clear message, local data intact.

## 5. Definition of done
- [x] `SheetSync` split into API / table / reconciler / orchestrator; reconciler is pure and tested.
- [x] Local apply is one transaction with one snapshot reload; mid-failure test passes.
- [x] Sync is coalesced; no sync-per-tap.
- [x] Pending uploads tracked by explicit flag (migration + migration test).
- [x] Typed errors + timeouts + retry/backoff; friendly messages; offline is not an error.
- [x] Existing/foreign `Plan` tab and other tabs are never overwritten (tests).
- [x] `SHEET_SYNC.md` updated; tests green; `PROGRESS.md` ticked.

Status 2026-10-03: all boxes met in code and automated tests (196 JVM tests; iOS klib compile passes). The manual two-device checks in section 4 are still owed and tracked in `PROGRESS.md`. Added during the completion review: automatic retry while offline with pending check-offs (`SyncScheduler.shouldRetry`).

## 6. Handoff
P0-C needs: no changes to sync, but needs the client IDs/scope moved to config. P1-1 will add logging hooks into the new `SyncError` paths.
