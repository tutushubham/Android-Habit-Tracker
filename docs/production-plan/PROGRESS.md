# Progress

Tick when the plan's *Definition of done* is fully met and merged.

## Part 1 — Local
- [ ] P0-A Product split & seed removal
- [ ] P0-B Sync correctness
- [ ] P0-C Release build config
- [ ] P1-1 Robustness & data safety
- [ ] P1-2 Architecture & code quality
- [ ] P1-3 Gaps in existing features
- [ ] P2 Testing
- [ ] **Part 1 exit gate** (see README)

## Part 2 — Beyond the code
- [ ] B1 CI/CD
- [ ] B2 Google OAuth & Sheets setup
- [ ] B3 Legal, privacy, store listing
- [ ] B4 Build, sign, distribute
- [ ] B5 Docs, support, launch

## Decisions (see 00-context/DECISIONS.md)
- [ ] D1 Personal data strategy
- [ ] D2 Repo visibility / history
- [ ] D3 Sheets OAuth scope & verification path
- [ ] D4 Reminders (default: out of scope)
- [ ] D5 App identity (name, bundle IDs, publisher)

## Session log
<!-- One line per session: date · plan · outcome · branch/commit -->
2026-10-02 · P0-A · steps 2–8 done on `prod/p0-a-product-split` (seed removed, SheetSync de-OND'd with rolling window, personal files moved, docs rewritten, tutorial restored); 87 JVM tests green; step 9 manual device checks pending · branch prod/p0-a-product-split
