# P0-C — Release build configuration, versions, secrets

**Goal:** a signed, minified, reproducible release build on both platforms from a clean checkout, with **no secrets, personal IDs or team IDs in the repo**, one source of truth for version numbers, and the iOS privacy manifest in place.

**Depends on:** P0-A (personal data out). Independent of P0-B except `AppGraph`.
**Branch:** none — work directly on `master` (no branches).
**Out of scope:** actually uploading to stores, creating accounts, CI (Part 2: B1, B4).

## 1. Code analysis

Android — `composeApp/build.gradle.kts`:
- `defaultConfig { versionCode = 6; versionName = "1.1.3" }`; no `buildTypes`, no `signingConfigs`, no R8/`proguard-rules.pro`, `minSdk 23 / target 35`.
- `settings.gradle.kts` forces `com.android.tools:r8:8.13.19` in `pluginManagement.buildscript` (workaround for Kotlin 2.3 metadata) — keep, document, re-check on upgrades.
- `AndroidManifest.xml`: `allowBackup="true"`, no `dataExtractionRules`/`fullBackupContent`; theme `@android:style/Theme.Material.Light.NoActionBar`; FileProvider + widget receiver (`exported=false`).
- Android OAuth client is chosen by package + signing cert (not in code).

iOS — `iosApp/`:
- `Info.plist`: `CFBundleShortVersionString 1.1.3`, `CFBundleVersion 6` hard-coded; `GIDClientID` and URL scheme contain the personal client ID.
- `project.pbxproj`: `MARKETING_VERSION = 1.1.3` (l.180, 214), `DEVELOPMENT_TEAM = G5VX9GMK76` (l.257), `PRODUCT_BUNDLE_IDENTIFIER = com.habitsheet.app`, `IPHONEOS_DEPLOYMENT_TARGET = 15.0`.
- No `PrivacyInfo.xcprivacy`, no entitlements, no `.xcconfig`.

Shared: version is read by `AndroidVersionProvider` / `IosVersionProvider` (About screen).

## 2. Steps

1. **Single version source.** Put `appVersionName` and `appVersionCode` in `gradle.properties` (or `gradle/libs.versions.toml`). Android reads them; for iOS generate an `iosApp/Config/Version.xcconfig` from the same values via a small Gradle task (`./gradlew syncIosVersion`) and reference `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` from it. Document the bump procedure.
2. **Externalise IDs and secrets.**
   - Android: signing via `keystore.properties` (gitignored) or env vars; the build must still work without it (debug only) and fail clearly for `assembleRelease` if missing.
   - iOS: create `iosApp/Config/Local.xcconfig.example` (committed) and `Local.xcconfig` (gitignored) holding `DEVELOPMENT_TEAM`, `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_REVERSED_CLIENT_ID`; `Info.plist` references `$(GOOGLE_IOS_CLIENT_ID)` etc.
   - Document that OAuth client IDs are public identifiers (not secrets) but are kept configurable so forks/you can use your own project (D3/B2).
