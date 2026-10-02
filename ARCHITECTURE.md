# Architecture

## Model

Habit definitions are reusable activity templates with a category and an action/avoidance kind. A dated plan row has a stable `ID`; multiple rows may use the same habit and date. A daily completion is keyed by `(planId, date)` and a weekly completion by `(weeklyHabitId, weekStartDate)`. `MonthKey` is a derived calendar window, never a database container. Removing or moving a planned row does not delete its completion history.

`HabitSnapshot.plannedHabitsOn(date)` is the single plan resolver. Dated sessions win; otherwise a weekly weekday prescription applies; otherwise a habit with no schedule stays due every day (legacy simple trackers). Habits with `datedOnly`, any weekly plan, or sheet management never fall through to that every-day default. Sheet-imported habits are plan-driven (`datedOnly`). Month progress uses planned, non-skipped sessions.

## Persistence

SQLDelight stores categories, daily habits, weekly habits, and completion records in SQLite. Stable text IDs and update timestamps make rows individually addressable. Unique completion keys make writes idempotent. The repository also stores the selected Sheet link, last successful sync, and keys previously imported from that Sheet; it never stores Google tokens. The repository exposes domain models only; generated SQL rows do not cross the data boundary.

`HabitRepository` is the contract used by presentation code. `LocalHabitRepository` is the production implementation. `SheetSync` uses the repository's local cache and the Google Sheets API without changing the presentation data model.

## Calculations

`HabitCalculations` contains pure summary functions. Month, Today, and Sheet export all resolve due work through `plannedHabitsOn`. `MonthEngine` handles leap years and the workbook's five day blocks.

## UI and presentation

Compose Multiplatform UI is shared across Android and iOS. `MonthViewModel` and `ManageHabitsViewModel` combine repository state with pure calculations into immutable UI state. Width-based layout primitives size the month grid and day plan; phone vs tablet is a breakpoint on available width, not device-specific business logic. The month grid shows only habits planned in that month and marks unplanned days as non-actionable (not empty checkboxes).

## Module boundaries

The project intentionally uses one shared application module rather than many Gradle modules. Packages maintain the useful boundaries:

- `domain.model`: persistent-history concepts
- `domain.calculation`: workbook formula translation
- `domain.repository`: data contract
- `data`: SQLDelight mapping and local repository
- `presentation`: UI state and view models
- `ui`: shared Compose screens and design system
- `sync`: shared Plan table validation, download, and completion upload
- `androidMain` / `iosMain`: database drivers, platform entry points, and Google authorization bridge

## Sync limits

The `Plan` tab is authoritative for dated sessions. App check-offs newer than the last successful sync are uploaded, and remote completion values are imported for other rows. The client tracks previously imported keys to apply Sheet row deletions without clearing unrelated local plans. It does not merge simultaneous edits to one row, sync weekly-habit definitions/categories, or provide a change-history UI. The simple last-upload-wins behavior should be revisited before broad production distribution.
