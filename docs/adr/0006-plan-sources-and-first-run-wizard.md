# ADR 0006: Plan sources and the first-run setup wizard

- Status: accepted (P3-1, 2026-10-12)

## Context

A new person had to add habits one by one, then create a Google Sheet and paste its link. Someone training for a
marathon or preparing for interviews wants to answer a few questions and get a plan. The app has no backend (ADR 0001),
all copy must be translatable (ADR 0005), and the linked sheet is the source of truth for dated sessions.

## Decision

- **Plans come from a `PlanSource`** (`domain/plan`). Today the only one is `TemplatePlanSource`: bundled, offline,
  deterministic generators (marathon, Hyrox, interview preparation, healthy routine; "start from scratch" is an empty
  plan). The interface is `suspend` so a generated source (for example an AI service) can be added later. Every
  source's output must keep `PlanInvariants` (no session before the start, after the event or beyond 180 days, at most
  the chosen days per week, nothing on the rest day, at most 20 habits, valid goals and arguments).
- **Plans carry no words.** Names and session texts are `PlanText` keys with integer arguments; the catalog holds the
  wording (`plan_text_*`, mapped exhaustively in `PlanTextResources`). The text is resolved in the person's language
  when the plan is added and then stored as ordinary data, like rows that come from the sheet. A future generated
  source that produces free text needs a `Raw` label variant, validated by the same invariants.
- **Applying a plan is additive and atomic.** `PlanMerger` (pure) turns a draft into rows that fit existing data:
  same-named habits are reused, a sheet-managed habit is never taken over (the plan gets its own copy), the person's
  own plan rows are never replaced, and the habit limit is checked first. `HabitStore.applyPlan` writes the result in
  one transaction; applying it twice changes nothing.
- **The sheet is filled by the normal first sync.** The wizard writes the plan locally, then either creates a new
  spreadsheet (`SheetsApi.createSpreadsheet`, one `Plan` tab, never retried after it may have reached Google: a lost
  reply is `SyncError.CreateUnconfirmed`) or links the person's own; the existing first sync uploads the plan. After
  that, the plan is edited in the sheet (the app's Plan screen is read-only while a sheet is linked).
- **Reminders are platform pieces behind two interfaces** (`ReminderScheduler`, `NotificationPermission`, bundled in
  `ReminderPlatform` and passed to `AppGraph`, ADR 0004). Android: an inexact repeating alarm (no exact-alarm
  permission), set again after reboot, update and clock or zone changes; Android 13 runtime permission. iOS: a
  repeating calendar trigger under a fixed identifier. The time is a per-device setting, not part of backups.
- **The wizard is a destination** (`Destination.Onboarding`, ADR 0003) with its steps in `OnboardingViewModel`; it
  opens on a fresh install (not completed, no habits), from Settings and from the empty tracker. Back goes to the
  previous step; on the first step it asks before skipping. The coach marks now follow a new plan instead of opening
  on first launch. Wizard answers are not saved across process death (the wizard restarts at its first step).

## Consequences

- New templates are a generator plus catalog strings; the property test covers them automatically.
- Template plans stop at 180 days, matching the sync window, so the sheet always holds the whole plan.
- Distances are in kilometres; a units setting would add an argument to the running texts.
- Android alarms may drift by a few minutes under Doze; acceptable for a daily nudge.
