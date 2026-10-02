# Habit Sheet — Production Plan

Goal: take the app from "my personal tracker" to "an app anyone can install, paste a Google Sheet link into, and use" — with **no new features**, **no backend**, and **no personal data or credentials in the shipped app**.

Everything here was derived from an audit of the repo at commit `53d074e` (see `00-context/PROJECT_CONTEXT.md`).

## How to use this folder

1. Do the plans **in order**. Each plan is sized for one working session (some may take two).
2. For each plan: open a fresh Claude Code session in the repo root, paste the **Session starter prompt** from the plan file, then paste the step prompts one by one (or the single "Run the whole plan" prompt if you trust it).
3. A plan is finished only when its **Definition of done** is met and `PROGRESS.md` is ticked. Then start the next plan in a *new* session — each plan carries its own context, so nothing needs to be remembered between sessions.
4. Work on a branch per plan (`prod/p0-a-...`). Merge only after the plan's verification passes.

## Order of execution

### Part 1 — Local (code, architecture, quality, tests) → `part-1-local/`

| # | Plan | Why it is here |
|---|------|----------------|
| 1 | `P0-A-product-split-and-seed-removal.md` | Separates "me" from "everyone": removes OND data/hard-coding from shared code |
| 2 | `P0-B-sync-correctness.md` | The Sheet *is* the backend — it must be correct, atomic, and not chatty |
| 3 | `P0-C-release-build-config.md` | Signing, R8, versions, secrets out of the repo, iOS privacy manifest |
| 4 | `P1-1-robustness-and-data-safety.md` | Logging, backups, migrations, timezone, destructive actions |
| 5 | `P1-2-architecture-and-code-quality.md` | Lifecycle-aware ViewModels, navigation, splitting god classes, lint |
| 6 | `P1-3-gaps-in-existing-features.md` | Strings/localization, accessibility, iOS file backup, neutral first-run |
| 7 | `P2-testing.md` | Fill coverage: UI, sync fakes, migrations, iOS |

**Exit gate for Part 1:** everything runs locally (`./gradlew` tests + Android release build, iOS simulator build on a Mac), a fresh install shows a neutral app with no personal data, and your own phone/iPad still work with your sheet.

### Part 2 — Beyond the code → `part-2-beyond-code/`

Start only after Part 1's exit gate.

| # | Plan | Covers |
|---|------|--------|
| B1 | `B1-ci-cd.md` | GitHub Actions for tests/builds/lint; release workflows |
| B2 | `B2-google-oauth-and-sheets-setup.md` | OAuth consent/verification, client IDs, release SHA-1, token expiry |
| B3 | `B3-legal-privacy-store-listing.md` | Privacy policy, data-safety forms, store listing, accounts |
| B4 | `B4-build-sign-distribute.md` | Play (internal → closed → production), TestFlight → App Store |
| B5 | `B5-docs-support-and-launch.md` | User docs ("create a sheet, paste the link"), support, monitoring |

## Ground rules baked into every plan

- **No new features.** Only make existing behaviour correct, safe, accessible, testable. Anything feature-like is listed under "Deferred / out of scope" in the plan, not built.
- **No backend.** The user's own Google Sheet is the only shared store. Local SQLite is the offline cache. No analytics, no accounts, no servers of ours.
- **Two audiences, one codebase:**
  - *Public users* get a neutral app: no seeded habits, no OND plan, no hard-coded habit names, no credentials that belong to you personally.
  - *You* keep your OND plan by keeping it in **your own Google Sheet** (and, if you want, a private/ignored personal build). Your existing installs keep their data — nothing in these plans deletes it.
- **Never commit** keystores, passwords, `local.properties`, signing certs, personal plan files, or personal OAuth/team IDs that you don't want public. Plans P0-A and P0-C move these out.
- **Existing data is sacred.** Every schema/seed/sync change must be non-destructive for installs that already have your data. Each plan says how to verify that.
- Prefer small commits, one concern each. Run the plan's verification commands before declaring done.

## Where builds can run

- Android build and JVM tests: any machine with JDK 17 + Android SDK 35 (Android Studio on your PC is the reference).
- iOS compile/run: **needs macOS + Xcode.** Plans mark iOS steps `[mac]`. If a Claude session runs somewhere without Gradle/Xcode, it must list the commands for you to run instead of claiming they passed.

## Files

- `PROGRESS.md` — tick boxes as you go.
- `00-context/PROJECT_CONTEXT.md` — product vision, constraints, architecture snapshot, full audit findings.
- `00-context/DECISIONS.md` — open decisions with recommended defaults. Resolve these early; plans assume the recommended default.
