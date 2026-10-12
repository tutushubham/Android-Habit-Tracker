# P1-3 — Gaps in existing features (minimal, no new features)

**Goal:** the features that already exist are finished enough to ship: localisable strings, accessible controls, a proper file-based backup on iOS, and a neutral first-run. Nothing new is added.

**Depends on:** P1-2 (decomposed screens).
**Branch:** none — work directly on `master` (no branches).

## 0. Handoff from P1-2 (read first)

- **Where the strings are now:** the month screen is `ui/month/*` (`MonthHeader`, `MonthGrid`, `MonthSummary`,
  `WeeklyBlock`, `TodayList`, `MonthYearPickerDialog`; entry `MonthScreen`), Habits is `ui/manage/*`
  (`ManageSections`, `HabitEditorDialog`, `CategoryEditorDialog`; entry `ManageHabitsScreen`), shared pieces are in
  `ui/Components.kt`. `ui/MonthScreen.kt` and `ui/ManageHabitsScreen.kt` no longer exist. Leaves take values and lambdas
  (`MonthActions`), never a ViewModel (`UiStructureTest`), and every leaf file has `@Preview`s with sample data
  (`MonthPreviewData`), handy to check translated or enlarged text.
- **Guards that will fail on purpose:** `UiStructureTest` (no `ui/` or `data/` file over 500 lines, no ViewModel in
  leaves, previews present), `DestructiveCallSitesTest` (recursive over `ui/**`, keys are paths such as
  `manage/ManageHabitsScreen.kt`), `NavigationWiringTest` (one `AppDestination`, `BackHandler`, saveable back stack),
  `MonthLayoutRulesTest` (grid widths and the day-plan sections, which match English category names: localising those
  needs a different rule, not just translated strings).
- **Navigation:** `ui/navigation/AppBackStack` (ADR 0003). System back and the iOS edge swipe go up one level; the
  current screen survives process death. A screen added for P1-3 (licences, tutorial replay) needs a `Destination` with
  a `parent` and a line in `AppBackStackTest`.
- **Accessibility starting point:** month cells already have `Role.Checkbox`, `stateDescription` and a description
  (`MonthGrid.CompletionCell`), day rows likewise (`TodayList.TodayHabitRow`); `DateHeader` merges its semantics.
- **Build rules:** `./gradlew check` must stay green: Spotless (ktlint), detekt with `composeApp/detekt-baseline.xml`
  (do not regenerate it to hide new findings), Android lint, and `allWarningsAsErrors` for main code. Moving strings to
  `composeResources` creates generated code; it is outside `src/` and not linted.
- **Visual check:** `scripts/screenshots/` (README there) compares 40 screens of two builds on an emulator; P1-3
  changes *are* visual, so use it to review differences rather than to require none.
- **Widget:** strings stay in `androidMain/res/values/widget_strings.xml`; its logic is `widget/HabitWidgetLogic.kt`.

## Scope decision gate
Reminders/notifications and an iOS widget are **not implemented** and **not part of this plan** (D4). If you later decide to build them, make that a separate release plan. Do not start them here.

## 1. Code analysis

- **Strings:** `ui/` contains ~82 `Text("…")` literals and many dialog/button texts; no `stringResource`/`Res.string` usage anywhere. `composeApp/src/commonMain/composeResources/` has only `font/`. Android widget strings are in `androidMain/res/values/widget_strings.xml` (this is correct for the widget, which is a RemoteViews/Android-only surface).
- **Accessibility:** 22 `contentDescription` usages in the UI; the month grid is custom-drawn (`ui/month/MonthGrid.kt` after the P1-2 split) — checkbox cells likely need `semantics { role = Role.Checkbox; toggleableState …; contentDescription = "Run, 12 October, planned, not done" }`. Touch targets unverified. Custom `Theme.kt` colours: contrast unverified. Fonts bundled (Hanken Grotesk, Inter, JetBrains Mono) — does the UI respect system font scale (`sp`) and large-text layouts?
- **iOS backup:** `iosMain/.../IosBackupService.kt` (21 lines) — clipboard-based; README says "copies JSON or CSV to the clipboard". Android uses SAF document pickers.
- **First-run/empty states:** `ui/EmptyStates.kt`, `ui/TutorialOverlay.kt`, `data/DefaultData.kt`. After P0-A there are no seeded habits; P0-A re-wired the tutorial into `MonthScreen` (shown once, only on installs with no habits); verify the empty/tutorial flow explains "link a Google Sheet or add a habit".
- **Settings sheet UX:** `ui/SettingsScreen.kt` l.50–75 — "Spreadsheet link / Save link / Connect & sync" with a one-paragraph explanation; users need a clear "Create a blank sheet in Google Sheets, paste its link" instruction, a validation message for a non-Sheets URL (`SheetLink.canonicalize`), and the Disconnect action.
- **Share:** `DailyShare.kt` (249 lines), `AndroidShareService`, iOS `UIActivityViewController` text.
- **Dark mode / theme:** `ThemeMode.System/Light/Dark`; confirm every screen (month, manage, plan, settings, backup, about, tutorial, widget).
- **Tablet vs phone:** breakpoint at `maxWidth >= 720.dp`.
- **Version/About:** `ui/AboutScreen.kt` — needs privacy-policy link and licence notices (filled in B3/B5; add the placeholder now).

