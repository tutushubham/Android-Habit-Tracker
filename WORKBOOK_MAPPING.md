# Workbook mapping

The original `Habits.xlsx` workbook (no longer shipped in this repository) contained three sheets: `Start Here`, `August`, and `September`. `Start Here` defines ten editable categories and explains the tracker. The two month sheets share one structure: a 20-row daily tracker, five seven-day summary blocks, category aggregates, and five weekly-habit blocks.

The native app preserves the workbook's terminology and calculations while replacing duplicated month sheets with a single historical record store.

| Workbook concept | Native concept |
| --- | --- |
| `Start Here` category cells | `Category` records and Manage Habits screen |
| Daily habit row | `DailyHabit` definition |
| Day checkbox | `DailyHabitCompletion(habitId, date)` |
| Month sheet | `MonthKey` selection over completion history |
| Per-day hidden formulas | `HabitCalculations.dailySummaries` |
| Per-row progress formulas | `HabitCalculations.habitSummaries` |
| Category hidden table and chart | `HabitCalculations.categorySummaries` |
| Week 1…5 daily charts | `HabitCalculations.dailyWeekSummaries` |
| Weekly habit text and checkbox | `WeeklyHabit` plus `WeeklyHabitCompletion` |
| Overall weekly doughnut | `HabitCalculations.weeklyOverall` |

Workbook-specific behavior retained:

- Daily completed is capped at the number of populated active habits.
- Daily completion percentage is neutral when there are no habits.
- A habit's numeric percentage may exceed 100%.
- Category remaining is clamped at zero, matching the hidden workbook formula.
- The fifth block contains only the real days 29–31 when applicable.
- The month total is daily completions divided by the sum of monthly habit goals.

## Sessions: what is counted (multi-session days)

A day can hold several sessions of one habit (for example "Easy run" and "Mobility"). They have different `planId`s, and the rule is the same everywhere:

- **The unit is the session, not the habit.** `plannedHabitsOn(date)` decides which sessions are due; skipped rest notes are shown but never due.
- A check-off belongs to one session, identified by `(planId, date)`. Completing "Easy run" does not complete "Mobility".
- Day summary: due = sessions due that day, done = those with a completion. Habit summary: goal = sessions due this month, done = those completed. Category and month totals are sums of the habit summaries. The Today list, the share summary and the Android widget list one row per session with its own done state.
- A completion for a session that is not due (removed, skipped, other day) is kept as history but counts nowhere.
- For a habit with one session per day (a weekly plan or no schedule) `planId` is `<habitId>|<date>`, so nothing changes for single-session habits.
- `HabitCalculations.dailySummaries`, `habitSummaries` and `dailyShareSummary` (habit-keyed, from the original workbook model) are not used by the app; they cannot represent two sessions of one habit. The app computes month figures in `MonthViewModel.toUiState` from the planned sessions. Do not use the habit-keyed functions for new code.
- Tests: `MonthUiStateGoldenTest` (fixed snapshot, hand-derived numbers) and `MultiSessionCountingTest`.

