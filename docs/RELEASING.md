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