## 2. Steps

1. **[DONE 2026-10-07, see ADR 0005]** **String externalisation.** Add `composeResources/values/strings.xml` (English) using Compose Multiplatform resources (`Res.string.*`, plurals). Replace every user-visible literal in `ui/` and presentation-layer messages with resource lookups (presentation emits message *keys* or sealed `UiText`, not hard-coded English). Add `values-xx/` only if you intend a second language now; otherwise just make it possible.
> Step 1 result: 345 strings and plurals in `composeResources/values/strings.xml` (+ plurals), `UiText` from presentation/sync/platform services, `NoHardCodedTextTest` as the guard, no English changed (screens pixel-identical). **Left for step 2:** month names (`UiFormatting.monthNames`, `DailyShare.monthNames`), weekday names from `DayOfWeek.name` (date lines in `PlanScreen`/`TodayList`, accessibility text of the grid headers), first day of the week, number and date formats. Deliberately not translated: logs, the CSV header, `BackupValidator` diagnostics, token-provider technical messages.

2. **[DONE 2026-10-12]** **Locale-correct formatting.** Dates, month names, weekday names, percentages and numbers via locale-aware formatting (kotlinx-datetime formatting + platform locale; avoid concatenated English). First day of week: respect locale (Monday vs Sunday) — check `MonthKey`/`MonthEngine` assumptions (workbook uses 5 seven-day blocks; keep the model, localise only labels).
> Step 2 result: month names, full/short weekday names, weekday initials and the date shapes (`date_weekday_day_month[_year]`, `date_day_month`, `percent_value`) are catalog strings, so the platform language picks them and a translator can reorder them (`UiFormatting.kt`). The Plan screen lists weekdays from the device's first day of the week (`localeFirstDayOfWeek()`, `weekdayNumbersFrom`); stored weekdays (1 = Monday) and the five-block month are unchanged. Accessibility descriptions that used ISO dates (`2026-10-12`) and upper-case enum names (`MONDAY 12`) now speak the full local date. Digits stay ASCII on purpose (sheet, CSV and backups use them; no screen shows a decimal separator). Only English is shipped: adding `values-xx/` is now translation work, not code.

3. **[DONE in code 2026-10-12; screen-reader/device checks owed, see `docs/accessibility-checklist.md`]** **Accessibility pass.**
   - Every icon-only button: `contentDescription`.
   - Month grid cells and checkboxes: role, state, and a descriptive label; reading order sensible (date → habit → state).
   - Minimum 48dp (Android) / 44pt (iOS) touch targets for tappable cells (use `minimumInteractiveComponentSize` or padding).
   - Contrast audit of `Theme.kt` light/dark (WCAG AA 4.5:1 text, 3:1 UI) — adjust tokens only where failing.
   - Respect font scale up to 200%: no clipped text; scrollable containers.
   - Reduce motion: honour system setting for any animations.
   - Test with TalkBack and VoiceOver `[mac/iOS device]`; write findings into `docs/accessibility-checklist.md`.
