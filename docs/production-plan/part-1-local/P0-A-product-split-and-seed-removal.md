# P0-A — Product split & seed removal

**Goal:** the shared app contains **no personal data, plan, names or hard-coded habits**. A fresh install is neutral. Your own plan lives in your Google Sheet. Your existing installs keep working with their data untouched.

**Depends on:** decisions D1 (default A) and D2 in `00-context/DECISIONS.md`.
**Branch:** `prod/p0-a-product-split`
**Out of scope:** changing sync algorithm (P0-B), build/signing (P0-C), new features.

## 1. Code analysis (what is wrong today)

| Area | File / lines | Problem |
|---|---|---|
| Seed data | `composeApp/src/commonMain/kotlin/com/habitsheet/data/OndSeedData.kt` (859 lines) | Personal Oct–Dec 2026 plan compiled into every build |
| Seeding at startup | `data/LocalHabitRepository.kt` l.25 `seedOndPlan = true`; init l.37–39; `seedOndPlanIfNeeded` l.482; `seedWinterArcRoutinesIfNeeded` l.554; `ensureWinterArcHabitsAreDatedOnly` l.590 | Runs for every user; inserts 13 habits with category IDs `category-4/-10/-8/-7/-2/-5` that only make sense for your category set |
| Hard-coded names | `sync/SheetSync.kt` l.111–118, l.164–166, `categoryFor()` | `"run","workout","android","dsa","sde"`, `"no junk food"`… drive sync decisions |
| Placeholder string | `SheetSync.kt` l.195, l.208 | `"Plan in OND sheet"` magic string |
| Fixed window | `SheetSync.kt` `ondRowsFor` l.203–204 | Only 2026-10-01..2026-12-31 is ever uploaded |
| Defaults | `data/DefaultData.kt` | Default ten workbook categories — check these are sensible for new users |
| Docs | `README.md` ("OND 2026 plan…"), `SHEET_SYNC.md` | Describe your plan as a product feature |
| Committed personal files | `Habits.xlsx`, `outputs/ond-2026/*`, `tools/build_ond_plan.mjs`, `stitch/*.zip` | Personal/working files in the product repo |

Important invariants to protect:
- Installs that already ran the seed have the settings flags set; **do not** write a migration that deletes habits/plans/completions.
- `InMemoryHabitRepository` and tests may rely on `seedOndPlan = false` already — keep that path working.

## 2. Steps

1. **Resolve D1/D2.** Confirm default A (plan lives in your Sheet). Decide repo handling (D2); this plan only moves files — history rewriting happens at the end of Part 1.
2. **Characterization tests first.** Add tests that pin current behaviour for: fresh DB with `seedOndPlan=false` → no habits except defaults; DB where seed flags are already `1` → data untouched after init. (These protect your installs.)
3. **Remove seeding from the shared repository.**
   - Delete the `seedOndPlan` constructor parameter and the three `seed*/ensure*` functions.
   - Do **not** delete the settings rows; just stop reading them.
   - Keep `seedDefaultsIfEmpty()` but make defaults neutral: keep categories, **no habits**.
4. **Delete `OndSeedData.kt`** from `commonMain`. Update every reference (`SheetSync.kt` l.4/111; tests).
5. **Remove hard-coding from `SheetSync.kt`.**
   - Delete the habit-name allow-lists and the `firstSyncSeedKeys` logic that depends on seed IDs.
   - Replace `ondRowsFor` with a generic `planRowsFor(snapshot, window)`; the default window = from the first day of the current month −31 days … +180 days (named constants, injectable `DateProvider`). The *algorithmic* correctness of sync belongs to P0-B; here you only remove the OND specifics.
   - Remove the `"Plan in OND sheet"` special case (clean up leftover weekly plans with that detail via a one-time, non-destructive cleanup only if present; otherwise ignore).
   - Make `categoryFor()` data-driven: map the sheet `Area` column to an existing category by name, else uncategorized; no habit-name switch.
6. **Neutral first-run.** Check `HabitSheetApp`/onboarding and `DefaultData` so a fresh install shows the empty state (`ui/EmptyStates.kt`) and the tutorial, not seeded habits.
7. **Move personal files out of the product.** Create top-level `personal/` (add to `.gitignore`): move `Habits.xlsx`, `outputs/ond-2026/`, `tools/build_ond_plan.mjs`. In a `personal/README.md` document how to upload `OND Plan 2026.xlsx` to your Google Drive and paste the link in the app.
8. **Docs.** Rewrite `README.md` and `SHEET_SYNC.md` to describe a generic product ("link a Google Sheet; the app manages a `Plan` tab"). Remove OND wording and the personal OAuth client IDs from prose (they move to config in P0-C).
9. **Your own device check.** Install over your existing build: confirm your habits, plans and completions are intact and that connecting your sheet still works.

