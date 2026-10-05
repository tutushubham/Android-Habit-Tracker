# ADR 0004: Hand-written composition root, no DI framework

- Status: accepted (recorded in P1-2 step 12, 2026-10-05)

## Context

The app has one Gradle module, one repository, one sync engine, four ViewModels and a few platform services
(database driver, Google token provider, logger, backup, share, startup recovery). Koin, Kodein, kotlin-inject and Hilt
(Android only) were options.

## Decision

Keep `AppGraph` as the composition root: a plain class that constructs the repository, sync, scheduler and the
ViewModel factory from the platform pieces passed in by `MainActivity` / `MainViewController` through
`AppStartup.create`. Interfaces (`HabitStore`, `SettingsStore`, `BackupStore`, `SheetTokenProvider`, `Logger`,
`BackupService`, `DateProvider`) are the seams tests use; fakes are passed through constructors.

## Consequences

- No reflection, annotation processing or runtime container; the whole graph is about 90 lines, readable in one go,
  and identical on Android and iOS.
- Adding a dependency means editing `AppGraph` (and its tests); acceptable at this size.
- Revisit if the graph grows past a few dozen objects or needs per-screen scopes; kotlin-inject (compile-time, KMP)
  would be the first candidate.
