# Architecture

## Model

Habit definitions are independent of months. A daily completion is keyed by `(habitId, date)` and a weekly completion by `(weeklyHabitId, weekStartDate)`. `MonthKey` is a derived calendar window, never a database container. Archiving a habit preserves its existing completion history.

## Persistence

SQLDelight stores categories, daily habits, weekly habits, and completion records in SQLite. Stable text IDs and update timestamps make the rows individually addressable for a future sync implementation. Unique completion keys make writes idempotent. The repository exposes domain models only; generated SQL rows do not cross the data boundary.

`HabitRepository` is the contract used by presentation code. `LocalHabitRepository` is the production implementation. A server-backed or syncing repository can later implement the same interface and retain the current UI and calculations.

## Calculations

`HabitCalculations` is a pure common-code translation of the workbook formulas. It calculates per-day, per-habit, category, daily-week, weekly-habit, overall-weekly, and monthly summaries. `MonthEngine` handles leap years, 28/29/30/31-day months, weekday metadata, and the workbook's five day blocks.

## UI and presentation

Compose Multiplatform UI is shared across Android and iOS. `MonthViewModel` and `ManageHabitsViewModel` combine repository state with pure calculations into immutable UI state. Width-based layout decisions provide a compact phone view and a multi-pane tablet view without device-specific business logic.

## Module boundaries

The project intentionally uses one shared application module rather than many Gradle modules. Packages maintain the useful boundaries:

- `domain.model`: persistent-history concepts
- `domain.calculation`: workbook formula translation
- `domain.repository`: data contract
- `data`: SQLDelight mapping and local repository
- `presentation`: UI state and view models
- `ui`: shared Compose screens and design system
- `androidMain` / `iosMain`: database drivers and entry points only

## Future server persistence

A future `SyncingHabitRepository` can compose the existing local database with a remote API. Completion rows already have stable composite identities and timestamps. Conflict resolution, accounts, networking, and sync are deliberately not implemented now.
