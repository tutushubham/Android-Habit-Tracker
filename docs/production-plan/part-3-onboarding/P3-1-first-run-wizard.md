# First-run onboarding wizard: curated plan, Google Sheet, reminders

## Context
New users today land on an empty tracker with a 3-step coach-mark overlay and must add habits one by one, then
create a Google Sheet by hand and paste its link. Someone preparing for a marathon, Hyrox or job interviews should
instead answer a few questions once and finish with: a curated plan (habits + dated sessions), optionally a Google
Sheet that the app **creates and seeds** for them, and a daily reminder. Fine-tuning afterwards happens in the
in-app Preview (before linking) and then in the Google Sheet (today's model: a linked sheet is the source of truth).

Decisions taken with you: bundled offline templates now, behind a `PlanSource` seam an AI generator can plug into
later; "Create a new sheet for me" is the default, "Use an existing sheet" secondary; the sheet step is skippable;
daily reminders with the notification permission are included; kilometres only; after linking, edits happen in the sheet.

Every phase must satisfy `.claude/skills/feature-definition-of-done/SKILL.md` (run the skill at the end of each phase).
Paths: `cm` = `composeApp/src/commonMain/kotlin/com/habitsheet`, `ct` = `composeApp/src/commonTest/kotlin/com/habitsheet`.

## Key design decisions
- **Plan is written locally first, then the sheet is linked.** The existing first sync already uploads a blank
  `Plan` tab from the local plan (`SheetSync.sync` → `uploadNewTab` → `PlanReconciler.initialUpload` + `putTable` of
  `planRowsFor(snapshot, SheetSyncWindow.rolling(today))`). No new upload path.
- **Wizard = a new back-stack destination** `Destination.Onboarding` (parent Tracker, sidebar hidden) in
  `cm/ui/navigation/AppBackStack.kt`; steps are ViewModel state; a nested `BackHandler` goes to the previous step,
  on the first step it asks "Skip setup?". Auto-start rule moves from `MonthViewModel` (`!isOnboardingCompleted() &&
  dailyHabits.isEmpty()`) to `OnboardingViewModel`. Existing users never see it.
- **Keep the coach-mark tutorial**, shown after wizard Done (`monthViewModel.showTutorial()`), no longer on first launch.
- **Template wording lives in `strings.xml`**; domain emits `PlanText` enum keys + int args; text is resolved in the
  person's language at apply time and then stored as ordinary data (like sheet data).
- **Template horizon ≤ 180 days** (matches the sync window; event date picker capped, `PlanProblem.EventTooFar`).
- **No schema change**: reminder settings use the existing settings key/value tables; not in backups (per device).
- **ADR 0006** "Plan sources and first-run wizard" records the above, incl. a `PlanLabel.Raw` variant reserved for a future AI source.

## Phase 1: Domain (pure) — `cm/domain/plan/`
- `PlanModels.kt`: `PlanTemplateId {MARATHON, HYROX, INTERVIEW, GENERAL_FITNESS, SCRATCH}`; `PlanQuestions(eventDate,
  level NEW/REGULAR/EXPERIENCED, daysPerWeek 2..6, restDay, startDate)`; `PlanDraft` of `DraftCategory`,
  `DraftHabit(key, label, categoryKey, kind, datedOnly, monthlyGoal, enabled)`, `DraftSession(habitKey, date, label)`;
  `PlanLabel(text: PlanText, args)`.
- `PlanSource` interface (`suspend fun draft(template, questions, today): PlanResult` = `Ok | Invalid(PlanProblem)`)
  and `TemplatePlanSource` delegating to pure generators `MarathonPlan`, `HyroxPlan`, `InterviewPlan`,
  `GeneralFitnessPlan` (SCRATCH → empty draft → wizard opens Habits). Session habits are `datedOnly = true`.
- `PlanMerger.merge(snapshot, draft, resolvedText, ids, now): PlanApplication` — reuse a matching non-sheet-managed
  habit, suffix "(new plan)" on clash with a sheet-managed one, `TooManyHabits` when > `MAX_DAILY_HABITS`
  (move the constant from `ManageHabitsViewModel` to `domain/model`). Categories map to `DefaultData` ids when present.
- Tests: `ct/domain/plan/*PlanTest`, seeded `PlanGeneratorPropertyTest` (no session before start / after event /
  beyond 180 days, ≤ daysPerWeek per ISO week, nothing on rest day, ≤ 20 habits, deterministic), `PlanMergerTest`.

## Phase 2: Data
- `HabitStore.applyPlan(PlanApplication)` — additive, one `db.write {}` + one reload in `cm/data/LocalHabitRepository.kt`
  (keep files < 500 lines; split a helper if needed); also `InMemoryHabitRepository`, `FailingHabitStore`.
  Idempotent: ids fixed when the application is built; day-plan ids follow `habitId|date`; `MonotonicTime` stamps.
- Settings keys `reminder_enabled`, `reminder_minute_of_day` in `cm/data/SettingsStoreImpl.kt`; `clearAllData` clears them.
- Tests: `HabitRepositoryContract` (SQLite, in-memory, native iOS: additive, re-apply = no change, completions kept),
  `AtomicWritesTest`, `SnapshotParityTest`.

## Phase 3: Sync — create a spreadsheet
- `cm/sync/SheetsApi.kt`: `createSpreadsheet(token, title)` → `POST /v4/spreadsheets` with one `Plan` sheet
  (existing `spreadsheets` scope suffices); `idempotent = false`, never auto-retried.
- New `SyncError.CreateUnconfirmed` (+ catalog message: "check Google Drive for '<title>' before trying again").
- New `cm/sync/SheetSetup.kt`: `createAndLink(title)` = token → create → `setSheetUrl(SheetLink.canonicalize(url))` →
  `sheetSync.sync(interactive = true)`; `linkExisting(input)` extracted from `SettingsViewModel.connect` (both VMs use it).
  Title from catalog `sheet_new_title` ("Habit Sheet: %1$s").
- Formatting gap: `SheetSync` formats only a tab it created; change `hasPlanTab()` → `planTabId(): Int?` and format on
  any first upload to a blank tab.
- Tests: `FakeSheetsServer` models spreadsheet creation (still Plan-only writes); `SheetSetupTest` (fault at each
  request, lost create response → `CreateUnconfirmed` and exactly one create, seeded rows == `planRowsFor` window,
  cancelled sign-in changes nothing); update `SheetSyncFaultMatrixTest`.

## Phase 4: Reminders (new platform code)
- `cm/platform/Reminders.kt`: `ReminderScheduler { schedule(minuteOfDay, title, body); cancel() }`,
  `NotificationPermission { status(); suspend request(); openSystemSettings() }`; NoOp defaults; injected via
  `AppGraph` (ADR 0004). Text resolved from the catalog in common code; rescheduled on every app start.
- Android (`androidMain/.../app/reminders/`): `AlarmManager.setInexactRepeating` daily (no exact-alarm permission, no
  new dependency); `ReminderReceiver` posts to a `reminders` channel (name in `platform_strings.xml`), tap opens
  `MainActivity`; `BootReceiver` (BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED) reschedules from a SharedPreferences copy;
  `AndroidNotificationPermission` with an ActivityResult launcher registered in `MainActivity` (API < 33 →
  `areNotificationsEnabled()`). Manifest: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, two non-exported receivers.
- iOS (`iosMain/.../app/IosReminders.kt`): `UNUserNotificationCenter` authorization + repeating
  `UNCalendarNotificationTrigger` with a fixed identifier; settings URL for "Open settings". Check `PrivacyInfo.xcprivacy`.
- Settings screen: "Daily reminder" row (switch + time picker; "Notifications are off · Open settings" when denied).
- Tests: `FakeReminderScheduler`/`FakeNotificationPermission` in `ct/testing/Fakes.kt`; Settings VM tests;
  `androidInstrumentedTest/ReminderSchedulerTest` (alarm set, boot reschedules); `iosTest/IosReminderRequestTest`
  (pure request builder).

## Phase 5: Presentation + UI
- `cm/presentation/onboarding/OnboardingViewModel.kt` (lifecycle VM via the `AppGraph` factory): steps
  `Welcome → Goal → Questions → Preview → Reminders → Sheet → Done`; Questions skipped for SCRATCH; Preview lets the
  person untick/rename habits (reuse `HabitEditorDialog` pattern) and "Create my plan" calls `applyPlan`; Sheet step
  offers Create (default) / Use existing / Skip and is skipped when a sheet is already linked; Done marks onboarding
  complete, explains "edit your plan in Google Sheets" with **Open sheet**, then shows the coach marks. Skip also
  marks complete. Re-run when habits exist starts with "Adds N habits to your current ones". Errors are `UiText`.
- `cm/presentation/onboarding/PlanTextResources.kt`: exhaustive `PlanText → StringResource` map.
- `cm/ui/onboarding/`: one leaf file per step + shared step scaffold (heading semantics, "Step 3 of 7" progress as a
  live region, 48 dp buttons, scrollable for 200% text, `imePadding`, ≤ 600 dp centred on tablets); Preview rows are
  `toggleable` checkboxes labelled "<habit>, N sessions".
- Wiring: `cm/ui/HabitSheetApp.kt` (destination branch, auto-start effect, tutorial after Done),
  `cm/ui/SettingsScreen.kt` ("Set up a new plan", reminders row), `cm/ui/month/MonthGrid.kt` (`EmptyHabits` gets
  "Set up a plan"), `cm/AppGraph.kt`. Remove the auto-show from `MonthViewModel`.
- All copy in `composeApp/src/commonMain/composeResources/values/strings.xml` (`onboarding_*`, `plan_text_*`,
  `plan_habit_*`, `reminder_*`, `sheet_new_*`).
- Tests: `ct/presentation/OnboardingViewModelTest` (every transition, failures via `FailingHabitStore`, denied
  permission, sheet errors, completed flag on Done and Skip); `AppBackStackTest`, `NavigationWiringTest`.

## Phase 6: Scenario, device and screenshot tests
- `ct/ui/UiScenariosOnboarding.kt` (+ one `@Test` line each in `AndroidUiTests` and `IosUiTests`; `UiWorld` gains fake
  PlanSource/SheetSetup/reminder fakes; phone-sized box): fresh install shows Welcome; marathon happy path ends with
  sessions on the tracker; Skip → empty state with "Set up a plan"; Back between steps; denied permission message;
  invalid existing-sheet link.
- Update first-run smoke tests: `androidInstrumentedTest/.../AppSmokeTest.kt`, `iosApp/iosAppUITests/AppSmokeUITests.swift`
  (skip setup), `scripts/android/release_smoke.py` (new labels + one template end to end on R8).
- `ScreenshotTests`: Goal, Preview, Sheet (light/dark, phone/tablet, 200%); goldens recorded on Windows (owed item).

## Phase 7: Docs
`docs/production-plan/part-3-onboarding/P3-1-first-run-wizard.md` (this plan), `docs/adr/0006-plan-sources-and-onboarding.md`,
`ARCHITECTURE.md`, `SHEET_SYNC.md` (create sheet, unconfirmed create, blank-tab formatting, edit-in-sheet),
`docs/TESTING.md`, `docs/accessibility-checklist.md`, `MAC_AND_DEVICE_CHECKLIST.md` owed items (Android 13 permission
dialog, reminder after reboot, iOS notification, real sheet creation, goldens on Windows), `PROGRESS.md`, privacy note
(notifications are local only).

Order: 1 → 2 → (3 ∥ 4) → 5 → 6 → 7. Phases 1–4 change nothing visible; the wizard switches on in phase 5.
Each phase: failing tests first, own commit(s), `/feature-definition-of-done` pass.

## Verification
- Every phase: `./gradlew check` (JVM + iOS simulator tests, lint, detekt, Spotless, coverage floors) and
  `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64`.
- On the Mac after phases 4–6: `./gradlew :composeApp:connectedDebugAndroidTest` (emulator API 36),
  `python3 scripts/android/release_smoke.py`, the XCUITest command from the skill (iPhone 17 Pro, `ARCHS=arm64`).
- Manual: fresh install on emulator and simulator → Marathon (event in 16 weeks, 4 days/week, Sunday rest) → Preview →
  reminder 07:00 (grant permission) → Create sheet with your Google account → sheet has a formatted `Plan` tab with the
  sessions; tracker shows today's session; notification arrives; Skip path and Settings → "Set up a new plan" re-run.

## Risks / follow-ups
- Linking an existing sheet that already has plan rows: the sheet wins, only unknown habits are appended — copy warns.
- Android inexact alarms may drift under Doze; acceptable for a daily nudge (alternative documented in ADR).
- Wizard answers are lost on process death (the wizard reopens at Welcome) — accepted.
- iOS banner while the app is in the foreground needs a notification delegate — follow-up.
- AI `PlanSource` later: needs `PlanLabel.Raw` for generated wording and output validation by the same invariants.

## Progress
- [x] Phase 1 — domain: `domain/plan` (models, `PlanSource`, four templates, `PlanInvariants`, `PlanMerger`), plan wording in the catalog (`plan_text_*`, `PlanTextResources`), `MAX_DAILY_HABITS` in `domain/model/Limits.kt`. Property test found and fixed: short plans could leave a session habit with no sessions.
- [x] Phase 2 — data: `HabitStore.applyPlan` (atomic, additive, idempotent, consistency checked first) on SQLite and in-memory; `DailyReminder` device setting (`daily_reminder_minute`), cleared by reset; contract, atomic-write and snapshot-parity tests.
- [x] Phase 3 — sync: `SheetsApi.createSpreadsheet` (one `Plan` tab, never retried; lost reply or 5xx → `SyncError.CreateUnconfirmed`), `SheetSetup` (createAndLink / linkExisting), `planTabId()` so any blank Plan tab is formatted on first upload; `FakeSheetsServer` models creation; `SheetSetupTest` incl. a fault at every request. Settings still uses its own connect (left as is: same behaviour, no need to churn).
- [ ] Phase 4 — reminders
- [ ] Phase 5 — presentation + UI
- [ ] Phase 6 — scenario, device and screenshot tests
- [ ] Phase 7 — docs