4. **[DONE in code 2026-10-12; device verification owed]** **iOS file backup `[mac]`.** Replace clipboard-only export with `UIActivityViewController` sharing a temp file (JSON/CSV) and import via `UIDocumentPickerViewController` (`forOpeningContentTypes: json`). Keep the clipboard option only if you want it as a secondary fallback. Validation path = P1-1.
> Step 4 result: `IosBackupService(host, logger)` writes a temp file (`habitsheet_backup_<ms>.json` / `habitsheet_export_<ms>.csv` in `NSTemporaryDirectory()/habitsheet-export`) and presents `UIActivityViewController` with its URL (popover anchored for iPad); the file is deleted when the sheet completes. Import uses `UIDocumentPickerViewController(forOpeningContentTypes: json/text/data, asCopy: true)`, refuses files over 32 MB, reads UTF-8, deletes the copy, and hands the text to `BackupViewModel` (same parse/checksum/validate path as Android). `usesClipboard` is now false on both platforms. **No clipboard fallback** (not trivial: it needs a second set of buttons and view-model paths); the `*_clipboard` UI strings remain behind `usesClipboard`. Not done here: "include sheet link" choice, preview-before-import and "export first" (section 7), which are shared UI work for step 5/6 time.

5. **[DONE in code 2026-10-12]** **Neutral first-run flow.** Ensure a new user lands on: empty state → short tutorial → "Add your first habit" or "Link a Google Sheet". Remove any copy that implies the OND plan. The tutorial is already shown on first run (restored in P0-A); here, add a way to re-open it from Settings/About.
> Steps 5–6 result: on a fresh install Today mode shows "No habits yet" with two buttons, **Add your first habit** (Habits) and **Link a Google Sheet** (Settings), instead of "0 of 0" (`MonthUiState.isEmpty`; the grid mode uses the same panel). The tutorial text mentions linking a sheet; **Settings → Help → Show the tutorial** calls `MonthViewModel.showTutorial()` and returns to the tracker (not added to About: one entry point). Settings → Plan sync now has an intro, three numbered steps, inline validation (`SheetLink.canonicalize` while typing; the button is disabled and the field shows the error), one **Connect & sync** button (`SettingsViewModel.connect` saves the canonical link, then syncs: the separate "Save link" button is gone), **Open Google Sheet** and **Disconnect** visible whenever a link is saved, and a polite live region for the status line. Every `SyncError` already mapped to catalog text; `SyncErrorStatusTest` now asserts that (no raw text, short). Not done: a link-state banner on the tracker screen.

6. **[DONE in code 2026-10-12]** **Sheet-link UX copy & validation.** Settings text: 3-step instruction; inline validation error for non-Sheets URLs; clear status line for each `SyncError` (from P0-B); visible "Disconnect" and "Open Google Sheet" actions; explain that data stays in the user's own Google account.
7. **[DONE in code 2026-10-12; device checks owed]** **Theme/dark-mode sweep.** Open every screen/dialog in both themes and on phone + tablet; fix hard-coded colours; check status/navigation bar icon contrast (edge-to-edge) on Android and safe areas on iOS (notch/home indicator, iPad multitasking).
8. **[DONE in code 2026-10-12; device checks owed]** **Orientation & iPad.** Verify rotation/Split View/Stage Manager behaviour (`UISupportedInterfaceOrientations~ipad` already lists all); verify keyboard avoidance (`ignoresSafeArea(.keyboard)` is set in `HabitSheetApp.swift` — confirm text fields stay visible).
9. **[DONE 2026-10-12]** **About/legal placeholders.** Add in About: privacy-policy link (constant read from config; real URL set in B3), open-source licence notices screen (generate from Gradle `licenses` plugin or a static file), support email (config).

