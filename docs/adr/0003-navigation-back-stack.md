# ADR 0003: A small hierarchical back stack instead of Navigation Compose

- Status: accepted (P1-2 step 6, 2026-10-05)
- Context: P1-2 plan, step 6

## Context

Until P1-2 the current screen was a `remember`ed enum in `ui/HabitSheetApp.kt`. There was no back stack: Android's
system back (and the predictive-back gesture enabled in P0-C) left the app from any screen, the iOS edge swipe did
nothing, and the screen was forgotten when Android killed the process. Phone and tablet layouts each called the
destination host separately (14 parameters, duplicated).

The app has six screens and one fixed hierarchy: Tracker is the start screen; Plan, Habits and Settings sit under it;
Data & Backup and About sit under Settings. Every screen's on-screen back button already went to that parent.

## Decision

Keep navigation in-house: `ui/navigation/AppBackStack.kt` (~70 lines).

- The back stack is the chain of parents to the current screen (`Tracker > Settings > Backup`). Navigating anywhere
  replaces it with that screen's chain, so **Back always means "up one level"**, identical to the on-screen back
  buttons. Jumping between sections (tablet sidebar, phone bottom bar) does not pile up history.
- System back: `BackHandler(enabled = backStack.canGoBack) { backStack.back() }` from Compose Multiplatform's
  `ui-backhandler` (same version as Compose, already in the app's dependency graph; now declared directly). On
  Android this is the activity's back dispatcher (predictive back included); on iOS, Compose Multiplatform 1.11 turns
  the screen-edge swipe into the same event. On the start screen the handler is disabled, so the platform handles
  back as before (Android leaves the app).
- `rememberSaveable(saver = AppBackStack.Saver)` keeps the current screen across configuration changes and Android
  process death. A saved state is rebuilt from its last screen, so it is always a valid chain.
- One destination host (`AppDestination`) for both layouts; `AdaptiveFrame` adds the tablet sidebar around it.

`BackHandler` is marked deprecated in favour of `NavigationEventHandler` from `navigationevent-compose`; the call is
annotated `@Suppress("DEPRECATION")` until the app depends on that artifact (it is not in the dependency graph for iOS
today). Switching is a one-line change.

## Alternatives considered

**Compose Multiplatform Navigation (`org.jetbrains.androidx.navigation:navigation-compose`).** Rejected for now:

- It changes what people see: `NavHost` animates between destinations by default, and keeps each back-stack entry's
  saved state (scroll positions, open dialogs) when returning, which the app never did. Both can be configured away,
  but then the library adds little.
- The ViewModels are scoped to `AppGraph`, not to navigation entries, so per-destination ViewModel stores would go
  unused.
- A pure state holder is unit-testable in `commonTest` (`AppBackStackTest`); `NavHost` behaviour needs UI tests.
- It is another dependency to keep in step with Compose Multiplatform.

Revisit when the app gains screens with arguments, deep links, or real history (not just "up").

## Consequences

- Behaviour change (intended by the plan): system back / edge swipe now go up one level instead of leaving the app;
  after Android process death the app reopens on the screen it was on.
- `NavigationWiringTest` (androidUnitTest) guards the wiring in `HabitSheetApp.kt` that unit tests cannot reach.
- The startup failure screen stays outside this navigation (nothing can be created without a database).
