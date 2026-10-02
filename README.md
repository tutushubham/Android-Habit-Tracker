# Habit Sheet Native

An offline-first Kotlin Multiplatform translation of `Habits.xlsx` for Android phones/tablets and iPhone/iPad. The shared Compose UI renders each selected month from one persistent event history; months are not copied or stored as separate datasets.

## What is included

- Shared daily and weekly habit definitions, categories, completion records, calendar engine, calculations, presentation state, and Compose UI
- SQLDelight persistence with stable IDs, timestamps, composite completion keys, and foreign keys
- Adaptive phone and tablet layouts, light/dark color schemes, and bundled Hanken Grotesk, Inter, and JetBrains Mono fonts
- Manage screen for the workbook's ten categories, up to 20 daily habits, monthly goals, and up to 20 weekly habits
- A Today screen with tomorrow preview, dated session details, skip status, and weekly repeating plans for daily habits
- Day-specific edits to weekly plans, such as changing a run distance or study subject
- Previous/next/current month navigation with correct 28/29/30/31-day behavior
- Android application target and SwiftUI-hosted iOS/iPadOS application target
- Unit tests for the workbook-derived formulas, calendar behavior, history, and actual SQLite restart persistence
- A one-time offline OND 2026 starter plan for fitness, study, daily routines, and avoidance commitments, preserving existing habits and completions

The product mapping is documented in [WORKBOOK_MAPPING.md](WORKBOOK_MAPPING.md), and the data/UI boundaries are documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## Plan a week

Create a daily habit such as Run, Gym, or Study. The app opens on Today; tap **Plan** and choose **Repeat weekly** to enter sessions such as “Easy run · 5 km”, “Upper · 45 min”, or “DSA graphs”. Once a habit has a weekly plan, rest days are excluded from Today and the daily progress count. Use the **Day** tab to override a session, add one on a rest day, or mark a planned session skipped. Today shows the next day's plan below the check-off list. When a Google Sheet is linked, edit the dated plan there instead; the app's Plan screen becomes read-only.

## Run Android

Requirements: JDK 17 and Android SDK 35.

1. Open the repository root in Android Studio.
2. Select the `composeApp` run configuration and an Android device or emulator.
3. Run the app.

Command-line build:

```bash
./gradlew :composeApp:assembleDebug
```

The debug APK is generated under `composeApp/build/outputs/apk/debug/`.

## Run iOS or iPadOS

Requirements: macOS with a full Xcode installation and its command-line tools selected.

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Select the shared `iosApp` scheme and an iPhone or iPad simulator.
3. Run. Xcode invokes `:composeApp:embedAndSignAppleFrameworkForXcode` before compiling the Swift host.

The shared Kotlin/Native target supports Apple silicon simulators and physical arm64 devices.

## Verify

```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

The second command validates shared and iOS Kotlin sources without launching a simulator. Linking and running the Apple app requires full Xcode.

## Key source locations

- `composeApp/src/commonMain/kotlin/com/habitsheet/domain` — models, repository contract, and workbook calculations
- `composeApp/src/commonMain/kotlin/com/habitsheet/data` — defaults and repository implementations
- `composeApp/src/commonMain/sqldelight` — SQLite schema and queries
- `composeApp/src/commonMain/kotlin/com/habitsheet/presentation` — view models and derived UI state
- `composeApp/src/commonMain/kotlin/com/habitsheet/ui` — responsive shared Compose screens
- `composeApp/src/androidMain` and `composeApp/src/iosMain` — minimal platform drivers/entry points
- `iosApp` — native SwiftUI host project
- `stitch` — downloaded Stitch screen code, images, and metadata

On iOS, Data & Backup copies JSON or CSV to the clipboard; save the copied text somewhere durable. Import reads a previously copied JSON backup. On Android, Data & Backup uses the system document picker for files.

There is no proprietary account server, notification system, or gamification. Optional Google Sheets sync uses native Google authorization and the Sheets API; credentials are not stored in the app database.

## OND 2026 plan and Google Sheet status

The app seeds the dated OND starter plan on first launch after this update. It reuses existing habits and does not replace prior completions or edited plans. A reference workbook is at `outputs/ond-2026/OND Plan 2026.xlsx`; the app accepts both its eight-column layout and the simpler six-column `Plan` schema.

In Settings → Plan sync, paste the same spreadsheet link on Android and iPad, then choose **Connect & sync**. The first authorized sync creates the `Plan` tab and uploads the seeded OND sessions if it is missing; otherwise the existing `Plan` tab becomes authoritative. Existing device-only daily routines are added as OND rows so they can travel to the other device. Check-offs for `Plan` rows are cached offline and written back to `Done` on sync. Outside OND, unscheduled habits remain local-only unless given dated `Plan` rows. The Google Sheets API, OAuth test-user configuration, and account edit access must be enabled before this can be verified on devices. See [SHEET_SYNC.md](SHEET_SYNC.md) for exact setup and table rules.
