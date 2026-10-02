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
