# Mac and device checklist

Everything the plans could not verify on the Windows PC, collected in one place so you can run it in a single session
when you have the Mac (and your phone/iPad). Last consolidated: **2026-10-12**, after P1-3.

How to use: work top to bottom, tick `[x]`, write what you saw after an arrow (`→ …`). Anything that fails becomes a
bug: note it in `PROGRESS.md` ("Session log") and fix it before P2, because P2 builds tests on top of this behaviour.
Sources in brackets point to the plan that raised the item.

## 0. Setup on the Mac

- [ ] Xcode (current), JDK 17, Android SDK not needed for the iOS part. `git pull` on `master`.
- [ ] `iosApp/Config/Local.xcconfig` exists (copy from `Local.xcconfig.example`): team ID and the iOS Google client IDs (see `docs/RELEASING.md`, `SHEET_SYNC.md`).
- [ ] An iPhone and an iPad (physical) trusted by Xcode; optionally the simulators.
- [ ] Your own Google account is a test user on the OAuth consent screen (`SHEET_SYNC.md`), and you have one blank Google Sheet plus your real plan sheet.
- [ ] Optional: a second phone/tablet or a second Google account, for the two-device items.

## 1. Automated commands (Mac)

Run from the repository root. Record the test counts (last Windows result: 383 JVM tests; iOS simulator tests were last run on the Mac at P1-3 step 1: 275).

```bash
./gradlew check                                   # JVM tests + migrations + lint + Spotless + detekt + iOS simulator tests
./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64
./gradlew :composeApp:iosSimulatorArm64Test       # the common tests on the simulator
./gradlew assembleDebug assembleRelease           # release needs keystore.properties or HABITSHEET_* (docs/RELEASING.md)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build
```

- [ ] `check` green on the Mac (watch for iOS-only failures: Windows only compiles the iOS klib, it does not run it). → 
- [ ] The simulator build links (this is the first link since P1-3 added `IosBackupService` file sharing, `IosSystemBars`, `IosReduceMotion`, `IosFirstDayOfWeek`). → 
- [ ] Optional: `./gradlew --stop`, then `scripts/screenshots/` comparison against the previous build (README there): **expected differences** are listed in `docs/accessibility-checklist.md` ("Look at the new grid"), plus new empty-state and Settings layouts.

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
- [ ] `PRAGMA foreign_keys` is ON on the iOS driver (P1-1 follow-up; deletes no longer depend on it, but confirm): e.g. a debug log or a test in P2. →

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

## 5. When done

1. Fill the arrows, commit this file with the results and a line in the `PROGRESS.md` session log.
2. Tick the owed boxes in `PROGRESS.md` ("Manual checks still owed") and the Definition of done in
   `part-1-local/P1-3-gaps-in-existing-features.md` (accessibility, iOS backup, first run, sweep).
3. File each failure as a bug with the section number above; fix before starting P2 (or carry them into P2 step 7–9
   as UI/instrumented tests that pin the fix).
4. Then continue with `part-1-local/P2-testing.md` (its section 0 lists what to start from).

## Added in P2 step 7

- [ ] On a device and on the iOS simulator, open Settings, Data and Backup, About and a destructive confirmation and read the texts that contain apostrophes ("Couldn't ...", "can't open its data", "Delete 'Name' ..."): no backslash anywhere (fixed in code, see PROGRESS.md).
- [ ] `./gradlew :composeApp:iosSimulatorArm64Test --tests "com.habitsheet.ui.IosUiTests"` on the Mac (25 shared UI scenarios; run with English and a phone-sized simulator).

## Device and simulator tests written in P2 steps 8-9 (not run on the machine they were written on)

Android (emulator or phone, API 26+; an emulator image with Google APIs is fine). The app is installed twice,
as the app and as the test APK; tests delete the app database first, so **use an emulator, not the phone that holds
your real data**.

```
./gradlew :composeApp:connectedDebugAndroidTest
./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habitsheet.app.BackupServiceTest
```

- [ ] `AppSmokeTest` - first run, skip tutorial, add a habit, check it off, rotate twice, recreate, relaunch.
- [ ] `WidgetToggleTest` - real broadcast to the widget provider: check, uncheck, stale date ignored.
- [ ] `BackupServiceTest` - export/import through a registry that answers instead of the picker.
- [ ] `DatabaseMigrationOnDeviceTest` - every released schema upgrades in place; `theDriverEnforcesForeignKeysSoAnOrphanCheckOffIsRejected` answers the open "does the Android driver keep foreign keys ON" question (if it fails, deletes rely only on the explicit cascades, which is safe; record the answer).

iOS (Mac with Xcode, English simulator language):

```
./gradlew :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:iosSimulatorArm64Test --tests "com.habitsheet.app.IosRepositoryContractTest"
./gradlew :composeApp:iosSimulatorArm64Test --tests "com.habitsheet.ui.IosUiTests"
gem install xcodeproj && ruby scripts/ios/add_ui_test_target.rb      # once
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:iosAppUITests
```

- [ ] `iosSimulatorArm64Test` - all shared tests plus the three below on the simulator.
- [ ] `IosRepositoryContractTest` - the repository contract on `NativeSqliteDriver`.
- [ ] `IosDriverTest` - answers whether the native driver enforces foreign keys.
- [ ] `IosStartupRecoveryTest` - where the driver puts `habit-sheet.db`, and that recovery finds and moves it.
- [ ] `AppSmokeUITests` (XCUITest) - add a habit, check it off, background and foreground. It assumes the accessibility labels of the English copy and that Compose exposes the row's state as the element value; if the last assertion fails, check how the element reports its value before changing the test.
