# P1-3 — Gaps in existing features (minimal, no new features)

**Goal:** the features that already exist are finished enough to ship: localisable strings, accessible controls, a proper file-based backup on iOS, and a neutral first-run. Nothing new is added.

**Depends on:** P1-2 (decomposed screens).
**Branch:** `prod/p1-3-feature-gaps`

## Scope decision gate
Reminders/notifications and an iOS widget are **not implemented** and **not part of this plan** (D4). If you later decide to build them, make that a separate release plan. Do not start them here.

## 1. Code analysis

- **Strings:** `ui/` contains ~82 `Text("…")` literals and many dialog/button texts; no `stringResource`/`Res.string` usage anywhere. `composeApp/src/commonMain/composeResources/` has only `font/`. Android widget strings are in `androidMain/res/values/widget_strings.xml` (this is correct for the widget, which is a RemoteViews/Android-only surface).
- **Accessibility:** 22 `contentDescription` usages in the UI; the month grid is custom-drawn (`ui/MonthScreen.kt`, to be split in P1-2) — checkbox cells likely need `semantics { role = Role.Checkbox; toggleableState …; contentDescription = "Run, 12 October, planned, not done" }`. Touch targets unverified. Custom `Theme.kt` colours: contrast unverified. Fonts bundled (Hanken Grotesk, Inter, JetBrains Mono) — does the UI respect system font scale (`sp`) and large-text layouts?
- **iOS backup:** `iosMain/.../IosBackupService.kt` (21 lines) — clipboard-based; README says "copies JSON or CSV to the clipboard". Android uses SAF document pickers.
- **First-run/empty states:** `ui/EmptyStates.kt`, `ui/TutorialOverlay.kt`, `data/DefaultData.kt`. After P0-A there are no seeded habits; P0-A re-wired the tutorial into `MonthScreen` (shown once, only on installs with no habits); verify the empty/tutorial flow explains "link a Google Sheet or add a habit".
- **Settings sheet UX:** `ui/SettingsScreen.kt` l.50–75 — "Spreadsheet link / Save link / Connect & sync" with a one-paragraph explanation; users need a clear "Create a blank sheet in Google Sheets, paste its link" instruction, a validation message for a non-Sheets URL (`SheetLink.canonicalize`), and the Disconnect action.
- **Share:** `DailyShare.kt` (249 lines), `AndroidShareService`, iOS `UIActivityViewController` text.
- **Dark mode / theme:** `ThemeMode.System/Light/Dark`; confirm every screen (month, manage, plan, settings, backup, about, tutorial, widget).
- **Tablet vs phone:** breakpoint at `maxWidth >= 720.dp`.
- **Version/About:** `ui/AboutScreen.kt` — needs privacy-policy link and licence notices (filled in B3/B5; add the placeholder now).

## 2. Steps

1. **String externalisation.** Add `composeResources/values/strings.xml` (English) using Compose Multiplatform resources (`Res.string.*`, plurals). Replace every user-visible literal in `ui/` and presentation-layer messages with resource lookups (presentation emits message *keys* or sealed `UiText`, not hard-coded English). Add `values-xx/` only if you intend a second language now; otherwise just make it possible.
2. **Locale-correct formatting.** Dates, month names, weekday names, percentages and numbers via locale-aware formatting (kotlinx-datetime formatting + platform locale; avoid concatenated English). First day of week: respect locale (Monday vs Sunday) — check `MonthKey`/`MonthEngine` assumptions (workbook uses 5 seven-day blocks; keep the model, localise only labels).
3. **Accessibility pass.**
   - Every icon-only button: `contentDescription`.
   - Month grid cells and checkboxes: role, state, and a descriptive label; reading order sensible (date → habit → state).
   - Minimum 48dp (Android) / 44pt (iOS) touch targets for tappable cells (use `minimumInteractiveComponentSize` or padding).
   - Contrast audit of `Theme.kt` light/dark (WCAG AA 4.5:1 text, 3:1 UI) — adjust tokens only where failing.
   - Respect font scale up to 200%: no clipped text; scrollable containers.
   - Reduce motion: honour system setting for any animations.
   - Test with TalkBack and VoiceOver `[mac/iOS device]`; write findings into `docs/accessibility-checklist.md`.
