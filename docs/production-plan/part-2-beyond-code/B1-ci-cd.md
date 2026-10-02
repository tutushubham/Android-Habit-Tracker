# B1 — CI/CD (GitHub Actions)

**Start only after the Part 1 exit gate.** Goal: every PR is built, linted and tested automatically on both platforms; releases are reproducible from tags; secrets live only in the CI secret store.

**Inputs from Part 1:** `./gradlew check`, `assembleRelease`/`bundleRelease`, `docs/RELEASING.md`, `keystore.properties` + `Local.xcconfig` conventions, `docs/TESTING.md`.
**No backend:** CI uses hosted runners only.

## 1. Analysis (what exists / what's missing)
- No `.github/` directory (verified). No Dependabot/Renovate config unless added in P1-2.
- Android needs JDK 17 + Android SDK 35 (Linux runner is enough). iOS compile/archive needs macOS runner (`macos-14`/`macos-15`) with Xcode matching your local version.
- Gradle config cache + build cache are enabled in `gradle.properties` (`org.gradle.caching=true`, `configuration-cache=true`) → good for CI caching.
- Kotlin/Native on macOS runners is slow without caching `~/.konan`.

## 2. Steps
1. **Branch policy:** protect `main` (require PR, require CI, linear history). Decide repo visibility (D2) first.
2. **Workflow `ci.yml` (on PR + push to main):**
   - Job `android`: checkout → setup JDK 17 → `gradle/actions/setup-gradle` (caching) → `./gradlew check` → `assembleDebug` → upload test/lint/Kover reports.
   - Job `ios` (macOS): setup Xcode → cache `~/.konan` + Gradle → `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:iosSimulatorArm64Test` → `xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator'` (no signing).
   - Job `instrumented` (optional, nightly): Android emulator runner executing `connectedDebugAndroidTest`.
   - Job `secrets-scan`: gitleaks to prevent committing keystores/passwords.
   - Job `no-literal-strings`/lint guard from P1-3.
3. **Workflow `release-android.yml` (on tag `v*`):** decode keystore from secret → `bundleRelease` → upload AAB artefact → (optional) `r0adkll/upload-google-play` to **internal** track using a Play service-account JSON secret.
4. **Workflow `release-ios.yml` (on tag `v*`) `[mac]`:** import signing certificate + provisioning profile (or use App Store Connect API key with `fastlane match`/Xcode automatic signing) → `xcodebuild archive` → export IPA → upload to TestFlight with `xcrun altool`/`fastlane pilot` (App Store Connect API key secret).
5. **Secrets inventory (GitHub → Settings → Secrets):** `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `APPSTORE_API_KEY_ID`, `APPSTORE_API_ISSUER_ID`, `APPSTORE_API_KEY_P8`, `IOS_CERT_P12_BASE64`, `IOS_CERT_PASSWORD`, `IOS_PROVISIONING_PROFILE_BASE64`, `GOOGLE_IOS_CLIENT_ID` (config, not secret), `APPLE_TEAM_ID` (config). Use environment protection rules for release jobs (manual approval).
6. **Dependency updates:** Dependabot (gradle, github-actions, swift) or Renovate; group Kotlin/Compose/AGP bumps; require CI to pass.
7. **Versioning in CI:** derive tag ↔ `appVersionName` check (fail if tag ≠ `gradle.properties`); auto-increment build number from run number if you prefer.
8. **Badges & docs:** add build badge to README; document CI in `docs/RELEASING.md`.

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md and part-2-beyond-code/B1-ci-cd.md and docs/RELEASING.md. Part 1 is complete. Create .github/ workflows described in B1 on a branch ci/setup. Never write real secrets into any file; use the secret names in the plan. Summarise the plan in 6 lines and wait.
```

### Step prompts
```
Create .github/workflows/ci.yml with jobs: android (JDK17, gradle caching, ./gradlew check, assembleDebug, upload reports), ios on macos (cache ~/.konan and gradle, compileKotlinIosSimulatorArm64, iosSimulatorArm64Test, xcodebuild build for iOS Simulator without signing), secrets-scan (gitleaks), and the no-literal-strings guard. Use concurrency groups to cancel superseded runs. Pin action versions by SHA.
```
```
Create release-android.yml (tag v*): verify tag equals appVersionName, decode ANDROID_KEYSTORE_BASE64, build the signed AAB, upload as artifact and optionally to the Play internal track via a service account; and release-ios.yml (tag v*) that archives, exports an IPA and uploads to TestFlight using an App Store Connect API key. Gate both behind a protected "release" environment with manual approval. Write docs for every required secret in docs/RELEASING.md (names only).
```
```
Add Dependabot (or Renovate) config for gradle, github-actions and swift packages with grouped Kotlin/Compose/AGP updates, add README build badges, and add CODEOWNERS plus branch-protection instructions to docs/RELEASING.md.
```

## 4. Verification
Open a PR with a trivial change → both jobs green; intentionally break a test → PR blocked; push a `v0.0.0-test` tag on a fork/test repo → artefacts produced (without store upload).

## 5. Definition of done
- [ ] CI green on PRs for Android + iOS; branch protection on.
- [ ] Release workflows produce signed AAB and TestFlight-ready IPA from a tag.
- [ ] Secrets only in GitHub secrets; gitleaks passes; documentation lists them.
- [ ] Dependabot/Renovate active.
- [ ] `PROGRESS.md` ticked.
