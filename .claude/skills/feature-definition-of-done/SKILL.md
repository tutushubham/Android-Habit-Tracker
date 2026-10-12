---
name: feature-definition-of-done
description: Use when adding or modifying any feature, screen, ViewModel, repository method, sync behaviour, backup format or schema in the Habit Sheet app. Walks the change through the app's own standards (architecture, localised strings, accessibility, data safety, tests, docs) and reports what is met, missing or not applicable before the change is called done.
---

# Feature definition of done (Habit Sheet)

These are the standards Part 1 of the production plan put in place (`docs/production-plan/part-1-local/`, `docs/TESTING.md`,
`docs/accessibility-checklist.md`, `docs/adr/`, `ARCHITECTURE.md`). A new or changed feature must keep them true.
Do not invent rules: where this file and the repo disagree, the repo (tests, ADRs) wins; fix this file.

## How to run this skill

1. **Scope the change.** `git diff --stat` (or the files the user names). Classify it: UI / ViewModel / domain / data
   (schema?) / sync / backup / platform (Android, iOS) / widget. Only the sections that apply are in play; say which you skipped and why.
2. **Walk the checklist below** against the actual diff (read the code, do not assume). For each item mark
   `met`, `missing`, or `n/a` with the file and line.
3. **Fix what is missing** if the user asked for implementation; otherwise report it. A bug found on the way gets a
   failing test committed first, then the fix, in separate commits.
4. **Run the gates** (section 9) and report the real output, including failures.
5. **Report** as a short table: item, status, evidence. End with what you could not verify (devices, screen readers).

## 1. Architecture

- [ ] Layering is respected: `domain` (pure, no platform) → `data` (SQLDelight, behind the narrow store interfaces
      `HabitStore` / `SettingsStore` / `BackupStore`) → `sync` → `presentation` (ViewModels) → `ui` (Compose).
      The UI never touches SQL rows or the repository implementation; the repository exposes domain models only.
- [ ] Logic lives in `commonMain` unless it truly is platform code; platform code sits behind an interface in
      `commonMain` with `androidMain` / `iosMain` actuals (see `DriverFactory`, `Logger`, `StartupRecovery`).
- [ ] ViewModels are lifecycle-aware (ADR 0002): no manual `CoroutineScope`, state collected lifecycle-aware.
- [ ] New screens are destinations in the single back-stack host (ADR 0003); system/edge-swipe back works from them.
- [ ] No DI framework is introduced; wiring goes through `AppGraph` (ADR 0004).
- [ ] Files stay under ~500 lines in `ui/` and `data/` (`UiStructureTest`); split into leaf composables instead.
- [ ] Failures are typed and reach the person as a message (never swallowed, never a raw exception string).
- [ ] Any structural decision worth remembering gets an ADR in `docs/adr/`; `ARCHITECTURE.md` stays true.

## 2. Strings and localisation (ADR 0005)

- [ ] **No literal user-visible text in Kotlin.** Every word is in `composeApp/src/commonMain/composeResources/values/strings.xml`
      (plurals as `<plurals>`); screens use `stringResource` / `pluralStringResource`.
- [ ] ViewModels, `SyncError`, `DestructiveAction`, `BackupResult` and platform services return `UiText`
      (`Res`, `Plural`, `Raw` for data such as a habit name, `Joined`), rendered with `asString()` / `resolve()`.
- [ ] Android-only system text goes in `androidMain/res/values/platform_strings.xml`.
- [ ] Apostrophes are written plainly (no backslash); no unescaped `&` (`StringCatalogEscapesTest`).
- [ ] Dates, weekdays, months and percentages use the locale-aware helpers in `UiFormatting.kt` / catalog date shapes,
      never string concatenation of English; first day of week comes from `localeFirstDayOfWeek()`.
- [ ] Not translated on purpose: log messages, CSV header, backup validator diagnostics. Everything else is.
- [ ] Every string added is used and every string used is defined (`NoHardCodedTextTest`).

## 3. Accessibility (`docs/accessibility-checklist.md`)

- [ ] Interactive elements have a role and state (`toggleable` Checkbox, `selectable` Tab, Switch) and a label that
      says what and for whom ("<habit>, <date>"); state text is Completed / Incomplete / Not planned style.
- [ ] Icon-only buttons have a content description; purely decorative glyphs are hidden from semantics.
- [ ] Touch targets are at least 48 dp.
- [ ] Colours come from theme tokens with AA contrast (4.5:1 text, 3:1 control boundaries) in light **and** dark;
      add new tokens to `ThemeContrastTest`. Never use fixed colours on a themed surface.
- [ ] Layout survives 200% font scale (no fixed heights that clip text), phone and tablet, rotation, split view,
      safe areas (notch, home indicator) and the keyboard (focused field stays visible).
- [ ] Headings are marked; status messages that change are live regions; dialogs trap and restore focus.
- [ ] Animations respect Reduce Motion (`IosReduceMotion` / Android animator scale).
- [ ] System bar icons follow the **app** theme on both platforms.
- [ ] Add the new screen/control to the manual walkthrough in `docs/accessibility-checklist.md`
      ("Needs your verification") if it cannot be proven by a test.

## 4. Data safety

- [ ] A destructive or hard-to-undo action goes through `DestructiveAction`: a confirmation that names what is lost,
      Cancel changes nothing, and (where it applies) "Export a backup first" is offered. Register new call sites in
      `DestructiveCallSitesTest` after review.
