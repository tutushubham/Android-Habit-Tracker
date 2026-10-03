# Releasing

## Bumping the version

The version lives in one place: `gradle.properties`.

```
appVersionName=1.1.3
appVersionCode=6
```

1. Edit both values (`appVersionCode` must increase on every store upload).
2. Run `./gradlew :composeApp:syncIosVersion`. This regenerates `iosApp/Config/Version.xcconfig`
   (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`), which the Xcode project and `Info.plist` use.
3. Commit `gradle.properties` and `iosApp/Config/Version.xcconfig` together.

Android reads the properties directly in `composeApp/build.gradle.kts`.

## Secrets and local configuration

Nothing secret or personal is tracked.

**Android signing.** Copy `keystore.properties.example` to `keystore.properties` (gitignored), or set the env vars
`HABITSHEET_STORE_FILE`, `HABITSHEET_STORE_PASSWORD`, `HABITSHEET_KEY_ALIAS`, `HABITSHEET_KEY_PASSWORD`.
Debug builds need neither; `assembleRelease`/`bundleRelease` fail with a clear message if signing is missing.

**iOS.** Copy `iosApp/Config/Local.xcconfig.example` to `iosApp/Config/Local.xcconfig` (gitignored) and set
`DEVELOPMENT_TEAM`, `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_REVERSED_CLIENT_ID`. The generated `Version.xcconfig` includes it
(optional include, so a missing file only means the values are empty). OAuth client IDs are public identifiers, not
secrets; they are configurable so forks can use their own Google Cloud project.

## Android release build

```bash
./gradlew :composeApp:assembleRelease :composeApp:bundleRelease
```

Outputs: `composeApp/build/outputs/apk/release/composeApp-release.apk` and `.../bundle/release/composeApp-release.aab`.
Release builds use R8 (`isMinifyEnabled`, `isShrinkResources`) with `composeApp/proguard-rules.pro`. Keep `mapping.txt`
(`composeApp/build/outputs/mapping/release/`) for each uploaded version to de-obfuscate crashes.

### Release smoke checklist (run on a device with the signed release APK)

1. Month screen: grid renders, toggling a habit works, month navigation.
2. Manage habits: add, edit, reorder, delete (with confirmation).
3. Backup: export, then import the exported file.
4. Share: share a summary/export through the system sheet (FileProvider).
5. Widget: add it, check a habit from the widget, confirm the app reflects it.
6. Google sign-in, paste a sheet link, sync, check off a habit, confirm it reaches the Sheet, then Disconnect.

If anything crashes with `ClassNotFoundException`, `NoSuchMethodException` or a serialization error, capture
`adb logcat`, add a targeted `-keep` rule to `proguard-rules.pro`, and rebuild.

### Debug application ID and Google sign-in

The debug build keeps the release application ID (`com.habitsheet.app`). An Android OAuth client is matched by
package name + signing-certificate SHA-1, so a `.debug` suffix would need its own Android OAuth client in Google Cloud
(package `com.habitsheet.app.debug`, SHA-1 of `~/.android/debug.keystore`), and would start with an empty database next
to any existing install. To opt in, add `debug { applicationIdSuffix = ".debug" }` to `buildTypes` and register that client.
The release build needs a client for `com.habitsheet.app` with the release (or Play App Signing) SHA-1; see B2.

## Backup decision

The SQLite database (`habit-sheet.db`) and shared preferences are **excluded** from Android cloud backup and
device-to-device transfer (`res/xml/data_extraction_rules.xml` for Android 12+, `res/xml/backup_rules.xml` for 11 and
lower). Reason: the database holds sync state (connected sheet link, pending-upload flags, last-sync markers); restoring
it onto a new device would resume syncing with state that does not match that device's Google sign-in.

Consequence: a new or restored phone starts empty. User data lives in their Google Sheet (reconnect to pull it back)
and in manual backups (Settings > Data & Backup export). `allowBackup` stays `true` so the rules are honoured, and the
exclusion covers every file the app stores today.

## Manifest notes

- Theme `Theme.HabitSheet` is a day/night pair (`values/` light, `values-night/` dark) over the platform Material
  NoActionBar themes, with transparent system bars and a window background matching the Compose theme to avoid a flash
  at launch. `Theme.Material3.DayNight` was not used because it needs the Material Components library, which the app
  does not depend on.
- `android:enableOnBackInvokedCallback="true"` opts in to predictive back.
- Exported flags: `MainActivity` is `exported=true` (launcher, required); the FileProvider and the widget receiver are
  `exported=false`. The widget receiver only receives system broadcasts and in-app `setPackage` intents.
