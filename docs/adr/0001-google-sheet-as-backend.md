# ADR 0001: The person's Google Sheet is the backend

- Status: accepted (P0-B; recorded in P1-2 step 12, 2026-10-05)

## Context

The app has to sync plans and check-offs between a phone, a tablet and a laptop, for anyone who installs it, without
the publisher running servers, holding accounts or seeing anyone's data (see `docs/production-plan/README.md`).

## Decision

- Local SQLite (SQLDelight) is the source the UI reads; the app works fully offline.
- The only shared store is a Google Sheet the person owns, linked by URL. The app reads and writes exactly one tab,
  `Plan` (`ID, Date, Habit, Session, Done, Skip`, or the 8-column layout), with the person's own OAuth token; other tabs
  are never touched and a `Plan` tab that does not match the schema is never modified.
- Sync is split so every decision is pure and testable: `SheetsApi` (HTTP), `PlanTable` (parse/serialize),
  `PlanReconciler` (decisions), `SheetSync` (orchestration, one atomic `applySheetSync`), `SyncScheduler` (debounce,
  retries). Conflicts: the sheet wins, except check-offs made on the device and flagged *pending upload*; no device
  clocks are compared. Details: `SHEET_SYNC.md`.

## Consequences

- No backend, no analytics, no accounts of ours; privacy story and store forms stay simple.
- Needs the sensitive `spreadsheets` OAuth scope (decision D3) and Google verification before a public release.
- Simultaneous edits to one row: the last device to sync wins. Weekly habits and categories are local only.
