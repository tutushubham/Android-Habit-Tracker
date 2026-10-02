# B4 — Build, sign, distribute

Goal: get the app to real devices and then the public in controlled stages — **you first, then a few testers, then everyone** — using Play and TestFlight/App Store only.

**Inputs:** Part 1 release builds, B1 workflows, B2 OAuth clients, B3 accounts/listings.

## 1. Analysis
- Android: signed AAB (`bundleRelease`), Play App Signing (upload key vs app-signing key), tracks: Internal testing → Closed testing → (Open testing) → Production, with staged rollout %.
- iOS: archive with distribution cert/profile, TestFlight internal (no review) → external (beta review) → App Store review → phased release.
- **Your own use** is supported earlier: Android internal testing / sideloaded release APK; iOS TestFlight to your own iPhone/iPad (build expires after 90 days — re-upload periodically).
- The app identity (`com.habitsheet.app`) is permanent once uploaded; decide D5 first.
- Existing-data compatibility: your personal installs (debug-signed or different signing key) **cannot update in place** to a Play-signed build on Android — signatures differ. Plan: export a JSON backup (P1-1) from your current install, install the production build, import the backup (or connect your sheet — sheet wins), verify, then uninstall the old one. iOS: if the bundle ID/team is unchanged, TestFlight/App Store builds update over development installs.

## 2. Steps
1. **Dry-run release locally:** follow `docs/RELEASING.md` to produce a signed AAB + IPA; install release APK from `bundletool` on your phone; smoke-test (sync, widget, backup).
2. **Play Console setup:** create app, upload first AAB to **Internal testing**, enrol Play App Signing, copy app-signing SHA-1 into the Android OAuth client (B2), add yourself + testers by email list.
3. **Pre-launch report & vitals:** review Play pre-launch report (crashes, accessibility, security), fix blockers.
4. **Migrate your own data** using the backup/sheet procedure above; keep the old install until verified.
5. **Closed testing:** run the required tester window if your account type needs it (≥12 testers/14 days for new personal accounts — verify current rule); gather feedback via a form (no backend).
6. **TestFlight:** upload via B1 workflow or Xcode Organizer; internal testers first (yourself on iPhone + iPad), then external group (requires beta app review); fill "what to test".
7. **Production submission:** Play: production release with **staged rollout** (e.g. 10% → 50% → 100%) once crash-free; App Store: submit with review notes (B3), **phased release** (7-day) enabled.
8. **Versioning discipline:** tag `vX.Y.Z`, update `CHANGELOG.md`, bump via P0-C procedure; never reuse a build number.
9. **Post-release monitoring (no backend):** Play Console vitals/ANR/crash clusters; Xcode Organizer crashes; App Store Connect metrics; user feedback email; review replies.
10. **Rollback/hotfix plan:** Play: halt rollout/ship patch; iOS: can't roll back — keep expedited-review procedure; keep previous AAB/IPA artefacts; documented hotfix branch flow.
11. **Update compatibility policy:** every release must pass: install-over-previous-version test with a populated DB (migration test fixtures from P1-1/P2).

## 3. Prompts

```
Read docs/production-plan/README.md and part-2-beyond-code/B4-build-sign-distribute.md and docs/RELEASING.md. Produce docs/release-runbook.md: a numbered, copy-pasteable runbook for (1) building signed AAB/IPA locally, (2) Play internal -> closed -> production with staged rollout, (3) TestFlight internal -> external -> App Store with phased release, (4) the personal-data migration procedure for my own devices (backup export, install, import/sheet reconnect, verify, remove old), (5) hotfix/rollback, (6) a pre-submission checklist including the install-over-previous-version test. Do not invent console UI text you cannot verify; describe by intent.
```
```
Create CHANGELOG.md (Keep a Changelog format) with an Unreleased section, and a script scripts/verify-release.sh that checks: tag == appVersionName, versionCode increased vs previous tag, no uncommitted changes, git grep for personal IDs is clean, and the release AAB/IPA exist. Wire it into the release workflows from B1.
```
```
Write docs/tester-guide.md (for friends/testers): how to join the Play internal/closed test and TestFlight, how to create the Google Sheet and paste the link, what to report, and where to send feedback.
```

## 4. Verification
Install production build from Play internal track and TestFlight on your actual phone + iPad; link your sheet; do a full day's tracking on both; confirm the sheet reflects changes; update to a second build and confirm data survives.

## 5. Definition of done
- [ ] Android in production (staged rollout complete) or consciously held at internal/closed.
- [ ] iOS on TestFlight (internal+external) and/or App Store with review passed.
- [ ] Your own data migrated and verified on phone and iPad.
- [ ] Runbook, changelog, verify script, hotfix plan exist.
- [ ] Monitoring routine defined; `PROGRESS.md` ticked.
