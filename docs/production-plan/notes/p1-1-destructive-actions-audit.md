# P1-1 steps 6-7: destructive actions audit

Scope: every path that can call `delete*`, `clearAllData` or `restoreFromSnapshot` (and the related `disconnect` / sheet-link changes). Audited against the code on `prod/p1-1-robustness`.

Legend — **Confirmed** = a dialog that names what is affected is shown before the call. "Before" is the state at the start of this step.

| # | Action (UI entry) | Reaches | Before | Now | Test |
|---|---|---|---|---|---|
| 1 | Reset everything (Data & Backup) | `BackupViewModel.clearAllData` → `repository.clearAllData` | Confirmed, generic text ("all your habits…"); did **not** clear sheet link or sync state, so Settings kept showing a link and "Synced…" for data that was gone | Confirmed with counts and local-only wording: "deletes N daily habits … on this device and disconnects the sheet link. Your Google Sheet is untouched." Clears link, synced keys, last sync, managed habits (pending flags go with the completions). Theme is kept. Settings re-reads storage afterwards | `DestructiveActionsConfirmationTest` (text, repo reset, notification, settings reload); `BackupRestoreSqliteTest.resetClears…` (real SQLite + restart) |
| 2 | Import backup (Data & Backup) | `BackupViewModel.importBackup` → `restoreFromSnapshot` | Confirmed, generic; the dialog is shown **before** the file is chosen, so it cannot name the file. Sync state was not cleared | Confirmed with what will be replaced (counts), says the sheet is untouched. The file is fully parsed, checksummed and validated before anything is replaced (one transaction; bad file changes nothing). Sync state always reset; Settings re-reads storage | Step 3/4 tests; `DestructiveActionsConfirmationTest` |
| 3 | Delete daily habit (Manage → editor → Delete) | `ManageHabitsViewModel.deleteDailyHabit` → cascades completions, day plans, weekly plans | Confirmed ("Delete 'X'? This cannot be undone.") but did not say history goes with it | Names the habit and the check-offs and sessions that go with it; points to Archive | `DestructiveActionsConfirmationTest.deletingAHabit…`; `DestructiveActionsTest` (VM) |
| 4 | Delete weekly habit | `deleteWeeklyHabit` → cascades weekly completions | as 3 | Names it and its check-offs | same |
| 5 | Delete category (Manage → categories) | `deleteCategory` → habits keep existing, `category_id` set NULL | Confirmed, generic | Names it and how many habits lose it ("stay, without a category") | same |
| 6 | Remove day session (Plan → "Remove plan for this day") | `MonthViewModel.deleteDayPlanById` | **Unguarded**: one tap deleted the session | Confirmed, names habit, date and session; says recorded check-offs are kept | `DestructiveActionsConfirmationTest.removingASession…` |
| 7 | Remove weekly session (Plan → "Remove weekly session") | `deleteWeeklyPlan` | **Unguarded** | Confirmed, names habit, weekday and session | same |
| 8 | Disconnect sheet (Settings) | `SettingsViewModel.disconnect` → `setSheetUrl("")` (clears sync state, pending flags) | No dialog; deliberately left: no habit, plan or check-off is removed (the checked state stays, only the "not yet uploaded" marks go) and the sheet is untouched | unchanged; listed in the guard test as reviewed | `DisconnectTest` |
| 9 | Change the sheet link (Settings → Save link) | `setSheetUrl(new)` clears sync state | No dialog; same reasoning as 8; the new sheet becomes the source of truth on the next sync | unchanged | `SheetSync*` tests |
| 10 | `MonthViewModel.deleteDayPlan(habitId, date)` | repository | Not used by any screen | unchanged (no UI path) | — |
| 11 | Sheet sync removes local day plans the sheet no longer has | `applySheetSync(planIdsToDelete)` | System path, not a user action; "sheet wins" is the documented rule | unchanged | `PlanReconciler`/sync tests |
| 12 | Sync removes legacy `"Plan in OND sheet"` weekly placeholders | `SheetSync.removeLegacyWeeklyPlaceholders` | System path, one-off cleanup of a placeholder that is not a real session | unchanged | `SheetSyncTest` |
| 13 | Archive habit (editor) | `archiveDailyHabit` / `archiveWeeklyHabit` | Not destructive (restorable); it is the default affordance and the delete dialog now points to it | unchanged | `ManageHabitsViewModelTest` |
| 14 | First-run / empty DB seeding, `restoreDailyHabit` etc. | — | not destructive | — | — |

## Guard against regressions

`DestructiveCallSitesTest` (JVM) scans `commonMain/.../ui/*.kt`:
- the set of destructive view-model calls per screen must equal the reviewed list above (a new `delete…`/`clearAllData`/`importBackup`/`disconnect` call in a screen fails the test until it is reviewed and given a `DestructiveAction` text);
- every screen on that list must show `DestructiveConfirmDialog` and build a `DestructiveAction`;
- screens must not call the repository's `restoreFromSnapshot` / `delete*` directly.

The test checks structure, not that each dialog is visually reachable; Compose UI tests are P2.

## Known gaps (not changed — they need a decision or a feature)

1. **No undo / safety copy before a restore or reset.** Both are irreversible by design ("This cannot be undone"). A one-step "export a backup first" prompt or an automatic pre-restore copy would remove the risk; that is a feature, so it is listed for P1-3 rather than built here.
2. **Import asks for confirmation before the file is chosen**, so the dialog shows what will be *replaced*, not what the file contains. Showing the backup's own counts would need a two-step flow (pick, preview, confirm).
3. **A sync running at the moment of a reset** can re-apply rows read from the sheet before the reset finished (the link is already read). The next sync with an empty link does nothing, and the data came from the person's own sheet, but the screen may briefly show it.
4. **Deleting a sheet-managed habit** is allowed locally; the next sync recreates it from the sheet (sheet wins). The dialog does not say so.