> Steps 7–9 result: **Colours:** no hard-coded text colours remain in `ui/` except the share image (fixed dark, now forced to the dark scheme) and the tutorial scrim (black 70 %, tooltip uses theme surface). **System bars:** `SystemBarsStyle(themeMode, darkTheme)` (`platform/SystemBars.kt`): Android re-applies `enableEdgeToEdge` with the app's own dark/light choice (bar icons used to follow only the system theme); iOS sets the host controller's `overrideUserInterfaceStyle` (status bar and system sheets follow Light/Dark/System). **Keyboard:** Settings scrolls with `imePadding()`; the editor dialogs already had it; Android uses `adjustResize`; `ignoresSafeArea(.keyboard)` stays in `HabitSheetApp.swift` because Compose handles the IME inset. **iPad/Split View:** the layout switches on the window width (`maxWidth >= 720.dp`), so Split View and Stage Manager resizing move between sidebar and phone layout without a restart; the back stack is `rememberSaveable`. **About:** `AppLinks` (`config/AppLinks.kt`, blank until B3: rows show "Not available yet" and are disabled), Open-source licences screen (`Destination.Licenses`, list in `config/OpenSourceNotices.kt`, guarded by `OpenSourceNoticesTest` against the version catalog), support email as `mailto:`. Not done: full licence texts are not bundled (names + project links only: B3/B5), no per-screen screenshot sweep (needs an emulator/device).

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md and part-1-local/P1-3-gaps-in-existing-features.md. Confirm P1-2 is on master. Work directly on master (no branches). Hard rule: do not add any new feature (no reminders, no iOS widget, no stats). Only finish existing features. Summarise the plan in 8 lines and wait.
```

### Step prompts
```
Steps 1-2: Externalise every user-visible string in commonMain ui/ and presentation messages into Compose Multiplatform resources (composeResources/values/strings.xml, with plurals), replacing literals with Res.string lookups (presentation should expose UiText/keys rather than English). Make date/month/weekday/number formatting locale-aware and respect locale first-day-of-week for labels only (keep the 5-block month model). Provide a script or detekt rule that fails if a new Text("literal") is introduced.
```
```
Step 3: Accessibility pass. Add contentDescription to all icon buttons; give month-grid cells and checkboxes proper semantics (role, toggle state, descriptive label "<habit>, <date>, <state>"); enforce minimum touch targets; audit Theme.kt colours against WCAG AA in light and dark and adjust only failing tokens; ensure layouts survive 200% font scale; honour reduce-motion. Write docs/accessibility-checklist.md with what you changed and what needs TalkBack/VoiceOver verification by me.
```
```
Step 4 [mac]: Replace clipboard-only iOS backup with file share (UIActivityViewController with a temp file) for export and UIDocumentPickerViewController for import, reusing the validation from P1-1. Keep clipboard only as a clearly secondary option if trivial. List what must be verified on a physical iPhone/iPad.
```
```
Steps 5-6: Make the first-run flow neutral (empty state -> short tutorial -> add first habit or link a Google Sheet), with the tutorial re-openable from Settings/About. Rewrite the Settings > Plan sync copy as three clear steps (create blank sheet in Google Sheets, copy link, paste & Connect), add inline validation for non-Sheets URLs via SheetLink, map every SyncError to a short status line, and make sure Disconnect and Open Google Sheet actions are visible. All strings via resources.
```
```
Steps 7-9: Sweep every screen and dialog in light/dark and phone/tablet for hard-coded colours, edge-to-edge system-bar contrast, iOS safe areas and keyboard avoidance, iPad rotation/Split View; fix issues. Add About-screen entries for privacy policy link, open-source licences and support email, all read from a small AppLinks config constant (real URLs filled in during B3).
```
```
Run the whole plan: execute P1-3 steps 1-9 in order, a commit per step, with [mac]/device-only items listed in PROGRESS.md as "needs my verification". Do not add features.
```

## 4. Verification
```
./gradlew check
```
Manual: switch device language/region (dates & first weekday adapt); TalkBack/VoiceOver walk-through of Month → toggle a habit → Manage → Settings; 200% font; iOS export → Files app → import on another device; fresh install flow end-to-end.

## 5. Definition of done
- [x] No hard-coded user-visible strings in `ui/`/presentation (guard: `NoHardCodedTextTest`, part of `check`); month/weekday names and formats come from the catalog (step 2).
- [x] Accessibility: semantics, touch targets, contrast guard, font scale and reduce motion implemented and documented (`docs/accessibility-checklist.md`).
  - [ ] Checklist walked through on at least one Android and one iOS device (`MAC_AND_DEVICE_CHECKLIST.md` 3.5, 4).
- [x] iOS backup is file-based (share sheet + document picker) and uses the P1-1 validation.
  - [ ] Validated on a physical iPhone/iPad (`MAC_AND_DEVICE_CHECKLIST.md` 3.2).
- [x] Neutral first run; sheet-link flow has clear copy, validation, Open Google Sheet and Disconnect; tutorial replayable.
  - [ ] Fresh install end-to-end on phone and tablet (`MAC_AND_DEVICE_CHECKLIST.md` 3.1, 4).
- [x] Light/dark/phone/tablet sweep done in code (system bars follow the theme, keyboard inset, safe areas, width-based layout); About has legal placeholders (`AppLinks`, Licences).
  - [ ] Sweep confirmed on devices (`MAC_AND_DEVICE_CHECKLIST.md` 3.4).
- [x] Section 7 inputs from P1-1: include-link choice on export, preview before import with optional link restore, "export first" in the Import/Reset dialogs, accessibility of the new dialogs and the startup screen.
- [x] `./gradlew check` green on Windows; `PROGRESS.md` ticked as [Done*] (device/`[mac]` verification owed, tracked in `MAC_AND_DEVICE_CHECKLIST.md`).

## 6. Handoff
P2 adds UI tests for these flows (empty state, sheet-link validation, accessibility semantics assertions, backup preview dialog). Run `MAC_AND_DEVICE_CHECKLIST.md` first: P2 pins the behaviour it confirms.

### Inputs for P2 and later (what P1-3 left behind)
- **Seams for tests:** `SettingsViewModel.connect`, `MonthViewModel.showTutorial`, `BackupViewModel.pickBackup/restore/hasSheetLink` + `BackupPreview`, `AppLinks(...)` (constructor-injectable; `AboutScreen(links = …)`), `LicensesScreen(notices = …)`, `weekdayNumbersFrom`, `dayOfWeekFromSundayBased`, `scaledForText`, `ThemeContrastTest` (tokens are `internal`).
- **Platform actuals added** (each needs a `[mac]`/device run, none was executed): `IosBackupService(host, logger)`, `IosFirstDayOfWeek`, `IosReduceMotion`, `IosSystemBars`; Android: `AndroidFirstDayOfWeek`, `AndroidReduceMotion`, `AndroidSystemBars`.
- **Guards that exist:** `NoHardCodedTextTest`, `ThemeContrastTest`, `OpenSourceNoticesTest`, `UiStructureTest` (500 lines), `DestructiveCallSitesTest` (lists `restore`), `SyncErrorStatusTest`.
- **Known gaps, deliberately left:** no iOS clipboard fallback; full licence texts not bundled; tutorial overlay is not a screen-reader focus trap (to verify); `outlineVariant` hairlines and the "·" dot in not-planned cells are decorative and below 3:1; the Android widget was not audited for contrast/semantics; digits are not localised; the month grid is 48dp-wide cells (fewer days visible at once): the single knob is `MIN_CELL_WIDTH`.
- **Before B3:** set `AppLinks.Default` (privacy URL, support email) and review `openSourceNotices`.

## 7. Inputs from P1-1 (already built; this plan finishes the user-facing side)
- **Backups:** the format is v4 and `BackupViewModel` already has `exportBackup(includeSheetLink = false)` and `importBackup(applySheetLink = false)`; the UI has no control for them yet. Add: an "include my sheet link" choice on export, and on import (when the backup contains a link) a confirm "use the backup's sheet link?".
- **Before Reset/Import:** offer "export a backup first" (or make an automatic pre-restore copy); Import currently confirms before the file is chosen and so cannot preview it: consider pick → preview counts (`DataSummary`) → confirm.
- **iOS backup** is still clipboard-based (`IosBackupService`); the file-based flow is this plan's step on iOS. Reuse `BackupResult` and `BackupStreams`-style error handling; import must keep calling `BackupSerializer.parse` + one restore transaction.
- **Strings:** all confirmation texts live in `DestructiveAction` (presentation) and the startup screen/recovery messages in `StartupFailureScreen`, `AndroidStartupRecovery`, `IosStartupRecovery`, `AndroidBackupService`, `BackupStreams`; they must move to resources too (some are shown from non-UI classes: pass message keys or resolved strings through a small interface). Tests assert some of these texts (`DestructiveActionsConfirmationTest`, `BackupStreamsTest`): update them with the move.
- **Accessibility:** the new dialogs and the startup failure screen need semantics/labels; the startup screen uses only theme defaults.

### Result of section 7 (2026-10-12)
Implemented: `BackupViewModel.hasSheetLink()` and the export checkbox "Include my Google Sheet link" (off by default, with a note that anyone with the file can see the link); import is now `pickBackup` (reads and fully validates, changes nothing, returns a `BackupPreview`) → confirmation dialog naming both sides ("replaces 1 daily habit … with 3 daily habits … from the file") → `restore(preview, applySheetLink)`; the checkbox "Also use the sheet link stored in this backup" only when the file has one; "Export a backup first" inside the Import and Reset dialogs (no automatic pre-restore copy: the person decides); `StartupFailureScreen` title is a heading and the result line a polite live region. `importBackup` remains as pick + restore in one call for tests and callers without a preview.