3. **Android release build type.** Add `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(...) }` + `proguard-rules.pro` with keep rules for kotlinx-serialization, SQLDelight, Ktor/okhttp, Play Services Auth, Compose resources; signing from step 2. Add `debug { applicationIdSuffix = ".debug" }` so debug and release can coexist (check this does not break the Google OAuth client registration — debug needs its own registration; document).
4. **Verify R8 output.** Build `assembleRelease` and `bundleRelease`; run the app on a device with the release build; exercise: month screen, manage habits, backup export/import, share, widget, sync sign-in. Fix missing keep rules.
5. **Backup rules.** Add `res/xml/data_extraction_rules.xml` and `backup_rules.xml`; decision: **exclude the SQLite DB from cloud backup** (sync state/keys must not be restored onto a new device) *or* include it but exclude sync-state — implement the safer choice (exclude) and note that user data lives in their Sheet + manual backups.
6. **Android theme/manifest hygiene.** Use a proper `Theme.Material3.DayNight.NoActionBar` (or the existing equivalent) so splash/system bars match dark mode; set `android:enableOnBackInvokedCallback="true"`; confirm `exported` flags and intent filters; add `android:localeConfig` only if P1-3 adds languages.
7. **iOS privacy manifest.** Add `iosApp/iosApp/PrivacyInfo.xcprivacy` declaring: no tracking, no collected data types by the app itself; required-reason APIs actually used (UserDefaults, file timestamp, etc. — derive from code and from GoogleSignIn's own manifest; the Kotlin/Native runtime and SQLite can add entries). Confirm GoogleSignIn-iOS version ships its own manifest.
8. **iOS project hygiene** `[mac]`: bump deployment target decision (15 vs 16), set `ENABLE_USER_SCRIPT_SANDBOXING`, check that archive works (`xcodebuild archive`) and Bitcode is not assumed, verify Info.plist `UISupportedInterfaceOrientations` are intended, add `ITSAppUsesNonExemptEncryption = NO` (HTTPS only; confirm), app icon set complete (1024 marketing icon).
9. **Reproducible toolchain.** Pin the Gradle JDK toolchain (`kotlin { jvmToolchain(17) }`), keep the wrapper, commit `gradle/verification-metadata.xml` only if you decide to (optional).
10. **Repo hygiene.** Update `.gitignore` (`keystore.properties`, `*.jks`, `*.keystore`, `Local.xcconfig`, `personal/`, `graphify-out/` if undesired, `.claude/` local files). Remove `stitch/*.zip` if redundant with extracted folders.
11. **Docs.** `docs/RELEASING.md`: how to bump versions, build signed AAB/APK, iOS archive, where secrets come from.

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md, 00-context/DECISIONS.md and part-1-local/P0-C-release-build-config.md. Confirm P0-A is merged. Work directly on master (no branches). Never commit keystores, passwords, local.properties or personal team IDs. iOS steps are [mac]; if you cannot run Xcode here, list the commands for me. Summarise the plan in 8 lines and wait.
```

### Step prompts
```
Step 1: Introduce one source of truth for versionName/versionCode (gradle.properties). Make composeApp/build.gradle.kts read it, and add a Gradle task that generates iosApp/Config/Version.xcconfig from the same values; make project.pbxproj/Info.plist use MARKETING_VERSION and CURRENT_PROJECT_VERSION from that file. Document the bump procedure in docs/RELEASING.md.
```
```
Step 2: Externalise signing and IDs. Android: signingConfigs.release reading keystore.properties or env vars (gitignored; debug builds must work without it; assembleRelease must fail with a clear message if missing). iOS: add Config/Local.xcconfig.example (committed) and Local.xcconfig (gitignored) for DEVELOPMENT_TEAM, GOOGLE_IOS_CLIENT_ID, GOOGLE_REVERSED_CLIENT_ID; Info.plist and the pbxproj must reference those variables. Remove the checked-in team ID and personal client IDs from tracked files (leave placeholders in the .example). Update .gitignore.
```
```
Steps 3-4: Add Android release buildType with R8 minify + shrinkResources, proguard-rules.pro (keep rules for kotlinx-serialization, SQLDelight, Ktor/OkHttp, Play Services Auth, Compose resources), and a debug applicationIdSuffix ".debug" if it doesn't break Google auth registration (document the extra OAuth registration needed). Build assembleRelease and bundleRelease if the environment allows; otherwise give me the exact commands and a manual test checklist (month screen, manage habits, backup export/import, share, widget, Google sign-in + sync) and wait for my results to adjust keep rules.
```
```
Steps 5-6: Add data_extraction_rules.xml and backup_rules.xml excluding the SQLite database from cloud backup (sync state must not restore onto a new device), update the manifest, switch to a DayNight Material3 no-action-bar theme, enable onBackInvokedCallback, and re-check every exported flag. Document the backup decision in docs/RELEASING.md.
```
```
Steps 7-8 [mac]: Add iosApp/iosApp/PrivacyInfo.xcprivacy (no tracking, no collected data, accurate required-reason API entries derived from our code and dependencies), ITSAppUsesNonExemptEncryption=NO if HTTPS-only is confirmed, verify app icons (incl. 1024 marketing icon) and orientations, and make `xcodebuild archive` succeed. List anything you cannot verify without Xcode.
```
```
Steps 9-11: Pin the Kotlin JVM toolchain to 17, finalise .gitignore (keystores, Local.xcconfig, personal/, local.properties), delete redundant stitch/*.zip if the extracted folders exist, and write docs/RELEASING.md (version bump, signed AAB/APK, iOS archive, where secrets live).
```
```
Run the whole plan: execute P0-C steps 1-11 with a commit per step; mark any [mac]-only steps as "needs Mac verification" in PROGRESS.md rather than claiming they passed.
```

## 4. Verification
```
git grep -nE "G5VX9GMK76|412589914724|apps.googleusercontent.com" -- . ':!docs' ':!personal'   # expect only .example placeholders / none
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleRelease :composeApp:bundleRelease   # needs keystore.properties
```
Manual: install the release APK on a device, run the full smoke checklist; iOS `[mac]`: archive succeeds and validates in Xcode Organizer.

## 5. Definition of done
- [x] One version source; Android + iOS read it.
- [x] No keystores/passwords/team ID/personal client IDs tracked (placeholders only; they remain in git history, see D2).
- [x] Release build is signed and minified (verified with a test keystore). On-device smoke test still owed; keep rules are provisional until then.
- [x] Backup rules exclude the DB; theme/manifest hygiene done.
- [x] `PrivacyInfo.xcprivacy` present (derived from code/dependencies). Accuracy and archive validation still need a Mac `[mac]`.
- [x] `docs/RELEASING.md` exists; `PROGRESS.md` updated.

Status 2026-10-03: all steps implemented on `master`. Deviations from the plan: no `.debug` application ID suffix (it would break Google sign-in until a separate OAuth client is registered and would start with an empty DB; opt-in instructions are in `docs/RELEASING.md`); `Theme.Material3.DayNight` replaced by a platform-based day/night pair (Material Components is not a dependency); the 1024 app icon was flattened to opaque because the App Store rejects alpha; the foojay resolver was bumped to 1.0.0 for Gradle 9. Verification still owed: release APK on a device, and everything marked `[mac]`.

## 6. Handoff
B1 (CI) consumes the Gradle tasks and the secret names defined here. B2 (OAuth) consumes the config variables and the release SHA-1 procedure.