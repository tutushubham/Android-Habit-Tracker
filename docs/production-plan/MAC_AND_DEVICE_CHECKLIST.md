# Mac and device runbook — closing Part 1

Everything that could not be verified on the Windows PC, in the order to do it. Last rewritten: **2026-10-12**, after
P2. When this file is done and its results are recorded, **Part 1 is complete** (section 6 says exactly what to tick).

**Two kinds of item.**

- **[auto]** — commands. Ask Claude on the Mac: *"do section 1 of docs/production-plan/MAC_AND_DEVICE_CHECKLIST.md"*.
  Claude runs them in order, records results next to each box, and fixes failures (failing test first, one commit per
  bug). Claude cannot do anything that needs your hands, a physical device, a Google sign-in or your Apple account.
- **[you]** — things only a person can do: sections 3, 4 and parts of 2 and 5. Do them with the devices and note
  what you saw after the arrow (`→ …`).

How to read an item: `- [ ]` open, `- [x]` done, then `→` the observed result. Failures are bugs: write them in the
`PROGRESS.md` session log with the section number and fix them before ticking Part 1.

## 0. Setup on the Mac

- [ ] **[you]** `git pull` on `master` (single-branch policy: no other branch is needed).
- [ ] **[you]** Xcode (current) with the iOS simulator runtimes; command line tools selected
  (`xcode-select -p`); JDK 17 (`java -version`); Android SDK + an emulator image (API 34, Google APIs) for the Android
  device tests (Android Studio's SDK Manager is the easy way; `ANDROID_HOME` set); Ruby with `gem install xcodeproj`.
- [ ] **[you]** `iosApp/Config/Local.xcconfig` exists (copy `Local.xcconfig.example`): your team ID and the iOS Google
  client IDs (`docs/RELEASING.md`, `SHEET_SYNC.md`). It is gitignored; never commit it.
- [ ] **[you]** For the Android release build: `keystore.properties` (repo root, gitignored) or the `HABITSHEET_*`
  environment variables (`docs/RELEASING.md`). Without them `release_smoke.py` uses a throwaway key, which is fine for
  smoke tests but is not a store build.
- [ ] **[you]** An iPhone and an iPad (physical) trusted by Xcode; your Android phone; optionally a second device or
  Google account for the two-device items.
- [ ] **[you]** Your Google account is a test user on the OAuth consent screen (`SHEET_SYNC.md`); one blank Google
  Sheet and your real plan sheet.

## 1. Automated commands **[auto]**

Run from the repository root, in this order. Record the numbers. A failure is investigated, not skipped: if the
cause is a test that assumed something about a platform that turns out to be false, say so and fix the test; if it
is the app, it is a bug (failing test first, separate commit).

### 1.1 The whole check

```bash
./gradlew --version                  # JDK 17 expected for the build toolchain
./gradlew check
```

- [x] `check` is green (2026-10-12, Mac, Xcode 26.6, JDK 17). It runs: JVM tests, schema-migration verification, Android lint, Spotless, detekt, the
  coverage floors, **and on a Mac the common + iOS tests on the simulator**. Windows result at the end of P2:
  **548 JVM tests**, package floors 97/97/97/92/70/19 and total 75 %, measured 99.3/99.3/99.6/94.6/72.0/21.4/77.1. Mac result: JVM tests = → **548, 0 failed**; iOS simulator tests = → **421, 0 failed** (measured 99.3/99.3/99.6/94.6/72.0/21.4/77.1, floors met). The first Mac run had 3 iOS failures, all test assumptions, fixed (PROGRESS session log).
- [x] The iOS simulator test report (`composeApp/build/reports/tests/iosSimulatorArm64Test/index.html`) shows these
  classes and their results (they were written without a Mac and compile but have never run):
  - `IosRepositoryContractTest` (the repository contract on the native SQLite driver) → pass (`restoringReplaces…` had used the repository closed by `reopen()`; test fixed)
  - `IosDriverTest` (**answers: does the iOS driver enforce foreign keys?**) → **yes, it enforces them** (test passes)
  - `IosStartupRecoveryTest` (**answers: where the native driver puts `habit-sheet.db`, and that recovery finds
    it**) → `Library/Application Support/databases/habit-sheet.db` in the app container (inside the searched list); the driver opens lazily, so the test now runs a statement first; recovery finds and moves it. Pass.
  - `IosUiTests` (the 25 shared Compose scenarios; they assume English and a phone-sized simulator) → 25/25 pass. The test window is 1024 dp wide, which selects the tablet layout (no bottom tabs), so `showMonthScreen` now renders in a 412×892 dp box.
- [x] Anything that fails only here is an iOS-only difference: note it, classify (test assumption / real bug).

### 1.2 Android builds

```bash
./gradlew :composeApp:assembleDebug :composeApp:assembleRelease :composeApp:bundleRelease
```

- [~] Debug APK builds (17 MB). Release/bundle stop at `checkReleaseSigning` without `keystore.properties`, by design: **[you]** run once with your keystore. The R8 release APK was built with the throwaway key and smoke-tested (1.5): 3.5 MB.

### 1.3 iOS simulator build and archive

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build   # arm64 only: no x86_64 Kotlin slice
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/HabitSheet.xcarchive \
  -allowProvisioningUpdates archive
```

- [x] The simulator build links (first link since P1-3 added `IosBackupService`, `IosSystemBars`, `IosReduceMotion`,
  `IosFirstDayOfWeek`). → links (needs `ARCHS=arm64`; the generic destination also builds x86_64 and fails in `syncComposeResourcesForIos`).
- [x] The archive succeeds with your team (needs `Local.xcconfig`). The ICU linker warning (data built for iOS 18.5
  vs deployment target 15.0) is expected; copy it here. → **ARCHIVE SUCCEEDED** with the team from `Local.xcconfig`. The ICU linker warning shows on the simulator build, as expected.
- [ ] **[you]** Open the archive in Xcode Organizer → Validate App passes; privacy report matches
  `PrivacyInfo.xcprivacy` (section 2).

### 1.4 The XCUITest smoke

```bash
gem install xcodeproj                              # once
ruby scripts/ios/add_ui_test_target.rb             # adds target iosAppUITests + scheme entry, idempotent
git diff --stat iosApp/                            # review: project.pbxproj and the shared scheme only
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO \
  -only-testing:iosAppUITests
```

- [x] The script runs and the project still opens in Xcode (the script was written without a Mac). → yes after one fix (`PRODUCT_NAME` was empty: "Multiple commands produce -Runner.app"); target and scheme are committed.
- [x] `AppSmokeUITests` passes: add a habit, check it off, home, activate. The test assumes Compose exposes the
  row's state as the element's accessibility value ("Completed") and the English labels; if only the value
  assertion fails, look at what the element reports (`po app.debugDescription`) and adjust the test, not the app. → **passes**, and on the way found a real bug: `IosLogger` crashed the app at the first log line (PROGRESS). The test needed text views (not text fields) and the goal field. The "Completed" value works as assumed.
- [x] If it worked, **commit** the pbxproj/scheme change and the (possibly adjusted) test: they become part of the
  repository from then on.

### 1.5 Android emulator (also works from the Mac)

```bash
emulator -list-avds
emulator -avd <name> -no-snapshot -no-audio &      # wait: adb wait-for-device
adb devices                                        # exactly one emulator; use an EMULATOR, the tests wipe app data
./gradlew :composeApp:connectedDebugAndroidTest
python3 scripts/android/release_smoke.py           # builds the release APK, installs, drives first run to restart
```

- [ ] `AppSmokeTest` (first run, add habit, check off, rotate, recreate, relaunch) → pass (API 36.1 emulator; needed espresso-core 3.7.0 and a wait for the saved habit)
- [ ] `WidgetToggleTest` (real broadcast to the widget provider) → pass
- [ ] `BackupServiceTest` (export/import through a registry that answers the pickers) → pass
- [ ] `DatabaseMigrationOnDeviceTest`: every released schema upgrades in place → pass; **foreign keys on the
  Android driver** (`theDriverEnforcesForeignKeys…`) → **enforced** (pass). 12/12 device tests.
- [ ] `release_smoke.py` passes on the R8 build (it selects UI by English text and `uiautomator dump`; if it cannot
  find a label, the output lists what was visible). → **PASSED** 6/6 (start, tutorial, add, check off, restart, no crash in log).

### 1.6 Informational: screenshot goldens

```bash
./gradlew :composeApp:testDebugUnitTest --tests "*ScreenshotTests" -Proborazzi.test.verify=true
```

- [ ] The 30 goldens were recorded on Windows. On macOS tiny font/anti-aliasing differences are **expected**; a
  failure here is not a bug. Look at `composeApp/build/outputs/roborazzi` (run with `-Proborazzi.test.compare=true`)
  only to confirm the differences are sub-pixel. **Never re-record the goldens on the Mac.** → all 30 differ on macOS, as predicted; goldens untouched.

### 1.7 Leave the tree clean

- [x] `git status`: only intentional changes (the pbxproj/scheme/test from 1.4, bug fixes, this file's results).
  No goldens, no `Local.xcconfig`, no `keystore.properties`.

## 2. iOS release path `[mac]` (P0-C)

- [ ] Open `iosApp/iosApp.xcodeproj` in Xcode: it opens (the pbxproj was hand-edited), the signing team resolves. →
- [ ] Product → Archive succeeds; Organizer → Validate App passes. →
- [ ] Organizer privacy report matches `PrivacyInfo.xcprivacy` (no undeclared required-reason APIs). →
- [ ] The app icon (opaque 1024) looks right on the home screen. →
- [ ] The linker warning about the bundled ICU data built for iOS 18.5 vs deployment target 15.0 (P1-2): decide the lowest supported iOS (B4) and test on the oldest device/simulator you can. →

## 3. iOS app behaviour (device; simulator where noted)

### 3.1 First run and sync (P0-A/B, P1-3 steps 5–6)
- [ ] Fresh install (delete the app first): "No habits yet" with **Add your first habit** and **Link a Google Sheet**; no OND/personal text anywhere. →
- [ ] Tutorial appears once; Skip/Next/Get started work; **Settings → Help → Show the tutorial** shows it again and returns to the tracker. →
- [ ] Settings → Plan sync: the three steps are readable; pasting `https://example.com` shows the inline error and disables Connect; pasting a real sheet link enables it. →
- [ ] **Connect & sync** → Google sign-in sheet appears (first real iOS sign-in), consent, status line becomes "Synced …"; a `Plan` tab exists in the sheet. →
- [ ] **Open Google Sheet** opens it; **Disconnect** clears the link and keeps local habits. →
- [ ] Failure messages: revoke the app in your Google Account → "Google sign-in needed…"; airplane mode → "Will sync when online." and an automatic upload on reconnect. (P0-B) →
- [ ] Update-over-install on your own iPad keeps your habits, plans and check-offs; your sheet still connects. (P0-A step 9) →

### 3.2 Backup, file based (P1-3 step 4; replaces the old clipboard checks of P1-1)
- [ ] Data & Backup → Export JSON → share sheet → **Save to Files** → open the file in Files: valid JSON, name `habitsheet_backup_<number>.json`. →
- [ ] Same on iPad: the sheet appears as a popover and does not crash. →
- [ ] Share to AirDrop/Mail: receives a `.json` with that name. →
- [ ] Cancel the share sheet: no message, nothing left in the app's `tmp`. →
- [ ] Export CSV → Save to Files → opens in Numbers; columns look right. →
- [ ] Import JSON → Files picker → pick the backup → **preview dialog shows counts** ("replaces … with … from the file") → Import & Replace → data restored, theme restored. →
- [ ] Import: pick a non-backup file, an empty file, a very large file: clear message, data unchanged. →
- [ ] Import a v1–v3 backup (old build's export) and a v4 backup that contains a sheet link: the "Also use the sheet link stored in this backup" checkbox appears only for the latter and does what it says. →
- [ ] Export with "Include my Google Sheet link" on and off: the file contains the link only when on (open it in a text editor). →
- [ ] "Export a backup first" in the Reset and Import dialogs opens the share sheet and returns to the dialog. →
- [ ] Import a file that is still in iCloud Drive (not downloaded). →

### 3.3 Dates and time (P1-1 step 8, P1-3 step 2)
- [ ] Change time zone while the app is open (Settings → General → Date & Time): the selected day/month and widget-less views move at once. (`NSSystemTimeZoneDidChange` observer) →
- [ ] Cross midnight with the app open/backgrounded: today moves (`NSCalendarDayChanged`). →
- [ ] Settings → General → Language & Region: US (Sunday first) vs Germany (Monday first): the Plan screen's weekly list starts on the right weekday (`IosFirstDayOfWeek`). Month and weekday names are English by design until translations exist. →

### 3.4 Navigation, layout, theme (P1-2, P1-3 steps 7–8)
- [ ] Edge-swipe back from Data & Backup, Settings, Plan, Habits, About, Licences; no action on the tracker. →
- [ ] Kill the app on About, reopen: returns to About. →
- [ ] iPad portrait and landscape: sidebar layout; Split View 1/3, 1/2, 2/3 and Stage Manager resize switch between sidebar and phone layout without clipping. →
- [ ] iPhone rotation: nothing clipped, the month grid keeps its scroll position. →
- [ ] Settings → Appearance: choose Dark while iOS is Light and the reverse: the status bar text and the system share sheet follow the **app** choice (`IosSystemBars`). →
- [ ] Safe areas: notch/Dynamic Island and home indicator never cover the top bar, bottom navigation, dialogs or the last list item (month, Habits, Settings, Backup, About). →
- [ ] Keyboard: sheet-link field in Settings, habit/category editor dialogs, session editor: the focused field stays visible and can be scrolled to. →
- [ ] About: Privacy policy and Contact support are disabled with "Not available yet" (until B3 fills `AppLinks.Default`); Licences opens, each row opens its project page. →

### 3.5 Accessibility (P1-3 step 3) — see `docs/accessibility-checklist.md` for the full list
- [ ] VoiceOver: Month grid cell reads "<habit>, <date>", then state; double tap toggles and announces; no "check mark" read twice. →
- [ ] VoiceOver: bottom bar "Tracker/Habits, tab, selected"; every icon button has a label; Settings status line is announced when it changes. →
- [ ] Larger Text (Settings → Accessibility → Display & Text Size → Larger Text, largest): month grid, weekly block, Today list, Settings, editors, dialogs: nothing clipped (fixed heights scale up to 200%). →
- [ ] Reduce Motion on: "today" in the grid jumps without scrolling animation. →
- [ ] Contrast in both themes: unchecked cell borders visible, error text readable, share preview readable. →

### 3.6 Recovery paths (P1-1 step 11)
- [ ] Simulate an unreadable database (replace `habit-sheet.db` in the app container with garbage via Xcode → Devices → Download/Replace Container): the recovery screen appears. Try again / Save a copy of the data file (share sheet) / Start with empty data (confirm; old file kept aside). `IosStartupRecovery` file locations and share sheet were never run. →
- [ ] Afterwards restore a normal install and confirm the app opens. →

### 3.7 SQLite
- [ ] `PRAGMA foreign_keys` on the iOS driver: answered by `IosDriverTest` in 1.1 (deletes do not depend on it either way). →

## 4. Android device pass (same sitting, no Mac needed) — owed from P0–P1-3

- [ ] Release-APK smoke test per `docs/RELEASING.md` checklist (P0-C). →
- [ ] Update-over-install on your phone keeps data and sheet connection (P0-A, P1-1 steps 9–10). →
- [ ] P0-B list: 20 quick check-offs = one coalesced sync; airplane toggle then reconnect; edit a session in the sheet; rename a habit in the sheet; revoke access; Disconnect. →
- [ ] Backup (P1-1 steps 4–5, now with the preview): export, kill the app while the save picker is open, save (toast appears, file complete); export to full storage shows an error; import a large old v3 file; import a non-backup/0-byte file; cancel the picker. →
- [ ] Preview dialog and "Export a backup first" / "Include my Google Sheet link" / "Also use the sheet link in this backup" as in 3.2. →
- [ ] Time zone and midnight (P1-1 step 8): change zone and cross midnight; widget moves too; clock set backwards then toggle and sync still uploads. →
- [ ] Forced corrupt database (P1-1 step 11): the recovery screen, three buttons, `habit-sheet.db.damaged-<time>` kept. →
- [ ] Widget (P1-2): add to your launcher, tick a row, open the app (tick shows), Sync now uploads. →
- [ ] Tablet/rotation/split-screen on every screen; predictive back from Plan/Habits/Settings/Data & Backup/About/Licences; kill on About and reopen (P1-2). →
- [ ] P1-3 first run, sheet-link flow, tutorial replay, locale first weekday (set region to US/Germany), About rows, as in section 3. →
- [ ] **TalkBack** walk-through and **font size at largest + display size largest**, **animations off** (Developer options) — see `docs/accessibility-checklist.md`. →
- [ ] Theme: app Dark on a Light phone and the reverse: status and navigation bar icons readable on every screen (gesture and 3-button navigation). →
- [ ] Tutorial overlay and TalkBack: can focus move to the screen behind it? (known open question) →

## 5. Part 1 exit gate (README, P2 plan section 6)

Do this last, when sections 1–4 are done and every bug they found is fixed.

- [x] **[auto] Clean clone builds and passes.** In an empty directory (this proves nothing depends on files that
  are only on your machine):
  ```bash
  git clone "$(git -C <your repo> remote get-url origin)" /tmp/habitsheet-clean && cd /tmp/habitsheet-clean
  cp <your repo>/iosApp/Config/Local.xcconfig iosApp/Config/        # not in git, needed by the iOS build
  ./gradlew check :composeApp:assembleRelease                        # release needs keystore.properties or HABITSHEET_*
  ```
  → clean clone of the committed state: `./gradlew check :composeApp:assembleDebug` BUILD SUCCESSFUL (548 JVM + 421 iOS tests, floors met); `assembleRelease` needs your keystore, not run there.
- [x] **[auto]** No personal strings or IDs in tracked files (expect only the `.example` placeholders and the
  intentional legacy cleanup constant): 
  ```bash
  git grep -nE "<your Apple team id>|<your client id number>|apps.googleusercontent.com" -- . ':!docs' ':!personal'
  git ls-files | grep -iE "xlsx|keystore|\.jks|Local\.xcconfig$|local\.properties"     # expect only keystore.properties.example
  ```
  → only `Local.xcconfig.example` placeholder and `keystore.properties.example` are tracked.
- [ ] **[you]** Fresh install on a phone **and** an iPad: neutral app, no personal data, tutorial once (3.1, 4).
- [ ] **[you]** Update-over-install on your own phone and iPad keeps your data and your sheet syncs both ways
  (P0-A step 9, P1-1 steps 9–10).
- [ ] **[you] D2 — repo history** (`00-context/DECISIONS.md`): decide now so Part 2 (B1/B4) does not ship private
  history. Recommended default: keep this repository private as the working repo and publish from a fresh repository
  with clean history after Part 1. Record the decision in `DECISIONS.md` and `PROGRESS.md`. (`stitch/` design exports
  are still tracked; decide with it.) →
- [ ] **[you] Housekeeping:** delete the remote branch `origin/docs/production-plan` if you do not want it; leave
  the open Dependabot PRs #2 and #3 unmerged (both need compileSdk 37, `notes/p1-2-dependency-review.md`).

## 6. Recording results and closing Part 1

1. Fill the arrows in this file and commit it ("Mac session results") together with any bug fixes (each bug: failing
   test first, its own commit) and the `iosAppUITests` target if 1.4 worked.
2. Add one line per finding to the `PROGRESS.md` session log; then tick, in `PROGRESS.md`:
   - **Manual checks still owed**: every item whose section above passed (P0-A step 9, P0-B list, P0-C release smoke
     and `[mac]` path, P1-1 steps 4–5/8/9–10/11, P1-2 device checks, P1-3 steps 1–9);
   - the bullets under P0-A, P0-C, P1-1 and P1-3 "Owed";
   - **[Done\*] → [Done]** for P0-A … P1-3, **P2 Testing** and **Part 1 exit gate**.
3. Tick the nested boxes in `part-1-local/P1-3-gaps-in-existing-features.md` (accessibility, iOS backup, first run,
   sweep) and the "pass on your machines" and "release-APK smoke" boxes in `part-1-local/P2-testing.md` section 5.
4. Update `00-context/PROJECT_CONTEXT.md` "Status" and "Verification state" with the Mac numbers (JVM tests, iOS
   simulator tests, results of `IosDriverTest` and `theDriverEnforcesForeignKeys…`) and remove the entry for foreign
   keys from "Known limitations" in `PROGRESS.md`.
5. **Part 1 is complete when:** sections 1 and 5 are fully ticked, sections 2–4 are ticked or each unticked item is
   written down as a known limitation with a reason, and `./gradlew check` is green on both the Windows PC and the Mac.
6. Then start Part 2 with `part-2-beyond-code/B1-ci-cd.md` (its CI jobs are listed at the end of `docs/TESTING.md`).
