# Testing

How the app is tested, how to run each layer, and how to add a test. Written at the end of plan P2; the history of
what was found along the way is in `docs/production-plan/PROGRESS.md`.

## The layers

| Layer | Where | Runs on | What it proves |
|---|---|---|---|
| Domain and calculations | `commonTest/.../domain` | JVM, iOS simulator | Plan resolution, month arithmetic, workbook figures, backup format, plan templates (`PlanGeneratorPropertyTest`: every template keeps `PlanInvariants` for random answers) and `PlanMerger`. Includes seeded property tests (300 seeds each). |
| Repository | `commonTest/.../data` (`HabitRepositoryContract`), `androidUnitTest/.../data` | JVM; the contract also on the iOS simulator | Every store method on real SQLite and on the in-memory double, idempotent upserts, cascades, monotonic `updated_at`, migrations from every released schema, atomic writes (`AtomicWritesTest` fails every statement of every multi-row write). |
| Sync | `commonTest/.../sync` | JVM, iOS simulator | `FakeSheetsServer` (a stand-in for the Sheets API): every typed error at every request, retry and backoff on virtual time, the scheduler, conflict rule, foreign tabs untouched, recovery equals a clean sync. |
| ViewModels | `commonTest/.../presentation` | JVM, iOS simulator | State, navigation, toggling, rollover, and the message for every failure. |
| Compose UI | `commonTest/.../ui/UiScenarios*.kt`, runners `AndroidUiTests`, `IosUiTests` | JVM (Robolectric); iOS simulator | Real screens with real ViewModels: first run, adding a habit, toggling, navigation, sheet link, backup confirmations, semantics (role, state, label, 48 dp). |
| Screenshots | `androidUnitTest/.../ui/ScreenshotTests.kt`, goldens in `androidUnitTest/screenshots` | JVM (Robolectric, Roborazzi) | Six key screens, light/dark, phone/tablet, and 200% text. |
| Structure guards | `androidUnitTest` (`UiStructureTest`, `NoHardCodedTextTest`, `StringCatalogEscapesTest`, `DestructiveCallSitesTest`, `NoSleepInTestsTest`, `ThemeContrastTest` in commonTest) | JVM | House rules: file size, no literal UI text, no stray backslashes in strings, reviewed destructive calls, no sleeping, AA contrast. |
| Device | `androidInstrumentedTest` | Emulator | The real activity, the widget broadcast, backup through the activity-result machinery, upgrades of every released database. |
| iOS | `iosTest`, `iosApp/iosAppUITests` | iOS simulator | Native SQLite driver (contract, foreign keys, file location and recovery), XCUITest smoke. |
| Release build | `scripts/android/release_smoke.py` | Emulator | The R8-shrunk release APK starts, takes a habit and a check-off, and survives a restart. |

## Running

On any machine with JDK 17 and the Android SDK:

```bash
./gradlew check                                  # unit tests, lint, Spotless, detekt, coverage floors (+ iOS tests on a Mac)
./gradlew :composeApp:testDebugUnitTest          # only the JVM tests
./gradlew :composeApp:testDebugUnitTest --tests "*SheetSyncFaultMatrixTest"
./gradlew :composeApp:koverHtmlReport            # composeApp/build/reports/kover/html/index.html
python scripts/coverage-by-package.py            # per-package table (after koverXmlReport)
./gradlew :composeApp:verifyCoverageFloors       # the floors alone
./gradlew :composeApp:assembleDebugAndroidTest   # compile the device tests without a device
```

Screenshots (Roborazzi library; its Gradle plugin is deliberately not used, it slowed every test in the module by about 2x):

```bash
S='--tests "*ScreenshotTests"'
./gradlew :composeApp:testDebugUnitTest $S -Proborazzi.test.verify=true    # fail if a screen differs from its golden
./gradlew :composeApp:testDebugUnitTest $S -Proborazzi.test.compare=true   # old/new/diff images in composeApp/build/outputs/roborazzi
./gradlew :composeApp:testDebugUnitTest $S -Proborazzi.test.record=true    # accept the current look as the new goldens, then review the git diff
```

