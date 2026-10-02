# B5 — Documentation, support and launch

Goal: a stranger can understand the idea in 60 seconds, set up their sheet in 2 minutes, get help when stuck, and you can maintain the project sustainably without a backend.

**Inputs:** B3 site + legal pages, B4 tester guide, the app's Settings copy (P1-3).

## 1. Analysis
- Existing docs are developer-oriented (`README.md`, `ARCHITECTURE.md`, `SHEET_SYNC.md`, `WORKBOOK_MAPPING.md`); no end-user guide, FAQ, contributor guide, security policy or support process.
- The Sheet **is** the product's second UI; users will open it on a laptop at month-end — they need to know which columns they may edit and what must not be touched (`ID`, headers).
- ADHD audience: instructions must be short, visual, step-based; avoid walls of text.
- Support has no backend: email + GitHub issues (templated), FAQ on the static site.

## 2. Steps
1. **User guide (site/ + docs/user-guide.md):**
   - "Create your Sheet in 3 steps" with screenshots (blank sheet → Share/copy link → paste in app → Connect).
   - "The Plan tab explained": columns `ID, Date, Habit, Session, Done, Skip`; what you can edit (Date, Habit, Session, Skip), what to leave (ID, header row); how to add a one-off event (party, festival) as a new row with a new unique ID; how to mark a rest day (Skip).
   - "Using it on phone + iPad + laptop", offline behaviour, what happens on conflicts (last write wins).
   - "Month-end review" tips in the Sheet (optional filters/pivot instructions — documentation only, no new features).
   - Troubleshooting: access denied, wrong link, quota, malformed Plan tab, unverified-app warning click-through (until B2 verification).
2. **FAQ & privacy plain-language summary** (links to the full policy).
3. **Contributor/maintainer docs:** `CONTRIBUTING.md` (setup, branches, tests, style), `SECURITY.md` (how to report vulnerabilities by email), `CODE_OF_CONDUCT.md` (optional), issue/PR templates (`.github/ISSUE_TEMPLATE/bug.yml` asking for platform, version, sync error message — never sheet content).
4. **README rewrite:** product pitch, screenshots, quick start for users, build instructions for developers, link to docs. Move the personal OND instructions to `personal/README.md` (gitignored).
5. **Support process:** support email alias; response SLA you can keep; macros for common issues; a 'known issues' page.
6. **Launch checklist:** soft launch to friends via testers (B4) → fix → public store listing; announcement copy (honest, no medical claims); landing page link; screenshots; short demo GIF/video.
7. **Maintenance plan:** monthly dependency/Dependabot review, yearly Apple renewal and certificate expiry reminders, Google OAuth review dates, TestFlight 90-day expiry reminder for your personal builds, Android `targetSdk` yearly bump (Play requirement), iOS SDK minimum requirement for new submissions, privacy policy review date.
8. **Retrospective & v1.1 backlog:** collect deferred items (reminders D4, iOS widget, statistics) into `docs/roadmap.md` — explicitly *not* part of this production pass.

## 3. Prompts

```
Read docs/production-plan/README.md, PROJECT_CONTEXT.md, SHEET_SYNC.md and part-2-beyond-code/B5-docs-support-and-launch.md. Write docs/user-guide.md and the matching site/ pages: a 3-step "Create your Sheet and connect" guide, a Plan-tab column reference (what to edit vs leave alone, adding one-off events like parties/festivals as new rows with unique IDs, marking rest days with Skip), offline/conflict behaviour, and troubleshooting mapped to the app's actual SyncError messages. Short sentences, numbered steps, no jargon (ADHD-friendly). Mark screenshot slots as [SCREENSHOT: description].
```
```
Create CONTRIBUTING.md, SECURITY.md, issue templates (bug report asking platform/app version/sync error text and warning never to paste sheet contents; feature request) and a PR template with a checklist (tests, strings externalised, no personal data). Rewrite README.md for end users and developers; keep personal instructions only in personal/README.md.
```
```
Create docs/maintenance-calendar.md (recurring tasks: dependency reviews, Apple membership renewal, certificate/profile expiry, TestFlight 90-day expiry for my personal builds, Play targetSdk yearly bump, Xcode/SDK minimums, privacy-policy and OAuth review dates, Google verification status) and docs/roadmap.md listing deferred items (reminders, iOS widget, statistics) clearly marked out of scope for v1.
```

## 4. Verification
Hand the guide to someone who has never seen the app; time them from install to first synced habit; fix whatever slows them down.

## 5. Definition of done
- [ ] User guide + FAQ live on the site; linked from the app (About) and store listings.
- [ ] README, CONTRIBUTING, SECURITY, templates in place.
- [ ] Support email + process defined.
- [ ] Maintenance calendar + roadmap written.
- [ ] A first-time tester completed setup unaided in < 5 minutes.
- [ ] `PROGRESS.md` ticked → **project production-ready**.
