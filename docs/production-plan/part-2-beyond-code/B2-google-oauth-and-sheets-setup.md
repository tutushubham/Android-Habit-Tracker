# B2 — Google OAuth & Sheets API setup

Goal: any user can sign in and sync with their own sheet, and **you** don't get signed out every week. No backend: only a Google Cloud project, OAuth clients, and a consent screen.

**Inputs from Part 1:** config variables `GOOGLE_IOS_CLIENT_ID` / reversed ID (P0-C), scope `https://www.googleapis.com/auth/spreadsheets`, release signing keys, privacy policy URL (B3).
**Decision D3 applies.** Facts below about Google policy should be re-checked in the Google Cloud console/docs at execution time — they change.

## 1. Analysis
- Today: one Cloud project (personal), consent screen in **Testing**, Android client registered with the **debug** SHA-1, iOS client with bundle `com.habitsheet.app`. Scope `spreadsheets` is **sensitive**.
- Pasted-link flow needs access to arbitrary user-created spreadsheets → narrow `drive.file` scope won't see them; stay on `spreadsheets` (D3).
- In *Testing* status for external users, refresh tokens expire after about 7 days → periodic sign-outs; limited to listed test users (max 100).
- Publishing status *In production* without verification: usable by anyone, shows "unverified app" warning, capped at ~100 users, until verified.
- Verification for sensitive scopes needs: a homepage and privacy policy on a **domain you own/verified in Search Console** (a `github.io` URL generally does not satisfy domain verification — use a custom domain if you pursue verification), a demo video showing scope use, justification text. No paid third-party security assessment for sensitive (non-restricted) scopes.
- Android: Google Identity `AuthorizationClient` identifies the app by **package name + signing-certificate SHA-1**. Play App Signing means the **Play upload key and the Play app-signing key each have different SHA-1s**; register the app-signing key SHA-1 (from Play Console) and the upload key for local release testing.
- iOS: iOS client ID + reversed client ID URL scheme in `Info.plist` (from xcconfig); GoogleSignIn-iOS SDK.

## 2. Steps
1. **Create/choose the Cloud project for production** (a dedicated project named after the app; owner = a shared mailbox you control). Enable **Google Sheets API**.
2. **OAuth consent screen:** User type External; app name, logo, support email, developer contact; authorised domains (your domain, if any); links to privacy policy (B3) and terms (optional); scopes: only `…/auth/spreadsheets` (plus basic profile if the SDKs require it).
3. **Create OAuth clients:**
   - Android clients: (a) debug SHA-1, (b) release/upload SHA-1, (c) **Play app-signing SHA-1** (after the first Play upload).
   - iOS client: bundle ID `com.habitsheet.app` (or your final D5 ID).
   - Put IDs in CI/xcconfig (P0-C), not in git-tracked files.
4. **Your own account's token stability:** set publishing status to **In production** (even unverified) so tokens don't expire in 7 days; accept the unverified-app warning for yourself; document the click-through in `docs/user-guide` for early users.
5. **Decide verification:** if you expect >100 users or want to remove the warning, prepare submission: privacy policy text states exact Sheets usage (read/write only the user-selected spreadsheet's `Plan` tab; nothing stored on servers; no sharing/selling), homepage, scope justification, a 2–3 min screencast (sign-in → paste link → sync → open sheet), and domain verification in Search Console.
6. **Error/consent UX check:** test flows: first-time consent, denied consent, revoked access (myaccount.google.com/permissions), account without edit access to the sheet, expired token, quota (429). Confirm messages from P0-B.
7. **Quota & limits:** default Sheets API read/write quotas per minute/project/user are generous for this app; check the quota dashboard and confirm the coalescing sync (P0-B) keeps requests low. Set quota alerts.
8. **Document** the whole setup in `docs/google-cloud-setup.md` (project IDs are fine; no secrets exist for these client types — no client secret is shipped).

## 3. Prompts (for the parts Claude can help with in the repo)

```
Read docs/production-plan/README.md, 00-context/DECISIONS.md (D3) and part-2-beyond-code/B2-google-oauth-and-sheets-setup.md. Draft docs/google-cloud-setup.md with a step-by-step checklist (project, API, consent screen fields with proposed text, OAuth clients incl. all three Android SHA-1s, iOS client, publishing status) and a "how to get each SHA-1" section (debug keystore, upload keystore, Play app signing). Also draft the Google verification submission pack as docs/google-verification-pack.md: scope justification, data-usage statement, demo-video script, homepage/privacy requirements. Do not invent URLs or IDs; use clearly marked placeholders.
```
```
Review the code paths that call Google authorization (androidMain AndroidSheetTokenProvider, iosApp HabitSheetApp.swift IosSheetTokenProvider) and write a test matrix doc docs/auth-test-matrix.md covering first consent, denied, revoked, no-edit-access, expired token, offline, multiple Google accounts, and sign-out/disconnect on both platforms, with expected UI messages from the SyncError mapping.
```

## 4. Verification (manual, by you)
Test matrix executed on a physical Android phone and an iPad using a **second Google account that is not a test user** (after setting *In production*). Confirm no 7-day expiry after a week of use.

## 5. Definition of done
- [ ] Production Cloud project, Sheets API enabled, consent screen complete (links filled after B3).
- [ ] Android clients for debug/upload/Play-signing SHA-1s + iOS client created; IDs wired through CI/xcconfig.
- [ ] Publishing status In production; owner no longer re-authenticates weekly.
- [ ] Decision recorded: stay unverified (≤100 users) or submit verification; pack drafted either way.
- [ ] Auth test matrix passed on both platforms; `PROGRESS.md` ticked.