An emulator (not a phone that holds your data: these tests delete the app's database):

```bash
./gradlew :composeApp:connectedDebugAndroidTest
./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habitsheet.app.BackupServiceTest
python scripts/android/release_smoke.py          # builds a release APK with a throwaway key, installs, drives it
python scripts/android/release_smoke.py --no-build --serial emulator-5554
python scripts/android/release_smoke.py --self-test    # parser only, no device
```

A Mac with Xcode (English simulator language):

```bash
./gradlew :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:iosSimulatorArm64Test --tests "com.habitsheet.ui.IosUiTests"
gem install xcodeproj && ruby scripts/ios/add_ui_test_target.rb      # once
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:iosAppUITests
```

`docs/production-plan/MAC_AND_DEVICE_CHECKLIST.md` lists what was written but has not been run on a device yet.

## Adding a test

**Pick the lowest layer that can show the behaviour.** Logic goes in `commonTest` so it runs on both platforms; SQLite
or Android-only code goes in `androidUnitTest`; a screen goes in the UI scenarios.

Fixtures (`commonTest/.../testing`):

- `buildSnapshot { dailyHabit("run", goal = 12); dayPlan("run", date, "Easy"); done("run", date) }`: a `HabitSnapshot`
  without positional constructors.
- `randomSnapshot(Random(seed), month)` with `PROPERTY_RUNS`: for property tests; put the seed in every message.
- `FakeDateProvider(today, nowMillis, tickMillis)`: the clock, moved by assigning or `advanceDays`.
- `FakeLogger`: assert what is logged, and that user text is not.
- `FakeSheetsServer`: faults (`fail`, `failCallNumber`, `FaultAction.LostResponse`) and `violations` (writes outside the
  Plan tab).
- `FakeSheetsServer` also models creating a spreadsheet (`isCreate`, `spreadsheetsCreated`, `createdTitles`); a new
  spreadsheet may only have the `Plan` tab.
- `FakeReminderScheduler` / `FakeNotificationPermission`: what would be scheduled, and the person's answer to the
  permission dialog. `UiWorld.onboarding` is the wizard's ViewModel with them and a `FakeSheetLinker`.
- `waitUntil { … }` (`androidInstrumentedTest/.../DeviceFixtures.kt`): for system-side effects without a callback (a
  posted notification); polls a condition up to a deadline. In Compose device tests use `compose.waitUntil` for state
  written on a background dispatcher.
- `FailingHabitStore(inner, "setDailyCompletion")`: makes named store calls throw, for error-message tests.
- `English.render(uiText)`: the exact copy, from the same catalog the app renders.
- `UiWorld`: real ViewModels on an unconfined scope and a fake clock (Monday 12 October 2026) for screen tests.

New store method: add it to `HabitRepositoryContract` (it runs on SQLite, the in-memory double and the native driver).
New multi-row write: add it to `AtomicWritesTest`. New screen: add a scenario to `UiScenarios*` and one `@Test` line to
each of `AndroidUiTests` and `IosUiTests`; add it to `ScreenshotTests` if it is a key screen.

## Rules the tests rely on

- **No sleeping.** Wait for an event: `runCurrent()`/virtual time in `runTest`, a `CompletableDeferred` completed by a
  fake, a state flow, or `waitForIdle()` in UI tests. `NoSleepInTestsTest` fails on `Thread.sleep` and friends. Where a
  test must use real dispatchers (`ViewModelLifecycleTest`), it waits on an event with a timeout.
- **Copy** lives in `composeResources/values/strings.xml`. Write apostrophes as they are: the resource plugin keeps a
  backslash (`StringCatalogEscapesTest`). UI tests use `text(Res.string.x)` so copy edits do not break them.
- **UI tests scroll before they tap** (`performScrollTo()`): the Robolectric screen is 320x470 dp.
- **Robolectric runs API 34**; the JDK 17 toolchain cannot run 35.
- **A failing test is committed before its fix**, in its own commit, when a bug is found.
- **Test-only JVM APIs** (`Map.merge`, `String.format`) compile for the JVM but break the iOS test compile; run
  `:composeApp:compileTestKotlinIosSimulatorArm64` before declaring done.

## Coverage

Kover measures the JVM unit tests (Robolectric UI tests included; device and iOS runs are not counted) and excludes
generated code. `verifyCoverageFloors` (part of `check`) fails when a package group drops below its floor:

| Group | Floor | Measured 2026-10-12 |
|---|---|---|
| domain | 97% | 99.3% |
| data | 97% | 99.3% |
| sync | 97% | 99.6% |
| presentation | 92% | 94.6% |
| ui | 70% | 72.0% |
| app + platform (Android and iOS shell) | 19% | 21.4% |
| total | 75% | 77.1% |

The floors are the measured value minus 2 points. **Ratchet up, never down**: when coverage rises, set the floor to
measured minus 2 in `composeApp/build.gradle.kts` (`coverageFloors`). The shell code is low on purpose; the device
tests above are what cover it.

## Screenshot goldens

The 30 PNGs in `composeApp/src/androidUnitTest/screenshots` were recorded on Windows with Robolectric native
graphics. Font rendering differs a little between operating systems, so **record and verify on the same OS**. When CI
is added (B1), record the goldens there once and treat that as the reference. A deliberate visual change is a
record run followed by reviewing the image diff in the pull request.

## When CI is added (plan B1)

One job per row of "Running": `./gradlew check` (Linux), the screenshot verify command (same OS the goldens were recorded
on), `connectedDebugAndroidTest` on an API 34 emulator, `release_smoke.py --no-build`, and on macOS
`iosSimulatorArm64Test` plus the XCUITest command.
