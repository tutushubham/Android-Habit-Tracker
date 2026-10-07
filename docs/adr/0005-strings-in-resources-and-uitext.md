# ADR 0005: Copy lives in Compose Multiplatform resources; presentation emits `UiText`

- Status: accepted (P1-3 step 1, 2026-10-07)

## Context

Every word the person reads was a Kotlin literal: about 90 `Text("…")` calls in `ui/`, error and confirmation messages in the
ViewModels, `DestructiveAction`, `SyncError`, and the Android/iOS backup and recovery services. Nothing could be translated,
tests asserted English strings built in several places, and platform code (which has no Compose) had no way to show a
translated message.

## Decision

- **One catalog:** `composeApp/src/commonMain/composeResources/values/strings.xml` (English; plurals as `<plurals>`). Screens use
  `stringResource(Res.string.x)` / `pluralStringResource`. The text in the catalog is the text that was in the code, word for
  word (see "Verification").
- **`UiText` (`presentation/UiText.kt`)** describes a message without rendering it: `Res` (resource + arguments), `Plural`,
  `Raw` (text that is data: a habit name, a sheet's wording, a validator's diagnostic) and `Joined`. ViewModels, `SyncError`,
  `DestructiveAction`, `BackupResult` and the platform services return `UiText`; the UI renders it with `asString()`
  (composable) and non-UI code with `resolve()` (suspend). Arguments may themselves be `UiText`.
- **Android-only text** that Android itself shows (the system share-sheet title) is in `androidMain/res/values/platform_strings.xml`,
  as the widget's strings already were. iOS builds the text it shares from the same catalog.
- **Not translated, on purpose:** log messages, the CSV header (a file format), the backup validator's English diagnostics
  (shown as `UiText.Raw`), technical text from the Google token providers (used for logs), and the `PlanTabProblem.logText`
  used in logs and tests.

## Tests

- `NoHardCodedTextTest` fails when a screen passes a literal with words to `Text` or a text-like parameter, when a resource is
  defined but unused or used but undefined, or when the XML contains an unescaped apostrophe or ampersand.
- Behaviour tests compare `UiText` values (`UiText.of(Res.string.err_save_category)`). Tests that care about the wording call
  `English.render(uiText)`, which uses `EnglishCatalog`, a Kotlin map generated from `strings.xml` into the common test sources
  by the `generateEnglishCatalog` Gradle task. It works on the JVM and on iOS without a resource environment.

## Verification of "no copy change"

A script compared every string in the catalog with the literals in the previous commit. It flagged 15 strings: nine were real
rewordings left by an earlier unfinished attempt (the sign-in message, the sheet-link validation message, three Plan-tab problem
texts, the backup-invalid sentence and three singular forms) and were put back; the other seven are text the old code assembled
from parts (checked by hand).
On Android, 40 screenshots (phone and tablet; light, dark, fresh install) of the new build are pixel-identical to the old build.

## Consequences

- Adding a language means adding `values-xx/strings.xml`; no code change for the strings listed above.
- Still English in code, for P1-3 step 2: month names (`UiFormatting`, `DailyShare`), weekday names taken from the `DayOfWeek`
  enum (date lines, grid headers' accessibility text), the first day of the week, and number/date formats.
- English singular/plural quirks of the old code were kept ("Synced 1 sessions", "1 checks uploaded"); fix them in a deliberate
  copy pass, the plurals are already in place.
