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