4. **iOS file backup `[mac]`.** Replace clipboard-only export with `UIActivityViewController` sharing a temp file (JSON/CSV) and import via `UIDocumentPickerViewController` (`forOpeningContentTypes: json`). Keep the clipboard option only if you want it as a secondary fallback. Validation path = P1-1.
5. **Neutral first-run flow.** Ensure a new user lands on: empty state → short tutorial → "Add your first habit" or "Link a Google Sheet". Remove any copy that implies the OND plan. The tutorial is already shown on first run (restored in P0-A); here, add a way to re-open it from Settings/About.
6. **Sheet-link UX copy & validation.** Settings text: 3-step instruction; inline validation error for non-Sheets URLs; clear status line for each `SyncError` (from P0-B); visible "Disconnect" and "Open Google Sheet" actions; explain that data stays in the user's own Google account.
7. **Theme/dark-mode sweep.** Open every screen/dialog in both themes and on phone + tablet; fix hard-coded colours; check status/navigation bar icon contrast (edge-to-edge) on Android and safe areas on iOS (notch/home indicator, iPad multitasking).
8. **Orientation & iPad.** Verify rotation/Split View/Stage Manager behaviour (`UISupportedInterfaceOrientations~ipad` already lists all); verify keyboard avoidance (`ignoresSafeArea(.keyboard)` is set in `HabitSheetApp.swift` — confirm text fields stay visible).
9. **About/legal placeholders.** Add in About: privacy-policy link (constant read from config; real URL set in B3), open-source licence notices screen (generate from Gradle `licenses` plugin or a static file), support email (config).

## 3. Prompts

### Session starter
```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md and part-1-local/P1-3-gaps-in-existing-features.md. Confirm P1-2 is merged. Branch prod/p1-3-feature-gaps. Hard rule: do not add any new feature (no reminders, no iOS widget, no stats). Only finish existing features. Summarise the plan in 8 lines and wait.
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
- [ ] No hard-coded user-visible strings in `ui/`/presentation (guard in CI/lint).
- [ ] Accessibility checklist completed on at least one Android and one iOS device.
- [ ] iOS backup is file-based and validated.
- [ ] Neutral first-run; sheet-link flow has clear copy, validation and Disconnect.
- [ ] Light/dark/phone/tablet sweep clean; About has legal placeholders.
- [ ] `PROGRESS.md` ticked.

## 6. Handoff
P2 adds UI tests for these flows (empty state, sheet-link validation, accessibility semantics assertions).

## 7. Inputs from P1-1 (already built; this plan finishes the user-facing side)
- **Backups:** the format is v4 and `BackupViewModel` already has `exportBackup(includeSheetLink = false)` and `importBackup(applySheetLink = false)`; the UI has no control for them yet. Add: an "include my sheet link" choice on export, and on import (when the backup contains a link) a confirm "use the backup's sheet link?".
- **Before Reset/Import:** offer "export a backup first" (or make an automatic pre-restore copy); Import currently confirms before the file is chosen and so cannot preview it: consider pick → preview counts (`DataSummary`) → confirm.
- **iOS backup** is still clipboard-based (`IosBackupService`); the file-based flow is this plan's step on iOS. Reuse `BackupResult` and `BackupStreams`-style error handling; import must keep calling `BackupSerializer.parse` + one restore transaction.
- **Strings:** all confirmation texts live in `DestructiveAction` (presentation) and the startup screen/recovery messages in `StartupFailureScreen`, `AndroidStartupRecovery`, `IosStartupRecovery`, `AndroidBackupService`, `BackupStreams`; they must move to resources too (some are shown from non-UI classes: pass message keys or resolved strings through a small interface). Tests assert some of these texts (`DestructiveActionsConfirmationTest`, `BackupStreamsTest`): update them with the move.
- **Accessibility:** the new dialogs and the startup failure screen need semantics/labels; the startup screen uses only theme defaults.