## 3. Prompts

### Session starter (paste first)
```
You are working in the Habit Sheet KMP repo. Read, in order: docs/production-plan/README.md, docs/production-plan/00-context/PROJECT_CONTEXT.md, docs/production-plan/00-context/DECISIONS.md and docs/production-plan/part-1-local/P0-A-product-split-and-seed-removal.md. If graphify-out/GRAPH_REPORT.md exists use it to navigate. Create branch prod/p0-a-product-split. Rules: no new features; never delete user data; existing installs that already seeded OND must keep all data. Confirm you understood by summarising the plan in 8 lines, then wait for my go.
```

### Step prompts
```
Step 2: Add characterization tests (commonTest/androidUnitTest) that pin: (a) a fresh LocalHabitRepository with no seeding creates only default categories and zero habits; (b) a DB with settings ond_2026_seeded, winter_arc_routines_seeded, winter_arc_dated_only already =1 and existing habits/plans/completions is unchanged after repository init. Run them against the CURRENT code first (with the current seeding) where meaningful, then report.
```
```
Step 3-4: Remove OND seeding from LocalHabitRepository (constructor param seedOndPlan, seedOndPlanIfNeeded, seedWinterArcRoutinesIfNeeded, ensureWinterArcHabitsAreDatedOnly) and delete data/OndSeedData.kt. Do not delete or migrate existing data or settings rows. Fix all references and tests so everything compiles. Keep seedDefaultsIfEmpty but ensure it creates categories only (no habits). Run ./gradlew :composeApp:testDebugUnitTest and report.
```
```
Step 5: In sync/SheetSync.kt remove every OND-specific rule: habit-name allow-lists ("run","workout","android","dsa","sde"), firstSyncSeedKeys/seed-ID logic, the "Plan in OND sheet" placeholder handling, and the fixed 2026-10-01..2026-12-31 window. Replace ondRowsFor with planRowsFor(snapshot, window) using a named, injectable rolling window (from first day of current month minus 31 days to +180 days) via DateProvider. Make categoryFor() map the sheet Area column to an existing category by name, else null. Do NOT change the sync algorithm otherwise (that is plan P0-B). Update SheetSyncTest/SheetPlanTableTest and keep them green.
```
```
Step 6-8: Verify a fresh install is neutral (empty state + tutorial, no habits). Create top-level personal/ (gitignored), move Habits.xlsx, outputs/ond-2026/ and tools/build_ond_plan.mjs into it with git mv where tracked, and write personal/README.md explaining how I upload "OND Plan 2026.xlsx" to my own Google Drive and paste the link. Rewrite README.md and SHEET_SYNC.md for a generic product (no OND wording, no personal client IDs in prose — leave a placeholder referencing P0-C config). Update ARCHITECTURE.md where it mentions OND/Winter Arc.
```
```
Run the whole plan: execute steps 2–8 of P0-A in order with commits per step, run the verification commands in the plan, and finish with the Definition-of-done checklist filled in. Stop and ask before anything that could delete user data.
```

## 4. Verification

```
./gradlew :composeApp:testDebugUnitTest
grep -rniE "ond|winter ?arc|gooning|junk food|adult content" composeApp/src iosApp README.md SHEET_SYNC.md ARCHITECTURE.md   # expect no matches
grep -rn "2026" composeApp/src/commonMain | grep -v -i "test"   # expect no hard-coded 2026 windows
```
Manual: (a) fresh emulator install → empty, neutral; (b) update-over-install on your device → all your data present; (c) link your sheet → plan appears.

## 5. Definition of done
- [ ] No `OndSeedData`, no seed/ensure functions, no OND/personal strings or habit-name allow-lists in `composeApp/src` or docs.
- [ ] Fresh install = zero habits, neutral onboarding.
- [ ] Characterization tests prove existing seeded DBs are untouched.
- [ ] Personal files live under gitignored `personal/` with usage instructions.
- [ ] Tests green. `PROGRESS.md` ticked, session log line added.

## 6. Handoff to next plan
P0-B inherits a `planRowsFor(snapshot, window)` function and a `SheetSync` that is free of OND rules but still has the old algorithm (name matching, non-atomic writes, chatty sync).
