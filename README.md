# Habit Sheet Native

An offline-first Kotlin Multiplatform translation of `Habits.xlsx` for Android phones/tablets and iPhone/iPad. The shared Compose UI renders each selected month from one persistent event history; months are not copied or stored as separate datasets.

## What is included

- Shared daily and weekly habit definitions, categories, completion records, calendar engine, calculations, presentation state, and Compose UI
- SQLDelight persistence with stable IDs, timestamps, composite completion keys, and foreign keys
- Adaptive phone and tablet layouts, light/dark color schemes, and bundled Hanken Grotesk, Inter, and JetBrains Mono fonts
- Manage screen for the workbook's ten categories, up to 20 daily habits, monthly goals, and up to 20 weekly habits
- Previous/next/current month navigation with correct 28/29/30/31-day behavior
- Android application target and SwiftUI-hosted iOS/iPadOS application target
- Unit tests for the workbook-derived formulas, calendar behavior, history, and actual SQLite restart persistence

The product mapping is documented in [WORKBOOK_MAPPING.md](WORKBOOK_MAPPING.md), and the data/UI boundaries are documented in [ARCHITECTURE.md](ARCHITECTURE.md).

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

There is deliberately no network client, account system, notification code, gamification, or sync implementation.
