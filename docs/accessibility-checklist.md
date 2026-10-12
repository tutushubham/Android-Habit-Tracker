# Accessibility checklist

Status after **P1-3 step 3** (2026-10-12). Code changes are verified by `./gradlew check` on Windows; everything that
needs a screen reader, a device or a Mac is listed under "Needs your verification" and is **not** done until you tick it.

## What changed

### Semantics
| Area | Before | Now |
|---|---|---|
| Month grid cell (`month/CompletionCell.kt`) | `clickable` + `Role.Checkbox` set by hand | `toggleable(role = Checkbox)`, so TalkBack/VoiceOver get a real checked state; label `"<habit>, <date>"` (`cell_label`, full local date), state text `Completed` / `Incomplete` / `Not planned` |
| Check mark glyph `✓` (grid, day list) | read as a separate "check mark" node | hidden from semantics (the state is already announced) |
| Not-planned cell `·` | read as "middle dot" | hidden |
| Multi-session cell and day-header | description already present | wording now `"<habit>, <date>, 2 of 3 complete"` (no colon) |
| Bottom navigation (Tracker / Habits) | clickable column, no role, no selected state | `selectable(role = Tab)` with selected state |
| Backup/Settings checkboxes (include link, use backup link) | none | `toggleable(role = Checkbox)` rows with the label as text |
| Startup failure screen | theme defaults only | title is a heading, result line is a polite live region |
| Dates in descriptions | ISO `2026-10-12`, enum `MONDAY 12` | full local date (`fullDateLabel`, step 2) |
| Icon-only buttons | already labelled (settings, back, month ±, move up/down, delete category, close preview) | audited, unchanged |

### Touch targets (minimum 48dp)
- Month grid day cells were 28–44dp wide; now **48–56dp** (`MonthGridLayout`). The grid already scrolled sideways on
  phones, so the visible effect is fewer days on screen at once (about 5 instead of 8 on a 360dp phone).
- Move up/down buttons in Habits: 44dp → 48dp.
- Already compliant (checked): bottom navigation (64dp high), sidebar rows, plan rows, settings rows, top-bar back,
  dialog icon buttons, chips, text buttons with `sizeIn(minHeight = 48.dp)`.

### Colour contrast (WCAG 2.x AA: 4.5:1 text, 3:1 control boundary)
Audited with `ThemeContrastTest` (fails when a token drops below the limit). Failing before, changed now:

| Token / use | Before | After | Ratio (before → after) |
|---|---|---|---|
| light `error` (text, destructive buttons) | `#EF4444` | `#B91C1C` | 3.76 → 6.47 (on tinted button 3.3 → 5.5) |
| light `outline` (checkbox/ring/field borders) | `#CBD5E1` | `#8190A5` | 1.48 → 3.25 |
| dark `outline` | `#334155` | `#5B6676` | 1.95 → 3.46 |
| grid checkbox border | `outlineVariant` (1.1:1) | `outline` | 1.10 → 3.25 (light), 1.38 → 3.46 (dark) |
| `✓` and "today" date number on `primary` | `Color.White` | `onPrimary` | dark theme 1.74 → 5.58 |
| category label colours | fixed slate/green/grey ×0.8 alpha | theme `secondary`/`primary`/`tertiary`, no alpha | dark 2.1–2.8 → 7.5–11.6; light 3.3–4.5 → 4.8–7.6 |
| share image text | followed the app theme on a fixed dark background | always dark scheme | light theme: 2.8–4.2 → 7+ |

Unchanged because they pass: `onSurface`, `onSurfaceVariant` (4.55–7.9), `primary`, `onPrimary`, containers,
dark `error`.
Deliberately not changed: the "·" dot in not-planned cells and divider lines (decorative; the cell carries its text
description). `outlineVariant` stays as the hairline divider colour.

### Text size and motion
- Fixed row and header heights in the month grid and the weekly block grow with the system font scale
  (`scaledForText`, capped at 200%); top bars use a minimum height so a long title can wrap.
- Reduce motion: `rememberReduceMotion()` (Android: animator scale 0; iOS: Reduce Motion). The only decorative
  animation the app starts itself, the grid's scroll to today, jumps instead of animating. System/Material
  transitions follow the platform setting on their own.

## Needs your verification (device / Mac)

Tick when done; write what you saw next to each item.

### TalkBack (Android)
- [ ] Month: swipe through a row: each cell reads "`<habit>, <weekday, day month year>`", then its state, then "double tap to toggle". Toggling announces the new state.
- [ ] A not-planned cell and a multi-session cell read sensibly; the date header reads "Monday 12" (and "Today, …").
- [ ] Bottom bar: "Tracker, tab, selected", "Habits, tab, not selected".
- [ ] Every icon button in: Month header, Habits (move up/down, edit), category editor (delete), month/year picker, share preview, Settings/Plan/About/Backup top bar.
- [ ] The tutorial overlay: can focus **move to the screen behind it** (it should not)? If yes, that is a follow-up (not fixed here).
- [ ] Today list: each row reads name, category/session, state.

### VoiceOver (iPhone/iPad) `[mac/iOS device]`
- [ ] Same walk-through as TalkBack (Month → toggle a habit → Habits → Settings).
- [ ] Checkbox state is spoken for grid cells and day rows; the check mark is not read twice.
- [ ] Back chevron, close "✕", picker arrows have labels.
- [ ] Reduce Motion on: tapping "today" in the grid jumps without scrolling animation.
- [ ] iOS compile of `IosReduceMotion.kt` and `IosFirstDayOfWeek.kt` (`UIAccessibilityIsReduceMotionEnabled`).

### Visual / size
- [ ] **Look at the new grid**: 48dp cells and the stronger checkbox border change how the month looks. If it feels too sparse, the cell width is `MIN_CELL_WIDTH` in `month/MonthGrid.kt` (and `MonthLayoutRulesTest`); WCAG AA itself only requires 24dp.
- [ ] Font size 200% (Android: Settings → Display → Font size, largest + Display size; iOS: Larger Text): Month grid, weekly block, Today list, Settings, Habits editor, dialogs. Note any clipped text.
- [ ] Light and dark: borders of unchecked cells visible, error text/buttons readable, share preview readable in the light theme.
- [ ] Re-run `scripts/screenshots/` and review the differences (expected: grid cell width/border, category label colours, error red).

## Known gaps / not done
- Tutorial overlay focus trapping (see above); the overlay blocks touches but its screen-reader behaviour is unverified.
- The Android widget (`widget_strings.xml`, RemoteViews) was not audited.
- Hard-coded glyph buttons (`‹`, `✕`, `›`) are labelled but are text glyphs, not vector icons; they scale with font size.
- Digits are not localised (see ADR 0005 / step 2 notes).
- Dynamic Type on iOS: Compose Multiplatform maps it to `fontScale`; the cap is whatever the platform reports (the layout rules above assume ≤ 200%).