- [ ] Multi-row writes are one transaction and are added to `AtomicWritesTest`.
- [ ] Writes are idempotent (stable text IDs, unique keys) and `updated_at` stays monotonic.
- [ ] **Schema change:** new `N.sqm` migration (never edit an old one), update `Habits.sq`, commit the new
      `databases/N.db` snapshot and its README row, and extend the migration tests
      (`everyReleasedVersionUpgradesInPlaceAndKeepsItsData`, migration verification, which `check` runs).
- [ ] **Backup format change:** bump the backup version, keep reading v1..vN, test old files and the preview dialog.
      Never put tokens or sync state in a backup.
- [ ] Startup failure still reaches the recovery screen (Try again / Save a copy / Start with empty data).
- [ ] Time handling goes through `DateProvider`; midnight and time-zone changes still move "today".
- [ ] Sync: never writes outside the Plan tab (`FakeSheetsServer.violations`), tokens are never stored, new failure
      kinds are a typed `SyncError` with their own message and a test at every request they can occur on.
- [ ] No personal data, IDs or credentials in tracked files (`Local.xcconfig`, `keystore.properties` stay ignored).
- [ ] Logs carry no user text (`FakeLogger` asserts it).

## 5. Tests: pick the lowest layer that shows the behaviour (`docs/TESTING.md`)

| Change | Test that must exist |
|---|---|
| Pure logic, calculations, plan resolution | `commonTest/.../domain` (+ a seeded property test if it has invariants) |
| New or changed store method | add to `HabitRepositoryContract` (runs on SQLite, in-memory double, native iOS driver) |
| New multi-row write | `AtomicWritesTest` |
| Sync behaviour | `commonTest/.../sync` with `FakeSheetsServer` (fault at each request, retry on virtual time) |
| ViewModel state / navigation / failure message | `commonTest/.../presentation` with `FakeDateProvider`, `FailingHabitStore` |
| A screen | scenario in `UiScenarios*.kt` using `text(Res.string.x)` and `UiWorld`, plus one `@Test` line in **both** `AndroidUiTests` and `IosUiTests`; scroll before tap (`performScrollTo`) |
| A key screen's look | add to `ScreenshotTests` (light/dark, phone/tablet, 200% text) |
| Android-only behaviour (activity, widget, pickers) | `androidInstrumentedTest` |
| iOS-only behaviour (driver, files, share sheet) | `iosTest`, or `iosApp/iosAppUITests` for a user flow |
| Anything R8 could break | `scripts/android/release_smoke.py` still passes |

Rules: no sleeping (`NoSleepInTestsTest`; wait on events/virtual time); a bug gets a failing test committed first;
avoid JVM-only APIs in `commonTest` (`String.format`, `Map.merge`); copy assertions go through the catalog, not
literals. UI scenario tests render screens in a phone-sized box (the iOS test window is 1024 dp wide and would pick
the tablet layout). Coverage floors are a ratchet (`coverageFloors` in `composeApp/build.gradle.kts`): raise, never lower.

## 6. Platform specifics

- [ ] Android: works with predictive back, rotation and process death; widget code uses `runWidgetWork`.
- [ ] iOS: share sheets get a popover source on iPad; file work cleans its temp files; edge swipe back works;
      anything new in `iosMain` has a compile check (`:composeApp:compileTestKotlinIosSimulatorArm64`).
- [ ] Logging on iOS uses `IosLogger` (the line is the format; **never** pass Kotlin strings as `NSLog` `%@` arguments).
- [ ] Native iOS SQLite opens lazily; do not assume the file exists before a statement has run.

## 7. Docs and records

- [ ] `ARCHITECTURE.md` / `SHEET_SYNC.md` / `WORKBOOK_MAPPING.md` updated if behaviour they describe changed.
- [ ] `docs/TESTING.md` updated if a layer, fixture or rule changed.
- [ ] User-facing behaviour that needs a device or a screen reader is added to
      `docs/production-plan/MAC_AND_DEVICE_CHECKLIST.md` (or the accessibility checklist) as an owed item for the person,
      never ticked by you.
- [ ] `docs/production-plan/PROGRESS.md` session log line for notable findings.
- [ ] No feature creep: anything beyond the request is listed as follow-up, not built.

## 8. What you must not do

- Do not tick device, screen-reader or Google sign-in items you did not observe.
- Do not re-record screenshot goldens on a Mac (they were recorded on Windows; macOS differs by sub-pixels).
- Do not lower a coverage floor, delete a failing test, or add a detekt/lint baseline entry to get green.
- Do not hand-edit generated SQLDelight output or an already released `.sqm`.
- Do not merge Dependabot bumps that need a higher `compileSdk` without reading
  `docs/production-plan/notes/p1-2-dependency-review.md`.

## 9. Gates (report real results)

```bash
./gradlew check                                   # JVM tests, migrations, lint, Spotless, detekt, coverage floors, iOS simulator tests on a Mac
./gradlew :composeApp:compileTestKotlinIosSimulatorArm64   # on any machine, if commonTest or iosMain changed
```

On a Mac, additionally when the change touches the matching area:

```bash
./gradlew :composeApp:connectedDebugAndroidTest   # needs an emulator (not a phone: the tests wipe app data)
python3 scripts/android/release_smoke.py          # R8 release build smoke
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO \
  -only-testing:iosAppUITests
```

After code changes run `python -m graphify update .` if graphify is installed (project CLAUDE.md).
