# B3 — Legal, privacy, and store listings

Goal: everything the stores and Google require before review: privacy policy, data-safety/privacy answers, listing assets, accounts, age rating, support contact. The app has no backend and collects nothing itself — the paperwork must say exactly that, and describe the Google Sheets access accurately.

**Inputs:** D5 (final name, bundle IDs, publisher, support email), Part 1 app (About screen links), B2 (consent-screen links).

## 1. Analysis — facts about the app to declare
- Data stays on the device (SQLite) and in the **user's own Google Sheet**. No servers, no analytics, no ads, no accounts of ours, no crash-reporting SDK (unless you add one — then update everything).
- Network: HTTPS calls to `sheets.googleapis.com` (and Google auth) only. Permission: `INTERNET` (Android).
- Google user data: OAuth access token (not persisted by the app; platform libraries manage sessions); scope `spreadsheets`.
- Backups: user-initiated JSON/CSV export; Android Auto Backup of the DB is disabled (P0-C).
- Sensitive-ish content: habit names, notes about health/fitness/ADHD habits typed by the user — it is user-generated data stored in their own sheet; the privacy policy must say it is never accessed by the developer.
- Children: not targeted; set an age rating accordingly (likely 4+/Everyone, verify questionnaires).
- Apple Sign-In rule (App Store Review Guideline 4.8) and account deletion rule (5.1.1(v)): Google is used only to authorise Sheets access, there is no app account — confirm with current guideline text; be ready to explain in review notes.
- Reviewers must be able to use core features **without** signing in (local-only mode works) — state this in review notes.

## 2. Steps
1. **Accounts:** Google Play Console (one-time fee; verify identity; a *personal* account may require a closed test with ≥12 testers for ≥14 days before production — check current policy, plan lead time) and Apple Developer Program (annual fee; enrolment can take days). Decide individual vs organisation (organisation requires a D-U-N-S number and a domain email).
2. **Domain & hosting for static pages (no backend):** custom domain (recommended; required if you pursue Google verification) pointing to GitHub Pages/Cloudflare Pages. Pages: home, privacy policy, support/FAQ, terms (optional), account/data deletion info ("delete data on device: Settings → Reset; your sheet is yours: delete it in Google Drive; revoke access at myaccount.google.com/permissions").
3. **Privacy policy** covering: who you are + contact; what data the app handles (on-device, user's own sheet); what is *not* collected; Google API Services User Data Policy statement including **Limited Use** disclosure ("use of information received from Google APIs will adhere to the Google API Services User Data Policy, including the Limited Use requirements"); retention/deletion; children; changes; GDPR/CCPA-style rights (no data held by you); contact email. Draft it from the template in this plan, have a human legal check if you can.
4. **Play Console forms:** Data safety (answer: no data collected/shared by the developer; data handled locally; Google Sheets access disclosed as user-initiated transfer), content rating questionnaire, target audience (13+/all, not "children"), ads: none, app access instructions (works without login), government/finance/health declarations (select none; confirm "health" category answers if the habit domain triggers any), privacy policy URL.
5. **App Store Connect:** app record (bundle ID), privacy "nutrition label" (Data Not Collected), privacy policy URL, support URL, age rating, export compliance (HTTPS only → exempt; matches `ITSAppUsesNonExemptEncryption=NO`), review notes (no login required; optional Google sign-in for sync; how to test sync with a demo Google account and a prepared sheet).
6. **Store listing assets:** app name/subtitle, short + full descriptions (ADHD-friendly, Sheets-as-backend, privacy-first, offline-first — avoid medical claims), keywords, feature graphic (Play 1024×500), icon (512 Play / 1024 Apple), screenshots per device class (phone, 7"/10" tablet for Play; iPhone 6.9"/6.5", iPad 13"/12.9"), optional preview video.
7. **Legal hygiene:** open-source licence notices screen/file (from P1-3), trademark check on the name, third-party fonts licence (Hanken Grotesk, Inter, JetBrains Mono — OFL; include licence texts), Google "Sheets" trademark usage: do not imply endorsement; use "works with Google Sheets" wording.
8. **Medical disclaimer:** state that the app is a tracker, not medical advice or treatment, in the description and About.

## 3. Prompts

```
Read docs/production-plan/README.md, 00-context/PROJECT_CONTEXT.md, DECISIONS.md (D5) and part-2-beyond-code/B3-legal-privacy-store-listing.md. Create a docs/legal/ folder with: privacy-policy.md (accurate to this app: on-device SQLite + user's own Google Sheet, Google API Services User Data Policy Limited Use statement, no analytics/ads/accounts, deletion/revocation instructions, contact placeholder), terms.md (short), data-safety-answers.md (Google Play Data safety form answers and Apple privacy nutrition label answers, each with a one-line justification traceable to code), and review-notes.md (App Review / Play review notes incl. how to test without sign-in and with a demo sheet). Use clear [PLACEHOLDER] tokens for name, email, domain, date. Do not claim anything the code doesn't do — cite the files that prove each statement.
```
```
Create docs/store-listing/ with listing copy (short/long description, subtitle, keywords, what's new), a screenshot shot-list for each required device class, and an asset checklist (icon sizes, feature graphic). Avoid medical claims; include the disclaimer. Include licence notices for bundled fonts and dependencies (generate the dependency list from Gradle).
```
```
Add a static site under site/ (plain HTML/CSS, no framework, no JavaScript tracking) with home, privacy, support/FAQ and data-deletion pages generated from docs/legal, plus deployment instructions for GitHub Pages with a custom domain (CNAME). Wire the final URLs into the app's AppLinks config and the About screen.
```

## 4. Verification
- Policy URL loads without login; text matches code reality (re-audit if any SDK is added).
- Play pre-launch report and App Store validation show no policy flags.
- Peer read of the data-safety answers against `AndroidManifest.xml` permissions and network calls.

## 5. Definition of done
- [ ] Play and Apple developer accounts active; payment/identity verified.
- [ ] Domain + static site live with privacy, support, deletion pages.
- [ ] Data-safety, privacy label, rating, export compliance, review notes completed.
- [ ] Listing assets prepared for all required device classes.
- [ ] URLs wired into app config and the Google consent screen (B2).
- [ ] `PROGRESS.md` ticked.
