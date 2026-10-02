# P0-B steps 3–4 — habit identity and atomic apply (design note)

Status: **approved and implemented** (Q1: sheet wins, rename reverts; Q2: skip + warn; Q3: empty-tab guard included).

## Problem (verified in `PlanReconciler` / `SheetSync` after the step-2 split)

Plan *rows* already have a stable identity (the `ID` column ↔ `dayPlanEntity.id`). The *habit* a row belongs to is found only by `habit.trim().lowercase()`. Consequences today:

| Situation | Today |
|---|---|
| Sheet user renames "Run" → "Jog" on all rows | Unknown name → new habit "Jog" created, every plan moved to it, old "Run" left behind, still sheet-managed (silent duplicate) |
| App user renames a sheet-managed habit | Same: sheet rows still say the old name → new habit created, rename effectively lost, orphan left |
| Two local habits normalise to the same name | `require(...)` throws; whole sync aborts with an exception string, nothing syncs until the user renames one |
| Typo in one sheet row ("Rnu") | New dated-only habit silently created |

## Options considered

**A. Hidden `HabitID` column written by the app (rejected).** Truly durable, but: changes the documented 6/8-column schema (readers must tolerate it, writers must append it to existing user sheets = modifying a tab we promised only to read/append to), needs a backfill write to every existing row, breaks "paste your own sheet" for hand-built tabs, and a user can delete or sort it away. Highest risk; no schema compatibility win.

**B. Derive habit identity from plan-row identity (recommended).** The sheet already tells us which local habit a row belongs to: a row whose `ID` matches a local plan is *that plan's habit*, whatever its name says. No new column, no sheet writes, no migration, fully compatible with 6- and 8-column sheets and with sheets edited by hand.

## Recommended rules (option B)

Resolving the habit for each remote row, in order:

1. **Known plan ID** → candidate habit = the habit that owns that local plan.
2. Otherwise **match by normalised name** among local habits (as today).
3. Otherwise **create** a dated-only habit (as today).

Name disagreements for case 1 (sheet name ≠ candidate habit's current name):

- **Rename:** if *every* remote row belonging to that habit's known plans carries the same new name, and no other local habit already has that name → rename the local habit to the sheet's spelling (sheet wins). No duplicate is created.
- **Move:** otherwise the row is re-resolved by name via rules 2/3 (a single row retyped to another habit moves that session — same as today).

Duplicate local names (never an exception again):

- Rows whose ID is known resolve through rule 1 and sync normally.
- A row with an **unknown ID** whose name matches **two or more** local habits is **skipped** (not guessed). The sync completes, and reports `"N rows skipped: 'Run' matches 2 habits — rename one"` as a warning in the status line. These rows are retried each sync, so renaming one habit resolves it with no further action.
- Local-only append (`localOnlyRows`) already keys on id, so duplicates need no special handling there.

`ReconcileResult` gains `habitRenames: List<HabitRename>` and `warnings: List<SyncWarning>` (typed; mapped to text in step 8).

**Documented consequence (needs your decision, see Q1):** names are *sheet-wins*. An app-side rename of a sheet-managed habit is reverted on the next sync, because the app never writes the Habit column back. That is a deliberate limitation, strictly better than today's duplicate-and-orphan; making the app push renames would be a new write path.

## Atomic local apply (step 4)

- New domain type `SheetSyncChanges(planIdsToDelete, habitsToCreate, habitRenames, plansToSave, completionsToSave, managedHabitIds)` in `domain/model`; `ReconcileResult` composes it plus the upload list and warnings (so the repository interface does not depend on the `sync` package).
- `HabitRepository.applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long)`:
  - `LocalHabitRepository`: under the existing mutex, **one** `database.transaction { … }` doing deletes → renames → habit inserts → plan upserts → completion upserts → managed ids / synced keys / `lastSync` settings; **one** `loadSnapshot()` afterwards. Any exception rolls the whole transaction back and leaves the cached snapshot untouched.
  - `InMemoryHabitRepository`: builds the new state on a copy and swaps it in once (same all-or-nothing contract).
- `SheetSync` order becomes: read → reconcile → **upload Done checks** → `applySheetSync` once. Uploading first means a failed upload changes nothing locally (today session edits are already applied when the upload fails; step-1 test `failedDoneUploadStillLeaves…` will be updated to assert the new, safer behaviour). If the upload succeeds but the local apply then fails, the next sync is idempotent (the sheet already holds the checks).
- The new-tab path also calls `applySheetSync` (empty changes + keys + managed ids + `lastSync`) so it too is a single write.
- Per-row `saveDailyHabit/saveDayPlan/setDailyCompletion/deleteDayPlanById/setSheet*` calls disappear from the sync loop.

## Tests (planned)

- Reconciler (pure): rename detected; rename blocked when new name already exists; single-row retype is a move; duplicate local names → known IDs sync, unknown-ID rows skipped with a warning, no exception.
- Characterization updates: duplicate-name test now expects a completed sync + warning; failed-upload test expects no local changes.
- Repository (SQLite JVM): `applySheetSync` writes everything in one transaction; **failure injection** (a change referencing a non-existent habit / forced SQL error mid-apply) → assert tables, settings and cached snapshot equal their pre-apply values. Same contract test run against `InMemoryHabitRepository`.
- Snapshot-reload count: a counting wrapper/driver asserts one `loadSnapshot` per apply.

## Not changing

Sheet schema (6/8 columns), `ID` semantics, last-upload-wins rule, `lastSync`-based pending detection (that is step 6), network behaviour.

## Questions for you

1. **Q1 — App-side rename of a sheet-managed habit:** accept "sheet wins, rename reverts" for now (recommended; documented in `SHEET_SYNC.md`), or should the app write renamed names back to the Habit column (new write path, more risk)?
2. **Q2 — Unknown-ID rows with an ambiguous name:** skip + warn (recommended) or create a third habit? Skipping never invents data.
3. **Q3 — Also pinned by the step-1 tests and a data-loss risk:** an emptied `Plan` tab (header only) deletes all previously synced local plans. Out of scope for 3–4 unless you want a guard now (recommended guard: if the remote has zero data rows but `oldKeys` is non-empty, treat it as "sheet cleared" and skip deletions + warn). Include in this step, or defer to step 9?
