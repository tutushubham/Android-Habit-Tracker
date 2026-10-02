# Open decisions

Plans assume the **recommended default**. Change it here first if you disagree, then tell the session.

## D1 — How does your personal OND plan live alongside a neutral public app?
**Status: resolved — option A applied in P0-A** (no seed in the app; the plan lives in your Google Sheet; personal files are in the gitignored `personal/` folder).

- **A (recommended): the plan lives in your Google Sheet only.** The app ships with no seed data. Your existing installs keep their local DB. A new install of yours: paste your sheet link → the `Plan` tab is authoritative → everything appears. Your `OND Plan 2026.xlsx` is uploaded to your own Drive. Zero personal code in the app. Works identically on phone and iPad.
- B: Android `personal` product flavor / separate iOS scheme that bundles `OndSeedData`, kept in a gitignored or private source set. More moving parts (KMP + flavors + Xcode scheme), only needed if you want the plan to appear *offline before ever connecting a sheet*.
- Do NOT keep the seed in the shared `commonMain`.

## D2 — Repo visibility and history
**Status: open** — decide before the end of Part 1; the history rewrite is the last step. Note `stitch/` (design exports, 1.3 MB with a zip) is also still tracked.

The repo appeared readable without credentials. Its history already contains: the OND plan, `Habits.xlsx` (personal data), the OAuth client IDs, and your Apple `DEVELOPMENT_TEAM`.
- **Recommended:** keep this repo **private** as your working repo; publish the app from a **fresh public repo (or none)** created after Part 1 with clean history (`git filter-repo` or a squashed export). Client IDs are identifiers, not secrets, but personal xlsx files and the team ID should not ship.
- Alternative: keep public, rewrite history with `git filter-repo`, force-push (breaks clones/forks).

## D3 — Sheets OAuth scope and verification path
Your flow is "user creates a sheet, pastes the link". With the narrow `drive.file` scope the app can only touch files it created or the user opened through a Google picker, so **pasted links would not work**. So:
- **Recommended:** keep the `spreadsheets` scope (sensitive, not restricted → no paid security assessment).
- Move the OAuth consent screen from **Testing → In production**. In Testing, refresh tokens for external users expire after ~7 days (verify against current Google docs), which would repeatedly sign *you* out.
- Unverified-in-production apps are capped at ~100 users and show a warning screen. Fine for personal/friends use. Pursue Google verification (privacy policy, homepage, demo video) only if you outgrow that — see B2.

## D4 — Reminders / notifications
Not implemented. You said no new features, but a habit app for ADHD users without reminders is weak.
- **Recommended default: out of scope for this production pass.** Revisit as a separate v1.1 feature.

## D5 — App identity
Needed before B3/B4. Decide: public app name (currently "Habit Sheet"), bundle/application ID (`com.habitsheet.app` — change before first store upload if you want a different one, it is permanent), publisher/developer name, support email, marketing/privacy URL host (GitHub Pages is free and needs no backend).
