# Habit Sheet

An offline-first Kotlin Multiplatform habit tracker for Android phones/tablets and iPhone/iPad. Link your own Google Sheet and the app manages a `Plan` tab in it, so you can plan and review on a laptop and check things off on your phone. The shared Compose UI renders each selected month from one persistent event history; months are not copied or stored as separate datasets.

## What is included

- Shared daily and weekly habit definitions, categories, completion records, calendar engine, calculations, presentation state, and Compose UI
- SQLDelight persistence with stable IDs, timestamps, composite completion keys, and foreign keys
- Adaptive phone and tablet layouts, light/dark color schemes, and bundled Hanken Grotesk, Inter, and JetBrains Mono fonts
- Manage screen for ten default categories, up to 20 daily habits, monthly goals, and up to 20 weekly habits
- A Today screen with tomorrow preview, dated session details, skip status, and weekly repeating plans for daily habits
- Day-specific edits to weekly plans, such as changing a run distance or study subject
- Previous/next/current month navigation with correct 28/29/30/31-day behavior
- Android application target and SwiftUI-hosted iOS/iPadOS application target
- Unit tests for the spreadsheet-derived formulas, calendar behavior, history, and actual SQLite restart persistence
- A neutral first run: default categories only, no pre-filled habits or plans, plus a short one-time tutorial (shown only on installs with no habits yet)

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
- `stitch` — design reference screens (code, images, metadata)

On iOS, Data & Backup copies JSON or CSV to the clipboard; save the copied text somewhere durable. Import reads a previously copied JSON backup. On Android, Data & Backup uses the system document picker for files.

There is no proprietary account server, notification system, or gamification. Optional Google Sheets sync uses native Google authorization and the Sheets API; credentials are not stored in the app database.

## Google Sheet sync

Habits are not the same every day, so the plan lives in a spreadsheet you control. Create a blank Google Sheet in your own account, copy its link, then in **Settings → Plan sync** paste it and choose **Connect & sync**. The first authorized sync creates a `Plan` tab and uploads your planned sessions; if a valid `Plan` tab already exists, it becomes authoritative. Check-offs are cached offline and written back to the `Done` column on sync. Habits without dated `Plan` rows stay local-only. The app accepts both an eight-column layout (`ID, Date, Area, Habit, Session, Done, Skip, Source`) and the simpler six-column schema.

Sync needs the Google Sheets API enabled, OAuth client IDs configured for the build (see the release build configuration; client IDs are provided by build config, not documented here), and a Google account with edit access to the sheet. See [SHEET_SYNC.md](SHEET_SYNC.md) for setup and table rules.
