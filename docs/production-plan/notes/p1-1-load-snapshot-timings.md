# P1-1 step 10: `loadSnapshot()` timings and proposal

Test: `LoadSnapshotPerformanceTest` (JVM, real SQLite file, desktop CPU, 2026-10-03).
Data: 5 years x 20 daily habits = **36,520** daily completions, 1,305 weekly completions, 900 day plans, 5 weekly habits.

| Measure | Result |
|---|---|
| `loadSnapshot()` (via `refresh()`), 15 runs after warm-up | **median 209 ms**, min 197, max 236 |
| One check-off (write + reload) | **median 248 ms**, max 295 |
| Recomputing the month screen state (`toUiState`) from a snapshot this size | 23-27 ms warm (170 ms the first time) |
| Seeding the same data with `restoreFromSnapshot` | 1.3 s (one-off) |

Target from the plan: 100 ms. **Result: over target by 2x on a desktop CPU; a mid-range phone is typically several
times slower**, so a heavy user would feel a quarter of a second or more on every tap, plus a UI recompute after it.
This is not a problem for a normal user (a few hundred rows loads in a few ms); it grows linearly with history.

## Where the time goes

Probe on the same data (not committed):

| Piece | Median |
|---|---|
| `selectAllDailyCompletions()` (query + row objects, `ORDER BY date, habit_id`) | 105 ms |
| `LocalDate.parse` x 36,520 | 15 ms |
| Mapping to domain objects | 14 ms |
| Pending-flag filtering | < 1 ms |

`loadSnapshot()` runs `selectAllDailyCompletions()` **twice** (once for `dailyCompletions`, once more for
`pendingCompletions`), so the single largest cost is paid twice: 2 x 105 ms is essentially the whole 209 ms.

## Proposal (not implemented, per the plan; do it in P1-2 if you agree)

1. **Read the completions once** and derive `pendingCompletions` from the same rows. Expected: about 209 -> about 105 ms
   (a 2-line change, no behaviour change).
2. **Drop the `ORDER BY` from that query** (or order by the primary key) and sort in memory only where order matters;
   there is no index on `(date, habit_id)`, so SQLite sorts 36k rows on every load. Measure after step 1.
3. **Do not reload everything after a single check-off**: for `setDailyCompletion` / `setWeeklyCompletion` update the
   in-memory snapshot (the repository already knows the new row) and keep the full reload for bulk operations (restore,
   sync). This makes a tap independent of history size.
4. If it is still needed: load completions for a window (for example the last 400 days) plus counts for older months, or
   switch to SQLDelight `asFlow()` per table. This is a larger change and probably unnecessary after 1-3.

Acceptance for whoever implements it: rerun `LoadSnapshotPerformanceTest` and record the new numbers here; target
`loadSnapshot` median below 100 ms and a check-off below 50 ms on this data size.

## Result after P1-2 step 5 (2026-10-05)

Implemented points 1–3 plus one extra; point 4 (windowed loading / `asFlow()` per table) was **not needed**.

- Completions are read **once**; `pendingCompletions` comes from the same rows.
- The two completion queries have **no `ORDER BY`**; the repository sorts in memory with `DAILY_COMPLETION_ORDER`
  (date, habit id, plan id) and `WEEKLY_COMPLETION_ORDER` (week start, habit id). The same comparators are used to
  patch a single check-off into the snapshot, so a reload and a patch always agree (`SnapshotParityTest`).
  The plan-id tie-breaker is new: two sessions of one habit on one day used to come back in insertion order.
- `setDailyCompletion` / `setWeeklyCompletion` **patch the in-memory snapshot** instead of reloading every table.
  Bulk writes (sync, restore, reset, deletes, edits) still reload.
- Extra: one parsed `LocalDate` per distinct date during a reload (36,520 rows share about 1,800 dates).

Same test, same data. **The numbers were taken on a different machine than the table above** (Apple silicon Mac,
JDK 17), so the old code was re-measured there first:

| Measure | Before (same Mac) | After | Change |
|---|---|---|---|
| `loadSnapshot()` median | 82 ms | **37 ms** | −55 % |
| One check-off (write + snapshot update) median | 83 ms | **1 ms** (max 4) | −99 % |

Scaling the reload by the same factor to the original Windows desktop gives about 95 ms (was 209), i.e. at the
100 ms target; the check-off no longer depends on history size at all, which was the cost a person actually felt.
Known consequence: a check-off made by another repository instance on the same file (the Android widget) is no
longer picked up by the app's next check-off, only by the next reload (`refresh()` on resume, or any other write).
